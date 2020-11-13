/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
package org.labkey.api.admin;

import org.labkey.api.services.ServiceRegistry;

import java.util.Collection;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface FolderSerializationRegistry
{
    static FolderSerializationRegistry get()
    {
        return ServiceRegistry.get().getService(FolderSerializationRegistry.class);
    }

    static void setInstance(FolderSerializationRegistry impl)
    {
        ServiceRegistry.get().registerService(FolderSerializationRegistry.class, impl);
    }

    void addFactories(FolderWriterFactory writerFactory, FolderImporterFactory importerFactory);
    void addImportFactory(FolderImporterFactory importerFactory);
    void addWriterFactory(FolderWriterFactory writerFactory);
    Collection<FolderWriter> getRegisteredFolderWriters();
    Collection<FolderImporter> getRegisteredFolderImporters();
}