/*
 * Copyright 2019-2020 Aitu Software Limited.
 *
 * https://aitusoftware.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aitusoftware.babl.monitoring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.agrona.IoUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MappedFile implements AutoCloseable
{
    private final MappedByteBuffer mappedByteBuffer;
    private final AtomicBuffer buffer;
    private final Path file;

    public MappedFile(final Path file, final long length)
    {
        this(file, length, true);
    }

    public MappedFile(final Path file, final long length, final boolean createNew)
    {
        this.file = file;
        try
        {
            if (createNew)
            {
                if (Files.exists(file))
                {
                    Files.delete(file);
                }
                Files.createDirectories(file.getParent());
            }
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException(e);
        }
        mappedByteBuffer = createNew ?
            IoUtil.mapNewFile(file.toFile(), length) : IoUtil.mapExistingFile(file.toFile(), "mapped-file");
        buffer = new UnsafeBuffer(mappedByteBuffer);
    }

    AtomicBuffer buffer()
    {
        return buffer;
    }

    @Override
    public void close()
    {
        IoUtil.unmap(mappedByteBuffer);
    }

    @Override
    public String toString()
    {
        return String.format("MappedFile{%s}", file);
    }
}
