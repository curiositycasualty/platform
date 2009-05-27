/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.*;
import org.labkey.api.study.query.ProtocolFilteredObjectTable;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: brittp
 * Date: Jul 6, 2007
 * Time: 5:35:15 PM
 */
public class RunDataTable extends FilteredTable
{
    public RunDataTable(final AssaySchema schema, final ExpProtocol protocol)
    {
        super(new ProtocolFilteredObjectTable(schema.getContainer(), protocol.getLSID()), schema.getContainer());
        List<FieldKey> visibleColumns = new ArrayList<FieldKey>();
        ColumnInfo objectIdColumn = addWrapColumn(_rootTable.getColumn("ObjectId"));
        objectIdColumn.setKeyField(true);
        ColumnInfo column = wrapColumn("Properties", _rootTable.getColumn("ObjectId"));
        column.setKeyField(false);
        column.setIsUnselectable(true);
        final AssayProvider provider = AssayService.get().getProvider(protocol);
        Domain resultsDomain = provider.getResultsDomain(protocol);
        DomainProperty[] resultsDPs = resultsDomain.getProperties();
        QcAwarePropertyForeignKey fk = new QcAwarePropertyForeignKey(resultsDPs, this, schema)
        {
            @Override
            protected ColumnInfo constructColumnInfo(final ColumnInfo parent, String name, final PropertyDescriptor pd)
            {
                ColumnInfo result = super.constructColumnInfo(parent, name, pd);
                // Don't override any lookups that are already set
                if (AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equals(pd.getName()) && pd.getLookupQuery() == null && pd.getLookupSchema() == null)
                {
                    DomainProperty[] batchDPs = provider.getBatchDomain(protocol).getProperties();
                    for (DomainProperty batchDP : batchDPs)
                    {
                        if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(batchDP.getName()))
                        {
                            result.setFk(new SpecimenForeignKey(schema, provider, protocol));
                            return result;
                        }
                    }
                }
                return result;
            }
        };

        Set<String> hiddenCols = new HashSet<String>();
        for (PropertyDescriptor pd : fk.getDefaultHiddenProperties())
            hiddenCols.add(pd.getName());

        FieldKey dataKeyProp = new FieldKey(null, column.getName());
        for (DomainProperty lookupCol : resultsDPs)
        {
            if (!lookupCol.isHidden() && !hiddenCols.contains(lookupCol.getName()))
                visibleColumns.add(new FieldKey(dataKeyProp, lookupCol.getName()));
        }
        column.setFk(fk);
        addColumn(column);

        // TODO - we should have a more reliable (and speedier) way of identifying just the data rows here
        SQLFragment dataRowClause = new SQLFragment("ObjectURI LIKE '%.DataRow-%'");
        addCondition(dataRowClause, "ObjectURI");

        String sqlRunLSID = "(SELECT RunObjects.objecturi FROM exp.Object AS DataRowParents, " +
                "    exp.Object AS RunObjects, exp.Data d, exp.ExperimentRun r WHERE \n" +
                "    DataRowParents.ObjectUri = d.lsid AND\n" +
                "    r.RowId = d.RunId AND\n" +
                "    RunObjects.ObjectURI = r.lsid AND\n" +
                "    DataRowParents.ObjectID IN (SELECT OwnerObjectId FROM exp.Object AS DataRowObjects\n" +
                "    WHERE DataRowObjects.ObjectId = " + ExprColumn.STR_TABLE_ALIAS + ".ObjectId))";

        ExprColumn runColumn = new ExprColumn(this, "Run", new SQLFragment(sqlRunLSID), Types.VARCHAR);
        runColumn.setFk(new LookupForeignKey("LSID")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpRunTable expRunTable = AssayService.get().createRunTable(protocol, provider, schema.getUser(), schema.getContainer());
                expRunTable.setContainerFilter(getContainerFilter());
                return expRunTable;
            }
        });
        addColumn(runColumn);

        List<PropertyDescriptor> runProperties = provider.getRunTableColumns(protocol);
        for (PropertyDescriptor prop : runProperties)
        {
            if (!prop.isHidden())
                visibleColumns.add(FieldKey.fromParts("Run", AssayService.RUN_PROPERTIES_COLUMN_NAME, prop.getName()));
        }

        for (DomainProperty prop : provider.getBatchDomain(protocol).getProperties())
        {
            if (!prop.isHidden())
                visibleColumns.add(FieldKey.fromParts("Run", AssayService.BATCH_COLUMN_NAME, AssayService.BATCH_PROPERTIES_COLUMN_NAME, prop.getName()));
        }

        Set<String> studyColumnNames = ((AbstractAssayProvider)provider).addCopiedToStudyColumns(this, protocol, schema.getUser(), "objectId", false);
        for (String columnName : studyColumnNames)
        {
            visibleColumns.add(new FieldKey(null, columnName));
        }

        setDefaultVisibleColumns(visibleColumns);
    }

    @Override
    public String toString()
    {
        return "RunDataTable";
    }
}
