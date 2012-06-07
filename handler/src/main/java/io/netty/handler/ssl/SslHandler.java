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
package io.netty.handler.ssl;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelFuture;
import io.netty.handler.codec.StreamToStreamCodec;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

/**
 * Adds <a href="http://en.wikipedia.org/wiki/Transport_Layer_Security">SSL
 * &middot; TLS</a> and StartTLS support to a {@link Channel}.  Please refer
 * to the <strong>"SecureChat"</strong> example in the distribution or the web
 * site for the detailed usage.
 *
 * <h3>Beginning the handshake</h3>
 * <p>
 * You must make sure not to write a message while the
 * {@linkplain #handshake() handshake} is in progress unless you are
 * renegotiating.  You will be notified by the {@link ChannelFuture} which is
 * returned by the {@link #handshake()} method when the handshake
 * process succeeds or fails.
 *
 * <h3>Handshake</h3>
 * <p>
 * If {@link #isIssueHandshake()} is {@code false}
 * (default) you will need to take care of calling {@link #handshake()} by your own. In most situations were {@link SslHandler} is used in 'client mode'
 * you want to issue a handshake once the connection was established. if {@link #setIssueHandshake(boolean)} is set to <code>true</code> you don't need to
 * worry about this as the {@link SslHandler} will take care of it.
 * <p>
 *
 * <h3>Renegotiation</h3>
 * <p>
 * If {@link #isEnableRenegotiation() enableRenegotiation} is {@code true}
 * (default) and the initial handshake has been done successfully, you can call
 * {@link #handshake()} to trigger the renegotiation.
 * <p>
 * If {@link #isEnableRenegotiation() enableRenegotiation} is {@code false},
 * an attempt to trigger renegotiation will result in the connection closure.
 * <p>
 * Please note that TLS renegotiation had a security issue before.  If your
 * runtime environment did not fix it, please make sure to disable TLS
 * renegotiation by calling {@link #setEnableRenegotiation(boolean)} with
 * {@code false}.  For more information, please refer to the following documents:
 * <ul>
 *   <li><a href="http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2009-3555">CVE-2009-3555</a></li>
 *   <li><a href="http://www.ietf.org/rfc/rfc5746.txt">RFC5746</a></li>
 *   <li><a href="http://www.oracle.com/technetwork/java/javase/documentation/tlsreadme2-176330.html">Phased Approach to Fixing the TLS Renegotiation Issue</a></li>
 * </ul>
 *
 * <h3>Closing the session</h3>
 * <p>
 * To close the SSL session, the {@link #close()} method should be
 * called to send the {@code close_notify} message to the remote peer.  One
 * exception is when you close the {@link Channel} - {@link SslHandler}
 * intercepts the close request and send the {@code close_notify} message
 * before the channel closure automatically.  Once the SSL session is closed,
 * it is not reusable, and consequently you should create a new
 * {@link SslHandler} with a new {@link SSLEngine} as explained in the
 * following section.
 *
 * <h3>Restarting the session</h3>
 * <p>
 * To restart the SSL session, you must remove the existing closed
 * {@link SslHandler} from the {@link ChannelPipeline}, insert a new
 * {@link SslHandler} with a new {@link SSLEngine} into the pipeline,
 * and start the handshake process as described in the first section.
 *
 * <h3>Implementing StartTLS</h3>
 * <p>
 * <a href="http://en.wikipedia.org/wiki/STARTTLS">StartTLS</a> is the
 * communication pattern that secures the wire in the middle of the plaintext
 * connection.  Please note that it is different from SSL &middot; TLS, that
 * secures the wire from the beginning of the connection.  Typically, StartTLS
 * is composed of three steps:
 * <ol>
 * <li>Client sends a StartTLS request to server.</li>
 * <li>Server sends a StartTLS response to client.</li>
 * <li>Client begins SSL handshake.</li>
 * </ol>
 * If you implement a server, you need to:
 * <ol>
 * <li>create a new {@link SslHandler} instance with {@code startTls} flag set
 *     to {@code true},</li>
 * <li>insert the {@link SslHandler} to the {@link ChannelPipeline}, and</li>
 * <li>write a StartTLS response.</li>
 * </ol>
 * Please note that you must insert {@link SslHandler} <em>before</em> sending
 * the StartTLS response.  Otherwise the client can send begin SSL handshake
 * before {@link SslHandler} is inserted to the {@link ChannelPipeline}, causing
 * data corruption.
 * <p>
 * The client-side implementation is much simpler.
 * <ol>
 * <li>Write a StartTLS request,</li>
 * <li>wait for the StartTLS response,</li>
 * <li>create a new {@link SslHandler} instance with {@code startTls} flag set
 *     to {@code false},</li>
 * <li>insert the {@link SslHandler} to the {@link ChannelPipeline}, and</li>
 * <li>Initiate SSL handshake by calling {@link SslHandler#handshake()}.</li>
 * </ol>
 * @apiviz.landmark
 * @apiviz.uses io.netty.handler.ssl.SslBufferPool
 */
