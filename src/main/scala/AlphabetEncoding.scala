package morkl

/** A decoder is compile-time data. Generated programs contain only the original
  * algebra, paths, calls, and functions; decoder states never accompany values. */
enum CodeBoundary:
  case Fixed(width: Int)
  case ZeroTerminated
  case Fibonacci

trait AlphabetEncoding[S, T]:
  def source: PathDomain[S]
  def target: FiniteDomain[T]
  def boundary: CodeBoundary
  def maxWidth: Option[Int]
  def encode(path: S): T
  def decode(path: T): S
  /** True only when unsigned target lexicographic order agrees with source. */
  def preservesOrder: Boolean
  /** Finite codebook, in SOURCE order, for order-sensitive lowering. */
  def orderedAtoms: Vector[S] = Vector.empty
  final def encodedUpperLength(symbolCount: Int): Option[Int] =
    maxWidth.flatMap(w => Option.when(BigInt(w) * symbolCount <= Int.MaxValue)(w * symbolCount))

object FibonacciCode:
  /** Zeckendorf digits, least weight first, followed by the second terminal 1. */
  def encode(positive: BigInt): Vector[Int] =
    require(positive > 0, "Fibonacci coding is defined for positive integers")
    val weights = collection.mutable.ArrayBuffer[BigInt](1, 2)
    while weights.last <= positive do weights += weights.takeRight(2).sum
    while weights.last > positive do weights.remove(weights.length - 1)
    var remaining = positive
    val digits = Array.fill(weights.length)(0)
    for i <- weights.indices.reverse do
      if weights(i) <= remaining then
        digits(i) = 1
        remaining -= weights(i)
    digits.toVector :+ 1
  def decode(digits: Vector[Int]): BigInt =
    require(digits.size >= 2 && digits.takeRight(2) == Vector(1, 1), "unterminated Fibonacci code")
    require(!digits.dropRight(1).sliding(2).exists(_ == Vector(1, 1)), "early Fibonacci terminator")
    var a = BigInt(1); var b = BigInt(2); var result = BigInt(0)
    digits.dropRight(1).foreach { d =>
      require(d == 0 || d == 1, "not a bit")
      if d == 1 then result += a
      val next = a + b; a = b; b = next
    }
    result

