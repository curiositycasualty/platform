<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
%>
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.study.StudyService"%>
<%@ page import="org.labkey.api.study.Visit"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.CohortFilter"%>
<%@ page import="org.labkey.study.controllers.BaseStudyController"%>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageStudyAction" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.controllers.samples.SpecimenController" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.CohortManager" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.QCStateSet" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.model.VisitMapKey" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager.VisitStatistic" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager.VisitStatistics" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.study.CohortFilterFactory" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyController.OverviewBean> me = (JspView<StudyController.OverviewBean>) HttpView.currentView();
    StudyController.OverviewBean bean = me.getModelBean();
    String contextPath = request.getContextPath();
    StudyImpl study = bean.study;
    Container container = study.getContainer();
    User user = (User) request.getUserPrincipal();
    StudyManager manager = StudyManager.getInstance();
    String visitsLabel = manager.getVisitManager(study).getPluralLabel();
    String subjectNoun = StudyService.get().getSubjectNounSingular(container);

    boolean showCohorts = CohortManager.getInstance().hasCohortMenu(container, user);
    CohortImpl selectedCohort = null;
    List<CohortImpl> cohorts = null;

    if (showCohorts)
    {
        selectedCohort = bean.cohortFilter != null ? bean.cohortFilter.getCohort(container, user) : null;
        cohorts = manager.getCohorts(container, user);
    }

    boolean showQCStates = manager.showQCStates(container);
    QCStateSet selectedQCStateSet = null;
    List<QCStateSet> qcStateSetOptions = null;

    if (showQCStates)
    {
        selectedQCStateSet = bean.qcStates;
        qcStateSetOptions = QCStateSet.getSelectableSets(container);
    }

    VisitStatistic[] statisticsToDisplay = bean.stats.toArray(new VisitStatistic[bean.stats.size()]);

    List<VisitImpl> visits = manager.getVisits(study, selectedCohort, user, Visit.Order.DISPLAY);
    DataSetDefinition[] datasets = manager.getDataSetDefinitions(study, selectedCohort);
    boolean cantReadOneOrMoreDatasets = false;
    String basePage = buildURL(StudyController.OverviewAction.class);

    if (selectedCohort != null)
        basePage += "cohortId=" + selectedCohort.getRowId() + "&";
    if (selectedQCStateSet != null)
        basePage += "QCState=" + selectedQCStateSet.getFormValue() + "&";

%><%= text(bean.canManage ? textLink("Manage Study", ManageStudyAction.class) : "") %>
&nbsp;<%= textLink("Views", new ActionURL(ReportsController.BeginAction.class, container))%>&nbsp;
&nbsp;<%= textLink("Specimens", new ActionURL(SpecimenController.BeginAction.class, container))%>&nbsp;
<%
    boolean hasHiddenData = false;
    for (int i = 0; i < visits.size() && !hasHiddenData; i++)
        hasHiddenData = !visits.get(i).isShowByDefault();
    for (int i = 0; i < datasets.length && !hasHiddenData; i++)
        hasHiddenData = !datasets[i].isShowByDefault();
    if (hasHiddenData)
    {
        String viewLink = bean.showAll ? textLink("Show Default Datasets", basePage) :
                textLink("Show All Datasets", basePage + "showAll=1");
        out.write(viewLink);
    }
%>
<form action="<%=h(buildURL(StudyController.OverviewAction.class))%>" name="changeFilterForm" method="GET">
    <input type="hidden" name="showAll" value="<%= text(bean.showAll ? "1" : "0") %>">
    <br><br>
<%
    if (showCohorts)
    {
%>
    <input type="hidden" name="<%= h(CohortFilterFactory.Params.cohortFilterType.name()) %>" value="<%= h(CohortFilter.Type.PTID_CURRENT.name()) %>">
    <%= h(subjectNoun) %>'s current cohort: <select name="<%= h(CohortFilterFactory.Params.cohortId.name()) %>" onchange="document.changeFilterForm.submit()">
    <option value="">All</option>
    <%
        for (CohortImpl cohort : cohorts)
        {
    %>
    <option value="<%= cohort.getRowId() %>" <%= text(selectedCohort != null && cohort.getRowId() == selectedCohort.getRowId() ? "SELECTED" : "") %>>
        <%= h(cohort.getLabel()) %>
    </option>
<%
        }
%>
    </select>
<%
    }
    if (showQCStates)
    {
%>
    QC State: <select name="<%= h(BaseStudyController.SharedFormParameters.QCState.name()) %>" onchange="document.changeFilterForm.submit()">
        <%
            for (QCStateSet set : qcStateSetOptions)
            {
        %>
        <option value="<%= h(set.getFormValue()) %>" <%= text(set.equals(selectedQCStateSet) ? "SELECTED" : "") %>>
            <%= h(set.getLabel()) %>
        </option>
        <%
            }
        %>
    </select>
    <%
    }

    for (VisitStatistic stat : VisitStatistic.values())
    {
        boolean checked = bean.stats.contains(stat);
        out.print(text("<input name=\"visitStatistic\" value=\"" + h(stat.name()) + "\" type=\"checkbox\"" + (checked ? " checked" : "") + " onclick=\"document.changeFilterForm.submit()\">" + h(stat.getDisplayString(study)) + "\n"));
    }
    %>
