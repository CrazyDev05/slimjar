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

package io.github.slimjar.util;

import io.github.slimjar.exceptions.ModuleExtractorException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.*;

public final class Connections {
    private Connections() { }

    @NotNull private static final String SLIMJAR_USER_AGENT = "SlimjarApplication/* URLDependencyDownloader";

    public static @NotNull URL newURL(@NotNull final String url) throws MalformedURLException {
        return URI.create(url).toURL();
    }

    public static @NotNull URLConnection createDownloadConnection(@NotNull final URL url) throws IOException {
        final var connection = url.openConnection();

        if (!(connection instanceof HttpURLConnection httpConnection)) {
            return connection;
        }

        connection.addRequestProperty("User-Agent", SLIMJAR_USER_AGENT);
        final int responseCode = httpConnection.getResponseCode();

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Could not download from" + url + " (" + httpConnection.getResponseMessage() + ")");
        }

        return connection;
    }

    public static void tryDisconnect(@NotNull final URLConnection urlConnection) {
        if (!(urlConnection instanceof HttpURLConnection httpURLConnection)) return;
        httpURLConnection.disconnect();
    }

    @Contract(value = "_ -> new", pure = true)
    public static @NotNull JarURLConnection openJarConnection(@NotNull final URL url) throws ModuleExtractorException {
        try {
            final URLConnection connection = url.openConnection();
            return (JarURLConnection) connection;
        } catch (final IOException err) {
            throw new ModuleExtractorException("Failed to open a connection to url (%s).".formatted(url), err);
        } catch (final ClassCastException err) {
            throw new ModuleExtractorException("Provided Non-Jar URL (%s).".formatted(url), err);
        }
    }

    @Contract(value = "_ -> new", pure = true)
    public static @NotNull File createTempFile(@NotNull final String name) throws ModuleExtractorException {
        try {
            final var tempFile = File.createTempFile(name, ".jar");
            tempFile.deleteOnExit();
            return tempFile;
        } catch (final IOException | SecurityException err) {
            throw new ModuleExtractorException("Failed to create temporary file", err);
        } catch (final IllegalArgumentException err) {
            throw new ModuleExtractorException("Module name must be at least 3 characters long", err);
        }
    }
}
