package morkl.alpha

import morkl.{PathRef, RoutinePtr, SpaceMention}
import java.nio.charset.StandardCharsets

/** ================================================================================================
 *  TRANSLATIONS BETWEEN ALPHABETS, all instances of ONE construction: a letter `a: A` of the source
 *  language is encoded as a codeword `enc(a): Word[B]` of the target language, where
 *    (1) the code is PREFIX-FREE and recognised letter-by-letter by a finite automaton (`Fsm`), so a
 *        path's encoding decodes uniquely and `enc(p) ≤prefix enc(q)` iff `p ≤prefix q`;
 *    (2) the code is ORDER-PRESERVING (letter order -> lexicographic codeword order), so `Range` and
 *        `Fold`'s head order are preserved;
 *    (3) every codeword has length ≤ `maxLen` — for interned codes the bit/byte length of the largest
 *        symbol in the universe.  That bound is the RECURSION DEPTH of every generated routine, and
 *        the routines are UNROLLED by it: routine `…_k<d>` handles a codeword with at most d letters
 *        left, and `…_k0` is `Empty`.
 *  Union/Intersection/Subtraction/Restriction/Raffination/Composition/Wrap/Unwrap/Fixpoint/Range/
 *  Call translate homomorphically (by (1) and (2)).  The four ops that observe ONE LETTER —
 *  Iteration, Fold, TailsUnion, TailsIntersection — become routines over the target alphabet that
 *  walk the codeword with the target's own one-letter Iteration, dispatching on the automaton's
 *  state; no metadata and no new language feature is introduced (see `docs/ALPHABETS.md`).
 *  ================================================================================================ */
enum Guard[B]:
  case All()
  case In(letters: Set[B])
  case NotIn(letters: Set[B])

/** codeword automaton: start state 0; `trans(s)` lists (letter guard, next state or None = end) */
case class Fsm[B](trans: Vector[Vector[(Guard[B], Option[Int])]])

trait Code[A, B]:
  def name: String
  def enc(a: A): Word[B]
  def fsm: Fsm[B]
  /** a sound upper bound on codeword length, or None (open universe): the generated routines are
   *  then plainly recursive (still terminating: every call consumes a letter) instead of unrolled */
  def bound: Option[Int]
  /** Some(w) iff EVERY codeword has exactly w letters: only then does an exact length annotation
   *  `known(n)` translate to an exact `known(n*w)`; otherwise it is dropped (never kept as a lie) */
  def width: Option[Int]
  /** letter order -> lexicographic codeword order; required by `Range` and `Fold` */
  def orderPreserving: Boolean
  def someLetter: B
  /** decode a concatenation of codewords */
  def dec(w: Word[B]): Word[A]
  def encWord(w: Word[A]): Word[B] = w.flatMap(enc)
  /** one letter <-> one letter: translation is a pure renaming */
  def letterwise: Boolean = width.contains(1)

/** a code given by an explicit finite codebook (the small fixed alphabets) */
abstract class TableCode[A, B](val name: String, book: Seq[(A, Word[B])]) extends Code[A, B]:
  private val fwd = book.toMap
  private val back = book.map(_.swap).toMap
  require(back.size == fwd.size, s"$name: not injective")
  def enc(a: A): Word[B] = fwd.getOrElse(a, throw IllegalArgumentException(s"$name: letter $a outside the alphabet"))
  private val lens = book.map(_._2.length).toSet
  val bound: Option[Int] = Some(if lens.isEmpty then 1 else lens.max)
  val width: Option[Int] = if lens.size == 1 then Some(lens.head) else None
  def orderPreserving = true
  def dec(w: Word[B]): Word[A] =
    val out = Vector.newBuilder[A]; var i = 0
    while i < w.length do
      var j = i + 1
      while j <= w.length && !back.contains(w.slice(i, j)) do j += 1
      if j > w.length then throw IllegalArgumentException(s"$name: undecodable ${w.drop(i)}")
      out += back(w.slice(i, j)); i = j
    out.result()

