/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.experiment.api.flag;

import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.util.StringExpression;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;

public class FlagForeignKey extends AbstractForeignKey
{
    public static final String DISPLAYFIELD_NAME = "Comment";
    String _urlFlagged;
    String _urlUnflagged;
    private final Container _container;
    User _user;
    
    public FlagForeignKey(String urlFlagged, String urlUnflagged, Container container, User user)
    {
        _urlFlagged = urlFlagged;
        _urlUnflagged = urlUnflagged;
        _container = container;
        _user = user;
    }

    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        if (displayField == null)
        {
            displayField = DISPLAYFIELD_NAME;
        }
        if (!displayField.equalsIgnoreCase(DISPLAYFIELD_NAME))
            return null;
        return new FlagColumn(parent, _urlFlagged, _urlUnflagged, _container, _user);
    }

    public TableInfo getLookupTableInfo()
    {
        VirtualTable ret = new VirtualTable(ExperimentService.get().getSchema());
        ret.setName("FlagComment");
        ColumnInfo colComment = new ColumnInfo("Comment", ret);
        colComment.setSqlTypeName("VARCHAR");
        ret.addColumn(colComment);
        return ret;
    }

    public StringExpression getURL(ColumnInfo parent)
    {
        return null;
    }
}
