package org.labkey.api.module;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.Portal;

import java.util.Collections;
import java.util.Set;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Aug 3, 2006
 * Time: 8:49:19 PM
 *
 * Interface to define look & feel of a folder. Folders with a folder type other than FolderType.NONE will have a single
 * "tab" owned by the FolderType. This "dashboard" drill down to arbitrary URLs without changing it's look & feel.
 */
public interface FolderType
{
    public static final FolderType NONE = new FolderTypeNone();

    /**
     * Configure the container with whatever active modules and web parts are required for this folder type.
     * Convention is to NOT remove web parts already in the folder.
     */
    public void configureContainer(Container c);

    /**
     * This FolderType is being *removed* as the owner of the container. Clean up anything that you
     * might want. Typically this involves turning off the *permanent* bit for the web parts this
     * FolderType may have set.
     */
    public void unconfigureContainer(Container c);

    /**
     * Name of this folder type. Used internally to track the folder type. Must be consistent across versions.
     * @return name
     */
    public String getName();

    /**
     * Description of this folder type. Used to let users know what to expect.
     */
    public String getDescription();

    /**
     * Label of this folder type. This is what the user sees. Should be a short name such as "MS2" not "MS2 Folder"
     * @return User visible label
     */
    public String getLabel();

    /**
     * URL to start at when navigating to this folder. This is often the same as getTabURL for the portal module, or
     * getTabURL for the "owner" module, but could be any URL to an appropriate starting page.
     * @param c
     * @param u
     * @return URL for "dashboard" of this
     */
    public ActionURL getStartURL(Container c, User u);

    /**
     * Label of the start page. Typically getLabel() + " Dashboard"
     * @return Label of the start page
     */
    public String getStartPageLabel(ViewContext ctx);

    /**
     * Module that *owns* this folder. Used in constructing navigation paths. If current URL's module is NOT part of the owning module
     * extra links will be added to automatically generated nav path
     * @return Owning module. May be null
     */
    Module getDefaultModule();

    /**
     * Return all modules required by this foldertype, INCLUDING the default module if any.
     * @return set
     */
    public Set<Module> getActiveModules();

    /**
     * @return all web parts that must be included in the portal page.
     */
    public List<Portal.WebPart> getRequiredWebParts();

    /**
     * @return all web parts that are recommended for inclusion in the portal page.
     */
    public List<Portal.WebPart> getPreferredWebParts();
    
    /**
     * Folder type that results in an old style "tabbed" folder.
     */
    static class FolderTypeNone implements FolderType
    {
        private FolderTypeNone(){}
        public void configureContainer(Container c) {  }
        public void unconfigureContainer(Container c) {  }
        public String getName() { return "None"; }

        public String getDescription()
        {
            return "Create a tab for each LabKey module you select. Used in older LabKey installations. Note that any LabKey module can also be used from any folder type    via Customize Folder.";
        }

        public List<Portal.WebPart> getRequiredWebParts()
        {
            return Collections.emptyList();
        }

        public List<Portal.WebPart> getPreferredWebParts()
        {
            return Collections.emptyList();
        }

        public String getLabel() { return "Custom"; }
        public Module getDefaultModule() { return null; }
        public Set<Module> getActiveModules() { return Collections.EMPTY_SET; }
        public String getStartPageLabel(ViewContext ctx) { return null; }
        public ActionURL getStartURL(Container c, User u)
        {
            if (null == c)
                return new ActionURL("Project", "start", ContainerManager.getHomeContainer());
            if (null == c.getDefaultModule())
                return new ActionURL("Project", "start", c);
            return c.getDefaultModule().getTabURL(c, u);
        }
    }

}
