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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public final class ConfigDumper
{
    public static void main(final String[] args) throws Exception
    {
        final Field[] fields = BablConfig.class.getDeclaredFields();
        for (final Field field : fields)
        {
            field.setAccessible(true);
            if (field.getName().endsWith("Config"))
            {
                final Class<?>[] classes = field.getType().getClasses();
                for (final Class<?> cls : classes)
                {
                    if (cls.getName().endsWith("Constants"))
                    {
                        final Field[] constantFields = cls.getDeclaredFields();
                        final Map<String, String> defaultValues = new HashMap<>();
                        final SortedMap<String, String> propertyNames = new TreeMap<>();
                        for (final Field constantField : constantFields)
                        {
                            constantField.setAccessible(true);
                            if ((constantField.getModifiers() & Modifier.STATIC) != 0)
                            {
                                final String fieldName = constantField.getName();
                                if (fieldName.endsWith("_DEFAULT"))
                                {
                                    defaultValues.put(fieldName.substring(0, fieldName.length() - 8),
                                        String.valueOf(constantField.get(null)));
                                }
                                else if (fieldName.endsWith("_PROPERTY"))
                                {
                                    propertyNames.put(String.valueOf(constantField.get(null)),
                                        fieldName.substring(0, fieldName.length() - 9));
                                }
                            }
                        }
                        for (final Map.Entry<String, String> entry : propertyNames.entrySet())
                        {
                            System.out.printf("%s=%s%n", entry.getKey(), defaultValues.get(entry.getValue()));
                        }
                    }
                }
            }
        }
    }
}