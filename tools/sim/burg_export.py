#!/usr/bin/env python3
"""Burg-Kopie fuer die Team-Sim: schneidet die Bloecke um den Core aus den Weltdateien.

Aufruf:
  burg_export.py <region-ordner> --core X Y Z [--radius 4] [--unten 24] [--oben 56] [-o tools/sim/burg.json.gz]
  burg_export.py <region-ordner> --state <welt>/nightsiege-state.json [...]

<region-ordner> ist der Ordner mit den r.X.Z.mca-Dateien der Oberwelt (auf dem Server
playtest/region). Mit --state wird der erste Core aus dem Spielstand genommen.

Geschrieben wird nur, was SimCastle.placeCopy liest: {"core":[x,y,z],"chunks":[{"x","z","s":[{"y","p","d"}]}]}
- p: Palette als "minecraft:block[prop=wert,...]"
- d: die gepackten Indizes der Section (Long-Array, big-endian, Base64), wie in den Weltdateien
Nur Bloecke: keine Truhen-Inhalte, keine Schilder-Texte, keine Wesen, keine Spieler.
Liest die Weltdateien nur, aendert nichts.
"""
import argparse
import base64
import gzip
import io
import json
import os
import struct
import sys
import zlib

# ------------------------------------------------------------------ NBT (nur lesen)

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Reader:
    def __init__(self, data):
        self.b = data
        self.i = 0

    def take(self, n):
        v = self.b[self.i:self.i + n]
        if len(v) < n:
            raise ValueError("NBT zu kurz")
        self.i += n
        return v

    def u(self, fmt):
        size = struct.calcsize(fmt)
        return struct.unpack(">" + fmt, self.take(size))[0]

    def string(self):
        n = self.u("H")
        return self.take(n).decode("utf-8", errors="replace")

    def payload(self, tag):
        if tag == BYTE:
            return self.u("b")
        if tag == SHORT:
            return self.u("h")
        if tag == INT:
            return self.u("i")
        if tag == LONG:
            return self.u("q")
        if tag == FLOAT:
            return self.u("f")
        if tag == DOUBLE:
            return self.u("d")
        if tag == BYTE_ARRAY:
            return self.take(self.u("i"))
        if tag == STRING:
            return self.string()
        if tag == LIST:
            inner = self.u("b")
            n = self.u("i")
            return [self.payload(inner) for _ in range(n)]
        if tag == COMPOUND:
            out = {}
            while True:
                t = self.u("b")
                if t == END:
                    return out
                name = self.string()
                out[name] = self.payload(t)
        if tag == INT_ARRAY:
            n = self.u("i")
            return list(struct.unpack(">%di" % n, self.take(4 * n)))
        if tag == LONG_ARRAY:
            n = self.u("i")
            # roh behalten: genau diese Bytes (big-endian) erwartet SimCastle.unpack
            return RawLongs(self.take(8 * n))
        raise ValueError("unbekannter NBT-Tag %d" % tag)

    def root(self):
        t = self.u("b")
        if t != COMPOUND:
            raise ValueError("NBT ohne Compound")
        self.string()
        return self.payload(COMPOUND)


class RawLongs(bytes):
    """Ein Long-Array als rohe big-endian Bytes."""


# ------------------------------------------------------------------ Region-Dateien

def read_chunk(region_dir, cx, cz):
    """Das NBT eines Chunks oder None (nicht erzeugt)."""
    rx, rz = cx >> 5, cz >> 5
    path = os.path.join(region_dir, "r.%d.%d.mca" % (rx, rz))
    if not os.path.exists(path):
        return None
    with open(path, "rb") as f:
        header = f.read(4096)
        if len(header) < 4096:
            return None
        k = 4 * ((cx & 31) + (cz & 31) * 32)
        off = int.from_bytes(header[k:k + 3], "big")
        if off == 0:
            return None
        f.seek(off * 4096)
        length = int.from_bytes(f.read(4), "big")
        kind = f.read(1)[0]
        data = f.read(length - 1)
    external = kind & 0x80
    kind &= 0x7F
    if external:
        ext = os.path.join(region_dir, "c.%d.%d.mcc" % (cx, cz))
        if not os.path.exists(ext):
            return None
        data = open(ext, "rb").read()
    if kind == 1:
        raw = gzip.decompress(data)
    elif kind == 2:
        raw = zlib.decompress(data)
    elif kind == 3:
        raw = data
    elif kind == 4:
        try:
            import lz4.frame  # type: ignore
        except ImportError:
            sys.exit("Chunk %d,%d ist mit LZ4 gepackt: bitte 'pip install lz4' (--break-system-packages)" % (cx, cz))
        raw = lz4.frame.decompress(data)
    else:
        raise ValueError("unbekannte Kompression %d in Chunk %d,%d" % (kind, cx, cz))
    return Reader(raw).root()


