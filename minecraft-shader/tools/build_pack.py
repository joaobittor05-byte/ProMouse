#!/usr/bin/env python3
from pathlib import Path
import json, subprocess, sys, zipfile

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'pack'
DIST = ROOT / 'dist'
DIST.mkdir(exist_ok=True)

subprocess.check_call([sys.executable, str(ROOT / 'tools' / 'validate_pack.py')])
man = json.loads((PACK / 'manifest.json').read_text(encoding='utf-8'))
ver = '.'.join(map(str, man['header']['version']))
base = f'Realistic_Universal_Shader_v{ver}'

for ext in ('mcpack', 'zip'):
    out = DIST / f'{base}.{ext}'
    if out.exists():
        out.unlink()
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        for f in PACK.rglob('*'):
            if f.is_file():
                z.write(f, f.relative_to(PACK))
    print(out)
