package morkl

import java.util.Arrays

/** A word is its payload only. In particular, no symbol-boundary table is kept
  * by either reference evaluator. BigInt words use a leading one as an in-band
  * length delimiter: epsilon = 1, 0 = 2, 1 = 3, 00 = 4.
  */
trait WordOps[P]:
  def alphabetSize: Int
  def digits(p: P): Vector[Int]
  def fromDigits(p: Iterable[Int]): P
  def empty: P = fromDigits(Vector.empty)
  def length(p: P): Int = digits(p).size
  def concat(p: P, q: P): P = fromDigits(digits(p) ++ digits(q))
  def startsWith(p: P, q: P): Boolean = digits(p).startsWith(digits(q))
  def drop(p: P, n: Int): P = fromDigits(digits(p).drop(n))
  def take(p: P, n: Int): P = fromDigits(digits(p).take(n))
  def split(p: P): Option[(P, P)] =
    Option.when(length(p) > 0)(take(p, 1) -> drop(p, 1))
  def compare(p: P, q: P): Int =
    val a = digits(p); val b = digits(q)
    a.zip(b).iterator.map((x, y) => x.compare(y)).find(_ != 0).getOrElse(a.size.compare(b.size))
  given ordering: Ordering[P] = new Ordering[P]:
    def compare(p: P, q: P): Int = WordOps.this.compare(p, q)

object BitWords extends WordOps[BigInt]:
  val alphabetSize = 2
  override val empty: BigInt = BigInt(1)
  def fromDigits(p: Iterable[Int]): BigInt = p.foldLeft(empty) { (n, b) =>
    require(b == 0 || b == 1, s"not a bit: $b")
    (n << 1) | BigInt(b)
  }
  def digits(p: BigInt): Vector[Int] =
    require(p > 0, "a bit word must have a leading length delimiter")
    Vector.tabulate(p.bitLength - 1)(i => if p.testBit(p.bitLength - 2 - i) then 1 else 0)
  override def length(p: BigInt): Int =
    require(p > 0, "a bit word must have a leading length delimiter")
    p.bitLength - 1
  override def concat(p: BigInt, q: BigInt): BigInt =
    val n = length(q)
    if length(p) + n <= 62 then BigInt((p.toLong << n) | (q.toLong & ((1L << n) - 1)))
    else (p << n) | q.clearBit(n)
  override def compare(p: BigInt, q: BigInt): Int =
    val a = length(p); val b = length(q); val common = a.min(b)
    val c = if a <= 62 && b <= 62 then java.lang.Long.compare(p.toLong >>> (a - common), q.toLong >>> (b - common))
      else if a == b then p.compare(q)
      else if a > b then (p >> (a - b)).compare(q)
      else p.compare(q >> (b - a))
    if c != 0 then c else a.compare(b)
  override def startsWith(p: BigInt, q: BigInt): Boolean =
    val d = length(p) - length(q)
    d >= 0 && (if p.bitLength <= 63 then (p.toLong >>> d) == q.toLong else (p >> d) == q)
  override def take(p: BigInt, n: Int): BigInt =
    val shift = length(p) - n.max(0).min(length(p))
    if p.bitLength <= 63 then BigInt(p.toLong >>> shift) else p >> shift
  override def drop(p: BigInt, n: Int): BigInt =
    val remaining = length(p) - n.max(0).min(length(p))
    if p.bitLength <= 63 then BigInt((p.toLong & ((1L << remaining) - 1)) | (1L << remaining))
    else (p & ((BigInt(1) << remaining) - 1)).setBit(remaining)
  override def split(p: BigInt): Option[(BigInt, BigInt)] =
    val n = length(p) - 1
    if n < 0 then None
    else if p.bitLength <= 63 then
      val bits = p.toLong
      Some(BigInt(if (bits & (1L << n)) == 0 then 2 else 3) -> BigInt((bits & ((1L << n) - 1)) | (1L << n)))
    else Some(take(p, 1) -> drop(p, 1))

