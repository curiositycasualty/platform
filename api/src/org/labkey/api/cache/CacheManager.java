package org.labkey.api.cache;

import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.implementation.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: Jun 20, 2010
 * Time: 10:05:07 AM
 */

/*
    TODO:

    - MemTrack all caches?
    - Document getTemporaryCache -- must close().  FastaDbLoader needs to close().
    - TransactionCacheMap -> TransactionCache
    - CacheWrapper -> TrackingCacheWrapper?
    - Track expirations
    - Change remove() to void.
    - DatabaseCache: remove synchronization, getMap() -> getCache()

 */
public class CacheManager
{
    private static final Logger LOG = Logger.getLogger(CacheManager.class);

    public static final long SECOND = DateUtils.MILLIS_PER_SECOND;
    public static final long MINUTE = DateUtils.MILLIS_PER_MINUTE;
    public static final long HOUR = DateUtils.MILLIS_PER_HOUR;
    public static final long DAY = DateUtils.MILLIS_PER_DAY;

    public static final long DEFAULT_TIMEOUT = HOUR;

    // Swap providers here to test Ehcache vs. LabKey's TTLCacheMap implementation
    private static final CacheProvider PROVIDER = EhCacheProvider.getInstance(); // TTLCacheProvider.getInstance();
    private static final List<Cache> KNOWN_CACHES = new LinkedList<Cache>();

    public static <K, V> Cache<K, V> getCache(int limit, long defaultTimeToLive, String debugName)
    {
        Cache<K, V> cache = new CacheWrapper<K, V>(PROVIDER.<K, V>getBasicCache(debugName, limit, defaultTimeToLive), debugName, null);
        addToKnownCaches(cache);
        return cache;
    }

    public static <V> StringKeyCache<V> getStringKeyCache(int limit, long defaultTimeToLive, String debugName)
    {
        StringKeyCache<V> cache = new StringKeyCacheWrapper<V>(PROVIDER.<String, V>getBasicCache(debugName, limit, defaultTimeToLive), debugName, null);
        addToKnownCaches(cache);
        return cache;
    }

    public static <V> StringKeyCache<V> getTemporaryCache(int limit, long defaultTimeToLive, String debugName, @Nullable Stats stats)
    {
        // Temporary caches are not tracked and their statistics can accumulate to another cache's stats
        return new StringKeyCacheWrapper<V>(PROVIDER.<String, V>getBasicCache(debugName, limit, defaultTimeToLive), debugName, stats);
    }

    private static final StringKeyCache<Object> SHARED_CACHE = getStringKeyCache(10000, DEFAULT_TIMEOUT, "sharedCache");

    public static <V> StringKeyCache<V> getSharedCache()
    {
        return (StringKeyCache<V>)SHARED_CACHE;
    }

    // We track "permanent" caches so memtracker can clear them and admin console can report statistics
    private static void addToKnownCaches(Cache cache)
    {
        synchronized (KNOWN_CACHES)
        {
            KNOWN_CACHES.add(cache);

            LOG.debug("Known caches: " + KNOWN_CACHES.size());
        }
    }

    public static void clearAllKnownCaches()
    {
        synchronized (KNOWN_CACHES)
        {
            for (Cache cache : KNOWN_CACHES)
                cache.clear();
        }

        clearAllKnownCacheMaps();
    }

    // We report statistics only for the Cache instances
    public static List<Cache> getKnownCaches()
    {
        List<Cache> copy = new ArrayList<Cache>();

        synchronized (KNOWN_CACHES)
        {
            for (Cache cache : KNOWN_CACHES)
                copy.add(cache);
        }

        return copy;
    }

    public static CacheStats getCacheStats(Cache cache)
    {
        return new CacheStats(cache.getDebugName(), cache.getStats(), cache.size(), cache.getLimit());
    }

    public static CacheStats getTransactionCacheStats(Cache cache)
    {
        return new CacheStats(cache.getDebugName(), cache.getTransactionStats(), cache.size(), cache.getLimit());
    }


    // TODO: Migrate all direct usages of cache implementations and delete everything below here

    private static final List<CacheMap> KNOWN_CACHEMAPS = new LinkedList<CacheMap>();

    // We track "permanent" cache maps so memtracker can clear them and admin console can report statistics
    private static void addToKnownCacheMaps(CacheMap cacheMap)
    {
        synchronized (KNOWN_CACHEMAPS)
        {
            KNOWN_CACHEMAPS.add(cacheMap);
        }
    }


    public static void clearAllKnownCacheMaps()
    {
        synchronized (KNOWN_CACHEMAPS)
        {
            for (CacheMap cache : KNOWN_CACHEMAPS)
                cache.clear();
        }
    }

    @Deprecated
    public static <K, V> CacheMap<K, V> getCacheMap(int initialSize, String debugName)
    {
        CacheMap<K, V> cache = new CacheMap<K, V>(initialSize, debugName);
        addToKnownCacheMaps(cache);
        return cache;
    }

    @Deprecated
    public static <K, V> CacheMap<K, V> getLimitedCacheMap(int initialSize, int limit, String debugName)
    {
        CacheMap<K, V> cache = new LimitedCacheMap<K, V>(initialSize, limit, debugName);
        addToKnownCacheMaps(cache);
        return cache;
    }

    @Deprecated
    public static <K, V> CacheMap<K, V> getTTLCacheMap(int limit, String debugName)
    {
        TTLCacheMap<K, V> cache = new TTLCacheMap<K, V>(limit, debugName);
        addToKnownCacheMaps(cache);
        return cache;
    }
}
