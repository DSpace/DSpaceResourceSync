package org.dspace.resourcesync;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.openarchives.resourcesync.ChangeListArchive;
import org.openarchives.resourcesync.ResourceSyncDescription;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// FIXME: this whole codebase is a total total mess, and needs to be rewritten

public class ResourceSyncGenerator
{
    public static void main(String[] args)
            throws Exception
    {
        Options options = new Options();
        options.addOption("i", "init", false, "Create a fresh ResourceSync description of this repository - this will remove any previous ResourceSync documents");
        options.addOption("u", "update", false, "Update the Change List ResourceSync document with the changes since this script last ran");
        options.addOption("r", "rebase", false, "Update the Resource List ResourceSync document to reflect the current state of the archive, and bring the Change List up to the same level");
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = parser.parse( options, args);

        Context context = new Context();
        ResourceSyncGenerator rsg = new ResourceSyncGenerator(context);

        try
        {
            if (cmd.hasOption("i"))
            {
                rsg.init();
            }
            else if (cmd.hasOption("u"))
            {
                rsg.update();
            }
            else if (cmd.hasOption("r"))
            {
                rsg.rebase();
            }
            else
            {
                HelpFormatter hf = new HelpFormatter();
                hf.printHelp("ResourceSyncGenerator", "Manage ResourceSync documents for DSpace", options, "");
            }
        }
        finally
        {
            context.abort();
        }
    }

    private UrlManager um;
    private SimpleDateFormat sdf;
    private Context context;
    private boolean resourceDump = false;
    private String outdir;

