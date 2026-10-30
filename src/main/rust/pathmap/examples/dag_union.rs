//! Reproduce native PathMap union's repeated work on distinct shared DAGs.
//! The input maps have O(depth) physical states and different accepted lengths.
//! Run with `cargo run --release --example dag_union -- 20` (maximum depth).
use std::{hint::black_box, time::Instant};
use zippy_pathmap::*;
fn words(depth: usize) -> Space {
    let mut map = singleton(&[]);
    for _ in 0..depth {
        let mut next = Space::new();
        next.write_zipper_at_path(&[0]).graft_map(map.clone());
        next.write_zipper_at_path(&[255]).graft_map(map);
        map = next;
    }
    map
}
fn main() {
    let maximum = std::env::args()
        .nth(1)
        .map(|n| n.parse::<usize>().unwrap())
        .unwrap_or(16);
    println!("depth,left_paths,right_paths,result_paths,native_join_ns");
    for depth in (4..=maximum).step_by(4) {
        let left = words(depth);
        let right = words(depth - 1);
        let start = Instant::now();
        let result = black_box(left.join(&right));
        let elapsed = start.elapsed().as_nanos();
        let expected = count(&left) + count(&right);
        assert_eq!(count(&result), expected);
        println!(
            "{depth},{},{},{expected},{elapsed}",
            count(&left),
            count(&right)
        );
    }
}
