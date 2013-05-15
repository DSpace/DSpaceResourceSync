package org.dspace.resourcesync;

public class MetadataFormat
{
    private String prefix;
    private String namespace;
    private String mimetype;

    public MetadataFormat(String prefix, String namespace, String mimetype)
    {
        this.prefix = prefix;
        this.namespace = namespace;
        this.mimetype = mimetype;
    }

    public String getPrefix()
    {
        return prefix;
    }

    public void setPrefix(String prefix)
    {
        this.prefix = prefix;
    }

    public String getNamespace()
    {
        return namespace;
    }

    public void setNamespace(String namespace)
    {
        this.namespace = namespace;
    }

    public String getMimetype()
    {
        return mimetype;
    }

    public void setMimetype(String mimetype)
    {
        this.mimetype = mimetype;
    }
}
