/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree
 */
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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Richard Jones
 *
 */
public class DSpaceResourceDumpZip extends DSpaceResourceList
{
    private String dumpDir;
    private ZipOutputStream zos;

    public DSpaceResourceDumpZip(Context context, String dumpDir)
    {
        super(context, true);
        this.dumpDir = dumpDir;
        try
        {
            this.zos = new ZipOutputStream(new FileOutputStream(this.dumpDir + File.separator + FileNames.resourceDumpZip));
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void serialise()
            throws SQLException, IOException
    {
        // first generate the manifest file.  This uses the other overrides in this object
        // to also copy in the bitstreams and metadata serialisations which are relevant
        // everything will be added to the zip
        String drlFile = this.dumpDir + File.separator + FileNames.resourceDumpManifest;
        FileOutputStream fos = new FileOutputStream(new File(drlFile));
        this.serialise(fos);

        // incorporate the manifest into the zip
        File manifest = new File(drlFile);
        FileInputStream is = new FileInputStream(manifest);

        this.copyToZip(FileNames.resourceDumpManifest, is);
        this.zos.close();

        // get rid of the left over manifest file
        manifest.delete();
    }

    private void copyToZip(String entryName, InputStream is)
            throws IOException
    {
        this.zos.putNextEntry(new ZipEntry(entryName));
        byte[] buffer = new byte[102400]; // 100k chunks
        int len = is.read(buffer);
        while (len != -1)
        {
            this.zos.write(buffer, 0, len);
            len = is.read(buffer);
        }
        this.zos.closeEntry();
    }

    @Override
    protected URL addBitstream(Bitstream bitstream, Item item, List<Collection> collections, ResourceSyncDocument rl)
    {
        URL url = super.addBitstream(bitstream, item, collections, rl);
        String dumppath = this.getPath(item, bitstream, null, false);
        url.setPath(dumppath);

        // now actually get the bitstream and stick it in the directory
        try
        {
            String entryName = this.getPath(item, bitstream, null, true);
            InputStream is = bitstream.retrieve();
            this.copyToZip(entryName, is);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e); // FIXME: not so good, probably best to have the method sig support the error
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e); // FIXME: not so good, probably best to have the method sig support the error
        }
        catch (AuthorizeException e)
        {
            throw new RuntimeException(e); // FIXME: not so good, probably best to have the method sig support the error
        }

        return url;
    }

    private String getPath(Item item, Bitstream bitstream, MetadataFormat format, boolean nativeSeparator)
    {
        String separator = nativeSeparator ? File.separator : "/";
        String itempath = item.getHandle().replace("/", "_");

        String filepath;
        if (bitstream != null)
        {
            filepath = Integer.toString(bitstream.getSequenceID()) + "_" + bitstream.getName();
        }
        else if (format != null)
        {
            filepath = format.getPrefix();
        }
        else
        {
            throw new RuntimeException("must provide either bitstream or metadata format");
        }
        String dumppath = separator + FileNames.dumpResourcesDir + separator + itempath + separator + filepath;
        return dumppath;
    }

    @Override
    protected URL addMetadata(Item item, MetadataFormat format, List<Bitstream> describes, List<Collection> collections, ResourceSyncDocument rl)
    {
        URL url = super.addMetadata(item, format, describes, collections, rl);
        String dumppath = this.getPath(item, null, format, false);
        url.setPath(dumppath);

        // now actually get the metadata export and stick it in the directory
        try
        {
            String entryName = this.getPath(item, null, format, true);
            ZipEntry e = new ZipEntry(entryName);
            this.zos.putNextEntry(e);

            // get the dissemination crosswalk for this prefix and get the element for the object
            MetadataDisseminator.disseminate(item, format.getPrefix(), this.zos);

            this.zos.closeEntry();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e); // FIXME: not so good, probably best to have the method sig support the error
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e); // FIXME: not so good, probably best to have the method sig support the error
        }
        catch (AuthorizeException e)
        {
            throw new RuntimeException(e); // FIXME: not so good, probably best to have the method sig support the error
        }
        catch (CrosswalkException e)
        {
            throw new RuntimeException(e); // FIXME: not so good, probably best to have the method sig support the error
        }

        return url;
    }
}