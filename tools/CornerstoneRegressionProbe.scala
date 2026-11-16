package morkl

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

object CornerstoneRegressionTiming:
  def digest(value: SpaceValue): String =
    val canonical = value.paths.toVector.map { p =>
      p.items.map(_.show).map(s => s.length.toString + ":" + s).mkString
    }.sorted.mkString("\n")
    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(UTF_8))
      .map(b => f"${b & 255}%02x").mkString

  def samples(run: () => Int, expected: Int): Vector[Double] =
    val warmupEnd = System.nanoTime() + java.lang.Long.getLong("morkl.regression.warmupMillis", 1000L) * 1000000L
    while System.nanoTime() < warmupEnd do require(run() == expected)
    val probe = System.nanoTime()
    require(run() == expected)
    val batchNanos = java.lang.Long.getLong("morkl.regression.sampleMillis", 50L) * 1000000L
    val repeats = (batchNanos / (System.nanoTime() - probe).max(1)).max(1).min(4096).toInt
    Vector.fill(5) {
      var count = 0
      val start = System.nanoTime()
      for _ <- 0 until repeats do count = run()
      val ms = (System.nanoTime() - start) / 1e6 / repeats
      require(count == expected)
      ms
    }

/** Compile with either revision's benchmark sources. Reflection is used only
  * for untimed graph preparation, keeping the two source snapshots unchanged.
  */
@main def cornerstoneRegressionProbe(output: String, index: Int): Unit =
  val c = TrieBenchmarks.cases()(index)
  given PathContext = PathContext.emptyMap
  val expected = eval(c.expr)(using summon[PathContext], c.sc, c.rc)
  val digest = CornerstoneRegressionTiming.digest(expected)
  val zipperContext = ZipperSpaceContext.fromTrie(c.tc)
  val graphMethod = TrieBenchmarks.getClass.getDeclaredMethods.find(_.getName == "graphPlan").get
  graphMethod.setAccessible(true)
  val compileStart = System.nanoTime()
  val plan = graphMethod.invoke(TrieBenchmarks, c).asInstanceOf[Option[TrieBenchmarks.GraphPlan]]
    .getOrElse(throw IllegalStateException(s"graph preparation failed: ${c.name}/${c.variant}"))
  val compileMs = (System.nanoTime() - compileStart) / 1e6
  def runExec(): SpaceValue =
    val stack = collection.mutable.Stack(new Array[PathValue | SpaceValue | Null](plan.top.nodes.length))
    for (mention, i) <- plan.mentions.zipWithIndex do stack.top(i) = c.sc.resolve(mention)
    exec(plan.top, stack, plan.index)
    stack.top.last.asInstanceOf[SpaceValue]
  def runExecT(): TrieSpace =
    val stack = collection.mutable.Stack(new Array[List[Int] | TrieSpace | Null](plan.top.nodes.length))
    for (mention, i) <- plan.mentions.zipWithIndex do stack.top(i) = c.tc.resolve(mention)
    execT(plan.top, stack, plan.index)
    stack.top.last.asInstanceOf[TrieSpace]
  val destination = Paths.get(output)
  Files.createDirectories(destination.getParent)
  val writer = Files.newBufferedWriter(destination)
  writer.write("name,variant,evaluator,paths,ms,samples,checked,output_sha256,graph_compile_ms,graph_nodes,compiled_away,sample_ms\n")
  def quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
  def measure(name: String, check: () => SpaceValue, run: () => Int): Unit =
    require(check() == expected, s"${c.name}/${c.variant}: $name output mismatch")
    val times = CornerstoneRegressionTiming.samples(run, expected.paths.size)
    writer.write(Vector(quote(c.name), quote(c.variant), name, expected.paths.size,
      f"${times.sorted.apply(2)}%.6f", times.size, "exact", digest, f"$compileMs%.3f",
      plan.stats.nodes, plan.compiledAway, times.mkString(";")).mkString(",") + "\n")
    writer.flush()
  try
    measure("eval", () => eval(c.expr)(using summon[PathContext], c.sc, c.rc),
      () => eval(c.expr)(using summon[PathContext], c.sc, c.rc).paths.size)
    measure("evalTrie", () => evalTrieValue(c.expr)(using summon[PathContext], c.tc, c.rc),
      () => evalTrie(c.expr)(using summon[PathContext], c.tc, c.rc).pathCount)
    measure("exec", () => runExec(), () => runExec().paths.size)
    measure("execT", () => runExecT().toSpaceValue, () => runExecT().pathCount)
    measure("evalZ", () => evalZValue(c.expr)(using summon[PathContext], zipperContext, c.rc),
      () => evalZ(c.expr)(using summon[PathContext], zipperContext, c.rc).pathCount)
  finally writer.close()

