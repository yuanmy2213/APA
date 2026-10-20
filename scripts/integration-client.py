#!/usr/bin/env python3
"""Standard-library API client. Credentials stay in APA_API_TOKEN, never CLI arguments."""
import argparse
import datetime
import json
import mimetypes
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description='财经权限审查数据接口客户端')
    parser.add_argument('--base-url', default='http://127.0.0.1:8080/apa')
    parser.add_argument('--product', default='P06')
    commands = parser.add_subparsers(dest='command', required=True)
    upload = commands.add_parser('upload-json')
    upload.add_argument('file', type=Path)
    upload.add_argument('--wait', action='store_true')
    file = commands.add_parser('upload-file')
    file.add_argument('file', type=Path)
    file.add_argument('--wait', action='store_true')
    file.add_argument('--mode', choices=['auto', 'user', 'matrix'], default='auto')
    file.add_argument('--raw-coverage', choices=['FULL', 'PARTIAL', 'UNKNOWN'], default='UNKNOWN')
    file.add_argument('--risk-coverage', choices=['FULL', 'PARTIAL', 'UNKNOWN'], default='FULL')
    file.add_argument('--source-as-of', required=True)
    for name in ['patch-source', 'patch-risk']:
        patch = commands.add_parser(name)
        patch.add_argument('--kind', choices=['user', 'matrix'], required=True)
        patch.add_argument('--business-id', required=True)
        patch.add_argument('--draft')
        changes = patch.add_mutually_exclusive_group(required=True)
        changes.add_argument('--changes', type=json.loads)
        changes.add_argument('--changes-file', type=Path)
        patch.add_argument('--reason', required=True)
        patch.add_argument('--app-id')
        patch.add_argument('--tenant-id')
        if name == 'patch-risk':
            patch.add_argument('--rule-code', required=True)
    publish = commands.add_parser('publish')
    publish.add_argument('--draft', required=True)
    publish.add_argument('--reason', required=True)
    publish.add_argument('--allow-empty', action='store_true')
    args = parser.parse_args()
    token = os.environ.get('APA_API_TOKEN', '')
    if not token:
        parser.error('请通过环境变量APA_API_TOKEN提供接口密钥')
    origin = args.base_url.rstrip('/') + '/api/integration/v1'
    product = urllib.parse.quote(args.product, safe='')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({})) if urllib.parse.urlparse(origin).hostname in ['localhost', '127.0.0.1', '::1'] else urllib.request.build_opener()

    def request(path, method='GET', body=None, headers=None):
        h = {'Authorization': 'Bearer ' + token, **(headers or {})}
        if body is not None and not isinstance(body, bytes):
            body = json.dumps(body, ensure_ascii=False).encode('utf-8')
            h['Content-Type'] = 'application/json'
        with opener.open(urllib.request.Request(origin + path, data=body, method=method, headers=h), timeout=180) as response:
            return json.load(response)

    def wait(job):
        for _ in range(900):
            current = request('/import-jobs/' + job['id'])
            if current['status'] not in ['QUEUED', 'RUNNING']:
                return current
            time.sleep(1)
        return current

    try:
        if args.command == 'upload-json':
            result = request(f'/products/{product}/imports', 'POST', json.loads(args.file.read_text(encoding='utf-8-sig')), {'Idempotency-Key': str(uuid.uuid4())})
            if args.wait:
                result = wait(result)
        elif args.command == 'upload-file':
            boundary = 'apa-' + uuid.uuid4().hex
            fields = {'mode': args.mode, 'rawCoverage': args.raw_coverage, 'riskCoverage': args.risk_coverage, 'sourceAsOf': args.source_as_of}
            payload = bytearray()
            for key, value in fields.items():
                payload.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
            name = args.file.name.replace('"', '').replace('\r', '').replace('\n', '')
            payload.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: {mimetypes.guess_type(name)[0] or "application/octet-stream"}\r\n\r\n'.encode())
            payload.extend(args.file.read_bytes())
            payload.extend(f'\r\n--{boundary}--\r\n'.encode())
            result = request(f'/products/{product}/files', 'POST', bytes(payload), {'Content-Type': 'multipart/form-data; boundary=' + boundary, 'Idempotency-Key': str(uuid.uuid4())})
            if args.wait:
                result = wait(result)
        elif args.command in ['patch-source', 'patch-risk']:
            draft = {'id': args.draft} if args.draft else request(f'/products/{product}/drafts', 'POST', {'kind': args.kind})
            resolve = {'kind': args.kind, 'source': args.command == 'patch-source', 'businessId': args.business_id}
            if args.app_id:
                resolve['appId'] = args.app_id
            if args.tenant_id:
                resolve['tenantId'] = args.tenant_id
            if args.command == 'patch-risk':
                resolve['ruleCode'] = args.rule_code
            target = request(f'/drafts/{draft["id"]}/records/resolve', 'POST', resolve)
            source = args.command == 'patch-source'
            row = target['source' if source else 'finding']
            path = f'/drafts/{draft["id"]}/{"source-records" if source else "findings"}/{row["id"]}'
            if source:
                path += '?kind=' + args.kind
            result = request(path, 'PATCH', {'changes': json.loads(args.changes_file.read_text(encoding='utf-8-sig')) if args.changes_file else args.changes, 'reason': args.reason}, {'If-Match': str(row['recordVersion'])})
            result['draftId'] = draft['id']
            result['publicationRequired'] = True
        else:
            preview = request('/drafts/' + args.draft)
            result = request(f'/drafts/{args.draft}/publish', 'POST', {'reason': args.reason, 'allowEmpty': args.allow_empty}, {'If-Match': str(preview['revision']), 'Idempotency-Key': str(uuid.uuid4())})
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except urllib.error.HTTPError as error:
        print(f'HTTP {error.code}: {error.read().decode("utf-8", errors="replace")}', file=sys.stderr)
        return 1
    except (OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
