package org.dspace.resourcesync;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

public class ResourceSyncGenerator
{
    public static void main(String[] args)
            throws Exception
    {
        Context context = new Context();
        try
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
                throw new IOException(outdir + " is not a directory");
            }

            // generate a full resourcelist
            String drlFile = outdir + File.separator + "resourcelist.xml";
            FileOutputStream fos = new FileOutputStream(new File(drlFile));
            DSpaceResourceList drl = new DSpaceResourceList();
            drl.generate(context, fos, null);
            fos.close();

            // generate a changelist for the last week
            String clFile = outdir + File.separator + "changelist.xml";
            Date to = new Date();
            Date from = new Date(to.getTime() - 604800000L);
            fos = new FileOutputStream(new File(clFile));
            DSpaceChangeList dcl = new DSpaceChangeList();
            dcl.generate(context, fos, from, to, null);
            fos.close();
        }
        finally
        {
            context.abort();
        }

    }
}
