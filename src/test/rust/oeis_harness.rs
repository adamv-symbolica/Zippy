#![allow(unused_imports, unused_variables, unused_mut, unused_parens, dead_code)]
// Zippy programs are inserted by exportOEISRust. The native comparison follows
// Adam-Vandervorst/PathMap benches/oeis.rs (MIT), including usize terminal IDs.
use num_bigint::BigInt;
use std::io::{BufRead, BufReader};
use std::time::Instant;
use zippy_pathmap::*;

// GENERATED_MODULES

fn execute(id: usize, c: &mut Context, e: &Env) -> Space {
    match id {
        // GENERATED_DISPATCH
        _ => unreachable!(),
    }
}

fn sequences(file: &str) -> Vec<(usize, Vec<u8>)> {
    let input = BufReader::new(std::fs::File::open(file).unwrap());
    input.lines().filter_map(|line| {
        let line = line.unwrap();
        if line.starts_with('#') || line.trim().is_empty() { return None; }
        let mut fields = line.split(',').map(str::trim);
        let id = fields.next().unwrap().strip_prefix('A').unwrap().parse().unwrap();
        let mut bytes = Vec::new();
        for number in fields.filter(|s| !s.is_empty()) {
            let payload = number.parse::<BigInt>().unwrap().to_signed_bytes_be();
            assert!(payload.len() <= 255);
            bytes.push(payload.len() as u8);
            bytes.extend_from_slice(&payload);
        }
        Some((id, bytes))
    }).collect()
}

fn native_build(seqs: &[(usize, Vec<u8>)]) -> pathmap::PathMap<usize> {
    let mut out = pathmap::PathMap::new();
    let mut z = out.write_zipper_at_path(&[0]);
    for (id, bytes) in seqs {
        if bytes.is_empty() { continue; }
        z.descend_to(bytes);
        if !z.is_val() { z.set_val(*id); }
        z.ascend(bytes.len());
    }
    drop(z);
    out
}

fn native_drop(map: &mut pathmap::PathMap<usize>) {
    let head = map.zipper_head();
    for offset in 1u8..=10 {
        let root = [offset];
        // These offsets are disjoint, exactly as in the upstream benchmark.
        let mut z = unsafe { head.write_zipper_at_exclusive_path_unchecked(&root) };
        z.graft(&unsafe { head.read_zipper_at_path_unchecked(&[offset-1]) });
        let origin = z.path().to_vec();
        for len in z.child_mask().iter() {
            if len == 0 { continue; }
            z.descend_to_existing_byte(len);
            z.join_k_path_into(len as usize, true);
            assert_eq!(z.ascend(1), 1);
        }
        z.reset();
        z.descend_to(&origin);
        z.join_k_path_into(1, true);
    }
}

fn query_mask() -> pathmap::PathMap<usize> {
    let mut result = pathmap::PathMap::new();
    for offset in 0u8..=10 {
        let mut p = vec![offset];
        for value in [1u8, 2, 3, 5, 8, 13] { p.extend([1, value]); }
        result.set_val_at(p, 0);
    }
    result
}

fn same_keys(actual: &Space, expected: &pathmap::PathMap<usize>) {
    let (mut a, mut b) = (actual.read_zipper(), expected.read_zipper());
    assert_eq!(a.is_val(), b.is_val());
    loop {
        let (am, bm) = (a.to_next_val(), b.to_next_val());
        assert_eq!(am, bm);
        if !am { break; }
        assert_eq!(a.path(), b.path());
    }
}

