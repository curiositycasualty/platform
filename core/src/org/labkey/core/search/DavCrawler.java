package org.labkey.core.search;

import org.labkey.api.search.SearchService;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.webdav.Resource;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 18, 2009
 * Time: 11:09:03 AM
 */
public class DavCrawler
{
    public static Runnable start(@NotNull SearchService.IndexTask task, String path)
    {
        WebdavResolver res = WebdavService.getResolver();
        if ("/".equals(path))
            path = "/" + WebdavService.getServletPath();
        return start(task,res,Path.parse(path));
    }


    public static Runnable start(@NotNull final SearchService.IndexTask task, final WebdavResolver res, final Path path)
    {
        return new Runnable()
        {
            public void run()
            {
                crawl(task, res, path);
            }
        };
    }


    static void crawl(@NotNull SearchService.IndexTask task, WebdavResolver res, Path path)
    {
        Resource r = res.lookup(path);
        if (null == r)
            return;

        if (r.isFile())
        {
            task.addResource("dav:" + path, SearchService.PRIORITY.background);
        }
        else if (r.isCollection())
        {
            for (Resource child : r.list())
            {
                if (child.isFile())
                    task.addResource("dav:" + child.getPath(), SearchService.PRIORITY.background);
                else if (!skipContainer(child.getName()))
                    task.addRunnable(start(task, res, child.getPath()), SearchService.PRIORITY.crawl);
            }
        }
    }

    static boolean skipContainer(String name)
    {
        // TODO configurable map, patterns
        return ".svn".equals(name);
    }
}