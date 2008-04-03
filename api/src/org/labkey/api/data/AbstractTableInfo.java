package org.labkey.api.data;

import org.labkey.api.util.*;
import org.labkey.api.query.*;
import org.labkey.api.data.collections.CaseInsensitiveMap;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.apache.log4j.Logger;
import org.apache.beehive.netui.pageflow.Forward;

import java.util.*;
import java.sql.SQLException;
import java.sql.ResultSet;

abstract public class AbstractTableInfo implements TableInfo
{
    static private final Logger _log = Logger.getLogger(AbstractTableInfo.class);
    protected Iterable<FieldKey> _defaultVisibleColumns;
    protected DbSchema _schema;
    private String _titleColumn;

    public ColumnInfo getColumnFromPropertyURI(String propertyURI)
    {
        return null;
    }

    protected final Map<String, ColumnInfo> _columnMap;
    private Map<String, MethodInfo> _methodMap;
    protected String _name;
    protected String _alias = "unnamed";
    private List<DetailsURL> _detailsURLs = new ArrayList(0);

    public ColumnInfo[] getPkColumns()
    {
        List<ColumnInfo> ret = new ArrayList();
        for (ColumnInfo column : getColumns())
        {
            if (column.isKeyField())
            {
                ret.add(column);
            }
        }
        return ret.toArray(new ColumnInfo[0]);
    }

    public String getRowTitle(Object pk) throws SQLException
    {
        return null;
    }

    public AbstractTableInfo(DbSchema schema)
    {
        _schema = schema;
        _columnMap = constructColumnMap();
        MemTracker.put(this);
    }

    protected Map<String, ColumnInfo> constructColumnMap()
    {
        if (isCaseSensitive())
        {
            return new LinkedHashMap();
        }
        return new CaseInsensitiveMap(new LinkedHashMap());
    }

    protected boolean isCaseSensitive()
    {
        return false;
    }

