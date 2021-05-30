/*
 * Copyright (C) 2021 The Authors of CEL-Java
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

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import org.jetbrains.gradle.ext.*

plugins {
  eclipse
  idea
  `maven-publish`
  id("org.jetbrains.gradle.plugin.idea-ext")
  id("com.diffplug.spotless")
}

allprojects {
  repositories { mavenCentral() }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform {}
    maxParallelForks = Runtime.getRuntime().availableProcessors()
  }

  tasks.withType<Jar>().configureEach {
    manifest {
      attributes["Implementation-Title"] = "CEL-Java"
      attributes["Implementation-Version"] = project.version
      attributes["Implementation-Vendor"] = "Authors of CEL-Java"
    }
  }

  tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

  plugins.withType<JavaPlugin>().configureEach {
    configure<JavaPluginExtension> {
      withJavadocJar()
      withSourcesJar()
      sourceCompatibility = JavaVersion.VERSION_1_8
      targetCompatibility = JavaVersion.VERSION_1_8
      modularity.inferModulePath.set(true)
    }
  }

  plugins.withType<MavenPublishPlugin>().configureEach {
    configure<PublishingExtension> {
      publications {
        register<MavenPublication>("maven") {
          groupId = "org.projectnessie.cel"
          artifactId = if (project == rootProject) "cel-parent" else "cel-${project.name}"
          version = project.version.toString()
          pom {
            if (project != rootProject) {
              withXml {
                val parentNode = asNode().appendNode("parent")
                parentNode.appendNode("groupId", rootProject.group)
                parentNode.appendNode("artifactId", "cel-parent")
                parentNode.appendNode("version", project.version)
              }
            }
            description.set(project.description)
            inceptionYear.set("2021")
            url.set("https://github.com/projectnessie/cel-java")
            developers {
              developer {
                id.set("snazy")
                name.set("Robert Stupp")
                url.set("https://github.com/snazy")
              }
              developer {
                id.set("nastra")
                name.set("Eduard Tudenhoefner")
                url.set("https://github.com/nastra")
              }
              developer {
                id.set("rymurr")
                name.set("Ryan Murray")
                url.set("https://github.com/rymurr")
              }
              developer {
                id.set("laurentgo")
                name.set("Laurent Goujon")
                url.set("https://github.com/laurentgo")
              }
            }
            organization {
              name.set("Project Nessie")
              url.set("https://projectnessie.org")
            }
            licenses {
              license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
              }
            }
            scm {
              connection.set("scm:git:https://github.com/projectnessie/cel-java")
              developerConnection.set("scm:git:https://github.com/projectnessie/cel-java</")
              url.set("https://github.com/projectnessie/cel-java/tree/main")
              tag.set("main")
            }
            issueManagement {
              system.set("Github")
              url.set("https://github.com/projectnessie/cel-java/issues")
            }
          }

          from(components.findByName("java"))
        }
      }
    }
  }

  if (this != rootProject) {
    plugins.withType<SpotlessPlugin>().configureEach {
      configure<SpotlessExtension> {
        java {
          googleJavaFormat()
          licenseHeaderFile(rootProject.file("gradle/license-header-java.txt"))
          targetExclude("**/build*/**")
        }
      }
    }

    tasks.withType<Jar>().configureEach {
      duplicatesStrategy = DuplicatesStrategy.WARN
      archiveBaseName.set("${rootProject.name}-${project.name}")
    }
  }
}

spotless {
  kotlinGradle {
    ktfmt().googleStyle()
    // licenseHeaderFile(rootProject.file("gradle/license-header-java.txt"), "")
  }
}

tasks.named<Wrapper>("wrapper") { distributionType = Wrapper.DistributionType.ALL }

val ideName = "CEL-Java ${rootProject.version.toString().replace(Regex("^([0-9.]+).*"), "$1")}"

idea {
  module {
    name = ideName
    isDownloadSources = true // this is the default BTW
    inheritOutputDirs = true
  }

  project {
    withGroovyBuilder {
      "settings" {
        val copyright: CopyrightConfiguration = getProperty("copyright") as CopyrightConfiguration
        val encodings: EncodingConfiguration = getProperty("encodings") as EncodingConfiguration
        val delegateActions: ActionDelegationConfig =
          getProperty("delegateActions") as ActionDelegationConfig

        delegateActions.testRunner = ActionDelegationConfig.TestRunner.CHOOSE_PER_TEST

        encodings.encoding = "UTF-8"
        encodings.properties.encoding = "UTF-8"

        copyright.useDefault = "ASL2"
        copyright.profiles.create("ASL2") {
          notice = rootProject.file("gradle/license-header.txt").readText()
        }
      }
    }
  }
}
// There's no proper way to set the name of the IDEA project (when "just importing" or syncing the
// Gradle project)
val ideaDir = projectDir.resolve(".idea")

if (ideaDir.isDirectory) {
  ideaDir.resolve(".name").writeText(ideName)
}

eclipse { project { name = ideName } }
