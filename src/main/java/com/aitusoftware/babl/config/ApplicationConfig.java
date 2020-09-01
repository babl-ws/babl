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

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import com.aitusoftware.babl.user.Application;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.IdleStrategy;

/**
 * Configuration for the user application.
 *
 * Will attempt to instantiate the class specified by {@code APPLICATION_CLASS_NAME_PROPERTY}.
 */
public final class ApplicationConfig
{
    private final PerformanceConfig performanceConfig;
    private Application application;

    private String applicationClassName = System.getProperty(Constants.APPLICATION_CLASS_NAME_PROPERTY);
    private BiFunction<Path, IdleStrategy, IdleStrategy> applicationIdleStrategyFactory;
    private Supplier<IdleStrategy> idleStrategySupplier;
    private Agent additionalWork;

    ApplicationConfig(final PerformanceConfig performanceConfig)
    {
        this.performanceConfig = performanceConfig;
    }

    /**
     * Sets the application.
     * @param application the application
     * @return this for a fluent API
     */
    public ApplicationConfig application(final Application application)
    {
        this.application = application;
        return this;
    }

    /**
     * Returns the application.
     * @return the application
     */
    public Application application()
    {
        return application;
    }

    /**
     * Sets the application class name.
     * @param applicationClassName the class name
     * @return this for a fluent API
     */
    public ApplicationConfig applicationClassName(final String applicationClassName)
    {
        this.applicationClassName = applicationClassName;
        return this;
    }

    /**
     * Sets the idle strategy supplier.
     * @param applicationIdleStrategySupplier the idle strategy supplier
     * @return this for a fluent API
     */
    public ApplicationConfig applicationIdleStrategySupplier(
        final Supplier<IdleStrategy> applicationIdleStrategySupplier)
    {
        this.idleStrategySupplier = applicationIdleStrategySupplier;
        return this;
    }

    /**
     * Gets the idle strategy for the application container.
     * @param directory the data directory
     * @return the idle strategy
     */
    public IdleStrategy applicationIdleStrategy(final String directory)
    {
        return applicationIdleStrategyFactory.apply(Paths.get(directory), idleStrategySupplier.get());
    }

    /**
     * Set additional work to be done on the application thread.
     * @param additionalWork additional work
     * @return this for a fluent API
     */
    public ApplicationConfig additionalWork(final Agent additionalWork)
    {
        this.additionalWork = additionalWork;
        return this;
    }

    /**
     * Gets the additional work to be done on the application thread.
     * @return additional work
     */
    public Agent additionalWork()
    {
        return additionalWork;
    }

    @SuppressWarnings("unchecked")
    void conclude()
    {
        if (applicationClassName != null)
        {
            if (application != null)
            {
                throw new IllegalStateException("Specify either applicationClassName or application");
            }
            try
            {
                application = (Application)Class.forName(applicationClassName).getConstructor().newInstance();
            }
            catch (final ClassNotFoundException |
                IllegalAccessException |
                InstantiationException |
                NoSuchMethodException |
                InvocationTargetException e)
            {
                throw new RuntimeException("Unable to instantiate class " + applicationClassName, e);
            }
        }

        Objects.requireNonNull(application, "application must be set");

        if (idleStrategySupplier == null)
        {
            idleStrategySupplier =
                () -> ConfigUtil.mapIdleStrategy(Constants.IDLE_STRATEGY_PROPERTY, performanceConfig.performanceMode());
        }
        if (System.getProperty(ApplicationConfig.Constants.IDLE_STRATEGY_FACTORY_PROPERTY) != null)
        {
            applicationIdleStrategyFactory =
                (BiFunction<Path, IdleStrategy, IdleStrategy>)ConfigUtil.instantiate(BiFunction.class)
                    .apply(System.getProperty(Constants.IDLE_STRATEGY_FACTORY_PROPERTY));
        }
        else
        {
            applicationIdleStrategyFactory = (path, configured) -> configured;
        }
    }

    /**
     * Constants used in configuration.
     */
    public static final class Constants
    {
        /**
         * The system property used to set the application class name
         */
        public static final String APPLICATION_CLASS_NAME_PROPERTY = "babl.application.class.name";

        /**
         * System property used to configure a factory for the application idle strategy
         */
        public static final String IDLE_STRATEGY_FACTORY_PROPERTY = "babl.application.idle.strategy.factory";

        /**
         * System property used to configure the application event-loop idle strategy
         */
        public static final String IDLE_STRATEGY_PROPERTY = "babl.application.idle.strategy";

        /**
         * Default value for application idle strategy
         */
        public static final String IDLE_STRATEGY_DEFAULT = "BUSY_SPIN";
    }
}