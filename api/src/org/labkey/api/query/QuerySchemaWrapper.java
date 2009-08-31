/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.query;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;

import java.util.Set;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Mar 31, 2009
 * Time: 10:14:40 AM
 *
 * Wrap a DbSchema, QUERY INTERNAL USE ONLY
 */
public class QuerySchemaWrapper implements QuerySchema
{
    final DbSchema _schema;

    public QuerySchemaWrapper(DbSchema schema)
    {
        _schema = schema;
    }

    public User getUser()
    {
        return null;
    }

    public Container getContainer()
    {
        return null;
    }

    public DbSchema getDbSchema()
    {
        return _schema;
    }

    public TableInfo getTable(String name)
    {
        return _schema.getTable(name);
    }

    public QuerySchema getSchema(String name)
    {
        return null;
    }

    public Set<String> getSchemaNames()
    {
        return Collections.emptySet();
    }

    public String getName()
    {
        return _schema.getName();
    }

    public String getDescription()
    {
        return "Contains data tables from the '" + _schema.getName() + "' database schema.";
    }
}
