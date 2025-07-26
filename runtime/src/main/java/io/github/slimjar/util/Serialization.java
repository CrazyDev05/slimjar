/*
 * MIT License
 *
 * Copyright (c) 2021 Vaishnav Anil
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.slimjar.util;

import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class Serialization {
    private Serialization() { }

    public static void writeURL(
            @NotNull final URL url,
            @NotNull final DataOutput out
    ) throws IOException {
        out.writeUTF(url.toExternalForm());
    }

    public static <T> void writeList(
            @NotNull final Collection<@NotNull T> list,
            @NotNull final DataOutput out,
            @NotNull final Serializer<@NotNull T> writer
    ) throws IOException {
        int size = list.size();
        writeSignedVarInt(size, out);
        for (T value : list) {
            writer.invoke(value, out);
        }
    }

    public static void writeSignedVarInt(int value, @NotNull final DataOutput out) throws IOException {
        value = (value << 1) ^ (value >> 31);
        while ((value & 0xFFFFFF80) != 0L) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value & 0x7F);
    }

    public static URL readURL(@NotNull final DataInput in) throws IOException {
        return URI.create(in.readUTF()).toURL();
    }

    public static <T> List<T> readList(
            @NotNull final DataInput in,
            @NotNull final Deserializer<@NotNull T> deserializer
    ) throws IOException {
        final int size = readSignedVarInt(in);
        final List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(deserializer.invoke(in));
        }
        return list;
    }

    public static int readSignedVarInt(DataInput in) throws IOException {
        int value = 0;
        int i = 0;
        int b;
        while (((b = in.readByte()) & 0x80) != 0) {
            value |= (b & 0x7F) << i;
            i += 7;
            if (i > 35) {
                throw new IllegalArgumentException("Variable length quantity is too long");
            }
        }
        int raw = value | (b << i);
        int temp = (((raw << 31) >> 31) ^ raw) >> 1;
        return temp ^ (raw & (1 << 31));
    }

    @FunctionalInterface
    public interface Deserializer<T> {
        T invoke(@NotNull final DataInput in) throws IOException;
    }

    @FunctionalInterface
    public interface Serializer<T> {
        void invoke(@NotNull final T value, @NotNull final DataOutput out) throws IOException;
    }
}
