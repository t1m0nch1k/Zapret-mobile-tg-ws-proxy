# Соответствие пресетов Flowseal ↔ Zapret Mobile (ciadpi)

## Как это устроено

Flowseal/zapret-discord-youtube — это набор `.bat`-файлов для Windows, внутри которых запускается `winws.exe` (движок zapret) с драйвером WinDivert: перехват и модификация пакетов на уровне ядра Windows.

В Android таких прав нет: движок приложения — ciadpi (ByeDPI), локальный SOCKS5-прокси. Он не перехватывает пакеты, а сам формирует исходящий поток и применяет приёмы десинхронизации до отправки в сокет. Поэтому переносятся **приёмы**, а не строки аргументов.

## Таблица переноса

| Приём | Flowseal (winws) | Пресет «FS: …» (ciadpi) |
|---|---|---|
| Fake TLS с SNI google | `--dpi-desync=fake --dpi-desync-repeats=6 --dpi-desync-fake-tls-mod=rnd,dupsid,sni=www.google.com --dpi-desync-fooling=ts` | `FS: Fake TLS (SNI google)` — `-f-1 -t6 -n www.google.com -Qr -a1` |
| Fake TLS + авто-триггеры | те же правила, подбор вручную через `service.bat` | `FS: Fake TLS AUTO (SNI google)` — `-f-1 -t6 -n www.google.com -Qr -a1 -At,r,s -f-1 -t4 -s1+s -a1` |
| hostfakesplit | `--dpi-desync=hostfakesplit --dpi-desync-hostfakesplit-mod=host=www.google.com` | `FS: Simple Fake (ttl 8)` — `-f-1 -t8 -n www.google.com -s1+s -a1` |
| multisplit по SNI | `--dpi-desync=multisplit --dpi-desync-split-pos=1 --dpi-desync-split-seqovl=681 --dpi-desync-split-seqovl-pattern=tls_clienthello_www_google_com.bin` | `FS: Multisplit (SNI x5)` — `-s1:5+sm -d1 -s3+s -a1` |
| multidisorder | `--dpi-desync=fake,multidisorder --dpi-desync-split-pos=1,midsld` | `FS: Multidisorder (SNI)` — `-d1+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -a1` |
| OOB-байт в SNI | (прямого аналога нет) | `FS: OOB in SNI` — `-o1+s -d3+s -a1` |
| Disorder + OOB | (прямого аналога нет) | `FS: DISOOB + Fake` — `-q1+s -f-1 -t6 -n www.google.com -Qr -a1` |
| Разрез TLS-записи | (прямого аналога нет) | `FS: TLS record split + split` — `-r1+s -s1:3+sm -a1` |
| fake + seqovl (EXP-вариант) | `--dpi-desync=fake,multisplit --dpi-desync-split-seqovl=480/664` | `FS: Fake + tlsrec + split (EXP)` — `-f-1 -t5 -n www.google.com -Qr -r1+s -s1:3+sm -a1` |
| fooling=badseq (защита фейка) | `--dpi-desync-fooling=badseq` | `FS: MD5SIG fake (Linux/Android)` — `-f-1 -t6 -S -n www.google.com -Qr -a1` (md5sig может быть недоступен в некоторых сборках ядра) |
| Повторные отправки/потери | `--dpi-desync-fooling=ts` | `FS: Drop SACK + disorder` — `-Y -d1+s -s3+s -a1` |
| Fake QUIC/UDP | `--dpi-desync=fake --dpi-desync-repeats=6 --dpi-desync-fake-quic=…bin` | `FS: QUIC fake x6 + Fake TLS` — `-f-1 -t6 -n www.google.com -Qr -a1 -Ku -a6` |
| Только TCP (QUIC отключён) | (не используется) | `FS: TCP-only (no QUIC)` — `-U -f-1 -t6 -n www.google.com -Qr -a1` |
| Discord media (alt-порты) | `--filter-tcp=2053,2083,2087,2096,8443 --hostlist-domains=discord.media` | `FS: Discord media (alt ports)` — `-V 2053 -V 2083 -V 2087 -V 2096 -V 8443 -f-1 -t6 -n www.google.com -Qr -a1` |
| Discord voice (UDP-порты) | `--filter-udp=19294-19344,50000-50100 --filter-l7=discord,stun` | `FS: Discord voice UDP (ports)` — `-V 19294-19344 -V 50000-50100 -Ku -a6` |
| Область по спискам доменов | `--hostlist=list-general.txt --hostlist-exclude=…` | `FS: Only Discord/YouTube/Google (list)` — `-H":{list:flowseal}" -An …` |
| Область: только YouTube | `--hostlist=list-google.txt` | `FS: Only YouTube (list) split` — `-H":{list:youtube}" -An -s1:3+sm -d1 -s3+s -a1` |

Списки доменов внутри пресетов подставляются из «Списков доменов» приложения (подстановка `{list:имя}`), список «Flowseal» добавлен в этом форке.

## Что не переносится (и почему)

- `--dpi-desync-split-seqovl` / `split-seqovl-pattern` — перекрытие sequence numbers пакетами-паттернами требует уровня пакетов; заменено мультисплитом и `tlsrec`.
- `--ip-id=zero` — манипуляция IP ID недоступна без raw-сокетов.
- `--dpi-desync-fooling=badseq|badsum` — подделка sequence/checksum недоступна; близкие аналоги: TTL фейка (`-t`), md5sig (`-S`), drop-sack (`-Y`).
- `--dpi-desync-fake-quic=*.bin` — инъекция заранее подготовленного QUIC ClientHello; в ciadpi есть только `-a/--udp-fake N` (N фейковых датаграмм).
- `--ipset` / `--ipset-exclude` — список доменов приложения работает по именам, не по IP; для сужения области используйте доменные списки (`-H`) и порты (`-V`).
- Game Filter (игровые UDP/TCP-порты) — не переносился; при необходимости добавьте порты через `-V`.

## Рекомендации

- Начните с `FS: Fake TLS (SNI google)`. Если соединения рвутся/таймаутятся — `FS: Fake TLS AUTO (SNI google)` (переключение по триггерам `torst/redirect/ssl_err`).
- Блокируется только YouTube/Discord — используйте пресеты «Only … (list)».
- Полный перебор: «Тест стратегий» (17 FS-стратегий стоят первыми в списке) — дождитесь отчёта и выберите стратегию с максимальным числом успешных проверок.
- `-S` (md5sig) поддерживается не всеми ядрами — если пресет не стартует, берите вариант без `-S`.

## Проверка аргументов

`tools/validate-presets.sh` (Linux/WSL) поднимает ciadpi с каждым пресетом и проверяет, что парсер принимает аргументы:

```
bash tools/validate-presets.sh
```

## Примечание (проверено на реальном устройстве)

- `-S` (TCP MD5SIG) на Android часто недоступен: на тестовом телефоне пресет с `-S` полностью рвал связь (все соединения 000), поэтому из поставляемых пресетов он исключён. Если хотите проверить на своём устройстве — добавьте `-S` вручную в команду.
