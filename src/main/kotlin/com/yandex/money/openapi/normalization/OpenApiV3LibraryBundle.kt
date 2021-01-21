package com.yandex.money.openapi.normalization

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.github.fge.jackson.jsonpointer.JsonPointer
import com.github.fge.jsonschema.core.ref.JsonRef
import java.net.URI

/**
 * Класс для сборки библиотеки типов из нескольких файлов
 * @param fileNames пут до корневого файла спецификации
 */
class OpenApiV3LibraryBundle(private val fileNames: Array<URI>) {

    private class CollectedData {
        var remoteRefContent: MutableMap<JsonRef, JsonNode> = mutableMapOf()
        var sourcesOfComponents: MutableMap<JsonPointer, MutableSet<URI>> = mutableMapOf()
    }

    /**
     * Результат сборки библиотеки типов
     *
     * @param bundledSpecification текст собранной библиотеки типов
     * @param conflictingTypeNames перечень всех конфликтов в формате: указатель на тип - список источников, которые содержат тип с таким
     * же названием
     */
    data class Result(val bundledSpecification: String?, val conflictingTypeNames: Map<String, Set<URI>>)

    /**
     * Производит нормализацию библиотеки типов:
     * * ссылки на типы, расположенные в других документах, помещает в соответствующие части блока components единого файла с типами,
     * меняет ссылку с определения типа в другом документе на определение в текущем документе
     * * Проверяет наличие конфликтов. Конфликтом считается попытка добавить в документ типы с одинаковым названием из разных источников
     * * Возвращает результат сборки: в случае успеха возвращает текст собранной спецификации,
     *    при наличии конфликтов, текст собранной спецификации отсутствует, в списке ошибок возвращается перечень всех конфликтов
     */
    fun bundle(): Result {
        if (fileNames.isEmpty()) {
            throw IllegalArgumentException("Empty filenames array")
        }

        val collectedData = CollectedData()
        var firstRootNode: JsonNode? = null
        var firstBaseJsonRef: JsonRef? = null

        for (fileName in fileNames) {
            val baseJsonRef = JsonRef.fromURI(fileName)
            if (firstBaseJsonRef == null) {
                firstBaseJsonRef = baseJsonRef
            }
            if (!collectedData.remoteRefContent.containsKey(baseJsonRef)) {
                // ссылка на домен, которая еще не обрабатывалась, загружаем кусок документа
                val tree: JsonNode = getTreeOrLoadToCache(baseJsonRef, collectedData.remoteRefContent)
                if (firstRootNode == null) {
                    firstRootNode = tree
                }
                processObjectNode(tree, baseJsonRef, collectedData)
            }
        }

        fillComponents(firstRootNode!!, firstBaseJsonRef!!, collectedData)

        val conflictingNames: Map<String, Set<URI>> = collectedData.sourcesOfComponents
            .filter { entry -> entry.value.size > 1 }
            .mapKeys { entry -> entry.key.toString() }

        val bundledSpecification = if (conflictingNames.isEmpty())
            mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(firstRootNode)
                .replace(stringValueMarker, "") // Удаляем маркеры форсирования добавления кавычек при сериализации
        else null

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
                entry.value.isTextual && entry.key == exampleKey ->
                    processExampleNode(entry.value, rootNode as ObjectNode)
                entry.value.isArray ->
                    entry.value.forEach { processObjectNode(it, baseJsonRef, collectedData) }
                entry.value.isObject ->
                    processObjectNode(entry.value, baseJsonRef, collectedData)
            }
        }
    }

    private fun processExampleNode(
        currentNode: JsonNode,
        rootNode: ObjectNode
    ) {
        // При сериализации строковых значений сериализатор убирает кавычки у строк из-за настройки YAMLGenerator.Feature.MINIMIZE_QUOTES.
        // Поэтому явно добавляем маркер с пробелом в начало строки, чтобы зафорсить добавление одинарных кавычек для строковых примеров.
        // После сериализации всей спецификации все маркеры будут удалены.
        if (rootNode.get(typeKey)?.textValue()?.toLowerCase() == "string") {
            rootNode.set(exampleKey, TextNode.valueOf(stringValueMarker + currentNode.textValue()))
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
                val tree: JsonNode = getTreeOrLoadToCache(jsonRef, collectedData.remoteRefContent)
                processObjectNode(tree, jsonRef, collectedData)
                rootNode.replace(refKey, TextNode.valueOf(locateRefOnCurrentDocument(jsonRef)))
            }
            else -> {
                // ссылка на домен, которая еще не обрабатывалась, просто меняем ссылку
                rootNode.replace(refKey, TextNode.valueOf(locateRefOnCurrentDocument(jsonRef)))
            }
        }
    }

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
                addSourceOfComponent(currentJsonPointer, baseJsonRef, collectedData.sourcesOfComponents)
            }
        }

        collectedData.remoteRefContent.keys.forEach { jsonRef ->
            val refNode: JsonNode = getTreeOrLoadToCache(jsonRef, collectedData.remoteRefContent)
            // Наличие узла components в части документа с определением домена обязательно
            val sourceComponentsNode: JsonNode = refNode.findValue(components)
                ?: throw IllegalStateException("Specification part doesn't contain components: ref=$jsonRef")
            sourceComponentsNode.fields().forEach { entry ->
                val targetComponentsChildNode = findOrCreateObjectNode(entry.key, targetComponentsJsonNode)
                entry.value.fields().forEach { sourceChildEntry ->
                    val currentJsonPointer = JsonPointer.of(components, entry.key, sourceChildEntry.key)
                    if (jsonRef.pointer == currentJsonPointer || jsonRef.pointer.isEmpty) {
                        targetComponentsChildNode.set(sourceChildEntry.key, sourceChildEntry.value)
                        addSourceOfComponent(currentJsonPointer, jsonRef, collectedData.sourcesOfComponents)
                    }
                }
            }
        }
    }
}