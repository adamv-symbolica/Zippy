#!/usr/bin/env python3
"""Join every cornerstone evaluator and both compiled backends without missing cells."""
import argparse
import csv
import hashlib
import json
import os
import math
import statistics
from collections import defaultdict
from pathlib import Path
from report_cornerstones import tables, group, GROUPS, EVALUATORS, md_table


def csv_rows(path):
    with path.open() as f:
        return list(csv.DictReader(f))


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--isolated',type=Path)
    p.add_argument('--cpp-bundle',type=Path,required=True)
    p.add_argument('--commit',required=True)
    p.add_argument('--before',type=Path,required=True)
    p.add_argument('--oeis',type=Path,required=True)
    p.add_argument('--benchmarks',type=Path,required=True)
    p.add_argument('--openfst',type=Path,required=True)
    p.add_argument('--references',type=Path,required=True)
    p.add_argument('--zipper',type=Path,required=True)
    p.add_argument('--bundle',type=Path,required=True)
    p.add_argument('--output',type=Path,default=Path('docs/pathmap-adiabatic-benchmarks'))
    args=p.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((args.bundle/'manifest.json').read_text())
    artifacts={(a['family'],a['name'],a['variant'],a['dialect']):a for a in manifest['artifacts']}
    assert len(artifacts)==144 and manifest['workloads']==36
    for a in artifacts.values():
        assert hashlib.sha256((args.bundle/a['source']).read_bytes()).hexdigest()==a['sha256']
    cpp=json.loads((args.cpp_bundle/'manifest.json').read_text())
    cpp_artifacts={(a['family'],a['name'],a['variant'],a['dialect']):a for a in cpp['artifacts']}
    assert len(cpp_artifacts)==108
    for a in cpp_artifacts.values():
        assert hashlib.sha256((args.cpp_bundle/a['source']).read_bytes()).hexdigest()==a['sha256']
    primary,compilation,queens=tables(args.benchmarks/'existing/trieBenchmarkReport.log')
    comp={(r['benchmark'],r['variant']):r for r in compilation}
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
                         source=source,rust_opt_level=level,notes=notes,
                         scala_compile_ms=comp[name,variant]['total compile ms'] if evaluator.startswith('ROG') else '',
                         scala_compile_run_ms=comp[name,variant]['compile+ROG execT ms'] if evaluator=='ROG execT' else '')
                case.append(row);joined.append(row)
            for e in EVALUATORS if family=='programs' else ('evalTrie','evalZ'):
                note={'eval Set':'reference path sets','evalTrie':'symbol trie','evalZ':'zipper evaluator'}.get(e,'')
                if e.startswith('ROG'):
                    note=r.get('graph note','').split('; direct evalZ')[0]
                add(e,r[e+' ms'],note)
            if family=='programs':
                for dialect in ('bits','bytes'):
                    ref=refs[f'{name}/{variant}',dialect]
                    assert ref['checked']=='exact' and int(ref['paths'])==count
                    add('Zippy_'+dialect+' reference',ref['reference_ms'],'independent set representation')
            for engine,dialects in [('C++ OpenFst',('symbols','bits','bytes')),
                                    ('Rust PathMap',('bytes-interned','bytes-strings','bytes-length','bits-bytes'))]:
                for dialect in dialects:
                    n=engines[engine,family,f'{name}/{variant}',dialect]
                    assert int(n['paths'])==count and n['checked']=='exact'
                    source=artifacts[family,name,variant,dialect]['source'] if engine=='Rust PathMap' else ''
                    if engine=='C++ OpenFst':
                        a=cpp_artifacts[family,name,variant,dialect]
                        assert int(n['generated_bytes'])==a['bytes']
                        source='cpp:'+a['source']
                    if engine=='Rust PathMap':
                        a=artifacts[family,name,variant,dialect]
                        assert int(n['generated_bytes'])==a['bytes'] and int(n['graph_nodes'])==a['graphNodes']
                        assert int(n['rust_opt_level'])==a['rustOptLevel'] and n['precomputed'].lower()==str(a['precomputed']).lower()
                    note=('precomputed literal' if n['precomputed'].lower()=='true' else 'optimized graph')
                    add(engine+' '+dialect,n['native_ms'],note,n['compile_ms'],n['graph_compile_ms'],source,n.get('rust_opt_level',''))
            case_groups[group(name)].append((name,variant,count,case))
    assert len(joined)==434
    with (args.output/'all-evaluators.csv').open('w',newline='') as f:
        w=csv.DictWriter(f,fieldnames=list(joined[0]),lineterminator="\n");w.writeheader();w.writerows(joined)
    for key,title in GROUPS.items():
        text=[f'# {title}\n','[All examples](README.md) · Milliseconds per evaluation.\n']
        for name,variant,count,case in case_groups[key]:
            text.extend([f'## {name} — {variant}\n',f'Result: **{count:,} paths**.\n'])
            def link(r):
                if not r['source']:return r['evaluator']
                root=args.cpp_bundle if r['source'].startswith('cpp:') else args.bundle
                source=r['source'].removeprefix('cpp:')
                relative=os.path.relpath(root/source,args.output)
                return f"[{r['evaluator']}]({relative})"
            text.append(md_table(('Evaluator / compiled encoding','Runtime ms','Execution'),
                                 [(link(r),r['runtime_ms'],r['notes']) for r in case]))
            text.append(md_table(('Compiled backend','Graph/source compile ms','Session setup ms¹','Rust opt-level'),
                                 [(r['evaluator'],r['graph_compile_ms'],r['session_setup_ms'],r['rust_opt_level']) for r in case if r['session_setup_ms']]))
            if (name,variant) in comp:
                c=comp[name,variant]
                text.append(md_table(('Scala graph pipeline','Prep compile ms','Graph compile ms','Total compile ms','Compile + ROG execT ms'),
                                     [('optimized executor',c['prep compile ms'],c['graph compile ms'],c['total compile ms'],c['compile+ROG execT ms'])]))
        if key=='n-queens':
            text.append('Independent bitboard reference from the original report (not a Zippy evaluator):\n')
            text.append(md_table(('Benchmark','Variant','Result','Runtime ms'),[(r['benchmark'],r['variant'],r['result'],r['ms']) for r in queens]))
        (args.output/(key+'.md')).write_text('\n'.join(text))
    intro=['# PathMap adiabatic reuse: cornerstone results\n',
           'Recorded on 2026-11-01 on a Symba h4d xlarge (192 AMD EPYC Turin CPUs).\n',
           'Every cornerstone is listed separately: five Scala evaluators, two digit references, three C++ dialects, and four Rust byte encodings. Supplementary large cases retain their two Scala evaluators and all seven native variants. All 434 runtime cells are measured and every native cornerstone result was checked exactly.\n',
           md_table(('Example group','Variants'),[(f'[{title}]({key}.md)',len(case_groups[key])) for key,title in GROUPS.items()]),
           'The [complete CSV](all-evaluators.csv) includes native session setup, graph/source compiler times, and Scala graph compile + execution. The [Rust review bundle](../compiled-cornerstones-rust/README.md) contains 144 sources, graph dumps, exact inputs and independent expected results. [Validation evidence](../validation/pathmap-adiabatic/README.md) retains all nine native benchmark families, microbenchmarks and the fourteen original evaluator reports.\n',
           '[Translation and performance notes](performance.md) explain the main implementation choices and include the large fold and product results.\n',
           'Native timings include graph execution and exact result cardinality; compilation, input construction, protocol IO and output enumeration are excluded. Constant-folded outputs are marked. Rust kernels always use release level 3; the table records the size-based generated-code level. Session setup can hit the compilation cache and is not a cold compiler comparison.\n',
           'The full matrix ran with four Rust and four C++ benchmark workers and four shards per family while validation used other cores. Treat small timing differences as noise. The original Scala reports, digit-reference runs and extra zipper runs have their own warmup policies; raw evidence is retained. The three source evalZ cases omitted by the legacy report were measured separately with exact checks.\n',
           '¹ Native session setup includes any compilation/cache lookup and process startup.\n']
    (args.output/'README.md').write_text('\n'.join(intro))
    write_performance(args)
    write_oeis(args)
    (args.output/'summary.json').write_text(json.dumps(dict(cases=36,measurements=len(joined),missing=0,exactNativeCornerstones=252),indent=2)+'\n')


