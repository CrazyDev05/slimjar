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

package io.github.slimjar.injector.loader.factory;

import io.github.slimjar.exceptions.InjectorException;
import io.github.slimjar.injector.loader.Injectable;
import io.github.slimjar.resolver.data.Repository;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public final class SelectingInjectableFactory implements InjectableFactory {

    @NotNull private final InjectableFactory fallback;
    @NotNull private final List<@NotNull InjectableFactory> factories;

    public SelectingInjectableFactory(
            @NotNull final InjectableFactory fallback,
            @NotNull final InjectableFactory @NotNull ... factories) {
        if (factories.length == 0) throw new IllegalArgumentException("No factories provided");
        this.fallback = fallback;
        this.factories = List.of(factories);
    }

    public @NotNull Injectable create(
        @NotNull final Path downloadPath,
        @NotNull final Collection<Repository> repositories,
        @NotNull final ClassLoader classLoader
    ) throws InjectorException {

        ClassLoader current = classLoader;
        while (current != null) {
            for (final InjectableFactory factory : factories) {
                try {
                    return factory.create(downloadPath, repositories, current);
                } catch (final InjectorException ignored) {}
            }
            current = current.getParent();
        }

        return fallback.create(downloadPath, repositories, classLoader);
    }
}