object Codes:
  /** Fibonacci (Zeckendorf) codeword of n >= 1: digits for F2=1, F3=2, … least-significant first,
   *  then a 1.  Weights are Longs, so every n up to Int.MaxValue+1 (= interned id + 1) is finite. */
  def fibonacci(n: Long): Word[Boolean] =
    require(n >= 1 && n <= (1L << 62))
    val fibs = Iterator.iterate((1L, 2L))((a, b) => (b, a + b)).map(_._1).takeWhile(_ <= n).toVector
    val bits = Array.fill(fibs.length)(false); var r = n
    for i <- fibs.indices.reverse if fibs(i) <= r do { bits(i) = true; r -= fibs(i) }
    bits.toVector :+ true
  def unfibonacci(w: Word[Boolean]): Long =
    require(w.length >= 2 && w.last && w(w.length - 2), s"not a Fibonacci codeword: $w")
    val fibs = Iterator.iterate((1L, 2L))((a, b) => (b, a + b)).map(_._1)
    w.init.iterator.zip(fibs).collect { case (true, f) => f }.sum

  private val fibFsm: Fsm[Boolean] = Fsm(Vector(  // a codeword ends at its first `11`
    Vector(Guard.In(Set(false)) -> Some(0), Guard.In(Set(true)) -> Some(1)),
    Vector(Guard.In(Set(false)) -> Some(0), Guard.In(Set(true)) -> None)))

  /** INTERNED SYMBOLS -> BITS: the Fibonacci code of the symbol's EXISTING `morkl.Interner` id + 1.
   *  Codewords depend only on the id, so they are stable across programs.  `maxId` is an optional
   *  contract (ids < maxId): it bounds the codeword length, hence the unrolling depth, and is
   *  enforced at encoding.  Id order is not string order, so this code is not order-preserving. */
  def symbolsToBits(maxId: Option[Int] = None): Code[String, Boolean] = new Code[String, Boolean]:
    val name = "fibonacci"
    def enc(a: String) =
      val id = morkl.Interner.intern(a)
      maxId.foreach(m => require(id < m, s"$name: interned id $id of $a violates the contract id < $m"))
      fibonacci(id.toLong + 1)
    val fsm = fibFsm
    val bound = maxId.map(m => fibonacci(math.max(m, 1).toLong).length)  // the longest code of 1..m
    val width = None
    def orderPreserving = false
    def someLetter = false
    def dec(w: Word[Boolean]) =
      val out = Vector.newBuilder[String]; var i = 0
      while i < w.length do
        var j = i + 1
        while j < w.length && !(w(j) && w(j - 1)) do j += 1
        out += morkl.Interner.unintern((unfibonacci(w.slice(i, j + 1)) - 1).toInt); i = j + 1
      out.result()

  /** STRING SYMBOLS -> BYTES, total and injective on every JVM string (surrogates and NUL
   *  included), order-preserving for `String.compareTo`, 0-terminated.  A UTF-16 code unit c is
   *  the byte c+1 when c < 0x7E, else the escape 0x7F followed by c big-endian (2 bytes).  The
   *  terminator 0 is the least byte, so a shorter string sorts first.  `maxUnits` is an optional
   *  length contract (enforced at encoding); without it the decoder routines are recursive. */
  def stringsToBytes(maxUnits: Option[Int] = None): Code[String, Byte] = new Code[String, Byte]:
    val name = "nul-terminated"
    def enc(a: String) =
      maxUnits.foreach(m => require(a.length <= m, s"$name: ${a.length} code units violate the contract <= $m"))
      val b = Vector.newBuilder[Byte]
      for c <- a do if c < 0x7e then b += (c + 1).toByte else { b += 0x7f.toByte; b += (c >> 8).toByte; b += c.toByte }
      b += 0.toByte; b.result()
    private val plain: Set[Byte] = (1 to 0x7e).map(_.toByte).toSet
    val fsm = Fsm(Vector(
      Vector(Guard.In(Set(0.toByte)) -> None, Guard.In(plain) -> Some(0), Guard.In(Set(0x7f.toByte)) -> Some(1)),
      Vector(Guard.All[Byte]() -> Some(2)),
      Vector(Guard.All[Byte]() -> Some(0))))
    val bound = maxUnits.map(3 * _ + 1)
    val width = None
    def orderPreserving = true
    def someLetter = 1.toByte
    def dec(w: Word[Byte]) =
      val out = Vector.newBuilder[String]; val cur = StringBuilder(); var i = 0
      while i < w.length do
        val x = w(i) & 0xff
        if x == 0 then { out += cur.result(); cur.clear(); i += 1 }
        else if x < 0x7f then { cur += (x - 1).toChar; i += 1 }
        else { cur += (((w(i + 1) & 0xff) << 8) | (w(i + 2) & 0xff)).toChar; i += 3 }
      require(cur.isEmpty, "unterminated symbol"); out.result()

  /** INTERNED STRING SYMBOLS -> 4 BYTES: the big-endian EXISTING `morkl.Interner` id (fixed width,
   *  so exact length annotations scale by 4).  Not order-preserving (id order is not string order). */
  def internedToBytes(width: Int = 4): Code[String, Byte] =
    val w = width
    new Code[String, Byte]:
      val name = "interned-" + w
      def enc(a: String) =
        val id = morkl.Interner.intern(a)
        require(w >= 4 || id < (1 << (8 * w)), s"$name: id $id does not fit")
        Vector.tabulate(w)(k => if 8 * (w - 1 - k) >= 32 then 0.toByte else ((id >>> (8 * (w - 1 - k))) & 0xff).toByte)
      val fsm = fixedFsm[Byte](w)
      val bound = Some(w)
      val width = Some(w)
      def orderPreserving = false
      def someLetter = 0.toByte
      def dec(x: Word[Byte]) = x.grouped(w).map(g => morkl.Interner.unintern(g.foldLeft(0)((n, b) => (n << 8) | (b & 0xff)))).toVector

  def fixedFsm[B](n: Int): Fsm[B] = Fsm(Vector.tabulate(n)(s => Vector(Guard.All[B]() -> (if s == n - 1 then None else Some(s + 1)))))

  /** BITS -> BYTES: false -> 0, true -> 1 (an injection; a pure renaming) */
  val bitsToBytes: Code[Boolean, Byte] = new TableCode[Boolean, Byte]("bit-byte", Seq(false -> Vector(0.toByte), true -> Vector(1.toByte))):
    val fsm = fixedFsm[Byte](1); def someLetter = 0.toByte

  /** BYTES -> BITS: each byte is 8 bits, most significant first (unsigned order preserved) */
  val bytesToBits: Code[Byte, Boolean] = new TableCode[Byte, Boolean]("byte-8bits",
      (0 until 256).map(i => i.toByte -> Vector.tabulate(8)(k => ((i >> (7 - k)) & 1) == 1))):
    val fsm = fixedFsm[Boolean](8); def someLetter = false

  /** BITS -> SYMBOLS: the only two symbols of the program are "0" and "1" */
  val bitsToSymbols: Code[Boolean, String] = new TableCode[Boolean, String]("bit-symbol", Seq(false -> Vector("0"), true -> Vector("1"))):
    val fsm = fixedFsm[String](1); def someLetter = "0"

  /** BYTES -> SYMBOLS: 0..255 are the symbols "000".."255" (zero-padded, so String order = byte order) */
  val bytesToSymbols: Code[Byte, String] = new TableCode[Byte, String]("byte-symbol", (0 until 256).map(i => i.toByte -> Vector(f"$i%03d"))):
    val fsm = fixedFsm[String](1); def someLetter = "000"

