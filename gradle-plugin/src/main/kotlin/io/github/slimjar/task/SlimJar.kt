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

package io.github.slimjar.task

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.github.slimjar.SLIM_API_CONFIGURATION_NAME
import io.github.slimjar.SlimJarExtension
import io.github.slimjar.func.performCompileTimeResolution
import io.github.slimjar.resolver.CachingDependencyResolver
import io.github.slimjar.resolver.ResolutionResult
import io.github.slimjar.resolver.data.Dependency
import io.github.slimjar.resolver.data.DependencyData
import io.github.slimjar.resolver.data.Repository
import io.github.slimjar.resolver.enquirer.PingingRepositoryEnquirerFactory
import io.github.slimjar.resolver.mirrors.SimpleMirrorSelector
import io.github.slimjar.resolver.pinger.HttpURLPinger
import io.github.slimjar.resolver.strategy.MavenChecksumPathResolutionStrategy
import io.github.slimjar.resolver.strategy.MavenPathResolutionStrategy
import io.github.slimjar.resolver.strategy.MavenPomPathResolutionStrategy
import io.github.slimjar.resolver.strategy.MavenSnapshotPathResolutionStrategy
import io.github.slimjar.resolver.strategy.MediatingPathResolutionStrategy
import io.github.slimjar.slimExtension
import io.github.slimjar.targetedJarTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult
import org.gradle.kotlin.dsl.* // ktlint-disable no-wildcard-imports
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import javax.inject.Inject

