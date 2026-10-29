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

  test("NUL string terminator rejects ambiguous symbols, but empty and Unicode symbols roundtrip") {
    val i = interner()
    val code = WordEncodings.stringSymbolsToBytes(i)
    source.paths.foreach(v => assertEquals(code.decode(code.encode(v)), v))
    val bad = new PathItemInterner; bad.intern(PathItem("a\u0000b"))
    intercept[IllegalArgumentException](WordEncodings.stringSymbolsToBytes(bad))
    val malformed = new PathItemInterner; malformed.intern(PathItem("\uD800"))
    intercept[java.nio.charset.CharacterCodingException](WordEncodings.stringSymbolsToBytes(malformed))
  }
