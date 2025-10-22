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

import io.github.zenhelix.gradle.plugin.MavenCentralUploaderPlugin.Companion.MAVEN_CENTRAL_PORTAL_NAME
import io.github.zenhelix.gradle.plugin.extension.MavenCentralUploaderExtension
import io.github.zenhelix.gradle.plugin.extension.PublishingType
import io.github.zenhelix.gradle.plugin.task.PublishBundleMavenCentralTask
import io.github.zenhelix.gradle.plugin.task.ZipDeploymentTask
import java.time.Duration
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
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
    delayRetriesStatusCheck = Duration.ofSeconds(2)
    maxRetriesStatusCheck = 3600

    aggregate {
      // Aggregate submodules into a single archive
      modules = true
      // Aggregate publications into a single archive for each module
      modulePublications = true
    }
  }
}

val mavenCentralDeploymentZipAggregation by configurations.creating

mavenCentralDeploymentZipAggregation.isTransitive = true

val zipAggregateMavenCentralDeployment by
  tasks.registering(Zip::class) {
    group = PUBLISH_TASK_GROUP
    description = "Generates the aggregated Maven publication zip file."

    inputs.files(mavenCentralDeploymentZipAggregation)
    from(mavenCentralDeploymentZipAggregation.map { zipTree(it) })
    // archiveFileName = mavenCentralPortal.deploymentName.orElse(project.name)
    destinationDirectory.set(layout.buildDirectory.dir("aggregatedDistribution"))
    doLast { logger.lifecycle("Built aggregated distribution ${archiveFile.get()}") }
  }

val publishAggregateMavenCentralDeployment by
  tasks.registering(PublishBundleMavenCentralTask::class) {
    group = PUBLISH_TASK_GROUP
    description =
      "Publishes the aggregated Maven publications $MAVEN_CENTRAL_PORTAL_NAME repository."

    dependsOn(zipAggregateMavenCentralDeployment)
    inputs.file(zipAggregateMavenCentralDeployment.flatMap { it.archiveFile })

    val task = this

    project.extensions.configure<MavenCentralUploaderExtension> {
      val ext = this
      task.baseUrl.set(ext.baseUrl)
      task.credentials.set(
        ext.credentials.username.flatMap { username ->
          ext.credentials.password.map { password ->
            io.github.zenhelix.gradle.plugin.client.model.Credentials.UsernamePasswordCredentials(
              username,
              password,
            )
          }
        }
      )

      task.publishingType.set(
        ext.publishingType.map {
          when (it) {
            PublishingType.AUTOMATIC ->
              io.github.zenhelix.gradle.plugin.client.model.PublishingType.AUTOMATIC
            PublishingType.USER_MANAGED ->
              io.github.zenhelix.gradle.plugin.client.model.PublishingType.USER_MANAGED
          }
        }
      )
      task.deploymentName.set(ext.deploymentName)

      task.maxRetriesStatusCheck.set(ext.uploader.maxRetriesStatusCheck)
      task.delayRetriesStatusCheck.set(ext.uploader.delayRetriesStatusCheck)

      task.zipFile.set(zipAggregateMavenCentralDeployment.flatMap { it.archiveFile })
    }
  }

// Configure the 'io.github.zenhelix.maven-central-publish' plugin to all projects
allprojects.forEach { p ->
  p.pluginManager.withPlugin("maven-publish") {
    p.pluginManager.apply("io.github.zenhelix.maven-central-publish")
    p.extensions.configure<MavenCentralUploaderExtension> {
      val aggregatedMavenCentralDeploymentZipPart by p.configurations.creating
      aggregatedMavenCentralDeploymentZipPart.description = "Maven central publication zip"
      val aggregatedMavenCentralDeploymentZipPartElements by p.configurations.creating
      aggregatedMavenCentralDeploymentZipPartElements.description =
        "Elements for the Maven central publication zip"
      aggregatedMavenCentralDeploymentZipPartElements.isCanBeResolved = false
      aggregatedMavenCentralDeploymentZipPartElements.extendsFrom(
        aggregatedMavenCentralDeploymentZipPart
      )
      aggregatedMavenCentralDeploymentZipPartElements.attributes {
        attribute(
          Usage.USAGE_ATTRIBUTE,
          project.getObjects().named(Usage::class.java, "publication"),
        )
      }

      val aggregatemavenCentralDeployment by
        p.tasks.registering {
          val zip = p.tasks.findByName("zipDeploymentMavenPublication") as ZipDeploymentTask
          dependsOn(zip)
          outputs.file(zip.archiveFile.get().asFile)
        }

      val artifact =
        p.artifacts.add(
          aggregatedMavenCentralDeploymentZipPart.name,
          aggregatemavenCentralDeployment,
        ) {
          builtBy(aggregatemavenCentralDeployment)
        }
      aggregatedMavenCentralDeploymentZipPart.outgoing.artifact(artifact)

      rootProject.dependencies.add(
        mavenCentralDeploymentZipAggregation.name,
        rootProject.dependencies.project(p.path, aggregatedMavenCentralDeploymentZipPart.name),
      )
    }
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
