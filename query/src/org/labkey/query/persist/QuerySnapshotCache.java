package org.labkey.query.persist;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.UnmodifiableMultiMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by adam on 10/4/2015.
 */
public class QuerySnapshotCache
{
    private static final Cache<Container, QuerySnapshotCollections> QUERY_SNAPSHOT_DEF_CACHE = CacheManager.getBlockingCache(CacheManager.UNLIMITED, CacheManager.DAY, "Query Snapshot Cache", new CacheLoader<Container, QuerySnapshotCollections>()
    {
        @Override
        public QuerySnapshotCollections load(Container c, @Nullable Object argument)
        {
            return new QuerySnapshotCollections(c);
        }
    });

    static @NotNull Collection<QuerySnapshotDef> getQuerySnapshotDefs(@Nullable Container c, @Nullable String schemaName)
    {
        QuerySnapshotCollections collections = QUERY_SNAPSHOT_DEF_CACHE.get(c);

        return null != schemaName ? collections.getForSchema(schemaName) : collections.getAllDefs();
    }

    static @Nullable QuerySnapshotDef getQuerySnapshotDef(@NotNull Container c, @NotNull String schemaName, @NotNull String snapshotName)
    {
        assert null != c && null != schemaName && null != snapshotName;
        return QUERY_SNAPSHOT_DEF_CACHE.get(c).getForSchemaAndName(schemaName, snapshotName);
    }

    static void uncache(Container c)
    {
        QUERY_SNAPSHOT_DEF_CACHE.remove(c);
        QUERY_SNAPSHOT_DEF_CACHE.remove(null);  // Clear out the full list (used for dependency tracking)
    }

    // Convenience method that handles null check
    static void uncache(QuerySnapshotDef def)
    {
        Container c = def.lookupContainer();

        if (null != c)
            uncache(c);
    }

    private static class QuerySnapshotCollections
    {
        private final Collection<QuerySnapshotDef> _allDefs;
        private final MultiMap<String, QuerySnapshotDef> _bySchema;
        private final Map<String, Map<String, QuerySnapshotDef>> _bySchemaAndName;

        private QuerySnapshotCollections(@Nullable Container c)
        {
            SimpleFilter filter = null != c ? SimpleFilter.createContainerFilter(c) : new SimpleFilter();

            _allDefs = Collections.unmodifiableCollection(
                new TableSelector(QueryManager.get().getTableInfoQuerySnapshotDef(), filter, null)
                    .getCollection(QuerySnapshotDef.class)
            );

            MultiMap<String, QuerySnapshotDef> bySchema = new MultiHashMap<>();
            Map<String, Map<String, QuerySnapshotDef>> bySchemaAndName = new HashMap<>();

            for (QuerySnapshotDef def : _allDefs)
            {
                bySchema.put(def.getSchema(), def);

                Map<String, QuerySnapshotDef> map = bySchemaAndName.get(def.getSchema());

                if (null == map)
                {
                    map = new HashMap<>();
                    bySchemaAndName.put(def.getSchema(), map);
                }

                map.put(def.getName(), def);
            }

            _bySchema = new UnmodifiableMultiMap<>(bySchema);
            _bySchemaAndName = Collections.unmodifiableMap(bySchemaAndName);
        }

        private @NotNull Collection<QuerySnapshotDef> getAllDefs()
        {
            return _allDefs;
        }

        private @NotNull Collection<QuerySnapshotDef> getForSchema(@NotNull String schemaName)
        {
            Collection<QuerySnapshotDef> defs = _bySchema.get(schemaName);
            return null != defs ? Collections.unmodifiableCollection(defs) : Collections.emptyList();
        }

        private @Nullable QuerySnapshotDef getForSchemaAndName(@NotNull String schemaName, @NotNull String snapshotName)
        {
            Map<String, QuerySnapshotDef> snapshotDefMap = _bySchemaAndName.get(schemaName);

            return null != snapshotDefMap ? snapshotDefMap.get(snapshotName) : null;
        }
    }
}