/** Array-backed immutable value. The backing array is never exposed, including
  * through case-class copy/product APIs or ArraySeq.unsafeArray.
  */
final class BytePathValue private (private val data: Array[Byte]):
  def toArray: Array[Byte] = data.clone()
  def unsigned: Vector[Int] = data.iterator.map(_ & 255).toVector
  def length: Int = data.length
  override def equals(other: Any): Boolean = other match
    case p: BytePathValue => Arrays.equals(data, p.data)
    case _ => false
  override def hashCode(): Int = Arrays.hashCode(data)
  override def toString: String = unsigned.mkString("BytePathValue(", ",", ")")
  private[morkl] def compare(that: BytePathValue): Int = Arrays.compareUnsigned(data, that.data)
  private[morkl] def startsWith(that: BytePathValue): Boolean =
    length >= that.length && Arrays.equals(data, 0, that.length, that.data, 0, that.length)
  private[morkl] def firstUnsigned: Int = data(0) & 255
  private[morkl] def concat(that: BytePathValue): BytePathValue =
    if length == 0 then return that
    if that.length == 0 then return this
    val result = Arrays.copyOf(data, length + that.length)
    System.arraycopy(that.data, 0, result, length, that.length)
    new BytePathValue(result)
  private[morkl] def slice(lo: Int, hi: Int): BytePathValue =
    if lo == 0 && hi == length then this
    else if lo == hi then BytePathValue.empty
    else new BytePathValue(Arrays.copyOfRange(data, lo, hi))

object BytePathValue:
  def apply(bytes: Array[Byte]): BytePathValue = new BytePathValue(bytes.clone())
  def fromUnsigned(bytes: Iterable[Int]): BytePathValue =
    val values = bytes.iterator.map { b =>
      require(b >= 0 && b < 256, s"not a byte: $b")
      b.toByte
    }.toArray
    new BytePathValue(values)
  val empty: BytePathValue = new BytePathValue(Array.emptyByteArray)

object ByteWords extends WordOps[BytePathValue]:
  val alphabetSize = 256
  def digits(p: BytePathValue): Vector[Int] = p.unsigned
  def fromDigits(p: Iterable[Int]): BytePathValue = BytePathValue.fromUnsigned(p)
  override val empty: BytePathValue = BytePathValue.empty
  override def length(p: BytePathValue): Int = p.length
  override def compare(p: BytePathValue, q: BytePathValue): Int = p.compare(q)
  override def startsWith(p: BytePathValue, q: BytePathValue): Boolean = p.startsWith(q)
  override def concat(p: BytePathValue, q: BytePathValue): BytePathValue = p.concat(q)
  override def take(p: BytePathValue, n: Int): BytePathValue = p.slice(0, n.max(0).min(p.length))
  override def drop(p: BytePathValue, n: Int): BytePathValue = p.slice(n.max(0).min(p.length), p.length)
  private lazy val atoms = Vector.tabulate(256)(n => BytePathValue(Array(n.toByte)))
  override def split(p: BytePathValue): Option[(BytePathValue, BytePathValue)] =
    if p.length == 0 then None else Some(atoms(p.firstUnsigned) -> drop(p, 1))

case class WordSpaceValue[P](paths: Set[P])

enum WordPath[P]:
  case Deref(pr: PathRef)
  case Constant(pi: P)
  case Concat(l: WordPath[P], r: WordPath[P])
  case GroundedPP(p: WordPath[P], f: P => P)
  case GroundedSP(p: WordSpace[P], f: WordSpaceValue[P] => P)

