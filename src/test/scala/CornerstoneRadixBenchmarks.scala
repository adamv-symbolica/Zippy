package morkl

import java.nio.file.{Files, Paths}

/** One isolated process per (case,dialect), so expensive set interpreters have
  * independent heaps. Translation, input encoding and correctness decoding are
  * outside the timed interval. All retained timing samples check exact results.
  */
object CornerstoneRadixBenchmarks:
  def run(output: String, index: Int, dialect: String): Unit =
    require(dialect == "bits" || dialect == "bytes")
    val c = TrieBenchmarks.cases()(index)
    val defs = OpenFstBackend.reachable(c.expr, c.rc)
    val alphabet = c.sc.m.valuesIterator.flatMap(_.paths).flatMap(_.items).toSet
    val interner = PathItemInterner()
    alphabet.toVector.sortBy(_.show).foreach(interner.intern)
    val expected = evalTrie(c.expr)(using sc = c.tc, rc = c.rc).toSpaceValue
    def benchmark[V](eval: () => Set[V], decode: V => PathValue): (Double, Int) =
      def checked(): (Set[V], Double) =
        val start = System.nanoTime()
        val result = eval()
        val ms = (System.nanoTime() - start) / 1e6
        require(result.map(decode) == expected.paths, s"$dialect reference mismatch: ${c.name}/${c.variant}")
        result -> ms
      checked()
      checked()
      val first = checked()._2
      val samples = if first > 1000 then Vector(first) else first +: Vector.fill(4)(checked()._2)
      samples.sorted.apply(samples.size / 2) -> samples.size
    val (ms, samples) = if dialect == "bits" then
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
    Files.writeString(destination, "family,name,dialect,paths,reference_ms,samples,checked\n" +
      Vector("programs", quote(s"${c.name}/${c.variant}"), dialect, expected.paths.size, f"$ms%.6f", samples, "exact").mkString(",") + "\n")

@main def cornerstoneRadixReference(output: String, index: Int, dialect: String): Unit =
  CornerstoneRadixBenchmarks.run(output, index, dialect)
