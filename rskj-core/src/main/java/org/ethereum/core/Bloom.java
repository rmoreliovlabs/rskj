/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.core;

import co.rsk.core.types.bytes.Bytes;
import org.ethereum.util.ByteUtil;

import java.util.Arrays;

/**
 * See http://www.herongyang.com/Java/Bit-String-Set-Bit-to-Byte-Array.html.
 *
 * @author Roman Mandeleil
 * @since 20.11.2014
 */

public class Bloom {
    public static final int BLOOM_BYTES = 256;

    static final int _8STEPS = 8;
    static final int _3LOW_BITS = 7;
    static final int ENSURE_BYTE = 255;

    private byte[] data = new byte[BLOOM_BYTES];

    public Bloom() {
    }

    public Bloom(byte[] data) {
        this.data = data;
    }

    public static Bloom create(byte[] toBloom) {

        int mov1 = (((toBloom[0] & ENSURE_BYTE) & (_3LOW_BITS)) << _8STEPS) + ((toBloom[1]) & ENSURE_BYTE);
        int mov2 = (((toBloom[2] & ENSURE_BYTE) & (_3LOW_BITS)) << _8STEPS) + ((toBloom[3]) & ENSURE_BYTE);
        int mov3 = (((toBloom[4] & ENSURE_BYTE) & (_3LOW_BITS)) << _8STEPS) + ((toBloom[5]) & ENSURE_BYTE);

        byte[] data = new byte[256];
        Bloom bloom = new Bloom(data);

        ByteUtil.setBit(data, mov1, 1);
        ByteUtil.setBit(data, mov2, 1);
        ByteUtil.setBit(data, mov3, 1);

        return bloom;
    }

    public void or(Bloom bloom) {
        for (int i = 0; i < data.length; ++i) {
            data[i] |= bloom.data[i];
        }
    }

    public boolean matches(Bloom topicBloom) {
        Bloom copy = copy();
        copy.or(topicBloom);
        return this.equals(copy);
    }

    public byte[] getData() {
        return data;
    }

    public Bloom copy() {
        return new Bloom(Arrays.copyOf(getData(), getData().length));
    }

    @Override
    public String toString() {
        return Bytes.toPrintableString(data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Bloom bloom = (Bloom) o;

        return Arrays.equals(data, bloom.data);

    }

    @Override
    public int hashCode() {
        return data != null ? Arrays.hashCode(data) : 0;
    }
}
