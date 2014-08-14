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
package io.netty.handler.codec.socksx.v4;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksProtocolVersion;

/**
 * Encodes a {@link Socks4Request} and {@link Socks4Response} into a {@link ByteBuf}.
 */
@ChannelHandler.Sharable
public final class Socks4MessageEncoder extends MessageToByteEncoder<SocksMessage> {

    public static final Socks4MessageEncoder INSTANCE = new Socks4MessageEncoder();

    private Socks4MessageEncoder() { }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return super.acceptOutboundMessage(msg) &&
               ((SocksMessage) msg).protocolVersion() == SocksProtocolVersion.SOCKS4a;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, SocksMessage msg, ByteBuf out) throws Exception {
        if (msg instanceof Socks4Response) {
            ((Socks4Response) msg).encodeAsByteBuf(out);
        } else if (msg instanceof Socks4Request) {
            ((Socks4Request) msg).encodeAsByteBuf(out);
        } else {
            // Should not reach here.
            throw new Error();
        }
    }
}
