# Burmalda — фронтенд

Чистые HTML / CSS / JS, без сборки. Откройте `index.html` (он перенаправляет на `login.html`). Лучше через статический сервер: `python3 -m http.server`.

## Страницы
| Файл | Что это |
|---|---|
| `login.html` | Вход и регистрация (роли: Сотрудник / Бухгалтер) |
| `employee.html` | Кабинет сотрудника |
| `accountant.html` | Кабинет бухгалтера |

Разделы меню (в обоих кабинетах): Dashboard, **AI Burmalda** (чат с PayPal AI агентом), **Transactions** (только PayPal), **My wallet** (у бухгалтера **Finance**), **Tasks**, **Reports**.

- Dashboard: нажатие на виджет раскрывает его с подробностями, клик вне окна или Esc возвращает назад.
- Tasks сотрудника: доска задач. Tasks бухгалтера: кто сколько задач и проектов закрыл, сколько выплатить, кнопка «Pay» (PayPal Mass Pay) и AI-сводка.
- Светлая / тёмная / системная тема: Settings (шестерёнка) или кнопка на странице входа.

## Подключение бэкенда
Весь обмен с сервером идёт через объект `API` в **`js/api.js`**. Сейчас `CONFIG.USE_MOCK = true`: данные берутся из заглушек и `localStorage`. Для сервера поставьте `USE_MOCK = false` и поправьте `BASE_URL`. Токен уходит как `Authorization: Bearer <token>`. Форму ответов смотрите в `MOCK` / `MOCK2` в том же файле.

| Метод | Путь | Тело → ответ |
|---|---|---|
| POST | `/auth/login` | `{email,password}` → `{token,user}` |
| POST | `/auth/register` | `{name,email,password,role,department,company}` → `{user}` |
| POST | `/auth/logout` | — |
| GET | `/dashboard/employee` | → показатели + `details` для раскрытых виджетов |
| GET | `/dashboard/accountant` | → показатели + `details` для раскрытых виджетов |
| POST | `/ai/chat` | `{message,role}` → `{reply}` (PayPal AI агент) |
| GET | `/ai/suggestions?role=` | → `string[]` |
| GET | `/ai/history?role=` | → `string[]` |
| GET | `/ai/summary?role=` | → `{updated, points:[{tone,text}]}` |
| GET | `/paypal/transactions` | → `{balance, items:[{id,date,name,note,dir,amount,fee,status}]}` |
| GET | `/tasks/mine`, PATCH `/tasks/:id` | `{status:'todo'\|'progress'\|'done'}` |
| GET | `/wallet` | → баланс, история выплат, PayPal-аккаунт |
| POST | `/wallet/withdraw` | `{amount}` |
| GET | `/accounting/team` | → `{period, employees, projects}` |
| POST | `/accounting/pay` | `{ids}` → `{ok,batchId}` (Mass Pay) |
| GET | `/accounting/finance` | → баланс PayPal, бюджет, предстоящие платежи |
| POST | `/accounting/funds` | `{amount}` |
| POST | `/accounting/payment` | `{to,amount,note}` |
| GET | `/reports` | → список отчётов |
| POST | `/reports/:id/generate` | `{format,period}` |
| POST | `/team/members`, `/time/log` | см. `api.js` |

Демо-аккаунты (мок): `engineer@burmalda.com`, `accountant@burmalda.com`, пароль `demo1234`.

## Важно для бэкенда
- Мок хранит пароли открытым текстом в `localStorage`. На сервере нужны хеши (bcrypt/argon2).
- Роль и доступ проверяйте на сервере, проверка во фронтенде лишь для удобства.
- PayPal (выплаты, Mass Pay, AI агент) подключается на бэкенде, фронтенд только показывает данные.

## Структура
```
index.html        — редирект на login.html
css/style.css     — стили и переменные тем
js/theme.js       — переключение темы
js/api.js         — API и мок-данные   <-- сюда подключать бэкенд
js/common.js      — каркас, модалки, раскрытие виджетов, графики
js/pages.js       — общие страницы: AI Burmalda, Transactions, Reports
js/auth.js        — вход / регистрация
js/employee.js    — кабинет сотрудника
js/accountant.js  — кабинет бухгалтера
```
