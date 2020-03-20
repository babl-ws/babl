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

import com.aitusoftware.babl.pool.Pooled;

/**
 * A container to describing the result of validation for a given session.
 */
public final class ValidationResult implements Pooled
{
    /**
     * Indicates that validation was successful.
     */
    public static final short CONNECTION_VALID = 0;
    /**
     * Indicates that validation was not successful.
     */
    public static final short VALIDATION_FAILED = 4700;

    private long sessionId;
    private short resultCode;

    ValidationResult()
    {
    }

    void sessionId(final long sessionId)
    {
        this.sessionId = sessionId;
    }

    /**
     * Returns the ID of the session that is being validated.
     * @return the session ID
     */
    public long sessionId()
    {
        return sessionId;
    }

    /**
     * Used by a validator implementation to indicate that validation was successful for the given session.
     */
    public void validationSuccess()
    {
        this.resultCode = CONNECTION_VALID;
    }

    /**
     * Used by a validator implementation to indicate that validation was not successful for the given session.
     * @param resultCode the web socket close reason code that will be sent to the client
     */
    public void validationFailure(final short resultCode)
    {
        if (resultCode < Constants.MIN_APPLICATION_CLOSE_REASON || resultCode > Constants.MAX_APPLICATION_CLOSE_REASON)
        {
            this.resultCode = VALIDATION_FAILED;
        }
        else
        {
            this.resultCode = resultCode;
        }
    }

    short resultCode()
    {
        return resultCode;
    }
}