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
package org.labkey.api.module;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.BreakpointThread;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.HttpView;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.servlet.mvc.Controller;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: migra
 * Date: Jul 13, 2005
 * Time: 10:27:45 AM
 */
public class ModuleLoader implements Filter
{
    private static final double EARLIEST_UPGRADE_VERSION = 10.3;
    private static final Logger _log = Logger.getLogger(ModuleLoader.class);
    private static final Map<String, Throwable> _moduleFailures = new HashMap<String, Throwable>();
    private static final Map<String, Module> _controllerNameToModule = new HashMap<String, Module>();
    private static final Map<Package, String> _packageToPageFlowURL = new HashMap<Package, String>();
    private static final Map<String, Module> _schemaNameToModule = new HashMap<String, Module>();
    private static final Map<String, Collection<ResourceFinder>> _resourceFinders = new ConcurrentHashMap<String, Collection<ResourceFinder>>();
    private static final Map<Class, Class<? extends UrlProvider>> _urlProviderToImpl = new HashMap<Class, Class<? extends UrlProvider>>();
    private static final CoreSchema _core = CoreSchema.getInstance();
    private static final Object UPGRADE_LOCK = new Object();
    private static final Object STARTUP_LOCK = new Object();

    private static ModuleLoader _instance = null;
    private static Throwable _startupFailure = null;
    private static boolean _newInstall = false;

    private boolean _deferUsageReport = false;
    private File _webappDir;
    private UpgradeState _upgradeState;
    private User upgradeUser = null;
    private boolean _startupComplete = false;

    private final List<ModuleResourceLoader> _resourceLoaders = new ArrayList<ModuleResourceLoader>();

    private enum UpgradeState {UpgradeRequired, UpgradeInProgress, UpgradeComplete}

    public enum ModuleState
    {
        Disabled,
        Loading,
        InstallRequired
        {
            public String describeModuleState(double installedVersion, double targetVersion)
            {
                if (installedVersion > 0.0)
                    return "Upgrade Required: " + ModuleContext.formatVersion(installedVersion) + " -> " + ModuleContext.formatVersion(targetVersion);
                else
                    return "Not Installed.";
            }
        },
        Installing,
        InstallComplete,
        ReadyToRun
        {
            public String describeModuleState(double installedVersion, double targetVersion)
            {
                return "Version " + ModuleContext.formatVersion(installedVersion) + " ready to run.";
            }
        },
        Running
        {
            public String describeModuleState(double installedVersion, double targetVersion)
            {
                return "Version " + ModuleContext.formatVersion(installedVersion) + " running.";
            }
        };

        public String describeModuleState(double installedVersion, double targetVersion)
        {
            return toString();
        }
    }


    private Map<String, ModuleContext> contextMap = new HashMap<String, ModuleContext>();
    private Map<String, Module> moduleMap = new CaseInsensitiveHashMap<Module>();
    private Map<Class<? extends Module>, Module> moduleClassMap = new HashMap<Class<? extends Module>, Module>();

    private List<Module> _modules;
    private final SortedMap<String, FolderType> _folderTypes = new TreeMap<String, FolderType>(new FolderTypeComparator());
    private static class FolderTypeComparator implements Comparator<String>
    {
        //Sort NONE to the bottom and Collaboration to the top
        static final String noneStr = FolderType.NONE.getName();
        static final String collabStr = "Collaboration"; //Cheating

        public int compare(String s, String s1)
        {
            if (s.equals(s1))
                return 0;

            if (noneStr.equals(s))
                return 1;
            if (noneStr.equals(s1))
                return -1;
            if (collabStr.equals(s))
                return -1;
            if (collabStr.equals(s1))
                return 1;

            return s.compareTo(s1);
        }
    }

    public ModuleLoader()
    {
        assert null == _instance : "Should be only one instance of module loader";
        if (null != _instance)
            _log.error("More than one instance of module loader...");

        _instance = this;
    }

    public static ModuleLoader getInstance()
    {
        //Will be initialized in first line of init
        return _instance;
    }

    public void init(FilterConfig filterConfig) throws ServletException
    {
        try
        {
            doInit(filterConfig.getServletContext());
        }
        catch (Throwable t)
        {
            setStartupFailure(t);
            _log.error("Failure occurred during ModuleLoader init.", t);
        }
    }

    ServletContext _servletContext = null;

    public static ServletContext getServletContext()
    {
        return getInstance() == null ? null : getInstance()._servletContext;
    }

