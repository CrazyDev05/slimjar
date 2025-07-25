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

package io.github.slimjar // ktlint-disable filename

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.HasConfigurableValue
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.getByType
import java.io.DataOutputStream
import java.io.File

internal val TaskContainer.targetedJarTask: Task get() {
    return findByName("shadowJar")
        ?: findByName("jar")
        ?: error("No jar task found")
}

internal val Project.slimResources get() = layout.buildDirectory.dir("resources/slimjar").get().asFile.also(File::mkdirs)
internal val Project.slimExtension: SlimJarExtension get() = extensions.getByType()
internal val Project.normalPath: String get() = path.replace(Regex("[^a-zA-Z0-9\\s]"), "_")

internal fun <T : HasConfigurableValue> T.andFinalizeValueOnRead(): T = apply { finalizeValueOnRead() }
internal fun File.dataOutputStream() = DataOutputStream(outputStream())