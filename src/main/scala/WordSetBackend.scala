package morkl

import scala.collection.immutable.TreeSet

/** Storage algorithms are evaluator implementation choices, not language ops
  * or side metadata on encoded paths. The reference remains a set of words.
  */
class WordSetBackend[P](val ops: WordOps[P]):
  private val sortedCache = new java.util.IdentityHashMap[Iterable[P], Set[P]]()
  def clearCaches(): Unit = sortedCache.clear()
  def literal(values: Set[P]): Set[P] = values
  def from(values: Iterable[P]): Set[P] = values match
    case sorted: TreeSet[P @unchecked] if sorted.ordering eq ops.ordering => sorted
    case _ =>
      val cached = sortedCache.get(values)
      if cached != null then cached
      else
        val result = TreeSet.from(values)(using ops.ordering)
        if values.knownSize > 8 then sortedCache.put(values, result)
        result
  private lazy val epsilon: Set[P] = TreeSet(ops.empty)(using ops.ordering)
  def singleton(word: P): Set[P] = if word == ops.empty then epsilon else TreeSet(word)(using ops.ordering)
  lazy val empty: Set[P] = from(Vector.empty)
  def union(a: Set[P], b: Set[P]): Set[P] =
    if a.isEmpty then b else if b.isEmpty then a
    else if a.size < b.size then from(b ++ a) else from(a ++ b)
  def intersect(a: Set[P], b: Set[P]): Set[P] =
    if a.size == 1 then if b(a.head) then a else empty
    else from(a intersect b)
  def subtract(a: Set[P], b: Set[P]): Set[P] =
    if a.size == 1 then if b(a.head) then empty else a
    else if b.size == 1 then from(from(a) - b.head)
    else from(a diff b)
  def groups(values: Set[P]): Vector[(P, Set[P])] =
    if values.isEmpty then return Vector.empty
    if values.size == 1 then return ops.split(values.head).toVector.map((h, t) => h -> singleton(t))
    val out = scala.collection.mutable.HashMap.empty[P, scala.collection.mutable.Set[P]]
    values.iterator.flatMap(ops.split).foreach((h, t) => out.getOrElseUpdate(h, scala.collection.mutable.Set.empty) += t)
    out.iterator.map((h, t) => h -> from(t)).toVector
  def unwrap(values: Set[P], prefix: P): Set[P] =
    val sorted = from(values).asInstanceOf[TreeSet[P]]
    from(sorted.iteratorFrom(prefix).takeWhile(ops.startsWith(_, prefix)).map(ops.drop(_, ops.length(prefix))).toVector)
  def wrap(values: Set[P], prefix: P): Set[P] = from(values.iterator.map(ops.concat(prefix, _)).toVector)
  def restrict(a: Set[P], b: Set[P], keep: Boolean): Set[P] =
    if a.size == 1 then
      if b.exists(ops.startsWith(a.head, _)) == keep then a else empty
    else from(a.filter(p => b.exists(ops.startsWith(p, _)) == keep))
  def compose(a: Set[P], b: Set[P]): Set[P] = from(for p <- a; q <- b yield ops.concat(p, q))
  def range(values: Set[P], start: Int, end: Int): Set[P] =
    val sorted = from(values).asInstanceOf[TreeSet[P]]
    val (lo, hi) = RangeBounds.normalize(sorted.size, start, end)
    if hi <= lo then empty
    else if lo == 0 && hi == sorted.size then sorted
    else if lo == 0 && hi == 1 then singleton(sorted.head)
    else sorted.drop(lo).take(hi - lo)
