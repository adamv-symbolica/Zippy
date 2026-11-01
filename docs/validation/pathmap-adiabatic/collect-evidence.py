from pathlib import Path
import json,csv,shutil,gzip,collections,hashlib
raw=Path('output/pathmap-adiabatic')
out=Path('docs/validation/pathmap-adiabatic');out.mkdir(parents=True,exist_ok=True)
def cp(source,target):
 target=out/target;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(raw/source,target)
 if target.suffix==".csv":target.write_bytes(target.read_bytes().replace(b"\r\n",b"\n"))
 if target.suffix==".log" and target.stat().st_size:target.write_bytes(target.read_bytes().rstrip()+b"\n")
def read(path):return json.loads((raw/path).read_text())
def csvs(path):return list(csv.DictReader((raw/path).open()))
assert 'morkl.pathmap.root=/home/ubuntu/Zippy-bulk-final' in (raw/'tests/tests.log').read_text()
assert read('tests/result.json')['success'] and read('tests/result.json')['fullSuite']
assert read('radix/result.json')['success'] and read('radix/result.json')['fullSuite']
for folder in ['after','before','openfst']:
 assert read(folder+'/summary.json')['success']
 for f in (raw/folder/'native').glob('*/native.csv'):
  if '--shard-' not in str(f):cp(f.relative_to(raw),f.relative_to(raw))
 cp(folder+'/summary.json',folder+'/summary.json')
 if folder!='before':cp(folder+'/native-micro.csv',folder+'/native-micro.csv')
for f in (raw/'after/existing').glob('*'):
 if f.is_file():cp(f.relative_to(raw),f.relative_to(raw))
for folder in ['references','zipper']:
 for f in (raw/folder).iterdir():
  if f.is_file():cp(f.relative_to(raw),f.relative_to(raw))
for name in ['native.csv']:
 cp('after/reuse/cursor-reuse/'+name,'after/reuse/cursor-reuse/'+name)
cp('after/reuse/cursor-reuse/native.csv','cursor-reuse.csv')
cp('tests/result.json','native-tests.json');cp('tests/audit/summary.json','audit-summary.json')
cp('radix/result.json','radix-tests.json');cp('radix/audit/summary.json','radix-summary.json')
for prefix in ['tests','radix']:
 data=(raw/prefix/'audit/checks.jsonl').read_bytes()
 (out/(prefix+'-checks.jsonl.gz')).write_bytes(gzip.compress(data,mtime=0))
 (out/(prefix+'.log.gz')).write_bytes(gzip.compress((raw/prefix/'tests.log').read_bytes(),mtime=0))
rows=[json.loads(l) for l in (raw/'tests/audit/checks.jsonl').read_text().splitlines()]
starts=collections.Counter((r['case'],r['dialect']) for r in rows if r['status']=='start')
passes=collections.Counter((r['case'],r['dialect']) for r in rows if r['status']=='pass')
assert starts==passes and all(n==1 for n in starts.values())
assert len(starts)==read('tests/audit/summary.json')['cases']
ledger=dict(success=True,uniqueCases=len(starts),eachCaseStartedExactlyOnce=True,eachCasePassedExactlyOnce=True,
 passed=dict(collections.Counter(k[1] for k in passes)),sourceCommit=read('source-manifest.json')['commit'])
(out/'replay-ledger-check.json').write_text(json.dumps(ledger,indent=2)+'\n')
radix=[json.loads(l) for l in (raw/'radix/audit/checks.jsonl').read_text().splitlines()]
rs=read('radix/audit/summary.json');counts=collections.Counter((r['case'],r['direction']) for r in radix if r.get('status')=='pass')
assert len(counts)==rs['checks'] and all(n==1 for n in counts.values()) and len(counts)==rs['cases']*8
(out/'radix-ledger-check.json').write_text(json.dumps(dict(success=True,uniqueChecks=len(counts),frames=rs['cases'],directions=rs['passed']),indent=2)+'\n')
for f in ['summary.json','median.csv','samples.csv','execution.log','export.log','runtime.log','rustc.log']:
 cp('oeis/'+f,'oeis/'+f)
assert read('oeis/summary.json')['success']
checks=read('export-checks/summary.json');assert checks['success'] and checks['sources']==144
cp('export-checks/summary.json','standalone-export-checks.json')
for f in ['model.log','clippy.log','upstream.log','python-tests.log','rustfmt.log','environment.json','source-manifest.json','source-verification.json','node.json']:
 cp(f,f)
assert read('source-verification.json')['success']
families=[p.name for p in (raw/'after/native').iterdir() if p.is_dir() and '--shard-' not in p.name]
allrows=[r for f in families for r in csvs('after/native/'+f+'/native.csv')]
assert len(allrows)==664
coverage=dict(success=True,workloads=len(set((r['family'],r['name']) for r in allrows)),nativeRows=len(allrows),
 exactRows=sum(r['checked']=='exact' for r in allrows),cardinalityOnlyRows=sum(r['checked']=='cardinality' for r in allrows),
 encodings=dict(collections.Counter(r['dialect'] for r in allrows)),families=dict(collections.Counter(r['family'] for r in allrows)),
 benchmarkJobs=read('after/summary.json')['total'],cppBenchmarkJobs=read('openfst/summary.json')['total'],
 nativeMicroRows=len(csvs('after/native-micro.csv')),cppMicroRows=len(csvs('openfst/native-micro.csv')),cursorReuseRows=len(csvs('after/reuse/cursor-reuse/native.csv')),
 sourceCommit=read('source-manifest.json')['commit'])
(out/'benchmark-coverage.json').write_text(json.dumps(coverage,indent=2)+'\n')
print(json.dumps(coverage,indent=2))
