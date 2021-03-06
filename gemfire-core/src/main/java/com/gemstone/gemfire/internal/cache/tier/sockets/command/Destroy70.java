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
/**
 * 
 */
package com.gemstone.gemfire.internal.cache.tier.sockets.command;

import com.gemstone.gemfire.cache.client.internal.DestroyOp;
import com.gemstone.gemfire.internal.cache.PartitionedRegion;
import com.gemstone.gemfire.internal.cache.tier.Command;
import com.gemstone.gemfire.internal.cache.tier.MessageType;
import com.gemstone.gemfire.internal.cache.tier.sockets.Message;
import com.gemstone.gemfire.internal.cache.tier.sockets.ServerConnection;
import com.gemstone.gemfire.internal.cache.versions.VersionTag;

import java.io.IOException;

/**
 * @author bruces
 *
 */
public class Destroy70 extends Destroy65 {
  private final static Destroy70 singleton = new Destroy70();

  public static Command getCommand() {
    return singleton;
  }

  private Destroy70() {
  }

  @Override
  protected void writeReplyWithRefreshMetadata(Message origMsg,
      ServerConnection servConn, PartitionedRegion pr,
      boolean entryNotFoundForRemove, byte nwHop, VersionTag versionTag) throws IOException {
    Message replyMsg = servConn.getReplyMessage();
    servConn.getCache().getCancelCriterion().checkCancelInProgress(null);
    replyMsg.setMessageType(MessageType.REPLY);
    int flags = 0;
    int numParts = 3;
    if (versionTag != null) {
      flags |= DestroyOp.HAS_VERSION_TAG;
      numParts++;
    }
    flags |= DestroyOp.HAS_ENTRY_NOT_FOUND_PART;
//    if (logger.fineEnabled()) {
//      logger.fine("writing response with metadata and " + numParts + " parts");
//    }
    replyMsg.setNumberOfParts(numParts);
    replyMsg.setTransactionId(origMsg.getTransactionId());
    replyMsg.addIntPart(flags);
    if (versionTag != null) {
      replyMsg.addObjPart(versionTag);
    }
    replyMsg.addBytesPart(new byte[]{pr.getMetadataVersion().byteValue(), nwHop});
    replyMsg.addIntPart(entryNotFoundForRemove? 1 : 0);
    pr.getPrStats().incPRMetaDataSentCount();
    replyMsg.send(servConn);
    if (logger.finerEnabled()) {
      logger.finer(servConn.getName() + ": rpl with REFRESH_METADAT tx: "
          + origMsg.getTransactionId());
    }
  }

  @Override
  protected void writeReply(Message origMsg, ServerConnection servConn,
      boolean entryNotFound, VersionTag versionTag)
  throws IOException {
    if (logger.fineEnabled()) {
      logger.fine("Destroy70.writeReply(entryNotFound="+entryNotFound+", tag="+versionTag+")");
    }
    Message replyMsg = servConn.getReplyMessage();
    servConn.getCache().getCancelCriterion().checkCancelInProgress(null);
    replyMsg.setMessageType(MessageType.REPLY);
    int flags = 0;
    int numParts = 3;
    if (versionTag != null) {
      flags |= DestroyOp.HAS_VERSION_TAG;
      numParts++;
    }
    flags |= DestroyOp.HAS_ENTRY_NOT_FOUND_PART;
//    if (logger.fineEnabled()) {
//      logger.fine("writing response with 1-byte metadata and " + numParts + " parts");
//    }
    replyMsg.setNumberOfParts(numParts);
    replyMsg.setTransactionId(origMsg.getTransactionId());
    replyMsg.addIntPart(flags);
    if (versionTag != null) {
//      if (logger.fineEnabled()) {
//        logger.fine("wrote version tag in response: " + versionTag);
//      }
      replyMsg.addObjPart(versionTag);
//    } else {
//      if (logger.fineEnabled()) {
//        logger.fine("response has no version tag");
//    }
    }
    replyMsg.addBytesPart(OK_BYTES); // make old single-hop code happy by puting byte[]{0} here
    replyMsg.addIntPart(entryNotFound? 1 : 0);
    replyMsg.send(servConn);
    if (logger.finerEnabled()) {
      logger.finer(servConn.getName() + ": rpl tx: "
          + origMsg.getTransactionId() + " parts=" + replyMsg.getNumberOfParts());
    }
  }
}
