package com.yandex.money.openapi.normalization

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.github.fge.jackson.jsonpointer.JsonPointer
import com.github.fge.jsonschema.core.ref.JsonRef
import org.apache.commons.io.IOUtils
import java.net.URI

class OpenApiBundle(private val fileName: URI) {

    private val refKey: String = "\$ref"
    private val components: String = "components"
    private val hash: String = "#"

    companion object {
        private val yamlFactory = YAMLFactory()
            .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .disable(YAMLGenerator.Feature.INDENT_ARRAYS)
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .disable(YAMLGenerator.Feature.CANONICAL_OUTPUT)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)

        val mapper = ObjectMapper(yamlFactory)
    }

    private val collectedData = CollectedData()

    private class CollectedData {
        var remoteRefContent: MutableMap<JsonRef, JsonNode> = mutableMapOf()
        var conflictingTypeNames: MutableMap<JsonPointer, MutableSet<JsonRef>> = mutableMapOf()
    }

    data class Result(val bundledSpecification: String?, val conflictingTypeNames: MutableMap<JsonPointer, MutableSet<JsonRef>>)

    /**
     * Производит нормализацию спецификации:
     * все http ссылки на типы помещает в соответствующие части components
     * менят ссылки на части
     * Возвращаем нормализованный документ
     */
    fun bundle(): Result {

        val content = loadContent(fileName)
        val rootNode = mapper.readTree(content)
        val baseJsonRef = JsonRef.fromURI(fileName)

        processObjectNode(rootNode, baseJsonRef, collectedData)
        fillComponents(rootNode, baseJsonRef, collectedData)

        val bundledSpecification = if (collectedData.conflictingTypeNames.isEmpty())
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode) else null

        return Result(bundledSpecification, collectedData.conflictingTypeNames)
    }

    private fun processObjectNode(
        rootNode: JsonNode,
        baseJsonRef: JsonRef,
        collectedData: CollectedData
    ) {
        rootNode.fields().forEach { entry ->
            when {
                entry.value.isTextual && entry.key == refKey ->
                    processTextNode(entry.value, baseJsonRef, collectedData, rootNode as ObjectNode)
                entry.value.isArray ->
                    entry.value.forEach { processObjectNode(it, baseJsonRef, collectedData) }
                entry.value.isObject ->
                    processObjectNode(entry.value, baseJsonRef, collectedData)
            }
        }
    }

    private fun processTextNode(
        currentNode: JsonNode,
        baseJsonRef: JsonRef,
        collectedData: CollectedData,
        rootNode: ObjectNode
    ) {
        val jsonRef = baseJsonRef.resolve(JsonRef.fromString(currentNode.asText()))
        if (jsonRef.pointer.toString().startsWith(hash)) {
            // ссылки, которые указывают на текущий документ не обрабатываются
            return
        }
        when {
            jsonRef.pointer.isEmpty -> {
                // ссылка на команду
                val content: String = loadContent(jsonRef.toURI())
                val tree: JsonNode = mapper.readTree(content)
                processObjectNode(tree, jsonRef, collectedData)
                rootNode.remove(refKey)
                rootNode.setAll(tree as ObjectNode)
            }
            !collectedData.remoteRefContent.containsKey(jsonRef) -> {
                // ссылка на домен, которая еще не обрабатывалась, загружаем кусок документа
                val tree: JsonNode = getTreeOrLoadAndCache(jsonRef)
                processObjectNode(tree, jsonRef, collectedData)
                rootNode.replace(refKey, TextNode.valueOf(locateRefOnCurrentDocument(jsonRef)))
            }
            else -> {
                // ссылка на домен, которая еще не обрабатывалась, просто меняем ссылку
                rootNode.replace(refKey, TextNode.valueOf(locateRefOnCurrentDocument(jsonRef)))
            }
        }
    }

    private fun locateRefOnCurrentDocument(jsonRef: JsonRef): String = hash.plus(jsonRef.pointer.toString())

    private fun fillComponents(
        rootNode: JsonNode,
        baseJsonRef: JsonRef,
        collectedData: CollectedData
    ) {
        val targetComponentsJsonNode = findOrCreateObjectNode(components, rootNode as ObjectNode)
        collectedData.remoteRefContent.keys.forEach { jsonRef ->
            val refNode: JsonNode = getTreeOrLoadAndCache(jsonRef)
            // Наличие узла components в части документа с определением домена обязательно
            val sourceComponentsNode: JsonNode = refNode.findValue(components)
                ?: throw IllegalStateException("Specification part doesn't contain components: ref=$jsonRef")
            sourceComponentsNode.fields().forEach { entry ->
                val targetComponentsChildNode = findOrCreateObjectNode(entry.key, targetComponentsJsonNode)
                entry.value.fields().forEach { sourceChildEntry ->
                    val currentJsonPointer = JsonPointer.of(components, entry.key, sourceChildEntry.key)
                    if (jsonRef.pointer == currentJsonPointer) {
                        val existingJsonNode: JsonNode? = targetComponentsChildNode.get(sourceChildEntry.key)
                        if (existingJsonNode != null && !jsonRef.contains(baseJsonRef)) {
                            val existingJsonNodes = collectedData.conflictingTypeNames
                                .getOrDefault(currentJsonPointer, mutableSetOf())
                            existingJsonNodes.add(jsonRef)
                            collectedData.conflictingTypeNames[currentJsonPointer] = existingJsonNodes
                        }
                        targetComponentsChildNode.set(sourceChildEntry.key, sourceChildEntry.value)
                    }
                }
            }
        }
    }

    private fun loadContent(location: URI): String {
        return IOUtils.toString(location)
    }

    private fun getTreeOrLoadAndCache(jsonRef: JsonRef): JsonNode {
        val jsonNode: JsonNode? = collectedData.remoteRefContent[jsonRef]
        if (jsonNode != null) {
            return jsonNode
        }
        val content = loadContent(jsonRef.toURI())
        val loadedTree: JsonNode = mapper.readTree(content)
        collectedData.remoteRefContent[jsonRef] = loadedTree
        return loadedTree
    }

    private fun findOrCreateObjectNode(key: String, parent: ObjectNode): ObjectNode {
        var jsonNode: JsonNode? = parent.findValue(key)
        if (jsonNode == null) {
            jsonNode = mapper.createObjectNode()
            parent.set(key, jsonNode)
        }
        return jsonNode as ObjectNode
    }
}