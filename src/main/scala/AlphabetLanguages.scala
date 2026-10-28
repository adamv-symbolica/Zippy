package morkl.alpha

import morkl.{Path, PathRef, PathValue, Routine, RoutinePtr, Space, SpaceMention, SpaceValue}
import java.nio.charset.StandardCharsets

/** ================================================================================================
 *  ZIPPY OVER A FIXED ALPHABET: Zippy_bits (`A = Boolean`) and Zippy_bytes (`A = Byte`).
 *
 *  `ZSpace[A]` / `ZPath[A]` mirror `Space` / `Path` CASE FOR CASE (checked by
 *  `AlphabetLanguagesCheck` through the compiler's `Mirror` labels); the only difference is the type
 *  of a path item.  Zippy_symbols is the existing `Space` (`A = String`), reached through
 *  [[Symbols.fromSpace]] / [[Symbols.toSpace]].  A path item bound by `Iteration`/`Fold` is ONE
 *  LETTER: in Zippy_bits the heads of a space are a subset of {0,1}, in Zippy_bytes a subset of
 *  0..255 (a 256-bit mask), as opposed to Zippy_symbols where they are bounded only by the space.
 *  ================================================================================================ */
type Word[A] = Vector[A]

enum ZPath[A]:
  case Deref(pr: PathRef)
  case Constant(pi: Word[A])
  case Concat(l: ZPath[A], r: ZPath[A])
  case GroundedPP(p: ZPath[A], f: Word[A] => Word[A])
  case GroundedSP(p: ZSpace[A], f: Set[Word[A]] => Word[A])

enum ZSpace[A]:
  case Empty()
  case Call(r: RoutinePtr, refs: Vector[ZPath[A]], mentions: Vector[ZSpace[A]])
  case Mention(variable: SpaceMention)
  case Singleton(p: ZPath[A])
  case Literal(p: Set[Word[A]])
  case Union(x: ZSpace[A], y: ZSpace[A])
  case Intersection(x: ZSpace[A], y: ZSpace[A])
  case Subtraction(x: ZSpace[A], y: ZSpace[A])
  case Restriction(x: ZSpace[A], y: ZSpace[A])
  case Raffination(x: ZSpace[A], y: ZSpace[A])
  case Composition(x: ZSpace[A], y: ZSpace[A])
  case Iteration(src: ZSpace[A], symbol: PathRef, rest: SpaceMention, templates: ZSpace[A])
  case Fixpoint(init: ZSpace[A], rec: SpaceMention, body: ZSpace[A])
  case Fold(src: ZSpace[A], initial: ZPath[A], acc: PathRef, symbol: PathRef, rest: SpaceMention, templates: ZSpace[A], update: ZPath[A])
  case Wrap(src: ZSpace[A], p: ZPath[A])
  case Unwrap(src: ZSpace[A], p: ZPath[A])
  case TailsUnion(src: ZSpace[A])
  case TailsIntersection(src: ZSpace[A])
  case GroundedPS(p: ZPath[A], f: Word[A] => Set[Word[A]])
  case GroundedSS(p: ZSpace[A], f: Set[Word[A]] => Set[Word[A]])
  case Range(x: ZSpace[A], lo: Int, hi: Int)

case class ZRoutine[A](name: RoutinePtr, refs: Vector[PathRef], mentions: Vector[SpaceMention], body: ZSpace[A])

/** a closed program: a main term and the routine table it (transitively) calls */
case class ZProgram[A](main: ZSpace[A], routines: Map[RoutinePtr, ZRoutine[A]])