public class SslHandler extends StreamToStreamCodec {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(SslHandler.class);

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
            "^.*(?:connection.*reset|connection.*closed|broken.*pipe).*$",
            Pattern.CASE_INSENSITIVE);

    private static SslBufferPool defaultBufferPool;

    /**
     * Returns the default {@link SslBufferPool} used when no pool is
     * specified in the constructor.
     */
    public static synchronized SslBufferPool getDefaultBufferPool() {
        if (defaultBufferPool == null) {
            defaultBufferPool = new DefaultSslBufferPool();
        }
        return defaultBufferPool;
    }

    private ChannelHandlerContext ctx;
    private final SSLEngine engine;
    private final SslBufferPool bufferPool;
    private final Executor delegatedTaskExecutor;

    private final boolean startTls;
    private boolean sentFirstMessage;

    private volatile boolean enableRenegotiation = true;

    final Object handshakeLock = new Object();

    private boolean handshaking;
    private volatile boolean handshaken;
    private ChannelFuture handshakeFuture;

    private boolean sentCloseNotify;

    int ignoreClosedChannelException;
    final Object ignoreClosedChannelExceptionLock = new Object();
    private volatile boolean issueHandshake;

    private final SSLEngineInboundCloseFuture sslEngineCloseFuture = new SSLEngineInboundCloseFuture();

    private int packetLength = -1;

    /**
     * Creates a new instance.
     *
     * @param engine  the {@link SSLEngine} this handler will use
     */
    public SslHandler(SSLEngine engine) {
        this(engine, getDefaultBufferPool(), ImmediateExecutor.INSTANCE);
    }

    /**
     * Creates a new instance.
     *
     * @param engine      the {@link SSLEngine} this handler will use
     * @param bufferPool  the {@link SslBufferPool} where this handler will
     *                    acquire the buffers required by the {@link SSLEngine}
     */
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool) {
        this(engine, bufferPool, ImmediateExecutor.INSTANCE);
    }

    /**
     * Creates a new instance.
     *
     * @param engine    the {@link SSLEngine} this handler will use
     * @param startTls  {@code true} if the first write request shouldn't be
     *                  encrypted by the {@link SSLEngine}
     */
    public SslHandler(SSLEngine engine, boolean startTls) {
        this(engine, getDefaultBufferPool(), startTls);
    }

    /**
     * Creates a new instance.
     *
     * @param engine      the {@link SSLEngine} this handler will use
     * @param bufferPool  the {@link SslBufferPool} where this handler will
     *                    acquire the buffers required by the {@link SSLEngine}
     * @param startTls    {@code true} if the first write request shouldn't be
     *                    encrypted by the {@link SSLEngine}
     */
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool, boolean startTls) {
        this(engine, bufferPool, startTls, ImmediateExecutor.INSTANCE);
    }

    /**
     * Creates a new instance.
     *
     * @param engine
     *        the {@link SSLEngine} this handler will use
     * @param delegatedTaskExecutor
     *        the {@link Executor} which will execute the delegated task
     *        that {@link SSLEngine#getDelegatedTask()} will return
     */
    public SslHandler(SSLEngine engine, Executor delegatedTaskExecutor) {
        this(engine, getDefaultBufferPool(), delegatedTaskExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param engine
     *        the {@link SSLEngine} this handler will use
     * @param bufferPool
     *        the {@link SslBufferPool} where this handler will acquire
     *        the buffers required by the {@link SSLEngine}
     * @param delegatedTaskExecutor
     *        the {@link Executor} which will execute the delegated task
     *        that {@link SSLEngine#getDelegatedTask()} will return
     */
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool, Executor delegatedTaskExecutor) {
        this(engine, bufferPool, false, delegatedTaskExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param engine
     *        the {@link SSLEngine} this handler will use
     * @param startTls
     *        {@code true} if the first write request shouldn't be encrypted
     *        by the {@link SSLEngine}
     * @param delegatedTaskExecutor
     *        the {@link Executor} which will execute the delegated task
     *        that {@link SSLEngine#getDelegatedTask()} will return
     */
    public SslHandler(SSLEngine engine, boolean startTls, Executor delegatedTaskExecutor) {
        this(engine, getDefaultBufferPool(), startTls, delegatedTaskExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param engine
     *        the {@link SSLEngine} this handler will use
     * @param bufferPool
     *        the {@link SslBufferPool} where this handler will acquire
     *        the buffers required by the {@link SSLEngine}
     * @param startTls
     *        {@code true} if the first write request shouldn't be encrypted
     *        by the {@link SSLEngine}
     * @param delegatedTaskExecutor
     *        the {@link Executor} which will execute the delegated task
     *        that {@link SSLEngine#getDelegatedTask()} will return
     */
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool, boolean startTls, Executor delegatedTaskExecutor) {
        if (engine == null) {
            throw new NullPointerException("engine");
        }
        if (bufferPool == null) {
            throw new NullPointerException("bufferPool");
        }
        if (delegatedTaskExecutor == null) {
            throw new NullPointerException("delegatedTaskExecutor");
        }
        this.engine = engine;
        this.bufferPool = bufferPool;
        this.delegatedTaskExecutor = delegatedTaskExecutor;
        this.startTls = startTls;
    }

    /**
     * Returns the {@link SSLEngine} which is used by this handler.
     */
    public SSLEngine getEngine() {
        return engine;
    }

    /**
     * Starts an SSL / TLS handshake for the specified channel.
     *
     * @return a {@link ChannelFuture} which is notified when the handshake
     *         succeeds or fails.
     */
    public ChannelFuture handshake() {
        if (handshaken && !isEnableRenegotiation()) {
            throw new IllegalStateException("renegotiation disabled");
        }

        Channel channel = ctx.channel();
        Exception exception = null;

        synchronized (handshakeLock) {
            if (handshaking) {
                return handshakeFuture;
            } else {
                handshaking = true;
                if (ctx.executor().inEventLoop()) {
                    try {
                        engine.beginHandshake();
                        runDelegatedTasks();
                        wrapNonAppData(ctx, channel);

                    } catch (Exception e) {
                        exception = e;
                    }

                } else {
                    ctx.executor().execute(new Runnable() {

                        @Override
                        public void run() {
                            Throwable exception = null;
                            synchronized (handshakeLock) {
                                try {

                                    engine.beginHandshake();
                                    runDelegatedTasks();
                                    wrapNonAppData(ctx, ctx.channel());

                                } catch (Exception e) {
                                    exception = e;
                                }
                            }
                            if (exception != null) { // Failed to initiate handshake.
                                handshakeFuture.setFailure(exception);
                                ctx.fireExceptionCaught(exception);
                            }
                        }
                    });
                }

            }
        }

        if (exception != null) { // Failed to initiate handshake.
            handshakeFuture.setFailure(exception);
            ctx.fireExceptionCaught(exception);
        }

        return handshakeFuture;
    }

    /**
     * Sends an SSL {@code close_notify} message to the specified channel and
     * destroys the underlying {@link SSLEngine}.
     */
    public ChannelFuture close() {
        ChannelHandlerContext ctx = this.ctx;
        Channel channel = ctx.channel();
        try {
            engine.closeOutbound();
            return wrapNonAppData(ctx, channel);
        } catch (SSLException e) {
            ctx.fireExceptionCaught(e);
            return channel.newFailedFuture(e);
        }
    }

    /**
     * Returns {@code true} if and only if TLS renegotiation is enabled.
     */
    public boolean isEnableRenegotiation() {
        return enableRenegotiation;
    }

    /**
     * Enables or disables TLS renegotiation.
     */
    public void setEnableRenegotiation(boolean enableRenegotiation) {
        this.enableRenegotiation = enableRenegotiation;
    }


    /**
     * Enables or disables the automatic handshake once the {@link Channel} is connected. The value will only have affect if its set before the
     * {@link Channel} is connected.
     */
    public void setIssueHandshake(boolean issueHandshake) {
        this.issueHandshake = issueHandshake;
    }

    /**
     * Returns <code>true</code> if the automatic handshake is enabled
     */
    public boolean isIssueHandshake() {
        return issueHandshake;
    }

    /**
     * Return the {@link ChannelFuture} that will get notified if the inbound of the {@link SSLEngine} will get closed.
     *
     * This method will return the same {@link ChannelFuture} all the time.
     *
     * For more informations see the apidocs of {@link SSLEngine}
     *
     */
    public ChannelFuture getSSLEngineInboundCloseFuture() {
        return sslEngineCloseFuture;
    }

    @Override
    public void disconnect(final ChannelHandlerContext ctx,
            final ChannelFuture future) throws Exception {
        closeOutboundAndChannel(ctx, future, true);
    }

    @Override
    public void close(final ChannelHandlerContext ctx,
            final ChannelFuture future) throws Exception {
        closeOutboundAndChannel(ctx, future, false);
    }

    @Override
    public void encode(ChannelHandlerContext ctx, ChannelBuffer in, ChannelBuffer out) throws Exception {


        // Do not encrypt the first write request if this handler is
        // created with startTLS flag turned on.
        if (startTls && !sentFirstMessage) {
            sentFirstMessage = true;
            out.writeBytes(in);
            return;
        }

        ByteBuffer outNetBuf = bufferPool.acquireBuffer();
        boolean success = true;
        boolean needsUnwrap = false;
        try {
            ByteBuffer outAppBuf = in.nioBuffer();

            while (in.readable()) {

                int read;
                int remaining = outAppBuf.remaining();
                SSLEngineResult result = null;

                synchronized (handshakeLock) {
                    result = engine.wrap(outAppBuf, outNetBuf);
                }
                read = remaining - outAppBuf.remaining();
                in.readerIndex(in.readerIndex() + read);



                if (result.bytesProduced() > 0) {
                    outNetBuf.flip();
                    out.writeBytes(outNetBuf);
                    outNetBuf.clear();


                } else if (result.getStatus() == Status.CLOSED) {
                    // SSLEngine has been closed already.
                    // Any further write attempts should be denied.
                    success = false;
                    break;
                } else {
                    final HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                    handleRenegotiation(handshakeStatus);
                    switch (handshakeStatus) {
                    case NEED_WRAP:
                        if (outAppBuf.hasRemaining()) {
                            break;
                        } else {
                            break;
                        }
                    case NEED_UNWRAP:
                        needsUnwrap = true;
                    case NEED_TASK:
                        runDelegatedTasks();
                        break;
                    case FINISHED:
                    case NOT_HANDSHAKING:
                        if (handshakeStatus == HandshakeStatus.FINISHED) {
                            setHandshakeSuccess(ctx.channel());
                        }
                        if (result.getStatus() == Status.CLOSED) {
                            success = false;
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown handshake status: " + handshakeStatus);
                    }
                }

            }
        } catch (SSLException e) {
            success = false;
            setHandshakeFailure(ctx.channel(), e);
            throw e;
        } finally {
            bufferPool.releaseBuffer(outNetBuf);

            if (!success) {
                // mark all bytes as read
                in.readerIndex(in.readerIndex() + in.readableBytes());

                throw new IllegalStateException("SSLEngine already closed");


            }
        }

        if (needsUnwrap) {
            unwrap(ctx, ctx.channel(), ChannelBuffers.EMPTY_BUFFER, 0, 0, ChannelBuffers.EMPTY_BUFFER);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Make sure the handshake future is notified when a connection has
        // been closed during handshake.
        synchronized (handshakeLock) {
            if (handshaking) {
                handshakeFuture.setFailure(new ClosedChannelException());
            }
        }

        try {
            super.channelInactive(ctx);
        } finally {
            unwrap(ctx, ctx.channel(), ChannelBuffers.EMPTY_BUFFER, 0, 0, ChannelBuffers.EMPTY_BUFFER);
            engine.closeOutbound();
            if (!sentCloseNotify && handshaken) {
                try {
                    engine.closeInbound();
                } catch (SSLException ex) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Failed to clean up SSLEngine.", ex);
                    }
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof IOException) {
            if (cause instanceof ClosedChannelException) {
                synchronized (ignoreClosedChannelExceptionLock) {
                    if (ignoreClosedChannelException > 0) {
                        ignoreClosedChannelException --;
                        if (logger.isDebugEnabled()) {
                            logger.debug(
                                    "Swallowing an exception raised while " +
                                    "writing non-app data", cause);
                        }

                        return;
                    }
                }
            } else if (engine.isOutboundDone()) {
                String message = String.valueOf(cause.getMessage()).toLowerCase();
                if (IGNORABLE_ERROR_MESSAGE.matcher(message).matches()) {
                    // It is safe to ignore the 'connection reset by peer' or
                    // 'broken pipe' error after sending closure_notify.
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                "Swallowing a 'connection reset by peer / " +
                                "broken pipe' error occurred while writing " +
                                "'closure_notify'", cause);
                    }

                    // Close the connection explicitly just in case the transport
                    // did not close the connection automatically.
                    ctx.close(ctx.channel().newFuture());
                    return;
                }
            }
        }
        super.exceptionCaught(ctx, cause);
    }



    @Override
    public void decode(ChannelHandlerContext ctx, ChannelBuffer in, ChannelBuffer out) throws Exception {

        // check if the packet lenght was read before
        if (packetLength == -1) {
            if (in.readableBytes() < 5) {
                return;
            }
            // SSLv3 or TLS - Check ContentType
            boolean tls;
            switch (in.getUnsignedByte(in.readerIndex())) {
            case 20:  // change_cipher_spec
            case 21:  // alert
            case 22:  // handshake
            case 23:  // application_data
                tls = true;
                break;
            default:
                // SSLv2 or bad data
                tls = false;
            }

            if (tls) {
                // SSLv3 or TLS - Check ProtocolVersion
                int majorVersion = in.getUnsignedByte(in.readerIndex() + 1);
                if (majorVersion == 3) {
                    // SSLv3 or TLS
                    packetLength = (getShort(in, in.readerIndex() + 3) & 0xFFFF) + 5;
                    if (packetLength <= 5) {
                        // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                        tls = false;
                    }
                } else {
                    // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                    tls = false;
                }
            }

            if (!tls) {
                // SSLv2 or bad data - Check the version
                boolean sslv2 = true;
                int headerLength = (in.getUnsignedByte(
                    in.readerIndex()) & 0x80) != 0 ? 2 : 3;
                int majorVersion = in.getUnsignedByte(
                    in.readerIndex() + headerLength + 1);
                if (majorVersion == 2 || majorVersion == 3) {
                    // SSLv2
                    if (headerLength == 2) {
                        packetLength = (getShort(in, in.readerIndex()) & 0x7FFF) + 2;
                    } else {
                        packetLength = (getShort(in, in.readerIndex()) & 0x3FFF) + 3;
                    }
                    if (packetLength <= headerLength) {
                        sslv2 = false;
                    }
                } else {
                    sslv2 = false;
                }

                if (!sslv2) {
                    // Bad data - discard the buffer and raise an exception.
                    SSLException e = new SSLException(
                            "not an SSL/TLS record: " + ChannelBuffers.hexDump(in));
                    in.skipBytes(in.readableBytes());
                    throw e;
                }
            }

            assert packetLength > 0;
        }


        if (in.readableBytes() < packetLength) {
            // not enough bytes left to read the packet
            // so return here for now
            return;
        }

        // We advance the buffer's readerIndex before calling unwrap() because
        // unwrap() can trigger FrameDecoder call decode(), this method, recursively.
        // The recursive call results in decoding the same packet twice if
        // the readerIndex is advanced *after* decode().
        //
        // Here's an example:
        // 1) An SSL packet is received from the wire.
        // 2) SslHandler.decode() deciphers the packet and calls the user code.
        // 3) The user closes the channel in the same thread.
        // 4) The same thread triggers a channelDisconnected() event.
        // 5) FrameDecoder.cleanup() is called, and it calls SslHandler.decode().
        // 6) SslHandler.decode() will feed the same packet with what was
        //    deciphered at the step 2 again if the readerIndex was not advanced
        //    before calling the user code.
        final int packetOffset = in.readerIndex();
        in.skipBytes(packetLength);
        try {
            unwrap(ctx, ctx.channel(), in, packetOffset, packetLength, out);
        } finally {
            // reset packet length
            packetLength = -1;
        }
    }

    /**
     * Reads a big-endian short integer from the buffer.  Please note that we do not use
     * {@link ChannelBuffer#getShort(int)} because it might be a little-endian buffer.
     */
    private static short getShort(ChannelBuffer buf, int offset) {
        return (short) (buf.getByte(offset) << 8 | buf.getByte(offset + 1) & 0xFF);
    }

    private ChannelFuture wrapNonAppData(ChannelHandlerContext ctx, Channel channel) throws SSLException {
        ChannelFuture future = null;
        ByteBuffer outNetBuf = bufferPool.acquireBuffer();

        SSLEngineResult result;
        try {
            for (;;) {
                synchronized (handshakeLock) {
                    result = engine.wrap(EMPTY_BUFFER, outNetBuf);
                }

                if (result.bytesProduced() > 0) {
                    outNetBuf.flip();
                    ChannelBuffer msg = ChannelBuffers.buffer(outNetBuf.remaining());
                    // Transfer the bytes to the new ChannelBuffer using some safe method that will also
                    // work with "non" heap buffers
                    //
                    // See https://github.com/netty/netty/issues/329
                    msg.writeBytes(outNetBuf);
                    outNetBuf.clear();

                    future = channel.newFuture();
                    future.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future)
                                throws Exception {
                            if (future.cause() instanceof ClosedChannelException) {
                                synchronized (ignoreClosedChannelExceptionLock) {
                                    ignoreClosedChannelException ++;
                                }
                            }
                        }
                    });

                    ctx.write(msg, future);
                }

                final HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                handleRenegotiation(handshakeStatus);
                switch (handshakeStatus) {
                case FINISHED:
                    setHandshakeSuccess(channel);
                    runDelegatedTasks();
                    break;
                case NEED_TASK:
                    runDelegatedTasks();
                    break;
                case NEED_UNWRAP:
                    if (!Thread.holdsLock(handshakeLock)) {
                        // unwrap shouldn't be called when this method was
                        // called by unwrap - unwrap will keep running after
                        // this method returns.
                        unwrap(ctx, channel, ChannelBuffers.EMPTY_BUFFER, 0, 0, ChannelBuffers.EMPTY_BUFFER);
                    }
                    break;
                case NOT_HANDSHAKING:
                case NEED_WRAP:
                    break;
                default:
                    throw new IllegalStateException(
                            "Unexpected handshake status: " + handshakeStatus);
                }

                if (result.bytesProduced() == 0) {
                    break;
                }
            }
        } catch (SSLException e) {
            setHandshakeFailure(channel, e);
            throw e;
        } finally {
            bufferPool.releaseBuffer(outNetBuf);
        }

        if (future == null) {
            future = channel.newSucceededFuture();
        }

        return future;
    }

    private void unwrap(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, int offset, int length, ChannelBuffer out) throws SSLException {
        ByteBuffer inNetBuf = buffer.nioBuffer(offset, length);
        ByteBuffer outAppBuf = bufferPool.acquireBuffer();

        try {
            boolean needsWrap = false;
            loop:
            for (;;) {
                SSLEngineResult result;
                boolean needsHandshake = false;
                synchronized (handshakeLock) {
                    if (!handshaken && !handshaking &&
                        !engine.getUseClientMode() &&
                        !engine.isInboundDone() && !engine.isOutboundDone()) {
                        needsHandshake = true;

                    }
                }
                if (needsHandshake) {
                    handshake();
                }

                synchronized (handshakeLock) {
                    result = engine.unwrap(inNetBuf, outAppBuf);
                }

                // notify about the CLOSED state of the SSLEngine. See #137
                if (result.getStatus() == Status.CLOSED) {
                    sslEngineCloseFuture.setClosed();
                }

                final HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                handleRenegotiation(handshakeStatus);
                switch (handshakeStatus) {
                case NEED_UNWRAP:
                    if (inNetBuf.hasRemaining() && !engine.isInboundDone()) {
                        break;
                    } else {
                        break loop;
                    }
                case NEED_WRAP:
                    wrapNonAppData(ctx, channel);
                    break;
                case NEED_TASK:
                    runDelegatedTasks();
                    break;
                case FINISHED:
                    setHandshakeSuccess(channel);
                    needsWrap = true;
                    break loop;
                case NOT_HANDSHAKING:
                    needsWrap = true;
                    break loop;
                default:
                    throw new IllegalStateException(
                            "Unknown handshake status: " + handshakeStatus);
                }

            }

            if (needsWrap) {
                wrapNonAppData(ctx, channel);
            }

            outAppBuf.flip();

            if (outAppBuf.hasRemaining()) {
                // Transfer the bytes to the new ChannelBuffer using some safe method that will also
                // work with "non" heap buffers
                //
                // See https://github.com/netty/netty/issues/329
                out.writeBytes(outAppBuf);
            }
        } catch (SSLException e) {
            setHandshakeFailure(channel, e);
            throw e;
        } finally {
            bufferPool.releaseBuffer(outAppBuf);
        }
    }

    private void handleRenegotiation(HandshakeStatus handshakeStatus) {
        if (handshakeStatus == HandshakeStatus.NOT_HANDSHAKING ||
            handshakeStatus == HandshakeStatus.FINISHED) {
            // Not handshaking
            return;
        }

        if (!handshaken) {
            // Not renegotiation
            return;
        }

        final boolean renegotiate;
        synchronized (handshakeLock) {
            if (handshaking) {
                // Renegotiation in progress or failed already.
                // i.e. Renegotiation check has been done already below.
                return;
            }

            if (engine.isInboundDone() || engine.isOutboundDone()) {
                // Not handshaking but closing.
                return;
            }

            if (isEnableRenegotiation()) {
                // Continue renegotiation.
                renegotiate = true;
            } else {
                // Do not renegotiate.
                renegotiate = false;
                // Prevent reentrance of this method.
                handshaking = true;
            }
        }

        if (renegotiate) {
            // Renegotiate.
            handshake();
        } else {
            // Raise an exception.
            ctx.fireExceptionCaught(new SSLException(
                            "renegotiation attempted by peer; " +
                            "closing the connection"));

            // Close the connection to stop renegotiation.
            ctx.close(ctx.channel().newSucceededFuture());
        }
    }

    private void runDelegatedTasks() {
        for (;;) {
            final Runnable task;
            synchronized (handshakeLock) {
                task = engine.getDelegatedTask();
            }

            if (task == null) {
                break;
            }

            delegatedTaskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (handshakeLock) {
                        task.run();
                    }
                }
            });
        }
    }

    private void setHandshakeSuccess(Channel channel) {
        synchronized (handshakeLock) {
            handshaking = false;
            handshaken = true;

            if (handshakeFuture == null) {
                handshakeFuture = channel.newFuture();
            }
        }

        handshakeFuture.setSuccess();
    }

    private void setHandshakeFailure(Channel channel, SSLException cause) {
        synchronized (handshakeLock) {
            if (!handshaking) {
                return;
            }
            handshaking = false;
            handshaken = false;

            if (handshakeFuture == null) {
                handshakeFuture = channel.newFuture();
            }

            // Release all resources such as internal buffers that SSLEngine
            // is managing.

            engine.closeOutbound();

            try {
                engine.closeInbound();
            } catch (SSLException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "SSLEngine.closeInbound() raised an exception after " +
                            "a handshake failure.", e);
                }

            }
        }

        handshakeFuture.setFailure(cause);
    }

    private void closeOutboundAndChannel(
            final ChannelHandlerContext context, final ChannelFuture future, boolean disconnect) throws Exception {
        if (!context.channel().isActive()) {
            if (disconnect) {
                ctx.disconnect(future);
            } else {
                ctx.close(future);
            }
            return;
        }

        boolean success = false;
        try {
            try {
                unwrap(context, context.channel(), ChannelBuffers.EMPTY_BUFFER, 0, 0, ChannelBuffers.EMPTY_BUFFER);
            } catch (SSLException ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to unwrap before sending a close_notify message", ex);
                }
            }

            if (!engine.isInboundDone()) {
                if (!sentCloseNotify) {
                    sentCloseNotify = true;
                    engine.closeOutbound();
                    try {
                        ChannelFuture closeNotifyFuture = wrapNonAppData(context, context.channel());
                        closeNotifyFuture.addListener(
                                new ClosingChannelFutureListener(context, future));
                        success = true;
                    } catch (SSLException ex) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Failed to encode a close_notify message", ex);
                        }
                    }
                }
            } else {
                success = true;
            }
        } finally {
            if (!success) {
                if (disconnect) {
                    ctx.disconnect(future);
                }  else {
                    ctx.close(future);
                }
            }
        }
    }
    private static final class ClosingChannelFutureListener implements ChannelFutureListener {

        private final ChannelHandlerContext context;
        private final ChannelFuture f;

        ClosingChannelFutureListener(
                ChannelHandlerContext context, ChannelFuture f) {
            this.context = context;
            this.f = f;
        }

        @Override
        public void operationComplete(ChannelFuture closeNotifyFuture) throws Exception {
            if (!(closeNotifyFuture.cause() instanceof ClosedChannelException)) {
                context.close(f);
            } else {
                f.setSuccess();
            }
        }
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        handshakeFuture = ctx.newFuture();
    }



    /**
     * Calls {@link #handshake()} once the {@link Channel} is connected
     */
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        if (issueHandshake) {
            // issue and handshake and add a listener to it which will fire an exception event if an exception was thrown while doing the handshake
            handshake().addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        ctx.pipeline().fireExceptionCaught(future.cause());
                    } else {
                        // Send the event upstream after the handshake was completed without an error.
                        //
                        // See https://github.com/netty/netty/issues/358
                       ctx.fireChannelActive();
                    }

                }
            });
        } else {
            super.channelActive(ctx);
        }
    }

    private final class SSLEngineInboundCloseFuture extends DefaultChannelFuture {
        public SSLEngineInboundCloseFuture() {
            super(null, true);
        }

        void setClosed() {
            super.setSuccess();
        }

        @Override
        public Channel channel() {
            if (ctx == null) {
                // Maybe we should better throw an IllegalStateException() ?
                return null;
            } else {
                return ctx.channel();
            }
        }

        @Override
        public boolean setSuccess() {
            return false;
        }

        @Override
        public boolean setFailure(Throwable cause) {
            return false;
        }
    }


}
