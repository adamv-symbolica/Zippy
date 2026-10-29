package morkl

import java.nio.file.{Files, Paths}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, Executors, ThreadFactory, TimeUnit}
import java.util.concurrent.atomic.AtomicLong

/** Native differential replay outside benchmark/cost-counter scopes. A ledger
  * is written even when a test catches an exception. Missing or partial results
  * are rejected by tools/test_openfst.py.
  */
object OpenFstAudit:
  val enabled = Option(System.getProperty("morkl.openfstAudit.path")).exists(_.nonEmpty)
  private val busy = ThreadLocal.withInitial(() => false)
  private[morkl] def withoutCapture[A](body: => A): A =
    val previous = busy.get();busy.set(true)
    try body finally busy.set(previous)
  private final class Identity(val value: AnyRef):
    override def hashCode(): Int = System.identityHashCode(value)
    override def equals(other: Any): Boolean = other match
      case that: Identity => value eq that.value
      case _ => false
  private trait Case:
    def dialect: String
    def caller: String
    def compile(): Check
  private trait Check:
    def program: OpenFstBackend.Compiled[?]
    def run(session: OpenFstBackend.Session, index: Int): Unit
  private def check[V](p: OpenFstBackend.Compiled[V], paths: Map[PathRef,V], spaces: Map[SpaceMention,Set[V]], expected: Set[V]): Check = new Check:
    val program = p
    def run(session: OpenFstBackend.Session,index: Int): Unit =
      try
        val actual = session.eval(index,p)(paths,spaces)
        if actual != expected then throw AssertionError(s"expected ${expected.size} paths, got ${actual.size}; missing=${(expected diff actual).take(5)}; extra=${(actual diff expected).take(5)}")
      catch case error: Throwable =>
        val directory = Paths.get(System.getProperty("morkl.openfstAudit.path")).resolve("failures")
        Files.createDirectories(directory)
        val key = Integer.toUnsignedString(p.code.hashCode,16)
        Files.writeString(directory.resolve(s"$key.cpp"),p.source)
        Files.writeString(directory.resolve(s"$key.graph.txt"),p.graphDescription)
        def path(v: Vector[Int]) = s"${v.size} ${v.mkString(" ")}"
        val ps = paths.toVector.flatMap((r,v) => p.pathIds.get(r).map(i => s"$i ${path(p.labels.encode(v))}"))
        val ss = spaces.toVector.flatMap((r,vs) => p.spaceIds.get(r).map(i => s"$i ${vs.size} ${vs.toVector.map(v => path(p.labels.encode(v))).mkString(" ")}"))
        val symbols = p.labels.symbols
        val dictionary = symbols.map((i,s) => s"$i ${s.length} ${s.toVector.map(_.toInt).mkString(" ")}").mkString(" ")
        Files.writeString(directory.resolve(s"$key.input.txt"),s"R 0 ${symbols.size} $dictionary ${ps.size} ${ps.mkString(" ")} ${ss.size} ${ss.mkString(" ")}\nQ\n")
        Files.writeString(directory.resolve(s"$key.error.txt"),s"$error\npaths=$paths\nspaces=$spaces\nexpected=$expected\n")
        throw error
  private val cases = ConcurrentLinkedQueue[Case]()
  private val seen = ConcurrentHashMap[Any, java.lang.Boolean]()
  private val observations = AtomicLong(0)
  private val failures = AtomicLong(0)
  private val checks = AtomicLong(0)
  private val counts = ConcurrentHashMap[String,AtomicLong]()
  def evaluate[V,L <: DigitLanguage[V]](source: L)(entry: source.Space, paths: Map[PathRef,V], spaces: Map[SpaceMention,source.SpaceValue],
      routines: PartialFunction[RoutinePtr,source.Routine])(run: => source.SpaceValue): source.SpaceValue =
    if !enabled || busy.get() then run
    else
      // Register the shared shutdown hook even in a digit-only test run.
      val registered = RadixTranslationAudit.enabled
      busy.set(true)
      try
        val result = run
        observations.incrementAndGet()
        val key = (source,Identity(entry),paths,spaces,Identity(routines),result)
        if seen.putIfAbsent(key,true) == null then
          val native = if source eq Zippy_bits then "bits-direct" else "bytes-direct"
          val origin = Thread.currentThread().getStackTrace.iterator.map(_.getClassName).filter(_.startsWith("morkl.")).toVector.lastOption.getOrElse("unknown")
          cases.add(new Case:
            val dialect = native
            val caller = origin
            def compile(): Check =
              val module = scala.collection.mutable.LinkedHashMap.empty[RoutinePtr,source.Routine]
              def scan(s: source.Space): Unit = s match
                case source.Space.Call(r,ps,ss) =>
                  ps.foreach(scanP);ss.foreach(scan)
                  if !module.contains(r) && routines.isDefinedAt(r) then
                    module(r)=routines(r);scan(module(r).body)
                case other => other.productIterator.foreach {
                  case p: source.Path @unchecked => scanP(p)
                  case s: source.Space @unchecked => scan(s)
                  case _ => ()
                }
              def scanP(p: source.Path): Unit = p match
                case source.Path.Concat(a,b) => scanP(a);scanP(b)
                case source.Path.GroundedPP(a,_) => scanP(a)
                case source.Path.GroundedSP(a,_) => scan(a)
                case _ => ()
              scan(entry)
              val generator = OpenFstBackend.compileDigits(source)(entry,module.toMap)
              check(generator,paths,spaces.view.mapValues(_.paths).toMap,result.paths)
          )
        result
      finally busy.remove()

  def addSymbol(entry: Space, paths: Map[PathRef,PathValue], spaces: Map[SpaceMention,SpaceValue],
      routines: Map[RoutinePtr,Routine], expected: SpaceValue, alphabet: Set[PathItem], origin: String): Unit =
    def add(label: String)(build: => Check): Unit = cases.add(new Case:
      val dialect = label
      val caller = origin
      def compile(): Check = build
    )
    add("symbols") { check(OpenFstBackend.symbols(entry,routines),paths,spaces.view.mapValues(_.paths).toMap,expected.paths) }
    add("bits-fibonacci") {
      val table = PathItemInterner(); alphabet.toVector.sortBy(_.show).foreach(table.intern)
      val lowered = RadixTranslations.symbolsToBits(entry,routines,table)
      check(OpenFstBackend.bits(lowered.program.entry,lowered.program.routines),paths.view.mapValues(lowered.encoding.encode).toMap,
        spaces.view.mapValues(v => lowered.encode(v).paths).toMap,lowered.encode(expected).paths)
    }
    add("bytes-strings") {
      val lowered = RadixTranslations.stringsToBytes(entry,routines)
      check(OpenFstBackend.bytes(lowered.program.entry,lowered.program.routines),paths.view.mapValues(lowered.encoding.encode).toMap,
        spaces.view.mapValues(v => lowered.encode(v).paths).toMap,lowered.encode(expected).paths)
    }
    add("bytes-interned") {
      val table = PathItemInterner(); alphabet.toVector.sortBy(_.show).foreach(table.intern)
      val lowered = RadixTranslations.internedSymbolsToBytes(entry,routines,table)
      check(OpenFstBackend.bytes(lowered.program.entry,lowered.program.routines),paths.view.mapValues(lowered.encoding.encode).toMap,
        spaces.view.mapValues(v => lowered.encode(v).paths).toMap,lowered.encode(expected).paths)
    }
    add("bits-bytes") {
      val table=PathItemInterner();alphabet.toVector.sortBy(_.show).foreach(table.intern)
      val bits=RadixTranslations.symbolsToBits(entry,routines,table)
      val codec=PathEncodings.bitsToBytes
      val translated=RadixTranslations.bitsToBytes(bits.program.entry,bits.program.routines)
      check(OpenFstBackend.bytes(translated.entry,translated.routines),paths.view.mapValues(v=>codec.encode(bits.encoding.encode(v))).toMap,
        spaces.view.mapValues(v=>v.paths.map(p=>codec.encode(bits.encoding.encode(p)))).toMap,
        expected.paths.map(p=>codec.encode(bits.encoding.encode(p))))
    }
    add("bytes-bits") {
      val bytes=RadixTranslations.stringsToBytes(entry,routines)
      val codec=PathEncodings.bytesToBits
      val translated=RadixTranslations.bytesToBits(bytes.program.entry,bytes.program.routines)
      check(OpenFstBackend.bits(translated.entry,translated.routines),paths.view.mapValues(v=>codec.encode(bytes.encoding.encode(v))).toMap,
        spaces.view.mapValues(v=>v.paths.map(p=>codec.encode(bytes.encoding.encode(p)))).toMap,
        expected.paths.map(p=>codec.encode(bytes.encoding.encode(p))))
    }
    add("bits-symbols") {
      val table=PathItemInterner();alphabet.toVector.sortBy(_.show).foreach(table.intern)
      val bits=RadixTranslations.symbolsToBits(entry,routines,table)
      val codec=PathEncodings.bitsToSymbols
      val translated=RadixTranslations.bitsToSymbols(bits.program.entry,bits.program.routines)
      check(OpenFstBackend.symbols(translated.entry,translated.routines),paths.view.mapValues(v=>codec.encode(bits.encoding.encode(v))).toMap,
        spaces.view.mapValues(v=>v.paths.map(p=>codec.encode(bits.encoding.encode(p)))).toMap,
        expected.paths.map(p=>codec.encode(bits.encoding.encode(p))))
    }
    add("bytes-symbols") {
      val table=PathItemInterner();alphabet.toVector.sortBy(_.show).foreach(table.intern)
      val bytes=RadixTranslations.internedSymbolsToBytes(entry,routines,table)
      val codec=PathEncodings.bytesToSymbols
      val translated=RadixTranslations.bytesToSymbols(bytes.program.entry,bytes.program.routines)
      check(OpenFstBackend.symbols(translated.entry,translated.routines),paths.view.mapValues(v=>codec.encode(bytes.encoding.encode(v))).toMap,
        spaces.view.mapValues(v=>v.paths.map(p=>codec.encode(bytes.encoding.encode(p)))).toMap,
        expected.paths.map(p=>codec.encode(bytes.encoding.encode(p))))
    }

  private def quote(s: String): String = "\"" + s.flatMap {
    case '\"' => "\\\""
    case '\\' => "\\\\"
    case '\n' => "\\n"
    case '\r' => "\\r"
    case '\t' => "\\t"
    case c if c < ' ' => f"\\u${c.toInt}%04x"
    case c => c.toString
  } + "\""
  def finish(symbolCases: Int): Unit =
    val output = Paths.get(System.getProperty("morkl.openfstAudit.path"))
    Files.createDirectories(output)
    val writer = Files.newBufferedWriter(output.resolve("checks.jsonl"))
    def record(line: String): Unit = writer.synchronized { writer.write(line);writer.newLine();writer.flush() }
    import scala.jdk.CollectionConverters.*
    val snapshot = cases.iterator().asScala.toVector
    val workers = Integer.getInteger("morkl.openfstAudit.workers",32).intValue().max(1)
    val batch = Integer.getInteger("morkl.openfstAudit.batch",64).intValue().max(1)
    val pool = Executors.newFixedThreadPool(workers,new ThreadFactory:
      def newThread(r: Runnable): Thread = {val t = Thread(r,"openfst-audit");t.setDaemon(true);t}
    )
    val executions = Executors.newFixedThreadPool(workers,new ThreadFactory:
      def newThread(r: Runnable): Thread = {val t=Thread(r,"openfst-native-check");t.setDaemon(true);t}
    )
    println(s"OpenFst audit: ${snapshot.size} cases ($symbolCases symbolic frames), $workers workers, batches of $batch")
    snapshot.zipWithIndex.grouped(batch).foreach { group => pool.submit(new Runnable:
      def run(): Unit =
        busy.set(true)
        val prepared = group.map { (c,id) =>
          try Right((c,id,c.compile()))
          catch case e: Throwable =>
            failures.incrementAndGet(); checks.incrementAndGet()
            record(s"{\"case\":$id,\"dialect\":${quote(c.dialect)},\"status\":\"compile-failure\",\"error\":${quote(e.toString)}}")
            Left(e)
        }.collect {case Right(value) => value}
        val limit=Integer.getInteger("morkl.openfstAudit.codeBytes",2000000).intValue().max(10000)
        val chunks=scala.collection.mutable.ArrayBuffer.empty[Vector[(Case,Int,Check)]]
        var pending=Vector.empty[(Case,Int,Check)]
        var bytes=0
        prepared.foreach { item =>
          val size=item._3.program.code.length
          if pending.nonEmpty && bytes.toLong+size>limit then {chunks+=pending;pending=Vector.empty;bytes=0}
          pending :+= item;bytes+=size
        }
        if pending.nonEmpty then chunks+=pending
        chunks.foreach { chunk => executions.submit(new Runnable:
          def run(): Unit =
            busy.set(true)
            var session = Option.empty[OpenFstBackend.Session]
            try
              if chunk.nonEmpty then
                session = Some(OpenFstBackend.Session(chunk.map(_._3.program).toVector))
                chunk.zipWithIndex.foreach { case ((c,id,value),index) =>
                  record(s"{\"case\":$id,\"dialect\":${quote(c.dialect)},\"status\":\"start\"}")
                  val start = System.nanoTime()
                  try
                    value.run(session.get,index)
                    counts.computeIfAbsent(c.dialect,_ => AtomicLong(0)).incrementAndGet()
                    record(s"{\"case\":$id,\"dialect\":${quote(c.dialect)},\"caller\":${quote(c.caller)},\"status\":\"pass\",\"micros\":${(System.nanoTime()-start)/1000}}")
                  catch case e: Throwable =>
                    failures.incrementAndGet()
                    record(s"{\"case\":$id,\"dialect\":${quote(c.dialect)},\"caller\":${quote(c.caller)},\"status\":\"fail\",\"error\":${quote(e.toString)}}")
                  finally
                    val n = checks.incrementAndGet()
                    if n % 1000 == 0 || n == snapshot.size then println(s"OpenFst audit: $n/${snapshot.size}, ${failures.get()} failures")
                }
            catch case e: Throwable =>
              failures.addAndGet(chunk.size)
              checks.addAndGet(chunk.size)
              record(s"{\"status\":\"batch-failure\",\"cases\":${chunk.map(_._2).mkString("[",",","]")},\"error\":${quote(e.toString)}}")
            finally
              session.foreach(_.close());busy.remove()
        ) }
        busy.remove()
    ) }
    pool.shutdown()
    val seconds=java.lang.Long.getLong("morkl.openfstAudit.seconds",14400L).longValue()
    val prepared=pool.awaitTermination(seconds,TimeUnit.SECONDS)
    if !prepared then pool.shutdownNow()
    executions.shutdown()
    val finished=prepared && executions.awaitTermination(seconds,TimeUnit.SECONDS)
    if !finished then {failures.incrementAndGet();executions.shutdownNow()}
    val passed = counts.asScala.toVector.sortBy(_._1).map((k,v) => quote(k)+":"+v.get()).mkString(",")
    val summary = s"{\"cases\":${snapshot.size},\"symbolCases\":$symbolCases,\"digitObservations\":${observations.get()},\"checks\":${checks.get()},\"failures\":${failures.get()},\"finished\":$finished,\"passed\":{$passed}}"
    Files.writeString(output.resolve("summary.json"),summary+"\n");writer.close();println("OpenFst audit: "+summary)
