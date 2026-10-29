package morkl

/** ==============================================================================================
 *  Zippy over an ARBITRARY ATOM TYPE: Zippy_symbols, Zippy_bits and Zippy_bytes.
 *
 *  `ZSpace[A]` / `ZPath[A]` mirror `Space` / `Path` case for case (same constructors, same binders,
 *  same field order).  Only the atom changes:
 *
 *    Zippy_symbols = ZSpace[String]   — isomorphic to `Space` (`ZSym.from` / `ZSym.to`)
 *    Zippy_bits    = ZSpace[Boolean]  — a head is one of {}, {0}, {1}, {0,1}
 *    Zippy_bytes   = ZSpace[Byte]     — a head is a subset of the 256 bytes (a 256-bit mask)
 *
 *  Reference back-ends (`ZEval`, parameterised by a `PathRepr`):
 *    bits  — a path is a `BigInt` with a sentinel 1 above its bits (`BitRepr`), a space is a Set[BigInt]
 *    bytes — a path is a byte array (`ByteArr`, unsigned order), a space is a Set[ByteArr]
 *
 *  Translations (`CodedTranslation`) are driven by a prefix-free, order-preserving `Code[A, B]`:
 *  every path operator (∪ ∩ \ <| \| ×, Wrap, Unwrap, Range, Fixpoint, Call, Literal) maps
 *  structurally, because concatenation of prefix-free codes preserves prefixes and order.  The only
 *  operator that observes atom boundaries is `Iteration` (and the three that desugar to it:
 *  `TailsUnion`, `TailsIntersection`, `Fold`); it becomes a decoder: recursive routines (one per
 *  automaton state) that walk the target atoms with target-level `Iteration`, accumulate the code in a
 *  path argument, and call the lambda-lifted body when the code is complete.  Acyclic decoders
 *  (fixed-length codes) and small known universes (interned Fibonacci codes, whose max code length
 *  bounds the recursion depth) are unrolled into nested Iterations instead.  No metadata is stored
 *  and no operator is added: only the existing cases and routine abstraction are used.
 *  ============================================================================================== */

enum ZPath[A]:
  case Deref(pr: PathRef)
  case Constant(pv: Vector[A])
  case Concat(l: ZPath[A], r: ZPath[A])
  case GroundedPP(p: ZPath[A], f: Vector[A] => Vector[A])
  case GroundedSP(p: ZSpace[A], f: Set[Vector[A]] => Vector[A])

enum ZSpace[A]:
  case Empty()
  case Call(r: RoutinePtr, refs: Vector[ZPath[A]], mentions: Vector[ZSpace[A]])
  case Mention(variable: SpaceMention)
  case Singleton(p: ZPath[A])
  case Literal(p: Set[Vector[A]])
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
  case GroundedPS(p: ZPath[A], f: Vector[A] => Set[Vector[A]])
  case GroundedSS(p: ZSpace[A], f: Set[Vector[A]] => Set[Vector[A]])
  case Range(x: ZSpace[A], lo: Int, hi: Int)

case class ZRoutine[A](name: RoutinePtr, refs: Vector[PathRef], mentions: Vector[SpaceMention], body: ZSpace[A])

type Bit = Boolean
type SymSpace = ZSpace[String]
type BitSpace = ZSpace[Bit]
type ByteSpace = ZSpace[Byte]

