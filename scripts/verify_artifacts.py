"""Validate local delivery artifacts without network or credentials output."""
import csv,json,re
from pathlib import Path
root=Path(__file__).resolve().parent.parent
spec=json.loads((root/'docs/openapi.yaml').read_text()) # JSON is a valid YAML 1.2 representation.
assert spec['openapi']=='3.0.3'
normalize=lambda path:re.sub(r'\{[^}]+\}','{}',path)
actual=set()
for path in (root/'src/main/java/com/urlshortener/controller').glob('*.java'):
    source=path.read_text()
    prefix=re.search(r'@RequestMapping\("([^"]*)"\)',source)
    prefix=prefix.group(1) if prefix else ''
    for method,route in re.findall(r'@(Get|Post|Put|Delete)Mapping\("([^"]*)"\)',source):
        actual.add((method.lower(),normalize(prefix+route)))
documented={(method,normalize(path)) for path,operations in spec['paths'].items() for method in operations if method in ['get','post','put','delete']}
assert actual<=documented, actual-documented
for path,operations in spec['paths'].items():
    for method,operation in operations.items():
        assert operation['responses']
        for name in re.findall(r'\{([^}]+)\}',path):
            assert any(p['name']==name and p['in']=='path' and p['required'] for p in operation.get('parameters',[]))
# Detect embedded private keys and common credential token formats; never print matches.
findings=[]
patterns=[r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',r'AKIA[0-9A-Z]{16}',r'gh[pousr]_[A-Za-z0-9]{30,}',r'sk-[A-Za-z0-9]{30,}']
for directory in ['src','docs','scripts']:
    for path in (root/directory).rglob('*'):
        if path.is_file():
            content=path.read_text(errors='ignore')
            if any(re.search(pattern,content) for pattern in patterns):findings.append(str(path.relative_to(root)))
assert not findings, 'Possible secret in files: '+str(findings)
rows=list(csv.DictReader((root/'target/site/jacoco/jacoco.csv').open()))
services=[row for row in rows if row['PACKAGE']=='com.urlshortener.service']
covered=sum(int(row['LINE_COVERED']) for row in services);missed=sum(int(row['LINE_MISSED']) for row in services)
coverage=covered/(covered+missed)
assert coverage>.8,coverage
report={'controller_operations':len(actual),'documented_operations':len(documented),'core_service_line_coverage':coverage,'covered_lines':covered,'missed_lines':missed,'credential_pattern_findings':findings,'secret_scan_scope':'Current src/docs/scripts only; pattern scan does not establish absence of every possible secret or scan unavailable Git history.'}
(root/'logs').mkdir(exist_ok=True)
(root/'logs/artifact-verification.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
