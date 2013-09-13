/*
 * Copyright (c) 2004-2013 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import org.labkey.api.data.*;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;

public class DetailsView extends DataView
{
    private Object[] _pk;

    public DetailsView(DataRegion dataRegion, TableViewForm form)
    {
        super(dataRegion, form, null);
        _pk = form.getPkVals();
    }

    public DetailsView(TableViewForm form)
    {
        super(form, null);
        _pk = form.getPkVals();
    }

    public DetailsView(DataRegion dataRegion, Object... pk)
    {
        super(dataRegion, (BindException) null);
        _pk = pk;
    }

    protected boolean isColumnIncluded(ColumnInfo col)
    {
        return col.isShownInDetailsView();
    }

    protected void _renderDataRegion(RenderContext ctx, Writer out) throws IOException, SQLException
    {
        if (ctx.getResults() == null)
        {
            Filter filter = ctx.getBaseFilter();
            assert _pk != null || filter != null;
            if (null == filter)
                ctx.setBaseFilter(
                        new PkFilter(getTable(), _pk));
            getDataRegion().renderDetails(ctx, out);
        }
        else
            getDataRegion().renderDetails(ctx, out);
    }
}
