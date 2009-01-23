<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.study.model.DataSetDefinition"%>
<%@ page import="java.util.List"%>
<%@ page import="java.util.ArrayList"%>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    List<DataSetDefinition> undefined = new ArrayList<DataSetDefinition>();
    for (DataSetDefinition def : getDataSets())
    {
        if (def.getTypeURI() == null)
            undefined.add(def);
    }
%>
<p>This study references <%= undefined.size() %> datasets without defined schemas.</p>
<%
    if (!undefined.isEmpty())
    {
%>
<p>A schema definition should be imported for each dataset in this study.</p>
<%
    }
%>
<%= textLink("Bulk Import Schemas", "bulkImportDataTypes.view")%>&nbsp;

<%
    if (!undefined.isEmpty())
    {
%>
<h3>Undefined datasets:</h3>
<table>
    <tr>
        <th>ID</th>
        <th>Label</th>
    </tr>
<%
    for (DataSetDefinition def : undefined)
    {
%>
    <tr>
        <td><%= def.getDataSetId() %></td>
        <td><%= def.getLabel() != null ? def.getLabel() : "" %></td>
    </tr>
<%
    }
%>
</table>
<%
    }
%>