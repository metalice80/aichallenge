# OpenAI Chat

Небольшое web-приложение на Spring Boot для отправки prompt в OpenAI Responses API. Пользователь вводит вопрос на web-странице, приложение обращается к OpenAI и показывает ответ модели.

История диалога не сохраняется: каждый запрос обрабатывается независимо.

## Возможности

- Web UI с полем `Prompt` и кнопкой `Send`.
- REST endpoint `POST /api/chat`.
- Интеграция с OpenAI Responses API.
- Настройка модели через `application.yml`.
- Валидация запросов и единый JSON-формат ошибок.
- Безопасное диагностическое логирование ошибок OpenAI без API-ключа и Authorization header.

## Технологии

- Java 21
- Spring Boot 3.5.6
- Gradle Kotlin DSL
- Spring MVC и Spring `RestClient`
- HTML, CSS и vanilla JavaScript
- JUnit 5, MockMvc и `MockRestServiceServer`

## Требования

- JDK 21
- действующий OpenAI API key
- доступ к OpenAI API из поддерживаемого региона

Устанавливать Gradle отдельно не требуется: в проекте есть Gradle Wrapper.

## Настройка API-ключа

API-ключ передаётся приложению только через environment variable `OPENAI_API_KEY`.

Не добавляйте ключ в `application.yml`, исходный код, документацию или Git. Файл `.env` исключён из Git, но Spring Boot не загружает его автоматически.

macOS/Linux:

```bash
export OPENAI_API_KEY="ваш-ключ"
```

Windows PowerShell:

```powershell
$env:OPENAI_API_KEY="ваш-ключ"
```

## Запуск приложения

macOS/Linux:

```bash
OPENAI_API_KEY="ваш-ключ" ./gradlew bootRun
```

Windows PowerShell:

```powershell
$env:OPENAI_API_KEY="ваш-ключ"
.\gradlew.bat bootRun
```

После запуска откройте web-страницу:

**http://localhost:8080/**

Введите текст в поле `Prompt` и нажмите `Send`. Web UI отправит запрос в `POST /api/chat` и покажет ответ модели.

## Настройка модели

Модель задаётся в `src/main/resources/application.yml`:

```yaml
openai:
  base-url: https://api.openai.com
  api-key: ${OPENAI_API_KEY:}
  model: gpt-4.1-mini
```

Чтобы использовать другую модель, измените значение `openai.model` и перезапустите приложение.

## REST API

Web UI использует тот же endpoint, который можно вызвать напрямую.

Запрос:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Расскажи кратко о Spring Boot"}'
```

Успешный ответ:

```json
{
  "response": "Ответ модели"
}
```

Поле `message` обязательно и не может быть пустым.

Возможные статусы ошибок:

| HTTP status | Причина |
|---:|---|
| 400 | Некорректный JSON или пустое сообщение |
| 502 | OpenAI вернул ошибку или недоступен |
| 503 | Не задан `OPENAI_API_KEY` |
| 500 | Непредвиденная внутренняя ошибка |

## Тестирование

Запустить unit tests:

```bash
./gradlew test
```

Выполнить чистую проверку:

```bash
./gradlew clean test
```

## Сборка исполняемого JAR

```bash
./gradlew bootJar
```

Запуск собранного приложения:

```bash
OPENAI_API_KEY="ваш-ключ" java -jar build/libs/openai-chat-0.0.1-SNAPSHOT.jar
```

## Структура проекта

```text
src/main/java/com/example/chat/
├── api/       REST API, DTO и обработка ошибок
├── config/    конфигурация OpenAI и RestClient
└── openai/    клиент OpenAI Responses API

src/main/resources/
├── application.yml
└── static/    index.html, styles.css и app.js
```

Подробное текущее состояние и архитектурные решения находятся в `docs/PROJECT_STATE.md` и `docs/ARCHITECTURE.md`.
