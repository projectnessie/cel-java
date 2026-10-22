/*
 * Copyright (C) 2023 The Authors of CEL-Java
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
import javax.inject.Inject
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class PrepareStandaloneSources : DefaultTask() {
  @get:Classpath abstract val sourceArchives: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @get:Inject abstract val archiveOperations: ArchiveOperations

  @get:Inject abstract val fileSystemOperations: FileSystemOperations

  @TaskAction
  fun prepareSources() {
    val sourceTrees = sourceArchives.files.map { archiveOperations.zipTree(it) }
    fileSystemOperations.sync {
      from(sourceTrees)
      into(outputDirectory)
      include("org/projectnessie/cel/**")
    }
  }
}

plugins {
  `java-library`
  `jvm-test-suite`
  `maven-publish`
  signing
  id("com.gradleup.shadow")
  id("cel-conventions")
}

val standaloneShadow =
  configurations.create("standaloneShadow") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
  }

val standaloneCelSources =
  configurations.create("standaloneCelSources") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    attributes {
      attribute(
        Category.CATEGORY_ATTRIBUTE,
        objects.named(Category::class.java, Category.DOCUMENTATION),
      )
      attribute(
        DocsType.DOCS_TYPE_ATTRIBUTE,
        objects.named(DocsType::class.java, DocsType.SOURCES),
      )
      attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
    }
  }

dependencies {
  api(project(":cel-tools"))
  api(project(":cel-jackson"))
  api(project(":cel-jackson3"))

  compileOnly(project(":cel-generated-pb"))
  compileOnly(libs.protobuf.java)
  compileOnly(libs.agrona)

  standaloneShadow(project(":cel-core"))
  standaloneShadow(project(":cel-tools"))
  standaloneShadow(project(":cel-jackson"))
  standaloneShadow(project(":cel-jackson3"))
  standaloneShadow(project(":cel-generated-pb"))
  standaloneShadow(libs.protobuf.java)
  standaloneShadow(libs.agrona)
  standaloneShadow(libs.re2j)

  standaloneCelSources(project(":cel-core"))
  standaloneCelSources(project(":cel-tools"))
  standaloneCelSources(project(":cel-jackson"))
  standaloneCelSources(project(":cel-jackson3"))
}

val shadowJar = tasks.named<ShadowJar>("shadowJar")

shadowJar.configure {
  // relocate com.google.api/protobuf/rpc classes
  relocate("com.google", "org.projectnessie.cel.relocated.com.google")
  relocate("org.agrona", "org.projectnessie.cel.relocated.org.agrona")
  manifest {
    attributes["Specification-Title"] = "Common-Expression-Language - dependency-free CEL"
    attributes["Specification-Version"] = libs.protobuf.java.get().version
  }
  configurations = listOf(standaloneShadow)
}

tasks.named("compileJava").configure { finalizedBy(shadowJar) }

tasks.named("processResources").configure { finalizedBy(shadowJar) }

tasks.named("jar").configure { dependsOn("shadowJar") }

shadowJar.configure {
  outputs.cacheIf { false } // do not cache uber/shaded jars
  archiveClassifier.set("")
  exclude("META-INF/native-image/**/native-image.properties")
  mergeServiceFiles()
  transform(NativeImageReflectionConfigTransformer::class.java)
}

tasks.named<Jar>("jar").configure {
  dependsOn(shadowJar)
  archiveClassifier.set("raw")
}

val standaloneCelSourceArchives = standaloneCelSources.incoming.artifactView {}.files
val prepareStandaloneSources =
  tasks.register<PrepareStandaloneSources>("prepareStandaloneSources") {
    sourceArchives.from(standaloneCelSourceArchives)
    outputDirectory.set(layout.buildDirectory.dir("standalone-sources"))
  }

tasks.named<Jar>("sourcesJar") {
  // The standalone project has no sources of its own. Publish the maintained CEL-Java sources
  // whose classes are bundled in the shaded binary. Generated protobuf and third-party sources
  // are deliberately excluded.
  from(prepareStandaloneSources.flatMap { it.outputDirectory })
}

tasks.withType<ShadowJar>().configureEach { exclude("META-INF/jandex.idx") }

val standaloneJar = files(shadowJar.flatMap { it.archiveFile }).builtBy(shadowJar)
val projectDependencies = dependencies

fun JvmTestSuite.configureStandaloneSmokeSuite(
  pbProjectPath: String,
  jacksonDependencies: JvmComponentDependencies.() -> Unit,
) {
  useJUnitJupiter(libs.versions.junit.get())
  dependencies {
    implementation(standaloneJar)
    implementation(projectDependencies.project(mapOf("path" to pbProjectPath)))
    implementation(libs.assertj.core)
    jacksonDependencies()
  }
  targets.configureEach { testTask.configure { shouldRunAfter(tasks.named("test")) } }
}

testing {
  suites {
    val standaloneJackson2Pb =
      register<JvmTestSuite>("standaloneJackson2Pb") {
        sources { java { setSrcDirs(listOf("src/standaloneJackson2Smoke/java")) } }
        configureStandaloneSmokeSuite(":cel-generated-pb") {
          implementation(platform(libs.jackson2.bom))
          implementation("com.fasterxml.jackson.core:jackson-databind")
        }
      }
    val standaloneJackson2Pb3 =
      register<JvmTestSuite>("standaloneJackson2Pb3") {
        sources { java { setSrcDirs(listOf("src/standaloneJackson2Smoke/java")) } }
        configureStandaloneSmokeSuite(":cel-generated-pb3") {
          implementation(platform(libs.jackson2.bom))
          implementation("com.fasterxml.jackson.core:jackson-databind")
        }
      }
    val standaloneJackson3Pb =
      register<JvmTestSuite>("standaloneJackson3Pb") {
        sources { java { setSrcDirs(listOf("src/standaloneJackson3Smoke/java")) } }
        configureStandaloneSmokeSuite(":cel-generated-pb") {
          implementation(platform(libs.jackson3.bom))
          implementation("tools.jackson.core:jackson-databind")
        }
      }
    val standaloneJackson3Pb3 =
      register<JvmTestSuite>("standaloneJackson3Pb3") {
        sources { java { setSrcDirs(listOf("src/standaloneJackson3Smoke/java")) } }
        configureStandaloneSmokeSuite(":cel-generated-pb3") {
          implementation(platform(libs.jackson3.bom))
          implementation("tools.jackson.core:jackson-databind")
        }
      }

    tasks.named("check") {
      dependsOn(standaloneJackson2Pb)
      dependsOn(standaloneJackson2Pb3)
      dependsOn(standaloneJackson3Pb)
      dependsOn(standaloneJackson3Pb3)
    }
  }
}

// The following makes :cel-standalone consumable from an including build

shadow {
  addShadowVariantIntoJavaComponent = false
}

listOf("shadowApiElements", "shadowRuntimeElements").forEach { configurationName ->
  configurations.named(configurationName) {
    isCanBeConsumed = false
  }
}

listOf("apiElements", "runtimeElements").forEach { configurationName ->
  configurations.named(configurationName) {
    outgoing.artifacts.clear()
    outgoing.artifact(shadowJar)
    outgoing.variants.removeAll { true }
    attributes {
      attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
    }
  }
}
