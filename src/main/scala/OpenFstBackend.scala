package morkl

import java.nio.file.{Files, Path as FilePath, Paths, StandardOpenOption, StandardCopyOption}
import java.nio.channels.{FileChannel, FileLock, OverlappingFileLockException}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/** C++ source generation for all three dialects. Every space in the executable
  * is an OpenFst acceptor; no JVM space operation participates in evaluation.
  * Grounded functions alone cross an explicit callback protocol.
  */
object OpenFstBackend:
  trait Labels[V]:
    def encode(value: V): Vector[Int]
    def decode(value: Vector[Int]): V
    def symbolic: Boolean = false
    def symbols: Vector[(Int, String)] = Vector.empty
  private class SymbolLabels extends Labels[PathValue]:
    private val ids = mutable.LinkedHashMap.empty[PathItem, Int]
    private val table = mutable.ArrayBuffer.empty[PathItem]
    def encode(value: PathValue): Vector[Int] = synchronized {
      value.items.iterator.map(v => ids.getOrElseUpdate(v, {table += v; table.size})).toVector
    }
    def decode(value: Vector[Int]): PathValue = synchronized {
      PathValue(value.map(i => table(i - 1)).toList)
    }
    override val symbolic = true
    override def symbols: Vector[(Int, String)] = synchronized { ids.iterator.map((s,i) => i -> s.show).toVector }
  private def digitLabels[V](paths: DigitPaths[V]): Labels[V] = new Labels[V]:
    def encode(value: V): Vector[Int] = paths.digits(value).map(_ + 1)
    def decode(value: Vector[Int]): V = paths.fromDigits(value.map(_ - 1))

  final class Compiled[V] private[morkl](val code: String, val entryFunction: String,
      val pathIds: Map[PathRef, Int], val spaceIds: Map[SpaceMention, Int], val labels: Labels[V],
      private[morkl] val callbacks: Vector[Vector[Vector[Int]] => Vector[Vector[Int]]],
      val nativeFunctions: Map[Int,String] = Map.empty):
    private[morkl] val initialSymbols = labels.symbols
    val callbackCount: Int = callbacks.size
    def isStandalone: Boolean = callbacks.indices.forall(nativeFunctions.contains)
    /** Bodies take (Context& c, const Space& argument) and return Space. Path
      * callbacks receive/return singleton acceptors. IDs follow source emission
      * order and appear explicitly in source; no new AST operation is added.
      */
    def withNativeFunctions(bindings: Map[Int,String]): Compiled[V] =
      require(bindings.keys.forall(i=>i>=0 && i<callbackCount),"unknown grounded callback ID")
      Compiled(code,entryFunction,pathIds,spaceIds,labels,callbacks,nativeFunctions++bindings)
    def source: String = translationUnit(Vector(this))
    def writeSource(path: FilePath): Unit = Files.writeString(path, source)
    def eval(paths: Map[PathRef, V] = Map.empty, spaces: Map[SpaceMention, Set[V]] = Map.empty): Set[V] =
      val session = Session(Vector(this))
      try session.eval(0, this)(paths, spaces) finally session.close()
    private[morkl] def module(namespace: String): String =
      val native=nativeFunctions.toVector.sortBy(_._1).map((id,body)=>s"static Space grounded_$id(Context& c,const Space& argument) { $body }").mkString("\n")
      s"namespace $namespace {\n$code\n$native\n}\n"

  def symbols(entry: Space, routines: Map[RoutinePtr, Routine] = Map.empty): Compiled[PathValue] =
    val compiler = Generator(SymbolSyntax, SymbolLabels())
    compiler.compile(SymbolSyntax.importSpace(entry), routines.view.mapValues(SymbolSyntax.importRoutine).toMap)
  def bits(entry: BitSpace, routines: Map[RoutinePtr, Zippy_bits.Routine] = Map.empty): Compiled[BigInt] =
    Generator(Zippy_bits, digitLabels(BitPaths)).compile(entry, routines)
  def bytes(entry: ByteSpace, routines: Map[RoutinePtr, Zippy_bytes.Routine] = Map.empty): Compiled[BytePathValue] =
    Generator(Zippy_bytes, digitLabels(BytePaths)).compile(entry, routines)

  private[morkl] def compileDigits[V,L <: DigitLanguage[V]](source: L)(entry: source.Space, routines: Map[RoutinePtr,source.Routine]): Compiled[V] =
    val generator = new Generator[V,source.type](source,digitLabels(source.paths))
    generator.compile(entry,routines)

  def reachable(entry: Space, definitions: PartialFunction[RoutinePtr,Routine]): Map[RoutinePtr,Routine] =
    val module = mutable.LinkedHashMap.empty[RoutinePtr,Routine]
    def scan(s: Space): Unit =
      val calls = mutable.ArrayBuffer.empty[RoutinePtr]
      subs(s)(spost={case c @ Space.Call(r,_,_) => calls += r;c})
      calls.foreach { r =>
        if !module.contains(r) then
          val routine = definitions(r);module(r)=routine;scan(routine.body)
      }
    scan(entry);module.toMap

  private def translationUnit(programs: Vector[Compiled[?]]): String =
    val modules = programs.zipWithIndex.map((p,i) => p.module(s"program_$i")).mkString
    val dispatch = programs.zipWithIndex.map((p,i) =>
      val table=p.initialSymbols
      val symbols=s"${table.size} "+table.map((id,text)=>s"$id ${text.length} "+text.toVector.map(_.toInt).mkString(" ")).mkString(" ")
      val native=p.nativeFunctions.keys.toVector.sorted.map(id=>s"c.native_callbacks[$id]=program_$i::grounded_$id;").mkString
      s"""case $i: { if(c.program_id!=$i) { c.program_id=$i; c.native_callbacks.clear(); $native c.symbolic=${p.labels.symbolic}; configure_symbols(c,"$symbols"); } return program_$i::${p.entryFunction}(c,e); }"""
    ).mkString("\n")
    s"""#include "zippy_openfst.hpp"
       |#include <iostream>
       |#include <stdexcept>
       |#if defined(__GNUC__) && !defined(__clang__) && __GNUC__ >= 9
       |#define ZIPPY_NOINLINE __attribute__((noipa))
       |#elif defined(__GNUC__)
       |#define ZIPPY_NOINLINE __attribute__((noinline))
       |#else
       |#define ZIPPY_NOINLINE
       |#endif
       |using namespace zippy;
       |$modules
       |int main() {
       | Context c(std::cin,std::cout);
       | return run(c, [](int id, Context& c, const Env& e) -> Space {
       |   switch(id) { $dispatch default: throw std::runtime_error("unknown program"); }
       | });
       |}
       |""".stripMargin

  private class Generator[V, L <: DigitLanguage[V]](val dialect: L, labels: Labels[V]):
    import dialect.{Path as P, Space as S}
    private val definitions = mutable.ArrayBuffer.empty[String]
    private val declarations = mutable.ArrayBuffer.empty[String]
    private val callbacks = mutable.ArrayBuffer.empty[Vector[Vector[Int]] => Vector[Vector[Int]]]
    private val pathIds = mutable.LinkedHashMap.empty[PathRef, Int]
    private val spaceIds = mutable.LinkedHashMap.empty[SpaceMention, Int]
    private val pmemo = java.util.IdentityHashMap[dialect.Path, String]()
    private val smemo = java.util.IdentityHashMap[dialect.Space, String]()
    private var routines = Map.empty[RoutinePtr, dialect.Routine]
    private var routineIds = Map.empty[RoutinePtr, String]
    private val neededRoutines = mutable.LinkedHashSet.empty[RoutinePtr]
    private var serial = 0
    private var headSelection: OpenFstHeadSelection[V,dialect.type] = null
    private def pid(p: PathRef): Int = pathIds.getOrElseUpdate(p, pathIds.size)
    private def sid(s: SpaceMention): Int = spaceIds.getOrElseUpdate(s, spaceIds.size)
    private def binder(p: PathRef): Int = if p.s == "_" then -1 else pid(p)
    private def binder(s: SpaceMention): Int = if s.s == "_" then -1 else sid(s)
    private def path(v: V): String = labels.encode(v).mkString("Path{", ",", "}")
    private def literal(vs: Set[V]): String =
      val values=vs.toVector.map(labels.encode).sorted
      val text=s"${values.size} "+values.map(p=>s"${p.size} "+p.mkString(" ")).mkString(" ")
      s"""literal_text("$text")"""
    private def node(kind: String, register: String => Unit)(body: => String): String =
      val name = s"n$serial"; serial += 1
      register(name)
      declarations += s"static ZIPPY_NOINLINE $kind $name(Context&, const Env&);"
      definitions += s"static ZIPPY_NOINLINE $kind $name(Context& c, const Env& e) { ${body} }"
      name
    private def callback(f: Vector[Vector[Int]] => Vector[Vector[Int]]): Int =
      val n = callbacks.size; callbacks += f; n
    private def cp(x: dialect.Path): String =
      val old = pmemo.get(x)
      if old != null then old
      else
        val name = node("Path", n => {pmemo.put(x,n);()})(x match
          case P.Deref(r) => s"return e.paths.at(${pid(r)});"
          case P.Constant(v) => s"return ${path(v)};"
          case P.Concat(a,b) => s"auto a=${cp(a)}(c,e); auto b=${cp(b)}(c,e); return concat_path(a,b);"
          case P.GroundedPP(a,f) =>
            val id = callback(ps => Vector(labels.encode(f(labels.decode(ps.head)))))
            s"return c.callback_path($id,singleton(${cp(a)}(c,e)));"
          case P.GroundedSP(a,f) =>
            val id = callback(ps => Vector(labels.encode(f(dialect.SpaceValue(ps.map(labels.decode).toSet)))))
            s"return c.callback_path($id,${cs(a)}(c,e));"
        )
        pmemo.put(x,name); name
    private def binary(a: dialect.Space, b: dialect.Space, op: String): String =
      s"auto a=${cs(a)}(c,e); auto b=${cs(b)}(c,e); return $op(a,b);"
    private def epsilon(x: dialect.Space): Boolean = x match
      case S.Singleton(P.Constant(v)) => dialect.paths.length(v)==0
      case _ => false
    private def truth(x: dialect.Space): Option[String] = x match
      case S.Union(S.Intersection(value,unit),S.Iteration(same,_,_,body)) if value==same && epsilon(unit) && epsilon(body) =>
        value match
          case S.Mention(r) => Some(s"return e.spaces.at(${sid(r)}).empty()?Space():singleton(Path{});")
          case S.TailsUnion(S.Mention(r)) => Some(s"return head_space(e.spaces.at(${sid(r)})).empty()?Space():singleton(Path{});")
          case _ => None
      case _ => None
    private def cs(x: dialect.Space): String =
      val old = smemo.get(x)
      if old != null then old
      else
        val name = node("Space", n => {smemo.put(x,n);()})(truth(x) match
          case Some(code) => code
          case None => headSelection.plan(x) match
            case Some(plan) =>
              val codes=plan.codes
              val fibonacci=codes.zipWithIndex.forall { (code,index) =>
                scala.util.Try(FibonacciCode.decode(code.map(_-1))==Vector(BigInt(index+1))).getOrElse(false)
              }
              val input=cs(plan.input)
              if fibonacci && codes.nonEmpty then
                s"return first_fibonacci($input(c,e),${codes.size},${codes.map(_.size).max},${plan.prefix});"
              else
                val text=s"${codes.size} "+codes.map(p=>s"${p.size} "+p.mkString(" ")).mkString(" ")
                if codes==codes.sorted then
                  s"""static const Space allowed=literal_text("$text");return first_matching($input(c,e),allowed,${plan.prefix});"""
                else s"""static const auto order=paths_text("$text");return first_listed($input(c,e),order,${plan.prefix});"""
            case None => x match
              case S.Empty => "return Space();"
              case S.Mention(r) => s"return e.spaces.at(${sid(r)});"
              case S.Literal(v) => s"static const Space value=${literal(v.paths)}; return value;"
              case S.Singleton(p) => s"return singleton(${cp(p)}(c,e));"
              case S.Call(r,ps,ss) =>
                neededRoutines += r
                val fn = routines.getOrElse(r,throw IllegalArgumentException(s"undefined routine: ${r.s}"))
                require(ps.size == fn.refs.size && ss.size == fn.mentions.size,s"arity mismatch: ${r.s}")
                val pv = ps.map(p => s"${cp(p)}(c,e)").mkString("std::vector<Path> pv{",",","};")
                val sv = ss.map(s => s"${cs(s)}(c,e)").mkString("std::vector<Space> sv{",",","};")
                val binds = fn.refs.zipWithIndex.map((r,i) => s"n.paths[${pid(r)}]=pv[$i];").mkString +
                  fn.mentions.zipWithIndex.map((r,i) => s"n.spaces[${sid(r)}]=sv[$i];").mkString
                val stationary = fn.body match
                  case S.Union(left,S.Call(`r`,`ps`,`ss`)) =>
                    val tests = ps.zipWithIndex.map((p,i) => s"${cp(p)}(c,n)==pv[$i]") ++
                      ss.zipWithIndex.map((s,i) => s"equal(${cs(s)}(c,n),sv[$i])")
                    s"if(${if tests.isEmpty then "true" else tests.mkString(" && ")}) return ${cs(left)}(c,n);"
                  case _ => ""
                s"$pv $sv Env n; $binds $stationary return ${routineIds(r)}(c,n);"
              case S.Union(a,b) => binary(a,b,"unite")
              case S.Intersection(a,b) => binary(a,b,"intersect")
              case S.Subtraction(a,b) => binary(a,b,"subtract")
              case S.Restriction(a,b) =>
                s"auto b=${cs(b)}(c,e); auto a=${cs(a)}(c,e); return restrict_to(a,b);"
              case S.Raffination(a,b) =>
                // Reference semantics evaluate a separately on both sides.
                s"auto a=${cs(a)}(c,e); auto b=${cs(b)}(c,e); auto again=${cs(a)}(c,e); return subtract(a,restrict_to(again,b));"
              case S.Composition(a,b) => s"auto a=${cs(a)}(c,e); if(a.empty()) return a; return compose(a,${cs(b)}(c,e));"
              case S.Wrap(a,p) => s"auto p=${cp(p)}(c,e); return wrap(${cs(a)}(c,e),p);"
              case S.Unwrap(a,p) => s"auto p=${cp(p)}(c,e); return unwrap(${cs(a)}(c,e),p);"
              case S.TailsUnion(a) => s"return tails_union(${cs(a)}(c,e));"
              case S.TailsIntersection(a) => s"return tails_intersection(${cs(a)}(c,e));"
              case S.PrefixClosure(a) => s"return prefix_closure(${cs(a)}(c,e));"
              case S.SuffixClosure(a) => s"return suffix_closure(${cs(a)}(c,e),false);"
              case S.TailsClosure(a) => s"return suffix_closure(${cs(a)}(c,e),true);"
              case S.Range(a,start,end) => s"return range(c,${cs(a)}(c,e),$start,$end);"
              case S.Iteration(a,h,_,S.Singleton(P.Deref(r))) if h.s!="_" && r==h => s"return head_space(${cs(a)}(c,e));"
              case S.Iteration(a,_,t,S.Mention(r)) if t.s!="_" && r==t => s"return tails_union(${cs(a)}(c,e));"
              case S.Iteration(a,h,t,b) => s"return iterate(c,e,${cs(a)}(c,e),${binder(h)},${binder(t)},${cs(b)});"
              case S.Fold(a,i,acc,h,t,b,u) =>
                s"auto initial=${cp(i)}(c,e); auto src=${cs(a)}(c,e); return fold(c,e,src,initial,${binder(acc)},${binder(h)},${binder(t)},${cs(b)},${cp(u)});"
              case S.Fixpoint(a,v,b) => s"return fixpoint(c,e,${cs(a)}(c,e),${binder(v)},${cs(b)});"
              case S.GroundedPS(p,f) =>
                val id = callback(ps => f(labels.decode(ps.head)).paths.toVector.map(labels.encode))
                s"return c.callback_space($id,singleton(${cp(p)}(c,e)));"
              case S.GroundedSS(s,f) =>
                val id = callback(ps => f(dialect.SpaceValue(ps.map(labels.decode).toSet)).paths.toVector.map(labels.encode))
                s"return c.callback_space($id,${cs(s)}(c,e));"
        )
        smemo.put(x,name); name
    def compile(entry: dialect.Space, module: Map[RoutinePtr, dialect.Routine]): Compiled[V] =
      routines = module
      headSelection = new OpenFstHeadSelection[V,dialect.type](dialect,labels.encode,routines)
      routineIds = module.keys.toVector.sortBy(_.s).zipWithIndex.map((r,i) => r -> s"routine_$i").toMap
      val root = cs(entry)
      val emitted = mutable.HashSet.empty[RoutinePtr]
      // Native idiom selection can eliminate whole scanner subgraphs. Emit
      // only calls surviving that selection, including their transitive and
      // mutually recursive dependencies, rather than the original module.
      while neededRoutines.exists(r => !emitted(r)) do
        val r = neededRoutines.find(r => !emitted(r)).get
        emitted += r
        declarations += s"static ZIPPY_NOINLINE Space ${routineIds(r)}(Context&, const Env&);"
        val body = cs(module(r).body)
        definitions += s"static ZIPPY_NOINLINE Space ${routineIds(r)}(Context& c,const Env& e) { return $body(c,e); }"
      Compiled((declarations ++ definitions).mkString("\n"),root,pathIds.toMap,spaceIds.toMap,labels,callbacks.toVector)

  /** A batch shares one native executable; each entry has its own label table
    * and callbacks. This keeps full-suite auditing practical without interpreting
    * the Zippy AST in C++ or substituting precomputed reference answers.
    */
  case class NativeStats(nanos: Long, states: Long, arcs: Long, paths: BigInt)
  final class Session private (val programs: Vector[Compiled[?]], executable: FilePath) extends AutoCloseable:
    private val process = ProcessBuilder(executable.toString).redirectError(ProcessBuilder.Redirect.INHERIT).start()
    private val input = process.inputReader(StandardCharsets.UTF_8)
    private val output = process.outputWriter(StandardCharsets.UTF_8)
    private def write(line: String): Unit = { output.write(line); output.newLine(); output.flush() }
    def eval[V](id: Int, program: Compiled[V])(paths: Map[PathRef,V], spaces: Map[SpaceMention,Set[V]]): Set[V] =
      request(id,program,"R")(paths,spaces)._1
    def benchmark[V](id: Int, program: Compiled[V], repeats: Int)(paths: Map[PathRef,V], spaces: Map[SpaceMention,Set[V]]): NativeStats =
      require(repeats > 0)
      val (value,stats) = request(id,program,s"B")(paths,spaces,repeats)
      stats.get
    private def request[V](id: Int, program: Compiled[V], mode: String)(paths: Map[PathRef,V], spaces: Map[SpaceMention,Set[V]], repeats: Int = 1): (Set[V],Option[NativeStats]) = synchronized {
      require(programs(id) eq program)
      val ps = paths.toVector.map((r,v) => program.pathIds.get(r).map(_ -> program.labels.encode(v))).flatten
      val ss = spaces.toVector.map((r,vs) => program.spaceIds.get(r).map(_ -> vs.toVector.map(program.labels.encode))).flatten
      def path(v: Vector[Int]): String = s"${v.size} ${v.mkString(" ")}"
      def space(v: Vector[Vector[Int]]): String = s"${v.size} ${v.map(path).mkString(" ")}"
      def symbols: String =
        val table = program.labels.symbols
        s"${table.size} " + table.map((i,s) => s"$i ${s.length} " + s.toVector.map(_.toInt).mkString(" ")).mkString(" ")
      val repetitions = if mode == "B" then s" $repeats" else ""
      write(s"$mode $id$repetitions $symbols ${ps.size} " + ps.map((i,v) => s"$i ${path(v)}").mkString(" ") +
        s" ${ss.size} " + ss.map((i,v) => s"$i ${space(v)}").mkString(" "))
      def parse(tokens: Iterator[String]): Vector[Vector[Int]] =
        Vector.fill(tokens.next().toInt) { Vector.fill(tokens.next().toInt)(tokens.next().toInt) }
      var result = Option.empty[Set[V]]
      var stats = Option.empty[NativeStats]
      while result.isEmpty do
        val line = input.readLine()
        if line == null then throw IllegalStateException(s"native process exited without a result (exit ${if process.isAlive then "running" else process.exitValue().toString})")
        val tokens = line.split(" ").iterator.filter(_.nonEmpty)
        tokens.next() match
          case "O" => result = Some(parse(tokens).map(program.labels.decode).toSet)
          case "N" if mode == "B" => result = Some(Set.empty[V])
          case "T" => stats = Some(NativeStats(tokens.next().toLong,tokens.next().toLong,tokens.next().toLong,BigInt(tokens.next())))
          case "C" =>
            val callback = tokens.next().toInt
            try
              val value = program.callbacks(callback)(parse(tokens))
              write(s"V $symbols ${space(value)}")
            catch
              case error: Throwable => process.destroyForcibly(); throw error
          case "E" => throw IllegalStateException(s"OpenFst execution failed: $line")
          case other => throw IllegalStateException(s"unexpected native response: $other")
      result.get -> stats
    }
    def close(): Unit =
      try
        if process.isAlive then
          scala.util.Try(write("Q"))
          if !process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS) then process.destroyForcibly()
      finally
        scala.util.Try(output.close());scala.util.Try(input.close())
  object Session:
    def apply(programs: Vector[Compiled[?]]): Session = new Session(programs, build(translationUnit(programs)))

  private def root: FilePath = Paths.get(System.getProperty("morkl.openfst.root", ".")).toAbsolutePath.normalize()
  private def cache: FilePath = Paths.get(System.getProperty("morkl.openfst.cache", root.resolve("target/openfst").toString))
  private def hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  private def flags(property: String, fallback: String): Vector[String] =
    System.getProperty(property,fallback).split("\\s+").filter(_.nonEmpty).toVector
  private def command(args: Vector[String], log: FilePath): Unit =
    val process = ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(log.toFile).start()
    val seconds=java.lang.Long.getLong("morkl.openfst.compileSeconds",1800L).longValue().max(1L)
    if !process.waitFor(seconds,java.util.concurrent.TimeUnit.SECONDS) then
      process.descendants().forEach(_.destroyForcibly());process.destroyForcibly(); throw IllegalStateException(s"C++ compilation timed out; see $log")
    require(process.exitValue() == 0,s"C++ compilation failed; see $log:\n${Files.readString(log).takeRight(16000)}")
  private def locked[A](path: FilePath)(body: => A): A =
    val channel = FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE)
    try
      // POSIX blocking record locks detect cycles at process granularity.
      // Independent compiler threads in concurrent JVMs can look cyclic even
      // though each compilation is progressing. Nonblocking acquisition avoids
      // that false EDEADLK while retaining cross-process cache exclusion.
      var lock: FileLock = null
      while lock == null do
        try lock = channel.tryLock()
        catch case _: OverlappingFileLockException => ()
        if lock == null then Thread.sleep(20L)
      try body finally lock.release()
    finally channel.close()
  private lazy val runtimeLibrary: FilePath = synchronized {
    Files.createDirectories(cache)
    val dir = root.resolve("src/main/cpp")
    val stamp = hash(Files.readString(dir.resolve("zippy_openfst.hpp")) + Files.readString(dir.resolve("zippy_openfst.cpp")) +
      Files.readString(dir.resolve("CMakeLists.txt")) + System.getProperty("morkl.openfst.cxxflags", "") +
      System.getProperty("morkl.openfst.cxx","c++") + System.getProperty("morkl.openfst.prefix",""))
    val buildDir=cache.resolve(s"runtime-$stamp")
    val extension=if System.getProperty("os.name").toLowerCase.contains("mac") then "dylib" else "so"
    val library=buildDir.resolve(s"libzippy_openfst.$extension")
    locked(cache.resolve(s"runtime-$stamp.lock")) {
      if !Files.exists(library) then
        val prefix=Option(System.getProperty("morkl.openfst.prefix")).toVector.map("-DCMAKE_PREFIX_PATH="+_)
        command(Vector("cmake","-S",dir.toString,"-B",buildDir.toString,"-DCMAKE_BUILD_TYPE=Release",
          "-DCMAKE_CXX_COMPILER="+System.getProperty("morkl.openfst.cxx","c++"),
          "-DCMAKE_CXX_FLAGS="+System.getProperty("morkl.openfst.cxxflags",""))++prefix,cache.resolve(s"runtime-$stamp-configure.log"))
        command(Vector("cmake","--build",buildDir.toString,"--target","zippy_openfst","-j","4"),cache.resolve(s"runtime-$stamp-build.log"))
      library
    }
  }

  def build(source: String): FilePath =
    val library = runtimeLibrary
    val key = hash(source + library.toString + System.getProperty("morkl.openfst.ldflags", "") + System.getProperty("morkl.openfst.optimize","-O2"))
    val executable = cache.resolve(key)
    // Distinct batches compile in parallel; identical sources share a lock.
    key.intern().synchronized { locked(cache.resolve(s"$key.lock")) {
      if !Files.exists(executable) then
        val cpp = cache.resolve(s"$key.cpp")
        Files.writeString(cpp,source)
        val temporary = cache.resolve(s"$key.building")
        command(Vector(System.getProperty("morkl.openfst.cxx","c++"),"-std=c++17",System.getProperty("morkl.openfst.optimize","-O2"),"-pthread","-I"+root.resolve("src/main/cpp")) ++
          (if source.length>2000000 then Vector("-fno-inline") else Vector.empty) ++
          flags("morkl.openfst.cxxflags", "") ++ Vector(cpp.toString,library.toString,"-Wl,-rpath,"+library.getParent) ++
          flags("morkl.openfst.ldflags", "") ++ Vector("-o",temporary.toString),cache.resolve(s"$key.build.log"))
        Files.move(temporary,executable,StandardCopyOption.ATOMIC_MOVE)
      executable
    } }
