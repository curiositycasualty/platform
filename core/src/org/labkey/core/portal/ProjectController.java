/*
 * Copyright (c) 2004-2012 Fred Hutchinson Cancer Research Center
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

package org.labkey.core.portal;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileUrls;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NavTreeManager;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PrintTemplate;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ProjectController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ProjectController.class);

    private static final Logger _log = Logger.getLogger(ProjectController.class);

    public static class ProjectUrlsImpl implements ProjectUrls
    {
        public ActionURL getStartURL(Container container)
        {
            return new ActionURL(StartAction.class, container);
        }

        public ActionURL getBeginURL(Container container)
        {
            return getBeginURL(container, null);
        }

        public ActionURL getBeginURL(Container container, @Nullable String pageId)
        {
            ActionURL url = new ActionURL(BeginAction.class, container);
            if (pageId != null)
            {
                url.addParameter("pageId", pageId);
            }
            return url;
        }

        public ActionURL getHomeURL()
        {
            return getStartURL(ContainerManager.getHomeContainer());
        }

        public ActionURL getCustomizeWebPartURL(Container c)
        {
            return new ActionURL(CustomizeWebPartAction.class, c);
        }

        public ActionURL getAddWebPartURL(Container c)
        {
            return new ActionURL(AddWebPartAction.class, c);
        }

        public ActionURL getCustomizeWebPartURL(Container c, Portal.WebPart webPart, ActionURL returnURL)
        {
            ActionURL url = getCustomizeWebPartURL(c);
            url.addParameter("webPartId", String.valueOf(webPart.getRowId()));
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getMoveWebPartURL(Container c, Portal.WebPart webPart, int direction, ActionURL returnURL)
        {
            ActionURL url = new ActionURL(MoveWebPartAction.class, c);
            url.addParameter("pageId", webPart.getPageId());
            url.addParameter("index", String.valueOf(webPart.getIndex()));
            url.addParameter("direction", String.valueOf(direction));
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getDeleteWebPartURL(Container c, String pageId, int index, ActionURL returnURL)
        {
            ActionURL url = new ActionURL(DeleteWebPartAction.class, c);
            url.addParameter("pageId", pageId);
            url.addParameter("index", index);
            url.addReturnURL(returnURL);
            return url;
        }

//        public ActionURL getHidePortalPageURL(Container c, int index, ActionURL returnURL)
//        {
//            ActionURL url = new ActionURL(HidePortalPageAction.class, c);
//            url.addParameter("index", index);
//            url.addReturnURL(returnURL);
//            return url;
//        }

        public ActionURL getHidePortalPageURL(Container c, String pageId, ActionURL returnURL)
        {
            ActionURL url = new ActionURL(HidePortalPageAction.class, c);
            url.addParameter("pageId", pageId);
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getDeleteWebPartURL(Container c, Portal.WebPart webPart, ActionURL returnURL)
        {
            return getDeleteWebPartURL(c, webPart.getPageId(), webPart.getIndex(), returnURL);
        }

        public ActionURL getExpandCollapseURL(Container c, String path, String treeId)
        {
            ActionURL url = new ActionURL(ExpandCollapseAction.class, c);
            url.addParameter("path", path);
            url.addParameter("treeId", treeId);

            return url;
        }

        @Override
        public ActionURL getFileBrowserURL(Container c, String path)
        {
            ActionURL url = new ActionURL(FileBrowserAction.class, c);
            url.addParameter("path", path);

            return url;
        }

        @Override
        public ActionURL getResetDefaultTabsURL(Container c, ActionURL returnURL)
        {
            ActionURL result = new ActionURL(ResetDefaultTabsAction.class, c);
            result.addReturnURL(returnURL);
            return result;
        }
    }


    public ProjectController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    ActionURL homeURL()
    {
        return new ActionURL(BeginAction.class, ContainerManager.getHomeContainer());
    }

    ActionURL beginURL()
    {
        return new ActionURL(BeginAction.class, getContainer());
    }

    @IgnoresTermsOfUse
    @RequiresNoPermission
    public class StartAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();

            if (c.isProject())
            {
                // This block is to handle the case where a user does not have permissions
                // to a project folder, but does have access to a subfolder.  In this case, we'd like
                // to let them see something at the project root (since this is where you land after
                // selecting the project from the projects menu), so we display an access-denied
                // message within the frame.  If the user isn't logged on, we simply show an
                // access-denied error.  This is necessary to force the login prompt to show up
                // for users with access who simply haven't logged on yet.  (brittp, 5.4.2007)
                if (!c.hasPermission(getUser(), ReadPermission.class))
                {
                    if (getUser().isGuest())
                    {
                        throw new UnauthorizedException();
                    }
                    return new HtmlView("You do not have permission to view this folder.<br>" +
                            "Please select another folder from the tree to the left.");
                }
            }
            return HttpView.redirect(c.getStartURL(getViewContext().getUser()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(getContainer().getName());
        }
    }


    public static class PageForm
    {
        private String _pageId;

        public String getPageId()
        {
            return _pageId;
        }

        public void setPageId(String pageId)
        {
            _pageId = pageId;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ResetDefaultTabsAction extends org.labkey.api.action.RedirectAction<ReturnUrlForm>
    {
        @Override
        public URLHelper getSuccessURL(ReturnUrlForm returnUrlForm)
        {
            return returnUrlForm.getReturnActionURL(getContainer().getStartURL(getUser()));
        }

        @Override
        public boolean doAction(ReturnUrlForm returnUrlForm, BindException errors) throws Exception
        {
            FolderType folderType = getContainer().getFolderType();
            if (folderType.hasConfigurableTabs())
            {
                folderType.resetDefaultTabs(getContainer());
            }
            return true;
        }

        @Override
        public void validateCommand(ReturnUrlForm target, Errors errors)
        {
            
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction<PageForm>
    {
        public ModelAndView getView(PageForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            if (null == c)
            {
                throw new NotFoundException();
            }

            boolean appendPath = true;
            String title;
            FolderType folderType = c.getFolderType();
            if (!FolderType.NONE.equals(c.getFolderType()))
            {
                title = folderType.getStartPageLabel(getViewContext());
            }
            else if (c.equals(ContainerManager.getHomeContainer()))
            {
                title = LookAndFeelProperties.getInstance(c).getShortName();
                appendPath = false;
            }
            else
                title = c.getName();

            ActionURL url = getViewContext().getActionURL();
            if (null == url || url.getExtraPath().equals("/"))
                return HttpView.redirect(homeURL());

            String pageId1 = url.getParameter("pageId");
            if (null != pageId1)
            {
                Container childContainer = ContainerManager.getChild(c, pageId1);
                if (null != childContainer && childContainer.isContainerTab())
                    throw new RedirectException(new ActionURL(BeginAction.class, childContainer));
            }

            PageConfig page = getPageConfig();
            if (title != null)
                page.setTitle(title, appendPath);
            page.setHelpTopic(folderType.getHelpTopic());

            HttpView template;
            if (!isPrint())
                template = new HomeTemplate(getViewContext(), c, new VBox(), page, new NavTree[0]);
            else
                template = new PrintTemplate(new VBox());

            String pageId = form.getPageId();
            if (pageId == null)
            {
                pageId = folderType.getDefaultPageId(getViewContext());
            }
            Portal.populatePortalView(getViewContext(), pageId, template);

            getPageConfig().setTemplate(PageConfig.Template.None);
            return template;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    /**
     * Same as begin, but used for our home page. We retain the action
     * for compatibility with old bookmarks, but redirect so doesn't show
     * up any more...
     */
    @RequiresNoPermission
    public class HomeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(beginURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    static final Path pipeline = new Path("@pipeline");
    static final Path files = new Path("@files");

    @RequiresNoPermission
    public class FileBrowserAction extends org.labkey.api.action.RedirectAction
    {
        ActionURL _redirect;

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return _redirect;
        }

        @Override
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            String p = StringUtils.trimToEmpty(getViewContext().getRequest().getParameter("path"));
            Path path = Path.decode(p);
            Container c = getViewContext().getContainer();
            Path containerPath = c.getParsedPath();
            Path webdavPath = WebdavService.getPath().append(containerPath);

            if (path.startsWith(webdavPath))
                path = webdavPath.relativize(path);

            if (path.startsWith(pipeline))
            {
                path = pipeline.relativize(path);
                _redirect = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(c).addParameter("path", path.encode());
            }
            else if (path.startsWith(files))
            {
                path = files.relativize(path);
                _redirect = PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c).addParameter("path", path.encode());
            }
            else
            {
                throw new NotFoundException();
            }
            return true;
        }

        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @IgnoresTermsOfUse
    public class DownloadProjectIconAction extends ExportAction<Object>
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            FolderType ft = getContainer().getFolderType();
            String iconPath = ft.getFolderIconPath();
            WebdavResource dir = WebdavService.get().getRootResolver().lookup(Path.parse(iconPath));
            File iconFile = dir.getFile();

            if (!NetworkDrive.exists(iconFile))
            {
                iconFile = new File(ModuleLoader.getServletContext().getRealPath(FolderType.NONE.getFolderIconPath()));  //fall back to default
                _log.warn("Could not find specified icon: "+iconPath);
            }
            PageFlowUtil.streamFile(response, iconFile, false);
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            setUseBasicAuthentication(true);
            super.checkPermissions();
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class HidePortalPageAction extends FormViewAction<CustomizePortletForm>
    {
        public ModelAndView getView(CustomizePortletForm form, boolean reshow, BindException errors) throws Exception
        {
            // UNDONE: this is used as a link, fix to make POST
            handlePost(form, errors);
            return HttpView.redirect(getSuccessURL(form));
        }

        @Override
        public void validateCommand(CustomizePortletForm target, Errors errors)
        {
            if (null == target.getPageId() && 0 > target.getIndex())
                throw new NotFoundException();
        }

        @Override
        public boolean handlePost(CustomizePortletForm form, BindException errors) throws Exception
        {
            if (null != form.getPageId())
                Portal.hidePage(getContainer(), form.getPageId());
            else if (0 <= form.getIndex())
                Portal.hidePage(getContainer(), form.getIndex());
            return true;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public URLHelper getSuccessURL(CustomizePortletForm form)
        {
            return form.getReturnURLHelper(beginURL());
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class MoveWebPartAction extends FormViewAction<MovePortletForm>
    {
        public void validateCommand(MovePortletForm target, Errors errors)
        {
        }

        public ModelAndView getView(MovePortletForm movePortletForm, boolean reshow, BindException errors) throws Exception
        {
            // UNDONE: this seems to be used a link, fix to make POST
            handlePost(movePortletForm, errors);
            return HttpView.redirect(getSuccessURL(movePortletForm));
        }

        public boolean handlePost(MovePortletForm form, BindException errors) throws Exception
        {
            Portal.WebPart webPart = Portal.getPart(getContainer(), form.getWebPartId());
            return handleMoveWebPart(webPart.getPageId(), webPart.getIndex(), form.getDirection());
        }

        public URLHelper getSuccessURL(MovePortletForm movePortletForm)
        {
            return movePortletForm.getReturnURLHelper(beginURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class AddWebPartAction extends FormViewAction<AddWebPartForm>
    {
        WebPartFactory _desc = null;
        Portal.WebPart _newPart = null;

        public void validateCommand(AddWebPartForm target, Errors errors)
        {
        }

        public ModelAndView getView(AddWebPartForm addWebPartForm, boolean reshow, BindException errors) throws Exception
        {
            // UNDONE: this seems to be used a link, fix to make POST
            handlePost(addWebPartForm, errors);
            return HttpView.redirect(getSuccessURL(addWebPartForm));
        }

        public boolean handlePost(AddWebPartForm form, BindException errors) throws Exception
        {
            _desc = Portal.getPortalPart(form.getName());
            if (null == _desc)
                return true;

            _newPart = Portal.addPart(getContainer(), form.getPageId(), _desc, form.getLocation());
            return true;
        }

        public URLHelper getSuccessURL(AddWebPartForm form)
        {
            if (null != _desc && _desc.isEditable() && _desc.showCustomizeOnInsert())
            {
                return new ActionURL(CustomizeWebPartAction.class, getContainer())
                        .addParameter("pageId", form.getPageId())
                        .addParameter("index", "" + _newPart.getIndex())
                        .addReturnURL(form.getReturnActionURL());
            }
            else
                return form.getReturnURLHelper();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class CustomizePortletApiForm implements HasViewContext
    {
        private String _pageId;
        private ViewContext _viewContext;
        private int _direction;
        private int _webPartId;

        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        public void setViewContext(ViewContext viewContext)
        {
            _viewContext = viewContext;
        }

        public String getPageId()
        {
            // if a page ID isn't provided, assume that it's the current container's page ID.  This assumption makes
            // sense as long as there's just one portal page per container.
            if (_pageId != null)
                return _pageId;
            else
                return getViewContext().getContainer().getId();
        }

        public void setPageId(String pageId)
        {
            _pageId = pageId;
        }

        public int getDirection()
        {
            return _direction;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setDirection(int direction)
        {
            _direction = direction;
        }

        public int getWebPartId()
        {
            return _webPartId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setWebPartId(int webPartId)
        {
            _webPartId = webPartId;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    @ApiVersion(10.2)
    public class DeleteWebPartAsyncAction extends ApiAction<CustomizePortletApiForm>
    {
        @Override
        public ApiResponse execute(CustomizePortletApiForm customizePortletForm, BindException errors) throws Exception
        {
            Portal.WebPart webPart = Portal.getPart(getContainer(), customizePortletForm.getWebPartId());
            if (webPart != null && handleDeleteWebPart(getViewContext().getContainer(), webPart.getPageId(), webPart.getIndex()))
                return getWebPartLayoutApiResponse(customizePortletForm.getPageId());
            else
                throw new NotFoundException("Unable to delete the specified web part.  Please refresh the page and try again.");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    @ApiVersion(10.2)
    public class MoveWebPartAsyncAction extends ApiAction<CustomizePortletApiForm>
    {
        @Override
        public ApiResponse execute(CustomizePortletApiForm movePortletForm, BindException errors) throws Exception
        {
            Portal.WebPart webPart = Portal.getPart(getContainer(), movePortletForm.getWebPartId());
            if (webPart != null && handleMoveWebPart(webPart.getPageId(), webPart.getIndex(), movePortletForm.getDirection()))
                return getWebPartLayoutApiResponse(webPart.getPageId());
            else
                throw new NotFoundException("Unable to move the specified web part.  Please refresh the page and try again.");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(10.2)
    public class GetWebPartsAction extends ApiAction<CustomizePortletApiForm>
    {
        @Override
        public ApiResponse execute(CustomizePortletApiForm movePortletForm, BindException errors) throws Exception
        {
            return getWebPartLayoutApiResponse(movePortletForm.getPageId());
        }
    }

    private ApiResponse getWebPartLayoutApiResponse(String pageId)
    {
        Collection<Portal.WebPart> parts = Portal.getParts(getViewContext().getContainer(), pageId);
        final Map<String, Object> properties = new HashMap<String, Object>();
        int lastIndex = -1;

        for (Portal.WebPart part : parts)
        {
            if (part.getIndex() < lastIndex)
                throw new IllegalStateException("Web parts should be sorted by index.");
            lastIndex = part.getIndex();

            String location = part.getLocation();
            // Why do we have two classes called SimpleWebPartFactory?!?
            location = org.labkey.api.module.SimpleWebPartFactory.getFriendlyLocationName(location);
            if (location == null)
                continue;
            List<Map<String, Object>> partList = (List<Map<String, Object>>) properties.get(location);
            if (partList == null)
            {
                partList = new ArrayList<Map<String, Object>>();
                properties.put(location, partList);
            }
            Map<String, Object> webPartProperties = new HashMap<String, Object>();
            webPartProperties.put("name", part.getName());
            webPartProperties.put("index", part.getIndex());
            webPartProperties.put("webPartId", part.getRowId());
            partList.add(webPartProperties);
        }

        return new ApiResponse()
        {
            public Map<String, Object> getProperties()
            {
                return properties;
            }
        };
    }

    private boolean handleDeleteWebPart(Container c, String pageId, int index)
    {
        List<Portal.WebPart> parts = Portal.getParts(c, pageId);
        //Changed on us..
        if (null == parts || parts.isEmpty())
            return true;

        ArrayList<Portal.WebPart> newParts = new ArrayList<Portal.WebPart>();
        for (Portal.WebPart part : parts)
            if (part.getIndex() != index)
                newParts.add(part);

        Portal.saveParts(c, pageId, newParts);
        return true;
    }

    private boolean handleMoveWebPart(String pageId, int index, int direction)
    {
        List<Portal.WebPart> parts = Portal.getParts(getContainer(), pageId);
        if (null == parts)
            return true;

        //Find the portlet. Theoretically index should be 1-based & consecutive, but
        //code defensively.
        int i;
        for (i = 0; i < parts.size(); i++)
            if (parts.get(i).getIndex() == index)
                break;

        if (i == parts.size())
            return true;

        Portal.WebPart part = parts.get(i);
        String location = part.getLocation();
        if (direction == Portal.MOVE_UP)
        {
            for (int j = i - 1; j >= 0; j--)
            {
                if (location.equals(parts.get(j).getLocation()))
                {
                    int newIndex = parts.get(j).getIndex();
                    part.setIndex(newIndex);
                    parts.get(j).setIndex(index);
                    break;
                }
            }
        }
        else
        {
            for (int j = i + 1; j < parts.size(); j++)
            {
                if (location.equals(parts.get(j).getLocation()))
                {
                    int newIndex = parts.get(j).getIndex();
                    part.setIndex(newIndex);
                    parts.get(j).setIndex(index);
                    break;
                }
            }
        }

        Portal.saveParts(getContainer(), pageId, parts);
        return true;
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteWebPartAction extends FormViewAction<CustomizePortletForm>
    {
        public void validateCommand(CustomizePortletForm target, Errors errors)
        {
        }

        public ModelAndView getView(CustomizePortletForm customizePortletForm, boolean reshow, BindException errors) throws Exception
        {
            // UNDONE: this seems to be used a link, fix to make POST
            handlePost(customizePortletForm, errors);
            return HttpView.redirect(getSuccessURL(customizePortletForm));
        }

        public boolean handlePost(CustomizePortletForm form, BindException errors) throws Exception
        {
            String pageId = form.getPageId();
            int index = form.getIndex();
            Portal.WebPart webPart = Portal.getPart(getContainer(), form.getWebPartId());
            if (null != webPart)
            {
                pageId = webPart.getPageId();
                index = webPart.getIndex();
            }

            return handleDeleteWebPart(getViewContext().getContainer(), pageId, index);
        }

        public URLHelper getSuccessURL(CustomizePortletForm customizePortletForm)
        {
            return customizePortletForm.getReturnURLHelper(beginURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin()
    public class PurgeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int rows = Portal.purge();
            return new HtmlView("deleted " + rows + " portlets<br>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    //
    // FORMS
    //

    public static class AddWebPartForm extends ReturnUrlForm
    {
        private String pageId;
        private String location;
        private String name;

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }

        public String getLocation()
        {
            return location;
        }

        public void setLocation(String location)
        {
            this.location = location;
        }


        public void setName(String name)
        {
            this.name = name;
        }


        public String getName()
        {
            return this.name;
        }
    }

    public static class CustomizePortletForm extends ReturnUrlForm
    {
        private int index;
        private String pageId;
        private int webPartId;
        private ViewContext _viewContext;

        public int getIndex()
        {
            return index;
        }

        public void setIndex(int index)
        {
            this.index = index;
        }

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }

        public int getWebPartId()
        {
            return webPartId;
        }

        public void setWebPartId(int webPartId)
        {
            this.webPartId = webPartId;
        }        
    }

    @RequiresPermissionClass(AdminPermission.class)
    @ApiVersion(11.3)
    public class CustomizeWebPartAsyncAction extends ApiAction<CustomizePortletApiForm>
    {
        @Override
        public ApiResponse execute(CustomizePortletApiForm form, BindException errors) throws Exception
        {
            Portal.WebPart webPart = Portal.getPart(getContainer(), form.getWebPartId());

            if (webPart != null)
            {
                CustomizeWebPartHelper.populatePropertyMap(getViewContext().getRequest(), webPart);
                Portal.updatePart(getUser(), webPart);
            }
            else
            {
                errors.reject(ERROR_MSG, "The web part you are trying to customize no longer exists. It may have been removed by another administrator.");
                return new ApiSimpleResponse("success", false);
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CustomizeWebPartAction extends FormViewAction<CustomizePortletForm>
    {
        Portal.WebPart _webPart;

        public void validateCommand(CustomizePortletForm target, Errors errors)
        {
        }

        public ModelAndView getView(CustomizePortletForm form, boolean reshow, BindException errors) throws Exception
        {
            // lookup the webpart by webpartId OR pageId/index
            if (form.getWebPartId() > 0)
                _webPart = Portal.getPart(getViewContext().getContainer(), form.getWebPartId());
            else
                _webPart = Portal.getPart(getViewContext().getContainer(), form.getPageId(), form.getIndex());

            ActionURL returnUrl = null != form.getReturnActionURL() ? form.getReturnActionURL() : beginURL();
            if (null == _webPart)
            {
                if (errors.hasErrors())
                    return new JspView<Object>("/org/labkey/core/portal/customizeErrors.jsp", null, errors);
                else
                    return HttpView.redirect(returnUrl);
            }

            WebPartFactory desc = Portal.getPortalPart(_webPart.getName());
            assert (null != desc);
            if (null == desc)
                return HttpView.redirect(returnUrl);

            HttpView v = desc.getEditView(_webPart, getViewContext());
            if (null == v)
                return HttpView.redirect(returnUrl);

            return v;
        }

        public boolean handlePost(CustomizePortletForm form, BindException errors) throws Exception
        {
            // lookup the webpart by webpartId OR pageId/index
            Portal.WebPart webPart;
            if (form.getWebPartId() > 0)
                webPart = Portal.getPart(getViewContext().getContainer(), form.getWebPartId());
            else
                webPart = Portal.getPart(getViewContext().getContainer(), form.getPageId(), form.getIndex());
            
            if (null == webPart)
            {
                //the web part no longer exists--probably because another admin has deleted it
                errors.reject(null, "The web part you are trying to customize no longer exists. It may have been removed by another administrator.");
                return false;
            }

            // Security check -- subclasses may have overridden isEditable for certain types of users
            WebPartFactory desc = Portal.getPortalPart(webPart.getName());
            if (!desc.isEditable())
                throw new IllegalStateException("This is not an editable web part");

            CustomizeWebPartHelper.populatePropertyMap(getViewContext().getRequest(), webPart);
            Portal.updatePart(getUser(), webPart);
            return true;
        }

        public ActionURL getSuccessURL(CustomizePortletForm form)
        {
            return null != form.getReturnActionURL() ? form.getReturnActionURL() : beginURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            // Subclasses may have overridden the display name of this webpart for a given container:
            String name;
            if (_webPart != null)
            {
                WebPartFactory factory = Portal.getPortalPart(_webPart.getName());
                name = factory.getDisplayName(getContainer(), _webPart.getLocation());
            }
            else
                name = "Web Part";

            return (new BeginAction()).appendNavTrail(root)
                    .addChild("Customize " + name);
        }
    }

    public static class CustomizeWebPartHelper
    {
        public static void populatePropertyMap(HttpServletRequest request, Portal.WebPart webPart)
        {
            Map<String, String> props = webPart.getPropertyMap();
            props.clear();

            // Loop once to find field markers -- this ensures, for example, that unchecked check boxes get set to 0
            Enumeration params = request.getParameterNames();

            while (params.hasMoreElements())
            {
                String name = (String) params.nextElement();

                if (name.startsWith(SpringActionController.FIELD_MARKER))
                    props.put(name.substring(SpringActionController.FIELD_MARKER.length()), "0");
            }

            params = request.getParameterNames();

            // TODO: Clean this up. Type checking... (though type conversion also must be done by the webpart)
            while (params.hasMoreElements())
            {
                String s = (String) params.nextElement();
                if (!"webPartId".equals(s) && !"index".equals(s) && !"pageId".equals(s) && !"x".equals(s) && !"y".equals(s) && !ActionURL.Param.returnUrl.name().equals(s))
                {
                    String value = request.getParameter(s);
                    if (value != null && !"".equals(value.trim()))
                        webPart.getPropertyMap().put(s, value.trim());
                }
            }
        }
    }


    public static class MovePortletForm extends CustomizePortletForm
    {
        private int direction;

        public int getDirection()
        {
            return direction;
        }

        public void setDirection(int direction)
        {
            this.direction = direction;
        }
    }


    @RequiresNoPermission
    public static class ExpandCollapseAction extends SimpleViewAction<CollapseExpandForm>
    {
        public ModelAndView getView(CollapseExpandForm form, BindException errors) throws Exception
        {
            NavTreeManager.expandCollapsePath(getViewContext(), form.getTreeId(), form.getPath(), form.isCollapse());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class CollapseExpandForm
    {
        private boolean collapse;
        private String path;
        private String treeId;

        public boolean isCollapse()
        {
            return collapse;
        }

        public void setCollapse(boolean collapse)
        {
            this.collapse = collapse;
        }

        public String getTreeId()
        {
            return treeId;
        }

        public void setTreeId(String treeId)
        {
            this.treeId = treeId;
        }

        public String getPath()
        {
            return path;
        }

        public void setPath(String path)
        {
            this.path = path;
        }
    }

    private static CaseInsensitiveHashMap<WebPartView.FrameType> _frameTypeMap = new CaseInsensitiveHashMap<WebPartView.FrameType>();

    static
    {
        _frameTypeMap.put("div", WebPartView.FrameType.DIV);
        _frameTypeMap.put("portal", WebPartView.FrameType.PORTAL);
        _frameTypeMap.put("none", WebPartView.FrameType.NONE);
        _frameTypeMap.put("false", WebPartView.FrameType.NONE);
        _frameTypeMap.put("0", WebPartView.FrameType.NONE);
        _frameTypeMap.put("dialog", WebPartView.FrameType.DIALOG);
        _frameTypeMap.put("left-nav", WebPartView.FrameType.LEFT_NAVIGATION);
        _frameTypeMap.put("leftNav", WebPartView.FrameType.LEFT_NAVIGATION);
        _frameTypeMap.put("left-navigation", WebPartView.FrameType.LEFT_NAVIGATION);
        _frameTypeMap.put("leftNavigation", WebPartView.FrameType.LEFT_NAVIGATION);
        _frameTypeMap.put("title", WebPartView.FrameType.TITLE);
    }

    public static class GetWebPartForm implements CustomApiForm
    {
        private Map<String, Object> _extendedProperites;

        public void bindProperties(Map<String, Object> props)
        {
            _extendedProperites = props;
        }

        public Map<String, Object> getExtendedProperites()
        {
            return _extendedProperites;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetWebPartAction extends ApiAction<GetWebPartForm>
    {
        public static final String PARAM_WEBPART = "webpart.name";

        public ApiResponse execute(GetWebPartForm form, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            String qs = request.getQueryString();
            String webPartName = request.getParameter(PARAM_WEBPART);

            if (null == webPartName || webPartName.length() == 0)
                throw new IllegalArgumentException("You must provide a value for the " + PARAM_WEBPART + " parameter!");

            WebPartFactory factory = Portal.getPortalPartCaseInsensitive(webPartName);
            if (null == factory)
                throw new RuntimeException("Couldn't get the web part factory for web part '" + webPartName + "'!");

            Portal.WebPart part = factory.createWebPart();
            if (null == part)
                throw new RuntimeException("Couldn't create web part '" + webPartName + "'!");

            part.setProperties(qs);
            part.setExtendedProperties(form.getExtendedProperites());

            WebPartView view = Portal.getWebPartViewSafe(factory, getViewContext(), part);
            if (null == view)
                throw new RuntimeException("Couldn't create web part view for part '" + webPartName + "'!");

            String frame = StringUtils.trimToEmpty(request.getParameter("webpart.frame"));
            if (null != frame && frame.length() > 0 && _frameTypeMap.containsKey(frame))
                view.setFrame(_frameTypeMap.get(frame));

            String bodyClass = StringUtils.trimToEmpty(request.getParameter("webpart.bodyClass"));
            if (null != bodyClass && bodyClass.length() > 0)
                view.setBodyClass(bodyClass);

            String title = StringUtils.trimToEmpty(request.getParameter("webpart.title"));
            if (null != title && title.length() > 0)
                view.setTitle(title);

            String titleHref = StringUtils.trimToEmpty(request.getParameter("webpart.titleHref"));
            if (null != titleHref && titleHref.length() > 0)
                view.setTitleHref(titleHref);

            ApiResponse resp = new ApiSimpleResponse();
            LinkedHashSet<ClientDependency> dependencies = view.getClientDependencies();
            LinkedHashSet<String> includes = new LinkedHashSet<String>();
            LinkedHashSet<String> implicitIncludes = new LinkedHashSet<String>();
            PageFlowUtil.getJavaScriptFiles(getContainer(), getUser(), dependencies, includes, implicitIncludes);

            LinkedHashSet<String> cssScripts = new LinkedHashSet<String>();
            LinkedHashSet<String> implicitCssScripts = new LinkedHashSet<String>();
            for (ClientDependency d : dependencies)
            {
                cssScripts.addAll(d.getCssPaths(getContainer(), getUser(), AppProps.getInstance().isDevMode()));
                implicitCssScripts.addAll(d.getCssPaths(getContainer(), getUser(), AppProps.getInstance().isDevMode()));

                implicitCssScripts.addAll(d.getCssPaths(getContainer(), getUser(), !AppProps.getInstance().isDevMode()));
            }


            MockHttpServletResponse mr = new MockHttpServletResponse();
            view.render(request, mr);

            if (mr.getStatus() != HttpServletResponse.SC_OK){
                view.render(request, getViewContext().getResponse());
                return null;
            }

            resp.getProperties().put("html", mr.getContentAsString());
            resp.getProperties().put("requiredJsScripts", includes);
            resp.getProperties().put("implicitJsIncludes", implicitIncludes);
            resp.getProperties().put("requiredCssScripts", cssScripts);
            resp.getProperties().put("implicitCssIncludes", implicitCssScripts);
            resp.getProperties().put("moduleContext", PageFlowUtil.getModuleClientContext(getContainer(), getUser(), dependencies));

            return resp;
        }
    }


    public static class GetContainersForm
    {
        private Container[] _container;
        private boolean _multipleContainers = false;
        private boolean _includeSubfolders = false;
        private int _depth = Integer.MAX_VALUE;

        public Container[] getContainer()
        {
            return _container;
        }

        public void setContainer(Container[] container)
        {
            _container = container;
        }

        public boolean isMultipleContainers()
        {
            return _multipleContainers;
        }

        public void setMultipleContainers(boolean multipleContainers)
        {
            _multipleContainers = multipleContainers;
        }

        public boolean isIncludeSubfolders()
        {
            return _includeSubfolders;
        }

        public void setIncludeSubfolders(boolean includeSubfolders)
        {
            _includeSubfolders = includeSubfolders;
        }

        public int getDepth()
        {
            return _depth;
        }

        public void setDepth(int depth)
        {
            _depth = depth;
        }
    }

    /**
     * Returns all containers visible to the current user
     */
    @RequiresNoPermission
    public class GetContainersAction extends ApiAction<GetContainersForm>
    {
        int _requestedDepth;

        public ApiResponse execute(GetContainersForm form, BindException errors) throws Exception
        {
            _requestedDepth = form.isIncludeSubfolders() ? form.getDepth() : 1;
            ApiSimpleResponse response = new ApiSimpleResponse();
            User user = getViewContext().getUser();

            Map<String, Object> resultMap;
            if (form.isMultipleContainers() || (form.getContainer() != null && form.getContainer().length > 1))
            {
                Set<Container> containers = new LinkedHashSet<Container>();
                if (form.getContainer() != null)
                    containers.addAll(Arrays.asList(form.getContainer()));

                if (containers.isEmpty())
                    containers.add(getViewContext().getContainer());

                List<Map<String, Object>> containerJSON = new ArrayList<Map<String, Object>>();
                for (Container c : containers)
                {
                    if (c != null)
                        containerJSON.add(getContainerJSON(c, user));
                }
                resultMap = Collections.<String, Object>singletonMap("containers", containerJSON);
            }
            else
            {
                Container c = getViewContext().getContainer();
                if (form.getContainer() != null && form.getContainer().length > 0)
                    c = form.getContainer()[0];
                resultMap = getContainerJSON(c, user);
            }

            response.putAll(resultMap);
            return response;
        }

        Map<String, Object> getContainerJSON(Container container, User user)
        {
            Map<String, Object> resultMap = container.toJSON(user);
            resultMap.put("children", getVisibleChildren(container, user, 0));
            return resultMap;
        }

        //return only those paths through the container tree leading to a container to which the user has permission
        protected List<Map<String, Object>> getVisibleChildren(Container parent, User user, int depth)
        {
            List<Map<String, Object>> visibleChildren = new ArrayList<Map<String, Object>>();
            if (depth == _requestedDepth)
                return visibleChildren;

            for (Container child : parent.getChildren())
            {
                List<Map<String, Object>> theseChildren = getVisibleChildren(child, user, depth + 1);
                if (child.hasPermission(user, ReadPermission.class) || !theseChildren.isEmpty())
                {
                    Map<String, Object> visibleChild = child.toJSON(user);
                    visibleChild.put("children", theseChildren);
                    visibleChildren.add(visibleChild);
                }

            }

            return visibleChildren;
        }
    }


    @RequiresNoPermission
    public class IconAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String name = StringUtils.trimToEmpty(getViewContext().getRequest().getParameter("name"));
            String path;
            if (name.endsWith("/"))
                path = "/_icon/folder.gif";
            else
                path = Attachment.getFileIcon(name);

            // allow caching
            getViewContext().getResponse().setDateHeader("Expires", HeartBeat.currentTimeMillis() + 1000 * 60 * 60 * 24);
            getViewContext().getResponse().setHeader("Cache-Control", "private");

            RequestDispatcher d = getViewContext().getRequest().getRequestDispatcher(path);
            getPageConfig().setTemplate(PageConfig.Template.None);
            d.forward(getViewContext().getRequest(), getViewContext().getResponse());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresNoPermission
    public class RedirectAction extends SimpleViewAction<ReturnUrlForm>
    {
        @Override
        public ModelAndView getView(ReturnUrlForm returnUrlForm, BindException errors) throws Exception
        {
            ReturnURLString r = returnUrlForm.getReturnUrl();
            if (null != r && !r.isEmpty())
                throw new RedirectException(r);
            throw new RedirectException(AppProps.getInstance().getHomePageUrl());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class SetWebPartPermissionsAction extends ApiAction<WebPartPermissionsForm>
    {
        @Override
        public ApiResponse execute(WebPartPermissionsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            Portal.WebPart webPart = Portal.getPart(getContainer(), form.getWebPartId());
            if (webPart != null)
            {
                Permission permission = RoleManager.getPermission(form.getPermission());
                Container permissionContainer = null;
                
                if(form.getContainerPath() != null)
                {
                    permissionContainer = ContainerManager.getForPath(form.getContainerPath());

                    // Only throw NotFoundException if the user actively set the container to something else.
                    if(permissionContainer == null)
                    {
                        throw new NotFoundException("Could not resolve the folder for path: \"" + form.getContainerPath() +
                                "\". The path may be incorrect or the folder may no longer exist.");
                    }
                }

                if(permission == null)
                {
                    throw new NotFoundException("Could not resolve the permission " + form.getPermission() +
                            ". The permission may no longer exist, or may not yet be registered.");
                }

                webPart.setPermission(form.getPermission());
                webPart.setPermissionContainer(permissionContainer);

                Portal.updatePart(getUser(), webPart);

                resp.put("success", "true");
                resp.put("webPartId", webPart.getRowId());
                resp.put("permission", webPart.getPermission());
                resp.put("permissionContainer", webPart.getPermissionContainer() != null ?  webPart.getPermissionContainer().getId() : null);

                return resp;
            }
            else
                throw new NotFoundException("WebPart not found. Unable to set WebPart permissions.");
        }
    }

    public static class WebPartPermissionsForm implements HasViewContext
    {
        private String _pageId;
        private ViewContext _viewContext;
        private int _webPartId;
        private String _permission;
        private String _containerPath;

        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        public void setViewContext(ViewContext viewContext)
        {
            _viewContext = viewContext;
        }

        public String getPageId()
        {
            // if a page ID isn't provided, assume that it's the current container's page ID.  This assumption makes
            // sense as long as there's just one portal page per container.
            if (_pageId != null)
                return _pageId;
            else
                return getViewContext().getContainer().getId();
        }

        public void setPageId(String pageId)
        {
            _pageId = pageId;
        }

        public int getWebPartId()
        {
            return _webPartId;
        }

        public void setWebPartId(int webPartId)
        {
            _webPartId = webPartId;
        }

        public String getPermission()
        {
            return _permission;
        }

        public void setPermission(String permission)
        {
            _permission = permission;
        }

        public String getContainerPath()
        {
            return _containerPath;
        }

        public void setContainerPath(String containerPath)
        {
            _containerPath = containerPath;
        }
    }
}
