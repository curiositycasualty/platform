package org.labkey.api.view.template;

import org.apache.commons.collections15.map.AbstractReferenceMap;
import org.apache.commons.collections15.map.ReferenceIdentityMap;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.clientLibrary.xml.DependenciesType;
import org.labkey.clientLibrary.xml.LibrariesDocument;
import org.labkey.clientLibrary.xml.LibraryType;
import org.labkey.clientLibrary.xml.ModeTypeEnum;
import org.labkey.clientLibrary.xml.RequiredModuleType;
import org.labkey.clientLibrary.xml.ScriptType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;


/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 6/13/12
 * Time: 5:25 PM
 */
public class ClientDependency
{
    private static Logger _log = Logger.getLogger(ClientDependency.class);
    private static final Map<String, ClientDependency> _references = new ReferenceIdentityMap<String, ClientDependency>(AbstractReferenceMap.WEAK, AbstractReferenceMap.WEAK, true);

    public static enum TYPE {
        js(".js"),
        css(".css"),
        sass(".sass"),
        jsb2(".jsb2"),
        context(".context"),
        lib(".lib.xml");

        private TYPE(String extension)
        {
            _extension = extension;
            _fileType = new FileType(extension);
        }

        public static TYPE fromPath(Path p)
        {
            for (TYPE t : TYPE.values())
            {
                if (t._fileType.isType(p.toString()))
                    return t;
            }
            return null;
        }

        public String getExtension()
        {
            return _extension;
        }

        private String _extension;
        private FileType _fileType;
    };

    private LinkedHashSet<ClientDependency> _children = new LinkedHashSet<ClientDependency>();
    private HashSet<Module> _moduleContexts = new HashSet<Module>();
    private String _prodModePath;
    private String _devModePath;
    private boolean _compileInProductionMode = true;

    private Path _filePath;
    private Resource _resource;
    private TYPE _primaryType;
    private ModeTypeEnum.Enum _mode = ModeTypeEnum.BOTH;

    private ClientDependency(Path filePath, ModeTypeEnum.Enum mode)
    {
        if (mode != null)
            _mode = mode;

        _filePath = filePath;

        _primaryType = TYPE.fromPath(_filePath);
        if (_primaryType == null)
        {
            _log.warn("Script type not recognized: " + filePath);
            return;
        }

        if (TYPE.context.equals(_primaryType))
        {
            String moduleName = FileUtil.getBaseName(_filePath.getName());
            Module m = ModuleLoader.getInstance().getModule(moduleName);
            if (m == null)
            {
                //TODO: throw exception??
                _log.error("Module not found, skipping: " + moduleName);
            }
            else
                _moduleContexts.add(m);
        }
        else
        {
            WebdavResource r = WebdavService.get().getRootResolver().lookup(_filePath);
            //TODO: can we connect this resource back to a module, and load that module's context by default?--

            if(r == null || !r.exists())
            {
                // Allows you to run in dev mode without having the concatenated scripts built
                if (!AppProps.getInstance().isDevMode() || !_mode.equals(ModeTypeEnum.PRODUCTION))
                    _log.error("Script file not found, skipping: " + filePath);
            }
            else
            {
                _resource = r;
                _filePath = r.getPath(); //use canonical, case-sensitive name
                processScript(_filePath);
            }
        }
    }

    private ClientDependency(Module m)
    {
        _moduleContexts.add(m);
        _primaryType = TYPE.context;
    }

    public static ClientDependency fromModuleName(String mn)
    {
        Module m = ModuleLoader.getInstance().getModule(mn);
        if (m == null)
        {
            _log.error("Module '" + mn + "' not found, unable to create client resource");
            return null;
        }

        return ClientDependency.fromModule(m);
    }

    public static ClientDependency fromModule(Module m)
    {
        ClientDependency cd = new ClientDependency(m);
        cd._children.addAll(m.getClientDependencies());
        return cd;
    }

    public static ClientDependency fromFilePath(String path)
    {
        return ClientDependency.fromFilePath(path, ModeTypeEnum.BOTH);
    }

