/*
 * Copyright 2010 Red Hat, Inc.
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
package org.jboss.netty.handler.codec.rtsp;

import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMessageDecoder;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Amit Bhayani (amit.bhayani@gmail.com)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public class RtspRequestDecoder extends HttpMessageDecoder {

    public RtspRequestDecoder() {
        super();
    }

    public RtspRequestDecoder(int maxInitialLineLength, int maxHeaderSize,
            int maxChunkSize) {
        super(maxInitialLineLength, maxHeaderSize, maxChunkSize);
    }

    @Override
    protected HttpMessage createMessage(String[] initialLine) throws Exception {
        return new DefaultHttpRequest(RtspVersion.valueOf(initialLine[2]),
                RtspMethod.valueOf(initialLine[0]), initialLine[1]);
    }

    @Override
    protected boolean isDecodingRequest() {
        return true;
    }

}
