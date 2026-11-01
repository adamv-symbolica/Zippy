package morkl

/** Only compiler state: no framing object is retained in a target AST. */
enum CodeFraming:
  case Fixed(width: Int)
  case Fibonacci(maxBits: Int)
  case ZeroTerminated
  case LengthPrefixed

trait PathEncoding[S, T]:
  def encode(path: S): T
  def decode(path: T): S
  def framing: CodeFraming
  def maxCodeLength: Option[Int]
  def preservesOrder: Boolean
  /** Source-ordered codewords, required only for non-monotone interning. */
  def orderedCodewords: Vector[T] = Vector.empty

object FibonacciCode:
  /** Standard Zeckendorf digits, least weight first, followed by a terminal 1. */
  def encode(value: BigInt): Vector[Int] =
    require(value > 0, "Fibonacci coding represents positive integers")
    val weights = scala.collection.mutable.ArrayBuffer[BigInt](1, 2)
    while weights.last <= value do weights += weights.takeRight(2).sum
    val bits = Array.fill(weights.length - 1)(0)
    var remaining = value
    for i <- bits.indices.reverse do
      if weights(i) <= remaining then
        bits(i) = 1
        remaining -= weights(i)
    bits.toVector :+ 1
  def decode(bits: Iterable[Int]): Vector[BigInt] =
    val out = Vector.newBuilder[BigInt]
    var a = BigInt(1)
    var b = BigInt(2)
    var value = BigInt(0)
    var previous = 0
    var pending = false
    bits.foreach { bit =>
      require(bit == 0 || bit == 1, "invalid Fibonacci digit")
      if previous == 1 && bit == 1 then
        out += value
        a = 1; b = 2; value = 0; previous = 0; pending = false
      else
        if bit == 1 then value += a
        val next = a + b
        a = b; b = next; previous = bit; pending = true
    }
    require(!pending, "truncated Fibonacci codeword")
    out.result()

