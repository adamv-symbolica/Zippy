package morkl

import munit.FunSuite

class WordEncodingBackendTest extends FunSuite:
  test("generated scanner dispatch agrees on symbol eval, execZ and eager execT") {
    val x = SpaceMention("backend_input")
    val head = PathRef("backend_head")
    val tail = SpaceMention("backend_tail")
    val input = SpaceValue((0 until 12).map(i => PathValue(List(PathItem(s"s$i"), PathItem(s"s${11-i}")))).toSet + PathValue(Nil))
    val interner = new PathItemInterner; input.paths.foreach(interner.encode)
    val entry = Space.Iteration(Space.Mention(x), head, tail, Space.Wrap(Space.Mention(tail), Path.Deref(head)))
    def check[A](code: WordEncoding[PathValue, A], injection: WordEncoding[A, PathValue]): Unit =
      for unroll <- Vector(false, true) do
        checkMode(code, injection, unroll)
    def checkMode[A](code: WordEncoding[PathValue, A], injection: WordEncoding[A, PathValue], unroll: Boolean): Unit =
      val coded = code.compile(SymbolWordLanguage.space(entry), unrollBounded = unroll)
      val injected = injection.compile(coded.entry, coded.routines, unrollBounded = unroll)
      val body = SymbolWordLanguage.symbolSpace(injected.entry)
      val routines = injected.routines.view.mapValues(SymbolWordLanguage.symbolRoutine).toMap
      val values = SpaceValue(input.paths.map(p => injection.encode(code.encode(p))))
      val sc = SpaceContextMap(Map(x -> values))
      val expected = morkl.eval(body)(using PathContext.emptyMap, sc, routines)
      assertEquals(expected.paths, values.paths - PathValue(Nil))
      // The existing zipper compiler accepts acyclic calls, but explicitly
      // rejects general recursive routines outside its fixpoint fragment.
      if unroll then assertEquals(execZ(body)(using PathContext.emptyMap, ZipperSpaceContext.fromReference(sc), routines).toSpaceValue, expected)
      val root = Routine(RoutinePtr("backend_root"), Vector.empty, Vector(x), body)
      val graph = transpile(root)
      val callees = routines.values.map(r => r.name.s -> transpile(r)).toMap
      val stack = scala.collection.mutable.Stack(new Array[List[Int] | TrieSpace | Null](graph.nodes.length))
      stack.top(0) = TrieSpace.fromSpaceValue(values)
      execT(graph, stack, callees)
      assertEquals(stack.top.last.asInstanceOf[TrieSpace].toSpaceValue, expected)
    check(WordEncodings.internedSymbolsToBits(interner), WordEncodings.bitsToSymbols())
    check(WordEncodings.stringSymbolsToBytes(interner, maxSymbolBytes = Some(3)), WordEncodings.bytesToSymbols())
    check(WordEncodings.internedSymbolsToBytes(interner), WordEncodings.bytesToSymbols())
  }
