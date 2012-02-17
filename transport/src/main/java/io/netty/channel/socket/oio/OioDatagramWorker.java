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
package io.netty.channel.socket.oio;

import static io.netty.channel.Channels.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ReceiveBufferSizePredictor;

class OioDatagramWorker extends AbstractOioWorker<OioDatagramChannel> {

    OioDatagramWorker(OioDatagramChannel channel) {
        super(channel);
    }

  

    @Override
    boolean process() throws IOException {

        ReceiveBufferSizePredictor predictor =
            channel.getConfig().getReceiveBufferSizePredictor();

        byte[] buf = new byte[predictor.nextReceiveBufferSize()];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        try {
            channel.socket.receive(packet);
        } catch (InterruptedIOException e) {
            // Can happen on interruption.
            // Keep receiving unless the channel is closed.
            return true;
        } 

        fireMessageReceived(
                channel,
                channel.getConfig().getBufferFactory().getBuffer(buf, 0, packet.getLength()),
                packet.getSocketAddress());
        return true;
    }



    static void write(
            OioDatagramChannel channel, ChannelFuture future,
            Object message, SocketAddress remoteAddress) {
        try {
            ChannelBuffer buf = (ChannelBuffer) message;
            int offset = buf.readerIndex();
            int length = buf.readableBytes();
            ByteBuffer nioBuf = buf.toByteBuffer();
            DatagramPacket packet;
            if (nioBuf.hasArray()) {
                // Avoid copy if the buffer is backed by an array.
                packet = new DatagramPacket(
                        nioBuf.array(), nioBuf.arrayOffset() + offset, length);
            } else {
                // Otherwise it will be expensive.
                byte[] arrayBuf = new byte[length];
                buf.getBytes(0, arrayBuf);
                packet = new DatagramPacket(arrayBuf, length);
            }

            if (remoteAddress != null) {
                packet.setSocketAddress(remoteAddress);
            }
            channel.socket.send(packet);
            fireWriteComplete(channel, length);
            future.setSuccess();
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    
    static void disconnect(OioDatagramChannel channel, ChannelFuture future) {
        boolean connected = channel.isConnected();
        try {
            channel.socket.disconnect();
            future.setSuccess();
            if (connected) {
                // Notify.
                fireChannelDisconnected(channel);
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

}
