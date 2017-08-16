<%--
/*
 * Copyright (c) 2017 LabKey Corporation
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
--%>
<%@ page import="org.labkey.api.admin.CoreUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.search.SearchUrls" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.settings.HeaderProperties" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.settings.TemplateResourceHandler" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.PopupAdminView" %>
<%@ page import="org.labkey.api.view.PopupMenuView" %>
<%@ page import="org.labkey.api.view.PopupUserView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.core.view.template.bootstrap.BootstrapHeader" %>
<%@ page import="org.labkey.core.view.template.bootstrap.BootstrapTemplate" %>
<%@ page import="org.labkey.api.view.HtmlView" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }
%>
<%
    BootstrapHeader me = (BootstrapHeader) HttpView.currentView();
    PageConfig pageConfig = me.getModelBean();
    Container c = getContainer();
    User user = getUser();
    boolean isRealUser = null != user && !user.isGuest();
    ViewContext context = getViewContext();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
    boolean showSearch = hasUrlProvider(SearchUrls.class);

    HtmlView headerHtml = BootstrapTemplate.getHomeTemplateResource(new HeaderProperties());
    String siteShortName = (laf.getShortName() != null && laf.getShortName().length() > 0) ? laf.getShortName() : null;
%>
<div class="labkey-page-header">
    <div class="container clearfix">
        <div class="row">
        <div class="hidden-xs navbar-header">
            <a class="brand-logo" href="<%=h(laf.getLogoHref())%>">
                <img src="<%=h(TemplateResourceHandler.LOGO.getURL(c))%>" alt="<%=h(laf.getShortName())%>" height="30">
            </a>
            <%-- _header.html overrides the server short name--%>
            <%  if (headerHtml == null) {
                    if (siteShortName != null) {
                        String displayedShortName = "LabKey Server".equals(siteShortName) ? "" : siteShortName;
            %>
                        <h4 class="brand-link"><a href="<%=h(laf.getLogoHref())%>"><%=h(displayedShortName)%></a></h4>
            <%      }
                } %>
        </div>
        <div class="hidden-sm hidden-md hidden-lg navbar-header">
            <a class="brand-logo-mobile" href="<%=h(laf.getLogoHref())%>">
                <img src="<%=h(TemplateResourceHandler.LOGO_MOBILE.getURL(c))%>" alt="<%=h(laf.getShortName())%>" height="30">
            </a>
            <% if (headerHtml == null) {
                    if (siteShortName != null) { %>
                        <h4 class="brand-link"><a href="<%=h(laf.getLogoHref())%>"><%=h(siteShortName)%></a></h4>
            <%      }
                } %>
        </div>
        <%--if a _header.html file is defined put it into dom without html encoding. It will need to define divs
        with appropriate bootstrap classes--%>
        <% if (headerHtml != null) {%>
            <%=text(headerHtml.getHtml())%>
        <%}%>
        <ul class="navbar-nav-lk">
<% if (showSearch) { %>
            <li class="navbar-search hidden-xs">
                <a class="fa fa-search" id="global-search-trigger"></a>
                <div id="global-search" class="global-search">
                    <labkey:form id="global-search-form" action="<%=h(urlProvider(SearchUrls.class).getSearchURL(c, null))%>" method="GET">
                        <input type="text" class="search-box" name="q" placeholder="Search LabKey Server" value="">
                        <input type="submit" hidden>
                        <a href="#" onclick="document.getElementById('global-search-form').submit(); return false;" class="btn-search fa fa-search"></a>
                    </labkey:form>
                </div>
            </li>
            <li id="global-search-xs" class="dropdown visible-xs">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">
                    <i class="fa fa-search"></i>
                </a>
                <ul class="dropdown-menu dropdown-menu-right">
                    <li>
                        <labkey:form action="<%=h(urlProvider(SearchUrls.class).getSearchURL(c, null))%>" method="GET">
                            <div class="input-group">
                                <input type="text" class="search-box" name="q" placeholder="Search LabKey Server" value="">
                                <input type="submit" hidden>
                            </div>
                        </labkey:form>
                    </li>
                </ul>
            </li>
<% } %>
<% if (isRealUser) { %>
            <li class="dropdown dropdown-rollup">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">
                    <i class="fa fa-user"></i>
                </a>
                <ul class="dropdown-menu dropdown-menu-right">
                    <% PopupMenuView.renderTree(PopupUserView.createNavTree(context, pageConfig), out); %>
                </ul>
            </li>
<% } %>
<% if (PopupAdminView.hasPermission(context)) { %>
            <li class="dropdown dropdown-rollup">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">
                    <i class="fa fa-cog"></i>
                </a>
                <ul class="dropdown-menu dropdown-menu-right">
                    <% PopupMenuView.renderTree(PopupAdminView.createNavTree(context), out); %>
                </ul>
            </li>
<% } %>
<% if (me.getView("notifications") != null) { %>
    <% include(me.getView("notifications"), out); %>
<% } %>

<% if (AppProps.getInstance().isDevMode() && isRealUser && user.isInSiteAdminGroup())
   { %>
            <li data-tt="tooltip" data-placement="bottom" title="Revert back to the legacy look and feel.">
                <a onclick="LABKEY.Utils.toggleUI();"><i class="fa fa-history"></i></a>
            </li>
<% } %>

<% if (user != null && user.isImpersonated()) { %>
            <li>
                <a href="<%=h(urlProvider(LoginUrls.class).getStopImpersonatingURL(c, user.getImpersonationContext().getReturnURL()))%>" class="btn btn-primary">Stop impersonating</a>
            </li>
<% } %>
<% if (PageFlowUtil.isPageAdminMode(getViewContext())) { %>
            <li>&nbsp;</li> <!--spacer, for the case of both impersonating and page admin mode-->
            <li>
                <a href="<%=h(urlProvider(ProjectUrls.class).getTogglePageAdminModeURL(c, getActionURL()))%>" class="btn btn-primary">Exit Admin Mode</a>
            </li>
<% } %>
<% if (!isRealUser && pageConfig.shouldIncludeLoginLink()) { %>
            <li>
                <a href="<%=h(urlProvider(LoginUrls.class).getLoginURL())%>">
                    <span>Sign In</span>
                </a>
            </li>
<% } %>
<% if (isRealUser)
{ %>
            <li data-tt="tooltip" data-placement="bottom" title="Give your thoughts on our new look.">
                <a href="<%=h(urlProvider(CoreUrls.class).getFeedbackURL())%>" target="_blank">
                    <span class="hidden-sm hidden-md hidden-lg">
                        <i class="fa fa-comments"></i>
                    </span>
                    <span class="hidden-xs">Give feedback</span>
                </a>
            </li>
<% } %>
        </ul>
    </div>
    </div>
</div>