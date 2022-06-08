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

import org.gradle.api.JavaVersion
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.CoreJavadocOptions
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import org.projectnessie.buildtools.publishing.PublishingHelperPlugin

plugins {
  id("org.projectnessie.buildsupport.jacoco")
  id("org.projectnessie.buildsupport.spotless")
  `maven-publish`
}

if (project.name != "conformance" && project.name != "jacoco") {
  apply<PublishingHelperPlugin>()
}

repositories {
  mavenCentral { content { excludeVersionByRegex("io[.]delta", ".*", ".*-nessie") } }
  if (System.getProperty("withMavenLocal").toBoolean()) {
    mavenLocal()
  }
}

if (project.projectDir.resolve("src/test/java").exists()) {
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

          dependsOn

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

tasks.withType<Jar>().configureEach {
  manifest {
    attributes["Implementation-Title"] = "CEL-Java"
    attributes["Implementation-Version"] = project.version
    attributes["Implementation-Vendor"] = "Authors of CEL-Java"
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
  options.release.set(8)
}

tasks.withType<Javadoc>().configureEach {
  val opt = options as CoreJavadocOptions
  // don't spam log w/ "warning: no @param/@return"
  opt.addStringOption("Xdoclint:-reference", "-quiet")
}

plugins.withType<JavaPlugin>().configureEach {
  configure<JavaPluginExtension> {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    modularity.inferModulePath.set(true)
  }
}

if (project != rootProject) {
  tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.WARN
    archiveBaseName.set("${rootProject.name}-${project.name}")
  }
}
