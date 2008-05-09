package org.labkey.api.view;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jan 30, 2007
 * Time: 3:40:04 PM
 */
public class GWTView extends JspView<GWTView.GWTViewBean>
{
    public static final String PROPERTIES_OBJECT_NAME = "LABKEY.GWTProperties";

    public static class CSSStyle
    {
        private String _name;
        private Map<String, String> _properties;

        public CSSStyle(String name, Map<String, String> properties)
        {
            _name = name;
            _properties = properties;
        }

        public String getCSSSource()
        {
            StringBuilder builder = new StringBuilder();
            builder.append(".").append(_name).append("\n{");
            for (Map.Entry<String, String> prop : _properties.entrySet())
                builder.append("\t").append(prop.getKey()).append(": ").append(prop.getValue()).append("\n");
            builder.append("}");
            return builder.toString();
        }
    }
    public static class GWTViewBean
    {
        private String _moduleName;
        private Map<String, String> _properties;
        private boolean _immediateLoad;

        public GWTViewBean(String moduleName, Map<String, String> properties)
        {
            _moduleName = moduleName;
            _properties = new HashMap<String, String>(properties);
        }

        public void init(ViewContext context)
        {
            _properties.put("container", context.getContainer().getPath());
            _properties.put("pageFlow", context.getActionURL().getPageFlow());
            _properties.put("contextPath", context.getContextPath());
            _properties.put("header1Size", ThemeFont.getThemeFont().getHeader_1Size());
        }

        public String getModuleName()
        {
            return _moduleName;
        }

        public Map<String, String> getProperties()
        {
            return _properties;
        }

        public boolean isImmediateLoad()
        {
            return _immediateLoad;
        }

        public void setImmediateLoad(boolean immediateLoad)
        {
            _immediateLoad = immediateLoad;
        }
    }

    private static String convertClassToModuleName(Class c)
    {
        String name = c.getName();
        int index = name.indexOf(".client.");
        if (index != -1)
        {
            name = name.substring(0, index) + name.substring(index + ".client.".length() - 1);
        }
        return name;
    }

    public GWTView(Class moduleClass)
    {
        this(convertClassToModuleName(moduleClass));
    }

    public GWTView(String moduleName)
    {
        this(moduleName, Collections.<String, String>emptyMap());
    }

    public GWTView(Class moduleClass, Map<String, String> properties)
    {
        this(convertClassToModuleName(moduleClass), properties);
    }

    public GWTView(String moduleName, Map<String, String> properties)
    {
        super("/org/labkey/api/view/GWTView.jsp", new GWTViewBean(moduleName, properties));
        getModelBean().init(getViewContext());
        getModulesForRootContext().add(moduleName);
    }

    public void setImmediateLoad(boolean immediateLoad)
    {
        getModelBean().setImmediateLoad(immediateLoad);
    }

    public static Set<String> getModulesForRootContext()
    {
        //Not synchronized since view rendering is single threaded
        Set<String> gwtModules = (Set<String>) HttpView.getRootContext().get("gwtModules");
        if (null == gwtModules)
        {
            gwtModules = new HashSet<String>();
            HttpView.getRootContext().put("gwtModules", gwtModules);
        }

        return gwtModules;
    }
}
