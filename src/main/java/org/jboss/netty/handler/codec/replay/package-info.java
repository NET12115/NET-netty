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

/**
 * Specialized variation of {@link io.netty.handler.codec.frame.FrameDecoder}
 * which enables implementation of a non-blocking decoder in the blocking I/O
 * paradigm.
 *
 * @apiviz.exclude ^java\.lang\.
 * @apiviz.exclude \.SimpleChannelUpstreamHandler$
 * @apiviz.exclude \.VoidEnum$
 * @apiviz.exclude \.codec\.(?!replay)[a-z0-9]+\.
 */
package io.netty.handler.codec.replay;

