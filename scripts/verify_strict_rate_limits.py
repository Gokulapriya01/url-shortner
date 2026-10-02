"""Verify per-client rolling-window HTTP limits without writing database rows."""
import concurrent.futures
import json
import os
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

BASE = os.environ.get('VERIFY_BASE_URL', 'http://localhost:3000')


def request(method, path, body, ip):
    req = urllib.request.Request(
        BASE + path, data=json.dumps(body).encode() if body is not None else None,
        headers={'Content-Type': 'application/json', 'X-Forwarded-For': ip}, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        response.read()
        return response.code


report = {}
for criterion, limit, method, path, body, initial_status in [
    ('AC-4.1', 100, 'POST', '/api/shorten', {'url': 'javascript:alert(1)'}, 400),
    ('AC-4.2', 1000, 'GET', '/missing-rate-' + uuid.uuid4().hex, None, 404),
]:
    ip = '192.0.2.' + str(10 + uuid.uuid4().int % 240)
    start = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
        statuses = list(pool.map(lambda _: request(method, path, body, ip), range(limit)))
    time.sleep(1.2)  # Greedy refill previously admitted the next request after this delay.
    extra = request(method, path, body, ip)
    elapsed = time.monotonic() - start
    isolated = request(method, path, body, '198.51.100.' + str(10 + uuid.uuid4().int % 240))
    report[criterion] = {
        'limit': limit, 'requests_within_60_seconds': limit + 1,
        'elapsed_seconds': elapsed, 'initial_status_counts': {
            str(code): statuses.count(code) for code in set(statuses)},
        'expected_extra_status': 429, 'actual_extra_status': extra,
        'independent_client_status': isolated,
        'pass': all(code == initial_status for code in statuses)
                and elapsed < 60 and extra == 429 and isolated == initial_status,
    }
Path('logs/strict-rate-runtime.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
if not all(row['pass'] for row in report.values()):
    raise SystemExit(1)
