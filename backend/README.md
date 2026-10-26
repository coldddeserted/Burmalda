# Burmalda — бэкенд

Spring Boot 3.5 · Java 21 · Maven · Spring Security + JWT · JPA/Hibernate · H2 (локально) / PostgreSQL (prod)

Сервер для фронта Burmalda: учёт времени и задач → автоматический расчёт зарплаты → выплата через PayPal Mass Pay → AI-ассистент по финансам.

## Быстрый старт
1. IDEA → **File → Open** → папка проекта (Maven подтянется сам). JDK — 21 (подойдёт и новее).
2. Запусти `BurmaldaApplication`.
3. Открой **http://localhost:8080** — фронт уже внутри (`src/main/resources/static`), работает с настоящим API.

Демо-логины (пароль у всех `demo1234`):

| Email | Роль |
|---|---|
| `accountant@burmalda.com` | бухгалтер (Anna Kowalska) |
| `engineer@burmalda.com` | сотрудник (Edgar Wilson) |
| `leon@`, `alexandra@`, `jack@`, `maria@`, `tomasz@`, `sofia@`, `daniel@burmalda.com` | сотрудники |

Полезное:
- http://localhost:8080/api/health — жив ли сервер и в каком режиме PayPal/AI
- http://localhost:8080/h2-console — БД (JDBC URL `jdbc:h2:file:./data/burmalda`, user `sa`, пароль пустой)
- Пересоздать демо-данные: остановить сервер, удалить папку `data/`, запустить снова. Даты в демо считаются от «сегодня».

## Переменные окружения (всё необязательно)
| Переменная | Зачем | Без неё |
|---|---|---|
| `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` | настоящие выплаты в PayPal **Sandbox** (developer.paypal.com → Apps & Credentials) | симуляция, batchId `SIM-…` |
| `AI_API_KEY`, `AI_MODEL` | AI Burmalda отвечает через LLM (Anthropic API) | ответы по правилам, но на реальных данных |
| `JWT_SECRET` | секрет подписи токенов (≥32 символа) | dev-секрет — **в проде обязателен свой** |
| `SEED_DEMO` | `false` — не создавать демо-данные | создаются |

В IDEA: Run → Edit Configurations → `BurmaldaApplication` → Environment variables.

Прод (PostgreSQL): профиль `prod` + `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`.

## Как считается зарплата
`PayrollService` — сердце проекта:

```
зарплата за месяц = часы из трекера × ставка в час
                  + $150 за каждый проект, закрытый в этом месяце, где у сотрудника были задачи
```

Бухгалтер видит расчёт на странице Tasks и жмёт **Pay** → одна пачка PayPal Mass Pay (комиссия 1% вместо 2% у поштучных переводов — разница видна в «Exchange & Fees Savings»). Сумму считает сервер, фронту не доверяем.

**AI Risk Alerts** (`AnomalyService`): дубликаты платежей (тот же получатель и сумма в пределах 3 дней) и платежи в 2+ раза выше среднего по получателю.

## API
Все пути с префиксом `/api`. Токен: `Authorization: Bearer <token>`. Ошибки: `{"message": "..."}`.

| Метод | Путь | Кто | Что |
|---|---|---|---|
| POST | `/auth/login` | все | `{email,password}` → `{token,user}` |
| POST | `/auth/register` | все | `{name,email,password,role,department,company}` → `{user}` |
| POST | `/auth/logout` | вошедший | отзывает токен |
| GET | `/auth/me` | вошедший | текущий пользователь |
| GET | `/dashboard/employee` | сотрудник | дашборд |
| GET | `/dashboard/accountant` | бухгалтер | дашборд |
| GET | `/tasks/mine` · PATCH `/tasks/{id}` | вошедший | задачи, `{status: todo/progress/done}` |
| POST | `/team/members` | вошедший | `{name,email,task}` — дать задачу коллеге |
| POST | `/time/log` | сотрудник | `{seconds}` — сохранить время из трекера |
| GET | `/wallet` · POST `/wallet/withdraw` | сотрудник | кошелёк, вывод `{amount}` |
| GET | `/accounting/team` | бухгалтер | кто сколько наработал и заработал |
| POST | `/accounting/pay` | бухгалтер | `{ids}` → Mass Pay |
| GET | `/accounting/finance` | бухгалтер | баланс, резервы, предстоящие платежи, бюджеты |
| POST | `/accounting/funds` · `/accounting/payment` | бухгалтер | пополнение `{amount}` · платёж `{to,amount,note}` |
| GET | `/accounting/approvals` · POST `/accounting/approvals/{id}/approve` | бухгалтер | ждущие одобрения платежи |
| GET | `/paypal/transactions` | вошедший | бухгалтеру — счёт компании, сотруднику — свой |
| GET | `/reports` · POST `/reports/{id}/generate` | вошедший | `{format,period: Month/Quarter/Year}` → CSV |
| POST | `/ai/chat` · GET `/ai/suggestions` `/ai/history` `/ai/summary` | вошедший | AI Burmalda |
| GET | `/search?q=` | вошедший | задачи, проекты, люди |

## Структура
```
com.burmalda
├── config/      SecurityConfig (роли, JWT), WebConfig (CORS), DataSeeder (демо)
├── security/    JwtService (HS256 руками, без библиотек), JwtAuthFilter, AuthUser
├── domain/      сущности: User, Project, Task, TimeLog, Payout, PaypalTransaction,
│                ScheduledPayment, DepartmentBudget, CompanyAccount, ComplianceItem, ChatMessage
├── repo/        Spring Data репозитории
├── service/     бизнес-логика (PayrollService — расчёт зарплаты; PaypalClient; LlmClient; AiService …)
├── web/         контроллеры + ApiExceptionHandler
│   └── dto/     Requests (входящие), *Views (ответы — ровно в формате фронта)
└── util/        Fmt (форматы дат/денег как у фронта), Ids, Errors
```

## Что поменяно во фронте (копия в `static/`)
- `js/api.js`: `USE_MOCK = false`; `BASE_URL` сам выбирает `/api` или `http://localhost:8080/api`;
  при 401 — выход на страницу логина; отчёты скачиваются CSV-файлом.
Если фронт открыт не с `localhost:8080` (например, из IDEA на `:63342`) — CORS уже разрешён в `application.yml`.

## Известные упрощения (для хакатона норм, для прода — доработать)
- Отозванные через logout токены хранятся в памяти — после рестарта сервера они снова валидны до истечения (12 ч).
- Вывод с PayPal на банк фиксируется только у нас: в PayPal API такого метода нет.
- Отчёты только в CSV (PDF/XLSX — следующий шаг).
- Трекер: фронт шлёт «сколько натикало», бэк по эвристике понимает, продолжение это или новая сессия.
