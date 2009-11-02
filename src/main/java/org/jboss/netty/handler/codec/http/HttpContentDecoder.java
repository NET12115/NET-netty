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
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;

/**
 * Decodes the content of the received {@link HttpMessage} and {@link HttpChunk}.
 * The original content ({@link HttpMessage#getContent()} or {@link HttpChunk#getContent()})
 * is replaced with the new content decoded by the {@link DecoderEmbedder},
 * which is created by {@link #newDecoder(String)}.  Once decoding is finished,
 * the value of the <tt>'Content-Encoding'</tt> header is set to <tt>'identity'</tt>
 * and the <tt>'Content-Length'</tt> header is updated to the length of the
 * decoded content.  If the content encoding of the original is not supported
 * by the decoder, {@link #newDecoder(String)} returns {@code null} and no
 * decoding occurs (i.e. pass-through).
 * <p>
 * Please note that this is an abstract class.  You have to extend this class
 * and implement {@link #newDecoder(String)} properly to make this class
 * functional.  For example, refer to the source code of {@link HttpContentDecompressor}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public abstract class HttpContentDecoder extends SimpleChannelUpstreamHandler {

    private volatile HttpMessage previous;
    private volatile DecoderEmbedder<ChannelBuffer> decoder;

    /**
     * Creates a new instance.
     */
    protected HttpContentDecoder() {
        super();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof HttpMessage) {
            HttpMessage m = (HttpMessage) msg;

            decoder = null;
            if (m.isChunked()) {
                previous = m;
            } else {
                previous = null;
            }

            // Determine the content encoding.
            String contentEncoding = m.getHeader(HttpHeaders.Names.CONTENT_ENCODING);
            if (contentEncoding != null) {
                contentEncoding = contentEncoding.trim();
            }

            if (contentEncoding != null && (decoder = newDecoder(contentEncoding)) != null) {
                // Decode the content and remove or replace the existing headers
                // so that the message looks like a decoded message.
                m.setHeader(HttpHeaders.Names.CONTENT_ENCODING, HttpHeaders.Values.IDENTITY);

                if (!m.isChunked()) {
                    ChannelBuffer content = m.getContent();
                    if (content.readable()) {
                        content = decode(content);

                        // Finish decoding.
                        ChannelBuffer lastProduct = finishDecode();

                        // Merge the last product into the content.
                        if (content == null) {
                            if (lastProduct != null) {
                                content = lastProduct;
                            }
                        } else {
                            if (lastProduct != null) {
                                content = ChannelBuffers.wrappedBuffer(content, lastProduct);
                            }
                        }

                        // Replace the content if necessary.
                        if (content != null) {
                            m.setContent(content);
                            if (m.containsHeader(HttpHeaders.Names.CONTENT_LENGTH)) {
                                m.setHeader(
                                        HttpHeaders.Names.CONTENT_LENGTH,
                                        Integer.toString(content.readableBytes()));
                            }
                        }
                    }
                }
            }

            // Because HttpMessage is a mutable object, we can simply forward the received event.
            ctx.sendUpstream(e);
        } else if (msg instanceof HttpChunk) {
            assert previous != null;

            HttpChunk c = (HttpChunk) msg;
            ChannelBuffer content = c.getContent();

            // Decode the chunk if necessary.
            if (decoder != null) {
                if (!c.isLast()) {
                    content = decode(content);
                    if (content != null) {
                        // Note that HttpChunk is immutable unlike HttpMessage.
                        // XXX API inconsistency? I can live with it though.
                        Channels.fireMessageReceived(ctx, new DefaultHttpChunk(content), e.getRemoteAddress());
                    }
                } else {
                    ChannelBuffer lastProduct = finishDecode();
                    previous = null;
                    decoder = null;

                    // Generate an additional chunk if the decoder produced
                    // the last product on closure,
                    if (lastProduct != null) {
                        Channels.fireMessageReceived(
                                ctx,
                                new DefaultHttpChunk(lastProduct),
                                e.getRemoteAddress());
                    }

                    // Emit the last chunk.
                    ctx.sendUpstream(e);
                }
            } else {
                ctx.sendUpstream(e);
            }
        } else {
            ctx.sendUpstream(e);
        }
    }

    /**
     * Returns a new {@link DecoderEmbedder} that decodes the HTTP message
     * content encoded in the specified <tt>contentEncoding</tt>.
     *
     * @param contentEncoding the value of the {@code "Content-Encoding"} header
     * @return a new {@link DecoderEmbedder} if the specified encoding is supported.
     *         {@code null} otherwise (alternatively, you can throw an exception
     *         to block unknown encoding).
     */
    protected abstract DecoderEmbedder<ChannelBuffer> newDecoder(String contentEncoding) throws Exception;

    private ChannelBuffer decode(ChannelBuffer buf) {
        decoder.offer(buf);
        return pollDecodeResult();
    }

    private ChannelBuffer finishDecode() {
        if (decoder.finish()) {
            return pollDecodeResult();
        } else {
            return null;
        }
    }

    private ChannelBuffer pollDecodeResult() {
        ChannelBuffer result = decoder.poll();
        if (result != null) {
            for (;;) {
                ChannelBuffer moreResult = decoder.poll();
                if (moreResult == null) {
                    break;
                }
                result = ChannelBuffers.wrappedBuffer(result, moreResult);
            }
        }

        if (result.readable()) {
            return result;
        } else {
            return null;
        }
    }
}
