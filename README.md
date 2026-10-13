# Zapret Mobile 2.1 — Android: обход DPI + ускорение Telegram в одном приложении

Android-приложение для **локального** обхода DPI-блокировок (без root и без внешних серверов).
Собрано на базе [ByeByeDPI-x-tg](https://github.com/EwenLoy/ByeByeDPI-x-tg) →
[ByeByeDPI](https://github.com/romanvht/ByeByeDPI) → [ByeDPI](https://github.com/hufrea/byedpi) + [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel).

Внутри **два независимых прокси**:

| Компонент | Порт | Назначение |
|---|---|---|
| **ByeDPI** (ciadpi) | `127.0.0.1:1080` | весь трафик через VPN: YouTube, Discord, Instagram и т.д. |
| **Telegram WS** (Rust-ядро `libtgwsproxy.so`) | `127.0.0.1:1082` | только Telegram: MTProto → WebSocket-шлюзы Telegram, при недоступности WS — прямое TCP |

Telegram WS работает автономно (трафик Telegram не проходит через ByeDPI).

## Что добавлено в этом форке (поверх ByeByeDPI-x-tg)

- **30 пресетов «FS: …» и «ZG: …»** — стратегии из [Flowseal/zapret-discord-youtube](https://github.com/Flowseal/zapret-discord-youtube)
  и перенос пресета **zapret 2 (winws2) «Default v1 (game filter)»** на движок ciadpi:
  fake TLS с подменой SNI (`ozon.ru`, `google.com`, `vimeo.com`), мультисплит по SNI/Host, UDP-fake для QUIC и голосовых портов Discord,
  маршрутизация по спискам доменов, Instagram/Facebook (fake ttl10).
  Пресеты автоматически закрепляются при первом запуске (доступны на главном экране и как ярлыки).
- Список доменов **«Flowseal»**; синхронизация списков доменов при старте приложения.
- Исправления, найденные в полевых тестах: исключён `-S` (TCP MD5SIG — на многих Android-ядрах полностью рвёт связь),
  исправлен порядок `-An` в маршрутизированных пресетах.
- `tools/windows-fix-symlinks.ps1` (обязательный шаг сборки на Windows), `tools/validate-presets.sh`
  (проверка пресетов парсером ciadpi), `docs/` — таблицы переноса пресетов.

## Быстрый старт

1. Установите APK, включите VPN (большая кнопка), примите системный запрос.
2. Нажмите чип со стратегией под кнопкой и выберите:
   - **«FS: Fake TLS (SNI google)»** — универсальная (Discord, часть YouTube);
   - **«ZG: YouTube/Instagram видео (multidisorder)»** — видео в YouTube и Instagram (Wi-Fi MTS);
   - **«ZG: Instagram/Facebook (fake ttl10)»** — Instagram/Facebook/Threads (мобильные сети);
   - для полного перебора — «Подбор» (авто-тест 87 стратегий).
3. **Telegram**: на главном экране включите **TG WS** (зелёная кнопка) → «Подключить прокси в Telegram» →
   в Telegram подтвердите прокси (127.0.0.1:1082, MTProto). Логи — по тапу на статус TG WS.
4. Настройки → Telegram: тумблер «Ускорить Telegram через WS» (включается/выключается без переподключения VPN).

## Сборка (Windows)

1. JDK 17+, Android SDK (platform 34+), **NDK 27.0.12077973**, CMake 3.22.1.
2. `git -c core.longpaths=true clone --recurse-submodules <repo>` в **короткий путь**.
3. `powershell -File tools/windows-fix-symlinks.ps1 -Root <путь>` — заменить симлинки-заголовки hev копиями
   (Windows-git выгружает их как текстовые файлы, иначе компиляция падает).
4. `local.properties`: `sdk.dir=C:/path/to/Android/Sdk`.
5. `gradlew.bat assembleDebug` (при капризной сети: `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`;
   при ошибке «Unable to determine HOST_OS»: `OS=Windows_NT`).

## Лицензии и кредиты

- Приложение: **GPL-3.0** (производное от ByeByeDPI). Telegram WS-модуль — по мотивам
  [tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy) (MIT, Flowseal); атрибуция — в `NOTICE`, `third-party/`.
- Кредиты: **EwenLoy** (ByeByeDPI-x-tg, Telegram WS-модуль), **romanvht** (ByeByeDPI), **hufrea** (byedpi),
  **bol-van** (zapret), **Flowseal** (пресеты и tg-ws-proxy), **hev** (hev-socks5-tunnel).
- Это неофициальная сборка; авторы оригинальных проектов к ней отношения не имеют.
- Инструмент предназначен для доступа к собственным сервисам и приватности; ответственность за использование — на пользователе.

---
Ниже — оригинальный README ByeByeDPI-x-tg.
Прошло много времени с момента последнего обновления. Сейчас я снова сажусь за этот проект. Спасибо за донаты и комментарии! Но чтобы я мог быстрее всё исправлять, пожалуйста, пишите мне в Telegram, а не на почту - @ewenloy. Возможно, в будущем появится отдельная группа или канал для этого приложения. Спасибо!

<div align="center">
  
# ByeByeDPI × TG

**Обход блокировок сайтов и прокси для Telegram — в одном приложении**

<br>

<a href="https://github.com/EwenLoy/ByeByeDPI-x-tg/releases/latest">
  <img src="https://img.shields.io/github/v/release/EwenLoy/ByeByeDPI-x-tg?style=for-the-badge&logo=github&label=Release&color=0aa1dd" alt="Release">
</a>
<img src="https://img.shields.io/badge/Android-SDK_21--36-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android SDK">
<img src="https://img.shields.io/badge/Rust-1.70%2B-000000?style=for-the-badge&logo=rust&logoColor=white" alt="Rust Version">
<img src="https://img.shields.io/badge/Kotlin-Native-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
<a href="https://github.com/EwenLoy/ByeByeDPI-x-tg/stargazers">
  <img src="https://img.shields.io/github/stars/EwenLoy/ByeByeDPI-x-tg?style=for-the-badge&logo=github&color=ffca28&labelColor=24292e" alt="Stars">
</a>

</div>
<br>

**ByeByeDPI × TG** — это два инструмента под одной крышей: **обход блокировок сайтов** на ядре `byedpi` и локальный **MTProto-прокси для Telegram**, который перенаправляет трафик мессенджера через защищённые CloudFlare WebSocket-соединения. YouTube открывается, Telegram оживает — нажатием одной кнопки каждое.

<!-- Скриншот главного экрана: сделай скрин приложения и вставь сюда
<img width="..." src="..." />
-->

---

## Возможности

- **Два переключателя вместо десяти настроек:** сайты и Telegram управляются отдельно, статус обоих виден на главном экране.
- **Интеграция с Telegram:** кнопка **«Применить в Telegram»** передаёт прокси в совместимые клиенты через `tg://proxy` (официальный, AyuGram, Plus Messenger, NekoGram и другие).
- **Проверка стратегий:** приложение само перебирает варианты обхода и показывает рабочие — как на реальных телефонах, так и «из коробки».
- **Диагностика TG WS одной кнопкой:** сервис, порт, нативное ядро и доступность серверов проверяются сами, с человеческим вердиктом.
- **Лог-вьюер:** живые логи обоих движков с фильтрами — видно, что происходит с подключением прямо сейчас.
- **Тёмная и светлая темы,** автозапуск при включении телефона и защита от выгрузки из памяти.

---

## Как это работает

```text
Telegram → 127.0.0.1:1082 (MTProto) → движок на Rust → WSS (через CloudFlare или напрямую) → Telegram DC
```

1. Приложение поднимает локальный MTProto-прокси средствами нативного движка на языке **Rust** (порт по умолчанию — `1082`, секрет генерируется сам).
2. Извлекает из пакета номер датацентра и устанавливает защищённое WebSocket-соединение с нужным DC, при необходимости проксируя через CloudFlare.
3. Держит пул соединений и переподключается при обрывах — чтобы в реальных сетях не отваливалось.

Сайтами в это же время занимается ядро [byedpi](https://github.com/hufrea/byedpi): desync-методы fake/split/oob и списки доменов настраиваются прямо в приложении.

---

## Быстрый старт

1. Скачайте актуальный APK со **[страницы релизов](https://github.com/EwenLoy/ByeByeDPI-x-tg/releases/latest)**.
2. Установите приложение (Android попросит разрешить установку — это нормально, приложение мимо Google Play).
3. Нажмите большую кнопку ⏻ — заработают сайты.
4. Для Telegram включите зелёный переключатель **TG WS**.
5. Нажмите **«Применить в Telegram»** и подтвердите прокси в мессенджере.
6. Не завелось? Меню теста → **«Проверить TG WS»** — приложение само покажет, где затык.

---

* **Краши и проблемы:** если приложение вылетает или не ставится — сохраните логи (меню ⋮ → «Логи TG WS») и приложите их к issue.

> [!NOTE]
> ### Отчёты об ошибках
> Приложение адаптировано под мобильные сети, однако фоновой работе могут мешать агрессивные оптимизации батареи — разрешите приложению их игнорировать при первом запуске.
>
> Мелкие ошибки в логах при нормально работающем прокси можно игнорировать. Перед созданием issue загляните в «Проверку стратегий» и «Проверить TG WS» — часто ответ уже там.

---

<details>
<summary>🔨 <b>Сборка из исходников</b></summary>

<br>

Каждый пуш собирается GitHub Actions ([вкладка Actions](https://github.com/EwenLoy/ByeByeDPI-x-tg/actions)), пуш тега `v*` создаёт релиз, коммит с `[skip ci]` сборку пропускает.

Локально:

```bash
git clone https://github.com/EwenLoy/ByeByeDPI-x-tg.git
cd ByeByeDPI-x-tg
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/
```

Нужны JDK 17 и Android SDK (NDK скачается сам). Собирать Rust **не нужно** — готовые `.so` движка уже лежат в `app/src/main/tglibs/`.

</details>

<details>
<summary>💙 <b>Цепочка форков и благодарности</b></summary>

<br>

**Сайты:** [hufrea/byedpi](https://github.com/hufrea/byedpi) (ядро) → [romanvht/ByeByeDPI](https://github.com/romanvht/ByeByeDPI) (Android-обёртка) → **этот форк** (текущая база — ByeByeDPI **1.8.2**, ядро byedpi `ba53229`)

**Telegram:** идея — [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy), Android-версия с Rust-движком — [amurcanov/tg-ws-proxy-android](https://github.com/amurcanov/tg-ws-proxy-android) → интегрировано в этот форк

Спасибо авторам оригинальных проектов 💙

</details>

## Лицензия

Этот форк распространяется под лицензией **GPLv3** (см. [LICENSE](LICENSE)).

- **ByeByeDPI для Android** и его разработчик [romanvht](https://github.com/romanvht) — спасибо за основу приложения 💙
- Ядро [byedpi](https://github.com/hufrea/byedpi) от [hufrea](https://github.com/hufrea)
- Оригинальный `tg-ws-proxy` от [Flowseal](https://github.com/Flowseal) доступен под **MIT**
- Android-порт с Rust-движком — [amurcanov/tg-ws-proxy-android](https://github.com/amurcanov/tg-ws-proxy-android)

---

<div align="center">

Помогло? Кинь ⭐ репозиторию и/или [поддержи автора](https://dalink.to/ewenloy) ❤️

</div>
