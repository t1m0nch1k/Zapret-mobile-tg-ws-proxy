"""Zapret Mobile -> tg-ws-proxy runner.

Thin wrapper around the vendored `proxy.tg_ws_proxy` core (MIT, Flowseal:
https://github.com/Flowseal/tg-ws-proxy). Exposes start/stop/status for the
Android side (Chaquopy) and runs the asyncio proxy in a daemon thread.

The core file `proxy/tg_ws_proxy.py` is kept byte-identical to upstream; the
MIT license text is in `proxy/LICENSE.txt`.
"""
from __future__ import annotations

import asyncio
import logging
import threading
import time

# The Android service shows these defaults; overridable from Kotlin.
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 1081
# Every DC is mapped to the Telegram IP that is reachable on this network
# (149.154.167.220). The bridge itself only needs the DC number to pick the
# kws<dc>.web.telegram.org endpoint; the IP is used as the pool/fallback target.
# Passing all DCs matters: the core falls back to plain TCP passthrough (which
# is blocked here) for any DC that is missing from the list.
DEFAULT_DC_IPS = [
    "1:149.154.167.220", "2:149.154.167.220", "3:149.154.167.220",
    "4:149.154.167.220", "5:149.154.167.220", "203:149.154.167.220",
]

# Extra DC IPs seen on this network but missing from the core's builtin
# _IP_TO_DC table (patched in at start()).
EXTRA_IP_TO_DC = {
    "91.108.56.118": (5, False),
}

_lock = threading.Lock()
_state = {
    "running": False,
    "host": DEFAULT_HOST,
    "port": DEFAULT_PORT,
    "error": "",
    "started_at": 0.0,
}
_thread: threading.Thread | None = None
_loop: asyncio.AbstractEventLoop | None = None
_stop_event: asyncio.Event | None = None


def _configure_logging() -> None:
    # sys.stdout/sys.stderr are redirected to logcat by Chaquopy
    # (tags: python.stdout / python.stderr).
    root = logging.getLogger()
    if not root.handlers:
        handler = logging.StreamHandler()
        handler.setFormatter(
            logging.Formatter("%(asctime)s %(levelname)s %(name)s: %(message)s"))
        root.addHandler(handler)
    if root.level > logging.INFO:
        root.setLevel(logging.INFO)


def start(port: int = DEFAULT_PORT, dc_ips: list | None = None,
          host: str = DEFAULT_HOST) -> str:
    """Start the SOCKS5 -> WebSocket bridge. Returns a short status string."""
    global _thread, _loop, _stop_event

    with _lock:
        if _state["running"]:
            return "already running on %s:%d" % (_state["host"], _state["port"])

        _configure_logging()
        from proxy import tg_ws_proxy as core  # noqa: WPS433
        parse_dc_ip_list, _run = core.parse_dc_ip_list, core._run

        # Keep upstream file untouched: extend its IP->DC table from here.
        for ip, dc_info in EXTRA_IP_TO_DC.items():
            core._IP_TO_DC.setdefault(ip, dc_info)

        dc_list = [str(x) for x in dc_ips] if dc_ips else list(DEFAULT_DC_IPS)
        try:
            dc_opt = parse_dc_ip_list(dc_list)
        except ValueError as exc:
            _state["error"] = str(exc)
            return "error: %s" % exc

        _state.update(running=True, host=host, port=int(port), error="",
                      started_at=time.time())
        ready = threading.Event()

        def _worker() -> None:
            global _loop, _stop_event
            try:
                _loop = asyncio.new_event_loop()
                asyncio.set_event_loop(_loop)
                _stop_event = asyncio.Event()
                ready.set()
                _loop.run_until_complete(_run(int(port), dc_opt, _stop_event, host))
            except Exception as exc:  # keep the error visible to the UI
                _state["error"] = "%s: %s" % (type(exc).__name__, exc)
                logging.getLogger("zm.runner").exception("bridge crashed")
            finally:
                _state["running"] = False
                try:
                    if _loop is not None:
                        _loop.close()
                except Exception:
                    pass
                _loop = None

        _thread = threading.Thread(target=_worker, name="zm-socks-bridge",
                                   daemon=True)
        _thread.start()
        ready.wait(timeout=5)

    return "started on %s:%d" % (host, int(port))


def stop() -> str:
    """Ask the bridge to stop and wait briefly for the thread to exit."""
    global _loop, _stop_event

    with _lock:
        if not _state["running"]:
            return "not running"
        try:
            if _loop is not None and _stop_event is not None:
                _loop.call_soon_threadsafe(_stop_event.set)
        except Exception as exc:
            _state["error"] = str(exc)
        thread = _thread

    if thread is not None:
        thread.join(timeout=5)

    with _lock:
        still = thread is not None and thread.is_alive()
        _state["running"] = still
        return "stop timeout" if still else "stopped"


def status() -> str:
    with _lock:
        state = dict(_state)
    if state["running"]:
        return "running %s:%d (%.0f s)" % (
            state["host"], state["port"], time.time() - state["started_at"])
    if state["error"]:
        return "error: " + state["error"]
    return "stopped"