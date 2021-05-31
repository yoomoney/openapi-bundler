## [2.6.1](https://bitbucket.yamoney.ru/projects/BACKEND-TOOLS/repos/openapi-spec-bundler/pull-requests/7) (31-05-2021)

* Исправлен баг, из-за которого в `components` не добавлялись `headers` из внешнего файла,
если в исходной спецификации был определен:
```yaml
components:
response:
```
у которого были определены `headers` (см пример в `src/test/resources/com/yandex/money/openapi/normalization/test-headers/test-headers.yaml`)

## [2.6.0](https://bitbucket.yamoney.ru/projects/BACKEND-TOOLS/repos/openapi-spec-bundler/pull-requests/6) (19-02-2021)

* Переименование yamoney-kotlin-module-plugin в ru.yoomoney.gradle.plugins.kotlin-plugin

## [2.5.0](https://bitbucket.yamoney.ru/projects/BACKEND-TOOLS/repos/openapi-spec-bundler/pull-requests/5) (29-01-2021)

* Добавлена поддержка path параметров в ref ссылках
* Добавлен сбор и логирование ошибочных ссылок

## [2.4.0](https://bitbucket.yamoney.ru/projects/BACKEND-TOOLS/repos/openapi-spec-bundler/pull-requests/4) (21-01-2021)

* Добавлено форсированое добавление одинарных кавычек для примеров строковых значений

## [2.3.0](https://bitbucket.yamoney.ru/projects/BACKEND-TOOLS/repos/openapi-spec-bundler/pull-requests/3) (20-01-2021)

* Добавлена возможность указывать промежуточные узлы с сылками на внешние документы

## [2.2.0](https://bitbucket.yamoney.ru/projects/BACKEND-TOOLS/repos/openapi-spec-bundler/pull-requests/2) (18-01-2021)

* Подержка ref ссылок на элементы paths

## [2.1.0](https://bitbucket.yamoney.ru/projects/BACKEND-TOOLS/repos/openapi-spec-bundler/pull-requests/1) (08-07-2020)

* Обновлена версия gradle 6.0.1 -> 6.4.1.

## [2.0.0]() (12-02-2020)

* Добавил сборщик библиотеки типов
* Рефакторинг сборщика спецификации: конфликты возвращаются в виде "указатель на тип - список источников, которые содержат тип с таким
же названием"

## [1.1.0]() (07-02-2020)

* Сборка на java 11

## [1.0.0]() (30-01-2020)

* Первая версия