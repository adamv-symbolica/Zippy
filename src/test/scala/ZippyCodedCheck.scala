package morkl

import Codes.*

/** Differential check of Zippy_bits / Zippy_bytes and every translation: for corpus programs and
 *  hand-written Fold/Fixpoint/recursive-routine programs, the coded program evaluated on the
 *  BigInt-set (bits) or byte-array-set (bytes) reference back-end equals the encoded image of the
 *  symbol-level `eval`. */
class ZippyCodedCheck extends munit.FunSuite:
  private val alphabet = Vector("a", "b", "c", "d", "w1", "w2")
  private def rndPath(rng: scala.util.Random, max: Int) = Vector.fill(rng.nextInt(max + 1))(alphabet(rng.nextInt(alphabet.length)))
  private def rndSpace(rng: scala.util.Random) = Set.fill(rng.nextInt(6))(rndPath(rng, 3))

  private given Ordering[Bit] = Ordering.Boolean

  /** check one program under one environment against all coded chains; returns #chains checked */
  private def checkAll(main: SymSpace, rs: Vector[ZRoutine[String]], pc: Map[PathRef, Vector[String]], sc: Map[SpaceMention, Set[Vector[String]]]): Unit =
    val expected = eval(ZSym.to(main))(using PathContextMap(pc.map((k, v) => k -> PathValue(v.toList))),
      SpaceContextMap(sc.map((k, v) => k -> ZSym.toV(v))), rs.map(r => r.name -> ZSym.to(r)).toMap)
    val exp = ZSym.fromV(expected)
    // Zippy_symbols twin
    assertEquals(ZEval.syms(rs)(main, pc, sc), exp)
    val univ = ZSym.universe(main +: rs.map(_.body), pc.values ++ sc.values.flatten)

    def run[B, R](label: String, back: PathRepr[B, R], m: ZSpace[B], trs: Vector[ZRoutine[B]], encB: Vector[String] => Vector[B]): Unit =
      val ev = ZEval(back, trs.map(r => r.name -> r).toMap)
      val got = ev(m, pc.map((k, v) => k -> back.fromAtoms(encB(v))), sc.map((k, v) => k -> v.map(p => back.fromAtoms(encB(p)))))
      val want = exp.map(p => back.fromAtoms(encB(p)))
      assertEquals(got, want, s"$label on ${ZSym.to(main).show}")

    val fib = FibInterned(univ)
    val (fm, fr) = CodedTranslation(fib, main, rs)
    run("symbols->bits (fib, unrolled)", BitRepr, fm, fr, fib.encPath)
    val fibR = FibInterned(univ, unrollUpTo = 0)
    val (fm2, fr2) = CodedTranslation(fibR, main, rs)
    run("symbols->bits (fib, recursive)", BitRepr, fm2, fr2, fibR.encPath)
    val (um, ur) = CodedTranslation(Utf8Z, main, rs)
    run("symbols->bytes (utf8+NUL)", ByteRepr, um, ur, Utf8Z.encPath)
    val i4 = Interned4(univ)
    val (im, ir) = CodedTranslation(i4, main, rs)
    run("symbols->bytes (interned-4)", ByteRepr, im, ir, i4.encPath)
    // bytes -> bits, one to eight, on top of interned-4
    val (bm, br) = CodedTranslation(BytesToBits, im, ir)
    run("symbols->bytes->bits", BitRepr, bm, br, v => BytesToBits.encPath(i4.encPath(v)))
    // bits -> bytes injection on top of fib, then back to bits by one-to-eight
    val (jm, jr) = CodedTranslation(BitsToBytes, fm2, fr2)
    run("symbols->bits->bytes", ByteRepr, jm, jr, v => BitsToBytes.encPath(fibR.encPath(v)))
    // bits -> symbols injection: a symbol program whose only symbols are "0" and "1"
    val (sm, sr) = CodedTranslation(BitsToSyms, fm2, fr2)
    run("symbols->bits->symbols", VecRepr[String], sm, sr, v => BitsToSyms.encPath(fibR.encPath(v)))
    // bytes -> symbols injection: the only symbols are 00..ff
    val (ym, yr) = CodedTranslation(BytesToSyms, um, ur)
    run("symbols->bytes->symbols", VecRepr[String], ym, yr, v => BytesToSyms.encPath(Utf8Z.encPath(v)))

  private def envFor(main: SymSpace, rng: scala.util.Random) =
    val (fp, fm) = ZOps.free(main)
    (fp.map(p => p -> (rndPath(rng, 2) match { case v if v.isEmpty => Vector("a"); case v => v })).toMap,
     fm.map(m => m -> rndSpace(rng)).toMap)

  test("BitRepr and ByteRepr implement the canonical path algebra"):
    val rng = scala.util.Random(7)
    for _ <- 0 until 2000 do
      val x = Vector.fill(rng.nextInt(6))(rng.nextBoolean()); val y = Vector.fill(rng.nextInt(6))(rng.nextBoolean())
      val v = VecRepr[Bit]
      assertEquals(BitRepr.toAtoms(BitRepr.concat(BitRepr.fromAtoms(x), BitRepr.fromAtoms(y))), x ++ y)
      assertEquals(Integer.signum(BitRepr.compare(BitRepr.fromAtoms(x), BitRepr.fromAtoms(y))), Integer.signum(v.compare(x, y)))
      assertEquals(BitRepr.startsWith(BitRepr.fromAtoms(x), BitRepr.fromAtoms(y)), x.startsWith(y))
      if x.startsWith(y) then assertEquals(BitRepr.toAtoms(BitRepr.dropPrefix(BitRepr.fromAtoms(x), BitRepr.fromAtoms(y))), x.drop(y.length))
      assertEquals(BitRepr.uncons(BitRepr.fromAtoms(x)).map((h, t) => BitRepr.toAtoms(h) ++ BitRepr.toAtoms(t)), Option.when(x.nonEmpty)(x))
      val bx = BytesToBits.decPath(Vector.fill(8 * rng.nextInt(3))(rng.nextBoolean()))
      val by = BytesToBits.decPath(Vector.fill(8 * rng.nextInt(3))(rng.nextBoolean()))
      assertEquals(Integer.signum(ByteRepr.compare(ByteRepr.fromAtoms(bx), ByteRepr.fromAtoms(by))),
        Integer.signum(v.compare(BytesToBits.encPath(bx), BytesToBits.encPath(by))))

  test("codes are prefix-free, order-preserving and decodable"):
    val univ = (alphabet ++ (0 until 200).map(i => s"s$i")).sorted
    val fib = FibInterned(univ)
    val codes = univ.map(fib.enc)
    for x <- codes; y <- codes if x != y do assert(!x.startsWith(y))
    assertEquals(codes, codes.sortWith((x, y) => VecRepr[Bit].compare(x, y) < 0))
    assertEquals(fib.maxCodeLength, codes.map(_.length).max)
    for s <- univ do
      assertEquals(fib.decPath(fib.enc(s) ++ fib.enc("a")), Vector(s, "a"))
      assertEquals(Utf8Z.decPath(Utf8Z.enc(s)), Vector(s))
    assertEquals(Codes.fibonacci(1), Vector(true, true)); assertEquals(Codes.fibonacci(4), Vector(true, false, true, true))

  test("Zippy_bits head is a subset of {0,1}, Zippy_bytes head a subset of 256 bytes"):
    val s: ZSpace[Bit] = ZSpace.Literal(Set(Vector(true, false), Vector(true, true), Vector()))
    val heads = ZSpace.Iteration(s, PathRef("h"), SpaceMention("_"), ZSpace.Singleton(ZPath.Deref(PathRef("h"))))
    assertEquals(ZEval.bits()(heads), Set(BitRepr.fromAtoms(Vector(true))))
    val (m, rs) = CodedTranslation(Utf8Z, ZSym.from(Space.TailsUnion(Space.Literal(SpaceValue(Set(PathValue(List("ab", "c"))))))))
    val hb = ZSpace.Iteration(m, PathRef("h"), SpaceMention("_"), ZSpace.Singleton(ZPath.Deref(PathRef("h"))))
    assertEquals(ZEval.bytes(rs)(hb), Set(ByteRepr.fromAtoms(Vector('c'.toByte))))

  test("hand-written programs: Fold, Fixpoint, TailsIntersection, recursive routines, Range"):
    import Syntax.{*, given}
    val s0 = Space.Mention(SpaceMention("s0")); val s1 = Space.Mention(SpaceMention("s1"))
    val h = PathRef("h"); val t = SpaceMention("t"); val acc = PathRef("acc")
    val progs = Vector[Space](
      Space.Fold(s0, Path.Constant(PathValue(List("a"))), acc, h, t,
        Space.Composition(Space.Singleton(Path.Concat(Path.Deref(acc), Path.Deref(h))), Space.Mention(t)),
        Path.Concat(Path.Deref(h), Path.Deref(acc))),
      Space.Fixpoint(s0, SpaceMention("x"), Space.Union(Space.TailsUnion(Space.Mention(SpaceMention("x"))), s1)),
      Space.TailsIntersection(Space.Union(s0, Space.Wrap(s1, Path.Constant(PathValue(List("b")))))),
      Space.Range(Space.Union(s0, s1), 2, 4),
      Space.Iteration(s0, h, t, Space.Iteration(s1, PathRef("g"), SpaceMention("u"),
        Space.Wrap(Space.Union(Space.Mention(t), Space.Mention(SpaceMention("u"))), Path.Concat(Path.Deref(PathRef("g")), Path.Deref(h))))),
      Space.Call(RoutinePtr("rev"), Vector(), Vector(s0)),
    )
    // rev(; X) = reverse every path: Iteration over heads, recursive on the tails
    val rev = Routine(RoutinePtr("rev"), Vector(), Vector(SpaceMention("X")),
      Space.Union(Space.Intersection(Space.Mention(SpaceMention("X")), Space.Singleton(Path.ZERO)),
        Space.Iteration(Space.Mention(SpaceMention("X")), h, t,
          Space.Composition(Space.Call(RoutinePtr("rev"), Vector(), Vector(Space.Mention(t))), Space.Singleton(Path.Deref(h))))))
    val rng = scala.util.Random(11)
    for p <- progs; _ <- 0 until 25 do
      val m = ZSym.from(p)
      val (pc, sc) = envFor(m, rng)
      checkAll(m, Vector(ZSym.from(rev)), pc, sc)

  test("corpus: every coded chain agrees with eval"):
    val corpus = Corpus.load()
    val rng = scala.util.Random(3)
    var checked = 0
    for rec <- corpus do
      val m = ZSym.from(rec.prog)
      for _ <- 0 until 3 do
        val (pc, sc) = envFor(m, rng)
        checkAll(m, Vector(), pc, sc)
        checked += 1
    assert(checked >= 3000, checked)
