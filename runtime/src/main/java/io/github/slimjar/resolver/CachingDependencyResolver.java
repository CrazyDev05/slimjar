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

package io.github.slimjar.resolver;


import io.github.slimjar.logging.LogDispatcher;
import io.github.slimjar.logging.ProcessLogger;
import io.github.slimjar.resolver.data.Repository;
import io.github.slimjar.resolver.data.Dependency;
import io.github.slimjar.resolver.enquirer.RepositoryEnquirer;
import io.github.slimjar.resolver.enquirer.RepositoryEnquirerFactory;
import io.github.slimjar.resolver.pinger.URLPinger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;

public final class CachingDependencyResolver implements DependencyResolver {
    @NotNull private static final ProcessLogger LOGGER = LogDispatcher.getMediatingLogger();
    @NotNull private final URLPinger urlPinger;
    @NotNull private final List<@NotNull RepositoryEnquirer> repositories;
    @NotNull private final Map<Dependency, ResolutionResult> cachedResults = new ConcurrentHashMap<>();
    @NotNull private final Map<String, ResolutionResult> preResolvedResults;

    @Contract(pure = true)
    public CachingDependencyResolver(
        @NotNull final URLPinger urlPinger,
        @NotNull final Collection<@NotNull Repository> repositories,
        @NotNull final RepositoryEnquirerFactory enquirerFactory,
        @NotNull final Map<String, ResolutionResult> preResolvedResults
    ) {
        this.urlPinger = urlPinger;
        this.preResolvedResults = new ConcurrentHashMap<>(preResolvedResults);
        this.repositories = repositories.stream()
                .map(enquirerFactory::create)
                .distinct()
                .toList();
    }

    @Override
    @Contract(pure = true)
    public @NotNull Optional<ResolutionResult> resolve(@NotNull final Dependency dependency) {
        return resolve(dependency, Collections.emptyList());
    }

    @Contract(pure = true)
    public @NotNull Optional<ResolutionResult> resolve(
        @NotNull final Dependency dependency,
        @NotNull final List<RepositoryEnquirer> enforcedRepositories
    ) { return Optional.ofNullable(cachedResults.computeIfAbsent(dependency, dep -> attemptResolve(dep, enforcedRepositories))); }

    @Contract(pure = true)
    private @Nullable ResolutionResult attemptResolve(
        @NotNull final Dependency dependency,
        @NotNull final List<RepositoryEnquirer> enforcedRepositories
    ) {
        final var preResolvedResult = preResolvedResults.get(dependency.toString()) != null ? preResolvedResults.get(dependency.toString()) : cachedResults.get(dependency);

        if (preResolvedResult != null) {
            if (preResolvedResult.checked()) return preResolvedResult;
            if (preResolvedResult.aggregator()) return preResolvedResult;

            final var preResolvedUrl = preResolvedResult.repository().url().toString();
            final var isDependencyValid = (enforcedRepositories.isEmpty() || enforcedRepositories.stream().anyMatch(repo -> repo.toString().equals(preResolvedUrl))) && urlPinger.ping(preResolvedResult.dependencyURL());
            final var isChecksumValid = preResolvedResult.checksumURL() == null || urlPinger.ping(preResolvedResult.checksumURL());

            if (isDependencyValid && isChecksumValid) {
                preResolvedResult.setChecked();
                return preResolvedResult;
            }
        }

        final var usedRepositories = enforcedRepositories.isEmpty() ? repositories : enforcedRepositories;
        final var futures = new ArrayList<ForkJoinTask<ResolutionResult>>(usedRepositories.size());
        for (final var enquirer : usedRepositories) {
            futures.add(ForkJoinTask.adapt(() -> enquirer.enquire(dependency)).fork());
        }
        for (final var future : futures) {
            final ResolutionResult result = future.join();
            if (result == null) continue;
            LOGGER.debug("Resolved %s @ %s", dependency, result.dependencyURL());
            return result;
        }
        LOGGER.debug("Resolved %s @ [FAILED TO RESOLVE]", dependency);
        return null;
    }
}