enum WordSpace[P]:
  case Empty[P]() extends WordSpace[P]
  case Call(r: RoutinePtr, refs: Vector[WordPath[P]], mentions: Vector[WordSpace[P]])
  case Mention(variable: SpaceMention)
  case Singleton(p: WordPath[P])
  case Literal(p: WordSpaceValue[P])
  case Union(x: WordSpace[P], y: WordSpace[P])
  case Intersection(x: WordSpace[P], y: WordSpace[P])
  case Subtraction(x: WordSpace[P], y: WordSpace[P])
  case Restriction(x: WordSpace[P], y: WordSpace[P])
  case Raffination(x: WordSpace[P], y: WordSpace[P])
  case Composition(x: WordSpace[P], y: WordSpace[P])
  case Iteration(src: WordSpace[P], symbol: PathRef, rest: SpaceMention, templates: WordSpace[P])
  case Fold(src: WordSpace[P], initial: WordPath[P], acc: PathRef, symbol: PathRef, rest: SpaceMention, templates: WordSpace[P], update: WordPath[P])
  case Fixpoint(initial: WordSpace[P], variable: SpaceMention, step: WordSpace[P])
  case Wrap(src: WordSpace[P], p: WordPath[P])
  case Unwrap(src: WordSpace[P], p: WordPath[P])
  case TailsUnion(src: WordSpace[P])
  case TailsIntersection(src: WordSpace[P])
  case PrefixClosure(src: WordSpace[P])
  case SuffixClosure(src: WordSpace[P])
  case TailsClosure(src: WordSpace[P])
  case GroundedPS(p: WordPath[P], f: P => WordSpaceValue[P])
  case GroundedSS(p: WordSpace[P], f: WordSpaceValue[P] => WordSpaceValue[P])
  case Range(x: WordSpace[P], start: Int, end: Int)

object WordSpace:
  /** Head is derived, just as Syntax.head is in Zippy_symbols. */
  def Head[P](src: WordSpace[P]): WordSpace[P] =
    val h = PathRef("__word_head").known(1)
    Iteration(src, h, SpaceMention("_"), Singleton(WordPath.Deref(h)))

case class WordRoutine[P](name: RoutinePtr, refs: Vector[PathRef], mentions: Vector[SpaceMention], body: WordSpace[P])
case class WordProgram[P](entry: WordSpace[P], routines: Map[RoutinePtr, WordRoutine[P]]):
  lazy val nodeCount: Long = WordSyntax.nodes(entry) + routines.valuesIterator.map(r => WordSyntax.nodes(r.body)).sum
  def eval(paths: Map[PathRef, P] = Map.empty[PathRef, P], spaces: Map[SpaceMention, WordSpaceValue[P]] = Map.empty[SpaceMention, WordSpaceValue[P]],
           stats: Option[WordEvaluationStats] = None, validateHints: Boolean = false)(using ops: WordOps[P]): WordSpaceValue[P] =
    WordEvaluator.eval(entry, paths, spaces, routines, stats, validateHints)

/** Optional per-run counters; these never participate in language values. */
final class WordEvaluationStats:
  var nodes: Long = 0
  var calls: Long = 0

