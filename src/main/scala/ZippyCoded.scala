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
 *  Interner-id codes do not preserve symbol order.  Order-free uses of `Fold` (accumulator `_` or
 *  unchanged) become one Iteration under every code.  Other `Range` / `Fold` need a FROZEN ADMISSIBLE
 *  VOCABULARY (`InternerId4.ordered(vocab)`, `FibInterner(bound).ordered(vocab)`): ids stay the stable
 *  interner ids, and the compiler emits ordinary recoding routines into an order-preserving projection
 *  (dict-4 / fib-dict over the vocabulary) and back around each ordered step.  Without a vocabulary
 *  they are rejected with a named error — an integer id cannot reveal where a future symbol sorts.
 *
 *  The reference evaluator is a trampoline (`TailCalls`): generated recursion depth (fold loops, scans
 *  over long codes) costs heap, not JVM stack.  See docs/ZIPPY_CODED.md for the contract and limits.
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

  /** EXHAUSTIVE walk: every space node, including spaces nested in paths (`ZPath.GroundedSP`) */
  def subspaces[A](s: ZSpace[A]): Iterator[ZSpace[A]] =
    Iterator(s) ++ children(s).flatMap(subspaces) ++ paths(s).flatMap(subpaths).flatMap {
      case ZPath.GroundedSP(q, _) => subspaces(q)
      case _ => Iterator.empty
    }
  /** every path node of `s` (exhaustive, through nested spaces) */
  def subpaths[A](p: ZPath[A]): Iterator[ZPath[A]] = Iterator(p) ++ (p match
    case ZPath.Concat(l, r) => subpaths(l) ++ subpaths(r)
    case ZPath.GroundedPP(q, _) => subpaths(q)
    case _ => Iterator.empty)
  def allPaths[A](s: ZSpace[A]): Iterator[ZPath[A]] = subspaces(s).flatMap(x => paths(x).flatMap(subpaths))
  /** true AST size: space nodes + path nodes, nested spaces included */
  def size[A](s: ZSpace[A]): Int = subspaces(s).size + allPaths(s).size
  /** no Grounded node anywhere: the expression has no host callback whose effects or failure could be
   *  observed, so evaluating it fewer times or in another order cannot change anything */
  def pure[A](s: ZSpace[A]): Boolean =
    subspaces(s).forall { case _: ZSpace.GroundedPS[A] | _: ZSpace.GroundedSS[A] => false; case _ => true } &&
      allPaths(s).forall(pure(_))
  def pure[A](p: ZPath[A]): Boolean = subpaths(p).forall {
    case _: ZPath.GroundedPP[A] | _: ZPath.GroundedSP[A] => false
    case _ => true }

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

/** an IMMUTABLE byte string with value equality and unsigned lexicographic order: the window
 *  `[off, off + len)` of a backing buffer.  The window's bytes are never written after construction:
 *  the public constructor copies its argument and `bytes` returns a copy, so a path stored in a hashed
 *  set never changes its hash.  Sharing keeps the reference evaluator linear on long paths:
 *    - `uncons` / `dropPrefix` return sub-windows of the same buffer (no copy);
 *    - `concat(x, y)` APPENDS in place when x ends at the buffer's high-water mark (the buffer's bytes
 *      beyond it belong to nobody), else copies into a fresh buffer with doubled capacity — so a
 *      scanner growing its prefix one byte at a time costs amortized O(1) per byte, not O(length).
 *  This is pure representation sharing: no symbol boundary or other metadata is stored. */
final class ByteArr private (private val buf: ByteArr.Buf, private val off: Int, val length: Int):
  def apply(i: Int): Byte = buf.a(off + i)
  def bytes: Array[Byte] = java.util.Arrays.copyOfRange(buf.a, off, off + length)
  override def equals(o: Any): Boolean = o match
    case b: ByteArr => length == b.length && java.util.Arrays.equals(buf.a, off, off + length, b.buf.a, b.off, b.off + length)
    case _ => false
  // computed on first use (a scanner's intermediate prefixes are never hashed); racy-benign like String
  private var h = 0
  override def hashCode: Int =
    if h == 0 then
      var x = 1; var i = off; val e = off + length; val a = buf.a
      while i < e do { x = 31 * x + a(i); i += 1 }
      h = x
    h
  override def toString: String = bytes.map(b => f"${b & 0xff}%02x").mkString("ByteArr(", " ", ")")
