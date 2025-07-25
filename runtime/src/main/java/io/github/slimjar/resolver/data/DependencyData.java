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

package io.github.slimjar.resolver.data;

import io.github.slimjar.relocation.RelocationRule;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import static io.github.slimjar.util.Serialization.readList;
import static io.github.slimjar.util.Serialization.writeList;

public record DependencyData(
    @NotNull Collection<Mirror> mirrors,
    @NotNull Collection<Repository> repositories,
    @NotNull Collection<Dependency> dependencies,
    @NotNull Collection<RelocationRule> relocations
) {

    @Contract(pure = true)
    public DependencyData(
        @NotNull final Collection<Mirror> mirrors,
        @NotNull final Collection<Repository> repositories,
        @NotNull final Collection<Dependency> dependencies,
        @NotNull final Collection<RelocationRule> relocations
    ) {
        this.mirrors = Collections.unmodifiableCollection(mirrors);
        this.repositories = Collections.unmodifiableCollection(repositories);
        this.dependencies = Collections.unmodifiableCollection(dependencies);
        this.relocations = Collections.unmodifiableCollection(relocations);
    }

    @NotNull
    @Contract(pure = true)
    public static DependencyData read(@NotNull final DataInput in) throws IOException {
        return new DependencyData(
                readList(in, Mirror::read),
                readList(in, Repository::read),
                readList(in, Dependency::read),
                readList(in, RelocationRule::read)
        );
    }

    public void write(@NotNull final DataOutput out) throws IOException {
        writeList(mirrors, out, Mirror::write);
        writeList(repositories, out, Repository::write);
        writeList(dependencies, out, Dependency::write);
        writeList(relocations, out, RelocationRule::write);
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (!(o instanceof DependencyData dependencyData)) return false;

        return isCollectionEqual(repositories, dependencyData.repositories)
            && isCollectionEqual(dependencies, dependencyData.dependencies)
            && isCollectionEqual(relocations, dependencyData.relocations);
    }

    @Contract(pure = true)
    private <T> boolean isCollectionEqual(
        @NotNull final Collection<T> collectionA,
        @NotNull Collection<T> collectionB
    ) {
        return collectionA.containsAll(collectionB) && collectionB.containsAll(collectionA);
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Objects.hash(repositories, dependencies, relocations);
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return "DependencyData{" +
            "mirrors=" + mirrors +
            ", repositories=" + repositories +
            ", dependencies=" + dependencies +
            ", relocations=" + relocations +
            '}';
    }

}
