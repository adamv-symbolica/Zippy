package morkl

import munit.FunSuite

class FiniteAlphabetTest extends FunSuite:
  private def p(xs: String*): PathValue = PathValue(xs.toList.map(PathItem(_)))
  private val h = PathRef("head")
  private val t = SpaceMention("tail")
  private val a = PathRef("acc")
  private val x = SpaceMention("input")
  private val outer = PathRef("outer")
  private val source = SpaceValue(Set(p(), p("z"), p("z", "a"), p("a", "z"), p("a", "a"), p("é", "a"), p("", "z"), p("😀"), p("\uE000")))
  private val other = SpaceValue(Set(p("a"), p("z"), p("a", "z")))
  private val input = Space.Mention(x)
  private val one = Path.Constant(p("a"))
  private val epsilon = Path.Constant(p())
  private def lit(s: SpaceValue): Space = Space.Literal(s)

  private def expressions: Vector[Space] = Vector(
    Space.Empty, input, Space.Singleton(Path.Deref(outer)), lit(source),
    Space.Singleton(Path.Concat(Path.Deref(outer), one)),
    Space.Union(input, lit(other)), Space.Intersection(input, lit(other)),
    Space.Subtraction(input, lit(other)), Space.Restriction(input, lit(other)),
    Space.Raffination(input, lit(other)), Space.Composition(input, lit(other)),
    Space.Iteration(input, h, t, Space.Union(Space.Singleton(Path.Deref(h)), Space.Mention(t))),
    Space.Iteration(input, h, t, Space.Iteration(Space.Mention(t), h, t,
      Space.Singleton(Path.Concat(Path.Deref(outer), Path.Deref(h))))),
    Space.Fold(input, epsilon, a, h, t,
      Space.Singleton(Path.Concat(Path.Deref(a), Path.Deref(h))),
      Path.Concat(Path.Deref(a), Path.Deref(h))),
    Space.Fixpoint(input, t, Space.Union(Space.Mention(t), lit(other))),
    Space.Wrap(input, Path.Deref(outer)), Space.Unwrap(Space.Wrap(input, one), one),
    Space.TailsUnion(input), Space.TailsIntersection(input),
    Space.PrefixClosure(input), Space.SuffixClosure(input), Space.TailsClosure(input),
    Space.GroundedPS(Path.Deref(outer), v => SpaceValue(Set(v, p()))),
    Space.GroundedSS(input, v => SpaceValue(v.paths.map(q => PathValue(q.items.reverse)))),
    Space.Singleton(Path.GroundedPP(Path.Deref(outer), v => PathValue(v.items.reverse))),
    Space.Singleton(Path.GroundedSP(input, _ => p("é"))),
  ) ++ Vector((0, 1), (1, 3), (-3, 0), (0, -1), (-4, -1), (-4, 3), (0, 0), (5, 2)).map {
    (lo, hi) => Space.Range(input, lo, hi)
  }

  private def interner(): PathItemInterner =
    val i = new PathItemInterner
    // Deliberately different from both lexical order and Fibonacci-code order.
    (source.paths ++ other.paths + p("a", "z")).foreach(i.encode)
    i

  private def checkSymbols[B](code: WordEncoding[PathValue, B], unroll: Boolean): Unit =
    given WordOps[B] = code.target
    val pc = Map(outer -> p("a", "z"))
    val sc = Map(x -> source)
    expressions.zipWithIndex.foreach { (expr, index) =>
      val program = code.compile(SymbolWordLanguage.space(ReferenceHints.tag(expr)), unrollBounded = unroll)
      val expected = morkl.eval(expr)(using PathContextMap(pc), SpaceContextMap(sc), PartialFunction.empty)
      val actual = program.eval(pc.view.mapValues(code.encode).toMap,
        sc.view.mapValues(v => code.encodeSpace(WordSpaceValue(v.paths))).toMap)
      assertEquals(code.decodeSpace(actual).paths, expected.paths, s"constructor $index: ${expr.show}; unroll=$unroll")
    }

  test("all source constructs commute with Fibonacci, string and fixed intern encodings") {
    checkSymbols(WordEncodings.internedSymbolsToBits(interner()), false)
    checkSymbols(WordEncodings.internedSymbolsToBits(interner()), true)
    checkSymbols(WordEncodings.stringSymbolsToBytes(interner()), false)
    checkSymbols(WordEncodings.stringSymbolsToBytes(interner()), true)
    checkSymbols(WordEncodings.internedSymbolsToBytes(interner()), false)
  }

  private def nativeExpressions[P](ops: WordOps[P]): Vector[WordSpace[P]] =
    import WordSpace.*
    def c(ds: Int*): WordPath[P] = WordPath.Constant(ops.fromDigits(ds))
    val values = WordSpaceValue(Set(ops.empty, ops.fromDigits(Vector(0)), ops.fromDigits(Vector(1)),
      ops.fromDigits(Vector(1, 0)), ops.fromDigits(Vector(0, 1)), ops.fromDigits(Vector(0, 0, 1))))
    val src: WordSpace[P] = Literal(values)
    val other: WordSpace[P] = Singleton(c(0))
    val identity = RoutinePtr("native_identity")
    Vector(Empty(), Call(identity, Vector.empty, Vector(src)), src, Singleton(c()), Singleton(WordPath.Concat(c(0), c(1))),
      Union(src, other), Intersection(src, other), Subtraction(src, other), Restriction(src, other), Raffination(src, other),
      Composition(src, other), Iteration(src, h, t, Union(Singleton(WordPath.Deref(h)), Mention(t))),
      Fold(src, c(), a, h, t, Singleton(WordPath.Concat(WordPath.Deref(a), WordPath.Deref(h))), WordPath.Concat(WordPath.Deref(a), WordPath.Deref(h))),
      Fixpoint(src, t, Union(Mention(t), other)), Wrap(src, c(1)), Unwrap(src, c(0)),
      TailsUnion(src), TailsIntersection(src), PrefixClosure(src), SuffixClosure(src), TailsClosure(src),
      GroundedPS(c(1), v => WordSpaceValue(Set(v))), GroundedSS(src, v => v),
      Singleton(WordPath.GroundedPP(c(1), v => v)), Singleton(WordPath.GroundedSP(src, _ => ops.empty)),
      Range(src, 0, 3), Range(src, -3, 0), Range(src, 0, -1))

  private def checkNative[A, B](code: WordEncoding[A, B]): Unit =
    val identity = RoutinePtr("native_identity")
    val formal = SpaceMention("formal")
    val routines = Map(identity -> WordRoutine[A](identity, Vector.empty, Vector(formal), WordSpace.Mention(formal)))
    nativeExpressions(code.source).zipWithIndex.foreach { (expr, index) =>
      val expected = WordEvaluator.eval(expr, routines = routines)(using code.source)
      val program = code.compile(expr, routines)
      val actual = program.eval()(using code.target)
      assertEquals(code.decodeSpace(actual), expected, s"native constructor $index")
      // Pure input programs must remain pure. Existing callbacks are the sole exception.
      if callbackCount(WordProgram(expr, routines)) == 0 then
        assertEquals(callbackCount(program), 0, s"introduced callback at $index")
    }

  test("bit/byte injections and eight-bit expansion preserve every construct") {
    checkNative(WordEncodings.bitsToBytes)
    checkNative(WordEncodings.bytesToBits)
    checkNative(WordEncodings.bitsToSymbols(PathItem("z"), PathItem("a")))
    checkNative(WordEncodings.bytesToSymbols(n => PathItem(f"reversed_${255 - n}%03d")))
  }

  private def callbackCount[P](program: WordProgram[P]): Int =
    // Existing generic traversal covers paths nested in grounded-space arguments.
    var count = 0
    def path(p: WordPath[P]): Unit = p match
      case WordPath.Concat(l, r) => path(l); path(r)
      case WordPath.GroundedPP(p, _) => count += 1; path(p)
      case WordPath.GroundedSP(s, _) => count += 1; space(s)
      case _ => ()
    def space(s: WordSpace[P]): Unit =
      import WordSpace.*
      s match
        case Call(_, ps, ss) => ps.foreach(path); ss.foreach(space)
        case Singleton(p) => path(p)
        case Union(l, r) => space(l); space(r)
        case Intersection(l, r) => space(l); space(r)
        case Subtraction(l, r) => space(l); space(r)
        case Restriction(l, r) => space(l); space(r)
        case Raffination(l, r) => space(l); space(r)
        case Composition(l, r) => space(l); space(r)
        case Iteration(s, _, _, b) => space(s); space(b)
        case Fold(s, i, _, _, _, b, u) => space(s); path(i); space(b); path(u)
        case Fixpoint(i, _, s) => space(i); space(s)
        case Wrap(s, p) => space(s); path(p)
        case Unwrap(s, p) => space(s); path(p)
        case TailsUnion(s) => space(s)
        case TailsIntersection(s) => space(s)
        case PrefixClosure(s) => space(s)
        case SuffixClosure(s) => space(s)
        case TailsClosure(s) => space(s)
        case GroundedPS(p, _) => count += 1; path(p)
        case GroundedSS(s, _) => count += 1; space(s)
        case Range(s, _, _) => space(s)
        case _ => ()
    space(program.entry); program.routines.values.foreach(r => space(r.body))
    count

  test("reference representations preserve zeros, array equality and bounded heads") {
    val zeroes = (0 to 200).map(n => BitWords.fromDigits(Vector.fill(n)(0)))
    assertEquals(zeroes.distinct.size, 201)
    zeroes.zipWithIndex.foreach((p, n) => assertEquals(BitWords.length(p), n))
    intercept[IllegalArgumentException](BitWords.digits(BigInt(0)))
    val bytes = Array[Byte](0, -1, 0)
    val p = BytePathValue(bytes)
    bytes(0) = 42
    assertEquals(p, BytePathValue(Array[Byte](0, -1, 0)))
    val copy = p.toArray; copy(0) = 44
    assertEquals(p.unsigned, Vector(0, 255, 0))
    assertEquals(Set(p, BytePathValue(Array[Byte](0, -1, 0))).size, 1)
    val all = ByteSpaceValue((0 until 256).map(i => ByteWords.fromDigits(Vector(i, 0))).toSet + ByteWords.empty)
    val heads = Zippy_bytes.eval(ByteSpace.Head(ByteSpace.Literal(all)))
    assertEquals(heads.paths.size, 256)
    assertEquals(Zippy_bytes.headMask(all), (BigInt(1) << 256) - 1)
    assertEquals(Zippy_bits.headMask(BitSpaceValue(Set(BigInt(1), BigInt(2), BigInt(3)))), 3)
  }

  test("Fibonacci coding is canonical, prefix-free and handles the largest intern id") {
    val ids = (0 to 10000).toVector ++ Vector(Int.MaxValue - 1, Int.MaxValue)
    ids.foreach { id =>
      val code = FibonacciCode.encode(id)
      assertEquals(FibonacciCode.decode(code), id)
      assertEquals(code.sliding(2).count(_ == Vector(1, 1)), 1)
    }
    val codes = ids.take(200).map(FibonacciCode.encode)
    for i <- codes.indices; j <- codes.indices if i != j do assert(!codes(i).startsWith(codes(j)))
    intercept[IllegalArgumentException](FibonacciCode.decode(Vector(1, 1, 1)))
    intercept[IllegalArgumentException](WordEncodings.bytesToBits.decode(BitWords.fromDigits(Vector(0))))
  }

  test("public translations execute recursive source routines and inject only their alphabets") {
    val r = RoutinePtr("drop_recursive")
    val arg = SpaceMention("arg")
    val routine = Routine(r, Vector.empty, Vector(arg),
      Space.Union(Space.Mention(arg), Space.Iteration(Space.Mention(arg), h, t,
        Space.Call(r, Vector.empty, Vector(Space.Mention(t))))))
    val entry = Space.Call(r, Vector.empty, Vector(lit(source)))
    val defs = Map(r -> routine)
    val expected = morkl.eval(entry)(using PathContext.emptyMap, SpaceContextMap(Map.empty), defs)
    val (bits, bp) = ZippyTranslations.symbolsToBits(entry, routines = defs)
    assertEquals(bits.decodeSpace(bp.eval()(using BitWords)).paths, expected.paths)
    val (bytes, byp) = ZippyTranslations.internedStringsToBytes(entry, routines = defs)
    assertEquals(bytes.decodeSpace(byp.eval()(using ByteWords)).paths, expected.paths)
    val (strings, sp) = ZippyTranslations.stringsToBytes(entry, routines = defs)
    assertEquals(strings.decodeSpace(sp.eval()(using ByteWords)).paths, expected.paths)
    val bitExpr = BitSpace.PrefixClosure(BitSpace.Literal(BitSpaceValue(Set(BitWords.fromDigits(Vector(0, 1, 0))))))
    val (injection, symbols) = ZippyTranslations.bitsToSymbols(bitExpr, falseSymbol = PathItem("z"), trueSymbol = PathItem("a"))
    val result = symbols.eval()
    assert(result.paths.flatMap(_.items).subsetOf(Set(PathItem("z"), PathItem("a"))))
    assertEquals(injection.decodeSpace(WordSpaceValue(result.paths)), Zippy_bits.eval(bitExpr))
  }

  test("NUL string termination escapes embedded NUL and escape bytes without collisions") {
    val i = interner()
    val code = WordEncodings.stringSymbolsToBytes(i)
    source.paths.foreach(v => assertEquals(code.decode(code.encode(v)), v))
    Vector(p("a\u0000b"), p("\u0000"), p("\u0001"), p("", ""), p("\u0000", "\u0001")).foreach { value =>
      assertEquals(code.decode(code.encode(value)), value)
    }
    intercept[IllegalArgumentException](code.decode(ByteWords.fromDigits(Vector(1, 3, 0))))
    val malformed = new PathItemInterner; malformed.intern(PathItem("\uD800"))
    intercept[java.nio.charset.CharacterCodingException](WordEncodings.stringSymbolsToBytes(malformed))
  }

  test("open string encoding preserves JVM order without a compilation dictionary") {
    val code = WordEncodings.stringSymbolsToBytes(new PathItemInterner)
    val program = code.compile(SymbolWordLanguage.space(Space.Range(Space.Mention(x), 0, 7)))
    // Register these only after compilation. D7FF, supplementary scalars and E000
    // straddle the UTF-16/UTF-8 ordering disagreement.
    val strings = Vector("", "\u0000", "\u0001", "a", "aa", "é", "\uD7FF", "\uE000", "\uFFFF", "😀", "𐀀", "􏿿")
    val input = SpaceValue(strings.map(s => p(s)).toSet ++ Set(p("a", ""), p("a", "a"), p()))
    val expected = morkl.eval(Space.Range(lit(input), 0, 7))
    val actual = program.eval(spaces = Map(x -> code.encodeSpace(WordSpaceValue(input.paths))))(using ByteWords)
    assertEquals(code.decodeSpace(actual).paths, expected.paths)
    val foldExpr = Space.Fold(Space.Mention(x), epsilon, a, h, t,
      Space.Singleton(Path.Concat(Path.Deref(a), Path.Deref(h))), Path.Concat(Path.Deref(a), Path.Deref(h)))
    val folded = code.compile(SymbolWordLanguage.space(foldExpr))
    val foldExpected = morkl.eval(foldExpr)(using PathContext.emptyMap, SpaceContextMap(Map(x -> input)), PartialFunction.empty)
    assertEquals(code.decodeSpace(folded.eval(spaces = Map(x -> code.encodeSpace(WordSpaceValue(input.paths))))(using ByteWords)).paths, foldExpected.paths)
  }

  test("all 256 unsigned bytes survive Head, iteration, range and symbol injection") {
    val values = ByteSpaceValue((0 until 256).flatMap { b =>
      Vector(ByteWords.fromDigits(Vector(b)), ByteWords.fromDigits(Vector(b, 255 - b, 0)))
    }.toSet + ByteWords.empty)
    val src: ByteSpace = ByteSpace.Literal(values)
    val programs = Vector[ByteSpace](ByteSpace.Head(src), ByteSpace.TailsUnion(src), ByteSpace.Range(src, -12, 0),
      ByteSpace.Iteration(src, h, t, ByteSpace.Wrap(ByteSpace.Mention(t), BytePath.Deref(h))))
    programs.foreach { expr =>
      val expected = Zippy_bytes.eval(expr)
      val bits = ZippyTranslations.bytesToBits(expr)
      assertEquals(WordEncodings.bytesToBits.decodeSpace(bits.eval()(using BitWords)), expected)
      val (code, symbols) = ZippyTranslations.bytesToSymbols(expr, symbol = n => PathItem(f"sym_${255 - n}%03d"))
      val result = symbols.eval()
      assertEquals(code.decodeSpace(WordSpaceValue(result.paths)), expected)
      assert(result.paths.flatMap(_.items).subsetOf((0 until 256).map(n => PathItem(f"sym_${255 - n}%03d")).toSet))
    }
  }

  test("path bounds drive finite scanner unrolling without becoming exact lengths") {
    val code = WordEncodings.internedSymbolsToBits(interner())
    val expr = SymbolWordLanguage.space(Space.Iteration(input, h, t, Space.Singleton(Path.Deref(h))))
    val recursive = code.compile(expr)
    val unrolled = code.compile(expr, unrollBounded = true)
    def cyclic(program: WordProgram[BigInt]): Boolean =
      def calls(s: BitSpace): Set[RoutinePtr] =
        s match
          case WordSpace.Call(r, _, ss) => Set(r) ++ ss.flatMap(calls)
          case WordSpace.Iteration(src, _, _, body) => calls(src) ++ calls(body)
          case WordSpace.Union(l, r) => calls(l) ++ calls(r)
          case WordSpace.Intersection(l, r) => calls(l) ++ calls(r)
          case WordSpace.Subtraction(l, r) => calls(l) ++ calls(r)
          case WordSpace.Composition(l, r) => calls(l) ++ calls(r)
          case WordSpace.Range(s, _, _) => calls(s)
          case _ => Set.empty
      val edges = program.routines.view.mapValues(r => calls(r.body)).toMap
      def visit(r: RoutinePtr, active: Set[RoutinePtr]): Boolean =
        active(r) || edges.getOrElse(r, Set.empty).exists(n => visit(n, active + r))
      edges.keys.exists(r => visit(r, Set.empty))
    assert(cyclic(recursive))
    assert(!cyclic(unrolled))
    val symbolBound = recursive.routines.values.find(_.refs.headOption.exists(_.s == h.s)).get.refs.head
    assertEquals(symbolBound.lengthHint, -1)
    assertEquals(symbolBound.maxLengthHint, code.maxCodeLength.get)
    assertEquals(WordPathLength.upper(BitPath.Deref(symbolBound))(using BitWords), code.maxCodeLength.map(BigInt(_)))
    val ref = PathRef("two_symbols").known(2)
    val boundProgram = code.compile(WordSpace.Singleton(WordPath.Deref(ref)))
    assertEquals(WordPathLength.upper(boundProgram.entry.asInstanceOf[WordSpace.Singleton[BigInt]].p)(using BitWords), code.maxCodeLength.map(n => BigInt(n * 2)))
    val byte = WordEncodings.bytesToBits.compile(ByteSpace.Singleton(BytePath.Deref(ref)))
    assertEquals(WordPathLength.upper(byte.entry.asInstanceOf[WordSpace.Singleton[BigInt]].p)(using BitWords), Some(BigInt(16)))
    val boundedStrings = WordEncodings.stringSymbolsToBytes(interner(), maxSymbolBytes = Some(4))
    checkSymbols(boundedStrings, true)
  }

  test("seeded dependent programs commute with all symbol encodings") {
    val rng = new scala.util.Random(0x7a69707079L)
    val bits = WordEncodings.internedSymbolsToBits(interner())
    val strings = WordEncodings.stringSymbolsToBytes(interner())
    val interned = WordEncodings.internedSymbolsToBytes(interner())
    def generate(depth: Int): Space =
      if depth == 0 then rng.nextInt(4) match
        case 0 => input
        case 1 => lit(other)
        case 2 => Space.Singleton(Path.Deref(outer))
        case _ => Space.Empty
      else
        def child = generate(depth - 1)
        rng.nextInt(19) match
          case 0 => Space.Union(child, child)
          case 1 => Space.Intersection(child, child)
          case 2 => Space.Subtraction(child, child)
          case 3 => Space.Restriction(child, child)
          case 4 => Space.Raffination(child, child)
          case 5 => Space.Composition(child, child)
          case 6 => Space.Wrap(child, one)
          case 7 => Space.Unwrap(child, one)
          case 8 => Space.TailsUnion(child)
          case 9 => Space.TailsIntersection(child)
          case 10 => Space.PrefixClosure(child)
          case 11 => Space.SuffixClosure(child)
          case 12 => Space.TailsClosure(child)
          case 13 => Space.Range(child, rng.nextInt(7) - 3, rng.nextInt(7) - 3)
          case 14 => Space.Iteration(child, h, t, Space.Wrap(Space.Mention(t), Path.Deref(h)))
          case 15 => Space.Iteration(child, h, t, Space.Union(Space.Mention(t), Space.Singleton(Path.Concat(Path.Deref(outer), Path.Deref(h)))))
          case 16 => Space.Fold(child, epsilon, a, h, t, Space.Union(Space.Singleton(Path.Deref(a)), Space.Mention(t)), Path.Concat(Path.Deref(a), Path.Deref(h)))
          case 17 => Space.Fixpoint(child, t, Space.TailsUnion(Space.Mention(t)))
          case _ => Space.Iteration(child, h, t, Space.Iteration(Space.Mention(t), PathRef("inner"), SpaceMention("inner_tail"), Space.Singleton(Path.Deref(h))))
    def check[B](code: WordEncoding[PathValue, B], expr: Space, expected: SpaceValue, input: SpaceValue, index: Int): Unit =
      val compiled = code.compile(SymbolWordLanguage.space(expr), unrollBounded = index % 2 == 0)
      assertEquals(callbackCount(compiled), 0)
      val result = compiled.eval(Map(outer -> code.encode(p("z", "a"))),
        Map(x -> code.encodeSpace(WordSpaceValue(input.paths))))(using code.target)
      assertEquals(code.decodeSpace(result).paths, expected.paths, s"seeded case $index: ${expr.show}")
    for index <- 0 until 160 do
      val expr = generate(3)
      val inputValue = SpaceValue(source.paths.filter(_ => rng.nextBoolean()))
      val expected = morkl.eval(expr)(using PathContextMap(Map(outer -> p("z", "a"))), SpaceContextMap(Map(x -> inputValue)), PartialFunction.empty)
      check(bits, expr, expected, inputValue, index)
      check(strings, expr, expected, inputValue, index)
      check(interned, expr, expected, inputValue, index)
  }

  test("translations compose and generated names avoid source binders") {
    val collision = PathRef("__zippy_prefix_0")
    val rest = SpaceMention("__zippy_remaining_1")
    val freePath = PathRef("__zippy_digit_2")
    val expr = Space.Iteration(lit(source), collision, rest,
      Space.Union(Space.Wrap(Space.Mention(rest), Path.Deref(collision)), Space.Singleton(Path.Deref(freePath))))
    val (code, bytes) = ZippyTranslations.stringsToBytes(expr)
    val bits = WordEncodings.bytesToBits.compile(bytes.entry, bytes.routines)
    val paths = Map(freePath -> p("a"))
    val expected = morkl.eval(expr)(using PathContextMap(paths), SpaceContextMap(Map.empty), PartialFunction.empty)
    val actual = bits.eval(paths.view.mapValues(v => WordEncodings.bytesToBits.encode(code.encode(v))).toMap)(using BitWords)
    assertEquals(code.decodeSpace(WordEncodings.bytesToBits.decodeSpace(actual)).paths, expected.paths)
  }

  test("ignored binders retain outer underscore bindings") {
    val ignored = PathRef("_")
    val ignoredSpace = SpaceMention("_")
    val expr = Space.Iteration(lit(source), ignored, ignoredSpace,
      Space.Union(Space.Singleton(Path.Deref(ignored)), Space.Mention(ignoredSpace)))
    val pc = Map(ignored -> p("a")); val sc = Map(ignoredSpace -> other)
    val expected = morkl.eval(expr)(using PathContextMap(pc), SpaceContextMap(sc), PartialFunction.empty)
    val (code, program) = ZippyTranslations.symbolsToBits(expr)
    val result = program.eval(pc.view.mapValues(code.encode).toMap,
      sc.view.mapValues(v => code.encodeSpace(WordSpaceValue(v.paths))).toMap)(using BitWords)
    assertEquals(code.decodeSpace(result).paths, expected.paths)
  }

  test("byte zipper follows rooted PathMapModel cursor semantics, including absent focuses") {
    def b(xs: Int*): BytePathValue = ByteWords.fromDigits(xs)
    val space = ByteSpaceValue(Set(b(), b(8), b(8, 0), b(8, 0, 255), b(8, 200), b(9)))
    val root = ByteZipper(space, root = b(8))
    assert(root.atRoot && root.isVal && root.pathExists)
    assertEquals(root.makeMap.paths, Set(b(), b(0), b(0, 255), b(200)))
    assertEquals(root.focusNode.paths, Set(b(0), b(0, 255), b(200)))
    assertEquals(root.childMask, BigInt(1).setBit(200))
    assertEquals(root.childCount, 2)
    val (second, byte) = root.descendIndexedByte(1).get
    assertEquals(byte, 200)
    assertEquals(second.focusByte, Some(200))
    assert(second.isVal)
    val absent = second.moveToPath(b(0, 254))
    assert(!absent.pathExists && !absent.isVal)
    assertEquals(absent.focus, b(8, 0, 254))
    val (ascended, count) = absent.ascend(100)
    assertEquals(count, 2)
    assertEquals(ascended, root)
    assertEquals(root.ascend(1), root -> 0)
  }

  test("mutual recursion and absorbing direct self calls retain reference behavior") {
    val left = RoutinePtr("mutual_left"); val right = RoutinePtr("mutual_right")
    val formal = SpaceMention("mutual_input")
    def r(name: RoutinePtr, next: RoutinePtr): Routine = Routine(name, Vector.empty, Vector(formal),
      Space.Union(Space.Mention(formal), Space.Iteration(Space.Mention(formal), h, t,
        Space.Call(next, Vector.empty, Vector(Space.Mention(t))))))
    val routines = Map(left -> r(left, right), right -> r(right, left))
    val entry = Space.Call(left, Vector.empty, Vector(lit(source)))
    val expected = morkl.eval(entry)(using PathContext.emptyMap, SpaceContextMap(Map.empty), routines)
    val (code, program) = ZippyTranslations.symbolsToBits(entry, routines = routines)
    assertEquals(code.decodeSpace(program.eval()(using BitWords)).paths, expected.paths)
    val self = RoutinePtr("absorb_self")
    val call = Space.Call(self, Vector.empty, Vector(input))
    val routine = Routine(self, Vector.empty, Vector(x), Space.Union(Space.Singleton(one), call))
    val (selfCode, selfProgram) = ZippyTranslations.symbolsToBits(call, interner(), Map(self -> routine))
    val actual = selfProgram.eval(spaces = Map(x -> selfCode.encodeSpace(WordSpaceValue(source.paths))))(using BitWords)
    assertEquals(selfCode.decodeSpace(actual).paths, Set(p("a")))
  }