/** Zippy_symbols <-> `Space`: an exact isomorphism (no case is lost in either direction). */
object ZSym:
  def fromP(p: Path): ZPath[String] = p match
    case Path.Deref(pr) => ZPath.Deref(pr)
    case Path.Constant(pv) => ZPath.Constant(pv.items.toVector)
    case Path.Concat(l, r) => ZPath.Concat(fromP(l), fromP(r))
    case Path.GroundedPP(q, f) => ZPath.GroundedPP(fromP(q), v => f(PathValue(v.toList)).items.toVector)
    case Path.GroundedSP(q, f) => ZPath.GroundedSP(from(q), s => f(SpaceValue(s.map(v => PathValue(v.toList)))).items.toVector)
  def fromV(sv: SpaceValue): Set[Vector[String]] = sv.paths.map(_.items.toVector)
  def toV(s: Set[Vector[String]]): SpaceValue = SpaceValue(s.map(v => PathValue(v.toList)))
  def from(s: Space): SymSpace = s match
    case Space.Empty => ZSpace.Empty()
    case Space.Call(r, refs, ms) => ZSpace.Call(r, refs.map(fromP), ms.map(from))
    case Space.Mention(v) => ZSpace.Mention(v)
    case Space.Singleton(p) => ZSpace.Singleton(fromP(p))
    case Space.Literal(sv) => ZSpace.Literal(fromV(sv))
    case Space.Union(x, y) => ZSpace.Union(from(x), from(y))
    case Space.Intersection(x, y) => ZSpace.Intersection(from(x), from(y))
    case Space.Subtraction(x, y) => ZSpace.Subtraction(from(x), from(y))
    case Space.Restriction(x, y) => ZSpace.Restriction(from(x), from(y))
    case Space.Raffination(x, y) => ZSpace.Raffination(from(x), from(y))
    case Space.Composition(x, y) => ZSpace.Composition(from(x), from(y))
    case Space.Iteration(s, h, r, t) => ZSpace.Iteration(from(s), h, r, from(t))
    case Space.Fixpoint(i, r, b) => ZSpace.Fixpoint(from(i), r, from(b))
    case Space.Fold(s, i, a, h, r, t, u) => ZSpace.Fold(from(s), fromP(i), a, h, r, from(t), fromP(u))
    case Space.Wrap(s, p) => ZSpace.Wrap(from(s), fromP(p))
    case Space.Unwrap(s, p) => ZSpace.Unwrap(from(s), fromP(p))
    case Space.TailsUnion(s) => ZSpace.TailsUnion(from(s))
    case Space.TailsIntersection(s) => ZSpace.TailsIntersection(from(s))
    case Space.GroundedPS(p, f) => ZSpace.GroundedPS(fromP(p), v => fromV(f(PathValue(v.toList))))
    case Space.GroundedSS(q, f) => ZSpace.GroundedSS(from(q), s => fromV(f(toV(s))))
    case Space.Range(x, lo, hi) => ZSpace.Range(from(x), lo, hi)
  def from(r: Routine): ZRoutine[String] = ZRoutine(r.name, r.refs, r.mentions, from(r.body))

  def toP(p: ZPath[String]): Path = p match
    case ZPath.Deref(pr) => Path.Deref(pr)
    case ZPath.Constant(v) => Path.Constant(PathValue(v.toList))
    case ZPath.Concat(l, r) => Path.Concat(toP(l), toP(r))
    case ZPath.GroundedPP(q, f) => Path.GroundedPP(toP(q), pv => PathValue(f(pv.items.toVector).toList))
    case ZPath.GroundedSP(q, f) => Path.GroundedSP(to(q), sv => PathValue(f(fromV(sv)).toList))
  def to(s: SymSpace): Space = s match
    case ZSpace.Empty() => Space.Empty
    case ZSpace.Call(r, refs, ms) => Space.Call(r, refs.map(toP), ms.map(to))
    case ZSpace.Mention(v) => Space.Mention(v)
    case ZSpace.Singleton(p) => Space.Singleton(toP(p))
    case ZSpace.Literal(sv) => Space.Literal(toV(sv))
    case ZSpace.Union(x, y) => Space.Union(to(x), to(y))
    case ZSpace.Intersection(x, y) => Space.Intersection(to(x), to(y))
    case ZSpace.Subtraction(x, y) => Space.Subtraction(to(x), to(y))
    case ZSpace.Restriction(x, y) => Space.Restriction(to(x), to(y))
    case ZSpace.Raffination(x, y) => Space.Raffination(to(x), to(y))
    case ZSpace.Composition(x, y) => Space.Composition(to(x), to(y))
    case ZSpace.Iteration(s, h, r, t) => Space.Iteration(to(s), h, r, to(t))
    case ZSpace.Fixpoint(i, r, b) => Space.Fixpoint(to(i), r, to(b))
    case ZSpace.Fold(s, i, a, h, r, t, u) => Space.Fold(to(s), toP(i), a, h, r, to(t), toP(u))
    case ZSpace.Wrap(s, p) => Space.Wrap(to(s), toP(p))
    case ZSpace.Unwrap(s, p) => Space.Unwrap(to(s), toP(p))
    case ZSpace.TailsUnion(s) => Space.TailsUnion(to(s))
    case ZSpace.TailsIntersection(s) => Space.TailsIntersection(to(s))
    case ZSpace.GroundedPS(p, f) => Space.GroundedPS(toP(p), pv => toV(f(pv.items.toVector)))
    case ZSpace.GroundedSS(q, f) => Space.GroundedSS(to(q), sv => toV(f(fromV(sv))))
    case ZSpace.Range(x, lo, hi) => Space.Range(to(x), lo, hi)
  def to(r: ZRoutine[String]): Routine = Routine(r.name, r.refs, r.mentions, to(r.body))

  /** every symbol a program (with its routines and inputs) can mention — the interning universe */
  def universe(ss: Iterable[SymSpace], inputs: Iterable[Vector[String]] = Nil): Vector[String] =
    val out = collection.mutable.Set.empty[String]
    inputs.foreach(out ++= _)
    ss.foreach(s => ZOps.constants(s).foreach(out ++= _))
    out.toVector.sorted