/** free path refs / space mentions of a term (by name) — what a lifted routine must be passed */
object FreeVars:
  def path[A](p: ZPath[A]): (Set[String], Set[String]) = p match
    case ZPath.Deref(pr) => (Set(pr.s), Set())
    case ZPath.Constant(_) => (Set(), Set())
    case ZPath.Concat(l, r) => join(path(l), path(r))
    case ZPath.GroundedPP(q, _) => path(q)
    case ZPath.GroundedSP(s, _) => space(s)
  /** `_` is an IGNORED binder (the evaluator does not bind it), so it never hides an outer `_` */
  private def bound(n: String): Set[String] = if n == "_" then Set() else Set(n)
  private def join(a: (Set[String], Set[String]), b: (Set[String], Set[String])) = (a._1 ++ b._1, a._2 ++ b._2)
  def space[A](s: ZSpace[A]): (Set[String], Set[String]) = s match
    case ZSpace.Empty() | ZSpace.Literal(_) => (Set(), Set())
    case ZSpace.Call(_, rs, ms) => (rs.map(path) ++ ms.map(space)).foldLeft((Set[String](), Set[String]()))(join)
    case ZSpace.Mention(m) => (Set(), Set(m.s))
    case ZSpace.Singleton(p) => path(p)
    case ZSpace.Union(x, y) => join(space(x), space(y))
    case ZSpace.Intersection(x, y) => join(space(x), space(y))
    case ZSpace.Subtraction(x, y) => join(space(x), space(y))
    case ZSpace.Restriction(x, y) => join(space(x), space(y))
    case ZSpace.Raffination(x, y) => join(space(x), space(y))
    case ZSpace.Composition(x, y) => join(space(x), space(y))
    case ZSpace.Iteration(src, h, r, t) => val (tr, tm) = space(t); join(space(src), (tr -- bound(h.s), tm -- bound(r.s)))
    case ZSpace.Fixpoint(i, r, b) => val (br, bm) = space(b); join(space(i), (br, bm -- bound(r.s)))
    case ZSpace.Fold(src, init, acc, h, r, t, u) =>
      val (tr, tm) = join(space(t), path(u))
      join(join(space(src), path(init)), (tr -- bound(h.s) -- bound(acc.s), tm -- bound(r.s)))
    case ZSpace.Wrap(x, p) => join(space(x), path(p))
    case ZSpace.Unwrap(x, p) => join(space(x), path(p))
    case ZSpace.TailsUnion(x) => space(x)
    case ZSpace.TailsIntersection(x) => space(x)
    case ZSpace.GroundedPS(p, _) => path(p)
    case ZSpace.GroundedSS(x, _) => space(x)
    case ZSpace.Range(x, _, _) => space(x)

// ==================================================================================================
// REFERENCE BACKENDS.  One generic evaluator, parameterised by a path representation:
//   Zippy_bits  -> a Set[BigInt]   (bit string b1..bn stored as the integer 1b1..bn: a leading
//                                   sentinel 1 carries the length, so `0`, `00` and `ε` differ)
//   Zippy_bytes -> a Set[ByteStr]  (an immutable byte array, unsigned lexicographic order)
// ==================================================================================================
trait PathRepr[A, P]:
  def empty: P
  def fromWord(w: Word[A]): P
  def toWord(p: P): Word[A]
  def concat(a: P, b: P): P
  def length(p: P): Int
  def startsWith(p: P, pre: P): Boolean
  def drop(p: P, n: Int): P
  def head(p: P): P                       // the one-letter prefix
  def compare(a: P, b: P): Int            // trie order: letter order, shorter-is-less on a shared prefix

object BitRepr extends PathRepr[Boolean, BigInt]:
  private val One = BigInt(1)
  val empty: BigInt = One
  def fromWord(w: Word[Boolean]): BigInt = w.foldLeft(One)((acc, b) => (acc << 1) | (if b then One else BigInt(0)))
  def toWord(p: BigInt): Word[Boolean] = { val n = length(p); Vector.tabulate(n)(i => p.testBit(n - 1 - i)) }
  def length(p: BigInt): Int = p.bitLength - 1
  def concat(a: BigInt, b: BigInt): BigInt = { val lb = length(b); (a << lb) | b.clearBit(lb) }
  def startsWith(p: BigInt, pre: BigInt): Boolean = { val d = length(p) - length(pre); d >= 0 && (p >> d) == pre }
  def drop(p: BigInt, n: Int): BigInt = { val m = length(p) - n; require(m >= 0); (p & ((One << m) - 1)).setBit(m) }
  def head(p: BigInt): BigInt = p >> (length(p) - 1)
  def compare(a: BigInt, b: BigInt): Int =
    val la = length(a); val lb = length(b); val m = la min lb
    val pa = a >> (la - m); val pb = b >> (lb - m)
    if pa != pb then pa.compare(pb) else Integer.compare(la, lb)

