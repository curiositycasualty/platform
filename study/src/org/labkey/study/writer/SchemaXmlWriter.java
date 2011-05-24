/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoWriter;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.StudyContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.api.exp.property.Domain;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataSetTable;
import org.labkey.study.query.StudyQuerySchema;

import java.io.IOException;
import java.util.List;

/**
 * User: adam
 * Date: May 27, 2009
 * Time: 11:12:33 AM
 */
public class SchemaXmlWriter implements Writer<List<DataSetDefinition>, StudyContext>
{
    public static final String SCHEMA_FILENAME = "datasets_metadata.xml";

    private final String _defaultDateFormat;

    public SchemaXmlWriter(String defaultDateFormat)
    {
        _defaultDateFormat = defaultDateFormat;
    }

    public String getSelectionText()
    {
        return "Dataset Schema Description";
    }

    public void write(List<DataSetDefinition> definitions, StudyContext ctx, VirtualFile vf) throws IOException
    {
        // Create dataset metadata file
        TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
        TablesDocument.Tables tablesXml = tablesDoc.addNewTables();

        StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);

        for (DataSetDefinition def : definitions)
        {
            TableInfo ti = new DataSetTable(schema, def);
            TableType tableXml = tablesXml.addNewTable();
            DatasetTableInfoWriter w = new DatasetTableInfoWriter(ti, def, _defaultDateFormat);
            w.writeTable(tableXml);
        }

        vf.saveXmlBean(SCHEMA_FILENAME, tablesDoc);
    }


    private static class DatasetTableInfoWriter extends TableInfoWriter
    {
        private final DataSetDefinition _def;
        private Domain _domain;

        private DatasetTableInfoWriter(TableInfo ti, DataSetDefinition def, String defaultDateFormat)
        {
            super(ti, DatasetWriter.getColumnsToExport(ti, def, true), defaultDateFormat);
            _def = def;
            _domain = _def.getDomain();
        }

        @Override
        public void writeTable(TableType tableXml)
        {
            super.writeTable(tableXml);

            tableXml.setTableName(_def.getName());  // Use dataset name, not temp table name
            if (null != _def.getLabel())
                tableXml.setTableTitle(_def.getLabel());
            if (null != _def.getDescription())
                tableXml.setDescription(_def.getDescription());
        }

        @Override
        public void writeColumn(ColumnInfo column, ColumnType columnXml)
        {
            super.writeColumn(column, columnXml);

            String columnName = column.getName();
            if (columnName.equals(_def.getKeyPropertyName()))
            {
                columnXml.setIsKeyField(true);

                if (_def.getKeyManagementType() == DataSet.KeyManagementType.RowId)
                    columnXml.setIsAutoInc(true);
                else if (_def.getKeyManagementType() == DataSet.KeyManagementType.GUID)
                    columnXml.setDatatype("entityid");
            }
        }

        @Override
        protected String getPropertyURI(ColumnInfo column)
        {
            String propertyURI = column.getPropertyURI();
            // Only round-trip PropertyURIs that are special. Ones that are standard domain properties will have
            // a new PropertyURI generated that contains the proper container id, etc on the import side.
            if (propertyURI != null && (_domain == null || !propertyURI.startsWith(_domain.getTypeURI())) && !propertyURI.startsWith(ColumnInfo.DEFAULT_PROPERTY_URI_PREFIX))
                return propertyURI;
            return null;
        }

        @Override
        protected String getConceptURI(ColumnInfo column)
        {
            String conceptURI = super.getConceptURI(column);
            if (null != conceptURI)
                    return conceptURI;

            // Proper ConceptURI support is not implemented, but we use the 'VisitDate' concept in this isolated spot
            // as a marker to indicate which dataset column should be tagged as the visit date column during import:
            if (column.getName().equals(_def.getVisitDateColumnName()))
                return DataSetDefinition.getVisitDateURI();
            return null;
        }
    }
}