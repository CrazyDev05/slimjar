package io.github.slimjar // ktlint-disable filename

import org.gradle.api.Project
import org.gradle.api.provider.HasConfigurableValue
import org.gradle.kotlin.dsl.getByType

internal val Project.slimExtension: SlimJarExtension get() = extensions.getByType()

internal fun <T : HasConfigurableValue> T.andFinalizeValueOnRead(): T = apply { finalizeValueOnRead() }
internal fun <T : HasConfigurableValue> T.andDisallowUnsafeRead(): T = apply { disallowChanges() }
