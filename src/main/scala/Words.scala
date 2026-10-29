package morkl

import scala.collection.immutable.ArraySeq
import scala.collection.mutable

/** ==============================================================================================
 *  ZIPPY OVER AN ARBITRARY ALPHABET: Zippy_symbols, Zippy_bits, Zippy_bytes.
 *
 *  `Space` is Zippy_symbols: paths are words over the (open) symbol universe.  `WSpace[W]` is the
 *  SAME language — case for case, binder for binder — over a word type `W` described by an
 *  [[Alphabet]].  Three instances:
 *
 *    Zippy_symbols  W = List[String]   ([[SymWords]]; `Space` <-> `WSpace[List[String]]` losslessly)
 *    Zippy_bits     W = BigInt         ([[BitWords]]; a word is a BigInt with a leading sentinel 1)
 *    Zippy_bytes    W = ArraySeq[Byte] ([[ByteWords]]; a word is an immutable byte array)
 *
 *  The only thing that changes between them is what ONE LETTER is: `Iteration`'s head, `Fold`'s
 *  symbol, `TailsUnion`'s dropped item and `TailsIntersection`'s grouping key are one symbol, one
 *  bit, or one byte.  In Zippy_bits a head is in {0,1}; in Zippy_bytes it is a byte, so `Head` of a
 *  byte space has at most 256 results, where `Space`'s head is bounded only by the subspace.
 *
 *  [[WEval]] is the one reference evaluator, generic in the alphabet, so the bits backend is a
 *  `Set[BigInt]` and the bytes backend a `Set[ArraySeq[Byte]]`.  [[CodeTranslate]] (Encodings.scala)
 *  maps a program over one alphabet to an EQUIVALENT program over another using only the existing
 *  constructors and routine abstraction.
 *  ============================================================================================== */
trait Alphabet[W]:
  def name: String
  def empty: W
  def length(w: W): Int
  def concat(a: W, b: W): W
  def startsWith(w: W, p: W): Boolean
  /** `w` with its first `length(p)` letters removed; callers check `startsWith` first */
  def dropPrefix(w: W, p: W): W
  /** (one-letter head, tail), or None for the empty word */
  def uncons(w: W): Option[(W, W)]
  /** the canonical order: letter order, shorter-is-less on a shared prefix (as `pathValueOrdering`) */
  def compare(a: W, b: W): Int
  def show(w: W): String
  def letters(w: W): Iterator[W] =
    Iterator.unfold(w)(c => uncons(c))
  def fromLetters(ls: IterableOnce[W]): W = ls.iterator.foldLeft(empty)(concat)
  given ordering: Ordering[W] = (a, b) => compare(a, b)

/** Zippy_symbols' words.  `compare` is exactly `pathValueOrdering`. */
object SymWords extends Alphabet[List[String]]:
  val name = "symbols"
  val empty = Nil
  def length(w: List[String]) = w.length
  def concat(a: List[String], b: List[String]) = a ++ b
  def startsWith(w: List[String], p: List[String]) = w.startsWith(p)
  def dropPrefix(w: List[String], p: List[String]) = w.drop(p.length)
  def uncons(w: List[String]) = w match { case h :: t => Some((h :: Nil, t)); case Nil => None }
  def compare(a: List[String], b: List[String]) = pathValueOrdering.compare(PathValue(a), PathValue(b))
  def show(w: List[String]) = w.mkString(".")

