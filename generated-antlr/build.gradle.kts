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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    antlr
    `maven-publish`
    signing
    id("com.github.johnrengelman.shadow")
}

val versionAntlr = "4.10"

dependencies {
    antlr("org.antlr:antlr4:$versionAntlr") // TODO remove from runtime-classpath *sigh*
    implementation("org.antlr:antlr4-runtime:$versionAntlr")
}

// The antlr-plugin should ideally do this
tasks.named<Jar>("sourcesJar") {
    dependsOn(tasks.named("generateGrammarSource"))
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("raw")
}

tasks.named<ShadowJar>("shadowJar") {
    // The antlr-plugin should ideally do this
    dependsOn(tasks.named("generateGrammarSource"))

    dependencies {
        include(dependency("org.antlr:antlr4-runtime"))
    }
    relocate("org.antlr.v4.runtime", "org.projectnessie.cel.shaded.org.antlr.v4.runtime")
    archiveClassifier.set("")
}

publishing {
    publications {
        getByName<MavenPublication>("maven") {
            project.shadow.component(this)
            artifact(project.tasks.findByName("javadocJar"))
            artifact(project.tasks.findByName("sourcesJar"))
        }
    }
}