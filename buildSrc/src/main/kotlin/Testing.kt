/*
 * Copyright (C) 2023 The Authors of CEL-Java
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

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

fun Project.nessieConfigureTestTasks() {
  tasks.withType<Test>().configureEach {
    useJUnitPlatform {}
    maxParallelForks = Runtime.getRuntime().availableProcessors()
  }

  if (project.hasProperty("alsoTestAgainstJava8")) {
    val javaToolchains = extensions.findByType(JavaToolchainService::class.java)
    if (javaToolchains != null) {
      val testWithJava8 =
        tasks.register<Test>("testWithJava8") {
          group = "verification"
          description = "Run unit tests against Java 8"

          dependsOn("test")

          useJUnitPlatform {}
          maxParallelForks = Runtime.getRuntime().availableProcessors()
          javaLauncher.set(
            javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) }
          )
        }
      tasks.named("check") { dependsOn(testWithJava8) }
    }
  }
}
