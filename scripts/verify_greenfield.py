"""Rebuild delivered sources in an initially empty project directory; no secrets copied."""
from pathlib import Path
import shutil,subprocess,tempfile,json
root=Path(__file__).resolve().parent.parent
with tempfile.TemporaryDirectory(prefix='greenfield-',dir=root/'logs') as work:
    directory=Path(work)
    assert not list(directory.iterdir())
    shutil.copy2(root/'pom.xml',directory/'pom.xml')
    shutil.copytree(root/'src',directory/'src')
    with (root/'logs/greenfield-build.log').open('w') as log:
        result=subprocess.run(['mvn','-o','-Daether.syncContext.named.factory=noop','clean','verify'],cwd=directory,stdout=log,stderr=subprocess.STDOUT)
    report={'initial_directory_empty':True,'secrets_copied':False,'source':'Delivered Java source and pom.xml','exit_code':result.returncode,'pass':result.returncode==0,'scope':'Reproducible build from delivered sources, not evidence of the original agent authoring a project from requirements.'}
    (root/'logs/greenfield-verification.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2))
    assert result.returncode==0
