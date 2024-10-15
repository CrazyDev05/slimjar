package io.github.slimjar

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import io.github.slimjar.func.slimInjectToIsolated
import io.github.slimjar.relocation.RelocationConfig
import io.github.slimjar.relocation.RelocationRule
import io.github.slimjar.resolver.data.Mirror
import io.github.slimjar.task.SlimJar
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.* // ktlint-disable no-wildcard-imports

public abstract class SlimJarExtension(project: Project) {

    @Transient internal val relocations = mutableSetOf<RelocationRule>()

    @Transient internal val mirrors = mutableSetOf<Mirror>()

    @Transient internal val isolatedProjects = mutableSetOf<Project>()

    /**
     * Sets a global repositories that will be used to resolve dependencies,
     * If not set each dependency will attempt to resolve from one of the projects repositories.
     *
     * When set the global repositories will be the only used repositories.
     */
    public val globalRepositories: SetProperty<String> = project.objects.setProperty<String>()
        .apply(SetProperty<*>::finalizeValueOnRead)

    /**
     * Contracts that when building the slimjar, all dependencies must be resolved and there is no ambiguity.
     * If any dependency is not found in the global repository, the build will fail.
     *
     * Defaults to false.
     */
    public val requirePreResolve: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(false).apply(Property<*>::finalizeValueOnRead)

    /**
     * Contracts that when building the slimjar, all pre-resolved dependencies must have a valid checksum.
     *
     * Defaults to false.
     */
    public val requireChecksum: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(false).apply(Property<*>::finalizeValueOnRead)

    /**
     * @receiver the original path
     * @param target the prefixed path to relocate to.
     */
    @JvmName("relocateInfix")
    public infix fun String.relocate(target: String) {
        addRelocation(this, target)
    }

    public fun relocate(original: String, target: String) {
        addRelocation(original, target)
    }

    private fun addRelocation(
        original: String,
        relocated: String,
        configure: Action<RelocationConfig>? = null
    ): SlimJarExtension {
        val relocationConfig = RelocationConfig()
        configure?.execute(relocationConfig)
        val rule = RelocationRule(original, relocated, relocationConfig.exclusions, relocationConfig.inclusions)
        relocations.add(rule)
        return this
    }

    public open fun isolate(target: Project) {
        isolatedProjects.add(target)

        if (target.slimInjectToIsolated) {
            target.pluginManager.apply(ShadowPlugin::class.java)
            target.pluginManager.apply(SlimJarPlugin::class.java)
            target.getTasksByName("slimJar", true).firstOrNull()?.setProperty("shade", false)
        }

        target.tasks {
            val jarTask = findByName("reobfJar")
                ?: findByName("shadowJar")
                ?: findByName("jar") ?: return@tasks

            withType<SlimJar> {
                dependsOn(jarTask)
            }
        }
    }
}
