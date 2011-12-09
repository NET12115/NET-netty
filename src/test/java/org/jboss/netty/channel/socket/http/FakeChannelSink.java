/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.channel.socket.http;

import java.util.LinkedList;
import java.util.Queue;

import io.netty.channel.AbstractChannelSink;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelPipeline;

/**
 * A fake channel sink for use in testing
 * 
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 */
public class FakeChannelSink extends AbstractChannelSink {

    public Queue<ChannelEvent> events = new LinkedList<ChannelEvent>();

    @Override
    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e)
            throws Exception {
        events.add(e);
    }

}
