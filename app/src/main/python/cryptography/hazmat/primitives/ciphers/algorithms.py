"""Shim: cipher algorithms (AES only)."""


class AES:
    name = "AES"

    def __init__(self, key):
        key = bytes(key)
        if len(key) not in (16, 24, 32):
            raise ValueError("AES key must be 16, 24 or 32 bytes long")
        self.key = key