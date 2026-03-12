# Contact Manager

Нативное Android-приложение (Kotlin, Android 5.0+) для управления контактами.

## Текущая версия
`Contact Manager v0.11.2`

## Основные функции
- Локальное хранение контактов (SQLite)
- Поиск и фильтрация по группам
- Создание, редактирование, удаление
- Импорт из текста
- Google auth mock и рабочие задачи

## APK
- Основной файл: `artifacts/ContactManager-v0.11.2-debug.apk`
- Копия версии: `versions/Contact Manager v0.11.2/ContactManager-v0.11.2-debug.apk`

## Версии
- `versions/DEMO-v0` — нулевая DEMO-версия
- `versions/Contact Manager v0.11` — архивная версия по HTML v0.11 (без APK в текущей ветке)
- `versions/Contact Manager v0.11.2` — исправленный стабильный релиз

## Структура
- `app/src/main/java/.../data` — SQLite и репозиторий
- `app/src/main/java/.../ui` — экраны и адаптер
- `app/src/main/res` — ресурсы интерфейса
- `docs` — текстовые отчеты по фронтэнду и бэкенду
