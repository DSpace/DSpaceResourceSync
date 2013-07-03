package org.dspace.resourcesync;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.DisseminationCrosswalk;
import org.dspace.core.Context;
import org.dspace.core.PluginManager;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.openarchives.resourcesync.ResourceDump;
import org.openarchives.resourcesync.ResourceSyncDocument;
import org.openarchives.resourcesync.URL;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DSpaceResourceDump extends DSpaceResourceDocument
{
    protected String metadataChangeFreq = null;
    protected String bitstreamChangeFreq = null;

    public DSpaceResourceDump(Context context)
    {
        super(context);
        this.metadataChangeFreq = this.getMetadataChangeFreq();
        this.bitstreamChangeFreq = this.getBitstreamChangeFreq();
    }

    public DSpaceResourceDump(Context context, List<String> exposeBundles, List<MetadataFormat> mdFormats,
                              String mdChangeFreq, String bitstreamChangeFreq)
    {
        super(context, exposeBundles, mdFormats);
        this.metadataChangeFreq = mdChangeFreq;
        this.bitstreamChangeFreq = bitstreamChangeFreq;
    }

    public void serialise(String rdDir)
            throws IOException, SQLException
    {
        // this generates the manifest file and zip file
        DSpaceResourceDumpZip drl = new DSpaceResourceDumpZip(this.context, rdDir);
        drl.serialise(); // no output stream required

        // now generate the dump file for the resourcesync framework
        ResourceDump rd = new ResourceDump(new Date(), this.um.capabilityList());
        rd.addResourceZip(this.um.resourceDumpZip(), new Date(), "application/zip", this.getDumpSize(rdDir));
        String rdFile = rdDir + File.separator + FileNames.resourceDump;
        FileOutputStream fos = new FileOutputStream(new File(rdFile));
        rd.serialise(fos);
        fos.close();
    }

    private long getDumpSize(String dir)
    {
        String path = dir + File.separator + FileNames.resourceDumpZip;
        File file = new File(path);
        return file.length();
    }
}
