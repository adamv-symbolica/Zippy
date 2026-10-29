package morkl

import munit.FunSuite
import morkl.finite.{Path as FP, Space as FS, Routine as FR, *}

class FiniteAlphabetTest extends FunSuite:
  test("bit BigInts are a length-preserving bijection and use numeric prefix operations") {
    val paths = (0 to 12).flatMap(n => (0 until (1 << n)).map(k => Vector.tabulate(n)(i => (k & (1 << i)) != 0)))
    assertEquals(paths.map(BitStorage.path).distinct.size, paths.size)
    paths.foreach(p => assertEquals(BitStorage.items(BitStorage.path(p)), p))
    for a <- paths.take(40); b <- paths.take(30) do
      val x = BitStorage.path(a); val y = BitStorage.path(b)
      assertEquals(BitStorage.items(BitStorage.concat(x, y)), a ++ b)
      assertEquals(BitStorage.startsWith(x, y), a.startsWith(b))
      assertEquals(BitStorage.items(BitStorage.drop(x, b.size)), a.drop(b.size))
    assertEquals(BitSpace.fromPaths(Vector(Vector.empty, Vector(false), Vector(true, false))).headMask, 3)
    intercept[IllegalArgumentException](BitSpace(Set(BigInt(0))))
  }

  test("byte arrays have immutable content equality, unsigned order and a 256-bit head bound") {
    val array = Array[Byte](0, -1)
    val p = BytePath(array)
    array(0) = 42
    val exposed = p.toArray
    exposed(1) = 0
    assertEquals(p, BytePath(Array[Byte](0, -1)))
    assertEquals(Set(p, BytePath(Array[Byte](0, -1))).size, 1)
    assert(ByteStorage.compare(BytePath.unsigned(Vector(127)), BytePath.unsigned(Vector(128))) < 0)
    val all = ByteSpace.fromPaths((0 until 256).map(b => Vector(b, 3)))
    assertEquals(all.head.size, 256)
    assertEquals(all.headMask, (BigInt(1) << 256) - 1)
    intercept[IllegalArgumentException](BytePath.unsigned(Vector(256)))
  }

  test("byte zipper observes root bounds, terminals, missing focuses, and snapshots") {
    val source = ByteSpace.fromPaths(Vector(Vector(1), Vector(1, 2), Vector(1, 3, 4), Vector(8)))
    val root = ByteZipper(source).descendTo(BytePath.unsigned(Vector(1))).fork
    assertEquals(root.makeMap.vectors, Set(Vector.empty[Int], Vector(2), Vector(3, 4)))
    assert(root.isValue)
    assertEquals(root.childMask, BigInt(0).setBit(2).setBit(3))
    val missing = root.descendTo(BytePath.unsigned(Vector(9, 8)))
    assert(!missing.pathExists)
    val (count, back) = missing.ascend(99)
    assertEquals(count, 2)
    assertEquals(back, root)
  }

  test("Fibonacci code is canonical, self-delimiting and covers Int IDs") {
    val ids = (0 until 10000).toVector ++ Vector(Int.MaxValue)
    ids.foreach(i => assertEquals(Codes.unfibonacci(Codes.fibonacci(i)), i))
    assertEquals(Codes.fibonacci(0), Vector(true, true))
    assertEquals(Codes.fibonacci(1), Vector(false, true, true))
    val codes = (0 until 128).map(Codes.fibonacci)
    for a <- codes; b <- codes if a != b do assert(!a.startsWith(b))
    intercept[IllegalArgumentException](Codes.unfibonacci(Vector(true, true, true)))
  }

  test("terminated strings preserve every UTF-16 value, boundaries and lexical order") {
    val strings = Vector("", "\u0000", "\u0001", "\u0002", "a", "aa", "a\u0000", "é", "水", "😀", "\ud800", "\ue000") ++
      (0 to 65535).map(_.toChar.toString)
    strings.foreach { s =>
      val encoded = Codes.stringBytes.encodeSymbol(PathItem(s))
      assertEquals(encoded.last, 0)
      assertEquals(encoded.count(_ == 0), 1)
      assertEquals(Codes.stringBytes.decodeSymbol(encoded).show, s)
    }
    val sorted = strings.distinct.sorted
    val byteOrd = Ordering.Iterable[Int]
    sorted.sliding(2).foreach(xs => assert(byteOrd.compare(
      Codes.stringBytes.encodeSymbol(PathItem(xs(0))), Codes.stringBytes.encodeSymbol(PathItem(xs(1)))) < 0))
    val path = Vector("", "a", "a.b", "\u0000", "😀").map(PathItem(_))
    assertEquals(Codes.stringBytes.decode(Codes.stringBytes.encode(path)), path)
    intercept[IllegalArgumentException](Codes.stringBytes.decode(Vector(65)))
    intercept[IllegalArgumentException](Codes.stringBytes.decode(Vector(1, 0)))
  }

  private def expressions[A](a: A, b: A): Vector[FS[A]] =
    val x: FS[A] = FS.Literal(Set(Vector.empty, Vector(a), Vector(b), Vector(a, a), Vector(a, b), Vector(b, a)))
    val y: FS[A] = FS.Literal(Set(Vector(a), Vector(a, b)))
    val h = PathRef("h").known(1); val r = SpaceMention("r"); val acc = PathRef("acc")
    val hp: FP[A] = FP.Deref(h)
    val zero: FP[A] = FP.zero[A]
    val cp: FP[A] = FP.Constant(Vector(a))
    val tail: FS[A] = FS.Mention(r)
    val identity = RoutinePtr("identity")
    Vector(FS.Empty(), x, FS.Singleton(zero), FS.Singleton(FP.Concat(cp, cp)), FS.Union(x, y),
      FS.Intersection(x, y), FS.Subtraction(x, y), FS.Restriction(x, y), FS.Raffination(x, y),
      FS.Composition(y, y), FS.Wrap(x, cp), FS.Unwrap(x, cp), FS.TailsUnion(x), FS.TailsIntersection(x),
      FS.PrefixClosure(x), FS.SuffixClosure(x), FS.TailsClosure(x), FS.Head(x),
      FS.Iteration(x, h, r, FS.Wrap(tail, hp)),
      FS.Iteration(x, h, r, FS.Iteration(tail, h, r, FS.Wrap(FS.Mention(r), FP.Deref(h)))),
      FS.Fold(x, zero, acc, h, r, FS.Singleton(FP.Concat(FP.Deref(acc), hp)), FP.Concat(FP.Deref(acc), hp)),
      FS.Fixpoint(y, r, FS.Union(FS.Mention(r), x)),
      FS.Call(identity, Vector(cp), Vector(x)),
      FS.GroundedPS(cp, p => Set(p, p ++ p)), FS.GroundedSS(x, ps => ps.filter(_.nonEmpty)),
      FS.Singleton(FP.GroundedPP(cp, _ ++ Vector(b))), FS.Singleton(FP.GroundedSP(x, _.maxBy(_.size)))) ++
      (for start <- Vector(Int.MinValue, -8, -2, -1, 0, 1, 2, 9, Int.MaxValue);
           end <- Vector(Int.MinValue, -8, -1, 0, 1, 2, 5, 9, Int.MaxValue) yield FS.Range(x, start, end))

  private def verify[S, T, P, V](expr: FS[S], routines: Map[RoutinePtr, FR[S]], code: Code[S, T],
                                 storage: Storage[T, P, V], expected: Set[Vector[S]]): Translation[S, T] =
    val compiled = Translator(code)(expr, routines)
    val result = Evaluator(storage)(compiled.expression, routines = compiled.routines)
    assertEquals(compiled.decodePaths(storage.paths(result).map(storage.items)), expected)
    compiled

  test("every bit-language constructor translates through bytes and the actual symbol evaluator") {
    val hr = PathRef("parameter"); val sr = SpaceMention("argument")
    val id = FR[Boolean](RoutinePtr("identity"), Vector(hr), Vector(sr), FS.Mention(sr))
    val rs = Map(id.name -> id)
    for expr <- expressions(false, true) do
      val expected = BitSpace.evaluator(expr, routines = rs).vectors
      val compiled = verify(expr, rs, Codes.bitsToBytes, ByteStorage, expected)
      val asSymbols = ZippyTranslations.bitsToSymbols(expr, rs)
      val symbolRoutines = asSymbols.routines.view.mapValues(Symbols.toRoutine).toMap
      val got = eval(Symbols.toSpace(asSymbols.expression))(using rc = symbolRoutines)
      assertEquals(asSymbols.decodePaths(Symbols.paths(got)), expected)
      val back = ZippyTranslations.bytesToBits(compiled.expression, compiled.routines)
      val result = BitSpace.evaluator(back.expression, routines = back.routines)
      assertEquals(compiled.decodePaths(back.decodePaths(result.vectors)), expected)
  }

  test("every byte-language constructor expands into eight bit levels and injects into symbols") {
    val id = FR[Int](RoutinePtr("identity"), Vector(PathRef("parameter")), Vector(SpaceMention("argument")), FS.Mention(SpaceMention("argument")))
    val rs = Map(id.name -> id)
    for expr <- expressions(0, 255) do
      val expected = ByteSpace.evaluator(expr, routines = rs).vectors
      val compiled = verify(expr, rs, Codes.bytesToBits, BitStorage, expected)
      val asSymbols = ZippyTranslations.bytesToSymbols(expr, rs)
      val symbolRoutines = asSymbols.routines.view.mapValues(Symbols.toRoutine).toMap
      val got = eval(Symbols.toSpace(asSymbols.expression))(using rc = symbolRoutines)
      assertEquals(asSymbols.decodePaths(Symbols.paths(got)), expected)
      assert(compiled.scanBounds.forall(_.maximumDepth.contains(8)))
      assert(compiled.scanBounds.forall(_.unrolled))
  }

  test("symbol constructors preserve lexical ranges, folds, boundaries and explicit grounded callbacks") {
    val a = PathItem("z"); val b = PathItem("a") // intern order deliberately opposes lexical order
    val interner = PathItemInterner(); interner.intern(a); interner.intern(b)
    val id = FR[PathItem](RoutinePtr("identity"), Vector(PathRef("parameter")), Vector(SpaceMention("argument")), FS.Mention(SpaceMention("argument")))
    val rs = Map(id.name -> id)
    val reference = Evaluator(VectorStorage[PathItem](Ordering.by(_.show)))
    for expr <- expressions(a, b) do
      val expected = reference(expr, routines = rs)
      verify(expr, rs, Codes.internedBits(interner), BitStorage, expected)
      verify(expr, rs, Codes.internedBytes(interner), ByteStorage, expected)
      verify(expr, rs, Codes.stringBytes, ByteStorage, expected)
  }

  test("translations introduce no grounded callbacks or symbols outside the injected alphabet") {
    val expr = FS.PrefixClosure(FS.Literal(Set(Vector(false, true), Vector(true))))
    val bytes = ZippyTranslations.bitsToBytes(expr)
    val bits = ZippyTranslations.bytesToBits(bytes.expression, bytes.routines)
    def pure[A](s: FS[A]): Unit = Symbols.foreach(s)({
      case FS.GroundedPS(_, _) | FS.GroundedSS(_, _) => fail("introduced grounded space callback")
      case _ => ()
    }, {
      case FP.GroundedPP(_, _) | FP.GroundedSP(_, _) => fail("introduced grounded path callback")
      case _ => ()
    })
    pure(bits.expression); bits.routines.values.foreach(r => pure(r.body))
    val symbols = ZippyTranslations.bitsToSymbols(expr)
    val seen = scala.collection.mutable.Set.empty[PathItem]
    def check(s: FS[PathItem]): Unit = Symbols.foreach(s)({
      case FS.Literal(ps) => seen ++= ps.flatten
      case _ => ()
    }, {
      case FP.Constant(ps) => seen ++= ps
      case _ => ()
    })
    check(symbols.expression); symbols.routines.values.foreach(r => check(r.body))
    assert(seen.subsetOf(Set(PathItem("__zippy_bit_0"), PathItem("__zippy_bit_1"))))
  }

  test("existing dependent corpus executes through all three symbol encodings") {
    val records = SpaceFuzzerCorpus.generate(160, 20261029L, maxDepth = 5, maxResult = 160, extended = true)
    for record <- records do
      val e = record.example
      val inputs = Map(SpaceFuzzer.argM -> e.arg)
      val routines = Symbols.reachable(e.program, SpaceFuzzer.routines)
      locally {
        assertEquals(ZippyTranslations.symbolsToBits(e.program, spaceInputs = inputs, routines = routines, extraSymbols = Vector(PathItem("g"))).runDecoded(), e.result)
        assertEquals(ZippyTranslations.stringsToBytes(e.program, spaceInputs = inputs, routines = routines).runDecoded(), e.result)
        assertEquals(ZippyTranslations.internedStringsToBytes(e.program, spaceInputs = inputs, routines = routines, extraSymbols = Vector(PathItem("g"))).runDecoded(), e.result)
      }
  }
