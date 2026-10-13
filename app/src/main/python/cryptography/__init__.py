"""Minimal local stand-in for the `cryptography` package.

Zapret Mobile needs only a tiny slice of the real API: AES-CTR, as used by the
vendored tg-ws-proxy core (MIT, Flowseal) for MTProto obfuscated2 parsing and
for splitting client TCP data into transport packets.

The real package cannot be installed at build time here (pypi.org is not
reachable), so this shim provides the used subset in pure Python.
"""
__version__ = "0.0.0-shim"