// ------------------------------------------------------------------------------------------------
// Syntactic utilities
// ------------------------------------------------------------------------------------------------
object ZOps:
  def constants[A](s: ZSpace[A]): Iterator[Vector[A]] =
    def p(x: ZPath[A]): Iterator[Vector[A]] = x match
      case ZPath.Constant(v) => Iterator(v)
      case ZPath.Concat(l, r) => p(l) ++ p(r)
      case ZPath.GroundedPP(q, _) => p(q)
      case ZPath.GroundedSP(q, _) => constants(q)
      case ZPath.Deref(_) => Iterator.empty
    children(s).flatMap(constants) ++ paths(s).flatMap(p) ++ (s match
      case ZSpace.Literal(ps) => ps.iterator
      case _ => Iterator.empty)

  private def children[A](s: ZSpace[A]): Iterator[ZSpace[A]] = s match
    case ZSpace.Call(_, _, ms) => ms.iterator
    case ZSpace.Union(x, y) => Iterator(x, y)
    case ZSpace.Intersection(x, y) => Iterator(x, y)
    case ZSpace.Subtraction(x, y) => Iterator(x, y)
    case ZSpace.Restriction(x, y) => Iterator(x, y)
    case ZSpace.Raffination(x, y) => Iterator(x, y)
    case ZSpace.Composition(x, y) => Iterator(x, y)
    case ZSpace.Iteration(s, _, _, t) => Iterator(s, t)
    case ZSpace.Fixpoint(i, _, b) => Iterator(i, b)
    case ZSpace.Fold(s, _, _, _, _, t, _) => Iterator(s, t)
    case ZSpace.Wrap(s, _) => Iterator(s)
    case ZSpace.Unwrap(s, _) => Iterator(s)
    case ZSpace.TailsUnion(s) => Iterator(s)
    case ZSpace.TailsIntersection(s) => Iterator(s)
    case ZSpace.GroundedSS(s, _) => Iterator(s)
    case ZSpace.Range(x, _, _) => Iterator(x)
    case _ => Iterator.empty
  private def paths[A](s: ZSpace[A]): Iterator[ZPath[A]] = s match
    case ZSpace.Call(_, refs, _) => refs.iterator
    case ZSpace.Singleton(p) => Iterator(p)
    case ZSpace.Fold(_, i, _, _, _, _, u) => Iterator(i, u)
    case ZSpace.Wrap(_, p) => Iterator(p)
    case ZSpace.Unwrap(_, p) => Iterator(p)
    case ZSpace.GroundedPS(p, _) => Iterator(p)
    case _ => Iterator.empty

  /** free path refs and free space mentions (binders: Iteration h/rest, Fold acc/h/rest, Fixpoint rec) */
  def free[A](s: ZSpace[A]): (Set[PathRef], Set[SpaceMention]) =
    def fp(x: ZPath[A]): (Set[PathRef], Set[SpaceMention]) = x match
      case ZPath.Deref(pr) => (if pr.s == "_" then Set() else Set(pr), Set())
      case ZPath.Constant(_) => (Set(), Set())
      case ZPath.Concat(l, r) => join(fp(l), fp(r))
      case ZPath.GroundedPP(q, _) => fp(q)
      case ZPath.GroundedSP(q, _) => free(q)
    def join(a: (Set[PathRef], Set[SpaceMention]), b: (Set[PathRef], Set[SpaceMention])) = (a._1 ++ b._1, a._2 ++ b._2)
    def minus(a: (Set[PathRef], Set[SpaceMention]), ps: Set[PathRef], ms: Set[SpaceMention]) = (a._1 -- ps, a._2 -- ms)
    s match
      case ZSpace.Mention(v) => (Set(), Set(v))
      case ZSpace.Iteration(src, h, r, t) => join(free(src), minus(free(t), Set(h), Set(r)))
      case ZSpace.Fixpoint(i, r, b) => join(free(i), minus(free(b), Set(), Set(r)))
      case ZSpace.Fold(src, i, a, h, r, t, u) =>
        join(join(free(src), fp(i)), minus(join(free(t), fp(u)), Set(a, h), Set(r)))
      case other =>
        (children(other).map(free) ++ paths(other).map(fp)).foldLeft((Set[PathRef](), Set[SpaceMention]()))(join)

  def size[A](s: ZSpace[A]): Int = 1 + children(s).map(size).sum

// ------------------------------------------------------------------------------------------------
// Path representations and the reference evaluator
// ------------------------------------------------------------------------------------------------
trait PathRepr[A, R]:
  def empty: R
  def fromAtoms(v: Vector[A]): R
  def toAtoms(r: R): Vector[A]
  def concat(x: R, y: R): R
  def startsWith(x: R, p: R): Boolean
  /** `x` with the prefix `p` removed; requires `startsWith(x, p)` */
  def dropPrefix(x: R, p: R): R
  /** (first atom as a length-1 path, the rest), or None on the empty path */
  def uncons(x: R): Option[(R, R)]
  /** the canonical trie order: atom order, shorter-is-less on a shared prefix */
  def compare(x: R, y: R): Int

/** Zippy_bits reference: a path b1..bn is the BigInt 1·b1·…·bn (a sentinel 1 keeps leading zeros). */
object BitRepr extends PathRepr[Bit, BigInt]:
  private inline def len(x: BigInt): Int = x.bitLength - 1
  val empty: BigInt = BigInt(1)
  def fromAtoms(v: Vector[Bit]): BigInt = v.foldLeft(BigInt(1))((acc, b) => if b then (acc << 1) | 1 else acc << 1)
  def toAtoms(r: BigInt): Vector[Bit] = { val n = len(r); Vector.tabulate(n)(i => r.testBit(n - 1 - i)) }
  def concat(x: BigInt, y: BigInt): BigInt = { val ly = len(y); (x << ly) | y.clearBit(ly) }
  def startsWith(x: BigInt, p: BigInt): Boolean = { val lx = len(x); val lp = len(p); lx >= lp && (x >> (lx - lp)) == p }
  def dropPrefix(x: BigInt, p: BigInt): BigInt =
    val k = len(x) - len(p)
    (x & ((BigInt(1) << k) - 1)).setBit(k)
  def uncons(x: BigInt): Option[(BigInt, BigInt)] =
    val n = len(x)
    if n == 0 then None
    else Some((if x.testBit(n - 1) then BigInt(3) else BigInt(2), (x & ((BigInt(1) << (n - 1)) - 1)).setBit(n - 1)))
  def compare(x: BigInt, y: BigInt): Int =
    val lx = len(x); val ly = len(y); val m = lx min ly
    val c = (x >> (lx - m)).compare(y >> (ly - m))
    if c != 0 then c else Integer.compare(lx, ly)

/** an immutable byte array with value equality and unsigned lexicographic order */
final class ByteArr(val a: Array[Byte]):
  override def equals(o: Any): Boolean = o match
    case b: ByteArr => java.util.Arrays.equals(a, b.a)
    case _ => false
  override def hashCode: Int = java.util.Arrays.hashCode(a)
  override def toString: String = a.map(b => f"${b & 0xff}%02x").mkString("ByteArr(", " ", ")")

/** Zippy_bytes reference: a path is a byte array, a space a Set[ByteArr]. */
object ByteRepr extends PathRepr[Byte, ByteArr]:
  val empty: ByteArr = ByteArr(Array.emptyByteArray)
  def fromAtoms(v: Vector[Byte]): ByteArr = ByteArr(v.toArray)
  def toAtoms(r: ByteArr): Vector[Byte] = r.a.toVector
  def concat(x: ByteArr, y: ByteArr): ByteArr = ByteArr(x.a ++ y.a)
  def startsWith(x: ByteArr, p: ByteArr): Boolean =
    x.a.length >= p.a.length && java.util.Arrays.equals(x.a, 0, p.a.length, p.a, 0, p.a.length)
  def dropPrefix(x: ByteArr, p: ByteArr): ByteArr = ByteArr(x.a.drop(p.a.length))
  def uncons(x: ByteArr): Option[(ByteArr, ByteArr)] =
    if x.a.isEmpty then None else Some((ByteArr(Array(x.a(0))), ByteArr(x.a.drop(1))))
  def compare(x: ByteArr, y: ByteArr): Int = java.util.Arrays.compareUnsigned(x.a, y.a)

