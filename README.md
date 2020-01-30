# yamoney-library-project-plugin

## Описание

Библиотека для сборки нескольких OpenApi v3 спецификаций в один файл.

## Использование

Пример использования:

```java
class Application {
    public static void main(String[] args) {
       OpenApiBundle.Result result = OpenApiBundle(fileName.toURI()).bundle();
    }
}
```
