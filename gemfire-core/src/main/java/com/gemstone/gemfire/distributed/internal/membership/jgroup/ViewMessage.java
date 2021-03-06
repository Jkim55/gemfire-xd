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
package com.gemstone.gemfire.distributed.internal.membership.jgroup;

import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.distributed.internal.membership.NetView;
import com.gemstone.gemfire.distributed.internal.DistributionManager;
import com.gemstone.gemfire.distributed.internal.SerialDistributionMessage;
import java.io.*;


/**
  ViewMessage is used to pass a new membership view to the GemFire cache
  in an orderly manner.  It is intended to be queued with serially
  executed messages so that the view takes effect at the proper time.
  
  @author Bruce Schuchardt
 */

public final class ViewMessage extends SerialDistributionMessage
{

  private JGroupMembershipManager manager;
  private long viewId;
  private NetView view;
  
  public ViewMessage(
    InternalDistributedMember addr,
    long viewId,
    NetView view,
    JGroupMembershipManager manager
    )
  {
    super();
    this.sender = addr;
    this.viewId = viewId;
    this.view = view;
    this.manager = manager;
  }
  
  @Override
  final public int getProcessorType() {
    return DistributionManager.VIEW_EXECUTOR;
  }


  @Override
  protected void process(DistributionManager dm) {
    //dm.getLogger().info("view message processed", new Exception());
    manager.processView(viewId, view);
  }

  // These "messages" are never DataSerialized 

  public int getDSFID() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void toData(DataOutput out) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void fromData(DataInput in) throws IOException, ClassNotFoundException {
    throw new UnsupportedOperationException();
  }
}    
