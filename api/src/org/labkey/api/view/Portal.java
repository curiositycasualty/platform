/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.labkey.common.util.Pair;
import org.labkey.api.data.*;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.util.Cache;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.springframework.web.servlet.ModelAndView;

import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.*;


public class Portal
{
    static WebPartBeanLoader _factory = new WebPartBeanLoader();

    private static final String _portalPrefix = "Portal/";
    private static final String SCHEMA_NAME = "portal";

    public static final int MOVE_UP = 0;
    public static final int MOVE_DOWN = 1;

    private static HashMap<String, WebPartFactory> _viewMap = null;
    private static MultiValueMap _regionMap = null;


    public static String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public static SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public static TableInfo getTableInfoPortalWebParts()
    {
        return getSchema().getTable("PortalWebParts");
    }

    //void wantsToDelete(Container c, List<String> messages);
    public void containerCreated(Container c)
    {

    }

    public static void containerDeleted(Container c)
    {
        Cache.getShared().removeUsingPrefix(_portalPrefix);
        try
        {
            Table.delete(getTableInfoPortalWebParts(), new SimpleFilter("PageId", c.getId()));

        } catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

    }

    public static class WebPart implements Serializable
    {
        String pageId;
        int index = 999;
        String name;
        String location = HttpView.BODY;
        boolean permanent;
        Map<String, String> propertyMap = new HashMap<String, String>();
        String properties = null;

        static
        {
            BeanObjectFactory.Registry.register(WebPart.class, new WebPartBeanLoader());
        }

        public WebPart()
        {

        }

        public WebPart(WebPart copyFrom)
        {
            pageId = copyFrom.pageId;
            index = copyFrom.index;
            name = copyFrom.name;
            location = copyFrom.location;
            permanent = copyFrom.permanent;
            setProperties(copyFrom.properties);
        }

        public String getPageId()
        {
            return pageId;
        }


        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }


        public int getIndex()
        {
            return index;
        }


        public void setIndex(int index)
        {
            this.index = index;
        }


        public String getName()
        {
            return name;
        }


        public void setName(String name)
        {
            this.name = name;
        }


        public String getLocation()
        {
            return location;
        }


        public void setLocation(String location)
        {
            this.location = location;
        }


        public Map<String, String> getPropertyMap()
        {
            return propertyMap;
        }


        public void setProperty(String k, String v)
        {
            propertyMap.put(k, v);
        }


        public String getProperties()
        {
            return PageFlowUtil.toQueryString(propertyMap.entrySet());
        }


        public void setProperties(String query)
        {
            Pair<String, String>[] props = PageFlowUtil.fromQueryString(query);
            for (Pair<String, String> prop : props)
                setProperty(prop.first, prop.second);
        }

        public boolean isPermanent()
        {
            return permanent;
        }

        public void setPermanent(boolean permanent)
        {
            this.permanent = permanent;
        }

