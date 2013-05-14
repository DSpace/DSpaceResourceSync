package org.dspace.resourcesync;

import org.dspace.core.ConfigurationManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ResourceSyncServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException
    {
        resp.setCharacterEncoding("UTF-8");

        // basically this is a glorified file server.  The only thing that it has to do
        // which is hard is serve metadata files, which means it needs to integrate with
        // a crosswalk

        // detect if this is a metadata crosswalk
        if (req.getParameter("format") != null)
        {
            // this is a metadata crosswalk
        }
        else
        {
            // this is a standard resourcesync document serve
            String dir = ConfigurationManager.getProperty("resourcesync", "resourcesync.dir");
            if (dir == null)
            {
                resp.sendError(404);
                return;
            }
            String document = req.getPathInfo();
            if (document.startsWith("/"))
            {
                document = document.substring(1);
            }
            String filepath = dir + File.separator + document;

            File f = new File(filepath);
            if (!f.exists() || !f.isFile())
            {
                resp.sendError(404);
                return;
            }

            resp.setContentType("application/xml");

            InputStream is = new FileInputStream(f);
            OutputStream os = resp.getOutputStream();

            byte[] buffer = new byte[102400]; // 100k chunks
            int len = is.read(buffer);
            while (len != -1)
            {
                os.write(buffer, 0, len);
                len = is.read(buffer);
            }

            return;
        }
    }

}