    private void doInit(ServletContext servletCtx) throws Exception
    {
        _servletContext = servletCtx;

        _log.debug("ModuleLoader init");

        verifyJavaVersion();

        rollErrorLogFile(_log);

        // make sure ConvertHelper is initialized
        ConvertHelper.getPropertyEditorRegistrar();

        _webappDir = FileUtil.getAbsoluteCaseSensitiveFile(new File(servletCtx.getRealPath(".")));

        List<File> explodedModuleDirs;

        try
        {
            ClassLoader webappClassLoader = getClass().getClassLoader();
            Method m = webappClassLoader.getClass().getMethod("getExplodedModuleDirectories");
            explodedModuleDirs = (List<File>)m.invoke(webappClassLoader);
        }
        catch (NoSuchMethodException e)
        {
            throw new ConfigurationException("Could not find getExplodedModuleDirectories() method.", "You probably need to copy labkeyBootstrap.jar into $CATALINA_HOME/server/lib and/or edit your labkey.xml to include <Loader loaderClass=\"org.labkey.bootstrap.LabkeyServerBootstrapClassLoader\" />", e);
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }

        //load module instances using Spring
        _modules = loadModules(explodedModuleDirs);

        for (Module module : _modules)
        {
            registerResourceLoaders(module.getResourceLoaders());
        }

        //sort the modules by dependencies
        ModuleDependencySorter sorter = new ModuleDependencySorter();
        _modules = sorter.sortModulesByDependencies(_modules, _resourceLoaders);

        // set the project source root before calling .initialize() on modules
        Module coreModule = _modules.get(0);
        if (coreModule == null || !DefaultModule.CORE_MODULE_NAME.equals(coreModule.getName()))
            throw new IllegalStateException("Core module was not first or could not find the Core module. Ensure that Tomcat user can create directories under the <LABKEY_HOME>/modules directory.");
        setProjectRoot(coreModule);

        //initialize each module in turn
        for (Module module : _modules)
        {
            try
            {
                module.initialize();
                moduleMap.put(module.getName(), module);
                moduleClassMap.put(module.getClass(), module);
            }
            catch(Throwable t)
            {
                _log.error("Unable to initialize module " + module.getName(), t);
                _moduleFailures.put(module.getName(), t);
            }
        }

        // Start up a thread that lets us hit a breakpoint in the debugger, even if
        // all the real working threads are hung. This lets us invoke methods in the debugger,
        // gain easier access to statics, etc.
        File coreModuleDir = coreModule.getExplodedPath();
        File modulesDir = coreModuleDir.getParentFile();
        new BreakpointThread(modulesDir).start();

        initializeDataSources();

        if (getTableInfoModules().getTableType() == DatabaseTableType.NOT_IN_DB)
            _newInstall = true;

        boolean coreRequiredUpgrade = upgradeCoreModule();

        for (ModuleContext context : getAllModuleContexts())
            contextMap.put(context.getName(), context);

        //Make sure we have a context for all modules, even ones we haven't seen before
        for (Module module : _modules)
        {
            ModuleContext context = contextMap.get(module.getName());
            if (null == context)
            {
                context = new ModuleContext(module);
                contextMap.put(context.getName(), context);
            }
            if (context.getInstalledVersion() < module.getVersion())
                context.setModuleState(ModuleState.InstallRequired);
            else
                context.setModuleState(ModuleState.ReadyToRun);
            /*
        else if (!context.isEnabled())
            context.setModuleState(ModuleState.Disabled);
            */
        }

        // Core module should be upgraded and ready-to-run
        ModuleContext coreCtx = contextMap.get(DefaultModule.CORE_MODULE_NAME);
        assert (ModuleState.ReadyToRun == coreCtx.getModuleState());

        List<String> modulesRequiringUpgrade = new LinkedList<String>();

        for (Module m : _modules)
        {
            ModuleContext ctx = getModuleContext(m);
            if (ctx.getInstalledVersion() < m.getVersion())
            {
                modulesRequiringUpgrade.add(ctx.getName());
            }
        }

        if (modulesRequiringUpgrade.isEmpty())
        {
            completeUpgrade(coreRequiredUpgrade);
        }
        else
        {
            setUpgradeState(UpgradeState.UpgradeRequired);
            _log.info("Module upgrade is required: " + modulesRequiringUpgrade.toString());
        }

        _log.info("Core LabKey Server startup is complete, modules will be initialized after the first HTTP/HTTPS request");
    }

    // Set the project source root based upon the core module's source path or the project.root system property.
    private void setProjectRoot(Module core)
    {
        List<String> possibleRoots = new ArrayList<String>();
        if (null != core.getSourcePath())
            possibleRoots.add(core.getSourcePath() + "/../../..");
        if (null != System.getProperty("project.root"))
            possibleRoots.add(System.getProperty("project.root"));

        for (String root : possibleRoots)
        {
            File projectRoot = new File(root);
            if (projectRoot.exists())
            {
                AppProps.getInstance().setProjectRoot(FileUtil.getAbsoluteCaseSensitiveFile(projectRoot).toString());
                // set the root only once
                break;
            }
        }
    }

