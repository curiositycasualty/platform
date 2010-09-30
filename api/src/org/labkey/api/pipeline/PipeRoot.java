/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.api.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;

import java.io.File;
import java.net.URI;
import java.util.List;

/**
 * User: Nick
 * Date: Jul 7, 2007
 * Time: 8:09:14 PM
 */
public interface PipeRoot extends SecurableResource
{
    Container getContainer();

    @NotNull
    URI getUri();

    @NotNull
    File getRootPath();

    File resolvePath(String path);

    /** @return relative path to the file from the root. null if the file isn't under the root. Does not include a leading slash */
    String relativePath(File file);

    boolean isUnderRoot(File file);

    boolean hasPermission(Container container, User user, Class<? extends Permission> perm);

    void requiresPermission(Container container, User user, Class<? extends Permission> perm);

    /** Creates a .labkey directory if it's not present and returns it. Used for things like protocol definition files,
     * log files for some upgrade tasks, etc. Its contents are generally not exposed directly to the user */
    @NotNull
    File ensureSystemDirectory();

    String getEntityId();

    /**
     * @return null if no key pair has been configured for this pipeline root
     */
    @Nullable
    GlobusKeyPair getGlobusKeyPair();

    // returns whether this root should be indexed by the crawler
    boolean isSearchable();

    String getWebdavURL();

    /** @return a list of any problems found with this pipeline root */
    List<String> validate();

    /** @return true if this root exists on disk and is a directory */
    boolean isValid();

    void configureForm(SetupForm form);
}
