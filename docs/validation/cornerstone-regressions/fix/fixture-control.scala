package morkl
import morkl.Syntax.{*, given}
import java.nio.file.{Files,Paths}
/** Compile with either revision's benchmark sources. Reflection is used only
  * for untimed graph preparation, keeping the two source snapshots unchanged.
  */
@main def isolatedTemperatureProbe(output: String, index: Int): Unit =
  val world = SpaceValue((for
    lat <- 0 until 32
    lon <- 0 until 64
    bucket = (lat * 7 + lon * 3) & 31
    label = if bucket < 12 then "C" else if bucket > 19 then "H" else "M"
    bits = TemperatureExample.bits(lat, 6) ++ TemperatureExample.bits(lon, 9) ++ TemperatureExample.bits(bucket, 5)
  yield Syntax.parse((Vector("cell") ++ bits :+ label).mkString("."))).toSet)
  val sc = SpaceContextMap(Map(SpaceMention("world") -> world))
  val query = S"world" <| ("cell" x Space.Literal(TemperatureExample.interval(8,40,6)) x Space.Literal(TemperatureExample.interval(32,180,9)))
  val c = TrieBenchmarks.ProgramCase("temperature", "synthetic 32x64 isolated setup", query, sc, TrieSpaceContext.fromReference(sc))
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