/** Zippy_symbols over vectors (the `ZSpace[String]` twin of `eval`, used to check `ZSym` and as an oracle) */
final class VecRepr[A](using ord: Ordering[A]) extends PathRepr[A, Vector[A]]:
  val empty: Vector[A] = Vector.empty
  def fromAtoms(v: Vector[A]): Vector[A] = v
  def toAtoms(r: Vector[A]): Vector[A] = r
  def concat(x: Vector[A], y: Vector[A]): Vector[A] = x ++ y
  def startsWith(x: Vector[A], p: Vector[A]): Boolean = x.startsWith(p)
  def dropPrefix(x: Vector[A], p: Vector[A]): Vector[A] = x.drop(p.length)
  def uncons(x: Vector[A]): Option[(Vector[A], Vector[A])] = if x.isEmpty then None else Some((Vector(x.head), x.tail))
  def compare(x: Vector[A], y: Vector[A]): Int =
    val m = x.length min y.length
    var i = 0
    while i < m do
      val c = ord.compare(x(i), y(i))
      if c != 0 then return c
      i += 1
    Integer.compare(x.length, y.length)

/** THE REFERENCE EVALUATOR, one definition for every atom type and representation.  It follows
 *  `eval` (MORKL.scala) line for line: same group semantics for Iteration/Fold, same Fixpoint
 *  operator `X ↦ X ∪ F(X)`, same self-call passthrough on `Call`, same `Range` slice order. */
final class ZEval[A, R](val repr: PathRepr[A, R], routines: Map[RoutinePtr, ZRoutine[A]] = Map.empty[RoutinePtr, ZRoutine[A]]):
  type PC = Map[PathRef, R]
  type SC = Map[SpaceMention, Set[R]]
  private given Ordering[R] = (x: R, y: R) => repr.compare(x, y)
  private def bindP(pc: PC, k: PathRef, v: R): PC = if k.s == "_" then pc else pc.updated(k, v)
  private def bindS(sc: SC, k: SpaceMention, v: Set[R]): SC = if k.s == "_" then sc else sc.updated(k, v)

  def path(x: ZPath[A], pc: PC, sc: SC): R = x match
    case ZPath.Deref(pr) => pc.getOrElse(pr, throw RuntimeException(s"$pr path ref not resolved"))
    case ZPath.Constant(v) => repr.fromAtoms(v)
    case ZPath.Concat(l, r) => repr.concat(path(l, pc, sc), path(r, pc, sc))
    case ZPath.GroundedPP(q, f) => repr.fromAtoms(f(repr.toAtoms(path(q, pc, sc))))
    case ZPath.GroundedSP(q, f) => repr.fromAtoms(f(apply(q, pc, sc).map(repr.toAtoms)))

  private def groups(s: Set[R]): Map[R, Set[R]] = s.iterator.flatMap(repr.uncons).toSeq.groupMap(_._1)(_._2).view.mapValues(_.toSet).toMap

  def apply(s: ZSpace[A], pc: PC = Map.empty, sc: SC = Map.empty): Set[R] =
    def rec(x: ZSpace[A]): Set[R] = apply(x, pc, sc)
    s match
      case ZSpace.Empty() => Set.empty
      case ZSpace.Call(rp, refs, ms) =>
        val refvs = refs.map(path(_, pc, sc))
        val msvs = ms.map(rec)
        val ZRoutine(_, refns, msns, body) = routines.getOrElse(rp, throw RuntimeException(s"routine ${rp.s} not defined"))
        val pctx: PC = refns.zip(refvs).foldLeft(Map.empty: PC)((m, kv) => bindP(m, kv._1, kv._2))
        val sctx: SC = msns.zip(msvs).foldLeft(Map.empty: SC)((m, kv) => bindS(m, kv._1, kv._2))
        body match
          case ZSpace.Union(l, ZSpace.Call(`rp`, `refs`, `ms`))
            if refs.zip(refvs).forall((p, v) => path(p, pctx, sctx) == v) && ms.zip(msvs).forall((q, v) => apply(q, pctx, sctx) == v) =>
            apply(l, pctx, sctx)
          case _ => apply(body, pctx, sctx)
      case ZSpace.Mention(v) => sc.getOrElse(v, throw RuntimeException(s"$v space mention not resolved"))
      case ZSpace.Singleton(p) => Set(path(p, pc, sc))
      case ZSpace.Literal(ps) => ps.map(repr.fromAtoms)
      case ZSpace.Union(x, y) => rec(x) union rec(y)
      case ZSpace.Intersection(x, y) => rec(x) intersect rec(y)
      case ZSpace.Subtraction(x, y) => rec(x) removedAll rec(y)
      case ZSpace.Restriction(x, y) => val ps = rec(y); rec(x).filter(e => ps.exists(p => repr.startsWith(e, p)))
      case ZSpace.Raffination(x, y) => val ps = rec(y); rec(x).filterNot(e => ps.exists(p => repr.startsWith(e, p)))
      case ZSpace.Composition(x, y) => val ys = rec(y); for a <- rec(x); b <- ys yield repr.concat(a, b)
      case ZSpace.Wrap(src, p) => val pv = path(p, pc, sc); rec(src).map(repr.concat(pv, _))
      case ZSpace.Unwrap(src, p) => val pv = path(p, pc, sc); rec(src).collect { case e if repr.startsWith(e, pv) => repr.dropPrefix(e, pv) }
      case ZSpace.TailsUnion(src) => rec(src).flatMap(e => repr.uncons(e).map(_._2))
      case ZSpace.TailsIntersection(src) =>
        val g = groups(rec(src))
        if g.isEmpty then Set.empty else g.valuesIterator.reduce(_ intersect _)
      case ZSpace.Iteration(src, h, r, t) =>
        groups(rec(src)).iterator.flatMap((hv, rv) => apply(t, bindP(pc, h, hv), bindS(sc, r, rv))).toSet
      case ZSpace.Fixpoint(init, r, body) =>
        var cur = rec(init)
        var stop = false
        while !stop do
          val nxt = cur union apply(body, pc, bindS(sc, r, cur))
          if nxt == cur then stop = true else cur = nxt
        cur
      case ZSpace.Fold(src, initial, acc, h, r, t, u) =>
        var accV = path(initial, pc, sc)
        val out = Set.newBuilder[R]
        for (hv, rv) <- groups(rec(src)).toSeq.sortBy(_._1) do
          val pc1 = bindP(bindP(pc, acc, accV), h, hv)
          val sc1 = bindS(sc, r, rv)
          out ++= apply(t, pc1, sc1)
          accV = path(u, pc1, sc1)
        out.result()
      case ZSpace.GroundedPS(p, f) => f(repr.toAtoms(path(p, pc, sc))).map(repr.fromAtoms)
      case ZSpace.GroundedSS(q, f) => f(rec(q).map(repr.toAtoms)).map(repr.fromAtoms)
      case ZSpace.Range(x, lo, hi) =>
        val v = rec(x)
        val (l, h) = RangeBounds.normalize(v.size, lo, hi)
        if h <= l then Set.empty else if l == 0 && h == v.size then v else v.toVector.sorted.slice(l, h).toSet