def write_performance(args):
    families=('programs','large','algebra','iter-range-tail','algebra-scaling','construction','independent-product','cost-counter','cost-correlation')
    def matrix(root):
        rows=[r for f in families for r in csv_rows(root/'native'/f/'native.csv')]
        result={(r['family'],r['name'],r['dialect']):r for r in rows}
        assert len(result)==len(rows)
        return result
    before,after=matrix(args.before),matrix(args.benchmarks)
    assert len(before)==498 and len(after)==664
    assert all(int(after[k]['paths'])==int(v['paths']) for k,v in before.items())
    comparison=[]
    for key,r in sorted(after.items()):
        old=before.get(key)
        comparison.append(dict(family=r['family'],name=r['name'],dialect=r['dialect'],paths=r['paths'],checked=r['checked'],
            before_ms=old['native_ms'] if old else '',after_ms=r['native_ms'],
            speedup=float(old['native_ms'])/float(r['native_ms']) if old else '',
            before_source_bytes=old['generated_bytes'] if old else '',after_source_bytes=r['generated_bytes'],
            precomputed=r['precomputed'],samples=r['samples']))
    with (args.output/'before-after.csv').open('w',newline='') as f:
        w=csv.DictWriter(f,fieldnames=list(comparison[0]),lineterminator="\n");w.writeheader();w.writerows(comparison)
    def geo(rows):return math.exp(statistics.mean(math.log(r['speedup']) for r in rows))
    paired=[r for r in comparison if r['before_ms']]
    reuse=csv_rows(args.benchmarks/'reuse/cursor-reuse/native.csv')
    assert len(reuse)==18
    pairs=[]
    for width,size in sorted({(int(r['width']),int(r['inputs'])) for r in reuse}):
        a,b=([r for r in reuse if int(r['width'])==width and int(r['inputs'])==size and r['reuse']==v][0] for v in ('false','true'))
        assert a['paths']==b['paths'] and a['samples']==b['samples']=='5'
        pairs.append((width,size,float(a['nanos'])/1e6,float(b['nanos'])/1e6,float(a['nanos'])/float(b['nanos']),a,b))
    text=['# Adiabatic cursor reuse and before/after performance\n',
        f'All measurements use the same Symba h4d xlarge. The baseline is commit `00c4163`; the final compiler/runtime is `{args.commit}`. Timings include execution and exact result cardinality. The complete [664-row matrix](before-after.csv) contains every native workload, including the new length-prefix encoding. All 498 common rows have matching result cardinalities.\n',
        'The baseline ran eight benchmark workers; the final run used four while validation, C++ benchmarks and exports used other cores. These are throughput-run measurements. Large effects are useful; small differences need isolated repeats. No warmup, counting or reference-representation equivalence is assumed across the different evaluator implementations.\n',
        '## Cursor reuse alone\n',
        'This ablation compiles the same optimized graphs twice with `morkl.pathmap.reuse=false/true`, using the same runtime, counting method, inputs and five timing samples. The measured program walks a fixed byte prefix and reconstructs it. Every output was checked exactly before timing.\n',
        md_table(('Prefix bytes','Input paths','Reuse off ms','Reuse on ms','Speedup'),[(w,f'{n:,}',f'{a:.3f}',f'{b:.3f}',f'{x:.2f}×') for w,n,a,b,x,_,_ in pairs]),
        'The optimized generated scanners contain one read cursor, one output map/write cursor, zero cursor forks and zero `concat_path` calls. Their final head projection writes from the child mask directly, using native masked grafts for dense masks at empty output focuses. The disabled versions retain nested maps, cursor forks and copied concatenations. The [ablation sources](../../output/pathmap-adiabatic/after/reuse/cursor-reuse/) and [raw rows](../validation/pathmap-adiabatic/cursor-reuse.csv) are retained.\n',
        '## Complete implementation comparison\n',
        'This includes cursor/path/output reuse, faster checked physical-node cardinality, helper sharing and proven scanner specialization. Dense heads and multi-branch tails use native bulk algebra; a discarded per-byte version regressed dense-prefix and OEIS build performance. It does not isolate those contributions. A speedup below 1 is a regression.\n',
        md_table(('Encoding','Paired rows','Geometric mean speedup','Median speedup','Worst speedup'),[(d,len(rs),f'{geo(rs):.2f}×',f'{statistics.median(r["speedup"] for r in rs):.2f}×',f'{min(r["speedup"] for r in rs):.2f}×') for d in ('bytes-interned','bytes-strings','bits-bytes') for rs in [[r for r in paired if r['dialect']==d]]]),
        md_table(('Family','Paired rows','Geometric mean speedup','Median speedup'),[(f,len(rs),f'{geo(rs):.2f}×',f'{statistics.median(r["speedup"] for r in rs):.2f}×') for f in (*families[:6],'incremental-union',*families[6:]) for rs in [[r for r in paired if r['family']==f]]]),
        'The eight largest improvements and eight largest regressions are shown below; the CSV retains every row. Some constant programs mostly measure result cardinality, and are marked in the CSV.\n',
        md_table(('Workload','Encoding','Before ms','After ms','Speedup'),[(r['family']+'/'+r['name'],r['dialect'],r['before_ms'],r['after_ms'],f'{r["speedup"]:.2f}×') for r in sorted(paired,key=lambda r:r['speedup'],reverse=True)[:8]+sorted(paired,key=lambda r:r['speedup'])[:8]]),
        '## Length-prefix folds and OEIS\n',
        'A generic ordered fold initially rescanned all remaining length-prefixed heads at each step. The compiler now proves the ordinary length scanner and streams native fixed-depth cursors once in payload order. This preserves string ordering independently of the leading length byte. The 32,768-head fold is included in construction and in the complete CSV. The old generic length-prefix implementation has no completed timing at that size; it is not included as a claimed speedup.\n',
        md_table(('Fold heads','Interned bytes ms','Terminated strings ms','Length-prefixed strings ms','Fibonacci bits ms'),[(n,*[after['incremental-union',str(n),d]['native_ms'] for d in ('bytes-interned','bytes-strings','bytes-length','bits-bytes')]) for n in (512,2048,8192,32768)]),
        'The [OEIS report](../OEIS_BENCHMARK.md) compares Zippy with the upstream PathMap algorithm on all 375,842 records. Execution and counting are reported separately. Native bulk length projection follows a proof of all 256 graph branches; no OEIS-specific runtime primitive or encoding metadata is introduced.\n']
    if args.isolated:
        summary=json.loads((args.isolated/'summary.json').read_text());assert summary['success']
        samples=csv_rows(args.isolated/'samples.csv');assert len(samples)==42
        isolated=[]
        for name in sorted({r['name'] for r in samples}):
            for dialect in ('bytes-interned','bytes-strings','bits-bytes'):
                groups=[[float(r['native_ms']) for r in samples if r['name']==name and r['dialect']==dialect and r['version']==v] for v in ('before','after')]
                assert all(len(g)==3 for g in groups)
                a,b=map(statistics.median,groups)
                isolated.append((name,dialect,f'{a:.6f}',f'{b:.6f}',f'{a/b:.2f}×'))
        text.extend(['## Single-worker regression checks\n',
            'The two largest throughput-run regressions were repeated in three sequential JVM pairs, pinned to CPUs 80–87. Each JVM retains five native timing samples. These checks are separate from the original matrix; no rows were replaced.\n',
            md_table(('Workload','Encoding','Before ms','After ms','Speedup'),isolated),
            'The string diff/restriction loss did not reproduce in these sequential runs. The 80-node Datalog workload with interned bytes retains a **0.52× speedup** (about 1.92× slower); this remains a performance limitation. String and Fibonacci encodings improved modestly on the same workload. [Raw samples and run metadata](../validation/pathmap-adiabatic/isolated-regressions/summary.json) are retained.\n'])
    (args.output/'performance.md').write_text('\n'.join(text))


