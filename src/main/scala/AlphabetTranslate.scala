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
  def maxLen: Int
  def someLetter: B
  /** decode a concatenation of codewords */
  def dec(w: Word[B]): Word[A]
  def encWord(w: Word[A]): Word[B] = w.flatMap(enc)
  /** a length-1 code whose automaton ends after every letter: translation is a pure renaming */
  def letterwise: Boolean = maxLen == 1

/** a code given by an explicit (finite) codebook */
abstract class TableCode[A, B](val name: String, book: Seq[(A, Word[B])]) extends Code[A, B]:
  private val fwd = book.toMap
  private val back = book.map(_.swap).toMap
  require(back.size == fwd.size, s"$name: not injective")
  def enc(a: A): Word[B] = fwd.getOrElse(a, throw IllegalArgumentException(s"$name: letter $a outside the universe"))
  val maxLen: Int = if book.isEmpty then 1 else book.map(_._2.length).max
  def dec(w: Word[B]): Word[A] =
    val out = Vector.newBuilder[A]; var i = 0
    while i < w.length do
      var j = i + 1
      while j <= w.length && !back.contains(w.slice(i, j)) do j += 1
      if j > w.length then throw IllegalArgumentException(s"$name: undecodable ${w.drop(i)}")
      out += back(w.slice(i, j)); i = j
    out.result()

object Codes:
  private def lexBits(a: Word[Boolean], b: Word[Boolean]): Boolean = BitRepr.compare(BitRepr.fromWord(a), BitRepr.fromWord(b)) < 0

  /** Fibonacci (Zeckendorf) codeword of n >= 1: digits for F2=1, F3=2, … least-significant first, then a 1 */
  def fibonacci(n: Int): Word[Boolean] =
    require(n >= 1)
    val fibs = Iterator.iterate((1, 2))((a, b) => (b, a + b)).map(_._1).takeWhile(_ <= n).toVector
    val bits = Array.fill(fibs.length)(false); var r = n
    for i <- fibs.indices.reverse if fibs(i) <= r do { bits(i) = true; r -= fibs(i) }
    bits.toVector :+ true

  /** INTERNED SYMBOLS -> BITS by Fibonacci coding.  The n symbols of the universe get the codewords
   *  of 1..n, assigned in sorted order to the codewords sorted lexicographically, which keeps the
   *  code order-preserving.  `maxLen` (≈ log_φ n + 2) is the recursion bound of the routines. */
  def symbolsToBits(universe: Set[String]): Code[String, Boolean] =
    val syms = universe.toVector.sorted
    val words = (1 to syms.length).map(fibonacci).sortWith(lexBits)
    new TableCode[String, Boolean]("fibonacci", syms.zip(words)):
      // a codeword ends at its first `11`: state 0 = previous letter 0 (or start), 1 = previous 1
      val fsm = Fsm(Vector(
        Vector(Guard.In(Set(false)) -> Some(0), Guard.In(Set(true)) -> Some(1)),
        Vector(Guard.In(Set(false)) -> Some(0), Guard.In(Set(true)) -> None)))
      def someLetter = false
      override def letterwise = false

  /** STRING SYMBOLS -> BYTES: UTF-8 then a 0 terminator.  Not interned, so the universe is only
   *  needed for the length bound (and for the order check: UTF-8 order = `String` order on it). */
  def stringsToBytes(universe: Set[String]): Code[String, Byte] =
    val syms = universe.toVector.sorted
    def e(s: String): Word[Byte] =
      require(!s.contains('\u0000'), s"symbol contains NUL: $s"); s.getBytes(StandardCharsets.UTF_8).toVector :+ 0.toByte
    val words = syms.map(e)
    require(words.zip(words.drop(1)).forall((a, b) => ByteRepr.compare(ByteRepr.fromWord(a), ByteRepr.fromWord(b)) < 0),
      "universe is not ordered alike in UTF-8 and UTF-16")
    new Code[String, Byte]:
      val name = "nul-terminated"
      def enc(a: String) = e(a)
      val maxLen = if words.isEmpty then 1 else words.map(_.length).max
      val fsm = Fsm(Vector(Vector(Guard.In(Set(0.toByte)) -> None, Guard.NotIn(Set(0.toByte)) -> Some(0))))
      def someLetter = 0.toByte
      override def letterwise = false
      def dec(w: Word[Byte]) =
        val out = Vector.newBuilder[String]; var cur = Vector.newBuilder[Byte]
        for b <- w do if b == 0 then { out += String(cur.result().toArray, StandardCharsets.UTF_8); cur = Vector.newBuilder } else cur += b
        require(cur.result().isEmpty, "unterminated symbol"); out.result()

  /** INTERNED STRING SYMBOLS -> 4 BYTES: the big-endian rank in the sorted universe (fixed length). */
  def internedToBytes(universe: Set[String], width: Int = 4): Code[String, Byte] =
    val syms = universe.toVector.sorted
    require(syms.length.toLong <= (1L << (8 * width)))
    def word(i: Int): Word[Byte] = Vector.tabulate(width)(k => ((i >>> (8 * (width - 1 - k))) & 0xff).toByte)
    new TableCode[String, Byte]("interned-" + width, syms.zipWithIndex.map((s, i) => s -> word(i))):
      val fsm = fixedFsm[Byte](width)
      def someLetter = 0.toByte
      override val maxLen = width
      override def letterwise = width == 1

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

  /** every symbol occurring in a symbol program (constants and literals) — the interning universe */
  def symbolsOf(p: ZProgram[String]): Set[String] =
    def path(x: ZPath[String]): Set[String] = x match
      case ZPath.Constant(w) => w.toSet
      case ZPath.Concat(l, r) => path(l) ++ path(r)
      case ZPath.GroundedPP(q, _) => path(q)
      case ZPath.GroundedSP(s, _) => space(s)
      case _ => Set()
    def space(s: ZSpace[String]): Set[String] = s match
      case ZSpace.Literal(ws) => ws.flatten
      case ZSpace.Singleton(p) => path(p)
      case ZSpace.Call(_, rs, ms) => rs.flatMap(path).toSet ++ ms.flatMap(space)
      case ZSpace.Wrap(x, p) => space(x) ++ path(p)
      case ZSpace.Unwrap(x, p) => space(x) ++ path(p)
      case ZSpace.Fold(x, i, _, _, _, t, u) => space(x) ++ path(i) ++ space(t) ++ path(u)
      case ZSpace.GroundedPS(p, _) => path(p)
      case p: Product => p.productIterator.collect { case c: ZSpace[String] @unchecked => space(c) }.flatten.toSet
    space(p.main) ++ p.routines.values.flatMap(r => space(r.body))

