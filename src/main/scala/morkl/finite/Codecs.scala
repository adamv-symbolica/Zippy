package morkl.finite

import morkl.{PathItem, PathItemInterner}

/** A prefix-free code and its codeword recognizer. The recognizer is a compiler
  * input, never a runtime tag, callback, or additional target operation. */
enum ScanStep:
  case Continue(state: Int)
  case Accept

trait Code[S, T]:
  def targetAlphabet: Vector[T]
  def encodeSymbol(symbol: S): Vector[T]
  def decodeSymbol(code: Vector[T]): S
  def transition(state: Int, unit: T): Option[ScanStep]
  def maxUnits: Option[Int]
  def fixedUnits: Option[Int] = None
  def orderPreserving: Boolean
  /** Required only for non-order-preserving codes, to compile an order adapter. */
  def orderedSymbols: Option[Vector[S]] = None
  final def encode(path: Vector[S]): Vector[T] = path.flatMap(encodeSymbol)
  final def decode(path: Vector[T]): Vector[S] =
    val out = Vector.newBuilder[S]
    var begin = 0
    var state = 0
    var i = 0
    while i < path.length do
      transition(state, path(i)) match
        case Some(ScanStep.Accept) =>
          out += decodeSymbol(path.slice(begin, i + 1))
          begin = i + 1
          state = 0
        case Some(ScanStep.Continue(next)) => state = next
        case None => throw IllegalArgumentException(s"invalid code unit at offset $i")
      i += 1
    require(begin == path.length, "truncated codeword")
    out.result()

