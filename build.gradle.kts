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

import org.jetbrains.gradle.ext.*

plugins {
  signing
  `java-platform`
  `maven-publish`
  id("org.caffinitas.gradle.aggregatetestresults")
  id("org.caffinitas.gradle.testsummary")
  id("org.caffinitas.gradle.testrerun")
  id("org.projectnessie.buildsupport.ide-integration")
  id("io.github.gradle-nexus.publish-plugin")
  `cel-conventions`
}

val versionAgrona = "1.16.0"
val versionAntlr = "4.10.1"
val versionAssertj = "3.23.1"
val versionGoogleJavaFormat = "1.15.0"
val versionGrpc = "1.48.1"
val versionImmutables = "2.9.0"
val versionJackson = "2.13.3"
val versionJacoco = "0.8.8"
val versionJmh = "1.35"
val versionJSR305 = "3.0.2"
val versionJunit = "5.9.0"
val versionProtobuf = "3.21.5"
val versionTomcatAnnotationsApi = "6.0.53"

extra["versionAgrona"] = versionAgrona

extra["versionGoogleJavaFormat"] = versionGoogleJavaFormat

extra["versionGrpc"] = versionGrpc

extra["versionJackson"] = versionJackson

extra["versionJacoco"] = versionJacoco

extra["versionJmh"] = versionJmh

extra["versionProtobuf"] = versionProtobuf

dependencies {
  constraints {
    api("com.fasterxml.jackson.core:jackson-databind:$versionJackson")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-protobuf:$versionJackson")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$versionJackson")
    api("com.google.code.findbugs:jsr305:$versionJSR305")
    api("com.google.googlejavaformat:google-java-format:$versionGoogleJavaFormat")
    api("com.google.protobuf:protobuf-java:$versionProtobuf")
    api("org.agrona:agrona:$versionAgrona")
    api("org.antlr:antlr4:$versionAntlr")
    api("org.antlr:antlr4-runtime:$versionAntlr")
    api("org.apache.tomcat:annotations-api:$versionTomcatAnnotationsApi")
    api("org.assertj:assertj-core:$versionAssertj")
    api("org.immutables:value-processor:$versionImmutables")
    api("org.immutables:value-annotations:$versionImmutables")
    api("org.jacoco:jacoco-maven-plugin:$versionJacoco")
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

javaPlatform { allowDependencies() }

tasks.named<Wrapper>("wrapper") { distributionType = Wrapper.DistributionType.ALL }

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

publishingHelper {
  nessieRepoName.set("cel-java")
  inceptionYear.set("2021")
}