/** every binder/reference/routine NAME of a program: generated names are allocated outside it */
object Names:
  def path[A](x: ZPath[A]): Set[String] = x match
    case ZPath.Deref(pr) => Set(pr.s)
    case ZPath.Constant(_) => Set()
    case ZPath.Concat(l, r) => path(l) ++ path(r)
    case ZPath.GroundedPP(q, _) => path(q)
    case ZPath.GroundedSP(s, _) => space(s)
  def space[A](s: ZSpace[A]): Set[String] = s match
    case ZSpace.Call(rp, rs, ms) => Set(rp.s) ++ rs.flatMap(path) ++ ms.flatMap(space)
    case ZSpace.Mention(m) => Set(m.s)
    case ZSpace.Iteration(x, h, r, t) => space(x) ++ space(t) + h.s + r.s
    case ZSpace.Fixpoint(i, r, b) => space(i) ++ space(b) + r.s
    case ZSpace.Fold(x, i, a, h, r, t, u) => space(x) ++ path(i) ++ space(t) ++ path(u) + a.s + h.s + r.s
    case p: Product => p.productIterator.flatMap {
      case c: ZSpace[A] @unchecked => space(c); case c: ZPath[A] @unchecked => path(c); case _ => Set() }.toSet
  def program[A](p: ZProgram[A]): Set[String] =
    space(p.main) ++ p.routines.values.flatMap(r => space(r.body) ++ r.refs.map(_.s) ++ r.mentions.map(_.s) + r.name.s)

