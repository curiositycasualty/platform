<%
/*
 * Copyright (c) 2005-2016 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.settings.AppProps"%>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Objects" %>
<%@ page import="java.io.File" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%=formatMissedErrors("form")%>
<%
    AdminController.SiteSettingsBean bean = ((JspView<AdminController.SiteSettingsBean>)HttpView.currentView()).getModelBean();
    AppProps.Interface appProps = AppProps.getInstance();
%>
<script type="text/javascript">

var testNetworkDrive;
var submitSystemMaintenance;

(function(){

    testNetworkDrive = function()
    {
        var preferenceForm = document.forms['preferences'];
        var networkDriveForm = document.forms['networkdrivetest'];
        if (preferenceForm.networkDriveLetter.value.length == 0)
        {
            alert("Please specify your drive letter before testing.");
            try {preferenceForm.networkDriveLetter.focus();} catch(x){}
            return;
        }

        if (preferenceForm.networkDrivePath.value.length == 0)
        {
            alert("Please specify your drive path before testing.");
            try {preferenceForm.networkDrivePath.focus();} catch(x){}
            return;
        }
        networkDriveForm.networkDriveLetter.value = preferenceForm.networkDriveLetter.value;
        networkDriveForm.networkDrivePath.value = preferenceForm.networkDrivePath.value;
        networkDriveForm.networkDriveUser.value = preferenceForm.networkDriveUser.value;
        networkDriveForm.networkDrivePassword.value = preferenceForm.networkDrivePassword.value;

        networkDriveForm.submit();
    };

    submitSystemMaintenance = function()
    {
        document.forms['systemMaintenance'].submit();
    }
})();

var enableUsageTest = function() {
    var el = document.getElementById('testUsageReport');
    var level = document.querySelector('input[name="usageReportingLevel"]:checked').value;
    enableTestButtion(el, level);
};

var enableExceptionTest = function() {
    var el = document.getElementById('testExceptionReport');
    var level = document.querySelector('input[name="exceptionReportingLevel"]:checked').value;
    enableTestButtion(el, level);
};

var enableTestButtion = function(el, level) {
    if ("NONE" == level)
    {
        LABKEY.Utils.replaceClass(el, 'labkey-button', 'labkey-disabled-button');
    }
    else
    {
        LABKEY.Utils.replaceClass(el, 'labkey-disabled-button', 'labkey-button');
    }
};

var testUsageReport = function() {
    var level = document.querySelector('input[name="usageReportingLevel"]:checked').value;
    testMothershipReport('CheckForUpdates', level);
};

var testExceptionReport = function() {
    var level = document.querySelector('input[name="exceptionReportingLevel"]:checked').value;
    testMothershipReport('ReportException', level);
};

var testMothershipReport = function(type, level, title) {
    LABKEY.Ajax.request({
        url: LABKEY.ActionURL.buildURL("admin", "testMothershipReport"),
        method: "POST",
        params: {
            type: type,
            level: level
        },
        success: LABKEY.Utils.getCallbackWrapper(function(response)
        {
            var report = JSON.parse(response).report;
            var reportStr = '';
            if (report != undefined)
            {
                if (report.jsonMetrics != undefined)
                {
                    var jsonMetrics = JSON.parse(report.jsonMetrics);  // jsonMetrics is a nested object, doesn't stringify well
                    if (jsonMetrics.modules != undefined)
                    {
                        jsonMetrics.modules = "Installed module list omitted from sample report."; // module list is ugly
                    }
                    report.jsonMetrics = jsonMetrics;
                }
                else if (report.stackTrace != undefined)
                {
                    report.stackTrace = "StackTrace omitted from sample report."; // Stack trace is ugly/truncated in the window alert
                }
                reportStr = JSON.stringify(report, null, "  ");
            }
            else {
                reportStr = 'An error occurred generating the sample report.';
            }
            window.alert(reportStr);
        })
    });
};
</script>

<labkey:form name="preferences" enctype="multipart/form-data" method="post">
<input type="hidden" name="upgradeInProgress" value="<%=bean.upgradeInProgress ? 1 : 0%>" />

<table>
<%
if (bean.upgradeInProgress)
{%>
<tr>
    <td><p>You can use this page to customize your LabKey Server installation. If you prefer to customize it later, you can reach this page again by clicking <strong>Admin->Site->Admin Console->Site Settings</strong>.</p>
Click the Save button at any time to accept the current settings and continue.</td>
</tr>
<%}%>
<tr>
    <td><%= button("Save").submit(true) %></td>
</tr>
</table>

<table>
<tr>
    <th style="width: 35em;"></th>
    <th></th>
</tr>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Set site administrators (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label" valign="top"><label for="administratorContactEmail">Primary site administrator</label></td>
    <td>
        <select name="administratorContactEmail" id="administratorContactEmail">
            <% List<Pair<Integer, String>> members = org.labkey.api.security.SecurityManager.getGroupMemberNamesAndIds("Administrators");
                for (Pair<Integer,String> member : members) { %>
            <option value="<%=h(member.getValue())%>"<%=selected(Objects.equals(member.getValue(), appProps.getAdministratorContactEmail()))%>><%=h(member.getValue())%></option>
            <% } %>
        </select>
    </td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Set default domain for user sign in and base server url (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label"><label for="defaultDomain">System default domain (default domain for user log in)</label></td>
    <td><input type="text" id="defaultDomain" name="defaultDomain" size="50" value="<%= h(appProps.getDefaultDomain()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="baseServerUrl">Base server URL (used to create links in emails sent by the system)</label></td>
    <td><input type="text" name="baseServerUrl" id="baseServerUrl" size="50" value="<%= h(appProps.getBaseServerUrl()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="useContainerRelativeURL">Use "path first" urls (/home/project-begin.view)</label></td>
    <td><labkey:checkbox id="useContainerRelativeURL" name="useContainerRelativeURL" checked="<%= appProps.getUseContainerRelativeURL() %>" value="true" /></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>


<tr>
    <td colspan=2>Automatically check for updates to LabKey Server and
        report usage statistics to LabKey. (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label" valign="top">Check for updates and report usage statistics to the LabKey team.
        Usage data helps LabKey improve the LabKey Server platform. All data is transmitted securely over SSL.
    </td>
    <td>
    <table>
        <tr>
            <td valign="top"><input type="radio" name="usageReportingLevel" id="usageReportingLevel1" onchange="enableUsageTest();" value="NONE"<%=checked("NONE".equals(appProps.getUsageReportingLevel().toString()))%>></td>
            <td><label for="usageReportingLevel1"><strong>OFF</strong> - Do not check for updates or report any usage data.</label></td>
        </tr>
        <tr>
            <td valign="top"><input type="radio" name="usageReportingLevel" id="usageReportingLevel2" onchange="enableUsageTest();" value="LOW"<%=checked("LOW".equals(appProps.getUsageReportingLevel().toString()))%>></td>
            <td><label for="usageReportingLevel2"><strong>ON, low</strong> - Check for updates and report system information.</label></td>
        </tr>
        <tr>
            <td valign="top"><input type="radio" name="usageReportingLevel" id="usageReportingLevel3" onchange="enableUsageTest();" value="MEDIUM"<%=checked("MEDIUM".equals(appProps.getUsageReportingLevel().toString()))%>></td>
            <td><label for="usageReportingLevel3"><strong>ON, medium</strong> - Check for updates and report system information, usage data, and organization details.</label></td>
        </tr>
        <tr>
            <td style="padding: 5px 0 5px;" colspan="2"><%=button("View").id("testUsageReport").onClick("testUsageReport(); return false;").enabled(!"NONE".equals(appProps.getUsageReportingLevel().toString()))%>
            <label>Display an example report for the selected level. <strong>No data will be submitted.</strong></label></td>
        </tr>
    </table>
    </td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Automatically report exceptions (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label" valign="top">Report exceptions to the LabKey development team. All data is transmitted securely over SSL.</td>
    <td>
        <table>
            <tr>
                <td valign="top"><input type="radio" name="exceptionReportingLevel" onchange="enableExceptionTest();" id="exceptionReportingLevel1" value="NONE"<%=checked("NONE".equals(appProps.getExceptionReportingLevel().toString()))%>></td>
                <td><label for="exceptionReportingLevel1"><strong>OFF</strong> - Do not report exceptions.</label></td>
            </tr>
            <tr>
                <td valign="top"><input type="radio" name="exceptionReportingLevel" onchange="enableExceptionTest();" id="exceptionReportingLevel2" value="LOW"<%=checked("LOW".equals(appProps.getExceptionReportingLevel().toString()))%>></td>
                <td><label for="exceptionReportingLevel2"><strong>ON, low</strong> - Include anonymous system and exception information.</label></td>
            </tr>
            <tr>
                <td valign="top"><input type="radio" name="exceptionReportingLevel" onchange="enableExceptionTest();" id="exceptionReportingLevel3" value="MEDIUM"<%=checked("MEDIUM".equals(appProps.getExceptionReportingLevel().toString()))%>></td>
                <td><label for="exceptionReportingLevel3"><strong>ON, medium</strong> - Include anonymous system and exception information, as well as the URL that triggered the exception.</label></td>
            </tr>
            <tr>
                <td valign="top"><input type="radio" name="exceptionReportingLevel" onchange="enableExceptionTest();" id="exceptionReportingLevel4" value="HIGH"<%=checked("HIGH".equals(appProps.getExceptionReportingLevel().toString()))%>></td>
                <td><label for="exceptionReportingLevel4"><strong>ON, high</strong> - Include the above, plus the user's email address. The user will be contacted only for assistance in reproducing the bug, if necessary.</label></td>
            </tr>
            <tr >
                <td style="padding: 5px 0 5px;" colspan="2"><%=button("View").id("testExceptionReport").onClick("testExceptionReport(); return false;").enabled(!"NONE".equals(appProps.getExceptionReportingLevel().toString()))%>
                <label>Display an example report for the selected level. <strong>No data will be submitted.</strong></label></td>
            </tr>
        </table>
    </td>
</tr>
<%-- Only show this option if the mothership module has enabled it --%>
<% if (bean.showSelfReportExceptions) { %>
<tr>
    <td class="labkey-form-label" valign="top"><label for="selfReportExceptions">Report exceptions to the local server</label></td>
    <td>
        <input type="checkbox" name="selfReportExceptions" id="selfReportExceptions" <%= text(appProps.isSelfReportExceptions() ? "checked" : "" )%> /> Self-reporting is always at the "high" level described above
    </td>
</tr>
<% } %>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Customize LabKey system properties (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label"><label for="memoryUsageDumpInterval">Log memory usage frequency, in minutes (for debugging, set to 0 to disable)</label></td>
    <td><input type="text" name="memoryUsageDumpInterval" id="memoryUsageDumpInterval" size="4" value="<%= h(appProps.getMemoryUsageDumpInterval()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="maxBLOBSize">Maximum file size, in bytes, to allow in database BLOBs</label></td>
    <td><input type="text" name="maxBLOBSize" id="maxBLOBSize" size="10" value="<%= h(appProps.getMaxBLOBSize()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="ext3Required">Require ExtJS v3.4.1 be loaded on each page</label></td>
    <td><input type="checkbox" name="ext3Required" id="ext3Required" <%=checked(appProps.isExt3Required())%>></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="ext3APIRequired">Require ExtJS v3.x based Client API be loaded on each page</label></td>
    <td><input type="checkbox" name="ext3APIRequired" id="ext3APIRequired" <%=checked(appProps.isExt3APIRequired())%>></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Configure Security (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label"><label for="sslRequired">Require SSL connections (users must connect via SSL)</label></td>
    <td><input type="checkbox" name="sslRequired" id="sslRequired" <%=checked(appProps.isSSLRequired())%>></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="sslPort">SSL port number (specified in server config file)</label></td>
    <td><input type="text" name="sslPort" id="sslPort" value="<%=appProps.getSSLPort()%>" size="6"></td>
</tr>
<tr>
    <td class="labkey-form-label">Allow API session keys</td>
    <td><labkey:checkbox id="allowSessionKeys" name="allowSessionKeys" checked="<%= appProps.isAllowSessionKeys()%>" value="true"/></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Configure pipeline settings (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label"><label for="pipelineToolsDirectory">Pipeline tools</label><%= helpPopup("Pipeline Tools", "A '" + File.pathSeparator + "' separated list of directories on the web server containing executables that are run for pipeline jobs (e.g. TPP or XTandem)") %></td>
    <td><input type="text" name="pipelineToolsDirectory" id="pipelineToolsDirectory" size="50" value="<%= h(appProps.getPipelineToolsDirectory()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Map network drive (Windows only) (<%=text(bean.helpLink)%>)</td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="networkDriveLetter">Drive letter</label></td>
    <td><input type="text" name="networkDriveLetter" id="networkDriveLetter" value="<%= h(appProps.getNetworkDriveLetter()) %>" size="1" maxlength="1"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="networkDrivePath">Path</label></td>
    <td><input type="text" name="networkDrivePath" id="networkDrivePath" value="<%= h(appProps.getNetworkDrivePath()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="networkDriveUser">User</label></td>
    <td><input type="text" name="networkDriveUser" id="networkDriveUser" value="<%= h(appProps.getNetworkDriveUser()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="networkDrivePassword">Password</label></td>
    <td><input type="password" name="networkDrivePassword" id="networkDrivePassword" value="<%= h(appProps.getNetworkDrivePassword()) %>"></td>
</tr>
<tr>
    <td></td>
    <td><%=textLink("Test network drive settings", "javascript:testNetworkDrive()")%> - Note: Do not test if the drive is currently being accessed from within LabKey Server.</td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Ribbon Bar Message (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label"><label for="showRibbonMessage">Display Message</label></td>
    <td><input type="checkbox" name="showRibbonMessage" id="showRibbonMessage" <%=checked(appProps.isShowRibbonMessage())%>></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="ribbonMessageHtml">Message HTML</label></td>
    <td><textarea id="ribbonMessageHtml" name="ribbonMessageHtml" id="ribbonMessageHtml" cols="60" rows="3"><%=h(appProps.getRibbonMessageHtml())%></textarea></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Put web site in administrative mode (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label"><label for="adminOnlyMode">Admin only mode (only site admins may log in)</label></td>
    <td><input type="checkbox" name="adminOnlyMode" id="adminOnlyMode" <%=checked(appProps.isUserRequestedAdminOnlyMode())%>></td>
</tr>
<tr>
    <td class="labkey-form-label" valign="top"><label for="adminOnlyMessage">Message to users when site is in admin-only mode</label><br/>(Wiki formatting allowed)</td>
    <td><textarea id="adminOnlyMessage" name="adminOnlyMessage" cols="60" rows="3"><%= h(appProps.getAdminOnlyMessage()) %></textarea></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>HTTP security settings</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label"><label for="CSRFCheck">CSRF checking</label></td>
    <td><select name="CSRFCheck" id="CSRFCheck">
        <option value="POST" <%=selectedEq("POST",appProps.getCSRFCheck())%>>All POST requests (recommended)</option>
        <option value="ADMINONLY" <%=selectedEq("ADMINONLY",appProps.getCSRFCheck())%>>Admin requests</option>
    </select></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="XFrameOptions">X-Frame-Options</label></td>
    <td><select name="XFrameOptions" id="XFrameOptions">
        <% String option = appProps.getXFrameOptions(); %>
        <%-- BREAKS GWT <option value="DENY" <%=selectedEq("DENY",option)%>>DENY</option> --%>
        <option value="SAMEORIGIN" <%=selectedEq("SAMEORIGIN",option)%>>SAMEORIGIN</option>
        <option value="ALLOW" <%=selectedEq("ALLOW",option)%>>Allow</option></select></td>
</tr>


    <tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td><%= button("Save").submit(true) %></td>
</tr>
</table>
</labkey:form>

<labkey:form name="networkdrivetest" action="<%=h(buildURL(AdminController.ShowNetworkDriveTestAction.class))%>" enctype="multipart/form-data" method="post" target="_new">
    <input type="hidden" name="upgradeInProgress" value="<%=bean.upgradeInProgress ? 0 : 1%>" />
    <input type="hidden" name="networkDriveLetter" value="" />
    <input type="hidden" name="networkDrivePath" value="" />
    <input type="hidden" name="networkDriveUser" value="" />
    <input type="hidden" name="networkDrivePassword" value="" />
</labkey:form>
