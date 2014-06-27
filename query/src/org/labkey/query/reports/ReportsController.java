/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.query.reports;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.ExtFormAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.views.DataViewInfo;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.data.views.DataViewService;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.message.digest.DailyMessageDigest;
import org.labkey.api.message.digest.ReportAndDatasetChangeDigestProvider;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.pipeline.file.PathMapperImpl;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.ExternalScriptEngineFactory;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.reports.RConnectionHolder;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportContentEmailManager;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.RserveScriptEngine;
import org.labkey.api.reports.actions.ReportForm;
import org.labkey.api.reports.model.DataViewEditForm;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.model.ViewInfo;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ChartReport;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.RReportJob;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.view.AjaxScriptReportView;
import org.labkey.api.reports.report.view.AjaxScriptReportView.Mode;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.reports.report.view.RenderBackgroundRReportView;
import org.labkey.api.reports.report.view.ReportDesignBean;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AdminConsole.SettingsLinkType;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.thumbnail.BaseThumbnailAction;
import org.labkey.api.thumbnail.DynamicThumbnailProvider;
import org.labkey.api.thumbnail.StaticThumbnailProvider;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.thumbnail.ThumbnailService.ImageType;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpRedirectView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.query.DataViewsWebPartFactory;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.reports.chart.ChartServiceImpl;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * User: Karl Lum
 * Date: Apr 19, 2007
 */
