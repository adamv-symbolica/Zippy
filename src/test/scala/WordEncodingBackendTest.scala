package morkl

import munit.FunSuite

class WordEncodingBackendTest extends FunSuite:
  test("generated scanner dispatch agrees on symbol eval, execZ and eager execT") {
    val x = SpaceMention("backend_input")
    val formal = SpaceMention("backend_formal")
    val head = PathRef("backend_head")
    val tail = SpaceMention("backend_tail")
    val name = RoutinePtr("backend_reconstruct")
    val sourceRoutine = Routine(name, Vector.empty, Vector(formal),
      Space.Union(Space.Intersection(Space.Mention(formal), Space.Singleton(Path.ZERO)),
        Space.Iteration(Space.Mention(formal), head, tail,
          Space.Wrap(Space.Call(name, Vector.empty, Vector(Space.Mention(tail))), Path.Deref(head)))))
    val input = SpaceValue((0 until 12).map(i => PathValue(List(PathItem(s"s$i"), PathItem(s"s${11-i}")))).toSet + PathValue(Nil))
    val interner = new PathItemInterner; input.paths.foreach(interner.encode)
    val entry = Space.Call(name, Vector.empty, Vector(Space.Mention(x)))
    def check[A](code: WordEncoding[PathValue, A], injection: WordEncoding[A, PathValue]): Unit =
      val coded = code.compile(SymbolWordLanguage.space(entry), Map(name -> SymbolWordLanguage.routine(sourceRoutine)), unrollBounded = false)
      val injected = injection.compile(coded.entry, coded.routines, unrollBounded = false)
      val body = SymbolWordLanguage.symbolSpace(injected.entry)
      val routines = injected.routines.view.mapValues(SymbolWordLanguage.symbolRoutine).toMap
      val values = SpaceValue(input.paths.map(p => injection.encode(code.encode(p))))
      val sc = SpaceContextMap(Map(x -> values))
      val expected = morkl.eval(body)(using PathContext.emptyMap, sc, routines)
      assertEquals(expected, values)
      assertEquals(execZ(body)(using PathContext.emptyMap, ZipperSpaceContext.fromReference(sc), routines).toSpaceValue, expected)
      val root = Routine(RoutinePtr("backend_root"), Vector.empty, Vector(x), body)
      val graph = transpile(root)
      val callees = routines.values.map(r => r.name.s -> transpile(r)).toMap
      val stack = scala.collection.mutable.Stack(new Array[List[Int] | TrieSpace | Null](graph.nodes.length))
      stack.top(0) = TrieSpace.fromSpaceValue(values)
      execT(graph, stack, callees)
      assertEquals(stack.top.last.asInstanceOf[TrieSpace].toSpaceValue, expected)
    check(WordEncodings.internedSymbolsToBits(interner), WordEncodings.bitsToSymbols())
    check(WordEncodings.stringSymbolsToBytes(interner), WordEncodings.bytesToSymbols())
    check(WordEncodings.internedSymbolsToBytes(interner), WordEncodings.bytesToSymbols())
  }