    /** We want to roll the file every time the server starts, which isn't directly supported by Log4J so we do it manually */
    private void rollErrorLogFile(Logger logger)
    {
        while (logger != null && !logger.getAllAppenders().hasMoreElements())
        {
            logger = (Logger)logger.getParent();
        }

        if (logger == null)
        {
            return;
        }

        for (Enumeration e2 = logger.getAllAppenders(); e2.hasMoreElements();)
        {
            final Appender appender = (Appender)e2.nextElement();
            if (appender instanceof RollingFileAppender && "ERRORS".equals(appender.getName()))
            {
                RollingFileAppender rfa = (RollingFileAppender)appender;
                rfa.rollOver();
            }
        }
    }

    public static List<Module> loadModules(List<File> explodedModuleDirs)
    {
        ApplicationContext parentContext = ServiceRegistry.get().getApplicationContext();

        Map<String,File> moduleNameToFile = new CaseInsensitiveHashMap<File>();
        List<Module> modules = new ArrayList<Module>();
        for(File moduleDir : explodedModuleDirs)
        {
            Module module = null;
            File moduleXml = new File(moduleDir, "config/module.xml");
            try
            {
                if (moduleXml.exists())
                {
                    ApplicationContext applicationContext = null;
                    if (null != ModuleLoader.getInstance() && null != ModuleLoader.getServletContext())
                    {
                        XmlWebApplicationContext beanFactory = new XmlWebApplicationContext();
                        beanFactory.setConfigLocations(new String[]{moduleXml.toURI().toString()});
                        beanFactory.setParent(parentContext);
                        beanFactory.setServletContext(new SpringModule.ModuleServletContextWrapper(ModuleLoader.getServletContext()));
                        beanFactory.refresh();
                        applicationContext = beanFactory;
                    }
                    else
                    {
                        FileSystemXmlApplicationContext beanFactory = new FileSystemXmlApplicationContext();
                        beanFactory.setConfigLocations(new String[]{moduleXml.toURI().toString()});
                        beanFactory.setParent(parentContext);
                        beanFactory.refresh();
                        applicationContext = beanFactory;
                    }

                    try
                    {
                        module = (Module)applicationContext.getBean("moduleBean", Module.class);
                    }
                    catch (NoSuchBeanDefinitionException x)
                    {
                        _log.error("module configuration does not specify moduleBean: " + moduleXml);
                    }
                    catch (RuntimeException x)
                    {
                        _log.error("error reading module configuration: " + moduleXml.getPath(), x);
                    }
                }
                else
                {
                    //check for simple .properties file
                    File modulePropsFile = new File(moduleDir, "config/module.properties");
                    Properties props = new Properties();
                    if (modulePropsFile.exists())
                    {
                        FileInputStream in = new FileInputStream(modulePropsFile);
                        try
                        {
                            props.load(in);
                        }
                        catch (IOException e)
                        {
                            _log.error("Error reading module properties file '" + modulePropsFile.getAbsolutePath() + "'", e);
                        }
                        finally
                        {
                            in.close();
                        }
                    }

                    //assume that module name is directory name
                    String moduleName = moduleDir.getName();
                    if (props.containsKey("name"))
                        moduleName = props.getProperty("name");

                    if (moduleName == null || moduleName.length() == 0)
                        throw new ConfigurationException("Simple module must specify a name in config/module.xml or config/module.properties: " + moduleDir.getParent());

                    SimpleModule simpleModule = new SimpleModule();
                    simpleModule.setName(moduleName);
                    simpleModule.setSourcePath(moduleDir.getAbsolutePath());
                    BeanUtils.populate(simpleModule, props);
                    if (simpleModule instanceof ApplicationContextAware)
                        simpleModule.setApplicationContext(parentContext);

                    module = simpleModule;
                }

                if (null != module)
                {
                    //don't load if we've already loaded a module of the same name
                    if (moduleNameToFile.containsKey(module.getName()))
                    {
                        _log.warn("Module with name '" + module.getName() + "' has already been loaded from "
                                + moduleNameToFile.get(module.getName()).getAbsolutePath() + ". Skipping additional copy of the module in " + moduleDir);
                    }
                    else
                    {
                        module.setExplodedPath(moduleDir);
                        modules.add(module);
                        moduleNameToFile.put(module.getName(), moduleDir);
                    }
                }
                else
                    _log.error("No module class was found for the module '" + moduleDir.getName() + "'");
            }
            catch (Throwable t)
            {
                _log.error("Unable to instantiate module " + moduleDir, t);
                //noinspection ThrowableResultOfMethodCallIgnored
                _moduleFailures.put(moduleDir.getName(), t);
            }
        }
        return modules;
    }


