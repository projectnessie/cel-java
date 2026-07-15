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

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import com.github.jengelman.gradle.plugins.shadow.transformers.CacheableTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import javax.inject.Inject
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.node.StringNode

@CacheableTransformer
open class NativeImageReflectionConfigTransformer
@Inject
constructor(override val objectFactory: ObjectFactory) : ResourceTransformer {
  @get:Input
  val reflectionConfigPath: Property<String> =
    objectFactory.property(String::class.java).convention(DEFAULT_REFLECTION_CONFIG_PATH)

  @get:Input
  val nativeImagePropertiesPath: Property<String> =
    objectFactory.property(String::class.java).convention(DEFAULT_NATIVE_IMAGE_PROPERTIES_PATH)

  @get:Input
  val emitNativeImageProperties: Property<Boolean> =
    objectFactory.property(Boolean::class.java).convention(true)

  @get:Internal internal val entriesByKey = linkedMapOf<String, ObjectNode>()

  override fun canTransformResource(element: FileTreeElement): Boolean =
    element.path.startsWith("META-INF/native-image/") &&
      element.path.endsWith("/reflection-config.json")

  override fun transform(context: TransformerContext) {
    val root =
      mapper.readTree(context.inputStream)
        ?: throw IllegalArgumentException("Empty native-image reflection config: ${context.path}")
    require(root.isArray()) {
      "Native-image reflection config must be a JSON array: ${context.path}"
    }

    root.forEachIndexed { index, node ->
      require(node is ObjectNode) {
        "Native-image reflection config entry $index must be an object: ${context.path}"
      }
      val relocated = relocateObject(node.deepCopy(), context.relocators)
      val key = reflectionEntryKey(relocated, context.path, index)
      entriesByKey.merge(key, relocated, ::mergeReflectionEntries)
    }
  }

  override fun hasTransformedResource(): Boolean = entriesByKey.isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val output = mapper.createArrayNode()
    entriesByKey.toSortedMap().values.forEach { output.add(it) }

    os.putNextEntry(zipEntry(reflectionConfigPath.get(), preserveFileTimestamps))
    os.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(output))
    os.closeEntry()

    if (emitNativeImageProperties.get()) {
      os.putNextEntry(zipEntry(nativeImagePropertiesPath.get(), preserveFileTimestamps))
      os.write(
        "Args = -H:ReflectionConfigurationResources=\${.}/reflection-config.json\n".toByteArray()
      )
      os.closeEntry()
    }
  }

  private fun relocateNode(node: JsonNode, relocators: Iterable<Relocator>): JsonNode =
    when {
      node.isObject() -> relocateObject(node as ObjectNode, relocators)
      node.isArray() -> relocateArray(node as ArrayNode, relocators)
      else -> node
    }

  private fun relocateObject(node: ObjectNode, relocators: Iterable<Relocator>): ObjectNode {
    val fields = node.properties().toList()
    fields.forEach { (fieldName, value) ->
      val relocated =
        if (fieldName in RELOCATABLE_TYPE_FIELDS) {
          relocateTypeValue(value, relocators)
        } else {
          relocateNode(value, relocators)
        }
      node.replace(fieldName, relocated)
    }
    return node
  }

  private fun relocateTypeValue(value: JsonNode, relocators: Iterable<Relocator>): JsonNode =
    when {
      value.isString() -> StringNode.valueOf(relocateTypeName(value.asString(), relocators))
      value.isArray() -> {
        val relocated = mapper.createArrayNode()
        value.forEach { relocated.add(relocateTypeValue(it, relocators)) }
        relocated
      }
      else -> relocateNode(value, relocators)
    }

  private fun relocateArray(node: ArrayNode, relocators: Iterable<Relocator>): ArrayNode {
    val relocated = mapper.createArrayNode()
    node.forEach { relocated.add(relocateNode(it, relocators)) }
    return relocated
  }

  private fun mergeReflectionEntries(existing: ObjectNode, incoming: ObjectNode): ObjectNode {
    val merged = existing.deepCopy()
    incoming.properties().forEach { (fieldName, incomingValue) ->
      val existingValue = merged.get(fieldName)
      when {
        existingValue == null -> merged.set(fieldName, incomingValue)
        existingValue == incomingValue -> Unit
        existingValue.isBoolean() && incomingValue.isBoolean() ->
          merged.put(fieldName, existingValue.booleanValue() || incomingValue.booleanValue())
        existingValue.isArray() && incomingValue.isArray() ->
          merged.set(fieldName, mergeArrays(existingValue as ArrayNode, incomingValue as ArrayNode))
        else ->
          throw IllegalArgumentException(
            "Conflicting native-image reflection metadata for ${reflectionEntryId(merged)} field '$fieldName'"
          )
      }
    }
    return merged
  }

  private fun mergeArrays(existing: ArrayNode, incoming: ArrayNode): ArrayNode {
    val values = linkedMapOf<String, JsonNode>()
    existing.forEach { values[canonicalJson(it)] = it }
    incoming.forEach { values.putIfAbsent(canonicalJson(it), it) }

    val merged = mapper.createArrayNode()
    values.toSortedMap().values.forEach { merged.add(it) }
    return merged
  }

  private fun reflectionEntryKey(node: ObjectNode, path: String, index: Int): String {
    val id = reflectionEntryId(node)
    require(id != null) {
      "Native-image reflection config entry $index in $path must contain 'name' or 'type'"
    }
    return id
  }

  private fun reflectionEntryId(node: ObjectNode): String? =
    node.get("name")?.takeIf(JsonNode::isString)?.asString()
      ?: node.get("type")?.takeIf(JsonNode::isString)?.asString()

  private fun relocateTypeName(typeName: String, relocators: Iterable<Relocator>): String {
    if (typeName.startsWith("[L") && typeName.endsWith(";")) {
      val component = typeName.substring(2, typeName.length - 1)
      return "[L${relocateTypeName(component, relocators)};"
    }

    if (typeName.endsWith("[]")) {
      return "${relocateTypeName(typeName.removeSuffix("[]"), relocators)}[]"
    }

    return relocators.relocateClass(typeName)
  }

  private fun canonicalJson(node: JsonNode): String = mapper.writeValueAsString(node)

  private fun zipEntry(name: String, preserveFileTimestamps: Boolean): ZipEntry =
    ZipEntry(name).apply {
      if (!preserveFileTimestamps) {
        time = CONSTANT_TIME_FOR_ZIP_ENTRIES
      }
    }

  companion object {
    const val DEFAULT_REFLECTION_CONFIG_PATH =
      "META-INF/native-image/org.projectnessie.cel/cel-standalone/reflection-config.json"
    const val DEFAULT_NATIVE_IMAGE_PROPERTIES_PATH =
      "META-INF/native-image/org.projectnessie.cel/cel-standalone/native-image.properties"

    private const val CONSTANT_TIME_FOR_ZIP_ENTRIES = 0L

    private val mapper = ObjectMapper()

    private val RELOCATABLE_TYPE_FIELDS =
      setOf(
        "name",
        "type",
        "parameterTypes",
        "parameterType",
        "returnType",
        "fieldType",
      )
  }
}

private inline fun <T> JsonNode.forEachIndexed(action: (Int, JsonNode) -> T) {
  var index = 0
  forEach { action(index++, it) }
}
