/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

package org.labkey.query.olap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler2;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;


public class OlapSchemaCacheHandler implements ModuleResourceCacheHandler2<Map<String, OlapSchemaDescriptor>>
{
    public static final String DIR_NAME = "olap";

    @Override
    public Map<String, OlapSchemaDescriptor> load(@Nullable Resource dir, Module module)
    {
        if (null == dir)
            return Collections.emptyMap();

        Map<String, OlapSchemaDescriptor> map = new HashMap<>();

        dir.list().stream()
            .filter(resource -> resource.isFile() && resource.getName().endsWith(".xml"))
            .forEach(resource -> {
                String configName = FileUtil.getBaseName(resource.getName());
                String configId = createOlapCacheKey(module, configName);
                OlapSchemaDescriptor descriptor = configName.contains("junit") ? new JunitOlapSchemaDescriptor(configId, module, resource) : new ModuleOlapSchemaDescriptor(configId, module, resource);
                map.put(configName, descriptor);
            });


        return Collections.unmodifiableMap(map);
    }

    @Nullable
    @Override
    public FileSystemDirectoryListener createChainedDirectoryListener(Module module)
    {
        return new OlapDirectoryListener();
    }

    private static final Pattern CONFIG_ID_PATTERN = Pattern.compile("("+ ModuleLoader.MODULE_NAME_REGEX + "):/(.+)");

    public static String createOlapCacheKey(@NotNull Module module, @NotNull String name)
    {
        if (module == null)
            throw new IllegalArgumentException("module required");

        if (name == null)
            throw new IllegalArgumentException("name required");

        return module.getName() + ":/" + name;
    }

    public static ModuleResourceCache.CacheId parseOlapCacheKey(String schemaId)
    {
        ModuleResourceCache.CacheId tid = ModuleResourceCache.parseCacheKey(schemaId, CONFIG_ID_PATTERN);
        return tid;
    }


    private static class OlapDirectoryListener implements FileSystemDirectoryListener
    {
        private OlapDirectoryListener()
        {
        }

        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            ServerManager.olapSchemaDescriptorChanged(null);
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            ServerManager.olapSchemaDescriptorChanged(null);
        }

        @Override
        public void overflow()
        {
        }
    }
}