    public ResourceSyncGenerator(Context context)
            throws IOException
    {
        this.um = new UrlManager();
        this.sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        this.context = context;
        this.resourceDump = ConfigurationManager.getBooleanProperty("resourcesync", "resourcedump.enable");
        this.outdir = ConfigurationManager.getProperty("resourcesync", "resourcesync.dir");
        if (this.outdir == null)
        {
            throw new IOException("No configuration for resourcesync.dir");
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    // methods to be used to interact with the generator
    //////////////////////////////////////////////////////////////////////////////

    public void init()
            throws IOException, SQLException, ParseException
    {
        // make sure that the directory exists, and that it is empty
        this.ensureResourceSyncDirectory();
        this.emptyResourceSyncDirectory();

        // generate the description document (which will point to the not-yet-existent capability list)
        this.generateResourceSyncDescription();

        // generate the resource list
        this.generateResourceList();

        // should we generate a resource dump?
        if (this.resourceDump)
        {
            this.generateResourceDump();
        }

        // generate the capability list (with a resource list, without a change list, and maybe with a resource dump)
        this.generateCapabilityList(true, false, this.resourceDump, false);

        // generate the blank changelist as a placeholder for the next iteration
        this.generateBlankChangeList();
    }

    public void update()
            throws IOException, SQLException, ParseException
    {
        // check the directory is there
        this.ensureResourceSyncDirectory();

        // generate the latest changelist
        String clFilename = this.generateLatestChangeList();

        // add to the change list archive
        this.addChangeListToArchive(clFilename);

        // update the last modified date in the capability list (and add the
        // changelistarchive if necessary)
        this.updateCapabilityList();
    }

    public void rebase()
            throws IOException, SQLException, ParseException
    {
        this.ensureResourceSyncDirectory();

        // generate the resource list
        this.generateResourceList();

        // should be generate a resource dump?
        if (this.resourceDump)
        {
            this.generateResourceDump();
        }

        // generate the latest changelist
        String clFilename = this.generateLatestChangeList();

        // add to the change list archive
        this.addChangeListToArchive(clFilename);

        // update the last modified date in the capability list (and add the
        // changelistarchive if necessary)
        this.updateCapabilityList();
    }

    //////////////////////////////////////////////////////////////////////
    // file management utility methods
    //////////////////////////////////////////////////////////////////////

    private void ensureResourceSyncDirectory()
            throws IOException
    {
        // make sure our output directory exists
        this.ensureDirectory(this.outdir);
    }

    private void ensureDirectory(String dir)
            throws IOException
    {
        File od = new File(dir);
        if (!od.exists())
        {
            od.mkdir();
        }
        if (!od.isDirectory())
        {
            throw new IOException(dir + " exists, but is not a directory");
        }
    }

    private void emptyResourceSyncDirectory()
            throws IOException
    {
        this.emptyDirectory(this.outdir);
    }

    private void emptyDirectory(String outdir)
    {
        File out = new File(outdir);
        File[] files = out.listFiles();
        if (files == null)
        {
            return;
        }
        for (File f : files)
        {
            f.delete();
        }
    }

    private FileOutputStream getFileOutputStream(String filename)
            throws IOException
    {
        String rsdFile = this.outdir + File.separator + filename;
        FileOutputStream fos = new FileOutputStream(new File(rsdFile));
        return fos;
    }

    private FileInputStream getFileInputStream(String filename)
            throws IOException
    {
        String file = this.outdir + File.separator + filename;
        FileInputStream fis = new FileInputStream(new File(file));
        return fis;
    }

    private void deleteFile(String filename)
    {
        File old = new File(this.outdir + File.separator + filename);
        if (old.exists())
        {
            old.delete();
        }
    }

    private String getLastChangeListName()
            throws ParseException
    {
        String filename = null;
        Date from = new Date(0);
        File dir = new File(this.outdir);
        File[] files = dir.listFiles();
        if (files != null)
        {
            for (File f : dir.listFiles())
            {
                if (FileNames.isChangeList(f))
                {
                    String dr = FileNames.changeListDate(f);
                    Date possibleFrom = this.sdf.parse(dr);
                    if (possibleFrom.getTime() > from.getTime())
                    {
                        from = possibleFrom;
                        filename = f.getName();
                    }
                }
            }
        }
        return filename;
    }

    private Date getLastChangeListDate()
            throws ParseException
    {
        Date from = new Date(0);
        File dir = new File(this.outdir);
        File[] files = dir.listFiles();
        if (files != null)
        {
            for (File f : dir.listFiles())
            {
                if (FileNames.isChangeList(f))
                {
                    String dr = FileNames.changeListDate(f);
                    Date possibleFrom = this.sdf.parse(dr);
                    if (possibleFrom.getTime() > from.getTime())
                    {
                        from = possibleFrom;
                    }
                }
            }
        }
        return from;
    }

    private boolean fileExists(String filename)
    {
        String path = this.outdir + File.separator + filename;
        File file = new File(path);
        return file.exists() && file.isFile();
    }

    ////////////////////////////////////////////////////////////////////
    // private document generation methods
    ////////////////////////////////////////////////////////////////////

    private void generateResourceSyncDescription()
            throws IOException
    {
        FileOutputStream fos = this.getFileOutputStream(FileNames.resourceSyncDocument);

        // no need for a DSpace-specific implementation, it is so simple
        ResourceSyncDescription desc = new ResourceSyncDescription();
        desc.addCapabilityList(this.um.capabilityList());
        desc.serialise(fos);

        fos.close();
    }

    private void updateCapabilityList()
            throws IOException, ParseException
    {
        // just regenerate the capability list in its entirity
        this.generateCapabilityList(true, true, this.resourceDump, true);
    }

    private void generateCapabilityList(boolean resourceList, boolean changeListArchive, boolean resourceDump, boolean changeList)
            throws IOException, ParseException
    {
        // get the latest change list if there is one and we want one
        String changeListUrl = null;
        if (changeList)
        {
            String clFilename = this.getLastChangeListName();
            changeListUrl = this.um.changeList(clFilename);
        }

        FileOutputStream fos = this.getFileOutputStream(FileNames.capabilityList);

        DSpaceCapabilityList dcl = new DSpaceCapabilityList(this.context, resourceList, changeListArchive, resourceDump, changeList, changeListUrl);
        dcl.serialise(fos);

        fos.close();
    }

    private void generateResourceList()
            throws SQLException, IOException
    {
        FileOutputStream fos = this.getFileOutputStream(FileNames.resourceList);

        DSpaceResourceList drl = new DSpaceResourceList();
        drl.generate(this.context, fos, this.um.capabilityList());

        fos.close();
    }

    private void generateResourceDump()
            throws IOException, SQLException
    {
        this.deleteFile(FileNames.resourceDump);
        this.deleteFile(FileNames.resourceDumpZip);

        DSpaceResourceDump drd = new DSpaceResourceDump();
        drd.generate(this.context, this.outdir, this.um.resourceSyncDescription());
    }

    private String generateLatestChangeList()
            throws ParseException, IOException, SQLException
    {
        // determine the "from" date by looking at existing changelists
        Date from = this.getLastChangeListDate();

        // the "to" date is now, and we'll also use that in the filename, so get the
        // string representation
        Date to = new Date();
        String tr = sdf.format(to);

        // create the changelist name for the period
        String filename = FileNames.changeList(tr);
        FileOutputStream fos = this.getFileOutputStream(filename);

        // generate the changelist for the period
        DSpaceChangeList dcl = new DSpaceChangeList();
        dcl.generate(this.context, fos, from, to, this.um.capabilityList(), this.um.changeListArchive());
        fos.close();

        return filename;
    }

    private void generateBlankChangeList()
            throws IOException, SQLException, ParseException
    {
        Date to = new Date();
        String tr = this.sdf.format(to);

        FileOutputStream fos = this.getFileOutputStream(FileNames.changeList(tr));

        // generate the changelist for the period (which is of 0 length)
        DSpaceChangeList dcl = new DSpaceChangeList();
        dcl.generate(this.context, fos, to, to, this.um.capabilityList(), null);

        fos.close();
    }

    private void addChangeListToArchive(String filename)
            throws IOException, ParseException
    {
        // get the URL of the new changelist
        String loc = this.um.changeList(filename);

        // get the date of the new changelist (it is encoded in the filename)
        String dr = FileNames.changeListDate(filename);
        Date date = this.sdf.parse(dr);

        // create a single element map of the changelist and its last modified
        // date for addition to the change list archive
        Map<String, Date> changeLists = new HashMap<String, Date>();
        changeLists.put(loc, date);

        DSpaceChangeListArchive dcla = new DSpaceChangeListArchive();
        ChangeListArchive cla = null;

        // if the change list archive exists and is a file, we need to
        // read it in as a change list
        if (this.fileExists(FileNames.changeListArchive))
        {
            // read the ChangeListArchive
            FileInputStream fis = this.getFileInputStream(FileNames.changeListArchive);
            cla = dcla.parse(fis);
            fis.close();

            // now serialise the new change lists
            FileOutputStream fos = this.getFileOutputStream(FileNames.changeListArchive);
            dcla.generate(this.context, fos, changeLists, this.um.capabilityList(), cla);
            fos.close();
        }
        // if the change list archive does not exist create a new one with
        // our one new changelist in it
        else
        {
            FileOutputStream fos = this.getFileOutputStream(FileNames.changeListArchive);
            dcla.generate(this.context, fos, changeLists, this.um.capabilityList());
            fos.close();
        }
    }

}
