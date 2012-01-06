/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.settings;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewServlet;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Date;

/**
 * User: arauch
 * Date: Apr 11, 2005
 * Time: 1:10:18 PM
 */
public class AppProps extends AbstractWriteableSettingsGroup
{
    private static AppProps _instance = null;

    private static String _contextPathStr;
    private static Path _contextPath = null;
    private static int _serverPort = -1;
    private static String _scheme;
    private static String _serverName;
    private static String _projectRoot = null;

    protected static final String LOOK_AND_FEEL_REVISION = "logoRevision";
    protected static final String DEFAULT_DOMAIN_PROP = "defaultDomain";
    protected static final String BASE_SERVER_URL_PROP = "baseServerURL";
    protected static final String DEFAULT_LSID_AUTHORITY_PROP = "defaultLsidAuthority";
    protected static final String PIPELINE_TOOLS_DIR_PROP = "pipelineToolsDirectory";    
    protected static final String SSL_REQUIRED = "sslRequired";
    protected static final String SSL_PORT = "sslPort";
    protected static final String USER_REQUESTED_ADMIN_ONLY_MODE = "adminOnlyMode";
    protected static final String ADMIN_ONLY_MESSAGE = "adminOnlyMessage";
    protected static final String EXCEPTION_REPORTING_LEVEL = "exceptionReportingLevel";
    protected static final String USAGE_REPORTING_LEVEL = "usageReportingLevel";
    protected static final String SERVER_GUID = "serverGUID";
    protected static final String SERVER_GUID_XML_PARAMETER_NAME = "org.labkey.mothership." + SERVER_GUID;
    protected static final String BLAST_SERVER_BASE_URL_PROP = "BLASTBaseURL";
    protected static final String MASCOT_SERVER_PROP = "MascotServer";
    protected static final String MASCOT_USERACCOUNT_PROP = "MascotUserAccount";
    protected static final String MASCOT_USERPASSWORD_PROP = "MascotUserPassword";
    protected static final String MASCOT_HTTPPROXY_PROP = "MascotHTTPProxy";
    protected static final String SYSTEM_MAINTENANCE_INTERVAL = "systemMaintenanceInterval";  // NEVER, DAILY
    protected static final String SYSTEM_MAINTENANCE_TIME = "systemMaintenanceTime"; // 02:00
    protected static final String MEMORY_USAGE_DUMP_INTERVAL = "memoryUsageDumpInterval";
    protected static final String NETWORK_DRIVE_LETTER = "networkDriveLetter";
    protected static final String NETWORK_DRIVE_PATH = "networkDrivePath";
    protected static final String NETWORK_DRIVE_USER = "networkDriveUser";
    protected static final String NETWORK_DRIVE_PASSWORD = "networkDrivePassword";
    protected static final String CABIG_ENABLED = "caBIGEnabled";
    protected static final String MAIL_RECORDER_ENABLED = "mailRecorderEnabled";
    protected static final String WEB_ROOT = "webRoot";
    protected static final String MAX_BLOB_SIZE = "maxBLOBSize";

    protected static final String SITE_CONFIG_NAME = "SiteConfig";

    private static final String SERVER_SESSION_GUID = GUID.makeGUID();

    public static synchronized AppProps getInstance()
    {
        if (null == _instance)
            _instance = new AppProps();

        return _instance;
    }

    protected String getType()
    {
        return "site settings";
    }

    protected String getGroupName()
    {
        return SITE_CONFIG_NAME;
    }

    public void setContextPath(HttpServletRequest request)
    {
        // Should be called once at first request
        assert null == _contextPathStr;

        _contextPathStr = request.getContextPath();
        assert _contextPathStr.isEmpty() || _contextPathStr.startsWith("/");
    }

    public String getContextPath()
    {
        if (_contextPathStr == null)
        {
            throw new IllegalStateException("Unable to determine the context path before a request has come in");
        }
        return _contextPathStr;
    }