object ZEval:
  def bits(rs: Iterable[ZRoutine[Bit]] = Nil) = ZEval(BitRepr, rs.map(r => r.name -> r).toMap)
  def bytes(rs: Iterable[ZRoutine[Byte]] = Nil) = ZEval(ByteRepr, rs.map(r => r.name -> r).toMap)
  def syms(rs: Iterable[ZRoutine[String]] = Nil) = ZEval(VecRepr[String], rs.map(r => r.name -> r).toMap)

// ------------------------------------------------------------------------------------------------
// Codes
// ------------------------------------------------------------------------------------------------
/** a set of target atoms a decoder transition fires on */
enum AtomClass[B]:
  case Is(b: B)
  case Not(bs: Set[B])
  case AnyAtom()

/** A prefix-free, order-preserving code A -> B*, with a deterministic decoding automaton
 *  (start state 0; a transition target `None` means "code complete"). */
trait Code[A, B]:
  def enc(a: A): Vector[B]
  def dec(code: Vector[B]): A
  def automaton: Vector[Vector[(AtomClass[B], Option[Int])]]
  /** the full code set when it is finite and known (licenses unrolling over the code trie) */
  def codeSet: Option[Vector[Vector[B]]] = None
  /** every code has exactly this length (a length hint of n atoms becomes n * fixedLength) */
  def fixedLength: Option[Int] = None
  def name: String

  private def matches(c: AtomClass[B], b: B): Boolean = c match
    case AtomClass.Is(x) => x == b
    case AtomClass.Not(xs) => !xs.contains(b)
    case AtomClass.AnyAtom() => true
  def encPath(v: Vector[A]): Vector[B] = v.flatMap(enc)
  def decPath(v: Vector[B]): Vector[A] =
    val out = Vector.newBuilder[A]
    var start = 0; var q = 0; var i = 0
    while i < v.length do
      automaton(q).find(t => matches(t._1, v(i))) match
        case None => throw IllegalArgumentException(s"$name: not a code at atom $i of $v")
        case Some((_, Some(q2))) => q = q2
        case Some((_, None)) => out += dec(v.slice(start, i + 1)); start = i + 1; q = 0
      i += 1
    if start != v.length then throw IllegalArgumentException(s"$name: truncated code in $v")
    out.result()
  /** the decoder has no cycle (fixed or bounded length) — it unrolls into nested Iterations */
  lazy val acyclic: Boolean =
    def ok(q: Int, seen: Set[Int]): Boolean = !seen(q) && automaton(q).forall(_._2.forall(ok(_, seen + q)))
    ok(0, Set())

