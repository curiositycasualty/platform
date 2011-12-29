/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
package org.labkey.study.model;

import org.apache.commons.lang3.ObjectUtils;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.CohortFilter;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.CohortController;

import javax.servlet.ServletException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: May 13, 2009
 * Time: 10:21:20 AM
 */
public class CohortManager
{
    private static CohortManager _instance;

    private CohortManager()
    {
    }

    public static synchronized CohortManager getInstance()
    {
        if (_instance == null)
            _instance = new CohortManager();
        return _instance;
    }

    public void setAutomaticCohortAssignment(StudyImpl study, User user, Integer participantCohortDataSetId, String participantCohortProperty, boolean advancedCohorts, boolean reassignParticipants) throws SQLException
    {
        study = study.createMutable();

        study.setManualCohortAssignment(false);
        study.setAdvancedCohorts(advancedCohorts);
        study.setParticipantCohortDataSetId(participantCohortDataSetId);
        study.setParticipantCohortProperty(participantCohortProperty);
        StudyManager.getInstance().updateStudy(user, study);
        if (reassignParticipants)
            updateParticipantCohorts(user, study);
    }

    public void setManualCohortAssignment(StudyImpl study, User user, Map<String, Integer> p2c) throws SQLException
    {
        Participant[] participants = StudyManager.getInstance().getParticipants(study);

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.ensureTransaction();

            if (!study.isManualCohortAssignment())
            {
                study = study.createMutable();
                study.setManualCohortAssignment(true);
                study.setAdvancedCohorts(false);
                StudyManager.getInstance().updateStudy(user, study);
            }

            clearParticipantCohorts(study);

            for (Participant p : participants)
            {
                Integer newCohortId = p2c.get(p.getParticipantId());

                if (newCohortId != null && newCohortId.intValue() >= 0)
                {
                    p.setInitialCohortId(newCohortId);
                    p.setCurrentCohortId(newCohortId);
                    StudyManager.getInstance().updateParticipant(user, p);

                    SQLFragment ptidVisitSql = new SQLFragment("UPDATE " + StudySchema.getInstance().getTableInfoParticipantVisit() +
                            " SET CohortId = ? WHERE ParticipantId = ? AND Container = ?", newCohortId, p.getParticipantId(), study.getContainer());

                    Table.execute(StudySchema.getInstance().getSchema(), ptidVisitSql);
                }
            }
            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }
    }


    // TODO: Check for null label here?
    public CohortImpl createCohort(Study study, User user, String newLabel) throws ServletException, SQLException
    {
        CohortImpl cohort = new CohortImpl();

        // Check if there's a conflict
        Cohort existingCohort = StudyManager.getInstance().getCohortByLabel(study.getContainer(), user, newLabel);

        if (existingCohort != null)
            throw new ServletException("A cohort with the label '" + newLabel + "' already exists");

        cohort.setLabel(newLabel);

        StudyManager.getInstance().createCohort(study, user, cohort);

        return cohort;
    }

    public CohortImpl ensureCohort(Study study, User user, String newLabel) throws ServletException, SQLException
    {
        CohortImpl existingCohort = StudyManager.getInstance().getCohortByLabel(study.getContainer(), user, newLabel);
        if (existingCohort != null)
            return existingCohort;
        else
            return createCohort(study, user, newLabel);
    }

    public ActionButton createCohortButton(ViewContext context, CohortFilter currentCohortFilter) throws ServletException
    {
        Container container = context.getContainer();
        User user = context.getUser();
        if (StudyManager.getInstance().showCohorts(container, user))
        {
            CohortImpl[] cohorts = StudyManager.getInstance().getCohorts(container, user);
            if (cohorts.length > 0)
            {
                MenuButton button = new MenuButton("Cohorts");
                ActionURL allCohortsURL = CohortFilter.clearURLParameters(context.cloneActionURL());
                NavTree item = new NavTree("All", allCohortsURL.toString());
                item.setId(button.getCaption() + ":" + item.getKey());
                if (currentCohortFilter == null)
                    item.setSelected(true);
                button.addMenuItem(item);

                NavTree btnNavTree = button.getPopupMenu().getNavTree();
                addCohortNavTree(container, user, context.getActionURL(), currentCohortFilter, btnNavTree);

                if (container.hasPermission(user, AdminPermission.class))
                {
                    button.addSeparator();
                    button.addMenuItem("Manage Cohorts", new ActionURL(CohortController.ManageCohortsAction.class, container));
                }
                return button;
            }
        }
        return null;
    }

    public boolean hasCohortMenu(Container container, User user)
    {
        if (StudyManager.getInstance().showCohorts(container, user))
        {
            CohortImpl[] cohorts = StudyManager.getInstance().getCohorts(container, user);
            return cohorts.length > 0;
        }
        return false;
    }

    public void addCohortNavTree(Container container, User user, ActionURL baseURL, CohortFilter currentCohortFilter, NavTree tree)
    {
        CohortImpl[] cohorts = StudyManager.getInstance().getCohorts(container, user);
        if (cohorts.length > 0)
        {
            String caption = "Cohorts";
            Study study = StudyManager.getInstance().getStudy(container);

            if (study.isAdvancedCohorts())
            {
                for (CohortImpl cohort : cohorts)
                {
                    NavTree item = new NavTree(cohort.getLabel());
                    item.setId(caption + ":" + cohort.getLabel());

                    for (CohortFilter.Type type : CohortFilter.Type.values())
                    {
                        CohortFilter filter = new CohortFilter(type, cohort.getRowId());
                        ActionURL url = filter.addURLParameters(baseURL.clone());

                        NavTree typeItem = new NavTree(type.getTitle(), url.toString());
                        typeItem.setId(cohort.getLabel() + ":" + typeItem.getKey());
                        if (filter.equals(currentCohortFilter))
                            typeItem.setSelected(true);

                        item.addChild(typeItem);
                    }
                    tree.addChild(item);
                }
            }
            else
            {
                for (CohortImpl cohort : cohorts)
                {
                    CohortFilter filter = new CohortFilter(CohortFilter.Type.PTID_CURRENT, cohort.getRowId());
                    ActionURL url = filter.addURLParameters(baseURL.clone());
                    NavTree item = new NavTree(cohort.getLabel(), url.toString());
                    item.setId(caption + ":" + item.getKey());
                    if (filter.equals(currentCohortFilter))
                        item.setSelected(true);
                    tree.addChild(item);
                }
            }
        }
    }

    public void clearParticipantCohorts(Study study) throws SQLException
    {
        // null out cohort for all participants in this container:
        Table.execute(StudySchema.getInstance().getSchema(), "UPDATE " + StudySchema.getInstance().getTableInfoParticipant() +
                " SET InitialCohortId = NULL, CurrentCohortId = NULL WHERE Container = ?", study.getContainer().getId());

        // null out cohort for all participant/visits in this container (required for advanced cohort support, where participants
        // can change cohorts over time):
        Table.execute(StudySchema.getInstance().getSchema(), "UPDATE " + StudySchema.getInstance().getTableInfoParticipantVisit() +
                " SET CohortId = NULL WHERE Container = ?", study.getContainer().getId());

    }


    public void updateParticipantCohorts(User user, StudyImpl study) throws SQLException, UnauthorizedException
    {
        if (study.isManualCohortAssignment() ||
                study.getParticipantCohortDataSetId() == null ||
                study.getParticipantCohortProperty() == null)
        {
            return;
        }
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.ensureTransaction();

            DataSetDefinition dataset = null;
            if (study.getParticipantCohortDataSetId() != null)
                dataset = study.getDataSet(study.getParticipantCohortDataSetId().intValue());

            if (null != dataset)
            {
                TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
                TableInfo cohortDatasetTinfo = dataset.getTableInfo(user, false);

                //TODO: Use Property URI & Make sure this is set properly
                ColumnInfo cohortLabelCol = cohortDatasetTinfo.getColumn(study.getParticipantCohortProperty());
                if (null != cohortLabelCol)
                {
                    clearParticipantCohorts(study);

                    // Find the set of cohorts specified in our dataset
                    SQLFragment sqlFragment = new SQLFragment();
                    sqlFragment.append("SELECT DISTINCT ").append(cohortLabelCol.getValueSql("CO")).append("\nFROM ");
                    sqlFragment.append(cohortDatasetTinfo.getFromSQL("CO")).append("\n" +
                            "WHERE ").append(cohortLabelCol.getValueSql("CO")).append(" IS NOT NULL AND ").append(cohortLabelCol.getValueSql("CO")).append(" NOT IN\n" +
                            "  (SELECT Label FROM " + StudySchema.getInstance().getTableInfoCohort() + " WHERE Container = ?)");
                    sqlFragment.add(study.getContainer().getId());

                    Set<String> newCohortLabels = new HashSet<String>();
                    Table.TableResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sqlFragment);
                    try
                    {
                        while (rs.next())
                        {
                            newCohortLabels.add(rs.getString(1));
                        }
                    }
                    finally
                    {
                        rs.close();
                    }

                    for (String cohortLabel : newCohortLabels)
                    {
                        CohortImpl cohort = new CohortImpl();
                        cohort.setLabel(cohortLabel);
                        StudyManager.getInstance().createCohort(study, user, cohort);
                    }

                    updateCohorts(user, study, tableParticipant, dataset, cohortLabelCol);

                    scope.commitTransaction();

                    // aggressively uncache study data (including cached participants) whenever
                    // cohorts may have changed:
                    StudyManager.getInstance().clearCaches(study.getContainer(), false);
                }
            }
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private SQLFragment getParticipantVisitCohortSql(User user, StudyImpl study, DataSetDefinition dsd, ColumnInfo cohortLabelCol)
    {
        // The following SQL will return a list of all participant/visit combinations, ordered by participant and sub-ordered by chronological
        // visit order.  There will be a column for cohort assignment, if available in the cohort dataset.  For example:
        //
        // ParticipantId;Visit;Cohort
        // "NegativeThroughout";0;"Negative"
        // "NegativeThroughout";1;null
        // "NegativeThroughout";2;null
        // "NegativeThroughout";3;null
        // "NegativeUntil2";0;"Negative"
        // "NegativeUntil2";1;null
        // "NegativeUntil2";2;"Positive"
        // "NegativeUntil2";3;null
        //
        // In this case, participant "NegativeThroughout" is assigned to the negative cohort in visit 0, after which the
        // assignment never changes.  Participant "NegativeUntil2" starts out negative, then switches to positive in visit
        // 2.  The following code uses this information to fill in the blanks between assignment changes, saving a cohort
        // assignment for every known participant/visit combination based on the results of this query.
        DataSetDefinition.DatasetSchemaTableInfo cohortDatasetTinfo = dsd.getTableInfo(user, false);
        ColumnInfo subjectCol = cohortDatasetTinfo.getParticipantColumn();
        SQLFragment pvCohortSql = new SQLFragment("SELECT PV.ParticipantId, PV.VisitRowId, PV.CohortId, " + cohortLabelCol.getValueSql("D") + "\n" +
                "FROM " + StudySchema.getInstance().getTableInfoParticipantVisit().getFromSQL("PV") + "\n" +
                "  LEFT OUTER JOIN " + StudySchema.getInstance().getTableInfoVisit().getFromSQL("V") + " ON PV.VisitRowId = V.RowId\n" +
                "  LEFT OUTER JOIN ").append(cohortDatasetTinfo.getFromSQL("D")).append(" ON " +
                    (!dsd.isDemographicData() ? "\tPV.SequenceNum = D.SequenceNum AND\n" : "") +
                    "\tPV.ParticipantId = " + subjectCol.getValueSql("D") + "\n" +
                "WHERE PV.Container = ? " + (study.getTimepointType() != TimepointType.VISIT  ? " AND PV.VisitDate IS NOT NULL" : "") + "\n" +
                "ORDER BY PV.ParticipantId, V.ChronologicalOrder, V.SequenceNumMin");
        pvCohortSql.add(study.getContainer());
        return pvCohortSql;
    }

    private SQLFragment getContinuousStudyCohortSql(User user, StudyImpl study, DataSetDefinition dsd, ColumnInfo cohortLabelCol)
    {
        // Continuous studies don't populate study.ParticipantVisit, so we have a simpler form of the SQL here.  This path
        // assumes that the study is continuous and that advanced cohort management (which allows subjects to change cohorts
        // over time) is not enabled.
        if (study.getTimepointType() != TimepointType.CONTINUOUS)
            throw new IllegalArgumentException("Only continuous studies should populate cohorts through this code path.");
        if (study.isAdvancedCohorts())
            throw new IllegalStateException("Continuous studies require simple cohort management");

        DataSetDefinition.DatasetSchemaTableInfo cohortDatasetTinfo = dsd.getTableInfo(user, false);
        ColumnInfo subjectCol = cohortDatasetTinfo.getParticipantColumn();
        SQLFragment pCohortSql = new SQLFragment("SELECT P.ParticipantId, -1 AS VisitRowId, -1 AS CohortId, " + cohortLabelCol.getValueSql("D") + "\n" +
                "FROM " + StudySchema.getInstance().getTableInfoParticipant().getFromSQL("P") + "\n" +
                "  LEFT OUTER JOIN ").append(cohortDatasetTinfo.getFromSQL("D")).append(" ON " +
                    "\tP.ParticipantId = " + subjectCol.getValueSql("D") + "\n" +
                "WHERE P.Container = ? " + "\n" +
                "ORDER BY P.ParticipantId" + (!dsd.isDemographicData() ? ", D.SequenceNumMin" : ""));
        pCohortSql.add(study.getContainer());
        return pCohortSql;
    }

    private void updateCohorts(User user, StudyImpl study, TableInfo tableParticipant, DataSetDefinition dsd, ColumnInfo cohortLabelCol)
    {
        SQLFragment cohortSql;
        // Continuous studies don't populate the participant/visit table, so we use simpler SQL (which doesn't support advanced cohort features)
        if (study.getTimepointType() != TimepointType.CONTINUOUS)
            cohortSql = getParticipantVisitCohortSql(user, study, dsd, cohortLabelCol);
        else
            cohortSql = getContinuousStudyCohortSql(user, study, dsd, cohortLabelCol);

        Map<String, Integer> initialCohortAssignments = new HashMap<String, Integer>();
        Map<String, Integer> currentCohortAssignments = new HashMap<String, Integer>();

        Set<List<Object>> participantVisitParams = new HashSet<List<Object>>();

        ResultSet rs = null;
        try
        {
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), cohortSql);
            String prevParticipantId = null;
            Integer prevCohortId = null;

            Map<String, Integer> cohortNameToId = new HashMap<String, Integer>();
            CohortImpl[] cohorts = StudyManager.getInstance().getCohorts(study.getContainer(), user);
            for (CohortImpl cohort : cohorts)
                cohortNameToId.put(cohort.getLabel(), cohort.getRowId());

            while (rs.next())
            {
                String participantId = rs.getString("ParticipantId");
                Integer visitRowId = (Integer) rs.getObject("VisitRowId");
                Integer assignedCohortId = (Integer) rs.getObject("CohortId");
                String newCohortLabel = rs.getString(cohortLabelCol.getName());
                Integer newCohortId = null;

                if (newCohortLabel != null)
                {
                    newCohortId = cohortNameToId.get(newCohortLabel);
                    if (newCohortId == null)
                        throw new IllegalStateException("Expected all newly named cohorts to be created.  Couldn't find: " + newCohortLabel);
                }

                // When we move on to the next participant, we null our current cohort; it's up to this
                // participant to identify their cohort in the resultset
                if (prevParticipantId == null || !prevParticipantId.equals(participantId))
                    prevCohortId = null;
                prevParticipantId = participantId;

                // if this visit doesn't specify its cohort, we'll assume it's the same as the previous visit:
                if (newCohortId == null)
                    newCohortId = prevCohortId;
                else
                    prevCohortId = newCohortId;

                // stash the most recent cohort ID; we'll use this to update the participant table later.
                currentCohortAssignments.put(participantId, newCohortId);
                if (newCohortId != null && !initialCohortAssignments.containsKey(participantId))
                    initialCohortAssignments.put(participantId, newCohortId);

                if (study.isAdvancedCohorts())
                {
                    // if this ptid/visit isn't already assigned to this cohort visit, we need to make the assignment now:
                    if (!ObjectUtils.equals(newCohortId, assignedCohortId))
                    {
                        List<Object> params = new ArrayList<Object>();
                        addCohortIdParameter(params, newCohortId);
                        params.add(participantId);
                        params.add(visitRowId);
                        params.add(study.getContainer().getId());
                        participantVisitParams.add(params);
                    }
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (rs != null)
                try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }

        Set<List<Object>> participantParams = new HashSet<List<Object>>();
        for (Map.Entry<String, Integer> cohortAssignment : currentCohortAssignments.entrySet())
        {
            List<Object> participantRow = new ArrayList<Object>();
            String participantId = cohortAssignment.getKey();
            Integer currentCohortId = cohortAssignment.getValue();

            // if this study uses advanced cohorts, we may have a different enrollment cohort.  Otherwise,
            // the initial cohort should always be the same as the current cohort:
            if (study.isAdvancedCohorts())
                addCohortIdParameter(participantRow, initialCohortAssignments.get(participantId));
            else
                addCohortIdParameter(participantRow, currentCohortId);

            addCohortIdParameter(participantRow, currentCohortId);
            participantRow.add(participantId);
            participantRow.add(study.getContainer().getId());
            participantParams.add(participantRow);

            if (!study.isAdvancedCohorts() && study.getTimepointType() != TimepointType.CONTINUOUS)
            {
                List<Object> participantVisitRow = new ArrayList<Object>();
                addCohortIdParameter(participantVisitRow, currentCohortId);
                participantVisitRow.add(participantId);
                participantVisitRow.add(study.getContainer().getId());
                participantVisitParams.add(participantVisitRow);
            }
        }

        // We've gathered all the necessary data; now we can update participant and participantvisit.
        // Note that some of the data gathered may not be used if we're not doing advanced cohort management.
        try
        {
            // update ParticipantVisit:
            if (!participantVisitParams.isEmpty())
            {
                String updateSql;
                if (study.isAdvancedCohorts())
                {
                    updateSql = "UPDATE " + StudySchema.getInstance().getTableInfoParticipantVisit() +
                            " SET CohortId = ? WHERE ParticipantId = ? AND VisitRowId = ? AND Container = ?";
                }
                else
                {
                    updateSql = "UPDATE " + StudySchema.getInstance().getTableInfoParticipantVisit() +
                            " SET CohortId = ? WHERE ParticipantId = ? AND Container = ?";
                }
                Table.batchExecute(StudySchema.getInstance().getSchema(), updateSql, participantVisitParams);
            }

            // update Participant:
            if (!participantParams.isEmpty())
            {
                String updateSql = "UPDATE " + tableParticipant +
                    " SET InitialCohortId = ?, CurrentCohortId = ? WHERE ParticipantId = ? AND Container = ?";
                Table.batchExecute(StudySchema.getInstance().getSchema(), updateSql, participantParams);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private void addCohortIdParameter(List<Object> parameters, Integer cohortId)
    {
        if (cohortId != null)
            parameters.add(cohortId);
        else
            parameters.add(Parameter.nullParameter(JdbcType.INTEGER));
    }
}

