/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
package org.labkey.issue.model;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.*;
import org.labkey.api.view.HttpView;
import org.labkey.issue.IssuesController;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


/**
 * User: mbellew
 * Date: Mar 11, 2005
 * Time: 11:07:27 AM
 */
public class IssueManager
{
    // UNDONE: Keywords, Summary, etc.

    private static IssuesSchema _issuesSchema = IssuesSchema.getInstance();
    
    private static TableInfo _tinfoIssues = _issuesSchema.getTableInfoIssues();
    private static TableInfo _tinfoIssueKeywords = _issuesSchema.getTableInfoIssueKeywords();
    private static TableInfo _tinfoComments = _issuesSchema.getTableInfoComments();
    private static Logger _log = Logger.getLogger(IssueManager.class);

    public static final int NOTIFY_ASSIGNEDTO_OPEN = 1;     // if a bug is assigned to me
    public static final int NOTIFY_ASSIGNEDTO_UPDATE = 2;   // if a bug assigned to me is modified
    public static final int NOTIFY_CREATED_UPDATE = 4;      // if a bug I created is modified
    public static final int NOTIFY_SELF_SPAM = 8;           // spam me when I enter/edit a bug
    public static final int DEFAULT_EMAIL_PREFS = NOTIFY_ASSIGNEDTO_OPEN | NOTIFY_ASSIGNEDTO_UPDATE | NOTIFY_CREATED_UPDATE;

    private static final String ISSUES_PREF_MAP = "IssuesPreferencesMap";
    private static final String ISSUES_REQUIRED_FIELDS = "IssuesRequiredFields";

//    private static SessionFactory sessionFactory = DataSourceSessionFactory.create(getSchema(),
//            new Class[]
//                {
//                Issues.model.Issue.class,
//                Issues.model.Issue.Comment.class
//                },
//            CacheMode.NORMAL);


    private IssueManager()
    {
    }

    public static Object openSession()
    {
        return null; // sessionFactory.openSession();
    }


    public static Issue getIssue(Object s, Container c, int issueId) throws SQLException
    {
        Issue[] issues = Table.selectForDisplay(
                _tinfoIssues,
                Table.ALL_COLUMNS,
                new SimpleFilter("issueId", new Integer(issueId))
                        .addCondition("container", c.getId()),
                null, Issue.class);
        if (null == issues || issues.length < 1)
            return null;
        Issue issue = issues[0];

        Issue.Comment[] comments = Table.select(
                _tinfoComments,
                Table.ALL_COLUMNS,
                new SimpleFilter("issueId", new Integer(issue.getIssueId())),
                new Sort("CommentId"), Issue.Comment.class);
        issue.setComments(new ArrayList<Issue.Comment>(Arrays.asList(comments)));
        return issue;

/*        Session session = null == s ? openSession() : s;
        try
            {
            if (true)
                {
                Criteria query = session.createCriteria(Issue.class);
                //query.add(Expression.eq("containerId", c.getId()));
                query.add(Expression.eq("issueId", new Integer(issueId)));
                Issue issue = (Issue) query.uniqueResult();
                if (issue == null)
                    return null;
                if (!c.getId().equals(issue.getContainer()))
                    return null;
                return issue;
                }
            else
                {
                Issue issue = (Issue)session.load(Issue.class, issueId);
                if (issue == null)
                    return null;
                if (!c.getId().equals(issue.getContainer()))
                    return null;
                return issue;
                }
            }
        catch (ObjectNotFoundException x)
            {
            return null;
            }
        finally
            {
            if (session != s)
                session.close();
            }
*/        }


    public static void saveIssue(Object s, User user, Container c, Issue issue) throws SQLException
    {

        // HACK - gets around npe, but violates contract of Issue
        if (issue.assignedTo == null)
            issue.assignedTo = new Integer(0);

        try
        {
            if (issue.issueId == 0)
            {
                issue.beforeInsert(user, c.getId());
                Table.insert(user, _tinfoIssues, issue);
            }
            else
            {
                issue.beforeUpdate(user);
                Table.update(user, _tinfoIssues, issue, new Integer(issue.getIssueId()), null);
            }
            saveComments(user, issue);
        }
        finally
        {
        }
    }


    protected static void saveComments(User user, Issue issue) throws SQLException
    {
        Collection<Issue.Comment> comments = issue._added;
        if (null == comments)
            return;
        for (Issue.Comment comment : comments)
        {
            HashMap<String, Object> m = new HashMap<String, Object>();
            m.put("issueId", new Integer(issue.getIssueId()));
            m.put("comment", comment.getComment());
            m.put("entityId", comment.getEntityId());
            Table.insert(user, _tinfoComments, m);
        }
        issue._added = null;
    }