/** Zippy_bits' words: bits b1..bn are the BigInt `1 b1 .. bn` (sentinel-1, so lengths are exact). */
object BitWords extends Alphabet[BigInt]:
  val name = "bits"
  val empty = BigInt(1)
  val Zero = BigInt(2)          // the one-letter word "0"
  val One = BigInt(3)           // the one-letter word "1"
  def bit(b: Boolean): BigInt = if b then One else Zero
  def length(w: BigInt) = w.bitLength - 1
  def concat(a: BigInt, b: BigInt) = { val lb = length(b); (a << lb) | b.clearBit(lb) }
  def startsWith(w: BigInt, p: BigInt) = { val d = length(w) - length(p); d >= 0 && (w >> d) == p }
  def dropPrefix(w: BigInt, p: BigInt) = { val r = length(w) - length(p); (w & ((BigInt(1) << r) - 1)).setBit(r) }
  def uncons(w: BigInt) =
    val l = length(w)
    if l == 0 then None else Some((w >> (l - 1), (w & ((BigInt(1) << (l - 1)) - 1)).setBit(l - 1)))
  def compare(a: BigInt, b: BigInt) =
    val la = length(a); val lb = length(b); val m = la min lb
    val c = (a >> (la - m)).compare(b >> (lb - m))
    if c != 0 then c else Integer.compare(la, lb)
  def show(w: BigInt) = w.toString(2).drop(1)
  def fromBits(bs: Iterable[Boolean]): BigInt = bs.foldLeft(empty)((acc, b) => (acc << 1) | (if b then 1 else 0))
  def toBits(w: BigInt): Vector[Boolean] = { val l = length(w); Vector.tabulate(l)(i => w.testBit(l - 1 - i)) }

/** Zippy_bytes' words: immutable byte arrays, compared as UNSIGNED bytes. */
object ByteWords extends Alphabet[ArraySeq[Byte]]:
  type B = ArraySeq[Byte]
  val name = "bytes"
  val empty: B = ArraySeq.empty[Byte]
  def of(bs: Int*): B = ArraySeq.from(bs.map(_.toByte))
  def length(w: B) = w.length
  def concat(a: B, b: B) = if b.isEmpty then a else if a.isEmpty then b else a ++ b
  def startsWith(w: B, p: B) = w.startsWith(p)
  def dropPrefix(w: B, p: B) = w.drop(p.length)
  def uncons(w: B) = if w.isEmpty then None else Some((w.take(1), w.drop(1)))
  def compare(a: B, b: B) =
    val m = a.length min b.length
    var i = 0
    while i < m do
      val c = Integer.compare(a(i) & 0xff, b(i) & 0xff)
      if c != 0 then return c
      i += 1
    Integer.compare(a.length, b.length)
  def show(w: B) = w.map(b => f"${b & 0xff}%02x").mkString(":")

enum WPath[W]:
  case Deref(pr: PathRef)
  case Constant(w: W)
  case Concat(l: WPath[W], r: WPath[W])
  case GroundedPP(p: WPath[W], f: W => W)
  case GroundedSP(p: WSpace[W], f: Set[W] => W)

/** `Space`, case for case, over the word type `W`. */
enum WSpace[W]:
  case Empty()
  case Call(r: RoutinePtr, refs: Vector[WPath[W]], mentions: Vector[WSpace[W]])
  case Mention(variable: SpaceMention)
  case Singleton(p: WPath[W])
  case Literal(ws: Set[W])
  case Union(x: WSpace[W], y: WSpace[W])
  case Intersection(x: WSpace[W], y: WSpace[W])
  case Subtraction(x: WSpace[W], y: WSpace[W])
  case Restriction(x: WSpace[W], y: WSpace[W])
  case Raffination(x: WSpace[W], y: WSpace[W])
  case Composition(x: WSpace[W], y: WSpace[W])
  case Iteration(src: WSpace[W], symbol: PathRef, rest: SpaceMention, templates: WSpace[W])
  case Fixpoint(init: WSpace[W], rec: SpaceMention, body: WSpace[W])
  case Fold(src: WSpace[W], initial: WPath[W], acc: PathRef, symbol: PathRef, rest: SpaceMention, templates: WSpace[W], update: WPath[W])
  case Wrap(src: WSpace[W], p: WPath[W])
  case Unwrap(src: WSpace[W], p: WPath[W])
  case TailsUnion(src: WSpace[W])
  case TailsIntersection(src: WSpace[W])
  case GroundedPS(p: WPath[W], f: W => Set[W])
  case GroundedSS(p: WSpace[W], f: Set[W] => Set[W])
  case Range(x: WSpace[W], lo: Int, hi: Int)

