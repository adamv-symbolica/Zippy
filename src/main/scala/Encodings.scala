package morkl

import scala.collection.immutable.ArraySeq
import scala.collection.mutable

/** ==============================================================================================
 *  TRANSLATIONS BETWEEN Zippy_symbols, Zippy_bits AND Zippy_bytes.
 *
 *  A [[Code]] maps every source LETTER to a target codeword; the codewords form a PREFIX-FREE code
 *  recognised by a small [[CodeAutomaton]] whose longest codeword is `maxDepth`.  Paths encode
 *  letter by letter, so `Union`, `Intersection`, `Subtraction`, `Restriction`, `Raffination`,
 *  `Composition`, `Wrap`, `Unwrap`, `Singleton`, `Literal` and `Fixpoint` translate homomorphically
 *  (prefix-freeness is what makes `Restriction`/`Unwrap` commute with the encoding).  Every code
 *  here is ORDER-PRESERVING — codeword order is letter order — so `Range`'s canonical-order slice and
 *  `Fold`'s head order also commute.
 *
 *  The four letter-level constructors — `Iteration`, `TailsUnion`, `TailsIntersection`, `Fold` —
 *  consume ONE SOURCE LETTER, i.e. one whole codeword.  Unless the code is letter-to-letter they
 *  become recursive routines over the code automaton, one per (state, depth), using only target
 *  constructors:
 *
 *    walk_{q,d}(prefix, fv..; S, fm..) =
 *        ∪_{explicit letter c of q}  Iteration(Restriction(S, {c}), b, r, NEXT(q --c--> ·))
 *      ∪                             Iteration(Raffination(S, explicit(q)), b, r, NEXT(q --other--> ·))
 *    NEXT(goto q')  = walk_{q',d+1}(prefix·b, fv..; r, fm..)        (Empty once d+1 = maxDepth)
 *    NEXT(accept)   = LEAF(h := prefix·b, rest := r)
 *
 *  `Iteration` doubles as the LAZY GUARD: its body runs only for heads that occur, so a walk visits
 *  only the codeword prefixes that are present in the data, and `maxDepth` — the interned universe's
 *  longest codeword — is a hard bound on recursion depth, i.e. the unrolling depth.  The walk for
 *  `TailsIntersection` returns PRESENCE-TAGGED sets `{0} ∪ 1·G` so that the target's own
 *  `TailsIntersection` (intersection over the PRESENT children) combines siblings, and a genuinely
 *  empty intersection is not confused with an absent subtree.
 *
 *  `Fold` threads its accumulator from one head to the next; it translates exactly under a
 *  letter-to-letter code and is reported as unsupported otherwise ([[CodeTranslate.Unsupported]]).
 *  ============================================================================================== */
case class CodeAutomaton[T](states: Vector[CodeAutomaton.State[T]]):
  /** next state after `letter` in `q`: `Accept` (-1) or a state index */
  def step(q: Int, letter: T): Int =
    val st = states(q)
    st.explicit.collectFirst { case (c, n) if c == letter => n }.orElse(st.other)
      .getOrElse(throw IllegalArgumentException(s"letter not in code at state $q"))

object CodeAutomaton:
  val Accept = -1
  /** explicit letters with their successor, and the successor of every other letter (if any) */
  case class State[T](explicit: Vector[(T, Int)], other: Option[Int])

trait Code[S, T]:
  def name: String
  def src: Alphabet[S]
  def tgt: Alphabet[T]
  def automaton: CodeAutomaton[T]
  def maxDepth: Int
  def encodeLetter(l: S): T
  def decodeLetter(cw: T): S
  /** every codeword is a single target letter: the letter-level constructors map one-to-one */
  def letterToLetter: Boolean = false
  def encode(w: S): T = src.letters(w).foldLeft(tgt.empty)((acc, l) => tgt.concat(acc, encodeLetter(l)))
  def decode(t: T): S =
    val out = mutable.ArrayBuffer.empty[S]
    var q = 0; var cw = tgt.empty
    for l <- tgt.letters(t) do
      cw = tgt.concat(cw, l)
      q = automaton.step(q, l)
      if q == CodeAutomaton.Accept then { out += decodeLetter(cw); q = 0; cw = tgt.empty }
    require(tgt.length(cw) == 0, s"$name: trailing partial codeword in ${tgt.show(t)}")
    src.fromLetters(out)
  def encodeSet(ws: Set[S]): Set[T] = ws.map(encode)
  def decodeSet(ts: Set[T]): Set[S] = ts.map(decode)

