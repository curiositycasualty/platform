/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.apache.commons.beanutils.ConvertUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.URIUtil;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Jan 21, 2009
 */
public class ExperimentJSONConverter
{
    // General experiment object properties
    public static final String ID = "id";
    public static final String ROW_ID = "rowId";
    public static final String CREATED = "created";
    public static final String CREATED_BY = "createdBy";
    public static final String MODIFIED = "modified";
    public static final String MODIFIED_BY = "modifiedBy";
    public static final String NAME = "name";
    public static final String LSID = "lsid";
    public static final String PROPERTIES = "properties";
    public static final String COMMENT = "comment";
    public static final String DATA_FILE_URL = "dataFileURL";
    public static final String ABSOLUTE_PATH = "absolutePath";
    public static final String PIPELINE_PATH = "pipelinePath"; //path relative to pipeline root

    // Run properties
    public static final String DATA_INPUTS = "dataInputs";
    public static final String MATERIAL_INPUTS = "materialInputs";
    public static final String ROLE = "role";
    public static final String DATA_OUTPUTS = "dataOutputs";
    public static final String MATERIAL_OUTPUTS = "materialOutputs";

    // Material properties
    public static final String SAMPLE_SET = "sampleSet";

    public static JSONObject serializeRunGroup(ExpExperiment runGroup, Domain domain) throws SQLException
    {
        JSONObject jsonObject = serializeStandardProperties(runGroup, domain.getProperties());
        jsonObject.put(COMMENT, runGroup.getComments());
        return jsonObject;
    }

    public static JSONObject serializeRun(ExpRun run, Domain domain) throws SQLException
    {
        JSONObject jsonObject = serializeStandardProperties(run, domain.getProperties());
        jsonObject.put(COMMENT, run.getComments());

        JSONArray inputDataArray = new JSONArray();
        for (ExpData data : run.getDataInputs().keySet())
        {
            inputDataArray.put(ExperimentJSONConverter.serializeData(data));
        }
        jsonObject.put(DATA_INPUTS, inputDataArray);

        JSONArray inputMaterialArray = new JSONArray();
        for (ExpMaterial material : run.getMaterialInputs().keySet())
        {
            JSONObject jsonMaterial = ExperimentJSONConverter.serializeMaterial(material);
            jsonMaterial.put(ROLE, run.getMaterialInputs().get(material));
            inputMaterialArray.put(jsonMaterial);
        }
        jsonObject.put(MATERIAL_INPUTS, inputMaterialArray);

        JSONArray outputDataArray = new JSONArray();
        for (ExpData data : run.getDataOutputs())
        {
            if (null != data.getFile())
                outputDataArray.put(ExperimentJSONConverter.serializeData(data));
        }
        jsonObject.put(DATA_OUTPUTS, outputDataArray);

        JSONArray outputMaterialArray = new JSONArray();
        for (ExpMaterial material : run.getMaterialOutputs())
        {
            outputMaterialArray.put(ExperimentJSONConverter.serializeMaterial(material));
        }
        jsonObject.put(MATERIAL_OUTPUTS, outputMaterialArray);

        return jsonObject;
    }


    public static JSONObject serializeStandardProperties(ExpObject object, List<? extends DomainProperty> properties)
    {
        JSONObject jsonObject = new JSONObject();

        // Standard properties on all experiment objects
        jsonObject.put(NAME, object.getName());
        jsonObject.put(LSID, object.getLSID());
        jsonObject.put(ID, object.getRowId());
        if (object.getCreatedBy() != null)
        {
            jsonObject.put(CREATED_BY, object.getCreatedBy().getEmail());
        }
        jsonObject.put(CREATED, object.getCreated());
        if (object.getModifiedBy() != null)
        {
            jsonObject.put(MODIFIED_BY, object.getModifiedBy().getEmail());
        }
        jsonObject.put(MODIFIED, object.getModified());
        String comment = object.getComment();
        if (comment != null)
            jsonObject.put(COMMENT, object.getComment());

        // Add the custom properties
        if (properties != null)
        {
            JSONObject propertiesObject = new JSONObject();
            for (DomainProperty dp : properties)
            {
                Object value = object.getProperty(dp);
                if (dp.getPropertyDescriptor().getPropertyType() == PropertyType.FILE_LINK && value instanceof File)
                {
                    // We need to return files not as simple string properties with the path, but as an Exp.Data object
                    // with multiple values
                    File f = (File)value;
                    ExpData data = ExperimentService.get().getExpDataByURL(f, object.getContainer());
                    if (data != null)
                    {
                        // If we can find a row in the data table, return that
                        value = serializeData(data);
                    }
                    else
                    {
                        // Otherwise, return a subset of all the data fields that we know about
                        JSONObject jsonFile = new JSONObject();
                        jsonFile.put(ABSOLUTE_PATH, f.getAbsolutePath());
                        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(object.getContainer());
                        if (pipeRoot != null)
                        {
                            jsonFile.put(PIPELINE_PATH, pipeRoot.relativePath(f));
                        }
                        value = jsonFile;
                    }
                }
                propertiesObject.put(dp.getName(), value);
            }
            jsonObject.put(PROPERTIES, propertiesObject);
        }

        return jsonObject;
    }


