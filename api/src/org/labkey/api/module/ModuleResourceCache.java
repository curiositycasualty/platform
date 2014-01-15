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
package org.labkey.api.module;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.resource.Resource;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: 12/26/13
 * Time: 7:17 AM
 */

// Standard cache for module resources that handles registration and listening for file system events. The goal is to
// factor out all common cache functionality to make creating well behaved caches for new module resource types easy.
public final class ModuleResourceCache<T>
{
    private static final Logger LOG = Logger.getLogger(ModuleResourceCache.class);

    private final BlockingStringKeyCache<Object> _cache;
    private final ModuleResourceCacheHandler<T> _handler;
    private final ModuleResourceDirectory _directory;

    ModuleResourceCache(ModuleResourceDirectory directory, String description, ModuleResourceCacheHandler<T> handler)
    {
        _directory = directory;
        _cache = CacheManager.getBlockingStringKeyCache(1000, CacheManager.DAY, description, null);
        _handler = handler;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public T getResource(String resourceName)
    {
        CacheLoader<String, Object> cacheLoader = (CacheLoader<String, Object>) _handler.getResourceLoader();
        return (T)_cache.get(resourceName, null, cacheLoader);
    }

    /**
     *  Return a collection of all resources managed by this cache that are defined in the active modules
     *  in the specified Container.
     */
    public @NotNull Collection<T> getResources(Container c)
    {
        Set<Module> activeModules = c.getActiveModules();
        Collection<T> resources = new LinkedList<>();

        for (Module module : _directory.getModules())
            if (activeModules.contains(module))
                resources.addAll(getResources(module));

        return Collections.unmodifiableCollection(resources);
    }

    /**
     *  Return a collection of all resources manqaged by this cache that are defined by the specified Module.
     */
    public @NotNull Collection<T> getResources(Module module)
    {
        Collection<T> resources = new LinkedList<>();
        List<String> resourceNames = getResourceNames(module);

        for (String resourceName : resourceNames)
        {
            T resource = getResource(_handler.createCacheKey(module, resourceName));

            if (null != resource)
                resources.add(resource);
        }

        return Collections.unmodifiableCollection(resources);
    }

    // Returns empty list if directory gets deleted, no resources, etc.
    @NotNull
    private List<String> getResourceNames(Module module)
    {
        //noinspection unchecked
        return (List<String>) _cache.get(module.getName(), null, _resourceNameLoader);
    }

    // Clear a single module's list of resources from the cache (but leave the resources themselves cached)
    private void removeResourceNames(Module module)
    {
        _cache.remove(module.getName());
    }

    // Clear a single resource from the cache
    private void removeResource(Module module, String resourceName)
    {
        _cache.remove(_handler.createCacheKey(module, resourceName));
    }

    // Clear the whole cache
    private void clear()
    {
        _cache.clear();
    }

    private final CacheLoader _resourceNameLoader = new CacheLoader<String, List<String>>()
    {
        @Override
        public List<String> load(String moduleName, @Nullable Object argument)
        {
            Module module = ModuleLoader.getInstance().getModule(moduleName);
            Resource resourceDir = module.getModuleResolver().lookup(_directory.getPath());
            List<String> resourceNames = new LinkedList<>();

            if (resourceDir != null && resourceDir.isCollection())
            {
                // Create a list of all files in this directory that conform to the resource file format.
                // Store just the base name, which matches the resource name format.
                for (Resource r : resourceDir.list())
                {
                    if (r.isFile())
                    {
                        String filename = r.getName();

                        if (_handler.isResourceFile(filename))
                            resourceNames.add(_handler.getResourceName(module, filename));
                    }
                }
            }

            return Collections.unmodifiableList(resourceNames);
        }
    };

    FileSystemDirectoryListener getListener(Module module)
    {
        return new StandardListener(module, _handler.createChainedDirectoryListener(module));
    }


    private class StandardListener implements FileSystemDirectoryListener
    {
        private final Module _module;
        private final @Nullable FileSystemDirectoryListener _chainedListener;

        public StandardListener(Module module, @Nullable FileSystemDirectoryListener chainedListener)
        {
            _module = module;
            _chainedListener = chainedListener;
        }

        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            String filename = entry.toString();
            if (_handler.isResourceFile(filename))
            {
                removeResourceNames(_module);

                if (null != _chainedListener)
                    _chainedListener.entryCreated(directory, entry);
            }
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            String filename = entry.toString();
            if (_handler.isResourceFile(filename))
            {
                removeResourceNames(_module);
                removeResource(_module, _handler.getResourceName(_module, filename));

                if (null != _chainedListener)
                    _chainedListener.entryDeleted(directory, entry);
            }
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            String filename = entry.toString();
            if (_handler.isResourceFile(filename))
            {
                removeResource(_module, _handler.getResourceName(_module, filename));

                if (null != _chainedListener)
                    _chainedListener.entryModified(directory, entry);
            }
        }

        @Override
        public void overflow()
        {
            LOG.warn("Overflow!!");

            // I guess we should just clear the entire cache
            clear();

            if (null != _chainedListener)
                _chainedListener.overflow();
        }
    }
}