@CacheableTask
public abstract class SlimJar @Inject constructor(
    @Transient private val config: Configuration
) : DefaultTask() {

    private companion object {
        val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public val buildDirectory: File = project.buildDir

    @get:OutputDirectory
    public val shadowWriteFolder: File = buildDirectory.resolve("resources/main/")

    @get:OutputDirectory
    public val outputDirectory: File = buildDirectory.resolve("resources/slimjar/")

    init {
        group = "slimJar"
        inputs.files(config)

        dependsOn(project.tasks.targetedJarTask)
    }

    /**
     * Action to generate the json file inside the jar
     */
    @TaskAction
    internal fun createJson() = with(project) {
        val repositories = repositories.getMavenRepos()
        val dependencies = config.incoming.getSlimDependencies().toMutableSet()
        val extension = extensions.getByType<SlimJarExtension>()

        // If api config is present map dependencies from it as well.
        project.configurations.findByName(SLIM_API_CONFIGURATION_NAME)?.incoming?.getSlimDependencies()?.toCollection(dependencies)

        if (!outputDirectory.exists()) outputDirectory.mkdirs()

        val file = File(outputDirectory, "slimjar.json")

        FileWriter(file).use {
            gson.toJson(DependencyData(extension.mirrors, repositories, dependencies, extension.relocations), it)
        }

        // Copies to shadow's main folder
        if (!shadowWriteFolder.exists()) shadowWriteFolder.mkdirs()
        file.copyTo(File(shadowWriteFolder, file.name), true)
    }

    // Finds jars to be isolated and adds them to the final jar.
    @TaskAction
    internal fun includeIsolatedJars() = with(project) {
        val extension = extensions.getByType<SlimJarExtension>()
        extension.isolatedProjects.filter { it != this }.forEach {
            it.tasks.targetedJarTask.apply {
                val archive = outputs.files.singleFile
                if (!outputDirectory.exists()) outputDirectory.mkdirs()
                val output = File(outputDirectory, "${it.name}.isolated-jar")
                archive.copyTo(output, true)

                // Copies to shadow's main folder
                if (!shadowWriteFolder.exists()) shadowWriteFolder.mkdirs()
                output.copyTo(File(shadowWriteFolder, output.name), true)
            }
        }
    }

    @TaskAction
    internal fun generateResolvedDependenciesFile() = with(project) {
        if (!project.performCompileTimeResolution) return@with

        val file = outputDirectory.resolve("slimjar-resolutions.json")

        val preResolved: MutableMap<String, ResolutionResult> = if (file.exists()) {
            val mapType = object : TypeToken<MutableMap<String, ResolutionResult>>() {}.type
            gson.fromJson(FileReader(file), mapType)
        } else mutableMapOf()

        val dependencies = config.incoming.getSlimDependencies().toMutableSet().flatten()
        val repositories = repositories.getMavenRepos()

        val releaseStrategy = MavenPathResolutionStrategy()
        val snapshotStrategy = MavenSnapshotPathResolutionStrategy()
        val resolutionStrategy = MediatingPathResolutionStrategy(releaseStrategy, snapshotStrategy)
        val pomURLCreationStrategy = MavenPomPathResolutionStrategy()
        val checksumResolutionStrategy = MavenChecksumPathResolutionStrategy("SHA-1", resolutionStrategy)
        val urlPinger = HttpURLPinger()
        val enquirerFactory = PingingRepositoryEnquirerFactory(
            resolutionStrategy,
            checksumResolutionStrategy,
            pomURLCreationStrategy,
            urlPinger
        )
        val mirrorSelector = SimpleMirrorSelector()
        val resolver = CachingDependencyResolver(
            urlPinger,
            mirrorSelector.select(repositories, slimExtension.mirrors),
            enquirerFactory,
            mapOf()
        )

        val result = mutableMapOf<String, ResolutionResult>()
        runBlocking(IO) {
            dependencies.asFlow()
                .filter {
                    preResolved[it.toString()]?.let { pre ->
                        repositories.none { r -> pre.repository.url().toString() == r.url().toString() }
                    } ?: true
                }.concurrentMap(this, 16) {
                    it.toString() to resolver.resolve(it).orElse(null)
                }.onEach { result[it.first] = it.second }.collect()
        }

        preResolved.forEach { result.putIfAbsent(it.key, it.value) }

        if (!outputDirectory.exists()) outputDirectory.mkdirs()

        FileWriter(file).use {
            gson.toJson(result, it)
        }

        // Copies to shadow's main folder
        if (shadowWriteFolder.exists().not()) shadowWriteFolder.mkdirs()
        file.copyTo(File(shadowWriteFolder, file.name), true)
    }

    /**
     * Turns a [RenderableDependency] into a [Dependency] with all its transitives.
     */
    private fun RenderableDependency.toSlimDependency(): Dependency? {
        val transitive = mutableSetOf<Dependency>()
        collectTransitive(transitive, children)
        return id.toString().toDependency(transitive)
    }

    /**
     * Recursively flattens the transitive dependencies.
     */
    private fun collectTransitive(
        transitive: MutableSet<Dependency>,
        dependencies: Set<RenderableDependency>
    ) {
        for (dependency in dependencies) {
            val dep = dependency.id.toString().toDependency(emptySet()) ?: continue
            if (dep in transitive) continue

            transitive.add(dep)
            collectTransitive(transitive, dependency.children)
        }
    }

    /**
     * Creates a [Dependency] based on a string
     * group:artifact:version:snapshot - The snapshot is the only nullable value.
     */
    private fun String.toDependency(transitive: Set<Dependency>): Dependency? {
        val array = arrayOfNulls<Any>(5)
        array[4] = transitive

        split(":").takeIf { it.size >= 3 }?.forEachIndexed { index, s ->
            array[index] = s
        } ?: return null

        return Dependency::class.java.constructors.first().newInstance(*array) as Dependency
    }

    private fun RepositoryHandler.getMavenRepos() = this.filterIsInstance<MavenArtifactRepository>()
        .filterNot { it.url.toString().startsWith("file") }
        .toSet()
        .map { Repository(it.url.toURL()) }

    private fun ResolvableDependencies.getSlimDependencies(): List<Dependency> = RenderableModuleResult(this.resolutionResult.root)
        .children.mapNotNull { it.toSlimDependency() }

    private fun Collection<Dependency>.flatten(): MutableSet<Dependency> {
        return this.flatMap { it.transitive().flatten() + it }.toMutableSet()
    }

    private fun <T, R> Flow<T>.concurrentMap(
        scope: CoroutineScope,
        concurrencyLevel: Int,
        transform: suspend (T) -> R
    ): Flow<R> = this
        .map { scope.async { transform(it) } }
        .buffer(concurrencyLevel)
        .map { it.await() }
}
