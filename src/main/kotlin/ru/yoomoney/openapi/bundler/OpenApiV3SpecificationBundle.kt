package ru.yoomoney.openapi.bundler

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.github.fge.jackson.jsonpointer.JsonPointer
import com.github.fge.jsonschema.core.ref.JsonRef
import java.net.URI

/**
 * Класс для сборки спецификации из нескольких файлов
 * @param fileName путь до корневого файла спецификации
 */
class OpenApiV3SpecificationBundle(private val fileName: URI) {

    private class CollectedData {
        var remoteRefContent: MutableMap<JsonRef, JsonNode> = mutableMapOf()
        var sourcesOfComponents: MutableMap<JsonPointer, MutableSet<URI>> = mutableMapOf()
        var missingRefs: MutableSet<JsonRef> = mutableSetOf()
    }

    /**
     * Результат сборки спецификации
     *
     * @param bundledSpecification текст собранной спецификации
     * @param conflictingTypeNames перечень всех конфликтов в формате: указатель на тип - список источников, которые содержат тип с таким
     * же названием
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

        val content = loadContent(fileName)
        val rootNode = mapper.readTree(content)
        val baseJsonRef = JsonRef.fromURI(fileName)
        val collectedData = CollectedData()

        processObjectNode(rootNode, baseJsonRef, collectedData)
        fill(rootNode, baseJsonRef, collectedData)

        if (collectedData.missingRefs.isNotEmpty()) {
            throw IllegalStateException("Some refs wasn't resolved: refs=${collectedData.missingRefs}")
        }

        val conflictingNames: Map<String, Set<URI>> = collectedData.sourcesOfComponents
            .filter { entry -> entry.value.size > 1 }
            .mapKeys { entry -> entry.key.toString() }

        val bundledSpecification = if (conflictingNames.isEmpty())
            mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(rootNode)
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
                    processRefNode(entry.value, baseJsonRef, collectedData, rootNode as ObjectNode)
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
            rootNode.set<JsonNode>(exampleKey, TextNode.valueOf(stringValueMarker + currentNode.textValue()))
        }
    }

    private fun processRefNode(
        currentNode: JsonNode,
        baseJsonRef: JsonRef,
        collectedData: CollectedData,
        rootNode: ObjectNode
    ) {
        val jsonRef = baseJsonRef.resolve(createJsonRef(currentNode.asText()))
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
                val content: String = loadContent(jsonRef.toURI())
                val tree: JsonNode = mapper.readTree(content)

                // ищем ссылку в загруженном куске
                val refNode = jsonRef.pointer.get(tree)
                if (refNode == null) {
                    collectedData.missingRefs.add(jsonRef)
                    return
                }
                if (refNode.has(refKey) && refNode.fields().asSequence().toList().size == 1) {
                    val nextRef = createJsonRef(refNode.get(refKey).asText())
                    val nextRefTree: JsonNode = getTreeOrLoadToCache(nextRef, collectedData.remoteRefContent)
                    processObjectNode(nextRefTree, nextRef, collectedData)
                    rootNode.replace(refKey, TextNode.valueOf(locateRefOnCurrentDocument(nextRef)))
                } else {
                    collectedData.remoteRefContent[jsonRef] = tree
                    processObjectNode(tree, jsonRef, collectedData)
                    rootNode.replace(refKey, TextNode.valueOf(locateRefOnCurrentDocument(jsonRef)))
                }
            }
            else -> {
                // ссылка на домен, которая еще не обрабатывалась, просто меняем ссылку
                rootNode.replace(refKey, TextNode.valueOf(locateRefOnCurrentDocument(jsonRef)))
            }
        }
    }

    private fun fillComponents(
        sourceComponentsNode: JsonNode,
        jsonRef: JsonRef,
        collectedData: CollectedData,
        targetComponentsJsonNode: ObjectNode
    ) {
        sourceComponentsNode.fields().forEach { entry ->
            val targetComponentsChildNode = findOrCreateObjectNode(entry.key, targetComponentsJsonNode)
            entry.value.fields().forEach { sourceChildEntry ->
                val currentJsonPointer = JsonPointer.of(components, entry.key, sourceChildEntry.key)
                if (jsonRef.pointer == currentJsonPointer) {
                    targetComponentsChildNode.set<JsonNode>(sourceChildEntry.key, sourceChildEntry.value)
                    addSourceOfComponent(currentJsonPointer, jsonRef, collectedData.sourcesOfComponents)
                }
            }
        }
    }

    private fun fillPaths(
        sourceComponentsNode: JsonNode,
        jsonRef: JsonRef,
        collectedData: CollectedData,
        targetComponentsJsonNode: ObjectNode
    ) {
        sourceComponentsNode.fields().forEach { entry ->
            val targetComponentsChildNode = findOrCreateObjectNode(entry.key, targetComponentsJsonNode)
            entry.value.fields().forEach { sourceChildEntry ->
                val currentJsonPointer = JsonPointer.of(paths, entry.key, sourceChildEntry.key)
                if (currentJsonPointer.parent() == jsonRef.pointer) {
                    targetComponentsChildNode.remove(refKey)
                    targetComponentsChildNode.set<JsonNode>(sourceChildEntry.key, sourceChildEntry.value)
                    addSourceOfComponent(currentJsonPointer, jsonRef, collectedData.sourcesOfComponents)
                }
            }
            if (targetComponentsChildNode.size() == 0) {
                targetComponentsJsonNode.remove(entry.key)
            }
        }
    }

    private fun fill(
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

        val targetPathsJsonNode = findOrCreateObjectNode(paths, rootNode)
        targetPathsJsonNode.fields().forEach { entry ->
            entry.value.fields().forEach { sourceChildEntry ->
                val currentJsonPointer = JsonPointer.of(paths, entry.key, sourceChildEntry.key)
                addSourceOfComponent(currentJsonPointer, baseJsonRef, collectedData.sourcesOfComponents)
            }
        }

        collectedData.remoteRefContent.keys.forEach { jsonRef ->
            val refNode: JsonNode = getTreeOrLoadToCache(jsonRef, collectedData.remoteRefContent)
            // Наличие узла components в части документа с определением домена обязательно
            if (jsonRef.pointer.toString().contains(components) && refNode.has(components)) {
                val sourceComponentsNode: JsonNode = refNode.findValue(components)
                fillComponents(sourceComponentsNode, jsonRef, collectedData, targetComponentsJsonNode)
            }
            if (jsonRef.pointer.toString().contains(paths) && refNode.has(paths)) {
                val sourcePathsNode: JsonNode = refNode.findValue(paths)
                fillPaths(sourcePathsNode, jsonRef, collectedData, targetPathsJsonNode)
            }
        }
    }
}