        public ActionURL getCustomizePostURL(Container container)
        {
            ActionURL ret = new ActionURL("Project", "customizeWebPart", container.getPath());
            ret.addParameter("pageId", getPageId());
            ret.addParameter("index", Integer.toString(getIndex()));
            return ret;
        }
    }


    static int _toInt(Integer i, int d)
    {
        return null == i ? d : i;
    }


    public static class WebPartBeanLoader extends BeanObjectFactory<WebPart>
    {
        public WebPartBeanLoader()
        {
            super(WebPart.class);
        }

        protected void fixupBean(WebPart part)
        {
            if (null == part.location || part.location.length() == 0)
                part.location = HttpView.BODY;
        }
    }


    public static WebPart[] getParts(String id)
    {
        return getParts(id, false);
    }


    public static WebPart[] getParts(String id, boolean force)
    {
        String key = _portalPrefix + id;
        WebPart[] parts;

        if (!force)
        {
            parts = (WebPart[]) Cache.getShared().get(key);
            if (null != parts)
                return parts;
        }

        Filter filter = new SimpleFilter("PageId", id);
        try
        {
            parts = Table.select(getTableInfoPortalWebParts(), Table.ALL_COLUMNS, filter, new Sort(getTableInfoPortalWebParts().getColumn("Index").getSelectName()), WebPart.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        Cache.getShared().put(key, parts, Cache.MINUTE);
        return parts;
    }


    public static WebPart getPart(String pageId, int index)
    {
        return Table.selectObject(getTableInfoPortalWebParts(), new Object[]{pageId, index}, WebPart.class);
    }


    public static void updatePart(User u, WebPart part) throws SQLException
    {
        Table.update(u, getTableInfoPortalWebParts(), part, new Object[]{part.getPageId(), part.getIndex()}, null);
        _clearCache(part.getPageId());
    }

    /**
     * Add a web part to the container at the end of the list
     */
    public static WebPart addPart(Container c, WebPartFactory desc, String location)
            throws SQLException
    {
        return addPart(c, desc, location, -1);
    }

    /**
     * Add a web part to the container at the end of the list, with properties
     */
    public static WebPart addPart(Container c, WebPartFactory desc, String location, Map<String, String> properties)
            throws SQLException
    {
        return addPart(c, desc, location, -1, properties);
    }

    /**
     * Add a web part to the container at the specified index
     */
    public static WebPart addPart(Container c, WebPartFactory desc, String location, int partIndex)
            throws SQLException
    {
        return addPart(c, desc, location, partIndex, null);
    }

    /**
     * Add a web part to the container at the specified index, with properties
     */
    public static WebPart addPart(Container c, WebPartFactory desc, String location, int partIndex, Map<String, String> properties)
            throws SQLException
    {
        WebPart[] parts = getParts(c.getId());

        WebPart newPart = new Portal.WebPart();
        newPart.setPageId(c.getId());
        newPart.setName(desc.getName());
        newPart.setIndex(partIndex >= 0 ? partIndex : parts.length);
        if (location == null)
        {
            newPart.setLocation(desc.getDefaultLocation());
        }
        else
        {
            newPart.setLocation(location);
        }

        if (properties != null)
        {
            for (Map.Entry prop : properties.entrySet())
            {
                String propName = prop.getKey().toString();
                String propValue = prop.getValue().toString();
                newPart.setProperty(propName, propValue);
            }
        }

        if (parts == null)
            parts = new Portal.WebPart[]{newPart};
        else
        {
            Portal.WebPart[] partsNew = new Portal.WebPart[parts.length + 1];
            int iNext = 0;
            for (final WebPart currentPart : parts)
            {
                if (iNext == newPart.getIndex())
                    partsNew[iNext++] = newPart;
                final int iPart = currentPart.getIndex();
                if (iPart > newPart.getIndex())
                    currentPart.setIndex(iPart + 1);
                partsNew[iNext++] = currentPart;
            }
            if (iNext == newPart.getIndex())
                partsNew[iNext++] = newPart;
            parts = partsNew;
        }

        Portal.saveParts(c.getId(), parts);

//        Set<Module> activeModules = new HashSet<Module>(c.getActiveModules());
//        if (!activeModules.contains(desc.getModule()))
//        {
//            activeModules.add(desc.getModule());
//            c.setActiveModules(activeModules);
//        }
//
        return newPart;
    }

    public static void saveParts(String id, WebPart[] parts)
    {
        // make sure indexes are unique
        Arrays.sort(parts, new Comparator()
        {
            public int compare(Object o, Object o1)
            {
                return ((WebPart) o).index - ((WebPart) o1).index;
            }
        });

        for (int i = 0; i < parts.length; i++)
        {
            WebPart part = parts[i];
            part.index = i + 1;
            part.pageId = id;
        }

        try
        {
            getSchema().getScope().beginTransaction();
            Table.delete(getTableInfoPortalWebParts(), new SimpleFilter("PageId", id));
            Table.execute(getSchema(), "DELETE FROM " + getTableInfoPortalWebParts() + " WHERE PageId = ?", new Object[]{id});
            for (WebPart part1 : parts) {
                Map m = _factory.toMap(part1, null);
                Table.insert(null, getTableInfoPortalWebParts(), m);
            }
            getSchema().getScope().commitTransaction();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            getSchema().getScope().closeConnection();
        }
        _clearCache(id);
    }


    static void _clearCache(String id)
    {
        Cache.getShared().remove(_portalPrefix + id);
    }


    public static class AddWebParts
    {
        public String pageId;
        public String location;
        public List<String> webPartNames;
        public List<String> rightWebPartNames;
    }

    private static void addCustomizeDropdowns(Container c, HttpView template, String id, Collection occupiedLocations)
    {
        Set<String> regionNames = getRegionMap().keySet();
        boolean rightEmpty = !occupiedLocations.contains("right");
        AddWebParts bodyAddPart = null;
        List<String> rightParts = null;
        
        for (String regionName : regionNames)
        {
            List<String> partsToAdd = Portal.getPartsToAdd(c, regionName);

            if ("right".equals(regionName) && rightEmpty)
                rightParts = partsToAdd;
            else
            {
                //TODO: Make addPartView a real class & move to ProjectController
                AddWebParts addPart = new AddWebParts();
                addPart.pageId = id;
                addPart.location = regionName;
                addPart.webPartNames = partsToAdd;
                WebPartView addPartView = new JspView<AddWebParts>("/org/labkey/api/view/addWebPart.jsp", addPart);
                addPartView.setFrame(WebPartView.FrameType.NONE);

                // save these off in case we have to re-shuffle due to an empty right region:
                if (HttpView.BODY.equals(regionName))
                    bodyAddPart = addPart;

                addViewToRegion(template, regionName, addPartView);
            }
        }
        if (rightEmpty && bodyAddPart != null && rightParts != null)
            bodyAddPart.rightWebPartNames = rightParts;
    }

    private static void addViewToRegion(HttpView template, String regionName, HttpView view)
    {
        //place
        ModelAndView region = template.getView(regionName);
        if (null == region)
        {
            region = new VBox();
            template.setView(regionName, region);
        }
        if (!(region instanceof VBox))
        {
            region = new VBox(region);
            template.setView(regionName, region);
        }
        ((VBox) region).addView(view);
    }

    /*
               PageId ENTITYID NOT NULL,
               [Index] INT NOT NULL,
               Name VARCHAR(64),
               Location VARCHAR(16),	-- 'body', 'left', 'right'

               Properties VARCHAR(4000),	-- url encoded properties	*/

    public static void populatePortalView(ViewContext context, String id, HttpView template, String contextPath)
            throws Exception
    {
        WebPart[] parts = getParts(id, false);
        Container c = context.getContainer();
        User u = context.getUser();
        boolean canCustomize = c.hasPermission(u, ACL.PERM_ADMIN) && context.isAdminMode();

        MultiMap locationMap = getPartsByLocation(parts);
        Collection locations = locationMap.keySet();

        for (Object location1 : locations)
        {
            String location = (String) location1;

            List partsForLocation = (List) locationMap.get(location);
            for (int i = 0; i < partsForLocation.size(); i++)
            {
                WebPart part = (WebPart) partsForLocation.get(i);

                //instantiate
                WebPartFactory desc = Portal.getPortalPart(part.getName());
                if (null == desc)
                    continue;

                WebPartView view = desc.getWebPartViewSafe(context, part);
                if (null == view)
                    continue;
                view.prepare(view.getModelBean());

                NavTree navTree = view.getCustomizeLinks();
                if (canCustomize)
                {
                    if (desc.isEditable())
                        navTree.addChild("Customize Web Part", getCustomizeURL(context, part), contextPath + "/_images/partedit.gif");
                }
                if (view.getTitleHref() != null)
                {
                    navTree.addChild("Maximize", view.getTitleHref(), contextPath + "/_images/partmaximize.gif");
                }
                if (canCustomize)
                {
                    if (i > 0)
                        navTree.addChild("Move Up", getMoveURL(context, part, MOVE_UP), contextPath + "/_images/partup.gif");
                    else
                        navTree.addChild("", "", contextPath + "/_images/partupg.gif");
                    if (i < partsForLocation.size() - 1)
                        navTree.addChild("Move Down", getMoveURL(context, part, MOVE_DOWN), contextPath + "/_images/partdown.gif");
                    else
                        navTree.addChild("", "", contextPath + "/_images/partdowng.gif");
                }
                if (canCustomize)
                {
                    if (!part.isPermanent())
                        navTree.addChild("Remove From Page", getDeleteURL(context, part), contextPath + "/_images/partdelete.gif");
                }

                addViewToRegion(template, location, view);
            }
        }

        if (canCustomize)
            addCustomizeDropdowns(context.getContainer(), template, id, locations);
    }


    public static String getCustomizeURL(ViewContext context, Portal.WebPart webPart)
    {
        ActionURL helper = context.cloneActionURL();
        helper.setAction("customizeWebPart");
        helper.deleteParameters();
        helper.addParameter("pageId", webPart.getPageId());
        helper.addParameter("index", String.valueOf(webPart.getIndex()));
        return helper.getLocalURIString();
    }


    public static String getMoveURL(ViewContext context, Portal.WebPart webPart, int direction)
    {
        ActionURL helper = context.cloneActionURL();
        helper.setAction("moveWebPart");
        helper.deleteParameters();
        helper.addParameter("pageId", webPart.getPageId());
        helper.addParameter("index", String.valueOf(webPart.getIndex()));
        helper.addParameter("direction", String.valueOf(direction));
        return helper.getLocalURIString();
    }


    public static String getDeleteURL(ViewContext context, Portal.WebPart webPart)
    {
        ActionURL helper = context.cloneActionURL();
        helper.setAction("deleteWebPart");
        helper.deleteParameters();
        helper.addParameter("pageId", webPart.getPageId());
        helper.addParameter("index", String.valueOf(webPart.getIndex()));
        return helper.getLocalURIString();
    }


    public static MultiMap getPartsByLocation(WebPart[] parts)
    {
        MultiMap multiMap = new MultiValueMap();

        for (WebPart part : parts)
        {
            if (null == part.getName() || 0 == part.getName().length())
                continue;
            String location = part.getLocation();
            multiMap.put(location, part);
        }

        return multiMap;
    }


    public static class BrokenWebPart extends WebPartView
    {
        public BrokenWebPart(String name)
        {
            setTitle("Web part not found: " + name);
            addObject("name", name);
        }

        @Override
        public void renderView(Object model, PrintWriter out)
        {
            out.print("error loading web part: " + getViewContext().get("name"));
        }
    }


    public static WebPartFactory getPortalPart(String name)
    {
        return getViewMap().get(name);
    }

    public static WebPartFactory getPortalPartCaseInsensitive(String name)
    {
        CaseInsensitiveHashMap<WebPartFactory> viewMap = new CaseInsensitiveHashMap<WebPartFactory>(getViewMap());
        return viewMap.get(name);
    }

    private static HashMap<String, WebPartFactory> getViewMap()
    {
        if (null == _viewMap)
            initMaps();

        return _viewMap;
    }


    private static MultiMap getRegionMap()
    {
        if (null == _regionMap)
            initMaps();

        return _regionMap;
    }


    private static void initMaps()
    {
        _viewMap = new HashMap<String, WebPartFactory>(20);
        _regionMap = new MultiValueMap();

        List<Module> modules = ModuleLoader.getInstance().getModules();
        for (Module module : modules)
        {
            WebPartFactory[] parts = module.getModuleWebParts();
            if (null == parts)
                continue;
            for (WebPartFactory webpart : parts)
            {
                _viewMap.put(webpart.name, webpart);
                for (String legacyName : webpart.getLegacyNames())
                {
                    _viewMap.put(legacyName, webpart);
                }
                _regionMap.put(webpart.defaultLocation, webpart.name);
            }
        }

        //noinspection unchecked
        for (String key : (Collection<String>)_regionMap.keySet())
        {
            //noinspection unchecked
            ArrayList<String> list = (ArrayList<String>)_regionMap.getCollection(key);
            Collections.sort(list);
        }
    }

    public static List<String> getPartsToAdd(Container c, String location)
    {
        //TODO: Cache these
        Set<String> webPartNames = new TreeSet<String>();
        for (Module module : ModuleLoader.getInstance().getModules())
        {
            WebPartFactory[] factories = module.getModuleWebParts();
            if (null != factories)
                for (WebPartFactory factory : module.getModuleWebParts())
                    if (factory.isAvailable(c, location))
                        webPartNames.add(factory.getName());
        }
        return new ArrayList<String>(webPartNames);
    }

    public static int purge() throws SQLException
    {
        return ContainerUtil.purgeTable(getTableInfoPortalWebParts(), "PageId");
    }
}
