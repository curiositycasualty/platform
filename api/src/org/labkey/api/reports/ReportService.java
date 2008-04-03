package org.labkey.api.reports;

import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 21, 2007
 */
public class ReportService
{
    static private I _instance;

    public static synchronized ReportService.I get()
    {
        return _instance;
    }

    private ReportService(){}

    static public synchronized void registerProvider(I provider)
    {
        // only one provider for now
        if (_instance != null)
            throw new IllegalStateException("A report service provider :" + _instance.getClass().getName() + " has already been registered");

        _instance = provider;
    }

    public interface I
    {
        /**
         * Registers a report type, reports must be registered in order to be created.
         */
        public void registerReport(Report report);

        /**
         * A descriptor class must be registered with the service in order to be valid.
         */
        public void registerDescriptor(ReportDescriptor descriptor);

        /**
         * creates new descriptor or report instances.
         */
        public ReportDescriptor createDescriptorInstance(String typeName);
        public Report createReportInstance(String typeName);

        public void deleteReport(ViewContext context, Report report) throws SQLException;

        public int saveReport(ViewContext context, String key, Report report) throws SQLException;

        public Report getReport(int reportId) throws SQLException;
        public Report[] getReports(User user) throws SQLException;
        public Report[] getReports(User user, Container c) throws SQLException;
        public Report[] getReports(User user, Container c, String key) throws SQLException;
        public Report[] getReports(User user, Container c, String key, int flagMask, int flagValue) throws SQLException;
        public Report[] getReports(Filter filter) throws SQLException;

        /**
         * Provides a module specific way to add ui to the report designers.
         */
        public void addViewFactory(ViewFactory vf);
        public List<ViewFactory> getViewFactories();

        public Report createFromQueryString(String queryString) throws Exception;
    }

    public interface ViewFactory
    {
        public HttpView createView(ViewContext context, RReportBean bean);
    }
}
