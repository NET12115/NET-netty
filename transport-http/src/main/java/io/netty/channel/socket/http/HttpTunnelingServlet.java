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
package io.netty.channel.socket.http;

import java.io.EOFException;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.SocketAddress;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.Channels;
import io.netty.channel.ExceptionEvent;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelUpstreamHandler;
import io.netty.channel.local.DefaultLocalClientChannelFactory;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

/**
 * An {@link HttpServlet} that proxies an incoming data to the actual server
 * and vice versa.  Please refer to the
 * <a href="package-summary.html#package_description">package summary</a> for
 * the detailed usage.
 * @apiviz.landmark
 */
public class HttpTunnelingServlet extends HttpServlet {

    private static final long serialVersionUID = 4259910275899756070L;

    private static final String ENDPOINT = "endpoint";

    static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpTunnelingServlet.class);

    private volatile SocketAddress remoteAddress;
    private volatile ChannelFactory channelFactory;

    @Override
    public void init() throws ServletException {
        ServletConfig config = getServletConfig();
        String endpoint = config.getInitParameter(ENDPOINT);
        if (endpoint == null) {
            throw new ServletException("init-param '" + ENDPOINT + "' must be specified.");
        }

        try {
            remoteAddress = parseEndpoint(endpoint.trim());
        } catch (ServletException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException("Failed to parse an endpoint.", e);
        }

        try {
            channelFactory = createChannelFactory(remoteAddress);
        } catch (ServletException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException("Failed to create a channel factory.", e);
        }

        // Stuff for testing purpose
        //ServerBootstrap b = new ServerBootstrap(new DefaultLocalServerChannelFactory());
        //b.getPipeline().addLast("logger", new LoggingHandler(getClass(), InternalLogLevel.INFO, true));
        //b.getPipeline().addLast("handler", new EchoHandler());
        //b.bind(remoteAddress);
    }

    protected SocketAddress parseEndpoint(String endpoint) throws Exception {
        if (endpoint.startsWith("local:")) {
            return new LocalAddress(endpoint.substring(6).trim());
        } else {
            throw new ServletException(
                    "Invalid or unknown endpoint: " + endpoint);
        }
    }

    protected ChannelFactory createChannelFactory(SocketAddress remoteAddress) throws Exception {
        if (remoteAddress instanceof LocalAddress) {
            return new DefaultLocalClientChannelFactory();
        } else {
            throw new ServletException(
                    "Unsupported remote address type: " +
                    remoteAddress.getClass().getName());
        }
    }

    @Override
    public void destroy() {
        try {
            destroyChannelFactory(channelFactory);
        } catch (Exception e) {
            logger.warn("Failed to destroy a channel factory.", e);
        }
    }

    protected void destroyChannelFactory(ChannelFactory factory) throws Exception {
        factory.releaseExternalResources();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(req.getMethod())) {
            logger.warn("Unallowed method: " + req.getMethod());
            res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        final ChannelPipeline pipeline = Channels.pipeline();
        final ServletOutputStream out = res.getOutputStream();
        final OutboundConnectionHandler handler = new OutboundConnectionHandler(out);
        pipeline.addLast("handler", handler);

        Channel channel = channelFactory.newChannel(pipeline);
        ChannelFuture future = channel.connect(remoteAddress).awaitUninterruptibly();
        if (!future.isSuccess()) {
            Throwable cause = future.getCause();
            logger.warn("Endpoint unavailable: " + cause.getMessage(), cause);
            res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        ChannelFuture lastWriteFuture = null;
        try {
            res.setStatus(HttpServletResponse.SC_OK);
            res.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/octet-stream");
            res.setHeader(HttpHeaders.Names.CONTENT_TRANSFER_ENCODING, HttpHeaders.Values.BINARY);

            // Initiate chunked encoding by flushing the headers.
            out.flush();

            PushbackInputStream in =
                    new PushbackInputStream(req.getInputStream());
            while (channel.isConnected()) {
                ChannelBuffer buffer;
                try {
                    buffer = read(in);
                } catch (EOFException e) {
                    break;
                }
                if (buffer == null) {
                    break;
                }
                lastWriteFuture = channel.write(buffer);
            }
        } finally {
            if (lastWriteFuture == null) {
                channel.close();
            } else {
                lastWriteFuture.addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    private static ChannelBuffer read(PushbackInputStream in) throws IOException {
        byte[] buf;
        int readBytes;

        int bytesToRead = in.available();
        if (bytesToRead > 0) {
            buf = new byte[bytesToRead];
            readBytes = in.read(buf);
        } else if (bytesToRead == 0) {
            int b = in.read();
            if (b < 0 || in.available() < 0) {
                return null;
            }
            in.unread(b);
            bytesToRead = in.available();
            buf = new byte[bytesToRead];
            readBytes = in.read(buf);
        } else {
            return null;
        }

        assert readBytes > 0;

        ChannelBuffer buffer;
        if (readBytes == buf.length) {
            buffer = ChannelBuffers.wrappedBuffer(buf);
        } else {
            // A rare case, but it sometimes happen.
            buffer = ChannelBuffers.wrappedBuffer(buf, 0, readBytes);
        }
        return buffer;
    }

    private static final class OutboundConnectionHandler extends SimpleChannelUpstreamHandler {

        private final ServletOutputStream out;

        public OutboundConnectionHandler(ServletOutputStream out) {
            this.out = out;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            ChannelBuffer buffer = (ChannelBuffer) e.getMessage();
            synchronized (this) {
                buffer.readBytes(out, buffer.readableBytes());
                out.flush();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            logger.warn("Unexpected exception while HTTP tunneling", e.getCause());
            e.getChannel().close();
        }
    }
}
