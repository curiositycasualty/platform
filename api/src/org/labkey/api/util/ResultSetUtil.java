/*
 * Copyright (c) 2003-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.util;

import org.apache.commons.beanutils.ConvertUtils;
import static org.apache.commons.collections.IteratorUtils.filteredIterator;
import static org.apache.commons.collections.IteratorUtils.toList;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.data.CachedRowSetImpl;
import org.labkey.api.security.ACL;
import org.labkey.api.security.PermissionsMap;
import org.labkey.api.security.User;

import java.beans.Introspector;
import java.io.*;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;

import junit.framework.Test;
import junit.framework.TestSuite;


public class ResultSetUtil
{
    private static Logger _log = Logger.getLogger(ResultSetUtil.class);

    private ResultSetUtil()
    {
    }

    
    public static void close(ResultSet rs)
    {
        if (null == rs)
            return;
        try
        {
            rs.close();
        }
        catch (SQLException x)
        {
            _log.error("unexpected error", x);
        }
    }


    public static ResultSet filter(ResultSet in, Predicate pred)
    {
        Iterator<Map> it;
        boolean isComplete = true;

        if (in instanceof Table.TableResultSet)
        {
            it = ((Table.TableResultSet)in).iterator();
            isComplete = ((Table.TableResultSet)in).isComplete();
        }
        else
        {
            it = new ResultSetIterator(in);
        }

        //noinspection unchecked
        List<Map<String,Object>> accepted = toList(filteredIterator(it, pred));

        try
        {
            return new CachedRowSetImpl(in.getMetaData(), accepted, isComplete);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static ResultSet filter(ResultSet in, final User u, final PermissionsMap<Map> fn, final int required)
    {
        Predicate pred = new Predicate()
        {
            public boolean evaluate(Object object)
            {
                int perm = fn.getPermissions(u, (Map)object);
                return (perm & required) == required;
            }
        };
        return filter(in, pred);
    }


    public static class OwnerPermissions implements PermissionsMap<Map>
    {
        private String _ownerColumn;
        private int _ownerPermissions;

        public OwnerPermissions(String ownerColumn, int ownerPermissions)
        {
            _ownerColumn = ownerColumn;
            _ownerPermissions = ownerPermissions;
        }

        public int getPermissions(User u, Map t)
        {
            Integer ownerid = (Integer)t.get(_ownerColumn);
            if (null != ownerid && u.getUserId() == ownerid)
                return _ownerPermissions;
            return 0;
        }
    }


    public static class MapPermissions implements PermissionsMap<Map>
    {
        private String _keyColumn;
        private Map<Object,ACL> _aclMap;
        private int _defaultPermissions;

        private HashMap<Object,Integer> _permMap;

        /** aclMap may be used across users/calls, HOWEVER, this class should not
         * be reused, as we cache per user information.
         *
         * @param keyColumn
         * @param aclMap
         * @param defaultPermissions
         */
        public MapPermissions(String keyColumn, Map<Object, ACL> aclMap, int defaultPermissions)
        {
            _keyColumn = keyColumn;
            _aclMap = aclMap;
            _defaultPermissions = defaultPermissions;
            _permMap = new HashMap<Object,Integer>(aclMap.size() * 2);
        }

        public int getPermissions(User u, Map t)
        {
            Object key = t.get(_keyColumn);
            Integer perm = _permMap.get(key);
            if (null == perm)
            {
                ACL acl = _aclMap.get(key);
                if (null == acl)
                    perm = _defaultPermissions;
                else
                    perm = acl.getPermissions(u);
                _permMap.put(key,perm);
            }
            return perm;
        }
    }


    public static class UnionPermissions implements PermissionsMap<Map>
    {
        PermissionsMap<Map>[] _mappers;

        public UnionPermissions(PermissionsMap<Map>... mappers)
        {
            _mappers = mappers;
        }

        public int getPermissions(User u, Map t)
        {
            int perm = 0;
            for (PermissionsMap<Map> mapper : _mappers)
                perm |= mapper.getPermissions(u, t);
            return perm;
        }
    }


    private static class _RowMap extends ArrayListMap<String, Object> implements Serializable
    {
        _RowMap(_RowMap m)
        {
            _findMap = m._findMap;
            _row = new ArrayList<Object>(m._row.size());
        }

        _RowMap(ResultSet rs) throws SQLException
        {
            _row = new ArrayList<Object>(rs.getMetaData().getColumnCount() + 1);
            //NOTE: This makes row map case insensitive...
            _findMap = new CaseInsensitiveHashMap<Integer>();

            ResultSetMetaData md = rs.getMetaData();
            int count = md.getColumnCount();
            put("_row", null);  // We're going to stuff the current row index at index 0
            for (int i = 1; i <= count; i++)
            {
                String propName = md.getColumnName(i);

                if (propName.length() > 0 && Character.isUpperCase(propName.charAt(0)))
                    propName = Introspector.decapitalize(propName);

                put(propName, null);
            }
        }


        void load(ResultSet rs) throws SQLException
        {
            int len = rs.getMetaData().getColumnCount();

            if (0 == _row.size())                    // Stuff current row into rowMap
                _row.add(rs.getRow());
            else
                _row.set(0, rs.getRow());

            for (int i = 1; i <= len; i++)
            {
                Object o = rs.getObject(i);
                // Note: When using getObject() on a SQL column of type Text, the Microsoft SQL Server jdbc driver returns
                // a String, while the jTDS driver returns a Clob.  For consistency we map here.
                // Could map at lower level, but don't want to preclude ever using Clob as Clob
                if (o instanceof Clob)
                {
                    Clob clob = (Clob) o;

                    try
                    {
                        o = clob.getSubString(1, (int) clob.length());
                    }
                    catch (SQLException e)
                    {
                        _log.error(e);
                    }
                }

                // BigDecimal objects are rare, and almost always are converted immediately
                // to doubles for ease of use in Java code; we can take care of this centrally here.
                if (o instanceof BigDecimal)
                {
                    BigDecimal dec = (BigDecimal) o;
                    o = dec.doubleValue();
                }

                if (i == _row.size())
                    _row.add(o);
                else
                    _row.set(i, o);
            }
        }
    }


    public static ArrayListMap<String, Object> mapRow(ResultSet rs, Map<String, Object> m, boolean clone) throws SQLException
    {
        _RowMap in = (_RowMap) m;
        _RowMap map = in;
        if (null == in)
            map = new _RowMap(rs);
        else if (clone)
            map = new _RowMap(in);
        map.load(rs);
        return map;
    }


    public static ArrayListMap<String, Object> mapRow(ResultSet rs, Map<String, Object> m) throws SQLException
    {
        return mapRow(rs, m, false);
    }


    // Just for testing purposes... splats ResultSet meta data to log
    public static void logMetaData(ResultSet rs)
    {
        try
        {
            ResultSetMetaData md = rs.getMetaData();

            for (int i = 1; i <= md.getColumnCount(); i++)
            {
                _log.debug("Name: " + md.getColumnName(i));
                _log.debug("Label: " + md.getColumnLabel(i));
                _log.debug("Type: " + md.getColumnType(i));
                _log.debug("Display Size: " + md.getColumnDisplaySize(i));
                _log.debug("Type Name: " + md.getColumnTypeName(i));
                _log.debug("Precision: " + md.getPrecision(i));
                _log.debug("Scale: " + md.getScale(i));
                _log.debug("Schema: " + md.getSchemaName(i));
                _log.debug("Table: " + md.getTableName(i));
                _log.debug("========================");
            }
        }
        catch (SQLException e)
        {
            _log.error("logMetaData: " + e);
        }
    }


    /* copied from AliasManager, should have shared JS XML specific versions */
    private static boolean isLegalNameChar(char ch, boolean first)
    {
        if (ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch == '_')
            return true;
        if (first)
            return false;
        if (ch >= '0' && ch <= '9')
            return true;
        return false;
    }

    public static String legalNameFromName(String str)
    {
        StringBuilder buf = null;
        for (int i = 0; i < str.length(); i ++)
        {
            if (isLegalNameChar(str.charAt(i), i == 0))
                continue;
            if (buf == null)
            {
                buf = new StringBuilder(str.length());
            }
            buf.append(str.substring(buf.length(), i));
            buf.append("_");
        }
        if (buf == null)
            return str;
        buf.append(str.substring(buf.length(), str.length()));
        return buf.toString();
    }


    private static String legalJsName(String name)
    {
        // UNDONE: handle JS specific cases (keywords)
        return legalNameFromName(name);
    }


    private static String legalXMLName(String name)
    {
        // UNDONE: handle XML specific cases
        return legalNameFromName(name);
    }


    public static void exportAsJSON(Writer out, ResultSet rs) throws SQLException, IOException
    {
        ResultSetMetaData md = rs.getMetaData();
        ExportCol[] cols = new ExportCol[md.getColumnCount()+1];
        int columnCount = md.getColumnCount();
        for (int i=1 ; i<= columnCount; i++)
        {
            String name = md.getColumnName(i);
            String legalName = legalJsName(name);
            cols[i] = new ExportCol(legalName+":", i==columnCount?"":",");
        }

        ExportResultSet export = new ExportResultSet()
        {
            SimpleDateFormat formatTZ = new SimpleDateFormat("d MMM yyyy HH:mm:ss Z");
            
            void writeNull() throws IOException
            {
                _out.write("null");
            }

            void writeString(String s) throws IOException
            {
                _out.write(PageFlowUtil.jsString(s));
            }

            void writeDate(Date d) throws IOException
            {
                _out.write("new Date(");
                _out.write(String.valueOf(d.getTime()));
                _out.write(")");
            }

//            SimpleDateFormat formatTZ = new SimpleDateFormat("d MMM yyyy HH:mm:ss Z");
//
//            void writeDate(Date d) throws IOException
//            {
//                _out.write("new Date('");
//                _out.write(formatTZ.format(d));
//                _out.write("')");
//            }

//            void writeDate(Date d) throws IOException
//            {
//                _out.write("'@");
//                _out.write(String.valueOf(d.getTime()));
//                _out.write("@'");
//            }

            void writeObject(Object o) throws IOException
            {
                _out.write(ConvertUtils.convert(o));
            }
        };
        out.write("[");
        export.write(out, rs, "{", "}", ",", cols);
        out.write("]");
    }


    /** Writer should be UTF-8 */
    public static void exportAsXML(Writer out, ResultSet rs, String collectionName, String typeName) throws SQLException, IOException
    {
        collectionName = StringUtils.defaultString(collectionName, "rowset");
        typeName = StringUtils.defaultString(typeName, "row");

        ResultSetMetaData md = rs.getMetaData();
        int columnCount = md.getColumnCount();
        ExportCol[] cols = new ExportCol[columnCount+1];
        for (int i=1 ; i<= columnCount; i++)
        {
            String name = md.getColumnName(i);
            String legalName = legalXMLName(name);
            cols[i] = new ExportCol("<"+legalName+">", "</" + legalName + ">");
        }

        String startRow = "<" + typeName + ">";
        String endRow = "</" + typeName + ">";

        out.write("<" + collectionName + ">\n");
        ExportResultSet export = new ExportResultSet()
        {
            void writeString(String s) throws IOException
            {
                _out.write(encodeXml(s));
            }

            void writeObject(Object o) throws IOException
            {
                _out.write(ConvertUtils.convert(o));
            }
        };
        export.write(out, rs, startRow, endRow, "\n", cols);
        out.write("</" + collectionName + ">\n");
    }


    public static class ExportCol
    {
        ExportCol(String pre, String post)
        {
            prefix = pre;
            postfix = post;
        }
        String prefix;
        String postfix;
    }

    
    static class ExportResultSet
    {
        Writer _out;

        void writeNull() throws IOException
        {
        }

        void writeString(String s) throws IOException
        {
            _out.write(s);
        }

        void writeDate(Date d) throws IOException
        {
            _out.write(DateUtil.toISO(d));
        }

        void writeObject(Object o) throws IOException
        {
            _out.write(String.valueOf(o));
        }

        void write(Object o) throws IOException
        {
            if (null == o)
                writeNull();
            else if (o instanceof String)
                writeString((String)o);
            else if (o instanceof Date)
                writeDate((Date)o);
            else
                writeObject(o);
        }
        
        /** CONSIER: wrap TSV and CSV as well? */
        void write(Writer out, ResultSet rs, String startRow, String endRow, String connector, ExportCol[] cols)
                throws SQLException, IOException
        {
            _out = out;
            int columnCount = rs.getMetaData().getColumnCount();

            String and = "";
            while (rs.next())
            {
                _out.write(and);
                _out.write(startRow);
                for (int i=1 ; i<=columnCount ; i++)
                {
                    _out.write(cols[i].prefix);
                    Object o = rs.getObject(i);
                    write(o);
                    _out.write(cols[i].postfix);
                }
                _out.write(endRow);
                and = connector;
            }
        }
    }

    
    static String encodeXml(String s)
    {
        // is this actually xml compatible???
        return PageFlowUtil.filter(s, false, false);
    }


    public static final double POSITIVE_INFINITY_DB_VALUE = 1e300;
    public static final double NEGATIVE_INFINITY_DB_VALUE = -POSITIVE_INFINITY_DB_VALUE;

    public static double mapJavaDoubleToDatabaseDouble(double javaDouble)
    {
        if (Double.NEGATIVE_INFINITY == javaDouble)
            return NEGATIVE_INFINITY_DB_VALUE;
        else if (Double.POSITIVE_INFINITY == javaDouble)
            return POSITIVE_INFINITY_DB_VALUE;
        else
            return javaDouble;
    }

    public static double mapDatabaseDoubleToJavaDouble(double databaseValue)
    {
        if (NEGATIVE_INFINITY_DB_VALUE == databaseValue)
            return Double.NEGATIVE_INFINITY;
        else if (POSITIVE_INFINITY_DB_VALUE == databaseValue)
            return Double.POSITIVE_INFINITY;
        else
            return databaseValue;
    }
    
    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testExport()
                throws IOException, SQLException
        {
            ArrayList<Map<String,Object>> maps = new ArrayList<Map<String,Object>>();
            Map<String,Object> m;

            m = new HashMap<String,Object>();
            m.put("int", 1);
            m.put("s", "one");
            maps.add(m);
            m = new HashMap<String,Object>();
            m.put("int", 2);
            m.put("s", "1<2");
            maps.add(m);
            m = new HashMap<String,Object>();
            m.put("int", null);
            m.put("s", null);
            maps.add(m);

            ResultSet rs = new CachedRowSetImpl(new TestMetaData(), maps, true);

            StringWriter swXML = new StringWriter(1000);
            rs.beforeFirst();
            exportAsXML(swXML, rs, null, null);
            System.out.println(swXML);

            StringWriter swJS = new StringWriter(1000);
            rs.beforeFirst();
            exportAsJSON(swJS, rs);
            System.out.println(swJS);

            rs.close();
        }
        

        class TestMetaData extends ResultSetMetaDataImpl
        {
            TestMetaData()
            {
                ColumnMetaData colInt = new ColumnMetaData();
                colInt.columnName = "int";
                addColumn(colInt);
                ColumnMetaData colS = new ColumnMetaData();
                colS.columnName = "s";
                addColumn(colS);
            }
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
