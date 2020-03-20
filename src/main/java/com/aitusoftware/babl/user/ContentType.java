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

import com.aitusoftware.babl.websocket.Constants;

/**
 * Represents the web-socket opcode defining the content type of a message.
 */
public enum ContentType
{
    BINARY(Constants.OPCODE_BINARY),
    TEXT(Constants.OPCODE_TEXT);

    private final int opCode;

    private static final ContentType[] CONTENT_TYPE_BY_OP_CODE = new ContentType[]
    {
        TEXT,
        BINARY
    };

    ContentType(final int opCode)
    {
        this.opCode = opCode;
    }

    /**
     * Returns the web-socket op-code of the content type.
     *
     * @return the op-code
     */
    public int opCode()
    {
        return opCode;
    }

    /**
     * Returns the content type corresponding to the supplied op-code.
     *
     * @param opCode the op-code
     * @return the content type
     */
    public static ContentType forOpCode(final int opCode)
    {
        return CONTENT_TYPE_BY_OP_CODE[opCode - 1];
    }
}
