package morkl

import munit.FunSuite
import scala.concurrent.duration.*

class WordEncodingReviewTest extends FunSuite:
  override val munitTimeout = 300.seconds
  private def p(text: String): PathValue = PathValue(List(PathItem(text)))
  test("nested 32-symbol decoder tries share their lifted bodies through depth six") {
    val it = new PathItemInterner
    (0 until 32).foreach(i => it.intern(PathItem(s"s$i")))
    val code = WordEncodings.sortedDictionaryBits(it)
    for depth <- 1 to 6 do
      val hs = Vector.tabulate(depth)(i => PathRef(s"h$i"))
      val ts = Vector.tabulate(depth)(i => SpaceMention(s"t$i"))
      def body(i: Int): Space =
        if i == depth then Space.Singleton(Path.Concat(Path.Deref(hs.head), Path.Deref(hs.last)))
        else Space.Iteration(if i == 0 then Space.Mention(SpaceMention("x")) else Space.Mention(ts(i-1)), hs(i), ts(i), body(i+1))
      val source = SymbolWordLanguage.space(body(0))
      val target = code.compile(source)
      val bound = 64L * (WordSyntax.nodes(source) + 32 * code.maxCodeLength.get)
      assert(target.nodeCount <= bound, s"depth=$depth size=${target.nodeCount} bound=$bound")
      assertEquals(target.routines.size, depth)
      println(s"nested depth=$depth nodes=${target.nodeCount}")
  }

  test("CESU-8 is total and preserves Java UTF-16 order, including lone surrogates") {
    val code = WordEncodings.stringSymbolsToBytes(new PathItemInterner)
    val samples = Vector("\uD800", "\uDC00", "􏿿", "\uE000", "?", "\u0000", "a\uDC00", "", "\u0001")
    samples.foreach(s => assertEquals(code.decode(code.encode(p(s))), p(s)))
    assertEquals(ByteWords.length(code.encode(p("😀"))), 7)
    val rng = new scala.util.Random(12)
    def text(): String = Vector.fill(rng.nextInt(12))(rng.nextInt(65536).toChar).mkString
    for _ <- 0 until 10000 do
      val a = text(); val b = text()
      val aa = code.encode(p(a)); val bb = code.encode(p(b))
      assertEquals(code.decode(aa), p(a))
      assertEquals(Integer.signum(ByteWords.compare(aa, bb)), Integer.signum(a.compareTo(b)))
  }

  test("literal interner codes stay stable, accept future symbols, and closed failures do not intern") {
    val it = new PathItemInterner; it.intern(PathItem("b"))
    val bits = WordEncodings.internedSymbolsToBits(it)
    val bytes = WordEncodings.internedSymbolsToBytes(it)
    val closed = WordEncodings.sortedDictionaryBytes(it)
    val beforeB = bytes.encode(p("b")); val beforeBits = bits.encode(p("b"))
    val x = SpaceMention("x"); val h = PathRef("h"); val t = SpaceMention("t")
    val source = SymbolWordLanguage.space(Space.Iteration(Space.Mention(x), h, t, Space.Singleton(Path.Deref(h))))
    val bp = bits.compile(source); val byp = bytes.compile(source)
    assert(bp.routines.size > 20, "the literal ID bit bound should produce a depth-indexed DAG")
    assert(bp.routines.size <= 2 * bits.maxCodeLength.get + 1)
    val count = it.size
    intercept[IllegalArgumentException](closed.encode(p("a")))
    assertEquals(it.size, count)
    val value = WordSpaceValue(Set(p("a"), p("b")))
    assertEquals(bytes.decodeSpace(byp.eval(spaces = Map(x -> bytes.encodeSpace(value)), validateHints = true)(using ByteWords)), value)
    assertEquals(bits.decodeSpace(bp.eval(spaces = Map(x -> bits.encodeSpace(value)), validateHints = true)(using BitWords)), value)
    assertEquals(bytes.encode(p("b")), beforeB)
    assertEquals(WordEncodings.internedSymbolsToBytes(it).encode(p("b")), beforeB)
    assertEquals(bits.encode(p("b")), beforeBits)
    assertEquals(bytes.encode(p("a")).unsigned, Vector(0,0,0,1))
    assertEquals(bits.encode(p("a")), BitWords.fromDigits(FibonacciCode.encode(1)))
    val ordered = SymbolWordLanguage.space(Space.Range(Space.Mention(x), 0, 1))
    intercept[UnsupportedEncodedOrdering](bits.compile(ordered))
    intercept[UnsupportedEncodedOrdering](bytes.compile(ordered))
  }

  test("byte-trie masks, focus and native algebra agree with the set reference") {
    val values = ByteSpaceValue((0 until 256).map(i => ByteWords.fromDigits(Vector(i, 255-i))).toSet + ByteWords.empty)
    val trie = ByteTrie.fromPaths(values.paths)
    assertEquals(trie.childMask, (BigInt(1) << 256) - 1)
    assertEquals(trie.pathCount, 257)
    val expressions = Vector[ByteSpace](ByteSpace.Head(ByteSpace.Literal(values)),
      ByteSpace.PrefixClosure(ByteSpace.Literal(values)), ByteSpace.Range(ByteSpace.Literal(values), -12, 0),
      ByteSpace.Restriction(ByteSpace.Literal(values), ByteSpace.Singleton(BytePath.Constant(ByteWords.fromDigits(Vector(127))))))
    expressions.foreach(s => assertEquals(Zippy_bytes.evalZ(s).toSpaceValue, Zippy_bytes.eval(s)))
  }

  test("long unbounded codewords are stack safe on both byte evaluators") {
    val source = p("x" * 5000)
    val x = SpaceMention("x"); val h = PathRef("h"); val t = SpaceMention("t")
    val code = WordEncodings.stringSymbolsToBytes(new PathItemInterner)
    val program = code.compile(SymbolWordLanguage.space(Space.Iteration(Space.Mention(x), h, t, Space.Singleton(Path.Deref(h)))))
    val input = Map(x -> code.encodeSpace(WordSpaceValue(Set(source))))
    val expected = program.eval(spaces = input, validateHints = true)(using ByteWords)
    assertEquals(Zippy_bytes.evalZ(program.entry, spaces = input, routines = program.routines, validateHints = true).toSpaceValue, expected)
  }

  test("Probe B changing Fold stays near-linear through 16000 groups") {
    val x = SpaceMention("x"); val a = PathRef("a"); val h = PathRef("h"); val t = SpaceMention("t")
    val source = Space.Fold(Space.Mention(x), Path.ZERO, a, h, t,
      Space.Singleton(Path.Concat(Path.Deref(a), Path.Deref(h))), Path.Deref(h))
    def median(f: () => Any): Double =
      (0 until 3).foreach(_ => f())
      Vector.fill(5) { val start = System.nanoTime(); f(); (System.nanoTime()-start).toDouble / 1e6 }.sorted.apply(2)
    val samples = scala.collection.mutable.Map.empty[(String, Int), Double]
    for n <- Vector(4000, 16000) do
      val it = new PathItemInterner
      val raw = SpaceValue((0 until n).map(i => p(f"s_$i%06d")).toSet)
      raw.paths.foreach(it.encode)
      def original() = morkl.eval(source)(using PathContext.emptyMap, SpaceContextMap(Map(x -> raw)), PartialFunction.empty)
      val expected = original()
      val baseline = median(() => original())
      def check[B](name: String, code: WordEncoding[PathValue, B]): Unit =
        val program = code.compile(SymbolWordLanguage.space(source))
        val data = Map(x -> code.encodeSpace(WordSpaceValue(raw.paths)))
        def run() = program.eval(spaces = data)(using code.target)
        assertEquals(code.decodeSpace(run()).paths, expected.paths)
        val time = median(() => run())
        samples((name, n)) = time
        println(f"Probe B/$name/$n $time%.3f ms source=$baseline%.3f ratio=${time/baseline}%.2f")
        assert(time <= 20 * baseline, s"$name/$n exceeds 20x source: $time / $baseline")
      check("dictionary4", WordEncodings.sortedDictionaryBytes(it))
      check("dictionaryFib", WordEncodings.sortedDictionaryBits(it))
      check("CESU8", WordEncodings.stringSymbolsToBytes(it))
    for name <- Vector("dictionary4", "dictionaryFib", "CESU8") do
      val exponent = math.log(samples((name,16000)) / samples((name,4000))) / math.log(4)
      assert(exponent <= 1.3, s"$name growth exponent $exponent")
  }

  test("CESU-8 Range stays within twice four-byte dictionary Range at 20000 paths") {
    val x = SpaceMention("x")
    val raw = WordSpaceValue((0 until 20000).map(i => p(f"s_$i%06d")).toSet)
    val it = new PathItemInterner; raw.paths.foreach(it.encode)
    val source = SymbolWordLanguage.space(Space.Range(Space.Mention(x), 0, 1))
    def measure(code: WordEncoding[PathValue, BytePathValue]): Double =
      val program = code.compile(source)
      assert(program.routines.isEmpty)
      val data = Map(x -> code.encodeSpace(raw))
      def run() = program.eval(spaces = data)(using ByteWords)
      assertEquals(code.decodeSpace(run()).paths, Set(p("s_000000")))
      (0 until 3).foreach(_ => run())
      Vector.fill(7) { val start = System.nanoTime(); run(); (System.nanoTime()-start).toDouble/1e6 }.sorted.apply(3)
    val fixed = measure(WordEncodings.sortedDictionaryBytes(it))
    val strings = measure(WordEncodings.stringSymbolsToBytes(it))
    println(f"Probe E/20000 dictionary=$fixed%.3f ms CESU8=$strings%.3f ms ratio=${strings/fixed}%.2f")
    assert(strings <= 2 * fixed, s"CESU8 Range $strings exceeds twice dictionary Range $fixed")
  }
