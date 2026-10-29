package morkl

import java.nio.file.{Files,Paths}
import scala.collection.mutable

/** Native timings exclude C++ compilation, protocol IO, initial FST building,
  * and result enumeration. Correctness is checked separately against evalTrie.
  */
object OpenFstBenchmarks:
  private case class Workload(family: String,name: String,expr: Space,context: SpaceContextMap,
                              routines: PartialFunction[RoutinePtr,Routine] = PartialFunction.empty,runs: Int = 3)
  private def path(xs: String*): PathValue = PathValue(xs.map(PathItem(_)).toList)
  private def algebraScaling: Vector[Workload] =
    Vector(1024,4096,16384,65536).flatMap { size =>
      val a=SpaceValue((0 until size).map(i=>path(s"head$i","zero")).toSet)
      val subset=SpaceValue((0 until size by 4).map(i=>path(s"head$i","zero")).toSet)
      val prefixes=SpaceValue((0 until size by 4).map(i=>path(s"head$i")).toSet)
      val disjoint=SpaceValue((0 until size).map(i=>path(s"other$i","zero")).toSet)
      val av=Space.Mention(SpaceMention("a"));val bv=Space.Mention(SpaceMention("b"))
      Vector(("union-subset",Space.Union(av,bv),subset),("intersection-subset",Space.Intersection(av,bv),subset),
        ("restriction-quarter",Space.Restriction(av,bv),prefixes),("subtraction-disjoint",Space.Subtraction(av,bv),disjoint)).map { (name,expr,b) =>
        Workload("algebra-scaling",s"$name/$size",expr,SpaceContextMap(Map(SpaceMention("a")->a,SpaceMention("b")->b)))
      }
    }
  private def construction: Vector[Workload] =
    Vector(256,1024,4096,16384).flatMap { size =>
      val width=Integer.numberOfTrailingZeros(Integer.highestOneBit(size))
      val wide=SpaceValue((0 until size).map(i=>path(s"head$i","zero")).toSet)
      val deep=SpaceValue((0 until size).map(i=>PathValue((0 until width).reverse.map(b=>PathItem(((i>>>b)&1).toString)).toList)).toSet)
      Vector("wide"->wide,"deep"->deep).map { (kind,value) =>
        // Materialize singleton outputs through native iteration, rather than
        // timing an already-built literal accessor.
        val h=PathRef("h");val t=SpaceMention("t");val input=SpaceMention("input")
        val expr=Space.Iteration(Space.Mention(input),h,t,Space.Wrap(Space.Mention(t),Path.Deref(h)))
        Workload("construction",s"$kind/$size",expr,SpaceContextMap(Map(input->value)))
      }
    } ++ Vector(512,2048,8192,32768).map { size =>
      val value=SpaceValue((0 until size).map(i=>path(s"head$i","zero")).toSet)
      val input=SpaceMention("input");val h=PathRef("h");val t=SpaceMention("t");val acc=PathRef("acc")
      val expr=Space.Fold(Space.Mention(input),Path.ZERO,acc,h,t,Space.Wrap(Space.Mention(t),Path.Deref(h)),Path.Deref(acc))
      Workload("incremental-union",size.toString,expr,SpaceContextMap(Map(input->value)))
    }
  private def independent: Vector[Workload] = Vector(32,64,128,256).flatMap { size =>
    val value=SpaceValue((0 until size).flatMap(i=>Vector(path("foo",s"f$i","tail"),path("bar",s"b$i","tail"))).toSet)
    Vector("original"->IndependentProductBenchmarkData.input,"optimized"->IndependentProductBenchmarkData.optimized).map { (name,expr) =>
      Workload("independent-product",s"$name/$size",expr,SpaceContextMap(Map(SpaceMention("s")->value)))
    }
  }
  private def counterCases: Vector[Workload] = SpatialCostCounterCorpus.cases.map { c =>
    Workload("cost-counter",c.name,Supercompiler.normalize(c.expression).space,
      SpaceContextMap(Map(SpaceMention("cost_left")->c.left,SpaceMention("cost_right")->c.right)))
  }
  private def correlationCases: Vector[Workload] = Vector(32,64,128,256).map { size =>
    def values(prefix: String)=SpaceValue((0 until size).map(i=>path(s"$prefix$i","t1")).toSet)
    val l=SpaceMention("correlation_left");val r=SpaceMention("correlation_right")
    Workload("cost-correlation",size.toString,Space.Composition(Space.Mention(l),Space.Mention(r)),
      SpaceContextMap(Map(l->values("l"),r->values("r"))))
  }
  private def workloads(family: String): Vector[Workload] =
    val groups: Vector[(String,()=>Vector[Workload])] = Vector(
      "programs" -> (()=>TrieBenchmarks.cases().map(c=>Workload("programs",s"${c.name}/${c.variant}",c.expr,c.sc,c.rc,c.runs))),
      "large" -> (()=>ZipperLargeBenchmarks.cases().map(c=>Workload("large",s"${c.name}/${c.size}",c.expr,c.sc,c.rc,c.runs))),
      "algebra" -> (()=>ZipperAlgebraBenchmarks.nativeCases().map((n,e,c)=>Workload("algebra",n,e,c))),
      "iter-range-tail" -> (()=>ZipperAlgebraBenchmarks.nativeMicroCases().map((n,e,c)=>Workload("iter-range-tail",n,e,c))),
      "cost-counter" -> (()=>counterCases),"cost-correlation" -> (()=>correlationCases),
      "algebra-scaling" -> (()=>algebraScaling),"construction" -> (()=>construction),"independent-product" -> (()=>independent))
    groups.filter((name,_)=>family.isEmpty||name==family).flatMap((_,build)=>build())
  private def quote(s: String): String = "\""+s.replace("\"","\"\"")+"\""
  def run(output: java.nio.file.Path, family: String = "", shard: Int = 0, shards: Int = 1): Unit =
    Files.createDirectories(output)
    val writer=Files.newBufferedWriter(output.resolve("native.csv"))
    writer.write("family,name,dialect,paths,states,arcs,compile_ms,native_ms,reference_trie_ms,samples,checked\n");writer.flush()
    val failures=mutable.ArrayBuffer.empty[String]
    require(shards>0 && shard>=0 && shard<shards,"invalid benchmark shard")
    val all=workloads(family).zipWithIndex.collect {case (work,index) if index%shards==shard => work}
    var completed=0
    all.foreach { work =>
      Console.err.println(s"[openfst-bench] ${work.family}/${work.name}")
      try
        val defs=OpenFstBackend.reachable(work.expr,work.routines)
        val start=System.nanoTime()
        val trie=evalTrie(work.expr)(using sc=TrieSpaceContext.fromReference(work.context),rc=work.routines)
        val trieMs=(System.nanoTime()-start)/1e6
        val count=trie.pathCount.toLong
        val expected=if count<=250000 then Some(trie.toSpaceValue) else None
        val alphabet=work.context.m.valuesIterator.flatMap(_.paths).flatMap(_.items).toSet
        val interner=PathItemInterner();alphabet.toVector.sortBy(_.show).foreach(interner.intern)
        val bits=RadixTranslations.symbolsToBits(work.expr,defs,interner)
        val bytes=RadixTranslations.internedSymbolsToBytes(work.expr,defs,interner)
        def measure[V](dialect: String,p: OpenFstBackend.Compiled[V],spaces: Map[SpaceMention,Set[V]],decode: V=>PathValue): Unit =
          val compileStart=System.nanoTime()
          val session=OpenFstBackend.Session(Vector(p))
          val compileMs=(System.nanoTime()-compileStart)/1e6
          try
            expected.foreach { value =>
              val actual=session.eval(0,p)(Map.empty,spaces).map(decode)
              require(actual==value.paths,s"native $dialect result mismatch")
            }
            if expected.isEmpty && sys.env.get("ZIPPY_BENCH_WARMUPS").contains("0") then
              session.benchmark(0,p,1)(Map.empty,spaces) // untimed setup for cardinality-only rows
            val first=session.benchmark(0,p,1)(Map.empty,spaces)
            val repeats=(10000000L/first.nanos.max(1L)).max(1L).min(4096L).toInt
            val samples=if first.nanos>1000000000L then Vector(first)
              else first +: Vector.fill(4)(session.benchmark(0,p,repeats)(Map.empty,spaces))
            require(samples.forall(_.paths==count),s"native $dialect cardinality mismatch")
            val stat=samples.sortBy(_.nanos).apply(samples.size/2)
            writer.write(Vector(quote(work.family),quote(work.name),dialect,count,stat.states,stat.arcs,
              f"$compileMs%.3f",f"${stat.nanos/1e6}%.6f",f"$trieMs%.3f",samples.size,if expected.isDefined then "exact" else "cardinality").mkString(",")+"\n")
            writer.flush()
          finally session.close()
        measure("symbols",OpenFstBackend.symbols(work.expr,defs),work.context.m.view.mapValues(_.paths).toMap,identity)
        measure("bits",OpenFstBackend.bits(bits.program.entry,bits.program.routines),work.context.m.view.mapValues(v=>bits.encode(v).paths).toMap,bits.encoding.decode)
        measure("bytes",OpenFstBackend.bytes(bytes.program.entry,bytes.program.routines),work.context.m.view.mapValues(v=>bytes.encode(v).paths).toMap,bytes.encoding.decode)
        completed+=1
      catch case error: Throwable =>
        failures+=s"${work.family}/${work.name}: $error";Console.err.println(failures.last)
    }
    writer.close()
    Files.writeString(output.resolve("summary.txt"),s"completed=$completed/${all.size}\n"+failures.mkString("\n")+"\n")
    require(failures.isEmpty,s"${failures.size} native benchmark failures; see $output/summary.txt")

@main def openFstBenchmarkReport(output: String = "/tmp/zippy-openfst-benchmarks", family: String = "", shard: Int = 0, shards: Int = 1): Unit =
  OpenFstBenchmarks.run(Paths.get(output),family,shard,shards)
