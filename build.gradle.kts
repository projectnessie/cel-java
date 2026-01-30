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

import java.time.Duration
import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

plugins {
  signing
  alias(libs.plugins.testsummary)
  alias(libs.plugins.testrerun)
  alias(libs.plugins.nmcp)
  `cel-conventions`
}

mapOf("versionJacoco" to libs.versions.jacoco.get(), "versionJandex" to libs.versions.jandex.get())
  .forEach { (k, v) -> extra[k] = v }

tasks.named<Wrapper>("wrapper") { distributionType = Wrapper.DistributionType.ALL }

// Pass environment variables:
//    ORG_GRADLE_PROJECT_sonatypeUsername
//    ORG_GRADLE_PROJECT_sonatypePassword
// Gradle targets:
//    publishAggregationToCentralPortal
//    publishAggregationToCentralPortalSnapshots
//    (zipAggregateMavenCentralDeployment to just generate the single, aggregated deployment zip)
// Ref: Maven Central Publisher API:
//    https://central.sonatype.org/publish/publish-portal-api/#uploading-a-deployment-bundle
nmcpAggregation {
  centralPortal {
    username.value(provider { System.getenv("ORG_GRADLE_PROJECT_sonatypeUsername") })
    password.value(provider { System.getenv("ORG_GRADLE_PROJECT_sonatypePassword") })
    publishingType = if (System.getenv("CI") != null) "AUTOMATIC" else "USER_MANAGED"
    publishingTimeout = Duration.ofMinutes(120)
    validationTimeout = Duration.ofMinutes(120)
    publicationName = "${project.name}-$version"
  }
  publishAllProjectsProbablyBreakingProjectIsolation()
}

val buildToolIntegrationGradle by
  tasks.registering(Exec::class) {
    group = "Verification"
    description =
      "Checks whether bom works fine with Gradle, requires preceding publishToMavenLocal in a separate Gradle invocation"

    workingDir = file("build-tool-integ-tests")
    commandLine("./gradlew", "jar", "-Dcel.version=${project.version}")
  }

val buildToolIntegrationMaven by
  tasks.registering(Exec::class) {
    group = "Verification"
    description =
      "Checks whether bom works fine with Maven, requires preceding publishToMavenLocal in a separate Gradle invocation"

    workingDir = file("build-tool-integ-tests")
    commandLine("./mvnw", "clean", "package", "-Dcel.version=${project.version}")
  }

val buildToolIntegrations by
  tasks.registering {
    group = "Verification"
    description =
      "Checks whether bom works fine with build tools, requires preceding publishToMavenLocal in a separate Gradle invocation"

    dependsOn(buildToolIntegrationGradle)
    dependsOn(buildToolIntegrationMaven)
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
    scriptLines.add(insertAtLine, $$". \"${APP_HOME}/gradle/gradlew-include.sh\"")

    scriptFile.writeText(scriptLines.joinToString("\n"))
  }
}
