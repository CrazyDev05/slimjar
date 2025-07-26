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

import io.github.slimjar.downloader.verify.ChecksumCalculator;
import io.github.slimjar.downloader.verify.FileChecksumCalculator;
import io.github.slimjar.exceptions.ModuleExtractorException;
import io.github.slimjar.relocation.Relocator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RelocatingModuleExtractor implements ModuleExtractor {
    @NotNull private final Path dataDirectory;
    @NotNull private final Relocator relocator;
    @NotNull private final ChecksumCalculator calculator;

    public RelocatingModuleExtractor(
            @NotNull final Path dataDirectory,
            @NotNull final Relocator relocator
    ) {
        this.dataDirectory = dataDirectory;
        this.relocator = relocator;
        this.calculator = new FileChecksumCalculator("sha256");
    }

    @Override
    public @NotNull URL extractModule(
            @NotNull final URL url,
            @NotNull final String name
    ) throws ModuleExtractorException {
        final var target = dataDirectory.resolve(name + ".jar").toFile();
        final var checksumFile = dataDirectory.resolve(name + ".checksum");
        final var extracted = ModuleExtractor.extractFile(url, name);
        final var calculatedChecksum = calculator.calculate(extracted);
        try {
            final var expectedChecksum = Files.exists(checksumFile) ?
                    new String(Files.readAllBytes(checksumFile)).trim() :
                    null;

            if (!calculatedChecksum.equals(expectedChecksum)) {
                target.getParentFile().mkdirs();
                relocator.relocate(extracted, target);
                Files.write(checksumFile, calculatedChecksum.getBytes());
            }

            return target.toURI().toURL();
        } catch (final IOException e) {
            throw new ModuleExtractorException("Encountered IOException.", e);
        } finally {
            extracted.delete();
        }
    }
}