    public Path getParsedContextPath()
    {
        if (_contextPath == null)
        {
            if (StringUtils.isEmpty(getContextPath()))
                _contextPath = Path.rootPath;
            else
                _contextPath = Path.parse(getContextPath() + "/");
            assert _contextPath.isDirectory();
        }
        return _contextPath;
    }

    public int getServerPort()
    {
        if (_serverPort == -1)
        {
            throw new IllegalStateException("Unable to determine the server port before a request has come in");
        }
        return _serverPort;
    }

    public String getScheme()
    {
        if (_scheme == null)
        {
            throw new IllegalStateException("Unable to determine the scheme before a request has come in");
        }
        return _scheme;
    }

    public String getServerName()
    {
        if (_serverName == null)
        {
            throw new IllegalStateException("Unable to determine the server name before a request has come in");
        }
        return _serverName;
    }

    public void initializeBaseServerUrl(HttpServletRequest request)
    {
        assert null == _serverName && null == _scheme && -1 == _serverPort;     // Should be called once at first request

        String baseServerUrl = lookupStringValue(BASE_SERVER_URL_PROP, null);

        if (baseServerUrl != null)
        {
            try
            {
                setBaseServerUrlAttributes(baseServerUrl);

/*              TODO: Turn into test case

                setBaseServerAttributes("https://localhost/");
                setBaseServerAttributes("http://localhost:8080");
                setBaseServerAttributes("http://localhost");
                setBaseServerAttributes("https://localhost/");
                setBaseServerAttributes("https://localhost/notallowed");  // Should error
*/
                return;
            }
            catch (URISyntaxException e)
            {
                // Ignore -- just use request
            }
        }

        _serverPort = request.getServerPort();
        _scheme = request.getScheme();
        _serverName = request.getServerName();
    }


    // Update the cached base server url attributes.
    public void setBaseServerUrlAttributes(String baseServerUrl) throws URISyntaxException
    {
        URLHelper url = new URLHelper(baseServerUrl);

        if (url.getParsedPath().size() > 0)
            throw new URISyntaxException(baseServerUrl, "Too many path parts");

        String scheme = url.getScheme();
        String serverName = url.getHost();
        int serverPort;

        if (url.getPort() != -1)
        {
            serverPort = url.getPort();
        }
        else
        {
            if ("http".equals(scheme))
                serverPort = 80;
            else if ("https".equals(scheme))
                serverPort = 443;
            else
                throw new URISyntaxException(baseServerUrl, "Invalid scheme");
        }

        // New values have been validated -- now update global settings
        _scheme = scheme;
        _serverName = serverName;
        _serverPort = serverPort;
    }


    // Mock up a request using cached port, server name, etc.  Use when sending email from background threads (when
    // a request is not available).
    public HttpServletRequest createMockRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest(ViewServlet.getViewServletContext());

        request.setContextPath(getContextPath());
        request.setServerPort(getServerPort());
        request.setServerName(getServerName());
        request.setScheme(getScheme());