    public File getWebappDir()
    {
        return _webappDir;
    }

    private void verifyJavaVersion() throws ServletException
    {
        if (!SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_6))
            throw new ConfigurationException("Unsupported Java runtime version: " + SystemUtils.JAVA_VERSION + ".  LabKey Server requires Java 1.6 or 1.7.");
    }

    private void removeAPIFiles(Set<File> unclaimedFiles, File webappRoot) throws IOException
    {
        File apiContentFile = new File(webappRoot, "WEB-INF/apiFiles.list");

        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(apiContentFile));
            String line;
            while ((line = reader.readLine()) != null)
            {
                unclaimedFiles.remove(new File(webappRoot, line));
            }
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException e) {} }
        }
    }

    private Set<File> listCurrentFiles(File file) throws IOException
    {
        Set<File> result = new HashSet<File>();
        result.add(file);
        if (file.isDirectory())
        {
            for (File content : file.listFiles())
            {
                result.addAll(listCurrentFiles(content));
            }
        }
        return result;
    }



    // Enumerate each jdbc DataSource in labkey.xml and tell DbScope to initialize them
    private void initializeDataSources() throws ServletException
    {
        _log.debug("Ensuring that all databases specified by datasources in webapp configuration xml are present");

        Map<String, DataSource> dataSources = new TreeMap<String, DataSource>(new Comparator<String>() {
            public int compare(String name1, String name2)
            {
                return name1.compareTo(name2);
            }
        });

        try
        {
            ensureLabKeyDatabase();

            InitialContext ctx = new InitialContext();
            Context envCtx = (Context) ctx.lookup("java:comp/env");
            NamingEnumeration<Binding> iter = envCtx.listBindings("jdbc");

            while (iter.hasMore())
            {
                try
                {
                    Binding o = iter.next();
                    String dsName = o.getName();
                    DataSource ds = (DataSource) o.getObject();
                    dataSources.put(dsName, ds);
                }
                catch (NamingException e)
                {
                    _log.error("DataSources are not properly configured in labkey.xml.", e);
                }
            }
        }
        catch (Exception e)
        {
            throw new ConfigurationException("DataSources are not properly configured in labkey.xml.", e);
        }

        DbScope.initializeScopes(dataSources);
    }


    // Ensures that a labkeyDataSource (or cpasDataSource, for old installations) exists in labkey.xml / cpas.xml and
    // creates the labkey database if it doesn't already exist.
    private void ensureLabKeyDatabase() throws NamingException, ServletException
    {
        InitialContext ctx = new InitialContext();
        Context envCtx = (Context) ctx.lookup("java:comp/env");

        DataSource labkeyDataSource = null;
        String dsName = null;

        for (String name : new String[]{"labkeyDataSource", "cpasDataSource"})
        {
            dsName = name;

            try
            {
                labkeyDataSource = (DataSource)envCtx.lookup("jdbc/" + name);
                break;
            }
            catch (NamingException e)
            {
                String message = e.getMessage();

                // labkeyDataSource is defined but the database doesn't exist. This happens only with the Tomcat JDBC
                // connection pool, which attempts a connection on bind. In this case, we need to use some horrible
                // reflection to get the properties we need to create the database.
                if ((message.contains("FATAL: database") && message.contains("does not exist")) ||
                    (message.contains("Cannot open database") && message.contains("requested by the login. The login failed.")))
                {
                    try
                    {
                        Object namingContext = envCtx.lookup("jdbc");
                        Field bindingsField = namingContext.getClass().getDeclaredField("bindings");
                        bindingsField.setAccessible(true);
                        Map bindings = (Map)bindingsField.get(namingContext);
                        Object namingEntry = bindings.get(name);
                        Field valueField = namingEntry.getClass().getDeclaredField("value");
                        Reference reference = (Reference)valueField.get(namingEntry);

                        String driverClassname = (String)reference.get("driverClassName").getContent();
                        SqlDialect dialect = SqlDialectManager.getFromDriverClassname(dsName, driverClassname);
                        String url = (String)reference.get("url").getContent();
                        String password = (String)reference.get("password").getContent();
                        String username = (String)reference.get("username").getContent();

                        DbScope.createDataBase(dialect, url, username, password);
                    }
                    catch (Exception e2)
                    {
                        throw new ConfigurationException("Failed to retrieve \"" + name + "\" properties from labkey.xml. Try creating the database manually and restarting the server.", e2);
                    }

                    // Try it again
                    labkeyDataSource = (DataSource)envCtx.lookup("jdbc/" + name);
                    break;
                }

                // Ignore any other NamingException... keep trying names until we find one defined.
            }
        }

        if (null == labkeyDataSource)
            throw new ConfigurationException("You must have a DataSource named \"labkeyDataSource\" defined in labkey.xml.");

        DbScope.ensureDataBase(dsName, labkeyDataSource);
    }


    // Update the CoreModule "manually", outside the normal page flow-based process.  We want to be able to change the core tables
    // before we display pages, require login, check permissions, etc.
    // Returns true if core module required upgrading, otherwise false
    private boolean upgradeCoreModule() throws ServletException
    {
        Module coreModule = ModuleLoader.getInstance().getCoreModule();
        if (coreModule == null)
        {
            throw new IllegalStateException("CoreModule does not exist");
        }
        ModuleContext coreContext;

        // If modules table doesn't exist (bootstrap case), then new up a core context
        if (getTableInfoModules().getTableType() == DatabaseTableType.NOT_IN_DB)
            coreContext = new ModuleContext(coreModule);
        else
            coreContext = getModuleContext("Core");

        // Does the core module need to be upgraded?
        if (coreContext.getInstalledVersion() >= coreModule.getVersion())
            return false;

        if (coreContext.isNewInstall())
        {
            _log.debug("Initializing core module to " + coreModule.getFormattedVersion());
        }
        else
        {
            if (coreContext.getInstalledVersion() < EARLIEST_UPGRADE_VERSION)
                throw new ConfigurationException("Can't upgrade from LabKey Server version " + coreContext.getInstalledVersion() + "; installed version must be " + EARLIEST_UPGRADE_VERSION + " or greater.");

            _log.debug("Upgrading core module from " + ModuleContext.formatVersion(coreContext.getInstalledVersion()) + " to " + coreModule.getFormattedVersion());
        }

        contextMap.put(coreModule.getName(), coreContext);

        try
        {
            ModuleUpgrader coreUpgrader = new ModuleUpgrader(Collections.singletonList(coreModule));
            coreUpgrader.upgrade();
        }
        catch (Exception e)
        {
            Throwable cause = e.getCause();

            if (cause instanceof ServletException)
                throw (ServletException)cause;

            throw new ServletException(e);
        }

        return true;
    }


    public Throwable getStartupFailure()
    {
        return _startupFailure;
    }

    public void setStartupFailure(Throwable t)
    {
        if (null == _startupFailure)
            _startupFailure = t;
    }

    public void addModuleFailure(String moduleName, Throwable t)
    {
        //noinspection ThrowableResultOfMethodCallIgnored
        _moduleFailures.put(moduleName, t);
    }

    public Map<String, Throwable> getModuleFailures()
    {
        if (_moduleFailures.size() == 0)
        {
            return Collections.emptyMap();
        }
        else
        {
            return new HashMap<String, Throwable>(_moduleFailures);
        }
    }

    private TableInfo getTableInfoModules()
    {
        return _core.getTableInfoModules();
    }

    public ModuleContext getModuleContext(Module module)
    {
        return contextMap.get(module.getName());
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {
        if (isUpgradeRequired())
        {
            setDeferUsageReport(true);
        }
        else
        {
            ensureStartupComplete();
        }

        filterChain.doFilter(servletRequest, servletResponse);

        ConnectionWrapper.dumpLeaksForThread(Thread.currentThread());
    }

    public boolean isDeferUsageReport()
    {
        return _deferUsageReport;
    }

    public void setDeferUsageReport(boolean defer)
    {
        _deferUsageReport = defer;
    }

    public void runBeforeUpdates()
    {
        synchronized (UPGRADE_LOCK)
        {
            List<Module> modules = getModules();
            ListIterator<Module> iter = modules.listIterator(modules.size());

            while (iter.hasPrevious())
            {
                Module module = iter.previous();
                module.beforeUpdate(getModuleContext(module));
            }
        }
    }

    public void runAfterUpdates()
    {
        synchronized (UPGRADE_LOCK)
        {
            for (Module module : getModules())
                module.afterUpdate(getModuleContext(module));
        }
    }

    // Runs the drop view and create view scripts in every module
    public void recreateViews()
    {
        synchronized (UPGRADE_LOCK)
        {
            runBeforeUpdates();
            runAfterUpdates();
        }
    }

    public boolean isStartupComplete()
    {
        synchronized (STARTUP_LOCK)
        {
            return _startupComplete;
        }
    }

    private void ensureStartupComplete()
    {
        synchronized (STARTUP_LOCK)
        {
            if (_startupComplete)
                return;

            if (isUpgradeRequired())
                throw new IllegalStateException("Can't start modules before upgrade is complete");

            _startupComplete = true;

            for (Module m : _modules)
            {
                try
                {
                    ModuleContext ctx = getModuleContext(m);
                    m.startup(ctx);
                    m.runDeferredUpgradeTasks(ctx);
                    ctx.setModuleState(ModuleLoader.ModuleState.Running);
                }
                catch (Throwable x)
                {
                    setStartupFailure(x);
                    _log.error("Failure starting module: " + m.getName(), x);
                }

                //call the module resource loaders
                for (ModuleResourceLoader resLoader : _resourceLoaders)
                {
                    try
                    {
                        resLoader.loadResources(m, m.getExplodedPath());
                    }
                    catch(Throwable t)
                    {
                        _log.error("Unable to load resources from module " + m.getName() + " using the resource loader " + resLoader.getClass().getName(), t);
                    }
                }
            }

            ContextListener.moduleStartupComplete(_servletContext);
        }
    }


    void saveModuleContext(ModuleContext context)
    {
        try
        {
            ModuleContext stored = getModuleContext(context.getName());
            if (null == stored)
                Table.insert(null, getTableInfoModules(), context);
            else
                Table.update(null, getTableInfoModules(), context, context.getName());
        }
        catch (SQLException x)
        {
            _log.error("Couldn't save module context.", x);
        }
    }


    // Not transacted: SQL Server sp_dropapprole can't be called inside a transaction
    public void removeModule(ModuleContext context)
    {
        DbScope scope = _core.getSchema().getScope();
        SqlDialect dialect = _core.getSqlDialect();

        try
        {
            String moduleName = context.getName();
            _log.info("Deleting module " + moduleName);
            String sql = "DELETE FROM " + _core.getTableInfoSqlScripts() + " WHERE ModuleName = ? AND Filename " + dialect.getCaseInsensitiveLikeOperator() + " ?";

            for (String schema : context.getSchemaList())
            {
                _log.info("Dropping schema " + schema);
                Table.execute(_core.getSchema(), sql, moduleName, schema + "-%");
                scope.getSqlDialect().dropSchema(_core.getSchema(), schema);
            }

            Table.delete(getTableInfoModules(), context.getName());
        }
        catch (SQLException e)
        {
            _log.error("Error attempting to delete module " + context.getName());
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }


    public void startNonCoreUpgrade(User user) throws Exception
    {
        synchronized(UPGRADE_LOCK)
        {
            if (_upgradeState == UpgradeState.UpgradeRequired)
            {
                List<Module> modules = new ArrayList<Module>(getModules());
                modules.remove(ModuleLoader.getInstance().getCoreModule());
                setUpgradeState(UpgradeState.UpgradeInProgress);
                setUpgradeUser(user);

                ModuleUpgrader upgrader = new ModuleUpgrader(modules);
                upgrader.upgradeInBackground(new Runnable(){
                    public void run()
                    {
                        completeUpgrade(true);
                    }
                });
            }
        }
    }


    // Very final step in upgrade process: set the upgrade state to complete and perform any post-upgrade tasks.
    // performedUpgrade is true if any module required upgrading
    private void completeUpgrade(boolean performedUpgrade)
    {
        setUpgradeState(UpgradeState.UpgradeComplete);

        if (performedUpgrade)
        {
            handleUnkownModules();
            updateModuleProperties();
        }
    }


    // Remove all unknown modules that are marked as AutoUninstall
    public void handleUnkownModules()
    {
        for (ModuleContext moduleContext : getUnknownModuleContexts().values())
            if (moduleContext.isAutoUninstall())
                removeModule(moduleContext);
    }


    private void updateModuleProperties()
    {
        for (Module module : getModules())
        {
            try
            {
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("AutoUninstall", module.isAutoUninstall());
                map.put("Schemas", StringUtils.join(module.getSchemaNames(), ','));
                Table.update(getUpgradeUser(), getTableInfoModules(), map, module.getName());
            }
            catch (SQLException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }


    public void setUpgradeUser(User user)
    {
        synchronized(UPGRADE_LOCK)
        {
            assert null == upgradeUser;
            upgradeUser = user;
        }
    }

    public User getUpgradeUser()
    {
        synchronized(UPGRADE_LOCK)
        {
            return upgradeUser;
        }
    }

    public void setUpgradeState(UpgradeState state)
    {
        synchronized(UPGRADE_LOCK)
        {
            _upgradeState = state;
        }
    }

    public boolean isUpgradeRequired()
    {
        synchronized(UPGRADE_LOCK)
        {
            return UpgradeState.UpgradeComplete != _upgradeState;
        }
    }

    public boolean isUpgradeInProgress()
    {
        synchronized(UPGRADE_LOCK)
        {
            return UpgradeState.UpgradeInProgress == _upgradeState;
        }
    }

    // Did this server start up with no modules installed?  If so, it's a new install.  This lets us tailor the
    // module upgrade UI to "install" or "upgrade," as appropriate.
    public boolean isNewInstall()
    {
        return _newInstall;
    }

    public void destroy()
    {
        // in the case of a startup failure, _modules may be null.
        // we want to allow a context reload to succeed in this case,
        // since the reload may contain the code change to fix the problem
        if (_modules != null)
        {
            for (Module module : _modules)
            {
                module.destroy();
            }
        }
    }

    public Module getModule(String name)
    {
        return moduleMap.get(name);
    }

    public Module getModule(Class<? extends Module> moduleClass)
    {
        return moduleClassMap.get(moduleClass);
    }

    public Module getCoreModule()
    {
        return getModule(DefaultModule.CORE_MODULE_NAME);
    }

    public List<Module> getModules()
    {
        return _modules;
    }

    // TODO: Move to LoginController, only place that uses this now
    public boolean isAdminOnlyMode()
    {
        return AppProps.getInstance().isUserRequestedAdminOnlyMode() || (isUpgradeRequired() && !UserManager.hasNoUsers());
    }

    public String getAdminOnlyMessage()
    {
        if (isUpgradeRequired() && !UserManager.hasNoUsers())
        {
            return "This site is currently being upgraded to a new version of LabKey Server.";
        }
        return AppProps.getInstance().getAdminOnlyMessage();
    }

    // CONSIDER: ModuleUtil.java
    public Collection<String> getModuleSummaries(Container c)
    {
        LinkedList<String> list = new LinkedList<String>();
        for (Module m : _modules)
        {
            Collection<String> messages = m.getSummary(c);
            if (null != messages)
                list.addAll(messages);
        }
        return list;
    }

    public void initPageFlowToModule()
    {
        synchronized(_controllerNameToModule)
        {
            if (!_controllerNameToModule.isEmpty())
                return;
            List<Module> allModules = ModuleLoader.getInstance().getModules();
            for (Module module : allModules)
            {
                TreeSet<String> set = new CaseInsensitiveTreeSet();

                for (Map.Entry<String, Class<? extends Controller>> entry : module.getPageFlowNameToClass().entrySet())
                {
                    String key = entry.getKey();
                    if (!set.add(key))
                        continue;   // Avoid duplicate work

                    _controllerNameToModule.put(key, module);
                    _controllerNameToModule.put(key.toLowerCase(), module);

                    Class clazz = entry.getValue();
                    _packageToPageFlowURL.put(clazz.getPackage(), key);
                    for (Class innerClass : clazz.getClasses())
                    {
                        for (Class inter : innerClass.getInterfaces())
                        {
                            Class[] supr = inter.getInterfaces();
                            if (supr != null && supr.length == 1 && UrlProvider.class.equals(supr[0]))
                                _urlProviderToImpl.put(inter, innerClass);
                        }
                    }
                }
            }
        }
    }

    public Module getModuleForController(String controllerName)
    {
        synchronized(_controllerNameToModule)
        {
            Module module = _controllerNameToModule.get(controllerName);
            if (null != module)
                return module;

            int i = controllerName.indexOf('-');
            if (-1 == i)
                return null;

            String prefix = controllerName.substring(0,i);
            module = _controllerNameToModule.get(prefix);
            if (null != module)
                _controllerNameToModule.put(controllerName, module);
            return module;
        }
    }


    public Module getModuleForSchemaName(String schemaName)
    {
        synchronized(_schemaNameToModule)
        {
            if (_schemaNameToModule.isEmpty())
            {
                for (Module module : getModules())
                {
                    for (String name : module.getSchemaNames())
                        _schemaNameToModule.put(name, module);
                }
            }
        }

        return _schemaNameToModule.get(schemaName);
    }

    public <P extends UrlProvider> P getUrlProvider(Class<P> inter)
    {
        Class<? extends UrlProvider> clazz = _urlProviderToImpl.get(inter);

        if (clazz == null)
        {
            throw new IllegalArgumentException("Interface " + inter.getName() + " not registered.");
        }

        try
        {
            P impl = (P) clazz.newInstance();
            return impl;
        }
        catch (InstantiationException e)
        {
            throw new RuntimeException("Failed to instantiate provider class " + clazz.getName() + " for " + inter.getName(), e);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException("Illegal access of provider class " + clazz.getName() + " for " + inter.getName(), e);
        }
    }


    public void registerResourcePrefix(String prefix, ResourceFinder finder)
    {
        if (null == prefix)
            return;

        synchronized(_resourceFinders)
        {
            Collection<ResourceFinder> col = _resourceFinders.get(prefix);

            if (null == col)
            {
                col = new ArrayList<ResourceFinder>();
                _resourceFinders.put(prefix, col);
            }

            col.add(finder);
        }
    }

    // TODO: Remove once we've investigated #16113
    public void logResourceFinders()
    {
        _log.info(_resourceFinders);
    }

    public Collection<ResourceFinder> getResourceFindersForPath(String path)
    {
        //NOTE: jasper encodes underscores in JSPs, so decode this here
        path = path.replaceAll("_005f", "_");

        boolean firstMultiple = true;
        Collection<ResourceFinder> finders = null;
        for (Map.Entry<String, Collection<ResourceFinder>> e : _resourceFinders.entrySet())
        {
            if (path.startsWith(e.getKey()))
            {
                if (finders == null)
                {
                    finders = e.getValue();
                }
                else
                {
                    // Combine all ResourceFinders that match the path prefix.
                    if (firstMultiple)
                    {
                        // First time we find multiple matches, create a new LinkedList.
                        // On subsequent matches we just append to the existing LinkedList.
                        finders = new LinkedList<ResourceFinder>(finders);
                        firstMultiple = false;
                    }
                    finders.addAll(e.getValue());
                }
            }
        }

        return finders;
    }

    public Resource getResource(Path path)
    {
        for (Module m : _modules)
        {
            Resource r = m.getModuleResource(path);
            if (r != null && r.exists())
                return r;
        }

        return null;
    }

    public Resource getResource(Module module, Path path)
    {
        return module.getModuleResource(path);
    }

    public Module getCurrentModule()
    {
        return ModuleLoader.getInstance().getModuleForController(HttpView.getRootContext().getActionURL().getController());
    }

    public FolderType getFolderType(String name)
    {
        synchronized (_folderTypes)
        {
            FolderType result = _folderTypes.get(name);
            if (result != null)
            {
                return result;
            }

            // Check if it's a legacy name for an existing folder type
            for (FolderType folderType : _folderTypes.values())
            {
                if (folderType.getLegacyNames().contains(name))
                {
                    return folderType;
                }
            }
            return null;
        }
    }

    /** Remove the named folder type from the list of known options */
    public void unregisterFolderType(String name)
    {
        synchronized (_folderTypes)
        {
            _folderTypes.remove(name);
        }
    }

    public void registerFolderType(Module sourceModule, FolderType folderType)
    {
        synchronized (_folderTypes)
        {
            if (_folderTypes.containsKey(folderType.getName()))
            {
                String msg = "Unable to register folder type " + folderType.getName() + " from module " + sourceModule.getName() +
                ".  A folder type with this name has already been registered ";
                Throwable ex = new IllegalStateException(msg);
                _log.error(msg, ex);
                addModuleFailure(sourceModule.getName(), ex);
            }
            else
                _folderTypes.put(folderType.getName(), folderType);
        }
    }

    /** @return an unmodifiable collection of all registered folder types */
    public Collection<FolderType> getFolderTypes()
    {
        synchronized (_folderTypes)
        {
            return Collections.unmodifiableCollection(new ArrayList<FolderType>(_folderTypes.values()));
        }
    }

    public void registerResourceLoader(ModuleResourceLoader loader)
    {
        registerResourceLoaders(Collections.singleton(loader));
    }

    public void registerResourceLoaders(Set<ModuleResourceLoader> loaders)
    {
        synchronized (_resourceLoaders)
        {
            _resourceLoaders.addAll(loaders);
        }
    }


    public ModuleContext getModuleContext(String name)
    {
        try
        {
            TableInfo modules = getTableInfoModules();
            SQLFragment sql = new SQLFragment("SELECT * FROM " + modules.getSelectName() + " WHERE Name=?", name);
            ModuleContext[] contexts = Table.executeQuery(modules.getSchema(), sql, ModuleContext.class);
            return contexts == null || contexts.length == 0 ? null : contexts[0];
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public Collection<ModuleContext> getAllModuleContexts()
    {
        return new TableSelector(getTableInfoModules()).getCollection(ModuleContext.class);
    }


    public Map<String, ModuleContext> getUnknownModuleContexts()
    {
        Map<String, ModuleContext> unknownContexts = new HashMap<String, ModuleContext>();

        for (ModuleContext moduleContext : getAllModuleContexts())
        {
            String name = moduleContext.getName();
            Module module = getModule(moduleContext.getName());

            if (null == module || !name.equals(module.getName()))
                unknownContexts.put(name, moduleContext);
        }

        return unknownContexts;
    }
}
