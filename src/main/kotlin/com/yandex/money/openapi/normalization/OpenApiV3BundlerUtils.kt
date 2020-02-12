package com.yandex.money.openapi.normalization

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
const val components: String = "components"
const val hash: String = "#"

private val yamlFactory = YAMLFactory()
    .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
    .disable(YAMLGenerator.Feature.INDENT_ARRAYS)
    .disable(YAMLGenerator.Feature.SPLIT_LINES)
    .disable(YAMLGenerator.Feature.CANONICAL_OUTPUT)
    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)

val mapper = ObjectMapper(yamlFactory)

fun loadContent(location: URI): String = IOUtils.toString(location)

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
    var jsonNode: JsonNode? = parent.findValue(key)
    if (jsonNode == null) {
        jsonNode = mapper.createObjectNode()
        parent.set(key, jsonNode)
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