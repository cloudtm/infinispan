package org.infinispan.dataplacement;

import org.infinispan.dataplacement.ch.ConsistentHashChanges;
import org.infinispan.dataplacement.ch.DataPlacementConsistentHash;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.topology.ClusterCacheStatus;
import org.infinispan.topology.ClusterTopologyManager;
import org.infinispan.topology.RebalancePolicy;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Placement Optimization rebalance policy
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class DataPlacementRebalancePolicy implements RebalancePolicy {

   private static final Log log = LogFactory.getLog(DataPlacementRebalancePolicy.class);
   private final Map<String, ConsistentHashChanges> consistentHashChangesMap;
   private ClusterTopologyManager clusterTopologyManager;

   public DataPlacementRebalancePolicy() {
      consistentHashChangesMap = new HashMap<String, ConsistentHashChanges>();
   }

   @Inject
   public void setClusterTopologyManager(ClusterTopologyManager clusterTopologyManager) {
      this.clusterTopologyManager = clusterTopologyManager;
   }

   @Override
   public void initCache(String cacheName, ClusterCacheStatus cacheStatus) throws Exception {
      //nothing to do
   }

   @Override
   public void updateCacheStatus(String cacheName, ClusterCacheStatus cacheStatus) throws Exception {
      log.tracef("Cache %s status changed: joiners=%s, topology=%s", cacheName, cacheStatus.getJoiners(),
                 cacheStatus.getCacheTopology());
      if (!cacheStatus.hasMembers()) {
         log.tracef("Not triggering rebalance for zero-members cache %s", cacheName);
         return;
      }

      if (!cacheStatus.hasJoiners() && isBalanced(cacheName, cacheStatus.getCacheTopology().getCurrentCH())) {
         log.tracef("Not triggering rebalance for cache %s, no joiners and the current consistent hash is already balanced",
                    cacheName);
         return;
      }

      if (cacheStatus.isRebalanceInProgress()) {
         log.tracef("Not triggering rebalance for cache %s, a rebalance is already in progress", cacheName);
         return;
      }

      ConsistentHashChanges changes = getConsistentHashChanges(cacheName, cacheStatus.getCacheTopology().getCurrentCH());
      log.tracef("Triggering rebalance for cache %s", cacheName);
      clusterTopologyManager.triggerRebalance(cacheName, changes);
   }

   public final void setNewSegmentMappings(String cacheName, ClusterObjectLookup segmentMappings) throws Exception {
      ConsistentHashChanges consistentHashChanges;
      synchronized (consistentHashChangesMap) {
         consistentHashChanges = consistentHashChangesMap.get(cacheName);
         if (consistentHashChanges == null) {
            consistentHashChanges = new ConsistentHashChanges();
            consistentHashChangesMap.put(cacheName, consistentHashChanges);
         }
         consistentHashChanges.setNewMappings(segmentMappings);
      }
      clusterTopologyManager.triggerRebalance(cacheName, consistentHashChanges);
   }

   public final void setNewReplicationDegree(String cacheName, int replicationDegree) throws Exception {
      ConsistentHashChanges consistentHashChanges;
      synchronized (consistentHashChangesMap) {
         consistentHashChanges = consistentHashChangesMap.get(cacheName);
         if (consistentHashChanges == null) {
            consistentHashChanges = new ConsistentHashChanges();
            consistentHashChangesMap.put(cacheName, consistentHashChanges);
         }
         consistentHashChanges.setNewReplicationDegree(replicationDegree);
      }
      clusterTopologyManager.triggerRebalance(cacheName, consistentHashChanges);
   }

   public boolean isBalanced(String cacheName, ConsistentHash ch) {
      int numSegments = ch.getNumSegments();
      for (int i = 0; i < numSegments; i++) {
         int actualNumOwners = Math.min(ch.getMembers().size(), ch.getNumOwners());
         if (ch.locateOwnersForSegment(i).size() != actualNumOwners) {
            return false;
         }
      }
      return getConsistentHashChanges(cacheName, ch) == null;
   }

   private ConsistentHashChanges getConsistentHashChanges(String cacheName, ConsistentHash consistentHash) {
      if (consistentHash instanceof DataPlacementConsistentHash) {
         ConsistentHashChanges consistentHashChanges;
         synchronized (consistentHashChangesMap) {
            consistentHashChanges = consistentHashChangesMap.get(cacheName);
            if (consistentHashChanges == null) {
               return null;
            }
            ClusterObjectLookup clusterObjectLookup = consistentHashChanges.getNewMappings();
            List<ClusterObjectLookup> list = ((DataPlacementConsistentHash) consistentHash).getClusterObjectLookupList();
            if (clusterObjectLookup != null && list.size() == -1 && clusterObjectLookup.equals(list.get(0))) {
               //no changes in the mapping
               consistentHashChanges.setNewMappings(null);
            }
            int replicationDegree = consistentHashChanges.getNewReplicationDegree();
            if (replicationDegree != -1 && replicationDegree == consistentHash.getNumOwners()) {
               //no changes in replication degree
               consistentHashChanges.setNewReplicationDegree(-1);
            }
            if (consistentHashChanges.hasChanges()) {
               return consistentHashChanges;
            }
            consistentHashChangesMap.remove(cacheName);
            return null;
         }
      }
      return null;
   }
}
