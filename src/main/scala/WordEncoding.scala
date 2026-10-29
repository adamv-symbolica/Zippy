package morkl

import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.{CodingErrorAction, StandardCharsets}

/** Codeword boundaries are recognized by generated ordinary target programs.
  * This description belongs to the compiler, never to an evaluated space.
  */
enum WordCodeShape:
  case Fixed(width: Int)
  case Terminated(byte: Int)
  case Fibonacci

final case class WordEncoding[A, B](source: WordOps[A], target: WordOps[B],
    code: Int => Vector[Int], decodeDigit: Vector[Int] => Int,
    shape: WordCodeShape, maxCodeLength: Option[Int],
    orderedDigits: Vector[Int], preservesOrder: Boolean):
  def encode(p: A): B = target.fromDigits(source.digits(p).flatMap(code))
  def decode(p: B): A =
    val ds = target.digits(p)
    val out = Vector.newBuilder[Int]
    var offset = 0
    while offset < ds.size do
      val length = shape match
        case WordCodeShape.Fixed(n) => n
        case WordCodeShape.Terminated(b) =>
          val end = ds.indexOf(b, offset)
          require(end >= 0, "unterminated codeword")
          end - offset + 1
        case WordCodeShape.Fibonacci =>
          var end = offset + 1
          while end < ds.size && !(ds(end - 1) == 1 && ds(end) == 1) do end += 1
          require(end < ds.size, "unterminated Fibonacci codeword")
          end - offset + 1
      require(length > 0 && offset + length <= ds.size, "truncated codeword")
      val word = ds.slice(offset, offset + length)
      val digit = decodeDigit(word)
      require(code(digit) == word, "noncanonical codeword")
      out += digit
      offset += length
    source.fromDigits(out.result())
  def encodeSpace(s: WordSpaceValue[A]): WordSpaceValue[B] = WordSpaceValue(s.paths.map(encode))
  def decodeSpace(s: WordSpaceValue[B]): WordSpaceValue[A] = WordSpaceValue(s.paths.map(decode))
  def compile(entry: WordSpace[A], routines: Map[RoutinePtr, WordRoutine[A]] = Map.empty,
              unrollBounded: Boolean = false): WordProgram[B] =
    new WordCompiler(this, unrollBounded).compile(entry, routines)

object FibonacciCode:
  /** Standard Fibonacci/Zeckendorf code, low weight first, terminated by 11.
    * Intern id zero is represented by the positive integer one.
    */
  def encode(id: Int): Vector[Int] =
    require(id >= 0, "intern ids must be nonnegative")
    var remaining = id.toLong + 1
    val weights = scala.collection.mutable.ArrayBuffer[Long](1, 2)
    while weights.last <= remaining do weights += weights.takeRight(2).sum
    val bits = Array.fill(weights.size - 1)(0)
    for i <- bits.indices.reverse do
      if weights(i) <= remaining then
        bits(i) = 1
        remaining -= weights(i)
    bits.toVector :+ 1
  def decode(bits: Vector[Int]): Int =
    require(bits.size >= 2 && bits.takeRight(2) == Vector(1, 1), "invalid Fibonacci terminator")
    var a = 1L; var b = 2L; var value = 0L
    bits.dropRight(1).foreach { bit =>
      require(bit == 0 || bit == 1, "not a bit")
      if bit == 1 then value += a
      val next = a + b; a = b; b = next
      require(a <= (1L << 34), "Fibonacci codeword too long")
    }
    require(value >= 1 && value <= Int.MaxValue.toLong + 1, "Fibonacci intern id overflow")
    val id = (value - 1).toInt
    require(encode(id) == bits, "noncanonical Fibonacci codeword")
    id

