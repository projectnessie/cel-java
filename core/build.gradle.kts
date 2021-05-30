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
    antlr
    `maven-publish`
    id("com.diffplug.spotless")
}

dependencies {
    antlr("org.antlr:antlr4:4.9.2") // TODO remove from runtime-classpath *sigh*
    implementation("org.antlr:antlr4-runtime:4.9.2")

    implementation("com.google.protobuf:protobuf-java:3.17.1")
    implementation("org.agrona:agrona:1.10.0")

    testImplementation("org.assertj:assertj-core:3.19.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.7.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
}