/** a code that is a fixed letter <-> letter bijection on a declared domain (the injections) */
abstract class LetterCode[S, T] extends Code[S, T]:
  override def letterToLetter = true
  def maxDepth = 1
  def targetLetters: Vector[T]
  lazy val automaton = CodeAutomaton(Vector(CodeAutomaton.State(Vector.empty, Some(CodeAutomaton.Accept))))

object Codes:
  import CodeAutomaton.{Accept, State}

  /** the symbol universe of a program and its inputs: every item of every constant and literal */
  def symbolsOf(s: WSpace[List[String]]): Set[String] =
    val acc = mutable.Set.empty[String]
    def p(x: WPath[List[String]]): Unit = x match
      case WPath.Constant(w) => acc ++= w
      case WPath.Concat(l, r) => p(l); p(r)
      case WPath.GroundedPP(q, _) => p(q)
      case WPath.GroundedSP(q, _) => go(q)
      case _ => ()
    def go(x: WSpace[List[String]]): Unit =
      import WSpace.*
      x match
        case Literal(ws) => ws.foreach(acc ++= _)
        case Singleton(q) => p(q)
        case Call(_, rs, ms) => rs.foreach(p); ms.foreach(go)
        case Union(a, b) => go(a); go(b)
        case Intersection(a, b) => go(a); go(b)
        case Subtraction(a, b) => go(a); go(b)
        case Restriction(a, b) => go(a); go(b)
        case Raffination(a, b) => go(a); go(b)
        case Composition(a, b) => go(a); go(b)
        case Iteration(a, _, _, b) => go(a); go(b)
        case Fixpoint(a, _, b) => go(a); go(b)
        case Fold(a, i, _, _, _, b, u) => go(a); p(i); go(b); p(u)
        case Wrap(a, q) => go(a); p(q)
        case Unwrap(a, q) => go(a); p(q)
        case TailsUnion(a) => go(a)
        case TailsIntersection(a) => go(a)
        case GroundedPS(q, _) => p(q)
        case GroundedSS(a, _) => go(a)
        case Range(a, _, _) => go(a)
        case _ => ()
    go(s); acc.toSet

  /** Zeckendorf digits of n >= 1, least significant first, terminated by the extra 1 */
  def fibonacci(n: Int): Vector[Boolean] =
    require(n >= 1)
    val fibs = Iterator.iterate((1, 2))((a, b) => (b, a + b)).map(_._1).takeWhile(_ <= n).toVector
    var rest = n
    val digits = Array.fill(fibs.length)(false)
    for i <- fibs.indices.reverse do if fibs(i) <= rest then { digits(i) = true; rest -= fibs(i) }
    digits.toVector :+ true

  /** INTERNED SYMBOLS -> BITS, Fibonacci coding.  The universe is interned in symbol order onto the
   *  Fibonacci codewords in LEXICOGRAPHIC order, which makes the code order-preserving (so `Range`
   *  and `Fold` commute).  Codewords end at the first `11`; the automaton tracks the last bit. */
  def fibonacciBits(universe: Set[String]): Code[List[String], BigInt] =
    val syms = universe.toVector.sorted
    val words = Iterator.from(1).map(n => BitWords.fromBits(fibonacci(n))).take(syms.length).toVector.sorted(using BitWords.ordering)
    val enc = Map.from(syms zip words); val dec = Map.from(words zip syms)
    new Code[List[String], BigInt]:
      val name = "symbols->bits(fibonacci)"
      val src = SymWords; val tgt = BitWords
      val maxDepth = (words.map(BitWords.length) :+ 2).max
      val automaton = CodeAutomaton(Vector(
        State(Vector(BitWords.Zero -> 0, BitWords.One -> 1), None),       // start / last bit 0
        State(Vector(BitWords.Zero -> 0, BitWords.One -> Accept), None))) // last bit 1
      def encodeLetter(l: List[String]) = enc.getOrElse(l.head, throw IllegalArgumentException(s"symbol ${l.head} not interned"))
      def decodeLetter(cw: BigInt) = dec(cw) :: Nil

  /** STRING SYMBOLS -> BYTES: UTF-8 then a `\0` terminator.  Order-preserving for symbols without
   *  NUL (UTF-8 byte order is code-point order).  `maxDepth` is the longest symbol + 1. */
  def nulTerminatedBytes(universe: Set[String]): Code[List[String], ArraySeq[Byte]] =
    new Code[List[String], ArraySeq[Byte]]:
      val name = "symbols->bytes(utf8\\0)"
      val src = SymWords; val tgt = ByteWords
      val maxDepth = (universe.iterator.map(_.getBytes("UTF-8").length + 1) ++ Iterator(1)).max
      val automaton = CodeAutomaton(Vector(State(Vector(ByteWords.of(0) -> Accept), Some(0))))
      def encodeLetter(l: List[String]) =
        val s = l.head
        require(!s.contains('\u0000'), s"symbol contains NUL: $s")
        val bs = s.getBytes("UTF-8")
        require(bs.length + 1 <= maxDepth, s"symbol $s longer than the universe bound $maxDepth")
        ArraySeq.unsafeWrapArray(bs :+ 0.toByte)
      def decodeLetter(cw: ArraySeq[Byte]) = new String(cw.init.toArray, "UTF-8") :: Nil

  /** INTERNED SYMBOLS -> BYTES: the symbol's rank in the (sorted) universe as `width` big-endian
   *  bytes.  Fixed length, so prefix-free; ranks follow symbol order, so order-preserving. */
  def internedBytes(universe: Set[String], width: Int = 4): Code[List[String], ArraySeq[Byte]] =
    val syms = universe.toVector.sorted
    require(width >= 4 || syms.length <= (1 << (8 * width)))
    val enc = syms.zipWithIndex.toMap;
    new Code[List[String], ArraySeq[Byte]]:
      val name = s"symbols->bytes(interned$width)"
      val src = SymWords; val tgt = ByteWords
      val maxDepth = width
      val automaton = CodeAutomaton(Vector.tabulate(width)(k => State[ArraySeq[Byte]](Vector.empty, Some(if k == width - 1 then Accept else k + 1))))
      def encodeLetter(l: List[String]) =
        val i = enc.getOrElse(l.head, throw IllegalArgumentException(s"symbol ${l.head} not interned"))
        ArraySeq.from((0 until width).map(k => ((i >>> (8 * (width - 1 - k))) & 0xff).toByte))
      def decodeLetter(cw: ArraySeq[Byte]) = syms(cw.foldLeft(0)((a, b) => (a << 8) | (b & 0xff))) :: Nil

  /** BYTES -> BITS: one byte is eight bits, most significant first (fixed length, order-preserving) */
  val bytesToBits: Code[ArraySeq[Byte], BigInt] = new Code[ArraySeq[Byte], BigInt]:
    val name = "bytes->bits(8)"
    val src = ByteWords; val tgt = BitWords
    val maxDepth = 8
    val automaton = CodeAutomaton(Vector.tabulate(8)(k =>
      val n = if k == 7 then Accept else k + 1
      State(Vector(BitWords.Zero -> n, BitWords.One -> n), None)))
    def encodeLetter(l: ArraySeq[Byte]) = BitWords.fromBits((0 until 8).map(k => ((l(0) >> (7 - k)) & 1) == 1))
    def decodeLetter(cw: BigInt) = ArraySeq((cw.toInt & 0xff).toByte)

  /** BITS -> BYTES injection: false |-> 0, true |-> 1 */
  val bitsToBytes: Code[BigInt, ArraySeq[Byte]] = new LetterCode[BigInt, ArraySeq[Byte]]:
    val name = "bits->bytes(0/1)"
    val src = BitWords; val tgt = ByteWords
    val targetLetters = Vector(ByteWords.of(0), ByteWords.of(1))
    def encodeLetter(l: BigInt) = if l == BitWords.One then ByteWords.of(1) else ByteWords.of(0)
    def decodeLetter(cw: ArraySeq[Byte]) = cw(0) match
      case 0 => BitWords.Zero
      case 1 => BitWords.One
      case b => throw IllegalArgumentException(s"byte $b is not a bit")

  /** BITS -> SYMBOLS injection: the program's only symbols are "0" and "1" */
  val bitsToSymbols: Code[BigInt, List[String]] = new LetterCode[BigInt, List[String]]:
    val name = "bits->symbols(0/1)"
    val src = BitWords; val tgt = SymWords
    val targetLetters = Vector(List("0"), List("1"))
    def encodeLetter(l: BigInt) = if l == BitWords.One then List("1") else List("0")
    def decodeLetter(cw: List[String]) = cw.head match
      case "0" => BitWords.Zero
      case "1" => BitWords.One
      case s => throw IllegalArgumentException(s"symbol $s is not a bit")

  /** BYTES -> SYMBOLS injection: 0..255 are the symbols "00".."ff" (fixed-width hex: string order
   *  is unsigned byte order) and are the program's only symbols */
  val bytesToSymbols: Code[ArraySeq[Byte], List[String]] = new LetterCode[ArraySeq[Byte], List[String]]:
    val name = "bytes->symbols(hex)"
    val src = ByteWords; val tgt = SymWords
    val targetLetters = Vector.tabulate(256)(i => List(f"$i%02x"))
    def encodeLetter(l: ArraySeq[Byte]) = List(f"${l(0) & 0xff}%02x")
    def decodeLetter(cw: List[String]) = ArraySeq(Integer.parseInt(cw.head, 16).toByte)

  /** composition of two codes, e.g. symbols->bytes(\0) then bytes->bits (a symbols->bits code) —
   *  used to translate a program twice and compare against the direct route */
  def compose[A, B, C](f: Code[A, B], g: Code[B, C]): (A => C, C => A) = (a => g.encode(f.encode(a)), c => f.decode(g.decode(c)))