final class ByteStr private (private val a: Array[Byte]):
  def bytes: Array[Byte] = a.clone()
  def length: Int = a.length
  def apply(i: Int): Byte = a(i)
  override def equals(o: Any): Boolean = o match { case b: ByteStr => java.util.Arrays.equals(a, b.a); case _ => false }
  override def hashCode: Int = java.util.Arrays.hashCode(a)
  override def toString: String = a.map(x => f"${x & 0xff}%02x").mkString("ByteStr(", "", ")")
object ByteStr:
  def apply(a: Array[Byte]): ByteStr = new ByteStr(a.clone())
  private[alpha] def wrap(a: Array[Byte]): ByteStr = new ByteStr(a)
  def compare(x: ByteStr, y: ByteStr): Int = java.util.Arrays.compareUnsigned(x.a, y.a)
  def slice(x: ByteStr, from: Int, until: Int): ByteStr = wrap(java.util.Arrays.copyOfRange(x.a, from, until))
  def concat(x: ByteStr, y: ByteStr): ByteStr = { val r = java.util.Arrays.copyOf(x.a, x.a.length + y.a.length); System.arraycopy(y.a, 0, r, x.a.length, y.a.length); wrap(r) }
  def startsWith(x: ByteStr, p: ByteStr): Boolean = x.a.length >= p.a.length && java.util.Arrays.equals(x.a, 0, p.a.length, p.a, 0, p.a.length)

object ByteRepr extends PathRepr[Byte, ByteStr]:
  val empty: ByteStr = ByteStr.wrap(Array.emptyByteArray)
  def fromWord(w: Word[Byte]): ByteStr = ByteStr.wrap(w.toArray)
  def toWord(p: ByteStr): Word[Byte] = Vector.tabulate(p.length)(p(_))
  def length(p: ByteStr): Int = p.length
  def concat(a: ByteStr, b: ByteStr): ByteStr = ByteStr.concat(a, b)
  def startsWith(p: ByteStr, pre: ByteStr): Boolean = ByteStr.startsWith(p, pre)
  def drop(p: ByteStr, n: Int): ByteStr = ByteStr.slice(p, n, p.length)
  def head(p: ByteStr): ByteStr = ByteStr.slice(p, 0, 1)
  def compare(a: ByteStr, b: ByteStr): Int = ByteStr.compare(a, b)

