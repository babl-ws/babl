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
package com.aitusoftware.babl.config;

import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.websocket.routing.ApplicationIdSelector;
import com.aitusoftware.babl.websocket.routing.SingleApplicationInstanceSelector;

public final class RoutingConfig
{
    private ApplicationIdSelector applicationIdSelector = new SingleApplicationInstanceSelector();

    private Application singletonApplication;
    private Application[] applicationInstances;

    public RoutingConfig singletonApplication(final Application application)
    {
        this.singletonApplication = application;
        return this;
    }

    public RoutingConfig applicationInstances(final Application[] applicationInstances)
    {
        this.applicationInstances = applicationInstances;
        return this;
    }

    public ApplicationIdSelector applicationIdSelector()
    {
        return applicationIdSelector;
    }

    public RoutingConfig applicationIdSelector(final ApplicationIdSelector applicationIdSelector)
    {
        this.applicationIdSelector = applicationIdSelector;
        return this;
    }
}