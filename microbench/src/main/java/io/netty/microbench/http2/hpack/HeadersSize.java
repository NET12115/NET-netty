/*
 * Copyright 2015 The Netty Project
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

/*
 * Copyright 2015 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.microbench.http2.hpack;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Enum that indicates the size of the headers to be used for the benchmark.
 */
public enum HeadersSize {
    SMALL(5, 20, 40),
    MEDIUM(20, 40, 80),
    LARGE(100, 100, 300);

    private final int numHeaders;
    private final int nameLength;
    private final int valueLength;

    HeadersSize(int numHeaders, int nameLength, int valueLength) {
        this.numHeaders = numHeaders;
        this.nameLength = nameLength;
        this.valueLength = valueLength;
    }

    public List<Header> newHeaders(boolean limitAscii) {
        return Header.createHeaders(numHeaders, nameLength, valueLength, limitAscii);
    }

    public ByteArrayOutputStream newOutputStream() {
        return new ByteArrayOutputStream(numHeaders * (nameLength + valueLength));
    }
}
