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
package io.netty.channel.socket.nio;

import java.nio.channels.Selector;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.ClientSocketChannelFactory;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.ExternalResourceReleasable;

/**
 * A {@link ClientSocketChannelFactory} which creates a client-side NIO-based
 * {@link SocketChannel}.  It utilizes the non-blocking I/O mode which was
 * introduced with NIO to serve many number of concurrent connections
 * efficiently.
 *
 * <h3>How threads work</h3>
 * <p>
 * There are two types of threads in a {@link NioClientSocketChannelFactory};
 * one is boss thread and the other is worker thread.
 *
 * <h4>Boss thread</h4>
 * <p>
 * One {@link NioClientSocketChannelFactory} has one boss thread.  It makes
 * a connection attempt on request.  Once a connection attempt succeeds,
 * the boss thread passes the connected {@link Channel} to one of the worker
 * threads that the {@link NioClientSocketChannelFactory} manages.
 *
 * <h4>Worker threads</h4>
 * <p>
 * One {@link NioClientSocketChannelFactory} can have one or more worker
 * threads.  A worker thread performs non-blocking read and write for one or
 * more {@link Channel}s in a non-blocking mode.
 *
 * <h3>Life cycle of threads and graceful shutdown</h3>
 * <p>
 * All threads are acquired from the {@link Executor}s which were specified
 * when a {@link NioClientSocketChannelFactory} was created.  A boss thread is
 * acquired from the {@code bossExecutor}, and worker threads are acquired from
 * the {@code workerExecutor}.  Therefore, you should make sure the specified
 * {@link Executor}s are able to lend the sufficient number of threads.
 * It is the best bet to specify {@linkplain Executors#newCachedThreadPool() a cached thread pool}.
 * <p>
 * Both boss and worker threads are acquired lazily, and then released when
 * there's nothing left to process.  All the related resources such as
 * {@link Selector} are also released when the boss and worker threads are
 * released.  Therefore, to shut down a service gracefully, you should do the
 * following:
 *
 * <ol>
 * <li>close all channels created by the factory usually using
 *     {@link ChannelGroup#close()}, and</li>
 * <li>call {@link #releaseExternalResources()}.</li>
 * </ol>
 *
 * Please make sure not to shut down the executor until all channels are
 * closed.  Otherwise, you will end up with a {@link RejectedExecutionException}
 * and the related resources might not be released properly.
 * @apiviz.landmark
 */
public class NioClientSocketChannelFactory implements ClientSocketChannelFactory {


    private final WorkerPool<NioWorker> workerPool;
    private final NioClientSocketPipelineSink sink;

    /**
     * Creates a new {@link NioClientSocketChannelFactory} which uses {@link Executors#newCachedThreadPool()} for the worker executor.
     *
     * See {@link #NioClientSocketChannelFactory(Executor, Executor)}
     */
    public NioClientSocketChannelFactory() {
        this(Executors.newCachedThreadPool());
    }
    
    /**
     * Creates a new instance.  Calling this constructor is same with calling
     * {@link #NioClientSocketChannelFactory(Executor, Executor, int, int)} with
     * 1 and (2 * the number of available processors in the machine) for
     * <tt>bossCount</tt> and <tt>workerCount</tt> respectively.  The number of
     * available processors is obtained by {@link Runtime#availableProcessors()}.
     *
     * @param workerExecutor
     *        the {@link Executor} which will execute the worker threads
     */
    public NioClientSocketChannelFactory(Executor workerExecutor) {
        this(workerExecutor, SelectorUtil.DEFAULT_IO_THREADS);
    }

    /**
     * Creates a new instance.  Calling this constructor is same with calling
     * {@link #NioClientSocketChannelFactory(Executor, int, int)} with
     * 1 as <tt>bossCount</tt>.
     *
     * @param workerExecutor
     *        the {@link Executor} which will execute the worker threads
     * @param workerCount
     *        the maximum number of worker threads
     */
    public NioClientSocketChannelFactory(Executor workerExecutor,
            int workerCount) {
        this(new NioWorkerPool(workerExecutor, workerCount, true));
    }

    public NioClientSocketChannelFactory(WorkerPool<NioWorker> workerPool) {

        if (workerPool == null) {
            throw new NullPointerException("workerPool");
        }
        

        this.workerPool = workerPool;
        sink = new NioClientSocketPipelineSink(workerPool);
    }
    

    @Override
    public SocketChannel newChannel(ChannelPipeline pipeline) {
        return NioClientSocketChannel.create(this, pipeline, sink, sink.nextWorker());
    }

    @Override
    public void releaseExternalResources() {
        if (workerPool instanceof ExternalResourceReleasable) {
            ((ExternalResourceReleasable) workerPool).releaseExternalResources();
        }
    }
}
