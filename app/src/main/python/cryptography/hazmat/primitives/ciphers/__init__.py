"""Shim: Cipher + re-exported algorithms/modes, matching the real package layout."""

from . import algorithms, modes  # noqa: F401
from ._stream import CTRStream


class Cipher:
    """Drop-in for `Cipher(AES(key), CTR(nonce))` with `.encryptor()/.decryptor()`.

    Only AES-CTR is implemented; that is everything the tg-ws-proxy core uses.
    """

    def __init__(self, algorithm, mode):
        if not isinstance(algorithm, algorithms.AES):
            raise TypeError("cryptography shim supports algorithms.AES only")
        if not isinstance(mode, modes.CTR):
            raise TypeError("cryptography shim supports modes.CTR only")
        self._key = algorithm.key
        self._mode = mode

    def encryptor(self):
        return CTRStream(self._key, self._mode.nonce)

    def decryptor(self):
        # CTR is symmetric: encryption and decryption are the same operation.
        return CTRStream(self._key, self._mode.nonce)