case class WRoutine[W](name: RoutinePtr, refs: Vector[PathRef], mentions: Vector[SpaceMention], body: WSpace[W])

/** A whole program: a main body plus its routine table (user routines and generated ones). */
case class WProgram[W](alphabet: Alphabet[W], main: WSpace[W], routines: Map[RoutinePtr, WRoutine[W]]):
  def size: Int = WSpaceOps.nodeCount(main) + routines.valuesIterator.map(r => WSpaceOps.nodeCount(r.body)).sum

object WSpaceOps:
  def nodeCount[W](s: WSpace[W]): Int =
    import WSpace.*
    def p(x: WPath[W]): Int = x match
      case WPath.Concat(l, r) => 1 + p(l) + p(r)
      case WPath.GroundedPP(q, _) => 1 + p(q)
      case WPath.GroundedSP(q, _) => 1 + nodeCount(q)
      case _ => 1
    s match
      case Call(_, rs, ms) => 1 + rs.map(p).sum + ms.map(nodeCount).sum
      case Singleton(q) => 1 + p(q)
      case Union(x, y) => 1 + nodeCount(x) + nodeCount(y)
      case Intersection(x, y) => 1 + nodeCount(x) + nodeCount(y)
      case Subtraction(x, y) => 1 + nodeCount(x) + nodeCount(y)
      case Restriction(x, y) => 1 + nodeCount(x) + nodeCount(y)
      case Raffination(x, y) => 1 + nodeCount(x) + nodeCount(y)
      case Composition(x, y) => 1 + nodeCount(x) + nodeCount(y)
      case Iteration(x, _, _, b) => 1 + nodeCount(x) + nodeCount(b)
      case Fixpoint(x, _, b) => 1 + nodeCount(x) + nodeCount(b)
      case Fold(x, i, _, _, _, b, u) => 1 + nodeCount(x) + p(i) + nodeCount(b) + p(u)
      case Wrap(x, q) => 1 + nodeCount(x) + p(q)
      case Unwrap(x, q) => 1 + nodeCount(x) + p(q)
      case TailsUnion(x) => 1 + nodeCount(x)
      case TailsIntersection(x) => 1 + nodeCount(x)
      case GroundedPS(q, _) => 1 + p(q)
      case GroundedSS(x, _) => 1 + nodeCount(x)
      case Range(x, _, _) => 1 + nodeCount(x)
      case _ => 1

  /** free path refs and space mentions (binders: Iteration's symbol/rest, Fixpoint's rec, Fold's
   *  acc/symbol/rest; `_` binds nothing, as in `PathContext.bind`).  Insertion-ordered. */
  def free[W](s: WSpace[W]): (Vector[PathRef], Vector[SpaceMention]) =
    val refs = mutable.LinkedHashSet.empty[PathRef]; val ms = mutable.LinkedHashSet.empty[SpaceMention]
    def p(x: WPath[W], br: Set[String]): Unit = x match
      case WPath.Deref(pr) => if !br(pr.s) then refs += PathRef(pr.s)
      case WPath.Constant(_) => ()
      case WPath.Concat(l, r) => p(l, br); p(r, br)
      case WPath.GroundedPP(q, _) => p(q, br)
      case WPath.GroundedSP(q, _) => go(q, br, Set.empty)
    def go(x: WSpace[W], br: Set[String], bm: Set[String]): Unit =
      import WSpace.*
      x match
        case Empty() | Literal(_) => ()
        case Mention(m) => if !bm(m.s) then ms += SpaceMention(m.s)
        case Call(_, rs, xs) => rs.foreach(p(_, br)); xs.foreach(go(_, br, bm))
        case Singleton(q) => p(q, br)
        case Union(a, b) => go(a, br, bm); go(b, br, bm)
        case Intersection(a, b) => go(a, br, bm); go(b, br, bm)
        case Subtraction(a, b) => go(a, br, bm); go(b, br, bm)
        case Restriction(a, b) => go(a, br, bm); go(b, br, bm)
        case Raffination(a, b) => go(a, br, bm); go(b, br, bm)
        case Composition(a, b) => go(a, br, bm); go(b, br, bm)
        case Iteration(a, h, r, b) => go(a, br, bm); go(b, br + h.s - "_", bm + r.s - "_")
        case Fixpoint(a, r, b) => go(a, br, bm); go(b, br, bm + r.s - "_")
        case Fold(a, i, acc, h, r, b, u) =>
          go(a, br, bm); p(i, br)
          val br2 = br + acc.s + h.s - "_"; go(b, br2, bm + r.s - "_"); p(u, br2)
        case Wrap(a, q) => go(a, br, bm); p(q, br)
        case Unwrap(a, q) => go(a, br, bm); p(q, br)
        case TailsUnion(a) => go(a, br, bm)
        case TailsIntersection(a) => go(a, br, bm)
        case GroundedPS(q, _) => p(q, br)
        case GroundedSS(a, _) => go(a, br, bm)
        case Range(a, _, _) => go(a, br, bm)
    go(s, Set.empty, Set.empty)
    (refs.toVector, ms.toVector)

  /** Zippy_symbols in both notations: `Space` <-> `WSpace[List[String]]`, structurally and losslessly. */
  def fromSpace(s: Space): WSpace[List[String]] =
    import WSpace.*
    def p(x: Path): WPath[List[String]] = x match
      case Path.Deref(pr) => WPath.Deref(pr)
      case Path.Constant(pv) => WPath.Constant(pv.items)
      case Path.Concat(l, r) => WPath.Concat(p(l), p(r))
      case Path.GroundedPP(q, f) => WPath.GroundedPP(p(q), w => f(PathValue(w)).items)
      case Path.GroundedSP(q, f) => WPath.GroundedSP(fromSpace(q), ws => f(SpaceValue(ws.map(PathValue(_)))).items)
    def g(x: Space) = fromSpace(x)
    s match
      case Space.Empty => Empty()
      case Space.Call(r, rs, ms) => Call(r, rs.map(p), ms.map(g))
      case Space.Mention(m) => Mention(m)
      case Space.Singleton(q) => Singleton(p(q))
      case Space.Literal(sv) => Literal(sv.paths.map(_.items))
      case Space.Union(a, b) => Union(g(a), g(b))
      case Space.Intersection(a, b) => Intersection(g(a), g(b))
      case Space.Subtraction(a, b) => Subtraction(g(a), g(b))
      case Space.Restriction(a, b) => Restriction(g(a), g(b))
      case Space.Raffination(a, b) => Raffination(g(a), g(b))
      case Space.Composition(a, b) => Composition(g(a), g(b))
      case Space.Iteration(a, h, r, b) => Iteration(g(a), h, r, g(b))
      case Space.Fixpoint(a, r, b) => Fixpoint(g(a), r, g(b))
      case Space.Fold(a, i, acc, h, r, b, u) => Fold(g(a), p(i), acc, h, r, g(b), p(u))
      case Space.Wrap(a, q) => Wrap(g(a), p(q))
      case Space.Unwrap(a, q) => Unwrap(g(a), p(q))
      case Space.TailsUnion(a) => TailsUnion(g(a))
      case Space.TailsIntersection(a) => TailsIntersection(g(a))
      case Space.GroundedPS(q, f) => GroundedPS(p(q), w => f(PathValue(w)).paths.map(_.items))
      case Space.GroundedSS(a, f) => GroundedSS(g(a), ws => f(SpaceValue(ws.map(PathValue(_)))).paths.map(_.items))
      case Space.Range(a, lo, hi) => Range(g(a), lo, hi)

  def toSpace(s: WSpace[List[String]]): Space =
    import WSpace.*
    def p(x: WPath[List[String]]): Path = x match
      case WPath.Deref(pr) => Path.Deref(pr)
      case WPath.Constant(w) => Path.Constant(PathValue(w))
      case WPath.Concat(l, r) => Path.Concat(p(l), p(r))
      case WPath.GroundedPP(q, f) => Path.GroundedPP(p(q), pv => PathValue(f(pv.items)))
      case WPath.GroundedSP(q, f) => Path.GroundedSP(toSpace(q), sv => PathValue(f(sv.paths.map(_.items))))
    def g(x: WSpace[List[String]]) = toSpace(x)
    s match
      case Empty() => Space.Empty
      case Call(r, rs, ms) => Space.Call(r, rs.map(p), ms.map(g))
      case Mention(m) => Space.Mention(m)
      case Singleton(q) => Space.Singleton(p(q))
      case Literal(ws) => Space.Literal(SpaceValue(ws.map(PathValue(_))))
      case Union(a, b) => Space.Union(g(a), g(b))
      case Intersection(a, b) => Space.Intersection(g(a), g(b))
      case Subtraction(a, b) => Space.Subtraction(g(a), g(b))
      case Restriction(a, b) => Space.Restriction(g(a), g(b))
      case Raffination(a, b) => Space.Raffination(g(a), g(b))
      case Composition(a, b) => Space.Composition(g(a), g(b))
      case Iteration(a, h, r, b) => Space.Iteration(g(a), h, r, g(b))
      case Fixpoint(a, r, b) => Space.Fixpoint(g(a), r, g(b))
      case Fold(a, i, acc, h, r, b, u) => Space.Fold(g(a), p(i), acc, h, r, g(b), p(u))
      case Wrap(a, q) => Space.Wrap(g(a), p(q))
      case Unwrap(a, q) => Space.Unwrap(g(a), p(q))
      case TailsUnion(a) => Space.TailsUnion(g(a))
      case TailsIntersection(a) => Space.TailsIntersection(g(a))
      case GroundedPS(q, f) => Space.GroundedPS(p(q), pv => SpaceValue(f(pv.items).map(PathValue(_))))
      case GroundedSS(a, f) => Space.GroundedSS(g(a), sv => SpaceValue(f(sv.paths.map(_.items)).map(PathValue(_))))
      case Range(a, lo, hi) => Space.Range(g(a), lo, hi)

  def fromRoutine(r: Routine): WRoutine[List[String]] = WRoutine(r.name, r.refs, r.mentions, fromSpace(r.body))
  def toRoutine(r: WRoutine[List[String]]): Routine = Routine(r.name, r.refs, r.mentions, toSpace(r.body))

