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
package com.aitusoftware.babl.ext;

import java.nio.file.Path;

import com.aitusoftware.babl.monitoring.MappedApplicationAdapterStatistics;
import com.aitusoftware.babl.monitoring.MappedSessionContainerAdapterStatistics;
import com.aitusoftware.babl.monitoring.MappedSessionContainerStatistics;
import com.aitusoftware.babl.monitoring.MappedSessionStatistics;

/**
 * Consumer interface for handling monitoring data from a instance of the web-socket server.
 */
public interface MonitoringConsumer
{
    void applicationAdapterStatistics(MappedApplicationAdapterStatistics applicationAdapterStatistics);

    void sessionAdapterStatistics(MappedSessionContainerAdapterStatistics[] sessionAdapterStatistics);

    void errorBuffers(MappedErrorBuffer[] errorBuffers);

    void sessionContainerStatistics(MappedSessionContainerStatistics[] sessionContainerStatistics);

    void sessionStatistics(Path statisticsFile, MappedSessionStatistics sessionStatistics);
}