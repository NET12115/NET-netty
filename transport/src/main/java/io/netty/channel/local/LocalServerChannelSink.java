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
package io.netty.channel.local;

import static io.netty.channel.Channels.*;

import io.netty.channel.AbstractChannelSink;
import io.netty.channel.Channel;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelState;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.MessageEvent;

final class LocalServerChannelSink extends AbstractChannelSink {

    LocalServerChannelSink() {
    }

    @Override
    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        Channel channel = e.getChannel();
        if (channel instanceof DefaultLocalServerChannel) {
            handleServerChannel(e);
        } else if (channel instanceof DefaultLocalChannel) {
            handleAcceptedChannel(e);
        }
    }

    /**
     * Just fire the event now by calling {@link ChannelPipeline#sendUpstream(ChannelEvent)} as this implementation does not support it otherwise
     */
    @Override
    public void fireUpstreamEventLater(ChannelPipeline pipeline, ChannelEvent event) throws Exception {
        pipeline.sendUpstream(event);
    }
    
    private void handleServerChannel(ChannelEvent e) {
        if (!(e instanceof ChannelStateEvent)) {
            return;
        }

        ChannelStateEvent event = (ChannelStateEvent) e;
        DefaultLocalServerChannel channel =
              (DefaultLocalServerChannel) event.getChannel();
        ChannelFuture future = event.getFuture();
        ChannelState state = event.getState();
        Object value = event.getValue();
        switch (state) {
        case OPEN:
            if (Boolean.FALSE.equals(value)) {
                close(channel, future);
            }
            break;
        case BOUND:
            if (value != null) {
                bind(channel, future, (LocalAddress) value);
            } else {
                close(channel, future);
            }
            break;
        }
    }

    private void handleAcceptedChannel(ChannelEvent e) {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
            DefaultLocalChannel channel = (DefaultLocalChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            ChannelState state = event.getState();
            Object value = event.getValue();

            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    channel.closeNow(future);
                }
                break;
            case BOUND:
            case CONNECTED:
                if (value == null) {
                    channel.closeNow(future);
                }
                break;
            case INTEREST_OPS:
                // Unsupported - discard silently.
                future.setSuccess();
                break;
            }
        } else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            DefaultLocalChannel channel = (DefaultLocalChannel) event.getChannel();
            boolean offered = channel.writeBuffer.offer(event);
            assert offered;
            channel.flushWriteBuffer();
        }
    }

    private void bind(DefaultLocalServerChannel channel, ChannelFuture future, LocalAddress localAddress) {
        try {
            if (!LocalChannelRegistry.register(localAddress, channel)) {
                throw new ChannelException("address already in use: " + localAddress);
            }
            if (!channel.bound.compareAndSet(false, true)) {
                throw new ChannelException("already bound");
            }

            channel.localAddress = localAddress;
            future.setSuccess();
            fireChannelBound(channel, localAddress);
        } catch (Throwable t) {
            LocalChannelRegistry.unregister(localAddress);
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void close(DefaultLocalServerChannel channel, ChannelFuture future) {
        try {
            if (channel.setClosed()) {
                future.setSuccess();
                LocalAddress localAddress = channel.localAddress;
                if (channel.bound.compareAndSet(true, false)) {
                    channel.localAddress = null;
                    LocalChannelRegistry.unregister(localAddress);
                    fireChannelUnbound(channel);
                }
                fireChannelClosed(channel);
            } else {
                future.setSuccess();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }
}