/** The translator: a [[WProgram]] over the code's source alphabet to an equivalent one over its
 *  target alphabet.  Generated routines are named `§<construct><k>_q<state>_d<depth>`. */
final class CodeTranslate[S, T](code: Code[S, T]):
  import CodeTranslate.*
  private val T = code.tgt
  private val routines = mutable.LinkedHashMap.empty[RoutinePtr, WRoutine[T]]
  /** generated (state, depth) walk routines, for the unrolling report */
  var walkRoutines = 0

  /** two distinct target letters used as the presence tag of the TailsIntersection walk */
  private lazy val (tagAbsent, tagPresent): (T, T) = T match
    case BitWords => (BitWords.Zero.asInstanceOf[T], BitWords.One.asInstanceOf[T])
    case ByteWords => (ByteWords.of(0).asInstanceOf[T], ByteWords.of(1).asInstanceOf[T])
    case SymWords => (List("0").asInstanceOf[T], List("1").asInstanceOf[T])
    case _ => throw Unsupported(s"no tag letters for ${T.name}")

  def path(p: WPath[S]): WPath[T] = p match
    case WPath.Deref(pr) => WPath.Deref(pr)
    case WPath.Constant(w) => WPath.Constant(code.encode(w))
    case WPath.Concat(l, r) => WPath.Concat(path(l), path(r))
    case WPath.GroundedPP(q, f) => WPath.GroundedPP(path(q), t => code.encode(f(code.decode(t))))
    case WPath.GroundedSP(q, f) => WPath.GroundedSP(space(q), ts => code.encode(f(code.decodeSet(ts))))

  def space(s: WSpace[S]): WSpace[T] =
    import WSpace.*
    s match
      case Empty() => Empty()
      case Call(r, rs, ms) => Call(r, rs.map(path), ms.map(space))
      case Mention(m) => Mention(m)
      case Singleton(p) => Singleton(path(p))
      case Literal(ws) => Literal(code.encodeSet(ws))
      case Union(a, b) => Union(space(a), space(b))
      case Intersection(a, b) => Intersection(space(a), space(b))
      case Subtraction(a, b) => Subtraction(space(a), space(b))
      case Restriction(a, b) => Restriction(space(a), space(b))
      case Raffination(a, b) => Raffination(space(a), space(b))
      case Composition(a, b) => Composition(space(a), space(b))
      case Fixpoint(a, r, b) => Fixpoint(space(a), r, space(b))
      case Wrap(a, p) => Wrap(space(a), path(p))
      case Unwrap(a, p) => Unwrap(space(a), path(p))
      case GroundedPS(p, f) => GroundedPS(path(p), t => code.encodeSet(f(code.decode(t))))
      case GroundedSS(a, f) => GroundedSS(space(a), ts => code.encodeSet(f(code.decodeSet(ts))))
      case Range(a, lo, hi) => Range(space(a), lo, hi)
      // ---- the letter-level constructors ----
      case Iteration(a, h, r, b) if code.letterToLetter => Iteration(space(a), h, r, space(b))
      case TailsUnion(a) if code.letterToLetter => TailsUnion(space(a))
      case TailsIntersection(a) if code.letterToLetter => TailsIntersection(space(a))
      case Fold(a, i, acc, h, r, b, u) if code.letterToLetter => Fold(space(a), path(i), acc, h, r, space(b), path(u))
      case Fold(_, _, _, _, _, _, _) => throw Unsupported(s"${code.name}: Fold over a multi-letter code (the accumulator cannot leave a codeword walk)")
      case Iteration(a, h, r, b) =>
        val body = space(b)
        val (fr0, fm0) = WSpaceOps.free(body)
        val fr = fr0.filterNot(_.s == h.s); val fm = fm0.filterNot(_.s == r.s)
        val k = nextId()
        // the template, lambda-lifted: its binders become the lifted routine's first parameters
        val bodyName = RoutinePtr(s"§iterbody$k")
        routines(bodyName) = WRoutine(bodyName, PathRef(if h.s == "_" then s"§h$k" else h.s) +: fr,
                                       SpaceMention(if r.s == "_" then s"§t$k" else r.s) +: fm, body)
        walk("iter", k, space(a), fr, fm, (hd, rest) => Call(bodyName, hd +: fr.map(WPath.Deref(_)), rest +: fm.map(Mention(_))),
             combine = identity)
      case TailsUnion(a) =>
        walk("tu", nextId(), space(a), Vector.empty, Vector.empty, (_, rest) => rest, combine = identity)
      case TailsIntersection(a) =>
        val tagged = walk("ti", nextId(), space(a), Vector.empty, Vector.empty,
          (_, rest) => Union(Wrap(rest, WPath.Constant(tagPresent)), Singleton(WPath.Constant(tagAbsent))),
          combine = TailsIntersection(_))
        Unwrap(tagged, WPath.Constant(tagPresent))

  // process-wide: a translated program may be translated again, and its generated names must not collide
  private def nextId(): Int = CodeTranslate.freshIds.incrementAndGet()

  /** Emit the (state, depth) walk routines for one occurrence and return the call at the root.
   *  `leaf(h, rest)` is the action once a codeword `h` is complete with tails `rest`; `combine` is
   *  applied to the union of the per-letter branches at each node (`TailsIntersection` combines
   *  with the target's own intersection-over-present-children; the others with plain union). */
  private def walk(kind: String, k: Int, src: WSpace[T], fr: Vector[PathRef], fm: Vector[SpaceMention],
                   leaf: (WPath[T], WSpace[T]) => WSpace[T], combine: WSpace[T] => WSpace[T]): WSpace[T] =
    import WSpace.*
    val A = code.automaton
    val L = code.maxDepth
    val pre = PathRef(s"§pre$k"); val S = SpaceMention(s"§S$k")
    val b = PathRef(s"§b$k"); val r = SpaceMention(s"§r$k")
    def name(q: Int, d: Int) = RoutinePtr(s"§$kind${k}_q${q}_d$d")
    val fArgs = fr.map(WPath.Deref[T](_)); val mArgs = fm.map(Mention[T](_))
    val needed = mutable.Set.empty[(Int, Int)]
    val todo = mutable.Queue((0, 0))
    needed += ((0, 0))
    while todo.nonEmpty do
      val (q, d) = todo.dequeue()
      val st = A.states(q)
      val hd: WPath[T] = WPath.Concat(WPath.Deref(pre), WPath.Deref(b))
      def next(n: Int): WSpace[T] =
        val inner =
          if n == CodeAutomaton.Accept then leaf(hd, Mention(r))
          else if d + 1 >= L then Empty()
          else
            if needed.add((n, d + 1)) then todo.enqueue((n, d + 1))
            Call(name(n, d + 1), hd +: fArgs, Mention[T](r) +: mArgs)
        // TailsIntersection tags each branch with its letter so the combinator groups by it
        if kind == "ti" then (if inner == Empty[T]() then inner else Wrap(inner, WPath.Deref(b))) else inner
      val branches: Vector[WSpace[T]] =
        st.explicit.map((c, n) => Iteration(Restriction(Mention(S), Literal(Set(c))), b, r, next(n))) ++
        st.other.map(n => Iteration(
          if st.explicit.isEmpty then Mention(S) else Raffination(Mention(S), Literal(st.explicit.map(_._1).toSet)),
          b, r, next(n))).toVector
      val body = branches.reduceOption(Union(_, _)).getOrElse(Empty())
      routines(name(q, d)) = WRoutine(name(q, d), pre +: fr, S +: fm, combine(body))
      walkRoutines += 1
    Call(name(0, 0), WPath.Constant(T.empty) +: fArgs, src +: mArgs)

  def routine(r: WRoutine[S]): WRoutine[T] = WRoutine(r.name, r.refs, r.mentions, space(r.body))

  def program(p: WProgram[S]): WProgram[T] =
    val main = space(p.main)
    val user = p.routines.map((n, r) => n -> routine(r))
    WProgram(T, main, user ++ routines)

object CodeTranslate:
  case class Unsupported(msg: String) extends Exception(msg)
  private[morkl] val freshIds = java.util.concurrent.atomic.AtomicInteger(0)
  def apply[S, T](code: Code[S, T], p: WProgram[S]): (WProgram[T], CodeTranslate[S, T]) =
    val t = new CodeTranslate(code); (t.program(p), t)
