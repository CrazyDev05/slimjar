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
import io.github.slimjar.injector.loader.InstrumentationInjectable;
import io.github.slimjar.injector.loader.UnsafeInjectable;
import io.github.slimjar.injector.loader.WrappedInjectableClassLoader;
import io.github.slimjar.resolver.data.Repository;
import org.jetbrains.annotations.NotNull;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;

@FunctionalInterface
public interface InjectableFactory {
    @NotNull
    Injectable create(
            @NotNull final Path downloadPath,
            @NotNull final Collection<Repository> repositories,
            @NotNull final ClassLoader loader
    ) throws InjectorException;

    @NotNull
    default Injectable create(
            @NotNull final Path downloadPath,
            @NotNull final Collection<Repository> repositories
    ) throws InjectorException {
        return create(downloadPath, repositories, InjectableFactory.class.getClassLoader());
    }

    static @NotNull InjectableFactory selecting(@NotNull final InjectableFactory fallback, @NotNull final InjectableFactory @NotNull ... factories) {
        return new SelectingInjectableFactory(fallback, factories);
    }

    @NotNull InjectableFactory INSTRUMENTATION = (downloadPath, repositories, loader) -> InstrumentationInjectable.create(downloadPath, repositories);

    @NotNull InjectableFactory INJECTABLE = (downloadPath, repositories, loader) -> {
        if (loader instanceof Injectable injectable) {
            return injectable;
        } else {
            throw new InjectorException("Loader is not an instance of Injectable!");
        }
    };

    @NotNull InjectableFactory WRAPPED = (downloadPath, repositories, loader) -> {
        if (!(loader instanceof URLClassLoader urlClassLoader))
            throw new InjectorException("Loader is not an instance of URLClassLoader!");
        try {
            return new WrappedInjectableClassLoader(urlClassLoader);
        } catch (final Throwable err) {
            throw new InjectorException("Failed to create WrappedInjectableClassLoader!", err);
        }
    };

    @NotNull InjectableFactory UNSAFE = (downloadPath, repositories, loader) -> {
        try {
            return UnsafeInjectable.create(loader);
        } catch (final Throwable err) {
            throw new InjectorException("Failed to create UnsafeInjectable!", err);
        }
    };

    @NotNull InjectableFactory ERROR = (downloadPath, repositories, loader) -> {
        throw new InjectorException("Failed to create injectable for class loader: " + loader);
    };

    @NotNull InjectableFactory DEFAULT = selecting(INSTRUMENTATION, INJECTABLE, WRAPPED, UNSAFE);
}
