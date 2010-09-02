/*
 * Copyright (c) 2005-2010 Fred Hutchinson Cancer Research Center
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
package org.labkey.announcements;

import org.apache.log4j.Logger;
import org.labkey.announcements.api.AnnouncementServiceImpl;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.DailyDigest;
import org.labkey.announcements.model.DiscussionServiceImpl;
import org.labkey.announcements.model.DiscussionWebPartFactory;
import org.labkey.announcements.model.SecureMessageBoardReadPermission;
import org.labkey.announcements.model.SecureMessageBoardRespondPermission;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.notification.EmailService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: migra
 * Date: Jul 13, 2005
 * Time: 3:05:50 PM
 * <p/>
 * NOTE: Wiki handles some of the shared Communications module stuff.
 * e.g. it handles ContainerListener and Attachments
 * <p/>
 * TODO: merge announcementModel & wiki into one module?
 */
public class AnnouncementModule extends DefaultModule implements SearchService.DocumentProvider
{
    public static final String WEB_PART_NAME = "Messages";

    private static Logger _log = Logger.getLogger(AnnouncementModule.class);

    public String getName()
    {
        return "Announcements";
    }

    public double getVersion()
    {
        return 10.20;
    }

    protected void init()
    {
        addController("announcements", AnnouncementsController.class);
        AnnouncementService.setInstance(new AnnouncementServiceImpl());
        AnnouncementSchema.register();
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(new AlwaysAvailableWebPartFactory(WEB_PART_NAME)
            {
                public WebPartView getWebPartView(ViewContext parentCtx, Portal.WebPart webPart)
                {
                    try
                    {
                        return new AnnouncementsController.AnnouncementWebPart(parentCtx);
                    }
                    catch(Exception e)
                    {
                        throw new RuntimeException(e); // TODO: getWebPartView should throw Exception?
                    }
                }
            },
            new AlwaysAvailableWebPartFactory(WEB_PART_NAME + " List")
            {
                public WebPartView getWebPartView(ViewContext parentCtx, Portal.WebPart webPart)
                {
                    try
                    {
                        return new AnnouncementsController.AnnouncementListWebPart(parentCtx);
                    }
                    catch (ServletException e)
                    {
                        throw new RuntimeException(e); // TODO: getWebPartView should throw Exception?
                    }
                }
            },
            new DiscussionWebPartFactory()));
    }

    public boolean hasScripts()
    {
        return true;
    }

    public String getTabName(ViewContext context)
    {
        return "Messages";
    }


    public void startup(ModuleContext moduleContext)
    {
        DiscussionService.register(new DiscussionServiceImpl());

        AnnouncementListener listener = new AnnouncementListener();
        ContainerManager.addContainerListener(listener);
        UserManager.addUserListener(listener);
        SecurityManager.addGroupListener(listener);
        AuditLogService.get().addAuditViewFactory(MessageAuditViewFactory.getInstance());
        ServiceRegistry.get().registerService(EmailService.I.class, new EmailServiceImpl());

        // Editors can read and respond to secure message boards
        RoleManager.registerPermission(new SecureMessageBoardReadPermission());
        RoleManager.registerPermission(new SecureMessageBoardRespondPermission());
        Role editor = RoleManager.getRole(EditorRole.class);
        editor.addPermission(SecureMessageBoardReadPermission.class);
        editor.addPermission(SecureMessageBoardRespondPermission.class);

        DailyDigest.setTimer();

        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null != ss)
        {
            ss.addSearchCategory(AnnouncementManager.searchCategory);
            ss.addDocumentProvider(this);
        }
    }
    

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
        super.afterUpdate(moduleContext);

        if (moduleContext.isNewInstall())
            bootstrap(moduleContext);
    }

    private void bootstrap(ModuleContext moduleContext)
    {
        try
        {
            Container supportContainer = ContainerManager.getDefaultSupportContainer();
            addWebPart(WEB_PART_NAME, supportContainer, null);

            User installerUser = moduleContext.getUpgradeUser();

            if (installerUser != null && !installerUser.isGuest())
                AnnouncementManager.saveEmailPreference(installerUser, supportContainer, AnnouncementManager.EMAIL_PREFERENCE_ALL);
        }
        catch (SQLException e)
        {
            _log.error("Unable to set up support folder", e);
        }
    }

    @Override
    public Set<Class> getJUnitTests()
    {
        return new HashSet<Class>(Arrays.asList(
            AnnouncementManager.TestCase.class));
    }

    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(CommSchema.getInstance().getSchema());
    }

    @Override
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(CommSchema.getInstance().getSchemaName());
    }

    public Collection<String> getSummary(Container c)
    {
        List<String> list = new ArrayList<String>(1);
        try
        {
            long count = AnnouncementManager.getMessageCount(c);
            if (count > 0)
                list.add("" + count + " " + (count > 1 ? "Messages/Responses" : "Message"));
        }
        catch (SQLException x)
        {
            list.add(x.toString());
        }
        return list;
    }


    public void enumerateDocuments(final SearchService.IndexTask task, final Container c, final Date modifiedSince)
    {
        Runnable r = new Runnable()
            {
                public void run()
                {
                    AnnouncementManager.indexMessages(task, c, modifiedSince);
                }
            };
        task.addRunnable(r, SearchService.PRIORITY.bulk);
    }

    public void indexDeleted() throws SQLException
    {
        Table.execute(CommSchema.getInstance().getSchema(), new SQLFragment("UPDATE comm.announcements SET LastIndexed=NULL"));
    }
}