object WordEvaluator:
  def eval[P](expression: WordSpace[P], paths: Map[PathRef, P] = Map.empty[PathRef, P],
              spaces: Map[SpaceMention, WordSpaceValue[P]] = Map.empty[SpaceMention, WordSpaceValue[P]],
              routines: Map[RoutinePtr, WordRoutine[P]] = Map.empty[RoutinePtr, WordRoutine[P]],
              stats: Option[WordEvaluationStats] = None, validateHints: Boolean = false,
              backend: Option[WordSetBackend[P]] = None)(using ops: WordOps[P]): WordSpaceValue[P] =
    import WordSpace.*
    import ops.ordering
    val store = backend.getOrElse(new WordSetBackend(ops))
    store.clearCaches()
    val literalCache = new java.util.IdentityHashMap[Set[P], Set[P]]()
    def stored(values: Set[P]): Set[P] =
      val cached = literalCache.get(values)
      if cached != null then cached
      else
        val result = store.literal(values)
        literalCache.put(values, result)
        result
    type PC = Map[PathRef, P]
    type SC = Map[SpaceMention, WordSpaceValue[P]]
    def bindPath(pc: PC, r: PathRef, p: P): PC = if r.s == "_" then pc else pc.updated(r, p)
    def bindSpace(sc: SC, r: SpaceMention, s: Set[P]): SC = if r.s == "_" then sc else sc.updated(r, WordSpaceValue(s))
    def checked(ref: PathRef, value: P): P =
      if validateHints then
        val length = ops.length(value)
        require(ref.lengthHint < 0 || ref.lengthHint == length, s"${ref.s} exact hint ${ref.lengthHint}, actual $length")
        require(ref.maxLengthHint < 0 || length <= ref.maxLengthHint, s"${ref.s} upper hint ${ref.maxLengthHint}, actual $length")
      value

    // A heap continuation machine: each suspension needs one continuation,
    // rather than TailRec's Call/Done/Cont objects and composed closures.
    // Both path and space evaluation use this same loop on both backends.
    val pending = scala.collection.mutable.ArrayDeque.empty[() => Unit]
    var currentSpace = expression
    var currentPath: WordPath[P] = WordPath.Constant(ops.empty)
    var currentPC = paths
    var currentSC = spaces.view.mapValues(v => WordSpaceValue(store.from(v.paths))).toMap
    var spaceValue = store.empty
    var pathValue = ops.empty
    var mode = 0 // 0: space, 1: path, 2: resume
    def space(s: WordSpace[P], pc: PC, sc: SC): Unit =
      currentSpace = s; currentPC = pc; currentSC = sc; mode = 0
    def path(p: WordPath[P], pc: PC, sc: SC): Unit =
      currentPath = p; currentPC = pc; currentSC = sc; mode = 1
    def result(s: Set[P]): Unit = { spaceValue = s; mode = 2 }
    def pathResult(p: P): Unit = { pathValue = p; mode = 2 }
    def after(k: () => Unit): Unit = pending.append(k)
    def binary(x: WordSpace[P], y: WordSpace[P], pc: PC, sc: SC)(f: (Set[P], Set[P]) => Set[P]): Unit =
      after { () =>
        val left = spaceValue
        after(() => result(f(left, spaceValue)))
        space(y, pc, sc)
      }
      space(x, pc, sc)
    def unary(s: WordSpace[P], pc: PC, sc: SC)(f: Set[P] => Set[P]): Unit =
      after(() => result(f(spaceValue))); space(s, pc, sc)
    def arguments(ps: Vector[WordPath[P]], ss: Vector[WordSpace[P]], pc: PC, sc: SC)
        (done: (Array[Any], Array[Set[P]]) => Unit): Unit =
      val pv = new Array[Any](ps.size)
      val sv = new Array[Set[P]](ss.size)
      var pi = 0; var si = 0
      def advance(): Unit =
        while pi < ps.size do
          ps(pi) match
            case WordPath.Constant(value) => pv(pi) = value; pi += 1
            case WordPath.Deref(ref) => pv(pi) = checked(ref, pc(ref)); pi += 1
            case p =>
              after(() => { pv(pi) = pathValue; pi += 1; advance() })
              path(p, pc, sc)
              return
        while si < ss.size do
          ss(si) match
            case Mention(ref) => stats.foreach(x => x.nodes += 1); sv(si) = sc(ref).paths; si += 1
            case Literal(value) => stats.foreach(x => x.nodes += 1); sv(si) = stored(value.paths); si += 1
            case Empty() => stats.foreach(x => x.nodes += 1); sv(si) = store.empty; si += 1
            case s =>
              after(() => { sv(si) = spaceValue; si += 1; advance() })
              space(s, pc, sc)
              return
        done(pv, sv)
      advance()

    var finished = false
    try
      while !finished do
        if mode == 2 then
          if pending.isEmpty then finished = true else pending.removeLast()()
        else if mode == 1 then
          val pc = currentPC; val sc = currentSC
          currentPath match
            case WordPath.Constant(p) => pathResult(p)
            case WordPath.Deref(r) => pathResult(checked(r, pc(r)))
            case WordPath.Concat(l, r) =>
              after { () =>
                val left = pathValue
                after(() => pathResult(ops.concat(left, pathValue)))
                path(r, pc, sc)
              }
              path(l, pc, sc)
            case WordPath.GroundedPP(p, f) => after(() => pathResult(f(pathValue))); path(p, pc, sc)
            case WordPath.GroundedSP(s, f) => after(() => pathResult(f(WordSpaceValue(spaceValue)))); space(s, pc, sc)
        else
          stats.foreach(x => x.nodes += 1)
          val pc = currentPC; val sc = currentSC
          currentSpace match
            case Empty() => result(store.empty)
            case Mention(m) => result(sc(m).paths)
            case Literal(v) => result(stored(v.paths))
            case Singleton(p) => after(() => result(store.from(Vector(pathValue)))); path(p, pc, sc)
            case Call(r, ps, ss) =>
              stats.foreach(x => x.calls += 1)
              val routine = routines(r)
              require(ps.size == routine.refs.size && ss.size == routine.mentions.size, s"arity mismatch: ${r.s}")
              arguments(ps, ss, pc, sc) { (pv, sv) =>
                var nextPC = Map.empty[PathRef, P]; var nextSC = Map.empty[SpaceMention, WordSpaceValue[P]]
                var i = 0
                while i < pv.length do
                  val value = pv(i).asInstanceOf[P]
                  nextPC = nextPC.updated(routine.refs(i), checked(routine.refs(i), value)); i += 1
                i = 0
                while i < sv.length do
                  nextSC = nextSC.updated(routine.mentions(i), WordSpaceValue(sv(i))); i += 1
                routine.body match
                  case Union(left, Call(next, nextPS, nextSS)) if next == r && nextPS == ps && nextSS == ss =>
                    arguments(nextPS, nextSS, nextPC, nextSC) { (actualP, actualS) =>
                      space(if actualP.sameElements(pv) && actualS.sameElements(sv) then left else routine.body, nextPC, nextSC)
                    }
                  case body => space(body, nextPC, nextSC)
              }
            case Union(x, y) => binary(x, y, pc, sc)(store.union)
            case Intersection(x, y) => binary(x, y, pc, sc)(store.intersect)
            case Subtraction(x, y) => binary(x, y, pc, sc)(store.subtract)
            case Restriction(x, y) => binary(x, y, pc, sc)((a, b) => store.restrict(a, b, true))
            case Raffination(x, y) => binary(x, y, pc, sc)((a, b) => store.restrict(a, b, false))
            case Composition(x, y) =>
              after { () =>
                val left = spaceValue
                if left.isEmpty then result(store.empty)
                else
                  after(() => result(store.compose(left, spaceValue)))
                  space(y, pc, sc)
              }
              space(x, pc, sc)
            case Wrap(s, p) =>
              after { () =>
                val prefix = pathValue
                after(() => result(store.wrap(spaceValue, prefix))); space(s, pc, sc)
              }
              path(p, pc, sc)
            case Unwrap(s, p) =>
              after { () =>
                val prefix = pathValue
                after(() => result(store.unwrap(spaceValue, prefix))); space(s, pc, sc)
              }
              path(p, pc, sc)
            case Iteration(s, h, t, body) =>
              after { () =>
                val values = spaceValue
                if h.s == "_" && t.s == "_" && values.size == 1 then
                  if ops.length(values.head) > 0 then space(body, pc, sc) else result(store.empty)
                else
                  val groups = store.groups(values)
                  if groups.isEmpty then result(store.empty)
                  else if groups.size == 1 then
                    val (head, tail) = groups.head
                    space(body, bindPath(pc, h, head), bindSpace(sc, t, tail))
                  else
                    val iterator = groups.iterator
                    var out = store.empty
                    def next(): Unit =
                      if !iterator.hasNext then result(out)
                      else
                        val (head, tail) = iterator.next()
                        after(() => { out = store.union(out, spaceValue); next() })
                        space(body, bindPath(pc, h, head), bindSpace(sc, t, tail))
                    next()
              }
              space(s, pc, sc)
            case Fold(s, initial, acc, h, t, body, update) =>
              after { () =>
                val initialValue = pathValue
                after { () =>
                  val iterator = store.groups(spaceValue).sortBy(_._1).iterator
                  var out = store.empty
                  def next(a: P): Unit =
                    if !iterator.hasNext then result(out)
                    else
                      val (head, tail) = iterator.next()
                      val nextPC = bindPath(bindPath(pc, acc, a), h, head)
                      val nextSC = bindSpace(sc, t, tail)
                      after { () =>
                        out = store.union(out, spaceValue)
                        after(() => next(pathValue)); path(update, nextPC, nextSC)
                      }
                      space(body, nextPC, nextSC)
                  next(initialValue)
                }
                space(s, pc, sc)
              }
              path(initial, pc, sc)
            case Fixpoint(initial, variable, step) =>
              after { () =>
                def next(current: Set[P]): Unit =
                  after { () =>
                    val value = store.union(current, spaceValue)
                    if value == current then result(current) else next(value)
                  }
                  space(step, pc, bindSpace(sc, variable, current))
                next(spaceValue)
              }
              space(initial, pc, sc)
            case TailsUnion(s) => unary(s, pc, sc)(v => store.from(store.groups(v).iterator.flatMap(_._2).toVector))
            case TailsIntersection(s) => unary(s, pc, sc)(v => store.groups(v).map(_._2).reduceOption(store.intersect).getOrElse(store.empty))
            case PrefixClosure(s) => unary(s, pc, sc)(v => store.from(v.flatMap(p => (1 to ops.length(p)).map(ops.take(p, _)))))
            case SuffixClosure(s) => unary(s, pc, sc)(v => store.from(v.flatMap(p => (0 until ops.length(p)).map(ops.drop(p, _)))))
            case TailsClosure(s) => unary(s, pc, sc)(v => store.from(v.flatMap(p => (0 to ops.length(p)).map(ops.drop(p, _)))))
            case GroundedPS(p, f) => after(() => result(stored(f(pathValue).paths))); path(p, pc, sc)
            case GroundedSS(s, f) => unary(s, pc, sc)(v => stored(f(WordSpaceValue(v)).paths))
            case Range(s, start, end) => unary(s, pc, sc)(v => store.range(v, start, end))
      WordSpaceValue(spaceValue)
    finally store.clearCaches()

