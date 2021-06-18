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
    antlr
    `maven-publish`
    signing
    id("com.diffplug.spotless")
    id("com.google.protobuf")
    id("me.champeau.jmh")
    id("org.caffinitas.gradle.aggregatetestresults")
    id("org.caffinitas.gradle.testsummary")
    id("org.caffinitas.gradle.testrerun")
}

val versionAgrona = "1.11.0"
val versionAntlr = "4.9.2"
val versionAssertj = "3.20.1"
val versionGrpc = "1.38.1"
val versionJmh = "1.32"
val versionJunit = "5.7.2"
val versionProtobuf = "3.17.3"

sourceSets.named("main") {
    java.srcDir(project.buildDir.resolve("generated/source/proto/main/java"))
    java.srcDir(project.buildDir.resolve("generated/source/proto/main/grpc"))
}
sourceSets.named("test") {
    java.srcDir(project.buildDir.resolve("generated/source/proto/test/java"))
}

dependencies {
    antlr("org.antlr:antlr4:$versionAntlr") // TODO remove from runtime-classpath *sigh*
    implementation("org.antlr:antlr4-runtime:$versionAntlr")

    api("com.google.protobuf:protobuf-java:$versionProtobuf")
    implementation("org.agrona:agrona:$versionAgrona")

    // Since we need the protobuf stuff in this cel-core module, it's easy to generate the
    // gRPC code as well. But do not expose the gRPC dependencies "publicly".
    compileOnly("io.grpc:grpc-protobuf:$versionGrpc")
    compileOnly("io.grpc:grpc-stub:$versionGrpc")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

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
        artifact = "com.google.protobuf:protoc:$versionProtobuf"
    }
    protobuf.plugins {
        this.create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$versionGrpc"
        }
    }
    protobuf.generateProtoTasks {
        all().configureEach {
            this.plugins.create("grpc") {}
        }
    }
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