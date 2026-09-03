# AGENTS.md

## Назначение проекта

`openai-chat` — небольшое Spring Boot web-приложение: браузерный интерфейс принимает prompt, REST API отправляет его в OpenAI Responses API и возвращает текст ответа. Текущая реализация не хранит историю диалога.

## Технологический стек

- Java 21.
- Spring Boot 3.5.6.
- Gradle 8.14.3 Wrapper, Kotlin DSL.
- Spring MVC и `RestClient` из Spring Framework.
- Jakarta Bean Validation.
- JUnit 5, AssertJ, Mockito, MockMvc и `MockRestServiceServer`.
- OpenAI Responses API без стороннего OpenAI SDK.
- Web UI на обычных HTML, CSS и JavaScript без frontend-фреймворка.

## Архитектурные принципы

- Сохранять приложение простым: контроллер отвечает только за HTTP-контракт, OpenAI-клиент — за внешний вызов и разбор ответа, `@RestControllerAdvice` — за единый формат ошибок.
- Использовать constructor injection; не применять field injection.
- Использовать Java records для простых неизменяемых DTO и configuration properties.
- Конфигурацию хранить вне кода. Модель и базовый URL задаются свойствами `openai.*`, API-ключ поступает только через `OPENAI_API_KEY`.
- Не добавлять новые слои, интерфейсы и зависимости без фактической необходимости.
- Не менять публичный HTTP-контракт без обновления тестов и архитектурной документации.
- Ошибки внешнего сервиса преобразовывать в контролируемые ошибки приложения; не передавать клиенту необработанные исключения и тела ошибок OpenAI.

## Структура проекта

- `README.md` — пользовательское описание, инструкции запуска, Web UI и REST API.
- `build.gradle.kts` — плагины, Java toolchain и зависимости.
- `src/main/resources/application.yml` — имя приложения и свойства OpenAI.
- `src/main/java/com/example/chat/ChatApplication.java` — точка входа.
- `src/main/java/com/example/chat/api/` — REST-контроллер, публичные DTO и централизованный обработчик ошибок.
- `src/main/java/com/example/chat/config/` — typed properties и создание `RestClient`.
- `src/main/java/com/example/chat/openai/` — интеграция с Responses API и доменные исключения интеграции.
- `src/main/resources/static/` — статический Web UI: `index.html`, `styles.css`, `app.js`.
- `src/test/java/com/example/chat/api/` — тесты HTTP-контракта через MockMvc.
- `src/test/java/com/example/chat/openai/` — тесты исходящих OpenAI-запросов и разбора ответов.
- `docs/PROJECT_STATE.md` — актуальный снимок состояния и ограничений.
- `docs/ARCHITECTURE.md` — текущая архитектура и принятые решения.

## Правила разработки

1. Перед изменением изучить существующий код и продолжать его соглашения, а не создавать параллельный стиль.
2. Поддерживать Java 21 и Gradle Kotlin DSL; запускать сборку через Wrapper (`./gradlew`), а не через локальный Gradle.
3. DTO входа валидировать на HTTP-границе. Ошибки должны сохранять единый JSON-формат `ApiErrorResponse`.
4. При изменении OpenAI-интеграции проверить сериализацию запроса, обязательные заголовки, HTTP-статусы и разбор массива `output[].content[]`.
5. Для нового наблюдаемого поведения добавлять узкий unit test. HTTP-контракт тестировать через MockMvc, внешний HTTP-вызов — через `MockRestServiceServer`; unit tests не должны обращаться к реальному OpenAI API.
6. Перед завершением существенного изменения выполнить как минимум `./gradlew test`. Изменения Web UI проверять в реальном браузере; для изменений запуска или HTTP-маршрутов дополнительно выполнить smoke-проверку запущенного приложения.
7. Не коммитить артефакты `.gradle/`, `build/`, IDE-файлы и `.env`.
8. После существенных изменений проекта агент обязан обновить `docs/PROJECT_STATE.md`. При изменении компонентов, потоков данных, публичного API, интеграций или иных архитектурных решений агент обязан обновить `docs/ARCHITECTURE.md`.

## Безопасность

- Никогда не записывать API-ключи, токены или другие секреты в исходный код, YAML, документацию, тестовые фикстуры, логи или Git.
- `OPENAI_API_KEY` читать из environment variable. Файл `.env` игнорируется Git, но Spring Boot не загружает его автоматически: перед запуском переменную нужно экспортировать или передать процессу другим безопасным способом.
- Не читать и не выводить содержимое локального `.env`, если задача явно не требует диагностики конкретной переменной.
- Не логировать заголовок `Authorization` и полный запрос пользователя без отдельного решения о политике приватности.
- Не возвращать клиенту stack trace, transport exception или необработанное тело ответа OpenAI.
- Сохранять серверную валидацию входа. Перед публичным deployment отдельно оценить аутентификацию, rate limiting, ограничения размера запроса, таймауты и сетевую политику.

## Команды

Требуется JDK 21.

```bash
# Сборка и unit tests
./gradlew clean test

# Только unit tests
./gradlew test

# Сборка исполняемого jar
./gradlew bootJar

# Запуск приложения
OPENAI_API_KEY="..." ./gradlew bootRun

# Запуск jar после bootJar
OPENAI_API_KEY="..." java -jar build/libs/openai-chat-0.0.1-SNAPSHOT.jar
```

Smoke-запрос при запущенном приложении:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Hello"}'
```

## Постоянные факты и ограничения

- Публичный endpoint: `POST /api/chat`.
- Web UI доступен на `/` и обращается к `POST /api/chat` через same-origin `fetch`.
- Текущая модель по умолчанию: `gpt-4.1-mini` в `application.yml`.
- Внешний endpoint: `POST /v1/responses` относительно `openai.base-url`.
- Успешный ответ собирается из элементов с типом `output_text`; несколько текстовых элементов объединяются переводом строки.
- Приложение stateless: контекст разговора, база данных, кэш и пользовательские сессии отсутствуют.
- Docker, Docker Compose, Nginx, CI/CD и конфигурация VPS в репозитории отсутствуют. Не описывать их как реализованные до появления соответствующих файлов.
- Рабочая ветка, использованная для первой версии, — `day1`; удалённый репозиторий — `https://github.com/metalice80/aichallenge.git`.
