/*
 * Copyright (C) 2026 The Authors of CEL-Java
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

import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile
import org.apache.tools.zip.ZipOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.file.FilePermissions
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.file.UserClassFilePermissions
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

class NativeImageReflectionConfigTransformerTest {
  @TempDir lateinit var tempDir: File

  @Test
  fun `matches reflection configs below native-image directory only`() {
    val transformer = newTransformer()

    assertThat(
        transformer.canTransformResource(
          fileTreeElement(
            "META-INF/native-image/org.projectnessie.cel/cel-core/main/reflection-config.json"
          )
        )
      )
      .isTrue()
    assertThat(
        transformer.canTransformResource(
          fileTreeElement(
            "META-INF/native-image/org.projectnessie.cel/cel-core/main/native-image.properties"
          )
        )
      )
      .isFalse()
    assertThat(transformer.canTransformResource(fileTreeElement("reflection-config.json")))
      .isFalse()
  }

  @Test
  fun `merges relocates deduplicates and emits deterministic reflection config`() {
    val transformer = newTransformer()

    transformer.transform(
      context(
        """
        [
          {
            "name": "com.google.protobuf.Empty",
            "allPublicMethods": true,
            "methods": [
              {
                "name": "parseFrom",
                "parameterTypes": ["[Lcom.google.protobuf.DynamicMessage;"]
              }
            ]
          },
          {
            "type": "com.google.protobuf.DynamicMessage[]"
          }
        ]
        """
          .trimIndent()
      )
    )
    transformer.transform(
      context(
        """
        [
          {
            "name": "com.google.protobuf.Empty",
            "allDeclaredMethods": true,
            "allPublicMethods": false,
            "queriedMethods": [
              {
                "name": "newBuilder",
                "parameterTypes": ["com.google.protobuf.DescriptorProtos"]
              }
            ]
          },
          {
            "name": "com.google.protobuf.DescriptorProtos"
          }
        ]
        """
          .trimIndent()
      )
    )

    val output = transformOutput(transformer)
    val reflectionConfig =
      mapper.readTree(
        output[NativeImageReflectionConfigTransformer.DEFAULT_REFLECTION_CONFIG_PATH]
          ?: error("missing reflection config")
      )

    val reflectionEntries = reflectionConfig.toList()
    assertThat(reflectionEntries.map { it.get("name")?.asString() ?: it.get("type").asString() })
      .isEqualTo(
        listOf(
          "org.projectnessie.cel.relocated.com.google.protobuf.DescriptorProtos",
          "org.projectnessie.cel.relocated.com.google.protobuf.DynamicMessage[]",
          "org.projectnessie.cel.relocated.com.google.protobuf.Empty",
        )
      )

    val empty = reflectionEntries.single {
      it.get("name")?.asString() == "org.projectnessie.cel.relocated.com.google.protobuf.Empty"
    }
    assertThat(empty.get("allDeclaredMethods").booleanValue()).isTrue()
    assertThat(empty.get("allPublicMethods").booleanValue()).isTrue()
    assertThat(empty.toString())
      .contains("[Lorg.projectnessie.cel.relocated.com.google.protobuf.DynamicMessage;")
      .contains("org.projectnessie.cel.relocated.com.google.protobuf.DescriptorProtos")

    assertThat(output[NativeImageReflectionConfigTransformer.DEFAULT_NATIVE_IMAGE_PROPERTIES_PATH])
      .isEqualTo("Args = -H:ReflectionConfigurationResources=\${.}/reflection-config.json\n")
  }

  @Test
  fun `relocates native-image array descriptor names`() {
    val transformer = newTransformer()

    transformer.transform(context("""[{"name":"[Lcom.google.protobuf.DynamicMessage;"}]"""))

    val reflectionConfig =
      mapper.readTree(
        transformOutput(transformer)[
          NativeImageReflectionConfigTransformer.DEFAULT_REFLECTION_CONFIG_PATH]
      )

    assertThat(reflectionConfig.toList().single().get("name").asString())
      .isEqualTo("[Lorg.projectnessie.cel.relocated.com.google.protobuf.DynamicMessage;")
  }

  @Test
  fun `rejects invalid top level json`() {
    val transformer = newTransformer()

    assertThatThrownBy {
        transformer.transform(context("""{"name":"com.google.protobuf.Empty"}"""))
      }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("must be a JSON array")
  }

  @Test
  fun `rejects duplicate entries with conflicting scalars`() {
    val transformer = newTransformer()

    transformer.transform(context("""[{"name":"com.google.protobuf.Empty","condition":"a"}]"""))

    assertThatThrownBy {
        transformer.transform(context("""[{"name":"com.google.protobuf.Empty","condition":"b"}]"""))
      }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Conflicting native-image reflection metadata")
  }

  private fun newTransformer(): NativeImageReflectionConfigTransformer =
    ProjectBuilder.builder()
      .build()
      .objects
      .newInstance(NativeImageReflectionConfigTransformer::class.java)

  private fun context(json: String): TransformerContext =
    TransformerContext(
      "META-INF/native-image/org.projectnessie.cel/cel-core/main/reflection-config.json",
      json.byteInputStream(),
      setOf(SimpleRelocator("com.google", "org.projectnessie.cel.relocated.com.google")),
    )

  private fun transformOutput(
    transformer: NativeImageReflectionConfigTransformer
  ): Map<String, String> {
    val zipFile = File(tempDir, "transformed.zip")
    ZipOutputStream(zipFile.outputStream()).use { transformer.modifyOutputStream(it, false) }

    return ZipFile(zipFile).use { zip ->
      zip.entries().asSequence().associate { entry ->
        entry.name to zip.getInputStream(entry).reader().readText()
      }
    }
  }

  private fun fileTreeElement(path: String): FileTreeElement =
    object : FileTreeElement {
      override fun getFile(): File = File(path)

      override fun isDirectory(): Boolean = false

      override fun getLastModified(): Long = 0

      override fun getSize(): Long = 0

      override fun open(): InputStream = ByteArray(0).inputStream()

      override fun copyTo(output: OutputStream) {}

      override fun copyTo(target: File): Boolean = false

      override fun getName(): String = File(path).name

      override fun getPath(): String = path

      override fun getRelativePath(): RelativePath = RelativePath.parse(false, path)

      override fun getPermissions(): FilePermissions = permissions
    }

  companion object {
    private val mapper = ObjectMapper()
    private val userClassPermissions =
      object : UserClassFilePermissions {
        override fun getRead(): Boolean = true

        override fun getWrite(): Boolean = true

        override fun getExecute(): Boolean = false
      }
    private val permissions =
      object : FilePermissions {
        override fun getUser(): UserClassFilePermissions = userClassPermissions

        override fun getGroup(): UserClassFilePermissions = userClassPermissions

        override fun getOther(): UserClassFilePermissions = userClassPermissions

        override fun toUnixNumeric(): Int = 0
      }
  }
}
