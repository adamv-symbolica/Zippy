package morkl

import munit.FunSuite

class RadixLanguageTest extends FunSuite:
  private def pv(items: String*): PathValue = PathValue(items.map(PathItem(_)).toList)
  private val alphabet = Vector("z", "a", "m", "", "tail")
  private def interner(): PathItemInterner =
    val result = PathItemInterner()
    alphabet.foreach(s => result.intern(PathItem(s)))
    result
  private val values = SpaceValue(Set(pv(), pv("z"), pv("z", "tail"), pv("a", "tail"), pv("m", "a"), pv("", "tail")))
  private val src = Space.Literal(values)
  private val h = PathRef("h")
  private val t = SpaceMention("t")
  private val acc = PathRef("acc")
  private val input = SpaceMention("input")
  private val identity = RoutinePtr("identity")
  private val identityRoutine = Routine(identity, Vector.empty, Vector(input), Space.Mention(input))
  private val routines = Map(identity -> identityRoutine)
  private val programs: Vector[Space] =
    val other = Space.Literal(SpaceValue(Set(pv("a"), pv("z"), pv())))
    val p = Path.Constant(pv("z"))
    Vector(
      Space.Empty, Space.Singleton(Path.Concat(p, Path.Constant(pv("a")))), src,
      Space.Call(identity, Vector.empty, Vector(src)),
      Space.Union(src, other), Space.Intersection(src, other), Space.Subtraction(src, other),
      Space.Restriction(src, other), Space.Raffination(src, other), Space.Composition(other, src),
      Space.Wrap(src, p), Space.Unwrap(src, p), Space.TailsUnion(src), Space.TailsIntersection(src),
      Space.PrefixClosure(src), Space.SuffixClosure(src), Space.TailsClosure(src),
      Space.Iteration(src, h, t, Space.Wrap(Space.Mention(t), Path.Deref(h))),
      Space.Iteration(src, h, t, Space.Union(Space.Singleton(Path.Deref(h)), Space.Mention(t))),
      Space.Fold(src, Path.Constant(pv()), acc, h, t,
        Space.Singleton(Path.Concat(Path.Deref(acc), Path.Deref(h))), Path.Concat(Path.Deref(acc), Path.Deref(h))),
      Space.Fixpoint(src, input, Space.Union(Space.Mention(input), Space.TailsUnion(Space.Mention(input)))),
      Space.GroundedPS(p, v => SpaceValue(Set(v))), Space.GroundedSS(src, v => v),
      Space.Singleton(Path.GroundedPP(p, v => v)),
      Space.Singleton(Path.GroundedSP(src, _ => pv("m")))
    ) ++ Vector((0, 1), (1, 3), (2, 4), (-2, 0), (0, -1), (-4, -1), (-3, 3), (5, 2),
      (0, 0), (Int.MinValue, Int.MaxValue), (Int.MaxValue, 0)).map((a, b) => Space.Range(src, a, b))

  test("symbol translations preserve every case, symbol boundaries and source ordering") {
    for (program, i) <- programs.zipWithIndex do
      val expected = eval(program)(using rc = routines)
      val bits = RadixTranslations.symbolsToBits(program, routines, interner())
      assertEquals(bits.decode(bits.program.eval()), expected, s"Fibonacci case $i")
      val bytes = RadixTranslations.internedSymbolsToBytes(program, routines, interner())
      assertEquals(bytes.decode(bytes.program.eval()), expected, s"interned bytes case $i")
      val strings = RadixTranslations.stringsToBytes(program, routines)
      assertEquals(strings.decode(strings.program.eval()), expected, s"strings case $i")
  }

  private def digitPrograms[V](lang: DigitLanguage[V]): Vector[lang.Space] =
    import lang.{Path as P, Space as E}
    val v = Vector(Vector.empty[Int], Vector(0), Vector(1, 0), Vector(0, 1), Vector(1, 1, 0))
    val src = lang.literal(v.map(lang.paths.fromDigits)*)
    val other = lang.literal(lang.paths.fromDigits(Vector(0)), lang.paths.empty)
    val a = PathRef("acc")
    val h = PathRef("head")
    val t = SpaceMention("tail")
    val f = SpaceMention("fix")
    Vector(E.Empty, src, E.Singleton(P.Concat(lang.digit(0), lang.digit(1))),
      E.Union(src, other), E.Intersection(src, other), E.Subtraction(src, other), E.Restriction(src, other),
      E.Raffination(src, other), E.Composition(src, other), E.Wrap(src, lang.digit(1)), E.Unwrap(src, lang.digit(0)),
      E.TailsUnion(src), E.TailsIntersection(src), E.PrefixClosure(src), E.SuffixClosure(src), E.TailsClosure(src),
      E.Iteration(src, h, t, E.Wrap(E.Mention(t), P.Deref(h))),
      E.Fold(src, P.ZERO, a, h, t, E.Singleton(P.Concat(P.Deref(a), P.Deref(h))), P.Concat(P.Deref(a), P.Deref(h))),
      E.Fixpoint(src, f, E.TailsUnion(E.Mention(f))),
      E.GroundedPS(lang.digit(0), v => lang.SpaceValue(Set(v))), E.GroundedSS(src, v => v),
      E.Singleton(P.GroundedPP(lang.digit(1), v => v)),
      E.Singleton(P.GroundedSP(src, _ => lang.paths.empty)),
      E.Range(src, 1, 4), E.Range(src, -2, 0), E.Range(src, 0, -1))

  test("bit/byte and radix/symbol translations preserve every operation") {
    for (program, i) <- digitPrograms(Zippy_bits).zipWithIndex do
      val expected = Zippy_bits.eval(program).paths
      val bytes = RadixTranslations.bitsToBytes(program).eval()
      assertEquals(bytes.paths.map(PathEncodings.bitsToBytes.decode), expected, s"bit to byte $i")
      val symbols = RadixTranslations.bitsToSymbols(program).eval()
      assertEquals(symbols.paths.map(PathEncodings.bitsToSymbols.decode), expected, s"bit to symbol $i")
    for (program, i) <- digitPrograms(Zippy_bytes).zipWithIndex do
      val expected = Zippy_bytes.eval(program).paths
      val bits = RadixTranslations.bytesToBits(program).eval()
      assertEquals(bits.paths.map(PathEncodings.bytesToBits.decode), expected, s"byte to bit $i")
      val symbols = RadixTranslations.bytesToSymbols(program).eval()
      assertEquals(symbols.paths.map(PathEncodings.bytesToSymbols.decode), expected, s"byte to symbol $i")
  }

  test("BigInt sentinel, immutable arrays, unsigned bytes, bounded heads and byte zipper") {
    for n <- 0 to 80 do
      val digits = Vector.fill(n)(0) ++ Vector(1, 0, 1)
      val p = BitPaths.fromDigits(digits)
      assertEquals(BitPaths.digits(p), digits)
      for k <- 0 to digits.length do
        assertEquals(BitPaths.concat(BitPaths.take(p, k), BitPaths.drop(p, k)), p)
    val array = Array[Byte](0, -1)
    val p = BytePathValue(array)
    array(0) = 1
    p.bytes(0) = 2
    assertEquals(p, BytePathValue(0, 255))
    val all = Zippy_bytes.literal((0 until 256).map(i => BytePathValue(i, 0))*)
    val heads = Zippy_bytes.eval(ByteSpace.Head(all))
    assertEquals(heads.paths.size, 256)
    assertEquals(Zippy_bytes.headMask(heads), (BigInt(1) << 256) - 1)
    assertEquals(Zippy_bits.eval(BitSpace.Head(Zippy_bits.literal(BigInt(1), BigInt(2), BigInt(3)))).paths, Set(BigInt(2), BigInt(3)))
    val ordered = Zippy_bytes.eval(ByteSpace.Range(all, 0, 1))
    assertEquals(ordered.paths, Set(BytePathValue(0, 0)))
    val zip = ByteZipper(ByteSpaceValue(Set(BytePathValue(7, 0), BytePathValue(7, 255))), root = BytePathValue(7))
    assertEquals(zip.childMask, BigInt(1).setBit(255))
    val (missing, moved) = zip.descendTo(BytePathValue(4, 5)).ascend(1)
    assertEquals(moved, 1)
    assert(!missing.pathExists)
    assertEquals(missing.ascend(10)._1, zip)
  }

  test("codecs round trip zeroes, empty symbols, all bytes, all JVM strings and extreme intern IDs") {
    assertEquals(FibonacciCode.encode(1), Vector(1, 1))
    assertEquals(FibonacciCode.encode(2), Vector(0, 1, 1))
    assertEquals(FibonacciCode.encode(3), Vector(0, 0, 1, 1))
    val nums = (1 to 1000).map(BigInt(_)).toVector :+ (BigInt(Int.MaxValue) + 1)
    assertEquals(FibonacciCode.decode(nums.flatMap(FibonacciCode.encode)), nums)
    intercept[IllegalArgumentException](FibonacciCode.decode(Vector(1, 0)))
    val text = Vector("", "a", "a" + 0.toChar, 0.toChar.toString, 1.toChar.toString, "é", "😀", 0xd800.toChar.toString)
    val path = pv(text*)
    assertEquals(PathEncodings.strings.decode(PathEncodings.strings.encode(path)), path)
    val sorted = text.sortWith(_.compareTo(_) < 0)
    val encodedSorted = text.sortWith((a, b) => BytePaths.compare(PathEncodings.strings.encode(pv(a)), PathEncodings.strings.encode(pv(b))) < 0)
    assertEquals(encodedSorted, sorted)
    val bytes = BytePaths.fromDigits(0 until 256)
    assertEquals(PathEncodings.bytesToBits.decode(PathEncodings.bytesToBits.encode(bytes)), bytes)
    intercept[IllegalArgumentException](PathEncodings.bytesToBits.decode(BigInt(2)))
    intercept[IllegalArgumentException](PathEncodings.bitsToBytes.decode(BytePathValue(2)))
  }

  test("open programs capture lexical environments; calls and mutual recursion survive lowering") {
    val p = PathRef("__zippy_symbol_0").known(1)
    val s = SpaceMention("s")
    val walk = RoutinePtr("walk")
    val helper = RoutinePtr("helper")
    val h = PathRef("h")
    val t = SpaceMention("t")
    val walkRoutine = Routine(walk, Vector(p), Vector(s), Space.Union(Space.Singleton(Path.Deref(p)),
      Space.Iteration(Space.Mention(s), h, t, Space.Call(helper, Vector(Path.Deref(h)), Vector(Space.Mention(t))))))
    val helperRoutine = Routine(helper, Vector(p), Vector(s), Space.Call(walk, Vector(Path.Deref(p)), Vector(Space.Mention(s))))
    val defs = Map(walk -> walkRoutine, helper -> helperRoutine)
    val entry = Space.Call(walk, Vector(Path.Deref(p)), Vector(Space.Mention(s)))
    val pc = PathContextMap(Map(p -> pv("m")))
    val sc = SpaceContextMap(Map(s -> values))
    val expected = eval(entry)(using pc, sc, defs)
    val b = RadixTranslations.symbolsToBits(entry, defs, interner())
    assertEquals(b.decode(b.program.eval(Map(p -> b.encoding.encode(pv("m"))), Map(s -> b.encode(values)))), expected)
    val y = RadixTranslations.internedSymbolsToBytes(entry, defs, interner())
    assertEquals(y.decode(y.program.eval(Map(p -> y.encoding.encode(pv("m"))), Map(s -> y.encode(values)))), expected)
    val z = RadixTranslations.stringsToBytes(entry, defs)
    assertEquals(z.decode(z.program.eval(Map(p -> z.encoding.encode(pv("m"))), Map(s -> z.encode(values)))), expected)
    assert(b.program.routines(walk).refs.head.lengthHint == -1)
    assert(b.program.routines(walk).refs.head.maxLengthHint == b.encoding.maxCodeLength.get)
    assertEquals(y.program.routines(walk).refs.head.lengthHint, 4)
    val estimate = ResultPathLength.estimateBaseline(Space.Singleton(Path.Deref(PathRef("bounded").bounded(46))))
    assertEquals(estimate.upper.annotatedValue, Some(BigInt(46)))
    assert(!estimate.exact)
  }

  test("lowered callback-free programs contain no grounded nodes and injections use only their alphabet") {
    for program <- programs.take(21) do
      val bit = RadixTranslations.symbolsToBits(program, routines, interner())
      val symbols = RadixTranslations.bitsToSymbols(bit.program.entry, bit.program.routines)
      val all = symbols.entry +: symbols.routines.values.map(_.body).toVector
      all.foreach { body =>
        val (ss, ps) = collect(body)(
          { case Space.GroundedPS(_, _) | Space.GroundedSS(_, _) => () },
          { case Path.GroundedPP(_, _) | Path.GroundedSP(_, _) => () })
        assert(ss.isEmpty && ps.isEmpty)
        val (literals, paths) = collect(body)(
          { case Space.Literal(v) => v.paths.flatMap(_.items).map(_.show) },
          { case Path.Constant(v) => v.items.map(_.show).toSet })
        assert((literals.flatMap(_._2) ++ paths.flatMap(_._2)).forall(Set("false", "true")))
      }
  }

  test("empty and epsilon spaces, all range endpoints and random path sets commute with encoding") {
    val rng = new scala.util.Random(71291L)
    val spaces = Vector(SpaceValue(Set.empty), SpaceValue(Set(pv())), values) ++ Vector.fill(24) {
      SpaceValue(Vector.fill(rng.nextInt(9)) {
        pv(Vector.fill(rng.nextInt(4))(alphabet(rng.nextInt(alphabet.size)))*)
      }.toSet)
    }
    for value <- spaces do
      val literal = Space.Literal(value)
      val operations = Vector[Space](Space.TailsUnion(literal), Space.TailsIntersection(literal),
        Space.PrefixClosure(literal), Space.SuffixClosure(literal), Space.TailsClosure(literal)) ++
        Vector.fill(5) { Space.Range(literal, rng.nextInt(17) - 8, rng.nextInt(17) - 8) }
      for op <- operations do
        val expected = eval(op)
        val bits = RadixTranslations.symbolsToBits(op, interner = interner())
        assertEquals(bits.decode(bits.program.eval()), expected)
        val bytes = RadixTranslations.internedSymbolsToBytes(op, interner = interner())
        assertEquals(bytes.decode(bytes.program.eval()), expected)
    for start <- -7 to 7; end <- -7 to 7 do
      val op = Space.Range(src, start, end)
      val translated = RadixTranslations.symbolsToBits(op, interner = interner())
      assertEquals(translated.decode(translated.program.eval()), eval(op), s"range($start,$end)")
  }

  test("every byte participates in grouped iteration and symbol-ordered folds after eight-bit lowering") {
    val src = Zippy_bytes.literal((0 until 256).flatMap(i => Vector(BytePathValue(i), BytePathValue(i, 255 - i)))*)
    val h = PathRef("h")
    val t = SpaceMention("t")
    val a = PathRef("acc")
    val ops = Vector(ByteSpace.Head(src), ByteSpace.TailsIntersection(src),
      ByteSpace.Iteration(src, h, t, ByteSpace.Wrap(ByteSpace.Mention(t), BytePath.Deref(h))),
      ByteSpace.Fold(src, BytePath.ZERO, a, h, t,
        ByteSpace.Singleton(BytePath.Concat(BytePath.Deref(a), BytePath.Deref(h))),
        BytePath.Concat(BytePath.Deref(a), BytePath.Deref(h))))
    for op <- ops do
      val expected = Zippy_bytes.eval(op).paths
      assertEquals(RadixTranslations.bytesToBits(op).eval().paths.map(PathEncodings.bytesToBits.decode), expected)
      assertEquals(RadixTranslations.bytesToSymbols(op).eval().paths.map(PathEncodings.bytesToSymbols.decode), expected)
  }

  test("byte lowering is eight digit iterations and Fibonacci scanners expose finite exact depths") {
    val h = PathRef("h")
    val t = SpaceMention("t")
    val lowered = RadixTranslations.bytesToBits(ByteSpace.Iteration(Zippy_bytes.literal(BytePathValue(0)), h, t,
      ByteSpace.Singleton(BytePath.Deref(h))))
    def depth(s: BitSpace): Int = s match
      case BitSpace.Iteration(_, _, _, body) => 1 + depth(body)
      case _ => 0
    assertEquals(depth(lowered.entry), 8)
    val fib = RadixTranslations.symbolsToBits(Space.Iteration(src, h, t, Space.Singleton(Path.Deref(h))), interner = interner())
    val scanners = fib.program.routines.values.filter(_.name.s.contains("fib_iter")).toVector
    assert(scanners.nonEmpty)
    assert(scanners.forall(r => r.refs.head.lengthHint >= 0 && r.refs.head.lengthHint < fib.encoding.maxCodeLength.get))
    val codeBody = fib.program.routines.values.find(_.name.s.contains("symbol_body")).get
    assertEquals(codeBody.refs.head.lengthHint, -1)
    assertEquals(codeBody.refs.head.maxLengthHint, fib.encoding.maxCodeLength.get)
  }

  test("nested shadowing and ignored binders preserve captured path and space values") {
    val outer = PathRef("outer").known(1)
    val ignored = PathRef("_")
    val ignoredSpace = SpaceMention("_")
    val nested = Space.Iteration(src, h, t, Space.Union(Space.Singleton(Path.Deref(h)),
      Space.Iteration(Space.Mention(t), h, t, Space.Wrap(Space.Mention(t),
        Path.Concat(Path.Deref(h), Path.Deref(outer))))))
    val skip = Space.Iteration(src, ignored, ignoredSpace,
      Space.Wrap(Space.Mention(ignoredSpace), Path.Deref(ignored)))
    val pc = PathContextMap(Map(outer -> pv("m"), ignored -> pv("a")))
    val sc = SpaceContextMap(Map(ignoredSpace -> values))
    for op <- Vector(nested, skip) do
      val expected = eval(op)(using pc, sc)
      val a = RadixTranslations.symbolsToBits(op, interner = interner())
      assertEquals(a.decode(a.program.eval(pc.m.view.mapValues(a.encoding.encode).toMap,
        sc.m.view.mapValues(a.encode).toMap)), expected)
      val b = RadixTranslations.stringsToBytes(op)
      assertEquals(b.decode(b.program.eval(pc.m.view.mapValues(b.encoding.encode).toMap,
        sc.m.view.mapValues(b.encode).toMap)), expected)
  }

  test("interned codecs freeze their alphabet and four-byte encoding crosses byte boundaries") {
    val table = PathItemInterner()
    val items = Vector.tabulate(300)(i => PathItem(s"item_$i"))
    items.foreach(table.intern)
    val fib = PathEncodings.fibonacci(table)
    val bytes = PathEncodings.internedBytes(table)
    val value = PathValue(items.toList)
    assertEquals(fib.decode(fib.encode(value)), value)
    assertEquals(bytes.decode(bytes.encode(value)), value)
    assertEquals(bytes.encode(PathValue(List(items(256)))), BytePathValue(0, 0, 1, 0))
    table.intern(PathItem("new"))
    intercept[IllegalArgumentException](fib.encode(pv("new")))
    intercept[IllegalArgumentException](bytes.encode(pv("new")))
    val op = Space.Range(Space.Literal(SpaceValue(Set(pv("item_299"), pv("item_0"), pv("item_2")))), 0, 1)
    val translated = RadixTranslations.internedSymbolsToBytes(op, interner = table)
    assertEquals(translated.decode(translated.program.eval()), eval(op))
  }

  test("empty and epsilon iteration never demand captures in the unused body") {
    val missing = Space.Mention(SpaceMention("not_bound"))
    for value <- Vector(SpaceValue(Set.empty), SpaceValue(Set(pv()))) do
      val op = Space.Iteration(Space.Literal(value), PathRef("h"), SpaceMention("t"), missing)
      assertEquals(eval(op), SpaceValue(Set.empty))
      val a = RadixTranslations.symbolsToBits(op)
      assertEquals(a.decode(a.program.eval()), SpaceValue(Set.empty))
      val b = RadixTranslations.stringsToBytes(op)
      assertEquals(b.decode(b.program.eval()), SpaceValue(Set.empty))
      val c = RadixTranslations.internedSymbolsToBytes(op)
      assertEquals(c.decode(c.program.eval()), SpaceValue(Set.empty))
  }

  test("deep paths and recursive target routines evaluate on a small JVM stack") {
    val failure = new java.util.concurrent.atomic.AtomicReference[Throwable]()
    val thread = new Thread(null, new Runnable:
      def run(): Unit =
        try
          val input = SpaceMention("input")
          val h = PathRef("digit")
          val t = SpaceMention("tail")
          val name = RoutinePtr("walk_deep_bits")
          val depth = 8192
          val path = BitPaths.fromDigits(Vector.fill(depth)(0))
          val routine = Zippy_bits.Routine(name, Vector.empty, Vector(input), BitSpace.Union(
            BitSpace.Intersection(BitSpace.Mention(input), Zippy_bits.epsilon),
            BitSpace.Iteration(BitSpace.Mention(input), h, t,
              BitSpace.Call(name, Vector.empty, Vector(BitSpace.Mention(t))))))
          val program = Zippy_bits.Program(BitSpace.Call(name, Vector.empty, Vector(Zippy_bits.literal(path))), Map(name -> routine))
          assertEquals(program.eval().paths, Set(BitPaths.empty))
          val concatenated = (0 until depth).foldLeft(BitPath.ZERO)((p, _) => BitPath.Concat(p, Zippy_bits.digit(0)))
          assertEquals(Zippy_bits.eval(BitSpace.Singleton(concatenated)).paths, Set(path))
        catch case error: Throwable => failure.set(error)
    , "radix-small-stack", 256 * 1024L)
    thread.start()
    thread.join()
    if failure.get() != null then throw failure.get()
  }

  test("ordered ranges share their source argument across scans and bounds") {
    val calls = new java.util.concurrent.atomic.AtomicInteger()
    val op = Space.Range(Space.GroundedSS(src, value => { calls.incrementAndGet(); value }), -3, -1)
    val expected = eval(op)
    val a = RadixTranslations.symbolsToBits(op, interner = interner())
    val b = RadixTranslations.internedSymbolsToBytes(op, interner = interner())
    val c = RadixTranslations.stringsToBytes(op)
    calls.set(0)
    assertEquals(a.decode(a.program.eval()), expected)
    assertEquals(calls.get(), 1)
    calls.set(0)
    assertEquals(b.decode(b.program.eval()), expected)
    assertEquals(calls.get(), 1)
    calls.set(0)
    assertEquals(c.decode(c.program.eval()), expected)
    assertEquals(calls.get(), 1)
  }

  test("encoded tails intersection evaluates its source once") {
    val calls = new java.util.concurrent.atomic.AtomicInteger()
    val op = Space.TailsIntersection(Space.GroundedSS(src,value=>{calls.incrementAndGet();value}))
    val expected = eval(op)
    val bit = RadixTranslations.symbolsToBits(op,interner=interner())
    val string = RadixTranslations.stringsToBytes(op)
    val interned = RadixTranslations.internedSymbolsToBytes(op,interner=interner())
    calls.set(0);assertEquals(bit.decode(bit.program.eval()),expected);assertEquals(calls.get(),1)
    calls.set(0);assertEquals(string.decode(string.program.eval()),expected);assertEquals(calls.get(),1)
    calls.set(0);assertEquals(interned.decode(interned.program.eval()),expected);assertEquals(calls.get(),1)
  }