type BitPathValue = BigInt
object BitPathValue:
  def apply(word: BigInt): BigInt =
    require(word > 0, "a bit word must have a leading length delimiter")
    word
  def apply(bits: Iterable[Boolean]): BigInt = BitWords.fromDigits(bits.iterator.map(b => if b then 1 else 0).toVector)
  def fromDigits(bits: Iterable[Int]): BigInt = BitWords.fromDigits(bits)
  val empty: BigInt = BitWords.empty

type BitPath = WordPath[BigInt]
object BitPath:
  export WordPath.{Deref, Constant, Concat, GroundedPP, GroundedSP}
  val ZERO: BitPath = WordPath.Constant(BitWords.empty)
type BitSpace = WordSpace[BigInt]
object BitSpace:
  export WordSpace.{Empty as _, *}
  val Empty: BitSpace = WordSpace.Empty()
type BitSpaceValue = WordSpaceValue[BigInt]
object BitSpaceValue:
  def apply(paths: Set[BigInt]): BitSpaceValue =
    require(paths.forall(_ > 0), "bit words must have leading length delimiters")
    WordSpaceValue(paths)
  def unapply(value: BitSpaceValue): Some[Set[BigInt]] = Some(value.paths)
type BitRoutine = WordRoutine[BigInt]
object BitRoutine:
  def apply(name: RoutinePtr, refs: Vector[PathRef], mentions: Vector[SpaceMention], body: BitSpace): BitRoutine =
    WordRoutine(name, refs, mentions, body)
