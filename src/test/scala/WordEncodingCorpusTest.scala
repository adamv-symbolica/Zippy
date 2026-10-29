package morkl

import munit.FunSuite
import scala.concurrent.duration.*

class WordEncodingCorpusTest extends FunSuite:
  override val munitTimeout = 300.seconds
  test("1000 repository fuzzer programs, two compile-once inputs, seven routes and compositions") {
    given java.util.Random = new java.util.Random(0x5a69707079L)
    val alphabet = SpaceFuzzer.alphabet ++ Vector("w0", "w1", "w2", "w3", "g").map(PathItem(_))
    val interner = new PathItemInterner; alphabet.reverse.foreach(interner.intern)
    val fib = WordEncodings.internedSymbolsToBits(interner)
    val utf8 = WordEncodings.stringSymbolsToBytes(interner)
    val intern = WordEncodings.internedSymbolsToBytes(interner)
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
      val composed = WordEncodings.bytesToBits.compile(injectedBytes.entry, injectedBytes.routines)
      for input <- Vector(first, second) do
        val expected = WordSpaceValue(morkl.eval(source)(using PathContext.emptyMap,
          SpaceContextMap(Map(SpaceFuzzer.argM -> input)), SpaceFuzzer.routines).paths)
        def run[A, B](code: WordEncoding[A, B], program: WordProgram[B], input: WordSpaceValue[A]): WordSpaceValue[A] =
          code.decodeSpace(program.eval(spaces = Map(SpaceFuzzer.argM -> code.encodeSpace(input)), validateHints = true)(using code.target))
        val raw = WordSpaceValue(input.paths)
        assertEquals(run(fib, bits, raw), expected, s"fib case $index")
        assertEquals(run(utf8, strings, raw), expected, s"utf8 case $index")
        assertEquals(run(intern, interned, raw), expected, s"intern case $index")
        assertEquals(fib.decodeSpace(run(WordEncodings.bitsToBytes, injectedBytes, fib.encodeSpace(raw))), expected, s"bit injection $index")
        assertEquals(utf8.decodeSpace(run(WordEncodings.bytesToBits, expandedBits, utf8.encodeSpace(raw))), expected, s"byte expansion $index")
        assertEquals(fib.decodeSpace(run(bitsToSymbols, injectedBitSymbols, fib.encodeSpace(raw))), expected, s"bit symbols $index")
        assertEquals(utf8.decodeSpace(run(bytesToSymbols, injectedByteSymbols, utf8.encodeSpace(raw))), expected, s"byte symbols $index")
        assertEquals(fib.decodeSpace(WordEncodings.bitsToBytes.decodeSpace(run(WordEncodings.bytesToBits, composed,
          WordEncodings.bitsToBytes.encodeSpace(fib.encodeSpace(raw))))), expected, s"composition $index")
  }
