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
    id("com.diffplug.spotless")
    id("com.google.protobuf")
    id("com.github.johnrengelman.shadow")
    id("org.caffinitas.gradle.aggregatetestresults")
    id("org.caffinitas.gradle.testsummary")
    id("org.caffinitas.gradle.testrerun")
}

sourceSets.main {
    java.srcDir(project.buildDir.resolve("generated/source/proto/main/java"))
}

dependencies {
    implementation(platform(rootProject))

    implementation(project(":core"))
    implementation(project(":core", "testJar"))
    implementation(project(":generated-pb", "testJar"))

    implementation("com.google.protobuf:protobuf-java")

    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-stub")
    runtimeOnly("io.grpc:grpc-netty-shaded")
    compileOnly("org.apache.tomcat:annotations-api")

    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
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
        artifact = "com.google.protobuf:protoc:${rootProject.extra["versionProtobuf"]}"
    }
}

// The protobuf-plugin should ideally do this
tasks.named<Jar>("sourcesJar") {
    dependsOn(tasks.named("generateProto"))
}
