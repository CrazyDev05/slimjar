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

package io.github.slimjar.resolver.reader.resolution;

import io.github.slimjar.exceptions.ResolutionException;
import io.github.slimjar.resolver.ResolutionResult;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.slimjar.util.Serialization.readList;

@FunctionalInterface
public interface PreResolutionDataReader {
    @NotNull Map<@NotNull String, @NotNull ResolutionResult> read(@NotNull final InputStream inputStream) throws ResolutionException;

    PreResolutionDataReader DEFAULT = inputStream -> {
        try (final DataInputStream in = new DataInputStream(inputStream)) {
            final List<Map.Entry<String, ResolutionResult>> list = readList(in, din -> Map.entry(din.readUTF(), ResolutionResult.read(din)));
            final Map<String, ResolutionResult> result = new LinkedHashMap<>(list.size());
            for (final Map.Entry<String, ResolutionResult> entry : list) {
                result.put(entry.getKey(), entry.getValue());
            }
            return result;
        } catch (final IOException e) {
            throw new ResolutionException("Failed to read dependency file", e);
        }
    };
}
