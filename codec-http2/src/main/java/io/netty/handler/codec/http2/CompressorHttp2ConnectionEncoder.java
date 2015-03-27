/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.IDENTITY;
import static io.netty.handler.codec.http.HttpHeaderValues.X_DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.X_GZIP;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseAggregator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;

/**
 * A decorating HTTP2 encoder that will compress data frames according to the {@code content-encoding} header for each
 * stream. The compression provided by this class will be applied to the data for the entire stream.
 */
public class CompressorHttp2ConnectionEncoder extends DecoratingHttp2ConnectionEncoder {
    private static final Http2ConnectionAdapter CLEAN_UP_LISTENER = new Http2ConnectionAdapter() {
        @Override
        public void streamRemoved(Http2Stream stream) {
            final EmbeddedChannel compressor = stream.getProperty(CompressorHttp2ConnectionEncoder.class);
            if (compressor != null) {
                cleanup(stream, compressor);
            }
        }
    };

    public static final int DEFAULT_COMPRESSION_LEVEL = 6;
    public static final int DEFAULT_WINDOW_BITS = 15;
    public static final int DEFAULT_MEM_LEVEL = 8;

    private final int compressionLevel;
    private final int windowBits;
    private final int memLevel;

    public CompressorHttp2ConnectionEncoder(Http2ConnectionEncoder delegate) {
        this(delegate, DEFAULT_COMPRESSION_LEVEL, DEFAULT_WINDOW_BITS, DEFAULT_MEM_LEVEL);
    }

    public CompressorHttp2ConnectionEncoder(Http2ConnectionEncoder delegate, int compressionLevel, int windowBits,
                                            int memLevel) {
        super(delegate);
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException("compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        if (windowBits < 9 || windowBits > 15) {
            throw new IllegalArgumentException("windowBits: " + windowBits + " (expected: 9-15)");
        }
        if (memLevel < 1 || memLevel > 9) {
            throw new IllegalArgumentException("memLevel: " + memLevel + " (expected: 1-9)");
        }
        this.compressionLevel = compressionLevel;
        this.windowBits = windowBits;
        this.memLevel = memLevel;

        connection().addListener(CLEAN_UP_LISTENER);
    }

    @Override
    public ChannelFuture writeData(final ChannelHandlerContext ctx, final int streamId, ByteBuf data, int padding,
            final boolean endOfStream, ChannelPromise promise) {
        final Http2Stream stream = connection().stream(streamId);
        final EmbeddedChannel channel = stream == null ? null :
            (EmbeddedChannel) stream.getProperty(CompressorHttp2ConnectionEncoder.class);
        if (channel == null) {
            // The compressor may be null if no compatible encoding type was found in this stream's headers
            return super.writeData(ctx, streamId, data, padding, endOfStream, promise);
        }

        try {
            // The channel will release the buffer after being written
            channel.writeOutbound(data);
            ByteBuf buf = nextReadableBuf(channel);
            if (buf == null) {
                if (endOfStream) {
                    if (channel.finish()) {
                        buf = nextReadableBuf(channel);
                    }
                    return super.writeData(ctx, streamId, buf == null ? Unpooled.EMPTY_BUFFER : buf, padding,
                            true, promise);
                }
                // END_STREAM is not set and the assumption is data is still forthcoming.
                promise.setSuccess();
                return promise;
            }

            ChannelPromiseAggregator aggregator = new ChannelPromiseAggregator(promise);
            ChannelPromise bufPromise = ctx.newPromise();
            aggregator.add(bufPromise);
            for (;;) {
                ByteBuf nextBuf = nextReadableBuf(channel);
                boolean compressedEndOfStream = nextBuf == null && endOfStream;
                if (compressedEndOfStream && channel.finish()) {
                    nextBuf = nextReadableBuf(channel);
                    compressedEndOfStream = nextBuf == null;
                }

                final ChannelPromise nextPromise;
                if (nextBuf != null) {
                    // We have to add the nextPromise to the aggregator before doing the current write. This is so
                    // completing the current write before the next write is done won't complete the aggregate promise
                    nextPromise = ctx.newPromise();
                    aggregator.add(nextPromise);
                } else {
                    nextPromise = null;
                }

                super.writeData(ctx, streamId, buf, padding, compressedEndOfStream, bufPromise);
                if (nextBuf == null) {
                    break;
                }

                padding = 0; // Padding is only communicated once on the first iteration
                buf = nextBuf;
                bufPromise = nextPromise;
            }
            return promise;
        } finally {
            if (endOfStream) {
                cleanup(stream, channel);
            }
        }
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
            boolean endStream, ChannelPromise promise) {
        initCompressor(streamId, headers, endStream);
        return super.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
    }

    @Override
    public ChannelFuture writeHeaders(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int streamDependency, final short weight, final boolean exclusive, final int padding,
            final boolean endOfStream, final ChannelPromise promise) {
        initCompressor(streamId, headers, endOfStream);
        return super.writeHeaders(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endOfStream,
                promise);
    }

