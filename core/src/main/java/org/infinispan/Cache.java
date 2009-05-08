/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan;

import org.infinispan.config.Configuration;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.lifecycle.Lifecycle;
import org.infinispan.loaders.CacheStore;
import org.infinispan.manager.CacheManager;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.notifications.Listenable;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The central interface of Infinispan.  A Cache provides a highly concurrent, optionally distributed data structure
 * with additional features such as:
 * <p/>
 * <ul> <li>JTA transaction compatibility</li> <li>Eviction support for evicting entries from memory to prevent {@link
 * OutOfMemoryError}s</li> <li>Persisting entries to a {@link CacheStore}, either when they are evicted as an overflow,
 * or all the time, to maintain persistent copies that would withstand server failure or restarts.</li> </ul>
 * <p/>
 * <p/>
 * <p/>
 * For convenience, Cache extends {@link ConcurrentMap} and implements all methods accordingly, although methods like
 * {@link ConcurrentMap#keySet()}, {@link ConcurrentMap#values()} and {@link ConcurrentMap#entrySet()} are expensive
 * (prohibitively so when using a distributed cache) and frequent use of these methods is not recommended.
 * <p/>
 * Also, like many {@link ConcurrentMap} implementations, Cache does not support the use of <tt>null</tt> keys (although
 * <tt>null</tt> values are allowed).
 * <p/>
 * <h3>Asynchronous operations</h3> Cache also supports the use of "async" remote operations.  Note that these methods
 * only really make sense if you are using a clustered cache.  I.e., when used in LOCAL mode, these "async" operations
 * offer no benefit whatsoever.  These methods, such as {@link #putAsync(Object, Object)} offer the best of both worlds
 * between a fully synchronous and a fully asynchronous cache in that a {@link Future} is returned.  The <tt>Future</tt>
 * can then be ignored or thrown away for typical asynchronous behaviour, or queried for synchronous behaviour, which
 * would block until any remote calls complete.  Note that all remote calls are, as far as the transport is concerned,
 * synchronous.  This allows you the guarantees that remote calls succeed, while not blocking your application thread
 * unnecessarily.  For example, usage such as the following could benefit from the async operations:
 * <pre>
 *   Future f1 = cache.putAsync("key1", "value1");
 *   Future f2 = cache.putAsync("key2", "value2");
 *   Future f3 = cache.putAsync("key3", "value3");
 *   f1.get();
 *   f2.get();
 *   f3.get();
 * </pre>
 * The net result is behavior similar to synchronous RPC calls in that at the end, you have guarantees that all calls
 * completed successfully, but you have the added benefit that the three calls could happen in parallel.  This is
 * especially advantageous if the cache uses distribution and the three keys map to different cache instances in the
 * cluster.
 * <p/>
 * <h3>Constructing a Cache</h3> An instance of the Cache is usually obtained by using a {@link CacheManager}.
 * <pre>
 *   CacheManager cm = new DefaultCacheManager(); // optionally pass in a default configuration
 *   Cache c = cm.getCache();
 * </pre>
 * See the {@link CacheManager} interface for more details on providing specific configurations, using multiple caches
 * in the same JVM, etc.
 * <p/>
 * Please see the <a href="http://www.jboss.org/infinispan/docs">Infinispan documentation</a> and/or the <a
 * href="http://www.jboss.org/community/wiki/5minutetutorialonInfinispan">5 Minute Usage Tutorial</a> for more details.
 * <p/>
 *
 * @author Mircea.Markus@jboss.com
 * @author Manik Surtani
 * @see CacheManager
 * @see DefaultCacheManager
 * @see <a href="http://www.jboss.org/infinispan/docs">Infinispan documentation</a>
 * @see <a href="http://www.jboss.org/community/wiki/5minutetutorialonInfinispan">5 Minute Usage Tutorial</a>
 * @since 4.0
 */
public interface Cache<K, V> extends ConcurrentMap<K, V>, Lifecycle, Listenable {
   /**
    * Under special operating behavior, associates the value with the specified key. <ul> <li> Only goes through if the
    * key specified does not exist; no-op otherwise (similar to {@link ConcurrentMap#putIfAbsent(Object, Object)})</i>
    * <li> Force asynchronous mode for replication to prevent any blocking.</li> <li> invalidation does not take place.
    * </li> <li> 0ms lock timeout to prevent any blocking here either. If the lock is not acquired, this method is a
    * no-op, and swallows the timeout exception.</li> <li> Ongoing transactions are suspended before this call, so
    * failures here will not affect any ongoing transactions.</li> <li> Errors and exceptions are 'silent' - logged at a
    * much lower level than normal, and this method does not throw exceptions</li> </ul> This method is for caching data
    * that has an external representation in storage, where, concurrent modification and transactions are not a
    * consideration, and failure to put the data in the cache should be treated as a 'suboptimal outcome' rather than a
    * 'failing outcome'.
    * <p/>
    * An example of when this method is useful is when data is read from, for example, a legacy datastore, and is cached
    * before returning the data to the caller.  Subsequent calls would prefer to get the data from the cache and if the
    * data doesn't exist in the cache, fetch again from the legacy datastore.
    * <p/>
    * See <a href="http://jira.jboss.com/jira/browse/JBCACHE-848">JBCACHE-848</a> for details around this feature.
    * <p/>
    *
    * @param key   key with which the specified value is to be associated.
    * @param value value to be associated with the specified key.
    * @throws IllegalStateException if {@link #getStatus()} would not return {@link ComponentStatus#RUNNING}.
    */
   void putForExternalRead(K key, V value);

   /**
    * Evicts an entry from the memory of the cache.  Note that the entry is <i>not</i> removed from any configured cache
    * stores or any other caches in the cluster (if used in a clustered mode).  Use {@link #remove(Object)} to remove an
    * entry from the entire cache system.
    * <p/>
    * This method is designed to evict an entry from memory to free up memory used by the application.  This method uses
    * a 0 lock acquisition timeout so it does not block in attempting to acquire locks.  It behaves as a no-op if the
    * lock on the entry cannot be acquired <i>immediately</i>.
    * <p/>
    *
    * @param key key to evict
    */
   void evict(K key);

   Configuration getConfiguration();

   /**
    * Starts a batch.  All operations on the current client thread are performed as a part of this batch, with locks
    * held for the duration of the batch and any remote calls delayed till the end of the batch.
    * <p/>
    *
    * @return true if a batch was successfully started; false if one was available and already running.
    */
   public boolean startBatch();

   /**
    * Completes a batch if one has been started using {@link #startBatch()}.  If no batch has been started, this is a
    * no-op.
    * <p/>
    *
    * @param successful if true, the batch completes, otherwise the batch is aborted and changes are not committed.
    */
   public void endBatch(boolean successful);

   /**
    * Retrieves the name of the cache
    *
    * @return the name of the cache
    */
   String getName();

   /**
    * Retrieves the version of Infinispan
    *
    * @return a version string
    */
   String getVersion();

   /**
    * Retrieves the cache manager responsible for creating this cache instance.
    *
    * @return a cache manager
    */
   CacheManager getCacheManager();

   /**
    * An overloaded form of {@link #put(Object, Object)}, which takes in lifespan parameters.
    *
    * @param key      key to use
    * @param value    value to store
    * @param lifespan lifespan of the entry.  Negative values are intepreted as unlimited lifespan.
    * @param unit     unit of measurement for the lifespan
    * @return the value being replaced, or null if nothing is being replaced.
    */
   V put(K key, V value, long lifespan, TimeUnit unit);

   /**
    * An overloaded form of {@link #putIfAbsent(Object, Object)}, which takes in lifespan parameters.
    *
    * @param key      key to use
    * @param value    value to store
    * @param lifespan lifespan of the entry.  Negative values are intepreted as unlimited lifespan.
    * @param unit     unit of measurement for the lifespan
    * @return the value being replaced, or null if nothing is being replaced.
    */
   V putIfAbsent(K key, V value, long lifespan, TimeUnit unit);

   /**
    * An overloaded form of {@link #putAll(Map)}, which takes in lifespan parameters.  Note that the lifespan is applied
    * to all mappings in the map passed in.
    *
    * @param map      map containing mappings to enter
    * @param lifespan lifespan of the entry.  Negative values are intepreted as unlimited lifespan.
    * @param unit     unit of measurement for the lifespan
    */
   void putAll(Map<? extends K, ? extends V> map, long lifespan, TimeUnit unit);

   /**
    * An overloaded form of {@link #replace(Object, Object)}, which takes in lifespan parameters.
    *
    * @param key      key to use
    * @param value    value to store
    * @param lifespan lifespan of the entry.  Negative values are intepreted as unlimited lifespan.
    * @param unit     unit of measurement for the lifespan
    * @return the value being replaced, or null if nothing is being replaced.
    */
   V replace(K key, V value, long lifespan, TimeUnit unit);

   /**
    * An overloaded form of {@link #replace(Object, Object, Object)}, which takes in lifespan parameters.
    *
    * @param key      key to use
    * @param oldValue value to replace
    * @param value    value to store
    * @param lifespan lifespan of the entry.  Negative values are intepreted as unlimited lifespan.
    * @param unit     unit of measurement for the lifespan
    * @return true if the value was replaced, false otherwise
    */
   boolean replace(K key, V oldValue, V value, long lifespan, TimeUnit unit);

   /**
    * An overloaded form of {@link #put(Object, Object)}, which takes in lifespan parameters.
    *
    * @param key             key to use
    * @param value           value to store
    * @param lifespan        lifespan of the entry.  Negative values are intepreted as unlimited lifespan.
    * @param lifespanUnit    time unit for lifespan
    * @param maxIdleTime     the maximum amount of time this key is allowed to be idle for before it is considered as
    *                        expired
    * @param maxIdleTimeUnit time unit for max idle time
    * @return the value being replaced, or null if nothing is being replaced.
    */
   V put(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit maxIdleTimeUnit);

   /**
    * An overloaded form of {@link #putIfAbsent(Object, Object)}, which takes in lifespan parameters.
    *
    * @param key             key to use
    * @param value           value to store
    * @param lifespan        lifespan of the entry.  Negative values are intepreted as unlimited lifespan.
    * @param lifespanUnit    time unit for lifespan
    * @param maxIdleTime     the maximum amount of time this key is allowed to be idle for before it is considered as
    *                        expired
    * @param maxIdleTimeUnit time unit for max idle time
    * @return the value being replaced, or null if nothing is being replaced.
    */
   V putIfAbsent(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit maxIdleTimeUnit);

   /**
    * An overloaded form of {@link #putAll(Map)}, which takes in lifespan parameters.  Note that the lifespan is applied
    * to all mappings in the map passed in.
    *
    * @param map             map containing mappings to enter
    * @param lifespan        lifespan of the entry.  Negative values are intepreted as unlimited lifespan.
    * @param lifespanUnit    time unit for lifespan
    * @param maxIdleTime     the maximum amount of time this key is allowed to be idle for before it is considered as
    *                        expired
    * @param maxIdleTimeUnit time unit for max idle time
    */
   void putAll(Map<? extends K, ? extends V> map, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit maxIdleTimeUnit);

   /**
    * An overloaded form of {@link #replace(Object, Object)}, which takes in lifespan parameters.
    *
    * @param key             key to use
    * @param value           value to store
    * @param lifespan        lifespan of the entry.  Negative values are intepreted as unlimited lifespan.
    * @param lifespanUnit    time unit for lifespan
    * @param maxIdleTime     the maximum amount of time this key is allowed to be idle for before it is considered as
    *                        expired
    * @param maxIdleTimeUnit time unit for max idle time
    * @return the value being replaced, or null if nothing is being replaced.
    */
   V replace(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit maxIdleTimeUnit);

   /**
    * An overloaded form of {@link #replace(Object, Object, Object)}, which takes in lifespan parameters.
    *
    * @param key             key to use
    * @param oldValue        value to replace
    * @param value           value to store
    * @param lifespan        lifespan of the entry.  Negative values are intepreted as unlimited lifespan.
    * @param lifespanUnit    time unit for lifespan
    * @param maxIdleTime     the maximum amount of time this key is allowed to be idle for before it is considered as
    *                        expired
    * @param maxIdleTimeUnit time unit for max idle time
    * @return true if the value was replaced, false otherwise
    */
   boolean replace(K key, V oldValue, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit maxIdleTimeUnit);

   /**
    * Asynchronous version of {@link #put(Object, Object)}.  This method does not block on remote calls, even if your
    * cache mode is synchronous.  Has no benefit over {@link #put(Object, Object)} if used in LOCAL mode.
    * <p/>
    *
    * @param key   key to use
    * @param value value to store
    * @return a future containing the old value replaced.
    */
   Future<V> putAsync(K key, V value);

   /**
    * Asynchronous version of {@link #put(Object, Object, long, TimeUnit)} .  This method does not block on remote
    * calls, even if your cache mode is synchronous.  Has no benefit over {@link #put(Object, Object, long, TimeUnit)}
    * if used in LOCAL mode.
    *
    * @param key      key to use
    * @param value    value to store
    * @param lifespan lifespan of entry
    * @param unit     time unit for lifespan
    * @return a future containing the old value replaced
    */
   Future<V> putAsync(K key, V value, long lifespan, TimeUnit unit);

   /**
    * Asynchronous version of {@link #put(Object, Object, long, TimeUnit, long, TimeUnit)}.  This method does not block
    * on remote calls, even if your cache mode is synchronous.  Has no benefit over {@link #put(Object, Object, long,
    * TimeUnit, long, TimeUnit)} if used in LOCAL mode.
    *
    * @param key          key to use
    * @param value        value to store
    * @param lifespan     lifespan of entry
    * @param lifespanUnit time unit for lifespan
    * @param maxIdle      the maximum amount of time this key is allowed to be idle for before it is considered as
    *                     expired
    * @param maxIdleUnit  time unit for max idle time
    * @return a future containing the old value replaced
    */
   Future<V> putAsync(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit);

   /**
    * Asynchronous version of {@link #putAll(Map)}.  This method does not block on remote calls, even if your cache mode
    * is synchronous.  Has no benefit over {@link #putAll(Map)} if used in LOCAL mode.
    *
    * @param data to store
    * @return a future containing a void return type
    */
   Future<Void> putAllAsync(Map<? extends K, ? extends V> data);

   /**
    * Asynchronous version of {@link #putAll(Map, long, TimeUnit)}.  This method does not block on remote calls, even if
    * your cache mode is synchronous.  Has no benefit over {@link #putAll(Map, long, TimeUnit)} if used in LOCAL mode.
    *
    * @param data     to store
    * @param lifespan lifespan of entry
    * @param unit     time unit for lifespan
    * @return a future containing a void return type
    */
   Future<Void> putAllAsync(Map<? extends K, ? extends V> data, long lifespan, TimeUnit unit);

   /**
    * Asynchronous version of {@link #putAll(Map, long, TimeUnit, long, TimeUnit)}.  This method does not block on
    * remote calls, even if your cache mode is synchronous.  Has no benefit over {@link #putAll(Map, long, TimeUnit,
    * long, TimeUnit)} if used in LOCAL mode.
    *
    * @param data         to store
    * @param lifespan     lifespan of entry
    * @param lifespanUnit time unit for lifespan
    * @param maxIdle      the maximum amount of time this key is allowed to be idle for before it is considered as
    *                     expired
    * @param maxIdleUnit  time unit for max idle time
    * @return a future containing a void return type
    */
   Future<Void> putAllAsync(Map<? extends K, ? extends V> data, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit);

   /**
    * Asynchronous version of {@link #clear()}.  This method does not block on remote calls, even if your cache mode is
    * synchronous.  Has no benefit over {@link #clear()} if used in LOCAL mode.
    *
    * @return a future containing a void return type
    */
   Future<Void> clearAsync();

   /**
    * Asynchronous version of {@link #putIfAbsent(Object, Object)}.  This method does not block on remote calls, even if
    * your cache mode is synchronous.  Has no benefit over {@link #putIfAbsent(Object, Object)} if used in LOCAL mode.
    * <p/>
    *
    * @param key   key to use
    * @param value value to store
    * @return a future containing the old value replaced.
    */
   Future<V> putIfAbsentAsync(K key, V value);

   /**
    * Asynchronous version of {@link #putIfAbsent(Object, Object, long, TimeUnit)} .  This method does not block on
    * remote calls, even if your cache mode is synchronous.  Has no benefit over {@link #putIfAbsent(Object, Object,
    * long, TimeUnit)} if used in LOCAL mode.
    *
    * @param key      key to use
    * @param value    value to store
    * @param lifespan lifespan of entry
    * @param unit     time unit for lifespan
    * @return a future containing the old value replaced
    */
   Future<V> putIfAbsentAsync(K key, V value, long lifespan, TimeUnit unit);

   /**
    * Asynchronous version of {@link #putIfAbsent(Object, Object, long, TimeUnit, long, TimeUnit)}.  This method does
    * not block on remote calls, even if your cache mode is synchronous.  Has no benefit over {@link
    * #putIfAbsent(Object, Object, long, TimeUnit, long, TimeUnit)} if used in LOCAL mode.
    *
    * @param key          key to use
    * @param value        value to store
    * @param lifespan     lifespan of entry
    * @param lifespanUnit time unit for lifespan
    * @param maxIdle      the maximum amount of time this key is allowed to be idle for before it is considered as
    *                     expired
    * @param maxIdleUnit  time unit for max idle time
    * @return a future containing the old value replaced
    */
   Future<V> putIfAbsentAsync(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit);

   /**
    * Asynchronous version of {@link #remove(Object)}.  This method does not block on remote calls, even if your cache
    * mode is synchronous.  Has no benefit over {@link #remove(Object)} if used in LOCAL mode.
    *
    * @param key key to remove
    * @return a future containing the value removed
    */
   Future<V> removeAsync(Object key);

   /**
    * Asynchronous version of {@link #remove(Object, Object)}.  This method does not block on remote calls, even if your
    * cache mode is synchronous.  Has no benefit over {@link #remove(Object, Object)} if used in LOCAL mode.
    *
    * @param key   key to remove
    * @param value value to match on
    * @return a future containing a boolean, indicating whether the entry was removed or not
    */
   Future<Boolean> removeAsync(Object key, Object value);

   /**
    * Asynchronous version of {@link #replace(Object, Object)}.  This method does not block on remote calls, even if
    * your cache mode is synchronous.  Has no benefit over {@link #replace(Object, Object)} if used in LOCAL mode.
    *
    * @param key   key to remove
    * @param value value to store
    * @return a future containing the previous value overwritten
    */
   Future<V> replaceAsync(K key, V value);

   /**
    * Asynchronous version of {@link #replace(Object, Object, long, TimeUnit)}.  This method does not block on remote
    * calls, even if your cache mode is synchronous.  Has no benefit over {@link #replace(Object, Object, long,
    * TimeUnit)} if used in LOCAL mode.
    *
    * @param key      key to remove
    * @param value    value to store
    * @param lifespan lifespan of entry
    * @param unit     time unit for lifespan
    * @return a future containing the previous value overwritten
    */
   Future<V> replaceAsync(K key, V value, long lifespan, TimeUnit unit);

   /**
    * Asynchronous version of {@link #replace(Object, Object, long, TimeUnit, long, TimeUnit)}.  This method does not
    * block on remote calls, even if your cache mode is synchronous.  Has no benefit over {@link #replace(Object,
    * Object, long, TimeUnit, long, TimeUnit)} if used in LOCAL mode.
    *
    * @param key          key to remove
    * @param value        value to store
    * @param lifespan     lifespan of entry
    * @param lifespanUnit time unit for lifespan
    * @param maxIdle      the maximum amount of time this key is allowed to be idle for before it is considered as
    *                     expired
    * @param maxIdleUnit  time unit for max idle time
    * @return a future containing the previous value overwritten
    */
   Future<V> replaceAsync(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit);

   /**
    * Asynchronous version of {@link #replace(Object, Object, Object)}.  This method does not block on remote calls,
    * even if your cache mode is synchronous.  Has no benefit over {@link #replace(Object, Object, Object)} if used in
    * LOCAL mode.
    *
    * @param key      key to remove
    * @param oldValue value to overwrite
    * @param newValue value to store
    * @return a future containing a boolean, indicating whether the entry was replaced or not
    */
   Future<Boolean> replaceAsync(K key, V oldValue, V newValue);

   /**
    * Asynchronous version of {@link #replace(Object, Object, Object, long, TimeUnit)}.  This method does not block on
    * remote calls, even if your cache mode is synchronous.  Has no benefit over {@link #replace(Object, Object, Object,
    * long, TimeUnit)} if used in LOCAL mode.
    *
    * @param key      key to remove
    * @param oldValue value to overwrite
    * @param newValue value to store
    * @param lifespan lifespan of entry
    * @param unit     time unit for lifespan
    * @return a future containing a boolean, indicating whether the entry was replaced or not
    */
   Future<Boolean> replaceAsync(K key, V oldValue, V newValue, long lifespan, TimeUnit unit);

   /**
    * Asynchronous version of {@link #replace(Object, Object, Object, long, TimeUnit, long, TimeUnit)}.  This method
    * does not block on remote calls, even if your cache mode is synchronous.  Has no benefit over {@link
    * #replace(Object, Object, Object, long, TimeUnit, long, TimeUnit)} if used in LOCAL mode.
    *
    * @param key          key to remove
    * @param oldValue     value to overwrite
    * @param newValue     value to store
    * @param lifespan     lifespan of entry
    * @param lifespanUnit time unit for lifespan
    * @param maxIdle      the maximum amount of time this key is allowed to be idle for before it is considered as
    *                     expired
    * @param maxIdleUnit  time unit for max idle time
    * @return a future containing a boolean, indicating whether the entry was replaced or not
    */
   Future<Boolean> replaceAsync(K key, V oldValue, V newValue, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit);


   AdvancedCache<K, V> getAdvancedCache();

   /**
    * Method that releases object references of cached objects held in the cache by serializing them to byte buffers.
    * Cached objects are lazily deserialized when accessed again, based on the calling thread's context class loader.
    * <p/>
    * This can be expensive, based on the effort required to serialize cached objects.
    * <p/>
    */
   void compact();

   ComponentStatus getStatus();
}
