use num_bigint::BigUint;
use pathmap::utils::BitMask;
use std::collections::BTreeSet;
use zippy_pathmap::*;
type Set = BTreeSet<Vec<u8>>;

#[test]
fn adiabatic_walks_restore_their_focus_and_never_visit_siblings() {
    let input = map(&[vec![0], vec![0, 4], vec![255, 4, 5], vec![]].into());
    let mut output = singleton(&[9]);
    {
        let mut source = input.read_zipper();
        let mut writer = output.write_zipper();
        writer.descend_to_byte(8);
        join_tails_into(&mut writer, &mut source);
        assert_eq!(source.path(), &[]);
        assert_eq!(writer.path(), &[8]);
    }
    same(output, [vec![9], vec![8], vec![8, 4], vec![8, 4, 5]].into());
    let mut dense = singleton(&[9]);
    {
        let mut writer = dense.write_zipper();
        writer.descend_to_byte(9);
        write_heads_mask(&mut writer, [u64::MAX; 4].into(), &[]);
        assert_eq!(writer.path(), &[9]);
    }
    let mut dense_expected = BTreeSet::from([vec![9]]);
    dense_expected.extend((0..=255u8).map(|b| vec![9, b]));
    same(dense, dense_expected);
    let mut preserved = singleton(&[9, 1, 2]);
    {
        let mut out = preserved.write_zipper();
        out.descend_to_byte(9);
        let mut mask = pathmap::utils::ByteMask::new();
        mask.set_bit(1);
        mask.set_bit(255);
        write_heads_mask(&mut out, mask, &[]);
        assert_eq!(out.path(), &[9]);
    }
    same(preserved, [vec![9, 1], vec![9, 1, 2], vec![9, 255]].into());

    let paths: Set = [
        vec![],
        vec![0],
        vec![0, 1],
        vec![0, 1, 2],
        vec![0, 2, 3],
        vec![1, 0],
        vec![255],
    ]
    .into_iter()
    .collect();
    let a = map(&paths);
    for prefix in [
        vec![],
        vec![0],
        vec![0, 1],
        vec![0, 1, 2],
        vec![1],
        vec![9, 9],
    ] {
        let mut z = a.read_zipper();
        z.descend_to(&prefix);
        let depth = z.path().len();
        let mut actual = Set::new();
        let mut more = z.is_val() || next_value_within(&mut z, depth);
        while more {
            actual.insert(z.path()[depth..].to_vec());
            let snapshot = cursor_at(&z, depth);
            assert!(equal(&snapshot, &a.read_zipper_at_path(&prefix)));
            more = next_value_within(&mut z, depth);
        }
        assert_eq!(z.path(), prefix);
        let expected: Set = paths
            .iter()
            .filter_map(|p| p.strip_prefix(prefix.as_slice()).map(<[u8]>::to_vec))
            .collect();
        assert_eq!(actual, expected);
    }
    for depth in 1..=7 {
        same(
            tails_union_n(&a, depth),
            paths
                .iter()
                .filter(|p| p.len() >= depth)
                .map(|p| p[depth..].to_vec())
                .collect(),
        );
    }
    let framed: Set = [
        vec![],
        vec![0],
        vec![0, 7],
        vec![1, 255, 7],
        vec![2, 0, 255, 9],
        vec![3, 1],
        vec![255, 1],
    ]
    .into_iter()
    .collect();
    let expected: Set = framed
        .iter()
        .filter(|p| !p.is_empty() && p.len() > usize::from(p[0]))
        .map(|p| p[1 + usize::from(p[0])..].to_vec())
        .collect();
    let a = map(&framed);
    same(drop_length_prefixes(&a), expected.clone());
    same(drop_length_prefixes(&a.read_zipper()), expected);
    let mut out = Space::new();
    let mut expected = Set::new();
    {
        let mut w = out.write_zipper();
        for prefix in [vec![], vec![0], vec![255, 1]] {
            join_wrapped_into(&mut w, &a.read_zipper(), &prefix);
            write_path(&mut w, &prefix);
            assert!(w.path().is_empty());
            expected.insert(prefix.clone());
            expected.extend(
                framed
                    .iter()
                    .map(|p| [prefix.as_slice(), p.as_slice()].concat()),
            );
        }
    }
    same(out, expected);
    let mut out = Space::new();
    {
        let mut w = out.write_zipper();
        let mut r = a.read_zipper();
        w.descend_to([6, 7]);
        join_branches(&mut w, &mut r);
        assert_eq!(w.path(), [6, 7]);
        assert!(r.path().is_empty());
        w.ascend(2);
    }
    same(
        out,
        framed
            .iter()
            .filter(|p| !p.is_empty())
            .map(|p| [&[6, 7], p.as_slice()].concat())
            .collect(),
    );
}
fn map(s: &Set) -> Space {
    let mut m = Space::new();
    for p in s {
        m.set_val_at(p, ());
    }
    m
}
fn same(m: Space, s: Set) {
    assert_eq!(enumerate(&m).map(|p| p.to_vec()).collect::<Set>(), s);
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
        let av = am.read_zipper();
        let bv = bm.read_zipper();
        assert!(equal(&av, &am));
        for (borrowed, owned) in [
            (unite(&av, &bv), unite(&am, &bm)),
            (intersect(&av, &bv), intersect(&am, &bm)),
            (subtract(&av, &bv), subtract(&am, &bm)),
            (restrict_to(&av, &bv), restrict_to(&am, &bm)),
            (raffine(&av, &bv), raffine(&am, &bm)),
            (compose(&av, &bv), compose(&am, &bm)),
            (prefix_closure(&av), prefix_closure(&am)),
            (suffix_closure(&av, true), suffix_closure(&am, true)),
            (range_const::<0, 5>(&av), range(&am, 0, 5)),
        ] {
            assert!(equal(&borrowed, &owned));
        }
        assert_eq!(equal(&am, &bm), a == b);
        assert!(equal(&am, &am.clone()));
        let mut reversed = Space::new();
        for p in a.iter().rev() {
            reversed.set_val_at(p, ());
        }
        assert!(equal(&am, &reversed));
        // Equal languages can have different physical nodes after deletion.
        reversed.set_val_at([255, 255, 255], ());
        reversed
            .write_zipper_at_path(&[255, 255, 255])
            .remove_val(true);
        assert!(equal(&am, &reversed));
        let terminated = a
            .iter()
            .filter_map(|p| p.iter().position(|b| *b == 0).map(|i| p[..=i].to_vec()))
            .collect::<Set>();
        assert_eq!(
            ordered_terminated(&am)
                .map(|p| p.to_vec())
                .collect::<Vec<_>>(),
            terminated.iter().cloned().collect::<Vec<_>>()
        );
        same(
            first_terminated(&am),
            terminated.iter().take(1).cloned().collect(),
        );
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
        macro_rules! specialized {
            ($lo:expr,$hi:expr) => {
                assert!(equal(
                    &range_const::<{ $lo }, { $hi }>(&am),
                    &range(&am, $lo, $hi)
                ));
            };
        }
        specialized!(0, 0);
        specialized!(0, 1);
        specialized!(0, 10);
        specialized!(1, 2);
        specialized!(2, 0);
        specialized!(3, 8);
        specialized!(-1, 0);
        specialized!(-10, 0);
        specialized!(i32::MIN, 0);
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
    let mut independently_built = singleton(&[]);
    for _ in 0..80 {
        independently_built = compose(&independently_built, &factor);
    }
    assert!(equal(&a, &independently_built));
    independently_built.set_val_at([], ());
    assert!(!equal(&a, &independently_built));
    independently_built.write_zipper().remove_val(true);
    independently_built.set_val_at([0; 79], ());
    assert!(!equal(&a, &independently_built));
    same(range(&a, -1, 0), [vec![255; 80]].into());
    same(range(&a, 0, 1), [vec![0; 80]].into());
    same(range_const::<0, 1>(&a), [vec![0; 80]].into());
    same(range_const::<-1, 0>(&a), [vec![255; 80]].into());
    assert_eq!(count(&range_const::<0, 1000>(&a)), BigUint::from(1000u32));
    assert_eq!(count(&range_const::<-1000, 0>(&a)), BigUint::from(1000u32));
    assert_eq!(count(&range(&a, 2, -1)), count(&a) - 2u32);
    // Every internal prefix is valued and shared. The focus terminal is stored
    // outside its node and must participate separately in comparison/count keys.
    let closed = prefix_closure(&a);
    let independent_closed = prefix_closure(&a);
    assert!(equal(&closed, &independent_closed));
    assert_eq!(count(&closed), (BigUint::from(1u32) << 81) - 2u32);
    assert!(equal(&prefix_closure(&closed), &closed));
    let two = compose(&factor, &factor);
    let mut grouped = singleton(&[]);
    for _ in 0..40 {
        grouped = compose(&grouped, &two);
    }
    assert!(equal(&a, &grouped));
    // Suffix union uses native algebra; this bounded case covers overlapping
    // accepted lengths without assuming that native algebra minimizes its DAG.
    let mut bounded = singleton(&[]);
    for _ in 0..12 {
        bounded = compose(&bounded, &factor);
    }
    let suffixes = suffix_closure(&bounded, true);
    assert_eq!(count(&suffixes), (BigUint::from(1u32) << 13) - 1u32);
    assert!(equal(
        &range_const::<0, 1000>(&closed),
        &range(&closed, 0, 1000)
    ));
}

