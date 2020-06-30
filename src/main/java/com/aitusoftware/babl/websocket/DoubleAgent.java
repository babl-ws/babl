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
package com.aitusoftware.babl.websocket;

import org.agrona.concurrent.Agent;

final class DoubleAgent implements Agent
{
    private final Agent one;
    private final Agent two;
    private final String roleName;

    DoubleAgent(final Agent one, final Agent two)
    {
        this.one = one;
        this.two = two;
        this.roleName = "[" + one.roleName() + "," + two.roleName() + "]";
    }

    @Override
    public int doWork() throws Exception
    {
        return one.doWork() + two.doWork();
    }

    @Override
    public String roleName()
    {
        return roleName;
    }

    @Override
    public void onStart()
    {
        one.onStart();
        two.onStart();
    }

    @Override
    public void onClose()
    {
        one.onClose();
        two.onClose();
    }
}
