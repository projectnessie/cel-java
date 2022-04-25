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

plugins {
  `kotlin-dsl`
  id("com.gradle.plugin-publish") version "0.21.0"
  id("com.diffplug.spotless") version "6.5.0"
}

repositories {
  gradlePluginPortal()
  mavenCentral()
}

group = "org.projectnessie.cel.build"

version = file("../version.txt").readText().trim()

val versionAsm = "9.3"
val versionProtobufPlugin = "0.8.18"

dependencies {
  implementation("org.ow2.asm:asm:$versionAsm")
  implementation("com.google.protobuf:protobuf-gradle-plugin:$versionProtobufPlugin")
}

java {
  withJavadocJar()
  withSourcesJar()
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

gradlePlugin {
  plugins {
    create("reflectionconfig") {
      id = "org.projectnessie.cel.reflectionconfig"
      implementationClass = "org.projectnessie.cel.tools.plugins.ReflectionConfigPlugin"
    }
  }
}

pluginBundle {
  vcsUrl = "https://github.com/projectnessie/cel-java/"

  plugins { named("reflectionconfig") {} }
}

spotless {
  kotlinGradle {
    ktfmt().googleStyle()
    licenseHeaderFile(rootProject.file("../gradle/license-header-java.txt"), "")
  }
  kotlin {
    ktfmt().googleStyle()
    licenseHeaderFile(rootProject.file("../gradle/license-header-java.txt"), "")
  }
}
