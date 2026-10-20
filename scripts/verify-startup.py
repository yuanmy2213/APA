#!/usr/bin/env python3
"""Check deploy startup arguments without launching another application or using real secrets."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='apa-startup-') as temporary:
    folder = Path(temporary)
    app = folder / 'deployment with spaces'
    (app / 'bin').mkdir(parents=True)
    (app / 'lib').mkdir()
    shutil.copy2(ROOT / 'deploy/bin/start.sh', app / 'bin/start.sh')
    java = folder / 'fake-jdk/bin/java'
    java.parent.mkdir(parents=True)
    java.write_text('#!/usr/bin/env python3\nimport os,json,sys\nopen(os.environ["APA_ARGV_OUTPUT"],"w").write(json.dumps(sys.argv[1:]))\n')
    java.chmod(0o755)
    capture = folder / 'arguments.json'
    env = {**os.environ, 'JAVA_HOME': str(java.parent.parent), 'APA_ARGV_OUTPUT': str(capture), 'JAVA_OPTS': '-Xms128m -Xmx256m -Dapa.test=true'}
    env.pop('CONTEXT_PATH', None)
    for context, expected in [(None, '/apa'), ('/', ''), ('/finance/audit', '/finance/audit')]:
        current = dict(env)
        if context is not None:
            current['CONTEXT_PATH'] = context
        subprocess.run(['bash', str(app / 'bin/start.sh')], env=current, check=True, capture_output=True)
        args = json.loads(capture.read_text())
        assert '-Dserver.servlet.context-path=' + expected in args
        assert '-Dapa.test=true' in args and '-Xmx256m' in args
        assert args[args.index('-jar') + 1] == str(app / 'lib/review.jar')
    invalid = subprocess.run(['bash', str(app / 'bin/start.sh')], env={**env, 'CONTEXT_PATH': 'apa'}, capture_output=True)
    assert invalid.returncode != 0
print(json.dumps({'startup_template': 'passed', 'prefix_cases': 3, 'paths_with_spaces': 'passed', 'java_options': 'preserved', 'invalid_prefix': 'rejected'}))
