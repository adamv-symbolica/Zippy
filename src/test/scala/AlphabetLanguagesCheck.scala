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

  test("Fibonacci code: prefix-free, order-preserving, bounded") {
    val c = Codes.symbolsToBits((1 to 40).map(i => f"s$i%02d").toSet)
    val ws = (1 to 40).map(i => c.enc(f"s$i%02d"))
    for a <- ws; b <- ws if a != b do assert(!a.startsWith(b))
    assert(ws.zip(ws.drop(1)).forall((a, b) => BitRepr.compare(BitRepr.fromWord(a), BitRepr.fromWord(b)) < 0))
    assertEquals(c.maxLen, ws.map(_.length).max)
    assert(c.maxLen <= 10, c.maxLen)
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

  /** run `p` on Zippy_symbols and through every translation; each must equal the encoded reference */
  def checkAll(name: String, p: ZProgram[String]): Unit =
    val expect = Symbols.evalWords(p)
    val u = Codes.symbolsOf(p) ++ expect.flatten
    val fib = Codes.symbolsToBits(u); val nul = Codes.stringsToBytes(u); val i4 = Codes.internedToBytes(u)
    val pb = Translate(fib, p)
    val bitsOut = Bits.evalWords(pb)
    assertEquals(bitsOut, Translate.encodeResult(fib, expect), s"$name: symbols->bits")
    assertEquals(Bytes.evalWords(Translate(nul, p)), Translate.encodeResult(nul, expect), s"$name: strings->bytes")
    val pi4 = Translate(i4, p)
    assertEquals(Bytes.evalWords(pi4), Translate.encodeResult(i4, expect), s"$name: interned->4 bytes")
    // bits -> bytes -> (8x) bits, bits -> symbols, bytes -> symbols: each a further translation of a
    // translated program, compared with the encoded expectation
    val pbb = Translate(Codes.bitsToBytes, pb)
    assertEquals(Bytes.evalWords(pbb), Translate.encodeResult(Codes.bitsToBytes, bitsOut), s"$name: bits->bytes")
    assertEquals(Symbols.evalWords(Translate(Codes.bitsToSymbols, pb)), Translate.encodeResult(Codes.bitsToSymbols, bitsOut), s"$name: bits->symbols")
    val i4Out = Translate.encodeResult(i4, expect)
    assertEquals(Bits.evalWords(Translate(Codes.bytesToBits, pi4)), Translate.encodeResult(Codes.bytesToBits, i4Out), s"$name: bytes->bits")
    assertEquals(Symbols.evalWords(Translate(Codes.bytesToSymbols, pi4)), Translate.encodeResult(Codes.bytesToSymbols, i4Out), s"$name: bytes->symbols")

  for (name, s) <- programs do
    test(s"all seven translations agree with morkl.eval: $name") { checkAll(name, symProg(s)) }

  test("Zippy_bits heads ⊆ {0,1} and Zippy_bytes heads form a ≤256 mask") {
    val u = Codes.symbolsOf(symProg(E))
    val heads = ZSpace.Iteration[Boolean](Translate(Codes.symbolsToBits(u), symProg(E)).main, PathRef("b"), SpaceMention("_"), ZSpace.Singleton(ZPath.Deref(PathRef("b"))))
    assert(Bits.evalWords(ZProgram(heads, Map())).subsetOf(Set(Vector(false), Vector(true))))
    val hb = ZSpace.Iteration[Byte](Translate(Codes.stringsToBytes(u), symProg(E)).main, PathRef("b"), SpaceMention("_"), ZSpace.Singleton(ZPath.Deref(PathRef("b"))))
    val mask = Bytes.eval(ZProgram(hb, Map())).foldLeft(BigInt(0))((m, w) => m.setBit(w(0) & 0xff))
    assertEquals(mask.bitCount, Set("a", "b", "c", "x").size)
  }

  test("the unrolling depth is the code length bound") {
    val u = (1 to 100).map("k" + _).toSet
    val fib = Codes.symbolsToBits(u)
    val t = Translator(fib); t.program(symProg(Space.TailsUnion(lit("k1.k2 k99"))))
    assertEquals(t.generated, 2 * (fib.maxLen + 1) - 1)  // state 0 at every depth, state 1 below the top
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
