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

/**
 * Класс для сборки библиотеки типов из нескольких файлов
 * @param fileNames пут до корневого файла спецификации
 */
class OpenApiV3LibraryBundle(private val fileNames: Array<URI>) {

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
        var sourceByParts: MutableMap<JsonPointer, MutableSet<URI>> = mutableMapOf()
    }

    /**
     * Результат сборки спецификации
     *
     * @param bundledSpecification текст собранной спецификации
     * @param conflictingTypeNames перечень всех конфликтов в формате: указатель на место в корневой спецификации, в которое пытались
     * добавить тип - список ссылок на типы, которые пытались добавить в это место корневой спецификации
     */
    data class Result(val bundledSpecification: String?, val conflictingTypeNames: Map<String, Set<URI>>)

    /**
     * Производит нормализацию спецификации:
     * * инлайнит ссылки на команды
     * * ссылки на типы, расположенные в других документах, помещает в соответствующие части блока components корневой спецификации,
     * меняет ссылку с определения типа в другом документе на определение в текущем документе
     * * Проверяет наличие конфликтов. Конфликтом считается попытка добавить в документ типы с одинаковым названием из разных источников
     * * Возвращает результат сборки: в случае успеха возвращает текст собранной спецификации,
     *    при наличии конфликтов, текст собранной спецификации отсутствует, в списке ошибок возвращается перечень всех конфликтов
     */
    fun bundle(): Result {

        if (fileNames.isEmpty()) {
            throw IllegalArgumentException("Empty filenames array")
        }

        var firstRootNode: JsonNode? = null
        var firstBaseJsonRef: JsonRef? = null

        for (fileName in fileNames) {
            val baseJsonRef = JsonRef.fromURI(fileName)
            if (firstBaseJsonRef == null) {
                firstBaseJsonRef = baseJsonRef
            }
            if (!collectedData.remoteRefContent.containsKey(baseJsonRef)) {
                // ссылка на домен, которая еще не обрабатывалась, загружаем кусок документа
                val tree: JsonNode = getTreeOrLoadAndCache(baseJsonRef)
                if (firstRootNode == null) {
                    firstRootNode = tree
                }
                processObjectNode(tree, baseJsonRef, collectedData)
            }
        }

        fillComponents(firstRootNode!!, firstBaseJsonRef!!, collectedData)

        val conflictingNames: Map<String, Set<URI>> = collectedData.sourceByParts
            .filter { entry -> entry.value.size > 1 }
            .mapKeys { entry -> entry.key.toString() }

        val bundledSpecification = if (conflictingNames.isEmpty())
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(firstRootNode) else null

        return Result(bundledSpecification, conflictingNames)
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
        // заполняем для первого документа
        val targetComponentsJsonNode = findOrCreateObjectNode(components, rootNode as ObjectNode)
        targetComponentsJsonNode.fields().forEach { entry ->
            entry.value.fields().forEach { sourceChildEntry ->
                val currentJsonPointer = JsonPointer.of(components, entry.key, sourceChildEntry.key)
                val sourceSet: MutableSet<URI> = collectedData.sourceByParts.getOrDefault(currentJsonPointer, mutableSetOf())
                sourceSet.add(baseJsonRef.locator)
                collectedData.sourceByParts[currentJsonPointer] = sourceSet
            }
        }

        collectedData.remoteRefContent.keys.forEach { jsonRef ->
            val refNode: JsonNode = getTreeOrLoadAndCache(jsonRef)
            // Наличие узла components в части документа с определением домена обязательно
            val sourceComponentsNode: JsonNode = refNode.findValue(components)
                ?: throw IllegalStateException("Specification part doesn't contain components: ref=$jsonRef")
            sourceComponentsNode.fields().forEach { entry ->
                val targetComponentsChildNode = findOrCreateObjectNode(entry.key, targetComponentsJsonNode)
                entry.value.fields().forEach { sourceChildEntry ->
                    val currentJsonPointer = JsonPointer.of(components, entry.key, sourceChildEntry.key)
                    if (jsonRef.pointer == currentJsonPointer || jsonRef.pointer.isEmpty) {
                        targetComponentsChildNode.set(sourceChildEntry.key, sourceChildEntry.value)

                        val sourceSet: MutableSet<URI> = collectedData.sourceByParts.getOrDefault(currentJsonPointer, mutableSetOf())
                        sourceSet.add(jsonRef.locator)
                        collectedData.sourceByParts[currentJsonPointer] = sourceSet
                    }
                }
            }
        }
    }

    private fun loadContent(location: URI): String = IOUtils.toString(location)

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