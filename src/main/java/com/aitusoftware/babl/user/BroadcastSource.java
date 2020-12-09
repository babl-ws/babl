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
package com.aitusoftware.babl.user;

import com.aitusoftware.babl.websocket.broadcast.Broadcast;

/**
 * User applications should implement this interface to obtain a handle to the {@code Broadcast} interface.
 */
public interface BroadcastSource
{
    /**
     * Called by the application container on start-up.
     * @param broadcast the handle to send broadcast messages
     */
    void setBroadcast(Broadcast broadcast);
}
