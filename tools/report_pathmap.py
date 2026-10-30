#!/usr/bin/env python3
"""Join every cornerstone evaluator and both compiled backends without missing cells."""
import argparse
import csv
import hashlib
import json
import os
from collections import defaultdict
from pathlib import Path
from report_cornerstones import tables, group, GROUPS, EVALUATORS, md_table


def csv_rows(path):
    with path.open() as f:
        return list(csv.DictReader(f))


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--benchmarks',type=Path,required=True)
    p.add_argument('--openfst',type=Path,required=True)
    p.add_argument('--references',type=Path,required=True)
    p.add_argument('--zipper',type=Path,required=True)
    p.add_argument('--bundle',type=Path,required=True)
    p.add_argument('--output',type=Path,default=Path('docs/pathmap-benchmarks'))
    args=p.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((args.bundle/'manifest.json').read_text())
    artifacts={(a['family'],a['name'],a['variant'],a['dialect']):a for a in manifest['artifacts']}
    assert len(artifacts)==108 and manifest['workloads']==36
    for a in artifacts.values():
        assert hashlib.sha256((args.bundle/a['source']).read_bytes()).hexdigest()==a['sha256']
    primary,compilation,queens=tables(args.benchmarks/'existing/trieBenchmarkReport.log')
    large,=tables(args.benchmarks/'existing/zipperLargeBenchmarkReport.log')
    assert len(primary)==22 and len(large)==14
    zipper=json.loads((args.zipper/'summary.json').read_text())
    assert zipper['measured']==3
    for r in zipper['runs']:
        old=primary[r['index']]
        assert old['evalZ ms']=='n/a' and int(old['result paths'].replace(',',''))==int(r['paths'])
        old['evalZ ms']=r['evalZ_ms']
    refs={(r['name'],r['dialect']):r for r in csv_rows(args.references/'reference.csv')}
    assert len(refs)==44 and json.loads((args.references/'summary.json').read_text())['success']
    engines={}
    for engine,root in [('Rust PathMap',args.benchmarks),('C++ OpenFst',args.openfst)]:
        assert json.loads((root/'summary.json').read_text())['success']
        for family in ('programs','large'):
            for r in csv_rows(root/'native'/family/'native.csv'):
                key=engine,family,r['name'],r['dialect']
                assert key not in engines
                engines[key]=r
    joined=[];case_groups=defaultdict(list)
    for family,rows in [('programs',primary),('large',large)]:
        for r in rows:
            name=r['benchmark'];variant=r['variant' if family=='programs' else 'size'];count=int(r['result paths'].replace(',',''))
            case=[]
            def add(evaluator,ms,notes='',compile_ms='',graph_ms='',source='',level=''):
                assert ms!='n/a', (name,variant,evaluator)
                row=dict(family=family,example=name,variant=variant,evaluator=evaluator,runtime_ms=ms,
                         paths=count,session_setup_ms=compile_ms,graph_compile_ms=graph_ms,
                         source=source,rust_opt_level=level,notes=notes)
                case.append(row);joined.append(row)
            for e in EVALUATORS if family=='programs' else ('evalTrie','evalZ'):
                add(e,r[e+' ms'],r.get('graph note',r.get('note','')))
            if family=='programs':
                for dialect in ('bits','bytes'):
                    ref=refs[f'{name}/{variant}',dialect]
                    assert ref['checked']=='exact' and int(ref['paths'])==count
                    add('Zippy_'+dialect+' reference',ref['reference_ms'],'independent set representation')
            for engine,dialects in [('C++ OpenFst',('symbols','bits','bytes')),
                                    ('Rust PathMap',('bytes-interned','bytes-strings','bits-bytes'))]:
                for dialect in dialects:
                    n=engines[engine,family,f'{name}/{variant}',dialect]
                    assert int(n['paths'])==count and n['checked']=='exact'
                    source=artifacts[family,name,variant,dialect]['source'] if engine=='Rust PathMap' else ''
                    note=('precomputed literal' if n['precomputed'].lower()=='true' else 'optimized graph')
                    add(engine+' '+dialect,n['native_ms'],note,n['compile_ms'],n['graph_compile_ms'],source,n.get('rust_opt_level',''))
            case_groups[group(name)].append((name,variant,count,case))
    assert len(joined)==398
    with (args.output/'all-evaluators.csv').open('w',newline='') as f:
        w=csv.DictWriter(f,fieldnames=list(joined[0]));w.writeheader();w.writerows(joined)
    for key,title in GROUPS.items():
        text=[f'# {title}\n','[All examples](README.md) · Milliseconds per evaluation.\n']
        for name,variant,count,case in case_groups[key]:
            text.extend([f'## {name} — {variant}\n',f'Result: **{count:,} paths**.\n'])
            def link(r):
                if not r['source']:return r['evaluator']
                relative=os.path.relpath(args.bundle/r['source'],args.output)
                return f"[{r['evaluator']}]({relative})"
            text.append(md_table(('Evaluator / compiled encoding','Runtime ms','Execution'),
                                 [(link(r),r['runtime_ms'],r['notes']) for r in case]))
            text.append(md_table(('Compiled backend','Graph/source compile ms','Session setup ms¹','Rust opt-level'),
                                 [(r['evaluator'],r['graph_compile_ms'],r['session_setup_ms'],r['rust_opt_level']) for r in case if r['session_setup_ms']]))
        (args.output/(key+'.md')).write_text('\n'.join(text))
    intro=['# PathMap cornerstone results\n',
           'Recorded on 2026-10-30 on a Symba h4d xlarge (192 AMD EPYC Turin CPUs).\n',
           'Every cornerstone is listed separately: five Scala evaluators, two digit references, three C++ dialects, and three Rust byte encodings. Supplementary large cases retain their two Scala evaluators and all six native variants. All 398 runtime cells are measured and every native cornerstone result was checked exactly.\n',
           md_table(('Example group','Variants'),[(f'[{title}]({key}.md)',len(case_groups[key])) for key,title in GROUPS.items()]),
           'The [complete CSV](all-evaluators.csv) includes native session setup and graph/source compiler times. The Rust review bundle contains 108 sources, graph dumps, exact inputs and independent expected results.\n',
           'Native timings include graph execution and exact result cardinality; compilation, input construction, protocol IO and output enumeration are excluded. Constant-folded outputs are marked. Rust kernels always use release level 3; the table records the size-based generated-code level. Session setup can hit the compilation cache and is not a cold compiler comparison.\n',
           'The full matrix ran with eight benchmark workers and four shards per family while validation used other cores. Treat small timing differences as noise. The original Scala reports, digit-reference runs and extra zipper runs have their own warmup policies; raw evidence is retained. The three source evalZ cases omitted by the legacy report were measured separately with exact checks.\n',
           '¹ Native session setup includes any compilation/cache lookup and process startup.\n']
    (args.output/'README.md').write_text('\n'.join(intro))
    (args.output/'summary.json').write_text(json.dumps(dict(cases=36,measurements=len(joined),missing=0,exactNativeCornerstones=216),indent=2)+'\n')


if __name__=='__main__':
    main()
