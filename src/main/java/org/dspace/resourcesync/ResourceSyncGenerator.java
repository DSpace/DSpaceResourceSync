package org.dspace.resourcesync;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

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
                hf.printHelp("ResourceSyncGenerator", "Manager ResourceSync documents for DSpace", options, "");
            }
        }
        finally
        {
            context.abort();
        }
    }

    public void init(Context context)
            throws IOException, SQLException, ParseException
    {
        // make sure that the directory exists, and that it is empty
        String outdir = this.ensureDirectory();
        this.emptyDirectory(outdir);

        // we need to know where the capability list will go before we generate the resource list
        String clUrl = this.getCapabilityListUrl();

        // generate the resource list
        String rlFilename = this.generateResourceList(context, outdir, clUrl);

        // generate the capability list (without a change list)
        this.generateCapabilityList(context, outdir, rlFilename, null);

        // generate the blank changelist
        this.generateBlankChangeList(context, outdir, clUrl);
    }

    public void update(Context context)
            throws IOException, SQLException, ParseException
    {
        String outdir = this.ensureDirectory();

        // we need to know where the capability list will go before we generate the change list
        String clUrl = this.getCapabilityListUrl();

        // generate the latest changelist
        String clFilename = this.generateLatestChangeList(context, outdir, clUrl);

        // add to the change list archive
        // TODO

        // update the last modified date in the capability list
        // TODO
    }

    public void rebase(Context context)
            throws IOException, SQLException, ParseException
    {
        String outdir = this.ensureDirectory();

        // we need to know where the capability list will go before we generate the resource list
        String clUrl = this.getCapabilityListUrl();

        // generate the resource list
        String rlFilename = this.generateResourceList(context, outdir, clUrl);

        // generate the latest changelist
        String clFilename = this.generateLatestChangeList(context, outdir, clUrl);

        // add to the change list archive
        // TODO

        // update the last modified dates in the capability list
        // TODO
    }

    private String ensureDirectory()
            throws IOException
    {
        // make sure our output directory exists
        String outdir = ConfigurationManager.getProperty("resourcesync", "resourcesync.dir");
        File od = new File(outdir);
        if (!od.exists())
        {
            od.mkdir();
        }
        if (!od.isDirectory())
        {
            throw new IOException(outdir + " exists, but is not a directory");
        }
        return outdir;
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

    public String getCapabilityListUrl()
    {
        String base = ConfigurationManager.getProperty("resourcesync", "base-url");
        return base + "/capabilitylist.xml";
    }

    private String generateResourceList(Context context, String outdir, String clUrl)
            throws SQLException, IOException
    {
        String drlFile = outdir + File.separator + "resourcelist.xml";
        FileOutputStream fos = new FileOutputStream(new File(drlFile));
        DSpaceResourceList drl = new DSpaceResourceList();
        drl.generate(context, fos, clUrl);
        fos.close();
        return "resourcelist.xml";
    }

    private String generateLatestChangeList(Context context, String outdir, String clUrl)
            throws ParseException, IOException, SQLException
    {
        // determine the "from" date by looking at existing changelists
        Date from = new Date(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        File dir = new File(outdir);
        File[] files = dir.listFiles();
        if (files != null)
        {
            for (File f : dir.listFiles())
            {
                if (f.getName().startsWith("changelist_"))
                {
                    String dr = f.getName().substring("changelist_".length(), ".xml".length());
                    Date possibleFrom = sdf.parse(dr);
                    if (possibleFrom.getTime() > from.getTime())
                    {
                        from = possibleFrom;
                    }
                }
            }
        }

        // the "to" date is now, and we'll also use that in the filename, so get the
        // string representation
        Date to = new Date();
        String tr = sdf.format(to);

        // create the changelist name for the period
        String filename = "changelist_" + tr + ".xml";
        String clFile = outdir + File.separator + filename;
        FileOutputStream fos = new FileOutputStream(new File(clFile));

        // generate the changelist for the period
        DSpaceChangeList dcl = new DSpaceChangeList();
        dcl.generate(context, fos, from, to, clUrl);
        fos.close();

        return filename;
    }

    private String generateBlankChangeList(Context context, String outdir, String clUrl)
            throws IOException, SQLException, ParseException
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Date from = new Date();
        Date to = new Date();
        String tr = sdf.format(to);

        String filename = "changelist_" + tr + ".xml";
        String clFile = outdir + File.separator + filename;
        FileOutputStream fos = new FileOutputStream(new File(clFile));

        // generate the changelist for the period
        DSpaceChangeList dcl = new DSpaceChangeList();
        dcl.generate(context, fos, from, to, clUrl);
        fos.close();

        return filename;
    }

    private void generateCapabilityList(Context context, String outdir, String rlFilename, String claFilename)
            throws IOException
    {
        String describedBy = ConfigurationManager.getProperty("resourcesync", "capabilitylist.described-by");
        if ("".equals(describedBy))
        {
            describedBy = null;
        }

        String clFile = outdir + File.separator + "capabilitylist.xml";
        FileOutputStream fos = new FileOutputStream(new File(clFile));

        String rlUrl = rlFilename == null ? null : this.getResourceListUrl(rlFilename);
        String claUrl = claFilename == null ? null : this.getChangeListArchiveUrl(claFilename);

        DSpaceCapabilityList dcl = new DSpaceCapabilityList();
        dcl.generate(context, fos, describedBy, rlUrl, claUrl);
    }

    private String getResourceListUrl(String filename)
    {
        String base = ConfigurationManager.getProperty("resourcesync", "base-url");
        return base + "/" + filename;
    }

    private String getChangeListArchiveUrl(String filename)
    {
        String base = ConfigurationManager.getProperty("resourcesync", "base-url");
        return base + "/" + filename;
    }
}
