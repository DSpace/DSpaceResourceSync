package org.dspace.resourcesync;

import org.dspace.core.ConfigurationManager;

public class UrlManager
{
    private String base;

    public UrlManager()
    {
        this.base = ConfigurationManager.getProperty("resourcesync", "base-url");
        if (!this.base.endsWith("/"))
        {
            this.base += "/";
        }
    }

    public String resourceSyncDescription()
    {
        return this.base + FileNames.resourceSyncDocument;
    }

    public String capabilityList()
    {
        return this.base + FileNames.capabilityList;
    }

    public String resourceList()
    {
        return this.base + FileNames.resourceList;
    }

    public String changeListArchive()
    {
        return this.base + FileNames.changeListArchive;
    }

    public String changeList(String filename)
    {
        return this.base + filename;
    }

    public String resourceDump()
    {
        return this.base + FileNames.resourceDump;
    }

    public String resourceDumpZip()
    {
        return this.base + FileNames.resourceDumpZip;
    }
}
