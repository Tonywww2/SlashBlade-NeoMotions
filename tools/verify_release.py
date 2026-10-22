"""Check built loader jars against shared assets and Java datagen, then stage them."""
import hashlib
import json
import re
import shutil
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT/'src/main/resources/assets/slashblade_neomotions'

def main():
    version = re.search(r'^mod.version=(.+)$', (ROOT/'gradle.properties').read_text(), re.M)[1].strip()
    manifest = json.loads((ASSETS/'motions/source_actions.json').read_text(encoding='utf-8'))
    manual = (ROOT/'src/main/java/com/tonywww/slashblade_neomotions/item/ManualMovesTooltip.java').read_text(encoding='utf-8')
    rows = re.findall(r'new Row\("([^"]+)", "([^"]+)"\)', manual)
    source_names = json.loads((ROOT/'docs/switchaxe-name-sources.json').read_text(encoding='utf-8'))
    for lang in ['zh_cn', 'en_us']:
        text = json.loads((ASSETS/'lang'/f'{lang}.json').read_text(encoding='utf-8'))
        for key, move in rows:
            assert 'move.slashblade_neomotions.'+move in text
            for suffix in ['input', 'follow']: assert 'tooltip.slashblade_neomotions.manual.'+key+'.'+suffix in text
        for move, evidence in source_names['actions'].items():
            assert text['move.slashblade_neomotions.'+move] == evidence[lang].strip()
    pending = []; checks = {}
    for node, loader, mc in [('1.20.1-forge','forge','1.20.1'), ('1.21.1-neoforge','neoforge','1.21.1')]:
        jar = ROOT/'versions'/node/'build/libs'/f'slashblade_neomotions-{loader}-{version}+{mc}.jar'
        suites = [ET.parse(p).getroot() for p in (ROOT/'versions'/node/'build/test-results/test').glob('TEST-*.xml')]
        count = sum(int(s.attrib['tests']) for s in suites)
        assert count >= 37 and all(int(s.attrib[k]) == 0 for s in suites for k in ['failures', 'errors', 'skipped'])
        with zipfile.ZipFile(jar) as archive:
            names = archive.namelist(); assert len(names) == len(set(names))
            assert not any(any(w in n.lower() for w in ['gametest', 'clientqa', '.local/', '.cache/', '__pycache__', 'extraction-manifest']) for n in names)
            assert not any(n.endswith(('.user.3', '.motlist.992', '.pak')) for n in names)
            for cls in ['combat/SwitchAxeStyle', 'combat/SwitchAxeRules', 'combat/SwitchAxeGauge', 'combat/SwitchAxeMove',
                        'animation/WeaponPose', 'platform/client/SwitchAxeGaugeDisplay']:
                assert f'com/tonywww/slashblade_neomotions/{cls}.class' in names
            for path in ASSETS.rglob('*'):
                if path.is_file(): assert archive.read(path.relative_to(ROOT/'src/main/resources').as_posix()) == path.read_bytes(), path
            for name in manifest['actions']:
                for directory, extension in [('player_animation', 'json'), ('motions', 'vmd')]:
                    assert f'assets/slashblade_neomotions/{directory}/{name}.{extension}' in names
            generated = ROOT/'src/generated'/node/'resources'
            data = list((generated/'data').rglob('*.json')); assert len(data) == 15
            for path in data: assert archive.read(path.relative_to(generated).as_posix()) == path.read_bytes(), path
            blade = json.loads(archive.read('data/slashblade_neomotions/slashblade/named_blades/switchaxe_prototype.json'))
            assert blade['properties']['root_combo'] == 'slashblade_neomotions:switchaxe_axe'
            assert blade['render']['model'] == 'slashblade_neomotions:model/switchaxe_prototype.obj'
            meta = 'META-INF/mods.toml' if loader == 'forge' else 'META-INF/neoforge.mods.toml'
            assert version in archive.read(meta).decode()
        raw = jar.read_bytes(); checks[jar.name] = {'bytes':len(raw), 'sha256':hashlib.sha256(raw).hexdigest()}
        pending.append(jar)
        print(f'{node}: {count} tests pass; all {len(manifest["actions"])} animation pairs, models, atlas, languages and 15 datagen files match.')
    out = ROOT/'build/release'; out.mkdir(exist_ok=True)
    for jar in pending: shutil.copy2(jar, out/jar.name)
    (out/f'checksums-{version}.json').write_text(json.dumps(checks, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(checks, indent=2))

if __name__ == '__main__': main()