    /*
    public static void saveIssue(Session s, User user, Container c, Issue issue)
        {
        Session session = null == s ? openSession() : s;
        Transaction t = session.beginTransaction();

        try
            {
            issue.beforeSave(user, c);
            session.saveOrUpdate(issue);
            t.commit();
            t = null;
            }
        finally
            {
            if (null != t)
                t.rollback();
            if (session != s)
                session.close();
            }
        }
    */


    public static void addKeyword(Container c, int type, String keyword)
    {
        try
        {
            Table.execute(_issuesSchema.getSchema(),
                    "INSERT INTO " + _tinfoIssueKeywords + " (Container, Type, Keyword) VALUES (?, ?, ?)",
                    new Object[]{c.getId(), new Integer(type), keyword});
            DbCache.clear(_tinfoIssueKeywords);
        }
        catch (SQLException x)
        {
            _log.error(x);
            //probably primary key violation
        }
    }


    public static class Keyword
    {
        String _keyword;
        boolean _default = false;

        public boolean isDefault()
        {
            return _default;
        }

        public void setDefault(boolean def)
        {
            _default = def;
        }

        public String getKeyword()
        {
            return _keyword;
        }

        public void setKeyword(String keyword)
        {
            _keyword = keyword;
        }
    }


    public static Keyword[] getKeywords(String container, int type)
    {
        Keyword[] keywords = null;
        SimpleFilter filter = new SimpleFilter("Container", container).addCondition("Type", type);
        Sort sort = new Sort("Keyword");

        try
        {
            keywords = Table.select(_tinfoIssueKeywords, PageFlowUtil.set("Keyword", "Default"), filter, sort, Keyword.class);
        }
        catch (SQLException e)
        {
            _log.error(e);
        }

        return keywords;
    }


