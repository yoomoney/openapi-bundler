# yamoney-openapi-spec-bundler

## Описание

Библиотека для сборки нескольких OpenApi v3 спецификаций в один файл.

Большие файлы спецификаций (>3000 строк) требуют разделения на более мелкие файлы, для читабельности и удобства редактирования.

Утилитарные классы этой библиотеки предоставлюят такой функционал.

На вход подается корневой файл описывающий основную часть спецификации, с перечнем всех endpoint.
В этом файле могут быть ссылки на другие файлы по средствам конструкции [```$ref```](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.2.md#referenceObject)

Результатом работы является спецификация без конструкции ```$ref```, содержимое всех файлов которые указаны в ```$ref``` заинлайнены в основной файл. 

У swagger есть свой инструмент объединении нескольких файлов в один [swagger-cli](https://www.npmjs.com/package/swagger-cli), но нам он не подходит 
т.к. при объединении он генерирует не валидные имена для объектов, это не позволит правильно генерировать код по таким спецификациям.  

# Пример использования:

```java
class Application {
    public static void main(String[] args) {
       OpenApiBundle.Result result = OpenApiBundle(rootFile.toURI()).bundle();
    }
}
```