/** the translator.  Generated routines are named `§<op><n>_s<state>_k<depth>` (`§` never occurs in a
 *  source name) and are produced on demand, so only reachable (state, depth) pairs exist. */
final class Translator[A, B](code: Code[A, B]):
  private val routines = collection.mutable.LinkedHashMap.empty[RoutinePtr, ZRoutine[B]]
  private var counter = 0
  /** generated names carry the translator's identity, so a translated program can be translated
   *  again (bits -> bytes -> bits) without its routines colliding with the new ones */
  private val tag = s"${code.name}#${Translator.instances.incrementAndGet()}"
  private def fresh(op: String): String = { counter += 1; s"§$tag:$op$counter" }

  // ---- small combinators over the target language (all existing ops) ----
  private def eps: ZSpace[B] = ZSpace.Literal(Set(Vector()))
  private def ref(n: String) = ZPath.Deref[B](PathRef(n))
  private def men(n: String) = ZSpace.Mention[B](SpaceMention(n))
  private val Throw = PathRef("_"); private val ThrowM = SpaceMention("_")
  /** `T` if `g` is non-empty, else ∅ — and `T` is evaluated at most ONCE, only when `g` is non-empty
   *  (one-letter `Iteration` over `l·g`, whose single head group runs the body once). */
  private def when(g: ZSpace[B], t: ZSpace[B]): ZSpace[B] = ZSpace.Iteration(ZSpace.Wrap(g, ZPath.Constant(Vector(code.someLetter))), Throw, ThrowM, t)
  private def headed(x: ZSpace[B]): ZSpace[B] = ZSpace.Iteration(x, Throw, ThrowM, eps)
  private def notHeaded(x: ZSpace[B]): ZSpace[B] = ZSpace.Subtraction(eps, headed(x))
  private def guardOn(g: Guard[B], b: ZPath[B]): Option[ZSpace[B]] = g match
    case Guard.All() => None
    case Guard.In(ls) => Some(ZSpace.Intersection(ZSpace.Singleton(b), ZSpace.Literal(ls.map(Vector(_)))))
    case Guard.NotIn(ls) => Some(ZSpace.Subtraction(ZSpace.Singleton(b), ZSpace.Literal(ls.map(Vector(_)))))
  /** dispatch on the automaton: Σ over the transitions of `st` of `when(letter ∈ guard, arm)` */
  private def dispatch(st: Int, b: ZPath[B])(arm: Option[Int] => ZSpace[B]): ZSpace[B] =
    code.fsm.trans(st).map((g, nxt) => guardOn(g, b).fold(arm(nxt))(when(_, arm(nxt)))).reduce(ZSpace.Union(_, _))
  /** the one path of a singleton space (the only Space -> Path op of the language is GroundedSP) */
  private def the(s: ZSpace[B]): ZPath[B] = ZPath.GroundedSP(s, ws => { require(ws.size == 1, s"expected one path, got ${ws.size}"); ws.head })
  private def define(name: String, refs: Vector[String], ms: Vector[String], body: => ZSpace[B]): RoutinePtr =
    val rp = RoutinePtr(name)
    if !routines.contains(rp) then
      routines(rp) = ZRoutine(rp, refs.map(PathRef(_)), ms.map(SpaceMention(_)), ZSpace.Empty()) // reserve (recursion)
      routines(rp) = ZRoutine(rp, refs.map(PathRef(_)), ms.map(SpaceMention(_)), body)
    rp

  // ---- paths ----
  def path(p: ZPath[A]): ZPath[B] = p match
    case ZPath.Deref(pr) => ZPath.Deref(pr)
    case ZPath.Constant(w) => ZPath.Constant(code.encWord(w))
    case ZPath.Concat(l, r) => ZPath.Concat(path(l), path(r))
    case ZPath.GroundedPP(q, f) => ZPath.GroundedPP(path(q), w => code.encWord(f(code.dec(w))))
    case ZPath.GroundedSP(s, f) => ZPath.GroundedSP(space(s), ws => code.encWord(f(ws.map(code.dec))))

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
    case ZSpace.Range(x, lo, hi) => ZSpace.Range(space(x), lo, hi)
    case ZSpace.GroundedPS(p, f) => ZSpace.GroundedPS(path(p), w => f(code.dec(w)).map(code.encWord))
    case ZSpace.GroundedSS(x, f) => ZSpace.GroundedSS(space(x), ws => f(ws.map(code.dec)).map(code.encWord))
    case _ if code.letterwise => s match   // one letter <-> one letter: the letter ops rename too
      case ZSpace.TailsUnion(x) => ZSpace.TailsUnion(space(x))
      case ZSpace.TailsIntersection(x) => ZSpace.TailsIntersection(space(x))
      case ZSpace.Iteration(x, h, r, t) => ZSpace.Iteration(space(x), h, r, space(t))
      case ZSpace.Fold(x, i, a, h, r, t, u) => ZSpace.Fold(space(x), path(i), a, h, r, space(t), path(u))
      case _ => throw MatchError(s)
    case ZSpace.TailsUnion(x) => ZSpace.Call(dropR(0, code.maxLen), Vector(), Vector(space(x)))
    case ZSpace.TailsIntersection(x) => ZSpace.Call(tiR(0, code.maxLen), Vector(), Vector(space(x)))
    case it @ ZSpace.Iteration(x, h, r, t) =>
      val (fr, fm) = FreeVars.space(t); val frees = (fr - h.s).toVector.sorted; val freeM = (fm - r.s).toVector.sorted
      val body = define(fresh("body"), h.s +: frees, r.s +: freeM, space(t))
      val id = fresh("iter")
      ZSpace.Call(iterR(id, body, frees, freeM, 0, code.maxLen), ZPath.Constant[B](Vector()) +: frees.map(ref), space(x) +: freeM.map(men))
    case ZSpace.Fold(x, init, acc, h, r, t, u) =>
      val (tr, tm) = FreeVars.space(t); val (ur, um) = FreeVars.path(u)
      val frees = ((tr ++ ur) - h.s - acc.s).toVector.sorted; val freeM = ((tm ++ um) - r.s).toVector.sorted
      val body = define(fresh("fbody"), Vector(h.s, acc.s) ++ frees, r.s +: freeM, space(t))
      val upd = define(fresh("fupd"), Vector(h.s, acc.s) ++ frees, r.s +: freeM, ZSpace.Singleton(path(u)))
      val f = FoldGen(fresh("fold"), body, upd, frees, freeM)
      ZSpace.Call(f.out(0, code.maxLen), Vector(ZPath.Constant[B](Vector()), path(init)) ++ frees.map(ref), space(x) +: freeM.map(men))

  // TailsUnion: drop one codeword.  §drop_s_k(;X) = Iteration(X, b, R, dispatch(s, b){end: R; s': §drop_s'_k-1(;R)})
  private def dropR(st: Int, k: Int): RoutinePtr = define(s"§$tag:drop_s${st}_k$k", Vector(), Vector(s"§${tag}X"),
    if k == 0 then ZSpace.Empty() else
      ZSpace.Iteration(men(s"§${tag}X"), PathRef(s"§${tag}b"), SpaceMention(s"§${tag}R"), dispatch(st, ref(s"§${tag}b")) {
        case None => men(s"§${tag}R")
        case Some(s2) => ZSpace.Call(dropR(s2, k - 1), Vector(), Vector(men(s"§${tag}R")))
      }))

  // TailsIntersection: the target's own TailsIntersection intersects the per-letter subresults; a
  // JUNK path (an incomplete codeword, never a result) keeps a letter's group present when its
  // subresult is empty, and is subtracted at the end.
  private lazy val junk: ZSpace[B] = ZSpace.Literal(Set(junkWord))
  private lazy val junkWord: Word[B] =
    // one letter that CONTINUES a codeword from the start state: an incomplete codeword
    val cont = code.fsm.trans(0).collectFirst { case (g, Some(_)) => g }.getOrElse(throw IllegalStateException("letterwise"))
    Vector(cont match
      case Guard.All() => code.someLetter
      case Guard.In(ls) => ls.head
      case Guard.NotIn(ls) => code.fsm.trans(0).flatMap { case (Guard.In(l2), _) => l2; case _ => Set() }.find(!ls(_))
        .getOrElse(nonMember(ls)))
  private def nonMember(ls: Set[B]): B = code.someLetter match
    case _: Byte => (0 until 256).map(_.toByte).find(b => !ls(b.asInstanceOf[B])).get.asInstanceOf[B]
    case l => throw IllegalStateException(s"no letter outside $ls")
  private def tiR(st: Int, k: Int): RoutinePtr = define(s"§$tag:ti_s${st}_k$k", Vector(), Vector(s"§${tag}X"),
    if k == 0 then ZSpace.Empty() else
      ZSpace.Subtraction(ZSpace.TailsIntersection(
        ZSpace.Iteration(men(s"§${tag}X"), PathRef(s"§${tag}b"), SpaceMention(s"§${tag}R"), ZSpace.Wrap(ZSpace.Union(dispatch(st, ref(s"§${tag}b")) {
          case None => men(s"§${tag}R")
          case Some(s2) => ZSpace.Call(tiR(s2, k - 1), Vector(), Vector(men(s"§${tag}R")))
        }, junk), ref(s"§${tag}b")))), junk))

  // Iteration: §iter_s_k(§P, frees; §X, freeM) walks the codeword, accumulating it in §P, and calls
  // the lifted body with h := §P·b at the codeword's end.
  private def iterR(id: String, body: RoutinePtr, frees: Vector[String], freeM: Vector[String], st: Int, k: Int): RoutinePtr =
    define(s"${id}_s${st}_k$k", s"§${tag}P" +: frees, s"§${tag}X" +: freeM,
      if k == 0 then ZSpace.Empty() else
        val pb = ZPath.Concat(ref(s"§${tag}P"), ref(s"§${tag}b"))
        ZSpace.Iteration(men(s"§${tag}X"), PathRef(s"§${tag}b"), SpaceMention(s"§${tag}R"), dispatch(st, ref(s"§${tag}b")) {
          case None => ZSpace.Call(body, pb +: frees.map(ref), men(s"§${tag}R") +: freeM.map(men))
          case Some(s2) => ZSpace.Call(iterR(id, body, frees, freeM, s2, k - 1), pb +: frees.map(ref), men(s"§${tag}R") +: freeM.map(men))
        })
    )

  /** Fold: the accumulator must thread through codewords in order, so the walk is SEQUENTIAL over the
   *  head letters (least first, read off with `Range`): `out` emits the body outputs, `acc` returns
   *  `{final accumulator}`.  Each subtree's `acc` is computed once per visit. */
  private final class FoldGen(id: String, body: RoutinePtr, upd: RoutinePtr, frees: Vector[String], freeM: Vector[String]):
    private val fr = frees.map(ref); private val fm = freeM.map(men)
    private def minLetter(x: ZSpace[B]): ZPath[B] =
      the(ZSpace.Range(ZSpace.Iteration(x, PathRef(s"§${tag}m"), ThrowM, ZSpace.Singleton(ref(s"§${tag}m"))), 1, 2))
    private def xb = ZSpace.Unwrap(men(s"§${tag}X"), ref(s"§${tag}b"))
    private def xr = ZSpace.Subtraction(men(s"§${tag}X"), ZSpace.Restriction(men(s"§${tag}X"), ZSpace.Singleton(ref(s"§${tag}b"))))
    private def pb = ZPath.Concat(ref(s"§${tag}P"), ref(s"§${tag}b"))
    /** accumulator after the head letter §b's subtree */
    private def accAfter(st: Int, k: Int)(nxt: Option[Int]): ZSpace[B] = nxt match
      case None => ZSpace.Call(upd, Vector(pb, ref(s"§${tag}a")) ++ fr, xb +: fm)
      case Some(s2) => ZSpace.Call(acc(s2, k - 1), Vector(pb, ref(s"§${tag}a")) ++ fr, xb +: fm)
    def out(st: Int, k: Int): RoutinePtr = define(s"${id}_out_s${st}_k$k", Vector(s"§${tag}P", s"§${tag}a") ++ frees, s"§${tag}X" +: freeM,
      if k == 0 then ZSpace.Empty() else
        when(headed(men(s"§${tag}X")), ZSpace.Call(step(st, k), Vector(ref(s"§${tag}P"), ref(s"§${tag}a"), minLetter(men(s"§${tag}X"))) ++ fr, men(s"§${tag}X") +: fm)))
    private def step(st: Int, k: Int): RoutinePtr = define(s"${id}_step_s${st}_k$k", Vector(s"§${tag}P", s"§${tag}a", s"§${tag}b") ++ frees, s"§${tag}X" +: freeM,
      dispatch(st, ref(s"§${tag}b")) { nxt =>
        val here = nxt match
          case None => ZSpace.Call(body, Vector(pb, ref(s"§${tag}a")) ++ fr, xb +: fm)
          case Some(s2) => ZSpace.Call(out(s2, k - 1), Vector(pb, ref(s"§${tag}a")) ++ fr, xb +: fm)
        ZSpace.Union(here, ZSpace.Call(out(st, k), Vector(ref(s"§${tag}P"), the(accAfter(st, k)(nxt))) ++ fr, xr +: fm))
      })
    def acc(st: Int, k: Int): RoutinePtr = define(s"${id}_acc_s${st}_k$k", Vector(s"§${tag}P", s"§${tag}a") ++ frees, s"§${tag}X" +: freeM,
      if k == 0 then ZSpace.Singleton(ref(s"§${tag}a")) else
        ZSpace.Union(
          when(headed(men(s"§${tag}X")), ZSpace.Call(accStep(st, k), Vector(ref(s"§${tag}P"), ref(s"§${tag}a"), minLetter(men(s"§${tag}X"))) ++ fr, men(s"§${tag}X") +: fm)),
          when(notHeaded(men(s"§${tag}X")), ZSpace.Singleton(ref(s"§${tag}a")))))
    private def accStep(st: Int, k: Int): RoutinePtr = define(s"${id}_accstep_s${st}_k$k", Vector(s"§${tag}P", s"§${tag}a", s"§${tag}b") ++ frees, s"§${tag}X" +: freeM,
      dispatch(st, ref(s"§${tag}b")) { nxt => ZSpace.Call(acc(st, k), Vector(ref(s"§${tag}P"), the(accAfter(st, k)(nxt))) ++ fr, xr +: fm) })

  def routine(r: ZRoutine[A]): ZRoutine[B] = ZRoutine(r.name, r.refs, r.mentions, space(r.body))
  def program(p: ZProgram[A]): ZProgram[B] =
    val main = space(p.main)
    val rs = p.routines.map((k, v) => k -> routine(v))
    ZProgram(main, rs ++ routines)
  /** how many routines the unrolling generated */
  def generated: Int = routines.size

object Translator:
  private[alpha] val instances = java.util.concurrent.atomic.AtomicInteger()

object Translate:
  def apply[A, B](code: Code[A, B], p: ZProgram[A]): ZProgram[B] = Translator(code).program(p)
  def encodeResult[A, B](code: Code[A, B], ws: Set[Word[A]]): Set[Word[B]] = ws.map(code.encWord)
