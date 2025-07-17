package io.github.slimjar.resolver.reader.dependency;

import io.github.slimjar.exceptions.ResolutionException;
import io.github.slimjar.resolver.data.DependencyData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class EmptyDependencyDataProvider implements DependencyDataProvider {
    @Override
    public @NotNull DependencyData get() throws ResolutionException {
        return new DependencyData(List.of(), List.of(), List.of(), List.of());
    }
}
