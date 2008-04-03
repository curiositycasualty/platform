package org.labkey.api.reports.report.view;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.view.ViewContext;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 15, 2007
 */
public class ManageReportsBean
{
    public static final String R_REPORT = "Saved R Views";
    public static final String CHART_REPORT = "Saved Chart Views";

    protected ViewContext _context;
    protected Map<String, List<ReportRecord>> _views;
    protected int _viewCount;
    private BindException _errors;

    public ManageReportsBean(ViewContext context)
    {
        _context = context;
    }

    public void setErrors(BindException errors){_errors = errors;}
    public BindException getErrors(){return _errors;}

    public int getReportCount() throws Exception
    {
        getViews();
        return _viewCount;
    }

    /**
     * Gets a map of live reports grouped by report type and datasets
     */
    public final Map<String, List<ReportRecord>> getViews() throws Exception
    {
        if (_views == null)
        {
            _views = createReportRecordMap();

            beforeGetViews(_views);
            for (Report r : ReportService.get().getReports(_context.getUser(), _context.getContainer()))
            {
                createReportRecord(r, _views);
            }
            afterGetViews(_views);

            // spin through the reports to get a count
            for (List<ReportRecord> category : _views.values())
                _viewCount += category.size();
        }
        return _views;
    }

    protected void beforeGetViews(Map<String, List<ReportRecord>> views)
    {
    }

    protected void afterGetViews(Map<String, List<ReportRecord>> views)
    {
    }

    protected Map<String, List<ReportRecord>> createReportRecordMap()
    {
        return new TreeMap<String, List<ReportRecord>>();
    }

    protected void createReportRecord(Report r, Map<String, List<ReportRecord>> views)
    {
        ReportRecordImpl rec = new ReportRecordImpl(r,
                r.getDescriptor().getReportName(),
                null,
                ChartUtil.getDeleteReportURL(_context, r, _context.getActionURL()).getLocalURIString());
        getList(r.getTypeDescription(), views).add(rec);
    }

    // helper for get or create the report list
    protected List<ReportRecord> getList(String key, Map<String, List<ReportRecord>> views)
    {
        List<ReportRecord> reports = views.get(key);
        if (reports == null) {
            reports = new ArrayList<ReportRecord>();
            views.put(key, reports);
        }
        return reports;
    }

    public interface ReportRecord
    {
        public String getName();
        public String getDisplayURL();
        public String getDeleteURL();
        public String getEditURL();
        public Report getReport();
        public String getTooltip();
        public User getCreatedBy();
        public boolean isShared();
    }

    protected static class ReportRecordImpl implements ReportRecord
    {
        private String _name;
        private String _displayURL;
        private String _deleteURL;
        private Report _report;
        private String _tooltip;
        private String _editURL;

        public ReportRecordImpl(Report report, String name, String displayURL, String deleteURL)
        {
            _report = report;
            _name = name;
            _displayURL = displayURL;
            _deleteURL = deleteURL;

            if (report != null && !StringUtils.isEmpty(report.getDescriptor().getReportDescription()))
                _tooltip = report.getDescriptor().getReportDescription();
        }
        public String getName(){return _name;}
        public String getDisplayURL(){return _displayURL;}
        public String getDeleteURL(){return _deleteURL;}
        public Report getReport(){return _report;}
        public String getTooltip(){return _tooltip;}

        public String getEditURL(){return _editURL;}
        public void setEditURL(String editURL){_editURL = editURL;}

        public boolean equals(Object obj)
        {
            if (obj instanceof ReportRecord)
                return StringUtils.equals(((ReportRecord)obj).getName(), getName());

            return false;
        }

        public int hashCode()
        {
            if (getName() != null)
                return getName().hashCode();
            return 0;
        }

        public User getCreatedBy()
        {
            Integer id = _report.getDescriptor().getCreatedBy();
            if (id != null)
                return UserManager.getUser(id);
            return null;
        }

        public boolean isShared()
        {
            return _report.getDescriptor().getOwner() == null;
        }
    }
}
