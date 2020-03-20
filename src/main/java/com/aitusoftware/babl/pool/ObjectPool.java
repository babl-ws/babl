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
package com.aitusoftware.babl.pool;

import java.util.ArrayDeque;
import java.util.function.Supplier;

/**
 * Stores re-usable objects.
 * @param <T> the type of object that will be pooled
 * @see Pooled
 */
public final class ObjectPool<T extends Pooled>
{
    private final ArrayDeque<T> pool = new ArrayDeque<>();
    private final Supplier<T> factory;

    /**
     * Construct a new object pool.
     * @param factory     factory for creating new instances
     * @param initialSize the initial size of the pool
     */
    public ObjectPool(final Supplier<T> factory, final int initialSize)
    {
        this.factory = factory;
        for (int i = 0; i < initialSize; i++)
        {
            pool.addLast(factory.get());
        }
    }

    /**
     * Acquire an existing instance from the pool, or create a new instance.
     * @return the acquired instance
     */
    public T acquire()
    {
        if (pool.isEmpty())
        {
            return factory.get();
        }
        return pool.removeLast();
    }

    /**
     * Return an instance to the pool for re-use.
     * @param instance the released instance
     */
    public void release(final T instance)
    {
        instance.reset();
        pool.addLast(instance);
    }
}