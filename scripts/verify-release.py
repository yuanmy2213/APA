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
import io
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'finance-access-review-1.4.0.jar'
if not JAR.is_file():
    JAR = ROOT / 'target/finance-access-review-1.4.0.jar'
EXAMPLE = ROOT / 'examples/财经法务_四类核心数据_演示.xlsx'
CONTEXT = os.environ.get('REVIEW_TEST_CONTEXT_PATH', '').rstrip('/')
if CONTEXT and not CONTEXT.startswith('/'):
    raise ValueError('REVIEW_TEST_CONTEXT_PATH应以/开头')
VERIFY_INTEGRATION = os.environ.get('REVIEW_TEST_INTEGRATION') == '1'
TEST_TOKEN = 'synthetic-http-integration-token-20261020'
with zipfile.ZipFile(JAR) as archive:
    assert archive.testzip() is None, 'JAR完整性校验失败'

with tempfile.TemporaryDirectory(prefix='finance-review-release-') as temporary:
    folder = Path(temporary)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    origin = f'http://127.0.0.1:{port}' + CONTEXT
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
    def upload_file(product, data, filename):
        boundary = 'review-' + uuid.uuid4().hex
        values = {'mode': 'auto', 'rawCoverage': 'FULL', 'riskCoverage': 'FULL', 'sourceAsOf': '2026-10-19T15:32:23+08:00'}
        body = b''
        for key, value in values.items():
            body += f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode()
        body += f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: text/csv\r\n\r\n'.encode() + data + f'\r\n--{boundary}--\r\n'.encode()
        job = request(f'/api/products/{product}/import-jobs', 'POST', body, {'Content-Type': 'multipart/form-data; boundary=' + boundary, 'Idempotency-Key': str(uuid.uuid4())})
        for _ in range(200):
            job = request('/api/import-jobs/' + job['id'])
            if job['status'] not in ('QUEUED', 'RUNNING'):
                return job
            time.sleep(.1)
        raise AssertionError('Import timed out')

    def upload_csv(product, csv_text):
        return upload_file(product, csv_text.encode('utf-8-sig'), 'source.csv')

    def report_without_risk_scope():
        source = zipfile.ZipFile(io.BytesIO(EXAMPLE.read_bytes()))
        ns = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
        strings = []
        if 'xl/sharedStrings.xml' in source.namelist():
            strings = [''.join(node.itertext()) for node in ET.fromstring(source.read('xl/sharedStrings.xml'))]
        # The fixture's second and fourth sheets are user/matrix findings; remove their scope columns.
        targets = {'xl/worksheets/sheet2.xml', 'xl/worksheets/sheet4.xml'}
        result = io.BytesIO()
        with zipfile.ZipFile(result, 'w', zipfile.ZIP_DEFLATED) as out:
            for entry in source.infolist():
                content = source.read(entry.filename)
                if entry.filename == 'xl/sharedStrings.xml':
                    content = content.decode('utf-8').replace('财经法务产品','支付清算产品').encode('utf-8')
                elif entry.filename.startswith('xl/worksheets/sheet') and entry.filename.endswith('.xml'):
                    content = content.decode('utf-8').replace('财经法务产品','支付清算产品').encode('utf-8')
                if entry.filename in targets:
                    root = ET.fromstring(content)
                    rows = root.findall('.//s:sheetData/s:row', ns)
                    removed = set()
                    for cell in rows[0]:
                        value = cell.find('s:v', ns)
                        label = ''.join(cell.itertext())
                        if cell.get('t') == 's' and value is not None:
                            label = strings[int(value.text)]
                        if label in ('所属应用', '应用ID', '租户ID', '来源系统'):
                            removed.add(''.join(x for x in cell.get('r') if x.isalpha()))
                    assert removed, entry.filename
                    for row in rows:
                        for cell in list(row):
                            if ''.join(x for x in cell.get('r', '') if x.isalpha()) in removed:
                                row.remove(cell)
                    content = ET.tostring(root, encoding='utf-8', xml_declaration=True)
                out.writestr(entry, content)
        return result.getvalue()

    def start():
        log = (folder / 'application.log').open('ab')
        process_env = {**os.environ, 'INTEGRATION_TOKEN': TEST_TOKEN if VERIFY_INTEGRATION else '', 'INTEGRATION_PRODUCTS': 'P13' if VERIFY_INTEGRATION else '', 'INTEGRATION_CAN_PUBLISH': 'true' if VERIFY_INTEGRATION else 'false'}
        process = subprocess.Popen(['java', '-Xms128m', '-Xmx1g', '-jar', str(JAR), '--spring.profiles.active=demo', f'--server.port={port}', '--server.address=127.0.0.1', '--server.servlet.context-path=' + CONTEXT, f'--spring.datasource.url=jdbc:h2:file:{folder / "review"};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE', f'--audit.storage={folder / "files"}'], cwd=folder, stdout=log, stderr=subprocess.STDOUT, env=process_env)
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
        if CONTEXT:
            with client.open(urllib.request.Request(origin, headers={'Authorization': auth}), timeout=30) as entry:
                assert entry.geturl().split('?')[0].endswith(CONTEXT + '/'), '应用入口未规范化到尾斜杠'
        html = request('/', binary=True).decode()
        assert 'FINANCE ACCOUNT PERMISSION AUDIT' not in html
        assert '财经权限，全量审查' in html
        assert '8项检查 · 第8项含6条矩阵细则' in html
        assert request('/api/me')['canPublish']
        assert request('/api/import-jobs') == []
        assert 'check-donut-panel' in html and '前三项风险占比' in html
        assert 'rule-share-list' not in html and 'class="eyebrow"' not in html
        css = request('/styles.css?v=1.4.0', binary=True).decode()
        assert 'font-size:22px;font-weight:700' in css
        assert "fetch('api'+path" in request('/api.js', binary=True).decode()
        assert 'RecordFields' in request('/record-fields.js', binary=True).decode()
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
        idless = upload_csv('P07', '产品名称,用户工号,权限名称,用户名称,人员类别,是否雇员\n交易经营产品,' + row + '\n交易经营产品,' + row + '\n')
        assert idless['status'] == 'VALIDATED', idless
        request('/api/drafts/' + idless['draftId'] + '/publish', 'POST', {'reason': '完整导入无ID明细', 'allowEmpty': False}, {'If-Match': '1', 'Idempotency-Key': str(uuid.uuid4())})
        sid = request('/api/snapshots', 'POST')['id']
        idless_rows = request('/api/source-records/query', 'POST', {**q, 'snapshotId': sid, 'productId': 'P07'})
        assert idless_rows['total'] == 2
        assert len({r['id'] for r in idless_rows['rows']}) == 2
        assert all(not r['authorizationId'] for r in idless_rows['rows'])
        duplicate = upload_csv('P08', '产品名称,用户工号,权限名称,授权记录ID\n资金收付安全产品,L00666785,角色,00001\n资金收付安全产品,L00766785,其他角色,00001\n')
        assert duplicate['status'] == 'FAILED', duplicate
        for csv_text in (
            '产品名称,权限组合ID,角色名称,权限集名称,权限点ID\n财经法务产品,C1,角色,集,00001\n',
            '权限组合ID,角色名称,权限集名称,权限点ID\nC1,角色,集,00001\n',
            '产品名称,角色名称,权限集名称,权限点ID\n资金收付安全产品,角色,集,00001\n',
        ):
            rejected = upload_csv('P08', csv_text)
            assert rejected['status'] == 'FAILED' and rejected['draftId'] is None, rejected
        paired = upload_file('P09', report_without_risk_scope(), 'missing-scope.xlsx')
        assert paired['status'] == 'VALIDATED', paired
        request('/api/drafts/' + paired['draftId'] + '/publish', 'POST', {'reason': '缺少来源列仍唯一关联', 'allowEmpty': False}, {'If-Match': '1', 'Idempotency-Key': str(uuid.uuid4())})
        sid = request('/api/snapshots', 'POST')['id']
        for kind in ('user', 'matrix'):
            findings = request('/api/findings/query', 'POST', {**q, 'snapshotId': sid, 'productId': 'P09', 'kind': kind})
            assert all(r['linkStatus'] == 'LINKED' for r in findings['rows'])
            trace = request('/api/findings/' + findings['rows'][0]['id'] + '?snapshotId=' + sid)
            assert trace['raw'] and trace['auditedRaw']
        error_csv = '产品名称,权限组合ID,角色名称,权限集名称,权限点ID\n' + '财经预算预测产品,C1,角色,权限集,000001\n' * 1210
        error_job = upload_csv('P10', error_csv)
        assert error_job['status'] == 'FAILED', error_job
        assert error_job['errors']['count'] == 1209 and len(error_job['errors']['items']) == 100
        page = request('/api/import-jobs/' + error_job['id'] + '/errors/page?page=7&pageSize=200')
        assert page['total'] == 1209 and len(page['rows']) == 9
        full_csv = request('/api/import-jobs/' + error_job['id'] + '/errors', binary=True).decode('utf-8-sig')
        assert len(full_csv.splitlines()) == 1210
        tasks = request('/api/import-jobs')
        assert len(tasks) >= 5 and all(j['originalName'] and j['assetId'] for j in tasks)
        assert [j['createdAt'] for j in tasks] == sorted((j['createdAt'] for j in tasks), reverse=True)
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
        assert expected['authorizations'] == 8 and expected['users'] == 4
        assert expected['total'] == 6 and expected['falsePositive'] == 2
        distribution = request('/api/dashboard?snapshotId=' + sid)['ruleDistribution']
        assert len(distribution) == 8 and sum(r['count'] for r in distribution) == expected['total']
        delta = request('/api/products/P06/releases/compare')
        assert delta['summary']['removed'] == 1

        if VERIFY_INTEGRATION:
            api_root = origin + '/api/integration/v1'
            machine_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            def machine(path, method='GET', body=None, token=TEST_TOKEN, headers=None):
                h = {'Authorization': 'Bearer ' + token, **(headers or {})}
                if body is not None:
                    body = json.dumps(body, ensure_ascii=False).encode()
                    h['Content-Type'] = 'application/json'
                with machine_opener.open(urllib.request.Request(api_root + path, data=body, method=method, headers=h), timeout=60) as response:
                    return json.load(response)
            def cli(*arguments):
                result = subprocess.run([os.sys.executable, str(ROOT / 'scripts/integration-client.py'), '--base-url', origin, '--product', 'P13', *arguments], capture_output=True, text=True, timeout=120, env={**os.environ, 'APA_API_TOKEN': TEST_TOKEN})
                assert result.returncode == 0, result.stderr
                return json.loads(result.stdout)
            try:
                machine('/snapshots', 'POST', {}, token='invalid')
                raise AssertionError('Invalid API token accepted')
            except urllib.error.HTTPError as error:
                assert error.code == 401
            payload = json.loads((ROOT / 'examples/接口上传_双业务ID.json').read_text())
            for dataset in payload['datasets']:
                for row in dataset['rows']:
                    row['productName'] = '财经助手产品'
            payload_file = folder / 'api-p13.json'
            payload_file.write_text(json.dumps(payload, ensure_ascii=False))
            job = cli('upload-json', str(payload_file), '--wait')
            assert job['status'] == 'VALIDATED', job
            published = cli('publish', '--draft', job['draftId'], '--reason', '验证机器身份上传与发布')
            snap = machine('/snapshots', 'POST', {})['id']
            query = {'snapshotId': snap, 'productId': 'P13', 'kind': 'matrix', 'filters': [{'field': 'permissionCombinationId', 'op': 'eq', 'value': 'COMBO-000001'}]}
            original = machine('/source-records/query', 'POST', query)['rows'][0]
            finding = machine('/findings/query', 'POST', query)['rows'][0]
            assert finding['currentSourceId'] == original['id'] and finding['linkMethod'] == 'COMBINATION_ID'
            assert original['pointId'] == '000042'
            edit = cli('patch-source', '--kind', 'matrix', '--business-id', 'COMBO-000001', '--changes', '{"pointName":"接口客户端修订名称"}', '--reason', '核对权限点描述')
            assert edit['publicationRequired'] and edit['affected'] == 1
            cli('publish', '--draft', edit['draftId'], '--reason', '发布客户端修订')
            fresh = machine('/snapshots', 'POST', {})['id']
            assert machine('/source-records/query', 'POST', {**query, 'snapshotId': fresh})['rows'][0]['pointName'] == '接口客户端修订名称'
            assert machine('/source-records/query', 'POST', query)['rows'][0]['pointName'] == original['pointName']
            try:
                machine('/source-records/query', 'POST', {**query, 'productId': 'P06'})
                raise AssertionError('API product scope bypassed')
            except urllib.error.HTTPError as error:
                assert error.code == 404
            # File uploads use the same server-to-server surface; duplicates retain full errors.
            invalid = folder / 'duplicate.csv'
            invalid.write_text('产品名称,权限组合ID,角色名称,权限集名称,权限点ID\n财经助手产品,SAME,角色,权限集,00001\n财经助手产品,SAME,角色,权限集,00001\n')
            failed = cli('upload-file', str(invalid), '--source-as-of', '2026-10-20T10:00:00+08:00', '--raw-coverage', 'FULL', '--wait')
            assert failed['status'] == 'FAILED' and failed['errors']['count'] == 1
            errors = machine('/import-jobs/' + failed['id'] + '/errors/page')
            assert errors['total'] == 1 and 'SAME' in errors['rows'][0]['businessKey']
            expected = request('/api/dashboard?snapshotId=' + request('/api/snapshots', 'POST')['id'])['overall']
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
    print(json.dumps({'contextPath': CONTEXT or '/', 'integration_client': 'passed' if VERIFY_INTEGRATION else 'not-run', 'packaged_http_flow': 'passed', 'login_csrf': 'passed', 'multipart_upload': 'passed', 'publication_snapshot': 'passed', 'source_recheck': 'passed', 'restart_persistence': 'passed', 'idless_complete_import': 'passed', 'strong_duplicate_rejected': 'passed', 'both_status_chains': 'passed', 'risk_reasons_preserved': 'passed', 'task_list_200': 'passed', 'business_id_association': 'passed', 'product_name_validation': 'passed', 'full_error_csv_pagination': 'passed', 'version_diff': 'passed', 'check_item_distribution': 'passed', 'example_records': {'authorizations': 3, 'user_risks': 3, 'matrix_risks': 1}}))