object AlphabetEncodings:
  /** The interner is snapshotted. All input symbols must have been interned.
    * This makes the finite maximum code length and order table honest bounds. */
  def internedBits(interner: PathItemInterner): AlphabetEncoding[PathValue, BigInt] =
    interned(interner, BitPaths, CodeBoundary.Fibonacci, id => FibonacciCode.encode(BigInt(id) + 1), ds => {
      val id = FibonacciCode.decode(ds) - 1
      require(id.isValidInt, "interned id overflow")
      id.toInt
    })

  def internedBytes(interner: PathItemInterner): AlphabetEncoding[PathValue, BytePath] =
    interned(interner, BytePaths, CodeBoundary.Fixed(4), id => Vector(24, 16, 8, 0).map(s => (id >>> s) & 255), ds => {
      require(ds.size == 4, "interned symbols occupy four bytes")
      val id = ds.foldLeft(0L)((a, b) => (a << 8) | b.toLong)
      require(id <= Int.MaxValue, "interned id overflow")
      id.toInt
    })

  private def interned[T](interner: PathItemInterner, d: FiniteDomain[T], b: CodeBoundary, enc: Int => Vector[Int], dec: Vector[Int] => Int): AlphabetEncoding[PathValue, T] =
    val items = Vector.tabulate(interner.size)(interner.decodeItem)
    val ids = items.zipWithIndex.toMap
    new AlphabetEncoding[PathValue, T]:
      val source = SymbolPaths
      val target = d
      val boundary = b
      val maxWidth = Some(items.indices.map(enc(_).size).maxOption.getOrElse(b match
        case CodeBoundary.Fixed(n) => n
        case _ => 2))
      val preservesOrder = false
      override val orderedAtoms = items.sortBy(_.show).map(i => PathValue(List(i)))
      def encode(path: PathValue): T = target.fromDigits(path.items.flatMap(i => enc(ids.getOrElse(i, throw IllegalArgumentException(s"symbol not in interner snapshot: ${i.show}")))))
      def decode(path: T): PathValue =
        val out = List.newBuilder[PathItem]
        var pending = Vector.empty[Int]
        target.digits(path).foreach { digit =>
          pending :+= digit
          val done = b match
            case CodeBoundary.Fixed(n) => pending.size == n
            case CodeBoundary.Fibonacci => pending.takeRight(2) == Vector(1, 1)
            case _ => false
          if done then
            val id = dec(pending)
            require(id >= 0 && id < items.size, s"unknown interned id $id")
            out += items(id)
            pending = Vector.empty
        }
        require(pending.isEmpty, "incomplete encoded symbol")
        PathValue(out.result())

  /** Zero-terminated, order-preserving strings. Each UTF-16 code unit uses
    * CESU-8, with NUL escaped to 01 01 and U+0001 to 01 02. Thus 00 is only
    * a terminator, empty strings and every JVM String round-trip, and ordering
    * agrees exactly with String.compareTo (including unpaired surrogates). */
  val stringBytes: AlphabetEncoding[PathValue, BytePath] = new AlphabetEncoding[PathValue, BytePath]:
    val source = SymbolPaths
    val target = BytePaths
    val boundary = CodeBoundary.ZeroTerminated
    val maxWidth = None
    val preservesOrder = true
    def encode(path: PathValue): BytePath =
      val bytes = Vector.newBuilder[Int]
      path.items.foreach { item =>
        item.show.foreach { c =>
          val u = c.toInt
          if u == 0 then bytes ++= Vector(1, 1)
          else if u == 1 then bytes ++= Vector(1, 2)
          else if u < 128 then bytes += u
          else if u < 2048 then bytes ++= Vector(0xc0 | (u >>> 6), 0x80 | (u & 63))
          else bytes ++= Vector(0xe0 | (u >>> 12), 0x80 | ((u >>> 6) & 63), 0x80 | (u & 63))
        }
        bytes += 0
      }
      target.fromDigits(bytes.result())
    def decode(path: BytePath): PathValue =
      val bytes = path.unsigned
      val out = List.newBuilder[PathItem]
      val text = new java.lang.StringBuilder
      var i = 0
      def continuation(index: Int): Int =
        require(index < bytes.size && (bytes(index) & 0xc0) == 0x80, "invalid CESU-8 continuation")
        bytes(index) & 63
      while i < bytes.size do
        val b = bytes(i)
        if b == 0 then
          out += PathItem(text.toString); text.setLength(0); i += 1
        else if b == 1 then
          require(i + 1 < bytes.size && (bytes(i + 1) == 1 || bytes(i + 1) == 2), "invalid string escape")
          text.append((bytes(i + 1) - 1).toChar); i += 2
        else if b < 128 then { text.append(b.toChar); i += 1 }
        else if b >= 0xc2 && b <= 0xdf then
          text.append((((b & 31) << 6) | continuation(i + 1)).toChar); i += 2
        else if b >= 0xe0 && b <= 0xef then
          val u = ((b & 15) << 12) | (continuation(i + 1) << 6) | continuation(i + 2)
          require(u >= 2048, "overlong CESU-8")
          text.append(u.toChar); i += 3
        else throw IllegalArgumentException("invalid string byte")
      require(bytes.isEmpty || bytes.last == 0, "missing string terminator")
      PathValue(out.result())

  val bitsToBytes: AlphabetEncoding[BigInt, BytePath] = new AlphabetEncoding[BigInt, BytePath]:
    val source = BitPaths; val target = BytePaths
    val boundary = CodeBoundary.Fixed(1); val maxWidth = Some(1); val preservesOrder = true
    def encode(p: BigInt): BytePath = target.fromDigits(BitPaths.digits(p))
    def decode(p: BytePath): BigInt = BitPaths.fromDigits(target.digits(p))

  val bytesToBits: AlphabetEncoding[BytePath, BigInt] = new AlphabetEncoding[BytePath, BigInt]:
    val source = BytePaths; val target = BitPaths
    val boundary = CodeBoundary.Fixed(8); val maxWidth = Some(8); val preservesOrder = true
    def encode(p: BytePath): BigInt = target.fromDigits(BytePaths.digits(p).flatMap(b => (7 to 0 by -1).map(i => (b >>> i) & 1)))
    def decode(p: BigInt): BytePath =
      val ds = target.digits(p)
      require(ds.size % 8 == 0, "incomplete byte")
      BytePaths.fromDigits(ds.grouped(8).map(_.foldLeft(0)((a, b) => (a << 1) | b)).toVector)

  def symbols[P](sourceDomain: FiniteDomain[P], names: Vector[PathItem]): AlphabetEncoding[P, PathValue] =
    require(names.size == sourceDomain.arity && names.distinct.size == names.size, "one distinct symbol per digit is required")
    // Preserve the order used by Fold and Range by requiring increasing names.
    require(names.map(_.show) == names.map(_.show).sorted, "injection symbols must be in digit order")
    val ids = names.zipWithIndex.toMap
    new AlphabetEncoding[P, PathValue]:
      val source = sourceDomain
      val target = new FiniteDomain[PathValue]:
        val arity = names.size
        val empty = SymbolPaths.empty
        def length(p: PathValue): Int = { require(p.items.forall(ids.contains), "foreign injection symbol"); p.items.size }
        def concat(a: PathValue, b: PathValue): PathValue = SymbolPaths.concat(a, b)
        def take(p: PathValue, n: Int): PathValue = SymbolPaths.take(p, n)
        def drop(p: PathValue, n: Int): PathValue = SymbolPaths.drop(p, n)
        def compare(a: PathValue, b: PathValue): Int = SymbolPaths.compare(a, b)
        def single(d: Int): PathValue = PathValue(List(names(d)))
        def digit(p: PathValue): Int = { require(p.items.size == 1); ids(p.items.head) }
      val boundary = CodeBoundary.Fixed(1); val maxWidth = Some(1); val preservesOrder = true
      def encode(p: P): PathValue = target.fromDigits(sourceDomain.digits(p))
      def decode(p: PathValue): P = sourceDomain.fromDigits(target.digits(p))

  val bitsToSymbols: AlphabetEncoding[BigInt, PathValue] = symbols(BitPaths, Vector(PathItem("false"), PathItem("true")))
  val bytesToSymbols: AlphabetEncoding[BytePath, PathValue] = symbols(BytePaths, Vector.tabulate(256)(b => PathItem(f"byte$b%03d")))
