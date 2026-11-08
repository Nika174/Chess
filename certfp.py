import hashlib
import struct
import sys

MAGIC = b"APK Sig Block 42"


def u32(b, o):
    return struct.unpack_from("<I", b, o)[0]


def u64(b, o):
    return struct.unpack_from("<Q", b, o)[0]


def v2_cert(path):
    data = open(path, "rb").read()
    # the signing block sits right before the central directory; magic is the last 16 bytes of it
    idx = data.rfind(MAGIC)
    if idx < 0:
        return None
    end = idx + len(MAGIC)
    block_size = u64(data, end - 24)
    # pairs start right after the leading uint64 size field
    o = end - 8 - block_size + 8
    while o < end - 24:
        pair_len = u64(data, o)
        pid = u32(data, o + 8)
        if pid == 0x7109871A:  # v2 signature scheme
            value = data[o + 12:o + 8 + pair_len]
            # signers: uint32 len, then first signer
            s_len = u32(value, 0)
            signer = value[4:4 + s_len]
            signed_len = u32(signer, 0)
            signed = signer[4:4 + signed_len]
            # signed data: digests, certificates, attributes
            d_len = u32(signed, 0)
            off = 4 + d_len
            c_len = u32(signed, off)
            certs = signed[off + 4:off + 4 + c_len]
            first_len = u32(certs, 0)
            cert = certs[4:4 + first_len]
            return hashlib.sha256(cert).hexdigest()
        o += 8 + pair_len
    return None


for p in sys.argv[1:]:
    print(p, "->", v2_cert(p))
