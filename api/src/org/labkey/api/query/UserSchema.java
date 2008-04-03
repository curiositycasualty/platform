package org.labkey.api.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;

import javax.servlet.ServletException;
import java.util.*;

abstract public class UserSchema extends AbstractSchema
{
    protected String _name;

    public UserSchema(String name, User user, Container container, DbSchema dbSchema)
    {
        super(dbSchema, user, container);
        _name = name;
    }

    public TableInfo getTable(String name, String alias)
    {
        if (name == null)
            return null;
        if (getTableNames().contains(name))
        {
            return null;
        }
        QueryDefinition def = QueryService.get().getQueryDef(getContainer(), getSchemaName(), name);
        if (def == null)
            return null;
        return def.getTable(alias, this, null);
    }

    abstract public Set<String> getTableNames();

    public Set<String> getVisibleTableNames()
    {
        return getTableNames();
    }

    public Container getContainer()
    {
        return _container;
    }

    public String getSchemaName()
    {
        return _name;
    }

    public Set<String> getSchemaNames()
    {
        Set<String> ret = new HashSet(super.getSchemaNames());
        ret.add("Folder");
        return ret;
    }

    public QuerySchema getSchema(String name)
    {
        return DefaultSchema.get(_user, _container).getSchema(name);
    }

    public boolean canCreate()
    {
        return getContainer().hasPermission(getUser(), ACL.PERM_UPDATE);
    }

    public ActionURL urlFor(QueryAction action)
    {
        ActionURL ret;
        ret = new ActionURL("query", action.toString(), getContainer());
        ret.addParameter(QueryParam.schemaName.toString(), getSchemaName());
        return ret;
    }

    public ActionURL urlFor(QueryAction action, QueryDefinition queryDef)
    {
        return queryDef.urlFor(action, getContainer());
    }

    public QueryDefinition getQueryDefForTable(String name)
    {                                                
        return QueryService.get().createQueryDefForTable(this, name);
    }

    public ActionURL urlSchemaDesigner()
    {
        ActionURL ret = new ActionURL("query", "begin", getContainer());
        ret.addParameter(QueryParam.schemaName.toString(), getSchemaName());
        return ret;
    }

    public QueryView createView(ViewContext context, QuerySettings settings) throws ServletException
    {
        return new QueryView(this, settings);
    }

    public List<String> getTableAndQueryNames(boolean visibleOnly)
    {
        Set<String> set = new HashSet();
        set.addAll(visibleOnly ? getVisibleTableNames() : getTableNames());
        for (Map.Entry<String, QueryDefinition> entry : QueryService.get().getQueryDefs(getContainer(), getSchemaName()).entrySet())
        {
            if (!visibleOnly || !entry.getValue().isHidden())
            {
                set.add(entry.getKey());
            }
        }
        List<String> ret = new ArrayList(set);

        Collections.sort(ret, new Comparator<String>()
        {

            public int compare(String o1, String o2)
            {
                return o1.compareToIgnoreCase(o2);
            }
        });
        return ret;
    }

    public QuerySettings getSettings(Portal.WebPart webPart, ViewContext context)
    {
        QuerySettings settings = new QuerySettings(webPart, context);
        settings.setSchemaName(getSchemaName());
        return settings;
    }

    public QuerySettings getSettings(ActionURL url, String dataRegionName)
    {
        QuerySettings settings = new QuerySettings(url, dataRegionName);
        settings.setSchemaName(getSchemaName());
        return settings;
    }

}