</form>
<br><br>
<table id="studyOverview" class="labkey-data-region labkey-show-borders" style="border-collapse:collapse;">
    <tr class="labkey-alternate-row">
        <td class="labkey-column-header"><img alt="" width=60 height=1 src="<%=h(contextPath)%>/_.gif"></td>
        <td class="labkey-column-header"><%
            String slash = "";
            for (VisitStatistic v : statisticsToDisplay)
            {
                %><%=text(slash)%><%
                if (v == VisitStatistic.ParticipantCount)
                {
                    %>All <%=h(visitsLabel)%><%
                }
                else
                {
                    %><%=h(v.getDisplayString(study))%><%
                }
                slash = " / ";
            }
        %></td><%

        for (VisitImpl visit : visits)
        {
            if (!bean.showAll && !visit.isShowByDefault())
                continue;
            String label = visit.getDisplayString();
            %><td class="labkey-column-header" align="center" valign="top"><%= h(label) %></td><%
        }
        %>
    </tr>
    <%
    int row = 0;
    VisitMapKey key = new VisitMapKey(0,0);
    String prevCategory = null;
    boolean useCategories = false;

    for (DataSetDefinition dataSet : datasets)
    {
        if (dataSet.getCategory() != null)
        {
            useCategories = true;
            break;
        }
    }

    Map<VisitMapKey, Boolean> requiredMap = StudyManager.getInstance().getRequiredMap(study);

    for (DataSetDefinition dataSet : datasets)
    {
        if (!bean.showAll && !dataSet.isShowByDefault())
            continue;

        boolean userCanRead = dataSet.canRead(user);

        if (!userCanRead)
            cantReadOneOrMoreDatasets = true;

        row++;
        key.datasetId = dataSet.getDataSetId();

        if (useCategories)
        {
            String category = dataSet.getCategory();
            if (category == null)
                category = "Uncategorized";
            if (!category.equals(prevCategory))
            {
                %><tr><td class="labkey-highlight-cell" align="left" colspan="<%= visits.size() + 2%>"><%= h(category) %></td></tr><%
            }
            prevCategory = category;
        }

        String dataSetLabel = (dataSet.getLabel() != null ? dataSet.getLabel() : "" + dataSet.getDataSetId());
        String className = row % 2 == 0 ? "labkey-alternate-row" : "labkey-row";
        %>
        <tr class="<%= text(className) %>" ><td align="center" class="labkey-row-header"><%= h(dataSetLabel) %><%
        if (null != StringUtils.trimToNull(dataSet.getDescription()))
        {
            %><%=PageFlowUtil.helpPopup(dataSetLabel, dataSet.getDescription())%><%
        }
        %></td>
        <td style="font-weight:bold;" align="center" nowrap="true"><%
        VisitStatistics all = new VisitStatistics();

        for (VisitImpl visit : visits)
        {
            key.visitRowId = visit.getRowId();
            VisitStatistics stats = bean.visitMapSummary.get(key);

            if (null != stats)
                for (VisitStatistic stat : VisitStatistic.values())
                    all.add(stat, stats.get(stat));
        }

        String innerHtml = "";

        for (VisitStatistic stat : statisticsToDisplay)
        {
            if (!innerHtml.isEmpty())
                innerHtml += " / ";

            innerHtml += all.get(stat);
        }

        if (userCanRead)
        {
            ActionURL defaultReportURL = new ActionURL(StudyController.DefaultDatasetReportAction.class, container);
            defaultReportURL.addParameter(DataSetDefinition.DATASETKEY, dataSet.getDataSetId());
            if (selectedCohort != null && bean.cohortFilter != null)
                bean.cohortFilter.addURLParameters(study, defaultReportURL, "Dataset");
            if (bean.qcStates != null)
                defaultReportURL.addParameter("QCState", bean.qcStates.getFormValue());

            %><a href="<%= h(defaultReportURL.getLocalURIString()) %>"><%=text(innerHtml)%></a><%
        }
        else
        {
            %><%=text(innerHtml)%><%
        }
        %></td><%

        for (VisitImpl visit : visits)
        {
            if (!bean.showAll && !visit.isShowByDefault())
                continue;

            key.visitRowId = visit.getRowId();
            VisitStatistics stats = bean.visitMapSummary.get(key);
            Boolean b = requiredMap.get(key);
            boolean isRequired = b == Boolean.TRUE;
            boolean isOptional = b == Boolean.FALSE;
            innerHtml = null;

            for (VisitStatistic stat : bean.stats)
            {
                int count = null != stats ? stats.get(stat) : 0;

                if (isRequired || isOptional || count > 0)
                    innerHtml = (null == innerHtml ? "" + count : innerHtml + " / " + count);
            }

            if (null == innerHtml)
            {
                innerHtml = "&nbsp;";
            }
            else if (userCanRead)
            {
                ActionURL datasetLink = new ActionURL(StudyController.DatasetAction.class, container);
                datasetLink.addParameter(VisitImpl.VISITKEY, visit.getRowId());
                datasetLink.addParameter(DataSetDefinition.DATASETKEY, dataSet.getDataSetId());
                if (selectedCohort != null)
                    bean.cohortFilter.addURLParameters(study, datasetLink, null);
                if (bean.qcStates != null)
                    datasetLink.addParameter(BaseStudyController.SharedFormParameters.QCState, bean.qcStates.getFormValue());

                innerHtml = "<a href=\"" + datasetLink.getLocalURIString() + "\">" + innerHtml + "</a>";
            }
            
            %><td align="center" nowrap="true"><%=text(innerHtml)%></td><%
        }
        %></tr>
    <%
    }
    %>
</table>
<%
    if (cantReadOneOrMoreDatasets)
    {
        %><span style="font-style: italic;">NOTE: You do not have read permission to one or more datasets.  Contact the study administrator for more information.</span><%
    }
%>
