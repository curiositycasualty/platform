package org.labkey.api.reports.report;

import org.labkey.api.query.*;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.security.ACL;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.*;

import javax.servlet.http.HttpServletResponse;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 6, 2006
 */
public class QueryReport extends AbstractReport
{
    public static final String TYPE = "ReportService.queryReport";

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "Data Grid View";
    }

    public String getDescriptorType()
    {
        return QueryReportDescriptor.TYPE;
    }

    public boolean canHavePermissions()
    {
        return true;
    }

    public HttpView renderReport(ViewContext context)
    {
        ReportDescriptor reportDescriptor = getDescriptor();

        String errorMessage = null;
        if (reportDescriptor instanceof QueryReportDescriptor)
        {
            try {
                final QueryReportDescriptor descriptor = (QueryReportDescriptor)reportDescriptor;
                QueryReportDescriptor.QueryViewGenerator qvGen = getQueryViewGenerator();
                if (qvGen == null)
                {
                    qvGen = descriptor.getQueryViewGenerator();
                }

                if (qvGen != null)
                {
                    ReportQueryView qv = qvGen.generateQueryView(context, descriptor);
                    if (qv != null)
                    {
                        final UserSchema schema = qv.getQueryDef().getSchema();
                        if (schema != null)
                        {
                            String queryName = descriptor.getProperty("queryName");
                            if (queryName != null)
                            {
                                String viewName = descriptor.getProperty(QueryParam.viewName.toString());
                                QuerySettings qs = new QuerySettings(context.getActionURL(), "Report");
                                qs.setSchemaName(schema.getSchemaName());
                                qs.setQueryName(queryName);
                                QueryDefinition queryDef = qv.getQueryDef();
                                if (queryDef.getCustomView(null, context.getRequest(), viewName) == null)
                                {
                                    CustomView view = queryDef.createCustomView(null, viewName);
                                    view.setIsHidden(true);
                                    view.save(context.getUser(), context.getRequest());
                                }
                                qs.setViewName(viewName);
                                //ReportQueryView queryReportView = new ReportQueryView(context, schema, qs);
                                JspView<HeaderBean> headerView = new JspView<HeaderBean>("/org/labkey/query/reports/view/queryReportHeader.jsp",
                                        new HeaderBean(context, qv.getCustomizeURL()));
                                return new VBox(headerView, qv);
                            }
                            else
                            {
                                errorMessage = "Invalid report params: the queryName must be specified in the QueryReportDescriptor";
                            }
                        }
                    }
                }
                else
                {
                    errorMessage = "Invalid report params: A query view generator has not been specified through the ReportDescriptor";
                }
            }
            catch (Exception e)
            {
                errorMessage = e.getMessage();
            }
        }
        else
        {
            errorMessage = "Invalid report params: The ReportDescriptor must be an instance of QueryReportDescriptor";
        }

        if (errorMessage != null)
        {
            return new VBox(ExceptionUtil.getErrorView(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage, null, context.getRequest(), false));
        }
        return null;
    }

    public static class HeaderBean
    {
        private ViewContext _viewContext;
        private ActionURL _customizeURL;

        public HeaderBean(ViewContext context, ActionURL customizeURL)
        {
            _viewContext = context;
            _customizeURL = customizeURL;
        }

        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        public boolean showCustomizeLink()
        {
            return _viewContext.getACL().hasPermission(_viewContext.getUser(), ACL.PERM_ADMIN);
        }

        public ActionURL getCustomizeURL()
        {
            return _customizeURL;
        }
    }

    public QueryReportDescriptor.QueryViewGenerator getQueryViewGenerator()
    {
        return null;
    }
}
