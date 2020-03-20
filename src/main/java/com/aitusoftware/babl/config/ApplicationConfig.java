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
import java.util.Objects;

import com.aitusoftware.babl.user.Application;

/**
 * Configuration for the user application.
 *
 * Will attempt to instantiate the class specified by {@code APPLICATION_CLASS_NAME_PROPERTY}.
 */
public final class ApplicationConfig
{
    private Application application;

    private String applicationClassName = System.getProperty(Constants.APPLICATION_CLASS_NAME_PROPERTY);

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
    }
}