    public static ClientDependency fromFilePath(String path, ModeTypeEnum.Enum mode)
    {
        path = path.replaceAll("^/", "");

        //as a convenience, if no extension provided, assume it's a library
        if (StringUtils.isEmpty(FileUtil.getExtension(path)))
            path = path + TYPE.lib.getExtension();

        Path filePath = Path.parse(path).normalize();

        String key = getCacheKey(filePath, mode);

        //TODO: test this
        if (!AppProps.getInstance().isDevMode())
        {
            ClientDependency cached = (ClientDependency)CacheManager.getSharedCache().get(key);
            if (cached != null)
                return cached;
        }

        ClientDependency cr = new ClientDependency(filePath, mode);
        CacheManager.getSharedCache().put(key, cr);
        return cr;
    }

    private static String getCacheKey(Path filePath, ModeTypeEnum.Enum mode)
    {
        return ClientDependency.class.getName() + "|" + filePath.toString().toLowerCase() + "|" + mode.toString();
    }

    private void processScript(Path filePath)
    {
        TYPE type = TYPE.fromPath(filePath);
        if(type == null)
        {
            _log.warn("Invalid file type for resource: " + filePath);
            return;
        }

        if (TYPE.lib.equals(type))
        {
            processLib(filePath);
        }
        else if (TYPE.jsb2.equals(type))
        {
            //NYI
            //processJsb(filePath);
        }
        else if (TYPE.sass.equals(type))
        {
            //NYI
        }
        else
        {
            if(!_mode.equals(ModeTypeEnum.PRODUCTION))
                _devModePath = filePath.toString();

            if(!_mode.equals(ModeTypeEnum.DEV))
                _prodModePath = filePath.toString();
        }
    }

    private void processLib(Path filePath)
    {
        try
        {
            XmlOptions xmlOptions = new XmlOptions();
            Map<String,String> namespaceMap = new HashMap<String,String>();
            namespaceMap.put("", "http://labkey.org/clientLibrary/xml/");
            xmlOptions.setLoadSubstituteNamespaces(namespaceMap);

            LibrariesDocument libDoc = LibrariesDocument.Factory.parse(_resource.getInputStream(), xmlOptions);
            boolean hasJsToCompile = false;
            boolean hasCssToCompile = false;
            if (libDoc != null && libDoc.getLibraries() != null)
            {
                LibraryType l = libDoc.getLibraries().getLibrary();

                if(l.isSetCompileInProductionMode())
                    _compileInProductionMode = l.getCompileInProductionMode();

                //dependencies first
                DependenciesType dependencies = libDoc.getLibraries().getDependencies();
                if (dependencies != null)
                {
                    for (ScriptType s : dependencies.getDependencyArray())
                    {
                        ModeTypeEnum.Enum mode = s.isSetMode() ? s.getMode() : ModeTypeEnum.BOTH;
                        ClientDependency cr = ClientDependency.fromFilePath(s.getPath(), mode);
                        _children.add(cr);
                    }
                }

                //module contexts
                if (libDoc.getLibraries() != null && libDoc.getLibraries().isSetRequiredModuleContext())
                {
                    for (RequiredModuleType mt : libDoc.getLibraries().getRequiredModuleContext().getModuleArray())
                    {
                        Module m = ModuleLoader.getInstance().getModule(mt.getName());
                        if (m == null)
                            _log.error("Unable to find module: '" + mt.getName() + "' in library " + _filePath.getName());
                        else
                            _moduleContexts.add(m);
                    }
                }

                for (ScriptType s : l.getScriptArray())
                {
                    ModeTypeEnum.Enum mode = s.isSetMode() ? s.getMode() :
                        _compileInProductionMode ? ModeTypeEnum.DEV : ModeTypeEnum.BOTH;
                    ClientDependency cr = ClientDependency.fromFilePath(s.getPath(), mode);

                    if(!TYPE.lib.equals(cr.getPrimaryType()))
                        _children.add(cr);
                    else {
                        _log.warn("Libraries cannot include other libraries: " + _filePath);
                    }

                    if (_compileInProductionMode && mode != ModeTypeEnum.PRODUCTION)
                    {
                        if(TYPE.js.equals(cr.getPrimaryType()))
                            hasJsToCompile = true;
                        if(TYPE.css.equals(cr.getPrimaryType()))
                            hasCssToCompile = true;
                    }
                }

                //add paths to the compiled scripts we expect to have created in the build.  these are production mode only
                if (hasJsToCompile)
                {
                    ClientDependency c = ClientDependency.fromFilePath(filePath.toString().replaceAll(TYPE.lib.getExtension() + "$", ".min" + TYPE.js.getExtension()), ModeTypeEnum.PRODUCTION);
                    _children.add(c);
                }
                if (hasCssToCompile)
                {
                    ClientDependency c = ClientDependency.fromFilePath(filePath.toString().replaceAll(TYPE.lib.getExtension() + "$", ".min" + TYPE.css.getExtension()), ModeTypeEnum.PRODUCTION);
                    _children.add(c);
                }
            }
        }
        catch(Exception e)
        {
            _log.error("Invalid client library XML file: " + _filePath + ". " + e.getMessage());
            return;
        }
    }

