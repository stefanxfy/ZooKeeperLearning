/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.server.quorum;

import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.RequestProcessor;
import org.apache.zookeeper.server.ServerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a very simple RequestProcessor that simply forwards a request from a
 * previous stage to the leader as an ACK.
 */
class AckRequestProcessor implements RequestProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AckRequestProcessor.class);
    Leader leader;

    AckRequestProcessor(Leader leader) {
        this.leader = leader;
    }

    /**
     * Forward the request as an ACK to the leader
     */
    public void processRequest(Request request) {
        // AckRequestProcessor处理器是Leader特有的处理器，其主要负责在SyncRequest Processor处理器完成事务日志记录后，
        // 向Proposal的投票收集器发送ACK反馈，以通知投票收集器当前服务器已经完成了对该Proposal的事务日志记录。
        QuorumPeer self = leader.self;
        if (self != null) {
            request.logLatency(ServerMetrics.getMetrics().PROPOSAL_ACK_CREATION_LATENCY);
            leader.processAck(self.getId(), request.zxid, null);
        } else {
            LOG.error("Null QuorumPeer");
        }
    }

    public void shutdown() {
        // TODO No need to do anything
    }

}
