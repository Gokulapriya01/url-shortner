import urllib.request, urllib.error, json, time, uuid
import os
base=os.environ.get('VERIFY_BASE_URL','http://localhost:3000')
rows=[]
def call(method,path,data=None,expected=None):
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,*args): return None
    req=urllib.request.Request(base+path,data=json.dumps(data).encode() if data is not None else None,headers={'Content-Type':'application/json'},method=method)
    try:
        res=urllib.request.build_opener(NoRedirect).open(req,timeout=10)
    except urllib.error.HTTPError as e: res=e
    body=res.read().decode(); status=res.code
    try: parsed=json.loads(body)
    except: parsed=body
    rows.append({'method':method,'path':path,'status':status,'expected':expected,'pass':status==expected if expected else None,'body':parsed,'location':res.headers.get('Location')})
    return parsed
call('POST','/admin/cleanup/run',{'dryRun':True},200)
for p in ['/health','/ready','/live','/actuator/health','/admin/stats','/admin/cleanup/status']:call('GET',p,expected=200)
suffix=uuid.uuid4().hex[:6]
a='t'+suffix
r=call('POST','/api/shorten',{'url':'https://example.com/api-smoke','customAlias':a,'expiresIn':60},201)
call('GET','/'+a,expected=302)
call('POST','/api/shorten',{'url':'https://example.com','customAlias':a},409)
call('POST','/api/shorten',{'url':'https://example.com','customAlias':'long-test-'+suffix},201)
r=call('POST','/api/shorten',{'url':'https://example.com/permanent'},201)
if isinstance(r,dict) and 'shortCode' in r:call('GET','/'+r['shortCode'],expected=301)
call('POST','/api/shorten',{'url':'javascript:alert(1)'},400)
call('POST','/api/shorten',{'url':''},400)
call('POST','/api/shorten',{'url':'https://example.com','customAlias':'ab'},400)
call('GET','/missing-'+suffix,expected=404)
r=call('POST','/api/shorten',{'url':'https://example.com','expiresIn':1},201)
time.sleep(2)
if isinstance(r,dict) and 'shortCode' in r:call('GET','/'+r['shortCode'],expected=410)
call('GET','/api/analytics/'+a,expected=200)
call('GET','/api/analytics/'+a+'?granularity=day&from=2026-10-01T00:00:00Z&to=2026-10-03T00:00:00Z',expected=200)
call('GET','/api/analytics/missing-'+suffix,expected=404)
tid='smoke-'+suffix
call('POST','/api/orchestration/tasks',{'tasks':[{'id':tid,'name':'Smoke task','phase':1,'orderIndex':1,'dependencies':[],'acIds':[]}]},200)
call('POST','/api/orchestration/gates',{'gates':[{'id':'gate-'+suffix,'name':'Smoke gate','phase':1,'requiresApproval':True}]},200)
s=call('POST','/api/orchestration/sessions',{'name':'API smoke '+suffix},201)
if isinstance(s,dict) and 'id' in s:
    p='/api/orchestration/sessions/'+s['id']; call('GET',p,expected=200)
    call('PUT',p+'/context',{'key':'smoke','value':'ok'},200);call('GET',p+'/context/smoke',expected=200)
    call('GET',p+'/metrics',expected=200)
    call('POST',p+'/pause',{},200);call('POST',p+'/resume',{},200)
    for g in s.get('gates',[]):
        if g['name']=='Smoke gate':
            call('POST',p+'/gates/'+g['id']+'/reject',{'reason':'smoke test'},200)
            call('POST',p+'/gates/'+g['id']+'/approve',{'approvedBy':'api-smoke'},200)
    call('POST',p+'/execute',{},200)
    call('POST',p+'/rollback',{'targetPhase':1},200)
    call('POST',p+'/cancel',{},200)
with open('logs/endpoint-test-results.json','w') as f:json.dump(rows,f,indent=2)
for r in rows:print(('PASS' if r['pass'] else 'FAIL'),r['method'],r['path'],r['status'],'expected',r['expected'])
print('Results saved to logs/endpoint-test-results.json')

if any(not row["pass"] for row in rows): raise SystemExit(1)
