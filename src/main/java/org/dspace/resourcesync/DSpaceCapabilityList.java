package org.dspace.resourcesync;

import org.dspace.core.Context;
import org.openarchives.resourcesync.CapabilityList;

import java.io.IOException;
import java.io.OutputStream;

public class DSpaceCapabilityList
{
    public void generate(Context context, OutputStream out, String describedBy, String resourceList, String changeListArchive, String resourceDump)
            throws IOException
    {
        CapabilityList cl = new CapabilityList(describedBy, null);
        if (resourceList != null)
        {
            cl.setResourceList(resourceList);
        }
        if (changeListArchive != null)
        {
            cl.setChangeList(changeListArchive);
        }
        if (resourceDump != null)
        {
            cl.setResourceDump(resourceDump);
        }
        cl.serialise(out);
    }
}
