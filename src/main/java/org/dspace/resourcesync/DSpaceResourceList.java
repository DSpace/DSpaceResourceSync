package org.dspace.resourcesync;

import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.ItemIterator;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.openarchives.resourcesync.ResourceList;
import org.openarchives.resourcesync.ResourceSync;
import org.openarchives.resourcesync.URL;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DSpaceResourceList
{
    List<String> exposeBundles = null;
    List<String> mdFormats = null;
    Map<String, String> formatType = null;
    String metadataChangeFreq = null;
    String bitstreamChangeFreq = null;

    public DSpaceResourceList()
    {
        // get all the configuration
        this.exposeBundles = this.getBundlesToExpose();
        this.mdFormats = this.getMetadataFormats();
        this.formatType = this.getFormatTypes();
        this.metadataChangeFreq = this.getMetadataChangeFreq();
        this.bitstreamChangeFreq = this.getBitstreamChangeFreq();
    }

    public DSpaceResourceList(List<String> exposeBundles, List<String> mdFormats, Map<String, String> formatType,
                                String mdChangeFreq, String bitstreamChangeFreq)
    {
        this.exposeBundles = exposeBundles;
        this.mdFormats = mdFormats;
        this.formatType = formatType;
        this.metadataChangeFreq = mdChangeFreq;
        this.bitstreamChangeFreq = bitstreamChangeFreq;
    }

    public void generate(Context context, OutputStream out, String capabilityList)
            throws SQLException, IOException
    {
        ResourceList rl = new ResourceList(capabilityList);

        ItemIterator allItems = Item.findAll(context);

        while (allItems.hasNext())
        {
            Item item = allItems.next();
            this.addResources(item, rl);
        }

        rl.setLastModified(new Date());
        rl.serialise(out);
    }

    private void addResources(Item item, ResourceList rl)
            throws SQLException
    {
        // record all of the bitstreams that we are going to expose
        List<Bitstream> exposed = new ArrayList<Bitstream>();

        // get the collections that the item is part of
        Collection[] collection = item.getCollections();
        List<Collection> clist = Arrays.asList(collection);

        // add all the relevant bitstreams
        for (Bundle bundle : item.getBundles())
        {
            // only expose resources in permitted bundles
            if (!exposeBundles.contains(bundle.getName()))
            {
                continue;
            }

            for (Bitstream bitstream : bundle.getBitstreams())
            {
                this.addBitstream(bitstream, item, clist, rl);
                exposed.add(bitstream);
            }
        }

        // add all the relevant metadata formats
        for (String format : this.mdFormats)
        {
            this.addMetadata(item, format, exposed, clist, rl);
        }
    }

    private void addBitstream(Bitstream bitstream, Item item, List<Collection> collections, ResourceList rl)
    {
        URL bs = new URL();

        bs.setLoc(this.getBitstreamUrl(item, bitstream));
        // bs.setLastModified(); // last modified date is not available
        bs.setType(bitstream.getFormat().getMIMEType());
        bs.setLength(bitstream.getSize());
        bs.setChangeFreq(this.bitstreamChangeFreq);

        for (String format : this.mdFormats)
        {
            bs.addLn(ResourceSync.REL_DESCRIBED_BY, this.getMetadataUrl(item, format));
        }

        for (Collection collection : collections)
        {
            bs.addLn(ResourceSync.REL_COLLECTION, this.getCollectionUrl(collection));
        }

        rl.addUrl(bs);
    }

    private void addMetadata(Item item, String format, List<Bitstream> describes, List<Collection> collections, ResourceList rl)
    {
        URL metadata = new URL();

        // set the metadata url
        metadata.setLoc(this.getMetadataUrl(item, format));
        metadata.addLn(ResourceSync.REL_DESCRIBED_BY, format);

        // technically this only tells us when the item was last updated, not the metadata
        metadata.setLastModified(item.getLastModified());

        // set the type
        String type = this.formatType.get(format);
        if (type != null && !"".equals(type))
        {
            metadata.setType(type);
        }

        if (this.metadataChangeFreq != null)
        {
            metadata.setChangeFreq(this.metadataChangeFreq);
        }

        for (Bitstream bs : describes)
        {
            metadata.addLn(ResourceSync.REL_DESCRIBES, this.getBitstreamUrl(item, bs));
        }

        for (Collection collection : collections)
        {
            metadata.addLn(ResourceSync.REL_COLLECTION, this.getCollectionUrl(collection));
        }

        rl.addUrl(metadata);
    }

    private List<String> getBundlesToExpose()
    {
        List<String> exposeBundles = new ArrayList<String>();
        String cfg = ConfigurationManager.getProperty("resourcesync", "expose-bundles");
        if (cfg == null || "".equals(cfg))
        {
            return exposeBundles;
        }

        String[] bits = cfg.split(",");
        for (String bundle : bits)
        {
            if (!exposeBundles.contains(bundle))
            {
                exposeBundles.add(bundle);
            }
        }
        return exposeBundles;
    }

    private List<String> getMetadataFormats()
    {
        List<String> formats = new ArrayList<String>();
        String cfg = ConfigurationManager.getProperty("resourcesync", "metadata.formats");
        if (cfg == null || "".equals(cfg))
        {
            return formats;
        }

        String[] bits = cfg.split(",");
        for (String format : bits)
        {
            if (!formats.contains(format))
            {
                formats.add(format);
            }
        }
        return formats;
    }

    private Map<String, String> getFormatTypes()
    {
        Map<String, String> formatTypes = new HashMap<String, String>();
        for (String format : this.mdFormats)
        {
            String cfg = ConfigurationManager.getProperty("resourcesync", "metadata.type." + format);
            if (cfg == null || "".equals(cfg))
            {
                continue;
            }
            formatTypes.put(format, cfg.trim());
        }
        return formatTypes;
    }

    private String getMetadataChangeFreq()
    {
        String cf = ConfigurationManager.getProperty("resourcesync", "metadata.change-freq");
        if (cf == null || "".equals(cf))
        {
            return null;
        }
        return cf;
    }

    private String getBitstreamChangeFreq()
    {
        String cf = ConfigurationManager.getProperty("resourcesync", "bitstream.change-freq");
        if (cf == null || "".equals(cf))
        {
            return null;
        }
        return cf;
    }

    private String getMetadataUrl(Item item, String format)
    {
        String baseUrl = ConfigurationManager.getProperty("resourcesync", "base-url");
        String handle = item.getHandle();
        String url = baseUrl + "/" + handle + "?format=" + format;
        return url;
    }

    private String getBitstreamUrl(Item item, Bitstream bitstream)
    {
        String handle = item.getHandle();
        String bsLink = ConfigurationManager.getProperty("dspace.url");

        if (handle != null && !"".equals(handle))
        {
            bsLink = bsLink + "/bitstream/" + handle + "/" + bitstream.getSequenceID() + "/" + bitstream.getName();
        }
        else
        {
            bsLink = bsLink + "/retrieve/" + bitstream.getID() + "/" + bitstream.getName();
        }

        return bsLink;
    }

    private String getCollectionUrl(Collection collection)
    {
        String handle = collection.getHandle();
        String base = ConfigurationManager.getProperty("dspace.url");
        return base + "/" + handle;
    }
}
