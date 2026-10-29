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
 *  path argument, and call the lambda-lifted body when the code is complete.  Fixed-length decoders
 *  unroll into nested Iterations; a code with a known maximum length L (Fibonacci over a bounded id
 *  or dictionary range) is specialized by depth into a recursion-free DAG of states × L routines.
 *  The decoder's source is a routine argument, so it is evaluated once.  No metadata is stored and
 *  no operator is added: only the existing cases and routine abstraction are used.
 *
 *  CODES (`Codes`):
 *    string-nul   String -> bytes, every UTF-16 unit as generalized UTF-8, 00/01 escaped, 00-terminated
 *                 (total over Java strings, order-preserving)
 *    fib-interner String -> bits, Fibonacci code of the stable `Interner` id + 1 (ids < idBound)
 *    interner-4   String -> bytes, the 4 big-endian bytes of the `Interner` id
 *    fib-dict / dict-4  closed-dictionary, order-preserving specializations of the two above
 *    byte-8bit    bytes -> bits;  bit-byte, bit-symbol, byte-symbol: alphabet injections
 *  Interner-id codes do not preserve symbol order, so `Range` (other than the identity window) and
 *  `Fold` are rejected for them with a named error rather than translated wrongly.
 *
 *  EXAMPLE
 *  {{{
 *    val src: SymSpace = ZSym.from(Space.TailsUnion(Space.Mention(SpaceMention("s"))))
 *    val (main, routines) = CodedTranslation(Codes.InternerId4, src)
 *    val in = Set(Vector("x", "y")).map(p => ByteRepr.fromAtoms(Codes.InternerId4.encPath(p)))
 *    ZEval.bytes(routines)(main, sc = Map(SpaceMention("s") -> in))   // == {encPath(Vector("y"))}
 *  }}}
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

  /** free path refs and free space mentions (binders: Iteration h/rest, Fold acc/h/rest, Fixpoint rec).
   *  A binder spelled `_` binds NOTHING (the evaluators ignore it), so an enclosing `_` stays free
   *  through it; a `_` dereference is an ordinary free reference. */
  def free[A](s: ZSpace[A]): (Set[PathRef], Set[SpaceMention]) =
    def bp(ps: PathRef*): Set[PathRef] = ps.filter(_.s != "_").toSet
    def bm(ms: SpaceMention*): Set[SpaceMention] = ms.filter(_.s != "_").toSet
    def fp(x: ZPath[A]): (Set[PathRef], Set[SpaceMention]) = x match
      case ZPath.Deref(pr) => (Set(pr), Set())
      case ZPath.Constant(_) => (Set(), Set())
      case ZPath.Concat(l, r) => join(fp(l), fp(r))
      case ZPath.GroundedPP(q, _) => fp(q)
      case ZPath.GroundedSP(q, _) => free(q)
    def join(a: (Set[PathRef], Set[SpaceMention]), b: (Set[PathRef], Set[SpaceMention])) = (a._1 ++ b._1, a._2 ++ b._2)
    def minus(a: (Set[PathRef], Set[SpaceMention]), ps: Set[PathRef], ms: Set[SpaceMention]) = (a._1 -- ps, a._2 -- ms)
    s match
      case ZSpace.Mention(v) => (Set(), Set(v))
      case ZSpace.Iteration(src, h, r, t) => join(free(src), minus(free(t), bp(h), bm(r)))
      case ZSpace.Fixpoint(i, r, b) => join(free(i), minus(free(b), Set(), bm(r)))
      case ZSpace.Fold(src, i, a, h, r, t, u) =>
        join(join(free(src), fp(i)), minus(join(free(t), fp(u)), bp(a, h), bm(r)))
      case other =>
        (children(other).map(free) ++ paths(other).map(fp)).foldLeft((Set[PathRef](), Set[SpaceMention]()))(join)

  def size[A](s: ZSpace[A]): Int = 1 + children(s).map(size).sum

  /** every NAME a program uses — routine names, path refs and space mentions, bound or free — so a
   *  translator can generate names that capture nothing and overwrite no routine */
  def names[A](s: ZSpace[A]): Set[String] =
    def p(x: ZPath[A]): Set[String] = x match
      case ZPath.Deref(pr) => Set(pr.s)
      case ZPath.Constant(_) => Set()
      case ZPath.Concat(l, r) => p(l) ++ p(r)
      case ZPath.GroundedPP(q, _) => p(q)
      case ZPath.GroundedSP(q, _) => names(q)
    val own: Set[String] = s match
      case ZSpace.Call(r, _, _) => Set(r.s)
      case ZSpace.Mention(v) => Set(v.s)
      case ZSpace.Iteration(_, h, r, _) => Set(h.s, r.s)
      case ZSpace.Fixpoint(_, r, _) => Set(r.s)
      case ZSpace.Fold(_, _, a, h, r, _, _) => Set(a.s, h.s, r.s)
      case _ => Set()
    own ++ children(s).flatMap(names) ++ paths(s).flatMap(p)
  def names[A](r: ZRoutine[A]): Set[String] = Set(r.name.s) ++ r.refs.map(_.s) ++ r.mentions.map(_.s) ++ names(r.body)

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
  def length(x: R): Int

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
  def length(x: BigInt): Int = len(x)
  def compare(x: BigInt, y: BigInt): Int =
    val lx = len(x); val ly = len(y); val m = lx min ly
    val c = (x >> (lx - m)).compare(y >> (ly - m))
    if c != 0 then c else Integer.compare(lx, ly)

