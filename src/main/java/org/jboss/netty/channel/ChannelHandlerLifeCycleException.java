/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel;

/**
 * A {@link RuntimeException} which is thrown when a
 * {@link LifeCycleAwareChannelHandler} throws an {@link Exception}
 * in its handler methods.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.hidden
 */
public class ChannelHandlerLifeCycleException extends RuntimeException {

    private static final long serialVersionUID = 8764799996088850672L;

    /**
     * Creates a new exception.
     */
    public ChannelHandlerLifeCycleException() {
        super();
    }

    /**
     * Creates a new exception.
     */
    public ChannelHandlerLifeCycleException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public ChannelHandlerLifeCycleException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public ChannelHandlerLifeCycleException(Throwable cause) {
        super(cause);
    }
}
