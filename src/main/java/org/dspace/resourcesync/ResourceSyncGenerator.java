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

// FIXME: this whole codebase is a total total mess, and needs to be rewritten from scratch

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

        ResourceSyncGenerator rsg = new ResourceSyncGenerator();
        Context context = new Context();

        try
        {
            if (cmd.hasOption("i"))
            {
                rsg.init(context);
            }
            else if (cmd.hasOption("u"))
            {
                rsg.update(context);
            }
            else if (cmd.hasOption("r"))
            {
                rsg.rebase(context);
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

    public ResourceSyncGenerator()
    {
        this.um = new UrlManager();
        this.sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    }

    public void init(Context context)
            throws IOException, SQLException, ParseException
    {
        // make sure that the directory exists, and that it is empty
        String outdir = this.ensureDirectory();
        this.emptyDirectory(outdir);

        // generate the description document (which will point to the not-yet-existent capability list)
        this.generateResourceSyncDescription(context);

        // generate the resource list
        this.generateResourceList(context);

        // should we generate a resource dump?
        boolean rd = false;
        if (ConfigurationManager.getBooleanProperty("resourcesync", "resourcedump.enable"))
        {
            this.generateResourceDump(context);
            rd = true;
        }

        // generate the capability list (with a resource list, without a change list, and maybe with a resource dump)
        this.generateCapabilityList(context, true, false, rd, false);

        // generate the blank changelist as a placeholder for the next iteration
        this.generateBlankChangeList(context);
    }

    public void update(Context context)
            throws IOException, SQLException, ParseException
    {
        String outdir = this.ensureDirectory();

        // generate the latest changelist
        String clFilename = this.generateLatestChangeList(context);

        // add to the change list archive
        this.addChangeListToArchive(context, clFilename);

        // update the last modified date in the capability list (and add the
        // changelistarchive if necessary)
        this.updateCapabilityList(context);
    }

    public void rebase(Context context)
            throws IOException, SQLException, ParseException
    {
        String outdir = this.ensureDirectory();

        // generate the resource list
        this.generateResourceList(context);

        // should be generate a resource dump?
        boolean rd = false;
        if (ConfigurationManager.getBooleanProperty("resourcesync", "resourcedump.enable"))
        {
            this.generateResourceDump(context);
            rd = true;
        }

        // generate the latest changelist
        String clFilename = this.generateLatestChangeList(context);

        // add to the change list archive
        this.addChangeListToArchive(context, clFilename);

        // update the last modified date in the capability list (and add the
        // changelistarchive if necessary)
        this.updateCapabilityList(context);
    }

    private String ensureDirectory()
            throws IOException
    {
        // make sure our output directory exists
        String outdir = ConfigurationManager.getProperty("resourcesync", "resourcesync.dir");
        if (outdir == null)
        {
            throw new IOException("No configuration for resourcesync.dir");
        }
        this.ensureDirectory(outdir);
        return outdir;
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
        String outdir = ConfigurationManager.getProperty("resourcesync", "resourcesync.dir");
        String rsdFile = outdir + File.separator + filename;
        FileOutputStream fos = new FileOutputStream(new File(rsdFile));
        return fos;
    }

    private FileInputStream getFileInputStream(String filename)
            throws IOException
    {
        String outdir = ConfigurationManager.getProperty("resourcesync", "resourcesync.dir");
        String file = outdir + File.separator + filename;
        FileInputStream fis = new FileInputStream(new File(file));
        return fis;
    }

    private void generateResourceSyncDescription(Context context)
            throws IOException
    {
        FileOutputStream fos = this.getFileOutputStream(FileNames.resourceSyncDocument);

        ResourceSyncDescription desc = new ResourceSyncDescription();
        desc.addCapabilityList(this.um.capabilityList());
        desc.serialise(fos);

        fos.close();
    }

    private void generateResourceList(Context context)
            throws SQLException, IOException
    {
        FileOutputStream fos = this.getFileOutputStream(FileNames.resourceList);

        DSpaceResourceList drl = new DSpaceResourceList();
        drl.generate(context, fos, this.um.capabilityList());

        fos.close();
    }

    private void deleteFile(String filename)
    {
        String outdir = ConfigurationManager.getProperty("resourcesync", "resourcesync.dir");
        File old = new File(outdir + File.separator + filename);
        if (old.exists())
        {
            old.delete();
        }
    }

    private void generateResourceDump(Context context)
            throws IOException, SQLException
    {
        this.deleteFile(FileNames.resourceDump);
        this.deleteFile(FileNames.resourceDumpZip);

        DSpaceResourceDump drd = new DSpaceResourceDump();
        drd.generate(context, ConfigurationManager.getProperty("resourcesync", "resourcesync.dir"), this.um.resourceSyncDescription());
    }

    private String getLastChangeListName()
            throws ParseException
    {
        String filename = null;
        Date from = new Date(0);
        File dir = new File(ConfigurationManager.getProperty("resourcesync", "resourcesync.dir"));
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
        File dir = new File(ConfigurationManager.getProperty("resourcesync", "resourcesync.dir"));
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

    private String generateLatestChangeList(Context context)
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
        dcl.generate(context, fos, from, to, this.um.capabilityList(), this.um.changeListArchive());
        fos.close();

        return filename;
    }

    private void generateBlankChangeList(Context context)
            throws IOException, SQLException, ParseException
    {
        Date to = new Date();
        String tr = this.sdf.format(to);

        FileOutputStream fos = this.getFileOutputStream(FileNames.changeList(tr));

        // generate the changelist for the period (which is of 0 length)
        DSpaceChangeList dcl = new DSpaceChangeList();
        dcl.generate(context, fos, to, to, this.um.capabilityList(), null);

        fos.close();
    }

    private void updateCapabilityList(Context context)
            throws IOException, ParseException
    {
        // just regenerate the capability list in its entirity
        boolean rd = false;
        if (ConfigurationManager.getBooleanProperty("resourcesync", "resourcedump.enable"))
        {
            rd = true;
        }
        this.generateCapabilityList(context, true, true, rd, true);
    }

    private void generateCapabilityList(Context context, boolean resourceList, boolean changeListArchive, boolean resourceDump, boolean changeList)
            throws IOException, ParseException
    {
        String describedBy = ConfigurationManager.getProperty("resourcesync", "capabilitylist.described-by");
        if ("".equals(describedBy))
        {
            describedBy = null;
        }

        FileOutputStream fos = this.getFileOutputStream(FileNames.capabilityList);

        String rlUrl = resourceList ? this.um.resourceList() : null;
        String claUrl = changeListArchive ? this.um.changeListArchive() : null;
        String rdUrl = resourceDump ? this.um.resourceDump() : null;
        String rsdUrl = this.um.resourceSyncDescription();

        // get the latest change list
        String changeListUrl = null;
        if (changeList)
        {
            String clFilename = this.getLastChangeListName();
            changeListUrl = this.um.changeList(clFilename);
        }

        DSpaceCapabilityList dcl = new DSpaceCapabilityList();
        dcl.generate(context, fos, describedBy, rlUrl, claUrl, rdUrl, rsdUrl, changeListUrl);
        fos.close();
    }


    private boolean fileExists(String filename)
    {
        String outdir = ConfigurationManager.getProperty("resourcesync", "resourcesync.dir");
        String path = outdir + File.separator + filename;
        File file = new File(path);
        return file.exists() && file.isFile();
    }


    private void addChangeListToArchive(Context context, String filename)
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
            dcla.generate(context, fos, changeLists, this.um.capabilityList(), cla);
            fos.close();
        }
        // if the change list archive does not exist create a new one with
        // our one new changelist in it
        else
        {
            FileOutputStream fos = this.getFileOutputStream(FileNames.changeListArchive);
            dcla.generate(context, fos, changeLists, this.um.capabilityList());
            fos.close();
        }
    }

}
