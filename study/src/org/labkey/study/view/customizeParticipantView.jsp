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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyController.CustomizeParticipantViewForm> me = (JspView<StudyController.CustomizeParticipantViewForm>) HttpView.currentView();
    StudyController.CustomizeParticipantViewForm bean = me.getModelBean();
    boolean useCustomView = bean.isUseCustomView();
    String subjectNoun = StudyService.get().getSubjectNounSingular(getViewContext().getContainer());
%>
<script type="text/javascript">
    window.onbeforeunload = LABKEY.beforeunload();

    var DEFAULT_SCRIPT_VALUE = <%= PageFlowUtil.jsString(bean.getDefaultScript()) %>;

    function setCustomScriptState(disabled)
    {
        document.getElementById('customScript').disabled = disabled;
        return true;
    }
</script>
<form action="customizeParticipantView.view" name="editorForm" method="POST">
    <input type="hidden" name="returnUrl" value="<%= h(bean.getReturnUrl())%>">
    <input type="hidden" name="reshow" value="false">
    <input type="hidden" name="participantId" value="<%= h(bean.getParticipantId()) %>">
    <table>
        <tr class="labkey-wp-header">
            <th><%= h(subjectNoun) %> View Contents</th>
        </tr>
<%
    if (bean.isEditable())
    {
%>
        <tr>
            <td>
                <input type="radio" name="useCustomView"
                       value="false"
                       onclick="return setCustomScriptState(!this.selected)"
                       <%= useCustomView ? "" : "CHECKED"%>>Use standard <%= h(subjectNoun.toLowerCase()) %> view<br>
            </td>
        </tr>
        <tr>
            <td>
                <input type="radio" name="useCustomView"
                       value="true"
                       onclick="return setCustomScriptState(this.selected)"
                       <%= useCustomView ? "CHECKED" : ""%>>Use customized <%= h(subjectNoun.toLowerCase()) %> view<br>
            </td>
        </tr>
        <tr>
            <td>
                <textarea rows="30" cols="150" name="customScript" id="customScript" onChange="LABKEY.setDirty(true); return true;"
                        style="width:100%"
                        <%=disabled(!useCustomView)%>><%= h(bean.getCustomScript() == null ? bean.getDefaultScript() : bean.getCustomScript())%></textarea><br>
            </td>
        </tr>
        <tr>
            <td>
                <%= buttonImg("Save", "document.forms['editorForm'].customScript.disabled = false; LABKEY.setSubmit(true); document.forms['editorForm'].reshow.value = true; return true;")%>
                <%= buttonImg("Save and Finish", "document.forms['editorForm'].customScript.disabled = false; LABKEY.setSubmit(true); return true;")%>
                <%= bean.getReturnUrl() != null && bean.getReturnUrl().length() > 0 ? 
                        generateButton("Cancel", bean.getReturnUrl()) :
                        generateButton("Cancel", new ActionURL(ReportsController.ManageReportsAction.class, getViewContext().getContainer())) %>
                <%= buttonImg("Restore default script", "if (confirm('Restore default script?  You will lose any changes made to this page.')) document.getElementById('customScript').value = DEFAULT_SCRIPT_VALUE; return false;") %>
            </td>
        </tr>
<%
    }
    else
    {
%>
        <tr>
            <td>
                This custom participant view is defined in an active module.  It cannot be edited via this interface.
            </td>
        </tr>
        <tr>
            <td>
                <textarea rows="30" cols="150" style="width:100%" DISABLED><%= bean.getCustomScript() %></textarea><br>
            </td>
        </tr>
<%
    }
%>
    </table>
</form>
<%
    if (bean.getParticipantId() != null)
    {
%>
<table width="100%">
    <tr class="labkey-wp-header">
        <th><%= h(subjectNoun) %> View Preview <%= bean.isEditable() ? "(Save to refresh)" : "" %></th>
    </tr>
    <tr>
        <td>
            <%= useCustomView ? bean.getCustomScript() : bean.getDefaultScript() %>
        </td>
    </tr>
</table>
<%
    }
%>