object PathEncodings:
  /** One unsigned length byte followed by 0..255 CESU-8 payload bytes. Unlike
    * terminated strings, payload NUL/SOH bytes need no escaping. */
  val lengthPrefixed: PathEncoding[morkl.PathValue, BytePathValue] = new PathEncoding[morkl.PathValue, BytePathValue]:
    val framing=CodeFraming.LengthPrefixed
    val maxCodeLength=Some(256)
    val preservesOrder=false
    def encode(path: morkl.PathValue): BytePathValue = BytePaths.fromDigits(path.items.flatMap { item=>
      val payload=item.show.toVector.flatMap { ch=>
        val c=ch.toInt
        if c<128 then Vector(c)
        else if c<2048 then Vector(192|(c>>>6),128|(c&63))
        else Vector(224|(c>>>12),128|((c>>>6)&63),128|(c&63))
      }
      require(payload.size<=255,s"symbol payload exceeds 255 bytes: ${payload.size}")
      payload.size +: payload
    })
    def decode(path: BytePathValue): morkl.PathValue =
      val bytes=path.unsigned;val result=List.newBuilder[PathItem]
      var at=0
      while at<bytes.size do
        val end=at+1+bytes(at);at+=1
        require(end<=bytes.size,"truncated length-prefixed symbol")
        val text=new StringBuilder
        while at<end do
          val c=bytes(at)
          val width=if c<128 then 1 else if c>=192 && c<224 then 2 else if c>=224 && c<240 then 3 else 0
          require(width>0 && at+width<=end,"invalid length-prefixed string bytes")
          require((1 until width).forall(j=>bytes(at+j)>=128 && bytes(at+j)<192),"invalid continuation byte")
          val unit=if width==1 then c else if width==2 then ((c&31)<<6)|(bytes(at+1)&63)
            else ((c&15)<<12)|((bytes(at+1)&63)<<6)|(bytes(at+2)&63)
          require(unit >= (if width==1 then 0 else if width==2 then 128 else 2048),"noncanonical string encoding")
          text.append(unit.toChar);at+=width
        result+=PathItem(text.result())
      morkl.PathValue(result.result())

  /** Snapshot the interner before compiling. Runtime inputs use this same alphabet. */
  def fibonacci(interner: PathItemInterner): PathEncoding[morkl.PathValue, BigInt] =
    interned(interner, BitPaths, bits = true)
  def internedBytes(interner: PathItemInterner): PathEncoding[morkl.PathValue, BytePathValue] =
    interned(interner, BytePaths, bits = false)
  private def interned[T](interner: PathItemInterner, rep: DigitPaths[T], bits: Boolean): PathEncoding[morkl.PathValue, T] =
    val alphabet = Vector.tabulate(interner.size)(interner.decodeItem)
    val ids = alphabet.zipWithIndex.toMap
    def code(id: Int): Vector[Int] =
      if bits then FibonacciCode.encode(BigInt(id) + 1)
      else Vector.tabulate(4)(i => (id >>> (24 - 8 * i)) & 255)
    def decodeIds(ds: Vector[Int]): Vector[Int] =
      if bits then FibonacciCode.decode(ds).map { n =>
        require(n - 1 <= Int.MaxValue, "intern ID overflow")
        (n - 1).toInt
      }
      else
        require(ds.length % 4 == 0, "truncated intern ID")
        ds.grouped(4).map { group =>
          val id = group.foldLeft(0L)((n, d) => (n << 8) | d.toLong)
          require(id <= Int.MaxValue, "intern ID overflow")
          id.toInt
        }.toVector
    new PathEncoding[morkl.PathValue, T]:
      val maxCodeLength = Some(if bits then code((alphabet.size - 1).max(0)).length else 4)
      val framing = if bits then CodeFraming.Fibonacci(maxCodeLength.get) else CodeFraming.Fixed(4)
      val preservesOrder = false
      def encode(path: morkl.PathValue): T = rep.fromDigits(path.items.flatMap { item =>
        code(ids.getOrElse(item, throw IllegalArgumentException(s"symbol outside compiled interner: ${item.show}")))
      })
      def decode(path: T): morkl.PathValue = morkl.PathValue(decodeIds(rep.digits(path)).map { id =>
        require(id >= 0 && id < alphabet.size, s"unknown intern ID: $id")
        alphabet(id)
      }.toList)
      override val orderedCodewords: Vector[T] = alphabet.sortBy(_.show).map(i => rep.fromDigits(code(ids(i))))

  /** Zero-terminated, order-preserving UTF-16-code-unit UTF-8 (CESU-8).
    * NUL and SOH use 1,1 and 1,2, so every JVM String, including lone
    * surrogates and embedded NULs, has a reversible encoding with no interior 0.
    */
  val strings: PathEncoding[morkl.PathValue, BytePathValue] = new PathEncoding[morkl.PathValue, BytePathValue]:
    val framing = CodeFraming.ZeroTerminated
    val maxCodeLength = None
    val preservesOrder = true
    def encode(path: morkl.PathValue): BytePathValue = BytePaths.fromDigits(path.items.flatMap { item =>
      item.show.toVector.flatMap { ch =>
        val c = ch.toInt
        if c < 2 then Vector(1, c + 1)
        else if c < 128 then Vector(c)
        else if c < 2048 then Vector(192 | (c >>> 6), 128 | (c & 63))
        else Vector(224 | (c >>> 12), 128 | ((c >>> 6) & 63), 128 | (c & 63))
      } :+ 0
    })
    def decode(path: BytePathValue): morkl.PathValue =
      val ds = path.unsigned
      val result = List.newBuilder[PathItem]
      val text = new StringBuilder
      var i = 0
      while i < ds.length do
        val c = ds(i)
        if c == 0 then
          result += PathItem(text.result())
          text.clear()
          i += 1
        else if c == 1 then
          require(i + 1 < ds.length && (ds(i + 1) == 1 || ds(i + 1) == 2), "invalid string escape")
          text.append((ds(i + 1) - 1).toChar)
          i += 2
        else if c < 128 then
          text.append(c.toChar)
          i += 1
        else
          val width = if c >= 192 && c < 224 then 2 else if c >= 224 && c < 240 then 3 else 0
          require(width != 0 && i + width <= ds.length, "invalid string byte sequence")
          require((1 until width).forall(j => ds(i + j) >= 128 && ds(i + j) < 192), "invalid continuation byte")
          val unit = if width == 2 then ((c & 31) << 6) | (ds(i + 1) & 63)
            else ((c & 15) << 12) | ((ds(i + 1) & 63) << 6) | (ds(i + 2) & 63)
          require(unit >= (if width == 2 then 128 else 2048), "noncanonical string encoding")
          text.append(unit.toChar)
          i += width
      require(ds.isEmpty || ds.last == 0, "missing string terminator")
      morkl.PathValue(result.result())

  val bitsToBytes: PathEncoding[BigInt, BytePathValue] = new PathEncoding[BigInt, BytePathValue]:
    val framing = CodeFraming.Fixed(1)
    val maxCodeLength = Some(1)
    val preservesOrder = true
    def encode(path: BigInt): BytePathValue = BytePaths.fromDigits(BitPaths.digits(path))
    def decode(path: BytePathValue): BigInt = BitPaths.fromDigits(path.unsigned)

  val bytesToBits: PathEncoding[BytePathValue, BigInt] = new PathEncoding[BytePathValue, BigInt]:
    val framing = CodeFraming.Fixed(8)
    val maxCodeLength = Some(8)
    val preservesOrder = true
    def encode(path: BytePathValue): BigInt = BitPaths.fromDigits(path.unsigned.flatMap(d => Vector.tabulate(8)(i => (d >>> (7 - i)) & 1)))
    def decode(path: BigInt): BytePathValue =
      val ds = BitPaths.digits(path)
      require(ds.length % 8 == 0, "truncated byte")
      BytePaths.fromDigits(ds.grouped(8).map(_.foldLeft(0)((n, d) => (n << 1) | d)).toVector)

  private def injection[S](rep: DigitPaths[S], names: Vector[PathItem]): PathEncoding[S, morkl.PathValue] =
    new PathEncoding[S, morkl.PathValue]:
      val framing = CodeFraming.Fixed(1)
      val maxCodeLength = Some(1)
      val preservesOrder = true
      private val reverse = names.zipWithIndex.toMap
      def encode(path: S): morkl.PathValue = morkl.PathValue(rep.digits(path).map(names).toList)
      def decode(path: morkl.PathValue): S = rep.fromDigits(path.items.map(i =>
        reverse.getOrElse(i, throw IllegalArgumentException(s"outside injected alphabet: ${i.show}"))))
  val bitsToSymbols: PathEncoding[BigInt, morkl.PathValue] = injection(BitPaths, Vector(PathItem("false"), PathItem("true")))
  val bytesToSymbols: PathEncoding[BytePathValue, morkl.PathValue] = injection(BytePaths, Vector.tabulate(256)(i => PathItem(f"$i%03d")))
