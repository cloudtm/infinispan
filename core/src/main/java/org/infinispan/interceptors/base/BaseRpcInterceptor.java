/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
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
package org.infinispan.interceptors.base;

import org.infinispan.commands.FlagAffectedCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.context.Flag;
import org.infinispan.context.impl.LocalTxInvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Start;
import org.infinispan.remoting.responses.IgnoreExtraResponsesValidityFilter;
import org.infinispan.remoting.responses.Response;
import org.infinispan.remoting.responses.SelfDeliverFilter;
import org.infinispan.remoting.responses.TotalOrderResponseFilter;
import org.infinispan.remoting.rpc.ResponseFilter;
import org.infinispan.remoting.rpc.ResponseMode;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.statetransfer.StateConsumer;
import org.infinispan.transaction.LocalTransaction;
import org.infinispan.util.concurrent.TimeoutException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Acts as a base for all RPC calls
 *
 * @author <a href="mailto:manik@jboss.org">Manik Surtani (manik@jboss.org)</a>
 * @author Mircea.Markus@jboss.com
 * @since 4.0
 */
public abstract class BaseRpcInterceptor extends CommandInterceptor {

   protected RpcManager rpcManager;
   private StateConsumer stateConsumer;

   protected boolean defaultSynchronous;

   @Inject
   public void inject(RpcManager rpcManager, StateConsumer stateConsumer) {
      this.rpcManager = rpcManager;
      this.stateConsumer = stateConsumer;
   }

   @Start
   public void init() {
      defaultSynchronous = cacheConfiguration.clustering().cacheMode().isSynchronous();
   }

   protected final boolean isSynchronous(FlagAffectedCommand command) {
      if (command.hasFlag(Flag.FORCE_SYNCHRONOUS))
         return true;
      else if (command.hasFlag(Flag.FORCE_ASYNCHRONOUS))
         return false;

      return defaultSynchronous;
   }

   protected final boolean isLocalModeForced(FlagAffectedCommand command) {
      if (command.hasFlag(Flag.CACHE_MODE_LOCAL)) {
         if (getLog().isTraceEnabled()) getLog().trace("LOCAL mode forced on invocation.  Suppressing clustered events.");
         return true;
      }
      return false;
   }

   protected boolean shouldInvokeRemoteTxCommand(TxInvocationContext ctx) {
      if (!ctx.isOriginLocal()) {
         return false;
      }

      // just testing for empty modifications isn't enough - the Lock API may acquire locks on keys but won't
      // register a Modification.  See ISPN-711.
      LocalTxInvocationContext localCtx = (LocalTxInvocationContext) ctx;
      boolean shouldInvokeRemotely = ctx.hasModifications() || !localCtx.getRemoteLocksAcquired().isEmpty() ||
         localCtx.getCacheTransaction().getTopologyId() != rpcManager.getTopologyId();

      if (getLog().isTraceEnabled()) {
         getLog().tracef("Should invoke remotely? %b. hasModifications=%b, hasRemoteLocksAcquired=%b",
               shouldInvokeRemotely, ctx.hasModifications(), !localCtx.getRemoteLocksAcquired().isEmpty());
      }

      boolean fromStateTransfer = ((LocalTransaction) ctx.getCacheTransaction()).isFromStateTransfer();

      return shouldInvokeRemotely && !fromStateTransfer;
   }

   protected static void totalOrderTxPrepare(TxInvocationContext ctx) {
      if (ctx.isOriginLocal()) {
         ctx.getCacheTransaction().markPrepareSent();
      }
   }

   protected static void totalOrderTxCommit(TxInvocationContext ctx) {
      if (ctx.isOriginLocal()) {
         ((LocalTransaction)ctx.getCacheTransaction()).markCommitOrRollbackSent();
      }
   }

   protected static void totalOrderTxRollback(TxInvocationContext ctx) {
      if (ctx.isOriginLocal()) {
         ((LocalTransaction)ctx.getCacheTransaction()).markCommitOrRollbackSent();
      }
   }

   protected static boolean shouldTotalOrderRollbackBeInvokedRemotely(TxInvocationContext ctx) {
      return ctx.isOriginLocal() && ((LocalTransaction)ctx.getCacheTransaction()).isPrepareSent()
            && !((LocalTransaction)ctx.getCacheTransaction()).isCommitOrRollbackSent();
   }

   protected final Map<Address, Response> totalOrderAnycastPrepare(Collection<Address> recipients,
                                                                   PrepareCommand prepareCommand,
                                                                   TotalOrderResponseFilter responseFilter) {
      Set<Address> realRecipients = new HashSet<Address>(recipients);
      realRecipients.add(rpcManager.getAddress());
      return internalTotalOrderPrepare(realRecipients, prepareCommand, responseFilter);
   }

   protected final Map<Address, Response> totalOrderBroadcastPrepare(PrepareCommand prepareCommand,
                                                                     TotalOrderResponseFilter responseFilter) {
      return internalTotalOrderPrepare(null, prepareCommand, responseFilter);
   }

   private Map<Address, Response> internalTotalOrderPrepare(Collection<Address> recipients, PrepareCommand prepareCommand,
                                                            TotalOrderResponseFilter responseFilter) {
      if (defaultSynchronous && responseFilter == null) {
         return rpcManager.invokeRemotely(recipients, prepareCommand, ResponseMode.SYNCHRONOUS_IGNORE_LEAVERS,
                                          getReplicationTimeout(), true, null, true);
      } else if (defaultSynchronous) {
         Map<Address, Response> responseMap = rpcManager.invokeRemotely(recipients, prepareCommand,
                                                                        ResponseMode.SYNCHRONOUS_IGNORE_LEAVERS,
                                                                        getReplicationTimeout(), true, responseFilter, true);
         responseFilter.validate();
         return responseMap;
      } else {
         return rpcManager.invokeRemotely(recipients, prepareCommand, false, true);
      }
   }

   protected final long getReplicationTimeout() {
      return cacheConfiguration.clustering().sync().replTimeout();
   }

   protected final boolean isSyncCommitPhase() {
      return cacheConfiguration.transaction().syncCommitPhase();
   }

   protected final TotalOrderResponseFilter getSelfDeliverFilter() {
      return new SelfDeliverFilter(rpcManager.getAddress());
   }
}
