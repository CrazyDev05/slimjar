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

import io.github.slimjar.exceptions.NotComparableDependencyException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import static io.github.slimjar.util.Serialization.readList;
import static io.github.slimjar.util.Serialization.writeList;

public record Dependency(
    @NotNull String groupId,
    @NotNull String artifactId,
    @NotNull String version,
    @Nullable String snapshotId,
    @NotNull Collection<Dependency> transitive
) implements Comparable<Dependency> {

    @NotNull
    @Contract(pure = true)
    public static Dependency read(@NotNull final DataInput in) throws IOException {
        return new Dependency(
                in.readUTF(),
                in.readUTF(),
                in.readUTF(),
                in.readBoolean() ? in.readUTF() : null,
                readList(in, Dependency::read)
        );
    }

    public boolean isSnapshot() {
        return snapshotId != null && !snapshotId.isEmpty();
    }

    public Optional<String> snapshot() {
        return isSnapshot() ? Optional.ofNullable(snapshotId) : Optional.empty();
    }

    public void write(@NotNull final DataOutput out) throws IOException {
        out.writeUTF(groupId);
        out.writeUTF(artifactId);
        out.writeUTF(version);
        if (snapshotId == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeUTF(snapshotId);
        }
        writeList(transitive, out, Dependency::write);
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        final String suffix = isSnapshot() ? (":" + snapshotId()) : "";
        return groupId() + ":" + artifactId() + ":" + version() + suffix;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (!(o instanceof Dependency otherDependency)) return false;

        // Don't check snapshotId for equality. (Maybe it should be checked?)
        return groupId.equals(otherDependency.groupId)
            && artifactId.equals(otherDependency.artifactId)
            && version.equals(otherDependency.version);
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    @Override
    @Contract(pure = true) // TODO: Tests
    public int compareTo(@NotNull final Dependency o) throws NotComparableDependencyException {
        if (!this.equals(o)) {
            throw new NotComparableDependencyException(this, o);
        }

        return this.version().compareTo(o.version());
    }
}