def state_text(entry):
    name = entry.get("Name", "minecraft:air")
    props = entry.get("Properties") or {}
    if not props:
        return name
    return name + "[" + ",".join("%s=%s" % (k, props[k]) for k in sorted(props)) + "]"


def export(region_dir, core, radius, below, above):
    x, y, z = core
    ccx, ccz = x >> 4, z >> 4
    low_sec = (y - below) >> 4
    high_sec = (y + above) >> 4
    chunks = []
    blocks = 0
    missing = 0
    for cx in range(ccx - radius, ccx + radius + 1):
        for cz in range(ccz - radius, ccz + radius + 1):
            nbt = read_chunk(region_dir, cx, cz)
            if nbt is None:
                missing += 1
                continue
            nbt = nbt.get("Level", nbt)   # sehr alte Welten hatten "Level"
            status = str(nbt.get("Status", "full"))
            if not status.endswith("full"):
                missing += 1
                continue
            secs = []
            for sec in nbt.get("sections", nbt.get("Sections", [])):
                sy = sec.get("Y")
                if sy is None or sy < low_sec or sy > high_sec:
                    continue
                bs = sec.get("block_states")
                if not bs:
                    continue
                palette = [state_text(p) for p in bs.get("palette", [])]
                if not palette:
                    continue
                entry = {"y": int(sy), "p": palette}
                data = bs.get("data")
                if data and len(palette) > 1:
                    entry["d"] = base64.b64encode(bytes(data)).decode("ascii")
                if palette == ["minecraft:air"]:
                    pass   # leere Luft-Section trotzdem setzen: sie raeumt die Sim-Welt an der Stelle
                secs.append(entry)
                blocks += 4096
            if secs:
                chunks.append({"x": cx, "z": cz, "s": secs})
    return {"core": [x, y, z], "chunks": chunks, "quelle": "burg_export.py",
            "radiusChunks": radius, "unten": below, "oben": above}, blocks, missing


def core_from_state(path):
    state = json.load(open(path, encoding="utf-8"))
    cores = state.get("cores") or []
    if not cores:
        sys.exit("Kein Core im Spielstand %s" % path)
    c = cores[0]
    return int(c["x"]), int(c["y"]), int(c["z"])


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("region")
    ap.add_argument("--core", nargs=3, type=int, metavar=("X", "Y", "Z"))
    ap.add_argument("--state")
    ap.add_argument("--radius", type=int, default=4, help="Chunks um den Core (4 = 9 x 9 Chunks, 144 Bloecke)")
    ap.add_argument("--unten", type=int, default=24, help="Bloecke unter dem Core")
    ap.add_argument("--oben", type=int, default=56, help="Bloecke ueber dem Core")
    ap.add_argument("-o", "--out", default="tools/sim/burg.json.gz")
    a = ap.parse_args()
    if a.core:
        core = tuple(a.core)
    elif a.state:
        core = core_from_state(a.state)
    else:
        sys.exit("--core X Y Z oder --state angeben")
    data, blocks, missing = export(a.region, core, a.radius, a.unten, a.oben)
    if not data["chunks"]:
        sys.exit("Keine Chunks gefunden – stimmt der Region-Ordner? (Core %s)" % (core,))
    raw = json.dumps(data, separators=(",", ":")).encode("utf-8")
    with gzip.open(a.out, "wb", compresslevel=9) as f:
        f.write(raw)
    print("Burg-Kopie: Core %s, %d Chunks (%d fehlen), %d Bloecke, %d KB -> %s"
          % (core, len(data["chunks"]), missing, blocks, os.path.getsize(a.out) // 1024, a.out))


if __name__ == "__main__":
    main()