/** an IMMUTABLE byte array with value equality and unsigned lexicographic order.  The storage is
 *  private and never escapes: the public constructor copies its argument and `bytes` returns a
 *  copy, so a path stored in a hashed set can never change its hash.  `ByteRepr` builds paths from
 *  arrays it has just allocated through the package-private `owned`, skipping the defensive copy. */
final class ByteArr private (private val a: Array[Byte]):
  def length: Int = a.length
  def apply(i: Int): Byte = a(i)
  def bytes: Array[Byte] = a.clone()
  override def equals(o: Any): Boolean = o match
    case b: ByteArr => java.util.Arrays.equals(a, b.a)
    case _ => false
  override val hashCode: Int = java.util.Arrays.hashCode(a)
  override def toString: String = a.map(b => f"${b & 0xff}%02x").mkString("ByteArr(", " ", ")")
object ByteArr:
  def apply(a: Array[Byte]): ByteArr = new ByteArr(a.clone())
  private[morkl] def owned(a: Array[Byte]): ByteArr = new ByteArr(a)
  private[morkl] def raw(b: ByteArr): Array[Byte] = b.a

/** Zippy_bytes reference: a path is a byte array, a space a Set[ByteArr]. */
object ByteRepr extends PathRepr[Byte, ByteArr]:
  import ByteArr.{owned, raw}
  val empty: ByteArr = owned(Array.emptyByteArray)
  def fromAtoms(v: Vector[Byte]): ByteArr = owned(v.toArray)
  def toAtoms(r: ByteArr): Vector[Byte] = raw(r).toVector
  def concat(x: ByteArr, y: ByteArr): ByteArr = owned(raw(x) ++ raw(y))
  def startsWith(x: ByteArr, p: ByteArr): Boolean =
    val xa = raw(x); val pa = raw(p)
    xa.length >= pa.length && java.util.Arrays.equals(xa, 0, pa.length, pa, 0, pa.length)
  def dropPrefix(x: ByteArr, p: ByteArr): ByteArr = owned(raw(x).drop(p.length))
  def uncons(x: ByteArr): Option[(ByteArr, ByteArr)] =
    val xa = raw(x)
    if xa.isEmpty then None else Some((owned(Array(xa(0))), owned(xa.drop(1))))
  def length(x: ByteArr): Int = x.length
  def compare(x: ByteArr, y: ByteArr): Int = java.util.Arrays.compareUnsigned(raw(x), raw(y))

