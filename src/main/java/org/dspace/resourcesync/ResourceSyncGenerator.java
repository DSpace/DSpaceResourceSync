package org.dspace.resourcesync;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ResourceSyncGenerator
{
    public static void main(String[] args)
            throws Exception
    {
        Context context = new Context();
        try
        {
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

            String drlFile = outdir + File.separator + "resourcelist.xml";
            FileOutputStream fos = new FileOutputStream(new File(drlFile));
            DSpaceResourceList drl = new DSpaceResourceList();
            drl.generate(context, fos, null);
        }
        finally
        {
            context.abort();
        }

    }
}
