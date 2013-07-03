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

    public DSpaceResourceDump()
    {
        super();
        this.metadataChangeFreq = this.getMetadataChangeFreq();
        this.bitstreamChangeFreq = this.getBitstreamChangeFreq();
    }

    public DSpaceResourceDump(List<String> exposeBundles, List<MetadataFormat> mdFormats,
                              String mdChangeFreq, String bitstreamChangeFreq)
    {
        super(exposeBundles, mdFormats);
        this.metadataChangeFreq = mdChangeFreq;
        this.bitstreamChangeFreq = bitstreamChangeFreq;
    }

    public void generate(Context context, String rdDir, String rsDesc)
            throws IOException, SQLException
    {
        // this generates the zip file
        String drlFile = rdDir + File.separator + "manifest.xml";
        FileOutputStream fos = new FileOutputStream(new File(drlFile));
        DSpaceResourceList drl = new DSpaceResourceListManifest(rdDir);
        drl.generate(context, fos, rsDesc);
        fos.close();

        // now generate the dump file for the resourcesync framework
        UrlManager um = new UrlManager();
        ResourceDump rd = new ResourceDump(new Date(), rsDesc);
        rd.addResourceZip(um.resourceDumpZip(), new Date(), "application/zip", this.getDumpSize(rdDir));
        String rdFile = rdDir + File.separator + FileNames.resourceDump;
        FileOutputStream fos2 = new FileOutputStream(new File(rdFile));
        rd.serialise(fos2);
        fos2.close();
    }

    private long getDumpSize(String dir)
    {
        String path = dir + File.separator + FileNames.resourceDumpZip;
        File file = new File(path);
        return file.length();
    }

    private class DSpaceResourceListManifest extends DSpaceResourceList
    {
        private String dumpDir;
        private ZipOutputStream zos;

        public DSpaceResourceListManifest(String dumpDir)
        {
            super();
            this.dumpDir = dumpDir;
            try
            {
                this.zos = new ZipOutputStream(new FileOutputStream(this.dumpDir + File.separator + "resourcedump.zip"));
            }
            catch (FileNotFoundException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void generate(Context context, OutputStream out, String capabilityList)
            throws SQLException, IOException
        {
            this.generate(context, out, capabilityList, true);

            // incorporate the manifest
            File manifest = new File(this.dumpDir + File.separator + "manifest.xml");
            FileInputStream is = new FileInputStream(manifest);
            this.zos.putNextEntry(new ZipEntry("manifest.xml"));

            // copy one to the other
            byte[] buffer = new byte[102400]; // 100k chunks
            int len = is.read(buffer);
            while (len != -1)
            {
                this.zos.write(buffer, 0, len);
                len = is.read(buffer);
            }
            this.zos.closeEntry();

            this.zos.close();

            // get rid of the left over manifest file
            manifest.delete();
        }

        @Override
        protected URL addBitstream(Bitstream bitstream, Item item, List<Collection> collections, ResourceSyncDocument rl)
        {
            URL url = super.addBitstream(bitstream, item, collections, rl);
            String itempath = item.getHandle().replace("/", "_");
            String filepath = Integer.toString(bitstream.getSequenceID()) + "_" + bitstream.getName();
            String dumppath = "/resources/" + itempath + "/" + filepath;
            url.setPath(dumppath);

            // now actually get the bitstream and stick it in the directory
            try
            {
                // ensure that the parent directory exists
                // String parent = this.dumpDir + File.separator + "resources" + File.separator + itempath;
                //File parentFile = new File(parent);
                //parentFile.mkdirs();
                ZipEntry e = new ZipEntry(File.separator + "resources" + File.separator + itempath + File.separator + filepath);
                this.zos.putNextEntry(e);

                // get the input and output streams
                // String path = parent + File.separator + filepath;
                // FileOutputStream os = new FileOutputStream(new File(path));
                InputStream is = bitstream.retrieve();

                // copy one to the other
                byte[] buffer = new byte[102400]; // 100k chunks
                int len = is.read(buffer);
                while (len != -1)
                {
                    this.zos.write(buffer, 0, len);
                    len = is.read(buffer);
                }

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

            return url;
        }

        @Override
        protected URL addMetadata(Item item, MetadataFormat format, List<Bitstream> describes, List<Collection> collections, ResourceSyncDocument rl)
        {
            URL url = super.addMetadata(item, format, describes, collections, rl);
            String itempath = item.getHandle().replace("/", "_");
            String filepath = format.getPrefix();
            String dumppath = "/resources/" + itempath + "/" + filepath;
            url.setPath(dumppath);

            // now actually get the metadata export and stick it in the directory
            try
            {
                ZipEntry e = new ZipEntry(File.separator + "resources" + File.separator + itempath + File.separator + filepath);
                this.zos.putNextEntry(e);

                // get the input and output streams
                // String path = parent + File.separator + filepath;
                // FileOutputStream os = new FileOutputStream(new File(path));
                // get the dissemination crosswalk for this prefix and get the element for the object
                DisseminationCrosswalk dc = (DisseminationCrosswalk) PluginManager.getNamedPlugin(DisseminationCrosswalk.class, format.getPrefix());
                Element element = dc.disseminateElement(item);

                // serialise the element out to the zip output stream
                element.detach();
                Document doc = new Document(element);
                XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
                out.output(doc, this.zos);

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
}
