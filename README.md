# ecp-emulator — stateful-эмулятор партнёрского API ЕЦП/ПроМед

Отдельное Spring Boot-приложение, реализующее контракт
[`medicina-test/ecp-api/openapi.yaml`](../medicina-test/ecp-api/openapi.yaml)
(13 методов фазы 1) + вендор-авторизацию `api/user/login` для клиента `EcpClient`.

В отличие от WireMock-мока (`medicina-test/mock-ecp`) эмулятор **stateful**: бронь занимает
бирку, отмена высвобождает, `TimeTableListbyPatient` и `TimeTableGrafStatus` отражают реальное
состояние. Тестовые данные совпадают с моком: Lpu `13` (ГКБ №1), специальности `3800` Терапия /
`3815` Кардиология, врачи `100`/`101`/`200`, пациент `900` Иванов, бирки с `5001`.

## Запуск

```bash
mvn spring-boot:run                       # http://localhost:9094
# как drop-in вместо mock-ecp (профиль ecp у medicina-test смотрит на :9090):
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=9090
```

### Docker

Jar собирается внутри образа (multi-stage) — на хосте нужны только Docker и git:

```bash
docker compose up --build -d              # http://localhost:9094
docker compose logs -f ecp                # ждать «Tomcat started on port 9094»
```

Требуется JDK 21. Копия контракта отдаётся самим приложением: `GET /openapi.yaml`.

**Swagger UI** — http://localhost:9092/swagger-ui.html (contract-first: рендерит `/openapi.yaml`;
сгенерированный `/v3/api-docs` в UI не используется). Первый server в спеке — сам эмулятор,
так что «Try it out» работает: подставьте любой непустой `apiKey`.

## Соглашения ЕЦП (реализованы)

- Параметры — **в query**, в т.ч. для POST/DELETE.
- Ответ всегда **HTTP 200** с конвертом `{error_code, error_msg, count, offset, data[]}`;
  ошибка — `error_code != 0`: `3` — нет обязательного параметра, `4` — ошибка параметров,
  бизнес-ошибки (`Пациент не найден в системе`, `Не найдена свободная бирка`,
  `Человек уже записан на данную бирку`) — код `6` с текстом в `error_msg`.
- Без/с неверным `apiKey` — **HTTP 401** без конверта.
- Даты `ГГГГ-ММ-ДД` / `ГГГГ-ММ-ДД чч:мм:сс`, без таймзоны.
- Статусы записи: `17` — записано, `12` — отменено. `TimeTable_id == TimeTableGraf_id`.

## Настройки (`application.yaml`, префикс `ecp`)

| Ключ | Дефолт | Смысл |
|---|---|---|
| `ecp.api-key` | `""` | ожидаемый apiKey; пусто — любой непустой |
| `ecp.require-session` | `false` | требовать `sess_id`: без него `error_code=1`, просрочен — `2` (проверка авто-релогина клиента) |
| `ecp.session-ttl-minutes` | `30` | TTL сессии |
| `ecp.schedule-days` | `14` | горизонт расписания: 8 бирок в день (09:00–11:20, шаг 20 мин) на врача |
| `ecp.person-fallback` | `true` | demo-режим: `api/Person` по неизвестному полису возвращает первого пациента (900), как WireMock-мок; `false` — строгий поиск |

## Проверка вручную

```bash
curl -s "http://localhost:9092/api/user/login?apiKey=k&login=vendor&password=x"
curl -s "http://localhost:9092/api/MedSpecOms/MedSpecOmsByMO?apiKey=k&Lpu_id=13&For_Record=1"
curl -s "http://localhost:9092/api/TimeTableGraf/TimeTableGrafFreeTime?apiKey=k&MedStaffFact_id=100&TimeTableGraf_begTime=$(date -d '+2 days' +%F)"
curl -s -X POST "http://localhost:9092/api/TimeTableGraf/TimeTableGrafWrite?apiKey=k&Person_id=900&TimeTableGraf_id=5017"
curl -s "http://localhost:9092/api/TimeTableListbyPatient?apiKey=k&Person_id=900"
curl -s -X DELETE "http://localhost:9092/api/TimeTable?apiKey=k&TimeTable_id=5017&TimeTableSource=Graf&FailCause=3"
```

## Тесты

`mvn test` — интеграционный флоу через MockMvc: 401 → login → специальности → врачи →
свободные даты/бирки → бронь → двойная бронь/чужая бронь/несуществующий пациент →
список → статус 17 → отмена → статус 12 → бирка снова свободна; плюс поиск пациента
(полис/ФИО) и справочные методы.
