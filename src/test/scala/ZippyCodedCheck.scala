package morkl

import Codes.*

/** Acceptance checks of Zippy_bits / Zippy_bytes and every translation.  Each program is COMPILED
 *  ONCE per code against a declared contract (the alphabet below, or the stable `Interner`) and then
 *  run on many input frames through the public `CodedTranslation` + `ZEval` route; the result on the
 *  BigInt-set (bits) or byte-array-set (bytes) back-end must equal the encoded symbol-level `eval`,
 *  with every length annotation checked at every binding. */
class ZippyCodedCheck extends munit.FunSuite:
  /** the input contract: ordinary symbols plus every string the string code must get right —
   *  empty, NUL, the escape byte, BMP boundaries, lone and paired surrogates, shared prefixes */
  private val alphabet = Vector("a", "b", "ab", "a\u0000b", "", "\u0000", "\u0001", "\u0002", "?", "\u007f", "\u0080",
    "߿", "ࠀ", "\uD800", "􏿿", "𐀀", "", "￿", "w1")
  private def rndPath(rng: scala.util.Random, max: Int) = Vector.fill(rng.nextInt(max + 1))(alphabet(rng.nextInt(alphabet.length)))
  private def rndSpace(rng: scala.util.Random, maxLen: Int = 3) = Set.fill(rng.nextInt(8))(rndPath(rng, maxLen))

  private given Ordering[Bit] = Ordering.Boolean

  private def symEval(main: SymSpace, rs: Vector[ZRoutine[String]], pc: Map[PathRef, Vector[String]], sc: Map[SpaceMention, Set[Vector[String]]]) =
    ZSym.fromV(eval(ZSym.to(main))(using PathContextMap(pc.map((k, v) => k -> PathValue(v.toList))),
      SpaceContextMap(sc.map((k, v) => k -> ZSym.toV(v))), rs.map(r => r.name -> ZSym.to(r)).toMap))

  /** one compiled chain: translate once, then run on every frame */
  private final case class Chain[B, R](label: String, back: PathRepr[B, R], main: ZSpace[B], rs: Vector[ZRoutine[B]], enc: Vector[String] => Vector[B]):
    def run(pc: Map[PathRef, Vector[String]], sc: Map[SpaceMention, Set[Vector[String]]]): Set[R] =
      ZEval(back, rs.map(r => r.name -> r).toMap, checkHints = true)(main,
        pc.map((k, v) => k -> back.fromAtoms(enc(v))), sc.map((k, v) => k -> v.map(p => back.fromAtoms(enc(p)))))
    def want(exp: Set[Vector[String]]): Set[R] = exp.map(p => back.fromAtoms(enc(p)))

  private val dictFib = FibDictionary(alphabet)
  private val dict4 = Dictionary4(alphabet)
  private val fibI = { alphabet.foreach(Interner.intern); FibInterner(Interner.size + 16) }

  private def hasOrdered(s: SymSpace): Boolean = s match
    case ZSpace.Range(_, lo, hi) if !(lo == 0 && hi == 0) => true
    case _: ZSpace.Fold[?] => true
    case _ => false
  private def ordered(m: SymSpace, rs: Vector[ZRoutine[String]]): Boolean =
    (m +: rs.map(_.body)).exists(b => sub(b).exists(hasOrdered))
  private def sub(s: SymSpace): Iterator[SymSpace] = Iterator(s) ++ (s match
    case ZSpace.Call(_, _, ms) => ms.iterator.flatMap(sub)
    case ZSpace.Union(x, y) => sub(x) ++ sub(y)
    case ZSpace.Intersection(x, y) => sub(x) ++ sub(y)
    case ZSpace.Subtraction(x, y) => sub(x) ++ sub(y)
    case ZSpace.Restriction(x, y) => sub(x) ++ sub(y)
    case ZSpace.Raffination(x, y) => sub(x) ++ sub(y)
    case ZSpace.Composition(x, y) => sub(x) ++ sub(y)
    case ZSpace.Iteration(x, _, _, t) => sub(x) ++ sub(t)
    case ZSpace.Fixpoint(x, _, t) => sub(x) ++ sub(t)
    case ZSpace.Fold(x, _, _, _, _, t, _) => sub(x) ++ sub(t)
    case ZSpace.Wrap(x, _) => sub(x)
    case ZSpace.Unwrap(x, _) => sub(x)
    case ZSpace.TailsUnion(x) => sub(x)
    case ZSpace.TailsIntersection(x) => sub(x)
    case ZSpace.GroundedSS(x, _) => sub(x)
    case ZSpace.Range(x, _, _) => sub(x)
    case _ => Iterator.empty)

  /** every chain, compiled once for (main, rs) */
  private def chains(main: SymSpace, rs: Vector[ZRoutine[String]]): Vector[Chain[?, ?]] =
    // the closed-dictionary contract: the input alphabet plus the program's own constants, fixed at compile time
    val univ = ZSym.universe(main +: rs.map(_.body), Seq(alphabet))
    val dictFib = FibDictionary(univ); val dict4 = Dictionary4(univ)
    val (fm, fr) = CodedTranslation(dictFib, main, rs)
    val (fm0, fr0) = CodedTranslation(dictFib, main, rs, depthBudget = 0)        // unbounded recursive decoder
    val (um, ur) = CodedTranslation(StringBytes, main, rs)
    val (dm, dr) = CodedTranslation(dict4, main, rs)
    val (bm, br) = CodedTranslation(BytesToBits, dm, dr)
    val (jm, jr) = CodedTranslation(BitsToBytes, fm, fr)
    val (sm, sr) = CodedTranslation(BitsToSyms, fm0, fr0)
    val (ym, yr) = CodedTranslation(BytesToSyms, um, ur)
    val (sbm, sbr) = CodedTranslation(BytesToBits, um, ur)
    val ordinary = Vector[Chain[?, ?]](
      Chain("symbols->bits (fib-dict, depth-bounded)", BitRepr, fm, fr, dictFib.encPath),
      Chain("symbols->bits (fib-dict, recursive)", BitRepr, fm0, fr0, dictFib.encPath),
      Chain("symbols->bytes (string-nul)", ByteRepr, um, ur, StringBytes.encPath),
      Chain("symbols->bytes (dict-4)", ByteRepr, dm, dr, dict4.encPath),
      Chain("symbols->bytes->bits (dict-4, 8 bit)", BitRepr, bm, br, v => BytesToBits.encPath(dict4.encPath(v))),
      Chain("symbols->bytes->bits (string-nul, 8 bit)", BitRepr, sbm, sbr, v => BytesToBits.encPath(StringBytes.encPath(v))),
      Chain("symbols->bits->bytes", ByteRepr, jm, jr, v => BitsToBytes.encPath(dictFib.encPath(v))),
      Chain("symbols->bits->symbols", VecRepr[String], sm, sr, v => BitsToSyms.encPath(dictFib.encPath(v))),
      Chain("symbols->bytes->symbols", VecRepr[String], ym, yr, v => BytesToSyms.encPath(StringBytes.encPath(v))))
    if ordered(main, rs) then ordinary
    else
      val (im, ir) = CodedTranslation(InternerId4, main, rs)
      val (fim, fir) = CodedTranslation(fibI, main, rs)
      ordinary ++ Vector(
        Chain("symbols->bytes (interner-4)", ByteRepr, im, ir, InternerId4.encPath),
        Chain("symbols->bits (fib-interner)", BitRepr, fim, fir, fibI.encPath))

  private def checkFrames(main: SymSpace, rs: Vector[ZRoutine[String]], frames: Seq[(Map[PathRef, Vector[String]], Map[SpaceMention, Set[Vector[String]]])]): Unit =
    val cs = chains(main, rs)
    for (pc, sc) <- frames do
      val exp = symEval(main, rs, pc, sc)
      assertEquals(ZEval.syms(rs, checkHints = true)(main, pc, sc), exp)
      for c <- cs do
        val ch = c.asInstanceOf[Chain[Any, Any]]
        assertEquals(ch.run(pc, sc), ch.want(exp), s"${c.label} on ${ZSym.to(main).show}")

  private def frames(main: SymSpace, rng: scala.util.Random, n: Int) =
    val (fp, fm) = ZOps.free(main)
    Vector.fill(n)((fp.map(p => p -> (rndPath(rng, 2) match { case v if v.isEmpty => Vector("a"); case v => v })).toMap,
                    fm.map(m => m -> rndSpace(rng)).toMap))

  private val s0 = ZSpace.Mention[String](SpaceMention("s0"))
  private val s1 = ZSpace.Mention[String](SpaceMention("s1"))
  private def lit(ps: Vector[String]*): SymSpace = ZSpace.Literal(ps.toSet)
  private def d(n: String) = ZPath.Deref[String](PathRef(n))
  private def c(xs: String*) = ZPath.Constant[String](xs.toVector)

  // --------------------------------------------------------------------------------------------
  test("BitRepr and ByteRepr implement the canonical path algebra"):
    val rng = scala.util.Random(7)
    val v = VecRepr[Bit]
    for _ <- 0 until 2000 do
      val x = Vector.fill(rng.nextInt(6))(rng.nextBoolean()); val y = Vector.fill(rng.nextInt(6))(rng.nextBoolean())
      assertEquals(BitRepr.toAtoms(BitRepr.concat(BitRepr.fromAtoms(x), BitRepr.fromAtoms(y))), x ++ y)
      assertEquals(Integer.signum(BitRepr.compare(BitRepr.fromAtoms(x), BitRepr.fromAtoms(y))), Integer.signum(v.compare(x, y)))
      assertEquals(BitRepr.startsWith(BitRepr.fromAtoms(x), BitRepr.fromAtoms(y)), x.startsWith(y))
      if x.startsWith(y) then assertEquals(BitRepr.toAtoms(BitRepr.dropPrefix(BitRepr.fromAtoms(x), BitRepr.fromAtoms(y))), x.drop(y.length))
      assertEquals(BitRepr.uncons(BitRepr.fromAtoms(x)).map((h, t) => BitRepr.toAtoms(h) ++ BitRepr.toAtoms(t)), Option.when(x.nonEmpty)(x))
      val bx = BytesToBits.decPath(Vector.fill(8 * rng.nextInt(3))(rng.nextBoolean()))
      val by = BytesToBits.decPath(Vector.fill(8 * rng.nextInt(3))(rng.nextBoolean()))
      assertEquals(Integer.signum(ByteRepr.compare(ByteRepr.fromAtoms(bx), ByteRepr.fromAtoms(by))),
        Integer.signum(v.compare(BytesToBits.encPath(bx), BytesToBits.encPath(by))))

  test("ByteArr is immutable: no aliasing through constructor or accessor, stable hashed membership"):
    val src = Array[Byte](1, 2)
    val k = ByteArr(src)
    src(0) = 100
    assertEquals(k, ByteArr(Array[Byte](1, 2)))
    k.bytes(0) = 100
    assertEquals(k(0), 1.toByte)
    val set = (0 until 64).map(i => ByteRepr.fromAtoms(Vector(i.toByte, 2.toByte))).toSet
    val stored = set.find(_ == ByteArr(Array[Byte](1, 2))).get
    stored.bytes(0) = 100
    assert(set.contains(ByteArr(Array[Byte](1, 2))))
    assertEquals(set.intersect(Set(ByteArr(Array[Byte](1, 2)), ByteArr(Array[Byte](5, 5)))), Set(ByteArr(Array[Byte](1, 2))))

  test("codes: prefix-free, order-preserving, total and reversible over Java strings"):
    val v = VecRepr[String](using Ordering.String)
    val strs = alphabet ++ Vector("a\u0001", "a\u0000", "\u0000\u0000", "\uDC00", "\uD800a")
    for x <- strs; y <- strs do
      val (ex, ey) = (StringBytes.enc(x), StringBytes.enc(y))
      if x != y then assert(!ex.startsWith(ey), s"${x.toVector} ${y.toVector}")
      assertEquals(Integer.signum(ByteRepr.compare(ByteRepr.fromAtoms(ex), ByteRepr.fromAtoms(ey))), Integer.signum(x.compareTo(y)), s"$x vs $y")
    for x <- strs do assertEquals(StringBytes.decPath(StringBytes.enc(x) ++ StringBytes.enc("a")), Vector(x, "a"))
    val univ = (alphabet ++ (0 until 200).map(i => s"s$i")).distinct.sorted
    val fib = FibDictionary(univ)
    val codes = univ.map(fib.enc)
    for x <- codes; y <- codes if x != y do assert(!x.startsWith(y))
    assertEquals(codes, codes.sortWith((x, y) => VecRepr[Bit].compare(x, y) < 0))
    assertEquals(fib.maxCodeLength, Some(codes.map(_.length).max))
    assertEquals(Codes.fibonacci(1), Vector(true, true)); assertEquals(Codes.fibonacci(4), Vector(true, false, true, true))

  test("string-nul regressions: surrogate order, lone surrogate vs '?', embedded NUL"):
    val (m1, r1) = CodedTranslation(StringBytes, ZSpace.Range(lit(Vector("𐀀"), Vector("")), 0, 1))
    assertEquals(ZEval.bytes(r1)(m1).map(b => StringBytes.decPath(ByteRepr.toAtoms(b))), Set(Vector("𐀀")))
    val (m2, r2) = CodedTranslation(StringBytes, ZSpace.Intersection(ZSpace.Singleton(c("?")), ZSpace.Singleton(c("\uD800"))))
    assertEquals(ZEval.bytes(r2)(m2), Set.empty[ByteArr])
    assertEquals(StringBytes.decPath(StringBytes.enc("a\u0000b")), Vector("a\u0000b"))

  test("interner codes use the stable Interner ids and do not move when unrelated symbols are interned"):
    val z = Interner.intern("zippy-review-z"); val a = Interner.intern("zippy-review-a")
    assertEquals(InternerId4.enc("zippy-review-z"), Vector(0, 0, 0, z).map(_.toByte).updated(2, (z >>> 8).toByte).updated(1, (z >>> 16).toByte))
    val before = InternerId4.enc("zippy-review-a")
    Interner.intern("zippy-review-unrelated-" + System.nanoTime())
    assertEquals(InternerId4.enc("zippy-review-a"), before)
    assertEquals(InternerId4.dec(before), "zippy-review-a")
    assertEquals(InternerId4.decPath(InternerId4.encPath(Vector("zippy-review-z", "zippy-review-a"))).map(Interner.intern), Vector(z, a))
    // the same four bytes an ITrie keys on: the id of every item of an interned path
    assertEquals(Interner.internPath(List("zippy-review-z", "zippy-review-a")),
      InternerId4.encPath(Vector("zippy-review-z", "zippy-review-a")).grouped(4).map(g => g.foldLeft(0)((x, b) => (x << 8) | (b & 0xff))).toList)
    assertEquals(fibI.dec(fibI.enc("a")), "a")
    intercept[UnsupportedOperationException](CodedTranslation(InternerId4, ZSpace.Range(s0, 1, 2)))

  test("Zippy_bits heads ⊆ {0,1}; Zippy_bytes heads: all 256 unsigned bytes"):
    val s: ZSpace[Bit] = ZSpace.Literal(Set(Vector(true, false), Vector(true, true), Vector(), Vector(false)))
    val heads = ZSpace.Iteration(s, PathRef("h"), SpaceMention("_"), ZSpace.Singleton(ZPath.Deref(PathRef("h"))))
    assertEquals(ZEval.bits()(heads), Set(BitRepr.fromAtoms(Vector(true)), BitRepr.fromAtoms(Vector(false))))
    val all: ZSpace[Byte] = ZSpace.Literal((0 until 256).map(i => Vector(i.toByte, 7.toByte)).toSet)
    val hb = ZSpace.Iteration(all, PathRef("h"), SpaceMention("_"), ZSpace.Singleton(ZPath.Deref(PathRef("h"))))
    val got = ZEval.bytes()(hb)
    assertEquals(got.size, 256)
    assertEquals(got.toVector.sortWith((x, y) => ByteRepr.compare(x, y) < 0).map(b => b(0) & 0xff), (0 until 256).toVector)
    // through bytes->bits, each byte head is 8 bit-levels deep
    val (bm, br) = CodedTranslation(BytesToBits, hb)
    assertEquals(ZEval.bits(br, checkHints = true)(bm).map(b => BytesToBits.decPath(BitRepr.toAtoms(b))), got.map(ByteRepr.toAtoms))

  test("hygiene: generated names, fold shadowing, `_` binders and `_` formals"):
    // a source routine with a name a naive generator would produce
    val clash = Vector("fib-dict_iter_body_1", "string-nul_iter_body_1", "dict-4_iter_body_1", "byte-8bit_iter_entry_1",
      "fib-dict_iter_q0_d0_3", "string-nul_iter_q0_2", "fib-interner_iter_body_1")
    val rs = clash.map(n => ZRoutine[String](RoutinePtr(n), Vector(), Vector(), lit(Vector("b"))))
    val prog = clash.foldLeft(ZSpace.Iteration(lit(Vector("a")), PathRef("h"), SpaceMention("r"), ZSpace.Singleton(d("h"))): SymSpace)(
      (acc, n) => ZSpace.Union(acc, ZSpace.Call(RoutinePtr(n), Vector(), Vector())))
    checkFrames(prog, rs, Seq((Map(), Map())))
    // head shadows accumulator when spelled the same
    val q = PathRef("q")
    val f = ZSpace.Fold(lit(Vector("a"), Vector("b")), c(), q, q, SpaceMention("r"), ZSpace.Singleton(ZPath.Deref(q)), ZPath.Deref(q))
    checkFrames(f, Vector(), Seq((Map(), Map())))
    assertEquals(symEval(f, Vector(), Map(), Map()), Set(Vector("a"), Vector("b")))
    // an ignored binder leaves the outer `_` visible
    val u = ZSpace.Iteration(lit(Vector("a")), PathRef("_"), SpaceMention("_"), ZSpace.Singleton(d("_")))
    checkFrames(u, Vector(), Seq((Map(PathRef("_") -> Vector("b")), Map())))
    val uf = ZSpace.Fold(s0, c("w1"), PathRef("_"), PathRef("_"), SpaceMention("_"), ZSpace.Composition(ZSpace.Singleton(d("_")), s0), c("a"))
    checkFrames(uf, Vector(), frames(uf, scala.util.Random(5), 10).map((pc, sc) => (pc.updated(PathRef("_"), Vector("b")), sc)))
    // a routine formal named `_` is an ordinary parameter
    val r_ = ZRoutine[String](RoutinePtr("id_"), Vector(PathRef("_")), Vector(SpaceMention("_")),
      ZSpace.Union(ZSpace.Singleton(d("_")), ZSpace.TailsUnion(ZSpace.Mention(SpaceMention("_")))))
    checkFrames(ZSpace.Call(RoutinePtr("id_"), Vector(c("a")), Vector(s0)), Vector(r_), frames(s0, scala.util.Random(6), 10))
    // alpha-renaming invariance: the same program under user names that collide with generated ones
    val alpha = ZSpace.Iteration(s0, PathRef("fib-dict_b_2"), SpaceMention("fib-dict_r_3"),
      ZSpace.Iteration(s1, PathRef("fib-dict_acc_4"), SpaceMention("fib-dict_x_5"),
        ZSpace.Wrap(ZSpace.Mention(SpaceMention("fib-dict_r_3")), ZPath.Concat(d("fib-dict_acc_4"), d("fib-dict_b_2")))))
    checkFrames(alpha, Vector(), frames(alpha, scala.util.Random(8), 20))

  test("hand-written programs: Fold, Fixpoint, TailsIntersection, recursion, mutual recursion, Range"):
    val h = PathRef("h"); val t = SpaceMention("t"); val acc = PathRef("acc")
    val progs = Vector[SymSpace](
      ZSpace.Fold(s0, c("a"), acc, h, t, ZSpace.Composition(ZSpace.Singleton(ZPath.Concat(ZPath.Deref(acc), ZPath.Deref(h))), ZSpace.Mention(t)),
        ZPath.Concat(ZPath.Deref(h), ZPath.Deref(acc))),
      ZSpace.Fixpoint(s0, SpaceMention("x"), ZSpace.Union(ZSpace.TailsUnion(ZSpace.Mention(SpaceMention("x"))), s1)),
      ZSpace.TailsIntersection(ZSpace.Union(s0, ZSpace.Wrap(s1, c("b")))),
      ZSpace.Range(ZSpace.Union(s0, s1), 2, 4),
      ZSpace.Range(ZSpace.Union(s0, s1), -2, 0),
      ZSpace.Iteration(s0, h, t, ZSpace.Iteration(s1, PathRef("g"), SpaceMention("u"),
        ZSpace.Wrap(ZSpace.Union(ZSpace.Mention(t), ZSpace.Mention(SpaceMention("u"))), ZPath.Concat(d("g"), ZPath.Deref(h))))),
      ZSpace.Iteration(ZSpace.Iteration(s0, h, t, ZSpace.Mention(t)), h, t, ZSpace.Composition(ZSpace.Mention(t), ZSpace.Singleton(ZPath.Deref(h)))),
      ZSpace.Call(RoutinePtr("rev"), Vector(), Vector(s0)),
      ZSpace.Call(RoutinePtr("even"), Vector(), Vector(s0)),
    )
    val X = SpaceMention("X")
    val rev = ZRoutine[String](RoutinePtr("rev"), Vector(), Vector(X),
      ZSpace.Union(ZSpace.Intersection(ZSpace.Mention(X), ZSpace.Singleton(c())),
        ZSpace.Iteration(ZSpace.Mention(X), h, t, ZSpace.Composition(ZSpace.Call(RoutinePtr("rev"), Vector(), Vector(ZSpace.Mention(t))), ZSpace.Singleton(ZPath.Deref(h))))))
    // even/odd: keep the paths of even length, by mutual recursion on tails
    val even = ZRoutine[String](RoutinePtr("even"), Vector(), Vector(X),
      ZSpace.Union(ZSpace.Intersection(ZSpace.Mention(X), ZSpace.Singleton(c())),
        ZSpace.Iteration(ZSpace.Mention(X), h, t, ZSpace.Wrap(ZSpace.Call(RoutinePtr("odd"), Vector(), Vector(ZSpace.Mention(t))), ZPath.Deref(h)))))
    val odd = ZRoutine[String](RoutinePtr("odd"), Vector(), Vector(X),
      ZSpace.Iteration(ZSpace.Mention(X), h, t, ZSpace.Wrap(ZSpace.Call(RoutinePtr("even"), Vector(), Vector(ZSpace.Mention(t))), ZPath.Deref(h))))
    val rng = scala.util.Random(11)
    for p <- progs do checkFrames(p, Vector(rev, even, odd), frames(p, rng, 30))

  test("pure programs compile to pure programs; injections use only their 2 / 256 symbols"):
    val p = ZSpace.Fold(ZSpace.TailsIntersection(s0), c("a"), PathRef("acc"), PathRef("h"), SpaceMention("t"),
      ZSpace.Wrap(ZSpace.Mention(SpaceMention("t")), d("acc")), ZPath.Concat(d("h"), d("acc")))
    val (fm, fr) = CodedTranslation(dictFib, p)
    val (sm, sr) = CodedTranslation(BitsToSyms, fm, fr)
    for b <- sm +: sr.map(_.body) do assert(ZOps.constants(b).flatten.toSet.subsetOf(Set("0", "1")))
    val (um, ur) = CodedTranslation(StringBytes, p)
    val (ym, yr) = CodedTranslation(BytesToSyms, um, ur)
    for b <- ym +: yr.map(_.body) do assert(ZOps.constants(b).flatten.forall(s => s.length == 2 && Integer.parseInt(s, 16) < 256))
    assert(!(fm +: fr.map(_.body)).exists(b => b.toString.contains("Grounded")))

  test("Fibonacci bound drives a recursion-free, depth-specialized decoder; variable lengths stay unannotated"):
    for k <- Vector(1, 64, 65, 1024) do
      val univ = (0 until k).map(i => f"s$i%05d").toVector
      val code = FibDictionary(univ)
      val maxLen = code.maxCodeLength.get
      assertEquals(maxLen, (1 to k).map(Codes.fibonacci(_).length).max)
      val (m, rs) = CodedTranslation(code, ZSpace.TailsUnion(s0))
      val states = rs.filter(_.name.s.contains("_iter_q"))
      assertEquals(states.length, 2 * maxLen, s"k=$k")
      // no routine calls itself or a shallower state: the depth is bounded by maxLen
      for r <- states do
        val dd = r.name.s.split("_d")(1).takeWhile(_.isDigit).toInt
        for n <- ZOps.names(r.body) if n.contains("_iter_q") do assert(n.split("_d")(1).takeWhile(_.isDigit).toInt == dd + 1)
      for r <- rs; f <- r.refs do assertEquals(f.lengthHint, -1, s"${r.name.s}.${f.s} must not claim an exact length")
      val in = Set(Vector(univ.last, univ.head), Vector(univ.head))
      assertEquals(ZEval.bits(rs, checkHints = true)(m, sc = Map(SpaceMention("s0") -> in.map(p => BitRepr.fromAtoms(code.encPath(p))))),
        Set(BitRepr.fromAtoms(code.encPath(Vector(univ.head))), BitRepr.empty))
    // fixed-width: a head annotated known(1) is a 4-byte / 8-bit formal, and the checker sees every binding
    val ann = ZSpace.Iteration(s0, PathRef("h").known(1), SpaceMention("t"), ZSpace.Singleton(ZPath.Deref(PathRef("h").known(1))))
    val (dm, dr) = CodedTranslation(dict4, ann)
    assertEquals(dr.flatMap(_.refs).filter(_.s == "h").map(_.lengthHint), Vector(4))
    val (bm, br) = CodedTranslation(BytesToBits, dm, dr)
    assertEquals(br.flatMap(_.refs).filter(_.s == "h").map(_.lengthHint), Vector(32))
    checkFrames(ann, Vector(), frames(ann, scala.util.Random(2), 20))

  test("decoder source is evaluated once: nested tails do linear, not exponential, work"):
    def nested(n: Int): SymSpace = (0 until n).foldLeft(lit(Vector.fill(n)("a")): SymSpace)((x, _) => ZSpace.TailsUnion(x))
    def work(code: Code[String, Bit], n: Int, budget: Int): Long =
      val (m, rs) = CodedTranslation(code, nested(n), depthBudget = budget)
      val ev = ZEval.bits(rs)
      assertEquals(ev(m), Set(BitRepr.empty))
      ev.steps
    val two = FibDictionary(Vector("a", "b"))
    for budget <- Vector(4096, 0) do
      val (w8, w16, w32) = (work(two, 8, budget), work(two, 16, budget), work(two, 32, budget))
      // work per consumed digit: tails over n paths of length ≤ n is Θ(n²) digits; 2^n would be 256x/65536x
      assert(w16.toDouble / w8 < 5.0 && w32.toDouble / w16 < 5.0, s"budget=$budget: $w8 $w16 $w32")

  test("corpus: every coded chain agrees with eval, one compilation per program"):
    val corpus = Corpus.load()
    val rng = scala.util.Random(3)
    var checked = 0
    for rec <- corpus do
      val m = ZSym.from(rec.prog)
      val fs = frames(m, rng, 3)
      checkFrames(m, Vector(), fs)
      checked += fs.length
    assert(checked >= 3000, checked)
