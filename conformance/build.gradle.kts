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

import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protoc

plugins {
    `java-library`
    id("com.diffplug.spotless")
    id("com.google.protobuf")
    id("com.github.johnrengelman.shadow")
    id("org.caffinitas.gradle.aggregatetestresults")
    id("org.caffinitas.gradle.testsummary")
    id("org.caffinitas.gradle.testrerun")
}

val versionAssertj = "3.22.0"
val versionJunit = "5.8.2"
val versionGrpc = "1.45.1"
val versionProtobuf = "3.20.0"

sourceSets.main {
    java.srcDir(project.buildDir.resolve("generated/source/proto/main/java"))
}

dependencies {
    implementation(project(":core"))
    implementation(project(":core", "testJar"))
    implementation(project(":generated-pb", "testJar"))

    implementation("com.google.protobuf:protobuf-java:$versionProtobuf")

    implementation("io.grpc:grpc-protobuf:$versionGrpc")
    implementation("io.grpc:grpc-stub:$versionGrpc")
    runtimeOnly("io.grpc:grpc-netty-shaded:$versionGrpc")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    testImplementation("org.assertj:assertj-core:$versionAssertj")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$versionJunit")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$versionJunit")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$versionJunit")
}

tasks.named<Jar>("shadowJar") {
    manifest {
        attributes("Main-Class" to "org.projectnessie.cel.server.ConformanceServer")
    }
}

// *.proto files taken from https://github.com/google/cel-spec/ repo, available as a git submodule
protobuf {
    // Configure the protoc executable
    protobuf.protoc {
        // Download from repositories
        artifact = "com.google.protobuf:protoc:$versionProtobuf"
    }
}

// The protobuf-plugin should ideally do this
tasks.named<Jar>("sourcesJar") {
    dependsOn(tasks.named("generateProto"))
}