    public DbSchema getSchema()
    {
        return _schema;
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    final public SQLFragment getFromSQL()
    {
        return getFromSQL(getAliasName());
    }

    public String[] getPkColumnNames()
    {
        List<String> ret = new ArrayList();
        for (ColumnInfo col : getPkColumns())
        {
            ret.add(col.getName());
        }
        return ret.toArray(new String[0]);
    }

    public ColumnInfo getVersionColumn()
    {
        return null;
    }

    public String getVersionColumnName()
    {
        return null;
    }

    public int getTableType()
    {
        return TABLE_TYPE_NOT_IN_DB;
    }

    public NamedObjectList getSelectList()
    {
        NamedObjectList ret = new NamedObjectList();
        ColumnInfo[] pkColumns = getPkColumns();
        if (pkColumns.length != 1)
            return ret;
        ColumnInfo titleColumn = getColumn(getTitleColumn());
        if (titleColumn == null)
            return ret;
        try
        {
            ColumnInfo[] cols;
            int titleIndex;
            if (pkColumns[0] == titleColumn)
            {
                cols = new ColumnInfo[] { pkColumns[0] };
                titleIndex = 1;
            }
            else
            {
                cols = new ColumnInfo[] { pkColumns[0], titleColumn };
                titleIndex = 2;
            }

            ResultSet rs = Table.select(this, cols, null, null);
            while (rs.next())
            {
                ret.put(new SimpleNamedObject(rs.getString(1), rs.getString(titleIndex)));
            }
            rs.close();
        }
        catch (SQLException e)
        {
            
        }
        return ret;
    }

    public ColumnInfo[] getUserEditableColumns()
    {
        List<ColumnInfo> ret = new ArrayList();
        for (ColumnInfo col : getColumns())
        {
            if (col.isUserEditable())
            {
                ret.add(col);
            }
        }
        return ret.toArray(new ColumnInfo[0]);
    }

    public ColumnInfo[] getColumns(String colNames)
    {
        String[] colNameArray = colNames.split(",");
        return getColumns(colNameArray);
    }

    public ColumnInfo[] getColumns(String... colNameArray)
    {
        List<ColumnInfo> ret = new ArrayList();
        for (String name : colNameArray)
        {
            ret.add(getColumn(name));
        }
        return ret.toArray(new ColumnInfo[0]);
    }

    public DataColumn[] getDisplayColumns(String colNames)
    {
        return getDisplayColumns(getColumns(colNames));
    }

    public DataColumn[] getDisplayColumns(String[] colNames)
    {
        return getDisplayColumns(getColumns(colNames));
    }

    public DataColumn[] getDisplayColumns(ColumnInfo[] columns)
    {
        List<DataColumn> ret = new ArrayList();
        for (ColumnInfo col : columns)
        {
            ret.add(new DataColumn(col));
        }
        return ret.toArray(new DataColumn[0]);
    }

    public String getSequence()
    {
        return null;
    }

    public String getTitleColumn()
    {
        return _titleColumn;
    }

    public void setTitleColumn(String titleColumn)
    {
        _titleColumn = titleColumn;
    }

    public ColumnInfo getColumn(String name)
    {
        ColumnInfo ret = _columnMap.get(name);
        if (ret != null)
            return ret;
        return resolveColumn(name);
    }

    protected ColumnInfo resolveColumn(String name)
    {
        return null;
    }

    public ColumnInfo[] getColumns()
    {
        Collection<ColumnInfo> col = _columnMap.values();
        return col.toArray(new ColumnInfo[col.size()]);
    }

    public Set<String> getColumnNameSet()
    {
        return _columnMap.keySet();
    }

    public String getName()
    {
        return _name;
    }

    public String getAliasName()
    {
        return _alias;
    }

    public boolean removeColumn(ColumnInfo column)
    {
        return _columnMap.remove(column.getName()) != null;
    }

    public ColumnInfo addColumn(ColumnInfo column)
    {
        // Not true if this is a VirtualTableInfo
        // assert column.getParentTable() == this;
        if (_columnMap.containsKey(column.getName()))
        {
            throw new IllegalArgumentException("Column " + column.getName() + " already exists.");
        }
        _columnMap.put(column.getName(), column);
        return column;
    }

    public void addMethod(String name, MethodInfo method)
    {
        if (_methodMap == null)
        {
            _methodMap = new HashMap();
        }
        _methodMap.put(name, method);
    }

    public MethodInfo getMethod(String name)
    {
        if (_methodMap == null)
            return null;
        return _methodMap.get(name);
    }

    public void setName(String name)
    {
        _name = name;
    }

    public void setAlias(String alias)
    {
        if (alias == null)
            return;
        _alias = alias;
    }

    public StringExpressionFactory.StringExpression getDetailsURL(Map<String, ColumnInfo> columns)
    {
        for (DetailsURL dUrl : _detailsURLs)
        {
            StringExpressionFactory.StringExpression ret = dUrl.getURL(columns);
            if (ret != null)
                return ret;
        }
        return null;
    }

    public ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    public Forward insert(User user, QueryUpdateForm form) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    public Forward update(User user, QueryUpdateForm form) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    public boolean hasPermission(User user, int perm)
    {
        return false;
    }

    public void setDetailsURL(DetailsURL detailsURL)
    {
        _detailsURLs.clear();
        _detailsURLs.add(detailsURL);
    }

    public void addDetailsURL(DetailsURL detailsURL)
    {
        _detailsURLs.add(detailsURL);
    }

    public void setDefaultVisibleColumns(Iterable<FieldKey> list)
    {
        _defaultVisibleColumns = list;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        if (_defaultVisibleColumns instanceof List)
        {
            return Collections.unmodifiableList((List<FieldKey>) _defaultVisibleColumns);
        }
        if (_defaultVisibleColumns != null)
        {
            List<FieldKey> ret = new ArrayList<FieldKey>();
            for (FieldKey key : _defaultVisibleColumns)
            {
                ret.add(key);
            }
            return Collections.unmodifiableList(ret);
        }
        return QueryService.get().getDefaultVisibleColumns(getColumns());
    }

    public boolean safeAddColumn(ColumnInfo column)
    {
        if (getColumn(column.getName()) != null)
            return false;
        addColumn(column);
        return true;

    }

    protected void initColumnFromXml(QuerySchema schema, ColumnInfo column, ColumnType xbColumn, Collection<QueryException> qpe)
    {
        if (xbColumn.getColumnTitle() != null)
        {
            column.setCaption(xbColumn.getColumnTitle());
        }
        if (xbColumn.isSetDescription())
        {
            column.setDescription(xbColumn.getDescription());
        }
        if (xbColumn.getFk() != null)
        {
            ColumnType.Fk fk = xbColumn.getFk();
            QuerySchema fkSchema = schema;
            if (fk.getFkDbSchema() != null)
            {
                fkSchema = QueryService.get().getUserSchema(schema.getUser(), schema.getContainer(), fk.getFkDbSchema());
                if (fkSchema == null)
                {
                    qpe.add(new MetadataException("Schema " + fk.getFkDbSchema() + " not found."));
                    return;
                }
            }
            column.setFk(new QueryForeignKey(fkSchema, fk.getFkTable(), fk.getFkColumnName(), null));
        }
        if (xbColumn.getFormatString() != null)
        {
            column.setFormatString(xbColumn.getFormatString());
        }
        if (xbColumn.getDatatype() != null)
        {
            column.setSqlTypeName(xbColumn.getDatatype());
        }
        if (xbColumn.isSetIsHidden())
        {
            column.setIsHidden(xbColumn.getIsHidden());
        }
        if (xbColumn.isSetIsUnselectable())
        {
            column.setIsUnselectable(xbColumn.getIsUnselectable());
        }
        if (xbColumn.isSetIsKeyField())
        {
            column.setKeyField(xbColumn.getIsKeyField());
        }
    }

    public void loadFromXML(QuerySchema schema, TableType xbTable, Collection<QueryException> qpe)
    {
        if (xbTable.getTitleColumn() != null)
        {
            setTitleColumn(xbTable.getTitleColumn());
        }
        if (xbTable.getTableUrl() != null)
        {
            try
            {
                setDetailsURL(DetailsURL.fromString(schema.getContainer(), xbTable.getTableUrl()));
            }
            catch (MetadataException me)
            {
                qpe.add(me);
            }
        }
        if (xbTable.getColumns() != null)
        {
            for (ColumnType xbColumn : xbTable.getColumns().getColumnArray())
            {
                ColumnInfo column = getColumn(xbColumn.getColumnName());
                if (column == null)
                {
                    // qpe.add(new MetadataException("Column " + xbColumn.getColumnName() + " not found."));
                    continue;
                }
                initColumnFromXml(schema, column, xbColumn, qpe);
            }
        }
    }

    /**
     * Returns true by default. Override if your derived class is not accessible through Query
     * @return Whether this table is public (i.e., accessible via Query)
     */
    public boolean isPublic()
    {
        //by default, all subclasses are public (i.e., accessible through Query)
        //override to change this
        return true;
    }

    public String getPublicName()
    {
        return getName();
    }

    public String getPublicSchemaName()
    {
        return getSchema().getName();
    }
}
