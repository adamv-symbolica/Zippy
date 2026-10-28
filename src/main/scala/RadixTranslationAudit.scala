package morkl

import java.nio.file.{Files, Paths}
import java.util.concurrent.{ConcurrentHashMap, Executors, ThreadFactory, TimeUnit}
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable

/** Opt-in full-suite differential audit. Capture reference executions during
  * tests; run translation checks at JVM shutdown, outside timed/counted scopes.
  * The accompanying runner treats a missing report or any failure as failure.
  */
object RadixTranslationAudit:
  private val output = Option(System.getProperty("morkl.radixAudit.path")).filter(_.nonEmpty)
  val enabled: Boolean = output.nonEmpty
  private final class Identity(val value: AnyRef):
    override def hashCode(): Int = System.identityHashCode(value)
    override def equals(other: Any): Boolean = other match
      case that: Identity => value eq that.value
      case _ => false
  private case class Key(expression: Space, paths: Map[PathRef, PathValue],
                         spaces: Map[SpaceMention, SpaceValue], routines: Map[RoutinePtr, Identity], expected: SpaceValue)
  private case class Example(id: Long, expression: Space, paths: Map[PathRef, PathValue],
                             spaces: Map[SpaceMention, SpaceValue], routines: Map[RoutinePtr, Routine],
                             expected: SpaceValue, alphabet: Set[PathItem], caller: String)
  private class Capture:
    val alphabet = mutable.HashSet.empty[PathItem]
    val paths = mutable.HashMap.empty[PathRef, PathValue]
    val spaces = mutable.HashMap.empty[SpaceMention, SpaceValue]
  private val current = ThreadLocal[Capture]()
  private val replaying = ThreadLocal.withInitial(() => false)
  private val examples = ConcurrentHashMap[Key, Example]()
  private val observations = AtomicLong(0)
  private val sourceErrors = AtomicLong(0)
  private val ids = AtomicLong(0)
  private val completed = AtomicLong(0)
  private val failures = AtomicLong(0)
  private val checks = AtomicLong(0)
  private val passed = ConcurrentHashMap[String, AtomicLong]()
  private val directions = Vector("symbols-bits", "strings-bytes", "interned-bytes", "bits-bytes", "bytes-bits", "bits-symbols", "bytes-symbols")
  def path(value: PathValue): PathValue =
    val c = if enabled then current.get() else null
    if c != null then c.alphabet ++= value.items
    value
  def space(value: SpaceValue): SpaceValue =
    val c = if enabled then current.get() else null
    if c != null then value.paths.foreach(p => c.alphabet ++= p.items)
    value
  private def definitions(expression: Space, rc: PartialFunction[RoutinePtr, Routine]): Map[RoutinePtr, Routine] =
    val found = mutable.LinkedHashMap.empty[RoutinePtr, Routine]
    val seen = mutable.HashSet.empty[RoutinePtr]
    def scan(s: Space): Unit =
      val calls = mutable.ArrayBuffer.empty[RoutinePtr]
      subs(s)(spost = { case call @ Space.Call(name, _, _) => calls += name; call })
      calls.foreach { name =>
        if seen.add(name) && rc.isDefinedAt(name) then
          val routine = rc(name)
          found(name) = routine
          scan(routine.body)
      }
    scan(expression)
    found.toMap

  def evaluate(expression: Space, pc: PathContext, sc: SpaceContext, rc: PartialFunction[RoutinePtr, Routine])
              (run: (PathContext, SpaceContext) => SpaceValue): SpaceValue =
    if !enabled || replaying.get() || current.get() != null then run(pc, sc)
    else
      val capture = Capture()
      current.set(capture)
      val wrappedP = new PathContext:
        override def resolve(ref: PathRef): PathValue =
          val value = pc.resolve(ref)
          capture.paths(ref) = value
          path(value)
      val wrappedS = new SpaceContext:
        override def resolve(ref: SpaceMention): SpaceValue =
          val value = sc.resolve(ref)
          capture.spaces(ref) = value
          space(value)
      try
        val result = try run(wrappedP, wrappedS) catch
          case error: Throwable => sourceErrors.incrementAndGet(); throw error
        observations.incrementAndGet()
        // Preserve every supplied free binding, including branches that the
        // reference did not need to read on this particular input.
        Matching.freeRefsV(expression).foreach { ref =>
          if !capture.paths.contains(ref) then scala.util.Try(pc.resolve(ref)).foreach { value =>
            capture.paths(ref) = value
            path(value)
          }
        }
        Matching.freeMentionsV(expression).foreach { ref =>
          if !capture.spaces.contains(ref) then scala.util.Try(sc.resolve(ref)).foreach { value =>
            capture.spaces(ref) = value
            space(value)
          }
        }
        val module = definitions(expression, rc)
        val paths = capture.paths.toMap
        val spaces = capture.spaces.toMap
        val key = Key(expression, paths, spaces, module.view.mapValues(r => Identity(r)).toMap, result)
        if !examples.containsKey(key) then
          val caller = Thread.currentThread().getStackTrace.iterator.map(_.getClassName)
            .filter(_.startsWith("morkl.")).toVector.lastOption.getOrElse("morkl.other")
          val example = Example(ids.incrementAndGet(), expression, paths, spaces, module,
            result, capture.alphabet.toSet ++ result.paths.flatMap(_.items), caller)
          examples.putIfAbsent(key, example)
        result
      finally current.remove()

  private def quote(text: String): String = "\"" + text.flatMap {
    case '\"' => "\\\""
    case '\\' => "\\\\"
    case '\n' => "\\n"
    case '\r' => "\\r"
    case '\t' => "\\t"
    case c if c < ' ' => f"\\u${c.toInt}%04x"
    case c => c.toString
  } + "\""
  private def same(actual: SpaceValue, expected: SpaceValue): Unit =
    if actual != expected then
      throw AssertionError(s"expected ${expected.paths.size} paths, got ${actual.paths.size}; missing=" +
        (expected.paths diff actual.paths).take(8).map(_.show).mkString(";") + "; extra=" +
        (actual.paths diff expected.paths).take(8).map(_.show).mkString(";"))

  private def verify(e: Example, record: String => Unit): Unit =
    replaying.set(true)
    try
      def table(): PathItemInterner =
        val result = PathItemInterner()
        e.alphabet.toVector.sortBy(_.show).foreach(result.intern)
        result
      lazy val bits = RadixTranslations.symbolsToBits(e.expression, e.routines, table())
      lazy val strings = RadixTranslations.stringsToBytes(e.expression, e.routines)
      lazy val bytes = RadixTranslations.internedSymbolsToBytes(e.expression, e.routines, table())
      def bitArgs = e.paths.view.mapValues(bits.encoding.encode).toMap
      def bitSpaces = e.spaces.view.mapValues(bits.encode).toMap
      def stringArgs = e.paths.view.mapValues(strings.encoding.encode).toMap
      def stringSpaces = e.spaces.view.mapValues(strings.encode).toMap
      def byteArgs = e.paths.view.mapValues(bytes.encoding.encode).toMap
      def byteSpaces = e.spaces.view.mapValues(bytes.encode).toMap
      def check(label: String)(result: => SpaceValue): Unit =
        val start = System.nanoTime()
        var error: Option[Throwable] = None
        try
          same(result, e.expected)
          passed.computeIfAbsent(label, _ => AtomicLong(0)).incrementAndGet()
        catch
          case failure: Throwable => failures.incrementAndGet(); error = Some(failure)
        checks.incrementAndGet()
        val detail = error.fold("")(f => s"${f.getClass.getName}: ${Option(f.getMessage).getOrElse("")}")
        record(s"{\"case\":${e.id},\"caller\":${quote(e.caller)},\"direction\":${quote(label)},\"status\":${quote(if error.isEmpty then "pass" else "fail")},\"millis\":${(System.nanoTime() - start) / 1000000L},\"error\":${quote(detail)}}")
        error.foreach { failure =>
          record(s"{\"case\":${e.id},\"expression\":${quote(e.expression.show)},\"paths\":${quote(e.paths.map((k,v) => s"${k.s}=${v.show}").mkString(";"))},\"spaces\":${quote(e.spaces.map((k,v) => s"${k.s}=${v.pretty}").mkString(";"))}}")
        }
      check("symbols-bits") { bits.decode(bits.program.eval(bitArgs, bitSpaces)) }
      check("strings-bytes") { strings.decode(strings.program.eval(stringArgs, stringSpaces)) }
      check("interned-bytes") { bytes.decode(bytes.program.eval(byteArgs, byteSpaces)) }
      check("bits-bytes") {
        val codec = PathEncodings.bitsToBytes
        val p = RadixTranslations.bitsToBytes(bits.program.entry, bits.program.routines)
        val value = p.eval(bitArgs.view.mapValues(codec.encode).toMap,
          bitSpaces.view.mapValues(v => ByteSpaceValue(v.paths.map(codec.encode))).toMap)
        SpaceValue(value.paths.map(p => bits.encoding.decode(codec.decode(p))))
      }
      check("bytes-bits") {
        val codec = PathEncodings.bytesToBits
        val p = RadixTranslations.bytesToBits(strings.program.entry, strings.program.routines)
        val value = p.eval(stringArgs.view.mapValues(codec.encode).toMap,
          stringSpaces.view.mapValues(v => BitSpaceValue(v.paths.map(codec.encode))).toMap)
        SpaceValue(value.paths.map(p => strings.encoding.decode(codec.decode(p))))
      }
      check("bits-symbols") {
        val codec = PathEncodings.bitsToSymbols
        val p = RadixTranslations.bitsToSymbols(bits.program.entry, bits.program.routines)
        val value = p.eval()(using PathContextMap(bitArgs.view.mapValues(codec.encode).toMap),
          SpaceContextMap(bitSpaces.view.mapValues(v => SpaceValue(v.paths.map(codec.encode))).toMap))
        SpaceValue(value.paths.map(p => bits.encoding.decode(codec.decode(p))))
      }
      check("bytes-symbols") {
        val codec = PathEncodings.bytesToSymbols
        val p = RadixTranslations.bytesToSymbols(bytes.program.entry, bytes.program.routines)
        val value = p.eval()(using PathContextMap(byteArgs.view.mapValues(codec.encode).toMap),
          SpaceContextMap(byteSpaces.view.mapValues(v => SpaceValue(v.paths.map(codec.encode))).toMap))
        SpaceValue(value.paths.map(p => bytes.encoding.decode(codec.decode(p))))
      }
    finally replaying.remove()

  output.foreach { location =>
    Runtime.getRuntime.addShutdownHook(new Thread(() =>
      val directory = Paths.get(location)
      Files.createDirectories(directory)
      val writer = Files.newBufferedWriter(directory.resolve("checks.jsonl"))
      def record(line: String): Unit = writer.synchronized { writer.write(line); writer.newLine(); writer.flush() }
      val snapshot = examples.values().toArray(new Array[Example](0)).toVector.sortBy(_.id)
      println(s"Radix translation audit: ${snapshot.size} distinct cases from ${observations.get()} reference evaluations")
      val workers = Integer.getInteger("morkl.radixAudit.workers", 16).intValue().max(1)
      val pool = Executors.newFixedThreadPool(workers, new ThreadFactory:
        def newThread(task: Runnable): Thread =
          val thread = new Thread(task, "radix-audit")
          thread.setDaemon(true)
          thread
      )
      snapshot.foreach { example => pool.submit(new Runnable:
        def run(): Unit =
          try verify(example, record)
          catch case failure: Throwable =>
            failures.incrementAndGet()
            record(s"{\"case\":${example.id},\"status\":\"harness-failure\",\"error\":${quote(failure.toString)}}")
          finally
            val n = completed.incrementAndGet()
            if n % 100 == 0 || n == snapshot.size then println(s"Radix translation audit: $n/${snapshot.size} cases, ${failures.get()} failures")
      ) }
      pool.shutdown()
      val finished = pool.awaitTermination(java.lang.Long.getLong("morkl.radixAudit.seconds", 5400L), TimeUnit.SECONDS)
      if !finished then
        failures.incrementAndGet()
        pool.shutdownNow()
      val counts = directions.map(d => quote(d) + ":" + Option(passed.get(d)).fold(0L)(_.get())).mkString(",")
      val callers = snapshot.groupBy(_.caller).toVector.sortBy(_._1).map((n,v) => quote(n) + ":" + v.size).mkString(",")
      val summary = s"{\"observations\":${observations.get()},\"cases\":${snapshot.size},\"completed\":${completed.get()},\"checks\":${checks.get()},\"failures\":${failures.get()},\"sourceErrors\":${sourceErrors.get()},\"finished\":$finished,\"passed\":{$counts},\"callers\":{$callers}}"
      Files.writeString(directory.resolve("summary.json"), summary + "\n")
      writer.close()
      println("Radix translation audit: " + summary)
      ()
    , "radix-audit-shutdown"))
  }
