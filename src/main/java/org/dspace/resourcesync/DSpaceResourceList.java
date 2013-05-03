package org.dspace.resourcesync;

import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.ItemIterator;
import org.dspace.core.Context;
import org.openarchives.resourcesync.ResourceList;
import org.openarchives.resourcesync.ResourceSyncDocument;
import org.openarchives.resourcesync.URL;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class DSpaceResourceList extends DSpaceResourceDocument
{
    protected String metadataChangeFreq = null;
    protected String bitstreamChangeFreq = null;

    public DSpaceResourceList()
    {
        super();
        this.metadataChangeFreq = this.getMetadataChangeFreq();
        this.bitstreamChangeFreq = this.getBitstreamChangeFreq();
    }

    public DSpaceResourceList(List<String> exposeBundles, List<String> mdFormats, Map<String, String> formatType,
                                String mdChangeFreq, String bitstreamChangeFreq)
    {
        super(exposeBundles, mdFormats, formatType);
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

    @Override
    protected URL addBitstream(Bitstream bitstream, Item item, List<Collection> collections, ResourceSyncDocument rl)
    {
        URL url = super.addBitstream(bitstream, item, collections, rl);
        url.setChangeFreq(this.bitstreamChangeFreq);
        return url;
    }

    @Override
    protected URL addMetadata(Item item, String format, List<Bitstream> describes, List<Collection> collections, ResourceSyncDocument rl)
    {
        URL url = super.addMetadata(item, format, describes, collections, rl);
        if (this.metadataChangeFreq != null)
        {
            url.setChangeFreq(this.metadataChangeFreq);
        }
        return url;
    }
}
