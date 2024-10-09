//
// MIT License
//
// Copyright (c) 2021 Vaishnav Anil
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//

package io.github.slimjar.injector;

import io.github.slimjar.injector.helper.InjectionHelper;
import io.github.slimjar.injector.helper.InjectionHelperFactory;
import io.github.slimjar.injector.loader.Injectable;
import io.github.slimjar.logging.LogDispatcher;
import io.github.slimjar.logging.ProcessLogger;
import io.github.slimjar.resolver.ResolutionResult;
import io.github.slimjar.resolver.data.Dependency;
import io.github.slimjar.resolver.data.DependencyData;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

public final class SimpleDependencyInjector implements DependencyInjector {
    private static final ProcessLogger LOGGER = LogDispatcher.getMediatingLogger();
    private final InjectionHelperFactory injectionHelperFactory;
    private final Set<Dependency> processingDependencies = Collections.synchronizedSet(new HashSet<>());

    public SimpleDependencyInjector(final InjectionHelperFactory injectionHelperFactory) {
        this.injectionHelperFactory = injectionHelperFactory;
    }

    @Override
    public void inject(final Injectable injectable, final DependencyData data, final Map<String, ResolutionResult> preResolvedResults) throws ReflectiveOperationException, NoSuchAlgorithmException, IOException, URISyntaxException {
        final InjectionHelper helper = injectionHelperFactory.create(data, preResolvedResults);
        ForkJoinPool pool = new ForkJoinPool();
        injectDependencies(pool, injectable, helper, data.dependencies());
        pool.shutdown();
    }

    // TODO -> check the checksums after instead of during the download
    private void injectDependencies(final ForkJoinPool pool, final Injectable injectable, final InjectionHelper injectionHelper, final Collection<Dependency> dependencies) throws RuntimeException {
        if (dependencies.isEmpty()) return;
        var futures = dependencies.stream()
                .map(dependency -> CompletableFuture.runAsync(() -> {
                    if (injectionHelper.isInjected(dependency) || !processingDependencies.add(dependency))
                        return;

                    try {
                        final var depJar = injectionHelper.fetch(dependency);

                        if (depJar == null) return;

                        injectable.inject(depJar.toURI().toURL());
                        LOGGER.log("Loaded library %s", depJar);
                        injectDependencies(pool, injectable, injectionHelper, dependency.transitive());
                    } catch (final IOException e) {
                        throw new InjectionFailedException(dependency, e);
                    } catch (IllegalAccessException | InvocationTargetException | URISyntaxException e) {
                        e.printStackTrace();
                    } catch (ReflectiveOperationException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    processingDependencies.remove(dependency);
                }, pool))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
    }


}