    public void processJsb(Path filePath)
    {
        try
        {
            File jsonFile = new File(ModuleLoader.getInstance().getWebappDir(), _resource.getPath().toString());
            String json = FileUtils.readFileToString(jsonFile);
            JSONObject o = new JSONObject(json);
            JSONArray pkgs = o.getJSONArray("pkgs");

            if (pkgs != null)
            {
                for(int i = 0 ; i < pkgs.length(); i++)
                {
                    JSONObject pkg = (JSONObject)pkgs.get(i);
                    if (pkg.getString("name").equals("Ext Core") || pkg.getString("name").equals("Ext Base"))
                    {
                        JSONArray files = pkg.getJSONArray("fileIncludes");
                        for(int j = 0 ; i < files.length(); i++)
                        {
                            JSONObject fileInfo = (JSONObject)pkgs.get(j);
                            File script = new File(jsonFile.getParentFile(), fileInfo.getString("path") + fileInfo.getString("text"));
                            if (script.exists())
                            {
                                ClientDependency d = ClientDependency.fromFilePath(script.getPath(), ModeTypeEnum.DEV);
                                _children.add(d);
                            }
                        }
                    }
                }
            }
        }
        catch (IOException e)
        {
            _log.error("Unable to read file: " + _resource.getPath());
        }
    }

    public TYPE getPrimaryType()
    {
        return _primaryType;
    }

    private LinkedHashSet<String> getProductionScripts(TYPE type)
    {
        LinkedHashSet<String> scripts = new LinkedHashSet<String>();
        if (_primaryType.equals(type) && _prodModePath != null)
            scripts.add(_prodModePath);

        for (ClientDependency r : _children)
            scripts.addAll(r.getProductionScripts(type));

        return scripts;
    }

    private LinkedHashSet<String> getDevModeScripts(TYPE type)
    {
        LinkedHashSet<String> scripts = new LinkedHashSet<String>();
        if (_primaryType.equals(type) && _devModePath != null)
            scripts.add(_devModePath);

        for (ClientDependency r : _children)
            scripts.addAll(r.getDevModeScripts(type));

        return scripts;
    }

    public LinkedHashSet<String> getCssPaths(boolean devMode)
    {
        if (devMode)
            return getDevModeScripts(TYPE.css);
        else
            return getProductionScripts(TYPE.css);
    }

    public LinkedHashSet<String> getJsPaths(boolean devMode)
    {
        if (devMode)
            return getDevModeScripts(TYPE.js);
        else
            return getProductionScripts(TYPE.js);
    }

    public Set<Module> getRequiredModuleContexts()
    {
        HashSet<Module> modules = new HashSet<Module>();
        modules.addAll(_moduleContexts);

        for (ClientDependency r : _children)
            modules.addAll(r.getRequiredModuleContexts());

        return modules;
    }

    public ModeTypeEnum.Enum getMode()
    {
        return _mode;
    }

    public void setMode(ModeTypeEnum.Enum mode)
    {
        _mode = mode;
    }
}
