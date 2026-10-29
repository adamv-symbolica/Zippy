package morkl

/** Reproducible review probes. Compilation and encoding happen outside timed
  * evaluation regions; report the median of five runs after two warmups.
  */
object WordEncodingBenchmarks:
  private val x = SpaceMention("x")
  private val h = PathRef("h")
  private val t = SpaceMention("t")
  private val a = PathRef("a")
  private def measure(label: String)(body: => Any): Double =
    for _ <- 0 until 2 do body
    val samples = Vector.fill(5) {
      val start = System.nanoTime(); body
      (System.nanoTime() - start).toDouble / 1e6
    }.sorted
    val median = samples(2)
    println(f"$label,$median%.4f")
    median
  private def input(n: Int, tails: Boolean = false): (PathItemInterner, SpaceValue) =
    val interner = new PathItemInterner
    val values = SpaceValue((0 until n).map { i =>
      PathValue(List(PathItem(f"s_$i%06d")) ++ (if tails then List(PathItem("z")) else Nil))
    }.toSet)
    values.paths.foreach(interner.encode)
    interner -> values
  private def coded[B](label: String, code: WordEncoding[PathValue, B], expression: Space, values: SpaceValue,
                       unroll: Boolean = true): Double =
    val program = code.compile(SymbolWordLanguage.space(expression), unrollBounded = unroll)
    val encoded = code.encodeSpace(WordSpaceValue(values.paths))
    measure(label)(program.eval(spaces = Map(x -> encoded))(using code.target))

  @main def wordEncodingReviewReport(): Unit =
    println("probe,median_ms")
    val iteration = Space.Iteration(Space.Mention(x), h, t, Space.Singleton(Path.Deref(h)))
    for n <- Vector(16, 4000) do
      val (interner, values) = input(n)
      val code = WordEncodings.internedSymbolsToBits(interner)
      coded(s"E1/fib/recursive/$n", code, iteration, values, unroll = false)
      coded(s"E1/fib/automatic/$n", code, iteration, values)
    val range = Space.Range(Space.Mention(x), 0, 1)
    for n <- Vector(1000, 4000, 20000) do
      val (interner, values) = input(n, tails = true)
      measure(s"E2/symbol/$n")(morkl.eval(range)(using PathContext.emptyMap, SpaceContextMap(Map(x -> values)), PartialFunction.empty))
      coded(s"E2/fib/$n", WordEncodings.internedSymbolsToBits(interner), range, values)
      coded(s"E2/intern4/$n", WordEncodings.internedSymbolsToBytes(interner), range, values)
    val fold = Space.Fold(Space.Mention(x), Path.ZERO, a, h, t, Space.Singleton(Path.Deref(h)), Path.Deref(a))
    val growth = scala.collection.mutable.Map.empty[(String, Int), Double]
    for n <- Vector(50, 200, 800, 10000) do
      val (interner, values) = input(n)
      growth(("intern4", n)) = coded(s"E3/intern4/$n", WordEncodings.internedSymbolsToBytes(interner), fold, values)
      growth(("utf8", n)) = coded(s"E3/utf8/$n", WordEncodings.stringSymbolsToBytes(interner), fold, values)
    for name <- Vector("intern4", "utf8") do
      val exponent = math.log(growth((name, 800)) / growth((name, 200))) / math.log(4)
      println(f"E3/growth_exponent/$name,$exponent%.4f")
    val raw = SpaceValue((0 until 128).map(i => PathValue(List.tabulate(7)(bit => PathItem(if ((i >>> bit) & 1) == 0 then "a" else "b")))).toSet)
    val interner = new PathItemInterner; raw.paths.foreach(interner.encode)
    for depth <- 1 to 6 do
      var evaluations = 0
      val source = Space.GroundedSS(Space.Literal(raw), value => { evaluations += 1; value })
      val expr = (0 until depth).foldLeft(source: Space)((s, _) => Space.TailsIntersection(s))
      coded(s"E4/TI/$depth", WordEncodings.internedSymbolsToBits(interner), expr, raw)
      println(s"E4/source_evaluations/$depth,$evaluations") // Seven runs: exactly seven evaluations.
    val rng = new scala.util.Random(7)
    val bytes = ByteSpaceValue((0 until 20000).map(_ => BytePathValue(rng.nextBytes(32))).toSet)
    measure("E5/byte_range/20000x32")(Zippy_bytes.eval(ByteSpace.Range(ByteSpace.Literal(bytes), 0, 1)))
    val left = ByteSpace.Literal(ByteSpaceValue(bytes.paths.take(300)))
    val right = ByteSpace.Literal(ByteSpaceValue(bytes.paths.drop(300).take(300)))
    measure("E5/composition/300x300")(Zippy_bytes.eval(ByteSpace.Composition(left, right)))
    // ASCII has the same UTF-8/UTF-16 order, so disabling repair is a valid
    // measurement control here. This is not safe for arbitrary Unicode input.
    val (asciiInterner, ascii) = input(20000)
    val strings = WordEncodings.stringSymbolsToBytes(asciiInterner)
    coded("E5/ASCII_range/UTF16_repair", strings, range, ascii)
    coded("E5/ASCII_range/native_control", strings.copy(preservesOrder = true), range, ascii)
