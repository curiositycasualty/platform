/*
 * Copyright (c) 2006-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.view;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.security.User;

import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: Mark Igra
 * Date: Jun 16, 2006
 * Time: 9:22:37 AM
 */
public class NavTreeManager
{
    private static final Logger _log = Logger.getLogger(NavTreeManager.class);
    private static final String EXPAND_CONTAINERS_KEY = NavTreeManager.class.getName() + "/expandedContainers";
    private static final String CACHE_PREFIX = NavTreeManager.class.getName() + "/";
    private static final String NULL_MARKER = "__null marker representing the root__";   // ConcurrentHashMap does not support null keys

    private static final StringKeyCache<Collapsible> NAV_TREE_CACHE = CacheManager.getSharedCache();
    
    public static void expandCollapsePath(ViewContext viewContext, String navTreeId, @Nullable String path, boolean collapse)
    {
        if (null == navTreeId)
            return;
        
        _log.debug("Expand/Collapse path navTreeId = " + navTreeId + " path=" +  path + " collapse= " + collapse);
        saveExpandState(viewContext, navTreeId, path, collapse);
    }

    private static void saveExpandState(ViewContext viewContext, String navTreeId, @Nullable String path, boolean collapse)
    {
        path = null == path ? NULL_MARKER : path;
        Set<String> expandedPaths = getExpandedPaths(viewContext, navTreeId);

        if (collapse)
            expandedPaths.remove(path);
        else
            expandedPaths.add(path);
    }

    private static void collapseAll(Collapsible tree)
    {
        tree.setCollapsed(true);

        if (tree.getChildren() != null)
        {
            for (Collapsible child : tree.getChildren())
                collapseAll(child);
        }
    }

    public static Set<String> getExpandedPaths(ViewContext viewContext, String navTreeId)
    {
        //Each navtreeid has a set of expanded paths...
        HttpSession session = viewContext.getRequest().getSession(true);
        Map<String, Set<String>> treeMap = (Map<String, Set<String>>) session.getAttribute(EXPAND_CONTAINERS_KEY);

        if (null == treeMap)
        {
            //FIX: 5389
            //It's possible to get two requests on two different threads under
            //the same session (e.g., nav tree embedded in a wiki web part). So these
            //collections be concurrent.
            treeMap = new ConcurrentHashMap<String, Set<String>>();
            // Don't track. These stick around in session, so are new?
            // assert MemTracker.put(treeMap);
            session.setAttribute(EXPAND_CONTAINERS_KEY, treeMap);
        }

        Set<String> expandedPaths = treeMap.get(navTreeId);

        if (null == expandedPaths)
        {
            expandedPaths = new ConcurrentHashSet<String>();
            //assert MemTracker.put(expandedPaths);
            treeMap.put(navTreeId, expandedPaths);
        }

        return expandedPaths;
    }

    private static void _expandCollapseSubtree(Collapsible tree, @NotNull String path, boolean collapse)
    {
        @SuppressWarnings({"StringEquality"})
        Collapsible subtree = tree.findSubtree(NULL_MARKER == path ? null : path);

        if (null != subtree)
            subtree.setCollapsed(collapse);
    }

    /*
     * Grab the expanded state for this tree from the session and apply it to the tree.
     */
    public static void applyExpandState(Collapsible tree, ViewContext viewContext)
    {
        Set<String> expandedPaths = getExpandedPaths(viewContext, tree.getId());

        for (String p : expandedPaths)
            _expandCollapseSubtree(tree, p, false);
    }

    /*
     * Cache a tree for the current user. User may be null
     */
    public static void cacheTree(Collapsible navTree, @Nullable User user)
    {
        collapseAll(navTree);
        NAV_TREE_CACHE.put(getCacheKey(navTree.getId(), user), navTree, CacheManager.MINUTE);
    }

    /*
     * Uncache a tree with the id given. Also uncaches any per-user cached trees.
     */
    public static void uncacheTree(String treeId)
    {
        NAV_TREE_CACHE.removeUsingPrefix(getCacheKey(treeId, null));
    }

    public static void uncacheTree(String treeId, User user)
    {
        NAV_TREE_CACHE.remove(getCacheKey(treeId, user));
    }

    public static Collapsible getFromCache(String navTreeId, ViewContext viewContext)
    {
        Collapsible cached = NAV_TREE_CACHE.get(getCacheKey(navTreeId, viewContext.getUser()));
        if (cached != null)
        {
            collapseAll(cached);
            applyExpandState(cached, viewContext);
        }
        return cached;
    }

    private static String getCacheKey(String navTreeId, @Nullable User user)
    {
        assert null != navTreeId;
        String key;
        if (null != user)
        {
            key = navTreeId + "/user=" + user.getUserId();

            // NavTree for user being impersonated will be different per impersonating user per project
            if (user.isImpersonated())
            {
                key = key + "/impersonatingUser=" + user.getImpersonatingUser().getUserId();

                if (null != user.getImpersonationProject())
                    key = key + "/impersonationProject=" + user.getImpersonationProject().getId();
            }
        }
        else
        {
            key = navTreeId + "/";
        }
        return CACHE_PREFIX + key;
    }

    public static void uncacheAll()
    {
        NAV_TREE_CACHE.removeUsingPrefix(CACHE_PREFIX);
    }
}
