package org.labkey.api.query;

import org.labkey.api.data.TableViewForm;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;

/**
 * Extension of the class TableViewForm which deals with the fact that we don't have much control over the names
 * of fields in a user-defined table.
 * All column names are prefixed by "quf_" to generate the name of the field on the input form.
 */
public class QueryUpdateForm extends TableViewForm
{
    protected String _prefix = "quf_";
    public QueryUpdateForm(TableInfo table, HttpServletRequest request)
    {
        _tinfo = table;
        _dynaClass = new QueryWrapperDynaClass(this);
        reset(null, request);
        for (Enumeration en = request.getParameterNames(); en.hasMoreElements();)
        {
            String key = (String) en.nextElement();

            for (String value : request.getParameterValues(key))
            {
                set(key, value);
            }
        }
    }

    public ColumnInfo getColumnByFormFieldName(String name)
    {
        if (name.length() < _prefix.length())
            return null;
        return getTable().getColumn(name.substring(_prefix.length()));
    }

    public String getFormFieldName(ColumnInfo column)
    {
        return _prefix + column.getName();
    }
}
