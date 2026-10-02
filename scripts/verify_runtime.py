"""Safe acceptance checks on an isolated app; creates only unique test links."""
import concurrent.futures, json, os, statistics, subprocess, time, urllib.request, urllib.error, uuid
from pathlib import Path
BASE=os.environ.get('VERIFY_BASE_URL','http://localhost:3001')
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args): return None

def request(method,path,data=None,ip='192.0.2.15'):
    req=urllib.request.Request(BASE+path,data=json.dumps(data).encode() if data is not None else None,
        headers={'Content-Type':'application/json','X-Forwarded-For':ip,'Referer':'https://acceptance.example/ref','User-Agent':'AcceptanceVerifier/1'},method=method)
    start=time.perf_counter()
    try: response=urllib.request.build_opener(NoRedirect).open(req,timeout=15)
    except urllib.error.HTTPError as e: response=e
    with response:
        raw=response.read();headers=dict(response.headers);status=response.code
    try: body=json.loads(raw)
    except ValueError: body=raw.decode()
    return status,body,headers,(time.perf_counter()-start)*1000

def docker(*args):
    return subprocess.check_output(['docker','exec',*args],text=True).strip()
report={}
for _ in range(30):
    try:
        if request('GET','/ready')[0]==200:break
    except Exception:pass
    time.sleep(1)
else:raise RuntimeError('isolated application not ready')
alias='verify-'+uuid.uuid4().hex[:10]
status,created,_,_=request('POST','/api/shorten',{'url':'https://example.com/acceptance','customAlias':alias})
assert status==201
assert request('GET','/api/analytics/'+alias)[1]['totalClicks']==0
for _ in range(5):
    status,_,headers,_=request('GET','/'+alias)
    assert status==301 and headers['Location']=='https://example.com/acceptance'
    assert all(x in headers for x in ['Content-Security-Policy','X-Frame-Options','X-Content-Type-Options'])
for _ in range(40):
    analytics=request('GET','/api/analytics/'+alias)[1]
    if analytics['totalClicks']==5:break
    time.sleep(.25)
assert analytics['totalClicks']==5 and sum(x['clicks'] for x in analytics['timeSeries'])==5
assert analytics['topReferrers']['items'][0]['count']==5
assert analytics['topUserAgents']['items'][0]['count']==5
report['five_redirects']={'pass':True,'analytics':analytics,'security_headers':headers}
schema=os.environ.get('VERIFY_SCHEMA','acceptance_verification')
assert schema.replace('_','').isalnum()
query=f"SELECT json_build_object('events',count(*),'timestamps',count(distinct e.timestamp),'hashed_ips',bool_and(length(ip_hash)=64),'referrers',bool_and(referrer='https://acceptance.example/ref'),'agents',bool_and(user_agent='AcceptanceVerifier/1')) FROM {schema}.click_events e JOIN {schema}.urls u ON u.id=e.url_id WHERE u.short_code='{alias}';"
raw=json.loads(docker('urlshortener-postgres','psql','-U','postgres','-d','urlshortener','-Atc',query))
assert raw['events']==5 and raw['hashed_ips'] and raw['referrers'] and raw['agents']
report['stored_events']=raw
hits0=json.loads('{}')
info=docker('urlshortener-redis','redis-cli','INFO','stats')
gethits=lambda text:int(next(line.split(':')[1] for line in text.splitlines() if line.startswith('keyspace_hits:')))
before=gethits(info)
# Missing redirects do not write analytics; five-click link stays untouched.
loadalias='load-'+uuid.uuid4().hex[:10]
assert request('POST','/api/shorten',{'url':'https://example.com/load','customAlias':loadalias})[0]==201
samples=[]; statuses=[];started=time.perf_counter()
with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
    futures=[]
    for i in range(500):
        wait=started+i/.1e3-time.perf_counter()
        if wait>0:time.sleep(wait)
        futures.append(pool.submit(request,'GET','/'+loadalias,None,'192.0.2.16'))
    for future in futures:
        status,_,_,latency=future.result();statuses.append(status);samples.append(latency)
elapsed=time.perf_counter()-started
p95=sorted(samples)[int(.95*(len(samples)-1))]
assert all(status==301 for status in statuses)
assert p95<50, f'p95 {p95:.2f}ms exceeds criterion'
after=gethits(docker('urlshortener-redis','redis-cli','INFO','stats'))
assert after-before>=500
report['load']={'requests':500,'target_rps':100,'observed_rps':500/elapsed,'p95_ms':p95,'max_ms':max(samples),'errors':0,'redis_hit_delta':after-before,'pass':True}
expiry='ttl-'+uuid.uuid4().hex[:10]
assert request('POST','/api/shorten',{'url':'https://example.com','customAlias':expiry,'expiresIn':30})[0]==201
ttl=int(docker('urlshortener-redis','redis-cli','PTTL','url:'+expiry)); assert 0<ttl<=30000
permanent=int(docker('urlshortener-redis','redis-cli','PTTL','url:'+alias));assert 0<permanent<=3600000
report['redis_ttl']={'expiring_ms':ttl,'permanent_ms':permanent,'pass':True}
# Safe dry-run only: no cleanup deletion.
assert request('POST','/admin/cleanup/run',{'dryRun':True})[1]['dryRun'] is True
report['cleanup_dry_run']={'pass':True}
Path('logs/acceptance-runtime.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
