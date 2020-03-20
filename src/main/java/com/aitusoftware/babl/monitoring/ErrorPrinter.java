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

import java.io.File;
import java.nio.MappedByteBuffer;
import java.time.Instant;

import org.agrona.IoUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.errors.ErrorConsumer;
import org.agrona.concurrent.errors.ErrorLogReader;

public final class ErrorPrinter
{
    public static void main(final String[] args)
    {
        final MappedByteBuffer buffer = IoUtil.mapExistingFile(
            new File(args[0], ServerMarkFile.MARK_FILE_NAME), "error-buffer",
            ServerMarkFile.ERROR_BUFFER_OFFSET, ServerMarkFile.ERROR_BUFFER_LENGTH);
        try
        {
            if (0 == ErrorLogReader.read(new UnsafeBuffer(buffer), new PrintingErrorConsumer()))
            {
                System.out.println("No errors reported.");
            }
        }
        finally
        {
            IoUtil.unmap(buffer);
        }
    }

    private static class PrintingErrorConsumer implements ErrorConsumer
    {
        @Override
        public void accept(
            final int observationCount,
            final long firstObservationTimestamp,
            final long lastObservationTimestamp,
            final String encodedException)
        {
            System.out.printf("Observations: %d, first: %s, last: %s%n",
                observationCount,
                Instant.ofEpochMilli(firstObservationTimestamp),
                Instant.ofEpochMilli(lastObservationTimestamp));
            System.out.printf("%s%n", encodedException);
        }
    }
}