<%
/*
 * Copyright (c) 2005-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page extends="org.labkey.announcements.EmailNotificationPage" %>
<%
    Container c = getViewContext().getContainer();
%>
<html>
<head>
<base href="<%=h(getViewContext().getActionURL().getBaseServerURI() + getViewContext().getContextPath())%>">
<%=PageFlowUtil.getStylesheetIncludes(c)%>
</head>

<body>
<table width=100%>
    <tr class="labkey-alternate-row"><td colspan="2" class="labkey-bordered" style="border-right: 0 none">
    <%
        int attachmentCount = announcementModel.getAttachments().size();
    %>
    <%=announcementModel.getCreatedByName(includeGroups, HttpView.currentContext()) + (announcementModel.getParent() != null ? " responded" : " created a new " + settings.getConversationName().toLowerCase()) + (attachmentCount > 0 ? " and attached " + attachmentCount + " document" + (attachmentCount > 1 ? "s" : "") : "")%></td>
    <td align="right" class="labkey-bordered" style="border-left: 0 none"><%=formatDateTime(announcementModel.getCreated())%></td></tr><%

    if (null != body)
    { %>
    <tr><td colspan="3"><%=body%></td></tr><%
    }

    %>
    <tr><td colspan="3">&nbsp;</td></tr>
    <tr><td colspan="3"><a href="<%=threadURL%>">View this <%=settings.getConversationName().toLowerCase()%></a></td></tr>
</table>

<br>
<br>
<hr size="1">

<table width=100%>
    <tr><td>You have received this email because <%
        switch(reason)
        {
            case broadcast:
    %>a site administrator sent this notification to all users of <a href="<%=siteURL%>"><%=siteURL%></a>.<%
            break;

            case signedUp:
    %>you are signed up to receive notifications about new posts to <a href="<%=boardURL%>"><%=boardPath%></a> at <a href="<%=siteURL%>"><%=siteURL%></a>.
If you no longer wish to receive these notifications you can <a href="<%=removeUrl%>">change your email preferences</a>.<%
            break;

            case memberList:
    %>you are on the member list for this <%=settings.getConversationName().toLowerCase()%>.  If you no longer wish to receive these
notifications you can remove yourself from the member list by <a href="<%=removeUrl%>">clicking here</a>.<%
            break;
        }
    %></td></tr>
</table>    
</body>
</html>
