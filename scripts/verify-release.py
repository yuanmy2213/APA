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
JAR = ROOT / 'finance-access-review-1.1.0.jar'
if not JAR.is_file():
    JAR = ROOT / 'target/finance-access-review-1.1.0.jar'
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
    def upload_csv(product, csv_text):
        boundary = 'review-' + uuid.uuid4().hex
        values = {'mode': 'auto', 'rawCoverage': 'FULL', 'riskCoverage': 'FULL', 'sourceAsOf': '2026-10-19T15:32:23+08:00'}
        body = b''
        for key, value in values.items():
            body += f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode()
        body += f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="source.csv"\r\nContent-Type: text/csv\r\n\r\n'.encode() + csv_text.encode('utf-8-sig') + f'\r\n--{boundary}--\r\n'.encode()
        job = request(f'/api/products/{product}/import-jobs', 'POST', body, {'Content-Type': 'multipart/form-data; boundary=' + boundary, 'Idempotency-Key': str(uuid.uuid4())})
        for _ in range(200):
            job = request('/api/import-jobs/' + job['id'])
            if job['status'] not in ('QUEUED', 'RUNNING'):
                return job
            time.sleep(.1)
        raise AssertionError('Import timed out')

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
        assert 'FINANCE ACCOUNT PERMISSION AUDIT' not in html
        assert '财经权限，全量审查' in html
        assert '8项检查 · 第8项含6条矩阵细则' in html
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
        # No authorization ID: both identical rows must survive parse, publication and restart.
        row = 'L00666785,重复角色,重复人员,IT人员,雇员'
        idless = upload_csv('P07', '用户工号,权限名称,用户名称,人员类别,是否雇员\n' + row + '\n' + row + '\n')
        assert idless['status'] == 'VALIDATED', idless
        request('/api/drafts/' + idless['draftId'] + '/publish', 'POST', {'reason': '完整导入无ID明细', 'allowEmpty': False}, {'If-Match': '1', 'Idempotency-Key': str(uuid.uuid4())})
        sid = request('/api/snapshots', 'POST')['id']
        idless_rows = request('/api/source-records/query', 'POST', {**q, 'snapshotId': sid, 'productId': 'P07'})
        assert idless_rows['total'] == 2
        assert len({r['id'] for r in idless_rows['rows']}) == 2
        assert all(not r['authorizationId'] for r in idless_rows['rows'])
        duplicate = upload_csv('P08', '用户工号,权限名称,授权记录ID\nL00666785,角色,00001\nL00766785,其他角色,00001\n')
        assert duplicate['status'] == 'FAILED', duplicate
        # Each status PATCH changes workflow only; original reasons are exact preserved values.
        for kind in ('user', 'matrix'):
            sid = request('/api/snapshots', 'POST')['id']
            query = {**q, 'snapshotId': sid, 'kind': kind}
            finding = request('/api/findings/query', 'POST', query)['rows'][0]
            d = request('/api/products/P06/drafts', 'POST', {'kind': kind})
            t = request(f'/api/drafts/{d["id"]}/record?originalId={finding["id"]}&kind={kind}')
            corrected = request(f'/api/drafts/{d["id"]}/findings/{t["finding"]["id"]}', 'PATCH', {'changes': {'workflow': '已确认误报'}, 'reason': '核实确认误报'}, {'If-Match': str(t['finding']['recordVersion'])})
            request('/api/drafts/' + d['id'] + '/publish', 'POST', {'reason': '发布处理状态', 'allowEmpty': False}, {'If-Match': str(corrected['draftRevision']), 'Idempotency-Key': str(uuid.uuid4())})
            sid = request('/api/snapshots', 'POST')['id']
            all_rows = request('/api/findings/query', 'POST', {**query, 'snapshotId': sid, 'includeFalsePositive': True})
            assert next(r for r in all_rows['rows'] if r['workflow'] == '已确认误报')['violation'] == finding['violation']
        sid = request('/api/snapshots', 'POST')['id']
        expected = request('/api/dashboard?snapshotId=' + sid)['overall']
        assert expected['authorizations'] == 5 and expected['users'] == 4
        assert expected['total'] == 2 and expected['falsePositive'] == 2
    finally:
        stop(process, log)
    process, log = start()
    try:
        snapshot = request('/api/snapshots', 'POST')['id']
        persisted = request('/api/dashboard?snapshotId=' + snapshot)['overall']
        for key in ('authorizations', 'users', 'total', 'falsePositive', 'recheck'):
            assert persisted[key] == expected[key], (key, persisted, expected)
        assert request('/api/source-records/query', 'POST', {**q, 'snapshotId': snapshot, 'productId': 'P07'})['total'] == 2
    finally:
        stop(process, log)
    print(json.dumps({'packaged_http_flow': 'passed', 'login_csrf': 'passed', 'multipart_upload': 'passed', 'publication_snapshot': 'passed', 'source_recheck': 'passed', 'restart_persistence': 'passed', 'idless_complete_import': 'passed', 'strong_duplicate_rejected': 'passed', 'both_status_chains': 'passed', 'risk_reasons_preserved': 'passed', 'example_records': {'authorizations': 3, 'user_risks': 3, 'matrix_risks': 1}}))
