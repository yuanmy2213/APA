#!/usr/bin/env python3
"""Verify the packaged Java application over HTTP, including database restart persistence.
Requires Python 3 standard library and Java 17 on PATH; uses only synthetic example data.
"""
import base64
import http.cookiejar
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.request
import urllib.error
import uuid

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'finance-access-review-1.0.0.jar'
if not JAR.is_file():
    JAR = ROOT / 'target/finance-access-review-1.0.0.jar'
EXAMPLE = ROOT / 'examples/财经法务_四类核心数据_演示.xlsx'

with tempfile.TemporaryDirectory(prefix='finance-review-release-') as temporary:
    folder = Path(temporary)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    origin = f'http://127.0.0.1:{port}'
    auth = 'Basic ' + base64.b64encode(b'aa:aa-demo-2026').decode()
    client = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    csrf = {}
    def request(path, method='GET', body=None, headers=None, binary=False):
        h = {'Authorization': auth, **(headers or {})}
        if method != 'GET' and csrf:
            h[csrf['headerName']] = csrf['token']
        if body is not None and not isinstance(body, bytes):
            body = json.dumps(body, ensure_ascii=False).encode()
            h['Content-Type'] = 'application/json'
        with client.open(urllib.request.Request(origin + path, data=body, headers=h, method=method), timeout=30) as response:
            raw = response.read()
            return raw if binary else json.loads(raw)
    def start():
        log = (folder / 'application.log').open('ab')
        process = subprocess.Popen(['java', '-Xms128m', '-Xmx1g', '-jar', str(JAR), '--spring.profiles.active=demo', f'--server.port={port}', '--server.address=127.0.0.1', f'--spring.datasource.url=jdbc:h2:file:{folder / "review"};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE', f'--audit.storage={folder / "files"}'], cwd=folder, stdout=log, stderr=subprocess.STDOUT)
        for _ in range(120):
            if process.poll() is not None:
                raise RuntimeError((folder / 'application.log').read_text()[-4000:])
            try:
                request('/api/me')
                csrf.clear()
                csrf.update(request('/api/csrf'))
                return process, log
            except Exception:
                time.sleep(.2)
        raise RuntimeError('Packaged application startup timed out')
    def stop(process, log):
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
        log.close()
    process, log = start()
    try:
        html = request('/', binary=True).decode()
        assert 'FINANCE ACCOUNT PERMISSION AUDIT' in html
        assert '财经权限，全量审查' in html
        assert '8项用户权限检查·6项权限矩阵明细检查' in html
        assert request('/api/me')['canPublish']
        before = request('/api/snapshots', 'POST')['id']
        dashboard = request('/api/dashboard?snapshotId=' + before)
        assert dashboard['overall']['authorizations'] is None
        boundary = 'review-' + uuid.uuid4().hex
        fields = {'mode': 'auto', 'rawCoverage': 'FULL', 'riskCoverage': 'FULL', 'sourceAsOf': '2026-10-16T18:30:00+08:00'}
        body = b''
        for k, v in fields.items():
            body += f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
        body += f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="demo.xlsx"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n'.encode() + EXAMPLE.read_bytes() + f'\r\n--{boundary}--\r\n'.encode()
        job = request('/api/products/P06/import-jobs', 'POST', body, {'Content-Type': 'multipart/form-data; boundary=' + boundary, 'Idempotency-Key': str(uuid.uuid4())})
        for _ in range(200):
            job = request('/api/import-jobs/' + job['id'])
            if job['status'] not in ('QUEUED', 'RUNNING'):
                break
            time.sleep(.1)
        assert job['status'] == 'VALIDATED', job
        assert request('/api/dashboard?snapshotId=' + before)['overall']['total'] == 0
        request('/api/drafts/' + job['draftId'] + '/publish', 'POST', {'reason': 'Release verification', 'allowEmpty': False}, {'If-Match': '1', 'Idempotency-Key': str(uuid.uuid4())})
        snapshot = request('/api/snapshots', 'POST')['id']
        dashboard = request('/api/dashboard?snapshotId=' + snapshot)
        assert dashboard['overall']['authorizations'] == 3
        assert dashboard['overall']['users'] == 3
        assert dashboard['overall']['total'] == 4
        assert [dashboard['overall'][k] for k in ('it', 'business', 'external')] == [1, 1, 1]
        q = {'snapshotId': snapshot, 'productId': 'P06', 'kind': 'user', 'page': 1, 'pageSize': 20}
        risks = request('/api/findings/query', 'POST', q)
        assert risks['total'] == 3
        raw = request('/api/source-records/query', 'POST', q)
        assert raw['total'] == 3
        assert any(r['authorizationId'] == '0000000001' for r in raw['rows'])
        record = next(r for r in risks['rows'] if r['employeeId'] == 'L00566785')
        trace = request('/api/findings/' + record['id'] + '?snapshotId=' + snapshot)
        assert trace['raw'] and trace['auditedRaw']
        draft = request('/api/products/P06/drafts', 'POST', {'kind': 'user'})
        target = request(f'/api/drafts/{draft["id"]}/record?originalId={record["id"]}&kind=user')
        changed = request(f'/api/drafts/{draft["id"]}/source-records/{target["source"]["id"]}?kind=user', 'PATCH', {'changes': {'expiresAt': '2028-01-01'}, 'reason': 'Verify source correction'}, {'If-Match': str(target['source']['recordVersion'])})
        assert changed['affected'] == 2
        request('/api/drafts/' + draft['id'] + '/publish', 'POST', {'reason': 'Publish correction', 'allowEmpty': False}, {'If-Match': str(changed['draftRevision']), 'Idempotency-Key': str(uuid.uuid4())})
        new_snapshot = request('/api/snapshots', 'POST')['id']
        current = request('/api/dashboard?snapshotId=' + new_snapshot)
        assert current['overall']['recheck'] == 2
        assert current['overall']['total'] == 4
        assert request('/api/dashboard?snapshotId=' + snapshot)['overall']['recheck'] == 0
    finally:
        stop(process, log)
    process, log = start()
    try:
        snapshot = request('/api/snapshots', 'POST')['id']
        persisted = request('/api/dashboard?snapshotId=' + snapshot)['overall']
        assert persisted['authorizations'] == 3 and persisted['users'] == 3
        assert persisted['total'] == 4 and persisted['recheck'] == 2
    finally:
        stop(process, log)
    print(json.dumps({'packaged_http_flow': 'passed', 'login_csrf': 'passed', 'multipart_upload': 'passed', 'publication_snapshot': 'passed', 'source_recheck': 'passed', 'restart_persistence': 'passed', 'example_records': {'authorizations': 3, 'user_risks': 3, 'matrix_risks': 1}}))
