package org.dspace.resourcesync;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.openarchives.resourcesync.CapabilityList;
import org.openarchives.resourcesync.ResourceSync;

import java.io.IOException;
import java.io.OutputStream;

public class DSpaceCapabilityList
{
    private Context context;
    private String describedBy;
    private UrlManager um;
    private boolean resourceList;
    private boolean changeListArchive;
    private boolean resourceDump;
    private boolean changeList;
    private String latestChangeList;

    public DSpaceCapabilityList(Context context, boolean resourceList, boolean changeListArchive, boolean resourceDump, boolean changeList, String latestChangeList)
    {
        this.context = context;
        this.describedBy = ConfigurationManager.getProperty("resourcesync", "capabilitylist.described-by");
        if ("".equals(this.describedBy))
        {
            this.describedBy = null;
        }
        this.um = new UrlManager();
        this.resourceList = resourceList;
        this.changeListArchive = changeListArchive;
        this.resourceDump = resourceDump;
        this.changeList = changeList;
        this.latestChangeList = latestChangeList;
    }

    public void serialise(OutputStream out)
            throws IOException
    {
        String rlUrl = this.resourceList ? this.um.resourceList() : null;
        String claUrl = this.changeListArchive ? this.um.changeListArchive() : null;
        String rdUrl = this.resourceDump ? this.um.resourceDump() : null;
        String rsdUrl = this.um.resourceSyncDescription();

        CapabilityList cl = new CapabilityList(describedBy, null);
        if (rlUrl != null)
        {
            cl.setResourceList(rlUrl);
        }
        if (claUrl != null)
        {
            cl.setChangeListArchive(claUrl);
        }
        if (rdUrl != null)
        {
            cl.setResourceDump(rdUrl);
        }
        if (rsdUrl != null)
        {
            cl.addLn(ResourceSync.REL_RESOURCESYNC, rsdUrl);
        }

        if (this.changeList && this.latestChangeList != null)
        {
            cl.setChangeList(this.latestChangeList);
        }

        cl.serialise(out);
    }
}
