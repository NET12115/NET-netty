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
package io.netty.channel;

import java.net.SocketAddress;

/**
 * A skeletal server-side {@link Channel} implementation.  A server-side
 * {@link Channel} does not allow the following operations:
 * <ul>
 * <li>{@link #connect(SocketAddress, ChannelFuture)}</li>
 * <li>{@link #disconnect(ChannelFuture)}</li>
 * <li>{@link #flush(ChannelFuture)}</li>
 * <li>and the shortcut methods which calls the methods mentioned above
 * </ul>
 */
public abstract class AbstractServerChannel extends AbstractChannel implements ServerChannel {

    /**
     * Creates a new instance.
     */
    protected AbstractServerChannel(Integer id) {
        super(null, id);
    }

    @Override
    public ChannelBufferHolder<Object> outbound() {
        return ChannelBufferHolders.discardBuffer();
    }

    @Override
    public SocketAddress remoteAddress() {
        return null;
    }

    @Override
    protected ChannelBufferHolder<Object> firstOut() {
        return ChannelBufferHolders.discardBuffer();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }
}
