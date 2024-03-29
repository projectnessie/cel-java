/*
 * Copyright (C) 2022 The Authors of CEL-Java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.protobuf.gradle.ProtobufExtract
import com.google.protobuf.gradle.ProtobufPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.withType

/** Makes the generated sources available to IDEs, disables Checkstyle on generated code. */
@Suppress("unused")
class ProtobufHelperPlugin : Plugin<Project> {
  override fun apply(project: Project): Unit =
    project.run {
      apply<ProtobufPlugin>()

      tasks.withType(ProtobufExtract::class.java).configureEach {
        dependsOn(tasks.named("processJandexIndex"))
      }
    }
}
