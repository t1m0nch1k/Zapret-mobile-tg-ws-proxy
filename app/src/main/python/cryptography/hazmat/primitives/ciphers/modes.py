"""Shim: cipher modes (CTR only)."""


class CTR:
    def __init__(self, nonce):
        nonce = bytes(nonce)
        if len(nonce) != 16:
            raise ValueError("CTR nonce must be 16 bytes long")
        self.nonce = nonce