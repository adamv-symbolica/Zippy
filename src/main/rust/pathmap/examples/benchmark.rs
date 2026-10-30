use std::{hint::black_box, time::Instant};
use zippy_pathmap::*;

fn measure(name: &str, size: usize, f: impl Fn() -> Space) {
    let expected = count(&f());
    for _ in 0..3 {
        black_box(count(&black_box(f())));
    }
    let start = Instant::now();
    black_box(count(&black_box(f())));
    let first = start.elapsed().as_nanos().max(1);
    let repeats = (10_000_000 / first).clamp(1, 4096) as u32;
    let mut samples = Vec::new();
    for _ in 0..5 {
        let start = Instant::now();
        for _ in 0..repeats {
            assert_eq!(black_box(count(&black_box(f()))), expected);
        }
        samples.push(start.elapsed().as_nanos() / repeats as u128);
    }
    samples.sort_unstable();
    println!("{name},{size},{expected},{},{}", samples[2], repeats);
}
fn main() {
    println!("operation,size,paths,nanos,repeats");
    for size in [16usize, 64, 256, 1024, 4096, 16384] {
        let mut a = Space::new();
        let mut b = Space::new();
        let mut disjoint = Space::new();
        let mut prefixes = Space::new();
        for i in 0..size as u32 {
            let p = i.to_be_bytes();
            a.set_val_at(concat_path(&p, &[0, 255]), ());
            if i % 4 == 0 {
                b.set_val_at(concat_path(&p, &[0, 255]), ());
                prefixes.set_val_at(p, ());
            }
            disjoint.set_val_at(concat_path(&[255], &p), ());
        }
        let suffix = singleton(&[4]).join(&singleton(&[5]));
        measure("union-subset", size, || unite(&a, &b));
        measure("union-disjoint", size, || unite(&a, &disjoint));
        measure("intersection-subset", size, || intersect(&a, &b));
        measure("intersection-disjoint", size, || intersect(&a, &disjoint));
        measure("subtract-subset", size, || subtract(&a, &b));
        measure("subtract-disjoint", size, || subtract(&a, &disjoint));
        measure("restriction", size, || restrict_to(&a, &prefixes));
        measure("raffination", size, || raffine(&a, &prefixes));
        measure("composition", size, || compose(&a, &suffix));
        measure("wrap", size, || wrap(&a, &[1, 2, 3, 4]));
        measure("unwrap", size, || unwrap(&a, &[0]));
        measure("tails-union", size, || tails_union(&a));
        measure("tails-intersection", size, || tails_intersection(&a));
        measure("prefix-closure", size, || prefix_closure(&a));
        measure("suffix-closure", size, || suffix_closure(&a, false));
        measure("tails-closure", size, || suffix_closure(&a, true));
        measure("head", size, || head_space(&a));
        measure("range-first", size, || range(&a, 0, 10));
        measure("range-last", size, || range(&a, -10, 0));
        measure("range-interior", size, || range(&a, 3, -3));
        measure("iter-reconstruct", size, || {
            let mut out = Space::new();
            let mut z = a.read_zipper();
            for byte in z.child_mask().iter() {
                z.descend_to_byte(byte);
                let tail = z.make_map();
                z.ascend(1);
                out.join_into(wrap(&tail, &[byte]));
            }
            out
        });
        measure("fixpoint-tails", size, || {
            let mut current = a.clone();
            loop {
                let next = unite(&current, &tails_union(&current));
                if equal(&current, &next) {
                    break current;
                }
                current = next;
            }
        });
    }
}
