/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

package org.infinispan.commands.tx.totalorder;

import org.infinispan.commands.tx.RollbackCommand;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.impl.RemoteTxInvocationContext;
import org.infinispan.transaction.RemoteTransaction;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * The 2nd phase command of 2PC, used when a transaction must be aborted. This implementation is used when Total Order
 * based protocol is used
 *
 * @author Pedro Ruivo
 * @since 5.3
 */
public class TotalOrderRollbackCommand extends RollbackCommand {

   public static final byte COMMAND_ID = 37;
   private static final Log log = LogFactory.getLog(TotalOrderRollbackCommand.class);

   public TotalOrderRollbackCommand(String cacheName, GlobalTransaction globalTransaction) {
      super(cacheName, globalTransaction);
   }

   public TotalOrderRollbackCommand(String cacheName) {
      super(cacheName);
   }

   private TotalOrderRollbackCommand() {
      super(null); // For command id uniqueness test
   }

   @Override
   public byte getCommandId() {
      return COMMAND_ID;
   }

   @Override
   protected RemoteTransaction getRemoteTransaction() {
      return txTable.getOrCreateRemoteTransaction(globalTx, null);
   }
}
