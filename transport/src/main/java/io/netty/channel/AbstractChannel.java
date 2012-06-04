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

import io.netty.buffer.ChannelBuffer;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.DefaultAttributeMap;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A skeletal {@link Channel} implementation.
 */
public abstract class AbstractChannel extends DefaultAttributeMap implements Channel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractChannel.class);

    static final ConcurrentMap<Integer, Channel> allChannels = new ConcurrentHashMap<Integer, Channel>();

    /**
     * Generates a negative unique integer ID.  This method generates only
     * negative integers to avoid conflicts with user-specified IDs where only
     * non-negative integers are allowed.
     */
    private static Integer allocateId(Channel channel) {
        int idVal = System.identityHashCode(channel);
        if (idVal > 0) {
            idVal = -idVal;
        } else if (idVal == 0) {
            idVal = -1;
        }

        Integer id;
        for (;;) {
            id = Integer.valueOf(idVal);
            // Loop until a unique ID is acquired.
            // It should be found in one loop practically.
            if (allChannels.putIfAbsent(id, channel) == null) {
                // Successfully acquired.
                return id;
            } else {
                // Taken by other channel at almost the same moment.
                idVal --;
                if (idVal >= 0) {
                    idVal = -1;
                }
            }
        }
    }

    private final Channel parent;
    private final Integer id;
    private final Unsafe unsafe;
    private final DefaultChannelPipeline pipeline;
    private final ChannelFuture succeededFuture = new SucceededChannelFuture(this);
    private final ChannelFuture voidFuture = new VoidChannelFuture(this);
    private final CloseFuture closeFuture = new CloseFuture(this);

    private volatile SocketAddress localAddress;
    private volatile SocketAddress remoteAddress;
    private volatile EventLoop eventLoop;
    private volatile boolean registered;

    private ClosedChannelException closedChannelException;
    private final Deque<FlushCheckpoint> flushCheckpoints = new ArrayDeque<FlushCheckpoint>();
    private long writeCounter;
    private boolean inFlushNow;
    private boolean flushNowPending;

    /** Cache for the string representation of this channel */
    private boolean strValActive;
    private String strVal;

    /**
     * Creates a new instance.
     *
     * @param id
     *        the unique non-negative integer ID of this channel.
     *        Specify {@code null} to auto-generate a unique negative integer
     *        ID.
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     */
    @SuppressWarnings("unchecked")
    protected AbstractChannel(Channel parent, Integer id, ChannelBufferHolder<?> outboundBuffer) {
        if (outboundBuffer == null) {
            throw new NullPointerException("outboundBuffer");
        }

        if (id == null) {
            id = allocateId(this);
        } else {
            if (id.intValue() < 0) {
                throw new IllegalArgumentException("id: " + id + " (expected: >= 0)");
            }
            if (allChannels.putIfAbsent(id, this) != null) {
                throw new IllegalArgumentException("duplicate ID: " + id);
            }
        }

        this.parent = parent;
        this.id = id;
        unsafe = newUnsafe();
        pipeline = new DefaultChannelPipeline(this);

        closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                allChannels.remove(id());
            }
        });

    }

    @Override
    public final Integer id() {
        return id;
    }

    @Override
    public Channel parent() {
        return parent;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public EventLoop eventLoop() {
        EventLoop eventLoop = this.eventLoop;
        if (eventLoop == null) {
            throw new IllegalStateException("channel not registered to an event loop");
        }
        return eventLoop;
    }

    @Override
    public SocketAddress localAddress() {
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                this.localAddress = localAddress = unsafe().localAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return localAddress;
    }

    protected void invalidateLocalAddress() {
        localAddress = null;
    }

    @Override
    public SocketAddress remoteAddress() {
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress = unsafe().remoteAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }

    protected void invalidateRemoteAddress() {
        remoteAddress = null;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return pipeline.bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return pipeline.connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline.connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return pipeline.disconnect();
    }

    @Override
    public ChannelFuture close() {
        return pipeline.close();
    }

    @Override
    public ChannelFuture deregister() {
        return pipeline.deregister();
    }

    @Override
    public ChannelFuture flush() {
        return pipeline.flush();
    }

    @Override
    public ChannelFuture write(Object message) {
        return pipeline.write(message);
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelFuture future) {
        return pipeline.bind(localAddress, future);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelFuture future) {
        return pipeline.connect(remoteAddress, future);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) {
        return pipeline.connect(remoteAddress, localAddress, future);
    }

    @Override
    public ChannelFuture disconnect(ChannelFuture future) {
        return pipeline.disconnect(future);
    }

    @Override
    public ChannelFuture close(ChannelFuture future) {
        return pipeline.close(future);
    }

    @Override
    public ChannelFuture deregister(ChannelFuture future) {
        return pipeline.deregister(future);
    }

    @Override
    public ChannelBuffer outboundByteBuffer() {
        return pipeline.outboundByteBuffer();
    }

    @Override
    public Queue<Object> outboundMessageBuffer() {
        return pipeline.outboundMessageBuffer();
    }

    @Override
    public ChannelFuture flush(ChannelFuture future) {
        return pipeline.flush(future);
    }

    @Override
    public ChannelFuture write(Object message, ChannelFuture future) {
        return pipeline.write(message, future);
    }

    @Override
    public ChannelFuture newFuture() {
        return new DefaultChannelFuture(this, false);
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return succeededFuture;
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return new FailedChannelFuture(this, cause);
    }

    @Override
    public ChannelFuture closeFuture() {
        return closeFuture;
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    protected abstract Unsafe newUnsafe();

    /**
     * Returns the {@linkplain System#identityHashCode(Object) identity hash code}
     * of this channel.
     */
    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Returns {@code true} if and only if the specified object is identical
     * with this channel (i.e: {@code this == o}).
     */
    @Override
    public final boolean equals(Object o) {
        return this == o;
    }

    /**
     * Compares the {@linkplain #id() ID} of the two channels.
     */
    @Override
    public final int compareTo(Channel o) {
        return id().compareTo(o.id());
    }

    /**
     * Returns the {@link String} representation of this channel.  The returned
     * string contains the {@linkplain #id() ID}, {@linkplain #localAddress() local address},
     * and {@linkplain #remoteAddress() remote address} of this channel for
     * easier identification.
     */
    @Override
    public String toString() {
        boolean active = isActive();
        if (strValActive == active && strVal != null) {
            return strVal;
        }

        SocketAddress remoteAddr = remoteAddress();
        SocketAddress localAddr = localAddress();
        if (remoteAddr != null) {
            SocketAddress srcAddr;
            SocketAddress dstAddr;
            if (parent == null) {
                srcAddr = localAddr;
                dstAddr = remoteAddr;
            } else {
                srcAddr = remoteAddr;
                dstAddr = localAddr;
            }
            strVal = String.format("[id: 0x%08x, %s %s %s]", id, srcAddr, active? "=>" : ":>", dstAddr);
        } else if (localAddr != null) {
            strVal = String.format("[id: 0x%08x, %s]", id, localAddr);
        } else {
            strVal = String.format("[id: 0x%08x]", id);
        }

        strValActive = active;
        return strVal;
    }

    protected abstract class AbstractUnsafe implements Unsafe {

        private final Runnable flushLaterTask = new FlushLater();

        @Override
        public final ChannelBufferHolder<Object> directOutbound() {
            return pipeline.directOutbound;
        }

        @Override
        public final ChannelFuture voidFuture() {
            return voidFuture;
        }

        @Override
        public final SocketAddress localAddress() {
            return localAddress0();
        }

        @Override
        public final SocketAddress remoteAddress() {
            return remoteAddress0();
        }

        @Override
        public final void register(EventLoop eventLoop, ChannelFuture future) {
            if (eventLoop == null) {
                throw new NullPointerException("eventLoop");
            }
            if (isRegistered()) {
                throw new IllegalStateException("registered to an event loop already");
            }
            if (!isCompatible(eventLoop)) {
                throw new IllegalStateException("incompatible event loop type: " + eventLoop.getClass().getName());
            }

            AbstractChannel.this.eventLoop = eventLoop;

            assert eventLoop().inEventLoop();

            if (!ensureOpen(future)) {
                return;
            }

            try {
                Runnable postRegisterTask = doRegister();
                registered = true;
                future.setSuccess();
                pipeline.fireChannelRegistered();
                if (postRegisterTask != null) {
                    postRegisterTask.run();
                }
                if (isActive()) {
                    pipeline.fireChannelActive();
                }
            } catch (Throwable t) {
                // Close the channel directly to avoid FD leak.
                try {
                    doClose();
                } catch (Throwable t2) {
                    logger.warn("Failed to close a channel", t2);
                }

                future.setFailure(t);
                pipeline.fireExceptionCaught(t);
                closeFuture().setSuccess();
            }
        }

        @Override
        public final void bind(final SocketAddress localAddress, final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                if (!ensureOpen(future)) {
                    return;
                }

                try {
                    boolean wasActive = isActive();
                    doBind(localAddress);
                    future.setSuccess();
                    if (!wasActive && isActive()) {
                        pipeline.fireChannelActive();
                    }
                } catch (Throwable t) {
                    future.setFailure(t);
                    pipeline.fireExceptionCaught(t);
                    closeIfClosed();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        bind(localAddress, future);
                    }
                });
            }
        }

        @Override
        public final void disconnect(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                try {
                    boolean wasActive = isActive();
                    doDisconnect();
                    future.setSuccess();
                    if (wasActive && !isActive()) {
                        pipeline.fireChannelInactive();
                    }
                } catch (Throwable t) {
                    future.setFailure(t);
                    closeIfClosed();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        disconnect(future);
                    }
                });
            }
        }

        @Override
        public final void close(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                if (closeFuture.setClosed()) {
                    boolean wasActive = isActive();
                    try {
                        doClose();
                        future.setSuccess();
                    } catch (Throwable t) {
                        future.setFailure(t);
                    }

                    if (closedChannelException != null) {
                        closedChannelException = new ClosedChannelException();
                    }

                    notifyFlushFutures(closedChannelException);

                    if (wasActive && !isActive()) {
                        pipeline.fireChannelInactive();
                    }

                    deregister(voidFuture());
                } else {
                    // Closed already.
                    future.setSuccess();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        close(future);
                    }
                });
            }
        }

        @Override
        public final void deregister(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                if (!registered) {
                    future.setSuccess();
                    return;
                }

                try {
                    doDeregister();
                } catch (Throwable t) {
                    logger.warn("Unexpected exception occurred while deregistering a channel.", t);
                } finally {
                    if (registered) {
                        registered = false;
                        future.setSuccess();
                        pipeline.fireChannelUnregistered();
                    } else {
                        // Some transports like local and AIO does not allow the deregistration of
                        // an open channel.  Their doDeregister() calls close().  Consequently,
                        // close() calls deregister() again - no need to fire channelUnregistered.
                        future.setSuccess();
                    }
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        deregister(future);
                    }
                });
            }
        }

        @Override
        public void flush(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                // Append flush future to the notification list.
                if (future != voidFuture) {
                    long checkpoint = writeCounter + directOutbound().size();
                    if (future instanceof FlushCheckpoint) {
                        FlushCheckpoint cp = (FlushCheckpoint) future;
                        cp.flushCheckpoint(checkpoint);
                        flushCheckpoints.add(cp);
                    } else {
                        flushCheckpoints.add(new DefaultFlushCheckpoint(checkpoint, future));
                    }
                }

                if (!inFlushNow) { // Avoid re-entrance
                    try {
                        if (!isFlushPending()) {
                            flushNow();
                        } else {
                            // Event loop will call flushNow() later by itself.
                        }
                    } catch (Throwable t) {
                        notifyFlushFutures(t);
                        pipeline.fireExceptionCaught(t);
                        if (t instanceof IOException) {
                            close(voidFuture());
                        }
                    } finally {
                        if (!isActive()) {
                            close(unsafe().voidFuture());
                        }
                    }
                } else {
                    if (!flushNowPending) {
                        flushNowPending = true;
                        eventLoop().execute(flushLaterTask);
                    }
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        flush(future);
                    }
                });
            }
        }

        @Override
        public final void flushNow() {
            if (inFlushNow) {
                return;
            }

            inFlushNow = true;
            final ChannelBufferHolder<Object> out = directOutbound();
            try {
                Throwable cause = null;
                int oldSize = out.size();
                try {
                    doFlush(out);
                } catch (Throwable t) {
                    cause = t;
                } finally {
                    final int newSize = out.size();
                    writeCounter += oldSize - newSize;
                    if (newSize == 0 && out.hasByteBuffer()) {
                        out.byteBuffer().discardReadBytes();
                    }
                }

                if (cause == null) {
                    notifyFlushFutures();
                } else {
                    notifyFlushFutures(cause);
                    pipeline.fireExceptionCaught(cause);
                    if (cause instanceof IOException) {
                        close(voidFuture());
                    }
                }
            } finally {
                inFlushNow = false;
            }
        }

        protected final boolean ensureOpen(ChannelFuture future) {
            if (isOpen()) {
                return true;
            }

            Exception e = new ClosedChannelException();
            future.setFailure(e);
            pipeline.fireExceptionCaught(e);
            return false;
        }

        protected final void closeIfClosed() {
            if (isOpen()) {
                return;
            }
            close(voidFuture());
        }
    }

    private class FlushLater implements Runnable {
        @Override
        public void run() {
            flushNowPending = false;
            unsafe().flush(voidFuture);
        }
    }

    protected abstract boolean isCompatible(EventLoop loop);

    protected abstract SocketAddress localAddress0();
    protected abstract SocketAddress remoteAddress0();

    protected abstract Runnable doRegister() throws Exception;
    protected abstract void doBind(SocketAddress localAddress) throws Exception;
    protected abstract void doDisconnect() throws Exception;
    protected abstract void doClose() throws Exception;
    protected abstract void doDeregister() throws Exception;
    protected abstract void doFlush(ChannelBufferHolder<Object> buf) throws Exception;

    protected abstract boolean isFlushPending();

    private void notifyFlushFutures() {
        if (flushCheckpoints.isEmpty()) {
            return;
        }

        final long writeCounter = AbstractChannel.this.writeCounter;
        for (;;) {
            FlushCheckpoint cp = flushCheckpoints.peek();
            if (cp == null) {
                // Reset the counter if there's nothing in the notification list.
                AbstractChannel.this.writeCounter = 0;
                break;
            }

            if (cp.flushCheckpoint() > writeCounter) {
                if (writeCounter > 0 && flushCheckpoints.size() == 1) {
                    AbstractChannel.this.writeCounter = 0;
                    cp.flushCheckpoint(cp.flushCheckpoint() - writeCounter);
                }
                break;
            }

            flushCheckpoints.remove();
            cp.future().setSuccess();
        }

        // Avoid overflow
        final long newWriteCounter = AbstractChannel.this.writeCounter;
        if (newWriteCounter >= 0x1000000000000000L) {
            // Reset the counter only when the counter grew pretty large
            // so that we can reduce the cost of updating all entries in the notification list.
            AbstractChannel.this.writeCounter = 0;
            for (FlushCheckpoint cp: flushCheckpoints) {
                cp.flushCheckpoint(cp.flushCheckpoint() - newWriteCounter);
            }
        }
    }

    private void notifyFlushFutures(Throwable cause) {
        notifyFlushFutures();
        for (;;) {
            FlushCheckpoint cp = flushCheckpoints.poll();
            if (cp == null) {
                break;
            }
            cp.future().setFailure(cause);
        }
    }

    static abstract class FlushCheckpoint {
        abstract long flushCheckpoint();
        abstract void flushCheckpoint(long checkpoint);
        abstract ChannelFuture future();
    }

    private static class DefaultFlushCheckpoint extends FlushCheckpoint {
        private long checkpoint;
        private final ChannelFuture future;

        DefaultFlushCheckpoint(long checkpoint, ChannelFuture future) {
            this.checkpoint = checkpoint;
            this.future = future;
        }

        @Override
        long flushCheckpoint() {
            return checkpoint;
        }

        @Override
        void flushCheckpoint(long checkpoint) {
            this.checkpoint = checkpoint;
        }

        @Override
        ChannelFuture future() {
            return future;
        }
    }

    private static final class CloseFuture extends DefaultChannelFuture implements ChannelFuture.Unsafe {

        CloseFuture(AbstractChannel ch) {
            super(ch, false);
        }

        @Override
        public boolean setSuccess() {
            throw new IllegalStateException();
        }

        @Override
        public boolean setFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        boolean setClosed() {
            boolean set = super.setSuccess();
            assert set;
            return set;
        }
    }
}
