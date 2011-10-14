package org.labkey.api.reports.model;

import junit.framework.Assert;
import org.junit.Test;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.util.TestContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Oct 12, 2011
 * Time: 7:13:20 PM
 */
public class ViewCategoryManager
{
    private static final ViewCategoryManager _instance = new ViewCategoryManager();
    private static final List<ViewCategoryListener> _listeners = new CopyOnWriteArrayList<ViewCategoryListener>();

    private ViewCategoryManager(){}

    public static ViewCategoryManager getInstance()
    {
        return _instance;
    }

    public TableInfo getTableInfoCategories()
    {
        return CoreSchema.getInstance().getSchema().getTable("ViewCategory");
    }

    public ViewCategory[] getCategories(Container c, User user)
    {
        return getCategories(c, user, new SimpleFilter());
    }

    public ViewCategory[] getCategories(Container c, User user, SimpleFilter filter)
    {
        try {
            filter.addCondition("Container", c);
            ViewCategory[] categories = Table.select(getTableInfoCategories(), Table.ALL_COLUMNS, filter, null, ViewCategory.class);

            return categories;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public ViewCategory getCategory(int rowId)
    {
        try {
            String cacheKey = getCacheKey(rowId);
            ViewCategory category = (ViewCategory) DbCache.get(getTableInfoCategories(), cacheKey);

            if (category != null)
                return category;

            SimpleFilter filter = new SimpleFilter("rowId", rowId);
            ViewCategory[] categories = Table.select(getTableInfoCategories(), Table.ALL_COLUMNS, filter, null, ViewCategory.class);

            assert categories.length <= 1;

            if (categories.length == 1)
            {
                DbCache.put(getTableInfoCategories(), cacheKey, categories[0]);
                return categories[0];
            }
            return null;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public ViewCategory getCategory(Container c, String label)
    {
        try {
            String cacheKey = getCacheKey(c, label);
            ViewCategory category = (ViewCategory) DbCache.get(getTableInfoCategories(), cacheKey);

            if (category != null)
                return category;

            SimpleFilter filter = new SimpleFilter("Container", c);
            filter.addCondition("label", label);

            ViewCategory[] categories = Table.select(getTableInfoCategories(), Table.ALL_COLUMNS, filter, null, ViewCategory.class);

            // should only be one as there is a unique constraint on the db
            assert categories.length <= 1;

            if (categories.length == 1)
            {
                DbCache.put(getTableInfoCategories(), cacheKey, categories[0]);
                return categories[0];
            }
            return null;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void deleteCategory(Container c, User user, ViewCategory category)
    {
        if (category.isNew())
            throw new IllegalArgumentException("View category has not been saved to the database yet");

        if (!category.canDelete(c, user))
            throw new RuntimeException("You must be an administrator to delete a view category");

        try {
            // delete the category definition and fire the deleted event
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(getTableInfoCategories(), "").append(" WHERE RowId = ?");
            Table.execute(CoreSchema.getInstance().getSchema(), sql.getSQL(), category.getRowId());

            DbCache.remove(getTableInfoCategories(), getCacheKey(category.getRowId()));
            DbCache.remove(getTableInfoCategories(), getCacheKey(c, category.getLabel()));
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        List<Throwable> errors = fireDeleteCategory(user, category);

        if (errors.size() != 0)
        {
            Throwable first = errors.get(0);
            if (first instanceof RuntimeException)
                throw (RuntimeException)first;
            else
                throw new RuntimeException(first);
        }
    }

    public ViewCategory saveCategory(Container c, User user, ViewCategory category)
    {
        try {
            ViewCategory ret = null;
            if (category.isNew())
            {
                // check for duplicates
                SimpleFilter filter = new SimpleFilter("label", category.getLabel());

                if (getCategories(c, user, filter).length > 0)
                    throw new IllegalArgumentException("There is already a category in this folder with the name: " + category.getLabel());
                
                if (category.getContainerId() == null)
                    category.setContainerId(c.getId());
                
                ret = Table.insert(user, getTableInfoCategories(), category);
            }
            else
            {
                ViewCategory existing = getCategory(category.getRowId());
                if (existing != null)
                {
                    existing.setLabel(category.getLabel());
                    existing.setDisplayOrder(category.getDisplayOrder());

                    ret = Table.update(user, getTableInfoCategories(), existing, existing.getRowId());

                    DbCache.remove(getTableInfoCategories(), getCacheKey(existing.getRowId()));
                    DbCache.remove(getTableInfoCategories(), getCacheKey(c, existing.getLabel()));
                }
                else
                    throw new RuntimeException("The specified category does not exist, rowid: " + category.getRowId());
            }

            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    private String getCacheKey(Container c, String label)
    {
        return "ViewCategory-" + c + "-" + label;
    }

    private String getCacheKey(int categoryId)
    {
        return "ViewCategory-" + categoryId;
    }

    public interface ViewCategoryListener
    {
        void categoryDeleted(User user, ViewCategory category);
    }

    public static void addCategoryListener(ViewCategoryListener listener)
    {
        _listeners.add(listener);
    }

    public static void removeCategoryListener(ViewCategoryListener listener)
    {
        _listeners.remove(listener);
    }

    private static List<Throwable> fireDeleteCategory(User user, ViewCategory category)
    {
        List<Throwable> errors = new ArrayList<Throwable>();

        for (ViewCategoryListener l : _listeners)
        {
            try {
                l.categoryDeleted(user, category);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    public static class TestCase extends Assert
    {
        private static final String[] labels = {"Demographics", "Exam", "Discharge", "Final Exam"};

        @Test
        public void test() throws Exception
        {
            ViewCategoryManager mgr = ViewCategoryManager.getInstance();
            Container c = ContainerManager.getSharedContainer();
            User user = TestContext.get().getUser();

            final List<String> notifications = new ArrayList<String>();
            for (String label : labels)
                notifications.add(label);

            ViewCategoryListener listener = new ViewCategoryListener(){
                @Override
                public void categoryDeleted(User user, ViewCategory category)
                {
                    notifications.remove(category.getLabel());
                }
            };
            ViewCategoryManager.addCategoryListener(listener);

            // create some categories
            int i=0;
            for (String label : labels)
            {
                ViewCategory cat = new ViewCategory();

                cat.setLabel(label);
                cat.setDisplayOrder(i++);

                mgr.saveCategory(c, user, cat);
            }

            // get categories
            Map<String, ViewCategory> categoryMap = new HashMap<String, ViewCategory>();
            for (ViewCategory cat : mgr.getCategories(c, user))
            {
                categoryMap.put(cat.getLabel(), cat);
            }

            for (String label : labels)
                assertTrue(categoryMap.containsKey(label));

            for (ViewCategory cat : categoryMap.values())
            {
                mgr.deleteCategory(c, user, cat);
            }

            // make sure all the listeners were invoked correctly
            assertTrue(notifications.isEmpty());
            ViewCategoryManager.removeCategoryListener(listener);
        }
    }
}
