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

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import groovy.util.Node
import groovy.util.NodeList
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

/** Applies common configurations to all Nessie projects. */
@Suppress("unused")
class PublishingHelperPlugin
@Inject
constructor(private val softwareComponentFactory: SoftwareComponentFactory) : Plugin<Project> {
  override fun apply(project: Project): Unit = project.run {
    extensions.create("publishingHelper", PublishingHelperExtension::class.java, this)

    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    plugins.withId("maven-publish") {
      val publication =
        extensions.getByType<PublishingExtension>().publications.register<MavenPublication>(
          "maven"
        ) {
          val parentGroupId = project.group.toString()
          val parentVersion = project.version.toString()

          groupId = "$group"
          version = parentVersion

          configurePom(project)

          if (path != ":") {
            pom.withXml {
              val projectNode = asNode()

              val parentNode = projectNode.appendNode("parent")
              parentNode.appendNode("groupId", parentGroupId)
              parentNode.appendNode("artifactId", "cel-parent")
              parentNode.appendNode("version", parentVersion)
            }
          }

          suppressPomMetadataWarningsFor("testApiElements")
          suppressPomMetadataWarningsFor("testJavadocElements")
          suppressPomMetadataWarningsFor("testRuntimeElements")
          suppressPomMetadataWarningsFor("testSourcesElements")
          suppressPomMetadataWarningsFor("testFixturesApiElements")
          suppressPomMetadataWarningsFor("testFixturesRuntimeElements")
        }

      if (project.plugins.hasPlugin(ShadowPlugin::class.java)) {
        publication.configure { configureShadowPublishing(project, this, softwareComponentFactory) }
      } else {
        plugins.withId("java-platform") {
          publication.configure { from(components.getByName("javaPlatform")) }
        }
        plugins.withId("java") { publication.configure { project.configureJavaPublishing(this) } }
      }

      configureSigning(publication.get())
    }

    tasks.named("generatePomFileForMavenPublication", GenerateMavenPom::class.java) {
      if (path == ":") {
        inputs
          .file(layout.settingsDirectory.file("gradle/developers.csv"))
          .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs
          .file(layout.settingsDirectory.file("gradle/contributors.csv"))
          .withPathSensitivity(PathSensitivity.RELATIVE)
      }
    }

    // Gradle complains when a Gradle module metadata ("pom on steroids") is generated with an
    // enforcedPlatform() dependency - but Quarkus requires enforcedPlatform(), so we have to
    // allow it.
    tasks.withType<GenerateModuleMetadata>().configureEach {
      suppressedValidationErrors.add("enforced-platform")
    }
  }

  private fun Project.configureJavaPublishing(mavenPublication: MavenPublication) {
    val component = components.findByName("java") ?: return
    if (component is AdhocComponentWithVariants) {
      listOf("testFixturesApiElements", "testFixturesRuntimeElements").forEach { cfg ->
        configurations.findByName(cfg)?.apply {
          component.addVariantsFromConfiguration(this) { skip() }
        }
      }
    }
    mavenPublication.from(component)
  }

  private fun MavenPublication.configurePom(project: Project) = project.run {
    val e = extensions.getByType<PublishingHelperExtension>()
    val pomName =
      if (extra.has("maven.name")) {
        extra["maven.name"].toString()
      } else {
        project.name
      }
    val pomDescription = project.description
    val developersFile = layout.settingsDirectory.file("gradle/developers.csv")
    val contributorsFile = layout.settingsDirectory.file("gradle/contributors.csv")

    pom {
      name.set(pomName)
      description.set(pomDescription)

      if (path == ":") {
        val repoUrl = e.nessieRepoName.map { "https://github.com/projectnessie/$it" }
        val scmUrl = repoUrl.map { "scm:git:$it" }
        val developers =
          parsePeopleFile(
            providers.fileContents(developersFile).asText.get(),
            "gradle/developers.csv",
            minColumns = 3,
            maxColumns = 3,
          )
        val contributors =
          parsePeopleFile(
            providers.fileContents(contributorsFile).asText.get(),
            "gradle/contributors.csv",
            minColumns = 2,
            maxColumns = 2,
          )

        inceptionYear.set(e.inceptionYear)
        url.set(repoUrl)
        organization {
          name.set("Project Nessie")
          url.set("https://projectnessie.org")
        }
        licenses {
          license {
            name.set("The Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
          }
        }
        mailingLists {
          mailingList {
            name.set("Project Nessie List")
            subscribe.set("projectnessie-subscribe@googlegroups.com")
            unsubscribe.set("projectnessie-unsubscribe@googlegroups.com")
            post.set("projectnessie@googlegroups.com")
            archive.set("https://groups.google.com/g/projectnessie")
          }
        }
        scm {
          connection.set(scmUrl)
          developerConnection.set(scmUrl)
          url.set(repoUrl.map { "$it/tree/main" })
          tag.set("main")
        }
        issueManagement {
          system.set("Github")
          url.set(repoUrl.map { "$it/issues" })
        }
        developers {
          developers.forEach { person ->
            developer {
              id.set(person[0])
              name.set(person[1])
              url.set(person[2])
            }
          }
        }
        contributors {
          contributors.forEach { person ->
            contributor {
              name.set(person[0])
              url.set(person[1])
            }
          }
        }
      }
    }
  }

  private fun Project.configureSigning(mavenPublication: MavenPublication) {
    if (providers.gradleProperty("release").isPresent) {
      plugins.withType<SigningPlugin>().configureEach {
        configure<SigningExtension> {
          useInMemoryPgpKeys(
            providers.gradleProperty("signingKey").orNull,
            providers.gradleProperty("signingPassword").orNull,
          )
          sign(mavenPublication)

          if (providers.gradleProperty("useGpgAgent").isPresent) {
            useGpgCmd()
          }
        }
      }
    }
  }

  private fun parsePeopleFile(
    text: String,
    path: String,
    minColumns: Int,
    maxColumns: Int,
  ): List<List<String>> =
    text
      .lineSequence()
      .map { line -> line.trim() }
      .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
      .map { line ->
        val args = line.split(",")
        if (args.size < minColumns || args.size > maxColumns) {
          throw GradleException("$path contains invalid line '${line}'")
        }
        args
      }
      .toList()

  private fun xmlNode(node: Node?, child: String): Node? {
    val found = node?.get(child)
    if (found is NodeList) {
      if (found.isNotEmpty()) {
        return found[0] as Node
      }
    }
    return null
  }
}

internal fun configureShadowPublishing(
  project: Project,
  mavenPublication: MavenPublication,
  softwareComponentFactory: SoftwareComponentFactory,
) = project.run {
  fun isPublishable(element: ConfigurationVariant): Boolean {
    for (artifact in element.artifacts) {
      if (JavaBasePlugin.UNPUBLISHABLE_VARIANT_ARTIFACTS.contains(artifact.type)) {
        return false
      }
    }
    return true
  }

  val shadowJar = project.tasks.named("shadowJar")
  val shadowProjectDependencies =
    project.configurations
      .getByName("shadow")
      .allDependencies
      .withType(ProjectDependency::class.java)
      .map {
        PomDependency(it.group, it.name, it.version)
      }

  val shadowApiElements =
    project.configurations.create("shadowApiElements") {
      isCanBeConsumed = true
      isCanBeResolved = false
      attributes {
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_API))
        attribute(
          Category.CATEGORY_ATTRIBUTE,
          project.objects.named(Category::class.java, Category.LIBRARY),
        )
        attribute(
          LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
          project.objects.named(LibraryElements::class.java, LibraryElements.JAR),
        )
        attribute(
          Bundling.BUNDLING_ATTRIBUTE,
          project.objects.named(Bundling::class.java, Bundling.SHADOWED),
        )
      }
      outgoing.artifact(shadowJar)
    }

  val component = softwareComponentFactory.adhoc("shadow")
  component.addVariantsFromConfiguration(shadowApiElements) {
    if (isPublishable(configurationVariant)) {
      mapToMavenScope("compile")
    } else {
      skip()
    }
  }
  // component.addVariantsFromConfiguration(configurations.getByName("runtimeElements")) {
  component.addVariantsFromConfiguration(
    project.configurations.getByName("shadowRuntimeElements")
  ) {
    if (isPublishable(configurationVariant)) {
      mapToMavenScope("runtime")
    } else {
      skip()
    }
  }
  // Sonatype requires the javadoc and sources jar to be present, but the
  // Shadow extension does not publish those.
  component.addVariantsFromConfiguration(project.configurations.getByName("javadocElements")) {}
  component.addVariantsFromConfiguration(project.configurations.getByName("sourcesElements")) {}
  mavenPublication.from(component)

  // This a replacement to add dependencies to the pom, if necessary. Equivalent to
  // 'shadowExtension.component(mavenPublication)', which we cannot use.

  mavenPublication.pom {
    withXml {
      val node = asNode()
      val depNode = node.get("dependencies")
      val dependenciesNode =
        if ((depNode as NodeList).isNotEmpty()) depNode[0] as Node
        else node.appendNode("dependencies")
      shadowProjectDependencies.forEach {
        val dependencyNode = dependenciesNode.appendNode("dependency")
        dependencyNode.appendNode("groupId", it.groupId)
        dependencyNode.appendNode("artifactId", it.artifactId)
        dependencyNode.appendNode("version", it.version)
        dependencyNode.appendNode("scope", "runtime")
      }
    }
  }
}

private data class PomDependency(val groupId: String?, val artifactId: String, val version: String?)
