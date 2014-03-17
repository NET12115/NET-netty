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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ChannelFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioDatagramChannel;
import io.netty.channel.socket.oio.OioServerSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapComboFactory;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SocketTestPermutation {

    static final SocketTestPermutation INSTANCE = new SocketTestPermutation();

    protected static final int BOSSES = 2;
    protected static final int WORKERS = 3;

    protected final EventLoopGroup nioBossGroup =
            new NioEventLoopGroup(BOSSES, new DefaultThreadFactory("testsuite-nio-boss", true));
    protected final EventLoopGroup nioWorkerGroup =
            new NioEventLoopGroup(WORKERS, new DefaultThreadFactory("testsuite-nio-worker", true));
    protected final EventLoopGroup oioBossGroup =
            new OioEventLoopGroup(Integer.MAX_VALUE, new DefaultThreadFactory("testsuite-oio-boss", true));
    protected final EventLoopGroup oioWorkerGroup =
            new OioEventLoopGroup(Integer.MAX_VALUE, new DefaultThreadFactory("testsuite-oio-worker", true));

    protected <A extends AbstractBootstrap<?, ?>, B extends AbstractBootstrap<?, ?>>
    List<BootstrapComboFactory<A, B>> combo(List<BootstrapFactory<A>> sbfs, List<BootstrapFactory<B>> cbfs) {

        List<BootstrapComboFactory<A, B>> list = new ArrayList<BootstrapComboFactory<A, B>>();

        // Populate the combinations
        for (BootstrapFactory<A> sbf: sbfs) {
            for (BootstrapFactory<B> cbf: cbfs) {
                final BootstrapFactory<A> sbf0 = sbf;
                final BootstrapFactory<B> cbf0 = cbf;
                list.add(new BootstrapComboFactory<A, B>() {
                    @Override
                    public A newServerInstance() {
                        return sbf0.newInstance();
                    }

                    @Override
                    public B newClientInstance() {
                        return cbf0.newInstance();
                    }
                });
            }
        }

        return list;
    }

    public List<BootstrapComboFactory<ServerBootstrap, Bootstrap>> socket() {
        // Make the list of ServerBootstrap factories.
        List<BootstrapFactory<ServerBootstrap>> sbfs = serverSocket();

        // Make the list of Bootstrap factories.
        List<BootstrapFactory<Bootstrap>> cbfs = clientSocket();

        // Populate the combinations
        List<BootstrapComboFactory<ServerBootstrap, Bootstrap>> list = combo(sbfs, cbfs);

        // Remove the OIO-OIO case which often leads to a dead lock by its nature.
        list.remove(list.size() - 1);

        return list;
    }

    public List<BootstrapComboFactory<Bootstrap, Bootstrap>> datagram() {
        // Make the list of Bootstrap factories.
        List<BootstrapFactory<Bootstrap>> bfs = Arrays.asList(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(nioWorkerGroup).channelFactory(new ChannelFactory<Channel>() {
                            @Override
                            public Channel newChannel() {
                                return new NioDatagramChannel(InternetProtocolFamily.IPv4);
                            }

                            @Override
                            public String toString() {
                                return NioDatagramChannel.class.getSimpleName() + ".class";
                            }
                        });
                    }
                },
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(oioWorkerGroup).channel(OioDatagramChannel.class);
                    }
                }
        );

        // Populare the combinations.
        return combo(bfs, bfs);
    }

    public List<BootstrapFactory<ServerBootstrap>> serverSocket() {
        return Arrays.asList(
                new BootstrapFactory<ServerBootstrap>() {
                    @Override
                    public ServerBootstrap newInstance() {
                        return new ServerBootstrap().group(nioBossGroup, nioWorkerGroup)
                                .channel(NioServerSocketChannel.class);
                    }
                },
                new BootstrapFactory<ServerBootstrap>() {
                    @Override
                    public ServerBootstrap newInstance() {
                        return new ServerBootstrap().group(oioBossGroup, oioWorkerGroup)
                                .channel(OioServerSocketChannel.class);
                    }
                }
        );
    }

    public List<BootstrapFactory<Bootstrap>> clientSocket() {
        return Arrays.asList(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(nioWorkerGroup).channel(NioSocketChannel.class);
                    }
                },
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(oioWorkerGroup).channel(OioSocketChannel.class);
                    }
                }
        );
    }
}
