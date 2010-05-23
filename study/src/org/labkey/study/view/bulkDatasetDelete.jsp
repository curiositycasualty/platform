<%
/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.DataSet" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<p>Please select the datasets you want to delete:</p>
<form action="bulkDatasetDelete.post" name="bulkDatasetDelete" method="POST">
<table class="labkey-data-region labkey-show-borders">
    <tr>
        <th><input type="checkbox" onchange="toggleAllRows(this);"></th>
        <th>ID</th>
        <th>Label</th>
        <th>Category</th>
        <th>Number of data rows</th>
    </tr>

    <%
    Study study = getStudy();

    String cancelURL = new ActionURL(StudyController.ManageTypesAction.class, study.getContainer()).getLocalURIString();

    for (DataSet def : study.getDataSets())
    {
        ActionURL detailsURL = new ActionURL(StudyController.DefaultDatasetReportAction.class, study.getContainer());
        detailsURL.addParameter("datasetId", def.getDataSetId());
        String detailsLink = detailsURL.getLocalURIString();
    %>

    <tr>
        <td><input type="checkbox" name="datasetIds" value="<%=def.getDataSetId()%>"></td>
        <td><a href="<%=detailsLink%>"><%=def.getDataSetId()%></a></td>
        <td><a href="<%=detailsLink%>"><%= h(def.getLabel()) %></a></td>
        <td><%= def.getCategory() != null ? h(def.getCategory()) : "&nbsp;" %></td>
        <td align="right"><%=StudyManager.getInstance().getNumDatasetRows(def)%></td>
    </tr>
    <%
        
    }
        
%>
</table>
<%=PageFlowUtil.generateSubmitButton("Delete Selected",
        "if (confirm('Delete selected datasets?')){" +
            "Ext.get(this).replaceClass('labkey-button', 'labkey-disabled-button');" +
            "return true;" +
        "} " +
            "else return false;")%>
<%=PageFlowUtil.generateButton("Cancel", cancelURL)%>    

</form>

<script type="text/javascript">
    function toggleAllRows(checkbox)
    {
        var i;
        var checked = checkbox.checked;
        var elements = document.getElementsByName("datasetIds");
        for (i in elements)
        {
            var e = elements[i];
            e.checked = checked;
        }
    }
</script>
