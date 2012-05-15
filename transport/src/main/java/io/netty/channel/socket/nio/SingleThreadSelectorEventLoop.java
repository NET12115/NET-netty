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
package io.netty.channel.socket.nio;

import io.netty.channel.Channel;
import io.netty.channel.Channel.Unsafe;
import io.netty.channel.ChannelException;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

final class SingleThreadSelectorEventLoop extends SingleThreadEventLoop {

    /**
     * Internal Netty logger.
     */
    protected static final InternalLogger logger = InternalLoggerFactory
            .getInstance(SingleThreadSelectorEventLoop.class);

    static final int CLEANUP_INTERVAL = 256; // XXX Hard-coded value, but won't need customization.

    /**
     * The NIO {@link Selector}.
     */
    protected final Selector selector;

    /**
     * Boolean that controls determines if a blocked Selector.select should
     * break out of its selection process. In our case we use a timeone for
     * the select method and the select method will block for that time unless
     * waken up.
     */
    protected final AtomicBoolean wakenUp = new AtomicBoolean();

    int cancelledKeys;

    SingleThreadSelectorEventLoop(ThreadFactory threadFactory, SelectorProvider selectorProvider) {
        super(threadFactory);
        if (selectorProvider == null) {
            throw new NullPointerException("selectorProvider");
        }
        selector = openSelector(selectorProvider);
    }

    private static Selector openSelector(SelectorProvider provider) {
        try {
            return provider.openSelector();
        } catch (IOException e) {
            throw new ChannelException("failed to open a new selector", e);
        }
    }

    @Override
    protected void run() {
        Selector selector = this.selector;
        for (;;) {

            wakenUp.set(false);

            try {
                SelectorUtil.select(selector);

                // 'wakenUp.compareAndSet(false, true)' is always evaluated
                // before calling 'selector.wakeup()' to reduce the wake-up
                // overhead. (Selector.wakeup() is an expensive operation.)
                //
                // However, there is a race condition in this approach.
                // The race condition is triggered when 'wakenUp' is set to
                // true too early.
                //
                // 'wakenUp' is set to true too early if:
                // 1) Selector is waken up between 'wakenUp.set(false)' and
                //    'selector.select(...)'. (BAD)
                // 2) Selector is waken up between 'selector.select(...)' and
                //    'if (wakenUp.get()) { ... }'. (OK)
                //
                // In the first case, 'wakenUp' is set to true and the
                // following 'selector.select(...)' will wake up immediately.
                // Until 'wakenUp' is set to false again in the next round,
                // 'wakenUp.compareAndSet(false, true)' will fail, and therefore
                // any attempt to wake up the Selector will fail, too, causing
                // the following 'selector.select(...)' call to block
                // unnecessarily.
                //
                // To fix this problem, we wake up the selector again if wakenUp
                // is true immediately after selector.select(...).
                // It is inefficient in that it wakes up the selector for both
                // the first case (BAD - wake-up required) and the second case
                // (OK - no wake-up required).

                if (wakenUp.get()) {
                    selector.wakeup();
                }

                cancelledKeys = 0;
                processTaskQueue();
                processSelectedKeys();

                if (isShutdown()) {
                    closeAll();
                    break;
                }
            } catch (Throwable t) {
                logger.warn(
                        "Unexpected exception in the selector loop.", t);

                // Prevent possible consecutive immediate failures that lead to
                // excessive CPU consumption.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
        }
    }

    @Override
    protected void cleanup() {
        try {
            selector.close();
        } catch (IOException e) {
            logger.warn(
                    "Failed to close a selector.", e);
        }
    }

    private void processTaskQueue() {
        for (;;) {
            final Runnable task = pollTask();
            if (task == null) {
                break;
            }

            task.run();
            cleanUpCancelledKeys();
        }
    }

    private void processSelectedKeys() {
        for (Iterator<SelectionKey> i = selector.selectedKeys().iterator(); i.hasNext();) {
            final SelectionKey k = i.next();
            final Channel ch = (Channel) k.attachment();
            final Unsafe unsafe = ch.unsafe();
            boolean removeKey = true;
            try {
                int readyOps = k.readyOps();
                if ((readyOps & SelectionKey.OP_READ) != 0 || readyOps == 0) {
                    unsafe.read();
                    if (!ch.isOpen()) {
                        // Connection already closed - no need to handle write.
                        continue;
                    }
                }
                if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                    unsafe.flushForcibly();
                }
                if ((readyOps & SelectionKey.OP_ACCEPT) != 0) {
                    unsafe.read();
                }
                if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                    unsafe.finishConnect();
                }
            } catch (CancelledKeyException ignored) {
                unsafe.close(unsafe.voidFuture());
            } finally {
                if (removeKey) {
                    i.remove();
                }
            }

            if (cleanUpCancelledKeys()) {
                break; // break the loop to avoid ConcurrentModificationException
            }
        }
    }

    private boolean cleanUpCancelledKeys() {
        if (cancelledKeys >= CLEANUP_INTERVAL) {
            cancelledKeys = 0;
            SelectorUtil.cleanupKeys(selector);
            return true;
        }
        return false;
    }

    private void closeAll() {
        SelectorUtil.cleanupKeys(selector);
        Set<SelectionKey> keys = selector.keys();
        Collection<Channel> channels = new ArrayList<Channel>(keys.size());
        for (SelectionKey k: keys) {
            channels.add((Channel) k.attachment());
        }

        for (Channel ch: channels) {
            ch.unsafe().close(ch.unsafe().voidFuture());
        }
    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        if (wakenUp.compareAndSet(false, true)) {
            selector.wakeup();
        }
    }
}
