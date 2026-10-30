package morkl

import munit.FunSuite

class RustPathMapBackendTest extends FunSuite:
  override val munitTimeout = scala.concurrent.duration.Duration(180,"s")
  import Zippy_bytes.{Space as S,Path as P}
  private val x=SpaceMention("input")
  private val h=PathRef("head");private val t=SpaceMention("tail");private val acc=PathRef("acc")
  private val input=S.Mention(x)
  private val values=Set(BytePathValue(),BytePathValue(0),BytePathValue(255),BytePathValue(0,1),BytePathValue(1,0,255))
  private val other=Zippy_bytes.literal(BytePathValue(),BytePathValue(0))
  private def operations: Vector[ByteSpace] = Vector(S.Empty,input,
    S.Singleton(P.Concat(P.Deref(h),Zippy_bytes.digit(255))),
    S.Union(input,other),S.Intersection(input,other),S.Subtraction(input,other),S.Restriction(input,other),S.Raffination(input,other),
    S.Composition(input,other),S.Wrap(input,Zippy_bytes.digit(255)),S.Unwrap(input,Zippy_bytes.digit(0)),
    S.TailsUnion(input),S.TailsIntersection(input),S.PrefixClosure(input),S.SuffixClosure(input),S.TailsClosure(input),
    S.Iteration(input,h,t,S.Wrap(S.Mention(t),P.Deref(h))),
    S.Fold(input,P.ZERO,acc,h,t,S.Singleton(P.Concat(P.Deref(acc),P.Deref(h))),P.Concat(P.Deref(acc),P.Deref(h))),
    S.Fixpoint(input,t,S.TailsUnion(S.Mention(t))),
    S.GroundedPS(P.Deref(h),p=>Zippy_bytes.SpaceValue(Set(p))),S.GroundedSS(input,identity),
    S.Singleton(P.GroundedPP(P.Deref(h),identity)),S.Singleton(P.GroundedSP(input,_=>BytePathValue(0,255)))) ++
    Vector((0,0),(0,1),(1,4),(-3,0),(0,-1),(Int.MinValue,Int.MaxValue),(Int.MaxValue,0),(4,2)).map((a,b)=>S.Range(input,a,b))

  test("Rust source consumes the optimized graph and emits lexical iteration loops") {
    val p=RustPathMapBackend.bytes(S.Iteration(input,h,t,S.Composition(S.Mention(t),S.Singleton(P.Deref(h)))))
    assert(p.graphDescription.nonEmpty)
    assert(p.source.contains("for v"))
    assert(p.source.contains(".child_mask().iter()"))
    assert(!p.source.contains("enum Space"))
    assert(!p.source.contains("eval("))
    assert(p.pathIds.isEmpty)
    assertEquals(p.spaceIds.keySet,Set(x))
  }

  if java.lang.Boolean.getBoolean("morkl.pathmap.test") then
    test("an existing optimized executor graph compiles without an expression round trip") {
      val ref=SpaceMention("graph-input")
      val prefix=Path.Constant(PathValue(List(PathItem("#digit:000"),PathItem("#digit:255"))))
      val body=Space.Wrap(Space.Mention(ref),prefix)
      val graph=optimize(transpile(Routine(RoutinePtr("graph"),Vector.empty,Vector(ref),body)))
      val p=RustPathMapBackend.fromGraph(graph)
      assertEquals(p.eval(spaces=Map(ref->Set(BytePathValue(),BytePathValue(2)))),Set(BytePathValue(0,255),BytePathValue(0,255,2)))
    }
    test("generated Rust implements all byte cases on dynamic inputs") {
      val expressions=operations
      val programs=expressions.map(RustPathMapBackend.bytes(_))
      val session=RustPathMapBackend.Session(programs)
      val paths=Map(h->BytePathValue(128))
      val spaces=Map(x->values)
      try expressions.zipWithIndex.foreach { (e,i)=>
        val expected=Zippy_bytes.eval(e,paths,spaces.view.mapValues(Zippy_bytes.SpaceValue(_)).toMap).paths
        assertEquals(session.eval(i,programs(i))(paths,spaces),expected,s"case $i: $e")
      } finally session.close()
    }
    test("all 256 labels, empty guards, recursion and lexical captures") {
      val all=(0 until 256).map(i=>BytePathValue(i,0,255)).toSet + BytePathValue()
      val f=RoutinePtr("walk");val mention=SpaceMention("s")
      val routine=Zippy_bytes.Routine(f,Vector.empty,Vector(mention),
        S.Union(S.Mention(mention),S.Iteration(S.Mention(mention),h,t,S.Call(f,Vector.empty,Vector(S.Mention(t))))))
      val expr=S.Call(f,Vector.empty,Vector(input))
      val p=RustPathMapBackend.bytes(expr,Map(f->routine))
      val expected=Zippy_bytes.eval(expr,spaceArgs=Map(x->Zippy_bytes.SpaceValue(all)),routines=Map(f->routine)).paths
      assertEquals(p.eval(spaces=Map(x->all)),expected)
      val missing=PathRef("unbound")
      val empty=S.Iteration(S.Empty,h,t,S.Singleton(P.Deref(missing)))
      assertEquals(RustPathMapBackend.bytes(empty).eval(),Set.empty[BytePathValue])
      val guarded=S.Composition(input,S.Singleton(P.Deref(missing)))
      assertEquals(RustPathMapBackend.bytes(guarded).eval(spaces=Map(x->Set.empty)),Set.empty[BytePathValue])
      val guardedMention=S.Composition(input,S.Mention(SpaceMention("unbound-space")))
      assertEquals(RustPathMapBackend.bytes(guardedMention).eval(spaces=Map(x->Set.empty)),Set.empty[BytePathValue])
      val stationary=Zippy_bytes.Routine(f,Vector.empty,Vector(mention),S.Union(S.Mention(mention),S.Call(f,Vector.empty,Vector(S.Mention(mention)))))
      assertEquals(RustPathMapBackend.bytes(expr,Map(f->stationary)).eval(spaces=Map(x->all)),all)
    }
    test("native benchmarks execute the graph and count results inside the timed region") {
      val p=RustPathMapBackend.bytes(S.Composition(input,input))
      val session=RustPathMapBackend.Session(Vector(p))
      try
        val stats=session.benchmark(0,p,3)(Map.empty,Map(x->values))
        val expected=Zippy_bytes.eval(S.Composition(input,input),spaceArgs=Map(x->Zippy_bytes.SpaceValue(values)))
        assertEquals(stats.paths,BigInt(expected.paths.size))
        assert(stats.nanos>0)
      finally session.close()
    }
    test("grounded functions can be supplied as standalone native Rust") {
      val reverse=S.Singleton(P.GroundedPP(P.Deref(h),p=>BytePaths.fromDigits(BytePaths.digits(p).reverse)))
      val p=RustPathMapBackend.bytes(reverse).withNativeFunctions(Map(0->"let mut p=singleton_path(argument); p.reverse(); singleton(&p)"))
      assert(p.isStandalone)
      assertEquals(p.eval(paths=Map(h->BytePathValue(0,128,255))),Set(BytePathValue(255,128,0)))
    }
    test("ordinary path-binding recursors fuse into zipper loops with epsilon and shared prefixes") {
      val bind=RoutinePtr("bind");val callback=RoutinePtr("body");val prefix=PathRef("prefix")
      val suffix=SpaceMention("suffix");val extra=SpaceMention("extra")
      val eps=S.Singleton(P.ZERO)
      val body=Zippy_bytes.Routine(callback,Vector(prefix),Vector(extra),S.Wrap(S.Mention(extra),P.Deref(prefix)))
      val walk=Zippy_bytes.Routine(bind,Vector(prefix),Vector(suffix,extra),
        S.Union(S.Composition(S.Intersection(S.Mention(suffix),eps),S.Call(callback,Vector(P.Deref(prefix)),Vector(S.Mention(extra)))),
          S.Iteration(S.Mention(suffix),h,t,S.Call(bind,Vector(P.Concat(P.Deref(prefix),P.Deref(h))),Vector(S.Mention(t),S.Mention(extra))))))
      val expression=S.Call(bind,Vector(P.ZERO),Vector(input,S.Mention(extra)))
      val defs=Map(bind->walk,callback->body)
      val p=RustPathMapBackend.bytes(expression,defs)
      assert(p.graphDescription.contains("NativePathIteration"))
      val spaces=Map(x->values,extra->Set(BytePathValue(),BytePathValue(128,0)))
      assertEquals(p.eval(spaces=spaces),Zippy_bytes.eval(expression,spaceArgs=spaces.view.mapValues(Zippy_bytes.SpaceValue(_)).toMap,routines=defs).paths)
      val observed=scala.collection.mutable.ArrayBuffer.empty[BytePathValue]
      val effect=body.copy(body=S.GroundedPS(P.Deref(prefix),p=>{observed+=p;Zippy_bytes.SpaceValue(Set(p))}))
      val effects=defs.updated(callback,effect)
      val expected=Zippy_bytes.eval(expression,spaceArgs=spaces.view.mapValues(Zippy_bytes.SpaceValue(_)).toMap,routines=effects).paths
      val order=observed.toVector;observed.clear()
      assertEquals(RustPathMapBackend.bytes(expression,effects).eval(spaces=spaces),expected)
      assertEquals(observed.toVector,order)
      var argumentCalls=0
      val strict=S.Call(bind,Vector(P.ZERO),Vector(S.Empty,
        S.GroundedSS(S.Mention(extra),v=>{argumentCalls+=1;v})))
      val strictProgram=RustPathMapBackend.bytes(strict,effects)
      assertEquals(argumentCalls,0)
      assertEquals(strictProgram.eval(spaces=spaces),Set.empty[BytePathValue])
      assertEquals(argumentCalls,1)
    }
    test("translated ordered folds over thousands of heads use a stack-safe native loop") {
      val input=SpaceMention("many-heads")
      val value=SpaceValue((0 until 8192).map(i=>PathValue(List(PathItem(s"head$i"),PathItem("tail")))).toSet)
      val expr=Space.Fold(Space.Mention(input),Path.ZERO,acc,h,t,Space.Wrap(Space.Mention(t),Path.Deref(h)),Path.Deref(acc))
      // The interner must include runtime symbols before taking its snapshot.
      val table=PathItemInterner();value.paths.flatMap(_.items).toVector.sortBy(_.show).foreach(table.intern)
      val translated=RadixTranslations.internedSymbolsToBytes(expr,Map.empty,table)
      val p=RustPathMapBackend.bytes(translated.program.entry,translated.program.routines)
      assert(p.code.contains("next_call"))
      val result=p.eval(spaces=Map(input->translated.encode(value).paths))
      assertEquals(result.map(translated.encoding.decode),value.paths)
      val bitValues=SpaceValue(value.paths.toVector.sortBy(_.show).take(512).toSet)
      val bitTable=PathItemInterner();bitValues.paths.flatMap(_.items).toVector.sortBy(_.show).foreach(bitTable.intern)
      val bits=RadixTranslations.symbolsToBits(expr,Map.empty,bitTable)
      val injection=PathEncodings.bitsToBytes
      val byteBits=RadixTranslations.bitsToBytes(bits.program.entry,bits.program.routines)
      val fib=RustPathMapBackend.bytes(byteBits.entry,byteBits.routines)
      assert(fib.graphDescription.contains("FirstFibonacci"))
      assert(fib.code.contains("next_call"))
      assert(fib.code.contains("ordered_fibonacci"))
      val folded=fib.eval(spaces=Map(input->bits.encode(bitValues).paths.map(injection.encode)))
      assertEquals(folded.map(p=>bits.encoding.decode(injection.decode(p))),bitValues.paths)
      val strings=RadixTranslations.stringsToBytes(expr)
      val text=RustPathMapBackend.bytes(strings.program.entry,strings.program.routines)
      assert(text.code.contains("ordered_terminated"))
      assertEquals(text.eval(spaces=Map(input->strings.encode(bitValues).paths)).map(strings.encoding.decode),bitValues.paths)
      val malformed=Set(BytePathValue(),BytePathValue(0),BytePathValue(0,0),BytePathValue(1),BytePathValue(1,0,255),BytePathValue(255,0,1,0),BytePathValue(255,1))
      assertEquals(text.eval(spaces=Map(input->malformed)),Zippy_bytes.eval(strings.program.entry,
        spaceArgs=Map(input->Zippy_bytes.SpaceValue(malformed)),routines=strings.program.routines).paths)
      val small=SpaceValue(value.paths.toVector.sortBy(_.show).take(64).toSet)
      val growing=Space.Fold(Space.Mention(input),Path.ZERO,acc,h,t,
        Space.Singleton(Path.Concat(Path.Deref(acc),Path.Deref(h))),Path.Concat(Path.Deref(acc),Path.Deref(h)))
      val encoded=RadixTranslations.internedSymbolsToBytes(growing,Map.empty,table)
      val actual=RustPathMapBackend.bytes(encoded.program.entry,encoded.program.routines).eval(spaces=Map(input->encoded.encode(small).paths))
      assertEquals(actual.map(encoded.encoding.decode),eval(growing)(using sc=SpaceContextMap(Map(input->small))).paths)
      val orderedStrings=RadixTranslations.stringsToBytes(growing)
      assertEquals(RustPathMapBackend.bytes(orderedStrings.program.entry,orderedStrings.program.routines)
        .eval(spaces=Map(input->orderedStrings.encode(small).paths)).map(orderedStrings.encoding.decode),
        eval(growing)(using sc=SpaceContextMap(Map(input->small))).paths)
    }
    test("terminated scanner proof rejects a shadowed accumulator and preserves strict callbacks") {
      val scan=RoutinePtr("scan");val callback=RoutinePtr("callback")
      val prefix=PathRef("prefix");val digit=PathRef("digit");val rest=SpaceMention("rest")
      val eps=S.Singleton(P.ZERO)
      def choose(test: ByteSpace,yes: ByteSpace,no: ByteSpace): ByteSpace =
        val exists=S.Union(S.Intersection(test,eps),S.Iteration(test,PathRef("_"),SpaceMention("_"),eps))
        S.Union(S.Composition(exists,yes),S.Composition(S.Subtraction(eps,exists),no))
      def zero(p: PathRef)=S.Intersection(S.Singleton(P.Deref(p)),S.Singleton(Zippy_bytes.digit(0)))
      val word=P.Concat(P.Deref(prefix),P.Deref(prefix))
      val shadowed=Zippy_bytes.Routine(scan,Vector(prefix),Vector(rest),
        S.Iteration(S.Mention(rest),prefix,t,choose(zero(prefix),S.Singleton(word),S.Call(scan,Vector(word),Vector(S.Mention(t))))))
      val entry=S.Range(S.Iteration(input,digit,t,choose(zero(digit),S.Singleton(P.Deref(digit)),
        S.Call(scan,Vector(P.Deref(digit)),Vector(S.Mention(t))))),0,1)
      val data=Set(BytePathValue(2,3,0),BytePathValue(4,0,255),BytePathValue(8))
      val program=RustPathMapBackend.bytes(entry,Map(scan->shadowed))
      assert(!program.graphDescription.contains("FirstTerminated"))
      assertEquals(program.eval(spaces=Map(x->data)),Zippy_bytes.eval(entry,
        spaceArgs=Map(x->Zippy_bytes.SpaceValue(data)),routines=Map(scan->shadowed)).paths)
      var calls=0
      val body=Zippy_bytes.Routine(callback,Vector(prefix),Vector(rest),S.Singleton(P.Deref(prefix)))
      def effect(p: P,s: S)=S.Call(callback,Vector(p),Vector(S.GroundedSS(s,v=>{calls+=1;v})))
      val next=P.Concat(P.Deref(prefix),P.Deref(digit))
      val strict=shadowed.copy(body=S.Iteration(S.Mention(rest),digit,t,choose(zero(digit),
        effect(next,S.Mention(t)),S.Call(scan,Vector(next),Vector(S.Mention(t))))))
      val strictEntry=S.Range(S.Iteration(input,digit,t,choose(zero(digit),effect(P.Deref(digit),S.Mention(t)),
        S.Call(scan,Vector(P.Deref(digit)),Vector(S.Mention(t))))),0,1)
      val defs=Map(scan->strict,callback->body)
      val expected=Zippy_bytes.eval(strictEntry,spaceArgs=Map(x->Zippy_bytes.SpaceValue(data)),routines=defs).paths
      val expectedCalls=calls;calls=0
      val native=RustPathMapBackend.bytes(strictEntry,defs)
      assert(!native.graphDescription.contains("FirstTerminated"))
      assertEquals(native.eval(spaces=Map(x->data)),expected)
      assertEquals(calls,expectedCalls)
    }
    test("typed native blocks preserve live values, captures, fold updates and callbacks") {
      val previous=Option(System.getProperty("morkl.pathmap.blockBytes"))
      System.setProperty("morkl.pathmap.blockBytes","512")
      try
        val expressions=operations
        val programs=expressions.map(RustPathMapBackend.bytes(_))
        assert(programs.exists(_.code.contains("native_block_")))
        val session=RustPathMapBackend.Session(programs)
        val paths=Map(h->BytePathValue(128));val spaces=Map(x->values)
        try expressions.zipWithIndex.foreach { (e,i)=>
          assertEquals(session.eval(i,programs(i))(paths,spaces),
            Zippy_bytes.eval(e,paths,spaces.view.mapValues(Zippy_bytes.SpaceValue(_)).toMap).paths,s"blocked case $i")
        } finally session.close()
      finally previous match
        case Some(value)=>System.setProperty("morkl.pathmap.blockBytes",value)
        case None=>System.clearProperty("morkl.pathmap.blockBytes")
    }
