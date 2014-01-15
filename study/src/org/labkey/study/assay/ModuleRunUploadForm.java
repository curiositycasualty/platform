/*
 * Copyright (c) 2009-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.assay;

import org.json.JSONObject;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.ModuleRunUploadContext;
import org.labkey.api.study.assay.TsvDataHandler;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is a utility class to help with validation/transformation of uploaded
 * data for module based assays.
 *
 * User: klum
 * Date: Apr 29, 2009
 */
public class ModuleRunUploadForm extends AssayRunUploadForm<ModuleAssayProvider> implements ModuleRunUploadContext<ModuleAssayProvider>
{
    JSONObject _runJsonObject;
    List<Map<String, Object>> _uploadedData;

    private Map<ExpData, String> inputDatas = new HashMap<>();
    private Map<ExpData, String> outputDatas = new HashMap<>();
    private Map<ExpMaterial, String> inputMaterials = new HashMap<>();
    private Map<ExpMaterial, String> outputMaterials = new HashMap<>();

    public ModuleRunUploadForm(ViewContext context, int protocolId, JSONObject jsonObject, List<Map<String, Object>> uploadedData)
    {
        _runJsonObject = jsonObject;
        _uploadedData = uploadedData;

        setViewContext(context);
        setRowId(protocolId);
    }

    @Override
    public Map<DomainProperty, String> getRunProperties() throws ExperimentException
    {
        if (_runProperties == null)
        {
            AssayProvider provider = AssayService.get().getProvider(getProtocol());
            _runProperties = new HashMap<>();

            if (_runJsonObject.has(ExperimentJSONConverter.PROPERTIES))
            {
                for (Map.Entry<DomainProperty, Object> entry : ExperimentJSONConverter.convertProperties(_runJsonObject.getJSONObject(ExperimentJSONConverter.PROPERTIES),
                        provider.getRunDomain(getProtocol()).getProperties(), getContainer(), false).entrySet())
                {
                    Object o = entry.getValue();
                    _runProperties.put(entry.getKey(), o == null ? null : String.valueOf(o));
                }
            }
        }
        return Collections.unmodifiableMap(_runProperties);
    }

    @Override
    public Map<DomainProperty, String> getBatchProperties()
    {
        if (_uploadSetProperties == null)
        {
            AssayProvider provider = AssayService.get().getProvider(getProtocol());
            _uploadSetProperties = new HashMap<>();

            if (_runJsonObject.has(ExperimentJSONConverter.PROPERTIES))
            {
                for (Map.Entry<DomainProperty, Object> entry : ExperimentJSONConverter.convertProperties(_runJsonObject.getJSONObject(ExperimentJSONConverter.PROPERTIES),
                        provider.getBatchDomain(getProtocol()).getProperties(), getContainer(), false).entrySet())
                {
                    Object o = entry.getValue();
                    _uploadSetProperties.put(entry.getKey(), o == null ? null : String.valueOf(o));
                }
            }
        }
        return Collections.unmodifiableMap(_uploadSetProperties);
    }

    public List<Map<String, Object>> getRawData()
    {
        return _uploadedData;
    }

    @Override
    public Map<ExpData, String> getInputDatas()
    {
        return inputDatas;
    }

    @Override
    public void setInputDatas(Map<ExpData, String> inputDatas)
    {
        this.inputDatas = inputDatas;
    }

    @Override
    public Map<ExpData, String> getOutputDatas()
    {
        return outputDatas;
    }

    @Override
    public void setOutputDatas(Map<ExpData, String> outputDatas)
    {
        this.outputDatas = outputDatas;
    }

    @Override
    public Map<ExpMaterial, String> getInputMaterials()
    {
        return inputMaterials;
    }

    @Override
    public void setInputMaterials(Map<ExpMaterial, String> inputMaterials)
    {
        this.inputMaterials = inputMaterials;
    }

    @Override
    public Map<ExpMaterial, String> getOutputMaterials()
    {
        return outputMaterials;
    }

    @Override
    public void setOutputMaterials(Map<ExpMaterial, String> outputMaterials)
    {
        this.outputMaterials = outputMaterials;
    }

    @Override
    public void importResultData(ExpRun run, Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, List<ExpData> insertedDatas) throws ExperimentException, ValidationException
    {
        insertedDatas.addAll(outputDatas.keySet());
        List<Map<String, Object>> rawData = getRawData();

        for (ExpData insertedData : insertedDatas)
        {
            TsvDataHandler dataHandler = new TsvDataHandler();
            dataHandler.setAllowEmptyData(true);
            dataHandler.importRows(insertedData, getUser(), run, getProtocol(), getProvider(), rawData);
        }
    }

    @Override
    public void addDataAndMaterials(Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, Map<ExpMaterial, String> inputMaterials, Map<ExpMaterial, String> outputMaterials)
    {
        if (!getInputDatas().isEmpty())
            inputDatas.putAll(getInputDatas());

        if (!getOutputDatas().isEmpty())
            outputDatas.putAll(getOutputDatas());

        if (!getInputMaterials().isEmpty())
            inputMaterials.putAll(getInputMaterials());

        if (!getOutputMaterials().isEmpty())
            outputMaterials.putAll(getOutputMaterials());
    }
}