object ByteArr:
  /** a backing buffer; `used` is the high-water mark: bytes at or past it are owned by no ByteArr */
  private[morkl] final class Buf(var a: Array[Byte], var used: Int)
  def apply(a: Array[Byte]): ByteArr = owned(a.clone())
  private[morkl] def owned(a: Array[Byte]): ByteArr = new ByteArr(Buf(a, a.length), 0, a.length)
  private[morkl] def slice(x: ByteArr, from: Int, until: Int): ByteArr = new ByteArr(x.buf, x.off + from, until - from)
  private[morkl] def compare(x: ByteArr, y: ByteArr): Int =
    java.util.Arrays.compareUnsigned(x.buf.a, x.off, x.off + x.length, y.buf.a, y.off, y.off + y.length)
  private[morkl] def startsWith(x: ByteArr, p: ByteArr): Boolean =
    x.length >= p.length && java.util.Arrays.equals(x.buf.a, x.off, x.off + p.length, p.buf.a, p.off, p.off + p.length)
  private[morkl] def concat(x: ByteArr, y: ByteArr): ByteArr =
    if y.length == 0 then x
    else if x.length == 0 then y
    else
      val b = x.buf
      b.synchronized {
        val end = x.off + x.length
        if end == b.used && end + y.length <= b.a.length then
          System.arraycopy(y.buf.a, y.off, b.a, end, y.length); b.used = end + y.length
          new ByteArr(b, x.off, x.length + y.length)
        else if end == b.used then
          // grow the shared buffer: the old window stays valid in the old array, new appends go to the new one
          val na = java.util.Arrays.copyOf(b.a, math.max(16, math.max(2 * b.a.length, end + y.length)))
          System.arraycopy(y.buf.a, y.off, na, end, y.length)
          val nb = Buf(na, end + y.length)
          b.used = Int.MaxValue      // the old buffer is frozen: x's window there is still readable
          new ByteArr(nb, x.off, x.length + y.length)
        else
          val n = x.length + y.length
          val na = new Array[Byte](math.max(16, 2 * n))
          System.arraycopy(b.a, x.off, na, 0, x.length); System.arraycopy(y.buf.a, y.off, na, x.length, y.length)
          new ByteArr(Buf(na, n), 0, n)
      }

