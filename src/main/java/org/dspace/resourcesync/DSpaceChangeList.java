package org.dspace.resourcesync;

import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.search.Harvest;
import org.dspace.search.HarvestedItemInfo;
import org.openarchives.resourcesync.ChangeList;
import org.openarchives.resourcesync.ResourceSync;
import org.openarchives.resourcesync.ResourceSyncDocument;
import org.openarchives.resourcesync.URL;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class DSpaceChangeList extends DSpaceResourceDocument
{
    private boolean includeRestricted = false;

    public DSpaceChangeList()
    {
        super();
        this.includeRestricted = ConfigurationManager.getBooleanProperty("resourcesync", "changelist.include-restricted");
    }

    public DSpaceChangeList(List<String> exposeBundles, List<String> mdFormats, Map<String, String> formatType,
                            boolean includeRestricted)
    {
        super(exposeBundles, mdFormats, formatType);
        this.includeRestricted = includeRestricted;
    }

    public void generate(Context context, OutputStream out, Date from, Date to, String capabilityList, String changeListArchive)
            throws SQLException, ParseException, IOException
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

        // explicitly declare our arguments, for readability
        DSpaceObject scope = null;
        String fromDate = from == null ? null : sdf.format(from);
        String toDate = to == null ? null : sdf.format(to);
        int offset = 0;
        int limit = 0;
        boolean getItems = true;
        boolean getCollections = false;
        boolean getWithdrawn = true;
        boolean getRestricted = this.includeRestricted;

        List<HarvestedItemInfo> his = Harvest.harvest(context, scope, fromDate, toDate,
                                            offset, limit, getItems, getCollections, getWithdrawn, getRestricted);

        ChangeList cl = new ChangeList(capabilityList);
        if (changeListArchive != null)
        {
            cl.inChangeListArchive(changeListArchive);
        }
        for (HarvestedItemInfo hi : his)
        {
            Item item = hi.item;
            this.addResources(item, cl);
        }

        // FIXME: what is the real date for a changelist, is it today or the "to" date
        cl.setLastModified(new Date());
        cl.serialise(out);
    }

    @Override
    protected URL addBitstream(Bitstream bitstream, Item item, List<Collection> collections, ResourceSyncDocument rl)
    {
        URL url = super.addBitstream(bitstream, item, collections, rl);
        // we can't ever know if an item is created in DSpace, as no such metadata exists
        // all we can say is that it was updated
        String change = ResourceSync.CHANGE_UPDATED;
        if (item.isWithdrawn())
        {
            // if the item is withdrawn, we say that the change that happened to it was
            // that it was deleted
            change = ResourceSync.CHANGE_DELETED;
        }
        url.setChange(change);
        return url;
    }

    @Override
    protected URL addMetadata(Item item, String format, List<Bitstream> describes, List<Collection> collections, ResourceSyncDocument rl)
    {
        URL url = super.addMetadata(item, format, describes, collections, rl);
        String change = ResourceSync.CHANGE_UPDATED;
        if (item.isWithdrawn())
        {
            // if the item is withdrawn, we say that the change that happened to it was
            // that it was deleted
            change = ResourceSync.CHANGE_DELETED;
        }
        url.setChange(change);
        return url;
    }


}
