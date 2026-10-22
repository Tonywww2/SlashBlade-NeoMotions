"""Read local Wilds hunter-note MSG.23 names; raw text stays in .local.

Format reference: https://github.com/dtlnor/REMSG_Converter (MIT).
Uses the same RE-Asset-Library hashing implementation as extract_wilds.py.
"""
import hashlib
import json
from pathlib import Path
import struct
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'.local/tools/RE-Asset-Library'))
from modules.hashing.mmh3.pymmh3 import hashUTF16


def read(path):
    raw = path.read_bytes()
    version, magic, _, count, _, languages = struct.unpack_from('<I4sQIII', raw)
    assert version == 23 and magic == b'GMSG' and languages == 33
    data_offset = struct.unpack_from('<Q', raw, 32)[0]
    key = bytes.fromhex('cfcefbf8ec0a336693a91d9350395f09')
    encrypted = raw[data_offset:]
    pool = bytes(c ^ (encrypted[i-1] if i else 0) ^ key[i % 16] for i, c in enumerate(encrypted))
    def string(offset):
        start = offset - data_offset
        assert 0 <= start < len(pool) and start % 2 == 0
        end = start
        while pool[end:end+2] != b'\0\0':
            end += 2
            assert end < len(pool)
        return pool[start:end].decode('utf-16-le')
    entries = []
    for i in range(count):
        offset = struct.unpack_from('<Q', raw, 72 + i*8)[0]
        name = string(struct.unpack_from('<Q', raw, offset+24)[0])
        assert hashUTF16(name) == struct.unpack_from('<I', raw, offset+20)[0]
        contents = struct.unpack_from('<33Q', raw, offset+40)
        entries.append({'name': name, 'guid': str(uuid.UUID(bytes_le=raw[offset:offset+16])),
                        'en_us': string(contents[1]), 'zh_cn': string(contents[13])})
    return {'source': path.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(raw).hexdigest(), 'entries': entries}


if __name__ == '__main__':
    folder = ROOT/'.local/wilds/move-names'
    documents = [read(p) for p in sorted(folder.rglob('*.msg.23'))]
    (folder/'labels.json').write_text(json.dumps(documents, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
    for doc in documents:
        print(Path(doc['source']).name)
        for e in doc['entries']:
            print(e['name'], '|', e['zh_cn'], '|', e['en_us'])
