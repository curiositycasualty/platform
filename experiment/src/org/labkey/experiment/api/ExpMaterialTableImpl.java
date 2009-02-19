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

package org.labkey.experiment.api;

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.ExpSampleSetTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.*;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public class ExpMaterialTableImpl extends ExpTableImpl<ExpMaterialTable.Column> implements ExpMaterialTable
{
    ExpSampleSet _ss;

    public ExpMaterialTableImpl(String name, String alias, UserSchema schema)
    {
        super(name, alias, ExperimentServiceImpl.get().getTinfoMaterial(), schema);
        setName(ExpSchema.TableType.Materials.name());
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Folder:
                return wrapColumn(alias, _rootTable.getColumn("Container"));
            case LSID:
                return wrapColumn(alias, _rootTable.getColumn("LSID"));
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case CpasType:
            {
                ColumnInfo columnInfo = wrapColumn(alias, _rootTable.getColumn("CpasType"));
                columnInfo.setFk(new LookupForeignKey("LSID")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        ExpSampleSetTable sampleSetTable = ExperimentService.get().createSampleSetTable(ExpSchema.TableType.SampleSets.toString(), "SampleSets", _schema);
                        sampleSetTable.populate();
                        return sampleSetTable;
                    }
                });
                return columnInfo;
            }
            case SourceProtocolLSID:
                // Todo - hook up foreign key
                return wrapColumn(alias, _rootTable.getColumn("SourceProtocolLSID"));//.setFk(new QueryForeignKey(this, PROTOCOLS_TABLE_NAME, "LSID", "Name"));
            case Run:
                return wrapColumn(alias, _rootTable.getColumn("RunId"));
            case RowId:
            {
                ColumnInfo ret = wrapColumn(alias, _rootTable.getColumn("RowId"));
                ret.setFk(new RowIdForeignKey(ret));
                ret.setIsHidden(true);
                return ret;
            }
            case Property:
                ColumnInfo ret = createPropertyColumn(alias);
                if (_ss != null)
                {
                    ret.setFk(new DomainForeignKey(_ss.getContainer(), _ss.getLSID(), _schema));
                }
                return ret;
            case Flag:
                return createFlagColumn(alias);
            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    public void setSampleSet(ExpSampleSet ss, boolean filter)
    {
        if (_ss != null)
        {
            throw new IllegalStateException("Cannot unset sample set");
        }
        if (filter)
            addCondition(getRealTable().getColumn("CpasType"), ss.getLSID());
        _ss = ss;
    }

    public void setMaterials(Set<ExpMaterial> materials)
    {
        if (materials.isEmpty())
        {
            addCondition(new SQLFragment("1 = 2"));
        }
        else
        {
            SQLFragment sql = new SQLFragment();
            sql.append("RowID IN (");
            String separator = "";
            for (ExpMaterial material : materials)
            {
                sql.append(separator);
                separator = ", ";
                sql.append(material.getRowId());
            }
            sql.append(")");
            addCondition(sql);
        }
    }

    public void populate()
    {
        populate(null, false);
    }

    public void populate(ExpSampleSet ss, boolean filter)
    {
        if (ss != null && !ss.getContainer().equals(getContainer()))
        {
            setContainerFilter(new ContainerFilter.CurrentPlusExtras(_schema.getUser(), ss.getContainer()));
        }

        addColumn(ExpMaterialTable.Column.RowId).setIsHidden(true);
        addColumn(ExpMaterialTable.Column.Name);
        ColumnInfo typeColumnInfo = addColumn(Column.CpasType);
        typeColumnInfo.setFk(new LookupForeignKey("lsid")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ExpSchema(_schema.getUser(), _schema.getContainer()).createSampleSetTable(null);
            }
        });
        addContainerColumn(ExpMaterialTable.Column.Folder, null);
        addColumn(ExpMaterialTable.Column.Run).setFk(new ExpSchema(_schema.getUser(), getContainer()).getRunIdForeignKey());
        ColumnInfo colLSID = addColumn(ExpMaterialTable.Column.LSID);
        colLSID.setIsHidden(true);

        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.Name));
        defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.Run));
        defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.CpasType));

        if (ss != null)
        {
            addColumn(ExpMaterialTable.Column.Flag);
            defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.Flag));
            setSampleSet(ss, filter);
            addSampleSetColumns(ss, defaultCols);
        }
        else
        {
            ExpSampleSet activeSource = ExperimentService.get().lookupActiveSampleSet(getContainer());
            if (activeSource != null)
            {
                setSampleSet(activeSource, false);
                addSampleSetColumns(_ss, defaultCols);
            }
        }
        if (_ss != null)
        {
            setName(_ss.getName());
        }

        ActionURL url = new ActionURL(ExperimentController.ShowMaterialAction.class, getContainer());
        setDetailsURL(new DetailsURL(url, Collections.singletonMap("rowId", "RowId")));
        setTitleColumn(Column.Name.toString());

        setDefaultVisibleColumns(defaultCols);

    }

    private void addSampleSetColumns(ExpSampleSet ss, List<FieldKey> visibleColumns)
    {
        addColumn(Column.Property);
        visibleColumns.remove(FieldKey.fromParts("Run"));
        FieldKey keyProp = new FieldKey(null, "Property");
        for (PropertyDescriptor pd : ss.getPropertiesForType())
        {
            visibleColumns.add(new FieldKey(keyProp, pd.getName()));
        }
        setDefaultVisibleColumns(visibleColumns);
    }

    public String getPublicSchemaName()
    {
        if (_ss != null)
        {
            return SamplesSchema.SCHEMA_NAME;
        }
        return super.getPublicSchemaName();
    }


    @Override
    public SQLFragment getFromSQL(String alias)
    {
        return super.getFromSQL(alias);
    }
}
