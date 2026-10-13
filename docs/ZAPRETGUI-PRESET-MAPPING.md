# Пресет zapret 2 «Default v1 (game filter)» → Zapret Mobile (ciadpi)

Источник: портативный пресет формата `zapretgui-portable-preset` (движок **winws2 / zapret 2**). Он использует lua-desync-движок, блобы (`--blob`), hostlist/ipset-маршрутизацию и фильтры WinDivert (`--wf-*`).

На Android без root движок работает как локальный SOCKS-прокси (ciadpi), а не как перехватчик пакетов, поэтому часть механики заменяется ближайшими аналогами. Перенос — в пресеты **«ZG: …»**.

## Таблица переноса

| Правило пресета (zapret 2) | Наш пресет (ciadpi) |
|---|---|
| `hostfakesplit:host=ozon.ru:tcp_ts=-1000:tcp_md5:repeats=4` — Discord (обновления/медиа/картинки/сайт), Telegram, ntc.party, «Мои сайты» | `ZG: Discord (hostfake ozon.ru)` = `-H":{list:discord}" -An -f-1 -S -t6 -n ozon.ru -a1` (аналогично Telegram / Cloudflare) |
| `hostfakesplit_multi:hosts=google.com,vimeo.com:tcp_ts=-1000:tcp_md5:repeats=2` — «YouTube · видео (googlevideo.com)», «Discord · сайт и приложение» | `ZG: YouTube видео (hostfake google/vimeo)` = `-H":{list:googlevideo}" -An -f-1 -S -t6 -n google.com -n vimeo.com -a1` |
| `multisplit:pos=1,host+2,sld+2,sld+5,sniext+1,sniext+2,endhost-2:seqovl=1` — «YouTube · сайт», «Бэкенд Google» | `ZG: YouTube сайт (multisplit)` = `-H":{list:youtube}" -An -s1 -s2+h -s2+s -s5+s -s-2+se -a1` |
| `fake:repeats=6:blob=fake_default_quic` — «YouTube · QUIC» (UDP 443) | `ZG: YouTube QUIC (UDP fake x6)` = `-V 443 -Ku -a6 -An` |
| `fake:blob=stun_pat:repeats=4` — «Голосовые звонки/чаты» (STUN/Discord) | `ZG: Discord медиа/голос (UDP x4)` = `-V 19294-19344 -V 50000-50100 -Ku -a4 -An` |
| `fake:blob=fake_stun_as_tls / tls_4pda / http_max:tcp_ts=-600000:repeats=6` — Cloudflare (3 варианта) | `ZG: Cloudflare (fake ozon.ru)` = `-H":{list:cloudflare}" -An -f-1 -S -t6 -n ozon.ru -a1` |
| `--lua-desync=pass` — «Исключения домены/айпи (RU сайты)» | Обход по спискам: `-H":{list:…}" -An` — десинк применяется только к доменам из списка |
| Game filter — UDP-фейки для игровых сервисов | `ZG: Игры UDP (fake x4)` = `-V 1024-65535 -Ku -a4 -An` |
| «Все сайты UDP (айпи)» + исключения RU/DNS | `ZG: Всё через списки (без -S)` = `-H":{list:flowseal}" -An -f-1 -t5 -n ozon.ru -a1` (область сужается списками) |

## Что не переносится (и чем заменено)

- lua-механики `syndata`, `fakedsplit`, `fakeddisorder`, `send:repeats`, `tls_multisplit_sni` — прямых аналогов нет; заменены комбинациями fake/split/disorder.
- Точные позиции сплита (`sld+2`, `sniext+1`, `endhost-2`) — в ciadpi доступны только смещения `+s`, `+h`, `+e`, `+m`; позиции приближены.
- `tcp_ts=-1000/-600000` (модификация TCP timestamp) и `ip_autottl` — недоступны; вместо них TTL фейка (`-t`) и md5sig (`-S`).
- Blobs `bin/*.bin` (готовые фейковые пакеты) — используются встроенные генераторы ciadpi + динамический SNI (`-n`).
- `--filter-l7=stun,discord` (фильтр по протоколу) — заменён фильтром по портам (`-V`).
- `ipset` по IP-адресам — заменён доменными списками приложения; для сервисов, работающих по IP, возможны расхождения.
- `--wf-tcp-out/--wf-udp-out/--wf-raw-part` — интерфейсные фильтры Windows/WinDivert; в прокси-модели не нужны.

## Проверка

- Синтаксис: `tools/validate-presets.sh` — все пресеты (FS + ZG) проходят парсер ciadpi (26/26 OK).
- Сеть: перебор стратегий на реальном телефоне через локальный SOCKS-прокси (результаты — в отчёте о сборке).