/** Zippy_symbols over vectors (the `ZSpace[String]` twin of `eval`, used to check `ZSym` and as an oracle) */
final class VecRepr[A](using ord: Ordering[A]) extends PathRepr[A, Vector[A]]:
  val empty: Vector[A] = Vector.empty
  def fromAtoms(v: Vector[A]): Vector[A] = v
  def toAtoms(r: Vector[A]): Vector[A] = r
  def concat(x: Vector[A], y: Vector[A]): Vector[A] = x ++ y
  def startsWith(x: Vector[A], p: Vector[A]): Boolean = x.startsWith(p)
  def dropPrefix(x: Vector[A], p: Vector[A]): Vector[A] = x.drop(p.length)
  def uncons(x: Vector[A]): Option[(Vector[A], Vector[A])] = if x.isEmpty then None else Some((Vector(x.head), x.tail))
  def length(x: Vector[A]): Int = x.length
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
final class ZEval[A, R](val repr: PathRepr[A, R], routines: Map[RoutinePtr, ZRoutine[A]] = Map.empty[RoutinePtr, ZRoutine[A]],
                        checkHints: Boolean = false):
  /** AST nodes evaluated so far: the work counter the scaling checks read */
  var steps: Long = 0L
  /** with `checkHints`, every binding of an annotated ref (`.known(n)`) is checked against its value */
  private def hint(pr: PathRef, v: R): Unit =
    if checkHints && pr.lengthHint >= 0 && repr.length(v) != pr.lengthHint then
      throw AssertionError(s"length annotation violated: ${pr.s}.known(${pr.lengthHint}) bound to a path of length ${repr.length(v)}")
  type PC = Map[PathRef, R]
  type SC = Map[SpaceMention, Set[R]]
  private given Ordering[R] = (x: R, y: R) => repr.compare(x, y)
  private def bindP(pc: PC, k: PathRef, v: R): PC = { hint(k, v); if k.s == "_" then pc else pc.updated(k, v) }
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
    steps += 1
    s match
      case ZSpace.Empty() => Set.empty
      case ZSpace.Call(rp, refs, ms) =>
        val refvs = refs.map(path(_, pc, sc))
        val msvs = ms.map(rec)
        val ZRoutine(_, refns, msns, body) = routines.getOrElse(rp, throw RuntimeException(s"routine ${rp.s} not defined"))
        // formals are bound DIRECTLY (as `eval` does: `PathContextMap(Map.from(refns zip refvs))`):
        // only local binders ignore `_`; a formal named `_` is an ordinary parameter
        refns.zip(refvs).foreach(hint)
        val pctx: PC = Map.from(refns zip refvs)
        val sctx: SC = Map.from(msns zip msvs)
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
  def bits(rs: Iterable[ZRoutine[Bit]] = Nil, checkHints: Boolean = false) = ZEval(BitRepr, rs.map(r => r.name -> r).toMap, checkHints)
  def bytes(rs: Iterable[ZRoutine[Byte]] = Nil, checkHints: Boolean = false) = ZEval(ByteRepr, rs.map(r => r.name -> r).toMap, checkHints)
  def syms(rs: Iterable[ZRoutine[String]] = Nil, checkHints: Boolean = false) = ZEval(VecRepr[String], rs.map(r => r.name -> r).toMap, checkHints)

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
  /** the longest codeword of the ADMISSIBLE symbol set, when bounded: the decoder's recursion depth.
   *  It is a bound, not an exact length — variable-length codes never annotate `.known(max)`. */
  def maxCodeLength: Option[Int] = None
  /** every code has exactly this length (a length hint of n atoms becomes n * fixedLength) */
  def fixedLength: Option[Int] = None
  /** code order = symbol order (Java `String.compareTo` per item): required for `Range` and `Fold` */
  def orderPreserving: Boolean = true
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

  /** CLOSED-DICTIONARY SYMBOLS -> BITS (an order-preserving specialization).  A fixed universe is
   *  ranked 1..k and each rank gets a Fibonacci code; symbols go to the k codewords in SORTED order of
   *  both, so the code preserves order (Range/Fold translate structurally) and, being prefix-free,
   *  keeps Restriction/Unwrap exact.  The universe is the compilation contract: one compiled program
   *  serves every input frame over it. */
  final class FibDictionary(universe: Vector[String]) extends Code[String, Bit]:
    val name = "fib-dict"
    private val syms = universe.distinct.sorted
    private val codes = (1 to syms.length).map(fibonacci).toVector.sortWith(lexLess[Bit])
    private val encM = syms.zip(codes).toMap
    private val decM = codes.zip(syms).toMap
    def enc(a: String) = encM.getOrElse(a, throw IllegalArgumentException(s"$name: symbol '$a' outside the dictionary"))
    def dec(c: Vector[Bit]) = decM(c)
    val automaton = FibAutomaton
    override val maxCodeLength = Some(if codes.isEmpty then 0 else codes.map(_.length).max)

  val FibAutomaton: Vector[Vector[(AtomClass[Bit], Option[Int])]] = Vector(
    Vector(AtomClass.Is(false) -> Some(0), AtomClass.Is(true) -> Some(1)),
    Vector(AtomClass.Is(false) -> Some(0), AtomClass.Is(true) -> None))

  /** INTERNED SYMBOLS -> BITS: the Fibonacci code of the repository's stable `Interner` id + 1.  Codes
   *  do not depend on the program or on other symbols.  CONTRACT: every admissible symbol has an id
   *  below `idBound`; that bound fixes the maximum code length, i.e. the decoder's recursion depth.
   *  Interner ids are first-seen order, not symbol order, so `Range`/`Fold` are rejected. */
  final class FibInterner(val idBound: Int) extends Code[String, Bit]:
    require(idBound >= 1)
    val name = "fib-interner"
    def enc(a: String) =
      val id = Interner.intern(a)
      if id >= idBound then throw IllegalArgumentException(s"$name: '$a' has interner id $id >= bound $idBound")
      fibonacci(id + 1)
    def dec(c: Vector[Bit]) =
      val fibs = Iterator.iterate((1L, 2L))((a, b) => (b, a + b)).map(_._1)
      Interner.unintern((c.init.zip(fibs).collect { case (true, f) => f }.sum - 1).toInt)
    val automaton = FibAutomaton
    override val maxCodeLength = Some((1 to idBound).iterator.map(fibonacci(_).length).max)
    override def orderPreserving = false

  /** STRING SYMBOLS -> BYTES, NUL-terminated.  Every UTF-16 code unit (lone surrogates included) is
   *  written as the generalized-UTF-8 encoding of its 16-bit value, so byte order = code-unit order =
   *  `String.compareTo`, and every Java string round-trips.  Units 0x00 and 0x01 are escaped as
   *  01 01 and 01 02 (order kept: 01 01 < 01 02 < 02), so the byte 00 occurs only as the terminator:
   *  the code is prefix-free and a shorter string sorts first. */
  object StringBytes extends Code[String, Byte]:
    val name = "string-nul"
    def enc(a: String) =
      val out = Vector.newBuilder[Byte]
      a.foreach { ch =>
        val u = ch.toInt
        if u <= 1 then { out += 1.toByte; out += (u + 1).toByte }
        else if u < 0x80 then out += u.toByte
        else if u < 0x800 then { out += (0xC0 | (u >> 6)).toByte; out += (0x80 | (u & 0x3F)).toByte }
        else { out += (0xE0 | (u >> 12)).toByte; out += (0x80 | ((u >> 6) & 0x3F)).toByte; out += (0x80 | (u & 0x3F)).toByte }
      }
      out += 0.toByte
      out.result()
    def dec(c: Vector[Byte]) =
      val sb = new StringBuilder
      var i = 0
      val n = c.length - 1
      while i < n do
        val b = c(i) & 0xff
        if b == 1 then { sb += (c(i + 1) - 1).toChar; i += 2 }
        else if b < 0x80 then { sb += b.toChar; i += 1 }
        else if b < 0xE0 then { sb += (((b & 0x1F) << 6) | (c(i + 1) & 0x3F)).toChar; i += 2 }
        else { sb += (((b & 0x0F) << 12) | ((c(i + 1) & 0x3F) << 6) | (c(i + 2) & 0x3F)).toChar; i += 3 }
      sb.result()
    val automaton = Vector(Vector(AtomClass.Is(0.toByte) -> None, AtomClass.Not(Set(0.toByte)) -> Some(0)))

  /** CLOSED-DICTIONARY SYMBOLS -> BYTES: fixed 4-byte big-endian sorted rank (order-preserving specialization) */
  final class Dictionary4(universe: Vector[String]) extends Code[String, Byte]:
    val name = "dict-4"
    private val syms = universe.distinct.sorted
    private val idx = syms.zipWithIndex.toMap
    def enc(a: String) = be4(idx.getOrElse(a, throw IllegalArgumentException(s"$name: symbol '$a' outside the dictionary")))
    def dec(c: Vector[Byte]) = syms(unbe4(c))
    val automaton = Fixed4
    override def fixedLength = Some(4)

  /** INTERNED SYMBOLS -> BYTES: the 4 big-endian bytes of the stable `Interner` id — the same int an
   *  `ITrie` keys on.  Stable across programs; not order-preserving, so `Range`/`Fold` are rejected. */
  object InternerId4 extends Code[String, Byte]:
    val name = "interner-4"
    def enc(a: String) = be4(Interner.intern(a))
    def dec(c: Vector[Byte]) = Interner.unintern(unbe4(c))
    val automaton = Fixed4
    override def fixedLength = Some(4)
    override def orderPreserving = false

  private def be4(i: Int) = Vector((i >>> 24).toByte, (i >>> 16).toByte, (i >>> 8).toByte, i.toByte)
  private def unbe4(c: Vector[Byte]) = c.foldLeft(0)((acc, b) => (acc << 8) | (b & 0xff))
  private val Fixed4 = Vector.tabulate(4)(q => Vector(AtomClass.AnyAtom[Byte]() -> (if q == 3 then None else Some(q + 1))))

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
 *  program over B whose denotation is the image of the source denotation under `code.encPath`.
 *
 *  HYGIENE.  `reserved` holds every name of the source program (routines, refs, mentions, bound or
 *  free); generated names avoid all of them, so no generated routine overwrites a source routine and
 *  no generated binder captures a source variable.  Lambda-lifted formals keep the native shadowing
 *  order (a Fold's head shadows its accumulator), and a binder spelled `_` — which binds nothing —
 *  gets a fresh formal so an enclosing `_` stays visible.
 *
 *  DECODERS.  The iteration source is evaluated ONCE: it is passed as the space argument of a decoder
 *  entry routine, and every dispatch arm refers to that parameter.  Decoders are chosen by bound:
 *  fixed-length codes unroll into nested Iterations; codes with a known `maxCodeLength` L are
 *  specialized by depth into a recursion-free DAG of states × L routines (budget `depthBudget`);
 *  otherwise one recursive routine per automaton state. */
final class CodedTranslation[A, B](val code: Code[A, B], reserved: Set[String], depthBudget: Int = 4096):
  private val out = collection.mutable.LinkedHashMap.empty[RoutinePtr, ZRoutine[B]]
  private var fresh = 0
  private def nm(base: String): String =
    var n = ""
    while { fresh += 1; n = s"${code.name}_${base}_$fresh"; reserved(n) } do ()
    n
  private val unit = code.fixedLength.contains(1)

  def routines: Vector[ZRoutine[B]] = out.values.toVector

  def path(p: ZPath[A]): ZPath[B] = p match
    case ZPath.Deref(pr) => ZPath.Deref(ref(pr))
    case ZPath.Constant(v) => ZPath.Constant(code.encPath(v))
    case ZPath.Concat(l, r) => ZPath.Concat(path(l), path(r))
    case ZPath.GroundedPP(q, f) => ZPath.GroundedPP(path(q), v => code.encPath(f(code.decPath(v))))
    case ZPath.GroundedSP(q, f) => ZPath.GroundedSP(space(q), s => code.encPath(f(s.map(code.decPath))))

  /** SOURCE ref -> TARGET ref, applied exactly once per occurrence: a length hint of n atoms is
   *  n * L target atoms under a fixed-length code, and unknown (-1) under a variable-length code */
  private def ref(pr: PathRef): PathRef =
    if pr.lengthHint < 0 then PathRef(pr.s)
    else code.fixedLength match
      case Some(l) => PathRef(pr.s).known(pr.lengthHint * l)
      case None => PathRef(pr.s)

  /** translate a SOURCE routine (formals are source refs) */
  def routine(r: ZRoutine[A]): ZRoutine[B] =
    require(!out.contains(r.name), s"routine ${r.name.s} defined twice")
    val t = ZRoutine(r.name, r.refs.map(ref), r.mentions, space(r.body))
    out(r.name) = t
    t
  private def emit(r: ZRoutine[B]): Unit =
    require(!out.contains(r.name), s"routine ${r.name.s} defined twice"); out(r.name) = r

  private def ordered(what: String): Unit =
    if !code.orderPreserving then throw UnsupportedOperationException(
      s"${code.name}: $what needs symbol order, which this code (stable interner ids) does not preserve; " +
      "use an order-preserving code (string-nul, fib-dict, dict-4) for programs with ordered operators")

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
    case ZSpace.Range(x, lo, hi) =>
      if !(lo == 0 && hi == 0) then ordered("Range")
      ZSpace.Range(space(x), lo, hi)
    // ---- the atom-observing cases ----
    case ZSpace.Iteration(src, h, r, t) =>
      if unit then ZSpace.Iteration(space(src), ref(h), r, space(t)) else iteration(space(src), h, r, t)
    case ZSpace.TailsUnion(x) =>
      if unit then ZSpace.TailsUnion(space(x))
      else { val r = SpaceMention(nm("r")); space(ZSpace.Iteration(x, PathRef(nm("h")), r, ZSpace.Mention(r))) }
    case ZSpace.TailsIntersection(x) =>
      if unit then ZSpace.TailsIntersection(space(x)) else space(tailsIntersection(x))
    case f @ ZSpace.Fold(src, i, a, h, r, t, u) =>
      if unit then ZSpace.Fold(space(src), path(i), ref(a), ref(h), r, space(t), path(u))
      else { ordered("Fold"); space(fold(f)) }

  /** a binder spelled `_` binds nothing: give its lifted formal a fresh name so `_` stays free */
  private def formalP(pr: PathRef): PathRef = if pr.s == "_" then PathRef(nm("ignored")) else pr
  private def formalS(sm: SpaceMention): SpaceMention = if sm.s == "_" then SpaceMention(nm("ignored")) else sm

  /** ⋂ᵢ Xᵢ = U \ ⋃ᵢ (U \ Xᵢ) with U = ⋃ᵢ Xᵢ — over the head groups of X; X and U are routine
   *  arguments, so each is evaluated once */
  private def tailsIntersection(x: ZSpace[A]): ZSpace[A] =
    val name = RoutinePtr(nm("tails_inter"))
    val xm = SpaceMention(nm("x")); val um = SpaceMention(nm("u")); val r = SpaceMention(nm("r"))
    val tu = ZSpace.TailsUnion[A](ZSpace.Mention(xm))
    val inner = RoutinePtr(nm("tails_inter_body"))
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
    val fp = (fp0 -- Set(acc, h).filter(_.s != "_")).toVector.sortBy(_.s)
    val fm = (fm0 -- Set(rest).filter(_.s != "_")).toVector.sortBy(_.s)
    val loop = RoutinePtr(nm("fold")); val step = RoutinePtr(nm("fold_step"))
    val accR = PathRef(nm("acc")); val hR = PathRef(nm("h")); val ss = SpaceMention(nm("s"))
    val fpA = fp.map(ZPath.Deref[A](_)); val fmA = fm.map(ZSpace.Mention[A](_))
    val nonEmptyPaths = ZSpace.Subtraction[A](ZSpace.Mention(ss), ZSpace.Singleton(ZPath.Constant(Vector())))
    routine(ZRoutine(loop, accR +: fp, ss +: fm,
      ZSpace.Iteration(ZSpace.Range(nonEmptyPaths, 1, 2), hR, SpaceMention(nm("ignored")),
        ZSpace.Call(step, Vector(ZPath.Deref(accR), ZPath.Deref(hR)) ++ fpA,
          Vector(ZSpace.Unwrap(ZSpace.Mention(ss), ZPath.Deref(hR)), ZSpace.Mention(ss)) ++ fmA))))
    // formals (acc, h): later formals win, so the head shadows the accumulator exactly as Fold binds them
    val hS = formalP(h)
    routine(ZRoutine(step, Vector(formalP(acc), hS) ++ fp, Vector(formalS(rest), ss) ++ fm,
      ZSpace.Union(t, ZSpace.Call(loop, u +: fpA,
        ZSpace.Raffination(ZSpace.Mention(ss), ZSpace.Singleton(ZPath.Deref(hS))) +: fmA))))
    ZSpace.Call(loop, initial +: fpA, src +: fmA)

  /** Iteration(src, h, rest, t) over A-symbols as a decoder over B-atoms.  The body is lambda-lifted
   *  into a routine of (h, free path refs; rest, free mentions); the decoder walks the atoms of its
   *  space parameter (bound once to `src`) with B-level Iterations, accumulating the code in a path. */
  private def iteration(src: ZSpace[B], h: PathRef, rest: SpaceMention, t: ZSpace[A]): ZSpace[B] =
    val (fp0, fm0) = ZOps.free(t)
    val fp = (fp0 -- Set(h).filter(_.s != "_")).toVector.sortBy(_.s)
    val fm = (fm0 -- Set(rest).filter(_.s != "_")).toVector.sortBy(_.s)
    val fpT = fp.map(ref)
    val fpB = fpT.map(ZPath.Deref[B](_)); val fmB = fm.map(ZSpace.Mention[B](_))
    val body = RoutinePtr(nm("iter_body"))
    routine(ZRoutine(body, formalP(h) +: fp, formalS(rest) +: fm, t))
    def callBody(hp: ZPath[B], r: ZSpace[B]) = ZSpace.Call(body, hp +: fpB, r +: fmB)
    def filter(c: AtomClass[B], x: ZSpace[B]): ZSpace[B] = c match
      case AtomClass.Is(b) => ZSpace.Restriction(x, ZSpace.Singleton(ZPath.Constant(Vector(b))))
      case AtomClass.Not(bs) => ZSpace.Raffination(x, ZSpace.Literal(bs.map(Vector(_))))
      case AtomClass.AnyAtom() => x
    def arm(c: AtomClass[B], x: ZSpace[B], acc: ZPath[B], k: (ZPath[B], ZSpace[B]) => ZSpace[B]): ZSpace[B] =
      val b = PathRef(nm("b")).known(1); val r = SpaceMention(nm("r"))
      val acc2 = acc match
        case ZPath.Constant(v) if v.isEmpty => ZPath.Deref[B](b)
        case _ => ZPath.Concat(acc, ZPath.Deref(b))
      ZSpace.Iteration(filter(c, x), b, r, k(acc2, ZSpace.Mention(r)))
    def unions(xs: Iterable[ZSpace[B]]): ZSpace[B] = xs.reduceOption(ZSpace.Union(_, _)).getOrElse(ZSpace.Empty())
    val nStates = code.automaton.length
    val xm = SpaceMention(nm("x")); val accR = PathRef(nm("acc"))
    val bounded = code.maxCodeLength.filter(l => l.toLong * nStates <= depthBudget)
    if code.acyclic then
      // FIXED/BOUNDED-LENGTH DECODER: one entry routine, nested Iterations, h = the atom binders
      val entry = RoutinePtr(nm("iter_entry"))
      def go(q: Int, acc: ZPath[B], x: ZSpace[B]): ZSpace[B] =
        unions(code.automaton(q).map((c, tgt) => arm(c, x, acc, (acc2, r) => tgt match
          case None => callBody(acc2, r)
          case Some(q2) => go(q2, acc2, r))))
      emit(ZRoutine(entry, fpT, xm +: fm, go(0, ZPath.Constant(Vector()), ZSpace.Mention(xm))))
      ZSpace.Call(entry, fpB, src +: fmB)
    else bounded match
      case Some(maxLen) =>
        // DEPTH-SPECIALIZED DECODER: state (q, d) has consumed d < maxLen atoms; a transition that would
        // need atom maxLen+1 is dropped.  Recursion-free: depth ≤ maxLen, size = states × maxLen.
        val rs = Vector.tabulate(nStates, maxLen)((q, d) => RoutinePtr(nm(s"iter_q${q}_d$d")))
        for q <- 0 until nStates; d <- 0 until maxLen do
          val arms = code.automaton(q).flatMap { (c, tgt) =>
            tgt match
              case None => Some(arm(c, ZSpace.Mention(xm), ZPath.Deref(accR), (acc2, r) => callBody(acc2, r)))
              case Some(q2) if d + 1 < maxLen =>
                Some(arm(c, ZSpace.Mention(xm), ZPath.Deref(accR), (acc2, r) => ZSpace.Call(rs(q2)(d + 1), acc2 +: fpB, r +: fmB)))
              case _ => None
          }
          emit(ZRoutine(rs(q)(d), accR +: fpT, xm +: fm, unions(arms)))
        if maxLen == 0 then ZSpace.Empty() else ZSpace.Call(rs(0)(0), ZPath.Constant[B](Vector()) +: fpB, src +: fmB)
      case None =>
        // UNBOUNDED DECODER: one recursive routine per state, (acc, free refs; X, free mentions)
        val states = code.automaton.indices.map(q => RoutinePtr(nm(s"iter_q$q"))).toVector
        for q <- code.automaton.indices do
          val arms = code.automaton(q).map((c, tgt) => arm(c, ZSpace.Mention(xm), ZPath.Deref(accR), (acc2, r) => tgt match
            case None => callBody(acc2, r)
            case Some(q2) => ZSpace.Call(states(q2), acc2 +: fpB, r +: fmB)))
          emit(ZRoutine(states(q), accR +: fpT, xm +: fm, unions(arms)))
        ZSpace.Call(states(0), ZPath.Constant[B](Vector()) +: fpB, src +: fmB)

object CodedTranslation:
  /** translate (main, routines) along `code`; returns the target main and the full target routine table */
  def apply[A, B](code: Code[A, B], main: ZSpace[A], routines: Iterable[ZRoutine[A]] = Nil, depthBudget: Int = 4096): (ZSpace[B], Vector[ZRoutine[B]]) =
    val reserved = ZOps.names(main) ++ routines.flatMap(ZOps.names(_))
    val t = new CodedTranslation(code, reserved, depthBudget)
    routines.foreach(t.routine)
    val m = t.space(main)
    (m, t.routines)
