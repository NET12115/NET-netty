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
package org.jboss.netty.channel;

/**
 * Provides the properties and operations which are specific to the
 * {@link ChannelHandler} and the {@link ChannelPipeline} it belongs to.
 * Via a {@link ChannelHandlerContext}, a {@link ChannelHandler} can send
 * an upstream or downstream {@link ChannelEvent} to the next or previous
 * {@link ChannelHandler} in a {@link ChannelPipeline}.
 *
 * <pre>
 * +---------+ 1 .. 1 +----------+ 1    n +---------+ n    m +---------+
 * | Channel |--------| Pipeline |--------| Context |--------| Handler |
 * +---------+        +----------+        +---------+        +---------+
 *         n = the number of the handler entries in a pipeline
 * </pre>
 *
 * Please note that a {@link ChannelHandler} instance can be added to more than
 * one {@link ChannelPipeline}.  It means a single {@link ChannelHandler}
 * instance can have more than one {@link ChannelHandlerContext} and therefore
 * can be invoked with different {@link ChannelHandlerContext}s if it is added
 * to one or more {@link ChannelPipeline}s more than once.  For example, the
 * following handler will have as many independent attachments as how many
 * times it is added to pipelines, regardless if it is added to the same
 * pipeline multiple times or added to different pipelines multiple times:
 * <pre>
 * public class FactorialHandler extends SimpleUpstreamChannelHandler {
 *
 *   // This handler will receive a sequence of increasing integers starting
 *   // from 1.
 *   public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt) {
 *     Integer a = (Integer) ctx.getAttachment();
 *     Integer b = (Integer) evt.getMessage();
 *
 *     if (a == null) {
 *       a = 1;
 *     }
 *
 *     ctx.setAttachment(Integer.valueOf(a * b));
 *   }
 * }
 *
 * // Different context objects are given to "f1", "f2", "f3", and "f4" even if
 * // they refer to the same handler instance.  Because the FactorialHandler
 * // stores its state in a context object (as an attachment), the factorial is
 * // calculated correctly 4 times.
 * FactorialHandler fh = new FactorialHandler();
 *
 * ChannelPipeline p1 = Channels.pipeline();
 * p1.addLast("f1", fh);
 * p1.addLast("f2", fh);
 *
 * ChannelPipeline p2 = Channels.pipeline();
 * p2.addLast("f3", fh);
 * p2.addLast("f4", fh);
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.owns org.jboss.netty.channel.ChannelHandler
 */
public interface ChannelHandlerContext {

    /**
     * Returns the {@link Channel} that the {@link ChannelPipeline} belongs to.
     * This method is a shortcut to <tt>getPipeline().getChannel()</tt>.
     */
    Channel getChannel();

    /**
     * Returns the {@link ChannelPipeline} that the {@link ChannelHandler}
     * belongs to.
     */
    ChannelPipeline getPipeline();

    /**
     * Returns the name of the {@link ChannelHandler} in the
     * {@link ChannelPipeline}.
     */
    String getName();

    /**
     * Returns the {@link ChannelHandler} that this context object is
     * serving.
     */
    ChannelHandler getHandler();

    /**
     * Returns {@code true} if and only if the {@link ChannelHandler} is an
     * instance of {@link ChannelUpstreamHandler}.
     */
    boolean canHandleUpstream();

    /**
     * Returns {@code true} if and only if the {@link ChannelHandler} is an
     * instance of {@link ChannelDownstreamHandler}.
     */
    boolean canHandleDownstream();

    /**
     * Sends the specified {@link ChannelEvent} to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline}.  It is
     * recommended to use the event generation methods in {@link Channels}
     * rather than calling this method directly.
     */
    void sendUpstream(ChannelEvent e);

    /**
     * Sends the specified {@link ChannelEvent} to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline}.  It is
     * recommended to use the event generation methods in {@link Channels}
     * rather than calling this method directly.
     */
    void sendDownstream(ChannelEvent e);

    Object getAttachment();
    void setAttachment(Object attachment);
}