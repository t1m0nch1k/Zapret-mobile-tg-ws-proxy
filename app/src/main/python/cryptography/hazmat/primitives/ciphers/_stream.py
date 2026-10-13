"""Pure-Python AES (FIPS-197) and an AES-CTR keystream.

Only what the vendored tg-ws-proxy core needs: AES-ECB block encryption for
128/192/256-bit keys, and a 128-bit big-endian counter stream compatible with
`cryptography.hazmat.primitives.ciphers` AES-CTR (verified byte-for-byte
against cryptography 49 on the build machine).
"""


def _xtime(a: int) -> int:
    a <<= 1
    return (a ^ 0x1B) & 0xFF if a & 0x100 else a


def _mul(a: int, b: int) -> int:
    p = 0
    for _ in range(8):
        if b & 1:
            p ^= a
        a = _xtime(a)
        b >>= 1
    return p


def _build_sbox():
    exp = [0] * 255
    log = [0] * 256
    x = 1
    for i in range(255):
        exp[i] = x
        log[x] = i
        x ^= _xtime(x)  # multiply by generator 3

    def affine(b: int) -> int:
        r = b
        for s in (1, 2, 3, 4):
            r ^= ((b << s) | (b >> (8 - s))) & 0xFF
        return r ^ 0x63

    sbox = [0] * 256
    for a in range(256):
        inv = 0 if a == 0 else exp[(255 - log[a]) % 255]
        sbox[a] = affine(inv)
    return sbox


SBOX = _build_sbox()
RCON = (0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1B, 0x36)
_MUL2 = [_mul(a, 2) for a in range(256)]
_MUL3 = [_mul(a, 3) for a in range(256)]
_SHIFT = (0, 5, 10, 15, 4, 9, 14, 3, 8, 13, 2, 7, 12, 1, 6, 11)


class _AES:
    """AES block cipher, encryption direction only (CTR needs no decryption)."""

    def __init__(self, key: bytes):
        nk = len(key) // 4
        self._rounds = {4: 10, 6: 12, 8: 14}[nk]
        self._rk = self._expand(key, nk)

    def _expand(self, key: bytes, nk: int):
        nr = self._rounds
        w = [list(key[4 * i:4 * i + 4]) for i in range(nk)]
        for i in range(nk, 4 * (nr + 1)):
            t = list(w[i - 1])
            if i % nk == 0:
                t = t[1:] + t[:1]
                t = [SBOX[b] for b in t]
                t[0] ^= RCON[i // nk - 1]
            elif nk > 6 and i % nk == 4:
                t = [SBOX[b] for b in t]
            w.append([w[i - nk][j] ^ t[j] for j in range(4)])
        return [
            bytes(b for word in w[4 * r:4 * r + 4] for b in word)
            for r in range(nr + 1)
        ]

    def encrypt_block(self, block: bytes) -> bytes:
        sbox = SBOX
        mul2 = _MUL2
        mul3 = _MUL3
        rk = self._rk[0]
        s = [block[i] ^ rk[i] for i in range(16)]
        for rnd in range(1, self._rounds):
            s = [sbox[b] for b in s]
            s = [s[i] for i in _SHIFT]
            for c in range(0, 16, 4):
                a0, a1, a2, a3 = s[c], s[c + 1], s[c + 2], s[c + 3]
                s[c] = mul2[a0] ^ mul3[a1] ^ a2 ^ a3
                s[c + 1] = a0 ^ mul2[a1] ^ mul3[a2] ^ a3
                s[c + 2] = a0 ^ a1 ^ mul2[a2] ^ mul3[a3]
                s[c + 3] = mul3[a0] ^ a1 ^ a2 ^ mul2[a3]
            rk = self._rk[rnd]
            s = [s[i] ^ rk[i] for i in range(16)]
        s = [sbox[b] for b in s]
        s = [s[i] for i in _SHIFT]
        rk = self._rk[self._rounds]
        return bytes([s[i] ^ rk[i] for i in range(16)])


class CTRStream:
    """`cryptography`-compatible AES-CTR encryptor/decryptor (update/finalize)."""

    __slots__ = ("_aes", "_counter", "_buffer")

    def __init__(self, key: bytes, nonce: bytes):
        self._aes = _AES(bytes(key))
        self._counter = int.from_bytes(bytes(nonce), "big")
        self._buffer = b""

    def update(self, data: bytes) -> bytes:
        aes = self._aes
        out = bytearray(len(data))
        pos = 0
        buf = self._buffer
        if buf:
            take = min(len(buf), len(data))
            for i in range(take):
                out[i] = data[i] ^ buf[i]
            self._buffer = buf[take:]
            pos = take
        n = len(data)
        counter = self._counter
        while pos < n:
            ks = aes.encrypt_block(counter.to_bytes(16, "big"))
            counter += 1
            take = 16 if n - pos >= 16 else n - pos
            for i in range(take):
                out[pos + i] = data[pos + i] ^ ks[i]
            if take < 16:
                self._buffer = ks[take:]
            pos += take
        self._counter = counter & ((1 << 128) - 1)
        return bytes(out)

    def finalize(self) -> bytes:
        return b""