object WordEncodings:
  val bitsToBytes: WordEncoding[BigInt, BytePathValue] = WordEncoding(
    BitWords, ByteWords, b => Vector(b), ds => { require(ds.size == 1 && ds.head <= 1); ds.head },
    WordCodeShape.Fixed(1), Some(1), Vector(0, 1), true)
  val bytesToBits: WordEncoding[BytePathValue, BigInt] = WordEncoding(
    ByteWords, BitWords, b => Vector.tabulate(8)(i => (b >>> (7 - i)) & 1),
    ds => { require(ds.size == 8); ds.foldLeft(0)((n, b) => (n << 1) | b) },
    WordCodeShape.Fixed(8), Some(8), (0 until 256).toVector, true)

  private def symbols(interner: PathItemInterner): (SymbolWords, Vector[Int]) =
    val ops = new SymbolWords(interner)
    val ordered = (0 until interner.size).toVector.sortBy(i => interner.decodeItem(i).show)
    ops -> ordered
  private def checkedCode(allowed: Set[Int], f: Int => Vector[Int]): Int => Vector[Int] = id =>
    require(allowed(id), s"symbol $id is outside the declared encoding alphabet")
    f(id)

  /** The interner is a compilation alphabet. Register open input symbols before
    * constructing an encoding, as well as symbols occurring in the program.
    */
  def internedSymbolsToBits(interner: PathItemInterner): WordEncoding[PathValue, BigInt] =
    val (ops, order) = symbols(interner)
    val max = order.map(FibonacciCode.encode(_).size).maxOption.getOrElse(2)
    WordEncoding(ops, BitWords, checkedCode(order.toSet, FibonacciCode.encode), FibonacciCode.decode,
      WordCodeShape.Fibonacci, Some(max), order, false)

  def internedSymbolsToBytes(interner: PathItemInterner): WordEncoding[PathValue, BytePathValue] =
    val (ops, order) = symbols(interner)
    WordEncoding(ops, ByteWords, checkedCode(order.toSet, id => Vector.tabulate(4)(i => (id >>> (24 - i * 8)) & 255)),
      ds => { require(ds.size == 4 && ds.head < 128); ds.foldLeft(0)((n, b) => (n << 8) | b) },
      WordCodeShape.Fixed(4), Some(4), order, false)

  def stringSymbolsToBytes(interner: PathItemInterner): WordEncoding[PathValue, BytePathValue] =
    val (ops, order) = symbols(interner)
    def utf8(id: Int): Vector[Int] =
      val text = interner.decodeItem(id).show
      require(!text.contains('\u0000'), "NUL is reserved as the string-symbol terminator")
      val encoder = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
      val buffer = encoder.encode(CharBuffer.wrap(text))
      val bytes = new Array[Byte](buffer.remaining()); buffer.get(bytes)
      bytes.iterator.map(_ & 255).toVector :+ 0
    val codes = order.map(id => id -> utf8(id)).toMap
    WordEncoding(ops, ByteWords, checkedCode(order.toSet, codes.apply),
      ds =>
        require(ds.nonEmpty && ds.last == 0)
        val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(ds.dropRight(1).map(_.toByte).toArray)).toString
        interner.intern(PathItem(text)),
      WordCodeShape.Terminated(0), codes.valuesIterator.map(_.size).maxOption,
      order, false)

  def bitsToSymbols(falseSymbol: PathItem = PathItem("false"), trueSymbol: PathItem = PathItem("true")):
      WordEncoding[BigInt, PathValue] =
    require(falseSymbol != trueSymbol, "bit injection needs two distinct symbols")
    val interner = new PathItemInterner
    interner.intern(falseSymbol); interner.intern(trueSymbol)
    WordEncoding(BitWords, new SymbolWords(interner), b => Vector(b), _.head,
      WordCodeShape.Fixed(1), Some(1), Vector(0, 1), false)

  def bytesToSymbols(symbol: Int => PathItem = n => PathItem(f"byte_$n%03d")):
      WordEncoding[BytePathValue, PathValue] =
    val interner = new PathItemInterner
    val items = (0 until 256).map(symbol)
    require(items.distinct.size == 256, "byte injection needs 256 distinct symbols")
    items.foreach(interner.intern)
    WordEncoding(ByteWords, new SymbolWords(interner), b => Vector(b), _.head,
      WordCodeShape.Fixed(1), Some(1), (0 until 256).toVector, false)
