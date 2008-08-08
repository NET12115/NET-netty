/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package net.gleamynode.netty.example.factorial;

import static net.gleamynode.netty.channel.Channels.*;

import java.math.BigInteger;

import net.gleamynode.netty.buffer.ChannelBuffer;
import net.gleamynode.netty.buffer.ChannelBuffers;
import net.gleamynode.netty.channel.ChannelDownstreamHandler;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelHandlerContext;
import net.gleamynode.netty.channel.ChannelPipelineCoverage;
import net.gleamynode.netty.channel.MessageEvent;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
@ChannelPipelineCoverage("all")
public class NumberEncoder implements ChannelDownstreamHandler {

    public void handleDownstream(
            ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendDownstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        if (!(e.getMessage() instanceof Number)) {
            // Ignore what this encoder can't encode.
            ctx.sendDownstream(evt);
            return;
        }

        // Convert to a BigInteger first for easier implementation.
        BigInteger v;
        if (e.getMessage() instanceof BigInteger) {
            v = (BigInteger) e.getMessage();
        } else {
            v = new BigInteger(e.getMessage().toString());
        }

        // Convert the number into a byte array.
        byte[] data = v.toByteArray();
        int dataLength = data.length;

        // Construct a message with a length header.
        ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
        buf.writeInt(dataLength);
        buf.writeBytes(data);

        // Send the constructed message.
        write(ctx, e.getChannel(), e.getFuture(), buf, e.getRemoteAddress());
    }
}
