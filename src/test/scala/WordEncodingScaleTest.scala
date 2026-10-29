package morkl

import munit.FunSuite
import scala.concurrent.duration.*

class WordEncodingScaleTest extends FunSuite:
  override val munitTimeout = 180.seconds
  private val x = SpaceMention("x")
  private val h = PathRef("h")
  private val t = SpaceMention("t")
  private val a = PathRef("a")
  private def symbols(n: Int, tail: Boolean = false): (PathItemInterner, SpaceValue) =
    val interner = new PathItemInterner
    val values = SpaceValue((0 until n).map { i =>
      PathValue(PathItem(f"symbol_${n-i}%06d") :: (if tail then List(PathItem("z")) else Nil))
    }.toSet)
    values.paths.foreach(interner.encode)
    interner -> values
  private def timed[A](label: String)(body: => A): A =
    val start = System.nanoTime()
    val value = body
    val elapsed = (System.nanoTime() - start).toDouble / 1e9
    println(f"$label: $elapsed%.4f s")
    assert(elapsed < 30, s"$label exceeded reference evaluation budget: $elapsed s")
    value
  private def execute[B](code: WordEncoding[PathValue, B], expr: Space, value: SpaceValue): SpaceValue =
    val compiled = code.compile(SymbolWordLanguage.space(expr))
    val output = compiled.eval(spaces = Map(x -> code.encodeSpace(WordSpaceValue(value.paths))), validateHints = true)(using code.target)
    SpaceValue(code.decodeSpace(output).paths)

  test("ordered interned Range scales through 20000 paths with no recoding routines") {
    val expr = Space.Range(Space.Mention(x), 0, 1)
    for n <- Vector(1000, 4000, 20000) do
      val (interner, input) = symbols(n, tail = true)
      val expected = morkl.eval(expr)(using PathContext.emptyMap, SpaceContextMap(Map(x -> input)), PartialFunction.empty)
      def check[B](label: String, code: WordEncoding[PathValue, B]): Unit =
        val program = code.compile(SymbolWordLanguage.space(expr))
        assert(program.routines.isEmpty)
        val encoded = code.encodeSpace(WordSpaceValue(input.paths))
        val result = timed(s"Range/$label/$n")(program.eval(spaces = Map(x -> encoded), validateHints = true)(using code.target))
        assertEquals(code.decodeSpace(result).paths, expected.paths)
      check("fib", WordEncodings.internedSymbolsToBits(interner))
      check("intern4", WordEncodings.internedSymbolsToBytes(interner))
  }

  test("invariant Fold scales through 10000 groups and preserves grouped rest spaces") {
    val expr = Space.Fold(Space.Mention(x), Path.ZERO, a, h, t,
      Space.Wrap(Space.Mention(t), Path.Deref(h)), Path.Deref(a))
    for n <- Vector(50, 200, 800, 1000, 10000) do
      val (interner, input) = symbols(n, tail = true)
      timed(s"Fold/intern4/$n") {
        assertEquals(execute(WordEncodings.internedSymbolsToBytes(interner), expr, input), input)
      }
      timed(s"Fold/utf8/$n") {
        assertEquals(execute(WordEncodings.stringSymbolsToBytes(interner), expr, input), input)
      }
  }

  test("sequenced Fold remains stack safe with a changing accumulator") {
    val (interner, input) = symbols(1000)
    val expr = Space.Fold(Space.Mention(x), Path.ZERO, a, h, t,
      Space.Singleton(Path.Deref(h)), Path.Concat(Path.Deref(a), Path.Deref(h)))
    timed("Fold/changing/intern4/1000") {
      assertEquals(execute(WordEncodings.internedSymbolsToBytes(interner), expr, input), input)
    }
  }

  test("nested tails intersection evaluates its arbitrary input once at every depth") {
    val raw = SpaceValue((0 until 128).map(i => PathValue(List.tabulate(7)(bit =>
      PathItem(if ((i >>> bit) & 1) == 0 then "a" else "b")))).toSet)
    val interner = new PathItemInterner; raw.paths.foreach(interner.encode)
    val code = WordEncodings.internedSymbolsToBits(interner)
    for depth <- 1 to 6 do
      var evaluations = 0
      val input = Space.GroundedSS(Space.Literal(raw), v => { evaluations += 1; v })
      val expr = (0 until depth).foldLeft(input: Space)((s, _) => Space.TailsIntersection(s))
      val program = code.compile(SymbolWordLanguage.space(expr))
      val result = timed(s"TailsIntersection/depth=$depth")(program.eval(validateHints = true)(using BitWords))
      assertEquals(result.paths.size, 1 << (7 - depth))
      assertEquals(evaluations, 1, s"input duplication at depth $depth")
      // Also run a pure literal path so callback instrumentation cannot hide a fast path.
      execute(code, (0 until depth).foldLeft(Space.Literal(raw): Space)((s, _) => Space.TailsIntersection(s)), raw)
  }

  test("scanner calls grow linearly in codeword length and small alphabets inline by default") {
    val (interner, input) = symbols(16)
    val code = WordEncodings.internedSymbolsToBits(interner)
    val expr = SymbolWordLanguage.space(Space.Iteration(Space.Mention(x), h, t, Space.Singleton(Path.Deref(h))))
    val recursive = code.compile(expr, unrollBounded = false)
    val inlined = code.compile(expr)
    assert(inlined.routines.isEmpty)
    for value <- input.paths do
      val stats = new WordEvaluationStats
      val encoded = code.encode(value)
      val output = recursive.eval(spaces = Map(x -> WordSpaceValue(Set(encoded))), stats = Some(stats), validateHints = true)(using BitWords)
      assertEquals(output.paths, Set(encoded))
      assert(stats.calls <= BitWords.length(encoded) + 2, s"${stats.calls} calls for ${BitWords.length(encoded)} digits")
  }

  test("array values remain stable as keys of hashed sets") {
    val arrays = Vector.tabulate(24)(i => Array(i.toByte, 0.toByte, 255.toByte))
    val keys = arrays.map(BytePathValue(_))
    val set = keys.toSet
    arrays.foreach(bytes => bytes(0) = 99)
    keys.foreach { key =>
      val copy = key.toArray; copy(0) = 88
      assert(set(key))
      assert(set(BytePathValue(key.toArray)))
    }
    assertEquals(set.size, 24)
  }

  test("native word operations agree with digit semantics across boundary lengths") {
    val rng = new scala.util.Random(42)
    for _ <- 0 until 2000 do
      val left = Vector.fill(rng.nextInt(80))(rng.nextInt(256))
      val right = Vector.fill(rng.nextInt(80))(rng.nextInt(256))
      val p = ByteWords.fromDigits(left); val q = ByteWords.fromDigits(right)
      val expected = Ordering.Iterable[Int].compare(left, right).sign
      assertEquals(ByteWords.compare(p, q).sign, expected)
      assertEquals(ByteWords.digits(ByteWords.concat(p, q)), left ++ right)
      assertEquals(ByteWords.startsWith(p, q), left.startsWith(right))
      val bitsA = left.map(_ & 1); val bitsB = right.map(_ & 1)
      assertEquals(BitWords.compare(BitWords.fromDigits(bitsA), BitWords.fromDigits(bitsB)).sign,
        Ordering.Iterable[Int].compare(bitsA, bitsB).sign)
  }

  test("annotation checking rejects false exact and upper bounds") {
    val exact = PathRef("contract_exact").known(1)
    val upper = PathRef("contract_upper").bounded(2)
    intercept[IllegalArgumentException] {
      WordProgram(BitSpace.Singleton(BitPath.Deref(exact)), Map.empty).eval(paths = Map(exact -> BitWords.empty), validateHints = true)(using BitWords)
    }
    intercept[IllegalArgumentException] {
      WordProgram(BitSpace.Singleton(BitPath.Deref(upper)), Map.empty).eval(paths = Map(upper -> BitWords.fromDigits(Vector(0, 0, 0))), validateHints = true)(using BitWords)
    }
  }

  test("ignored iteration and fold binders do not falsify an outer path contract") {
    val ignored = PathRef("_").known(2)
    val outer = PathValue(List(PathItem("a"), PathItem("b")))
    val input = Space.Literal(SpaceValue(Set(PathValue(List(PathItem("a"))), PathValue(List(PathItem("b"))))))
    val expressions = Vector(
      Space.Iteration(input, PathRef("_"), SpaceMention("_"), Space.Singleton(Path.Deref(ignored))),
      Space.Fold(input, Path.ZERO, PathRef("_"), PathRef("_"), SpaceMention("_"), Space.Singleton(Path.Deref(ignored)), Path.Deref(ignored)))
    expressions.foreach { expr =>
      val (code, program) = ZippyTranslations.symbolsToBits(expr)
      val actual = program.eval(paths = Map(ignored -> code.encode(outer)), validateHints = true)(using BitWords)
      assertEquals(code.decodeSpace(actual).paths, Set(outer))
    }
  }
