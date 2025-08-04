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

import io.github.slimjar.app.AppendingApplication;
import io.github.slimjar.app.Application;
import io.github.slimjar.exceptions.InjectorException;
import io.github.slimjar.injector.loader.IsolatedInjectableClassLoader;
import io.github.slimjar.logging.ProcessLogger;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A builder class for creating and configuring a Spigot-based application.
 *
 * <p>The {@code SpigotApplicationBuilder} provides methods for customizing the behavior of the
 * application, such as enabling debug mode, configuring dependency remapping, and injecting
 * dependencies into the application. This builder is specifically designed to work in the context
 * of a Spigot plugin environment, utilizing plugin-specific resources such as the logger, class
 * loader, and data folder.</p>
 *
 * <p>The builder ensures that dependencies are properly loaded and optionally remapped, enabling
 * advanced use cases like dependency isolation or custom dependency management for Spigot plugins.</p>
 *
 * <p>This class is immutable and can only be configured through fluent methods. Once configuration
 * is complete, calling {@link #build()} will create and return the configured application
 * instance.</p>
 */
public final class SpigotApplicationBuilder extends ApplicationBuilder<SpigotApplicationBuilder> {
    private final ClassLoader classLoader;
    private boolean debug = false;
    private boolean remap = false;

    /**
     * Constructs a new instance of {@code SpigotApplicationBuilder} for building a Spigot-based application.
     * This constructor initializes the application builder with plugin-specific configurations such as
     * the name, class loader, logger, and download directory for dependency management.
     *
     * @param plugin an instance of {@link Plugin} representing the Spigot plugin. This plugin
     *               provides the necessary context, such as its name, logger, class loader, and
     *               data folder, for configuring the application builder.
     */
    public SpigotApplicationBuilder(@NotNull Plugin plugin) {
        super(plugin.getName());
        this.classLoader = plugin.getClass().getClassLoader();
        final Logger logger = plugin.getLogger();
        downloadDirectoryPath(new File(plugin.getDataFolder(), ".libs").toPath());
        logger(new ProcessLogger() {
            @Override
            public void info(@NotNull String message, @Nullable Object... args) {
                if (!debug) return;
                logger.info(message.formatted(args));
            }

            @Override
            public void error(@NotNull String message, @Nullable Object... args) {
                logger.severe(message.formatted(args));
            }

            @Override
            public void debug(@NotNull String message, @Nullable Object... args) {
                if (!debug) return;
                logger.info(message.formatted(args));
            }
        });
    }

    /**
     * Sets the debug mode for the application builder.
     * Ignored when a custom process logger is used
     *
     * @param debug a boolean indicating whether debug logging should be enabled (true) or disabled (false)
     * @return the current instance of {@code SpigotApplicationBuilder} for method chaining
     */
    @Contract(value = "_ -> this", mutates = "this")
    public SpigotApplicationBuilder debug(final boolean debug) {
        this.debug = debug;
        return this;
    }

    /**
     * Sets the remap mode for the application builder.
     *
     * @param remap a boolean indicating whether remapping should be enabled (true) or disabled (false)
     * @return the current instance of {@code SpigotApplicationBuilder} for method chaining
     */
    @Contract(value = "_ -> this", mutates = "this")
    public SpigotApplicationBuilder remap(final boolean remap) {
        this.remap = remap;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected @NotNull Application buildApplication() {
        try {
            final Field libraryLoaderField = classLoader.getClass().getDeclaredField("libraryLoader");
            libraryLoaderField.setAccessible(true);
            final ClassLoader libraryLoader = (ClassLoader) libraryLoaderField.get(classLoader);

            Function<List<Path>, List<Path>> remapper = null;
            BiFunction<URL[], ClassLoader, URLClassLoader> factory = null;

            if (remap) {
                var values = findRemapper(false);
                remapper = values.remapper();
                factory = values.factory();
            }

            if (remapper == null) remapper = Function.identity();
            if (factory == null) factory = (urls, parent) -> new IsolatedInjectableClassLoader(urls, Collections.emptyList(), parent);

            final var classpath = new ArrayList<URL>();
            final var dataProvider = getDataProviderFactory().create(getDependencyFileUrl());
            final var dependencyData = dataProvider.get();
            final var dependencyInjector = createInjector();

            final var preResolutionDataProvider = getPreResolutionDataProviderFactory().create(getPreResolutionFileUrl());
            final var preResolutionResultMap = preResolutionDataProvider.get();

            dependencyInjector.inject(classpath::add, dependencyData, preResolutionResultMap);


            final var paths = new ArrayList<Path>(classpath.size());
            for (final URL url : classpath) {
                try {
                    paths.add(Path.of(url.toURI()));
                } catch (final URISyntaxException err) {
                    throw new InjectorException("Failed to convert URL to path", err);
                }
            }
            final var remappedPaths = remapper.apply(paths);
            final var urls = new URL[remappedPaths.size()];
            for (int i = 0; i < remappedPaths.size(); i++) {
                try {
                    urls[i] = remappedPaths.get(i).toUri().toURL();
                } catch (final MalformedURLException err) {
                    throw new InjectorException("Failed to convert path to URL", err);
                }
            }

            libraryLoaderField.set(classLoader, factory.apply(urls, libraryLoader == null ? classLoader.getParent() : libraryLoader));
        } catch (final Exception err) {
            throw new InjectorException("Failed to build application", err);
        }
        return new AppendingApplication();
    }

    static Values findRemapper(boolean addDefaults) {
        Function<List<Path>, List<Path>> remapper = null;
        BiFunction<URL[], ClassLoader, URLClassLoader> factory = null;
        try {
            final Class<?> libraryLoaderClass = Class.forName("org.bukkit.plugin.java.LibraryLoader");
            final Field remapperField = libraryLoaderClass.getDeclaredField("REMAPPER");
            final Field factoryField = libraryLoaderClass.getDeclaredField("LIBRARY_LOADER_FACTORY");
            remapperField.setAccessible(true);
            factoryField.setAccessible(true);
            remapper = (Function<List<Path>, List<Path>>) remapperField.get(null);
            factory = (BiFunction<URL[], ClassLoader, URLClassLoader>) factoryField.get(null);
        } catch (final Throwable ignored) {}

        if (addDefaults) {
            if (remapper == null) remapper = Function.identity();
            if (factory == null) factory = (urls, parent) -> new IsolatedInjectableClassLoader(urls, Collections.emptyList(), parent);
        }

        return new Values(remapper, factory);
    }

    record Values(Function<List<Path>, List<Path>> remapper, BiFunction<URL[], ClassLoader, URLClassLoader> factory) {
    }
}
