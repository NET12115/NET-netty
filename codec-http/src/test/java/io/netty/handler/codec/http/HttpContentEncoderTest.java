/*
 * Copyright 2013 The Netty Project
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

package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedByteChannel;
import io.netty.channel.embedded.EmbeddedMessageChannel;
import io.netty.handler.codec.ByteToByteEncoder;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class HttpContentEncoderTest {

    @Test
    public void testSplitContent() throws Exception {
        EmbeddedMessageChannel ch = new EmbeddedMessageChannel(
                new HttpContentEncoder() {
                    @Override
                    protected Result beginEncode(HttpMessage header, HttpContent msg, String acceptEncoding) {
                        return new Result("test", new EmbeddedByteChannel(new ByteToByteEncoder() {
                            @Override
                            protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) {
                                out.writeBytes(String.valueOf(in.readableBytes()).getBytes(CharsetUtil.US_ASCII));
                                in.skipBytes(in.readableBytes());
                            }
                        }));
                    }
                });

        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        ch.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        ch.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[3])));
        ch.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[2])));
        ch.writeOutbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(new byte[1])));

        HttpResponse res = (HttpResponse) ch.readOutbound();
        assertThat(res.headers().get(Names.TRANSFER_ENCODING), is("chunked"));
        assertThat(res.headers().get(Names.CONTENT_LENGTH), is(nullValue()));

        HttpContent chunk;
        chunk = (HttpContent) ch.readOutbound();
        assertThat(chunk.data().toString(CharsetUtil.US_ASCII), is("3"));
        chunk = (HttpContent) ch.readOutbound();
        assertThat(chunk.data().toString(CharsetUtil.US_ASCII), is("2"));
        chunk = (HttpContent) ch.readOutbound();
        assertThat(chunk.data().toString(CharsetUtil.US_ASCII), is("1"));

        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        assertThat(ch.readOutbound(), is(nullValue()));
    }

    @Test
    public void testFullContent() throws Exception {
        EmbeddedMessageChannel ch = new EmbeddedMessageChannel(
                new HttpContentEncoder() {
                    @Override
                    protected Result beginEncode(HttpMessage header, HttpContent msg, String acceptEncoding) {
                        return new Result("test", new EmbeddedByteChannel(new ByteToByteEncoder() {
                            @Override
                            protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) {
                                out.writeBytes(String.valueOf(in.readableBytes()).getBytes(CharsetUtil.US_ASCII));
                                in.skipBytes(in.readableBytes());
                            }
                        }));
                    }
                });

        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(new byte[42]));
        res.headers().set(Names.CONTENT_LENGTH, 42);
        ch.writeOutbound(res);

        HttpResponse encodedRes = (HttpResponse) ch.readOutbound();
        assertThat(encodedRes.headers().get(Names.TRANSFER_ENCODING), is(nullValue()));
        assertThat(encodedRes.headers().get(Names.CONTENT_LENGTH), is("2"));

        LastHttpContent c = (LastHttpContent) ch.readOutbound();
        assertThat(c.data().readableBytes(), is(2));

        assertThat(ch.readOutbound(), is(nullValue()));
    }
}
