use std::collections::BTreeSet;
use zippy_pathmap::*;
type Set = BTreeSet<Vec<u8>>;
fn map(s: &Set) -> Space {
    let mut m = Space::new();
    for p in s {
        m.set_val_at(p, ());
    }
    m
}
fn same(m: Space, s: Set) {
    assert_eq!(
        enumerate(&m)
            .into_iter()
            .map(|p| p.to_vec())
            .collect::<Set>(),
        s
    );
    assert_eq!(count(&m).to_string(), s.len().to_string());
}

#[test]
fn algebra_against_independent_path_sets() {
    let mut seed = 0x5eedd00du64;
    let mut random = || {
        seed ^= seed << 13;
        seed ^= seed >> 7;
        seed ^= seed << 17;
        seed
    };
    for _ in 0..1200 {
        let mut generate = || {
            (0..random() % 25)
                .map(|_| {
                    (0..random() % 7)
                        .map(|_| (random() % 5) as u8)
                        .collect::<Vec<_>>()
                })
                .collect::<Set>()
        };
        let a = generate();
        let b = generate();
        let am = map(&a);
        let bm = map(&b);
        same(unite(&am, &bm), a.union(&b).cloned().collect());
        same(intersect(&am, &bm), a.intersection(&b).cloned().collect());
        same(subtract(&am, &bm), a.difference(&b).cloned().collect());
        same(
            restrict_to(&am, &bm),
            a.iter()
                .filter(|p| b.iter().any(|q| p.starts_with(q)))
                .cloned()
                .collect(),
        );
        same(
            raffine(&am, &bm),
            a.iter()
                .filter(|p| !b.iter().any(|q| p.starts_with(q)))
                .cloned()
                .collect(),
        );
        same(
            compose(&am, &bm),
            a.iter()
                .flat_map(|p| b.iter().map(move |q| [p.as_slice(), q.as_slice()].concat()))
                .collect(),
        );
        same(
            prefix_closure(&am),
            a.iter()
                .flat_map(|p| (1..=p.len()).map(move |n| p[..n].to_vec()))
                .collect(),
        );
        same(
            suffix_closure(&am, false),
            a.iter()
                .flat_map(|p| (0..p.len()).map(move |n| p[n..].to_vec()))
                .collect(),
        );
        same(
            suffix_closure(&am, true),
            a.iter()
                .flat_map(|p| (0..=p.len()).map(move |n| p[n..].to_vec()))
                .collect(),
        );
        same(
            tails_union(&am),
            a.iter()
                .filter(|p| !p.is_empty())
                .map(|p| p[1..].to_vec())
                .collect(),
        );
        let groups = (0..5)
            .filter_map(|h| {
                let set = a
                    .iter()
                    .filter(|p| p.first() == Some(&h))
                    .map(|p| p[1..].to_vec())
                    .collect::<Set>();
                (!set.is_empty()).then_some(set)
            })
            .reduce(|a, b| a.intersection(&b).cloned().collect())
            .unwrap_or_default();
        same(tails_intersection(&am), groups);
        same(
            head_space(&am),
            a.iter()
                .filter_map(|p| p.first().map(|h| vec![*h]))
                .collect(),
        );
        for p in [vec![], vec![0], vec![1, 0], vec![255]] {
            same(
                wrap(&am, &p),
                a.iter()
                    .map(|q| [p.as_slice(), q.as_slice()].concat())
                    .collect(),
            );
            same(
                unwrap(&am, &p),
                a.iter()
                    .filter(|q| q.starts_with(&p))
                    .map(|q| q[p.len()..].to_vec())
                    .collect(),
            );
        }
        let v = a.iter().cloned().collect::<Vec<_>>();
        for (lo, hi) in [
            (0, 0),
            (0, 1),
            (1, 2),
            (2, 0),
            (-1, 0),
            (-4, -1),
            (-5, 3),
            (i32::MIN, i32::MAX),
            (7, 2),
        ] {
            let n = v.len() as i64;
            let start = (if lo > 0 {
                lo as i64 - 1
            } else if lo < 0 {
                n + lo as i64
            } else {
                0
            })
            .clamp(0, n) as usize;
            let stop = (if hi == 0 {
                n
            } else if hi > 0 {
                hi as i64 - if lo == 0 { 0 } else { 1 }
            } else {
                n + hi as i64
            })
            .clamp(0, n) as usize;
            same(
                range(&am, lo, hi),
                v[start.min(stop)..stop].iter().cloned().collect(),
            );
        }
    }
}

#[test]
fn compact_products_have_exact_unbounded_cardinality_and_ranges() {
    let factor = singleton(&[0]).join(&singleton(&[255]));
    let mut a = singleton(&[]);
    for _ in 0..80 {
        a = compose(&a, &factor);
    }
    assert_eq!(count(&a).to_string(), "1208925819614629174706176");
    same(range(&a, -1, 0), [vec![255; 80]].into());
    same(range(&a, 0, 1), [vec![0; 80]].into());
    assert_eq!(count(&range(&a, 2, -1)), count(&a) - 2u32);
}

#[test]
fn proven_codeword_selectors_preserve_ordinal_order() {
    fn fib(mut n: u64) -> Path {
        let mut weights = vec![1, 2];
        while *weights.last().unwrap() <= n {
            let k = weights.len();
            weights.push(weights[k - 1] + weights[k - 2]);
        }
        weights.pop();
        let mut out = Path::from_vec(vec![0; weights.len()]);
        for i in (0..weights.len()).rev() {
            if weights[i] <= n {
                out[i] = 1;
                n -= weights[i];
            }
        }
        out.push(1);
        out
    }
    let codes = (1..=200).map(fib).collect::<Vec<_>>();
    for step in 1..35 {
        let selected = codes
            .iter()
            .enumerate()
            .filter(|(i, _)| (i + step) % 37 == 0)
            .map(|(_, p)| p.clone())
            .collect::<Vec<_>>();
        let set = selected.iter().map(|p| p.to_vec()).collect();
        let a = map(&set);
        assert!(equal(
            &first_fibonacci(&a, 200, 13, false),
            &first_listed(&a, &codes, false)
        ));
        let with_tail = compose(&a, &singleton(&[255]));
        assert!(equal(
            &first_fibonacci(&with_tail, 200, 13, true),
            &first_listed(&with_tail, &codes, true)
        ));
        assert!(first_fibonacci(&with_tail, 200, 13, false).is_empty());
        let allowed = map(&codes.iter().map(|p| p.to_vec()).collect());
        let expected = set.iter().next().map_or(Space::new(), |p| singleton(p));
        assert!(equal(
            &first_matching(&with_tail, &allowed, true),
            &expected
        ));
    }
}
