package ru.yoomoney.openapi.bundler

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.github.fge.jackson.jsonpointer.JsonPointer
import com.github.fge.jsonschema.core.ref.JsonRef
import org.apache.commons.io.IOUtils
import java.net.URI

const val refKey: String = "\$ref"
const val exampleKey: String = "example"
const val typeKey: String = "type"
const val stringValueMarker = " #string-value-marker# "
const val components: String = "components"
const val paths: String = "paths"
const val hash: String = "#"

private val yamlFactory = YAMLFactory()
    .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
    .disable(YAMLGenerator.Feature.INDENT_ARRAYS)
    .disable(YAMLGenerator.Feature.SPLIT_LINES)
    .disable(YAMLGenerator.Feature.CANONICAL_OUTPUT)
    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)

val mapper = ObjectMapper(yamlFactory)

fun loadContent(location: URI): String = IOUtils.toString(location, Charsets.UTF_8)

fun createJsonRef(str: String): JsonRef {
    return JsonRef.fromString(str.replace("{", "%7B").replace("}", "%7D"))
}

fun getTreeOrLoadToCache(jsonRef: JsonRef, cache: MutableMap<JsonRef, JsonNode>): JsonNode {
    val jsonNode: JsonNode? = cache[jsonRef]
    if (jsonNode != null) {
        return jsonNode
    }
    val content = loadContent(jsonRef.toURI())
    val loadedTree: JsonNode = mapper.readTree(content)
    cache[jsonRef] = loadedTree
    return loadedTree
}

fun findOrCreateObjectNode(key: String, parent: ObjectNode): ObjectNode {
    var jsonNode: JsonNode? = null
    for (field in parent.fields()) {
        if (field.key == key) {
            jsonNode = field.value
            break
        }
    }
    if (jsonNode == null) {
        jsonNode = mapper.createObjectNode()
        parent.set<JsonNode>(key, jsonNode)
    }
    return jsonNode as ObjectNode
}

fun locateRefOnCurrentDocument(jsonRef: JsonRef): String = hash.plus(jsonRef.pointer.toString())

fun addSourceOfComponent(
    currentJsonPointer: JsonPointer,
    jsonRef: JsonRef,
    sourcesOfComponents: MutableMap<JsonPointer, MutableSet<URI>>
) {
    val sourceSet: MutableSet<URI> = sourcesOfComponents.getOrDefault(currentJsonPointer, mutableSetOf())
    sourceSet.add(jsonRef.locator)
    sourcesOfComponents[currentJsonPointer] = sourceSet
}