fn env(map: &Space) -> Env { Env { paths: vec![], spaces: vec![Some(map.clone())] } }
fn native_samples<F: FnMut() -> pathmap::PathMap<usize>>(stage: &str, samples: usize, mut f: F) -> pathmap::PathMap<usize> {
    let mut result = None;
    for sample in 0..samples {
        drop(result.take());
        let start = Instant::now();
        let map = std::hint::black_box(f());
        let elapsed = start.elapsed().as_nanos();
        let start = Instant::now();
        let paths = std::hint::black_box(map.val_count());
        let counting = start.elapsed().as_nanos();
        println!("PathMap,{stage},{sample},{elapsed},{counting},{paths}");
        result = Some(map);
    }
    result.unwrap()
}
fn zippy_samples(id: usize, stage: &str, samples: usize, c: &mut Context, e: &Env, expected: &pathmap::PathMap<usize>) -> Space {
    let mut result = None;
    for sample in 0..samples {
        drop(result.take());
        let start = Instant::now();
        let map = std::hint::black_box(execute(id,c,e));
        let elapsed = start.elapsed().as_nanos();
        let start = Instant::now();
        let paths = std::hint::black_box(count(&map));
        let counting = start.elapsed().as_nanos();
        assert_eq!(paths.to_string(), expected.val_count().to_string());
        println!("Zippy,{stage},{sample},{elapsed},{counting},{paths}");
        result = Some(map);
    }
    let result = result.unwrap(); same_keys(&result,expected); result
}

fn benchmark() {
    let args: Vec<String> = std::env::args().collect();
    let samples: usize = args.get(2).map_or(3,|n| n.parse().unwrap());
    assert!(samples>0);
    let seqs = sequences(&args[1]);
    eprintln!("OEIS records={}, nonempty={}",seqs.len(),seqs.iter().filter(|(_,s)| !s.is_empty()).count());
    let mut records = Space::new();
    let start = Instant::now();
    {
        let mut w = records.write_zipper();
        for (id, seq) in &seqs {
            if seq.is_empty() { continue; }
            w.descend_to((*id as u32).to_be_bytes()); w.descend_to(seq); w.set_val(()); w.reset();
        }
    }
    eprintln!("Record-trie ingestion_ns={} (outside Zippy execution)",start.elapsed().as_nanos());
    println!("engine,stage,sample,exec_ns,count_ns,paths");
    let mut c = Context::default();
    let built = native_samples("build_map",samples,|| native_build(&seqs));
    let zippy_built = zippy_samples(0,"build_map",samples,&mut c,&env(&records),&built);
    let indexed = native_samples("drophead_cold",samples,|| { let mut m=built.clone();native_drop(&mut m);m });
    let zippy_index = zippy_samples(1,"drophead_cold",samples,&mut c,&env(&zippy_built),&indexed);
    let mut steady = indexed.clone();
    for sample in 0..samples {
        let start=Instant::now();native_drop(&mut steady);let elapsed=start.elapsed().as_nanos();
        println!("PathMap,drophead_steady,{sample},{elapsed},0,{}",steady.val_count());
    }
    let _ = zippy_samples(1,"drophead_steady",samples,&mut c,&env(&zippy_index),&steady);
    let mask=query_mask();
    let found=native_samples("query",samples,|| indexed.restrict(&mask));
    let _=zippy_samples(2,"query",samples,&mut c,&env(&zippy_index),&found);
    let e=env(&zippy_index);
    for offset in 0u8..=10 {
        for sample in 0..samples {
            let start=Instant::now();let paths=std::hint::black_box(indexed.read_zipper_at_path(&[offset]).val_count());let elapsed=start.elapsed().as_nanos();
            println!("PathMap,contents_{offset},{sample},{elapsed},0,{paths}");
            let start=Instant::now();let sub=execute(3+offset as usize,&mut c,&e);let got=std::hint::black_box(count(&sub));let elapsed=start.elapsed().as_nanos();
            assert_eq!(got.to_string(),paths.to_string());
            println!("Zippy,contents_{offset},{sample},{elapsed},0,{got}");
        }
    }
    eprintln!("OEIS exact key-set checks passed for build, all ten offsets, and query");
}
fn main() {
    std::thread::Builder::new().stack_size(1024*1024*1024).spawn(benchmark).unwrap().join().unwrap();
}
