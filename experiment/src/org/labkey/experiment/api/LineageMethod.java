package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.snapshot.AbstractTableMethodInfo;

/**
 * User: kevink
 * Date: 2/23/16
 *
 * Adds a table method to exp.Data or exp.Material tables.  For example:
 *   exp.Data.Parents(type, depth)
 *   exp.Data.Children(type, depth)
 *
 * Where:
 * - (optional) type is either one of 'ExperimentRun', 'Data', or 'Material' or is the cpastype lsid of the SampleSet or DataClass.
 *   CONSIDER: we could use use run.protocollsid as it's cpastype.
 * - (optional) depth is an integer >= 0.
 */
/*package*/ class LineageMethod extends AbstractTableMethodInfo
{
    private Container _container;
    private ColumnInfo _lsidColumn;
    private boolean _parents;

    LineageMethod(Container c, ColumnInfo lsidColumn, boolean parents)
    {
        super(JdbcType.VARCHAR);

        _container = c;
        _lsidColumn = lsidColumn;
        _parents = parents;
    }

    @Override
    public ColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias)
    {
        ColumnInfo col = super.createColumnInfo(parentTable, arguments, alias);
        ForeignKey fk = new LookupForeignKey("self_lsid", "Name")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return createLineageJunctionTable(arguments);
            }
        };
        String junctionLookup = _parents ? "parent_lsid" : "child_lsid";
        col.setFk(new MultiValuedForeignKey(fk, junctionLookup));
        return col;
    }

    protected TableInfo createLineageJunctionTable(ColumnInfo[] arguments)
    {
        TableInfo parentTable = _lsidColumn.getParentTable();
        UserSchema schema = parentTable.getUserSchema();

        // TODO: Use the _lsidColumn instead of hard-coding 'lsid' from the parentTable
        SQLFragment lsids = new SQLFragment();
        lsids.append("(SELECT qq.").append("lsid").append(" FROM ");
        lsids.append(parentTable.getFromSQL("qq"));
        lsids.append(")");

        SQLFragment[] fragments = getSQLFragments(arguments);
        SQLFragment expType = null;
        SQLFragment cpasType = null;
        if (fragments.length > 0 && isSimpleString(fragments[0]))
        {
            String type = toSimpleString(fragments[0]);
            if (type.equals("Data") || type.equals("Material") || type.equals("ExperimentRun"))
                expType = fragments[0];
            else
                cpasType = fragments[0];
        }

        Integer depth = null;
        if (fragments.length > 1)
        {
            String s = fragments[1].getSQL();
            try
            {
                depth = Integer.parseInt(s);
            }
            catch (NumberFormatException ex) { /* ok */ }

        }

        return new LineageTableInfo("Foo", schema, lsids, _parents, depth, expType, cpasType);
    }

    @Override
    public SQLFragment getSQL(String tableAlias, DbSchema schema, SQLFragment[] arguments)
    {
        String alias = _lsidColumn.getAlias();
        return new SQLFragment(tableAlias + "." + alias);
    }

    @Override
    protected SQLFragment[] getSQLFragments(ColumnInfo[] arguments)
    {
        return super.getSQLFragments(arguments);
    }

    public static boolean isSimpleString(SQLFragment f)
    {
        if (f.getParams().size() > 0)
            return false;
        String s = f.getSQL();
        if (s.length() < 2 || !s.startsWith("'"))
            return false;
        return s.length()-1 == s.indexOf('\'',1);
    }


    public static String toSimpleString(SQLFragment f)
    {
        assert isSimpleString(f);
        String s = f.getSQL();
        if (s.length() < 2 || !s.startsWith("'"))
            return s;
        s = s.substring(1,s.length()-1);
        s = StringUtils.replace(s,"''","'");
        return s;
    }

}
