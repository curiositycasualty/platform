/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.MemTracker;

import java.util.*;
import java.sql.SQLException;

abstract public class AbstractSchema implements QuerySchema
{
    protected DbSchema _dbSchema;
    protected User _user;
    protected Container _container;

    public AbstractSchema(DbSchema dbSchema, User user, Container container)
    {
        _dbSchema = dbSchema;
        _user = user;
        _container = container;
        assert MemTracker.put(this);
    }

    public DbSchema getDbSchema()
    {
        return _dbSchema;
    }

    public @Nullable QuerySchema getSchema(String name)
    {
        return null;
    }

    public Set<String> getSchemaNames()
    {
        return Collections.emptySet();
    }

    public User getUser()
    {
        return _user;
    }

    public Container getContainer()
    {
        return _container;
    }

    protected void afterConstruct(TableInfo info)
    {
        if (info instanceof AbstractTableInfo)
        {
            AbstractTableInfo t = ((AbstractTableInfo)info);
            t.afterConstruct();
        }
    }
}
