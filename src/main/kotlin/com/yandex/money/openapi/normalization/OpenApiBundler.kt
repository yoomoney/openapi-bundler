package com.yandex.money.openapi.normalization

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.github.fge.jsonschema.core.ref.JsonRef
import org.apache.commons.io.IOUtils
import java.net.URI

class OpenApiBundler {

    private val refKey: String = "\$ref"
    private val components: String = "components"

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

    private class CollectedData() {
        var remoteRefs: MutableSet<JsonRef> = mutableSetOf()
        var remoteRefContent: MutableMap<JsonRef, JsonNode> = mutableMapOf()
    }

    /**
     * Производит нормализацию спецификации:
     * все http ссылки на типы помещает в соответствующие части components
     * менят ссылки на части
     * Возвращаем нормализованный документ
     */
    fun bundle(fileName: URI): String {

        val content = loadContent(fileName)
        val rootNode = mapper.readTree(content)

        val collectedData = CollectedData()
        val baseJsonRef: JsonRef = JsonRef.fromURI(fileName)
        // collect remote refs
        processObjectNode(rootNode, baseJsonRef, collectedData)
        // fill components
        fillComponents(rootNode, collectedData)

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode)
    }

    private fun processObjectNode(
        rootNode: JsonNode,
        baseJsonRef: JsonRef,
        collectedData: CollectedData
    ) {
        for (entry: Map.Entry<String, JsonNode> in rootNode.fields()) {
            if (entry.value.isTextual && entry.key == refKey) {
                processTextNode(entry.value, baseJsonRef, collectedData, rootNode)
            } else if (entry.value.isArray) {
                for (item in entry.value) {
                    processObjectNode(item, baseJsonRef, collectedData)
                }
            } else if (entry.value.isObject) {
                processObjectNode(entry.value, baseJsonRef, collectedData)
            }
        }
    }

    private fun processTextNode(
        currentNode: JsonNode,
        baseJsonRef: JsonRef,
        collectedData: CollectedData,
        rootNode: JsonNode
    ) {
        val jsonRef = baseJsonRef.resolve(JsonRef.fromString(currentNode.asText()))
        if (!jsonRef.locator.toASCIIString().startsWith('#')) {
            when {
                jsonRef.pointer.isEmpty -> {
                    val content: String = loadContent(jsonRef.toURI())
                    val tree: JsonNode = mapper.readTree(content)
                    processObjectNode(tree, jsonRef, collectedData)
                    (rootNode as ObjectNode).remove(refKey)
                    (rootNode as ObjectNode).setAll(tree as ObjectNode)
                }
                collectedData.remoteRefs.add(jsonRef) -> {
                    val content: String = loadContent(jsonRef.toURI())
                    val tree: JsonNode = mapper.readTree(content)
                    collectedData.remoteRefContent[jsonRef] = tree
                    processObjectNode(tree, jsonRef, collectedData)
                    (rootNode as ObjectNode).replace(refKey, TextNode.valueOf(resolveRef(jsonRef)))
                }
                else -> {
                    (rootNode as ObjectNode).replace(refKey, TextNode.valueOf(resolveRef(jsonRef)))
                }
            }
        }
    }

    private fun resolveRef(jsonRef: JsonRef?): String? {
        return "#" + jsonRef?.pointer.toString()
    }

    private fun fillComponents(
        rootNode: JsonNode,
        collectedData: CollectedData
    ) {
        var componentsJsonNode: JsonNode? = rootNode.findValue(components)
        if (componentsJsonNode == null) {
            componentsJsonNode = mapper.createObjectNode()
            (rootNode as ObjectNode).set(components, componentsJsonNode)
        }
        collectedData.remoteRefs.forEach {
            var refNode: JsonNode? = collectedData.remoteRefContent[it]
            if (refNode == null) {
                refNode = mapper.readTree(loadContent(it.toURI()))
                collectedData.remoteRefContent[it] = refNode
            }
            val refComponentsNode: JsonNode = refNode!!.findValue(components)
            for (entry: Map.Entry<String, JsonNode> in refComponentsNode.fields()) {
                var componentsChildNode: JsonNode? = (componentsJsonNode as ObjectNode).findValue(entry.key)
                if (componentsChildNode == null) {
                    componentsChildNode = mapper.createObjectNode()
                    componentsJsonNode.set(entry.key, componentsChildNode)
                }

                for (childEntry: Map.Entry<String, JsonNode> in entry.value.fields()) {
                    if (it.pointer.last().token.raw == childEntry.key) {
                        (componentsChildNode as ObjectNode).set(childEntry.key, childEntry.value)
                    }
                }
            }
        }
    }

    private fun loadContent(location: URI): String {
        return IOUtils.toString(location)
    }
}