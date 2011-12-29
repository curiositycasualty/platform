/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.webdav.AbstractDocumentResource;
import org.labkey.api.webdav.AbstractWebdavResourceCollection;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Feb 4, 2009
 * Time: 11:17:57 AM
 */
public class QueryWebdavProvider implements WebdavService.Provider
{
	final String QUERY_NAME = "@query";

	public Set<String> addChildren(@NotNull WebdavResource target)
	{
		if (target instanceof WebdavResolverImpl.WebFolderResource)
			return PageFlowUtil.set(QUERY_NAME);
		return null;
	}

	public WebdavResource resolve(@NotNull WebdavResource parent, @NotNull String name)
	{
		if (!QUERY_NAME.equalsIgnoreCase(name))
			return null;
		if (!(parent instanceof WebdavResolverImpl.WebFolderResource))
			return null;
		if (parent.getPath().equals("/"))
			return null;
		WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) parent;
		return new QueryResource(folder);
	}


	class QueryResource extends AbstractWebdavResourceCollection
	{
		Container _c;
		ArrayList<String> _schemaNames = null;

		QueryResource(WebdavResolverImpl.WebFolderResource parent)
		{
			super(parent.getPath(), QUERY_NAME);
			_c = parent.getContainer();
			setPolicy(_c.getPolicy());
		}
		
		public boolean exists()
		{
			return true;
		}

		public WebdavResource find(String name)
		{
			DefaultSchema folderSchema = DefaultSchema.get(null, _c);
			QuerySchema s  = folderSchema.getSchema(name);
			if (null != s && s instanceof UserSchema)
				return new SchemaResource(this, ((UserSchema)s).getSchemaName());
			return null;
		}

		public Collection<String> listNames()
		{
			if (_schemaNames == null)
			{
				DefaultSchema folderSchema = DefaultSchema.get(null, _c);
				ArrayList<String> names = new ArrayList<String>();
				Set<String> s = folderSchema.getUserSchemaNames();
				if (null != s)
					names.addAll(s);
				_schemaNames = names;
			}
			return _schemaNames;
		}

		public long getCreated()
		{
            return Long.MIN_VALUE;
		}

		public long getLastModified()
		{
            return Long.MIN_VALUE;
		}
    }


	class SchemaResource extends AbstractWebdavResourceCollection
	{
		QueryResource _parent;
		
		SchemaResource(QueryResource parent, String schemaName)
		{
			super(parent.getPath(), schemaName);
			_parent = parent;
			setPolicy(_parent._c.getPolicy());
		}
		
		public boolean exists()
		{
			return true;
		}

		public WebdavResource find(String name)
		{
			if (!name.endsWith(".sql"))
				return null;
			name = name.substring(0,name.length()-".sql".length());
			QueryDefinition q = QueryService.get().getQueryDef(null, _parent._c, getName(), name);
			if (null == q)
				return null;
			return new SqlResource(this, q);
		}

		public Collection<String> listNames()
		{
			Map<String, QueryDefinition> m = QueryService.get().getQueryDefs(null, _parent._c, getName());
			ArrayList<String> list = new ArrayList<String>(m.size());
			for (String name : m.keySet())
				list.add(name + ".sql");
			return list;
		}

		public long getCreated()
		{
            return Long.MIN_VALUE;
		}

		public long getLastModified()
		{
            return Long.MIN_VALUE;
		}
    }


	class SqlResource extends AbstractDocumentResource
	{
		SchemaResource _parent;
		QueryDefinition _q;

		SqlResource(SchemaResource parent, QueryDefinition query)
		{
			super(parent.getPath(), query.getName() + ".sql");
			_parent = parent;
			setPolicy(_parent._parent._c.getPolicy());
			_q = query;
		}

		public boolean exists()
		{
			return true;
		}

		public long getCreated()
		{
			return 0;	// TODO
		}

		public long getLastModified()
		{
			return 0;	// TODO
		}

		@Override
		public String getETag(boolean force)
		{
			// TODO since getLastModified() is NYI
			String sql = StringUtils.trimToEmpty(_q.getSql());
			return "W/\"" + sql.length() + "-" + sql.hashCode() + "\"";
		}

		public InputStream getInputStream(User user) throws IOException
		{
			String sql = StringUtils.trimToEmpty(_q.getSql());
			return new ByteArrayInputStream(sql.getBytes("UTF-8"));
		}

		public long copyFrom(User user, FileStream in) throws IOException
		{
			String sql = PageFlowUtil.getStreamContentsAsString(in.openInputStream());
			_q.setSql(sql);
			try
			{
				_q.save(user, _q.getContainer());
			}
			catch (SQLException sqlx)
			{
				throw new RuntimeSQLException(sqlx);
			}
			return getContentLength();
		}

		public long getContentLength() throws IOException
		{
			String sql = StringUtils.trimToEmpty(_q.getSql());
			return sql.getBytes("UTF-8").length;
		}

		@Override
		public boolean canRead(User user)
		{
			return super.canRead(user);
		}

		@Override
		public boolean canWrite(User user)
		{
			return super.canWrite(user);
		}

		@Override
		public boolean canRename(User user)
		{
			return false;
		}

		@Override
		public boolean canCreate(User user)
		{
			return false;
		}

		@Override
		public boolean canDelete(User user)
		{
			return false;
		}
    }
}
