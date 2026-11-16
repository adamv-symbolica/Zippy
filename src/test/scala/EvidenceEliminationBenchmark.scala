package morkl

import java.nio.file.{Files, Paths}

/** Compare the previous source pass list with consumer/producer simplification.
  * Input construction, compilation and output enumeration are outside timing.
  * PathMap timing includes counting; OpenFst timing includes realization.
  * Every variant is checked against a set oracle.
  */
object EvidenceEliminationBenchmark:
  import EvidenceEliminationExample.*
  private def median(f: => Unit): Long =
    f; f
    val probe = System.nanoTime(); f
    val repeats = (20000000L / (System.nanoTime() - probe).max(1)).max(1).min(32).toInt
    Vector.fill(5) {
      val start = System.nanoTime()
      for _ <- 0 until repeats do f
      (System.nanoTime() - start) / repeats
    }.sorted.apply(2)

  def run(output: String, native: Boolean): Unit =
    val folder = Paths.get(output); Files.createDirectories(folder)
    val before = Supercompiler.normalize(expression,
      passes = Supercompiler.defaultSourcePasses.filterNot(_._1 == "evidence-elimination")).space
    val variants = Vector("before" -> before, "after" -> optimized)
    variants.foreach((name, e) => Files.writeString(folder.resolve(s"$name.zippy"), e.show + "\n"))
    def graph(name: String, e: Space): RecursiveOpGraph = optimize(transpile(
      Routine(RoutinePtr(s"evidence_$name"), Vector.empty, Vector(a, b), e)))
    val graphs = variants.map((name, e) => name -> graph(name, e)).toMap
    val writer = Files.newBufferedWriter(folder.resolve("results.csv"))
    writer.write("evaluator,heads,variant,paths,nanos,samples,iterations\n")
    try
      for n <- Vector(16, 64, 256) do
        val av = SpaceValue((0 until n).map(i => PathValue(List(PathItem(s"a$i")))).toSet)
        val bv = SpaceValue((0 until n).map(i => PathValue(List(PathItem(s"b$i"), PathItem("tail")))).toSet)
        val context = TrieSpaceContextMap(Map(a -> TrieSpace.fromSpaceValue(av), b -> TrieSpace.fromSpaceValue(bv)))
        val reference = SpaceContextMap(Map(a -> av, b -> bv))
        val zipper = ZipperSpaceContext.fromTrie(context)
        for (name, e) <- variants do
          val g = graphs(name)
          def execValue(): TrieSpace =
            val frame = new Array[List[Int] | TrieSpace | Null](g.nodes.size)
            frame(0) = context.resolve(a); frame(1) = context.resolve(b)
            execT(g, collection.mutable.Stack(frame))
            frame.last.asInstanceOf[TrieSpace]
          def execSet(): SpaceValue =
            val frame = new Array[PathValue | SpaceValue | Null](g.nodes.size)
            frame(0) = av; frame(1) = bv
            exec(g, collection.mutable.Stack(frame))
            frame.last.asInstanceOf[SpaceValue]
          require(eval(e)(using sc = reference) == bv)
          require(evalTrieValue(e)(using sc = context) == bv)
          require(execValue().toSpaceValue == bv)
          require(execSet() == bv)
          require(evalZValue(e)(using sc = zipper) == bv)
          for (evaluator, operation) <- Vector[(String, () => Unit)](
            "eval" -> (() => { eval(e)(using sc = reference); () }),
            "evalTrie" -> (() => { evalTrie(e)(using sc = context); () }),
            "evalZ" -> (() => { evalZ(e)(using sc = zipper); () }),
            "exec" -> (() => { execSet(); () }),
            "execT" -> (() => { execValue(); () }),
            "execZ" -> (() => { execZ(e)(using sc = zipper); () })) do
            val nanos = median(operation())
            writer.write(s"$evaluator,$n,$name,${bv.paths.size},$nanos,5,${Supercompiler.stats(e).iterations}\n")
            writer.flush()
      if native then
        // These graphs have no nonempty constants: the same graph operates on
        // byte items directly, so no symbol-code scanner contaminates this pair.
        val programs = variants.map((name, _) => name -> RustPathMapBackend.fromGraph(graphs(name)))
        programs.foreach((name, p) => Files.writeString(folder.resolve(s"$name.rs"), p.source))
        val session = RustPathMapBackend.Session(programs.map(_._2))
        try
          for n <- Vector(16, 64, 256) do
            val av = (0 until n).map(i => BytePathValue(i)).toSet
            val bv = (0 until n).map(i => BytePathValue(i, 255)).toSet
            for ((name, p), id) <- programs.zipWithIndex do
              require(session.eval(id, p)(Map.empty, Map(a -> av, b -> bv)) == bv)
              session.benchmark(id, p, 1)(Map.empty, Map(a -> av, b -> bv))
              val probe = session.benchmark(id, p, 1)(Map.empty, Map(a -> av, b -> bv))
              val repeats = (20000000L / probe.nanos.max(1)).max(1).min(256).toInt
              val samples = Vector.fill(5)(session.benchmark(id, p, repeats)(Map.empty, Map(a -> av, b -> bv)))
              require(samples.forall(_.paths == bv.size))
              val nanos = samples.map(_.nanos).sorted.apply(2)
              writer.write(s"pathmap,$n,$name,${bv.size},$nanos,5,${Supercompiler.stats(variants(id)._2).iterations}\n")
              writer.flush()
        finally session.close()
        val cpp = variants.map((name, _) => name -> OpenFstBackend.fromGraph(graphs(name)))
        cpp.foreach((name, p) => Files.writeString(folder.resolve(s"$name.cpp"), p.source))
        val cppSession = OpenFstBackend.Session(cpp.map(_._2))
        try
          for n <- Vector(16, 64, 256) do
            val av = (0 until n).map(i => PathValue(List(PathItem(s"a$i")))).toSet
            val bv = (0 until n).map(i => PathValue(List(PathItem(s"b$i"), PathItem("tail")))).toSet
            for ((name, p), id) <- cpp.zipWithIndex do
              require(cppSession.eval(id, p)(Map.empty, Map(a -> av, b -> bv)) == bv)
              cppSession.benchmark(id, p, 1)(Map.empty, Map(a -> av, b -> bv))
              val probe = cppSession.benchmark(id, p, 1)(Map.empty, Map(a -> av, b -> bv))
              val repeats = (20000000L / probe.nanos.max(1)).max(1).min(256).toInt
              val samples = Vector.fill(5)(cppSession.benchmark(id, p, repeats)(Map.empty, Map(a -> av, b -> bv)))
              require(samples.forall(_.paths == bv.size))
              writer.write(s"openfst-symbols,$n,$name,${bv.size},${samples.map(_.nanos).sorted.apply(2)},5,${Supercompiler.stats(variants(id)._2).iterations}\n")
              writer.flush()
        finally cppSession.close()
    finally writer.close()

@main def evidenceEliminationBenchmarkReport(output: String, native: Boolean = true): Unit =
  EvidenceEliminationBenchmark.run(output, native)