public class ReportsController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(ReportsController.class);
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ReportsController.class);
    private static final MimeMap _mimeMap = new MimeMap();

    public static final String TAB_SOURCE = "source";
    public static final String TAB_VIEW = "view";

    public static class ReportUrlsImpl implements ReportUrls
    {
        public ActionURL urlDownloadData(Container c)
        {
            return new ActionURL(DownloadInputDataAction.class, c);
        }

        public ActionURL urlRunReport(Container c)
        {
            return new ActionURL(RunReportAction.class, c);
        }

        @Override
        public ActionURL urlAjaxSaveScriptReport(Container c)
        {
            return new ActionURL(AjaxSaveScriptReportAction.class, c);
        }

        public ActionURL urlDesignChart(Container c)
        {
            return new ActionURL(DesignChartAction.class, c);
        }

        @Override
        public ActionURL urlViewScriptReport(Container c)
        {
            return new ActionURL(ViewScriptReportAction.class, c);
        }

        @Override
        public ActionURL urlViewBackgroundScriptReport(Container c)
        {
            return new ActionURL(GetBackgroundReportResultsAction.class, c);
        }

        public ActionURL urlCreateScriptReport(Container c)
        {
            return new ActionURL(CreateScriptReportAction.class, c);
        }

        public ActionURL urlStreamFile(Container c)
        {
            return new ActionURL(StreamFileAction.class, c);
        }
        
        public ActionURL urlReportSections(Container c)
        {
            return new ActionURL(ReportSectionsAction.class, c);
        }

        public ActionURL urlManageViews(Container c)
        {
            return new ActionURL(ManageViewsAction.class, c);
        }

        public ActionURL urlPlotChart(Container c)
        {
            return new ActionURL(PlotChartAction.class, c);
        }

        public ActionURL urlDeleteReport(Container c)
        {
            return new ActionURL(DeleteReportAction.class, c);
        }

        public ActionURL urlExportCrosstab(Container c)
        {
            return new ActionURL(CrosstabExportAction.class, c);
        }

        @Override
        public ActionURL urlThumbnail(Container c, Report r)
        {
            ActionURL url = new ActionURL(ThumbnailAction.class, c);
            url.addParameter("reportId", r.getDescriptor().getReportId().toString());
            return url;
        }

        @Override
        public ActionURL urlIcon(Container c, Report r)
        {
            ActionURL url = new ActionURL(ThumbnailAction.class, c);
            url.addParameter("reportId", r.getDescriptor().getReportId().toString());
            url.addParameter("imageType", "Small");
            return url;
        }

        @Override
        public Class<? extends Controller> getDownloadClass()
        {
            return DownloadAction.class;
        }

        @Override
        public ActionURL urlReportInfo(Container c)
        {
            return new ActionURL(ReportInfoAction.class, c);
        }

        @Override
        public ActionURL urlAttachmentReport(Container c, ActionURL returnURL)
        {
            return getCreateAttachmentReportURL(c, returnURL);
        }

        @Override
        public ActionURL urlLinkReport(Container c, ActionURL returnURL)
        {
            return getCreateLinkReportURL(c, returnURL);
        }

        @Override
        public ActionURL urlReportDetails(Container c, Report r)
        {
            return new ActionURL(DetailsAction.class, c).addParameter(ReportDescriptor.Prop.reportId, r.getDescriptor().getReportId().toString());
        }

        @Override
        public ActionURL urlQueryReport(Container c, Report r)
        {
            return new ActionURL(RenderQueryReport.class, c).addParameter(ReportDescriptor.Prop.reportId, r.getDescriptor().getReportId().toString());
        }

        @Override
        public ActionURL urlManageNotifications(Container c)
        {
            return new ActionURL(ManageNotificationsAction.class, c);
        }
    }

    public ReportsController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    public static void registerAdminConsoleLinks()
    {
        AdminConsole.addLink(SettingsLinkType.Configuration, "views and scripting", new ActionURL(ConfigureReportsAndScriptsAction.class, ContainerManager.getRoot()));
    }

    @RequiresPermissionClass(ReadPermission.class)
    @Action(ActionType.SelectData)
    public class DesignChartAction extends SimpleViewAction<ChartDesignerBean>
    {
        public ModelAndView getView(ChartDesignerBean form, BindException errors) throws Exception
        {
            Map<String, String> props = new HashMap<>();
            for (Pair<String, String> param : form.getParameters())
            {
                props.put(param.getKey(), param.getValue());
            }
            props.put("isAdmin", String.valueOf(getContainer().hasPermission(getUser(), AdminPermission.class)));
            props.put("isGuest", String.valueOf(getUser().isGuest()));
            HttpView view = new GWTView("org.labkey.reports.designer.ChartDesigner", props);
            return new VBox(view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Chart View");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @Action(ActionType.SelectData)
    public class ChartServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new ChartServiceImpl(getViewContext());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @Action(ActionType.SelectData)
    public class PlotChartAction extends SimpleViewAction<ChartDesignerBean>
    {
        public ModelAndView getView(ChartDesignerBean form, BindException errors) throws Exception
        {
            final ViewContext context = getViewContext();
            Report report = null;

            if (null != form.getReportId())
                report = form.getReportId().getReport(getViewContext());

            if (report == null)
            {
                List<String> reportIds = context.getList("reportId");
                if (reportIds != null && !reportIds.isEmpty())
                    report = ReportService.get().getReport(NumberUtils.toInt(reportIds.get(0)));
            }

            if (report == null)
            {
                report = ReportService.get().createFromQueryString(context.getActionURL().getQueryString());
                if (report != null)
                {
                    // set the container in case we need to get a securable resource for the report descriptor
                    if (report.getDescriptor().lookupContainer() == null)
                        report.getDescriptor().setContainer(context.getContainer().getId());
                }
            }

            if (report instanceof Report.ImageReport)
                ((Report.ImageReport)report).renderImage(context);
            else if (report != null)
                throw new RuntimeException("Report must implement Report.ImageReport to use the plot chart action");
            
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class PlotChartApiAction extends ApiAction<ChartDesignerBean>
    {
        public ApiResponse execute(ChartDesignerBean form, BindException errors) throws Exception
        {
            verifyBean(form);
            ApiSimpleResponse response = new ApiSimpleResponse();
            Report report = form.getReport(getViewContext());
            if (report != null)
            {
                ActionURL url;
                if (null != report.getDescriptor().getReportId())
                    url = ReportUtil.getPlotChartURL(getViewContext(), report);
                else
                {
                    url = new ActionURL(PlotChartAction.class, getContainer());
                    for (Pair<String, String> param : form.getParameters())
                    {
                        url.addParameter(param.getKey(), param.getValue());
                    }
                }
                response.put("imageURL", url.getLocalURIString());

                if (report instanceof Report.ImageMapGenerator && !StringUtils.isEmpty(form.getImageMapName()))
                {
                    String map = ((Report.ImageMapGenerator)report).generateImageMap(getViewContext(), form.getImageMapName(),
                            form.getImageMapCallback(), form.getImageMapCallbackColumns());
                    response.put("imageMap", map);
                }
                return response;
            }
            throw new ServletException("Unable to render the specified chart");
        }

        private ChartDesignerBean verifyBean(ChartDesignerBean form) throws Exception
        {
            // a saved report
            if (null != form.getReportId())
                return form;

            UserSchema schema = (UserSchema) DefaultSchema.get(getUser(), getContainer()).getSchema(form.getSchemaName());
            if (schema != null)
            {
                QuerySettings settings = schema.getSettings(getViewContext(), form.getDataRegionName());
                QueryView view = schema.createView(getViewContext(), settings);
                if (view.getTable() == null)
                    throw new IllegalArgumentException("the specified query name: '" + form.getQueryName() + "' does not exist");
            }
            else
                throw new IllegalArgumentException("the specified schema: '" + form.getSchemaName() + "' does not exist");

            if (form.getReportType() == null)
            {
                // need to find a better way to handle this, if they are querying a study schema they have
                // to use a study report type in order to get study security in their chart.
                form.setReportType("study".equals(form.getSchemaName()) ? "Study.chartQueryReport" : ChartQueryReport.TYPE);
            }
            return form;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DeleteReportAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String reportId = getViewContext().getRequest().getParameter(ReportDescriptor.Prop.reportId.name());
            String forwardUrl = getViewContext().getRequest().getParameter(ReportUtil.FORWARD_URL);
            Report report = null;

            if (reportId != null)
                report = ReportService.get().getReport(NumberUtils.toInt(reportId));

            if (report != null)
            {
                if (!report.canDelete(getUser(), getContainer()))
                    throw new UnauthorizedException();
                ReportService.get().deleteReport(getViewContext(), report);
            }
            return HttpView.redirect(forwardUrl);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ConfigureReportsAndScriptsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/query/reports/view/configReportsAndScripts.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setHelpTopic(new HelpTopic("configureScripting"));
            root.addChild("Admin Console", PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL());
            return root.addChild("Views and Scripting Configuration");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ScriptEnginesSummaryAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            List<Map<String, Object>> views = new ArrayList<>();

            ScriptEngineManager manager = ServiceRegistry.get().getService(ScriptEngineManager.class);

            for (ScriptEngineFactory factory : manager.getEngineFactories())
            {
                Map<String, Object> record = new HashMap<>();

                record.put("name", factory.getEngineName());
                record.put("extensions", StringUtils.join(factory.getExtensions(), ','));
                record.put("languageName", factory.getLanguageName());
                record.put("languageVersion", factory.getLanguageVersion());

                boolean isExternal = factory instanceof ExternalScriptEngineFactory;
                record.put("external", String.valueOf(isExternal));
                record.put("enabled", String.valueOf(LabkeyScriptEngineManager.isFactoryEnabled(factory)));

                if (isExternal)
                {
                    // extra metadata for external engines
                    ExternalScriptEngineDefinition def = ((ExternalScriptEngineFactory)factory).getDefinition();

                    if (def instanceof LabkeyScriptEngineManager.EngineDefinition)
                    {
                        record.put("key", ((LabkeyScriptEngineManager.EngineDefinition)def).getKey());
                        record.put("remote", def.isRemote());
                    }

                    record.put("exePath", def.getExePath());
                    record.put("exeCommand", def.getExeCommand());
                    record.put("outputFileName", def.getOutputFileName());

                    if (def.isRemote())
                    {
                        record.put("machine", def.getMachine());
                        record.put("port", String.valueOf(def.getPort()));

                        PathMapper pathMap = def.getPathMap();
                        if (pathMap != null)
                            record.put("pathMap", ((PathMapperImpl)pathMap).toJSON());
                        else
                            record.put("pathMap", null);

                        record.put("user", def.getUser());
                        record.put("password", def.getPassword());
                    }
                }
                views.add(record);
            }
            return new ApiSimpleResponse("views", views);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ScriptEnginesSaveAction extends ExtFormAction<LabkeyScriptEngineManager.EngineDefinition>
    {
        @Override
        public void validateForm(LabkeyScriptEngineManager.EngineDefinition def, Errors errors)
        {
            // validate definition
            if (StringUtils.isEmpty(def.getName()))
                errors.rejectValue("name", ERROR_MSG, "The Name field cannot be empty");

            if (def.isExternal())
            {
                //
                // If the engine is remote then don't validate the exe and command line values
                //
                if (!def.isRemote())
                {
                    File rexe = new File(def.getExePath());
                    if (!rexe.exists())
                        errors.rejectValue("exePath", ERROR_MSG, "The program location: '" + def.getExePath() + "' does not exist");
                    if (rexe.isDirectory())
                        errors.rejectValue("exePath", ERROR_MSG, "Please specify the entire path to the program, not just the directory (e.g., 'c:/Program Files/R/R-2.7.1/bin/R.exe)");
                }
            }
        }

        public ApiResponse execute(LabkeyScriptEngineManager.EngineDefinition def, BindException errors) throws Exception
        {
            LabkeyScriptEngineManager.saveDefinition(def);

            return new ApiSimpleResponse("success", true);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ScriptEnginesDeleteAction extends ApiAction<LabkeyScriptEngineManager.EngineDefinition>
    {
        public ApiResponse execute(LabkeyScriptEngineManager.EngineDefinition def, BindException errors) throws Exception
        {
            LabkeyScriptEngineManager.deleteDefinition(def);
            return new ApiSimpleResponse("success", true);
        }
    }

    public static class CreateSessionForm
    {
        private Object _clientContext;

        public Object getClientContext()
        {
            return _clientContext;
        }

        public void setClientContext(Object clientContext)
        {
            _clientContext = clientContext;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class CreateSessionAction extends MutatingApiAction<CreateSessionForm>
    {
        public ApiResponse execute(CreateSessionForm form, BindException errors) throws Exception
        {
            String reportSessionId = null;
            //
            // create a unique key for this session.  Note that a report session id can never
            // span sessions but does span multiple requests within a session
            //
            if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
            {
                reportSessionId = ReportUtil.createReportSessionId();
                getViewContext().getSession().setAttribute(reportSessionId,
                        new RConnectionHolder(reportSessionId, form.getClientContext()));
            }
            else
            {
                //
                // consider: don't throw an exception
                //
                throw new ScriptException("This feature requires that the 'Rserve Reports' experimental feature be turned on");
            }

            return new ApiSimpleResponse(Report.renderParam.reportSessionId.name(), reportSessionId);
        }
    }

    public static class DeleteSessionForm
    {
        private String _reportSessionId;

        public String getReportSessionId()
        {
            return _reportSessionId;
        }

        public void setReportSessionId(String reportSessionId)
        {
            _reportSessionId = reportSessionId;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DeleteSessionAction extends MutatingApiAction<DeleteSessionForm>
    {
        public ApiResponse execute(DeleteSessionForm form , BindException errors) throws Exception
        {
            if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
            {
                String reportSessionId = form.getReportSessionId();

                if (null != reportSessionId)
                {
                    RConnectionHolder rh = (RConnectionHolder) getViewContext().getSession().getAttribute(reportSessionId);
                    if (rh != null)
                    {
                        synchronized(rh)
                        {
                            if (!rh.isInUse())
                            {
                                rh.setConnection(null);
                                getViewContext().getSession().removeAttribute(reportSessionId);
                            }
                            else
                            {
                                throw new ScriptException("Cannot delete a report session that is currently in use");
                            }
                        }
                    }
                }
                //
                //  Don't error if rh could not be found.  This could happen because the session timed out.
                //
            }
            else
            {
                throw new ScriptException("This feature requires that the 'Rserve Reports' experimental feature be turned on");
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class ExecuteScriptForm
    {
        private String _reportSessionId;
        private String _reportId;
        private String _reportName;
        private String _schemaName;
        private String _queryName;
        private String _functionName;
        private Map<String, Object> _inputParams;

        public ExecuteScriptForm()
        {
            _inputParams = new ArrayListMap<>();
        }

        public String getReportSessionId()
        {
            return _reportSessionId;
        }

        public void setReportSessionId(String reportSessionId)
        {
            _reportSessionId = reportSessionId;
        }

        public String getReportId()
        {
            return _reportId;
        }

        public void setReportId(String reportId)
        {
            _reportId = reportId;
        }

        public String getReportName()
        {
            return _reportName;
        }

        public void setReportName(String reportName)
        {
            _reportName = reportName;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public String getFunctionName()
        {
            return _functionName;
        }

        public void setFunctionName(String functionName)
        {
            _functionName = functionName;
        }

        public Map<String, Object> getInputParams()
        {
            return _inputParams;
        }

        public void setInputParams(Map<String, Object> inputParams)
        {
            _inputParams = inputParams;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @Action(ActionType.SelectData)
    public class ExecuteAction extends MutatingApiAction<ExecuteScriptForm>
    {
        //
        // returned object from the execute action must have these fields
        //
        public static final String OUTPUT_CONSOLE = "console";
        public static final String OUTPUT_ERROR = "errors";
        public static final String OUTPUT_PARAMS = "outputParams";

        public ApiResponse execute(ExecuteScriptForm form, BindException errors) throws Exception
        {
            List<ScriptOutput> outputs;
            String reportSessionId = form.getReportSessionId();
            Map<String, Object> inputParams = form.getInputParams();

            // if we have a script (instead of report name) then execute it directly
            if (null != form.getFunctionName())
            {
                outputs = execFunction(form.getFunctionName(), reportSessionId, inputParams);
            }
            else
            {
                outputs = execReport(getReport(form), reportSessionId, inputParams);
            }

            //
            // break the outputs into console, error, and output params
            //
            return buildResponse(outputs);
        }

        private List<ScriptOutput> execReport(Report report, String reportSessionId, Map<String, Object> inputParams) throws Exception
        {
            //
            // validate that the underlying report is present and based on a script
            //
            if (null == report)
                throw new IllegalArgumentException("Unknown report id or report name");

            if (!(report instanceof Report.ScriptExecutor))
                throw new IllegalArgumentException("The specified report is not based upon a script and therefore cannot be executed.");

            //
            // used a shared sesssion if specfied and the feature is turned on
            //
            if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
            {
                getViewContext().put(Report.renderParam.reportSessionId.name(), reportSessionId);
            }

            //
            // execute the script
            //
            Report.ScriptExecutor exec = (Report.ScriptExecutor) report;
            return exec.executeScript(getViewContext(), inputParams);
        }

        private List<ScriptOutput> execFunction(String functionName, String reportSessionId, Map<String, Object> inputParams) throws Exception
        {
            List<ScriptOutput> scriptOutputs = new ArrayList<>();

            //
            // we must be using Rserve for this
            //
            if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
            {
                try
                {
                    Object result = RserveScriptEngine.eval(getViewContext(), functionName, reportSessionId, inputParams);
                    //
                    // currently only support a single json return value
                    //
                    scriptOutputs.add(new ScriptOutput(ScriptOutput.ScriptOutputType.json, "jsonout:", result.toString()));
                }
                catch(Exception e)
                {

                    String message = ReportUtil.makeExceptionString(e, "%s: %s");
                    scriptOutputs.add(new ScriptOutput(ScriptOutput.ScriptOutputType.error, e.getClass().getName(), message));
                }
            }
            else
            {
                throw new ScriptException("Executing a function requires that the 'Rserve Reports' experimental feature be turned on");
            }

            return scriptOutputs;
        }

        //
        // Build our response object.  It must look like:
        // {
        //      console: string[]
        //      errors: string[]
        //      outputParams: ScriptOutput[]
        // }
        //
        private ApiResponse buildResponse(List<ScriptOutput> outputs) throws Exception
        {
            ArrayList<String> consoleOutputs = new ArrayList<>();
            ArrayList<String> errorOutputs = new ArrayList<>();
            ArrayList<ScriptOutput> removeItems = new ArrayList<>();

            // collect any console and error output types and put them in their own collections
            for (ScriptOutput output : outputs)
            {
                if (output.getType() == ScriptOutput.ScriptOutputType.console)
                {
                    consoleOutputs.add(output.getValue());
                    removeItems.add(output);
                }

                if (output.getType() == ScriptOutput.ScriptOutputType.error)
                {
                    errorOutputs.add(output.getValue());
                    removeItems.add(output);
                }
            }

            // remove console and error outputs
            outputs.removeAll(removeItems);

            // build the response object
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put(OUTPUT_CONSOLE, consoleOutputs);
            response.put(OUTPUT_ERROR, errorOutputs);
            response.putBeanList(OUTPUT_PARAMS, outputs, "name", "type", "value");
            return response;
        }

        private Report getReport(ExecuteScriptForm form)
        {
            Report report;
            String reportId = form.getReportId();

            if (reportId != null)
            {
                report = ReportUtil.getReportById(getViewContext(), reportId);
            }
            else
            {
                String reportName = form.getReportName();
                String key = ReportUtil.getReportKey(form.getSchemaName(), form.getQueryName());

                // consider:  moving this logic into the getReportKey function for 14.2
                // see issue 19206 for more details
                if (StringUtils.isBlank(key))
                    key = reportName;

                report = ReportUtil.getReportByName(getViewContext(), reportName, key);
            }
            return report;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class getSessionsAction extends MutatingApiAction
    {
        public static final String REPORT_SESSIONS = "reportSessions";
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ArrayList<ReportSession> outputReportSessions = new ArrayList<>();

            if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
            {
                synchronized (RConnectionHolder.getReportSessions())
                {
                    Iterator<String> i = RConnectionHolder.getReportSessions().iterator();
                    while (i.hasNext())
                    {
                        String reportSessionId = i.next();
                        // ensure we only return valid sessions for this session state
                        RConnectionHolder rh = (RConnectionHolder) getViewContext().getSession().getAttribute(reportSessionId);
                        if (rh != null)
                        {
                            outputReportSessions.add(new ReportSession(rh));
                        }
                    }
                }
            }
            else
            {
                //
                // consider: don't throw an exception
                //
                throw new ScriptException("This feature requires that the 'Rserve Reports' experimental feature be turned on");
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.putBeanList(REPORT_SESSIONS, outputReportSessions, "reportSessionId", "inUse", "clientContext");
            return response;
        }

        public class ReportSession
        {
            private String _reportSessionId;
            private boolean _inUse;
            private Object _clientContext;

            public ReportSession(RConnectionHolder rh)
            {
                _reportSessionId = rh.getReportSessionId();
                _inUse = rh.isInUse();
                _clientContext = rh.getClientContext();
            }

            public boolean getInUse()
            {
                return _inUse;
            }

            public String getReportSessionId()
            {
                return _reportSessionId;
            }

            public Object getClientContext()
            {
                return _clientContext;
            }
        }
    }

    @RequiresPermissionClass(InsertPermission.class)  // Need insert AND developer (checked below)
    public class CreateScriptReportAction extends FormViewAction<ScriptReportBean>
    {
        private Report _report;

        public void validateCommand(ScriptReportBean form, Errors errors)
        {
        }

        public ModelAndView getView(ScriptReportBean form, boolean reshow, BindException errors) throws Exception
        {
            _report = form.getReport(getViewContext());
            List<ValidationError> reportErrors = new ArrayList<>();
            validatePermissions(getViewContext(), _report, reportErrors);

            if (reportErrors.isEmpty())
                return new AjaxScriptReportView(null, form, Mode.create);
            else
            {
                StringBuilder sb = new StringBuilder();

                for (ValidationError error : reportErrors)
                    sb.append(error.getMessage()).append("<br>");

                return new HtmlView(sb.toString());
            }
        }

        public boolean handlePost(ScriptReportBean form, BindException errors) throws Exception
        {
            return true;
        }

        public ActionURL getSuccessURL(ScriptReportBean form)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("jsViews");
            if (_report != null)
                return root.addChild(_report.getTypeDescription() + " Builder");

            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    @Action(ActionType.SelectData)
    public class ViewScriptReportAction extends ApiAction<ScriptReportBean>
    {
        @Override
        public ApiResponse execute(ScriptReportBean bean, BindException errors) throws Exception
        {
            // TODO: Do something with errors?

            // ApiAction doesn't seem to bind URL parameters on POST... so manually populate them into the bean.
            errors.addAllErrors(defaultBindParameters(bean, getViewContext().getBindPropertyValues()));
            VBox resultsView = new VBox();
            Report report = bean.getReport(getViewContext());
            if (report != null)
            {
                if (bean.getIsDirty())
                    report.clearCache();

                // for now, limit pipeline view to saved R reports
                if (null != bean.getReportId() && bean.isRunInBackground())
                {
                    if (report instanceof RReport)
                        resultsView.addView(new RenderBackgroundRReportView((RReport)report));
                }
                else
                    resultsView.addView(report.renderReport(getViewContext()));
            }

            // TODO: assert?
            if (null != resultsView)
            {
                Map<String, Object> resultProperties = new HashMap<>();

                LinkedHashSet<ClientDependency> dependencies = resultsView.getClientDependencies();
                LinkedHashSet<String> cssScripts = new LinkedHashSet<>();
                addScriptDependencies(bean, dependencies, cssScripts);

                LinkedHashSet<String> includes = new LinkedHashSet<>();
                LinkedHashSet<String> implicitIncludes = new LinkedHashSet<>();
                PageFlowUtil.getJavaScriptFiles(getContainer(), getUser(), dependencies, includes, implicitIncludes);

                MockHttpServletResponse mr = new MockHttpServletResponse();
                resultsView.render(getViewContext().getRequest(), mr);

                if (mr.getStatus() != HttpServletResponse.SC_OK){
                    resultsView.render(getViewContext().getRequest(), getViewContext().getResponse());
                    return null;
                }

                resultProperties.put("html", mr.getContentAsString());
                resultProperties.put("requiredJsScripts", includes);
                resultProperties.put("requiredCssScripts", cssScripts);
                resultProperties.put("implicitJsIncludes", implicitIncludes);
                resultProperties.put("moduleContext", PageFlowUtil.getModuleClientContext(getViewContext(), dependencies));
                return new ApiSimpleResponse(resultProperties);
            }

/*
            if (null != resultsView)
                resultsView.render(getViewContext().getRequest(), getViewContext().getResponse());
*/

            return null;
        }

        private void addScriptDependencies(ScriptReportBean bean, LinkedHashSet<ClientDependency> clientDependencies, LinkedHashSet<String> cssScripts)
        {
            LinkedHashSet<ClientDependency> scriptDependencies = ClientDependency.fromList(bean.getScriptDependencies());

            // add any css dependencies we have
            for (ClientDependency cd : scriptDependencies)
                cssScripts.addAll(cd.getCssPaths(getContainer(), getUser(), AppProps.getInstance().isDevMode()));

            // add these to our client dependencies
            clientDependencies.addAll(scriptDependencies);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetBackgroundReportResultsAction extends ApiAction<ScriptReportBean>
    {
        @Override
        public ApiResponse execute(ScriptReportBean bean, BindException errors) throws Exception
        {
            Report report = bean.getReport(getViewContext());
            File logFile = new File(((RReport)report).getReportDir(), RReportJob.LOG_FILE_NAME);
            PipelineStatusFile statusFile = PipelineService.get().getStatusFile(logFile);

            VBox vbox = new VBox();

            StringBuilder html = new StringBuilder("<table>\n");

            if (null != statusFile)
            {
                html.append("<tr><td class=\"labkey-form-label\">Description</td><td>");
                html.append(PageFlowUtil.filter(statusFile.getDescription()));
                html.append("</td></tr>\n");
                html.append("<tr><td class=\"labkey-form-label\">Status</td><td>");
                html.append(PageFlowUtil.filter(statusFile.getStatus()));
                html.append("</td></tr>\n");
                html.append("<tr><td class=\"labkey-form-label\">Email</td><td>");
                html.append(PageFlowUtil.filter(statusFile.getEmail()));
                html.append("</td></tr>\n");
                html.append("<tr><td class=\"labkey-form-label\">Info</td><td>");
                html.append(PageFlowUtil.filter(StringUtils.defaultString(statusFile.getInfo(), "")));
                html.append("</td></tr>\n");
                html.append("<tr><td colspan=\"2\">&nbsp;</td></tr>\n");
            }
            else
            {
                html.append("<tr><td class=\"labkey-form-label\">Status</td><td>Not Run</td></tr>");
            }

            html.append("<table>\n");
            vbox.addView(new HtmlView(html.toString()));

            if (statusFile != null &&
                    !(PipelineJob.TaskStatus.waiting.matches(statusFile.getStatus()) ||
                      statusFile.getStatus().equals(RReportJob.PROCESSING_STATUS)))
                vbox.addView(new RenderBackgroundRReportView((RReport)report));

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("results", HttpView.renderToString(vbox, getViewContext().getRequest()));

            if (null != statusFile)
                response.put("status", statusFile.getStatus());

            return response;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class RunReportAction extends SimpleViewAction<ReportDesignBean>
    {
        Report _report;

        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            _report = null;

            if (null != form.getReportId())
                _report = form.getReportId().getReport(getViewContext());

            if (null == _report)
                return new HtmlView("<span class=\"labkey-error\">Invalid report identifier, unable to create report.</span>");

            HttpView ret = null;
            try
            {
                ret = _report.getRunReportView(getViewContext());
            }
            catch (RuntimeException e)
            {
                return new HtmlView("<span class=\"labkey-error\">" + e.getMessage() + ". Unable to create report.</span>");
            }

            if (!isPrint() && !(ret instanceof HttpRedirectView))
            {
                VBox box = new VBox(ret);
                DiscussionService.Service service = DiscussionService.get();
                String title = "Discuss report - " + _report.getDescriptor().getReportName();
                box.addView(service.getDisussionArea(getViewContext(), _report.getEntityId(), new ActionURL(CreateScriptReportAction.class, getContainer()), title, true, false));
                ret = box;
            }
            return ret;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_report != null)
                return root.addChild(_report.getDescriptor().getReportName());
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DetailsAction extends SimpleViewAction<ReportDesignBean>
    {
        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            Report report = form.getReport(getViewContext());
            if (null != report)
            {
                VBox box = new VBox(new JspView<>("/org/labkey/query/reports/view/reportDetails.jsp", form));

                DiscussionService.Service service = DiscussionService.get();
                String title = "Discuss report - " + report.getDescriptor().getReportName();
                box.addView(service.getDisussionArea(getViewContext(), report.getEntityId(), new ActionURL(CreateScriptReportAction.class, getContainer()), title, true, false));

                return box;
            }
            else
                return new HtmlView("Specified report not found");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Report Details");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ReportInfoAction extends SimpleViewAction<ReportDesignBean>
    {
        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            return new ReportInfoView(form.getReport(getViewContext()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Report Debug Information");
        }
    }


    public static class ReportInfoView extends HttpView
    {
        private Report _report;

        public ReportInfoView(Report report)
        {
            _report = report;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (_report != null)
            {
                out.write("<table>");
                addRow(out, "Name", PageFlowUtil.filter(_report.getDescriptor().getReportName()));

                User user = UserManager.getUser(_report.getDescriptor().getCreatedBy());
                if (user != null)
                    addRow(out, "Created By", PageFlowUtil.filter(user.getDisplayName(getViewContext().getUser())));

                addRow(out, "Key", PageFlowUtil.filter(_report.getDescriptor().getReportKey()));
                for (Map.Entry<String, Object> prop : _report.getDescriptor().getProperties().entrySet())
                {
                    addRow(out, PageFlowUtil.filter(prop.getKey()), PageFlowUtil.filter(Objects.toString(prop.getValue(), "")));
                }
                out.write("<table>");
            }
            else
                out.write("Report not found");
        }

        private void addRow(PrintWriter out, String key, String value)
        {
            out.write("<tr><td>");
            out.write(key);
            out.write("</td><td>");
            out.write(value);
            out.write("</td></tr>");
        }
    }


    protected void validatePermissions(ViewContext context, Report report, List<ValidationError> errors)
    {
        if (report != null)
        {
            if (report.getDescriptor().isNew())
            {
                if (!ReportUtil.canCreateScript(context))
                    errors.add(new SimpleValidationError("Only members of the Site Admin and Site Developers groups are allowed to create script views."));
            }
            else
                report.canEdit(context.getUser(), context.getContainer(), errors);
        }
        else
            errors.add(new SimpleValidationError("Unable to locate the report, it may have been deleted."));
    }


    @RequiresNoPermission
    public class AjaxSaveScriptReportAction extends MutatingApiAction<RReportBean>
    {
        @Override
        public void validateForm(RReportBean form, Errors errors)
        {
            try
            {
                ReportIdentifier id = form.getReportId();
                Report report;

                if (id != null)
                    report = id.getReport(getViewContext());
                else
                    report = form.getReport(getViewContext());

                List<ValidationError> reportErrors = new ArrayList<>();
                validatePermissions(getViewContext(), report, reportErrors);

                if (!reportErrors.isEmpty())
                {
                    for (ValidationError error : reportErrors)
                    {
                        String message = error.getMessage() != null ? error.getMessage() : "A validation error occurred";
                        errors.reject(ERROR_MSG, message);
                    }
                }
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
        }

        @Override
        public ApiResponse execute(RReportBean form, BindException errors) throws Exception
        {
            Report report = null;

            try
            {
                if (getUser().isGuest())
                {
                    errors.reject("saveScriptReport", "You must be logged in to be able to save reports");
                    return null;
                }

                report = form.getReport(getViewContext());

                if (null == report)
                {
                    errors.reject("saveScriptReport", "Report not found.");
                }
                // on new reports, check for duplicates
                else if (null == report.getDescriptor().getReportId())
                {
                    if (reportNameExists(report.getDescriptor().getReportName(), ReportUtil.getReportQueryKey(report.getDescriptor())))
                    {
                        errors.reject("saveScriptReport", "There is already a report with the name of: '" + report.getDescriptor().getReportName() +
                                "'. Please specify a different name.");
                    }
                }
            }
            catch (Exception e)
            {
                errors.reject("saveScriptReport", e.getMessage());
            }

            if (errors.hasErrors())
                return null;

            ApiSimpleResponse response = new ApiSimpleResponse();

            int newId = ReportService.get().saveReport(getViewContext(), ReportUtil.getReportQueryKey(report.getDescriptor()), report);
            report = ReportService.get().getReport(newId);  // Re-select saved report so we get EntityId, etc.

            if (report instanceof DynamicThumbnailProvider)
            {
                ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

                if (null != svc)
                {
                    DynamicThumbnailProvider provider = (DynamicThumbnailProvider) report;

                    if (form.getThumbnailType().equals(DataViewProvider.EditInfo.ThumbnailType.NONE.name()))
                    {
                        // User checked the "no thumbnail" radio... need to proactively delete the thumbnail
                        svc.deleteThumbnail(provider, ImageType.Large);
                        ReportPropsManager.get().setPropertyValue(report.getEntityId(), getContainer(), "thumbnailType", DataViewProvider.EditInfo.ThumbnailType.NONE.name());
                    }
                    else if (form.getThumbnailType().equals(DataViewProvider.EditInfo.ThumbnailType.AUTO.name()))
                    {
                        svc.replaceThumbnail(provider, ImageType.Large, getViewContext());
                        ReportPropsManager.get().setPropertyValue(report.getEntityId(), getContainer(), "thumbnailType", DataViewProvider.EditInfo.ThumbnailType.AUTO.name());
                    }
                }
            }

            response.put("success", true);
            response.put("redirect", form.getRedirectUrl());

            return response;
        }

        // TODO: Use shared method instead?
        private boolean reportNameExists(String reportName, String key)
        {
            try
            {
                ViewContext context = getViewContext();

                for (Report report : ReportService.get().getReports(context.getUser(), context.getContainer(), key))
                {
                    if (StringUtils.equals(reportName, report.getDescriptor().getReportName()))
                        return true;
                }

                return false;
            }
            catch (Exception e)
            {
                return false;
            }
        }
    }


    /**
     * Ajax action to start a pipeline-based R view.
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class StartBackgroundRReportAction extends ApiAction<RReportBean>
    {
        public ApiResponse execute(RReportBean form, BindException errors) throws Exception
        {
            final ViewContext context = getViewContext();
            final Container c = getContainer();
            final ViewBackgroundInfo info = new ViewBackgroundInfo(c, context.getUser(), context.getActionURL());
            ApiSimpleResponse response = new ApiSimpleResponse();

            Report report;
            PipelineJob job;
            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());

            if (null == form.getReportId())
            {
                report = form.getReport(getViewContext());
                job = new RReportJob(ReportsPipelineProvider.NAME, info, form, root);
            }
            else
            {
                report = form.getReportId().getReport(getViewContext());
                job = new RReportJob(ReportsPipelineProvider.NAME, info, form.getReportId(), root);
            }

            if (report instanceof RReport)
            {
                ((RReport)report).deleteReportDir();
                ((RReport)report).createInputDataFile(getViewContext());
                PipelineService.get().queueJob(job);
                response.put("success", true);
            }

            return response;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadInputDataAction extends SimpleViewAction<RReportBean>
    {
        public ModelAndView getView(RReportBean form, BindException errors) throws Exception
        {
            Report report = form.getReport(getViewContext());
            if (report instanceof RReport)
            {
                try
                {
                    File file = ((RReport)report).createInputDataFile(getViewContext());
                    if (file.exists())
                    {
                        PageFlowUtil.streamFile(getViewContext().getResponse(), file, true);
                    }
                }
                catch (SQLException e)
                {
                    _log.error("failed trying to download input RReport data", e);
                }
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class StreamFileAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String sessionKey = (String) getViewContext().get("sessionKey");
            String deleteFile = (String) getViewContext().get("deleteFile");
            String attachment = (String) getViewContext().get("attachment");
            String cacheFile = (String) getViewContext().get("cacheFile");
            if (sessionKey != null)
            {
                File file = (File) getViewContext().getRequest().getSession().getAttribute(sessionKey);
                if (file != null && file.exists())
                {
                    Map<String, String> responseHeaders = Collections.emptyMap();
                    if (BooleanUtils.toBoolean(cacheFile))
                    {
                        responseHeaders = new HashMap<>();

                        responseHeaders.put("Pragma", "private");
                        responseHeaders.put("Cache-Control", "private");
                        responseHeaders.put("Cache-Control", "max-age=3600");
                    }
                    PageFlowUtil.streamFile(getViewContext().getResponse(), responseHeaders, file, BooleanUtils.toBoolean(attachment));
                    if (BooleanUtils.toBoolean(deleteFile))
                        file.delete();
                    return null;
                }
            }
            return new HtmlView("Requested Resource not found");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadAction extends SimpleViewAction<AttachmentForm>
    {
        public ModelAndView getView(AttachmentForm form, BindException errors) throws Exception
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ContainerId"), getContainer().getId());
            filter.addCondition(FieldKey.fromParts("EntityId"), form.getEntityId());

            Report[] report = ReportService.get().getReports(filter);
            if (report.length != 1)
            {
                throw new NotFoundException("Unable to find report");
            }

            if (report[0] instanceof RReport || report[0] instanceof AttachmentReport)
                AttachmentService.get().download(getViewContext().getResponse(), report[0], form.getName());

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class AttachmentReportForm extends DataViewEditForm
    {
        public enum AttachmentReportType { local, server }

        private AttachmentReportType attachmentType;
        private String filePath; // only valid for AttachmentReportType.server
        private String uploadFileName; // only valid for AttachmentReportType.local

        public AttachmentReportType getAttachmentType()
        {
            return attachmentType;
        }

        public void setAttachmentType(AttachmentReportType attachmentType)
        {
            this.attachmentType = attachmentType;
        }

        public String getFilePath()
        {
            return filePath;
        }

        public void setFilePath(String filePath)
        {
            this.filePath = filePath;
        }

        public String getUploadFileName()
        {
            return uploadFileName;
        }

        public void setUploadFileName(String fileName)
        {
            this.uploadFileName = fileName;
        }
    }

    public static ActionURL getCreateAttachmentReportURL(Container c, ActionURL returnURL)
    {
        ActionURL url = new ActionURL(CreateAttachmentReportAction.class, c);
        url.addReturnURL(returnURL);
        return url;
    }

    public static ActionURL getCreateLinkReportURL(Container c, ActionURL returnURL)
    {
        ActionURL url = new ActionURL(CreateLinkReportAction.class, c);
        url.addReturnURL(returnURL);
        return url;
    }

    public static ActionURL getCreateQueryReportURL(Container c, ActionURL returnURL)
    {
        ActionURL url = new ActionURL(CreateQueryReportAction.class, c);
        url.addReturnURL(returnURL);
        return url;
    }

    protected abstract class BaseReportAction<F extends DataViewEditForm, R extends AbstractReport & DynamicThumbnailProvider> extends FormViewAction<F>
    {
        protected void initialize(F form) throws Exception
        {
            setHelpTopic(new HelpTopic("staticReports"));

            // we can share if we own the report.  if we don't
            // own the report then we'll disable the checkbox
            form.setCanChangeSharing(true);

            if (form.getReportId() != null)
            {
                R report = (R)form.getReportId().getReport(getViewContext());

                if (report != null)
                {
                    initializeForm(form, report);
                }
            }
        }

        protected void initializeForm(F form, R report) throws Exception
        {
            form.setViewName(report.getDescriptor().getReportName());
            form.setReportId(form.getReportId());
            form.setDescription(report.getDescriptor().getReportDescription());
            form.setShared(report.getDescriptor().isShared());

            if (null != report.getDescriptor().getCategory())
                form.setViewCategory(report.getDescriptor().getCategory());

            Integer authorId = report.getDescriptor().getAuthor();
            if (null != authorId)
                form.setAuthor(authorId);

            String status = report.getDescriptor().getStatus();
            form.setStatus(null != status ? ViewInfo.Status.valueOf(status) : ViewInfo.Status.None);
            form.setRefreshDate(report.getDescriptor().getRefreshDate());

            ReportService.get().validateReportPermissions(getViewContext(), report);

            //
            // see if this user can make a public report private
            // if not, then don't enable the sharing checkbox
            //
//            if (null == report.getDescriptor().getOwner())
//            {
//                List<ValidationError> errors = new ArrayList<>();
//                report.getDescriptor().setOwner(getUser().getUserId());
//                form.setCanChangeSharing(ReportService.get().tryValidateReportPermissions(getViewContext(), report, errors));
//                report.getDescriptor().setOwner(null);
//            }

            form.setCanChangeSharing(report.canShare(getUser(), getContainer()));
        }


        public void validateCommand(F form, Errors errors)
        {
            if (null == StringUtils.trimToNull(form.getViewName()))
                errors.reject("viewName", "You must enter a report name.");

/*
            String dateStr = form.getReportDateString();
            if (dateStr != null && dateStr.length() > 0)
            {
                try
                {
                    Long l = DateUtil.parseDateTime(dateStr);
                    Date reportDate = new Date(l);
                }
                catch (ConversionException x)
                {
                    errors.reject("uploadForm", "You must enter a legal report date");
                }
            }
*/
        }

        protected String getReportKey(R report, F form)
        {
            return form.getViewName();
        }

        public boolean saveReport(F form, BindException errors) throws Exception
        {
            DbScope scope = CoreSchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction tx = scope.ensureTransaction())
            {
                // save the category information then the report
                ViewCategory category = form.getViewCategory();

                R report = initializeReportForSave(form);
                ReportDescriptor descriptor = report.getDescriptor();

                descriptor.setContainer(getContainer().getId());
                descriptor.setReportName(form.getViewName());
                descriptor.setModified(form.getModifiedDate());

                descriptor.setReportDescription(form.getDescription());
                descriptor.setCategory(category);

                // Note: Keep this code in sync with ReportViewProvider.updateProperties()
                boolean isPrivate = !form.getShared();

                if (isPrivate)
                {
                    // If switching from shared to private then set owner back to original creator.
                    if (descriptor.isShared())
                    {
                        // Convey previous state to save code, otherwise admins will be denied the ability to unshare.
                        descriptor.setWasShared();
                        descriptor.setOwner(descriptor.getCreatedBy());
                    }
                }
                else
                {
                    descriptor.setOwner(null);
                }

                User author = UserManager.getUser(form.getAuthor());
                ViewInfo.Status status = form.getStatus();
                Date refreshDate = form.getRefreshDate();

                if (author != null)
                    descriptor.setAuthor(author.getUserId());
                if (status != null)
                    descriptor.setStatus(status.name());
                if (refreshDate != null)
                    descriptor.setRefreshDate(refreshDate);

                int id = ReportService.get().saveReport(getViewContext(), getReportKey(report, form), report);

                report = (R)ReportService.get().getReport(id);

                afterReportSave(form, report);

                tx.commit();

                ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

                if (null != svc)
                    svc.queueThumbnailRendering(report, ImageType.Large);

                return true;
            }
        }


        abstract protected R initializeReportForSave(F form) throws Exception;

        protected void afterReportSave(F form, R report) throws Exception
        {
        }

        public ActionURL getSuccessURL(F uploadForm)
        {
            ActionURL defaultURL = null;
            ReportIdentifier id = uploadForm.getReportId();

            if (null != id)
            {
                Report r = id.getReport(getViewContext());
                defaultURL = new ReportUrlsImpl().urlReportDetails(getContainer(), r);
            }

            return uploadForm.getReturnActionURL(defaultURL);
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public abstract class BaseAttachmentReportAction extends BaseReportAction<AttachmentReportForm, AttachmentReport>
    {
        @Override
        public ModelAndView getView(AttachmentReportForm form, boolean reshow, BindException errors) throws Exception
        {
            initialize(form);
            return new JspView<>("/org/labkey/query/reports/view/attachmentReport.jsp", form, errors);
        }

        @Override
        public void validateCommand(AttachmentReportForm form, Errors errors)
        {
            super.validateCommand(form, errors);
            if (errors.hasErrors())
                return;

            Map<String, MultipartFile> fileMap = getFileMap();
            MultipartFile[] formFiles = fileMap.values().toArray(new MultipartFile[fileMap.size()]);

            if (form.getAttachmentType() == AttachmentReportForm.AttachmentReportType.server)
            {
                String filePath = StringUtils.trimToNull(form.getFilePath());

                // Only site administrators can specify a path, #14445
                if (null != filePath)
                {
                    if (!getUser().isSiteAdmin())
                        throw new UnauthorizedException();
                }

            }
            else if (form.getAttachmentType() == AttachmentReportForm.AttachmentReportType.local)
            {
                // Require a file if we are creating a new report or if the report we are updating
                // does not already have an attached file.  This could occur if the user updated
                // the AttachmentReportType from server to local
                boolean requireFile = true;

                if (form.isUpdate())
                {
                    // if the form we are updating already has an attachment then it is okay not to submit one
                    AttachmentReport report = (AttachmentReport) form.getReportId().getReport(getViewContext());
                    requireFile = (null == report.getLatestVersion());
                }

                if (requireFile)
                {
                    if (0 == formFiles.length || formFiles[0].isEmpty())
                    {
                        errors.reject("filePath", "You must specify a file");
                    }
                    else
                    {
                        String filename = formFiles[0].getOriginalFilename();
                        for (String reserved : ThumbnailService.ImageFilenames)
                        {
                            if (reserved.equalsIgnoreCase(filename))
                            {
                                errors.reject("filePath", "You may not specify a file named Thumbnail or SmallThumbnail");
                                break;
                            }
                        }
                    }
                }
            }
            else
            {
                errors.reject(ERROR_MSG, "Unknown attachment report type");
                return;
            }
        }

        @Override
        public boolean handlePost(AttachmentReportForm form, BindException errors) throws Exception
        {
            return saveReport(form, errors);
        }

        @Override
        protected AttachmentReport initializeReportForSave(AttachmentReportForm form)
        {
            AttachmentReport report = (AttachmentReport) (form.isUpdate() ? form.getReportId().getReport(getViewContext()) : ReportService.get().createReportInstance(AttachmentReport.TYPE));

            if (getUser().isSiteAdmin())
            {
                // only an admin can create or update an attachment report with a server path
                if (form.getAttachmentType() == AttachmentReportForm.AttachmentReportType.server)
                {
                    report.setFilePath(form.getFilePath());
                }
                else
                {
                    // an admin may edit an attachment report and change its type from server to local
                    // for this case be sure to remove the file path before save
                    report.setFilePath(null);
                }
            }

            return report;
        }

    }

    @RequiresPermissionClass(InsertPermission.class)
    public class CreateAttachmentReportAction extends BaseAttachmentReportAction
    {
        @Override
        protected void afterReportSave(AttachmentReportForm form, AttachmentReport report) throws Exception
        {
            if (form.getAttachmentType() == AttachmentReportForm.AttachmentReportType.local)
            {
                List<AttachmentFile> attachments = getAttachmentFileList();
                if (attachments != null && attachments.size() > 0)
                {
                    AttachmentService.get().addAttachments(report, attachments, getUser());
                }
            }

            super.afterReportSave(form, report);
        }


        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Attachment Report");
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class UpdateAttachmentReportAction extends BaseAttachmentReportAction
    {
        @Override
        protected void initializeForm(AttachmentReportForm form, AttachmentReport report) throws Exception
        {
            super.initializeForm(form, report);
            String filePath = report.getFilePath();

            if (StringUtils.isNotEmpty(filePath))
            {
                form.setAttachmentType(AttachmentReportForm.AttachmentReportType.server);
                form.setFilePath(filePath);
            }
            else
            {
                form.setAttachmentType(AttachmentReportForm.AttachmentReportType.local);
                Attachment latest = report.getLatestVersion();
                if (latest != null)
                {
                    form.setUploadFileName(latest.getName());
                }
                else
                {
                    // a report must have an attachment or server link somewhere
                    throw new IllegalStateException();
                }
            }
        }

        @Override
        protected void afterReportSave(AttachmentReportForm form, AttachmentReport report) throws Exception
        {
            //
            // We need to deal with attachments because the following cases could occur on update
            // 1) local -> server, remove existing attachments [admin only]
            // 2) local -> local, remove existing attachments, add new ones
            // 3) server -> local, no existing attachments, add new ones [admin only]
            // 4) server -> server, no existing attachments, no new ones [admin only]
            //
            if (form.getAttachmentType() == AttachmentReportForm.AttachmentReportType.local)
            {
                List<AttachmentFile> attachments = getAttachmentFileList();

                //
                // if the user has provided an attachment, then remove previous and add new
                // otherwise, keep the existing attachment.  There is no way to "clear" an attchment.  An
                // attachment report must either specify a local or server attachment.
                //
                if (attachments != null && attachments.size() > 0)
                {
                    // be sure to remove any existing local attachments
                    AttachmentService.get().deleteAttachments(report);
                    AttachmentService.get().addAttachments(report, attachments, getUser());
                }
            }
            else
            {
                //
                // updated attachment type is server so be sure to discard any attachments in case this
                // attachment type was local previously
                //
                AttachmentService.get().deleteAttachments(report);
            }

            super.afterReportSave(form, report);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Update Attachment Report");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadReportFileAction extends SimpleViewAction<AttachmentReportForm>
    {
        public ModelAndView getView(AttachmentReportForm form, BindException errors) throws Exception
        {
            ReportIdentifier reportId = form.getReportId();

            if (null == reportId)
                throw new NotFoundException("ReportId not specified");

            Report report = reportId.getReport(getViewContext());

            if (null == report)
                throw new NotFoundException("Report not found");

            if (report instanceof AttachmentReport)
            {
                AttachmentReport aReport = (AttachmentReport)report;

                if (null == aReport.getFilePath())
                    throw new NotFoundException("Report is not a server file attachment report");

                File file = new File(aReport.getFilePath());
                if (!file.exists())
                    throw new NotFoundException("Could not find file with name " + aReport.getFilePath());

                boolean isInlineImage = _mimeMap.isInlineImageFor(aReport.getFilePath());
                boolean asAttachment = !isInlineImage;

                PageFlowUtil.streamFile(getViewContext().getResponse(), file, asAttachment);
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class LinkReportForm extends DataViewEditForm
    {
        private String linkUrl;
        private boolean targetNewWindow = true;

        public String getLinkUrl()
        {
            return linkUrl;
        }

        public void setLinkUrl(String linkUrl)
        {
            this.linkUrl = linkUrl;
        }

        public boolean isTargetNewWindow()
        {
            return targetNewWindow;
        }

        public void setTargetNewWindow(boolean b)
        {
            this.targetNewWindow = b;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public abstract class BaseLinkReportAction extends BaseReportAction<LinkReportForm, LinkReport>
    {
        public ModelAndView getView(LinkReportForm form, boolean reshow, BindException errors) throws Exception
        {
            initialize(form);
            return new JspView<>("/org/labkey/query/reports/view/linkReport.jsp", form, errors);
        }

        @Override
        public void validateCommand(LinkReportForm form, Errors errors)
        {
            super.validateCommand(form, errors);

            String linkUrl = StringUtils.trimToNull(form.getLinkUrl());
            if (null == linkUrl)
            {
                errors.reject("linkUrl", "You must specify a link URL");
            }
            else
            {
                if (linkUrl.startsWith("/") || linkUrl.startsWith("http://") || linkUrl.startsWith("https://"))
                {
                    try
                    {
                        URLHelper url = new URLHelper(linkUrl);
                    }
                    catch (URISyntaxException e)
                    {
                        errors.reject("linkUrl", "You must specify a valid link URL: " + e.getMessage());
                    }
                }
                else
                {
                    errors.reject("linkUrl", "Link URL must be either absolute (starting with http or https) or relative to this server (start with '/')");
                }
            }
        }

        @Override
        public boolean handlePost(LinkReportForm form, BindException errors) throws Exception
        {
            return saveReport(form, errors);
        }

        @Override
        protected LinkReport initializeReportForSave(LinkReportForm form) throws Exception
        {
            LinkReport report = (LinkReport) (form.isUpdate() ? form.getReportId().getReport(getViewContext()) : ReportService.get().createReportInstance(LinkReport.TYPE));

            if (null == report)
                throw new NotFoundException("Report does not exist");

            report.setRunReportTarget(form.isTargetNewWindow() ? "_blank" : null);

            try
            {
                URLHelper url = new URLHelper(form.getLinkUrl());
                report.setUrl(url);
            }
            catch (URISyntaxException e)
            {
                // Shouldn't happen -- we've already checked the URL is not malformed in validateCommand.
                throw new IllegalArgumentException(e.getMessage());
            }

            return report;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class CreateLinkReportAction extends BaseLinkReportAction
    {
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Link Report");
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class UpdateLinkReportAction extends BaseLinkReportAction
    {
        @Override
        protected void initializeForm(LinkReportForm form, LinkReport report) throws Exception
        {
            super.initializeForm(form, report);

            form.setLinkUrl(report.getURL().toString());
            form.setTargetNewWindow(null != report.getRunReportTarget());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Update Link Report");
        }
    }

    public static class QueryReportForm extends DataViewEditForm
    {
        private String selectedSchemaName;
        private String selectedQueryName;
        private String selectedViewName;
        private ActionURL srcURL;

        public String getSelectedSchemaName()
        {
            return selectedSchemaName;
        }

        public void setSelectedSchemaName(String selectedSchemaName)
        {
            this.selectedSchemaName = selectedSchemaName;
        }

        public String getSelectedQueryName()
        {
            return selectedQueryName;
        }

        public void setSelectedQueryName(String selectedQueryName)
        {
            this.selectedQueryName = selectedQueryName;
        }

        public String getSelectedViewName()
        {
            return selectedViewName;
        }

        public void setSelectedViewName(String selectedViewName)
        {
            this.selectedViewName = selectedViewName;
        }

        public ActionURL getSrcURL()
        {
            return srcURL;
        }

        public void setSrcURL(ActionURL srcURL)
        {
            this.srcURL = srcURL;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class CreateQueryReportAction extends BaseReportAction<QueryReportForm, QueryReport>
    {
        public ModelAndView getView(QueryReportForm form, boolean reshow, BindException errors) throws Exception
        {
            initialize(form);
            return new JspView<>("/org/labkey/query/reports/view/createQueryReport.jsp", form, errors);
        }

        @Override
        public void initialize(QueryReportForm form) throws Exception
        {
            form.setSrcURL(getViewContext().getActionURL());
            super.initialize(form);
        }

        @Override
        public void validateCommand(QueryReportForm form, Errors errors)
        {
            super.validateCommand(form, errors);

            String schemaName = StringUtils.trimToNull(form.getSelectedSchemaName());
            String queryName = StringUtils.trimToNull(form.getSelectedQueryName());

            if (null == schemaName)
            {
                errors.reject("selectedSchemaName", "You must specify a schema");
            }

            if (null == queryName)
            {
                errors.reject("selectedQueryName", "You must specify a query");
            }
        }

        @Override
        public boolean handlePost(QueryReportForm form, BindException errors) throws Exception
        {
            return saveReport(form, errors);
        }

        @Override
        protected QueryReport initializeReportForSave(QueryReportForm form)
        {
            QueryReport report = (QueryReport)ReportService.get().createReportInstance(QueryReport.TYPE);

            report.setSchemaName(form.getSelectedSchemaName());
            report.setQueryName(form.getSelectedQueryName());
            report.setViewName(form.getSelectedViewName());

            return report;
        }

        @Override
        protected String getReportKey(QueryReport report, QueryReportForm form)
        {
            return ReportUtil.getReportQueryKey(report.getDescriptor());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Query Report");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ManageViewsAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            WebPartFactory factory = Portal.getPortalPart(DataViewsWebPartFactory.NAME);
            if (factory != null)
            {
                Portal.WebPart part = factory.createWebPart();
                part.getPropertyMap().put("manageView", "true");

                WebPartView view = factory.getWebPartView(getViewContext(), part);

                setTitle("Manage Views");
                setHelpTopic(new HelpTopic("manageViews"));
                view.setTitle("Manage Views");
                view.setIsWebPart(false);

                return view;
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    private static QueryForm getQueryForm(ViewContext context, String viewId)
    {
        Map<String, String> map = PageFlowUtil.mapFromQueryString(viewId);
        QueryForm form = new QueryForm();

        form.setSchemaName(map.get(QueryParam.schemaName.name()));
        form.setQueryName(map.get(QueryParam.queryName.name()));
        form.setViewName(map.get(QueryParam.viewName.name()));
        form.setViewContext(context);

        return form;
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class RenameReportAction extends FormViewAction<ReportDesignBean>
    {
        private String _newReportName;
        private Report _report;

        public void validateCommand(ReportDesignBean form, Errors errors)
        {
            ReportIdentifier reportId =  form.getReportId();
            _newReportName =  form.getReportName();

            if (!StringUtils.isEmpty(_newReportName))
            {
                try
                {
                    if (null != reportId)
                        _report = reportId.getReport(getViewContext());

                    if (_report != null)
                    {
                        if (!_report.canEdit(getUser(), getContainer()))
                        {
                            errors.reject("renameReportAction", "Unauthorized operation");
                            return;
                        }

                        if (reportNameExists(getViewContext(), _newReportName, _report.getDescriptor().getReportKey()))
                            errors.reject("renameReportAction", "There is already a view with the name of: " + _newReportName +
                                    ". Please specify a different name.");
                    }
                    else
                        errors.reject("renameReportAction", "Unable to find the specified report");
                }
                catch (Exception e)
                {
                    errors.reject("renameReportAction", "An error occurred trying to rename the specified report");
                }
            }
            else
                errors.reject("renameReportAction", "The view name cannot be blank");
        }

        public ModelAndView getView(ReportDesignBean form, boolean reshow, BindException errors) throws Exception
        {
            ManageViewsAction action = new ManageViewsAction();
            action.setViewContext(getViewContext());
            action.setPageConfig(getPageConfig());
            return action.getView(null, errors);
        }

        public boolean handlePost(ReportDesignBean form, BindException errors) throws Exception
        {
            _report.getDescriptor().setReportName(_newReportName);
            ReportService.get().saveReport(getViewContext(), _report.getDescriptor().getReportKey(), _report);

            return true;
        }

        public ActionURL getSuccessURL(ReportDesignBean form)
        {
            return new ActionURL(ManageViewsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    private boolean reportNameExists(ViewContext context, String reportName, String key)
    {
        try
        {
            for (Report report : ReportService.get().getReports(context.getUser(), context.getContainer(), key))
            {
                if (StringUtils.equals(reportName, report.getDescriptor().getReportName()))
                    return true;
            }
            return false;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ReportSectionsAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            ReportIdentifier reportId = ReportService.get().getReportIdentifier((String)getViewContext().get(ReportDescriptor.Prop.reportId.name()));
            String sections = (String)getViewContext().get(Report.renderParam.showSection.name());
            if (reportId != null)
            {
                Report report = reportId.getReport(getViewContext());

                // may need a better way to determine sections, do we want to add to the interface?
                response.put("success", true);

                if (report instanceof RReport)
                {
                    List<String> sectionNames = Collections.emptyList();

                    if (sections != null)
                    {
                        sections = PageFlowUtil.decode(sections);
                        sectionNames = Arrays.asList(sections.split("&"));
                    }

                    String script = report.getDescriptor().getProperty(ScriptReportDescriptor.Prop.script);
                    StringBuilder sb = new StringBuilder();

                    for (ParamReplacement param : ParamReplacementSvc.get().getParamReplacements(script))
                    {
                        sb.append("<option value=\"");
                        sb.append(PageFlowUtil.filter(param.getName()));

                        if (sectionNames.contains(param.getName()))
                            sb.append("\" selected>");
                        else
                            sb.append("\">");

                        sb.append(PageFlowUtil.filter(param.toString()));
                        sb.append("</option>");
                    }

                    if (sb.length() > 0)
                        response.put("sectionNames", sb.toString());
                }
            }
            return response;
        }
    }

    protected static class PlotView extends WebPartView
    {
        private Report _report;
        public PlotView(Report report)
        {
            setFrame(FrameType.NONE);
            _report = report;
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            if (_report instanceof ChartReport)
            {
                ActionURL url = getViewContext().cloneActionURL();
                url.setAction("plotChart");
                url.addParameter("reportId", _report.getDescriptor().getReportId().toString());

                out.write("<img src='" + url + "'>");
            }
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class CrosstabExportAction extends SimpleViewAction<ReportDesignBean>
    {
        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            ReportIdentifier reportId = form.getReportId();
            if (reportId != null)
            {
                Report report = reportId.getReport(getViewContext());
                if (report instanceof CrosstabReport)
                {
                    ExcelWriter writer = ((CrosstabReport)report).getExcelWriter(getViewContext());
                    writer.write(getViewContext().getResponse());
                }
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ThumbnailAction extends BaseThumbnailAction<ThumbnailForm>
    {
        @Override
        public StaticThumbnailProvider getProvider(ThumbnailForm form) throws Exception
        {
            return form.getReportId().getReport(getViewContext());
        }

        @Override
        protected ImageType getImageType(ThumbnailForm form)
        {
            if ("Small".equals(form.getImageType()))
                return ImageType.Small;
            else
                return ImageType.Large;
        }
    }


    public static class ThumbnailForm
    {
        private ReportIdentifier _reportId;
        private String _imageType;

        public ReportIdentifier getReportId()
        {
            return _reportId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setReportId(ReportIdentifier reportId)
        {
            _reportId = reportId;
        }

        public String getImageType()
        {
            return _imageType;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setImageType(String imageType)
        {
            _imageType = imageType;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @Action(ActionType.SelectData)
    public class RenderQueryReport extends SimpleViewAction<ReportDesignBean>
    {
        String _reportName;

        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            ReportIdentifier id = form.getReportId();

            if (id != null)
            {
                Report report = id.getReport(getViewContext());
                if (report != null)
                {
                    _reportName = report.getDescriptor().getReportName();

                    VBox view = new VBox(new JspView<>("/org/labkey/api/reports/report/view/renderQueryReport.jsp", report));

                    if (!isPrint())
                    {
                        DiscussionService.Service service = DiscussionService.get();
                        String title = "Discuss report - " + _reportName;
                        view.addView(service.getDisussionArea(getViewContext(), report.getEntityId(), new ActionURL(CreateScriptReportAction.class, getContainer()), title, true, false));
                    }
                    return view;
                }
            }
            return new HtmlView("<span class=\"labkey-error\">Invalid report identifier, unable to render report.</span>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_reportName != null)
                return root.addChild(_reportName);
            return null;
        }
    }

    public static class BrowseDataForm extends ReturnUrlForm implements CustomApiForm
    {
        private int index;
        private String pageId;
        private boolean includeData = true;
        private boolean includeMetadata = true;
        private boolean manageView;
        private int _parent = -2;
        Map<String, Object> _props;

        private ViewInfo.DataType[] _dataTypes = new ViewInfo.DataType[]{ViewInfo.DataType.reports, ViewInfo.DataType.datasets, ViewInfo.DataType.queries};

        public ViewInfo.DataType[] getDataTypes()
        {
            return _dataTypes;
        }

        public void setDataTypes(ViewInfo.DataType[] dataTypes)
        {
            _dataTypes = dataTypes;
        }

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

        public boolean includeData()
        {
            return includeData;
        }

        public void setIncludeData(boolean includedata)
        {
            includeData = includedata;
        }

        public boolean includeMetadata()
        {
            return includeMetadata;
        }

        public void setIncludeMetadata(boolean includeMetadata)
        {
            this.includeMetadata = includeMetadata;
        }

        public void setParent(int parent)
        {
            _parent = parent;
        }

        public int getParent()
        {
            return _parent;
        }

        public boolean isManageView()
        {
            return manageView;
        }

        public void setManageView(boolean manageView)
        {
            this.manageView = manageView;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            _props = props;
        }

        public Map<String, Object> getProps()
        {
            return _props;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BrowseDataAction extends ApiAction<BrowseDataForm>
    {
        public ApiResponse execute(BrowseDataForm form, BindException errors) throws Exception
        {
            Map<String, Map<String, Object>> types = new TreeMap<>();
            ApiSimpleResponse response = new ApiSimpleResponse();

            Portal.WebPart webPart = Portal.getPart(getContainer(), form.getPageId(), form.getIndex());

            Map<String, String> props;
            if (webPart != null)
            {
                props = webPart.getPropertyMap();
                Map<String, String> webPartProps = new HashMap<>();
                webPartProps.put("name", webPart.getName());
                if (props.containsKey("webpart.title"))
                    webPartProps.put("title", props.get("webpart.title"));
                if (props.containsKey("webpart.height"))
                    webPartProps.put("height", props.get("webpart.height"));
                else
                    webPartProps.put("height", String.valueOf(700));
                response.put("webpart", new JSONObject(webPartProps));
            }
            else if (form.isManageView())
            {
                props = getAdminConfiguration();
            }
            else
            {
                props = resolveJSONProperties(form.getProps());
            }

            List<DataViewProvider.Type> visibleDataTypes = new ArrayList<>();
            for (DataViewProvider.Type type : DataViewService.get().getDataTypes(getContainer(), getUser()))
            {
                Map<String, Object> info = new HashMap<>();
                boolean visible = getCheckedState(type.getName(), props, type.isShowByDefault());

                info.put("name", type.getName());
                info.put("visible", visible);

                types.put(type.getName(), info);

                if (visible)
                    visibleDataTypes.add(type);
            }

            response.put("types", new JSONArray(types.values()));

            String dateFormat = DateUtil.getDateFormatString(getContainer());

            //The purpose of this flag is so LABKEY.Query.getDataViews() can omit additional information only used to render the
            //webpart.  this also leaves flexibility to change that metadata
            if (form.includeMetadata())
            {
                // visible columns
                Map<String, Map<String, Boolean>> columns = new LinkedHashMap<>();

                columns.put("Type", Collections.singletonMap("checked", getCheckedState("Type", props, false)));
                columns.put("Author", Collections.singletonMap("checked", getCheckedState("Author", props, false)));
                columns.put("Modified", Collections.singletonMap("checked", getCheckedState("Modified", props, false)));
                columns.put("Status", Collections.singletonMap("checked", getCheckedState("Status", props, false)));
                columns.put("Access", Collections.singletonMap("checked", getCheckedState("Access", props, true)));
                columns.put("Details", Collections.singletonMap("checked", getCheckedState("Details", props, true)));
                columns.put("Data Cut Date", Collections.singletonMap("checked", getCheckedState("Data Cut Date", props, false)));

                response.put("visibleColumns", columns);

                // provider editor information
                Map<String, Map<String, Object>> viewTypeProps = new HashMap<>();
                for (DataViewProvider.Type type : visibleDataTypes)
                {
                    DataViewProvider provider = DataViewService.get().getProvider(type, getViewContext());
                    DataViewProvider.EditInfo editInfo = provider.getEditInfo();
                    if (editInfo != null)
                        viewTypeProps.put(type.getName(), DataViewService.get().toJSON(getContainer(), getUser(), editInfo));
                }
                response.put("editInfo", viewTypeProps);
            }

            if (form.includeData())
            {
                int startingDefaultDisplayOrder = 0;
                Set<String> defaultCategories = new TreeSet<>(new Comparator<String>(){
                    @Override
                    public int compare(String s1, String s2)
                    {
                        return s1.compareToIgnoreCase(s2);
                    }
                });

                getViewContext().put("returnUrl", form.getReturnUrl());

                // get the data view information from all visible providers
                List<DataViewInfo> views = new ArrayList<>();

                for (DataViewProvider.Type type : visibleDataTypes)
                {
                    DataViewProvider provider = DataViewService.get().getProvider(type, getViewContext());
                    views.addAll(provider.getViews(getViewContext()));
                }

                for (DataViewInfo info : views)
                {
                    ViewCategory category = info.getCategory();

                    if (category != null)
                    {
                        if (category.getDisplayOrder() != ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER)
                            startingDefaultDisplayOrder = Math.max(startingDefaultDisplayOrder, category.getDisplayOrder());
                        else
                            defaultCategories.add(category.getLabel());
                    }
                }

                // add the default categories after the explicit categories
                Map<String, Integer> defaultCategoryMap = new HashMap<>();
                for (Iterator<String> it = defaultCategories.iterator(); it.hasNext(); )
                {
                    defaultCategoryMap.put(it.next(), ++startingDefaultDisplayOrder);
                }

                for (DataViewInfo info : views)
                {
                    ViewCategory category = info.getCategory();

                    if (category != null)
                    {
                        if (category.getDisplayOrder() == ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER && defaultCategoryMap.containsKey(category.getLabel()))
                            category.setDisplayOrder(defaultCategoryMap.get(category.getLabel()));
                    }
                }
                response.put("data", DataViewService.get().toJSON(getContainer(), getUser(), views));
            }

            return response;
        }

        private Boolean getCheckedState(String prop, Map<String, String> propMap, boolean defaultState)
        {
            if (propMap.containsKey(prop))
            {
                return !propMap.get(prop).equals("0");
            }
            return defaultState;
        }

        private Map<String, String> resolveJSONProperties(Map<String, Object> formProps)
        {
            JSONObject jsonProps = (JSONObject) formProps;
            Map<String, String> props = new HashMap<>();
            boolean explicit = false;

            if (null != jsonProps && jsonProps.size() > 0)
            {
                try
                {
                    // Data Types Filter
                    JSONArray dataTypes = jsonProps.getJSONArray("dataTypes");
                    for (int i=0; i < dataTypes.length(); i++)
                    {
                        props.put((String) dataTypes.get(i), "on");
                        explicit = true;
                    }
                }
                catch (JSONException x)
                {
                    /* No-op */
                }

                if (explicit)
                {
                    for (ViewInfo.DataType t : ViewInfo.DataType.values())
                    {
                        if (!props.containsKey(t.name()))
                        {
                            props.put(t.name(), "0");
                        }
                    }
                }
            }

            return props;
        }
    }

    private Map<String, String> getAdminConfiguration()
    {
        Map<String, String> props = new HashMap<>();

        for (DataViewProvider.Type type : DataViewService.get().getDataTypes(getContainer(), getUser()))
        {
            String typeName = type.getName();

            if (typeName.equalsIgnoreCase("reports") ||
                typeName.equalsIgnoreCase("queries"))
            {
                props.put(typeName, "1");
            }
            else
                props.put(typeName, "0");
        }

        // visible columns
        props.put("Type", "1");
        props.put("Author", "1");
        props.put("Modified", "1");
        props.put("Status", "1");
        props.put("Access", "1");
        props.put("Details", "1");
        props.put("Data Cut Date", "1");

        return props;
    }

    /**
     * This action is currently just an example. This would provide the proper configuration for a tree-based
     * layout of categorized data views. For now only dummy data is generated for rendering.
     * See 'asTree' in DataViewsPanel.js.
     */
    @RequiresPermissionClass(ReadPermission.class)
    @Action(ActionType.SelectMetaData)
    public class BrowseDataTreeAction extends ApiAction<BrowseDataForm>
    {
        @Override
        public ApiResponse execute(BrowseDataForm form, BindException errors) throws Exception
        {
            HttpServletResponse resp = getViewContext().getResponse();
            resp.setContentType("application/json");
            resp.getWriter().write(getTreeData(form).toString());

            return null;
        }

        private JSONObject getTreeData(BrowseDataForm form) throws Exception
        {
            List<DataViewProvider.Type> visibleDataTypes = getVisibleDataTypes(form);

            int startingDefaultDisplayOrder = 0;
            Set<String> defaultCategories = new TreeSet<>(new Comparator<String>(){
                @Override
                public int compare(String s1, String s2)
                {
                    return s1.compareToIgnoreCase(s2);
                }
            });

            if (null != form.getReturnUrl())
                getViewContext().put("returnUrl", form.getReturnUrl());

            // get the data view information from all visible providers
            List<DataViewInfo> views = new ArrayList<>();

            for (DataViewProvider.Type type : visibleDataTypes)
            {
                for (DataViewInfo info : DataViewService.get().getProvider(type, getViewContext()).getViews(getViewContext()))
                {
                    if (!form.isManageView() || !info.isReadOnly())
                        views.add(info);
                }
            }

            for (DataViewInfo info : views)
            {
                ViewCategory category = info.getCategory();

                if (category != null)
                {
                    if (category.getDisplayOrder() != ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER)
                    {
                        startingDefaultDisplayOrder = Math.max(startingDefaultDisplayOrder, category.getDisplayOrder());
                    }
                    else
                        defaultCategories.add(category.getLabel());
                }
            }

            // add the default categories after the explicit categories
            Map<String, Integer> defaultCategoryMap = new HashMap<>();
            for (String cat : defaultCategories)
            {
                defaultCategoryMap.put(cat, ++startingDefaultDisplayOrder);
            }

            for (DataViewInfo info : views)
            {
                ViewCategory category = info.getCategory();

                if (category != null)
                {
                    if (category.getDisplayOrder() == ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER && defaultCategoryMap.containsKey(category.getLabel()))
                        category.setDisplayOrder(defaultCategoryMap.get(category.getLabel()));
                }
            }

            return buildTree(views);
        }

        private JSONObject buildTree(List<DataViewInfo> views)
        {
            Comparator<ViewCategory> t = new Comparator<ViewCategory>()
            {
                @Override
                public int compare(ViewCategory c1, ViewCategory c2)
                {
                    int order = ((Integer) c1.getDisplayOrder()).compareTo(c2.getDisplayOrder());
                    if (order == 0)
                        return c1.getLabel().compareToIgnoreCase(c2.getLabel());
                    else if (c1.getLabel().equalsIgnoreCase("Uncategorized"))
                        return 1;
                    else if (c2.getLabel().equalsIgnoreCase("Uncategorized"))
                        return -1;
                    else if (c1.getDisplayOrder() == 0)
                        return 1;
                    else if (c2.getDisplayOrder() == 0)
                        return -1;
                    return order;
                }
            };

            // Get all categories -- group views by them
            Map<Integer, List<DataViewInfo>> groups = new HashMap<>();
            Map<Integer, ViewCategory> categories = new HashMap<>();
            TreeSet<ViewCategory> order = new TreeSet<>(t);

            ViewCategoryManager vcm = ViewCategoryManager.getInstance();
            for (DataViewInfo view : views)
            {
                ViewCategory og = view.getCategory();

                if (null != og)
                {
                    if (og.getLabel().equalsIgnoreCase("Uncategorized"))
                    {
                        // Because 'Uncategorized' is not a real persisted category
                        int rowId = og.getRowId(); // == 0
                        if (!groups.containsKey(rowId))
                            groups.put(rowId, new ArrayList<DataViewInfo>());

                        groups.get(rowId).add(view);
                        categories.put(rowId, og);
                        order.add(og);
                    }
                    else
                    {
                        ViewCategory vc = vcm.getCategory(og.getRowId()); // ask the real authority

                        if (null != vc)
                        {
                            int rowId = vc.getRowId();
                            ViewCategory parent = vc.getParent();

                            if (!groups.containsKey(rowId))
                                groups.put(rowId, new ArrayList<DataViewInfo>());
                            groups.get(rowId).add(view);
                            categories.put(rowId, vc);

                            // if child is handled before parent
                            if (null != parent)
                            {
                                int pid = parent.getRowId();
                                if (!categories.containsKey(pid))
                                {
                                    categories.put(pid, parent);

                                    if (!groups.containsKey(pid))
                                        groups.put(pid, new ArrayList<DataViewInfo>());

                                    order.add(parent);
                                }
                            }
                            else
                            {
                                order.add(vc);
                            }
                        }
                    }
                }
            }

            // Construct category tree
            Map<Integer, TreeSet<ViewCategory>> tree = new HashMap<>();

            for (Integer ckey : groups.keySet())
            {
                ViewCategory c = categories.get(ckey);

                if (!tree.containsKey(ckey))
                {
                    tree.put(ckey, new TreeSet<>(t));
                }

                ViewCategory p = c.getParent();
                if (null != p)
                {
                    if (!tree.containsKey(p.getRowId()))
                    {
                        tree.put(p.getRowId(), new TreeSet<>(t));
                    }
                    tree.get(p.getRowId()).add(c);
                }
            }

            // create output

            // Construct root node
            JSONObject root = new JSONObject();
            JSONArray rootChildren = new JSONArray();

            for (ViewCategory vc : order)
            {
                JSONObject category = new JSONObject();
                category.put("name", vc.getLabel());
                category.put("icon", false);
                category.put("expanded", true);
                category.put("cls", "dvcategory");
                category.put("children", processChildren(vc, groups, tree));

                rootChildren.put(category);
            }

            root.put("name", ".");
            root.put("expanded", true);
            root.put("children", rootChildren);

            return root;
        }

        private JSONArray processChildren(ViewCategory vc, Map<Integer, List<DataViewInfo>> groups, Map<Integer, TreeSet<ViewCategory>> tree)
        {
            JSONArray children = new JSONArray();

            // process other categories
            if (tree.get(vc.getRowId()).size() > 0)
            {
                // has it's own sub-categories
                for (ViewCategory v : tree.get(vc.getRowId()))
                {
                    JSONObject category = new JSONObject();
                    category.put("name", v.getLabel());
                    category.put("icon", false);
                    category.put("expanded", true);
                    category.put("cls", "dvcategory");
                    category.put("children", processChildren(v, groups, tree));

                    children.put(category);
                }
            }

            // process views
            for (DataViewInfo view : groups.get(vc.getRowId()))
            {
                JSONObject viewJson = DataViewService.get().toJSON(getContainer(), getUser(), view);
                viewJson.put("name", view.getName());
                viewJson.put("leaf", true);
                viewJson.put("icon", view.getIcon());
                children.put(viewJson);
            }

            return children;
        }

        private List<DataViewProvider.Type> getVisibleDataTypes(BrowseDataForm form)
        {
            List<DataViewProvider.Type> visibleDataTypes = new ArrayList<>();
            Map<String, String> props;
            Portal.WebPart webPart = getWebPart(form);

            if (null != webPart)
                props = webPart.getPropertyMap();
            else if (form.isManageView())
                props = getAdminConfiguration();
            else
                props = resolveJSONProperties(form.getProps());

            for (DataViewProvider.Type type : DataViewService.get().getDataTypes(getContainer(), getUser()))
            {
                boolean visible = getCheckedState(type.getName(), props, type.isShowByDefault());

                if (visible)
                    visibleDataTypes.add(type);
            }

            return visibleDataTypes;
        }

        @Nullable
        private Portal.WebPart getWebPart(BrowseDataForm form)
        {
            return Portal.getPart(getContainer(), form.getPageId(), form.getIndex());
        }

        private Boolean getCheckedState(String prop, Map<String, String> propMap, boolean defaultState)
        {
            if (propMap.containsKey(prop))
            {
                return !propMap.get(prop).equals("0");
            }
            return defaultState;
        }

        private Map<String, String> resolveJSONProperties(Map<String, Object> formProps)
        {
            JSONObject jsonProps = (JSONObject) formProps;
            Map<String, String> props = new HashMap<>();
            boolean explicit = false;

            if (null != jsonProps && jsonProps.size() > 0)
            {
                try
                {
                    // Data Types Filter
                    JSONArray dataTypes = jsonProps.getJSONArray("dataTypes");
                    for (int i=0; i < dataTypes.length(); i++)
                    {
                        props.put((String) dataTypes.get(i), "on");
                        explicit = true;
                    }
                }
                catch (JSONException x)
                {
                    /* No-op */
                }

                if (explicit)
                {
                    for (ViewInfo.DataType t : ViewInfo.DataType.values())
                    {
                        if (!props.containsKey(t.name()))
                        {
                            props.put(t.name(), "0");
                        }
                    }
                }
            }

            return props;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetCategoriesAction extends ApiAction<BrowseDataForm>
    {
        public ApiResponse execute(BrowseDataForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<JSONObject> categoryList = new ArrayList<>();

            List<ViewCategory> categoriesWithDisplayOrder = new ArrayList<>();
            List<ViewCategory> categoriesWithoutDisplayOrder = new ArrayList<>();

            ViewCategory[] categories;
            int parent = form.getParent();

            // Default, no parent specifically requested
            if (parent == -2)
            {
                categories = ViewCategoryManager.getInstance().getCategories(getContainer(), getUser());
            }
            else if (parent == 0)
            {
                // parent filter on non-existent category
                categories = new ViewCategory[0];
            }
            else
            {
                SimpleFilter filter;
                FieldKey field = FieldKey.fromParts("Parent");

                if (parent > 0)
                    filter = new SimpleFilter(field, parent);
                else
                    filter = new SimpleFilter(field, null, CompareType.ISBLANK);
                categories = ViewCategoryManager.getInstance().getCategories(getContainer(), getUser(), filter);
            }

            for (ViewCategory c : categories)
            {
                if (c.getDisplayOrder() != 0)
                    categoriesWithDisplayOrder.add(c);
                else
                    categoriesWithoutDisplayOrder.add(c);
            }

            Collections.sort(categoriesWithDisplayOrder, new Comparator<ViewCategory>(){
                @Override
                public int compare(ViewCategory c1, ViewCategory c2)
                {
                    return c1.getDisplayOrder() - c2.getDisplayOrder();
                }
            });

            if (!categoriesWithoutDisplayOrder.isEmpty())
            {
                Collections.sort(categoriesWithoutDisplayOrder, new Comparator<ViewCategory>(){
                    @Override
                    public int compare(ViewCategory c1, ViewCategory c2)
                    {
                        return c1.getLabel().compareToIgnoreCase(c2.getLabel());
                    }
                });
            }
            for (ViewCategory vc : categoriesWithDisplayOrder)
                categoryList.add(vc.toJSON(getUser()));

            // assign an order to all categories returned to the client
            int count = categoriesWithDisplayOrder.size() + 1;
            for (ViewCategory vc : categoriesWithoutDisplayOrder)
            {
                vc.setDisplayOrder(count++);
                categoryList.add(vc.toJSON(getUser()));
            }
            response.put("categories", categoryList);

            return response;
        }
    }

    public static class CategoriesForm implements CustomApiForm
    {
        List<ViewCategory> _categories = new ArrayList<>();

        public List<ViewCategory> getCategories()
        {
            return _categories;
        }

        public void setCategories(List<ViewCategory> categories)
        {
            _categories = categories;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            Object categoriesProp = props.get("categories");
            if (categoriesProp != null)
            {
                for (JSONObject categoryInfo : ((JSONArray) categoriesProp).toJSONObjectArray())
                {
                    _categories.add(ViewCategory.fromJSON(categoryInfo));
                }
            }
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SaveCategoriesAction extends MutatingApiAction<CategoriesForm>
    {
        public ApiResponse execute(CategoriesForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DbScope scope = QueryManager.get().getDbSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (ViewCategory category : form.getCategories())
                    ViewCategoryManager.getInstance().saveCategory(getContainer(), getUser(), category);

                transaction.commit();

                response.put("success", true);
                return response;
            }
            catch (Exception e) {
                response.put("success", false);
                response.put("message", e.getMessage());
            }
            return response;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteCategoriesAction extends MutatingApiAction<CategoriesForm>
    {
        @Override
        public void validateForm(CategoriesForm form, Errors errors)
        {
            for (ViewCategory category : form.getCategories())
            {
                if (!category.canDelete(getContainer(), getUser()))
                    errors.reject(ERROR_MSG, "You must be an administrator to delete a view category");
            }
        }

        public ApiResponse execute(CategoriesForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DbScope scope = QueryManager.get().getDbSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (ViewCategory category : form.getCategories())
                    ViewCategoryManager.getInstance().deleteCategory(getContainer(), getUser(), category);

                transaction.commit();

                response.put("success", true);
                return response;
            }
        }
    }

    public static class EditViewsForm
    {
        String _id;
        String _dataType;

        public String getId()
        {
            return _id;
        }

        public void setId(String id)
        {
            _id = id;
        }

        public String getDataType()
        {
            return _dataType;
        }

        public void setDataType(String dataType)
        {
            _dataType = dataType;
        }

        public Map<String, Object> getPropertyMap(PropertyValues pv, List<String> editableValues, Map<String, MultipartFile> files) throws ValidationException
        {
            Map<String, Object> map = new HashMap<>();

            for (PropertyValue value : pv.getPropertyValues())
            {
                if (editableValues.contains(value.getName()))
                    map.put(value.getName(), value.getValue());
            }

            for (String fileName : files.keySet())
            {
                if (editableValues.contains(fileName) && !files.get(fileName).isEmpty())
                {
                    try {
                        map.put(fileName, files.get(fileName).getInputStream());
                    }
                    catch(IOException e)
                    {
                        throw new ValidationException("Unable to read file: " + fileName);
                    }
                }
            }

            return map;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class EditViewAction extends MutatingApiAction<EditViewsForm>
    {
        private DataViewProvider _provider;
        private Map<String, Object> _propertiesMap;

        public EditViewAction()
        {
            super();
            //because this will typically be called from a hidden iframe
            //we must respond with a content-type of text/html or the
            //browser will prompt the user to save the response, as the
            //browser won't natively show application/json content-type
            setContentTypeOverride("text/html");
        }

        @Override
        public void validateForm(EditViewsForm form, Errors errors)
        {
            DataViewProvider.Type type = DataViewService.get().getDataTypeByName(form.getDataType());
            if (type != null)
            {
                _provider = DataViewService.get().getProvider(type, getViewContext());
                DataViewProvider.EditInfo editInfo = _provider.getEditInfo();

                if (editInfo != null)
                {
                    List<String> editable = Arrays.asList(editInfo.getEditableProperties(getContainer(), getUser()));
                    try
                    {
                        _propertiesMap = form.getPropertyMap(getPropertyValues(), editable, getFileMap());
                        editInfo.validateProperties(getContainer(), getUser(), form.getId(), _propertiesMap);
                    }
                    catch (ValidationException e)
                    {
                        for (ValidationError error : e.getErrors())
                            errors.reject(ERROR_MSG, error.getMessage());
                    }
                }
                else
                    errors.reject(ERROR_MSG, "This data view does not support editing");
            }
            else
                errors.reject(ERROR_MSG, "Unable to find the specified data view type");
        }

        public ApiResponse execute(EditViewsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DataViewProvider.EditInfo editInfo = _provider.getEditInfo();
            if (editInfo != null && _propertiesMap != null)
            {
                editInfo.updateProperties(getViewContext(), form.getId(), _propertiesMap);
                response.put("success", true);
            }
            else
                response.put("success", false);

            return response;
        }
    }

    public static class DeleteDataViewsForm implements CustomApiForm
    {
        List<Pair<String, String>> _views = new ArrayList<>();

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            if (props.containsKey("views"))
            {
                Object views = props.get("views");
                if (views instanceof JSONArray)
                {
                    for (JSONObject view : ((JSONArray)views).toJSONObjectArray())
                        _views.add(new Pair<String, String>(view.getString("dataType"), view.getString("id")));
                }
                else if (views instanceof JSONObject)
                {
                    JSONObject view = (JSONObject)views;
                    _views.add(new Pair<String, String>(view.getString("dataType"), view.getString("id")));
                }
            }
        }

        public List<Pair<String, String>> getViews()
        {
            return _views;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DeleteViewsAction extends MutatingApiAction<DeleteDataViewsForm>
    {
        @Override
        public void validateForm(DeleteDataViewsForm form, Errors errors)
        {
            Map<String, Boolean> validMap = new HashMap<>();

            for (Pair<String, String> view : form.getViews())
            {
                if (validMap.containsKey(view.getKey()))
                {
                    continue;
                }
                else
                {
                    DataViewProvider.Type type = DataViewService.get().getDataTypeByName(view.getKey());
                    if (type != null)
                    {
                        DataViewProvider provider = DataViewService.get().getProvider(type, getViewContext());
                        DataViewProvider.EditInfo editInfo = provider.getEditInfo();

                        if (editInfo != null)
                        {
                            List<DataViewProvider.EditInfo.Actions> actions = Arrays.asList(editInfo.getAllowableActions(getContainer(), getUser()));
                            if (actions.contains(DataViewProvider.EditInfo.Actions.delete))
                                validMap.put(view.getKey(), true);
                            else
                            {
                                errors.reject(ERROR_MSG, "This data view does not support deletes");
                                return;
                            }
                        }
                        else
                        {
                            errors.reject(ERROR_MSG, "This data view does not support deletes");
                            return;
                        }
                    }
                    else
                    {
                        errors.reject(ERROR_MSG, "Unable to find the specified data view type");
                        return;
                    }
                }
            }
        }

        public ApiResponse execute(DeleteDataViewsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            DbScope scope = QueryManager.get().getDbSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (Pair<String, String> view : form.getViews())
                {
                    DataViewProvider.Type type = DataViewService.get().getDataTypeByName(view.getKey());
                    if (type != null)
                    {
                        DataViewProvider provider = DataViewService.get().getProvider(type, getViewContext());
                        DataViewProvider.EditInfo editInfo = provider.getEditInfo();

                        if (editInfo != null)
                        {
                            editInfo.deleteView(getContainer(), getUser(), view.getValue());
                        }
                    }
                }
                transaction.commit();
            }
            response.put("success", true);
            return response;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetReportAction extends ApiAction<ReportForm>
    {
        @Override
        public ApiResponse execute(ReportForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Report report = null;
            if (form.getReportId() != null)
                report = form.getReportId().getReport(getViewContext());

            if (report != null)
            {
                response.put("reportConfig", report.serialize(getContainer(), getUser()));
                response.put("success", true);
            }
            else
                throw new IllegalStateException("Unable to find specified report");

            return response;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ManageNotificationsAction extends SimpleViewAction<NotificationsForm>
    {
        public ModelAndView getView(NotificationsForm form, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/query/reports/view/manageNotifications.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Manage Report/Dataset Notifications");
        }
    }

    public static class NotificationsForm extends ViewForm
    {
        public NotificationsForm()
        {
        }

        public List<ViewCategoryManager.ViewCategoryTreeNode> getCategorySubcriptionTree()
        {
            Set<Integer> subscriptionSet = ReportContentEmailManager.getSubscriptionSet(getContainer(), getUser());
            return ViewCategoryManager.getInstance().getCategorySubcriptionTree(getContainer(), getUser(), subscriptionSet);
        }

    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SaveCategoryNotificationsAction extends MutatingApiAction<SaveCategoryNotificationsForm>
    {
        @Override
        public ApiResponse execute(SaveCategoryNotificationsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            ReportContentEmailManager.setSubscriptionSet(getContainer(), getUser(), form.getSubscriptionSet());
            response.put("success", true);
            return response;
        }

    }

    public static class SaveCategoryNotificationsForm implements CustomApiForm
    {
        private Set<Integer> _subscriptionSet = new HashSet<>();

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            JSONArray categories = (JSONArray)props.get("categories");
            if (null != categories)
            {
                for (int i = 0; i < categories.length(); i += 1)
                {
                    Integer rowId = ((JSONObject) categories.get(i)).getInt("rowid");
                    Boolean subscribed = ((JSONObject) categories.get(i)).getBoolean("subscribed");
                    assert(null != rowId);
                    if (subscribed)
                        _subscriptionSet.add(rowId);
                }
            }
        }

        public Set<Integer> getSubscriptionSet()
        {
            return _subscriptionSet;
        }
    }

    // Used for testing the daily digest email notifications
    @RequiresSiteAdmin
    public class SendDailyDigest extends SimpleRedirectAction
    {

        @Override
        public URLHelper getRedirectURL(Object o) throws Exception
        {
            DailyMessageDigest messageDigest = new DailyMessageDigest() {
                @Override
                protected Date getEndRange(Date current, Date last)
                {
                    return current;
                }
            };
            digestThread.start();

            return new ActionURL(ManageViewsAction.class, getContainer());
        }

        // Spawn a new thread so the digest creation doesn't have the Spring Action context available.
        Thread digestThread = new Thread() {
            @Override
            public void run()
            {
                // Normally, daily digest stops at previous midnight; override to include all messages through now
                DailyMessageDigest messageDigest = new DailyMessageDigest() {
                    @Override
                    protected Date getEndRange(Date current, Date last)
                    {
                        return current;
                    }
                };
                messageDigest.addProvider(ReportAndDatasetChangeDigestProvider.get());
                try
                {
                    messageDigest.sendMessageDigest();
                }
                catch (Exception e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
            }
        };
    }
}
