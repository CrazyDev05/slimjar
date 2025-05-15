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
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.github.slimjar.exceptions.ConfigurationNotFoundException
import io.github.slimjar.exceptions.ShadowNotFoundException
import io.github.slimjar.task.SlimJarTask
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.hasPlugin
import org.gradle.kotlin.dsl.withType

class SlimJarPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        plugins.apply(JavaPlugin::class.java)
        if (!plugins.hasPlugin(ShadowPlugin::class)) {
            throw ShadowNotFoundException(
                """
                    SlimJar depends on the Shadow plugin, please apply the plugin.
                    For more information visit: https://imperceptiblethoughts.com/shadow/
                """.trimMargin()
            )
        }

        extensions.create(SLIM_EXTENSION_NAME, SlimJarExtension::class.java, project)
        createConfig(
            SLIM_CONFIGURATION_NAME,
            JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME
        )

        if (plugins.hasPlugin("java-library")) {
            createConfig(
                SLIM_API_CONFIGURATION_NAME,
                JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME,
                JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME
            )
        }

        val slimJar = tasks.register(SLIM_JAR_TASK_NAME, SlimJarTask::class.java)
        // The fuck does this do?
        dependencies.extra.set(
            "slimjar",
            asGroovyClosure("+", ::slimJarLib)
        )

        // Hooks into shadow to inject relocations
        tasks.withType<ShadowJar> {
            doFirst { _ ->
                slimExtension.relocations.get().forEach { rule ->
                    relocate(rule.originalPackagePattern, rule.relocatedPackagePattern) {
                        rule.inclusions.forEach { include(it) }
                        rule.exclusions.forEach { exclude(it) }
                    }
                }
            }
        }

        // Runs the task once resources are being processed to save the json file
        tasks.findByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)?.finalizedBy(slimJar)
    }

    companion object {
        const val SLIM_CONFIGURATION_NAME: String = "slim"
        const val SLIM_API_CONFIGURATION_NAME: String = "slimApi"
        const val SLIM_JAR_TASK_NAME: String = "slimJar"
        const val SLIM_EXTENSION_NAME: String = "slimJar"

        internal fun Project.createConfig(
            configurationName: String,
            vararg extends: String
        ): NamedDomainObjectProvider<Configuration> = project.configurations.register(configurationName) { config ->
            config.isTransitive = true
            extends.map { configurations.findByName(it) ?: throw ConfigurationNotFoundException("Could not find `$extends` configuration!") }
                .forEach { it.extendsFrom(config) }
        }
    }
}

internal fun slimJarLib(version: String) = "de.crazydev22.slimjar:runtime:$version"
