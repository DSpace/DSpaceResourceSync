package org.dspace.resourcesync;

import org.dspace.core.Context;
import org.openarchives.resourcesync.ChangeListArchive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;

public class DSpaceChangeListArchive
{
    public void generate(Context context, OutputStream out, Map<String, Date> changeLists, String capabilityList, ChangeListArchive cla)
            throws IOException
    {
        if (cla == null)
        {
            cla = new ChangeListArchive(capabilityList);
        }

        for (String loc : changeLists.keySet())
        {
            cla.addChangeList(loc, changeLists.get(loc));
        }

        cla.serialise(out);
    }

    public void generate(Context context, OutputStream out, Map<String, Date> changeLists, String capabilityList)
            throws IOException
    {
        this.generate(context, out, changeLists, capabilityList, null);
    }

    public ChangeListArchive parse(InputStream in)
    {
        ChangeListArchive cla = new ChangeListArchive(in);
        return cla;
    }
}
