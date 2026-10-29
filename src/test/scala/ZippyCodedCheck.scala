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
  /** (constructor, codec chain) pairs that actually ran and matched: the coverage matrix */
  private val coverage = collection.mutable.Set.empty[(String, String)]

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
    // the stable-id routes ALWAYS run: plain for order-free programs, with the frozen vocabulary otherwise
    univ.foreach(Interner.intern)
    val fibIp = FibInterner(Interner.size + 16)
    val (i4, fi) = if ordered(main, rs) then (InternerId4.ordered(univ), fibIp.ordered(univ)) else (InternerId4, fibIp)
    val (im, ir) = CodedTranslation(i4, main, rs)
    val (fim, fir) = CodedTranslation(fi, main, rs)
    val (fim0, fir0) = CodedTranslation(fi, main, rs, depthBudget = 0)
    ordinary ++ Vector(
      Chain("symbols->bytes (interner-4)", ByteRepr, im, ir, i4.encPath),
      Chain("symbols->bits (fib-interner, depth-bounded)", BitRepr, fim, fir, fi.encPath),
      Chain("symbols->bits (fib-interner, recursive)", BitRepr, fim0, fir0, fi.encPath))

  private def checkFrames(main: SymSpace, rs: Vector[ZRoutine[String]], frames: Seq[(Map[PathRef, Vector[String]], Map[SpaceMention, Set[Vector[String]]])]): Unit =
    val cs = chains(main, rs)
    val kinds = (main +: rs.map(_.body)).flatMap(sub).map(_.productPrefix).toSet
    for (pc, sc) <- frames do
      val exp = symEval(main, rs, pc, sc)
      assertEquals(ZEval.syms(rs, checkHints = true)(main, pc, sc), exp)
      for c <- cs do
        val ch = c.asInstanceOf[Chain[Any, Any]]
        assertEquals(ch.run(pc, sc), ch.want(exp), s"${c.label} on ${ZSym.to(main).show}")
        for k <- kinds do coverage += ((k, c.label))

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
    // without a frozen vocabulary an id cannot say where a symbol sorts: a named rejection, not a wrong answer
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

  // ----------------------------------------------------------------------------------------------
  // round 3: ordered operators on the stable-id codecs, stack safety, scale, coverage matrix

  /** run `f` on a thread with a deliberately SMALL stack (512 KiB): stack safety, not a big -Xss */
  private def smallStack[T](f: => T): T =
    var res: Either[Throwable, T] = null
    val th = Thread(null, () => res = try Right(f) catch { case e: Throwable => Left(e) }, "small-stack", 512L * 1024)
    th.start(); th.join()
    res.fold(e => throw e, identity)

  private val fa = PathRef("a"); private val fh = PathRef("h"); private val ft = SpaceMention("t")
  /** Fold collecting each head; accumulator unchanged (`u = a`) or changing (`u = a ++ h`, emitted with the head) */
  private def foldProg[A](src: ZSpace[A], changing: Boolean): ZSpace[A] =
    if changing then ZSpace.Fold(src, ZPath.Constant(Vector.empty), fa, fh, ft,
      ZSpace.Singleton(ZPath.Concat(ZPath.Deref(fa), ZPath.Deref(fh))), ZPath.Concat(ZPath.Deref(fa), ZPath.Deref(fh)))
    else ZSpace.Fold(src, ZPath.Constant(Vector.empty), fa, fh, ft, ZSpace.Singleton(ZPath.Deref(fh)), ZPath.Deref(fa))

  test("stable-id codecs run Range and Fold through a frozen vocabulary; ids are unchanged"):
    // the review's minimized example: a literal supplies its own vocabulary
    val src: SymSpace = ZSpace.Range(lit(Vector("z"), Vector("a")), 0, 1)
    val vocab = Vector("z", "a")
    for code <- Vector[Code[String, ?]](InternerId4.ordered(vocab), FibInterner(Interner.size + 10).ordered(vocab)) do
      code match
        case c: InternerIds4 =>
          val (m, rs) = CodedTranslation(c, src)
          assertEquals(ZEval.bytes(rs, checkHints = true)(m), Set(ByteRepr.fromAtoms(c.encPath(Vector("a")))))
        case c: FibInterner =>
          val (m, rs) = CodedTranslation(c, src)
          assertEquals(ZEval.bits(rs, checkHints = true)(m), Set(BitRepr.fromAtoms(c.encPath(Vector("a")))))
    // OPPOSITE interning and lexical order: ids increase while symbols decrease
    val tag = "ord" + System.nanoTime()
    val opp = Vector(s"$tag-9", s"$tag-5", s"$tag-1")
    opp.foreach(Interner.intern)
    assert(Interner.intern(opp(0)) < Interner.intern(opp(2)) && opp(0) > opp(2))
    val idsBefore = opp.map(InternerId4.enc)
    val gp: SymSpace = ZSpace.Singleton(ZPath.GroundedSP(ZSpace.Range(s0, -2, -1), ps => ps.toVector.flatten.sorted))
    val ev = RoutinePtr("ev"); val od = RoutinePtr("od")
    val mutual = Vector(
      ZRoutine[String](ev, Vector(), Vector(SpaceMention("x")), ZSpace.Union(ZSpace.Range(ZSpace.Mention(SpaceMention("x")), 0, 1),
        ZSpace.Iteration(ZSpace.Mention(SpaceMention("x")), PathRef("_"), SpaceMention("r"), ZSpace.Call(od, Vector(), Vector(ZSpace.Mention(SpaceMention("r"))))))),
      ZRoutine[String](od, Vector(), Vector(SpaceMention("x")), ZSpace.Range(ZSpace.TailsUnion(ZSpace.Mention(SpaceMention("x"))), -1, 0)))
    val progs: Vector[(SymSpace, Vector[ZRoutine[String]])] = Vector(
      ZSpace.Range(s0, 1, 3) -> Vector(), ZSpace.Range(s0, -3, -1) -> Vector(), ZSpace.Range(s0, -1, 0) -> Vector(),
      foldProg(s0, changing = true) -> Vector(), foldProg(s0, changing = false) -> Vector(),
      // accumulator-sensitive: only heads after the first contribute the accumulated prefix
      ZSpace.Fold(s0, c(), fa, fh, ft, ZSpace.Wrap(ZSpace.Mention(ft), ZPath.Deref(fa)), ZPath.Deref(fh)) -> Vector(),
      gp -> Vector(), ZSpace.Call(ev, Vector(), Vector(s0)) -> mutual)
    val rng = scala.util.Random(11)
    val pool = opp ++ Vector("a", "b", "ab")
    for (m, rs) <- progs do
      val fs = Vector.fill(6)((Map.empty[PathRef, Vector[String]],
        Map(SpaceMention("s0") -> Set.fill(rng.nextInt(7))(Vector.fill(rng.nextInt(4))(pool(rng.nextInt(pool.length)))))))
      val i4 = InternerId4.ordered(pool); val fi = FibInterner(Interner.size + 16).ordered(pool)
      val (bm, br) = CodedTranslation(i4, m, rs)
      val chainsF = Vector(CodedTranslation(fi, m, rs), CodedTranslation(fi, m, rs, depthBudget = 0))
      for (pc, sc) <- fs do
        val exp = symEval(m, rs, pc, sc)
        assertEquals(ZEval.bytes(br, checkHints = true)(bm, sc = sc.map((k, v) => k -> v.map(p => ByteRepr.fromAtoms(i4.encPath(p))))),
          exp.map(p => ByteRepr.fromAtoms(i4.encPath(p))), ZSym.to(m).show)
        for (fm, fr) <- chainsF do
          assertEquals(ZEval.bits(fr, checkHints = true)(fm, sc = sc.map((k, v) => k -> v.map(p => BitRepr.fromAtoms(fi.encPath(p))))),
            exp.map(p => BitRepr.fromAtoms(fi.encPath(p))), ZSym.to(m).show)
    Interner.intern(s"$tag-unrelated")
    assertEquals(opp.map(InternerId4.enc), idsBefore)
    assertEquals(idsBefore.map(_.foldLeft(0)((x, b) => (x << 8) | (b & 0xff))), opp.map(Interner.intern))
    // an inadmissible program constant is rejected at compile time
    intercept[IllegalArgumentException](CodedTranslation(InternerId4.ordered(Vector("a")), ZSpace.Range(lit(Vector("q")), 0, 1)))
    // no grounded callback is introduced into a pure ordered program
    val (pm, pr) = CodedTranslation(InternerId4.ordered(pool), foldProg(s0, changing = true))
    assert(!(pm +: pr.map(_.body)).exists(_.toString.contains("Grounded")))

  test("stack safety: the review's fold, long-head and native byte-fold witnesses on a 512 KiB stack"):
    smallStack {
      // A: 200-group fold under dict-4
      val values = (0 until 200).map(i => Vector(f"s$i%06d")).toSet
      val fold: SymSpace = ZSpace.Fold(ZSpace.Literal(values), ZPath.Constant(Vector.empty), fa, fh, ft,
        ZSpace.Singleton(ZPath.Deref(fh)), ZPath.Deref(fa))
      val d4 = Dictionary4(values.flatten.toVector)
      for changing <- Vector(false, true) do
        val src = foldProg(ZSpace.Literal(values), changing)
        val (m, rs) = CodedTranslation(d4, src)
        assertEquals(ZEval.bytes(rs)(m), ZEval.syms()(src).map(p => ByteRepr.fromAtoms(d4.encPath(p))))
      // B: one string head of 512 .. 65,536 characters through the unbounded string scanner
      for n <- Vector(128, 512, 4096, 65536) do
        val src: SymSpace = ZSpace.Iteration(ZSpace.Literal(Set(Vector("a" * n))), fh, ft, ZSpace.Singleton(ZPath.Deref(fh)))
        val (m, rs) = CodedTranslation(StringBytes, src)
        val out = ZEval.bytes(rs)(m)
        assertEquals(out, Set(ByteRepr.fromAtoms(StringBytes.enc("a" * n))))
        assertEquals(out.head.length, n + 1)
      // C: a NATIVE byte-language Fold over all 256 heads, then bytes -> bits
      for changing <- Vector(false, true) do
        val src: ByteSpace = foldProg(ZSpace.Literal((0 until 256).map(i => Vector(i.toByte)).toSet), changing)
        val native = ZEval.bytes()(src)
        assertEquals(native.size, 256)
        val (m, rs) = CodedTranslation(BytesToBits, src)
        assertEquals(ZEval.bits(rs, checkHints = true)(m), native.map(b => BitRepr.fromAtoms(BytesToBits.encPath(b.bytes.toVector))))
      // a native bit-language Fold translated to bytes and symbols
      val bsrc: BitSpace = foldProg(ZSpace.Literal(Set(Vector(true, false), Vector(false), Vector(true, true, false))), changing = true)
      val bn = ZEval.bits()(bsrc)
      val (jm, jr) = CodedTranslation(BitsToBytes, bsrc)
      assertEquals(ZEval.bytes(jr)(jm), bn.map(b => ByteRepr.fromAtoms(BitsToBytes.encPath(BitRepr.toAtoms(b)))))
    }

  test("fold sweep: 50 .. 10,000 groups, unchanged and changing accumulator, every byte/bit codec"):
    smallStack {
      for n <- Vector(0, 50, 200, 800, 4000, 10000); changing <- Vector(false, true) if !(changing && n > 4000) do
        val syms = (0 until n).map(i => f"g$i%06d").toVector
        // grouped tails and epsilon: each head carries two tails, plus the empty path
        val in: Set[Vector[String]] = syms.flatMap(s => Vector(Vector(s), Vector(s, "t"))).toSet + Vector()
        val src = foldProg(ZSpace.Mention[String](SpaceMention("s")), changing)
        val exp = ZEval.syms()(src, sc = Map(SpaceMention("s") -> in))
        val vocab = syms :+ "t"
        vocab.foreach(Interner.intern)
        val codes: Vector[Code[String, Byte]] = Vector(Dictionary4(vocab), StringBytes, InternerId4.ordered(vocab))
        for code <- codes do
          val (m, rs) = CodedTranslation(code, src)
          val ev = ZEval.bytes(rs)
          assertEquals(ev(m, sc = Map(SpaceMention("s") -> in.map(p => ByteRepr.fromAtoms(code.encPath(p))))),
            exp.map(p => ByteRepr.fromAtoms(code.encPath(p))), s"${code.name} n=$n changing=$changing")
    }

  test("FibInterner bound: direct maximum code length equals the exhaustive one, logarithmic construction"):
    for b <- (1 to 3000) do
      assertEquals(FibInterner(b).maxCodeLength.get, (1 to b).map(Codes.fibonacci(_).length).max, s"bound $b")
    val size0 = Interner.size
    val t0 = System.nanoTime()
    for b <- Vector(1, 64, 65, 1024, 1000000, Int.MaxValue) do
      val code = FibInterner(b)
      val (m, rs) = CodedTranslation(code, ZSpace.TailsUnion(s0))
      assert(rs.length <= 2 * code.maxCodeLength.get + 1, s"bound $b: ${rs.length} routines")
    assert((System.nanoTime() - t0) / 1e9 < 5.0)
    assertEquals(Interner.size, size0)
    assertEquals(FibInterner(Int.MaxValue).maxCodeLength.get, Codes.fibonacci(Int.MaxValue).length)

  test("coverage matrix: every constructor ran through every mandatory codec chain"):
    val constructors = Set("Empty", "Call", "Mention", "Singleton", "Literal", "Union", "Intersection", "Subtraction",
      "Restriction", "Raffination", "Composition", "Iteration", "Fixpoint", "Fold", "Wrap", "Unwrap", "TailsUnion",
      "TailsIntersection", "GroundedPS", "GroundedSS", "Range")
    // grounded callbacks (with an ordered argument) and Empty, through the same compile-once route
    val g: SymSpace = ZSpace.Union(ZSpace.GroundedSS(ZSpace.Range(s0, 0, 2), _.map(_.reverse)),
      ZSpace.Union(ZSpace.GroundedPS(c("a"), v => Set(v, v :+ "b")), ZSpace.Empty()))
    checkFrames(g, Vector(), frames(g, scala.util.Random(5), 6))
    val labels = coverage.map(_._2).toSet
    assertEquals(labels.size, 13, labels.toString) // CONTROLLED FAILURE
    val missing = for k <- constructors; l <- labels if !coverage((k, l)) yield (k, l)
    assert(missing.isEmpty, s"constructor × codec pairs never exercised: ${missing.toVector.sorted.take(20)}")
