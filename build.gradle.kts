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

import io.github.zenhelix.gradle.plugin.extension.PublishingType
import java.time.Duration
import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

plugins {
  signing
  alias(libs.plugins.testsummary)
  alias(libs.plugins.testrerun)
  alias(libs.plugins.maven.central.publish)
  `cel-conventions`
}

mapOf("versionJacoco" to libs.versions.jacoco.get(), "versionJandex" to libs.versions.jandex.get())
  .forEach { (k, v) -> extra[k] = v }

tasks.named<Wrapper>("wrapper") { distributionType = Wrapper.DistributionType.ALL }

// Pass environment variables:
//    ORG_GRADLE_PROJECT_sonatypeUsername
//    ORG_GRADLE_PROJECT_sonatypePassword
// Gradle targets:
//    publishAggregateMavenCentralDeployment
//    (zipAggregateMavenCentralDeployment to just generate the single, aggregated deployment zip)
// Ref: Maven Central Publisher API:
//    https://central.sonatype.org/publish/publish-portal-api/#uploading-a-deployment-bundle
mavenCentralPortal {
  credentials {
    username.value(provider { System.getenv("ORG_GRADLE_PROJECT_sonatypeUsername") })
    password.value(provider { System.getenv("ORG_GRADLE_PROJECT_sonatypePassword") })
  }

  deploymentName = "${project.name}-$version"

  // publishingType
  //   AUTOMATIC = fully automatic release
  //   USER_MANAGED = user has to manually publish/drop
  publishingType =
    if (System.getenv("CI") != null) PublishingType.AUTOMATIC else PublishingType.USER_MANAGED
  // baseUrl = "https://central.sonatype.com"
  uploader {
    // 2 seconds * 3600 = 7200 seconds = 2hrs
    statusCheckDelay = Duration.ofSeconds(2)
    maxStatusChecks = 3600
  }
}

// Configure the 'io.github.zenhelix.maven-central-publish' plugin to all projects
allprojects.forEach { p ->
  p.pluginManager.withPlugin("maven-publish") {
    p.pluginManager.apply("io.github.zenhelix.maven-central-publish")
  }
}

val buildToolIntegrationGradle by
  tasks.creating(Exec::class) {
    group = "Verification"
    description =
      "Checks whether bom works fine with Gradle, requires preceding publishToMavenLocal in a separate Gradle invocation"

    workingDir = file("build-tool-integ-tests")
    commandLine("./gradlew", "jar", "-Dcel.version=${project.version}")
  }

val buildToolIntegrationMaven by
  tasks.creating(Exec::class) {
    group = "Verification"
    description =
      "Checks whether bom works fine with Maven, requires preceding publishToMavenLocal in a separate Gradle invocation"

    workingDir = file("build-tool-integ-tests")
    commandLine("./mvnw", "clean", "package", "-Dcel.version=${project.version}")
  }

val buildToolIntegrations by
  tasks.creating {
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

subprojects.forEach {
  it.tasks.register("compileAll").configure {
    group = "build"
    description = "Runs all compilation and jar tasks"
    dependsOn(tasks.withType<AbstractCompile>(), tasks.withType<ProcessResources>())
  }
}
