/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package com.gemstone.gemfire.internal.cache.tier.sockets;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.distributed.internal.DistributionManager;
import com.gemstone.gemfire.distributed.internal.PooledDistributionMessage;
import com.gemstone.gemfire.i18n.LogWriterI18n;
import com.gemstone.gemfire.internal.cache.BridgeServerImpl;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
/**
 * Distribution message for dropping client from blacklist.
 * 
 * @since 6.0
 *
 */
public class RemoveClientFromBlacklistMessage extends PooledDistributionMessage {

  //The proxy id of the client represented by this proxy
  private ClientProxyMembershipID proxyID;
  
  @Override
  protected void process(DistributionManager dm) {
    final Cache cache;
    try {
      // use GemFireCache.getInstance to avoid blocking during cache.xml
      // processing.
      cache = GemFireCacheImpl.getInstance();
    }
    catch (Exception ignore) {
      DistributedSystem ds = dm.getSystem();
      if (ds != null) {
        LogWriterI18n logger = ds.getLogWriter().convertToLogWriterI18n();
        if (logger.finestEnabled()) {
          logger
              .finest(
                  "The node does not contain cache & so QDM Message will return . ",
                  ignore);
        }
      }
      return;
    }

    Cache c = GemFireCacheImpl.getInstance();
    if (c != null) {
      List l = c.getCacheServers();
      if (l != null) {
        Iterator i = l.iterator();
        while (i.hasNext()) {
          BridgeServerImpl bs = (BridgeServerImpl)i.next();
          CacheClientNotifier ccn = bs.getAcceptor().getCacheClientNotifier();
          Set s = ccn.getBlacklistedClient();
          if (s != null) {
            if(s.remove(proxyID)){          
            DistributedSystem ds = dm.getSystem();
            if (ds != null) {
              LogWriterI18n logger = ds.getLogWriter().convertToLogWriterI18n();
              if (logger.fineEnabled()) {
                logger.fine(" Remove the client from black list as its queue is already destroyed : "+
                    proxyID);
              }
            }
           }
        }
      }
    }
   }   
  }

  public RemoveClientFromBlacklistMessage() {
    this.setRecipient(ALL_RECIPIENTS);
  }

  public void setProxyID(ClientProxyMembershipID proxyID) {
    this.proxyID = proxyID;
  }

  @Override
  public void toData(DataOutput out) throws IOException {
    super.toData(out);
    DataSerializer.writeObject(proxyID, out);
  }

  public int getDSFID() {
    return REMOVE_CLIENT_FROM_BLACKLIST_MESSAGE;
  }

  @Override
  public void fromData(DataInput in) throws IOException, ClassNotFoundException {
    super.fromData(in);
    proxyID = ClientProxyMembershipID.readCanonicalized(in);
  }  
  
}  