/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.channel.socket.oio;

import io.netty.channel.AbstractChannelSink;
import io.netty.channel.Channel;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.Worker;

public abstract class AbstractOioChannelSink extends AbstractChannelSink {

    @Override
    public void fireUpstreamEventLater(final ChannelPipeline pipeline, final ChannelEvent e) throws Exception {
        Channel ch = e.getChannel();
        if (ch instanceof AbstractOioChannel) {
            AbstractOioChannel channel = (AbstractOioChannel) ch;
            Worker worker = channel.worker;
            if (worker != null && channel.workerThread != Thread.currentThread()) {
                channel.worker.executeInIoThread(new Runnable() {
                    
                    @Override
                    public void run() {
                        pipeline.sendUpstream(e);
                    }
                });
            } else {
                // no worker thread yet or the current thread is a worker thread so just fire the event now
                pipeline.sendUpstream(e);
            }
           
        } else {
            super.fireUpstreamEventLater(pipeline, e);
        }

    }

    @Override
    protected boolean isFireExceptionCaughtLater(ChannelEvent event, Throwable actualCause) {
        Channel channel = event.getChannel();
        boolean fireLater = false;
        if (channel instanceof AbstractOioChannel) {
            fireLater = !AbstractOioWorker.isIoThead((AbstractOioChannel) channel);
        }
        return fireLater;
    }

}
