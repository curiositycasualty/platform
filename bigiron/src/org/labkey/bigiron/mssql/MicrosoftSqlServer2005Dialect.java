/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

package org.labkey.bigiron.mssql;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.data.SQLFragment;

/**
 * User: kevink
 * Date: Jan 28, 2008 2:56:27 PM
 */
public class MicrosoftSqlServer2005Dialect extends MicrosoftSqlServer2000Dialect
{
    public MicrosoftSqlServer2005Dialect()
    {
        _oldReservedWordSet.addAll(PageFlowUtil.set(
           "EXTERNAL", "PIVOT", "REVERT", "SECURITYAUDIT", "TABLESAMPLE", "UNPIVOT"
        ));
    }


    @Override
    public boolean supportsOffset()
    {
        return true;
    }

    @Override
    protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int rowCount, long offset)
    {
        if (order == null || order.trim().length() == 0)
            throw new IllegalArgumentException("ERROR: ORDER BY clause required to limit");

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT * FROM (\n");
        sql.append(select);
        sql.append(",\nROW_NUMBER() OVER (\n");
        sql.append(order);
        sql.append(") AS _RowNum\n");
        sql.append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (groupBy != null) sql.append("\n").append(groupBy);
        sql.append("\n) AS z\n");
        sql.append("WHERE _RowNum BETWEEN ");
        sql.append(offset + 1);
        sql.append(" AND ");
        sql.append(offset + rowCount);
        return sql;
    }

    @Override
    public boolean supportsSelectConcat()
    {
        return true;
    }

    @Override
    public SQLFragment getSelectConcat(SQLFragment selectSql)
    {
        String sql = selectSql.getSQL();
        int fromIndex = sql.indexOf("FROM");

        SQLFragment ret = new SQLFragment(selectSql);
        ret.insert(fromIndex, "AS [data()] ");
        ret.insert(0, "REPLACE ((");
        ret.append(" FOR XML PATH ('')), ' ', ',')");

        return ret;
    }
}