/** the reference evaluator, case for case the semantics of `morkl.eval` over a path representation */
final class Evaluator[A, P](val repr: PathRepr[A, P], routines: Map[RoutinePtr, ZRoutine[A]]):
  private given Ordering[P] = (a: P, b: P) => repr.compare(a, b)
  type PC = Map[String, P]
  type SC = Map[String, Set[P]]
  private def bindP(pc: PC, r: PathRef, v: P): PC = if r.s == "_" then pc else pc.updated(r.s, v)
  private def bindS(sc: SC, m: SpaceMention, v: Set[P]): SC = if m.s == "_" then sc else sc.updated(m.s, v)

  def path(x: ZPath[A], pc: PC, sc: SC): P = x match
    case ZPath.Deref(pr) => pc.getOrElse(pr.s, throw RuntimeException(s"$pr path ref not resolved"))
    case ZPath.Constant(w) => repr.fromWord(w)
    case ZPath.Concat(l, r) => repr.concat(path(l, pc, sc), path(r, pc, sc))
    case ZPath.GroundedPP(p, f) => repr.fromWord(f(repr.toWord(path(p, pc, sc))))
    case ZPath.GroundedSP(s, f) => repr.fromWord(f(space(s, pc, sc).map(repr.toWord)))

  private def groups(src: Set[P]): Map[P, Set[P]] =
    src.iterator.filter(repr.length(_) > 0).map(p => repr.head(p) -> repr.drop(p, 1)).toSeq.groupMap(_._1)(_._2).view.mapValues(_.toSet).toMap

  def space(x: ZSpace[A], pc: PC, sc: SC): Set[P] = x match
    case ZSpace.Empty() => Set()
    case ZSpace.Call(rp, refs, mentions) =>
      val refvs = refs.map(path(_, pc, sc)); val mvs = mentions.map(space(_, pc, sc))
      val ZRoutine(_, rn, mn, body) = routines.getOrElse(rp, throw RuntimeException(s"no routine ${rp.s}"))
      val pc2: PC = rn.map(_.s).zip(refvs).toMap; val sc2: SC = mn.map(_.s).zip(mvs).toMap
      body match  // the stationary-self-call reading `morkl.eval` gives `l \/ self(same args)`
        case ZSpace.Union(l, ZSpace.Call(`rp`, `refs`, `mentions`))
          if refs.zip(refvs).forall((p, v) => path(p, pc2, sc2) == v) && mentions.zip(mvs).forall((s, v) => space(s, pc2, sc2) == v) =>
          space(l, pc2, sc2)
        case _ => space(body, pc2, sc2)
    case ZSpace.Mention(m) => sc.getOrElse(m.s, throw RuntimeException(s"$m space mention not resolved"))
    case ZSpace.Singleton(p) => Set(path(p, pc, sc))
    case ZSpace.Literal(ws) => ws.map(repr.fromWord)
    case ZSpace.Union(a, b) => space(a, pc, sc) union space(b, pc, sc)
    case ZSpace.Intersection(a, b) => space(a, pc, sc) intersect space(b, pc, sc)
    case ZSpace.Subtraction(a, b) => space(a, pc, sc) removedAll space(b, pc, sc)
    case ZSpace.Restriction(a, b) => val ps = space(b, pc, sc); space(a, pc, sc).filter(v => ps.exists(repr.startsWith(v, _)))
    case ZSpace.Raffination(a, b) => space(ZSpace.Subtraction(a, ZSpace.Restriction(a, b)), pc, sc)
    case ZSpace.Composition(a, b) => val bs = space(b, pc, sc); for u <- space(a, pc, sc); v <- bs yield repr.concat(u, v)
    case ZSpace.Wrap(s, p) => space(ZSpace.Composition(ZSpace.Singleton(p), s), pc, sc)
    case ZSpace.Unwrap(s, p) => val pv = path(p, pc, sc); val n = repr.length(pv)
      space(s, pc, sc).collect { case v if repr.startsWith(v, pv) => repr.drop(v, n) }
    case ZSpace.TailsUnion(s) => space(s, pc, sc).collect { case v if repr.length(v) > 0 => repr.drop(v, 1) }
    case ZSpace.TailsIntersection(s) => val g = groups(space(s, pc, sc)); if g.isEmpty then Set() else g.valuesIterator.reduce(_ intersect _)
    case ZSpace.Iteration(src, h, r, t) =>
      groups(space(src, pc, sc)).iterator.flatMap((hd, rest) => space(t, bindP(pc, h, hd), bindS(sc, r, rest))).toSet
    case ZSpace.Fixpoint(init, rec, body) =>
      var cur = space(init, pc, sc); var stop = false
      while !stop do
        val nxt = cur union space(body, pc, bindS(sc, rec, cur))
        if nxt == cur then stop = true else cur = nxt
      cur
    case ZSpace.Fold(src, init, acc, h, r, t, u) =>
      var a = path(init, pc, sc); val out = Set.newBuilder[P]
      for (hd, rest) <- groups(space(src, pc, sc)).toSeq.sortBy(_._1) do
        val pc2 = bindP(bindP(pc, acc, a), h, hd); val sc2 = bindS(sc, r, rest)
        out ++= space(t, pc2, sc2); a = path(u, pc2, sc2)
      out.result()
    case ZSpace.GroundedPS(p, f) => f(repr.toWord(path(p, pc, sc))).map(repr.fromWord)
    case ZSpace.GroundedSS(s, f) => f(space(s, pc, sc).map(repr.toWord)).map(repr.fromWord)
    case ZSpace.Range(s, lo, hi) =>
      val v = space(s, pc, sc); val (l, h) = morkl.RangeBounds.normalize(v.size, lo, hi)
      if h <= l then Set() else if l == 0 && h == v.size then v else v.toVector.sorted.slice(l, h).toSet

  def run(main: ZSpace[A], pc: PC = Map(), sc: SC = Map()): Set[P] = space(main, pc, sc)
  def runWords(main: ZSpace[A]): Set[Word[A]] = run(main).map(repr.toWord)

