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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

public final class WrappingPreResolutionDataProvider implements PreResolutionDataProvider {
    @NotNull private final PreResolutionDataReader resolutionDataReader;
    @NotNull private final URL resolutionFileURL;
    @Nullable private Map<@NotNull String, @NotNull ResolutionResult> cachedData = null;

    @Contract(pure = true)
    public WrappingPreResolutionDataProvider(
        @NotNull final PreResolutionDataReader resolutionDataReader,
        @NotNull final URL resolutionFileURL
    ) {
        this.resolutionDataReader = resolutionDataReader;
        this.resolutionFileURL = resolutionFileURL;
    }

    @Override
    @Contract(pure = true)
    public @NotNull Map<@NotNull String, @NotNull ResolutionResult> get() {
        if (cachedData != null) {
            return cachedData;
        }

        try (final var is = resolutionFileURL.openStream()) {
            cachedData = resolutionDataReader.read(is);
            return cachedData;
        } catch (final IOException | ResolutionException ignored) {
            return Collections.emptyMap();
        }
    }
}