object Codes:
  /** Fibonacci code of n >= 1: Zeckendorf digits, least significant first, then a terminating 1 */
  def fibonacci(n: Int): Vector[Bit] =
    require(n >= 1)
    val fibs = Iterator.iterate((1L, 2L))((a, b) => (b, a + b)).map(_._1).takeWhile(_ <= n).toVector
    val bits = Array.fill(fibs.length)(false)
    var r = n.toLong
    for i <- fibs.indices.reverse do if fibs(i) <= r then { bits(i) = true; r -= fibs(i) }
    bits.toVector :+ true

  private def lexLess[B](x: Vector[B], y: Vector[B])(using o: Ordering[B]): Boolean =
    VecRepr[B].compare(x, y) < 0
  private given Ordering[Bit] = Ordering.Boolean
  private given byteOrd: Ordering[Byte] = (x: Byte, y: Byte) => Integer.compare(x & 0xff, y & 0xff)

  /** INTERNED SYMBOLS -> BITS.  The universe is interned to 1..k and each index gets a Fibonacci code;
   *  symbols are assigned to the k codes in SORTED order of both, so the code is order-preserving
   *  (Range and Fold agree) and, being prefix-free, keeps Restriction/Unwrap exact.  The maximum code
   *  length (≈ log_φ k + 2) is the recursion bound of the decoder, which is unrolled when k is small. */
  final class FibInterned(universe: Vector[String], unrollUpTo: Int = 64) extends Code[String, Bit]:
    val name = "fib-interned"
    private val syms = universe.distinct.sorted
    private val codes = (1 to syms.length).map(fibonacci).toVector.sortWith(lexLess[Bit])
    private val encM = syms.zip(codes).toMap
    private val decM = codes.zip(syms).toMap
    val maxCodeLength: Int = if codes.isEmpty then 0 else codes.map(_.length).max
    def enc(a: String) = encM.getOrElse(a, throw IllegalArgumentException(s"$name: symbol '$a' outside the interned universe"))
    def dec(c: Vector[Bit]) = decM(c)
    val automaton = Vector(
      Vector(AtomClass.Is(false) -> Some(0), AtomClass.Is(true) -> Some(1)),
      Vector(AtomClass.Is(false) -> Some(0), AtomClass.Is(true) -> None))
    override def codeSet = if codes.length <= unrollUpTo then Some(codes) else None

  /** STRING SYMBOLS -> BYTES: UTF-8 with a 0 terminator.  Order-preserving (UTF-8 byte order is code
   *  point order; a terminator 0 makes a shorter symbol sort first) for symbols in the BMP outside the
   *  surrogate range, which is where Java's String order and code-point order agree. */
  object Utf8Z extends Code[String, Byte]:
    val name = "utf8-nul"
    def enc(a: String) =
      if a.indexOf('\u0000') >= 0 then throw IllegalArgumentException(s"$name: symbol contains NUL")
      a.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector :+ 0.toByte
    def dec(c: Vector[Byte]) = new String(c.init.toArray, java.nio.charset.StandardCharsets.UTF_8)
    val automaton = Vector(Vector(AtomClass.Is(0.toByte) -> None, AtomClass.Not(Set(0.toByte)) -> Some(0)))

  /** INTERNED SYMBOLS -> BYTES: a fixed 4-byte big-endian index, sorted interning (order-preserving). */
  final class Interned4(universe: Vector[String]) extends Code[String, Byte]:
    val name = "interned-4"
    private val syms = universe.distinct.sorted
    private val idx = syms.zipWithIndex.toMap
    def enc(a: String) =
      val i = idx.getOrElse(a, throw IllegalArgumentException(s"$name: symbol '$a' outside the interned universe"))
      Vector((i >>> 24).toByte, (i >>> 16).toByte, (i >>> 8).toByte, i.toByte)
    def dec(c: Vector[Byte]) = syms(c.foldLeft(0)((acc, b) => (acc << 8) | (b & 0xff)))
    val automaton = Vector.tabulate(4)(q => Vector(AtomClass.AnyAtom[Byte]() -> (if q == 3 then None else Some(q + 1))))
    override def fixedLength = Some(4)

  /** BYTES -> BITS, one to eight (MSB first: unsigned byte order = bit order) */
  object BytesToBits extends Code[Byte, Bit]:
    val name = "byte-8bit"
    def enc(a: Byte) = Vector.tabulate(8)(i => ((a >> (7 - i)) & 1) == 1)
    def dec(c: Vector[Bit]) = c.foldLeft(0)((acc, b) => (acc << 1) | (if b then 1 else 0)).toByte
    val automaton = Vector.tabulate(8)(q => Vector(AtomClass.AnyAtom[Bit]() -> (if q == 7 then None else Some(q + 1))))
    override def fixedLength = Some(8)

  /** a one-atom code: an injection of alphabets; the translation is then a homomorphism case for case */
  final class Injection[A, B](val name: String, f: A => B, g: B => A) extends Code[A, B]:
    def enc(a: A) = Vector(f(a))
    def dec(c: Vector[B]) = g(c.head)
    val automaton = Vector(Vector(AtomClass.AnyAtom[B]() -> None))
    override def fixedLength = Some(1)

  val BitsToBytes = Injection[Bit, Byte]("bit-byte", b => if b then 1.toByte else 0.toByte,
    { case 0 => false; case 1 => true; case x => throw IllegalArgumentException(s"byte $x is not a bit") })
  val BitsToSyms = Injection[Bit, String]("bit-symbol", b => if b then "1" else "0",
    { case "0" => false; case "1" => true; case x => throw IllegalArgumentException(s"symbol $x is not a bit") })
  val BytesToSyms = Injection[Byte, String]("byte-symbol", b => f"${b & 0xff}%02x",
    s => { require(s.length == 2, s"symbol $s is not a byte"); Integer.parseInt(s, 16).toByte })

// ------------------------------------------------------------------------------------------------
// The translation
// ------------------------------------------------------------------------------------------------
/** Translate a Zippy_A program (main + routine table) into Zippy_B along `code`.  The result is a
 *  program over B whose denotation is the image of the source denotation under `code.encPath`. */