object Codes:
  def fibonacci(id: Int): Vector[Boolean] =
    require(id >= 0, "negative interned symbol")
    var n = BigInt(id) + 1
    val fib = scala.collection.mutable.ArrayBuffer(BigInt(1), BigInt(2))
    while fib.last <= n do fib += fib(fib.size - 1) + fib(fib.size - 2)
    fib.remove(fib.size - 1)
    val bits = Array.fill(fib.size)(false)
    for i <- fib.indices.reverse do
      if fib(i) <= n then
        bits(i) = true
        n -= fib(i)
    bits.toVector :+ true

  def unfibonacci(bits: Vector[Boolean]): Int =
    require(bits.size >= 2 && bits.takeRight(2) == Vector(true, true), "Fibonacci code must end in 11")
    require(!bits.dropRight(1).sliding(2).exists(_ == Vector(true, true)), "non-canonical Fibonacci code")
    var a = BigInt(1)
    var b = BigInt(2)
    var n = BigInt(0)
    bits.dropRight(1).foreach { bit =>
      if bit then n += a
      val next = a + b
      a = b
      b = next
    }
    require(n > 0 && n - 1 <= Int.MaxValue, "Fibonacci symbol exceeds Int range")
    (n - 1).toInt

  /** Freeze the interner after registering program constants and input symbols.
    * The snapshot supplies compile-time ordering, never runtime metadata. */
  def internedBits(interner: PathItemInterner): Code[PathItem, Boolean] =
    val symbols = Vector.tabulate(interner.size)(interner.decodeItem)
    val ids = symbols.zipWithIndex.toMap
    new Code[PathItem, Boolean]:
      val targetAlphabet = Vector(false, true)
      val maxUnits = Some(if symbols.isEmpty then 2 else fibonacci(symbols.size - 1).size)
      val orderPreserving = false
      override val orderedSymbols = Some(symbols.sortBy(_.show))
      def encodeSymbol(s: PathItem) = fibonacci(ids.getOrElse(s, throw IllegalArgumentException(s"symbol ${s.show} was not registered before translation")))
      def decodeSymbol(p: Vector[Boolean]) = symbols(unfibonacci(p))
      def transition(state: Int, unit: Boolean) =
        Some(if state == 1 && unit then ScanStep.Accept else ScanStep.Continue(if unit then 1 else 0))

  def internedBytes(interner: PathItemInterner): Code[PathItem, Int] =
    val symbols = Vector.tabulate(interner.size)(interner.decodeItem)
    val ids = symbols.zipWithIndex.toMap
    new Code[PathItem, Int]:
      val targetAlphabet = (0 until 256).toVector
      val maxUnits = Some(4)
      override val fixedUnits = Some(4)
      val orderPreserving = false
      override val orderedSymbols = Some(symbols.sortBy(_.show))
      def encodeSymbol(s: PathItem) =
        val id = ids.getOrElse(s, throw IllegalArgumentException(s"symbol ${s.show} was not registered before translation"))
        Vector(24, 16, 8, 0).map(shift => (id >>> shift) & 255)
      def decodeSymbol(p: Vector[Int]) =
        require(p.size == 4 && p.forall(b => b >= 0 && b < 256), "invalid four-byte symbol")
        val id = p.foldLeft(0L)((n, b) => (n << 8) | b.toLong)
        require(id < symbols.size, "unknown interned symbol")
        symbols(id.toInt)
      def transition(state: Int, unit: Int) =
        if unit < 0 || unit > 255 then None
        else Some(if state == 3 then ScanStep.Accept else ScanStep.Continue(state + 1))

  /** Zero-terminated UTF-16-order-preserving UTF-8 (CESU-8 for surrogate
    * pairs). NUL and SOH use 01 01 and 01 02, keeping payload zero-free and
    * preserving Java String order, including empty strings and unpaired surrogates. */
  val stringBytes: Code[PathItem, Int] = new Code[PathItem, Int]:
    val targetAlphabet = (0 until 256).toVector
    val maxUnits = None
    val orderPreserving = true
    def encodeSymbol(s: PathItem): Vector[Int] =
      s.show.toVector.flatMap { c =>
        val n = c.toInt
        if n <= 1 then Vector(1, n + 1)
        else if n < 128 then Vector(n)
        else if n < 2048 then Vector(0xc0 | (n >> 6), 0x80 | (n & 63))
        else Vector(0xe0 | (n >> 12), 0x80 | ((n >> 6) & 63), 0x80 | (n & 63))
      } :+ 0
    def decodeSymbol(p: Vector[Int]): PathItem =
      require(p.nonEmpty && p.last == 0 && !p.dropRight(1).contains(0), "invalid terminated string")
      val chars = new StringBuilder
      var i = 0
      def continuation(j: Int): Int =
        require(j < p.size - 1 && (p(j) & 0xc0) == 0x80, "invalid UTF-8 continuation")
        p(j) & 63
      while i < p.size - 1 do
        val b = p(i)
        require(b >= 1 && b <= 255, "invalid byte")
        if b == 1 then
          require(i + 1 < p.size - 1 && (p(i + 1) == 1 || p(i + 1) == 2), "invalid string escape")
          chars += (p(i + 1) - 1).toChar
          i += 2
        else if b < 128 then
          chars += b.toChar
          i += 1
        else if b >= 0xc2 && b < 0xe0 then
          chars += (((b & 31) << 6) | continuation(i + 1)).toChar
          i += 2
        else
          require(b >= 0xe0 && b <= 0xef, "invalid UTF-8 leader")
          val c = ((b & 15) << 12) | (continuation(i + 1) << 6) | continuation(i + 2)
          require(c >= 2048, "overlong UTF-8")
          chars += c.toChar
          i += 3
      PathItem(chars.toString)
    def transition(state: Int, unit: Int) =
      if unit < 0 || unit > 255 then None
      else Some(if unit == 0 then ScanStep.Accept else ScanStep.Continue(0))

  val bitsToBytes: Code[Boolean, Int] = new Code[Boolean, Int]:
    val targetAlphabet = (0 until 256).toVector
    val maxUnits = Some(1)
    override val fixedUnits = Some(1)
    val orderPreserving = true
    def encodeSymbol(b: Boolean) = Vector(if b then 1 else 0)
    def decodeSymbol(p: Vector[Int]) =
      require(p == Vector(0) || p == Vector(1), "not an injected bit")
      p.head == 1
    def transition(state: Int, unit: Int) = Option.when(unit == 0 || unit == 1)(ScanStep.Accept)

  val bytesToBits: Code[Int, Boolean] = new Code[Int, Boolean]:
    val targetAlphabet = Vector(false, true)
    val maxUnits = Some(8)
    override val fixedUnits = Some(8)
    val orderPreserving = true
    def encodeSymbol(b: Int) =
      require(b >= 0 && b < 256, "byte out of range")
      Vector.tabulate(8)(i => (b & (1 << (7 - i))) != 0)
    def decodeSymbol(p: Vector[Boolean]) =
      require(p.size == 8, "byte requires eight bits")
      p.foldLeft(0)((b, bit) => (b << 1) | (if bit then 1 else 0))
    def transition(state: Int, unit: Boolean) = Some(if state == 7 then ScanStep.Accept else ScanStep.Continue(state + 1))

  def injection[S, T](pairs: Vector[(S, T)], ordering: Ordering[T]): Code[S, T] = new Code[S, T]:
    private val forward = pairs.toMap
    private val backward = pairs.map(_.swap).toMap
    require(forward.size == pairs.size && backward.size == pairs.size, "injection must be one-to-one")
    val targetAlphabet = pairs.map(_._2).sorted(ordering)
    val maxUnits = Some(1)
    override val fixedUnits = Some(1)
    val orderPreserving = true
    def encodeSymbol(s: S) = Vector(forward(s))
    def decodeSymbol(p: Vector[T]) =
      require(p.size == 1, "injected symbol must have length one")
      backward(p.head)
    def transition(state: Int, unit: T) = Option.when(backward.contains(unit))(ScanStep.Accept)

  val bitsToSymbols: Code[Boolean, PathItem] = injection(
    Vector(false -> PathItem("__zippy_bit_0"), true -> PathItem("__zippy_bit_1")), Ordering.by(_.show))
  val bytesToSymbols: Code[Int, PathItem] = injection(
    Vector.tabulate(256)(b => b -> PathItem(f"__zippy_byte_$b%03d")), Ordering.by(_.show))
