/*
 * Copyright 2012 The Netty Project
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

package io.netty.channel.udt.nio;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class NioUdtProviderTest {

    protected static final Logger log = LoggerFactory.getLogger(NioUdtProviderTest.class);

    /**
     * verify factory
     */
    @Test
    public void provideFactory() {

        // bytes
        assertNotNull(NioUdtProvider.BYTE_ACCEPTOR.newChannel());
        assertNotNull(NioUdtProvider.BYTE_CONNECTOR.newChannel());
        assertNotNull(NioUdtProvider.BYTE_RENDEZVOUS.newChannel());

        // message
        assertNotNull(NioUdtProvider.MESSAGE_ACCEPTOR.newChannel());
        assertNotNull(NioUdtProvider.MESSAGE_CONNECTOR.newChannel());
        assertNotNull(NioUdtProvider.MESSAGE_RENDEZVOUS.newChannel());

    }

}
