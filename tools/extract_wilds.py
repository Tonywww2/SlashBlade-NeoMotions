"""Targeted, read-only PAK extraction using a separate RE-Asset-Library checkout.

The third-party tool and game files live in .local, never in the mod jar.
No game installation files are modified. Latest patch entry wins.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import zlib
import zstandard


def extract(game, library, paths, output):
    sys.path.insert(0, str(library.resolve()))
    from modules.pak.file_re_pak import PakFile
    from modules.hashing.mmh3.pymmh3 import hashUTF16
    from modules.pak.re_pak_extract_mp import decryptResource

    names = [p.strip().replace('\\', '/') for p in paths if p.strip() and not p.startswith('#')]
    hashes = {(hashUTF16(p.lower()), hashUTF16(p.upper())): p for p in names}
    found = {}
    # Base and sub chunk first, then patches by numerical revision.
    def revision(p):
        suffix = p.name.split('.patch_')
        return (int(suffix[1].split('.')[0]) if len(suffix) > 1 else 0, p.name)
    for pak in sorted(game.glob('re_chunk_*.pak'), key=revision):
        with pak.open('rb') as stream:
            table = PakFile()
            table.readTOC(stream)
            for entry in table.toc.entryList:
                name = hashes.get((entry.hashNameLower, entry.hashNameUpper))
                if name:
                    found[name] = (pak, entry)
        print(f'Indexed {pak.name}; matched {len(found)}/{len(names)}', flush=True)
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    manifest = []
    for name, (pak, entry) in sorted(found.items()):
        destination = (output / name).resolve()
        if not destination.is_relative_to(output):
            raise ValueError(f'Unsafe output path: {name}')
        if entry.decompressedSize > 128 * 1024 * 1024:
            raise ValueError(f'Refusing unexpectedly large motion: {name}')
        with pak.open('rb') as stream:
            stream.seek(entry.offset)
            data = stream.read(entry.compressedSize)
        if entry.encryptionType:
            data = decryptResource(data)
        if entry.compressionType == 1:
            data = zlib.decompress(data, -zlib.MAX_WBITS)
        elif entry.compressionType == 2:
            data = zstandard.ZstdDecompressor().decompress(data, max_output_size=entry.decompressedSize)
        elif entry.compressionType != 0:
            raise ValueError(f'Unsupported compression {entry.compressionType}')
        if len(data) != entry.decompressedSize:
            raise ValueError(f'Size mismatch: {name}')
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        manifest.append(dict(path=name, pak=pak.name, bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
        print(f'Extracted {name}: {len(data)} bytes', flush=True)
    (output / 'extraction-manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    missing = set(names) - found.keys()
    if missing:
        raise SystemExit('Missing resources: ' + ', '.join(sorted(missing)))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game', type=Path, required=True)
    parser.add_argument('--library', type=Path, default=Path('.local/tools/RE-Asset-Library'))
    parser.add_argument('--paths', type=Path, default=Path('tools/wilds-greatsword-paths.txt'))
    parser.add_argument('--output', type=Path, default=Path('.local/wilds'))
    args = parser.parse_args()
    extract(args.game, args.library, args.paths.read_text(encoding='utf-8').splitlines(), args.output)

