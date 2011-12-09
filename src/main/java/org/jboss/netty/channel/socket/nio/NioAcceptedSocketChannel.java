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
package io.netty.channel.socket.nio;

import static io.netty.channel.Channels.*;

import java.nio.channels.SocketChannel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;

/**
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 */
final class NioAcceptedSocketChannel extends NioSocketChannel {

    final Thread bossThread;

    static NioAcceptedSocketChannel create(ChannelFactory factory,
            ChannelPipeline pipeline, Channel parent, ChannelSink sink,
            SocketChannel socket, NioWorker worker, Thread bossThread) {
        NioAcceptedSocketChannel instance = new NioAcceptedSocketChannel(
                factory, pipeline, parent, sink, socket, worker, bossThread);
        instance.setConnected();
        fireChannelOpen(instance);
        return instance;
    }
    
    private NioAcceptedSocketChannel(
            ChannelFactory factory, ChannelPipeline pipeline,
            Channel parent, ChannelSink sink,
            SocketChannel socket, NioWorker worker, Thread bossThread) {

        super(parent, factory, pipeline, sink, socket, worker);

        this.bossThread = bossThread;
    }
}