final class CodedTranslation[A, B](val code: Code[A, B]):
  private val out = collection.mutable.LinkedHashMap.empty[RoutinePtr, ZRoutine[B]]
  private var fresh = 0
  private def nm(base: String): String = { fresh += 1; s"__${code.name}_${base}_$fresh" }
  private val unit = code.fixedLength.contains(1)

  def routines: Vector[ZRoutine[B]] = out.values.toVector

  def path(p: ZPath[A]): ZPath[B] = p match
    case ZPath.Deref(pr) => ZPath.Deref(ref(pr))
    case ZPath.Constant(v) => ZPath.Constant(code.encPath(v))
    case ZPath.Concat(l, r) => ZPath.Concat(path(l), path(r))
    case ZPath.GroundedPP(q, f) => ZPath.GroundedPP(path(q), v => code.encPath(f(code.decPath(v))))
    case ZPath.GroundedSP(q, f) => ZPath.GroundedSP(space(q), s => code.encPath(f(s.map(code.decPath))))

  /** a length hint of n atoms is n * L target atoms under a fixed-length code, unknown otherwise */
  private def ref(pr: PathRef): PathRef =
    if pr.lengthHint < 0 then PathRef(pr.s)
    else code.fixedLength match
      case Some(l) => PathRef(pr.s).known(pr.lengthHint * l)
      case None => PathRef(pr.s)

  def routine(r: ZRoutine[A]): ZRoutine[B] =
    val t = ZRoutine(r.name, r.refs.map(ref), r.mentions, space(r.body))
    out(r.name) = t
    t

  def space(s: ZSpace[A]): ZSpace[B] = s match
    case ZSpace.Empty() => ZSpace.Empty()
    case ZSpace.Call(r, refs, ms) => ZSpace.Call(r, refs.map(path), ms.map(space))
    case ZSpace.Mention(v) => ZSpace.Mention(v)
    case ZSpace.Singleton(p) => ZSpace.Singleton(path(p))
    case ZSpace.Literal(ps) => ZSpace.Literal(ps.map(code.encPath))
    case ZSpace.Union(x, y) => ZSpace.Union(space(x), space(y))
    case ZSpace.Intersection(x, y) => ZSpace.Intersection(space(x), space(y))
    case ZSpace.Subtraction(x, y) => ZSpace.Subtraction(space(x), space(y))
    case ZSpace.Restriction(x, y) => ZSpace.Restriction(space(x), space(y))
    case ZSpace.Raffination(x, y) => ZSpace.Raffination(space(x), space(y))
    case ZSpace.Composition(x, y) => ZSpace.Composition(space(x), space(y))
    case ZSpace.Fixpoint(i, r, b) => ZSpace.Fixpoint(space(i), r, space(b))
    case ZSpace.Wrap(x, p) => ZSpace.Wrap(space(x), path(p))
    case ZSpace.Unwrap(x, p) => ZSpace.Unwrap(space(x), path(p))
    case ZSpace.GroundedPS(p, f) => ZSpace.GroundedPS(path(p), v => f(code.decPath(v)).map(code.encPath))
    case ZSpace.GroundedSS(q, f) => ZSpace.GroundedSS(space(q), x => f(x.map(code.decPath)).map(code.encPath))
    // order-preserving code: the canonical-order slice of the image is the image of the slice
    case ZSpace.Range(x, lo, hi) => ZSpace.Range(space(x), lo, hi)
    // ---- the atom-observing cases ----
    case ZSpace.Iteration(src, h, r, t) =>
      if unit then ZSpace.Iteration(space(src), ref(h), r, space(t)) else iteration(space(src), h, r, t)
    case ZSpace.TailsUnion(x) =>
      if unit then ZSpace.TailsUnion(space(x))
      else { val r = SpaceMention(nm("r")); space(ZSpace.Iteration(x, PathRef(nm("h")), r, ZSpace.Mention(r))) }
    case ZSpace.TailsIntersection(x) =>
      if unit then ZSpace.TailsIntersection(space(x)) else space(tailsIntersection(x))
    case f @ ZSpace.Fold(src, i, a, h, r, t, u) =>
      if unit then ZSpace.Fold(space(src), path(i), ref(a), ref(h), r, space(t), path(u)) else space(fold(f))

  /** ⋂ᵢ Xᵢ = U \ ⋃ᵢ (U \ Xᵢ) with U = ⋃ᵢ Xᵢ — over the head groups of X, lifted to a routine of X */
  private def tailsIntersection(x: ZSpace[A]): ZSpace[A] =
    val name = RoutinePtr(nm("tails_inter"))
    val xm = SpaceMention(nm("x")); val um = SpaceMention(nm("u")); val r = SpaceMention(nm("r"))
    val tu = ZSpace.TailsUnion[A](ZSpace.Mention(xm))
    val inner = RoutinePtr(nm("tails_inter_body"))
    // body(X; U) = U \ ⋃_h (U \ X_h): U is evaluated once and passed down
    routine(ZRoutine(inner, Vector(), Vector(xm, um),
      ZSpace.Subtraction(ZSpace.Mention(um),
        ZSpace.Iteration(ZSpace.Mention(xm), PathRef(nm("h")), r, ZSpace.Subtraction(ZSpace.Mention(um), ZSpace.Mention(r))))))
    routine(ZRoutine(name, Vector(), Vector(xm), ZSpace.Call(inner, Vector(), Vector(ZSpace.Mention(xm), tu))))
    ZSpace.Call(name, Vector(), Vector(x))

  /** Fold as recursion over the canonically-first head group: take the least non-empty path
   *  (Range(·,1,2)), iterate it to bind its head h, run the template on group h, recurse on the
   *  remaining groups with the updated accumulator.  Group order = canonical order = `Fold`'s order. */
  private def fold(f: ZSpace.Fold[A]): ZSpace[A] =
    val ZSpace.Fold(src, initial, acc, h, rest, t, u) = f
    val (fp0, fm0) = ZOps.free(ZSpace.Union(t, ZSpace.Singleton(u)))
    val fp = (fp0 -- Set(acc, h)).toVector.sortBy(_.s); val fm = (fm0 - rest).toVector.sortBy(_.s)
    val loop = RoutinePtr(nm("fold")); val step = RoutinePtr(nm("fold_step"))
    val accR = PathRef(nm("acc")); val hR = PathRef(nm("h")); val ss = SpaceMention(nm("s"))
    val fpA = fp.map(ZPath.Deref[A](_)); val fmA = fm.map(ZSpace.Mention[A](_))
    val nonEmptyPaths = ZSpace.Subtraction[A](ZSpace.Mention(ss), ZSpace.Singleton(ZPath.Constant(Vector())))
    routine(ZRoutine(loop, accR +: fp, ss +: fm,
      ZSpace.Iteration(ZSpace.Range(nonEmptyPaths, 1, 2), hR, SpaceMention("_"),
        ZSpace.Call(step, Vector(ZPath.Deref(hR), ZPath.Deref(accR)) ++ fpA,
          Vector(ZSpace.Unwrap(ZSpace.Mention(ss), ZPath.Deref(hR)), ZSpace.Mention(ss)) ++ fmA))))
    routine(ZRoutine(step, Vector(h, acc) ++ fp, Vector(rest, ss) ++ fm,
      ZSpace.Union(t, ZSpace.Call(loop, u +: fpA,
        ZSpace.Raffination(ZSpace.Mention(ss), ZSpace.Singleton(ZPath.Deref(h))) +: fmA))))
    ZSpace.Call(loop, initial +: fpA, src +: fmA)

  /** Iteration(src, h, rest, t) over A-symbols as a decoder over B-atoms.  The body is lambda-lifted
   *  into a routine of (h, free path refs; rest, free mentions); the decoder walks the atoms of `src`
   *  with B-level Iterations, one routine per automaton state, accumulating the code in a path. */
  private def iteration(src: ZSpace[B], h: PathRef, rest: SpaceMention, t: ZSpace[A]): ZSpace[B] =
    val (fp0, fm0) = ZOps.free(t)
    val fp = (fp0 - h).toVector.sortBy(_.s); val fm = (fm0 - rest).toVector.sortBy(_.s)
    val fpB = fp.map(pr => ZPath.Deref[B](ref(pr))); val fmB = fm.map(ZSpace.Mention[B](_))
    val body = RoutinePtr(nm("iter_body"))
    routine(ZRoutine(body, ref(h) +: fp, rest +: fm, t))
    def callBody(hp: ZPath[B], r: ZSpace[B]) = ZSpace.Call(body, hp +: fpB, r +: fmB)
    def filter(c: AtomClass[B], x: ZSpace[B]): ZSpace[B] = c match
      case AtomClass.Is(b) => ZSpace.Restriction(x, ZSpace.Singleton(ZPath.Constant(Vector(b))))
      case AtomClass.Not(bs) => ZSpace.Raffination(x, ZSpace.Literal(bs.map(Vector(_))))
      case AtomClass.AnyAtom() => x
    code.codeSet match
      case Some(codes) =>
        // KNOWN UNIVERSE: unroll over the code trie (depth = max code length); h is a constant per leaf
        def trie(prefix: Vector[B], cs: Vector[Vector[B]], x: ZSpace[B]): ZSpace[B] =
          val byAtom = cs.groupBy(_(prefix.length)).toVector
          byAtom.map { (b, group) =>
            val p2 = prefix :+ b
            val r = SpaceMention(nm("r"))
            val k: ZSpace[B] =
              if group.exists(_.length == p2.length) then callBody(ZPath.Constant(p2), ZSpace.Mention(r))
              else trie(p2, group, ZSpace.Mention(r))
            ZSpace.Iteration(filter(AtomClass.Is(b), x), PathRef("_"), r, k): ZSpace[B]
          }.reduceOption(ZSpace.Union(_, _)).getOrElse(ZSpace.Empty())
        trie(Vector(), codes, src)
      case None if code.acyclic =>
        // BOUNDED DECODER (fixed-length codes): nested Iterations, h = concatenation of the atom binders
        def go(q: Int, acc: ZPath[B], x: ZSpace[B]): ZSpace[B] =
          code.automaton(q).map { (c, tgt) =>
            val b = PathRef(nm("b")).known(1); val r = SpaceMention(nm("r"))
            val acc2 = acc match
              case ZPath.Constant(v) if v.isEmpty => ZPath.Deref[B](b)
              case _ => ZPath.Concat(acc, ZPath.Deref(b))
            ZSpace.Iteration(filter(c, x), b, r,
              tgt match
                case None => callBody(acc2, ZSpace.Mention(r))
                case Some(q2) => go(q2, acc2, ZSpace.Mention(r))): ZSpace[B]
          }.reduceOption(ZSpace.Union(_, _)).getOrElse(ZSpace.Empty())
        go(0, ZPath.Constant(Vector()), src)
      case None =>
        // UNBOUNDED DECODER: one recursive routine per state, (acc, free refs; X, free mentions)
        val states = code.automaton.indices.map(q => RoutinePtr(nm(s"iter_q$q"))).toVector
        val accR = PathRef(nm("acc")); val xm = SpaceMention(nm("x"))
        for q <- code.automaton.indices do
          val arms = code.automaton(q).map { (c, tgt) =>
            val b = PathRef(nm("b")).known(1); val r = SpaceMention(nm("r"))
            val acc2 = ZPath.Concat[B](ZPath.Deref(accR), ZPath.Deref(b))
            ZSpace.Iteration(filter(c, ZSpace.Mention(xm)), b, r,
              tgt match
                case None => callBody(acc2, ZSpace.Mention(r))
                case Some(q2) => ZSpace.Call(states(q2), acc2 +: fpB, ZSpace.Mention[B](r) +: fmB)): ZSpace[B]
          }
          out(states(q)) = ZRoutine(states(q), accR +: fp.map(ref), xm +: fm, arms.reduce(ZSpace.Union(_, _)))
        ZSpace.Call(states(0), ZPath.Constant[B](Vector()) +: fpB, src +: fmB)

object CodedTranslation:
  /** translate (main, routines) along `code`; returns the target main and the full target routine table */
  def apply[A, B](code: Code[A, B], main: ZSpace[A], routines: Iterable[ZRoutine[A]] = Nil): (ZSpace[B], Vector[ZRoutine[B]]) =
    val t = new CodedTranslation(code)
    routines.foreach(t.routine)
    val m = t.space(main)
    (m, t.routines)
