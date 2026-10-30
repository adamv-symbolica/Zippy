package morkl

import java.nio.file.{Files,Path as FilePath,Paths}
import java.security.MessageDigest
import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.mutable

/** Reproducible, standalone Rust source and byte fixtures for manual review. */
object CornerstoneRustExport:
  private case class Work(family: String,name: String,variant: String,expr: Space,context: SpaceContextMap,
                          routines: PartialFunction[RoutinePtr,Routine])
  private def json(s: String): String = "\""+s.flatMap {
    case '"'=>"\\\"";case '\\'=>"\\\\";case '\n'=>"\\n";case '\r'=>"\\r";case '\t'=>"\\t"
    case c if c<' ' || Character.isSurrogate(c)=>f"\\u${c.toInt}%04x"
    case c=>c.toString
  }+"\""
  private def write(path: FilePath,text: String): Unit =
    Files.createDirectories(path.getParent);Files.writeString(path,text,UTF_8)
  private def slug(s: String)=s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+","-").stripSuffix("-")
  def run(output: FilePath): Unit =
    require(!Files.exists(output.resolve("manifest.json")),s"export exists: $output")
    val records=mutable.ArrayBuffer.empty[String]
    val all=TrieBenchmarks.cases().map(c=>Work("programs",c.name,c.variant,c.expr,c.sc,c.rc)) ++
      ZipperLargeBenchmarks.cases().map(c=>Work("large",c.name,c.size,c.expr,c.sc,c.rc))
    for (work,i)<-all.zipWithIndex do
      val key=f"${i+1}%02d-${slug(work.name)}-${slug(work.variant)}"
      val folder=output.resolve(work.family).resolve(key)
      Console.err.println(s"[rust-export] $key")
      val defs=OpenFstBackend.reachable(work.expr,work.routines)
      val interner=PathItemInterner()
      work.context.m.valuesIterator.flatMap(_.paths).flatMap(_.items).toSet.toVector.sortBy(_.show).foreach(interner.intern)
      val bytes=RadixTranslations.internedSymbolsToBytes(work.expr,defs,interner)
      val strings=RadixTranslations.stringsToBytes(work.expr,defs)
      val bits=RadixTranslations.symbolsToBits(work.expr,defs,interner)
      val injected=RadixTranslations.bitsToBytes(bits.program.entry,bits.program.routines)
      val bitCodec=PathEncodings.bitsToBytes
      write(folder.resolve("symbolic.zippy"),work.expr.show+"\n\n"+defs.values.map(_.show).mkString("\n\n"))
      val encodingMap=Vector.tabulate(interner.size) { id=>
        val symbol=interner.decodeItem(id);val p=PathValue(List(symbol))
        def digits(p: BytePathValue)=BytePaths.digits(p).mkString("[",",","]")
        s"{\"symbol\":${json(symbol.show)},\"bytes-interned\":${digits(bytes.encoding.encode(p))},\"bytes-strings\":${digits(strings.encoding.encode(p))},\"bits-bytes\":${digits(bitCodec.encode(bits.encoding.encode(p)))}}"
      }
      write(folder.resolve("encodings.json"),encodingMap.mkString("[\n",",\n","\n]\n"))
      val expected=evalTrie(work.expr)(using sc=TrieSpaceContext.fromReference(work.context),rc=work.routines).toSpaceValue
      write(folder.resolve("expected.json"),expected.paths.toVector.map(p=>p.items.map(s=>json(s.show)).mkString("[",",","]")).sorted.mkString("[",",\n","]\n"))
      def emit(encoding: String,p: RustPathMapBackend.Compiled,spaces: Map[SpaceMention,Set[BytePathValue]]): Unit =
        require(p.callbackCount==0,s"$key requires grounded bindings")
        val base=folder.resolve(encoding)
        write(folder.resolve(encoding+".rs"),p.source)
        write(folder.resolve(encoding+".graph.txt"),p.graphDescription)
        val ss=spaces.toVector.flatMap((ref,values)=>p.spaceIds.get(ref).map(id=>id->values.toVector.map(p.labels.encode).sorted)).sortBy(_._1)
        def path(v: Vector[Int])=(v.size+:v).mkString(" ")
        val request=s"R 0 0 0 ${ss.size} "+ss.map((id,ps)=>s"$id ${ps.size} "+ps.map(path).mkString(" ")).mkString(" ")+"\nQ\n"
        write(folder.resolve(encoding+".input.txt"),request)
        val hash=MessageDigest.getInstance("SHA-256").digest(p.source.getBytes(UTF_8)).map(b=>f"${b&255}%02x").mkString
        val source=s"${work.family}/$key/$encoding.rs"
        records+=s"{\"family\":${json(work.family)},\"name\":${json(work.name)},\"variant\":${json(work.variant)},\"dialect\":${json(encoding)},\"source\":${json(source)},\"sha256\":${json(hash)},\"bytes\":${p.source.getBytes(UTF_8).length},\"graphNodes\":${p.graphNodes},\"precomputed\":${p.precomputed}}"
      emit("bytes-interned",RustPathMapBackend.bytes(bytes.program.entry,bytes.program.routines),work.context.m.view.mapValues(v=>bytes.encode(v).paths).toMap)
      emit("bytes-strings",RustPathMapBackend.bytes(strings.program.entry,strings.program.routines),work.context.m.view.mapValues(v=>strings.encode(v).paths).toMap)
      emit("bits-bytes",RustPathMapBackend.bytes(injected.entry,injected.routines),work.context.m.view.mapValues(v=>bits.encode(v).paths.map(bitCodec.encode)).toMap)
    for relative<-Vector("Cargo.toml","Cargo.lock","src/lib.rs",".cargo/config.toml") do
      val target=output.resolve("runtime").resolve(relative)
      write(target,Files.readString(Paths.get("src/main/rust/pathmap").resolve(relative)))
    write(output.resolve("manifest.json"),s"{\"workloads\":${all.size},\"artifacts\":[${records.mkString(",\n")}]}\n")
    write(output.resolve("README.md"),"""# Rust cornerstone review

36 logical cases, each compiled from its optimized executor graph for three byte encodings.
Each case contains the symbolic source, Rust translation units, graph dumps,
wire-protocol inputs, codeword dictionary and the independent Scala trie result.
Runtime PathMap labels are actual bytes 0..255; only protocol labels use byte+1.
No encoding dictionary is loaded into a runtime space.

Build the runtime with `cd runtime && cargo build --release --locked`.
Compile a source with `rustc --edition=2024 -O -C target-cpu=native
-L dependency=runtime/target/release/deps
--extern zippy_pathmap=runtime/target/release/libzippy_pathmap.rlib CASE.rs -o program`.
Run `./program < CASE.input.txt`. Use the repository's `tools/check_cornerstone_rust.py`
to compile every source and compare its decoded result with expected.json.
""")

@main def exportCornerstoneRust(output: String = "output/cornerstone-rust-review"): Unit =
  CornerstoneRustExport.run(Paths.get(output))