/** THE REFERENCE EVALUATOR, generic in the alphabet: `Set[W]` is the backend (a set of big ints for
 *  Zippy_bits, a set of byte arrays for Zippy_bytes).  It mirrors `eval` (MORKL.scala) arm for arm,
 *  including the self-recursive `Call` cut, `Fold`'s head order and `Range`'s canonical-order slice. */
final class WEval[W](prog: WProgram[W]):
  private val A = prog.alphabet
  import A.ordering
  var calls = 0L                       // routine entries, for the depth/unrolling report
  type PC = Map[String, W]
  type SC = Map[String, Set[W]]
  private def bindP(pc: PC, r: PathRef, w: W): PC = if r.s == "_" then pc else pc.updated(r.s, w)
  private def bindS(sc: SC, m: SpaceMention, v: Set[W]): SC = if m.s == "_" then sc else sc.updated(m.s, v)

  def path(x: WPath[W])(using pc: PC, sc: SC): W = x match
    case WPath.Deref(pr) => pc.getOrElse(pr.s, throw RuntimeException(s"$pr path ref not resolved"))
    case WPath.Constant(w) => w
    case WPath.Concat(l, r) => A.concat(path(l), path(r))
    case WPath.GroundedPP(p, f) => f(path(p))
    case WPath.GroundedSP(s, f) => f(space(s))

  private def heads(src: Set[W]): Map[W, Set[W]] =
    src.iterator.flatMap(A.uncons).toSeq.groupMap(_._1)(_._2).view.mapValues(_.toSet).toMap

  def run(pc: Map[PathRef, W] = Map.empty, sc: Map[SpaceMention, Set[W]] = Map.empty): Set[W] =
    space(prog.main)(using pc.map((k, v) => k.s -> v), sc.map((k, v) => k.s -> v))

  def space(x: WSpace[W])(using pc: PC, sc: SC): Set[W] =
    import WSpace.*
    x match
      case Empty() => Set.empty
      case Call(rp, refs, mentions) =>
        calls += 1
        val refvs = refs.map(path)
        val mvs = mentions.map(space)
        val WRoutine(_, rn, mn, body) = prog.routines.getOrElse(rp, throw RuntimeException(s"routine ${rp.s} not defined"))
        val pc2: PC = Map.from(rn.map(_.s) zip refvs)
        val sc2: SC = Map.from(mn.map(_.s) zip mvs)
        body match
          case Union(l, Call(`rp`, `refs`, `mentions`))
            if (refs zip refvs).forall((p, v) => v == path(p)(using pc2, sc2)) &&
               (mentions zip mvs).forall((s, v) => v == space(s)(using pc2, sc2)) =>
            space(l)(using pc2, sc2)
          case _ => space(body)(using pc2, sc2)
      case Mention(m) => sc.getOrElse(m.s, throw RuntimeException(s"$m space mention not resolved"))
      case Singleton(p) => Set(path(p))
      case Literal(ws) => ws
      case Union(a, b) => space(a) union space(b)
      case Intersection(a, b) => space(a) intersect space(b)
      case Subtraction(a, b) => space(a) removedAll space(b)
      case Restriction(a, b) => val ps = space(b); space(a).filter(w => ps.exists(p => A.startsWith(w, p)))
      case Raffination(a, b) => val ps = space(b); space(a).filterNot(w => ps.exists(p => A.startsWith(w, p)))
      case Composition(a, b) => val ys = space(b); for e1 <- space(a); e2 <- ys yield A.concat(e1, e2)
      case Wrap(a, p) => val q = path(p); space(a).map(A.concat(q, _))
      case Unwrap(a, p) => val q = path(p); space(a).collect { case w if A.startsWith(w, q) => A.dropPrefix(w, q) }
      case TailsUnion(a) => space(a).flatMap(w => A.uncons(w).map(_._2))
      case TailsIntersection(a) =>
        val gs = heads(space(a))
        if gs.isEmpty then Set.empty else gs.valuesIterator.reduce(_ intersect _)
      case Iteration(a, h, r, body) =>
        heads(space(a)).iterator.flatMap((hd, tails) => space(body)(using bindP(pc, h, hd), bindS(sc, r, tails))).toSet
      case Fixpoint(init, rec, body) =>
        var cur = space(init)
        var stop = false
        while !stop do
          val nxt = cur union space(body)(using pc, bindS(sc, rec, cur))
          if nxt == cur then stop = true else cur = nxt
        cur
      case Fold(a, initial, acc, h, r, body, update) =>
        var accV = path(initial)
        val out = Set.newBuilder[W]
        for (hd, tails) <- heads(space(a)).toSeq.sortBy(_._1) do
          val pc2 = bindP(bindP(pc, acc, accV), h, hd); val sc2 = bindS(sc, r, tails)
          out ++= space(body)(using pc2, sc2)
          accV = path(update)(using pc2, sc2)
        out.result()
      case GroundedPS(p, f) => f(path(p))
      case GroundedSS(a, f) => f(space(a))
      case Range(a, lo, hi) =>
        val s = space(a)
        val (l, h) = RangeBounds.normalize(s.size, lo, hi)
        if h <= l then Set.empty else if l == 0 && h == s.size then s else s.toVector.sorted.slice(l, h).toSet
