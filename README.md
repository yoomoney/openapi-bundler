[![Build Status](https://travis-ci.org/yoomoney/openapi-bundler.svg?branch=master)](https://travis-ci.org/yoomoney/openapi-bundler)
[![codecov](https://codecov.io/gh/yoomoney/openapi-bundler/branch/master/graph/badge.svg)](https://codecov.io/gh/yoomoney/openapi-bundler)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Javadoc](https://img.shields.io/badge/javadoc-latest-blue.svg)](https://yoomoney.github.io/openapi-bundler/)
[![Download](https://img.shields.io/badge/Download-latest-green.svg) ](https://search.maven.org/artifact/ru.yoomoney.tech/openapi-bundler)

# openapi-bundler

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
Добавьте зависимость в build.gradle:
```
repositories {
    mavenCentral()
}
dependencies {
    implementation 'ru.yoomoney.tech:openapi-bundler:3.0.0'
}
```

```java
import java.net.URI;

public class Application {
    public static void main(String[] args) {
        URI specificationURI = URI.create("file:path/to/openapi/specification.yaml");

        OpenApiV3SpecificationBundle bundler = new OpenApiV3SpecificationBundle(specificationURI);
        OpenApiV3SpecificationBundle.Result result = bundler.bundle();

        System.out.println("Errors: " + result.getConflictingTypeNames());
        System.out.println("Bundled specification: " + result.getBundledSpecification());
    }
}
```

Варианты организации спецификаций [смотрите в тестах](src/test/resources/ru/yoomoney/openapi/bundler). 