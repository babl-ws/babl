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
package com.aitusoftware.babl.performance;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class PortProbe
{
    PortProbe()
    {
    }

    public static void ensurePortOpen(final int port)
    {
        final long startupDeadline = System.currentTimeMillis() + 5_000L;
        boolean available = false;
        while (!available && System.currentTimeMillis() < startupDeadline)
        {
            try (Socket socket = new Socket())
            {
                socket.connect(new InetSocketAddress("localhost", port));
                available = true;
            }
            catch (final IOException e)
            {
                // not yet listening
            }
        }
        assertThat(available).isTrue();
    }
}