/** Zippy_bytes reference: a path is a byte string, a space a Set[ByteArr]. */
object ByteRepr extends PathRepr[Byte, ByteArr]:
  import ByteArr.owned
  val empty: ByteArr = owned(Array.emptyByteArray)
  def fromAtoms(v: Vector[Byte]): ByteArr = owned(v.toArray)
  def toAtoms(r: ByteArr): Vector[Byte] = r.bytes.toVector
  def concat(x: ByteArr, y: ByteArr): ByteArr = ByteArr.concat(x, y)
  def startsWith(x: ByteArr, p: ByteArr): Boolean = ByteArr.startsWith(x, p)
  def dropPrefix(x: ByteArr, p: ByteArr): ByteArr = ByteArr.slice(x, p.length, x.length)
  private val atoms = Array.tabulate(256)(i => owned(Array(i.toByte)))
  def uncons(x: ByteArr): Option[(ByteArr, ByteArr)] =
    if x.length == 0 then None else Some((atoms(x(0) & 0xff), ByteArr.slice(x, 1, x.length)))
  def length(x: ByteArr): Int = x.length
  def compare(x: ByteArr, y: ByteArr): Int = ByteArr.compare(x, y)

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
  import scala.util.control.TailCalls.*
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

  // INDEXING (pure evaluator caching, same results).  A large set that is queried by prefix or sliced
  // in order gets a sorted index (a persistent TreeSet keyed by set identity).  Subtraction and
  // Raffination of a few elements from an indexed set carry the index over (O(k log n)), so a loop that
  // repeatedly takes the least element / removes a group / unwraps one prefix of a fixed input (the
  // shape of every generated Fold) costs O(log n) per step, not a scan.
  // LIFETIME.  Caches for the literals of the evaluator's ROUTINES live as long as the evaluator (they are
  // part of the compiled program, reused warm across frames); every other cache — main-program literals
  // and set indexes — lives for ONE top-level `apply` / `path` call, so discarded programs leave nothing.
  import scala.collection.immutable.TreeSet
  private val routineLits: java.util.IdentityHashMap[ZSpace.Literal[A], Set[R]] =
    val m = java.util.IdentityHashMap[ZSpace.Literal[A], Set[R]]()
    routines.valuesIterator.flatMap(r => ZOps.subspaces(r.body)).foreach { case l: ZSpace.Literal[A] => m.put(l, null); case _ => () }
    m
  private val routineIdx = java.util.IdentityHashMap[Set[R], TreeSet[R]]()
  private var runLits = java.util.IdentityHashMap[ZSpace.Literal[A], Set[R]]()
  private var runIdx = java.util.IdentityHashMap[Set[R], TreeSet[R]]()
  private var depth = 0
  /** entries currently cached (persistent, per-run): the retention checks read this */
  def cacheSizes: (Int, Int) = (routineLits.size + routineIdx.size, runLits.size + runIdx.size)
  private def run[T](f: => T): T =
    depth += 1
    try f finally { depth -= 1; if depth == 0 then { runLits = java.util.IdentityHashMap(); runIdx = java.util.IdentityHashMap() } }
  private def literal(l: ZSpace.Literal[A]): Set[R] =
    val persistent = routineLits.containsKey(l)
    val m = if persistent then routineLits else runLits
    var v = m.get(l)
    if v == null then
      v = l.p.map(repr.fromAtoms); m.put(l, v)
      if v.size > 32 then (if persistent then routineIdx else runIdx).put(v, TreeSet.from(v))
    v
  private def idxOf(s: Set[R]): TreeSet[R] = { val t = runIdx.get(s); if t != null then t else routineIdx.get(s) }
  private def indexed(s: Set[R]): Boolean = idxOf(s) != null
  private def sortedOf(s: Set[R]): TreeSet[R] =
    var t = idxOf(s)
    if t == null then { t = TreeSet.from(s); runIdx.put(s, t) }
    t
  private def note(s: Set[R], t: TreeSet[R]): Set[R] = { if s.size > 32 && !indexed(s) then runIdx.put(s, t); s }
  /** worth indexing: already indexed, or large enough that a sort amortizes over repeated queries */
  private def big(s: Set[R]): Boolean = indexed(s) || s.size > 32
  /** the elements of `s` starting with prefix `p` (extensions of p are contiguous in canonical order) */
  private def withPrefix(s: Set[R], p: R): Iterator[R] =
    if big(s) then sortedOf(s).iteratorFrom(p).takeWhile(repr.startsWith(_, p))
    else s.iterator.filter(repr.startsWith(_, p))
  private def restrict(xs: Set[R], ps: Set[R]): Set[R] =
    if big(xs) && ps.size * 8 < xs.size then ps.iterator.flatMap(withPrefix(xs, _)).toSet
    else xs.filter(e => ps.exists(p => repr.startsWith(e, p)))
  private def subtract(xs: Set[R], ys: Set[R]): Set[R] =
    val r = xs removedAll ys
    if indexed(xs) && (r ne xs) && ys.size * 8 < xs.size then note(r, sortedOf(xs) removedAll ys) else r
  private def raffinate(xs: Set[R], ps: Set[R]): Set[R] =
    if big(xs) && ps.size * 8 < xs.size then
      val gone = ps.iterator.flatMap(withPrefix(xs, _)).toSet
      if gone.isEmpty then xs else note(xs removedAll gone, sortedOf(xs) removedAll gone)
    else xs.filterNot(e => ps.exists(p => repr.startsWith(e, p)))
  /** union: insert the smaller set into the larger */
  private def unite(a: Set[R], b: Set[R]): Set[R] = if a.size >= b.size then a concat b else b concat a
  private def range(v: Set[R], lo: Int, hi: Int): Set[R] =
    val (l, h) = RangeBounds.normalize(v.size, lo, hi)
    if h <= l then Set.empty
    else if l == 0 && h == v.size then v
    else if big(v) then
      val t = sortedOf(v)
      if h - l == 1 && l == 0 then Set(t.head) else if h - l == 1 && h == v.size then Set(t.last)
      else t.iterator.slice(l, h).toSet
    else if h - l == 1 && l == 0 then Set(v.min)                 // one-element slices: a scan, not a sort
    else if h - l == 1 && h == v.size then Set(v.max)
    else v.toVector.sorted.slice(l, h).toSet

  /** stack-safe sequencing: every recursive evaluation goes through `TailCalls`, so deep generated
   *  recursion (fold loops, scanners over long codes) runs in constant JVM stack */
  private def foldT[X, S](it: Iterator[X], z: S)(f: (S, X) => TailRec[S]): TailRec[S] =
    if !it.hasNext then done(z) else { val x = it.next(); f(z, x).flatMap(s => tailcall(foldT(it, s)(f))) }
  private def allT[X](xs: Vector[X])(f: X => TailRec[Boolean]): TailRec[Boolean] =
    foldT(xs.iterator, true)((ok, x) => if !ok then done(false) else f(x))
  private def mapT[X, Y](xs: Vector[X])(f: X => TailRec[Y]): TailRec[Vector[Y]] =
    foldT(xs.iterator, Vector.empty[Y])((acc, x) => f(x).map(acc :+ _))

  def path(x: ZPath[A], pc: PC = Map.empty, sc: SC = Map.empty): R = run(pathT(x, pc, sc).result)
  def apply(s: ZSpace[A], pc: PC = Map.empty, sc: SC = Map.empty): Set[R] = run(ev(s, pc, sc).result)

  private def pathT(x: ZPath[A], pc: PC, sc: SC): TailRec[R] = x match
    case ZPath.Deref(pr) => done(pc.getOrElse(pr, throw RuntimeException(s"$pr path ref not resolved")))
    case ZPath.Constant(v) => done(repr.fromAtoms(v))
    case ZPath.Concat(l, r) => for a <- tailcall(pathT(l, pc, sc)); b <- tailcall(pathT(r, pc, sc)) yield repr.concat(a, b)
    case ZPath.GroundedPP(q, f) => tailcall(pathT(q, pc, sc)).map(v => repr.fromAtoms(f(repr.toAtoms(v))))
    case ZPath.GroundedSP(q, f) => tailcall(ev(q, pc, sc)).map(v => repr.fromAtoms(f(v.map(repr.toAtoms))))

  private def groups(s: Set[R]): Map[R, Set[R]] = s.iterator.flatMap(repr.uncons).toSeq.groupMap(_._1)(_._2).view.mapValues(_.toSet).toMap

  private def ev(s: ZSpace[A], pc: PC, sc: SC): TailRec[Set[R]] =
    def rec(x: ZSpace[A]): TailRec[Set[R]] = tailcall(ev(x, pc, sc))
    def bin(x: ZSpace[A], y: ZSpace[A])(f: (Set[R], Set[R]) => Set[R]) = for a <- rec(x); b <- rec(y) yield f(a, b)
    steps += 1
    s match
      case ZSpace.Empty() => done(Set.empty)
      case ZSpace.Call(rp, refs, ms) =>
        for
          refvs <- mapT(refs)(p => tailcall(pathT(p, pc, sc)))
          msvs <- mapT(ms)(rec)
          res <- {
            val ZRoutine(_, refns, msns, body) = routines.getOrElse(rp, throw RuntimeException(s"routine ${rp.s} not defined"))
            // formals are bound DIRECTLY (as `eval` does: `PathContextMap(Map.from(refns zip refvs))`):
            // only local binders ignore `_`; a formal named `_` is an ordinary parameter
            refns.zip(refvs).foreach(hint)
            val pctx: PC = Map.from(refns zip refvs)
            val sctx: SC = Map.from(msns zip msvs)
            body match
              case ZSpace.Union(l, ZSpace.Call(`rp`, `refs`, `ms`)) =>
                // self-call passthrough, as in `eval`: the call re-binds the same arguments
                for
                  same <- allT(refs.indices.toVector)(i => tailcall(pathT(refs(i), pctx, sctx)).map(_ == refvs(i)))
                  same2 <- if !same then done(false) else allT(ms.indices.toVector)(i => tailcall(ev(ms(i), pctx, sctx)).map(_ == msvs(i)))
                  r <- tailcall(ev(if same2 then l else body, pctx, sctx))
                yield r
              case _ => tailcall(ev(body, pctx, sctx))
          }
        yield res
      case ZSpace.Mention(v) => done(sc.getOrElse(v, throw RuntimeException(s"$v space mention not resolved")))
      case ZSpace.Singleton(p) => tailcall(pathT(p, pc, sc)).map(Set(_))
      case l: ZSpace.Literal[A] => done(literal(l))
      case ZSpace.Union(x, y) => bin(x, y)(unite)
      case ZSpace.Intersection(x, y) => bin(x, y)(_ intersect _)
      case ZSpace.Subtraction(x, y) => bin(x, y)(subtract)
      case ZSpace.Restriction(x, y) => bin(x, y)(restrict)
      case ZSpace.Raffination(x, y) => bin(x, y)(raffinate)
      case ZSpace.Composition(x, y) => bin(x, y)((xs, ys) => for a <- xs; b <- ys yield repr.concat(a, b))
      case ZSpace.Wrap(src, p) => for pv <- tailcall(pathT(p, pc, sc)); v <- rec(src) yield v.map(repr.concat(pv, _))
      case ZSpace.Unwrap(src, p) => for pv <- tailcall(pathT(p, pc, sc)); v <- rec(src) yield withPrefix(v, pv).map(repr.dropPrefix(_, pv)).toSet
      case ZSpace.TailsUnion(src) => rec(src).map(_.flatMap(e => repr.uncons(e).map(_._2)))
      case ZSpace.TailsIntersection(src) =>
        rec(src).map { v => val g = groups(v); if g.isEmpty then Set.empty else g.valuesIterator.reduce(_ intersect _) }
      case ZSpace.Iteration(src, h, r, t) =>
        rec(src).flatMap(v => foldT(groups(v).iterator, Set.empty[R])((acc, hr) =>
          tailcall(ev(t, bindP(pc, h, hr._1), bindS(sc, r, hr._2))).map(unite(acc, _))))
      case ZSpace.Fixpoint(init, r, body) =>
        def loop(cur: Set[R]): TailRec[Set[R]] =
          tailcall(ev(body, pc, bindS(sc, r, cur))).flatMap { b =>
            val nxt = unite(cur, b)
            if nxt == cur then done(cur) else tailcall(loop(nxt))
          }
        rec(init).flatMap(loop)
      case ZSpace.Fold(src, initial, acc, h, r, t, u) =>
        for
          acc0 <- tailcall(pathT(initial, pc, sc))
          v <- rec(src)
          res <- foldT(groups(v).toSeq.sortBy(_._1).iterator, (acc0, Set.empty[R])) { case ((accV, out), (hv, rv)) =>
            val pc1 = bindP(bindP(pc, acc, accV), h, hv)
            val sc1 = bindS(sc, r, rv)
            for o <- tailcall(ev(t, pc1, sc1)); a2 <- tailcall(pathT(u, pc1, sc1)) yield (a2, unite(out, o))
          }
        yield res._2
      case ZSpace.GroundedPS(p, f) => tailcall(pathT(p, pc, sc)).map(v => f(repr.toAtoms(v)).map(repr.fromAtoms))
      case ZSpace.GroundedSS(q, f) => rec(q).map(v => f(v.map(repr.toAtoms)).map(repr.fromAtoms))
      case ZSpace.Range(x, lo, hi) => rec(x).map(range(_, lo, hi))

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
  /** for a code that is NOT order-preserving: a frozen admissible vocabulary and an order-preserving
   *  code over it with the same target alphabet.  Ordered operators are compiled through ordinary
   *  recoding routines into this projection and back (see `CodedTranslation`). */
  def orderProjection: Option[(Code[A, B], Vector[A])] = None
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
  final class FibInterner(val idBound: Int, val snapshot: Option[Vector[String]] = None) extends Code[String, Bit]:
    require(idBound >= 1)
    val name = "fib-interner"
    private val admissible = snapshot.map(_.toSet)
    def enc(a: String) =
      // the boundary checks run BEFORE interning: a rejected symbol leaves the interner unchanged
      for v <- admissible if !v(a) do throw IllegalArgumentException(s"$name: '$a' outside the frozen vocabulary")
      val known = Interner.lookup(a)
      if known < 0 && Interner.size >= idBound then
        throw IllegalArgumentException(s"$name: '$a' is not interned and the next id ${Interner.size} >= bound $idBound")
      val id = if known >= 0 then known else Interner.intern(a)
      if id >= idBound then throw IllegalArgumentException(s"$name: '$a' has interner id $id >= bound $idBound")
      fibonacci(id + 1)
    def dec(c: Vector[Bit]) =
      val fibs = Iterator.iterate((1L, 2L))((a, b) => (b, a + b)).map(_._1)
      Interner.unintern((c.init.zip(fibs).collect { case (true, f) => f }.sum - 1).toInt)
    val automaton = FibAutomaton
    // Fibonacci code length is monotone in n, so the longest code below the bound is that of idBound itself
    override val maxCodeLength = Some(fibonacci(idBound).length)
    override def orderPreserving = false
    override val orderProjection = snapshot.map(v => (FibDictionary(v), v.distinct))
    /** the same stable ids, plus a frozen admissible vocabulary for `Range` / `Fold` */
    def ordered(vocab: Iterable[String]) = FibInterner(idBound, Some(vocab.toVector))

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
  final class InternerIds4(val snapshot: Option[Vector[String]]) extends Code[String, Byte]:
    val name = "interner-4"
    private val admissible = snapshot.map(_.toSet)
    def enc(a: String) =
      for v <- admissible if !v(a) do throw IllegalArgumentException(s"$name: '$a' outside the frozen vocabulary")
      be4(Interner.intern(a))
    def dec(c: Vector[Byte]) = Interner.unintern(unbe4(c))
    val automaton = Fixed4
    override def fixedLength = Some(4)
    override def orderPreserving = false
    override val orderProjection = snapshot.map(v => (Dictionary4(v), v.distinct))
    /** the same stable 4-byte ids, plus a frozen admissible vocabulary for `Range` / `Fold` */
    def ordered(vocab: Iterable[String]) = InternerIds4(Some(vocab.toVector))
  val InternerId4 = InternerIds4(None)

  private def be4(i: Int) = Vector((i >>> 24).toByte, (i >>> 16).toByte, (i >>> 8).toByte, i.toByte)
  private def unbe4(c: Vector[Byte]) = c.foldLeft(0)((acc, b) => (acc << 8) | (b & 0xff))
  private lazy val Fixed4 = Vector.tabulate(4)(q => Vector(AtomClass.AnyAtom[Byte]() -> (if q == 3 then None else Some(q + 1))))

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
final class CodedTranslation[A, B](val code: Code[A, B], reserved: Set[String], depthBudget: Int = 4096,
                                  pureRoutine: RoutinePtr => Boolean = (_: RoutinePtr) => false):
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

  /** the order projection of a non-order-preserving code (stable interner ids carry no symbol order) */
  private def projection(what: String): (Code[A, B], Vector[A]) = code.orderProjection.getOrElse(throw UnsupportedOperationException(
    s"${code.name}: $what needs symbol order; an interner id does not determine it — compile with a frozen " +
    "admissible vocabulary (`.ordered(vocab)`), or use an order-preserving code (string-nul, fib-dict, dict-4)"))

  /** RECODING between the code and its order projection, as an ordinary routine:
   *    recode(X) = (X ∩ {ε}) ∪ ⋃_{head h of X} Unwrap(T, h) × recode(tail_h)
   *  where T = { from(c) ++ to(c) | c in vocabulary } is a literal table (prefix-free: Unwrap(T, h) is
   *  exactly {to(c)} for the c with from(c) = h, and empty for an inadmissible head). */
  private val recoders = collection.mutable.Map.empty[Boolean, RoutinePtr]
  private def recode(toProjection: Boolean, x: ZSpace[B]): ZSpace[B] =
    val rp = recoders.getOrElseUpdate(toProjection, {
      val (pc, vocab) = projection("Range/Fold")
      val (from, to) = if toProjection then (code, pc) else (pc, code)
      val rp = RoutinePtr(nm(if toProjection then "to_order" else "from_order"))
      val tab = ZSpace.Literal[B](vocab.map(c => from.enc(c) ++ to.enc(c)).toSet)
      val xm = SpaceMention(nm("x"))
      val body = ZSpace.Union(ZSpace.Intersection(ZSpace.Mention(xm), ZSpace.Singleton(ZPath.Constant(Vector()))),
        decode(from, ZSpace.Mention(xm), Vector(), Vector(),
          (hp, r) => ZSpace.Composition(ZSpace.Unwrap(tab, hp), ZSpace.Call(rp, Vector(), Vector(r)))))
      emit(ZRoutine(rp, Vector(), Vector(xm), body))
      rp
    })
    ZSpace.Call(rp, Vector(), Vector(x))

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
    // a non-order-preserving code slices in its order projection: recode, slice, recode back
    case ZSpace.Range(x, lo, hi) =>
      if code.orderPreserving || (lo == 0 && hi == 0) then ZSpace.Range(space(x), lo, hi)
      else recode(false, ZSpace.Range(recode(true, space(x)), lo, hi))
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
      // ORDER-FREE SHORTCUTS, only when nothing can observe the difference: the template (and every
      // routine it calls) and the erased initial/update have no Grounded callback.  A callback's effects,
      // evaluation count, order and failures are part of the source semantics, so impure folds keep the
      // full sequenced lowering below (initial once, then body and update per group in canonical order).
      // the accumulator is never bound (`_`): the fold is a union over groups
      else if a.s == "_" && pureT(t) && ZOps.pure(i) && ZOps.pure(u) then space(ZSpace.Iteration(src, h, r, t))
      // the accumulator never changes (u = a, not shadowed by the head): bind it once, iterate
      else if u == ZPath.Deref(a) && (h.s == "_" || h != a) && pureT(t) then space(constantFold(src, i, a, h, r, t))
      else sequencedFold(f)

  /** Fold(src, i, a, h, r, t, a) = (λ a. Iteration(src, h, r, t))(i), with src evaluated outside a's scope */
  private def constantFold(src: ZSpace[A], i: ZPath[A], a: PathRef, h: PathRef, r: SpaceMention, t: ZSpace[A]): ZSpace[A] =
    val rp = RoutinePtr(nm("fold_const")); val xm = SpaceMention(nm("x"))
    val it = ZSpace.Iteration(ZSpace.Mention[A](xm), h, r, t)
    val (fp0, fm0) = ZOps.free(it)
    val fp = (fp0 - a).toVector.sortBy(_.s); val fm = (fm0 - xm).toVector.sortBy(_.s)
    routine(ZRoutine(rp, a +: fp, xm +: fm, it))
    ZSpace.Call(rp, i +: fp.map(ZPath.Deref[A](_)), src +: fm.map(ZSpace.Mention[A](_)))

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

  /** a template is pure if it has no Grounded node and calls only pure routines */
  private def pureT(t: ZSpace[A]): Boolean =
    ZOps.pure(t) && ZOps.subspaces(t).forall { case ZSpace.Call(rp, _, _) => pureRoutine(rp); case _ => true }

  /** THE Fold lowering, for every code.  The source is evaluated once; its complete HEADS are extracted
   *  once (and, for a code without symbol order, recoded once into the order projection); the loop takes
   *  the least remaining head (Range(heads, 1, 2)), decodes it back to bind h, runs the step on the group
   *  Unwrap(src, h) of the fixed source, and recurses on heads \ {least}.  Every step is a prefix query
   *  on a fixed set or a removal from the heads set — the reference evaluator indexes both, so a fold
   *  over n groups costs O(n log n) bulk work.  Steps and shadowing: formals (acc, h) with the head
   *  winning, `_` binders fresh.  initial is evaluated once, before src, even when there are no groups. */
  private def sequencedFold(f: ZSpace.Fold[A]): ZSpace[B] =
    val ZSpace.Fold(src, initial, acc, h, rest, t, u) = f
    if !code.orderPreserving then projection("Fold")
    def toOrder(x: ZSpace[B]) = if code.orderPreserving then x else recode(true, x)
    def fromOrder(x: ZSpace[B]) = if code.orderPreserving then x else recode(false, x)
    val (fp0, fm0) = ZOps.free(ZSpace.Union(t, ZSpace.Singleton(u)))
    val fp = (fp0 -- Set(acc, h).filter(_.s != "_")).toVector.sortBy(_.s)
    val fm = (fm0 -- Set(rest).filter(_.s != "_")).toVector.sortBy(_.s)
    val fpT = fp.map(ref); val fpB = fpT.map(ZPath.Deref[B](_)); val fmB = fm.map(ZSpace.Mention[B](_))
    val entry = RoutinePtr(nm("fold_entry")); val loop = RoutinePtr(nm("fold")); val step = RoutinePtr(nm("fold_step"))
    val accR = PathRef(nm("acc")); val xm = SpaceMention(nm("x"))
    val hsm = SpaceMention(nm("heads")); val hsRest = SpaceMention(nm("heads_rest")); val srcm = SpaceMention(nm("src"))
    val least = ZSpace.Range(ZSpace.Mention[B](hsm), 1, 2)
    emit(ZRoutine(loop, accR +: fpT, Vector(hsm, srcm) ++ fm,
      decode(code, fromOrder(least), accR +: fpT, Vector(hsm, srcm) ++ fm, (hp, _) =>
        ZSpace.Call(step, Vector(ZPath.Deref(accR), hp) ++ fpB,
          Vector(ZSpace.Unwrap(ZSpace.Mention(srcm), hp), ZSpace.Subtraction(ZSpace.Mention(hsm), least), ZSpace.Mention(srcm)) ++ fmB))))
    val heads = decode(code, ZSpace.Mention(xm), Vector(), Vector(), (hp, _) => ZSpace.Singleton(hp))
    emit(ZRoutine(entry, accR +: fpT, xm +: fm,
      ZSpace.Call(loop, ZPath.Deref[B](accR) +: fpB, Vector(toOrder(heads), ZSpace.Mention(xm)) ++ fmB)))
    val fpA = fp.map(ZPath.Deref[A](_)); val fmA = fm.map(ZSpace.Mention[A](_))
    routine(ZRoutine(step, Vector(formalP(acc), formalP(h)) ++ fp, Vector(formalS(rest), hsRest, srcm) ++ fm,
      ZSpace.Union(t, ZSpace.Call(loop, u +: fpA, Vector(ZSpace.Mention[A](hsRest), ZSpace.Mention[A](srcm)) ++ fmA))))
    ZSpace.Call(entry, path(initial) +: fpB, space(src) +: fmB)

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
    decode(code, src, fpT, fm, (hp, r) => ZSpace.Call(body, hp +: fpB, r +: fmB))

  /** the DECODER of code `c` over `src` (evaluated once): for every complete code h at the front of a
   *  path, with tails r, the result of `leaf(h, r)`.  `fpT` / `fm` are the target refs and mentions the
   *  leaf mentions; they are threaded through the decoder routines as formals. */
  private def decode(c: Code[A, B], src: ZSpace[B], fpT: Vector[PathRef], fm: Vector[SpaceMention],
                     callBody: (ZPath[B], ZSpace[B]) => ZSpace[B]): ZSpace[B] =
    val fpB = fpT.map(ZPath.Deref[B](_)); val fmB = fm.map(ZSpace.Mention[B](_))
    def filter(ac: AtomClass[B], x: ZSpace[B]): ZSpace[B] = ac match
      case AtomClass.Is(b) => ZSpace.Restriction(x, ZSpace.Singleton(ZPath.Constant(Vector(b))))
      case AtomClass.Not(bs) => ZSpace.Raffination(x, ZSpace.Literal(bs.map(Vector(_))))
      case AtomClass.AnyAtom() => x
    def arm(ac: AtomClass[B], x: ZSpace[B], acc: ZPath[B], k: (ZPath[B], ZSpace[B]) => ZSpace[B]): ZSpace[B] =
      val b = PathRef(nm("b")).known(1); val r = SpaceMention(nm("r"))
      val acc2 = acc match
        case ZPath.Constant(v) if v.isEmpty => ZPath.Deref[B](b)
        case _ => ZPath.Concat(acc, ZPath.Deref(b))
      ZSpace.Iteration(filter(ac, x), b, r, k(acc2, ZSpace.Mention(r)))
    def unions(xs: Iterable[ZSpace[B]]): ZSpace[B] = xs.reduceOption(ZSpace.Union(_, _)).getOrElse(ZSpace.Empty())
    val nStates = c.automaton.length
    val xm = SpaceMention(nm("x")); val accR = PathRef(nm("acc"))
    val bounded = c.maxCodeLength.filter(l => l.toLong * nStates <= depthBudget)
    if c.acyclic then
      // FIXED/BOUNDED-LENGTH DECODER: one entry routine, nested Iterations, h = the atom binders
      val entry = RoutinePtr(nm("iter_entry"))
      def go(q: Int, acc: ZPath[B], x: ZSpace[B]): ZSpace[B] =
        unions(c.automaton(q).map((ac, tgt) => arm(ac, x, acc, (acc2, r) => tgt match
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
          val arms = c.automaton(q).flatMap { (ac, tgt) =>
            tgt match
              case None => Some(arm(ac, ZSpace.Mention(xm), ZPath.Deref(accR), (acc2, r) => callBody(acc2, r)))
              case Some(q2) if d + 1 < maxLen =>
                Some(arm(ac, ZSpace.Mention(xm), ZPath.Deref(accR), (acc2, r) => ZSpace.Call(rs(q2)(d + 1), acc2 +: fpB, r +: fmB)))
              case _ => None
          }
          emit(ZRoutine(rs(q)(d), accR +: fpT, xm +: fm, unions(arms)))
        if maxLen == 0 then ZSpace.Empty() else ZSpace.Call(rs(0)(0), ZPath.Constant[B](Vector()) +: fpB, src +: fmB)
      case None =>
        // UNBOUNDED DECODER: one recursive routine per state, (acc, free refs; X, free mentions)
        val states = c.automaton.indices.map(q => RoutinePtr(nm(s"iter_q$q"))).toVector
        for q <- c.automaton.indices do
          val arms = c.automaton(q).map((ac, tgt) => arm(ac, ZSpace.Mention(xm), ZPath.Deref(accR), (acc2, r) => tgt match
            case None => callBody(acc2, r)
            case Some(q2) => ZSpace.Call(states(q2), acc2 +: fpB, r +: fmB)))
          emit(ZRoutine(states(q), accR +: fpT, xm +: fm, unions(arms)))
        ZSpace.Call(states(0), ZPath.Constant[B](Vector()) +: fpB, src +: fmB)

object CodedTranslation:
  /** translate (main, routines) along `code`; returns the target main and the full target routine table */
  def apply[A, B](code: Code[A, B], main: ZSpace[A], routines: Iterable[ZRoutine[A]] = Nil, depthBudget: Int = 4096): (ZSpace[B], Vector[ZRoutine[B]]) =
    val reserved = ZOps.names(main) ++ routines.flatMap(ZOps.names(_))
    // a frozen vocabulary is the admissibility contract: the program's own constants must be in it
    for (_, vocab) <- code.orderProjection do
      val admissible = vocab.toSet
      val outside = (ZOps.constants(main) ++ routines.iterator.flatMap(r => ZOps.constants(r.body))).flatten.filterNot(admissible).toSet
      require(outside.isEmpty, s"${code.name}: program constants outside the frozen vocabulary: ${outside.take(5).mkString(", ")}")
    // a routine is pure if its body has no Grounded node and it calls only pure routines (greatest fixpoint)
    val table = routines.map(r => r.name -> r).toMap
    var impure = table.keySet.filterNot(n => ZOps.pure(table(n).body))
    var grown = true
    while grown do
      val next = impure ++ table.keySet.filter(n => ZOps.subspaces(table(n).body).exists {
        case ZSpace.Call(rp, _, _) => impure(rp) || !table.contains(rp); case _ => false })
      grown = next.size > impure.size; impure = next
    val t = new CodedTranslation(code, reserved, depthBudget, rp => table.contains(rp) && !impure(rp))
    routines.foreach(t.routine)
    val m = t.space(main)
    (m, t.routines)
