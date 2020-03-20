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
package com.aitusoftware.babl.monitoring;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

class BitSetTest
{
    @Test
    void shouldSetBits()
    {
        assertSetBit(0L, 63, 1L << 63);
        assertSetBit(0L, 0, 1L);
        assertSetBit(0xFF00FF00FF00FF00L, 0, 0xFF00FF00FF00FF01L);
        assertSetBit(0xFF00FF00FF00FF00L, 1, 0xFF00FF00FF00FF02L);
    }

    @Test
    void shouldClearBits()
    {
        assertClearedBit(1L, 0, 0L);
        assertClearedBit(0L, 63, 0L);
        assertClearedBit(1L << 63, 63, 0L);
        assertClearedBit(0xFF00FF00FF00FF03L, 0, 0xFF00FF00FF00FF02L);
        assertClearedBit(0xFF00FF00FF00FF03L, 1, 0xFF00FF00FF00FF01L);
    }

    @Test
    void testIndices()
    {
        for (int i = 0; i < 64; i++)
        {
            final long set = BitSet.setBit(0L, i);
            assertThat(set).isEqualTo(1L << i);
            assertThat(BitSet.clearBit(set, i)).isEqualTo(0L);
        }
    }

    @Test
    void shouldFindZeroBits()
    {
        assertThat(BitSet.hasZeroBit(0L)).isTrue();
        assertThat(BitSet.hasZeroBit(Long.MAX_VALUE)).isTrue();
        assertThat(BitSet.hasZeroBit(0xFFFFFFFFFFFFFFFFL + 1)).isTrue();
        assertThat(BitSet.hasZeroBit(0xFFFFFFFFFFFFFFFFL)).isFalse();
    }

    @Test
    void shouldFindZeroBitIndex()
    {
        assertThat(BitSet.indexOfZeroBit(0L)).isEqualTo(0);
        assertThat(BitSet.indexOfZeroBit(1L)).isEqualTo(1);
        assertThat(BitSet.indexOfZeroBit(3L)).isEqualTo(2);
        assertThat(BitSet.indexOfZeroBit(0b1010L)).isEqualTo(0);
        assertThat(BitSet.indexOfZeroBit(0b1011L)).isEqualTo(2);
    }

    private void assertClearedBit(final long input, final int bitIndex, final long expected)
    {
        assertThat(BitSet.clearBit(input, bitIndex)).isEqualTo(expected);
    }

    private void assertSetBit(final long input, final int bitIndex, final long expected)
    {
        assertThat(BitSet.setBit(input, bitIndex)).isEqualTo(expected);
    }
}