type BytePath = WordPath[BytePathValue]
object BytePath:
  export WordPath.{Deref, Constant, Concat, GroundedPP, GroundedSP}
  val ZERO: BytePath = WordPath.Constant(ByteWords.empty)
type ByteSpace = WordSpace[BytePathValue]
object ByteSpace:
  export WordSpace.{Empty as _, *}
  val Empty: ByteSpace = WordSpace.Empty()
type ByteSpaceValue = WordSpaceValue[BytePathValue]
object ByteSpaceValue:
  def apply(paths: Set[BytePathValue]): ByteSpaceValue = WordSpaceValue(paths)
  def unapply(value: ByteSpaceValue): Some[Set[BytePathValue]] = Some(value.paths)
type ByteRoutine = WordRoutine[BytePathValue]
object ByteRoutine:
  def apply(name: RoutinePtr, refs: Vector[PathRef], mentions: Vector[SpaceMention], body: ByteSpace): ByteRoutine =
    WordRoutine(name, refs, mentions, body)

object Zippy_bits:
  type Path = BitPath
  val Path = BitPath
  type Space = BitSpace
  val Space = BitSpace
  type PathValue = BitPathValue
  val PathValue = BitPathValue
  type SpaceValue = BitSpaceValue
  val SpaceValue = BitSpaceValue
  type Routine = BitRoutine
  val Routine = BitRoutine
  val maxHeadSize = 2
  given WordOps[BigInt] = BitWords
  def eval(s: BitSpace, paths: Map[PathRef, BigInt] = Map.empty, spaces: Map[SpaceMention, BitSpaceValue] = Map.empty,
           routines: Map[RoutinePtr, BitRoutine] = Map.empty): BitSpaceValue = WordEvaluator.eval(s, paths, spaces, routines)
  def headMask(value: BitSpaceValue): Int = value.paths.iterator.flatMap(BitWords.split).foldLeft(0)((m, p) => m | (1 << BitWords.digits(p._1).head))

