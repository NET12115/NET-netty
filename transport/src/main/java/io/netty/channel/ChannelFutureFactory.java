/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

/**
 * Factory which is responsible to create new {@link ChannelFuture}'s
 *
 */
public interface ChannelFutureFactory {

    /**
     * Create a new {@link ChannelFuture}
     */
    ChannelFuture newFuture();

    /**
     * Create a new {@link ChannelFuture} which is marked as successes already. So {@link ChannelFuture#isSuccess()}
     * will return <code>true</code>. All {@link ChannelFutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    ChannelFuture newSucceededFuture();

    /**
     * Create a new {@link ChannelFuture} which is marked as fakued already. So {@link ChannelFuture#isSuccess()}
     * will return <code>false</code>. All {@link ChannelFutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    ChannelFuture newFailedFuture(Throwable cause);
}