object Bits:
  def eval(p: ZProgram[Boolean]): Set[BigInt] = Evaluator(BitRepr, p.routines).run(p.main)
  def evalWords(p: ZProgram[Boolean]): Set[Word[Boolean]] = Evaluator(BitRepr, p.routines).runWords(p.main)
object Bytes:
  def eval(p: ZProgram[Byte]): Set[ByteStr] = Evaluator(ByteRepr, p.routines).run(p.main)
  def evalWords(p: ZProgram[Byte]): Set[Word[Byte]] = Evaluator(ByteRepr, p.routines).runWords(p.main)

/** Zippy_symbols <-> ZSpace[String]: a bijection on terms (grounded functions are re-wrapped) */
object Symbols:
  private def pv(w: Word[String]) = PathValue(w.toList)
  private def sv(ws: Set[Word[String]]) = SpaceValue(ws.map(pv))
  private def w(p: PathValue): Word[String] = p.items.toVector
  private def ws(s: SpaceValue): Set[Word[String]] = s.paths.map(w)
  def fromPath(p: Path): ZPath[String] = p match
    case Path.Deref(pr) => ZPath.Deref(pr)
    case Path.Constant(c) => ZPath.Constant(w(c))
    case Path.Concat(l, r) => ZPath.Concat(fromPath(l), fromPath(r))
    case Path.GroundedPP(q, f) => ZPath.GroundedPP(fromPath(q), x => w(f(pv(x))))
    case Path.GroundedSP(s, f) => ZPath.GroundedSP(fromSpace(s), x => w(f(sv(x))))
  def toPath(p: ZPath[String]): Path = p match
    case ZPath.Deref(pr) => Path.Deref(pr)
    case ZPath.Constant(c) => Path.Constant(pv(c))
    case ZPath.Concat(l, r) => Path.Concat(toPath(l), toPath(r))
    case ZPath.GroundedPP(q, f) => Path.GroundedPP(toPath(q), x => pv(f(w(x))))
    case ZPath.GroundedSP(s, f) => Path.GroundedSP(toSpace(s), x => pv(f(ws(x))))
  def fromSpace(s: Space): ZSpace[String] = s match
    case Space.Empty => ZSpace.Empty()
    case Space.Call(r, rs, ms) => ZSpace.Call(r, rs.map(fromPath), ms.map(fromSpace))
    case Space.Mention(m) => ZSpace.Mention(m)
    case Space.Singleton(p) => ZSpace.Singleton(fromPath(p))
    case Space.Literal(v) => ZSpace.Literal(ws(v))
    case Space.Union(x, y) => ZSpace.Union(fromSpace(x), fromSpace(y))
    case Space.Intersection(x, y) => ZSpace.Intersection(fromSpace(x), fromSpace(y))
    case Space.Subtraction(x, y) => ZSpace.Subtraction(fromSpace(x), fromSpace(y))
    case Space.Restriction(x, y) => ZSpace.Restriction(fromSpace(x), fromSpace(y))
    case Space.Raffination(x, y) => ZSpace.Raffination(fromSpace(x), fromSpace(y))
    case Space.Composition(x, y) => ZSpace.Composition(fromSpace(x), fromSpace(y))
    case Space.Iteration(src, h, r, t) => ZSpace.Iteration(fromSpace(src), h, r, fromSpace(t))
    case Space.Fixpoint(i, r, b) => ZSpace.Fixpoint(fromSpace(i), r, fromSpace(b))
    case Space.Fold(src, i, a, h, r, t, u) => ZSpace.Fold(fromSpace(src), fromPath(i), a, h, r, fromSpace(t), fromPath(u))
    case Space.Wrap(x, p) => ZSpace.Wrap(fromSpace(x), fromPath(p))
    case Space.Unwrap(x, p) => ZSpace.Unwrap(fromSpace(x), fromPath(p))
    case Space.TailsUnion(x) => ZSpace.TailsUnion(fromSpace(x))
    case Space.TailsIntersection(x) => ZSpace.TailsIntersection(fromSpace(x))
    case Space.GroundedPS(p, f) => ZSpace.GroundedPS(fromPath(p), x => ws(f(pv(x))))
    case Space.GroundedSS(x, f) => ZSpace.GroundedSS(fromSpace(x), y => ws(f(sv(y))))
    case Space.Range(x, lo, hi) => ZSpace.Range(fromSpace(x), lo, hi)
  def toSpace(s: ZSpace[String]): Space = s match
    case ZSpace.Empty() => Space.Empty
    case ZSpace.Call(r, rs, ms) => Space.Call(r, rs.map(toPath), ms.map(toSpace))
    case ZSpace.Mention(m) => Space.Mention(m)
    case ZSpace.Singleton(p) => Space.Singleton(toPath(p))
    case ZSpace.Literal(v) => Space.Literal(sv(v))
    case ZSpace.Union(x, y) => Space.Union(toSpace(x), toSpace(y))
    case ZSpace.Intersection(x, y) => Space.Intersection(toSpace(x), toSpace(y))
    case ZSpace.Subtraction(x, y) => Space.Subtraction(toSpace(x), toSpace(y))
    case ZSpace.Restriction(x, y) => Space.Restriction(toSpace(x), toSpace(y))
    case ZSpace.Raffination(x, y) => Space.Raffination(toSpace(x), toSpace(y))
    case ZSpace.Composition(x, y) => Space.Composition(toSpace(x), toSpace(y))
    case ZSpace.Iteration(src, h, r, t) => Space.Iteration(toSpace(src), h, r, toSpace(t))
    case ZSpace.Fixpoint(i, r, b) => Space.Fixpoint(toSpace(i), r, toSpace(b))
    case ZSpace.Fold(src, i, a, h, r, t, u) => Space.Fold(toSpace(src), toPath(i), a, h, r, toSpace(t), toPath(u))
    case ZSpace.Wrap(x, p) => Space.Wrap(toSpace(x), toPath(p))
    case ZSpace.Unwrap(x, p) => Space.Unwrap(toSpace(x), toPath(p))
    case ZSpace.TailsUnion(x) => Space.TailsUnion(toSpace(x))
    case ZSpace.TailsIntersection(x) => Space.TailsIntersection(toSpace(x))
    case ZSpace.GroundedPS(p, f) => Space.GroundedPS(toPath(p), x => sv(f(w(x))))
    case ZSpace.GroundedSS(x, f) => Space.GroundedSS(toSpace(x), y => sv(f(ws(y))))
    case ZSpace.Range(x, lo, hi) => Space.Range(toSpace(x), lo, hi)
  def toRoutine(r: ZRoutine[String]): Routine = Routine(r.name, r.refs, r.mentions, toSpace(r.body))
  def fromRoutine(r: Routine): ZRoutine[String] = ZRoutine(r.name, r.refs, r.mentions, fromSpace(r.body))
  /** run a Zippy_symbols program on the EXISTING symbol evaluator `morkl.eval` */
  def evalWords(p: ZProgram[String]): Set[Word[String]] =
    val table = p.routines.map((k, v) => k -> toRoutine(v))
    morkl.eval(toSpace(p.main))(using rc = table).paths.map(w)
