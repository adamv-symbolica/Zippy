package morkl

import munit.FunSuite

/** Regression tests for generated execution, not just encoded literal values. */
class AlphabetScalingTest extends FunSuite:
  private def p(items: String*): PathValue = PathValue(items.map(PathItem(_)).toList)
  private val h = PathRef("head")
  private val t = SpaceMention("tail")
  private val a = PathRef("acc")

  private def snapshot(size: Int): PathItemInterner =
    val interner = new PathItemInterner
    (0 until size).reverseIterator.foreach(i => interner.intern(PathItem(f"s$i%05d")))
    interner.intern(PathItem("t"))
    interner

  private def nodes[P](program: AlphabetProgram[P]): Int =
    var count = 0
    def visit(s: AlphabetSpace[P]): Unit =
      AlphabetSyntax.map[P, P](s)(_ => { count += 1; None }, _ => { count += 1; None }, identity, identity)
    visit(program.expression)
    program.routines.values.foreach(r => visit(r.body))
    count

  test("tails intersection, ordered range, and fold scale to 2000 interned heads on the default stack") {
    for n <- Vector(25, 200, 2000) do
      val interner = snapshot(n)
      val input = Space.Literal(SpaceValue((0 until n).map(i => p(f"s$i%05d", "t")).toSet + p()))
      val expressions = Vector(
        "intersection" -> Space.TailsIntersection(input),
        "range" -> Space.Range(input, -4, -1),
        "fold" -> Space.Fold(input, Path.Constant(p()), a, h, t,
          Space.Singleton(Path.Concat(Path.Deref(a), Path.Deref(h))), Path.Deref(h)))
      def check[T](codec: AlphabetEncoding[PathValue, T], name: String): Unit =
        expressions.foreach { (op, source) =>
          val expected = eval(source).paths
          val program = AlphabetTranslation.symbols(source, codec)
          val start = System.nanoTime()
          val actual = program.eval().map(codec.decode)
          val ms = (System.nanoTime() - start) / 1000000.0
          assertEquals(actual, expected, s"$name $op n=$n")
          println(f"alphabet-scale $name%-5s $op%-12s n=$n%4d nodes=${nodes(program)}%4d evalMs=$ms%.3f")
        }
      check(AlphabetEncodings.internedBits(interner), "bits")
      check(AlphabetEncodings.internedBytes(interner), "bytes")
  }

  test("range code size is independent of interner size and scanners grow only with code width") {
    val source = Space.Mention(SpaceMention("input"))
    val range = Space.Range(source, 0, 1)
    val fold = Space.Fold(source, Path.Constant(p()), a, h, t, Space.Singleton(Path.Deref(a)), Path.Deref(h))
    val small = snapshot(10)
    val large = snapshot(10000)
    val smallBits = AlphabetEncodings.internedBits(small)
    val largeBits = AlphabetEncodings.internedBits(large)
    assertEquals(nodes(AlphabetTranslation.symbols(range, smallBits)), nodes(AlphabetTranslation.symbols(range, largeBits)))
    assertEquals(nodes(AlphabetTranslation.symbols(range, AlphabetEncodings.internedBytes(small))), nodes(AlphabetTranslation.symbols(range, AlphabetEncodings.internedBytes(large))))
    assertEquals(nodes(AlphabetTranslation.symbols(fold, AlphabetEncodings.internedBytes(small))), nodes(AlphabetTranslation.symbols(fold, AlphabetEncodings.internedBytes(large))))
    val largeFold = AlphabetTranslation.symbols(fold, largeBits)
    assert(largeFold.routines.size <= 4 * largeBits.maxWidth.get + 10)
    assert(nodes(largeFold) <= 200 * largeBits.maxWidth.get + 200)
    val sorted = (0 until 10000).map(i => largeBits.encode(p(f"s$i%05d")))
    assertEquals(sorted.toVector.sortWith(BitPaths.compare(_, _) < 0), sorted.toVector)
  }

  private def compiled(program: AlphabetProgram[PathValue]): Set[PathValue] =
    val functions = program.routines.view.mapValues(r => transpile(SymbolAlphabet.toRoutine(r))).toMap.map((k, v) => k.s -> v)
    val top = transpile(Routine(RoutinePtr("alphabetCompiledTest"), Vector.empty, Vector.empty, SymbolAlphabet.toSpace(program.expression)))
    val frame = new Array[List[Int] | TrieSpace | Null](top.nodes.length)
    execT(top, collection.mutable.Stack(frame), functions.lift.unlift)
    frame.last.asInstanceOf[TrieSpace].toSpaceValue.paths

  test("scanner guards remain lazy in the eager compiled symbol executor") {
    val interner = snapshot(25)
    val input = Space.Literal(SpaceValue(Set(p("s00000", "t"), p("s00007", "t"), p("s00024"), p())))
    val expressions = Vector(
      Space.Iteration(input, h, t, Space.Singleton(Path.Deref(h))),
      Space.TailsIntersection(input),
      Space.PrefixClosure(input),
      Space.Fold(input, Path.Constant(p()), a, h, t, Space.Singleton(Path.Deref(a)), Path.Concat(Path.Deref(a), Path.Deref(h))))
    def check[T](codec: AlphabetEncoding[PathValue, T], injection: AlphabetEncoding[T, PathValue]): Unit =
      expressions.foreach { source =>
        val target = AlphabetTranslation(AlphabetTranslation.symbols(source, codec), injection)
        assertEquals(compiled(target).map(injection.decode).map(codec.decode), eval(source).paths)
      }
      // The compiled backend rejects host callbacks. The eager trie evaluator
      // supports them and catches attempts to decode incomplete codewords.
      val callback = Space.Iteration(input, h, t, Space.GroundedPS(Path.Deref(h), x => SpaceValue(Set(x))))
      val target = AlphabetTranslation(AlphabetTranslation.symbols(callback, codec), injection)
      val routines = target.routines.view.mapValues(SymbolAlphabet.toRoutine).toMap
      val result = evalTrieValue(SymbolAlphabet.toSpace(target.expression))(using PathContext.emptyMap, TrieSpaceContext.emptyMap, routines)
      assertEquals(result.paths.map(injection.decode).map(codec.decode), eval(callback).paths)
    check(AlphabetEncodings.internedBits(interner), AlphabetEncodings.bitsToSymbols)
    check(AlphabetEncodings.internedBytes(interner), AlphabetEncodings.bytesToSymbols)
    check(AlphabetEncodings.stringBytes, AlphabetEncodings.bytesToSymbols)
  }

  test("byte paths remain immutable keys in a hash-backed set after every public array access") {
    val arrays = Vector.tabulate(12)(i => Array(i.toByte, 0.toByte, 255.toByte))
    val paths = arrays.map(BytePath(_))
    val set = paths.toSet
    val hash = set.hashCode()
    arrays.foreach(_(0) = 100)
    paths.foreach(_.toArray(0) = 101)
    assertEquals(set.hashCode(), hash)
    paths.indices.foreach(i => assert(set.contains(BytePath(i, 0, 255))))
    assertEquals(BytePaths.take(paths.head, -1), BytePath())
    assertEquals(BytePaths.drop(paths.head, -1), paths.head)
  }

  test("native recursion and unbounded terminated scanners do not consume the JVM call stack") {
    val n = 4000
    val text = "x" * n
    val source = Space.Iteration(Space.Literal(SpaceValue(Set(p(text)))), h, t, Space.Singleton(Path.Deref(h)))
    val codec = AlphabetEncodings.stringBytes
    assertEquals(AlphabetTranslation.symbols(source, codec).eval().map(codec.decode), Set(p(text)))
    val f = RoutinePtr("walkLongBits")
    val input = SpaceMention("input")
    val body = BitSpace.Union(BitSpace.Singleton(BitPath.Constant(BitPaths.empty)),
      BitSpace.Iteration(BitSpace.Mention(input), h, t, BitSpace.Call(f, Vector.empty, Vector(BitSpace.Mention(t)))))
    val routine = AlphabetRoutine(f, Vector.empty, Vector(input), body)
    val program = AlphabetProgram(BitSpace.Call(f, Vector.empty, Vector(BitSpace.Literal(Set(BitPaths.fromDigits(Vector.fill(n)(0)))))), Map(f -> routine), BitPaths)
    assertEquals(program.eval(), Set(BitPaths.empty))
  }

  test("byte Range over 100000 paths agrees with the copying comparator") {
    val random = new scala.util.Random(78491)
    val paths = Vector.fill(100000)(BytePath(Array.fill[Byte](16)(random.nextInt(256).toByte))).toSet
    val values = paths.toVector
    val bean = java.lang.management.ManagementFactory.getThreadMXBean match
      case b: com.sun.management.ThreadMXBean if b.isThreadAllocatedMemorySupported => Some(b)
      case _ => None
    bean.foreach(_.setThreadAllocatedMemoryEnabled(true))
    def allocated: Long = bean.fold(0L)(_.getThreadAllocatedBytes(Thread.currentThread().getId))
    def measure[A](name: String)(body: => A): (A, Long) =
      val before = allocated
      val start = System.nanoTime()
      val result = body
      val elapsed = (System.nanoTime() - start) / 1000000.0
      val bytes = allocated - before
      println(f"alphabet-byte-range $name%-8s paths=${paths.size}%d ms=$elapsed%.3f allocatedBytes=$bytes%d")
      (result, bytes)
    // Warm both comparison paths before recording allocation and time.
    values.take(1000).sortWith((a, b) => java.util.Arrays.compareUnsigned(a.toArray, b.toArray) < 0)
    ByteSpace.eval(ByteSpace.Range(ByteSpace.Literal(values.take(1000).toSet), 1, -1))
    val (copying, copiedBytes) = measure("copying") {
      values.sortWith((a, b) => java.util.Arrays.compareUnsigned(a.toArray, b.toArray) < 0).slice(1, paths.size - 1).toSet
    }
    val (native, nativeBytes) = measure("native") {
      ByteSpace.eval(ByteSpace.Range(ByteSpace.Literal(paths), 1, -1))
    }
    assertEquals(native, copying)
    if bean.isDefined then assert(nativeBytes < copiedBytes / 2, s"native=$nativeBytes copying=$copiedBytes")
  }

  test("intersection traversal work follows encoded width rather than head count squared") {
    val n = 2000
    val codec = AlphabetEncodings.internedBits(snapshot(n))
    val input = (0 until n).map(i => p(f"s$i%05d", "t")).toSet
    val program = AlphabetTranslation.symbols(Space.TailsIntersection(Space.Literal(SpaceValue(input))), codec)
    var drops = 0L
    val measured = new FiniteDomain[BigInt]:
      val arity = 2
      val empty = BitPaths.empty
      def length(p: BigInt): Int = BitPaths.length(p)
      def concat(a: BigInt, b: BigInt): BigInt = BitPaths.concat(a, b)
      def take(p: BigInt, n: Int): BigInt = BitPaths.take(p, n)
      def drop(p: BigInt, n: Int): BigInt = { drops += 1; BitPaths.drop(p, n) }
      def compare(a: BigInt, b: BigInt): Int = BitPaths.compare(a, b)
      def single(d: Int): BigInt = BitPaths.single(d)
      def digit(p: BigInt): Int = BitPaths.digit(p)
      override def firstDigit(p: BigInt): Int = BitPaths.firstDigit(p)
    assertEquals(program.copy(domain = measured).eval().map(codec.decode), Set(p("t")))
    assert(drops < 20L * n * codec.maxWidth.get, s"$drops drops for $n heads of width ${codec.maxWidth.get}")
  }

  test("one-edge injections keep the original syntax and use only the declared symbols") {
    val source = BitSpace.Fold(BitSpace.Literal(Set(BitPaths.fromDigits(Vector(0, 1)), BitPaths.single(1))),
      BitPath.Constant(BitPaths.empty), a, h, t, BitSpace.Singleton(BitPath.Deref(a)), BitPath.Concat(BitPath.Deref(a), BitPath.Deref(h)))
    val original = AlphabetProgram(source, Map.empty[RoutinePtr, AlphabetRoutine[BigInt]], BitPaths)
    val symbols = AlphabetTranslation(original, AlphabetEncodings.bitsToSymbols)
    assertEquals(nodes(symbols), nodes(original))
    val names = Set(PathItem("false"), PathItem("true"))
    def inspect(path: PathValue): PathValue = { assert(path.items.forall(names)); path }
    AlphabetSyntax.map[PathValue, PathValue](symbols.expression)(_ => None, _ => None, inspect, identity)
    assertEquals(symbols.eval().map(AlphabetEncodings.bitsToSymbols.decode), original.eval())
  }