/** the translator.  HYGIENE: every generated routine, path ref and space mention starts with a
 *  prefix `§<n>.` chosen (deterministically, least n) so that NO name of the source program starts
 *  with it; translating a translated program again picks a fresh prefix the same way.  Lifted
 *  bodies take their binders as formals in source binding order (Fold: accumulator, then head, so a
 *  coincident name resolves to the head as in the source); an ignored binder `_` gets a fresh unused
 *  formal and never captures an outer `_`.  Routines are produced on demand, so only reachable
 *  (state, depth) pairs exist; depth -1 means "unbounded" (plain recursion, open universes). */
final class Translator[A, B](code: Code[A, B]):
  private val routines = collection.mutable.LinkedHashMap.empty[RoutinePtr, ZRoutine[B]]
  private var counter = 0
  private var pre: String = "§0."
  private def fresh(op: String): String = { counter += 1; s"$pre$op$counter" }
  private def g(n: String): String = pre + n   // a generated (non-source) variable name

  // ---- small combinators over the target language (all existing ops) ----
  private def eps: ZSpace[B] = ZSpace.Literal(Set(Vector()))
  private def ref(n: String) = ZPath.Deref[B](PathRef(n))
  private def men(n: String) = ZSpace.Mention[B](SpaceMention(n))
  private val Throw = PathRef("_"); private val ThrowM = SpaceMention("_")
  /** `T` if `g` is non-empty, else ∅ — and `T` is evaluated at most ONCE, only when `g` is non-empty */
  private def when(c: ZSpace[B], t: ZSpace[B]): ZSpace[B] = ZSpace.Iteration(ZSpace.Wrap(c, ZPath.Constant(Vector(code.someLetter))), Throw, ThrowM, t)
  private def guardOn(gd: Guard[B], b: ZPath[B]): Option[ZSpace[B]] = gd match
    case Guard.All() => None
    case Guard.In(ls) => Some(ZSpace.Intersection(ZSpace.Singleton(b), ZSpace.Literal(ls.map(Vector(_)))))
    case Guard.NotIn(ls) => Some(ZSpace.Subtraction(ZSpace.Singleton(b), ZSpace.Literal(ls.map(Vector(_)))))
  /** dispatch on the automaton: Σ over the transitions of `st` of `when(letter ∈ guard, arm)` */
  private def dispatch(st: Int, b: ZPath[B])(arm: Option[Int] => ZSpace[B]): ZSpace[B] =
    code.fsm.trans(st).map((gd, nxt) => guardOn(gd, b).fold(arm(nxt))(when(_, arm(nxt)))).reduce(ZSpace.Union(_, _))
  private def top: Int = code.bound.getOrElse(-1)
  private def down(k: Int): Int = if k < 0 then k else k - 1
  private def kName(k: Int): String = if k < 0 then "" else s"_k$k"
  private def define(name: String, refs: Vector[PathRef], ms: Vector[SpaceMention], body: => ZSpace[B]): RoutinePtr =
    val rp = RoutinePtr(name)
    if !routines.contains(rp) then
      routines(rp) = ZRoutine(rp, refs, ms, ZSpace.Empty()) // reserve (recursion)
      routines(rp) = ZRoutine(rp, refs, ms, body)
    rp

  /** ANNOTATIONS: an exact source length scales by a fixed width, and is dropped otherwise */
  def ann(pr: PathRef): PathRef =
    if pr.lengthHint < 0 then pr else code.width match
      case Some(w) => PathRef(pr.s).known(pr.lengthHint * w)
      case None => PathRef(pr.s)
  /** a binder as a routine formal: `_` (ignored) becomes a fresh, never-read name */
  private def formal(pr: PathRef): PathRef = if pr.s == "_" then PathRef(fresh("ignored")) else ann(pr)
  private def formalM(m: SpaceMention): SpaceMention = if m.s == "_" then SpaceMention(fresh("ignoredM")) else m

  // ---- paths ----
  def path(p: ZPath[A]): ZPath[B] = p match
    case ZPath.Deref(pr) => ZPath.Deref(ann(pr))
    case ZPath.Constant(w) => ZPath.Constant(code.encWord(w))
    case ZPath.Concat(l, r) => ZPath.Concat(path(l), path(r))
    case ZPath.GroundedPP(q, f) => ZPath.GroundedPP(path(q), w => code.encWord(f(code.dec(w))))
    case ZPath.GroundedSP(s, f) => ZPath.GroundedSP(space(s), ws => code.encWord(f(ws.map(code.dec))))

  private def ordered(op: String): Unit =
    if !code.orderPreserving then throw UnsupportedOperationException(
      s"$op observes symbol order, but the ${code.name} code (existing interned ids) is not order-preserving")

  // ---- spaces ----
  def space(s: ZSpace[A]): ZSpace[B] = s match
    case ZSpace.Empty() => ZSpace.Empty()
    case ZSpace.Call(r, rs, ms) => ZSpace.Call(r, rs.map(path), ms.map(space))
    case ZSpace.Mention(m) => ZSpace.Mention(m)
    case ZSpace.Singleton(p) => ZSpace.Singleton(path(p))
    case ZSpace.Literal(ws) => ZSpace.Literal(ws.map(code.encWord))
    case ZSpace.Union(x, y) => ZSpace.Union(space(x), space(y))
    case ZSpace.Intersection(x, y) => ZSpace.Intersection(space(x), space(y))
    case ZSpace.Subtraction(x, y) => ZSpace.Subtraction(space(x), space(y))
    case ZSpace.Restriction(x, y) => ZSpace.Restriction(space(x), space(y))
    case ZSpace.Raffination(x, y) => ZSpace.Raffination(space(x), space(y))
    case ZSpace.Composition(x, y) => ZSpace.Composition(space(x), space(y))
    case ZSpace.Fixpoint(i, r, b) => ZSpace.Fixpoint(space(i), r, space(b))
    case ZSpace.Wrap(x, p) => ZSpace.Wrap(space(x), path(p))
    case ZSpace.Unwrap(x, p) => ZSpace.Unwrap(space(x), path(p))
    case ZSpace.Range(x, lo, hi) => ordered("Range"); ZSpace.Range(space(x), lo, hi)
    case ZSpace.GroundedPS(p, f) => ZSpace.GroundedPS(path(p), w => f(code.dec(w)).map(code.encWord))
    case ZSpace.GroundedSS(x, f) => ZSpace.GroundedSS(space(x), ws => f(ws.map(code.dec)).map(code.encWord))
    case _ if code.letterwise => s match   // one letter <-> one letter: the letter ops rename too
      case ZSpace.TailsUnion(x) => ZSpace.TailsUnion(space(x))
      case ZSpace.TailsIntersection(x) => ZSpace.TailsIntersection(space(x))
      case ZSpace.Iteration(x, h, r, t) => ZSpace.Iteration(space(x), ann(h), r, space(t))
      case ZSpace.Fold(x, i, a, h, r, t, u) =>
        ordered("Fold"); ZSpace.Fold(space(x), path(i), ann(a), ann(h), r, space(t), path(u))
      case _ => throw MatchError(s)
    case ZSpace.TailsUnion(x) => ZSpace.Call(dropR(0, top), Vector(), Vector(space(x)))
    case ZSpace.TailsIntersection(x) => ZSpace.Call(tiR(0, top), Vector(), Vector(space(x)))
    case ZSpace.Iteration(x, h, r, t) =>
      val (fr, fm) = FreeVars.space(ZSpace.Iteration(ZSpace.Empty(), h, r, t))
      val frees = fr.toVector.sorted.map(PathRef(_)); val freeM = fm.toVector.sorted.map(SpaceMention(_))
      val body = define(fresh("body"), formal(h) +: frees, formalM(r) +: freeM, space(t))
      ZSpace.Call(iterR(fresh("iter"), body, frees, freeM, 0, top), ZPath.Constant[B](Vector()) +: frees.map(ZPath.Deref(_)),
        space(x) +: freeM.map(ZSpace.Mention(_)))
    case ZSpace.Fold(x, init, acc, h, r, t, u) =>
      ordered("Fold")
      val (fr, fm) = FreeVars.space(ZSpace.Fold(ZSpace.Empty(), ZPath.Constant(Vector()), acc, h, r, t, u))
      val frees = fr.toVector.sorted.map(PathRef(_)); val freeM = fm.toVector.sorted.map(SpaceMention(_))
      // source binding order: accumulator, then head (the later formal wins on a coincident name)
      val ah = Vector(formal(acc), formal(h))
      val body = define(fresh("fbody"), ah ++ frees, formalM(r) +: freeM, space(t))
      val rest = SpaceMention(g("Xr"))
      val id = fresh("fold")
      define(s"${id}_next", ah ++ frees, rest +: freeM,
        ZSpace.Call(foldTop(id, body, frees, freeM), path(u) +: frees.map(ZPath.Deref(_)), ZSpace.Mention(rest) +: freeM.map(ZSpace.Mention(_))))
      ZSpace.Call(foldTop(id, body, frees, freeM), path(init) +: frees.map(ZPath.Deref(_)), space(x) +: freeM.map(ZSpace.Mention(_)))

  // TailsUnion: drop one codeword.  §drop_s_k(;X) = Iteration(X, b, R, dispatch(s, b){end: R; s': §drop_s'_k-1(;R)})
  private def dropR(st: Int, k: Int): RoutinePtr = define(s"${pre}drop_s$st${kName(k)}", Vector(), Vector(SpaceMention(g("X"))),
    if k == 0 then ZSpace.Empty() else
      ZSpace.Iteration(men(g("X")), PathRef(g("b")), SpaceMention(g("R")), dispatch(st, ref(g("b"))) {
        case None => men(g("R"))
        case Some(s2) => ZSpace.Call(dropR(s2, down(k)), Vector(), Vector(men(g("R"))))
      }))

  // TailsIntersection: the target's own TailsIntersection intersects the per-letter subresults; an
  // incomplete codeword (never a well-formed value) keeps a letter's group present when its
  // subresult is empty, and is subtracted at the end.  It lives only inside this one expression.
  private lazy val junk: ZSpace[B] = ZSpace.Literal(Set(junkWord))
  private lazy val junkWord: Word[B] =
    val cont = code.fsm.trans(0).collectFirst { case (gd, Some(_)) => gd }.getOrElse(throw IllegalStateException("letterwise"))
    Vector(cont match
      case Guard.All() => code.someLetter
      case Guard.In(ls) => ls.minBy(_.toString)
      case Guard.NotIn(ls) => throw IllegalStateException(s"no continuing letter outside $ls"))
  private def tiR(st: Int, k: Int): RoutinePtr = define(s"${pre}ti_s$st${kName(k)}", Vector(), Vector(SpaceMention(g("X"))),
    if k == 0 then ZSpace.Empty() else
      ZSpace.Subtraction(ZSpace.TailsIntersection(
        ZSpace.Iteration(men(g("X")), PathRef(g("b")), SpaceMention(g("R")), ZSpace.Wrap(ZSpace.Union(dispatch(st, ref(g("b"))) {
          case None => men(g("R"))
          case Some(s2) => ZSpace.Call(tiR(s2, down(k)), Vector(), Vector(men(g("R"))))
        }, junk), ref(g("b"))))), junk))

  // Iteration: iter_s_k(P, frees; X, freeM) walks the codeword, accumulating it in P, and calls
  // the lifted body with h := P·b at the codeword's end.
  private def iterR(id: String, body: RoutinePtr, frees: Vector[PathRef], freeM: Vector[SpaceMention], st: Int, k: Int): RoutinePtr =
    val fr = frees.map(ZPath.Deref[B](_)); val fm = freeM.map(ZSpace.Mention[B](_))
    define(s"${id}_s$st${kName(k)}", PathRef(g("P")) +: frees, SpaceMention(g("X")) +: freeM,
      if k == 0 then ZSpace.Empty() else
        val pb = ZPath.Concat(ref(g("P")), ref(g("b")))
        ZSpace.Iteration(men(g("X")), PathRef(g("b")), SpaceMention(g("R")), dispatch(st, ref(g("b"))) {
          case None => ZSpace.Call(body, pb +: fr, men(g("R")) +: fm)
          case Some(s2) => ZSpace.Call(iterR(id, body, frees, freeM, s2, down(k)), pb +: fr, men(g("R")) +: fm)
        }))

  /** FOLD: ONE walk per source group, accumulator threaded as an ordinary path argument.
   *    top(a; X)          = walk_0(ε, a; X, X)
   *    walk_s(P, a; Y, X) = Iteration(least path of Y∖{ε}, b, _, dispatch(s, b){
   *                           end: body(a, P·b; Y/b) ∪ next(a, P·b; X ∖ restricted to P·b)
   *                           s' : walk_s'(P·b, a; Y/b, X) })
   *    next(a, h; X')     = top(update(a, h); X')
   *  The least path's first codeword is the least source head (order-preserving code), so groups are
   *  visited in source order; `update` is evaluated once per group; no Space->Path extraction. */
  private def foldTop(id: String, body: RoutinePtr, frees: Vector[PathRef], freeM: Vector[SpaceMention]): RoutinePtr =
    val fr = frees.map(ZPath.Deref[B](_)); val fm = freeM.map(ZSpace.Mention[B](_))
    define(s"${id}_top", PathRef(g("a")) +: frees, SpaceMention(g("X")) +: freeM,
      ZSpace.Call(foldWalk(id, body, frees, freeM, 0, top), Vector(ZPath.Constant[B](Vector()), ref(g("a"))) ++ fr,
        Vector(men(g("X")), men(g("X"))) ++ fm))
  private def foldWalk(id: String, body: RoutinePtr, frees: Vector[PathRef], freeM: Vector[SpaceMention], st: Int, k: Int): RoutinePtr =
    val fr = frees.map(ZPath.Deref[B](_)); val fm = freeM.map(ZSpace.Mention[B](_))
    define(s"${id}_walk_s$st${kName(k)}", Vector(PathRef(g("P")), PathRef(g("a"))) ++ frees,
      Vector(SpaceMention(g("Y")), SpaceMention(g("X"))) ++ freeM,
      if k == 0 then ZSpace.Empty() else
        val pb = ZPath.Concat(ref(g("P")), ref(g("b")))
        val yb = ZSpace.Unwrap(men(g("Y")), ref(g("b")))
        ZSpace.Iteration(ZSpace.Range(ZSpace.Subtraction(men(g("Y")), eps), 1, 2), PathRef(g("b")), ThrowM, dispatch(st, ref(g("b"))) {
          case None => ZSpace.Union(
            ZSpace.Call(body, Vector(ref(g("a")), pb) ++ fr, yb +: fm),
            ZSpace.Call(RoutinePtr(s"${id}_next"), Vector(ref(g("a")), pb) ++ fr,
              ZSpace.Raffination(men(g("X")), ZSpace.Singleton(pb)) +: fm))
          case Some(s2) => ZSpace.Call(foldWalk(id, body, frees, freeM, s2, down(k)), Vector(pb, ref(g("a"))) ++ fr,
            Vector(yb, men(g("X"))) ++ fm)
        }))

  def routine(r: ZRoutine[A]): ZRoutine[B] = ZRoutine(r.name, r.refs.map(ann), r.mentions, space(r.body))
  def program(p: ZProgram[A]): ZProgram[B] =
    val names = Names.program(p)
    pre = Iterator.from(0).map(i => s"§$i.").find(q => !names.exists(_.startsWith(q))).get
    val main = space(p.main)
    val rs = p.routines.map((k, v) => k -> routine(v))
    ZProgram(main, rs ++ routines)
  /** how many routines the translation generated */
  def generated: Int = routines.size

object Translate:
  def apply[A, B](code: Code[A, B], p: ZProgram[A]): ZProgram[B] = Translator(code).program(p)
  def encodeResult[A, B](code: Code[A, B], ws: Set[Word[A]]): Set[Word[B]] = ws.map(code.encWord)