def write_oeis(args):
    report=json.loads((args.oeis/'summary.json').read_text());assert report['success'] and report['exactKeySets']
    rows=csv_rows(args.oeis/'median.csv')
    by={(r['engine'],r['stage']):r for r in rows};assert len(by)==30
    stages=['build_map','drophead_cold','drophead_steady','query']+['contents_'+str(i) for i in range(11)]
    data=report['dataset']
    text=['# OEIS with Zippy\n',
        'Zippy reproduces [PathMap’s OEIS benchmark](https://github.com/Adam-Vandervorst/PathMap/blob/master/benches/oeis.rs): build a sequence trie, index suffixes at offsets 0–10, and query the prefix `1,2,3,5,8,13`. The eleven per-offset content counts are measured separately as well. The upstream file contains the content-count routine but does not register it among its benchmark arguments.\n',
        'The full local OEIS snapshot contains **375,842 records** (2024-09-12). Numeric symbols use the upstream signed big-endian `BigInt` bytes, preceded by one length byte. This uses the same framing as the new string translation, with numeric payloads supplied as existing byte paths. The original PathMap comparison retains `usize` terminal IDs; Zippy stores unit terminals and compares the exact key sets. Duplicate sequences therefore have one terminal in both structures.\n',
        '## Results\n',
        'Median of five samples on the Symba h4d xlarge, 2026-11-01. All times are milliseconds. Compilation, text parsing, record-trie ingestion, protocol IO and exact key-set comparisons are outside execution timings. Native execution and cardinality are separate columns. For content stages, execution includes counting.\n',
        md_table(('Stage','Paths','PathMap exec ms','Zippy exec ms','PathMap count ms','Zippy count ms'),[(stage,f'{int(by["Zippy",stage]["paths"]):,}',by['PathMap',stage]['exec_ms'],by['Zippy',stage]['exec_ms'],by['PathMap',stage]['count_ms'],by['Zippy',stage]['count_ms']) for stage in stages]),
        'Cold drophead starts from the original map. Steady drophead starts from the indexed map, matching the repeated in-place upstream benchmark. Zippy’s pure operation returns the corresponding indexed key set. The upstream steady sample does not include a cardinality call; its zero count column is intentional. Query cardinality can differ in cost because unit-valued PathMap representation shares more state than maps retaining sequence IDs. These are not interchangeable storage or ingestion benchmarks.\n',
        'Exact native/Zippy key-set comparisons passed for the build, all offsets and the query: **373,311** unique initial sequences, **3,996,625** indexed paths, and **222** matching query paths. Independent small-model tests include negative and multibyte integers, duplicate sequences, incomplete suffixes and short records.\n',
        '## Program and reproduction\n',
        '[OEISBenchmark.scala](../src/test/scala/OEISBenchmark.scala) constructs ordinary byte-language operations and functions. The build drops the four-byte record identifier and wraps offset zero. Drophead shares ten calls to a proven ordinary length-dispatch helper. Query uses native prefix restriction. No new Zippy expression case or OEIS runtime opcode is added. The [generated programs and harness](../output/pathmap-adiabatic/oeis/sources/) contain fourteen standalone stage programs and the combined native comparison harness.\n',
        '```sh\npython3 tools/benchmark_oeis.py --data /path/to/oeis_stripped.txt \\\n  --output output/oeis-rust --samples 5\n```\n',
        f'Dataset: `{data["bytes"]:,}` bytes; SHA-256 `{data["sha256"]}`. The large input is not committed. [Raw samples](validation/pathmap-adiabatic/oeis/samples.csv), [medians](validation/pathmap-adiabatic/oeis/median.csv), [run metadata](validation/pathmap-adiabatic/oeis/summary.json) and [execution log](validation/pathmap-adiabatic/oeis/execution.log) are retained.\n']
    (args.output.parent/'OEIS_BENCHMARK.md').write_text('\n'.join(text))


if __name__=='__main__':
    main()
