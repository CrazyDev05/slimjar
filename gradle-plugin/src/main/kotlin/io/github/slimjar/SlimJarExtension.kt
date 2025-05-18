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
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.gradle.kotlin.dsl.withType
import javax.inject.Inject

open class SlimJarExtension @Inject constructor(private val project: Project) {
    @get:Input
    @get:Optional
    val isolatedProjects: SetProperty<Project> = project.objects.setProperty<Project>()
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
     * Sets a global repositories that will be used to resolve dependencies,
     * If not set each dependency will attempt to resolve from one of the projects repositories.
     *
     * When set the global repositories will be the only used repositories.
     */
    @get:Input
    @get:Optional
    val globalRepositories: SetProperty<String> = project.objects.setProperty<String>()
        .andFinalizeValueOnRead()

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

    /**
     * @receiver the original path
     * @param target the prefixed path to relocate to.
     */
    @JvmName("relocateInfix")
    infix fun String.relocate(target: String) {
        addRelocation(this, target)
    }

    fun relocate(original: String, target: String) {
        addRelocation(original, target)
    }

    fun isolate(target: Project) {
        isolatedProjects.add(target)

        if (target.slimInjectToIsolated) {
            target.pluginManager.apply(ShadowPlugin::class.java)
            target.pluginManager.apply(SlimJarPlugin::class.java)
        }

        project.tasks.withType<SlimJarTask> {
            dependsOn(target.tasks.targetedJarTask)
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
