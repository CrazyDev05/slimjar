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


import io.github.slimjar.exceptions.InjectorException;
import io.github.slimjar.injector.loader.Injectable;
import io.github.slimjar.logging.ProcessLogger;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.library.ClassPathLibrary;
import io.papermc.paper.plugin.loader.library.LibraryLoadingException;
import io.papermc.paper.plugin.loader.library.LibraryStore;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A builder class for creating and configuring a paper-based application.
 *
 * <p>The {@code PaperApplicationBuilder} provides methods for customizing the behavior of the
 * application, such as enabling debug mode, configuring dependency remapping, and injecting
 * dependencies into the application. This builder is specifically designed to work in the context
 * of a Paper plugin environment, utilizing plugin-specific resources such as the logger, data folder.</p>
 *
 * <p>The builder ensures that dependencies are properly loaded and optionally remapped, enabling
 * advanced use cases like dependency isolation or custom dependency management for Paper plugins.</p>
 *
 * <p>This class is immutable and can only be configured through fluent methods. Once configuration
 * is complete, calling {@link #build()} will create and return the configured application
 * instance.</p>
 */
public class PaperApplicationBuilder extends InjectingApplicationBuilder<PaperApplicationBuilder> {
    private boolean remap = false;

    /**
     * Constructs an instance of {@code PaperApplicationBuilder} which extends the {@code InjectingApplicationBuilder}.
     * This constructor initializes the application context, configures the logger,
     * sets the download directory path, and adds the necessary library injection functionality.
     *
     * @param builder an instance of {@code PluginClasspathBuilder} used to configure the Paper application by
     *                providing classpath dependencies and initializing the injectable supplier.
     *                The builder provides the context with necessary configurations and access to the logger.
     */
    public PaperApplicationBuilder(@NotNull final PluginClasspathBuilder builder) {
        super(builder.getContext().getConfiguration().getName(), b -> {
            final PaperClassPath path = new PaperClassPath(b.remap);
            builder.addLibrary(path);
            return path::inject;
        });
        var context = builder.getContext();
        logger(context.getLogger());
        downloadDirectoryPath(context.getDataDirectory().resolve(".libs"));
    }

    @Contract(value = "_ -> this", mutates = "this")
    public PaperApplicationBuilder remap(final boolean remap) {
        this.remap = remap;
        return this;
    }

    @Contract(value = "_ -> this", mutates = "this")
    public @NotNull PaperApplicationBuilder logger(@NotNull final Logger logger) {
        return logger(logger, false);
    }

    @Contract(value = "_, _ -> this", mutates = "this")
    public @NotNull PaperApplicationBuilder logger(@NotNull final Logger logger, boolean debug) {
        return logger(new Slf4jProcessLogger(logger, debug));
    }

    private static final class PaperClassPath implements ClassPathLibrary, Injectable {
        private final boolean remap;
        private final Set<Path> libraries = ConcurrentHashMap.newKeySet();
        private volatile boolean registered = false;

        private PaperClassPath(boolean remap) {
            this.remap = remap;
        }

        public void inject(@NotNull URL url) throws InjectorException {
            if (registered) throw new InjectorException("Cannot inject into already registered library");
            try {
                libraries.add(Path.of(url.toURI()));
            } catch (final URISyntaxException e) {
                throw new InjectorException("Failed to add %s to classpath".formatted(url), e);
            }
        }

        @Override
        public void register(LibraryStore store) throws LibraryLoadingException {
            registered = true;
            final Collection<Path> paths;
            if (remap) paths = SpigotApplicationBuilder.findRemapper(true).remapper().apply(new ArrayList<>(libraries));
            else paths = libraries;

            paths.forEach(store::addLibrary);
        }

        @Override
        public boolean isThreadSafe() {
            return true;
        }
    }

    private record Slf4jProcessLogger(@NotNull Logger logger, boolean debug) implements ProcessLogger {
        @Override
        public void info(@NotNull String message, @Nullable Object... args) {
            if (!debug) return;
            logger.info(message, args);
        }

        @Override
        public void debug(@NotNull String message, @Nullable Object... args) {
            if (!debug) return;
            logger.info(message, args);
        }

        @Override
        public void error(@NotNull String message, @Nullable Object... args) {
            logger.error(message, args);
        }
    }
}