#[test]
fn proven_codeword_selectors_preserve_ordinal_order() {
    // Arbitrary bytes, all 256 length buckets, incomplete records, epsilon,
    // interior terminals and shared tails. Compare with independent set logic.
    let mut raw = BTreeSet::new();
    for n in 0..=255usize {
        for seed in 0..3usize {
            let mut p = vec![n as u8];
            p.extend((0..n).map(|i| (seed * 83 + i * 37) as u8));
            raw.insert(p.clone());
            p.extend([0, 255]);
            raw.insert(p);
        }
        if n > 0 {
            raw.insert(vec![n as u8]);
        }
    }
    raw.insert(vec![]);
    let mut expected = raw
        .iter()
        .filter(|p| !p.is_empty() && p.len() > p[0] as usize)
        .map(|p| p[..=p[0] as usize].to_vec())
        .collect::<BTreeSet<_>>()
        .into_iter()
        .collect::<Vec<_>>();
    expected.sort_by(|a, b| a[1..].cmp(&b[1..]));
    let a = map(&raw);
    assert_eq!(
        ordered_length_prefixed(&a)
            .map(|p| p.to_vec())
            .collect::<Vec<_>>(),
        expected
    );
    same(first_length_prefixed(&a), [expected[0].clone()].into());
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
        assert_eq!(
            ordered_fibonacci(&a, 200, 13, false).collect::<Vec<_>>(),
            selected
        );
        assert_eq!(
            ordered_listed(&a, &codes, false).collect::<Vec<_>>(),
            selected
        );
        let with_tail = compose(&a, &singleton(&[255]));
        assert!(equal(
            &first_fibonacci(&with_tail, 200, 13, true),
            &first_listed(&with_tail, &codes, true)
        ));
        assert!(first_fibonacci(&with_tail, 200, 13, false).is_empty());
        let allowed = map(&codes.iter().map(|p| p.to_vec()).collect());
        let lexical = set.iter().cloned().map(Path::from).collect::<Vec<_>>();
        assert_eq!(
            ordered_fibonacci(&with_tail, 200, 13, true).collect::<Vec<_>>(),
            selected
        );
        assert_eq!(
            ordered_listed(&with_tail, &codes, true).collect::<Vec<_>>(),
            selected
        );
        assert_eq!(
            ordered_matching(&a, &allowed, false).collect::<Vec<_>>(),
            lexical
        );
        assert_eq!(
            ordered_matching(&with_tail, &allowed, true).collect::<Vec<_>>(),
            lexical
        );
        assert!(
            ordered_matching(&with_tail, &allowed, false)
                .collect::<Vec<_>>()
                .is_empty()
        );
        let expected = set.iter().next().map_or(Space::new(), |p| singleton(p));
        assert!(equal(
            &first_matching(&with_tail, &allowed, true),
            &expected
        ));
    }
}