    public static JSONObject serializeData(ExpData data)
    {
        JSONObject jsonObject = serializeStandardProperties(data, null);
        jsonObject.put(DATA_FILE_URL, data.getDataFileUrl());
        File f = data.getFile();
        if (f != null)
        {
            jsonObject.put(ABSOLUTE_PATH, f.getAbsolutePath());
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(data.getContainer());
            if (pipeRoot != null)
            {
                jsonObject.put(PIPELINE_PATH, pipeRoot.relativePath(f));
            }
        }
        jsonObject.put(DATA_FILE_URL, data.getDataFileUrl());
        return jsonObject;
    }

    public static JSONObject serializeMaterial(ExpMaterial material)
    {
        ExpSampleSet sampleSet = material.getSampleSet();

        JSONObject jsonObject;
        if (sampleSet == null)
        {
            jsonObject = serializeStandardProperties(material, null);
        }
        else
        {
            jsonObject = serializeStandardProperties(material, sampleSet.getPropertiesForType());
            if (sampleSet.hasNameAsIdCol())
            {
                JSONObject properties = jsonObject.optJSONObject(ExperimentJSONConverter.PROPERTIES);
                if (properties == null)
                    properties = new JSONObject();
                properties.put(ExperimentJSONConverter.NAME, material.getName());
                jsonObject.put(ExperimentJSONConverter.PROPERTIES, properties);
            }

            JSONObject sampleSetJson = serializeStandardProperties(sampleSet, null);
            jsonObject.put(SAMPLE_SET, sampleSetJson);
        }

        return jsonObject;
    }

    public static Map<DomainProperty, Object> convertProperties(JSONObject propertiesJsonObject, List<? extends DomainProperty> dps, Container container, boolean ignoreMissingProperties)
    {
        Map<DomainProperty, Object> properties = new HashMap<>();
        for (DomainProperty dp : dps)
        {
            if (propertiesJsonObject.has(dp.getName()))
            {
                Class javaType = dp.getPropertyDescriptor().getPropertyType().getJavaType();
                Object convertedValue = ConvertUtils.lookup(javaType).convert(javaType, propertiesJsonObject.get(dp.getName()));

                // We need to special case Files to apply extra checks based on the context from which they're being
                // referenced
                if (javaType.equals(File.class) && convertedValue instanceof File)
                {
                    File f = (File)convertedValue;
                    PipeRoot root = PipelineService.get().getPipelineRootSetting(container);
                    FileContentService fileService = ServiceRegistry.get().getService(FileContentService.class);
                    File fileRoot = fileService == null ? null : fileService.getFileRoot(container);
                    boolean acceptableFile = (root != null && root.isUnderRoot((File)convertedValue)) || (fileRoot != null && URIUtil.isDescendant(fileRoot.toURI(), f.toURI()));
                    if (!acceptableFile)
                    {
                        // Don't let an assay reference a file that doesn't fall under this container's file root
                        // and/or pipeline root.
                        // This would be a security problem.
                        convertedValue = null;
                    }
                }
                
                properties.put(dp, convertedValue);
            }
            else
            {
                if (!ignoreMissingProperties)
                {
                    properties.put(dp, null);
                }
            }
        }
        return properties;
    }
}
