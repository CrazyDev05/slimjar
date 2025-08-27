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

import com.velocitypowered.api.plugin.PluginManager;
import io.github.slimjar.exceptions.InjectorException;
import io.github.slimjar.logging.ProcessLogger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Builder class for creating and configuring an application specifically for the context of a Velocity plugin.
 * This class extends {@link InjectingApplicationBuilder} and provides additional functionality tailored
 * to the Velocity platform, including handling plugin-specific classpath and logging configuration.
 * <p>
 * Use this builder to configure various aspects of the application such as logging and dependencies
 * while integrating it with the Velocity plugin system.
 */
public class VelocityApplicationBuilder extends InjectingApplicationBuilder<VelocityApplicationBuilder> {

    /**
     * Constructs a new VelocityApplicationBuilder instance.
     * This builder allows configuring and customizing an application in the context of a Velocity plugin
     * by setting up plugin-specific integrations, such as classpath management.
     *
     * @param pluginManager the {@link PluginManager} instance to manage plugins in the Velocity environment.
     *                      This is used to interact with the plugin system and handle classpath modifications.
     *                      Must not be null.
     * @param plugin the plugin instance associated with this builder. Used to link the builder
     *               with the specified plugin and verify its validity.
     *               Must not be null and should represent a valid plugin instance.
     * @throws IllegalArgumentException if the provided plugin is not a valid plugin instance.
     * @throws InjectorException if the builder fails to add required components to the classpath.
     */
    public VelocityApplicationBuilder(
            @NotNull PluginManager pluginManager,
            @NotNull Object plugin
    ) {
        super(pluginManager.fromInstance(plugin).orElseThrow(() -> new IllegalArgumentException("plugin is not a valid plugin instance")).getDescription().getId(),
                builder -> url -> {
                    try {
                        pluginManager.addToClasspath(plugin, Path.of(url.toURI()));
                    } catch (final Exception err) {
                        throw new InjectorException("Failed to add %s to classpath".formatted(url), err);
                    }
                });
    }

    @Contract(value = "_ -> this", mutates = "this")
    public @NotNull VelocityApplicationBuilder logger(@NotNull final Logger logger) {
        return logger(logger, false);
    }

    @Contract(value = "_, _ -> this", mutates = "this")
    public @NotNull VelocityApplicationBuilder logger(@NotNull final Logger logger, boolean debug) {
        return logger(new Slf4jProcessLogger(logger, debug));
    }

    private record Slf4jProcessLogger(@NotNull Logger logger, boolean debug) implements ProcessLogger {
        @Override
        public void info(@NotNull String message, @Nullable Object... args) {
            if (!debug) return;
            logger.info(message.formatted(args));
        }

        @Override
        public void debug(@NotNull String message, @Nullable Object... args) {
            if (!debug) return;
            logger.info(message.formatted(args));
        }

        @Override
        public void error(@NotNull String message, @Nullable Object... args) {
            logger.error(message.formatted(args));
        }
    }
}
