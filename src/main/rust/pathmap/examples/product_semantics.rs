//! ProductZipper's factor values are observable and stitch points are leaves.
//! These observations explain why Zippy composition cannot use it unchanged.
use zippy_pathmap::*;
fn main() {
    for (name, a, b) in [
        ("prefix-free", literal_text("2 1 1 1 2"), singleton(&[3])),
        (
            "terminal-prefix",
            literal_text("2 1 1 2 1 2"),
            singleton(&[3]),
        ),
        ("epsilon-primary", literal_text("2 0 1 1"), singleton(&[3])),
        (
            "epsilon-secondary",
            singleton(&[1]),
            literal_text("2 0 1 3"),
        ),
        ("empty-secondary", singleton(&[1]), Space::new()),
    ] {
        let mut p = ProductZipper::new(a.read_zipper(), [b.read_zipper()]);
        let mut observed = Vec::new();
        if p.is_val() {
            observed.push(p.path().to_vec());
        }
        while p.to_next_val() {
            observed.push(p.path().to_vec());
        }
        let composed = enumerate(&compose(&a, &b))
            .map(|p| p.to_vec())
            .collect::<Vec<_>>();
        println!("{name}: Zippy={composed:?}; ProductZipper values={observed:?}");
    }
}
