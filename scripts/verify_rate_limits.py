"""Check default HTTP rate limits on the isolated verification app only."""
import concurrent.futures,json,os,urllib.request,urllib.error,uuid
from pathlib import Path
BASE=os.environ.get('VERIFY_BASE_URL','http://localhost:3001')
def call(path,method,body,ip):
    request=urllib.request.Request(BASE+path,data=json.dumps(body).encode() if body else None,
        headers={'Content-Type':'application/json','X-Forwarded-For':ip},method=method)
    try:response=urllib.request.urlopen(request,timeout=15)
    except urllib.error.HTTPError as error:response=error
    with response:response.read();return response.code
report={}
for name,count,path,method,body,ip in [('shorten',125,'/api/shorten','POST',{'url':'javascript:alert(1)'},'192.0.2.31'),
    ('redirect',1200,'/missing-rate-'+uuid.uuid4().hex[:8],'GET',None,'192.0.2.32')]:
    with concurrent.futures.ThreadPoolExecutor(max_workers=30) as pool:
        statuses=list(pool.map(lambda _:call(path,method,body,ip),range(count)))
    assert 429 in statuses, (name,statuses)
    assert set(statuses)<={429,400 if method=='POST' else 404}
    report[name]={'requests':count,'status_counts':{str(status):statuses.count(status) for status in set(statuses)},'pass':True}
Path('logs/rate-limit-runtime.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
