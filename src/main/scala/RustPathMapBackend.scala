package morkl

import java.nio.file.{Files,Path as FilePath,Paths,StandardCopyOption,StandardOpenOption}
import java.nio.channels.{FileChannel,FileLock,OverlappingFileLockException}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*

/** Rust compiler for Zippy_bytes. The existing symbol/bit translations feed
  * this backend without changing the byte language or adding space metadata.
  */
object RustPathMapBackend:
  final class Compiled private[morkl](val code: String, private[morkl] val wire: OpenFstBackend.Compiled[BytePathValue],
                                     val nativeFunctions: Map[Int,String] = Map.empty, private val emissionNanos: Long = 0L):
    def pathIds = wire.pathIds
    def spaceIds = wire.spaceIds
    def labels = wire.labels
    def graphDescription = wire.graphDescription
    def compileNanos = wire.compileNanos+emissionNanos
    def graphNodes = wire.graphNodes
    def precomputed = wire.precomputed
    def callbackCount = wire.callbackCount
    def isStandalone: Boolean = wire.callbacks.indices.forall(nativeFunctions.contains)
    /** Rust bodies take c: &mut Context and argument: &Space and return Space.
      * Path functions use singleton arguments/results. IDs are stable in source.
      */
    def withNativeFunctions(bindings: Map[Int,String]): Compiled =
      require(bindings.keys.forall(i=>i>=0 && i<callbackCount),"unknown grounded function ID")
      new Compiled(code,wire,nativeFunctions++bindings,emissionNanos)
    def source: String = translationUnit(Vector(this))
    def writeSource(path: FilePath): Unit = Files.writeString(path,source)
    def eval(paths: Map[PathRef,BytePathValue] = Map.empty, spaces: Map[SpaceMention,Set[BytePathValue]] = Map.empty): Set[BytePathValue] =
      val session=Session(Vector(this))
      try session.eval(0,this)(paths,spaces) finally session.close()
  private def byteLabels: OpenFstBackend.Labels[BytePathValue] = new OpenFstBackend.Labels[BytePathValue]:
    def encode(v: BytePathValue): Vector[Int] = BytePaths.digits(v).map(_+1)
    def decode(v: Vector[Int]): BytePathValue = BytePaths.fromDigits(v.map(_-1))
  def bytes(entry: ByteSpace, routines: Map[RoutinePtr,Zippy_bytes.Routine] = Map.empty): Compiled =
    val prepared=new OpenFstGraphCompiler(Zippy_bytes,byteLabels).prepare(entry,routines)
    emit(prepared)

  /** Direct executor-graph entry point. Byte graph constants use #digit:000
    * through #digit:255, the shared digit-lowering graph alphabet. */
  def fromGraph(graph: RecursiveOpGraph, callees: Map[String,RecursiveOpGraph] = Map.empty): Compiled =
    val prepared=new OpenFstGraphCompiler(Zippy_bytes,byteLabels).prepareGraph(graph,callees)
    emit(prepared)

  private def emit(prepared: NativeGraphProgram[BytePathValue]): Compiled =
    val start=System.nanoTime()
    val code=new RustPathMapGraphCompiler(prepared).compile()
    new Compiled(code,prepared.compiled,emissionNanos=System.nanoTime()-start)

  private def translationUnit(programs: Vector[Compiled]): String =
    val modules=programs.zipWithIndex.map { (p,i)=>
      val grounded=(0 until p.callbackCount).map { id=>
        val body=p.nativeFunctions.getOrElse(id,s"c.callback_space($id,argument)")
        s"#[inline] fn grounded_$id(c: &mut Context, argument: &Space) -> Space { $body }"
      }.mkString("\n")
      s"mod program_$i { use super::*;\n${p.code}\n$grounded\n}"
    }.mkString("\n")
    val dispatch=programs.indices.map(i=>s"$i => program_$i::entry(c,e),").mkString("\n")
    s"""#![allow(unused_imports,unused_variables,unused_mut,unused_parens,dead_code)]
       |use zippy_pathmap::*;
       |$modules
       |fn main() { run(|id,c,e| match id { $dispatch _ => panic!("unknown program") }); }
       |""".stripMargin

  final class Session private (val programs: Vector[Compiled], native: OpenFstBackend.Session) extends AutoCloseable:
    def eval(id: Int, p: Compiled)(paths: Map[PathRef,BytePathValue], spaces: Map[SpaceMention,Set[BytePathValue]]): Set[BytePathValue] =
      require(programs(id) eq p); native.eval(id,p.wire)(paths,spaces)
    def benchmark(id: Int, p: Compiled, repeats: Int)(paths: Map[PathRef,BytePathValue], spaces: Map[SpaceMention,Set[BytePathValue]]): OpenFstBackend.NativeStats =
      require(programs(id) eq p); native.benchmark(id,p.wire,repeats)(paths,spaces)
    def close(): Unit = native.close()
  object Session:
    def apply(programs: Vector[Compiled]): Session =
      new Session(programs,OpenFstBackend.Session.usingExecutable(programs.map(_.wire),build(translationUnit(programs))))

  private def root: FilePath = Paths.get(System.getProperty("morkl.pathmap.root",".")).toAbsolutePath.normalize()
  private def cache: FilePath = Paths.get(System.getProperty("morkl.pathmap.cache",root.resolve("target/pathmap").toString))
  private def hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)).map(b=>f"${b&255}%02x").mkString
  private def locked[A](path: FilePath)(body: => A): A =
    val channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE)
    try
      var lock: FileLock=null
      while lock==null do
        try lock=channel.tryLock() catch case _: OverlappingFileLockException => ()
        if lock==null then Thread.sleep(20)
      try body finally lock.release()
    finally channel.close()
  private def command(args: Vector[String], log: FilePath, directory: FilePath = root,
                      environment: Map[String,String] = Map.empty): Unit =
    val builder=ProcessBuilder(args.asJava).directory(directory.toFile).redirectErrorStream(true).redirectOutput(log.toFile)
    environment.foreach((k,v)=>builder.environment().put(k,v))
    val p=builder.start()
    val seconds=java.lang.Long.getLong("morkl.pathmap.compileSeconds",600L)
    if !p.waitFor(seconds,java.util.concurrent.TimeUnit.SECONDS) then
      p.descendants().forEach(_.destroyForcibly());p.destroyForcibly()
      throw IllegalStateException(s"Rust compilation timed out; see $log")
    require(p.exitValue()==0,s"Rust compilation failed; see $log:\n${Files.readString(log).takeRight(16000)}")
  private lazy val runtime: (FilePath,String) = synchronized {
    Files.createDirectories(cache)
    val crate=root.resolve("src/main/rust/pathmap")
    val versionProcess=ProcessBuilder(System.getProperty("morkl.pathmap.rustc","rustc"),"-vV").redirectErrorStream(true).start()
    require(versionProcess.waitFor(30,java.util.concurrent.TimeUnit.SECONDS),"rustc version query timed out")
    val version=versionProcess.inputReader(StandardCharsets.UTF_8).lines().iterator().asScala.mkString("\n")
    require(versionProcess.exitValue()==0,s"rustc version query failed: $version")
    val rustflags=System.getProperty("morkl.pathmap.rustflags","-C target-cpu=native")
    val identity=hash(Vector("Cargo.toml","Cargo.lock","src/lib.rs",".cargo/config.toml").map(p=>
      if Files.exists(crate.resolve(p)) then Files.readString(crate.resolve(p)) else "").mkString+rustflags+version)
    val target=cache.resolve("runtime-"+identity)
    locked(cache.resolve("runtime-"+identity+".lock")) {
      if !Files.exists(target.resolve("release/libzippy_pathmap.rlib")) then
        command(Vector(System.getProperty("morkl.pathmap.cargo","cargo"),"build","--release","--locked","--target-dir",target.toString),cache.resolve("runtime-"+identity+".log"),crate,Map("RUSTFLAGS"->rustflags))
    }
    target.resolve("release") -> identity
  }
  def build(source: String): FilePath =
    val (library,identity)=runtime
    val optimization=System.getProperty("morkl.pathmap.optimize","3")
    val flags=System.getProperty("morkl.pathmap.rustflags","-C target-cpu=native").split("\\s+").filter(_.nonEmpty).toVector
    val key=hash(source+identity+optimization+flags.mkString(" "))
    val executable=cache.resolve(key)
    locked(cache.resolve(key+".lock")) {
      if !Files.exists(executable) then
        val rs=cache.resolve(key+".rs");Files.writeString(rs,source)
        val temporary=cache.resolve(key+".building")
        command(Vector(System.getProperty("morkl.pathmap.rustc","rustc"),"--edition=2024","--crate-name","zippy_generated",
          "-C",s"opt-level=$optimization","-C","codegen-units=1","-L","dependency="+library.resolve("deps"),
          "--extern","zippy_pathmap="+library.resolve("libzippy_pathmap.rlib"),rs.toString,"-o",temporary.toString)++flags,cache.resolve(key+".build.log"))
        Files.move(temporary,executable,StandardCopyOption.ATOMIC_MOVE)
      executable
    }
