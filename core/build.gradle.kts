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
    id("me.champeau.jmh")
    id("org.caffinitas.gradle.aggregatetestresults")
    id("org.caffinitas.gradle.testsummary")
    id("org.caffinitas.gradle.testrerun")
}

val versionAgrona = "1.15.1"
val versionAssertj = "3.22.0"
val versionJmh = "1.35"
val versionJunit = "5.8.2"

dependencies {
    implementation(project(":generated-antlr", "shadow"))
    api(project(":generated-pb"))

    implementation("org.agrona:agrona:$versionAgrona")

    testImplementation(project(":generated-pb", "testJar"))
    testImplementation("org.assertj:assertj-core:$versionAssertj")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$versionJunit")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$versionJunit")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$versionJunit")

    jmhImplementation("org.openjdk.jmh:jmh-core:$versionJmh")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:$versionJmh")
}

jmh {
    jmhVersion.set(versionJmh)
}

val testJar by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = true
}
tasks.register<Jar>("testJar") {
    val testClasses = tasks.getByName<JavaCompile>("compileTestJava")
    val baseJar = tasks.getByName<Jar>("jar")
    from(testClasses.destinationDirectory)
    dependsOn(testClasses)
    dependsOn(tasks.named("processTestResources"))
    archiveBaseName.set(baseJar.archiveBaseName)
    destinationDirectory.set(baseJar.destinationDirectory)
    archiveClassifier.set("tests")
}
artifacts {
    val testJar = tasks.getByName<Jar>("testJar")
    add("testJar", testJar.archiveFile) {
        builtBy(testJar)
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jmh"))
}

tasks.named("assemble") {
    dependsOn(tasks.named("jmhJar"))
}
