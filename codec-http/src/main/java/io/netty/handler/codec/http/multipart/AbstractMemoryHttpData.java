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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.handler.codec.http.HttpConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

/**
 * Abstract Memory HttpData implementation
 */
public abstract class AbstractMemoryHttpData extends AbstractHttpData {

    private ChannelBuffer channelBuffer;
    private int chunkPosition;
    protected boolean isRenamed;

    public AbstractMemoryHttpData(String name, Charset charset, long size) {
        super(name, charset, size);
    }

    @Override
    public void setContent(ChannelBuffer buffer) throws IOException {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        long localsize = buffer.readableBytes();
        if (definedSize > 0 && definedSize < localsize) {
            throw new IOException("Out of size: " + localsize + " > " +
                    definedSize);
        }
        channelBuffer = buffer;
        size = localsize;
        completed = true;
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new NullPointerException("inputStream");
        }
        ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
        byte[] bytes = new byte[4096 * 4];
        int read = inputStream.read(bytes);
        int written = 0;
        while (read > 0) {
            buffer.writeBytes(bytes);
            written += read;
            read = inputStream.read(bytes);
        }
        size = written;
        if (definedSize > 0 && definedSize < size) {
            throw new IOException("Out of size: " + size + " > " + definedSize);
        }
        channelBuffer = buffer;
        completed = true;
    }

    @Override
    public void addContent(ChannelBuffer buffer, boolean last)
            throws IOException {
        if (buffer != null) {
            long localsize = buffer.readableBytes();
            if (definedSize > 0 && definedSize < size + localsize) {
                throw new IOException("Out of size: " + (size + localsize) +
                        " > " + definedSize);
            }
            size += localsize;
            if (channelBuffer == null) {
                channelBuffer = buffer;
            } else {
                channelBuffer = ChannelBuffers.wrappedBuffer(
                        channelBuffer, buffer);
            }
        }
        if (last) {
            completed = true;
        } else {
            if (buffer == null) {
                throw new NullPointerException("buffer");
            }
        }
    }

    @Override
    public void setContent(File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("file");
        }
        long newsize = file.length();
        if (newsize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "File too big to be loaded in memory");
        }
        FileInputStream inputStream = new FileInputStream(file);
        FileChannel fileChannel = inputStream.getChannel();
        byte[] array = new byte[(int) newsize];
        ByteBuffer byteBuffer = ByteBuffer.wrap(array);
        int read = 0;
        while (read < newsize) {
            read += fileChannel.read(byteBuffer);
        }
        fileChannel.close();
        byteBuffer.flip();
        channelBuffer = ChannelBuffers.wrappedBuffer(byteBuffer);
        size = newsize;
        completed = true;
    }

    @Override
    public void delete() {
        // nothing to do
    }

    @Override
    public byte[] get() {
        if (channelBuffer == null) {
            return new byte[0];
        }
        byte[] array = new byte[channelBuffer.readableBytes()];
        channelBuffer.getBytes(channelBuffer.readerIndex(), array);
        return array;
    }

    @Override
    public String getString() {
        return getString(HttpConstants.DEFAULT_CHARSET);
    }

    @Override
    public String getString(Charset encoding) {
        if (channelBuffer == null) {
            return "";
        }
        if (encoding == null) {
            return getString(HttpConstants.DEFAULT_CHARSET);
        }
        return channelBuffer.toString(encoding);
    }

    /**
     * Utility to go from a In Memory FileUpload
     * to a Disk (or another implementation) FileUpload
     * @return the attached ChannelBuffer containing the actual bytes
     */
    @Override
    public ChannelBuffer getChannelBuffer() {
        return channelBuffer;
    }

    @Override
    public ChannelBuffer getChunk(int length) throws IOException {
        if (channelBuffer == null || length == 0 || channelBuffer.readableBytes() == 0) {
            chunkPosition = 0;
            return ChannelBuffers.EMPTY_BUFFER;
        }
        int sizeLeft = channelBuffer.readableBytes() - chunkPosition;
        if (sizeLeft == 0) {
            chunkPosition = 0;
            return ChannelBuffers.EMPTY_BUFFER;
        }
        int sliceLength = length;
        if (sizeLeft < length) {
            sliceLength = sizeLeft;
        }
        ChannelBuffer chunk = channelBuffer.slice(chunkPosition, sliceLength);
        chunkPosition += sliceLength;
        return chunk;
    }

    @Override
    public boolean isInMemory() {
        return true;
    }

    @Override
    public boolean renameTo(File dest) throws IOException {
        if (dest == null) {
            throw new NullPointerException("dest");
        }
        if (channelBuffer == null) {
            // empty file
            dest.createNewFile();
            isRenamed = true;
            return true;
        }
        int length = channelBuffer.readableBytes();
        FileOutputStream outputStream = new FileOutputStream(dest);
        FileChannel fileChannel = outputStream.getChannel();
        int written = 0;
        while (written < length) {
            written += channelBuffer.readBytes(fileChannel, length - written);
        }
        fileChannel.force(false);
        fileChannel.close();
        isRenamed = true;
        return written == length;
    }

    @Override
    public File getFile() throws IOException {
        throw new IOException("Not represented by a file");
    }
}
