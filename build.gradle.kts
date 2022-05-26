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

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import org.jetbrains.gradle.ext.*

plugins {
  eclipse
  idea
  signing
  `java-platform`
  `maven-publish`
  id("org.jetbrains.gradle.plugin.idea-ext")
  id("com.diffplug.spotless")
  id("org.caffinitas.gradle.aggregatetestresults")
  id("org.caffinitas.gradle.testsummary")
  id("org.caffinitas.gradle.testrerun")
  id("io.github.gradle-nexus.publish-plugin")
}

val versionAgrona = "1.15.2"
val versionAntlr = "4.10.1"
val versionAssertj = "3.22.0"
val versionGrpc = "1.46.0"
val versionImmutables = "2.9.0"
val versionJackson = "2.13.3"
var versionJacoco = "0.8.7"
val versionJmh = "1.35"
val versionJSR305 = "3.0.2"
val versionJunit = "5.8.2"
val versionProtobuf = "3.21.0"
val versionTomcatAnnotationsApi = "6.0.53"

extra["versionAgrona"] = versionAgrona

extra["versionGrpc"] = versionGrpc

extra["versionJackson"] = versionJackson

extra["versionJmh"] = versionJmh

extra["versionProtobuf"] = versionProtobuf

dependencies {
  constraints {
    api("com.fasterxml.jackson.core:jackson-databind:$versionJackson")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-protobuf:$versionJackson")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$versionJackson")
    api("com.google.code.findbugs:jsr305:$versionJSR305")
    api("com.google.protobuf:protobuf-java:$versionProtobuf")
    api("org.agrona:agrona:$versionAgrona")
    api("org.antlr:antlr4:$versionAntlr")
    api("org.antlr:antlr4-runtime:$versionAntlr")
    api("org.apache.tomcat:annotations-api:$versionTomcatAnnotationsApi")
    api("org.assertj:assertj-core:$versionAssertj")
    api("org.immutables:value-processor:$versionImmutables")
    api("org.immutables:value-annotations:$versionImmutables")
    api("org.junit.jupiter:junit-jupiter-api:$versionJunit")
    api("org.junit.jupiter:junit-jupiter-params:$versionJunit")
    api("org.junit.jupiter:junit-jupiter-engine:$versionJunit")
    api("org.openjdk.jmh:jmh-core:$versionJmh")
    api("org.openjdk.jmh:jmh-generator-annprocess:$versionJmh")
    api("io.grpc:grpc-protobuf:$versionGrpc")
    api("io.grpc:grpc-stub:$versionGrpc")
    api("io.grpc:grpc-netty-shaded:$versionGrpc")
  }
}

allprojects {
  repositories { mavenCentral() }

  if (project.projectDir.resolve("src/test/java").exists()) {
    tasks.withType<Test>().configureEach {
      useJUnitPlatform {}
      maxParallelForks = Runtime.getRuntime().availableProcessors()
    }
  }

  tasks.withType<Jar>().configureEach {
    manifest {
      attributes["Implementation-Title"] = "CEL-Java"
      attributes["Implementation-Version"] = project.version
      attributes["Implementation-Vendor"] = "Authors of CEL-Java"
    }
  }

  tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

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
            name.set(project.name)
            description.set(project.description)
            inceptionYear.set("2021")
            url.set("https://github.com/projectnessie/cel-java")
            developers {
              file(rootProject.file("gradle/developers.csv"))
                .readLines()
                .map { line -> line.trim() }
                .filter { line -> !line.isEmpty() && !line.startsWith("#") }
                .forEach { line ->
                  val args = line.split(",")
                  if (args.size < 3) {
                    throw GradleException("gradle/developers.csv contains invalid line '${line}'")
                  }
                  developer {
                    id.set(args[0])
                    name.set(args[1])
                    url.set(args[2])
                  }
                }
            }
            contributors {
              file(rootProject.file("gradle/contributors.csv"))
                .readLines()
                .map { line -> line.trim() }
                .filter { line -> !line.isEmpty() && !line.startsWith("#") }
                .forEach { line ->
                  val args = line.split(",")
                  if (args.size > 2) {
                    throw GradleException("gradle/contributors.csv contains invalid line '${line}'")
                  }
                  contributor {
                    name.set(args[0])
                    url.set(args[1])
                  }
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
              developerConnection.set("scm:git:https://github.com/projectnessie/cel-java")
              url.set("https://github.com/projectnessie/cel-java/tree/main")
              tag.set("main")
            }
            issueManagement {
              system.set("Github")
              url.set("https://github.com/projectnessie/cel-java/issues")
            }
          }

          // Must exclude the `generated-antlr` project, because otherwise its `antlr` configuration
          // leaks antlr dependencies downstream.
          if (project.name != "generated-antlr" && project != rootProject) {
            from(components.firstOrNull { c -> c.name == "javaPlatform" || c.name == "java" })
          }
        }
      }
    }
  }

  plugins.withType<SigningPlugin>().configureEach {
    configure<SigningExtension> {
      if (project.hasProperty("release")) {
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
      }
    }
  }

  if (this.path != ":jacoco") {
    plugins.withType<JacocoPlugin>().configureEach {
      tasks.named<JacocoReport>("jacocoTestReport") {
        reports {
          html.required.set(true)
          xml.required.set(true)
        }
      }

      // Share sources folder with other projects for aggregated JaCoCo reports
      configurations.create("transitiveSourcesElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        attributes {
          attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
          attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
          attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("source-folders"))
        }
        val sourceSets = project.extensions.getByType<JavaPluginExtension>().sourceSets
        sourceSets.named("main").get().java.srcDirs.forEach { outgoing.artifact(it) }
      }

      // Share classes folder with other projects for aggregated JaCoCo reports
      configurations.create("transitiveClassesElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        attributes {
          attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
          attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
          attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("class-folders"))
        }
        val sourceSets = project.extensions.getByType<JavaPluginExtension>().sourceSets
        outgoing.artifact(sourceSets.named("main").get().java.destinationDirectory)
      }

      // Share the coverage data to be aggregated for the whole product
      configurations.create("coverageDataElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        attributes {
          attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
          attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
          attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("jacoco-coverage-data"))
        }
        // This will cause the test task to run if the coverage data is requested by the aggregation
        // task
        outgoing.artifact(
          tasks.named("test").map { task ->
            task.extensions.getByType<JacocoTaskExtension>().destinationFile!!
          }
        )
      }
    }
  }

  if (this != rootProject) {
    plugins.withType<JacocoPlugin>().configureEach {
      configure<JacocoPluginExtension> { toolVersion = versionJacoco }

      tasks.withType<JacocoReport> {
        reports {
          html.required.set(true)
          xml.required.set(true)
        }
      }
    }

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

javaPlatform { allowDependencies() }

spotless {
  kotlinGradle {
    ktfmt().googleStyle()
    licenseHeaderFile(rootProject.file("gradle/license-header-java.txt"), "$")
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

// Pass environment variables:
//    ORG_GRADLE_PROJECT_sonatypeUsername
//    ORG_GRADLE_PROJECT_sonatypePassword
// OR in ~/.gradle/gradle.properties set
//    sonatypeUsername
//    sonatypePassword
// Call targets:
//    publishToSonatype
//    closeAndReleaseSonatypeStagingRepository
nexusPublishing {
  transitionCheckOptions {
    // default==60 (10 minutes), wait up to 60 minutes
    maxRetries.set(360)
    // default 10s
    delayBetween.set(java.time.Duration.ofSeconds(10))
  }
  repositories { sonatype() }
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
