<%
/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.reports.ReportService" %>
<%@ page import="org.labkey.api.reports.report.ReportIdentifier" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="java.util.Collections" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>

<%
    JspView<StudyController.ViewPrefsBean> me = (JspView<StudyController.ViewPrefsBean>) HttpView.currentView();
    StudyController.ViewPrefsBean bean = me.getModelBean();
    ActionURL url = HttpView.currentContext().cloneActionURL();

    ViewContext context = HttpView.currentContext();
    String defaultView = StudyController.getDefaultView(context, bean.getDataSetDefinition().getDataSetId());
%>

<table>
    <tr class="labkey-wp-header">
        <th colspan="3" align="left">Default View<%=PageFlowUtil.helpPopup("Default View", "Select the default View that will display from the Study Datasets Web Part")%></th>
    </tr>
    <%
        if (bean.getViews().size() > 1) {
            for (Pair<String, String> view : bean.getViews()) {
    %>
            <tr><td><%=getLabel(view, defaultView)%></td>
                <td>&nbsp;</td>
                <td><%=textLink("select", url.relativeUrl("viewPreferences", Collections.singletonMap("defaultView", view.getValue()), "Study", false))%></td>
            </tr>
    <%
        }
        } else {
    %>
        <tr><td>There is only a single view for this dataset.</td></tr>
    <%
        }
        ActionURL doneUrl = HttpView.currentContext().cloneActionURL();
        doneUrl.setAction(StudyController.DatasetReportAction.class);
        doneUrl.deleteParameter("defaultView");
        doneUrl.deleteParameter("Dataset.reportId");
        doneUrl.deleteParameter("Dataset.viewName");

        ReportIdentifier reportId = ReportService.get().getReportIdentifier(defaultView);
        if (reportId != null)
            doneUrl.addParameter("Dataset.reportId", defaultView);
        else
            doneUrl.addParameter("Dataset.viewName", defaultView);
    %>
        <tr><td>&nbsp;</td></tr>
        <tr><td><%=generateButton("Done", doneUrl)%></td></tr>
</table>

<%!
    String getLabel(Pair<String, String> view, String defaultView)
    {
        if (StringUtils.equals(view.getValue(), defaultView))
            return "<b>" + view.getKey() + "</b>";

        return view.getKey();
    }
%>


