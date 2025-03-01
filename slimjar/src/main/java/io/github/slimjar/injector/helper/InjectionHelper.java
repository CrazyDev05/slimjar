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

package io.github.slimjar.injector.helper;


import io.github.slimjar.downloader.DependencyDownloader;
import io.github.slimjar.relocation.helper.RelocationHelper;
import io.github.slimjar.resolver.data.Dependency;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InjectionHelper {
    private final Set<Dependency> injectedDependencies;
    private final DependencyDownloader dependencyDownloader;
    private final RelocationHelper relocationHelper;

    public InjectionHelper(
        final DependencyDownloader dependencyDownloader,
        final RelocationHelper relocationHelper,
        final Set<Dependency> injectedDependencies
    ) {
        this.dependencyDownloader = dependencyDownloader;
        this.relocationHelper = relocationHelper;
        this.injectedDependencies = injectedDependencies;
    }

    public InjectionHelper(final DependencyDownloader dependencyDownloader, final RelocationHelper relocationHelper) {
        this(dependencyDownloader, relocationHelper, ConcurrentHashMap.newKeySet());
    }

    public File fetch(final Dependency dependency) throws IOException, ReflectiveOperationException, InterruptedException {
        final File downloaded = dependencyDownloader.download(dependency);
        if (downloaded == null) {
            return null;
        }

        injectedDependencies.add(dependency);
        return relocationHelper.relocate(dependency, downloaded);
    }

    public boolean isInjected(final Dependency dependency) {
        return injectedDependencies.contains(dependency);
    }
}
