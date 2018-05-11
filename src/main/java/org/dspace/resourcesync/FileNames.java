/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree
 */
package org.dspace.resourcesync;

import java.io.File;

/**
 * @author Richard Jones
 *
 */
public class FileNames
{
    public static String resourceSyncDocument = "resourcesync.xml";
    public static String resourceList = "resourcelist.xml";
    public static String resourceDumpZip = "resourcedump.zip";
    public static String resourceDump = "resourcedump.xml";
    public static String capabilityList = "capabilitylist.xml";
    public static String changeListArchive = "changelistarchive.xml";
    public static String resourceDumpManifest = "manifest.xml";
    public static String dumpResourcesDir = "resources";

    public static String changeList(String dateString)
    {
        return "changelist_" + dateString + ".xml";
    }

    public static boolean isChangeList(File file)
    {
        return file.getName().startsWith("changelist_");
    }

    public static String changeListDate(File file)
    {
        return FileNames.changeListDate(file.getName());
    }

    public static String changeListDate(String filename)
    {
        int start = "changelist_".length(); // 11
        int end = filename.length() - ".xml".length();
        String dr = filename.substring(start, end);
        return dr;
    }
}
