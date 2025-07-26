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

package io.github.slimjar

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import io.github.slimjar.func.slimInjectToIsolated
import io.github.slimjar.relocation.RelocationConfig
import io.github.slimjar.relocation.RelocationRule
import io.github.slimjar.resolver.data.Mirror
import io.github.slimjar.task.SlimJarTask
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.gradle.kotlin.dsl.withType
import java.io.File
import javax.inject.Inject

open class SlimJarExtension @Inject constructor(private val project: Project) {
    @get:Input
    @get:Optional
    val isolatedProjects: MapProperty<Project, File> = project.objects.mapProperty<Project, File>()
        .andFinalizeValueOnRead()

    @get:Input
    @get:Optional
    val relocations: SetProperty<RelocationRule> = project.objects.setProperty<RelocationRule>()
        .andFinalizeValueOnRead()

    @get:Input
    @get:Optional
    val mirrors: SetProperty<Mirror> = project.objects.setProperty<Mirror>()
        .andFinalizeValueOnRead()

    /**
     * Sets global repositories that will be used to resolve dependencies,
     * If not set, each dependency will attempt to resolve from one of the projects' repositories.
     *
     * When set the global repositories will be the only used repositories.
     */
    @get:Input
    @get:Optional
    val globalRepositories: SetProperty<String> = project.objects.setProperty<String>()
        .andFinalizeValueOnRead()

    /**
     * Controls whether dependencies should attempt to be resolved during the configuration phase.
     *
     * If set to `true`, the plugin may try to pre-resolve dependencies to improve build performance
     * by avoiding redundant resolutions during the execution phase. If set to `false`, pre-resolution
     * will be skipped, and dependency resolution will occur as needed during execution.
     *
     * Defaults to true.
     */
    @get:Input
    @get:Optional
    val tryPreResolve: Property<Boolean> = project.objects.property<Boolean>()
        .convention(true).andFinalizeValueOnRead()

    /**
     * Contracts that when building the slimjar, all dependencies must be resolved and there is no ambiguity.
     * If any dependency is not found in the global repository, the build will fail.
     *
     * Defaults to false.
     */
    @get:Input
    @get:Optional
    val requirePreResolve: Property<Boolean> = project.objects.property<Boolean>()
        .convention(false).andFinalizeValueOnRead()

    /**
     * Contracts that when building the slimjar, all pre-resolved dependencies must have a valid checksum.
     *
     * Defaults to false.
     */
    @get:Input
    @get:Optional
    val requireChecksum: Property<Boolean> = project.objects.property<Boolean>()
        .convention(false).andFinalizeValueOnRead()

    @get:Input
    @get:Optional
    val dumpJson: Property<Boolean> = project.objects.property<Boolean>()
        .convention(false).andFinalizeValueOnRead()

    /**
     * @receiver the original path
     * @param target the prefixed path to relocate to.
     */
    @JvmName("relocateInfix")
    infix fun String.relocate(target: String) {
        addRelocation(this, target)
    }

    /**
     * Relocates the specified original path to the given target path.
     *
     * @param original the original path to relocate.
     * @param target the target path to relocate to.
     */
    fun relocate(original: String, target: String) {
        addRelocation(original, target)
    }

    /**
     * Relocates a specified original path to the given target path with additional configuration.
     *
     * @param original the original path to be relocated.
     * @param target the target path to relocate to.
     * @param configure an action used to customize the relocation configuration.
     */
    fun relocate(original: String, target: String, configure: Action<RelocationConfig>) {
        addRelocation(original, target, configure)
    }

    /**
     * Isolates the specified project by using the output of the shadowJar or jar task.
     *
     * @param target the project to be isolated.
     */
    fun isolate(target: Project) =
        isolate(target, target.tasks.targetedJarTask)

    /**
     * Isolates the specified project by using the output file of the targetTask.
     *
     * @param target the project to be isolated.
     * @param targetTask the task within the specified project to isolate.
     */
    fun isolate(target: Project, targetTask: Task) =
        isolate(target, targetTask, targetTask.outputs.files.singleFile)

    /**
     * Isolates the specified project by using the output of its shadowJar or jar task
     * and associates it with the provided target file.
     *
     * @param target the project to be isolated.
     * @param targetFile the file to associate with the isolated project.
     */
    fun isolate(target: Project, targetFile: File) =
        isolate(target, target.tasks.targetedJarTask, targetFile)

    /**
     * Isolates a specified project by associating it with a target task and a target file.
     * This method ensures that the target project and task are aligned, applies the necessary plugins if required,
     * and configures task dependencies and inputs.
     *
     * @param target the project to be isolated.
     * @param targetTask the task within the project to isolate.
     * @param targetFile the file to associate with the isolated project.
     */
    fun isolate(target: Project, targetTask: Task, targetFile: File) {
        assert(target == targetTask.project) { "Target project and task must be the same" }
        isolatedProjects.put(target, targetFile)

        if (target.slimInjectToIsolated) {
            target.pluginManager.apply(ShadowPlugin::class.java)
            target.pluginManager.apply(SlimJarPlugin::class.java)
        }

        project.tasks.withType<SlimJarTask> {
            dependsOn(targetTask)
            inputs.files(targetTask, targetFile)
        }
    }

    private fun addRelocation(
        original: String,
        relocated: String,
        configure: Action<RelocationConfig> = Action { }
    ): SlimJarExtension {
        val relocationConfig = RelocationConfig()
        configure.execute(relocationConfig)
        val rule = RelocationRule(original, relocated, relocationConfig.exclusions, relocationConfig.inclusions)
        relocations.add(rule)
        return this
    }
}
