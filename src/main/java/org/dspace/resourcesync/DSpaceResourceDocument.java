package org.dspace.resourcesync;

import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.openarchives.resourcesync.ResourceSync;
import org.openarchives.resourcesync.ResourceSyncDocument;
import org.openarchives.resourcesync.URL;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DSpaceResourceDocument
{
    protected List<String> exposeBundles = null;
    protected List<String> mdFormats = null;
    protected Map<String, String> formatType = null;

    public DSpaceResourceDocument()
    {
        // get all the configuration
        this.exposeBundles = this.getBundlesToExpose();
        this.mdFormats = this.getMetadataFormats();
        this.formatType = this.getFormatTypes();
    }

    public DSpaceResourceDocument(List<String> exposeBundles, List<String> mdFormats, Map<String, String> formatType)
    {
        this.exposeBundles = exposeBundles;
        this.mdFormats = mdFormats;
        this.formatType = formatType;
    }

    protected void addResources(Item item, ResourceSyncDocument rl)
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

    protected URL addBitstream(Bitstream bitstream, Item item, List<Collection> collections, ResourceSyncDocument rl)
    {
        URL bs = new URL();

        bs.setLoc(this.getBitstreamUrl(item, bitstream));
        // bs.setLastModified(); // last modified date is not available
        bs.setType(bitstream.getFormat().getMIMEType());
        bs.setLength(bitstream.getSize());

        for (String format : this.mdFormats)
        {
            bs.addLn(ResourceSync.REL_DESCRIBED_BY, this.getMetadataUrl(item, format));
        }

        for (Collection collection : collections)
        {
            bs.addLn(ResourceSync.REL_COLLECTION, this.getCollectionUrl(collection));
        }

        rl.addEntry(bs);
        return bs;
    }

    protected URL addMetadata(Item item, String format, List<Bitstream> describes, List<Collection> collections, ResourceSyncDocument rl)
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

        for (Bitstream bs : describes)
        {
            metadata.addLn(ResourceSync.REL_DESCRIBES, this.getBitstreamUrl(item, bs));
        }

        for (Collection collection : collections)
        {
            metadata.addLn(ResourceSync.REL_COLLECTION, this.getCollectionUrl(collection));
        }

        rl.addEntry(metadata);
        return metadata;
    }

    protected List<String> getBundlesToExpose()
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

    protected List<String> getMetadataFormats()
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

    protected Map<String, String> getFormatTypes()
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

    protected String getMetadataChangeFreq()
    {
        String cf = ConfigurationManager.getProperty("resourcesync", "metadata.change-freq");
        if (cf == null || "".equals(cf))
        {
            return null;
        }
        return cf;
    }

    protected String getBitstreamChangeFreq()
    {
        String cf = ConfigurationManager.getProperty("resourcesync", "bitstream.change-freq");
        if (cf == null || "".equals(cf))
        {
            return null;
        }
        return cf;
    }

    protected String getMetadataUrl(Item item, String format)
    {
        String baseUrl = ConfigurationManager.getProperty("resourcesync", "base-url");
        String handle = item.getHandle();
        String url = baseUrl + "/" + handle + "?format=" + format;
        return url;
    }

    protected String getBitstreamUrl(Item item, Bitstream bitstream)
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

    protected String getCollectionUrl(Collection collection)
    {
        String handle = collection.getHandle();
        String base = ConfigurationManager.getProperty("dspace.url");
        return base + "/" + handle;
    }
}
