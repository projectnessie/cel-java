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

import com.google.protobuf.gradle.protoc

plugins {
    `java-library`
    antlr
    `maven-publish`
    id("com.diffplug.spotless")
    id("com.google.protobuf")
    id("me.champeau.jmh")
}

val versionAgrona = "1.10.0"
val versionAntlr = "4.9.2"
val versionAssertj = "3.19.0"
val versionJmh = "1.32"
val versionJunit = "5.7.2"
val versionProtobuf = "3.17.3"

sourceSets.named("main") {
    java.srcDir(project.buildDir.resolve("generated/source/proto/main/java"))
}
sourceSets.named("test") {
    java.srcDir(project.buildDir.resolve("generated/source/proto/test/java"))
}

dependencies {
    antlr("org.antlr:antlr4:$versionAntlr") // TODO remove from runtime-classpath *sigh*
    implementation("org.antlr:antlr4-runtime:$versionAntlr")

    implementation("com.google.protobuf:protobuf-java:$versionProtobuf")
    implementation("org.agrona:agrona:$versionAgrona")

    testImplementation("org.assertj:assertj-core:$versionAssertj")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$versionJunit")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$versionJunit")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$versionJunit")

    jmhImplementation("org.openjdk.jmh:jmh-core:$versionJmh")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:$versionJmh")
}

// *.proto files taken from https://github.com/googleapis/googleapis/ repo, available as a git submodule
protobuf {
    // Configure the protoc executable
    protobuf.protoc {
        // Download from repositories
        artifact = "com.google.protobuf:protoc:3.0.0"
    }
}

jmh {
    jmhVersion.set(versionJmh)
}