@main def cornerstoneRadixRegressionProbe(output: String, index: Int, dialect: String): Unit =
  require(dialect == "bits" || dialect == "bytes")
  val c = TrieBenchmarks.cases()(index)
  val defs = OpenFstBackend.reachable(c.expr, c.rc)
  val interner = PathItemInterner()
  c.sc.m.valuesIterator.flatMap(_.paths).flatMap(_.items).toSet.toVector.sortBy(_.show).foreach(interner.intern)
  val expected = evalTrieValue(c.expr)(using PathContext.emptyMap, c.tc, c.rc)
  def benchmark[V](eval: () => Set[V], decode: V => PathValue): Vector[Double] =
    require(eval().map(decode) == expected.paths, s"$dialect reference mismatch: ${c.name}/${c.variant}")
    val result = CornerstoneRegressionTiming.samples(() => eval().size, expected.paths.size)
    require(eval().map(decode) == expected.paths, s"$dialect reference mismatch after timing")
    result
  val times = if dialect == "bits" then
    val translated = RadixTranslations.symbolsToBits(c.expr, defs, interner)
    val inputs = c.sc.m.view.mapValues(translated.encode).toMap
    benchmark(() => translated.program.eval(spaceArgs = inputs).paths, translated.encoding.decode)
  else
    val translated = RadixTranslations.internedSymbolsToBytes(c.expr, defs, interner)
    val inputs = c.sc.m.view.mapValues(translated.encode).toMap
    benchmark(() => translated.program.eval(spaceArgs = inputs).paths, translated.encoding.decode)
  def quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
  val destination = Paths.get(output)
  Files.createDirectories(destination.getParent)
  Files.writeString(destination, "family,name,dialect,paths,reference_ms,samples,checked,sample_ms\n" +
    Vector("programs", quote(s"${c.name}/${c.variant}"), dialect, expected.paths.size,
      f"${times.sorted.apply(2)}%.6f", times.size, "exact", times.mkString(";")).mkString(",") + "\n")

@main def cornerstoneLargeRegressionProbe(output: String, index: Int): Unit =
  val c = ZipperLargeBenchmarks.cases()(index)
  val expected = evalTrieValue(c.expr)(using PathContext.emptyMap, c.tc, c.rc)
  require(evalZValue(c.expr)(using PathContext.emptyMap, c.zc, c.rc) == expected)
  val digest = CornerstoneRegressionTiming.digest(expected)
  val destination = Paths.get(output)
  Files.createDirectories(destination.getParent)
  val writer = Files.newBufferedWriter(destination)
  writer.write("name,variant,evaluator,paths,ms,samples,checked,output_sha256,sample_ms\n")
  def quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
  try
    for (name, run) <- Vector(
      "evalTrie" -> (() => evalTrie(c.expr)(using PathContext.emptyMap, c.tc, c.rc).pathCount),
      "evalZ" -> (() => evalZ(c.expr)(using PathContext.emptyMap, c.zc, c.rc).pathCount)) do
      val times = CornerstoneRegressionTiming.samples(run, expected.paths.size)
      writer.write(Vector(quote(c.name), quote(c.size), name, expected.paths.size,
        f"${times.sorted.apply(2)}%.6f", times.size, "exact", digest, times.mkString(";")).mkString(",") + "\n")
      writer.flush()
  finally writer.close()
