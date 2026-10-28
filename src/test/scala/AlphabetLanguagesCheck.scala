package morkl

import morkl.alpha.*

/** Zippy_bits / Zippy_bytes: the case-for-case mirror of `Space`, the two reference backends, and
 *  every translation, checked DIFFERENTIALLY against the existing symbol evaluator `morkl.eval`. */
class AlphabetLanguagesCheck extends munit.FunSuite:
  import scala.deriving.Mirror
  import scala.compiletime.constValueTuple

  test("ZSpace / ZPath mirror Space / Path case for case") {
    def labels(t: Product): List[String] = t.productIterator.map(_.toString).toList
    val s = summon[Mirror.SumOf[Space]]; val z = summon[Mirror.SumOf[ZSpace[Boolean]]]
    assertEquals(labels(constValueTuple[z.MirroredElemLabels]), labels(constValueTuple[s.MirroredElemLabels]))
    val p = summon[Mirror.SumOf[Path]]; val zp = summon[Mirror.SumOf[ZPath[Byte]]]
    assertEquals(labels(constValueTuple[zp.MirroredElemLabels]), labels(constValueTuple[p.MirroredElemLabels]))
  }

  test("BigInt bit representation: ε, 0, 00 distinct; concat/drop/prefix/order") {
    val ws = for n <- 0 to 4; bits <- (0 until (1 << n)) yield Vector.tabulate(n)(i => ((bits >> i) & 1) == 1)
    assertEquals(ws.map(BitRepr.fromWord).toSet.size, ws.size)
    for a <- ws; b <- ws do
      val (pa, pb) = (BitRepr.fromWord(a), BitRepr.fromWord(b))
      assertEquals(BitRepr.toWord(BitRepr.concat(pa, pb)), a ++ b)
      assertEquals(BitRepr.startsWith(pa, pb), a.startsWith(b))
      val lex = a.zip(b).find(_ != _).map((x, _) => if x then 1 else -1).getOrElse(Integer.compare(a.length, b.length))
      assertEquals(Integer.signum(BitRepr.compare(pa, pb)), Integer.signum(lex))
      if b.length <= a.length then assertEquals(BitRepr.toWord(BitRepr.drop(pa, b.length)), a.drop(b.length))
  }

  test("Fibonacci code of interned id+1: prefix-free, stable, finite at Int.MaxValue") {
    val ws = (1L to 200L).map(Codes.fibonacci)
    for x <- ws; y <- ws if x != y do assert(!x.startsWith(y))
    for n <- Seq(1L, 2L, 3L, 1134903170L, 1836311903L, 2971215073L, Int.MaxValue.toLong, Int.MaxValue.toLong + 1) do
      val w = Codes.fibonacci(n); assertEquals(Codes.unfibonacci(w), n); assert(w.length <= 48, w.length)
    // real interner ids, interned in reverse lexical order: the codeword depends on the id only
    val (z, a) = (s"zz-${System.nanoTime}", s"aa-${System.nanoTime}")
    val iz = Interner.intern(z); val ia = Interner.intern(a)
    val c = Codes.symbolsToBits()
    assertEquals(c.enc(z), Codes.fibonacci(iz + 1L)); assertEquals(c.enc(a), Codes.fibonacci(ia + 1L))
    assertEquals(c.dec(c.enc(z) ++ c.enc(a)), Vector(z, a))
    val i4 = Codes.internedToBytes()
    assertEquals(i4.enc(z), Vector(iz >>> 24, iz >>> 16, iz >>> 8, iz).map(_.toByte))
    assertEquals(i4.dec(i4.enc(a) ++ i4.enc(z)), Vector(a, z))
    val bounded = Codes.symbolsToBits(Some(Interner.size))
    assert(bounded.bound.get >= c.enc(a).length)
    intercept[IllegalArgumentException](Codes.symbolsToBits(Some(0)).enc(a))
  }

  test("string code: total, injective, order-preserving on all UTF-16 code units, NUL and surrogates") {
    val c = Codes.stringsToBytes()
    val units = (0 until 65536).map(_.toChar.toString)
    val enc = units.map(c.enc)
    assertEquals(enc.toSet.size, 65536)
    for (u, e) <- units.zip(enc) do assertEquals(c.dec(e), Vector(u))
    def lt(x: String, y: String) = ByteStr.compare(ByteStr(c.enc(x).toArray), ByteStr(c.enc(y).toArray)) < 0
    val rnd = java.util.Random(7)
    val strs = Vector("", "\u0000", "a\u0000", "a", "\ud83d\ude00", "\ue000", "\ud800") ++
      (1 to 300).map(_ => String(Array.fill(rnd.nextInt(5))(rnd.nextInt(65536).toChar)))
    for x <- strs; y <- strs if x != y do assertEquals(lt(x, y), x.compareTo(y) < 0, s"$x vs $y")
    assertEquals(c.dec(strs.flatMap(c.enc)), strs)
    intercept[IllegalArgumentException](Codes.stringsToBytes(Some(2)).enc("long"))
  }

  test("ByteStr cannot be built over a caller-owned array") {
    val a = Array[Byte](1); val b = ByteStr(a)
    val s = Set(b, ByteStr(Array[Byte](2)), ByteStr(Array[Byte](3)), ByteStr(Array[Byte](4)), ByteStr(Array[Byte](5)))
    a(0) = 99; b.bytes(0) = 98
    assert(s.contains(b)); assertEquals(b(0), 1.toByte)
  }

  // ---------------------------------------------------------------------------------------------
  val L = (s: String) => SpaceValue(s.split(' ').filter(_.nonEmpty).map(x => PathValue(if x == "ε" then Nil else x.split('.').toList)).toSet)
  def lit(s: String) = Space.Literal(L(s))
  val E = lit("a.b.c a.c b.c b.b.a c a.b ε xyzzy.a xyzzy.c.b")
  val F = lit("a.b b.c c.a.b d")
  val ref = PathRef("h"); val restM = SpaceMention("r")
  val programs: Vector[(String, Space)] = Vector(
    "union" -> Space.Union(E, F), "inter" -> Space.Intersection(E, F), "sub" -> Space.Subtraction(E, F),
    "restrict" -> Space.Restriction(E, lit("a b.b")), "raff" -> Space.Raffination(E, lit("a")),
    "compose" -> Space.Composition(lit("a b"), F), "wrap" -> Space.Wrap(F, Path.Constant(PathValue(List("x", "y")))),
    "unwrap" -> Space.Unwrap(E, Path.Constant(PathValue(List("a")))),
    "tailsU" -> Space.TailsUnion(E), "tailsI" -> Space.TailsIntersection(lit("a.b a.c b.b b.c c.b")),
    "tailsI-empty-group" -> Space.TailsIntersection(lit("a.b c")), "tailsI-eps" -> Space.TailsIntersection(lit("ε a.b")),
    "range-first" -> Space.Range(E, 1, 2), "range-last2" -> Space.Range(E, -2, 0), "range-mid" -> Space.Range(F, 2, 4),
    "iter-swap" -> Space.Iteration(E, ref, restM, Space.Composition(Space.Mention(restM), Space.Singleton(Path.Deref(ref)))),
    "iter-free" -> Space.Iteration(F, ref, restM, Space.Intersection(Space.Mention(restM), Space.Unwrap(E, Path.Deref(ref)))),
    "iter-nested" -> Space.Iteration(E, ref, restM,
      Space.Iteration(Space.Mention(restM), PathRef("g"), SpaceMention("q"), Space.Singleton(Path.Concat(Path.Deref(PathRef("g")), Path.Deref(ref))))),
    "fold-concat" -> Space.Fold(E, Path.Constant(PathValue(List("z"))), PathRef("acc"), ref, restM,
      Space.Wrap(Space.Mention(restM), Path.Deref(PathRef("acc"))), Path.Concat(Path.Deref(PathRef("acc")), Path.Deref(ref))),
    "fixpoint-tc" -> Space.Fixpoint(lit("a.b b.c c.d"), SpaceMention("X"),
      Space.Iteration(Space.Mention(SpaceMention("X")), PathRef("x"), SpaceMention("ys"),
        Space.Wrap(Space.Iteration(Space.Mention(SpaceMention("ys")), PathRef("y"), SpaceMention("_"),
          Space.Unwrap(lit("a.b b.c c.d"), Path.Deref(PathRef("y")))).pipe(Space.TailsUnion(_)).pipe(identity), Path.Deref(PathRef("x"))))),
    "grounded" -> Space.GroundedSS(F, sv => SpaceValue(sv.paths.map(p => PathValue(p.items.reverse)))),
    "call" -> Space.Call(RoutinePtr("twice"), Vector(Path.Constant(PathValue(List("a")))), Vector(F)),
  )
  extension (s: Space) def pipe(f: Space => Space): Space = f(s)
  val table: Map[RoutinePtr, ZRoutine[String]] = Map(RoutinePtr("twice") -> Symbols.fromRoutine(Routine(RoutinePtr("twice"), Vector(PathRef("p")), Vector(SpaceMention("S")),
    Space.Union(Space.Wrap(Space.Mention(SpaceMention("S")), Path.Deref(PathRef("p"))), Space.TailsUnion(Space.Mention(SpaceMention("S")))))))

  def symProg(s: Space) = ZProgram(Symbols.fromSpace(s), table)

  def ordered(p: ZProgram[String]): Boolean =
    def sp(x: ZSpace[String]): Boolean = x match
      case ZSpace.Range(_, _, _) | ZSpace.Fold(_, _, _, _, _, _, _) => true
      case q: Product => q.productIterator.exists { case c: ZSpace[String] @unchecked => sp(c); case _ => false }
    sp(p.main) || p.routines.values.exists(r => sp(r.body))
  /** translate with a code that may refuse order-observing ops; refusal only where the source has them */
  def tr[A, B](code: Code[A, B], p: ZProgram[A], src: ZProgram[String]): Option[ZProgram[B]] =
    try Some(Translate(code, p)) catch case e: UnsupportedOperationException =>
      assert(ordered(src), s"${code.name} refused an order-free program: ${e.getMessage}"); None

  /** run `p` on Zippy_symbols and through every translation; each must equal the encoded reference.
   *  The codes are built BEFORE evaluation and know nothing about the program or its outputs. */
  def checkAll(name: String, p: ZProgram[String]): Unit =
    val fib = Codes.symbolsToBits(); val nul = Codes.stringsToBytes(); val i4 = Codes.internedToBytes()
    val pb = tr(fib, p, p); val pn = Translate(nul, p); val pi4 = tr(i4, p, p)
    val expect = Symbols.evalWords(p)
    pb.foreach(q => assertEquals(Bits.evalWords(q), Translate.encodeResult(fib, expect), s"$name: symbols->bits"))
    val nulOut = Bytes.evalWords(pn)
    assertEquals(nulOut, Translate.encodeResult(nul, expect), s"$name: strings->bytes")
    pi4.foreach(q => assertEquals(Bytes.evalWords(q), Translate.encodeResult(i4, expect), s"$name: interned->4 bytes"))
    // bytes -> (8x) bits -> bytes / symbols, bytes -> symbols: further translations of translated programs
    val p8 = Translate(Codes.bytesToBits, pn)
    val bitsOut = Bits.evalWords(p8)
    assertEquals(bitsOut, Translate.encodeResult(Codes.bytesToBits, nulOut), s"$name: bytes->bits")
    assertEquals(Symbols.evalWords(Translate(Codes.bytesToSymbols, pn)), Translate.encodeResult(Codes.bytesToSymbols, nulOut), s"$name: bytes->symbols")
    assertEquals(Bytes.evalWords(Translate(Codes.bitsToBytes, p8)), Translate.encodeResult(Codes.bitsToBytes, bitsOut), s"$name: bits->bytes")
    assertEquals(Symbols.evalWords(Translate(Codes.bitsToSymbols, p8)), Translate.encodeResult(Codes.bitsToSymbols, bitsOut), s"$name: bits->symbols")

  for (name, s) <- programs do
    test(s"all seven translations agree with morkl.eval: $name") { checkAll(name, symProg(s)) }

  test("Zippy_bits heads ⊆ {0,1} and Zippy_bytes heads form a ≤256 mask") {
    val heads = ZSpace.Iteration[Boolean](Translate(Codes.symbolsToBits(), symProg(E)).main, PathRef("b"), SpaceMention("_"), ZSpace.Singleton(ZPath.Deref(PathRef("b"))))
    assert(Bits.evalWords(ZProgram(heads, Map())).subsetOf(Set(Vector(false), Vector(true))))
    val hb = ZSpace.Iteration[Byte](Translate(Codes.stringsToBytes(), symProg(E)).main, PathRef("b"), SpaceMention("_"), ZSpace.Singleton(ZPath.Deref(PathRef("b"))))
    val mask = Bytes.eval(ZProgram(hb, Map())).foldLeft(BigInt(0))((m, w) => m.setBit(w(0) & 0xff))
    assertEquals(mask.bitCount, Set("a", "b", "c", "x").size)
  }

  test("the unrolling depth is the declared code length bound; open codes recurse") {
    val fib = Codes.symbolsToBits(Some(100))
    val t = Translator(fib); t.program(symProg(Space.TailsUnion(lit("k1.k2 k99"))))
    assertEquals(t.generated, 2 * (fib.bound.get + 1) - 1)  // state 0 at every depth, state 1 below the top
    val o = Translator(Codes.symbolsToBits()); o.program(symProg(Space.TailsUnion(lit("k1.k2 k99"))))
    assertEquals(o.generated, 2)
  }

  // ---- regressions from review ------------------------------------------------------------------
  def outer(body: Space, formal: String, arg: String): ZProgram[String] =
    val r = RoutinePtr("outer")
    ZProgram(Symbols.fromSpace(Space.Call(r, Vector(Path.Constant(PathValue(List(arg)))), Vector())),
      Map(r -> Symbols.fromRoutine(Routine(r, Vector(PathRef(formal)), Vector(), body))))

  test("annotations: exact lengths scale by a fixed width and are dropped for variable width") {
    val p = ZProgram(ZSpace.Singleton[String](ZPath.Deref(PathRef("p").known(1))), Map())
    def hint(q: ZProgram[?]) = q.main match { case ZSpace.Singleton(ZPath.Deref(pr)) => pr.lengthHint; case x => fail(x.toString) }
    assertEquals(hint(Translate(Codes.internedToBytes(), p)), 4)
    assertEquals(hint(Translate(Codes.bytesToBits, Translate(Codes.internedToBytes(), p))), 32)
    assertEquals(hint(Translate(Codes.symbolsToBits(), p)), -1)
    assertEquals(hint(Translate(Codes.stringsToBytes(), p)), -1)
    // the optimizer counterexample: TailsUnion over a once-annotated Singleton, before/after lowering
    val q = Translate(Codes.bytesToSymbols, Translate(Codes.internedToBytes(), p))
    val prog = Space.TailsUnion(Symbols.toSpace(q.main))
    val r = RoutinePtr("w")
    val v = PathValue(Codes.bytesToSymbols.encWord(Codes.internedToBytes().enc("a")).toList)
    val call = Space.Call(r, Vector(Path.Constant(v)), Vector())
    val table = Map(r -> Routine(r, Vector(PathRef("p").known(4)), Vector(), prog))
    val before = Symbols.evalWords(ZProgram(Symbols.fromSpace(call), table.view.mapValues(Symbols.fromRoutine).toMap))
    assertEquals(before, Set(v.items.drop(1).toVector))
  }

  test("ignored binder `_` does not capture the outer `_`") {
    for (code, name) <- Seq[(Code[String, ?], String)](Codes.symbolsToBits() -> "fib", Codes.stringsToBytes() -> "nul") do
      val p = outer(Space.Iteration(lit("a"), PathRef("_"), SpaceMention("r"), Space.Singleton(Path.Deref(PathRef("_")))), "_", "z")
      assertEquals(Symbols.evalWords(p), Set(Vector("z")))
      checkAll(s"underscore-$name", p)
    checkAll("underscore-fold", outer(Space.Fold(lit("a b"), Path.Constant(PathValue(List("i"))), PathRef("_"), PathRef("_"), SpaceMention("_"),
      Space.Singleton(Path.Deref(PathRef("_"))), Path.Deref(PathRef("_"))), "_", "z"))
  }

  test("coincident fold binder names keep the source shadowing (head over accumulator)") {
    val h = PathRef("h")
    val p = symProg(Space.Fold(lit("a b"), Path.Constant(PathValue(List("z"))), h, h, restM, Space.Singleton(Path.Deref(h)), Path.Deref(h)))
    assertEquals(Symbols.evalWords(p), Set(Vector("a"), Vector("b")))
    checkAll("fold-coincident", p)
  }

  test("source names that look generated are not captured; translation is deterministic") {
    for n <- Seq("§0.P", "§0.b", "§0.X", "§1.a", "§0.iter2_s0") do
      val p = outer(Space.Iteration(lit("a.b"), PathRef("h"), SpaceMention("r"),
        Space.Composition(Space.Singleton(Path.Deref(PathRef(n))), Space.Mention(SpaceMention("r")))), n, "z")
      checkAll(s"capture-$n", p)
    val p = symProg(programs.find(_._1 == "fold-concat").get._2)
    assertEquals(Translate(Codes.stringsToBytes(), p).routines.keySet, Translate(Codes.stringsToBytes(), p).routines.keySet)
  }

  test("fold: one walk per source group, routines linear in the codeword bound") {
    val counts = for l <- Seq(8, 16, 32) yield
      val s = "x" * l
      val p = symProg(Space.Fold(lit(s + ".a"), Path.Constant(PathValue(List("z"))), PathRef("acc"), ref, restM,
        Space.Wrap(Space.Mention(restM), Path.Deref(PathRef("acc"))), Path.Deref(PathRef("acc"))))
      val t = Translator(Codes.stringsToBytes(Some(l))); val q = t.program(p)
      assertEquals(Bytes.evalWords(q), Translate.encodeResult(Codes.stringsToBytes(), Symbols.evalWords(p)))
      assert(t.generated <= 8 * (3 * l + 2), s"L=$l: ${t.generated} routines")
      t.generated
    // linear (the former out/acc double walk was quadratic in traversal): doubling L at most doubles
    assert(counts(2) <= 2 * counts(1) + 8 && counts(1) <= 2 * counts(0) + 8, counts.toString)
  }

  test("fuzzed corpus: all seven translations agree with morkl.eval") {
    val recs = Corpus.load(200)
    val sBank = Vector(lit("a.b.c a.c b.c b.b.a c ε"), lit("a.b b.c c.a.b d a"), lit("c.c b a.b.b"), lit("a b.a.c d.d"))
    val pBank = Vector("a", "b.c", "c", "a.a")
    var n = 0
    for (r, i) <- recs.zipWithIndex do
      val (fr, fm) = FreeVars.space(Symbols.fromSpace(r.prog))
      val refs = fr.toVector.sorted; val ms = fm.toVector.sorted
      val wrap = RoutinePtr("corpusMain")
      val main = Space.Call(wrap, refs.indices.map(j => Path.Constant(L(pBank(j % pBank.size)).paths.head)).toVector,
        ms.indices.map(j => sBank(j % sBank.size)).toVector)
      val prog = ZProgram(Symbols.fromSpace(main), table + (wrap -> Symbols.fromRoutine(Routine(wrap, refs.map(PathRef(_)), ms.map(SpaceMention(_)), r.prog))))
      checkAll(s"corpus#$i", prog); n += 1
    assert(n >= 100, n)
  }
