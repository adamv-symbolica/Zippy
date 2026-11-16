package morkl

import munit.FunSuite

object EvidenceEliminationExample:
  val a = SpaceMention("evidence_a")
  val b = SpaceMention("evidence_b")
  val x = PathRef("evidence_x")
  val y = PathRef("evidence_y")
  val at = SpaceMention("evidence_at")
  val bt = SpaceMention("evidence_bt")
  val epsilon = Space.Singleton(Path.ZERO)
  val expression = Space.TailsUnion(Space.Iteration(Space.Mention(a), x, at,
    Space.Iteration(Space.Mention(b), y, bt,
      Space.Wrap(Space.Mention(bt), Path.Concat(Path.Deref(x), Path.Deref(y))))))
  val optimized = Supercompiler.normalize(expression).space

class EvidenceEliminationTest extends FunSuite:
  import EvidenceEliminationExample.*
  private def p(s: String): Path = Path.Constant(Syntax.parse(s))
  private def value(s: String*): SpaceValue = SpaceValue(s.map(Syntax.parse).toSet)
  private def run(e: Space, av: SpaceValue, bv: SpaceValue = SpaceValue(Set.empty),
                  ps: Map[PathRef, PathValue] = Map.empty): SpaceValue =
    eval(e)(using PathContextMap(ps), SpaceContextMap(Map(a -> av, b -> bv)))
  private val words = Vector("", "a", "b", "a.a", "a.b", "b.a", "b.b").map(Syntax.parse)
  private val languages = (0 until (1 << words.size)).map { mask =>
    SpaceValue(words.zipWithIndex.collect { case (p, i) if (mask & (1 << i)) != 0 => p }.toSet)
  }

  test("evidence elimination matches an independent set oracle for all 16384 pairs") {
    val reduced = EvidenceElimination(expression)
    assertEquals(Supercompiler.stats(optimized).iterations, 1, optimized.show)
    assert(!Matching.freeRefs(optimized)(x) && !Matching.freeMentions(optimized)(at))
    assert(collect(optimized)(spre = { case Space.Range(Space.Subtraction(Space.Mention(`a`), _), 0, 1) => () })._1.nonEmpty)
    for av <- languages; bv <- languages do
      val expected = if av.paths.exists(_.items.nonEmpty) then SpaceValue(bv.paths.filter(_.items.nonEmpty))
        else SpaceValue(Set.empty)
      assertEquals(run(expression, av, bv), expected)
      assertEquals(run(reduced, av, bv), expected)
      assertEquals(run(optimized, av, bv), expected)
  }

  test("the optimized exec graph and trie evaluator agree on empty and shared-head sources") {
    val routine = Routine(RoutinePtr("evidence_law"), Vector.empty, Vector(a, b), optimized)
    val graph = optimize(transpile(routine))
    for av <- languages.filter(v => v.paths.size % 3 == 0); bv <- languages.take(16) do
      val context = TrieSpaceContextMap(Map(a -> TrieSpace.fromSpaceValue(av), b -> TrieSpace.fromSpaceValue(bv)))
      val expected = run(expression, av, bv)
      assertEquals(evalTrieValue(optimized)(using sc = context), expected)
      val frame = new Array[List[Int] | TrieSpace | Null](graph.nodes.size)
      frame(0) = context.resolve(a); frame(1) = context.resolve(b)
      execT(graph, collection.mutable.Stack(frame))
      assertEquals(frame.last.asInstanceOf[TrieSpace].toSpaceValue, expected)
  }

  test("invariant iterations preserve singleton rank windows without another selection") {
    val windows = Vector(0 -> 1, 2 -> 3, 3 -> 4, -1 -> 0, -2 -> -1,
      2 -> 2, 3 -> 2, -1 -> -2, -1 -> 1, -1 -> 2,
      Int.MinValue -> (Int.MinValue + 1), (Int.MaxValue - 1) -> Int.MaxValue)
    for (lo, hi) <- windows do
      assert(RangeBounds.cardinalityUpperBound(lo, hi).exists(_ <= 1))
      val selected = Space.Range(Space.Mention(a), lo, hi)
      val original = Space.Iteration(selected, x, at, Space.Mention(b))
      val reduced = EvidenceElimination(original)
      assertEquals(reduced, ReferenceHints.tag(original))
      assertEquals(EvidenceElimination(reduced), reduced)
      for av <- languages; bv <- Vector(value(), value(""), value("b"), value("", "a", "b.tail")) do
        val headed = run(selected, av).paths.exists(_.items.nonEmpty)
        val expected = if headed then bv else value()
        assertEquals(run(original, av, bv), expected)
        assertEquals(run(reduced, av, bv), expected)
    // Wider and open windows still need the headed cap: epsilon may be first.
    for (lo, hi) <- Vector(0 -> 2, 0 -> 0, 1 -> 0, 0 -> -1) do
      val wider = Space.Iteration(Space.Range(Space.Mention(a), lo, hi), x, at, Space.Mention(b))
      val reduced = EvidenceElimination(wider)
      assert(collect(reduced)(spre = {
        case Space.Range(Space.Subtraction(Space.Range(_, `lo`, `hi`), _), 0, 1) => ()
      })._1.nonEmpty)
      for av <- languages do
        assertEquals(run(reduced, av, value("b")), run(wider, av, value("b")))
  }

  test("Fibonacci rank witnesses compile a single logical range selector") {
    val inputs = Vector(value(), value(""), value("", "a"), value("a", "b", "b.tail"),
      value("", "a", "a.b", "b", "b.a"))
    for rank <- 2 to 4 do
      val witness = Space.Iteration(Space.Range(Space.Mention(a), rank, rank + 1), x, at, epsilon)
      val interner = PathItemInterner()
      inputs.flatMap(_.paths).foreach(interner.encode)
      val lowered = RadixTranslations.symbolsToBits(witness, interner = interner)
      assertEquals(lowered.program.routines.keys.count(_.s.contains("ordered_range")), 1)
      for input <- inputs do
        assertEquals(lowered.decode(lowered.program.eval(spaceArgs = Map(a -> lowered.encode(input)))), run(witness, input))
  }

  test("unwrap avoids capturing an external prefix with the producer's head name") {
    val producer = Space.Iteration(Space.Mention(a), x, at,
      Space.Wrap(Space.Mention(at), Path.Deref(x)))
    val original = Space.Unwrap(producer, Path.Deref(x))
    val reduced = EvidenceElimination(original)
    assert(Matching.freeRefs(reduced)(x), reduced.show)
    for av <- languages; prefix <- words do
      assertEquals(run(reduced, av, ps = Map(x -> prefix)), run(original, av, ps = Map(x -> prefix)))
  }

  test("prefix cancellation crosses concat association and partial constant factors") {
    val cases = Vector(
      Space.Unwrap(Space.Wrap(Space.Mention(a), Path.Deref(x)), Path.Deref(x)) -> Space.Mention(a),
      Space.Unwrap(Space.Wrap(Space.Mention(a), Path.Concat(Path.Deref(x), p("a.b"))), Path.Deref(x)) ->
        Space.Wrap(Space.Mention(a), p("a.b")),
      Space.Unwrap(Space.Wrap(Space.Mention(a), Path.Concat(p("a.b"), Path.Deref(x))), p("a")) ->
        Space.Wrap(Space.Mention(a), Path.Concat(p("b"), Path.Deref(x))),
      Space.Unwrap(Space.Wrap(Space.Mention(a), Path.Concat(p("a"), Path.Concat(p("b"), Path.Deref(x)))), p("a.b")) ->
        Space.Wrap(Space.Mention(a), Path.Deref(x)))
    for (original, expected) <- cases; av <- languages; prefix <- words.take(4) do
      assertEquals(run(EvidenceElimination(original), av, ps = Map(x -> prefix)), run(expected, av, ps = Map(x -> prefix)))
  }

  test("tails removes exactly one item, never a bounded or multi-item path") {
    for prefix <- words do
      val original = Space.TailsUnion(Space.Wrap(Space.Mention(a), Path.Constant(prefix)))
      for av <- languages do
        assertEquals(run(EvidenceElimination(original), av), run(original, av))
    for (ref, prefixes) <- Vector(x.known(1) -> words.filter(_.items.size == 1),
        x.known(2) -> words.filter(_.items.size == 2), x.bounded(1) -> words.filter(_.items.size <= 1)) do
      val original = Space.TailsUnion(Space.Wrap(Space.Mention(a), Path.Concat(Path.Deref(ref), p("a"))))
      val reduced = EvidenceElimination(original)
      for prefix <- prefixes; av <- languages do
        assertEquals(run(reduced, av, ps = Map(x -> prefix)), run(original, av, ps = Map(x -> prefix)))
  }

  test("consumers simplify unions only when cancellation pays for distribution") {
    val original = Space.Unwrap(Space.Union(Space.Wrap(Space.Mention(a), p("a")),
      Space.Wrap(Space.Mention(b), p("a.b"))), p("a"))
    val reduced = EvidenceElimination(original)
    assert(Supercompiler.stats(reduced).totalNodes < Supercompiler.stats(original).totalNodes)
    for av <- languages.take(16); bv <- languages.take(16) do
      assertEquals(run(reduced, av, bv), run(original, av, bv))
    val plain = Space.TailsUnion(Space.Union(Space.Mention(a), Space.Mention(b)))
    assertEquals(EvidenceElimination(plain), plain)
    val factored = Space.Union(Space.Unwrap(Space.Mention(a), p("a")), Space.Unwrap(Space.Mention(b), p("a")))
    assert(Supercompiler.normalize(factored).converged)
    val shared = Space.TailsUnion(Space.Wrap(Space.Mention(a), p("a")))
    EvidenceElimination(Space.Intersection(shared, shared)) match
      case Space.Intersection(left, right) => assert(left.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef])
      case other => fail(other.show)
  }

  test("free-variable checks respect nested shadowing, including tail mentions") {
    val inner = Space.Iteration(Space.Mention(b), x, at, Space.Wrap(Space.Mention(at), Path.Deref(x)))
    val original = Space.Iteration(Space.Mention(a), x, at, inner)
    val reduced = EvidenceElimination(original)
    assertEquals(Supercompiler.stats(reduced).iterations, 1, reduced.show)
    for av <- languages.take(16); bv <- languages do
      assertEquals(run(reduced, av, bv), run(original, av, bv))
    assertEquals(EvidenceElimination(reduced), reduced)
  }

  test("ignored binders do not bind or confer a one-item length on external references") {
    val ignored = PathRef("_"); val ignoredSpace = SpaceMention("_")
    val original = Space.TailsUnion(Space.Iteration(Space.Mention(a), ignored, ignoredSpace,
      Space.Wrap(Space.Mention(ignoredSpace), Path.Deref(ignored))))
    assert(Matching.freeRefs(original)(ignored))
    assert(Matching.freeMentions(original)(ignoredSpace))
    for av <- languages.take(16); prefix <- words do
      val pc = PathContextMap(Map(ignored -> prefix))
      val sc = SpaceContextMap(Map(a -> av, ignoredSpace -> value("a", "b.tail")))
      val expected = eval(original)(using pc, sc)
      assertEquals(eval(EvidenceElimination(original))(using pc, sc), expected)
      assertEquals(eval(Supercompiler.normalize(original).space)(using pc, sc), expected)
  }

  test("suffix spaces are reused under invariant prefixes") {
    val original = Space.Iteration(Space.Mention(a), x, at,
      Space.Wrap(Space.Mention(at), Path.Concat(p("rf.x"), Path.Deref(x))))
    val reduced = EvidenceElimination(original)
    assertEquals(Supercompiler.stats(reduced).iterations, 0)
    for av <- languages do assertEquals(run(reduced, av), run(original, av))
  }

  test("arbitrary iterations retain their combined tail groups across source unions") {
    val body = Space.Composition(Space.Intersection(Space.Mention(at), Space.Singleton(p("b"))),
      Space.Intersection(Space.Mention(at), Space.Singleton(p("c"))))
    val original = Space.Iteration(Space.Union(Space.Mention(a), Space.Mention(b)), x, at, body)
    val reduced = EvidenceElimination(original)
    assertEquals(run(reduced, value("a.b"), value("a.c")), value("b.c"))
    val invalid = Space.Union(Space.Iteration(Space.Mention(a), x, at, body),
      Space.Iteration(Space.Mention(b), x, at, body))
    assertEquals(run(invalid, value("a.b"), value("a.c")), value())
  }

  test("opaque bodies and prefixes keep their callback multiplicity and external scope") {
    var calls = 0
    val body = Space.GroundedSS(Space.Mention(b), v => { calls += 1; v })
    val original = Space.Iteration(Space.Mention(a), x, at, body)
    val reduced = EvidenceElimination(original)
    run(original, value("a", "b"), value("b")); val expected = calls; calls = 0
    run(reduced, value("a", "b"), value("b")); assertEquals(calls, expected)
    val prefix = Path.GroundedSP(Space.Mention(at), v => { calls += 1; v.paths.head })
    val wrapped = Space.Unwrap(Space.Iteration(Space.Mention(a), x, at,
      Space.Wrap(Space.Mention(at), Path.Deref(x))), prefix)
    val context = SpaceContextMap(Map(a -> value("a.a", "b.b"), at -> value("a")))
    calls = 0; val result = eval(wrapped)(using sc = context); val count = calls; calls = 0
    assertEquals(eval(EvidenceElimination(wrapped))(using sc = context), result)
    assertEquals(calls, count)
    val duplicate = Space.Union(body, body)
    calls = 0; run(duplicate, value(), value("a")); val duplicates = calls; calls = 0
    run(EvidenceElimination(duplicate), value(), value("a")); assertEquals(calls, duplicates)
  }

  test("logical evidence is eliminated before all four symbol encodings") {
    val av = value("", "alpha", "beta.tail")
    val bv = value("", "long-symbol", "b.a", "a.b")
    val expected = run(expression, av, bv)
    val interner = PathItemInterner(); av.paths.foreach(interner.encode); bv.paths.foreach(interner.encode)
    val bit = RadixTranslations.symbolsToBits(expression, interner = interner)
    assertEquals(bit.decode(bit.program.eval(spaceArgs = Map(a -> bit.encode(av), b -> bit.encode(bv)))), expected)
    val bytes = Vector(RadixTranslations.stringsToBytes(expression),
      RadixTranslations.lengthPrefixedStringsToBytes(expression),
      RadixTranslations.internedSymbolsToBytes(expression, interner = interner))
    for lowered <- bytes do
      assertEquals(lowered.decode(lowered.program.eval(spaceArgs = Map(a -> lowered.encode(av), b -> lowered.encode(bv)))), expected)
      if java.lang.Boolean.getBoolean("morkl.pathmap.test") then
        val native = RustPathMapBackend.bytes(lowered.program.entry, lowered.program.routines)
        assertEquals(native.eval(spaces = Map(a -> lowered.encode(av).paths, b -> lowered.encode(bv).paths)).map(lowered.encoding.decode), expected.paths)
    if java.lang.Boolean.getBoolean("morkl.openfst.test") then
      assertEquals(OpenFstBackend.symbols(expression).eval(spaces = Map(a -> av.paths, b -> bv.paths)), expected.paths)
      val native = OpenFstBackend.bits(bit.program.entry, bit.program.routines)
      assertEquals(native.eval(spaces = Map(a -> bit.encode(av).paths, b -> bit.encode(bv).paths)).map(bit.encoding.decode), expected.paths)
  }
