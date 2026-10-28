package morkl

import munit.FunSuite
import scala.util.Random

class FiniteAlphabetTest extends FunSuite:
  private def p(items: String*): PathValue = PathValue(items.map(PathItem(_)).toList)
  private val paths = Set(p(), p("a"), p("a", "b"), p("a", "a", "z"), p("b", "b"), p("z"), p(""))
  private val literal = Space.Literal(SpaceValue(paths))
  private val other = Space.Literal(SpaceValue(Set(p(), p("b"), p("a", "a"))))
  private val h = PathRef("h")
  private val r = SpaceMention("r")
  private val acc = PathRef("acc")
  private val interner = new PathItemInterner
  Vector("z", "b", "a", "", "q", "c", "d", "e", "f").foreach(s => interner.intern(PathItem(s)))
  private val fib = AlphabetEncodings.internedBits(interner)
  private val fixed = AlphabetEncodings.internedBytes(interner)
  private val strings = AlphabetEncodings.stringBytes

  private val expressions: Vector[Space] = Vector(
    Space.Empty, Space.Singleton(Path.Constant(p())), Space.Singleton(Path.Concat(Path.Constant(p("a")), Path.Constant(p("b")))), literal,
    Space.Union(literal, other), Space.Intersection(literal, other), Space.Subtraction(literal, other),
    Space.Restriction(literal, other), Space.Raffination(literal, other), Space.Composition(literal, other),
    Space.Wrap(literal, Path.Constant(p("b"))), Space.Unwrap(literal, Path.Constant(p("a"))),
    Space.TailsUnion(literal), Space.TailsIntersection(literal), Space.PrefixClosure(literal), Space.SuffixClosure(literal), Space.TailsClosure(literal),
    Space.Iteration(literal, h, r, Space.Wrap(Space.Mention(r), Path.Deref(h))),
    Space.Iteration(literal, h, r, Space.Singleton(Path.Deref(h))),
    Space.Iteration(literal, h, r, Space.Iteration(Space.Mention(r), PathRef("inner"), SpaceMention("tail"),
      Space.Wrap(Space.Mention(SpaceMention("tail")), Path.Concat(Path.Deref(h), Path.Deref(PathRef("inner")))))),
    Space.Fold(literal, Path.Constant(p()), acc, h, r, Space.Singleton(Path.Concat(Path.Deref(acc), Path.Deref(h))), Path.Concat(Path.Deref(acc), Path.Deref(h))),
    Space.Fixpoint(other, SpaceMention("fixed"), Space.Union(Space.Mention(SpaceMention("fixed")), literal)),
    Space.Range(literal, 2, -1),
    Space.GroundedPS(Path.Constant(p("a")), x => SpaceValue(Set(x, p("b")))),
    Space.GroundedSS(literal, x => SpaceValue(x.paths.filter(_.items.size == 1))),
    Space.Singleton(Path.GroundedPP(Path.Constant(p("a")), x => SymbolPaths.concat(x, p("b")))),
    Space.Singleton(Path.GroundedSP(literal, x => x.paths.toVector.sortWith(SymbolPaths.compare(_, _) < 0).last))
  )

  private def check[T](expr: Space, code: AlphabetEncoding[PathValue, T], routines: Vector[Routine] = Vector.empty): Unit =
    val translated = AlphabetTranslation.symbols(expr, code, routines)
    val expected = eval(expr)(using PathContext.emptyMap, SpaceContextMap(Map.empty), routines.map(r => r.name -> r).toMap)
    assertEquals(translated.eval().map(code.decode), expected.paths, expr.show)

  test("native representations retain leading zeros, epsilon, and byte array value equality") {
    for digits <- Vector(Vector.empty, Vector(0), Vector(0, 0, 1), Vector(1, 0, 0)) do
      val bits = BitPaths.fromDigits(digits)
      assertEquals(BitPaths.digits(bits), digits)
      assertEquals(BitPaths.length(bits), digits.size)
    intercept[IllegalArgumentException](BitPaths.length(BigInt(0)))
    val array = Array[Byte](0, -1, 2)
    val bytes = BytePath(array)
    array(0) = 12
    bytes.toArray(0) = 99
    assertEquals(bytes, BytePath(0, 255, 2))
    assertEquals(Set(bytes, BytePath(0, 255, 2)).size, 1)
    assertEquals(BytePaths.compare(BytePath(127), BytePath(128)), -1)
  }

  test("Fibonacci codes are canonical and self delimiting") {
    assertEquals(FibonacciCode.encode(1), Vector(1, 1))
    assertEquals(FibonacciCode.encode(2), Vector(0, 1, 1))
    assertEquals(FibonacciCode.encode(3), Vector(0, 0, 1, 1))
    for n <- (1 to 2000).map(BigInt(_)) :+ (BigInt(Int.MaxValue) + 1) do
      assertEquals(FibonacciCode.decode(FibonacciCode.encode(n)), n)
    paths.foreach { x =>
      assertEquals(fib.decode(fib.encode(x)), x)
      assertEquals(fixed.decode(fixed.encode(x)), x)
      assertEquals(BytePaths.length(fixed.encode(x)), x.items.size * 4)
    }
    intercept[IllegalArgumentException](fib.encode(p("not-in-snapshot")))
    intercept[IllegalArgumentException](fixed.decode(BytePath(0)))
  }

  test("zero terminated strings preserve every UTF-16 string and symbol order") {
    val items = Vector("", "\u0000", "\u0001", "\u0002", "abc", "a\u0000b", "é", "\ud800", "\ud83d\ude00", "\ue000", "\uffff")
    val all = p(items*)
    assertEquals(strings.decode(strings.encode(all)), all)
    val sorted = items.sorted.map(x => strings.encode(p(x)))
    assertEquals(sorted.sortWith(BytePaths.compare(_, _) < 0), sorted)
    assertEquals(strings.encode(p("abc")).unsigned, Vector(97, 98, 99, 0))
    assertEquals(strings.encode(p("")).unsigned, Vector(0))
    intercept[IllegalArgumentException](strings.decode(BytePath(97)))
  }

  test("every symbol language case translates through Fibonacci, four-byte, and string codecs") {
    expressions.foreach { expression =>
      check(expression, fib)
      check(expression, fixed)
      check(expression, strings)
    }
  }

  test("tails intersection handles shared tails, epsilon, empty groups, and multiple symbols") {
    Vector(Set.empty[PathValue], Set(p()), Set(p("a"), p("b")), Set(p("a", "z"), p("b", "z"), p()),
      Set(p("a", "z"), p("a", "b"), p("b", "z"))).foreach { xs =>
      val s = Space.TailsIntersection(Space.Literal(SpaceValue(xs)))
      check(s, fib); check(s, fixed); check(s, strings)
    }
  }

  test("ranges and folds retain SYMBOL order when interner insertion order disagrees") {
    for start <- -4 to 4; end <- -4 to 4 do
      check(Space.Range(literal, start, end), fib)
      check(Space.Range(literal, start, end), fixed)
    val s = Space.Fold(literal, Path.Constant(p()), acc, h, r, Space.Singleton(Path.Deref(acc)), Path.Concat(Path.Deref(acc), Path.Deref(h)))
    check(s, fib); check(s, fixed); check(s, strings)
  }

  test("free inputs, lexical shadowing, recursive calls, and helper name collisions") {
    val src = SpaceMention("#zippy_body_1")
    val prefix = PathRef("prefix").known(1)
    val rp = RoutinePtr("walk")
    val function = Routine(rp, Vector(prefix), Vector(src),
      Space.Union(Space.Wrap(Space.Mention(src), Path.Deref(prefix)),
        Space.Iteration(Space.Mention(src), h, r, Space.Call(rp, Vector(Path.Deref(h)), Vector(Space.Mention(r))))))
    val expr = Space.Call(rp, Vector(Path.Constant(p("b"))), Vector(literal))
    check(expr, fib, Vector(function)); check(expr, strings, Vector(function))
    val open = Space.Iteration(Space.Mention(src), h, r, Space.Wrap(Space.Mention(r), Path.Deref(prefix)))
    val translated = AlphabetTranslation.symbols(open, fib)
    val actual = translated.eval(Map(prefix -> fib.encode(p("z"))), Map(src -> paths.map(fib.encode)))
    val expected = eval(open)(using PathContextMap(Map(prefix -> p("z"))), SpaceContextMap(Map(src -> SpaceValue(paths))), PartialFunction.empty)
    assertEquals(actual.map(fib.decode), expected.paths)
    val shadowed = Space.Iteration(literal, h, r, Space.Iteration(Space.Mention(r), h, r, Space.Singleton(Path.Deref(h))))
    check(shadowed, fib); check(shadowed, strings)
  }

  test("native heads have at most two or 256 elements and native iteration binds one digit") {
    val bytes = (0 to 255).map(b => BytePath(b, 17)).toSet + BytePath()
    assertEquals(ByteSpace.eval(ByteSpace.Head(ByteSpace.Literal(bytes))), (0 to 255).map(BytePath(_)).toSet)
    assertEquals(ByteSpace.headMask(bytes).bitCount, 256)
    val bits = Set(BitPaths.empty, BitPaths.fromDigits(Vector(0, 1)), BitPaths.fromDigits(Vector(1, 0)))
    assertEquals(BitSpace.eval(BitSpace.Head(BitSpace.Literal(bits))), Set(BigInt(2), BigInt(3)))
    assertEquals(BitSpace.headMask(bits), BigInt(3))
  }

  private def program[P](s: AlphabetSpace[P], d: PathDomain[P]): AlphabetProgram[P] = AlphabetProgram(s, Map.empty, d)
  private def digitExpressions[P](d: FiniteDomain[P]): Vector[AlphabetSpace[P]] =
    import AlphabetSpace.*
    val a = Literal(Set(d.empty, d.fromDigits(Vector(0)), d.fromDigits(Vector(0, 1)), d.fromDigits(Vector(1, 0)), d.fromDigits(Vector(d.arity - 1, 1))))
    val b = Literal(Set(d.fromDigits(Vector(1)), d.fromDigits(Vector(0, 1))))
    Vector(Empty(), a, Union(a, b), Intersection(a, b), Subtraction(a, b), Restriction(a, b), Raffination(a, b), Composition(a, b),
      TailsUnion(a), TailsIntersection(a), PrefixClosure(a), SuffixClosure(a), TailsClosure(a), Range(a, -3, 0),
      Iteration(a, h, r, Wrap(Mention(r), AlphabetPath.Deref(h))),
      Fold(a, AlphabetPath.Constant(d.empty), acc, h, r, Singleton(AlphabetPath.Deref(acc)), AlphabetPath.Concat(AlphabetPath.Deref(acc), AlphabetPath.Deref(h))))

  test("bits to bytes, bytes to eight bits, and both symbol injections execute every structural operator") {
    digitExpressions(BitPaths).foreach { s =>
      val original = program(s, BitPaths)
      val bytes = AlphabetTranslation(original, AlphabetEncodings.bitsToBytes)
      assertEquals(bytes.eval().map(AlphabetEncodings.bitsToBytes.decode), original.eval())
      val symbols = AlphabetTranslation(original, AlphabetEncodings.bitsToSymbols)
      assertEquals(symbols.eval().map(AlphabetEncodings.bitsToSymbols.decode), original.eval())
      val real = SymbolAlphabet.toSpace(symbols.expression)
      val functions = symbols.routines.view.mapValues(SymbolAlphabet.toRoutine).toMap
      assertEquals(eval(real)(using PathContext.emptyMap, SpaceContextMap(Map.empty), functions).paths, symbols.eval())
    }
    digitExpressions(BytePaths).foreach { s =>
      val original = program(s, BytePaths)
      val bits = AlphabetTranslation(original, AlphabetEncodings.bytesToBits)
      assertEquals(bits.eval().map(AlphabetEncodings.bytesToBits.decode), original.eval())
      val symbols = AlphabetTranslation(original, AlphabetEncodings.bytesToSymbols)
      assertEquals(symbols.eval().map(AlphabetEncodings.bytesToSymbols.decode), original.eval())
      val real = SymbolAlphabet.toSpace(symbols.expression)
      val functions = symbols.routines.view.mapValues(SymbolAlphabet.toRoutine).toMap
      assertEquals(eval(real)(using PathContext.emptyMap, SpaceContextMap(Map.empty), functions).paths, symbols.eval())
    }
    for b <- 0 to 255 do
      val path = BytePath(b, 0, 255)
      assertEquals(AlphabetEncodings.bytesToBits.decode(AlphabetEncodings.bytesToBits.encode(path)), path)
  }

  test("generated lowerings introduce no grounded callbacks and have closed helper functions") {
    val pure = expressions.take(23)
    pure.foreach { s =>
      val translated = AlphabetTranslation.symbols(s, fib)
      def inspect(x: BitSpace): Unit =
        AlphabetSyntax.map[BigInt, BigInt](x)({
          case AlphabetPath.GroundedPP(_, _) | AlphabetPath.GroundedSP(_, _) => fail("introduced path callback")
          case _ => None
        }, {
          case AlphabetSpace.GroundedPS(_, _) | AlphabetSpace.GroundedSS(_, _) => fail("introduced space callback")
          case _ => None
        }, identity, identity)
      inspect(translated.expression)
      translated.routines.values.foreach { f =>
        inspect(f.body)
        val free = AlphabetSyntax.free(f.body)
        assertEquals(free._1 -- f.refs, Set.empty[PathRef])
        assertEquals(free._2 -- f.mentions, Set.empty[SpaceMention])
      }
    }
  }

  test("length annotations become encoded lengths and finite bounds on scanner parameters") {
    val ref = PathRef("input").known(3)
    val fixedProgram = AlphabetTranslation.symbols(Space.Singleton(Path.Deref(ref)), fixed)
    val fixedRef = fixedProgram.expression.asInstanceOf[AlphabetSpace.Singleton[BytePath]].path.asInstanceOf[AlphabetPath.Deref[BytePath]].ref
    assertEquals(fixedRef.lengthHint, 12)
    val fibProgram = AlphabetTranslation.symbols(Space.Singleton(Path.Deref(ref)), fib)
    val fibRef = fibProgram.expression.asInstanceOf[AlphabetSpace.Singleton[BigInt]].path.asInstanceOf[AlphabetPath.Deref[BigInt]].ref
    assertEquals(fibRef.lengthHint, -1)
    assertEquals(fibRef.maximumLengthHint, fib.maxWidth.get * 3)
    val scan = AlphabetTranslation.symbols(Space.Iteration(literal, h, r, Space.Singleton(Path.Deref(h))), fib)
    assert(scan.routines.values.filter(_.name.s.contains("scan")).forall(f => f.refs.head.lengthHint >= 0 && f.refs.head.maximumLengthHint < fib.maxWidth.get))
    val ref2 = ref.bounded(5)
    assertEquals(ref2, ref)
    assertEquals(ref2.lengthHint, 3)
    assertEquals(ref2.maximumLengthHint, 5)
  }

  test("encoded upper bounds feed existing path-length and spatial analyses") {
    val ref = PathRef("bounded").bounded(9)
    val bitExpr = BitSpace.Singleton(BitPath.Deref(ref))
    val estimate = AlphabetAnalysis.pathLengths(bitExpr, BitPaths)
    assertEquals(estimate.upper.annotatedBound(Z3BoundDirection.Upper), Some(BigInt(9)))
    val base = ResultPathLength.estimateBaseline(Space.Singleton(Path.Deref(ref)))
    assertEquals(base.upper.annotatedBound(Z3BoundDirection.Upper), Some(BigInt(9)))
    val constant = ByteSpace.Literal(Set(BytePath(), BytePath(0, 1, 255)))
    val length = AlphabetAnalysis.pathLengths(constant, BytePaths)
    assertEquals(length.lower.annotatedValue, Some(BigInt(0)))
    assertEquals(length.upper.annotatedValue, Some(BigInt(3)))
  }

  test("ignored iteration and fold binders retain enclosing wildcard bindings") {
    val wildP = PathRef("_")
    val wildS = SpaceMention("_")
    val expressions = Vector(
      Space.Iteration(literal, wildP, wildS, Space.Wrap(Space.Mention(wildS), Path.Deref(wildP))),
      Space.Fold(literal, Path.Constant(p()), wildP, h, wildS, Space.Wrap(Space.Mention(wildS), Path.Deref(wildP)), Path.Deref(wildP)))
    expressions.foreach { s =>
      val expected = eval(s)(using PathContextMap(Map(wildP -> p("z"))), SpaceContextMap(Map(wildS -> SpaceValue(Set(p("b"))))), PartialFunction.empty)
      val t = AlphabetTranslation.symbols(s, fib)
      assertEquals(t.eval(Map(wildP -> fib.encode(p("z"))), Map(wildS -> Set(fib.encode(p("b"))))).map(fib.decode), expected.paths)
    }
  }

  test("fixed-width decoding is exactly eight target edges and finite scanners have no cycles") {
    val expr = ByteSpace.Iteration(ByteSpace.Literal(Set(BytePath(255, 1))), h, r, ByteSpace.Singleton(BytePathExpr.Deref(h)))
    val t = AlphabetTranslation(program(expr, BytePaths), AlphabetEncodings.bytesToBits)
    def scanDepth(r: RoutinePtr, seen: Set[RoutinePtr]): Int =
      assert(!seen(r), s"cycle in specialized scanner ${r.s}")
      var next = Set.empty[RoutinePtr]
      AlphabetSyntax.map[BigInt, BigInt](t.routines(r).body)(_ => None, {
        case AlphabetSpace.Call(r, _, _) if r.s.contains("scan") => next += r; None
        case _ => None
      }, identity, identity)
      1 + next.map(scanDepth(_, seen + r)).maxOption.getOrElse(0)
    val start = t.expression.asInstanceOf[AlphabetSpace.Call[BigInt]].routine
    assertEquals(scanDepth(start, Set.empty), 8)
    val terminated = AlphabetTranslation.symbols(Space.Iteration(literal, h, r, Space.Singleton(Path.Deref(h))), strings)
    assert(terminated.routines.values.exists { routine =>
      var self = false
      AlphabetSyntax.map[BytePath, BytePath](routine.body)(_ => None, {
        case AlphabetSpace.Call(name, _, _) if name == routine.name => self = true; None
        case _ => None
      }, identity, identity)
      self
    })
  }

  test("byte zipper preserves terminals, unsigned children, roots, and off-trie positions") {
    val data = Set(BytePath(), BytePath(2), BytePath(2, 0), BytePath(2, 255, 1), BytePath(5, 1))
    val root = ByteZipper(data)
    assert(root.isValue)
    assertEquals(root.childMask, BigInt(0).setBit(2).setBit(5))
    val z = ByteZipper(data, BytePath(2))
    assertEquals(z.makeSpace, Set(BytePath(), BytePath(0), BytePath(255, 1)))
    assert(z.isValue)
    assertEquals(z.descendIndexedByte(1).get._1, 255)
    assertEquals(z.descendByte(0).nextSiblingByte.get._1, 255)
    assertEquals(z.descendByte(255).previousSiblingByte.get._1, 0)
    assert(z.nextSiblingByte.isEmpty)
    val missing = z.descendTo(BytePath(42, 43))
    assert(!missing.pathExists)
    assert(!missing.isValue)
    assertEquals(missing.makeSpace, Set.empty[BytePath])
    val (count, reset) = missing.ascend(100)
    assertEquals(count, 2)
    assertEquals(reset, z)
    assertEquals(reset.originPath, BytePath(2))
  }

  test("random closed programs and path sets agree across encodings and translation composition") {
    val rng = new Random(0x5a17)
    def leaf(): Space =
      val values = (0 until rng.nextInt(7)).map { _ =>
        p(Vector.fill(rng.nextInt(4))(Vector("", "a", "b", "z")(rng.nextInt(4)))*)
      }.toSet
      Space.Literal(SpaceValue(values))
    def expression(depth: Int): Space =
      if depth == 0 then leaf()
      else
        def child = expression(depth - 1)
        rng.nextInt(16) match
          case 0 => Space.Union(child, child)
          case 1 => Space.Intersection(child, child)
          case 2 => Space.Subtraction(child, child)
          case 3 => Space.Restriction(child, child)
          case 4 => Space.Raffination(child, child)
          case 5 => Space.Composition(child, child)
          case 6 => Space.TailsUnion(child)
          case 7 => Space.TailsIntersection(child)
          case 8 => Space.PrefixClosure(child)
          case 9 => Space.SuffixClosure(child)
          case 10 => Space.TailsClosure(child)
          case 11 => Space.Range(child, rng.nextInt(7) - 3, rng.nextInt(7) - 3)
          case 12 => Space.Iteration(child, h, r, Space.Wrap(Space.TailsClosure(Space.Mention(r)), Path.Deref(h)))
          case 13 => Space.Fold(child, Path.Constant(p()), acc, h, r, Space.Wrap(Space.Mention(r), Path.Deref(acc)), Path.Concat(Path.Deref(acc), Path.Deref(h)))
          case 14 => Space.Unwrap(child, Path.Constant(p("a")))
          case _ => Space.Wrap(child, Path.Constant(p("b")))
    for i <- 0 until 120 do
      val s = expression(3)
      check(s, fib); check(s, fixed); check(s, strings)
      if i < 20 then
        val bits = AlphabetTranslation.symbols(s, fib)
        val bytes = AlphabetTranslation(bits, AlphabetEncodings.bitsToBytes)
        assertEquals(bytes.eval().map(AlphabetEncodings.bitsToBytes.decode).map(fib.decode), eval(s).paths)
  }

  test("head cardinality analyses know the finite alphabet even for open spaces") {
    val bits = AlphabetAnalysis.resultSize(BitSpace.Head(BitSpace.Mention(SpaceMention("open"))), BitPaths)
    val bytes = AlphabetAnalysis.resultSize(ByteSpace.Head(ByteSpace.Mention(SpaceMention("open"))), BytePaths)
    assertEquals(bits.upper.annotatedBound(Z3BoundDirection.Upper), Some(BigInt(2)))
    assertEquals(bytes.upper.annotatedBound(Z3BoundDirection.Upper), Some(BigInt(256)))
  }

  test("mutual recursion and nested folds retain all transitive captures") {
    val a = RoutinePtr("mutualA"); val b = RoutinePtr("mutualB")
    val input = SpaceMention("mutualInput"); val prefix = PathRef("mutualPrefix")
    def routine(name: RoutinePtr, next: RoutinePtr): Routine = Routine(name, Vector(prefix), Vector(input),
      Space.Union(Space.Singleton(Path.Deref(prefix)), Space.Iteration(Space.Mention(input), h, r,
        Space.Call(next, Vector(Path.Concat(Path.Deref(prefix), Path.Deref(h))), Vector(Space.Mention(r))))))
    val call = Space.Call(a, Vector(Path.Constant(p("b"))), Vector(literal))
    val defs = Vector(routine(a, b), routine(b, a))
    check(call, fib, defs); check(call, fixed, defs); check(call, strings, defs)
    val nested = Space.Fold(literal, Path.Constant(p()), acc, h, r,
      Space.Fold(Space.Mention(r), Path.Deref(acc), PathRef("innerAcc"), PathRef("innerHead"), SpaceMention("innerRest"),
        Space.Singleton(Path.Concat(Path.Deref(h), Path.Deref(PathRef("innerAcc")))), Path.Concat(Path.Deref(PathRef("innerAcc")), Path.Deref(PathRef("innerHead")))),
      Path.Concat(Path.Deref(acc), Path.Deref(h)))
    check(nested, fib); check(nested, fixed); check(nested, strings)
  }

  test("wildcard free-variable capture and annotations agree with ignored-binder evaluation") {
    val wild = SpaceMention("_")
    val expr = Space.Iteration(literal, h, r, Space.Fixpoint(Space.Empty, wild, Space.Mention(wild)))
    val translated = AlphabetTranslation.symbols(expr, fib)
    assertEquals(translated.eval(spaces = Map(wild -> Set(fib.encode(p("z"))))).map(fib.decode), Set(p("z")))
    val source = Space.Iteration(literal, PathRef("_"), r, Space.Singleton(Path.Deref(PathRef("_").known(2))))
    val tagged = ReferenceHints.tag(source)
    val body = tagged.asInstanceOf[Space.Iteration].templates.asInstanceOf[Space.Singleton].p.asInstanceOf[Path.Deref]
    assertEquals(body.pr.lengthHint, 2)
  }

  test("all UTF-16 code units round-trip and preserve order in terminated string encoding") {
    val all = (0 to 65535).iterator.map(i => i.toChar.toString).mkString
    assertEquals(strings.decode(strings.encode(p(all))), p(all))
    val random = new Random(2718)
    for _ <- 0 until 500 do
      val a = Vector.fill(random.nextInt(6))(random.nextInt(65536).toChar).mkString
      val b = Vector.fill(random.nextInt(6))(random.nextInt(65536).toChar).mkString
      assertEquals(Integer.signum(BytePaths.compare(strings.encode(p(a)), strings.encode(p(b)))), Integer.signum(a.compareTo(b)))
  }

  test("coincident Fold accumulator and head names retain the head binding") {
    val expr = Space.Fold(literal, Path.Constant(p()), h, h, r,
      Space.Singleton(Path.Deref(h)), Path.Deref(h))
    val tagged = ReferenceHints.tag(expr).asInstanceOf[Space.Fold]
    assertEquals(tagged.templates.asInstanceOf[Space.Singleton].p.asInstanceOf[Path.Deref].pr.lengthHint, 1)
    check(expr, fib); check(expr, fixed); check(expr, strings)
  }
