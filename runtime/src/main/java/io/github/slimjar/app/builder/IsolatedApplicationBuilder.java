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

package io.github.slimjar.app.builder;

import io.github.slimjar.app.Application;
import io.github.slimjar.exceptions.SlimJarException;
import io.github.slimjar.injector.loader.Injectable;
import io.github.slimjar.injector.loader.IsolatedInjectableClassLoader;
import io.github.slimjar.util.Reflections;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Collections;

public final class IsolatedApplicationBuilder extends ModularApplicationBuilder<IsolatedApplicationBuilder> {
    @Nullable private final Object @NotNull [] arguments;
    @NotNull private final String applicationClass;
    @Nullable private ClassLoader parentClassloader;

    @Contract(pure = true)
    public IsolatedApplicationBuilder(
        @NotNull final String applicationName,
        @NotNull final String applicationClass,
        @Nullable final Object @NotNull ... arguments
    ) {
        super(applicationName, builder -> new IsolatedInjectableClassLoader(
                new URL[0],
                Collections.singleton(Application.class),
                builder.getParentClassloader()
        ));
        this.applicationClass = applicationClass;
        this.arguments = arguments.clone();
    }

    /**
     * Sets the parent class loader to be used by the modular application.
     *
     * @param classLoader The parent {@link ClassLoader} to be assigned. Must not be null.
     * @return The builder instance for method chaining.
     */
    @Contract(value = "_ -> this", mutates = "this")
    public @NotNull IsolatedApplicationBuilder parentClassLoader(@NotNull final ClassLoader classLoader) {
        this.parentClassloader = classLoader;
        return self;
    }

    @Contract(mutates = "this")
    private @NotNull ClassLoader getParentClassloader() {
        if (parentClassloader == null) {
            this.parentClassloader = ClassLoader.getSystemClassLoader().getParent();
        }

        return parentClassloader;
    }

    @Override
    protected Application buildApplication(@NotNull final Injectable injectable) throws SlimJarException {
        try {
            final var applicationClass = Class.forName(this.applicationClass, true, injectable.getClassLoader());
            return (Application) Reflections.findConstructor(applicationClass, arguments).newInstance(arguments);
        } catch (final ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException err) {
            throw new SlimJarException("Failed to reflectively create application class.", err);
        }
    }
}