    /**
     * Returns a new {@link EmbeddedChannel} that encodes the HTTP2 message content encoded in the specified
     * {@code contentEncoding}.
     *
     * @param contentEncoding the value of the {@code content-encoding} header
     * @return a new {@link ByteToMessageDecoder} if the specified encoding is supported. {@code null} otherwise
     * (alternatively, you can throw a {@link Http2Exception} to block unknown encoding).
     * @throws Http2Exception If the specified encoding is not not supported and warrants an exception
     */
    protected EmbeddedChannel newContentCompressor(AsciiString contentEncoding) throws Http2Exception {
        if (GZIP.equalsIgnoreCase(contentEncoding) || X_GZIP.equalsIgnoreCase(contentEncoding)) {
            return newCompressionChannel(ZlibWrapper.GZIP);
        }
        if (DEFLATE.equalsIgnoreCase(contentEncoding) || X_DEFLATE.equalsIgnoreCase(contentEncoding)) {
            return newCompressionChannel(ZlibWrapper.ZLIB);
        }
        // 'identity' or unsupported
        return null;
    }

    /**
     * Returns the expected content encoding of the decoded content. Returning {@code contentEncoding} is the default
     * behavior, which is the case for most compressors.
     *
     * @param contentEncoding the value of the {@code content-encoding} header
     * @return the expected content encoding of the new content.
     * @throws Http2Exception if the {@code contentEncoding} is not supported and warrants an exception
     */
    protected AsciiString getTargetContentEncoding(AsciiString contentEncoding) throws Http2Exception {
        return contentEncoding;
    }

    /**
     * Generate a new instance of an {@link EmbeddedChannel} capable of compressing data
     * @param wrapper Defines what type of encoder should be used
     */
    private EmbeddedChannel newCompressionChannel(ZlibWrapper wrapper) {
        return new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder(wrapper, compressionLevel, windowBits,
                memLevel));
    }

    /**
     * Checks if a new compressor object is needed for the stream identified by {@code streamId}. This method will
     * modify the {@code content-encoding} header contained in {@code headers}.
     *
     * @param streamId The identifier for the headers inside {@code headers}
     * @param headers Object representing headers which are to be written
     * @param endOfStream Indicates if the stream has ended
     */
    private void initCompressor(int streamId, Http2Headers headers, boolean endOfStream) {
        final Http2Stream stream = connection().stream(streamId);
        if (stream == null) {
            return;
        }

        EmbeddedChannel compressor = stream.getProperty(CompressorHttp2ConnectionEncoder.class);
        if (compressor == null) {
            if (!endOfStream) {
                AsciiString encoding = headers.get(CONTENT_ENCODING);
                if (encoding == null) {
                    encoding = IDENTITY;
                }
                try {
                    compressor = newContentCompressor(encoding);
                    if (compressor != null) {
                        stream.setProperty(CompressorHttp2ConnectionEncoder.class, compressor);
                        AsciiString targetContentEncoding = getTargetContentEncoding(encoding);
                        if (IDENTITY.equalsIgnoreCase(targetContentEncoding)) {
                            headers.remove(CONTENT_ENCODING);
                        } else {
                            headers.set(CONTENT_ENCODING, targetContentEncoding);
                        }
                    }
                } catch (Throwable ignored) {
                    // Ignore
                }
            }
        } else if (endOfStream) {
            cleanup(stream, compressor);
        }

        if (compressor != null) {
            // The content length will be for the decompressed data. Since we will compress the data
            // this content-length will not be correct. Instead of queuing messages or delaying sending
            // header frames...just remove the content-length header
            headers.remove(CONTENT_LENGTH);
        }
    }

    /**
     * Release remaining content from {@link EmbeddedChannel} and remove the compressor from the {@link Http2Stream}.
     *
     * @param stream The stream for which {@code compressor} is the compressor for
     * @param compressor The compressor for {@code stream}
     */
    private static void cleanup(Http2Stream stream, EmbeddedChannel compressor) {
        if (compressor.finish()) {
            for (;;) {
                final ByteBuf buf = compressor.readOutbound();
                if (buf == null) {
                    break;
                }

                buf.release();
            }
        }
        stream.removeProperty(CompressorHttp2ConnectionEncoder.class);
    }

    /**
     * Read the next compressed {@link ByteBuf} from the {@link EmbeddedChannel} or {@code null} if one does not exist.
     *
     * @param compressor The channel to read from
     * @return The next decoded {@link ByteBuf} from the {@link EmbeddedChannel} or {@code null} if one does not exist
     */
    private static ByteBuf nextReadableBuf(EmbeddedChannel compressor) {
        for (;;) {
            final ByteBuf buf = compressor.readOutbound();
            if (buf == null) {
                return null;
            }
            if (!buf.isReadable()) {
                buf.release();
                continue;
            }
            return buf;
        }
    }
}
