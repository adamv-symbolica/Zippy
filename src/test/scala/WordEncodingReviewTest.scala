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
    assertEquals(bp.routines.size, 3) // Two open scanner states and one lifted body.
    val bounded = WordEncodings.internedSymbolsToBits(it, maxAdmissibleId = Some(10000))
    val dag = bounded.compile(source)
    assert(dag.routines.size > 10 && dag.routines.size <= 2 * bounded.maxCodeLength.get + 1)
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
    // Order-free Fold remains available in the open-world codecs.
    val a = PathRef("acc")
    val fold = SymbolWordLanguage.space(Space.Fold(Space.Mention(x), Path.ZERO, a, h, t,
      Space.Singleton(Path.Deref(h)), Path.Deref(a)))
    assertEquals(bits.decodeSpace(bits.compile(fold).eval(spaces = Map(x -> bits.encodeSpace(value)))(using BitWords)), value)
    assertEquals(bytes.decodeSpace(bytes.compile(fold).eval(spaces = Map(x -> bytes.encodeSpace(value)))(using ByteWords)), value)
  }

  test("byte-trie masks, focus and native algebra agree with the set reference") {
    val values = ByteSpaceValue((0 until 256).map(i => ByteWords.fromDigits(Vector(i, 255-i))).toSet + ByteWords.empty)
    val trie = ByteTrie.fromPaths(values.paths)
    assertEquals(trie.children.keysIterator.foldLeft(BigInt(0))(_.setBit(_)), (BigInt(1) << 256) - 1)
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

  test("frozen literal-ID vocabularies support every ordered form without changing IDs") {
    val it = new PathItemInterner
    val vocabulary = Vector("z", "b", "a").map(PathItem(_)); vocabulary.foreach(it.intern)
    val before = WordEncodings.internedSymbolsToBytes(it).encode(p("b"))
    val x = SpaceMention("x"); val a = PathRef("a"); val h = PathRef("h"); val t = SpaceMention("t")
    val raw = SpaceValue(vocabulary.map(v => PathValue(List(v))).toSet)
    val literal = Space.Literal(raw)
    val r1 = RoutinePtr("ordered1"); val r2 = RoutinePtr("ordered2"); val formal = SpaceMention("formal")
    val defs = Map(
      r1 -> Routine(r1, Vector.empty, Vector(formal), Space.Call(r2, Vector.empty, Vector(Space.Mention(formal)))),
      r2 -> Routine(r2, Vector.empty, Vector(formal), Space.Iteration(Space.Range(Space.Mention(formal), 0, 1), h, t,
        Space.Union(Space.Singleton(Path.Deref(h)), Space.Call(r1, Vector.empty, Vector(Space.Mention(t)))))))
    val expressions = Vector(
      Space.Range(literal, 0, 1), Space.Range(Space.Mention(x), -2, 0), Space.Range(Space.Mention(x), 0, -1),
      Space.Fold(Space.Mention(x), Path.ZERO, a, h, t, Space.Singleton(Path.Concat(Path.Deref(a), Path.Deref(h))), Path.Deref(h)),
      Space.Singleton(Path.GroundedSP(Space.Range(Space.Mention(x), 0, 1), v => v.paths.head)),
      Space.Call(r1, Vector.empty, Vector(Space.Mention(x))),
      Space.Fold(Space.Mention(x), Path.ZERO, h, h, t, Space.Singleton(Path.Deref(h)), Path.Deref(h)))
    def check[B](code: WordEncoding[PathValue, B]): Unit =
      val definitions = defs.view.mapValues(SymbolWordLanguage.routine).toMap
      expressions.foreach { source =>
        val expected = morkl.eval(source)(using PathContext.emptyMap, SpaceContextMap(Map(x -> raw)), defs)
        val program = code.compile(SymbolWordLanguage.space(source), definitions)
        val actual = program.eval(spaces = Map(x -> code.encodeSpace(WordSpaceValue(raw.paths))), validateHints = true)(using code.target)
        assertEquals(code.decodeSpace(actual).paths, expected.paths)
      }
      val count = it.size
      intercept[IllegalArgumentException](code.encode(p("future")))
      intercept[IllegalArgumentException](code.compile(SymbolWordLanguage.space(Space.Singleton(Path.Constant(p("future"))))))
      assertEquals(it.size, count)
    check(WordEncodings.internedSymbolsToBits(it, Some(vocabulary)))
    check(WordEncodings.internedSymbolsToBytes(it, Some(vocabulary)))
    it.intern(PathItem("unrelated"))
    assertEquals(WordEncodings.internedSymbolsToBytes(it).encode(p("b")), before)
    val bounded = WordEncodings.internedSymbolsToBits(it, Some(vocabulary))
    assertEquals(bounded.maxCodeLength, Some(FibonacciCode.encode(2).size))
  }

  test("order-free open-ID rewrites retain source evaluation and ignored-accumulator updates") {
    val x = SpaceMention("x"); val h = PathRef("h"); val t = SpaceMention("t")
    val outer = PathRef("_")
    val raw = WordSpaceValue(Set(p("future"), p("b")))
    def check[B](code: WordEncoding[PathValue, B]): Unit =
      var initialized = 0; var evaluated = 0
      val input = Space.GroundedSS(Space.Mention(x), v => { evaluated += 1; v })
      val initial = Path.GroundedPP(Path.ZERO, v => { initialized += 1; v })
      val expr = Space.Fold(input, initial, PathRef("_"), h, t, Space.Singleton(Path.Deref(h)), Path.Deref(outer))
      val program = code.compile(SymbolWordLanguage.space(expr))
      val data = Map(x -> code.encodeSpace(raw))
      val actual = program.eval(paths = Map(outer -> code.encode(p("b"))), spaces = data)(using code.target)
      assertEquals(code.decodeSpace(actual), raw)
      assertEquals(initialized, 1); assertEquals(evaluated, 1)
      val all = code.compile(SymbolWordLanguage.space(Space.Range(input, 0, 0)))
      assertEquals(code.decodeSpace(all.eval(spaces = data)(using code.target)), raw)
      assertEquals(evaluated, 2)
      val empty = code.compile(SymbolWordLanguage.space(Space.Range(input, 2, 2)))
      assertEquals(code.decodeSpace(empty.eval(spaces = data)(using code.target)).paths, Set.empty[PathValue])
      assertEquals(evaluated, 3)
      val badInitial = Path.GroundedPP(Path.ZERO, _ => throw IllegalStateException("initializer must run"))
      val bad = Space.Fold(Space.Empty, badInitial, PathRef("_"), h, t, Space.Empty, Path.Deref(outer))
      intercept[IllegalStateException](code.compile(SymbolWordLanguage.space(bad)).eval(
        paths = Map(outer -> code.encode(p("b"))))(using code.target))
    check(WordEncodings.internedSymbolsToBits(new PathItemInterner))
    check(WordEncodings.internedSymbolsToBytes(new PathItemInterner))
  }

  test("snapshot vocabularies are frozen and facade constant failures do not intern") {
    val it = new PathItemInterner
    val vocabulary = scala.collection.mutable.ArrayBuffer(PathItem("b"), PathItem("a"))
    val bits = WordEncodings.internedSymbolsToBits(it, Some(vocabulary))
    val old = bits.encode(p("b"))
    vocabulary += PathItem("future")
    val count = it.size
    intercept[IllegalArgumentException](bits.encode(p("future")))
    intercept[IllegalArgumentException](ZippyTranslations.symbolsToBits(
      Space.Singleton(Path.Constant(p("future"))), it, orderedVocabulary = Some(Vector(PathItem("a"), PathItem("b")))))
    assertEquals(it.size, count)
    it.intern(PathItem("unrelated"))
    assertEquals(bits.encode(p("b")), old)
    val explicit = WordEncodings.internedSymbolsToBits(it, maxAdmissibleId = Some(1))
    assertEquals(explicit.maxCodeLength, Some(FibonacciCode.encode(1).size))
  }

  test("ordered projected folds preserve grounded callback sequencing") {
    val it = new PathItemInterner
    val vocabulary = Vector("z", "b", "a").map(PathItem(_)); vocabulary.foreach(it.intern)
    val raw = SpaceValue(vocabulary.map(item => PathValue(List(item))).toSet)
    val h = PathRef("h"); val a = PathRef("a"); val t = SpaceMention("t")
    val events = scala.collection.mutable.ArrayBuffer.empty[String]
    val source = Space.Fold(Space.Literal(raw), Path.GroundedPP(Path.ZERO, v => { events += "initial"; v }), a, h, t,
      Space.GroundedSS(Space.Singleton(Path.Deref(h)), v => { events += s"body:${v.paths.head.items.head.show}"; v }),
      Path.GroundedPP(Path.Deref(h), v => { events += s"update:${v.items.head.show}"; v }))
    val expected = morkl.eval(source)
    val expectedEvents = events.toVector
    def check[B](code: WordEncoding[PathValue, B]): Unit =
      events.clear()
      val result = code.compile(SymbolWordLanguage.space(source)).eval()(using code.target)
      assertEquals(code.decodeSpace(result).paths, expected.paths)
      assertEquals(events.toVector, expectedEvents)
    check(WordEncodings.internedSymbolsToBits(it, Some(vocabulary)))
    check(WordEncodings.internedSymbolsToBytes(it, Some(vocabulary)))
    check(WordEncodings.stringSymbolsToBytes(it))
  }
