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

package io.github.slimjar.resolver.mirrors;

import io.github.slimjar.logging.LocationAwareProcessLogger;
import io.github.slimjar.logging.ProcessLogger;
import io.github.slimjar.resolver.data.Mirror;
import io.github.slimjar.resolver.data.Repository;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

public final class SimpleMirrorSelector implements MirrorSelector {
    @NotNull private static final ProcessLogger LOGGER = LocationAwareProcessLogger.generic();

    @Override
    @Contract(pure = true)
    public @NotNull Collection<@NotNull Repository> select(
        final @NotNull Collection<@NotNull Repository> mainRepositories,
        final @NotNull Collection<@NotNull Mirror> mirrors
    ) {
        if (mainRepositories.isEmpty()) return mainRepositories;
        if (mirrors.isEmpty()) return mainRepositories;

        final var repositoryMirrors = mirrors.stream()
                .collect(Collectors.toMap(
                        m -> m.original().toString(),
                        m -> m.mirroring().toString(),
                        (existing, replacement) -> {
                            LOGGER.error("Duplicate mirror found '{}' and '{}'", existing, replacement);
                            return existing;
                        }
                ));

        return mainRepositories.stream()
                .distinct()
                .map(original -> {
                    var url = original.url().toString();
                    var visited = new LinkedHashSet<String>();

                    String mirror;
                    while ((mirror = repositoryMirrors.get(url)) != null) {
                        if (!visited.add(url)) {
                            LOGGER.error("Circular mirror detected for '{}'", original.url());
                            break;
                        }
                        url = mirror;
                    }

                    try {
                        return new Repository(URI.create(url).toURL());
                    } catch (MalformedURLException e) {
                        LOGGER.error("Failed to parse mirror URL '{}'", url);
                        return original;
                    }
                })
                .distinct()
                .toList();
    }
}
