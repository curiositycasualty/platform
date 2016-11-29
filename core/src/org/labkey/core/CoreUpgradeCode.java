/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.core;

import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.UpgradeUtils;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.reports.model.ViewCategoryManager;

import java.sql.SQLException;

/**
 * User: adam
 * Date: Nov 21, 2008
 * Time: 9:57:49 PM
 */
public class CoreUpgradeCode implements UpgradeCode
{
    // We don't call ContainerManager.getRoot() during upgrade code since the container table may not yet match
    // ContainerManager's assumptions. For example, older installations don't have a description column until
    // the 10.1 scripts run (see #9927).
    @SuppressWarnings("UnusedDeclaration")
    private String getRootId()
    {
        return new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT EntityId FROM core.Containers WHERE Parent IS NULL").getObject(String.class);
    }

    // Not currently invoked, but available for future scripts
    @SuppressWarnings({"UnusedDeclaration"})
    public void handleUnknownModules(ModuleContext context)
    {
        ModuleLoader.getInstance().handleUnkownModules();
    }

    // invoked by core-14.20-14.30.sql
    //
    // PostgreSQL only. Current PostgreSQL uq_container_label_parent UNIQUE CONSTRAINT has two problems:
    // 1. It allows multiple top-level categories with the same name (Parent column is NULLABLE and PostgreSQL doesn't treat NULL as unique)
    // 2. It treats Label as case-sensitive (although the UI prevents entry of multiples that differ by case only)
    // We run this to uniquify the labels so we can add a more constraining constraint. See #21698.
    @SuppressWarnings({"UnusedDeclaration"})
    public void uniquifyViewCategoryLabels(final ModuleContext context) throws SQLException
    {
        if (context.isNewInstall())
            return;

        TableInfo ti = ViewCategoryManager.getInstance().getTableInfoCategories();
        UpgradeUtils.uniquifyValues(ti.getColumn("Label"), ti.getColumn("Parent"), new Sort("RowId"), false, false);
    }
}
