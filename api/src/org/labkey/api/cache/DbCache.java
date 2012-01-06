/*
 * Copyright (c) 2005-2012 LabKey Corporation
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

package org.labkey.api.cache;

import org.apache.log4j.Logger;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.Path;

import java.util.HashMap;
import java.util.Map;

/**
 * User: migra
 * Date: Nov 30, 2005
 * Time: 4:41:26 PM
 *
 * DbCache is a wrapper that allocates a shared transaction-aware cache per TableInfo (for non-transaction use)
 * and a private transaction-aware cache per TableInfo per thread/connection (used for the duration of a transaction)
 */
public class DbCache
{
    private static Logger LOG = Logger.getLogger(DbCache.class);
    private static final Map<Path, DatabaseCache<Object>> CACHES = new HashMap<Path, DatabaseCache<Object>>(100);

    public static int DEFAULT_CACHE_SIZE = 1000;   // Each TableInfo can override this (see tableInfo.xsd <cacheSize> element)


    public static DatabaseCache<Object> getCache(TableInfo tinfo)
    {
        return getCache(tinfo, true);
    }


    private static DatabaseCache<Object> getCache(TableInfo tinfo, boolean create)
    {
        Path cacheKey = tinfo.getNotificationKey();
        assert null != cacheKey : "DbCache not supported for " + tinfo.toString();

        synchronized(CACHES)
        {
            DatabaseCache<Object> cache = CACHES.get(cacheKey);

            if (null == cache && create)
            {
                cache = new DatabaseCache<Object>(tinfo.getSchema().getScope(), tinfo.getCacheSize(), tinfo.getName());
                CACHES.put(cacheKey, cache);
            }

            return cache;
        }
    }


    public static void put(TableInfo tinfo, String name, Object obj)
    {
        DatabaseCache<Object> cache = getCache(tinfo, true);
        cache.put(name, obj);
    }


    public static void put(TableInfo tinfo, String name, Object obj, long millisToLive)
    {
        DatabaseCache<Object> cache = getCache(tinfo, true);
        cache.put(name, obj, millisToLive);
    }


    public static Object get(TableInfo tinfo, String name)
    {
        DatabaseCache cache = getCache(tinfo, false);
        return null == cache ? null : cache.get(name);
    }
    

    public static void remove(TableInfo tinfo, String name)
    {
        DatabaseCache cache = getCache(tinfo, false);
        if (null != cache)
            cache.remove(name);
    }


//    /*
//    With the introduction of the query layer, we can now have multiple tableinfo objects
//    for a single actual table.  These tableinfo objects may have different filters applied.
//    As a result, we need to be very careful with our caching; we look up and store using
//    Object .equals (by just letting the CacheMap do its thing), but on removal we need to
//    remove all tableinfos that are bound to the same underlying table.  We gather that
//    list of tables here:
//    */
//    private static List<TableInfo> getTableInfosByUnderlyingTable(TableInfo tinfo)
//    {
//        synchronized (CACHES)
//        {
//            List<TableInfo> matchingCaches = new ArrayList<TableInfo>();
//            for (Map.Entry<Path, DatabaseCache<Object>> entry : CACHES.entrySet())
//            {
//                if (entry.getKey().getName().equals(tinfo.getName()))
//                    matchingCaches.add(entry.getKey());
//            }
//            return matchingCaches;
//        }
//    }


    /* used by SqlScriptRunner */
    public static void invalidateAllCaches()
    {
        synchronized(CACHES)
        {
            for (DatabaseCache<Object> c : CACHES.values())
                c.clear();
        }
    }


    /** used by Table */
    public static void invalidateAll(TableInfo tinfo)
    {
        DatabaseCache cache = CACHES.get(tinfo.getNotificationKey());
        if (null != cache)
            cache.clear();

//        // see comment above 'getTableInfosByUnderlyingTable' for an explanation of why
//        // we clear multiple caches here:
//        synchronized(CACHES)
//        {
//            List<TableInfo> tableInfos = getTableInfosByUnderlyingTable(tinfo);
//
//            for (TableInfo matchingTinfo : tableInfos)
//            {
//                DatabaseCache cache = CACHES.get(matchingTinfo);
//                if (null != cache)
//                    cache.clear();
//            }
//        }
    }


    public static void clear(TableInfo tinfo)
    {
        DatabaseCache cache = getCache(tinfo, false);
        if (null != cache)
            cache.clear();
    }


    public static void removeUsingPrefix(TableInfo tinfo, String name)
    {
        DatabaseCache cache = getCache(tinfo, false);
        if (null != cache)
            cache.removeUsingPrefix(name);
    }
}
