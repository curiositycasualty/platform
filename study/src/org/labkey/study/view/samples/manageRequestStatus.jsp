<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.SampleRequestStatus"%>
<%@ page import="org.labkey.study.SampleManager"%>
<%@ page import="java.util.List"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.samples.notifications.ActorNotificationRecipientSet" %>
<%@ page import="org.labkey.study.controllers.samples.SpecimenController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<SpecimenController.ManageRequestBean> me = (JspView<SpecimenController.ManageRequestBean>) HttpView.currentView();
    SpecimenController.ManageRequestBean bean = me.getModelBean();
    ViewContext context = me.getViewContext();
    SampleRequestStatus[] statuses = SampleManager.getInstance().getRequestStatuses(context.getContainer(), context.getUser());
%>
<labkey:errors />
<form action="<%=h(buildURL(SpecimenController.ManageRequestStatusAction.class))%>m" enctype="multipart/form-data" method="POST">
    <input type="hidden" name="id" value="<%= bean.getSampleRequest().getRowId()%>">
    <table  class="labkey-manage-display">
        <tr>
            <th align="right">Request Description</th>
            <td>
                <textarea rows="10" cols="50" name="requestDescription"><%= h(bean.getSampleRequest().getComments()) %></textarea>
            </td>
        </tr>
        <tr>
            <th align="right">Status</th>
            <td>
                <select name="status">
                    <%
                        for (SampleRequestStatus status : statuses)
                        {
                    %>
                    <option value="<%= status.getRowId() %>" <%= text(bean.getSampleRequest().getStatusId() == status.getRowId() ? "SELECTED" : "") %>>
                        <%= h(status.getLabel()) %>
                    </option>
                    <%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <th align="right">Comments</th>
            <td><textarea name="comments" rows="10" cols="50"></textarea></td>
        </tr>
        <tr>
            <th align="right">Supporting<br>Documents</th>
            <td>
                <input type="file" size="40" name="formFiles[0]"><br>
                <input type="file" size="40" name="formFiles[1]"><br>
                <input type="file" size="40" name="formFiles[2]"><br>
                <input type="file" size="40" name="formFiles[3]"><br>
                <input type="file" size="40" name="formFiles[4]">
            </td>
        </tr>
        <tr>
            <th>Notify</th>
            <td>
                <%
                    boolean hasInactiveEmailAddress = false;
                    List<ActorNotificationRecipientSet> possibleNotifications = bean.getPossibleNotifications();
                    for (ActorNotificationRecipientSet possibleNotification : possibleNotifications)
                    {
                        boolean hasEmailAddresses = possibleNotification.getAllEmailAddresses().length > 0;
                        if (hasEmailAddresses)
                            hasInactiveEmailAddress |= possibleNotification.hasInactiveEmailAddress();
                %>
                <input type="checkbox"
                       name="notificationIdPairs"
                       value="<%= text(possibleNotification.getFormValue()) %>" <%= text(hasEmailAddresses ? "" : "DISABLED") %>>
                <%= text(possibleNotification.getHtmlDescriptionAndLink(hasEmailAddresses)) %><br>
                <%
                    }
                    if (hasInactiveEmailAddress)
                    {
                %>
                    <input type="checkbox"
                           name="emailInactiveUsers">
                    Include inactive users<br>
                <%
                    }
                %>
            </td>
        </tr>
        <tr>
            <th>&nbsp;</th>
            <td>
                <%= generateSubmitButton("Save Changes and Send Notifications")%>&nbsp;
                <%= generateButton("Cancel", buildURL(SpecimenController.ManageRequestAction.class, "id=" + bean.getSampleRequest().getRowId()))%>
            </td>
        </tr>
    </table>
</form>
