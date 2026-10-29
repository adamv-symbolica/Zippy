package morkl

import scala.collection.immutable.{SortedSet, TreeSet}

/** Storage algorithms are evaluator implementation choices, not language ops
  * or side metadata on encoded paths. The reference remains a set of words.
  */
class WordSetBackend[P](val ops: WordOps[P]):
  def from(values: Iterable[P]): Set[P] = values match
    case sorted: TreeSet[P @unchecked] if sorted.ordering eq ops.ordering => sorted
    case _ => TreeSet.from(values)(using ops.ordering)
  def empty: Set[P] = from(Vector.empty)
  def union(a: Set[P], b: Set[P]): Set[P] = if a.size < b.size then from(b ++ a) else from(a ++ b)
  def intersect(a: Set[P], b: Set[P]): Set[P] = from(a intersect b)
  def subtract(a: Set[P], b: Set[P]): Set[P] = from(a diff b)
  def groups(values: Set[P]): Vector[(P, Set[P])] =
    val out = scala.collection.mutable.HashMap.empty[P, scala.collection.mutable.Set[P]]
    values.iterator.flatMap(ops.split).foreach((h, t) => out.getOrElseUpdate(h, scala.collection.mutable.Set.empty) += t)
    out.iterator.map((h, t) => h -> from(t)).toVector
  def unwrap(values: Set[P], prefix: P): Set[P] =
    val sorted = from(values).asInstanceOf[TreeSet[P]]
    from(sorted.iteratorFrom(prefix).takeWhile(ops.startsWith(_, prefix)).map(ops.drop(_, ops.length(prefix))).toVector)
  def wrap(values: Set[P], prefix: P): Set[P] = from(values.iterator.map(ops.concat(prefix, _)).toVector)
  def restrict(a: Set[P], b: Set[P], keep: Boolean): Set[P] =
    from(a.filter(p => b.exists(ops.startsWith(p, _)) == keep))
  def compose(a: Set[P], b: Set[P]): Set[P] = from(for p <- a; q <- b yield ops.concat(p, q))
  def range(values: Set[P], start: Int, end: Int): Set[P] =
    val sorted = from(values).asInstanceOf[TreeSet[P]]
    val (lo, hi) = RangeBounds.normalize(sorted.size, start, end)
    if hi <= lo then empty
    else if lo == 0 && hi == sorted.size then sorted
    else if lo == 0 && hi == 1 then from(Vector(sorted.head))
    else sorted.drop(lo).take(hi - lo)
