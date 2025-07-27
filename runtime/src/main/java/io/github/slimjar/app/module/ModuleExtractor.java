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

package io.github.slimjar.app.module;

import io.github.slimjar.exceptions.ModuleExtractorException;
import io.github.slimjar.exceptions.ModuleNotFoundException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static io.github.slimjar.util.Connections.createTempFile;
import static io.github.slimjar.util.Connections.openJarConnection;

@FunctionalInterface
public interface ModuleExtractor {
    @NotNull URL extractModule(
        @NotNull final URL url,
        @NotNull final String name
    ) throws ModuleExtractorException;

    static @NotNull File extractFile(
            @NotNull final URL url,
            @NotNull final String name
    ) throws ModuleExtractorException {
        final var tempFile = createTempFile(name);
        final var connection = openJarConnection(url);

        try (final var jarFile = connection.getJarFile()) {
            final var module = jarFile.getJarEntry("modules/%s.isolated-jar".formatted(name));
            if (module == null) throw new ModuleNotFoundException(name);

            try (final var stream = jarFile.getInputStream(module)) {
                Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            return tempFile;
        } catch (final IOException e) {
            throw new ModuleExtractorException("Encountered IOException.", e);
        }
    }
}
