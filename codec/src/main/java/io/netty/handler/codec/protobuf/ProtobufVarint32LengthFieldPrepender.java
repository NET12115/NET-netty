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
package io.netty.handler.codec.protobuf;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBufferOutputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToStreamEncoder;

import com.google.protobuf.CodedOutputStream;

/**
 * An encoder that prepends the the Google Protocol Buffers
 * <a href="http://code.google.com/apis/protocolbuffers/docs/encoding.html#varints">Base
 * 128 Varints</a> integer length field.  For example:
 * <pre>
 * BEFORE DECODE (300 bytes)       AFTER DECODE (302 bytes)
 * +---------------+               +--------+---------------+
 * | Protobuf Data |-------------->| Length | Protobuf Data |
 * |  (300 bytes)  |               | 0xAC02 |  (300 bytes)  |
 * +---------------+               +--------+---------------+
 * </pre> *
 *
 * @see com.google.protobuf.CodedOutputStream
 */
@Sharable
public class ProtobufVarint32LengthFieldPrepender extends MessageToStreamEncoder<ChannelBuffer> {

    /**
     * Creates a new instance.
     */
    public ProtobufVarint32LengthFieldPrepender() {
    }

    @Override
    public boolean isEncodable(Object msg) throws Exception {
        return msg instanceof ChannelBuffer;
    }

    @Override
    public void encode(
            ChannelHandlerContext ctx, ChannelBuffer msg, ChannelBuffer out) throws Exception {
        ChannelBuffer body = msg;
        int bodyLen = body.readableBytes();
        int headerLen = CodedOutputStream.computeRawVarint32Size(bodyLen);
        out.ensureWritableBytes(headerLen + bodyLen);

        CodedOutputStream headerOut =
                CodedOutputStream.newInstance(new ChannelBufferOutputStream(out));
        headerOut.writeRawVarint32(bodyLen);
        headerOut.flush();

        out.writeBytes(body);
    }
}
