package morkl

import munit.FunSuite

class OpenFstGraphCompilerTest extends FunSuite:
  private val input = SpaceMention("input")
  private val h = PathRef("h")
  private val t = SpaceMention("t")
  private val loop = Space.Iteration(Space.Mention(input),h,t,
    Space.Singleton(Path.Concat(Path.Deref(h),Path.Deref(h))))

  test("graph constants preserve symbol boundaries and exact UTF-16 code units") {
    val paths = Vector(Nil,List("a.b"),List(""),List("a","b"),List("path64:"),
      List("path16:S"),List("", ".", "\uD800", "\uDC00", "\uD83D\uDE00"))
    for items <- paths do
      val path = PathValue(items.map(PathItem(_)))
      assertEquals(PathConstantCodec.decode(PathConstantCodec.encode(path)),path)
      val compiled = OpenFstBackend.symbols(Space.Wrap(Space.Mention(input),Path.Constant(path)))
      assert(compiled.source.nonEmpty)
      if java.lang.Boolean.getBoolean("morkl.openfst.test") then
        val value = PathValue(List(PathItem("tail")))
        assertEquals(compiled.eval(spaces=Map(input->Set(value))),Set(PathValue(path.items ++ value.items)))
        val unwrapped = OpenFstBackend.symbols(Space.Unwrap(Space.Mention(input),Path.Constant(path)))
        assertEquals(unwrapped.eval(spaces=Map(input->Set(PathValue(path.items ++ value.items)))),Set(value))
  }

  test("optimized graph positions become locals and iteration becomes an inline loop") {
    val compiled = OpenFstBackend.symbols(loop)
    assert(compiled.source.contains("for (const auto&"))
    assert(compiled.source.contains("_branch : v"))
    assert(!compiled.source.contains("iterate("))
    assert(!compiled.source.contains("SFn"))
    assert(!compiled.source.contains("bind_path"))
    assert(!compiled.source.contains("static ZIPPY_NOINLINE"))
    assert(compiled.graphDescription.contains("Iteration"))
    val body = compiled.code.substring(compiled.code.indexOf("for (const auto&"))
    assert(!body.takeWhile(_ != '}').contains("e.paths"))
    assert(!body.takeWhile(_ != '}').contains("e.spaces"))
  }

  test("the compiler consumes the existing optimized exec graph directly") {
    val routine = Routine(RoutinePtr("direct"),Vector.empty,Vector(input),loop)
    val graph = Supercompiler.compile(routine).graph.get
    val compiled = OpenFstBackend.fromGraph(graph)
    assert(compiled.graphDescription.contains(graph.show))
    assert(compiled.source.contains("for (const auto&"))
    assert(!compiled.source.contains("switch(op"))
  }

  test("helper inlining does not capture an outer loop head in a nested scanner") {
    val p = PathRef("prefix")
    val helper = RoutinePtr("helper")
    val routine = Routine(helper,Vector(p),Vector(t),Space.Iteration(Space.Mention(t),h,SpaceMention("rest"),
      Space.Singleton(Path.Concat(Path.Deref(p),Path.Deref(h)))))
    val source = Space.Iteration(Space.Mention(input),h,t,Space.Call(helper,Vector(Path.Deref(h)),Vector(Space.Mention(t))))
    val data = SpaceValue(Set(PathValue(List(PathItem("a"),PathItem("b")))))
    val inlined = Lower.inline(using Map(helper -> routine).lift.unlift)(source)
    assertEquals(eval(inlined)(using sc=SpaceContextMap(Map(input -> data))),
      eval(source)(using sc=SpaceContextMap(Map(input -> data)),rc=Map(helper -> routine)))
  }

  test("alpha conversion preserves nested shadowing and ignored captures before specialization") {
    val outer=PathRef("outer");val ignored=PathRef("_");val rest=SpaceMention("_")
    val value=SpaceValue(Set(PathValue(Nil),PathValue(List(PathItem("a"))),
      PathValue(List(PathItem("a"),PathItem("tail"))),PathValue(List(PathItem("m"),PathItem("a")))))
    val literal=Space.Literal(value)
    val nested=Space.Iteration(literal,h,t,Space.Union(Space.Singleton(Path.Deref(h)),
      Space.Iteration(Space.Mention(t),h,t,Space.Wrap(Space.Mention(t),Path.Concat(Path.Deref(h),Path.Deref(outer))))))
    val skip=Space.Iteration(literal,ignored,rest,Space.Wrap(Space.Mention(rest),Path.Deref(ignored)))
    val pc=PathContextMap(Map(outer->PathValue(List(PathItem("m"))),ignored->PathValue(List(PathItem("a")))))
    val sc=SpaceContextMap(Map(rest->value))
    for source <- Vector(nested,skip) do
      val expected=eval(source)(using pc,sc)
      assertEquals(eval(Matching.canon(source))(using pc,sc),expected)
      if java.lang.Boolean.getBoolean("morkl.openfst.test") then
        assertEquals(OpenFstBackend.symbols(source).eval(pc.m,sc.m.view.mapValues(_.paths).toMap),expected.paths)
    val name=RoutinePtr("ignored-formals")
    val routine=Routine(name,Vector(ignored),Vector(rest),Space.Iteration(Space.Mention(rest),ignored,rest,
      Space.Wrap(Space.Mention(rest),Path.Deref(ignored))))
    val call=Space.Call(name,Vector(Path.Constant(PathValue(List(PathItem("prefix"))))),Vector(literal))
    val expected=eval(call)(using rc=Map(name->routine))
    if java.lang.Boolean.getBoolean("morkl.openfst.test") then
      assertEquals(OpenFstBackend.symbols(call,Map(name->routine)).eval(),expected.paths)
  }

  if java.lang.Boolean.getBoolean("morkl.openfst.test") then
    test("a shared recursive call stays inside the guards that demand it") {
      val left = SpaceMention("left");val right = SpaceMention("right")
      val name = RoutinePtr("diverge")
      val call = Space.Call(name,Vector.empty,Vector(Space.Mention(input)))
      val routine = Routine(name,Vector.empty,Vector(input),call)
      val source = Space.Union(Space.Composition(Space.Mention(left),call),Space.Composition(Space.Mention(right),call))
      val compiled = OpenFstBackend.symbols(source,Map(name -> routine))
      val spaces = Map(left -> Set.empty[PathValue],right -> Set.empty[PathValue],input -> Set(PathValue(Nil)))
      assertEquals(compiled.eval(spaces=spaces),Set.empty[PathValue])
      val nested = Space.Iteration(Space.Mention(left),h,t,
        Space.Wrap(call,Path.Constant(PathValue(List(PathItem("prefix"))))))
      assertEquals(OpenFstBackend.symbols(nested,Map(name -> routine)).eval(spaces=spaces),Set.empty[PathValue])
    }
    test("opaque functions are not executed during compilation or in empty loop bodies") {
      var calls = 0
      val missing = SpaceMention("missing")
      val opaque = Space.GroundedSS(Space.Mention(missing),v => {calls += 1;v})
      val source = Space.Iteration(Space.Mention(input),h,t,Space.Wrap(opaque,Path.Deref(h)))
      val compiled = OpenFstBackend.symbols(source)
      assertEquals(calls,0)
      assertEquals(compiled.eval(spaces=Map(input -> Set.empty[PathValue])),Set.empty[PathValue])
      assertEquals(calls,0)
      val conditional = OpenFstBackend.symbols(Space.Composition(Space.Empty,opaque))
      assertEquals(conditional.eval(),Set.empty[PathValue])
      assertEquals(calls,0)
    }
    test("dynamic loops, fold updates and fixpoints compile natively in all dialects") {
      def verify[V,L <: DigitLanguage[V]](source: L): Unit =
        import source.{Space as S,Path as P}
        val a = PathRef("acc");val fix = SpaceMention("fix")
        val data = Vector(Vector.empty,Vector(0),Vector(1),Vector(0,1),Vector(1,0,1)).map(source.paths.fromDigits).toSet
        val expression = Vector(
          S.Iteration(S.Mention(input),h,t,S.Singleton(P.Concat(P.Deref(h),P.Deref(h)))),
          S.Fold(S.Mention(input),P.ZERO,a,h,t,S.Singleton(P.Concat(P.Deref(a),P.Deref(h))),P.Concat(P.Deref(a),P.Deref(h))),
          S.Fixpoint(S.Mention(input),fix,S.TailsUnion(S.Mention(fix))),
          S.TailsIntersection(S.Mention(input)),
          S.Head(S.Range(S.Intersection(S.Mention(input),source.literal(source.paths.empty,
            source.paths.fromDigits(Vector(0,0)),source.paths.fromDigits(Vector(1)))),0,2)),
          S.Composition(S.Mention(input),S.Mention(input)))
        val programs = expression.map(s => OpenFstBackend.compileDigits(source)(s,Map.empty))
        val native = OpenFstBackend.Session(programs)
        try expression.zipWithIndex.foreach { (s,i) =>
          val expected = source.eval(s,spaceArgs=Map(input -> source.SpaceValue(data)))
          assertEquals(native.eval(i,programs(i))(Map.empty,Map(input -> data)),expected.paths,s"${source.paths.alphabetSize} / $i")
        } finally native.close()
      verify(Zippy_bits);verify(Zippy_bytes)
    }
    test("direct optimized graphs and graph callees run without value stacks") {
      val source = Routine(RoutinePtr("direct"),Vector.empty,Vector(input),loop)
      val compiled = OpenFstBackend.fromGraph(Supercompiler.compile(source).graph.get)
      val value = SpaceValue(Set(PathValue(List(PathItem("a"))),PathValue(List(PathItem("b"),PathItem("a")))))
      assertEquals(compiled.eval(spaces=Map(input -> value.paths)),
        eval(loop)(using sc=SpaceContextMap(Map(input -> value))).paths)
    }