        return request;
    }

    public String getDefaultDomain()
    {
        return lookupStringValue(DEFAULT_DOMAIN_PROP, "");
    }

    public String getBaseServerUrl()
    {
        return URLHelper.getBaseServer(_scheme, _serverName, _serverPort).toString();
    }

    // CONSIDER: All the following should probably be migrated into look & feel settings, making them overrideable at the project level

    public String getHomePageUrl()
    {
        return getHomePageActionURL().getLocalURIString();
    }

    public ActionURL getHomePageActionURL()
    {
        return PageFlowUtil.urlProvider(ProjectUrls.class).getHomeURL();
    }

    public int getLookAndFeelRevision()
    {
        return lookupIntValue(LOOK_AND_FEEL_REVISION, 0);
    }

    public String getDefaultLsidAuthority()
    {
        String result = lookupStringValue(DEFAULT_LSID_AUTHORITY_PROP, "localhost");
        if (result == null || "".equals(result))
        {
            // We now prevent empty values but in case there's an installation that has one, convert to "localhost"
            return "localhost";
        }
        return result;
    }

    public String getPipelineToolsDirectory()
    {
        File webappDir = new File(ModuleLoader.getServletContext().getRealPath(""));
        File binDir = new File(webappDir.getParentFile(), "bin");

        return lookupStringValue(PIPELINE_TOOLS_DIR_PROP, binDir.getAbsolutePath());
    }

    public boolean isSSLRequired()
    {
        return lookupBooleanValue(SSL_REQUIRED, false);
    }

    public boolean isUserRequestedAdminOnlyMode()
    {
        return lookupBooleanValue(USER_REQUESTED_ADMIN_ONLY_MODE, false);
    }

    public String getAdminOnlyMessage()
    {
        return lookupStringValue(ADMIN_ONLY_MESSAGE, "This site is currently undergoing maintenance, and only administrators can log in.");
    }

    public int getSSLPort()
    {
        return lookupIntValue(SSL_PORT, 443);
    }

    public String getSystemMaintenanceInterval()
    {
        return lookupStringValue(SYSTEM_MAINTENANCE_INTERVAL, "daily");
    }

    public Date getSystemMaintenanceTime()
    {
        String time = lookupStringValue(SYSTEM_MAINTENANCE_TIME, "2:00");
        return SystemMaintenance.parseSystemMaintenanceTime(time);
    }

    public int getMemoryUsageDumpInterval()
    {
        return lookupIntValue(MEMORY_USAGE_DUMP_INTERVAL, 0);
    }

    public int getMaxBLOBSize()
    {
        return lookupIntValue(MAX_BLOB_SIZE, 50000000);
    }

    public ExceptionReportingLevel getExceptionReportingLevel()
    {
        try
        {
            // Ensure that dev machines are setup with High reporting levels.  That way, if they test in production mode we'll be able to filter out those exception reports.
            return ExceptionReportingLevel.valueOf(lookupStringValue(EXCEPTION_REPORTING_LEVEL, isDevMode() ? ExceptionReportingLevel.HIGH.toString() : ExceptionReportingLevel.LOW.toString()));
        }
        catch (IllegalArgumentException e)
        {
            return ExceptionReportingLevel.LOW;
        }
    }

    public String getServerGUID()
    {
        ServletContext context = ModuleLoader.getServletContext();
        if (context != null)
        {
            String serverGUID = context.getInitParameter(SERVER_GUID_XML_PARAMETER_NAME);
            if (serverGUID != null)
            {
                return serverGUID;
            }
        }
        String serverGUID = lookupStringValue(SERVER_GUID, SERVER_SESSION_GUID);
        if (serverGUID.equals(SERVER_SESSION_GUID))
        {
            try
            {
                WriteableAppProps writeable = WriteableAppProps.getWriteableInstance();
                writeable.storeStringValue(SERVER_GUID, serverGUID);
                writeable.save();
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        return serverGUID;
    }

    public String getBLASTServerBaseURL()
    {
        return lookupStringValue(BLAST_SERVER_BASE_URL_PROP, "http://www.ncbi.nlm.nih.gov/blast/Blast.cgi?CMD=Web&amp;LAYOUT=TwoWindows&amp;AUTO_FORMAT=Semiauto&amp;ALIGNMENTS=50&amp;ALIGNMENT_VIEW=Pairwise&amp;CDD_SEARCH=on&amp;CLIENT=web&amp;COMPOSITION_BASED_STATISTICS=on&amp;DATABASE=nr&amp;DESCRIPTIONS=100&amp;ENTREZ_QUERY=(none)&amp;EXPECT=1000&amp;FILTER=L&amp;FORMAT_OBJECT=Alignment&amp;FORMAT_TYPE=HTML&amp;I_THRESH=0.005&amp;MATRIX_NAME=BLOSUM62&amp;NCBI_GI=on&amp;PAGE=Proteins&amp;PROGRAM=blastp&amp;SERVICE=plain&amp;SET_DEFAULTS.x=41&amp;SET_DEFAULTS.y=5&amp;SHOW_OVERVIEW=on&amp;END_OF_HTTPGET=Yes&amp;SHOW_LINKOUT=yes&amp;QUERY=");
    }

    public boolean hasMascotServer()
    {
        return !"".equals(getMascotServer());
    }

    public String getMascotServer()
    {
        return lookupStringValue(MASCOT_SERVER_PROP, "");
    }

    public String getMascotUserAccount()
    {
        return lookupStringValue(MASCOT_USERACCOUNT_PROP, "");
    }

    public String getMascotUserPassword()
    {
        return lookupStringValue(MASCOT_USERPASSWORD_PROP, "");
    }

    public String getMascotHTTPProxy()
    {
        return lookupStringValue(MASCOT_HTTPPROXY_PROP, "");
    }

    public String getNetworkDriveLetter()
    {
        return lookupStringValue(NETWORK_DRIVE_LETTER, "");
    }

    public String getNetworkDrivePath()
    {
        return lookupStringValue(NETWORK_DRIVE_PATH, "");
    }

    public String getNetworkDriveUser()
    {
        return lookupStringValue(NETWORK_DRIVE_USER, "");
    }

    public String getNetworkDrivePassword()
    {
        return lookupStringValue(NETWORK_DRIVE_PASSWORD, "");
    }

    public String getServerSessionGUID()
    {
        return SERVER_SESSION_GUID;
    }

    public boolean isMailRecorderEnabled()
    {
        return lookupBooleanValue(MAIL_RECORDER_ENABLED, false);
    }

    public boolean isDevMode()
    {
        return Boolean.getBoolean("devmode");
    }

    public boolean isCachingAllowed()
    {
        return Boolean.getBoolean("caching") || !isDevMode();
    }

    public boolean isRecompileJspEnabled()
    {
        return isDevMode() && !Boolean.getBoolean("labkey.disableRecompileJsp");
    }

    public void setProjectRoot(String projectRoot)
    {
        _projectRoot = projectRoot;
    }

    // Return the root of the main source tree
    public String getProjectRoot()
    {
        return _projectRoot;
    }

    public File getFileSystemRoot()
    {
        String webRoot = lookupStringValue(WEB_ROOT, "");
        if (!StringUtils.isEmpty(webRoot))
        {
            return new File(webRoot);
        }
        return null;
    }

    public UsageReportingLevel getUsageReportingLevel()
    {
        try
        {
            return UsageReportingLevel.valueOf(lookupStringValue(USAGE_REPORTING_LEVEL, UsageReportingLevel.MEDIUM.toString()));
        }
        catch (IllegalArgumentException e)
        {
            return UsageReportingLevel.LOW;
        }
    }

    public static WriteableAppProps getWriteableInstance() throws SQLException
    {
        return new WriteableAppProps(ContainerManager.getRoot());
    }

    public boolean isCaBIGEnabled()
    {
        return lookupBooleanValue(CABIG_ENABLED, false);
    }

    // Get the name of the webapp configuration file, e.g., labkey.xml, cpas.xml, or root.xml.  Used in some error messages
    //  to provide suggestions to the admin.
    public String getWebappConfigurationFilename()
    {
        // Would rather determine the context filename from ModuleLoader.getServletContext(), but there appears to be
        //  no way to do this.

        String path = getContextPath();

        return "".equals(path) ? "root.xml" : path.substring(1) + ".xml";
    }

    //WCH: 20060629 - for Customisable web colour theme
    protected static final String WEB_THEME_CONFIG_NAME = "WebThemeConfig";
    //END-WCH: 20060629

    //WCH: 20060629 - for Customisable web colour theme
    public static PropertyManager.PropertyMap getWebThemeConfigProperties() throws SQLException
    {
        return PropertyManager.getWritableProperties(SITE_CONFIG_USER_ID, ContainerManager.getRoot().getId(), WEB_THEME_CONFIG_NAME, true);
    }

    public String getLabKeyVersionString()
    {
        DecimalFormat format = new DecimalFormat("0.00");
        return format.format(ModuleLoader.getInstance().getCoreModule().getVersion());
    }
}