    public static Map<Integer, String> getAllDefaults(Container container) throws SQLException
    {
        ResultSet rs = null;

        try
        {
            SimpleFilter filter = new SimpleFilter("container", container.getId()).addCondition("Default", true);
            rs = Table.select(_tinfoIssueKeywords, PageFlowUtil.set("Type", "Keyword"), filter, null);

            Map<Integer, String> defaults = new HashMap<Integer, String>(5);

            while (rs.next())
                defaults.put(rs.getInt(1), rs.getString(2));

            return defaults;
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    // Clear old default value and set new one
    public static void setKeywordDefault(Container c, int type, String keyword) throws SQLException
    {
        clearKeywordDefault(c, type);

        String selectName = _tinfoIssueKeywords.getColumn("Default").getSelectName();

        Table.execute(_issuesSchema.getSchema(),
                "UPDATE " + _tinfoIssueKeywords + " SET " + selectName + "=? WHERE Container=? AND Type=? AND Keyword=?",
                new Object[]{Boolean.TRUE, c.getId(), type, keyword});
    }


    // Clear existing default value
    public static void clearKeywordDefault(Container c, int type) throws SQLException
    {
        String selectName = _tinfoIssueKeywords.getColumn("Default").getSelectName();

        Table.execute(_issuesSchema.getSchema(),
                "UPDATE " + _tinfoIssueKeywords + " SET " + selectName + "=? WHERE Container=? AND Type=?",
                new Object[]{Boolean.FALSE, c.getId(), type});
    }


    public static void deleteKeyword(Container c, int type, String keyword)
    {
        try
        {
            Table.execute(_issuesSchema.getSchema(),
                    "DELETE FROM " + _tinfoIssueKeywords + " WHERE Container=? AND Type=? AND Keyword=?",
                    new Object[]{c.getId(), new Integer(type), keyword});
            DbCache.clear(_tinfoIssueKeywords);
        }
        catch (SQLException x)
        {
            _log.error("deleteKeyword", x);
        }
    }


    private static final String CUSTOM_COLUMN_CONFIGURATION = "IssuesCaptions";

    public static CustomColumnConfiguration getCustomColumnConfiguration(Container c)
    {
        Map<String, String> map = PropertyManager.getProperties(c.getId(), CUSTOM_COLUMN_CONFIGURATION, false);

        return new CustomColumnConfiguration(map);
    }


    public static void saveCustomColumnConfiguration(Container c, CustomColumnConfiguration ccc) throws SQLException
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(0, c.getId(), CUSTOM_COLUMN_CONFIGURATION, true);

        map.clear();
        map.putAll(ccc.getColumnCaptions());
        map.put(CustomColumnConfiguration.PICK_LIST_NAME, StringUtils.join(ccc.getPickListColumns().iterator(), ","));

        PropertyManager.saveProperties(map);
    }


    public static class CustomColumnConfiguration
    {
        public static final String PICK_LIST_NAME = "pickListColumns";
        private static String[] _tableColumns = new String[]{"int1", "int2", "string1", "string2"};
        private Map<String, String> _columnCaptions = new CaseInsensitiveHashMap<String>(5);
        private Set<String> _pickListColumns = new HashSet<String>(2);

        public CustomColumnConfiguration(Map<String, ? extends Object> map)
        {
            if (null == map)
                return;

            setColumnCaptions(map);
            setPickListColumns(map);
        }

        private void setColumnCaptions(Map<String, ? extends Object> map)
        {
            for (String tableColumn : _tableColumns)
            {
                String caption = (String)map.get(tableColumn);

                if (!StringUtils.isEmpty(caption))
                    _columnCaptions.put(tableColumn, caption);
            }
        }

        public Map<String, String> getColumnCaptions()
        {
            return _columnCaptions;
        }

        private void setPickListColumns(Map<String, ? extends Object> map)
        {
            Object pickListColumnNames = map.get(PICK_LIST_NAME);

            if (null == pickListColumnNames)
                return;

            String[] columns;

            if (pickListColumnNames.getClass().equals(String.class))
                columns = ((String)pickListColumnNames).split(",");
            else
                columns = ((List<String>)pickListColumnNames).toArray(new String[]{});

            for (String column : columns)
                if (null != _columnCaptions.get(column))
                    _pickListColumns.add(column);
        }

        public Set<String> getPickListColumns()
        {
            return _pickListColumns;
        }
    }


    public static Map[] getSummary(Container c) throws SQLException
    {
        return Table.executeQuery(_issuesSchema.getSchema(),
                "SELECT DisplayName, SUM(CASE WHEN Status='open' THEN 1 ELSE 0 END) AS " + _issuesSchema.getSqlDialect().getTableSelectName("Open") + ", SUM(CASE WHEN Status='resolved' THEN 1 ELSE 0 END) AS " + _issuesSchema.getSqlDialect().getTableSelectName("Resolved") + "\n" +
                        "FROM " + _tinfoIssues + " LEFT OUTER JOIN " + CoreSchema.getInstance().getTableInfoUsers() + " ON AssignedTo = UserId\n" +
                        "WHERE Status in ('open', 'resolved') AND Container = ?\n" +
                        "GROUP BY DisplayName",
                new Object[]{c.getId()},
                Map.class);
    }

    public static User[] getAssignedToList(Container c, Issue issue)
    {
        final TableInfo table = CoreSchema.getInstance().getTableInfoUsers();
        final String cacheKey = getCacheKey(c);

        Map<String, User> assignedToMap = (Map<String, User>) DbCache.get(table, cacheKey);
        if (assignedToMap == null)
        {
            assignedToMap = new TreeMap<String, User>();
            for (User user : SecurityManager.getProjectMembers(c.getProject()))
            {
                assignedToMap.put(user.getEmail(), user);
            }
            DbCache.put(table, cacheKey, assignedToMap, Cache.HOUR);
        }

        //add the user who opened this issue, unless they are a guest, or already in the list.
        if (issue != null)
        {
            User createdByUser = UserManager.getUser(issue.getCreatedBy());
            if (createdByUser != null && !createdByUser.isGuest() && !assignedToMap.containsKey(createdByUser.getEmail()))
            {
                assignedToMap.put(createdByUser.getEmail(), createdByUser);
                DbCache.put(table, cacheKey, assignedToMap, Cache.HOUR);
            }
        }
        return assignedToMap.values().toArray(new User[0]);
    }

    public static String getCacheKey(Container c)
    {
        String projectName = c.getProject().getName();
        return projectName + "AssignedTo";
    }

    public static int getUserEmailPreferences(Container c, int userId)
    {
        Integer[] emailPreference = null;

        try
        {
            emailPreference = Table.executeArray(
                    _issuesSchema.getSchema(),
                    "SELECT EmailOption FROM " + _issuesSchema.getTableInfoEmailPrefs() + " WHERE Container=? AND UserId=?",
                    new Object[]{c.getId(), userId},
                    Integer.class);
        }
        catch (SQLException e)
        {
            _log.error(e);
        }

        if (emailPreference.length == 0)
        {
            if (userId == UserManager.getGuestUser().getUserId())
            {
                return 0; 
            }
            return DEFAULT_EMAIL_PREFS;
        }
        return emailPreference[0];
    }

    public static void setUserEmailPreferences(Container c, int userId, int emailPrefs, int currentUser)
    {
        try
        {
            int ret = Table.execute(_issuesSchema.getSchema(),
                    "UPDATE " + _issuesSchema.getTableInfoEmailPrefs() + " SET EmailOption=? WHERE Container=? AND UserId=?",
                    new Object[]{emailPrefs, c.getId(), userId});


            if (ret == 0)
            {
                // record doesn't exist yet...
                Table.execute(_issuesSchema.getSchema(),
                        "INSERT INTO " + _issuesSchema.getTableInfoEmailPrefs() + " (Container, UserId, EmailOption ) VALUES (?, ?, ?)",
                        new Object[]{c.getId(), userId, emailPrefs});
            }
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
    }

    public static void deleteUserEmailPreferences(User user) throws SQLException
    {
        Table.delete(_issuesSchema.getTableInfoEmailPrefs(), new SimpleFilter("UserId", user.getUserId()));
    }

    public static long getIssueCount(Container c)
            throws SQLException
    {
        return Table.executeSingleton(_issuesSchema.getSchema(), "SELECT COUNT(*) FROM " + _tinfoIssues + " WHERE Container = ?", new Object[]{c.getId()}, Long.class);
    }

    public static void uncache(Container c)
    {
        if (c != null)
            DbCache.remove(CoreSchema.getInstance().getTableInfoUsers(), getCacheKey(c));
    }

    public static void purgeContainer(Container c)
    {
        try
        {
            _issuesSchema.getSchema().getScope().beginTransaction();
            String deleteComments = "DELETE FROM " + _tinfoComments + " WHERE IssueId IN (SELECT IssueId FROM " + _tinfoIssues + " WHERE Container = ?)";
            Table.execute(_issuesSchema.getSchema(), deleteComments, new Object[]{c.getId()});
            ContainerUtil.purgeTable(_tinfoIssues, c, null);
            ContainerUtil.purgeTable(_tinfoIssueKeywords, c, null);
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoEmailPrefs(), c, null);
            _issuesSchema.getSchema().getScope().commitTransaction();
        }
        catch (SQLException x)
        {
        }
        finally
        {
            _issuesSchema.getSchema().getScope().closeConnection();
        }
    }


    public static String purge() throws SQLException
    {
        String message = "";

        try
        {
            _issuesSchema.getSchema().getScope().beginTransaction();
            String deleteComments =
                    "DELETE FROM " + _tinfoComments + " WHERE IssueId IN (SELECT IssueId FROM " + _tinfoIssues + " WHERE Container NOT IN (SELECT EntityId FROM core.Containers))";
            int commentsDeleted = Table.execute(_issuesSchema.getSchema(), deleteComments, null);
            String deleteOrphanedComments =
                    "DELETE FROM " + _tinfoComments + " WHERE IssueId NOT IN (SELECT IssueId FROM " + _tinfoIssues + ")";
            commentsDeleted += Table.execute(_issuesSchema.getSchema(), deleteOrphanedComments, null);
            int issuesDeleted = ContainerUtil.purgeTable(_tinfoIssues, null);
            ContainerUtil.purgeTable(_tinfoIssueKeywords, null);
            _issuesSchema.getSchema().getScope().commitTransaction();

            message = "deleted " + issuesDeleted + " issues<br>\ndeleted " + commentsDeleted + " comments<br>\n";
        }
        finally
        {
            if (_issuesSchema.getSchema().getScope().isTransactionActive())
                _issuesSchema.getSchema().getScope().rollbackTransaction();
        }

        return message;
    }


    public static MultiMap<String, String> search(Collection<String> containerIds, Search.SearchTermParser parser)
    {
        SqlDialect dialect = _issuesSchema.getSchema().getSqlDialect();
        String from = _tinfoIssues + " i LEFT OUTER JOIN " + _tinfoComments + " c ON i.IssueId = c.IssueId";
        SQLFragment searchSql = Search.getSQLFragment("Container, Title, IssueId", "Container, Title, i.IssueId", from, "Container", null, containerIds, parser, dialect, "Comment"); // No need to search title since it ends up in the comment
        MultiMap<String, String> map = new MultiHashMap<String, String>();
        ResultSet rs = null;

        try
        {
            rs = Table.executeQuery(_issuesSchema.getSchema(), searchSql);

            while(rs.next())
            {
                String containerId = rs.getString(1);
                Container c = ContainerManager.getForId(containerId);

                StringBuilder link = new StringBuilder("<a href=\"");
                link.append(IssuesController.issueURL(c, "details").addParameter("issueId",rs.getString(3)));
                link.append("\">");
                link.append(PageFlowUtil.filter(rs.getString(2)));
                link.append("</a>");

                map.put(rs.getString(1), link.toString());
            }
        }
        catch(SQLException e)
        {
            ExceptionUtil.logExceptionToMothership(HttpView.currentRequest(), e);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        return map;
    }

    public static String getRequiredIssueFields(Container container)
    {
        String requiredFields = IssuesController.DEFAULT_REQUIRED_FIELDS;
        Map<String, String> map = PropertyManager.getProperties(container.getId(), ISSUES_PREF_MAP, false);
        if (map != null)
            requiredFields = (String)map.get(ISSUES_REQUIRED_FIELDS);
        return requiredFields;
    }

    public static void setRequiredIssueFields(Container container, String requiredFields) throws SQLException
    {
        Map<String, String> map = PropertyManager.getWritableProperties(0, container.getId(), ISSUES_PREF_MAP, true);

        if (!StringUtils.isEmpty(requiredFields))
            requiredFields = requiredFields.toLowerCase();
        map.put(ISSUES_REQUIRED_FIELDS, requiredFields);
        PropertyManager.saveProperties(map);
    }

    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testIssues()
                throws IOException, SQLException, ServletException
        {
            TestContext context = TestContext.get();

            User user = context.getUser();
            assertTrue("login before running this test", null != user);
            assertFalse("login before running this test", user.isGuest());

            Container c = JunitUtil.getTestContainer();

            int issueId;

            //
            // INSERT
            //
            {
                Issue issue = new Issue();
                issue.Open(c, user);
                issue.setAssignedTo(new Integer(user.getUserId()));
                issue.setTitle("This is a junit test bug");
                issue.setTag("junit");
                issue.addComment(user, "new issue");
                issue.setPriority(3);

                IssueManager.saveIssue(null, user, c, issue);
                issueId = issue.getIssueId();
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(null, c, issueId);
                assertEquals("This is a junit test bug", issue.getTitle());
                assertEquals(user.getUserId(), issue.getCreatedBy());
                assertTrue(issue.getCreated().getTime() != 0);
                assertTrue(issue.getModified().getTime() != 0);
                assertEquals(user.getUserId(), issue.getAssignedTo().intValue());
                assertEquals(Issue.statusOPEN, issue.getStatus());
                assertEquals(1, issue.getComments().size());
                assertEquals("new issue", (issue.getComments().iterator().next()).getComment());
            }

            //
            // ADD COMMENT
            //
            {
                Issue issue = IssueManager.getIssue(null, c, issueId);
                issue.addComment(user, "what was I thinking");
                IssueManager.saveIssue(null, user, c, issue);
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(null, c, issueId);
                assertEquals(2, issue.getComments().size());
                Iterator it = issue.getComments().iterator();
                assertEquals("new issue", ((Issue.Comment) it.next()).getComment());
                assertEquals("what was I thinking", ((Issue.Comment) it.next()).getComment());
            }

            //
            // RESOLVE
            //
            {
                Issue issue = IssueManager.getIssue(null, c, issueId);
                assertNotNull("issue not found", issue);
                issue.Resolve(user);

                Issue copy = (Issue) JunitUtil.copyObject(issue);
                copy.setResolution("fixed");
                copy.addComment(user, "fixed it");
                IssueManager.saveIssue(null, user, c, copy);
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(null, c, issueId);
                assertEquals(Issue.statusRESOLVED, issue.getStatus());
                assertEquals(3, issue.getComments().size());
            }

            //
            // CLOSE
            //
            {
                Issue issue = getIssue(null, c, issueId);
                assertNotNull("issue not found", issue);
                issue.Close(user);

                Issue copy = (Issue) JunitUtil.copyObject(issue);
                copy.addComment(user, "closed");
                IssueManager.saveIssue(null, user, c, copy);
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(null, c, issueId);
                assertEquals(Issue.statusCLOSED, issue.getStatus());
                assertEquals(4, issue.getComments().size());
            }
        }


        protected void tearDown() throws Exception
        {
            Container c = JunitUtil.getTestContainer();

            String deleteComments = "DELETE FROM " + _tinfoComments + " WHERE IssueId IN (SELECT IssueId FROM " + _tinfoIssues + " WHERE Container = ?)";
            Table.execute(_issuesSchema.getSchema(), deleteComments, new Object[]{c.getId()});
            String deleteIssues = "DELETE FROM " + _tinfoIssues + " WHERE Container = ?";
            Table.execute(_issuesSchema.getSchema(), deleteIssues, new Object[]{c.getId()});
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