object Zippy_bytes:
  type Path = BytePath
  val Path = BytePath
  type Space = ByteSpace
  val Space = ByteSpace
  type PathValue = BytePathValue
  val PathValue = BytePathValue
  type SpaceValue = ByteSpaceValue
  val SpaceValue = ByteSpaceValue
  type Routine = ByteRoutine
  val Routine = ByteRoutine
  val maxHeadSize = 256
  given WordOps[BytePathValue] = ByteWords
  def eval(s: ByteSpace, paths: Map[PathRef, BytePathValue] = Map.empty, spaces: Map[SpaceMention, ByteSpaceValue] = Map.empty,
           routines: Map[RoutinePtr, ByteRoutine] = Map.empty): ByteSpaceValue = WordEvaluator.eval(s, paths, spaces, routines)
  def evalZ(s: ByteSpace, paths: Map[PathRef, BytePathValue] = Map.empty,
            spaces: Map[SpaceMention, ByteSpaceValue] = Map.empty,
            routines: Map[RoutinePtr, ByteRoutine] = Map.empty,
            stats: Option[WordEvaluationStats] = None, validateHints: Boolean = false): ByteTrie =
    val value = WordEvaluator.eval(s, paths, spaces, routines, stats, validateHints, Some(new ByteTrieBackend))(using ByteWords)
    value.paths.asInstanceOf[ByteTrieSet].trie
  /** A 256-bit result, independent of the number of paths in the subspace. */
  def headMask(value: ByteSpaceValue): BigInt = value.paths.iterator.flatMap(ByteWords.split).foldLeft(BigInt(0))((m, p) => m.setBit(ByteWords.digits(p._1).head))
