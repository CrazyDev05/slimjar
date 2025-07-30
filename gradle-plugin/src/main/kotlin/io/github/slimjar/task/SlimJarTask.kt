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

package io.github.slimjar.task

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.slimjar.*
import io.github.slimjar.SlimJarPlugin.Companion.SLIM_API_CONFIGURATION_NAME
import io.github.slimjar.SlimJarPlugin.Companion.SLIM_CONFIGURATION_NAME
import io.github.slimjar.func.performCompileTimeResolution
import io.github.slimjar.logging.LogDispatcher
import io.github.slimjar.logging.ProcessLogger
import io.github.slimjar.resolver.CachingDependencyResolver
import io.github.slimjar.resolver.ResolutionResult
import io.github.slimjar.resolver.data.Dependency
import io.github.slimjar.resolver.data.DependencyData
import io.github.slimjar.resolver.data.Repository
import io.github.slimjar.resolver.enquirer.PingingRepositoryEnquirerFactory
import io.github.slimjar.resolver.mirrors.SimpleMirrorSelector
import io.github.slimjar.resolver.pinger.HttpURLPinger
import io.github.slimjar.resolver.reader.resolution.PreResolutionDataReader
import io.github.slimjar.resolver.strategy.*
import io.github.slimjar.util.Serialization.writeList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult
import org.gradle.kotlin.dsl.setProperty
import java.io.File
import java.net.URI
import javax.inject.Inject

@CacheableTask
open class SlimJarTask @Inject constructor() : DefaultTask() {
    @OutputDirectory
    val outputDirectory: File = project.slimResources

    @get:Internal
    val slimJarExtension: SlimJarExtension = project.slimExtension

    @get:Internal
    val slimjarConfigurations: SetProperty<Configuration> = project.objects.setProperty<Configuration>()
        .convention(arrayOf(SLIM_CONFIGURATION_NAME, SLIM_API_CONFIGURATION_NAME).mapNotNull { project.configurations.findByName(it) })
        .andFinalizeValueOnRead()

    @get:Input
    @get:Optional
    val isolatedProjects = slimJarExtension.isolatedProjects

    @get:Input
    @get:Optional
    val relocations = slimJarExtension.relocations

    @get:Input
    @get:Optional
    val mirrors = slimJarExtension.mirrors

    @get:Input
    @get:Optional
    val globalRepositories = slimJarExtension.globalRepositories

    @get:Input
    @get:Optional
    val tryPreResolve = slimJarExtension.tryPreResolve

    @get:Input
    @get:Optional
    val requirePreResolve = slimJarExtension.requirePreResolve

    @get:Input
    @get:Optional
    val requireChecksum = slimJarExtension.requireChecksum

    @get:Input
    @get:Optional
    val dumpJson = slimJarExtension.dumpJson

    private val dumpDirectory = project.layout.buildDirectory.dir("dump").get().asFile

    init {
        group = "slimJar"
        inputs.files(slimjarConfigurations)
        outputs.dir(outputDirectory)
    }

