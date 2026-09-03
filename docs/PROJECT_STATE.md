# Состояние проекта

Актуально на 2026-09-02. Документ описывает фактическое состояние ветки `day1` и должен обновляться после существенных изменений.

## Назначение

Проект `openai-chat` предоставляет Web UI и REST API для отправки одного пользовательского сообщения в OpenAI Responses API и возврата сгенерированного текста. История диалога не хранится.

## Что реализовано

### Основа приложения

- Spring Boot 3.5.6 на Java 21.
- Gradle Kotlin DSL и Gradle Wrapper 8.14.3.
- Точка входа `com.example.chat.ChatApplication`.
- Настроены Spring MVC и Jakarta Bean Validation.

### REST API

Реализован `POST /api/chat`.

Запрос:

```json
{
  "message": "Hello"
}
```

Поле `message` обязательно и не может быть пустым или состоять только из пробелов.

Успешный ответ:

```json
{
  "response": "Hello back"
}
```

DTO:

- `ChatRequest(String message)`;
- `ChatResponse(String response)`;
- `ApiErrorResponse(Instant timestamp, int status, String error, String message, String path)`.

### Web UI

- Статическая страница доступна на `/` и поставляется тем же Spring Boot приложением.
- Реализована форма с полем `Prompt`, кнопкой `Send` и кнопкой `Clear`.
- Vanilla JavaScript отправляет same-origin `POST /api/chat` с JSON `{ \"message\": \"...\" }`.
- Во время запроса обе кнопки блокируются, а `Send` показывает состояние отправки.
- Ответ модели или безопасное сообщение API-ошибки выводится в live region без вставки HTML.
- Используются обычные `index.html`, `styles.css` и `app.js`; React и другие frontend-фреймворки отсутствуют.

### Интеграция с OpenAI

- Используется OpenAI Responses API: `POST {openai.base-url}/v1/responses`.
- HTTP-клиент — Spring `RestClient`; отдельный OpenAI SDK не используется.
- Исходящий JSON содержит текущую модель и строку `input`.
- Заголовок `Authorization: Bearer <key>` формируется перед запросом.
- Ответ разбирается из `output[].content[]`; элементы типа `output_text` объединяются через перевод строки.
- Значения по умолчанию в `application.yml`:
  - `openai.base-url`: `https://api.openai.com`;
  - `openai.model`: `gpt-4.1-mini`;
  - `openai.api-key`: `${OPENAI_API_KEY:}`.
- При пустом ключе реальный запрос не выполняется.

### Обработка ошибок

`GlobalExceptionHandler` возвращает единый JSON-формат:

- `400 Bad Request` — ошибка Bean Validation или malformed JSON;
- `502 Bad Gateway` — OpenAI вернул ошибочный HTTP-статус, произошла transport/serialization error либо ответ не содержит текста;
- `503 Service Unavailable` — не задан `OPENAI_API_KEY`;
- `500 Internal Server Error` — непредвиденная ошибка.

Тело внешней ошибки OpenAI и stack trace клиенту не передаются.

При ошибочном ответе OpenAI серверный WARN log содержит только выбранные диагностические поля: HTTP status, `error.message`, `error.type`, `error.code` и `x-request-id`, если заголовок присутствует. API-ключ, Authorization header и полный upstream body не логируются. Совпадения с текущим ключом, Bearer credentials и значениями формата `sk-*` редактируются как `[REDACTED]`; переводы строк удаляются для защиты целостности log entry.

### Тесты

Реализовано восемь unit tests:

- четыре MockMvc-теста для успешного `/api/chat`, пустого сообщения, malformed JSON и ошибки OpenAI;
- четыре теста `OpenAiResponsesClient` через `MockRestServiceServer`: исходящий URL/метод/заголовок/JSON, безопасное структурированное логирование ошибочного ответа, отсутствие `output_text`, отсутствие API-ключа.

Последняя выполненная проверка в текущей сессии:

```text
./gradlew test
BUILD SUCCESSFUL
```

Smoke-проверка реально запущенного приложения выполнена с локальным mock OpenAI: upstream `403` вернулся клиенту как структурированный `502`, а server log содержал status, безопасно отредактированный message, type, code и `x-request-id` без test credential. Ранее также проверялся сценарий запуска без `OPENAI_API_KEY`, возвращающий контролируемый `503`.

Web UI проверен в реальном headless Chromium с локальным success mock OpenAI: страница загрузилась на `/`, форма вызвала `POST /api/chat`, ответ появился в live region, а `Clear` очистил prompt и ответ, скрыл result и вернул focus в поле Prompt.

### Репозиторий

- Remote: `https://github.com/metalice80/aichallenge.git`.
- Ветка первой версии: `day1`.
- Исходная версия приложения опубликована коммитом `fb85e5b` (`Implement Spring Boot OpenAI chat API`).
- `.gitignore` исключает `.gradle/`, `build/`, `.idea/`, `*.iml` и `.env`.

## Текущая работа

Функциональная разработка сейчас не ведётся. Последнее существенное изменение — кнопка `Clear`, очищающая prompt и результат модели. Web UI использует существующий `POST /api/chat`; публичный REST-контракт не изменён.

## Что предстоит сделать

Согласованный продуктовый backlog отсутствует. Не считать перечисленные ниже ограничения утверждёнными задачами без запроса пользователя.

Для эксплуатации за пределами локального окружения потенциально потребуются отдельные решения по deployment, аутентификации, rate limiting, лимиту размера входа, явным HTTP-таймаутам и наблюдаемости. В текущем репозитории таких решений нет.

## Известные проблемы и ограничения

- Каждый запрос независим: история разговора и context window между вызовами не сохраняются.
- Нет базы данных, кэша, очереди, аккаунтов и пользовательских сессий.
- Нет retry/backoff и явной политики таймаутов для OpenAI; используется поведение `RestClient` и его базового request factory по умолчанию.
- Все ошибочные HTTP-статусы OpenAI преобразуются в `502`; семантика отдельных upstream-статусов, например `401` или `429`, наружу не сохраняется, кроме номера статуса в тексте сообщения.
- Нет live integration test с OpenAI; unit tests используют локальный mock HTTP server.
- Нет Dockerfile, Docker Compose, Nginx-конфигурации, VPS-конфигурации и CI/CD workflow.
- Нет health/readiness endpoints и Spring Boot Actuator.
- Локальный `.env` игнорируется Git, но Spring Boot сам его не загружает. Переменную `OPENAI_API_KEY` нужно экспортировать или передать процессу при запуске.

## Инструкции следующему агенту

1. Сначала прочитать `AGENTS.md`, этот файл и `docs/ARCHITECTURE.md`.
2. Проверить актуальную ветку и рабочее дерево; не перезаписывать пользовательские изменения.
3. Не читать и не коммитить `.env`.
4. Перед изменением публичных Java-символов найти все call sites.
5. Сохранять контракт `POST /api/chat`, если пользователь не потребовал его изменить.
6. После изменений запустить релевантные tests; для HTTP/runtime-изменений выполнить smoke-проверку.
7. Обновить этот снимок после существенных изменений; при архитектурном изменении также обновить `docs/ARCHITECTURE.md`.
