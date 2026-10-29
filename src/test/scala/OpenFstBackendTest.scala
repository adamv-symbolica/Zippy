package morkl

import munit.FunSuite

class OpenFstBackendTest extends FunSuite:
  private def pv(items: String*): PathValue = PathValue(items.map(PathItem(_)).toList)
  private def programs[V,L <: DigitLanguage[V]](source: L): Vector[source.Space] =
    import source.{Path as P, Space as S}
    val src = source.literal(Vector(Vector.empty,Vector(0),Vector(1),Vector(0,1),Vector(1,0,1)).map(source.paths.fromDigits)*)
    val other = source.literal(source.paths.empty,source.paths.fromDigits(Vector(0)))
    val h=PathRef("h");val t=SpaceMention("t");val a=PathRef("a");val f=SpaceMention("fix")
    Vector(S.Empty,src,S.Singleton(P.Concat(source.digit(0),source.digit(1))),
      S.Union(src,other),S.Intersection(src,other),S.Subtraction(src,other),S.Restriction(src,other),S.Raffination(src,other),
      S.Composition(src,other),S.Wrap(src,source.digit(1)),S.Unwrap(src,source.digit(0)),S.TailsUnion(src),S.TailsIntersection(src),
      S.PrefixClosure(src),S.SuffixClosure(src),S.TailsClosure(src),
      S.Iteration(src,h,t,S.Wrap(S.Mention(t),P.Deref(h))),
      S.Fold(src,P.ZERO,a,h,t,S.Singleton(P.Concat(P.Deref(a),P.Deref(h))),P.Concat(P.Deref(a),P.Deref(h))),
      S.Fixpoint(src,f,S.TailsUnion(S.Mention(f))),
      S.GroundedPS(source.digit(0),v => source.SpaceValue(Set(v))),S.GroundedSS(src,v => v),
      S.Singleton(P.GroundedPP(source.digit(1),identity)),S.Singleton(P.GroundedSP(src,_ => source.paths.empty))) ++
      Vector((0,0),(0,1),(1,4),(-3,0),(0,-1),(Int.MinValue,Int.MaxValue),(Int.MaxValue,0),(4,2)).map((lo,hi) => S.Range(src,lo,hi))

  test("all operations transpile to native calls in all three dialects") {
    val b = OpenFstBackend.bits(BitSpace.TailsClosure(Zippy_bits.literal(BigInt(1),BigInt(2))))
    assert(b.source.contains("suffix_closure"))
    assert(b.source.contains("int main()"))
    assert(!b.source.contains("enum Space"))
    val s = OpenFstBackend.symbols(Space.Range(Space.Literal(SpaceValue(Set(pv("z"),pv("a")))),0,1)).source
    assert(s.contains("configure_symbols"))
    assert(s.contains("c.symbolic=true"))
    val unused=RoutinePtr("unused")
    val dead=Routine(unused,Vector.empty,Vector.empty,Space.GroundedSS(Space.Empty,identity))
    val pruned=OpenFstBackend.symbols(Space.Singleton(Path.ZERO),Map(unused->dead))
    assertEquals(pruned.callbackCount,0)
    assert(!pruned.source.contains("static ZIPPY_NOINLINE Space routine_"))
  }

  if java.lang.Boolean.getBoolean("morkl.openfst.test") then
    test("native bit and byte backends implement every operation") {
      def verify[V,L <: DigitLanguage[V]](source: L): Unit =
        val expressions = programs(source)
        val native = expressions.map(e => OpenFstBackend.compileDigits(source)(e,Map.empty))
        val session = OpenFstBackend.Session(native)
        try expressions.zipWithIndex.foreach { (e,i) =>
          assertEquals(session.eval(i,native(i))(Map.empty,Map.empty),source.eval(e).paths,s"case $i")
        } finally session.close()
      verify(Zippy_bits);verify(Zippy_bytes)
    }
    test("native symbol backend preserves string order and dynamic grounded symbols") {
      val text = Vector("z","a","",0.toChar.toString,"😀",0xd800.toChar.toString,"é")
      val value = Space.Literal(SpaceValue(text.map(s => pv(s,"tail")).toSet + pv()))
      val h=PathRef("h");val t=SpaceMention("t");val acc=PathRef("acc")
      val generated = Space.GroundedSS(value,_ => SpaceValue(Set(pv("new"),pv("", "dynamic"),pv("😀"))))
      val expressions = Vector(value,Space.Range(value,0,3),Space.Range(generated,0,2),
        Space.Fold(value,Path.ZERO,acc,h,t,Space.Singleton(Path.Concat(Path.Deref(acc),Path.Deref(h))),Path.Concat(Path.Deref(acc),Path.Deref(h))),
        Space.Singleton(Path.GroundedPP(Path.Constant(pv("a")),_ => pv("new"))),
        Space.Singleton(Path.GroundedSP(value,_ => pv("new","path"))),
        Space.GroundedPS(Path.Constant(pv("a")),_ => SpaceValue(Set(pv("x"),pv("y")))))
      val native = expressions.map(e => OpenFstBackend.symbols(e))
      val session = OpenFstBackend.Session(native)
      try expressions.zipWithIndex.foreach((e,i) => assertEquals(session.eval(i,native(i))(Map.empty,Map.empty),eval(e).paths))
      finally session.close()
    }
    test("native calls, mutual recursion, stationary calls and lexical shadowing") {
      val p=PathRef("p");val s=SpaceMention("s");val h=PathRef("h");val t=SpaceMention("t")
      val walk=RoutinePtr("walk");val helper=RoutinePtr("helper")
      val defs=Map(walk -> Routine(walk,Vector(p),Vector(s),Space.Union(Space.Singleton(Path.Deref(p)),
        Space.Iteration(Space.Mention(s),h,t,Space.Call(helper,Vector(Path.Deref(h)),Vector(Space.Mention(t)))))),
        helper -> Routine(helper,Vector(p),Vector(s),Space.Call(walk,Vector(Path.Deref(p)),Vector(Space.Mention(s)))))
      val expression=Space.Call(walk,Vector(Path.Constant(pv("seed"))),Vector(Space.Literal(SpaceValue(Set(pv("a","b"),pv("z"))))))
      assertEquals(OpenFstBackend.symbols(expression,defs).eval(),eval(expression)(using rc=defs).paths)
      val f=RoutinePtr("stationary");val call=Space.Call(f,Vector.empty,Vector(Space.Mention(s)))
      val routine=Routine(f,Vector.empty,Vector(s),Space.Union(Space.Mention(s),call))
      val entry=Space.Call(f,Vector.empty,Vector(Space.Singleton(Path.ZERO)))
      assertEquals(OpenFstBackend.symbols(entry,Map(f->routine)).eval(),Set(pv()))
      val guarded=Routine(f,Vector.empty,Vector(s),Space.Union(Space.Composition(Space.Empty,call),call))
      assertEquals(OpenFstBackend.symbols(entry,Map(f->guarded)).eval(),Set.empty[PathValue])
      val missing=PathRef("missing")
      val empty=Space.Iteration(Space.Empty,h,t,Space.Singleton(Path.Deref(missing)))
      assertEquals(OpenFstBackend.symbols(empty).eval(),Set.empty[PathValue])
    }
    test("all 256 labels and byte zero are distinct from OpenFst epsilon") {
      val all=Zippy_bytes.literal((0 until 256).map(i => BytePathValue(i,0,255))*)
      val expressions=Vector(all,ByteSpace.Head(all),ByteSpace.Range(all,0,1),ByteSpace.Range(all,-1,0))
      val native=expressions.map(e => OpenFstBackend.bytes(e))
      val session=OpenFstBackend.Session(native)
      try expressions.zipWithIndex.foreach((e,i) => assertEquals(session.eval(i,native(i))(Map.empty,Map.empty),Zippy_bytes.eval(e).paths))
      finally session.close()
    }
    test("native ranges select from compressed spaces larger than 64-bit cardinalities") {
      val both=Zippy_bits.literal(BigInt(2),BigInt(3))
      val huge=(0 until 80).foldLeft(Zippy_bits.epsilon)((a,_)=>BitSpace.Composition(a,both))
      val expressions=Vector(BitSpace.Range(huge,0,1),BitSpace.Range(huge,-1,0),BitSpace.Range(huge,-4,-1))
      val native=expressions.map(e=>OpenFstBackend.bits(e))
      val session=OpenFstBackend.Session(native)
      try
        assertEquals(session.eval(0,native(0))(Map.empty,Map.empty),Set(BigInt(1)<<80))
        assertEquals(session.eval(1,native(1))(Map.empty,Map.empty),Set((BigInt(1)<<81)-1))
        assertEquals(session.eval(2,native(2))(Map.empty,Map.empty),(4 to 2 by -1).map(i=>(BigInt(1)<<81)-i).toSet)
      finally session.close()
    }
    test("grounded functions accept native C++ bindings without a JVM callback") {
      val value=Space.Literal(SpaceValue(Set(pv("a","tail"),pv("b","other"))))
      val expression=Space.GroundedSS(value,_=>throw AssertionError("JVM callback must not execute"))
      val base=OpenFstBackend.symbols(expression)
      assertEquals(base.callbackCount,1)
      assert(!base.isStandalone)
      val native=base.withNativeFunctions(Map(0->"return tails_union(argument);"))
      assert(native.isStandalone)
      assertEquals(native.eval(),Set(pv("tail"),pv("other")))
    }
    test("large translated codebooks compile to direct prefix selectors") {
      val names=(0 until 4096).map(i=>f"head$i%04d").toVector:+"tail"
      val table=PathItemInterner();names.foreach(s=>table.intern(PathItem(s)))
      val input=SpaceMention("ordered-input");val a=PathRef("acc");val h=PathRef("head");val t=SpaceMention("tail")
      val expr=Space.Fold(Space.Mention(input),Path.ZERO,a,h,t,Space.Singleton(Path.Concat(Path.Deref(a),Path.Deref(h))),Path.Concat(Path.Deref(a),Path.Deref(h)))
      val value=SpaceValue(Vector(0,7,13,255,2048,4095).map(i=>pv(names(i),"tail")).toSet)
      val expected=eval(expr)(using sc=SpaceContextMap(Map(input->value)))
      val bit=RadixTranslations.symbolsToBits(expr,interner=table)
      val binary=OpenFstBackend.bits(bit.program.entry,bit.program.routines)
      assert(binary.code.linesIterator.exists(l=>l.contains("first_fibonacci")&&l.contains(",true)")));assert(binary.source.length<2000000)
      assertEquals(bit.decode(BitSpaceValue(binary.eval(spaces=Map(input->bit.encode(value).paths)))),expected)
      val byte=RadixTranslations.internedSymbolsToBytes(expr,interner=table)
      val octets=OpenFstBackend.bytes(byte.program.entry,byte.program.routines)
      assert(octets.code.linesIterator.exists(l=>l.contains("first_matching")&&l.contains(",true)")));assert(octets.source.length<2000000)
      assertEquals(byte.decode(ByteSpaceValue(octets.eval(spaces=Map(input->byte.encode(value).paths)))),expected)
    }
