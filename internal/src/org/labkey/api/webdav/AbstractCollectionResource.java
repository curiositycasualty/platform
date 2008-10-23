/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.webdav;

import org.apache.axis.encoding.ser.ArrayDeserializer;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 22, 2008
 * Time: 2:49:44 PM
 */
public abstract class AbstractCollectionResource extends AbstractResource
{
    protected AbstractCollectionResource(String path)
    {
        super(path);
    }
    
    protected AbstractCollectionResource(String parent, String name)
    {
        super(parent, name);
    }

    public boolean isCollection()
    {
        return exists();
    }

    public boolean isFile()
    {
        return false;
    }

    public InputStream getInputStream() throws IOException
    {
        return null;
    }

    public OutputStream getOutputStream() throws IOException
    {
        return null;
    }

    public long getContentLength()
    {
        return 0;
    }

    public List<WebdavResolver.Resource> list()
    {
        List<String> names = listNames();
        List<WebdavResolver.Resource> list = new ArrayList<WebdavResolver.Resource>(names.size());
        for (String name : names)
        {
            WebdavResolver.Resource r = find(name);
            if (r != null)
                list.add(r);
        }
        return list;
    }
}