    private companion object {
        val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    @TaskAction
    internal fun generateData() = with(project) {
        val ignored = setOfNotNull("", "slimjar.dat", "slimjar-resolutions.dat".takeIf { tryPreResolve.get() })
        outputDirectory.mkdirs()
        outputDirectory.walkBottomUp()
            .filter { !ignored.contains(it.toRelativeString(outputDirectory)) }
            .forEach { it.delete() }
        if (dumpJson.get()) {
            dumpDirectory.deleteRecursively()
            dumpDirectory.mkdirs()
        }

        createJson()
        if (tryPreResolve.get()) {
            generateResolvedDependenciesFile()
        }
        includeIsolatedJars()
    }

    /** Action to generate the JSON file inside the jar */
    private fun createJson() = with(project) {
        val dependencies = slimjarConfigurations.get().flatMap { it.incoming.getSlimDependencies() }
        val repositories = globalRepositories.get()
            .map { Repository(URI.create(it).toURL()) }
            .ifEmpty { repositories.getMavenRepos() }
        val dependencyData = DependencyData(
            mirrors.get(),
            repositories,
            dependencies,
            relocations.get()
        )
        if (dumpJson.get()) {
            dumpDirectory.resolve("slimjar.json")
                .writer()
                .use { writer -> GSON.toJson(dependencyData, writer) }
        }

        outputDirectory.resolve("slimjar.dat")
            .dataOutputStream()
            .use { out -> dependencyData.write(out) }
    }

    /** Finds jars to be isolated and adds them to the final jar. */
    private fun includeIsolatedJars() = with(project) {
        val indexes = mutableMapOf<String, Int>()
        isolatedProjects.get()
            .filter { it.key != this }
            .toList()
            .sortedBy { it.second.canonicalPath }
            .forEach {
                val path = it.first.normalPath
                it.second.copyTo(outputDirectory.resolve("modules/$path.${indexes.compute(path) { _, i -> (i ?: -1) + 1}}.isolated-jar"))
            }
    }

    private fun generateResolvedDependenciesFile() = with(project) {
        val file = outputDirectory.resolve("slimjar-resolutions.dat")
        if (!project.performCompileTimeResolution) {
            file.delete()
            return@with
        }

        val preResolved: Map<String, ResolutionResult> = if (file.exists()) {
            file.inputStream().use { PreResolutionDataReader.DEFAULT.read(it) }
        } else {
            mutableMapOf()
        }

        val dependencies = slimjarConfigurations.get()
            .flatMap { it.incoming.getSlimDependencies() }
            .toMutableSet().flatten()
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
            mirrorSelector.select(repositories, mirrors.get()),
            enquirerFactory,
            mapOf()
        )
        val processLogger = object : ProcessLogger {
            override fun info(message: String, vararg args: Any?) {
                logger.info(message.format(*args))
            }

            override fun debug(message: String, vararg args: Any?) {
                logger.debug(message.format(*args))
            }

            override fun error(message: String, vararg args: Any?) {
                logger.error(message.format(*args))
            }
        }
        val med = LogDispatcher.getMediatingLogger()
        med.addLogger(processLogger)

        val results = mutableMapOf<String, ResolutionResult>()
        // TODO: Cleanup this mess
        runBlocking(IO) {
            val globalRepositoryEnquirer = globalRepositories.map { repos ->
                repos.map { repoString -> enquirerFactory.create(Repository(URI.create(repoString).toURL())) }
            }

            dependencies.asFlow()
                .filter { dep ->
                    // TODO: Ensure existing results match global if present
                    preResolved[dep.toString()]?.let { pre ->
                        repositories.none { r -> pre.repository().url().toString() == r.url().toString() }
                    } ?: true
                }.concurrentMap(this, 16) { dep ->
                    dep to if (globalRepositoryEnquirer.isPresent) {
                        resolver.resolve(dep, globalRepositoryEnquirer.get())
                    } else {
                        resolver.resolve(dep)
                    }
                }.filter { (dep, result) ->
                    if (!result.isEmpty) return@filter true

                    logger.warn("Failed to resolve dependency $dep")
                    if (requirePreResolve.get()) {
                        error(
                            """
                            Failed to resolve dependency $dep during pre-resolve.
                            Please ensure that the dependency is available in the gradle repositories or global repositories.
                            Or disable required pre-resolve in the slimJar extension.
                            """.trimIndent()
                        )
                    }

                    false
                }.map { (dep, result) -> dep to result.get() }.onEach { (dep, result) ->
                    if (!requireChecksum.get() || result.checksumURL() != null) return@onEach
                    logger.warn("Failed to resolve checksum for dependency $dep")
                    error(
                        """
                            Failed to resolve checksum for dependency $dep during pre-resolve.
                            Please ensure that the dependency has a checksum.
                            Or disable required checksum in the slimJar extension.
                        """.trimIndent()
                    )
                }.onEach { (dep, result) -> results[dep.toString()] = result }.collect()
        }
        med.removeLogger(processLogger)

        preResolved.forEach { results.putIfAbsent(it.key, it.value) }

        if (dumpJson.get()) {
            dumpDirectory.resolve("slimjar-resolutions.json")
                .writer()
                .use { writer -> GSON.toJson(results, writer) }
        }
        file.dataOutputStream().use { writeList(results.entries, it) { (key, value), out ->
            out.writeUTF(key)
            value.write(out)
        }}
    }

    /**
     * Turns a [RenderableDependency] into a [Dependency] with all its
     * transitives.
     */
    private fun RenderableDependency.toSlimDependency(added: MutableSet<String>): Dependency? {
        return id.toString().takeIf(added::add)?.toDependency(children.mapNotNull { it.toSlimDependency(added) })
    }

    /**
     * Creates a [Dependency] based on a string
     * group:artifact:version:snapshot - The
     * snapshot is the only nullable value.
     */
    private fun String.toDependency(transitive: Collection<Dependency>): Dependency? {
        val split = split(":")
        if (split.size < 3) return null
        return Dependency(
            split[0],
            split[1],
            split[2],
            if (split.size > 3) split[3] else null,
            transitive
        )
    }

    private fun RepositoryHandler.getMavenRepos() = filterIsInstance<MavenArtifactRepository>()
        .filterNot { it.url.toString().startsWith("file") }
        .distinct()
        .map { Repository(it.url.toURL()) }

    private fun ResolvableDependencies.getSlimDependencies(): List<Dependency> = mutableSetOf<String>().run {
        RenderableModuleResult(resolutionResult.root).children
            .mapNotNull { it.toSlimDependency(this) }
            .filterNot { it.artifactId().endsWith("-bom") }
    }

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
