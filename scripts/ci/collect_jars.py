#!/usr/bin/env python3
"""Validate exact release outputs instead of accidentally uploading thin or source JARs."""
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

output = Path('dist')
output.mkdir(exist_ok=True)
checksums = []
for mc in ('26.3',):
    jars = list(Path(f'mc{mc}/build/libs').glob(f'bingout-*+mc{mc}.jar'))
    if len(jars) != 1:
        raise RuntimeError(f'Expected one installable JAR for {mc}, got {jars}; use a clean build directory')
    jar = jars[0]
    with zipfile.ZipFile(jar) as archive:
        assert archive.testzip() is None, f'Corrupt JAR: {jar}'
        metadata = json.loads(archive.read('fabric.mod.json'))
        assert metadata['name'] == 'Bingout'
        assert metadata['version'].endswith(f'+mc{mc}')
        assert jar.name == f"bingout-{metadata['version']}.jar"
        names = archive.namelist()
        assert any(n.endswith('/BingoutDraftScreen.class') for n in names)
        assert sum('/objectives/draftout/' in n and n.endswith('.json') for n in names) == 407
        assert sum('/images/draftout/' in n and n.endswith('.png') for n in names) == 246
        assert any(n.startswith('META-INF/jars/') and n.endswith('.jar') for n in names)
    shutil.copy2(jar, output / jar.name)
    checksums.append(f'{hashlib.sha256(jar.read_bytes()).hexdigest()}  {jar.name}')
    print(jar.name)
(output / 'SHA256SUMS.txt').write_text('\n'.join(checksums) + '\n')
