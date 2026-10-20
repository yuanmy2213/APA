#!/usr/bin/env python3
"""Create the deployable package from a built JAR and the tracked startup template."""
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = ET.parse(ROOT / 'pom.xml').getroot().find('{http://maven.apache.org/POM/4.0.0}version').text
JAR = ROOT / 'target' / f'finance-access-review-{VERSION}.jar'
with zipfile.ZipFile(JAR) as archive:
    if archive.testzip() is not None:
        raise RuntimeError('JAR完整性校验失败，请重新打包')
PACKAGE = ROOT / 'target' / 'package' / 'finance-access-review'
(PACKAGE / 'bin').mkdir(parents=True, exist_ok=True)
(PACKAGE / 'lib').mkdir(exist_ok=True)
shutil.copy2(JAR, PACKAGE / 'lib' / 'review.jar')
shutil.copy2(ROOT / 'deploy' / 'bin' / 'start.sh', PACKAGE / 'bin' / 'start.sh')
(PACKAGE / 'bin' / 'start.sh').chmod(0o755)
for name in ['docs', 'examples']:
    shutil.copytree(ROOT / name, PACKAGE / name, dirs_exist_ok=True)
shutil.copy2(ROOT / 'scripts' / 'integration-client.py', PACKAGE / 'bin' / 'integration-client.py')
shutil.copy2(ROOT / 'README.md', PACKAGE / 'README.md')
print(PACKAGE)
