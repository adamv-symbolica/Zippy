package morkl

import java.nio.file.{Files, Path as FilePath, Paths, StandardCopyOption}
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import scala.collection.mutable

/** Review artifacts only: source emission never constructs a native Session or
  * recompiles the generated C++. The inputs and encodings match OpenFstBenchmarks.
  */
object CornerstoneCppExport:
  private case class Work(family: String, name: String, variant: String, expr: Space,
                          context: SpaceContextMap, routines: PartialFunction[RoutinePtr, Routine])
  private def json(s: String): String = "\"" + s.flatMap {
    case '"' => "\\\""
    case '\\' => "\\\\"
    case '\n' => "\\n"
    case '\r' => "\\r"
    case '\t' => "\\t"
    case c if c < ' ' || Character.isSurrogate(c) => f"\\u${c.toInt}%04x"
    case c => c.toString
  } + "\""
  private def slug(s: String): String = s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").stripSuffix("-")
  private def write(path: FilePath, content: String): Unit =
    Files.createDirectories(path.getParent)
    Files.writeString(path, content, UTF_8)

  def run(output: FilePath, includeLarge: Boolean = true): Unit =
    require(!Files.exists(output.resolve("manifest.json")), s"export already exists: $output")
    Files.createDirectories(output)
    val records = mutable.ArrayBuffer.empty[String]
    val index = mutable.ArrayBuffer.empty[String]
    for name <- Vector("zippy_openfst.hpp", "zippy_openfst.cpp", "zippy_lazy.hpp", "CMakeLists.txt") do
      Files.createDirectories(output.resolve("runtime"))
      Files.copy(Paths.get("src/main/cpp", name), output.resolve("runtime").resolve(name), StandardCopyOption.REPLACE_EXISTING)
    val main = TrieBenchmarks.cases().map(c => Work("programs", c.name, c.variant, c.expr, c.sc, c.rc))
    val large = if includeLarge then ZipperLargeBenchmarks.cases().map(c => Work("large", c.name, c.size, c.expr, c.sc, c.rc)) else Vector.empty
    val work = main ++ large
    work.zipWithIndex.foreach { (c, i) =>
      val key = f"${i + 1}%02d-${slug(c.name)}-${slug(c.variant)}"
      val directory = output.resolve(c.family).resolve(key)
      Files.createDirectories(directory)
      Console.err.println(s"[cpp-export] ${c.family}/${c.name}/${c.variant}")
      val defs = OpenFstBackend.reachable(c.expr, c.routines)
      write(directory.resolve("symbolic.zippy"), c.expr.show + "\n\n" + defs.toVector.sortBy(_._1.s).map(_._2.show).mkString("\n\n") + "\n")
      val alphabet = c.context.m.valuesIterator.flatMap(_.paths).flatMap(_.items).toSet
      val interner = PathItemInterner()
      alphabet.toVector.sortBy(_.show).foreach(interner.intern)
      val bits = RadixTranslations.symbolsToBits(c.expr, defs, interner)
      val bytes = RadixTranslations.internedSymbolsToBytes(c.expr, defs, interner)
      val encodingMap = Vector.tabulate(interner.size) { id =>
        val symbol = interner.decodeItem(id)
        val path = PathValue(List(symbol))
        val bitDigits = BitPaths.digits(bits.encoding.encode(path)).mkString("[", ",", "]")
        val byteDigits = BytePaths.digits(bytes.encoding.encode(path)).mkString("[", ",", "]")
        s"{\"symbol\":${json(symbol.show)},\"internId\":$id,\"bits\":$bitDigits,\"bytes\":$byteDigits}"
      }
      write(directory.resolve("encodings.json"), encodingMap.mkString("[\n", ",\n", "\n]\n"))
      val descriptions = mutable.ArrayBuffer.empty[String]
      def emit[V](dialect: String, encoding: String, compiled: OpenFstBackend.Compiled[V], spaces: Map[SpaceMention, Set[V]]): Unit =
        // Sort native labels, rather than JVM Set iteration, for stable fixtures.
        val encoded = spaces.toVector.sortBy(_._1.s).flatMap { (ref, values) =>
          compiled.spaceIds.get(ref).map(id => (id, values.toVector.map(compiled.labels.encode).sortBy(_.mkString(","))))
        }.sortBy(_._1)
        val symbols = compiled.labels.symbols.sortBy(_._1)
        def path(v: Vector[Int]): String = (v.size +: v).mkString(" ")
        val dictionary = symbols.flatMap((id, s) => Vector(id.toString, s.length.toString) ++ s.toVector.map(_.toInt.toString))
        val context = encoded.flatMap((id, paths) => Vector(id.toString, paths.size.toString) ++ paths.map(path))
        val request = (Vector("R", "0", symbols.size.toString) ++ dictionary ++ Vector("0", encoded.size.toString) ++ context).mkString(" ")
        write(directory.resolve(s"$dialect.input.txt"), request + "\nQ\n")
        write(directory.resolve(s"$dialect.graph.txt"), compiled.graphDescription + "\n")
        val source = compiled.source
        val sourceBytes = source.getBytes(UTF_8)
        write(directory.resolve(s"$dialect.cpp"), source)
        val hash = MessageDigest.getInstance("SHA-256").digest(sourceBytes).map(b => f"${b & 255}%02x").mkString
        val bindingJson = compiled.spaceIds.toVector.sortBy(_._2).map((ref, id) => s"{\"id\":$id,\"name\":${json(ref.s)}}").mkString("[", ",", "]")
        val pathJson = compiled.pathIds.toVector.sortBy(_._2).map((ref, id) => s"{\"id\":$id,\"name\":${json(ref.s)}}").mkString("[", ",", "]")
        val symbolJson = symbols.map((id, s) => s"{\"id\":$id,\"symbol\":${json(s)}}").mkString("[", ",", "]")
        write(directory.resolve(s"$dialect.bindings.json"), s"{\"encoding\":${json(encoding)},\"callbackCount\":${compiled.callbackCount},\"standalone\":${compiled.isStandalone},\"spaces\":$bindingJson,\"paths\":$pathJson,\"symbols\":$symbolJson}\n")
        val relative = s"${c.family}/$key/$dialect.cpp"
        records += s"{\"family\":${json(c.family)},\"name\":${json(c.name)},\"variant\":${json(c.variant)},\"dialect\":${json(dialect)},\"encoding\":${json(encoding)},\"source\":${json(relative)},\"bytes\":${sourceBytes.length},\"lines\":${source.count(_ == '\n')},\"sha256\":${json(hash)},\"callbackCount\":${compiled.callbackCount},\"standalone\":${compiled.isStandalone}}"
        descriptions += s"| [$dialect.cpp]($dialect.cpp) | $encoding | ${sourceBytes.length} | ${compiled.callbackCount} | ${compiled.isStandalone} |"
      emit("symbols", "symbol labels", OpenFstBackend.symbols(c.expr, defs), c.context.m.view.mapValues(_.paths).toMap)
      emit("bits", "Fibonacci-coded intern IDs", OpenFstBackend.bits(bits.program.entry, bits.program.routines), c.context.m.view.mapValues(v => bits.encode(v).paths).toMap)
      emit("bytes", "four-byte big-endian intern IDs", OpenFstBackend.bytes(bytes.program.entry, bytes.program.routines), c.context.m.view.mapValues(v => bytes.encode(v).paths).toMap)
      write(directory.resolve("README.md"), s"""# ${c.name} — ${c.variant}
         |
         |Family: `${c.family}`. The entry expression and reachable symbolic routines are in [symbolic.zippy](symbolic.zippy).
         |
         || Generated source | Encoding | Source bytes | Host callbacks | Standalone |
         ||---|---|---:|---:|---|
         |${descriptions.mkString("\n")}
         |
         |Each translation unit contains `main`, typed graph locals, inline control-flow loops, and all reachable routines.
         |[encodings.json](encodings.json) maps every interned symbol to its actual bit and byte codewords.
         |The corresponding `*.input.txt` contains the numeric protocol request for this exact fixture;
         |`*.bindings.json` identifies environment slots and symbolic labels. Digit labels are offset by one (OpenFst reserves zero).
         |The graph dumps beside each source show the optimized executor graph used for emission.
         |A callback count above zero requires the JVM callback bridge or explicit native function bindings;
         |its input file alone cannot supply those callbacks. See the bundle README for compilation.
         |""".stripMargin)
      index += s"| ${c.family} | ${c.name} | ${c.variant} | [source directory](${c.family}/$key/README.md) |"
    }
    write(output.resolve("manifest.json"), s"{\"schema\":1,\"workloads\":${work.size},\"programVariants\":${main.size},\"largeVariants\":${large.size},\"artifacts\":[${records.mkString(",\n")}]}\n")
    write(output.resolve("README.md"), s"""# Cornerstone compiled-code review bundle
       |
       |${main.size} cornerstone variants and ${large.size} supplementary large cases, each with complete C++ for the symbolic, Fibonacci-bit, and fixed-four-byte dialects (${records.size} translation units).
       |These are emitted directly from the same case factories and encodings used by the recorded OpenFst benchmarks.
       |No native compilation or new timing measurement is performed by this exporter.
       |The retained benchmark evidence was collected on the Symba h4d on 2026-10-29; code is regenerated from the current checkout.
       |Fresh residual routine identifiers and C++ function numbering can differ between exports.
       |
       |Start with a case's `symbolic.zippy`, then its `symbols.cpp`, `bits.cpp`, and `bytes.cpp`.
       |`manifest.json` records file hashes, sizes, and whether each program needs host callbacks.
       |The shared OpenFst implementation is copied once into [runtime/](runtime/).
       |Per-example benchmark tables are in [benchmarks/README.md](benchmarks/README.md).
       |
       |## Build one translation unit
       |
       |Install OpenFst and its dependencies, then from this bundle:
       |
       |```sh
       |cmake -S runtime -B build -DCMAKE_BUILD_TYPE=Release -DBUILD_TESTING=OFF \\
       |  -DZIPPY_PROGRAM="$$PWD/programs/<case>/symbols.cpp"
       |cmake --build build -j 8
       |ZIPPY_STACK_MIB=256 build/zippy_program < programs/<case>/symbols.input.txt
       |```
       |
       |For very large generated units use `-DCMAKE_CXX_FLAGS_RELEASE="-O0"`;
       |this review build setting is separate from the recorded benchmark settings.
       |Only programs marked standalone run directly with these input files. Others emit callback requests and require
       |`OpenFstBackend.Session` or supplied native grounded functions. Callbacks are explicitly counted per artifact.
       |
       || Family | Example | Variant | Files |
       ||---|---|---|---|
       |${index.mkString("\n")}
       |""".stripMargin)

@main def exportCornerstoneCpp(output: String = "output/cornerstone-review", includeLarge: Boolean = true): Unit =
  CornerstoneCppExport.run(Paths.get(output).toAbsolutePath, includeLarge)
