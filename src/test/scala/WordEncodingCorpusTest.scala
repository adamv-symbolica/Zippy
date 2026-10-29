package morkl

import munit.FunSuite
import scala.concurrent.duration.*

class WordEncodingCorpusTest extends FunSuite:
  override val munitTimeout = 300.seconds
  test("1000 repository fuzzer programs, two compile-once inputs, seven routes and compositions") {
    given java.util.Random = new java.util.Random(0x5a69707079L)
    val alphabet = SpaceFuzzer.alphabet ++ Vector("w0", "w1", "w2", "w3", "g").map(PathItem(_))
    val interner = new PathItemInterner; alphabet.reverse.foreach(interner.intern)
    val fib = WordEncodings.sortedDictionaryBits(interner)
    val utf8 = WordEncodings.stringSymbolsToBytes(interner)
    val intern = WordEncodings.sortedDictionaryBytes(interner)
    val actualFib = WordEncodings.internedSymbolsToBits(interner, Some(alphabet))
    val actualBytes = WordEncodings.internedSymbolsToBytes(interner, Some(alphabet))
    val coverage = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
    val records = scala.collection.mutable.ArrayBuffer.empty[(Int, Long, Long, Long, Long)]
    val bitsToSymbols = WordEncodings.bitsToSymbols()
    val bytesToSymbols = WordEncodings.bytesToSymbols()
    val defs = Map(SpaceFuzzer.callRoutine.name -> SymbolWordLanguage.routine(SpaceFuzzer.callRoutine))
    for index <- 0 until 1000 do
      val first = SpaceFuzzer.argDist.sample
      val second = SpaceFuzzer.argDist.sample
      val source = SpaceFuzzer.genProg(first, maxDepth = 3, extended = true).sample
      val syntax = SymbolWordLanguage.space(ReferenceHints.tag(source))
      val bits = fib.compile(syntax, defs)
      val strings = utf8.compile(syntax, defs)
      val interned = intern.compile(syntax, defs)
      val injectedBytes = WordEncodings.bitsToBytes.compile(bits.entry, bits.routines)
      val expandedBits = WordEncodings.bytesToBits.compile(strings.entry, strings.routines)
      val injectedBitSymbols = bitsToSymbols.compile(bits.entry, bits.routines)
      val injectedByteSymbols = bytesToSymbols.compile(strings.entry, strings.routines)
      val literalFib = actualFib.compile(syntax, defs)
      val literalBytes = actualBytes.compile(syntax, defs)
      val literalChain = WordEncodings.bitsToBytes.compile(literalFib.entry, literalFib.routines)
      coverage("literal-fib/executed") += 1
      coverage("literal-bytes/executed") += 1
      val composed = WordEncodings.bytesToBits.compile(injectedBytes.entry, injectedBytes.routines)
      for input <- Vector(first, second) do
        val expected = WordSpaceValue(morkl.eval(source)(using PathContext.emptyMap,
          SpaceContextMap(Map(SpaceFuzzer.argM -> input)), SpaceFuzzer.routines).paths)
        val sourceStats = new WordEvaluationStats
        val baseline = WordEvaluator.eval(syntax, spaces = Map(SpaceFuzzer.argM -> WordSpaceValue(input.paths)),
          routines = defs, stats = Some(sourceStats))(using fib.source)
        assertEquals(baseline, expected)
        val sourceNodes = WordSyntax.nodes(syntax) + defs.valuesIterator.map(r => WordSyntax.nodes(r.body)).sum
        val sourceCells = input.paths.iterator.map(p => p.items.size + 1).sum + expected.paths.iterator.map(p => p.items.size + 1).sum
        def run[A, B](code: WordEncoding[A, B], program: WordProgram[B], input: WordSpaceValue[A], expansion: Int = 1): WordSpaceValue[A] =
          val stats = new WordEvaluationStats
          val result = program.eval(spaces = Map(SpaceFuzzer.argM -> code.encodeSpace(input)), stats = Some(stats), validateHints = true)(using code.target)
          val width = code.maxCodeLength.getOrElse(8).max(1) * expansion
          // Normalize work by the actual alphabet expansion, not by input cardinality.
          assert(program.nodeCount <= 50L * width * sourceNodes, s"AST expansion at program $index: ${program.nodeCount}/$sourceNodes")
          assert(stats.nodes <= 50L * width * (sourceStats.nodes + sourceCells).max(1), s"work expansion at program $index: ${stats.nodes}/${sourceStats.nodes}")
          records += ((index, sourceNodes, program.nodeCount, sourceStats.nodes, stats.nodes))
          code.decodeSpace(result)
        val raw = WordSpaceValue(input.paths)
        assertEquals(run(actualFib, literalFib, raw), expected, s"literal fib $index")
        assertEquals(run(actualBytes, literalBytes, raw), expected, s"literal bytes $index")
        assertEquals(actualFib.decodeSpace(run(WordEncodings.bitsToBytes, literalChain, actualFib.encodeSpace(raw), expansion = actualFib.maxCodeLength.get)), expected, s"literal chain $index")
        val trie = Zippy_bytes.evalZ(strings.entry, spaces = Map(SpaceFuzzer.argM -> utf8.encodeSpace(raw)),
          routines = strings.routines, validateHints = true).toSpaceValue
        assertEquals(utf8.decodeSpace(trie), expected, s"byte trie case $index")
        coverage("byte-trie/frames") += 1
        assertEquals(run(fib, bits, raw), expected, s"fib case $index")
        assertEquals(run(utf8, strings, raw), expected, s"utf8 case $index")
        assertEquals(run(intern, interned, raw), expected, s"intern case $index")
        assertEquals(fib.decodeSpace(run(WordEncodings.bitsToBytes, injectedBytes, fib.encodeSpace(raw), expansion = fib.maxCodeLength.get)), expected, s"bit injection $index")
        assertEquals(utf8.decodeSpace(run(WordEncodings.bytesToBits, expandedBits, utf8.encodeSpace(raw), expansion = 8)), expected, s"byte expansion $index")
        assertEquals(fib.decodeSpace(run(bitsToSymbols, injectedBitSymbols, fib.encodeSpace(raw), expansion = fib.maxCodeLength.get)), expected, s"bit symbols $index")
        assertEquals(utf8.decodeSpace(run(bytesToSymbols, injectedByteSymbols, utf8.encodeSpace(raw), expansion = 8)), expected, s"byte symbols $index")
        assertEquals(fib.decodeSpace(WordEncodings.bitsToBytes.decodeSpace(run(WordEncodings.bytesToBits, composed,
          WordEncodings.bitsToBytes.encodeSpace(fib.encodeSpace(raw)), expansion = fib.maxCodeLength.get))), expected, s"composition $index")
    assertEquals(coverage("literal-fib/executed"), 1000)
    assertEquals(coverage("literal-bytes/executed"), 1000)
    assertEquals(coverage("byte-trie/frames"), 2000)
    assert(records.size >= 16000)
    println(s"encoding coverage: ${coverage.toVector.sorted.mkString(", ")}; measurements=${records.size}")
    println(s"largest AST/work record: ${records.maxBy(_._3)} / ${records.maxBy(_._5)}")
  }
