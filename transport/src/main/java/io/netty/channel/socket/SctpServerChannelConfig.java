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
package io.netty.channel.socket;

import io.netty.channel.ChannelConfig;

import static com.sun.nio.sctp.SctpStandardSocketOptions.InitMaxStreams;

/**
 * A {@link io.netty.channel.ChannelConfig} for a {@link SctpServerChannelConfig}.
 * <p/>
 * <h3>Available options</h3>
 * <p/>
 * In addition to the options provided by {@link io.netty.channel.ChannelConfig},
 * {@link SctpServerChannelConfig} allows the following options in the
 * option map:
 * <p/>
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@code "backlog"}</td><td>{@link #setBacklog(int)}</td>
 * </tr><tr>
 * * <td>{@code "receiveBufferSize"}</td><td>{@link #setReceiveBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@code "sendBufferSize"}</td><td>{@link #setSendBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@code "sctpInitMaxStreams"}</td>
 * <td>{@link #setInitMaxStreams(InitMaxStreams)} (int)}}</td>
 * </tr>
 * </table>
 */
public interface SctpServerChannelConfig extends ChannelConfig {

    /**
     * Gets the backlog value to specify when the channel binds to a local address.
     */
    int getBacklog();

    /**
     * Sets the backlog value to specify when the channel binds to a local address.
     */
    void setBacklog(int backlog);


    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SO_SNDBUF}</a> option.
     */
    int getSendBufferSize();

    /**
     * Sets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SO_SNDBUF}</a> option.
     */
    void setSendBufferSize(int sendBufferSize);

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SO_RCVBUF}</a> option.
     */
    int getReceiveBufferSize();

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SO_RCVBUF}</a> option.
     */
    void setReceiveBufferSize(int receiveBufferSize);


    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SCTP_INIT_MAXSTREAMS}</a> option.
     */
    InitMaxStreams getInitMaxStreams();

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SCTP_INIT_MAXSTREAMS}</a> option.
     */
    void setInitMaxStreams(InitMaxStreams initMaxStreams);
}
