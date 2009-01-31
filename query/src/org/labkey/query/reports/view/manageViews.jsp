<%
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
%>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.reports.report.view.RReportBean" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    ViewContext context = HttpView.currentContext();

    RReportBean bean = new RReportBean();
    bean.setReportType(RReport.TYPE);
    bean.setRedirectUrl(context.getActionURL().getLocalURIString());

    ActionURL newRView = ReportUtil.getRReportDesignerURL(context, bean);
%>

<script type="text/javascript">

    LABKEY.requiresScript("reports/manageViews.js");

    Ext.onReady(function()
    {
        var gridConfig = {
            renderTo: 'viewsGrid',
            container: '<%=context.getContainer().getPath()%>',
            createMenu :[{
                id: 'create_rView',
                text:'New R View',
                listeners:{click:function(button, event) {window.location = '<%=newRView.getLocalURIString()%>';}}}]
        };
        var panel = new LABKEY.ViewsPanel(gridConfig);
        panel.show();
    });
    
</script>

<labkey:errors/>

<div id="viewsGrid" class="extContainer"></div>
