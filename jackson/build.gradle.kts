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

plugins {
    `java-library`
    `maven-publish`
    signing
    jacoco
    id("com.diffplug.spotless")
    id("org.caffinitas.gradle.aggregatetestresults")
    id("org.caffinitas.gradle.testsummary")
    id("org.caffinitas.gradle.testrerun")
}

val versionAssertj = "3.22.0"
val versionImmutables = "2.9.0"
val versionJackson = "2.13.1"
val versionJSR305 = "3.0.2"
val versionJunit = "5.8.2"

dependencies {
    api(project(":core"))

    implementation("com.fasterxml.jackson.core:jackson-databind:$versionJackson")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-protobuf:$versionJackson")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$versionJackson")

    testImplementation(project(":tools"))
    testAnnotationProcessor("org.immutables:value-processor:$versionImmutables")
    testCompileOnly("org.immutables:value-annotations:$versionImmutables")
    testImplementation("com.google.code.findbugs:jsr305:$versionJSR305")
    testImplementation("org.assertj:assertj-core:$versionAssertj")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$versionJunit")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$versionJunit")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$versionJunit")
}
