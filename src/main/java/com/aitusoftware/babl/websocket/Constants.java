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

import java.nio.ByteOrder;

/**
 * Constant values relating to the web-socket protocol.
 */
public interface Constants
{
    int OPCODE_CONTINUATION = 0x0;
    int OPCODE_TEXT = 0x1;
    int OPCODE_BINARY = 0x2;
    int OPCODE_CLOSE = 0x8;
    int OPCODE_PING = 0x9;
    int OPCODE_PONG = 0xA;

    int SMALL_MESSAGE_SIZE_INDICATOR = 125;
    int MEDIUM_MESSAGE_SIZE_INDICATOR = 126;
    int LARGE_MESSAGE_SIZE_INDICATOR = 127;

    short CLOSE_REASON_NORMAL = 1000;
    short CLOSE_REASON_GOING_AWAY = 1001;
    short CLOSE_REASON_PROTOCOL_ERROR = 1002;
    short CLOSE_REASON_INVALID_DATA_TYPE = 1003;
    short CLOSE_REASON_INVALID_DATA_VALUE = 1007;
    short CLOSE_REASON_INVALID_DATA_SIZE = 1009;

    short MIN_APPLICATION_CLOSE_REASON = 4000;
    short MAX_APPLICATION_CLOSE_REASON = 4999;

    ByteOrder NETWORK_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
}
