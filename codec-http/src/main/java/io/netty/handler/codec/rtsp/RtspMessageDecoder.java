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
package io.netty.handler.codec.rtsp;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.embedder.DecoderEmbedder;
import io.netty.handler.codec.http.HttpChunkAggregator;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMessageDecoder;

/**
 * Decodes {@link ChannelBuffer}s into RTSP messages represented in
 * {@link HttpMessage}s.
 * <p>
 * <h3>Parameters that prevents excessive memory consumption</h3>
 * <table border="1">
 * <tr>
 * <th>Name</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code maxInitialLineLength}</td>
 * <td>The maximum length of the initial line
 *     (e.g. {@code "SETUP / RTSP/1.0"} or {@code "RTSP/1.0 200 OK"})
 *     If the length of the initial line exceeds this value, a
 *     {@link TooLongFrameException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxHeaderSize}</td>
 * <td>The maximum length of all headers.  If the sum of the length of each
 *     header exceeds this value, a {@link TooLongFrameException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxContentLength}</td>
 * <td>The maximum length of the content.  If the content length exceeds this
 *     value, a {@link TooLongFrameException} will be raised.</td>
 * </tr>
 * </table>
 * @apiviz.landmark
 */
public abstract class RtspMessageDecoder extends HttpMessageDecoder {

    private final DecoderEmbedder<HttpMessage> aggregator;

    /**
     * Creates a new instance with the default
     * {@code maxInitialLineLength (4096}}, {@code maxHeaderSize (8192)}, and
     * {@code maxContentLength (8192)}.
     */
    protected RtspMessageDecoder() {
        this(4096, 8192, 8192);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    protected RtspMessageDecoder(int maxInitialLineLength, int maxHeaderSize, int maxContentLength) {
        super(maxInitialLineLength, maxHeaderSize, maxContentLength * 2);
        aggregator = new DecoderEmbedder<HttpMessage>(new HttpChunkAggregator(maxContentLength));
    }


    @Override
    public Object decode(ChannelInboundHandlerContext<Byte> ctx, ChannelBuffer buffer) throws Exception {
        Object o = super.decode(ctx, buffer);
        if (o != null && aggregator.offer(o)) {
            return aggregator.poll();
        } else {
            return null;
        }
    }

    @Override
    protected boolean isContentAlwaysEmpty(HttpMessage msg) {
        // Unlike HTTP, RTSP always assumes zero-length body if Content-Length
        // header is absent.
        boolean empty = super.isContentAlwaysEmpty(msg);
        if (empty) {
            return true;
        }
        if (!msg.containsHeader(RtspHeaders.Names.CONTENT_LENGTH)) {
            return true;
        }
        return empty;
    }
}
