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
import io.github.slimjar.app.module.ModuleExtractor;
import io.github.slimjar.app.module.RelocatingModuleExtractor;
import io.github.slimjar.exceptions.SlimJarException;
import io.github.slimjar.injector.loader.Injectable;
import io.github.slimjar.resolver.data.Repository;
import io.github.slimjar.resolver.reader.dependency.DependencyDataProviderFactory;
import io.github.slimjar.resolver.reader.dependency.DependencyReader;
import io.github.slimjar.resolver.reader.dependency.ExternalDependencyDataProviderFactory;
import io.github.slimjar.resolver.reader.resolution.PreResolutionDataProvider;
import io.github.slimjar.resolver.reader.resolution.PreResolutionDataProviderFactory;
import io.github.slimjar.resolver.reader.resolution.PreResolutionDataReader;
import io.github.slimjar.resolver.reader.resolution.WrappingPreResolutionDataProviderFactory;
import io.github.slimjar.util.Modules;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public abstract class ModularApplicationBuilder<T extends ModularApplicationBuilder<T>> extends InjectingApplicationBuilder<T> {

    @NotNull private final Set<String> modules = ConcurrentHashMap.newKeySet();
    @Nullable private DependencyDataProviderFactory moduleDataProviderFactory;
    @Nullable private PreResolutionDataProviderFactory modulePreResolutionDataProviderFactory;
    @Nullable private ModuleExtractor moduleExtractor;

    @Contract(pure = true)
    public ModularApplicationBuilder(
            @NotNull String applicationName,
            @NotNull Function<T, Injectable> injectableSupplier
    ) {
        super(applicationName, injectableSupplier);
    }

    @Contract(value = "_ -> new", pure = true)
    public static @NotNull ModularApplicationBuilder<?> create(@NotNull final String applicationName) {
        final var classLoader = ApplicationBuilder.class.getClassLoader();
        return create(applicationName, classLoader);
    }

    @Contract(value = "_, _ -> new", pure = true)
    public static @NotNull ModularApplicationBuilder<?> create(
            @NotNull final String applicationName,
            @NotNull final ClassLoader classLoader
    ) {
        return new Impl(applicationName, builder -> builder.getInjectableFactory().create(
                builder.getDownloadDirectoryPath(),
                Collections.singleton(Repository.central()),
                classLoader
        ));
    }

    @Contract(value = "_, _ -> new", pure = true)
    public static @NotNull ModularApplicationBuilder<?> create(
            @NotNull final String applicationName,
            @NotNull final Injectable injectable
    ) {
        return new Impl(applicationName, builder -> injectable);
    }

    /**
     * Factory that produces DataProvider for modules in jar-in-jar classloading. Ignored if not using jar-in-jar/isolated(...)
     * Used to fetch the `slimjar.dat` file of each submodule.
     * @param moduleDataProviderFactory Factory that produces DataProvider for modules in jar-in-jar
     * @return <code>this</code>
     */
    @Contract(value = "_ -> this", mutates = "this")
    public final @NotNull T moduleDataProviderFactory(@NotNull final DependencyDataProviderFactory moduleDataProviderFactory) {
        this.moduleDataProviderFactory = moduleDataProviderFactory;
        return self;
    }

    /**
     * Factory that produces {@link PreResolutionDataProvider} for modules in jar-in-jar classloading. Ignored if not using jar-in-jar/isolated(...)
     * Used to fetch the `slimjar.dat` file of each submodule.
     * @param modulePreResolutionDataProviderFactory Factory that produces DataProvider for modules in jar-in-jar
     * @return <code>this</code>
     */
    @Contract(value = "_ -> this", mutates = "this")
    public final @NotNull T modulePreResolutionDataProviderFactory(@NotNull final  PreResolutionDataProviderFactory modulePreResolutionDataProviderFactory) {
        this.modulePreResolutionDataProviderFactory = modulePreResolutionDataProviderFactory;
        return self;
    }

    /**
     * Adds one or more modules to the modular application builder.
     * This method updates the internal list of modules that will be included in the application.
     *
     * @param modules The names of the modules to be added. Must not be null.
     * @return The builder instance for method chaining.
     */
    @Contract(value = "_ -> this", mutates = "this")
    public @NotNull T modules(@NotNull final String... modules) {
        this.modules.addAll(List.of(modules));
        return self;
    }

    /**
     * Sets the specified {@link ModuleExtractor} for the modular application builder.
     * The {@link ModuleExtractor} is used to handle the extraction of modules during the
     * application setup process.
     *
     * @param moduleExtractor The {@link ModuleExtractor} instance to be used by the builder.
     *                        Must not be null.
     * @return The builder instance for method chaining.
     */
    @Contract(value = "_ -> this", mutates = "this")
    public @NotNull T moduleExtractor(@NotNull final ModuleExtractor moduleExtractor) {
        this.moduleExtractor = moduleExtractor;
        return self;
    }

    @Contract(mutates = "this")
    protected final @NotNull Collection<String> getModules() {
        if (modules.isEmpty()) {
            this.modules.addAll(Modules.findLocalModules());
        }

        return modules;
    }

    @Contract(mutates = "this")
    protected final @NotNull DependencyDataProviderFactory getModuleDataProviderFactory() {
        if (moduleDataProviderFactory == null) {
            this.moduleDataProviderFactory = new ExternalDependencyDataProviderFactory(DependencyReader.DEFAULT);
        }

        return moduleDataProviderFactory;
    }

    @Contract(mutates = "this")
    protected final @NotNull PreResolutionDataProviderFactory getModulePreResolutionDataProviderFactory() {
        if (modulePreResolutionDataProviderFactory == null) {
            this.modulePreResolutionDataProviderFactory = new WrappingPreResolutionDataProviderFactory(PreResolutionDataReader.DEFAULT);
        }

        return modulePreResolutionDataProviderFactory;
    }

    @Override
    protected final @NotNull Application buildApplication() throws SlimJarException {
        final var injector = createInjector();

        final var dataProvider = getDataProviderFactory().create(getDependencyFileUrl());
        final var selfDependencyData = dataProvider.get();

        final var preResolutionDataProvider = getPreResolutionDataProviderFactory().create(getPreResolutionFileUrl());
        final var preResolutionResultMap = preResolutionDataProvider.get();

        final var moduleUrls = Modules.extract(moduleExtractor == null ?
                        new RelocatingModuleExtractor(
                                getDownloadDirectoryPath().resolve("modules"),
                                getRelocatorFactory().create(selfDependencyData.relocations())
                        ) : moduleExtractor,
                getModules()
        );

        final var injectable = injectableSupplier.apply(self);
        for (final var module : moduleUrls) {
            injectable.inject(module);
        }
        injector.inject(injectable, selfDependencyData, preResolutionResultMap);

        for (final var module : moduleUrls) {
            final var moduleDataProvider = getModuleDataProviderFactory().create(module);
            final var modulePreResolutionDataProvider = getModulePreResolutionDataProviderFactory().create(module);
            injector.inject(injectable, moduleDataProvider.get(), modulePreResolutionDataProvider.get());
        }

        return buildApplication(injectable);
    }

    protected Application buildApplication(@NotNull final Injectable injectable) throws SlimJarException {
        return new AppendingApplication();
    }

    private static final class Impl extends ModularApplicationBuilder<Impl> {
        private Impl(
                @NotNull final String applicationName,
                @NotNull final Function<Impl, Injectable> injectableSupplier
        ) {
            super(applicationName, injectableSupplier);
        }
    }
}
