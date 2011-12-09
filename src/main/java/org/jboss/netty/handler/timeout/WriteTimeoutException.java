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
package io.netty.handler.timeout;


/**
 * A {@link TimeoutException} raised by {@link WriteTimeoutHandler} when no data
 * was written within a certain period of time.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 */
public class WriteTimeoutException extends TimeoutException {

    private static final long serialVersionUID = -7746685254523245218L;

    /**
     * Creates a new instance.
     */
    public WriteTimeoutException() {
    }

    /**
     * Creates a new instance.
     */
    public WriteTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance.
     */
    public WriteTimeoutException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public WriteTimeoutException(Throwable cause) {
        super(cause);
    }
}
