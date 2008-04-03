package org.labkey.api.data;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: brittp
 * Date: Aug 6, 2006
 * Time: 3:36:42 PM
 */
public class Aggregate
{
    public enum Type
    {
        SUM("Total"),
        AVG("Average"),
        COUNT("Count"),
        MIN("Minimum"),
        MAX("Maximum");

        private String _friendlyName;

        private Type(String friendlyName)
        {
            _friendlyName = friendlyName;
        }

        public String getSQLColumnFragment(String columnName, String asName)
        {
            return name() + "(" + CoreSchema.getInstance().getSqlDialect().getColumnSelectName(columnName) + ") AS " + asName;
        }

        public String getFriendlyName()
        {
            return _friendlyName;
        }
    }

    public static class Result
    {
        private Aggregate _aggregate;
        private Object _value;
        public Result(Aggregate aggregate, Object value)
        {
            _aggregate = aggregate;
            _value = value;
        }

        public Aggregate getAggregate()
        {
            return _aggregate;
        }

        public Object getValue()
        {
            return _value;
        }
    }

    private String _columnName;
    private Type _type;
    private String _aggregateColumnName;
    public Aggregate(ColumnInfo column, Aggregate.Type type)
    {
        _columnName = column.getAlias();
        _type = type;
        _aggregateColumnName = type.name() + _columnName;
    }

    public String getSQL()
    {
        return _type.getSQLColumnFragment(_columnName, _aggregateColumnName);
    }

    public String getColumnName()
    {
        return _columnName;
    }

    public Type getType()
    {
        return _type;
    }

    public Result getResult(ResultSet rs) throws SQLException
    {
        double resultValue = rs.getDouble(_aggregateColumnName);
        if (resultValue == Math.floor(resultValue))
            return new Result(this, new Long((long) resultValue));
        else
            return new Result(this, new Double(resultValue));
    }
}
