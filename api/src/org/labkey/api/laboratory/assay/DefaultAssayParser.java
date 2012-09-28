/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.laboratory.assay;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/15/12
 * Time: 10:37 AM
 */
public class DefaultAssayParser implements AssayParser
{
    protected boolean _hasHeaders = true;
    protected JSONObject _jsonData;
    protected ExpProtocol _protocol;
    protected AssayProvider _provider;
    protected Container _container;
    protected User _user;
    protected AssayImportMethod _method;

    private static final Logger _log = Logger.getLogger(AssayParser.class);

    public DefaultAssayParser(AssayImportMethod method, Container c, User u, int assayId, JSONObject formData)
    {
        _method = method;
        _container = c;
        _user = u;
        populateProtocolProvider(assayId);

        _jsonData = formData;
    }

    protected TabLoader getTabLoader(String contents) throws IOException
    {
        return new TabLoader(new StringReader(contents), _hasHeaders);
    }

    protected Map<String, PropertyDescriptor> getPropertyMap(Map<String, DomainProperty> importMap)
    {
        Map<String, PropertyDescriptor> map = new CaseInsensitiveHashMap<PropertyDescriptor>(importMap.size());
        Set<PropertyDescriptor> seen = new HashSet<PropertyDescriptor>(importMap.size());
        for (Map.Entry<String, DomainProperty> entry : importMap.entrySet())
        {
            PropertyDescriptor pd = entry.getValue().getPropertyDescriptor();
            if (!seen.contains(pd))
            {
                String description = pd.getDescription();
                if (description != null && description.length() > 0)
                    map.put(description.toLowerCase(), pd);
                seen.add(pd);
            }
            map.put(entry.getKey(), pd);
        }
        return map;
    }

    public List<Map<String, Object>> parseResultFile(File inputFile, ExpProtocol protocol) throws ExperimentException, ValidationException
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        Domain dataDomain = provider.getResultsDomain(protocol);
        Map<String, DomainProperty> importMap = dataDomain.createImportMap(false);
        Map<String, PropertyDescriptor> propertyNameToDescriptor = getPropertyMap(importMap);

        try
        {
            String sb = readRawFile(inputFile);
            TabLoader loader = getTabLoader(sb);
            configureColumns(propertyNameToDescriptor, loader);
            List<Map<String, Object>> rows = loader.load();
            rows = processRowsFromFile(rows);
            return rows;
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }

    protected List<Map<String, Object>> processRowsFromFile(List<Map<String, Object>> rows) throws ValidationException
    {
        ListIterator<Map<String, Object>> rowsIter = rows.listIterator();
        while (rowsIter.hasNext())
        {
            Map<String, Object> row = rowsIter.next();
            appendPromotedResultFields(row);
        }

        return rows;
    }

    protected void appendPromotedResultFields(Map<String, Object> row)
    {
        if (_jsonData != null && _jsonData.has("Results"))
        {
            JSONObject resultData = _jsonData.getJSONObject("Results");
            for (String prop : resultData.keySet())
            {
                row.put(prop, resultData.get(prop));
            }
        }
    }

    /**
     * Reads the raw input file and provides some basic normalization before passing to TabLoader
     * @param inputFile
     * @return
     * @throws FileNotFoundException
     * @throws ExperimentException
     */
    protected String readRawFile(File inputFile) throws FileNotFoundException, ExperimentException
    {
        Reader fileReader = null;
        try
        {
            fileReader = new FileReader(inputFile);
            StringBuffer sb = new StringBuffer((int)(inputFile.length()));

            // replace "n/a" with "0"
            BufferedReader br = new BufferedReader(fileReader);
            String line;
            Pattern p = Pattern.compile("\\bn/a\\b");
            while (null != (line = br.readLine()))
            {
                line = p.matcher(line).replaceAll("0");
                if (!StringUtils.isEmpty(line))
                    sb.append(line).append("\n");
            }

            return sb.toString();

        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
        finally
        {
            try { if (fileReader != null) fileReader.close(); } catch (IOException e) {}
        }
    }

    protected void configureColumns(Map<String, PropertyDescriptor> propertyNameToDescriptor, TabLoader loader) throws IOException
    {
        for (ColumnDescriptor column : loader.getColumns())
        {
            String columnName = column.name.toLowerCase();
            PropertyDescriptor pd = propertyNameToDescriptor.get(columnName);
            if (pd != null)
            {
                column.clazz = pd.getPropertyType().getJavaType();
                if (!columnName.equals(pd.getName()))
                    column.name = pd.getName();
            }
            else
            {
                _log.info("skipping column: " + column.name);
            }
        }
    }

    public JSONObject getPreview(JSONObject json, File file, String fileName, ViewContext ctx) throws ValidationException, ExperimentException
    {
        JSONObject ret = new JSONObject();
        JSONArray batches = new JSONArray();
        JSONObject batch = new JSONObject();
        batch.put("properties", json.get("Batch"));

        JSONArray runs = new JSONArray();
        JSONObject run = new JSONObject();
        run.put("properties", json.get("Run"));
        run.put("results", parseResults(json, file));
        runs.put(run);
        batch.put("runs", runs);

        batches.put(batch);
        ret.put("batches", batches);
        ret.put("importMethod", _method.getName());

        return ret;
    }

    /**
     * This is the primary method where the JSON and raw results file are parsed to produce a list of row maps
     * @param json
     * @param file
     * @return
     * @throws ValidationException
     * @throws ExperimentException
     */
    public List<Map<String, Object>> parseResults(JSONObject json, File file) throws ValidationException, ExperimentException
    {
        List<Map<String, Object>> rows;
        if (json.has("ResultRows"))
        {
            JSONArray resultRows = json.getJSONArray("ResultRows");
            rows = resultRows.toMapList();
            rows = processRowsFromJson(rows, json);
        }
        else
        {
            rows = parseResultFile(file, _protocol);
        }

        return processRows(rows, json);
    }

    /**
     * Override this method to provide custom processing of each result row provided as JSON
     * @param rows
     * @param json
     * @return
     */
    public List<Map<String, Object>> processRowsFromJson(List<Map<String, Object>> rows, JSONObject json)
    {
        return rows;
    }

    /**
     * Override this method to transform rows after the file/json has been processed
     * @param rows
     * @param json
     * @return
     */
    public List<Map<String, Object>> processRows(List<Map<String, Object>> rows, JSONObject json)
    {
        return rows;
    }

    public void saveBatch(JSONObject json, File file, String fileName, ViewContext ctx) throws ValidationException, ExperimentException
    {
        LaboratoryService.get().saveAssayBatch(this, json, file, fileName, ctx);
    }

    protected void populateProtocolProvider(int assayId)
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(assayId);
        if (protocol == null)
        {
            throw new NotFoundException("Could not find assay id " + assayId);
        }

        List<ExpProtocol> availableAssays = AssayService.get().getAssayProtocols(_container);
        if (!availableAssays.contains(protocol))
        {
            throw new NotFoundException("Assay id " + assayId + " is not visible for folder " + _container);
        }

        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
        {
            throw new NotFoundException("Could not find assay provider for assay id " + assayId);
        }

        _provider = provider;
        _protocol = protocol;
    }

    public ExpProtocol getProtocol()
    {
        return _protocol;
    }

    public AssayProvider getProvider()
    {
        return _provider;
    }

}
