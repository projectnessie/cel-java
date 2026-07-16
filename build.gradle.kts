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

import org.gradle.api.tasks.TaskProvider
import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

plugins {
  signing
  id("cel-conventions")
}

mapOf("versionJacoco" to libs.versions.jacoco.get(), "versionJandex" to libs.versions.jandex.get())
  .forEach { (k, v) -> extra[k] = v }

tasks.named<Wrapper>("wrapper") { distributionType = Wrapper.DistributionType.ALL }

fun registerBuildToolIntegrationGradleTask(
  taskName: String,
  generatedProtobufArtifact: String,
): TaskProvider<Exec> =
  tasks.register<Exec>(taskName) {
    group = "Verification"
    description =
      "Checks whether bom works fine with Gradle, requires preceding publishToMavenLocal in a separate Gradle invocation"

    workingDir = file("build-tool-integ-tests")
    commandLine(
      "./gradlew",
      "jar",
      "-Dcel.version=${project.version}",
      "-Dcel.generated.pb.artifact=$generatedProtobufArtifact",
    )
  }

fun registerBuildToolIntegrationMavenTask(
  taskName: String,
  generatedProtobufArtifact: String,
): TaskProvider<Exec> =
  tasks.register<Exec>(taskName) {
    group = "Verification"
    description =
      "Checks whether bom works fine with Maven, requires preceding publishToMavenLocal in a separate Gradle invocation"

    workingDir = file("build-tool-integ-tests")
    commandLine(
      "./mvnw",
      "clean",
      "package",
      "-Dcel.version=${project.version}",
      "-Dcel.generated.pb.artifact=$generatedProtobufArtifact",
    )
  }

val buildToolIntegrationGradle =
  registerBuildToolIntegrationGradleTask("buildToolIntegrationGradle", "cel-generated-pb")
val buildToolIntegrationGradlePb3 =
  registerBuildToolIntegrationGradleTask("buildToolIntegrationGradlePb3", "cel-generated-pb3")
val buildToolIntegrationMaven =
  registerBuildToolIntegrationMavenTask("buildToolIntegrationMaven", "cel-generated-pb")
val buildToolIntegrationMavenPb3 =
  registerBuildToolIntegrationMavenTask("buildToolIntegrationMavenPb3", "cel-generated-pb3")

buildToolIntegrationGradlePb3.configure { mustRunAfter(buildToolIntegrationGradle) }

buildToolIntegrationMaven.configure { mustRunAfter(buildToolIntegrationGradlePb3) }

buildToolIntegrationMavenPb3.configure { mustRunAfter(buildToolIntegrationMaven) }

val buildToolIntegrations =
  tasks.register("buildToolIntegrations") {
    group = "Verification"
    description =
      "Checks whether bom works fine with build tools, requires preceding publishToMavenLocal in a separate Gradle invocation"

    dependsOn(buildToolIntegrationGradle)
    dependsOn(buildToolIntegrationGradlePb3)
    dependsOn(buildToolIntegrationMaven)
    dependsOn(buildToolIntegrationMavenPb3)
  }

publishingHelper {
  nessieRepoName.set("cel-java")
  inceptionYear.set("2021")
}

idea.project.settings {
  taskTriggers {
    afterSync(
      ":cel-generated-pb:jar",
      ":cel-generated-pb:testJar",
      ":cel-generated-antlr:shadowJar",
    )
  }
}

tasks.named<Wrapper>("wrapper") {
  actions.addLast {
    val script = scriptFile.readText()
    val scriptLines = script.lines().toMutableList()

    val insertAtLine =
      scriptLines.indexOf("# Use the maximum available, or set MAX_FD != -1 to use that value.")
    scriptLines.add(insertAtLine, "")
    scriptLines.add(insertAtLine, $$"[ -f \"${APP_HOME}/.env\" ] && . \"${APP_HOME}/.env\"")
    scriptLines.add(insertAtLine, $$". \"${APP_HOME}/gradle/gradlew-include.sh\"")

    scriptFile.writeText(scriptLines.joinToString("\n"))
  }
}
