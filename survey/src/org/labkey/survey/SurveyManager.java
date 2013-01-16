/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

package org.labkey.survey;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JsonWriter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SurveyManager
{
    private static final SurveyManager _instance = new SurveyManager();

    private SurveyManager()
    {
        // prevent external construction with a private default constructor
    }

    public static SurveyManager get()
    {
        return _instance;
    }

    @Nullable
    public JSONObject createSurveyTemplate(ViewContext context, String schemaName, String queryName)
    {
        BindException errors = new NullSafeBindException(this, "form");
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), schemaName);
        Map<String, Object> survey = new HashMap<String, Object>();

        if (schema != null)
        {
            QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, queryName);
            QueryView view = schema.createView(context, settings, errors);

            if (view != null)
            {
                survey.put("layout", "auto");
                survey.put("showCounts", false); // whether or not to show the count of completed questions next to the section header

                Map<String, Object> panel = new HashMap<String, Object>();

                panel.put("title", queryName);
                panel.put("description", null);
                panel.put("header", true);
                panel.put("collapsible", true);
                panel.put("defaultLabelWidth", 350);

                List<Map<String, Object>> columns = new ArrayList<Map<String, Object>>();
                for (DisplayColumn dc : view.getDisplayColumns())
                {
                    if (dc.isQueryColumn())
                    {
                        Map<String, Object> metaDataMap = JsonWriter.getMetaData(dc, null, false, true, false);
                        Map<String, Object> trimmedMap = getTrimmedMetaData(metaDataMap);

                        // set defaults for the survey questions
                        trimmedMap.put("width", 800);

                        columns.add(trimmedMap);
                    }
                }
                panel.put("questions", columns);

                survey.put("sections", Collections.singletonList(panel));
            }
        }
        return new JSONObject(survey);
    }

    public List<String> getKeyMetaDataProps()
    {
        return Arrays.asList("name", "caption", "shortCaption", "hidden", "jsonType", "inputType", "lookup");
    }

    public Map<String, Object> getTrimmedMetaData(Map<String, Object> origMap)
    {
        // trim the metadata property map to just those properties needed for rendering the Survey questions
        List<String> props = getKeyMetaDataProps();
        Map<String, Object> trimmedMap = new LinkedHashMap<String, Object>();
        for (String property : props)
        {
            if (origMap.get(property) != null)
                trimmedMap.put(property, origMap.get(property));
        }
        return trimmedMap;
    }

    public SurveyDesign saveSurveyDesign(Container container, User user, SurveyDesign survey)
    {
        DbScope scope = SurveySchema.getInstance().getSchema().getScope();

        try {
            scope.ensureTransaction();

            SurveyDesign ret;
            if (survey.isNew())
            {
                survey.beforeInsert(user, container.getId());
                ret = Table.insert(user, SurveySchema.getInstance().getSurveyDesignsTable(), survey);
            }
            else
                ret = Table.update(user, SurveySchema.getInstance().getSurveyDesignsTable(), survey, survey.getRowId());

            scope.commitTransaction();
            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public Survey saveSurvey(Container container, User user, Survey survey)
    {
        DbScope scope = SurveySchema.getInstance().getSchema().getScope();

        try {
            scope.ensureTransaction();

            TableInfo table = SurveySchema.getInstance().getSurveysTable();
            table.setAuditBehavior(AuditBehaviorType.DETAILED);

            Survey ret;
            if (survey.isNew())
            {
                survey.beforeInsert(user, container.getId());
                ret = Table.insert(user, table, survey);
            }
            else
                ret = Table.update(user, table, survey, survey.getRowId());

            scope.commitTransaction();
            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public SurveyDesign getSurveyDesign(Container container, User user, int surveyId)
    {
        return new TableSelector(SurveySchema.getInstance().getSurveyDesignsTable(), new SimpleFilter("rowId", surveyId), null).getObject(SurveyDesign.class);
    }

    public SurveyDesign[] getSurveyDesigns(Container container)
    {
        return new TableSelector(SurveySchema.getInstance().getSurveyDesignsTable(), new SimpleFilter(FieldKey.fromParts("container"), container), null).getArray(SurveyDesign.class);
    }

    public Survey getSurvey(Container container, User user, int rowId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("container"), container);
        filter.addCondition(FieldKey.fromParts("rowId"), rowId);
        return new TableSelector(SurveySchema.getInstance().getSurveysTable(), filter, null).getObject(Survey.class);
    }

    public Survey[] getSurveys(Container container)
    {
        return new TableSelector(SurveySchema.getInstance().getSurveysTable(), new SimpleFilter(FieldKey.fromParts("container"), container), null).getArray(Survey.class);
    }

    // delete all survey designs and survey instances in this container
    public void delete(Container c)
    {
        SurveySchema s = SurveySchema.getInstance();
        SqlExecutor executor = new SqlExecutor(s.getSchema());

        SQLFragment deleteSurveysSql = new SQLFragment("DELETE FROM ");
        deleteSurveysSql.append(s.getSurveysTable().getSelectName()).append(" WHERE Container = ?").add(c);
        executor.execute(deleteSurveysSql);

        SQLFragment deleteSurveyDesignsSql = new SQLFragment("DELETE FROM ");
        deleteSurveyDesignsSql.append(s.getSurveyDesignsTable().getSelectName()).append(" WHERE Container = ?").add(c);
        executor.execute(deleteSurveyDesignsSql);
    }

    /**
     * Deletes a specified survey design
     * @param c
     * @param user
     * @param surveyId
     * @param deleteSurveyInstances - true to delete survey instances of this design
     */
    public void deleteSurveyDesign(Container c, User user, int surveyId, boolean deleteSurveyInstances)
    {
        DbScope scope = SurveySchema.getInstance().getSchema().getScope();

        try {
            scope.ensureTransaction();

            SurveySchema s = SurveySchema.getInstance();
            SqlExecutor executor = new SqlExecutor(s.getSchema());

            if (deleteSurveyInstances)
            {
                SQLFragment deleteSurveysSql = new SQLFragment("DELETE FROM ");
                deleteSurveysSql.append(s.getSurveysTable().getSelectName()).append(" WHERE SurveyDesignId = ?").add(surveyId);
                executor.execute(deleteSurveysSql);
            }
            SQLFragment deleteSurveyDesignsSql = new SQLFragment("DELETE FROM ");
            deleteSurveyDesignsSql.append(s.getSurveyDesignsTable().getSelectName()).append(" WHERE RowId = ?").add(surveyId);
            executor.execute(deleteSurveyDesignsSql);

            scope.commitTransaction();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testGetMetaDataForColumn()
        {
            SurveyManager sm = SurveyManager.get();
            ColumnInfo ci = new ColumnInfo("test");
            DisplayColumn dc = new DataColumn(ci, false);
            Map<String, Object> metaDataMap = JsonWriter.getMetaData(dc, null, false, true, false);
            Map<String, Object> trimmedMap = sm.getTrimmedMetaData(metaDataMap);

            // we may not have all of the key properties in our trimmed map, but we shouldn't have any extras
            List<String> props = sm.getKeyMetaDataProps();
            for (String key : trimmedMap.keySet())
            {
                assertTrue("Unexpected property in the trimmed metadata map", props.contains(key));
            }

            // check a few of the key properties
            assertTrue("Unexpected property value", trimmedMap.get("name").equals("test"));
            assertTrue("Unexpected property value", trimmedMap.get("caption").equals("Test"));
            assertTrue("Unexpected property value", trimmedMap.get("shortCaption").equals("Test"));
            assertTrue("Unexpected property value", trimmedMap.get("hidden").equals(false));
            assertTrue("Unexpected property value", trimmedMap.get("jsonType").equals("string"));
            assertTrue("Unexpected property value", trimmedMap.get("inputType").equals("text"));
        }
    }
}