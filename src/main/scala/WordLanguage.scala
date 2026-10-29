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
    (p << n) | q.clearBit(n)
  override def compare(p: BigInt, q: BigInt): Int =
    val a = length(p); val b = length(q); val common = a.min(b)
    val c = (p >> (a - common)).compare(q >> (b - common))
    if c != 0 then c else a.compare(b)
  override def startsWith(p: BigInt, q: BigInt): Boolean =
    val d = length(p) - length(q)
    d >= 0 && (p >> d) == q
  override def take(p: BigInt, n: Int): BigInt = p >> (length(p) - n.max(0).min(length(p)))
  override def drop(p: BigInt, n: Int): BigInt =
    val remaining = length(p) - n.max(0).min(length(p))
    (p & ((BigInt(1) << remaining) - 1)).setBit(remaining)

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
  private[morkl] def concat(that: BytePathValue): BytePathValue =
    val result = Arrays.copyOf(data, length + that.length)
    System.arraycopy(that.data, 0, result, length, that.length)
    new BytePathValue(result)
  private[morkl] def slice(lo: Int, hi: Int): BytePathValue = new BytePathValue(Arrays.copyOfRange(data, lo, hi))

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
  override def split(p: BytePathValue): Option[(BytePathValue, BytePathValue)] =
    if p.length == 0 then None else Some(take(p, 1) -> drop(p, 1))

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
              stats: Option[WordEvaluationStats] = None, validateHints: Boolean = false)(using ops: WordOps[P]): WordSpaceValue[P] =
    import WordSpace.*
    import ops.ordering
    import scala.util.control.TailCalls.{TailRec, done, tailcall}
    type PC = Map[PathRef, P]
    type SC = Map[SpaceMention, WordSpaceValue[P]]
    def bindPath(pc: PC, r: PathRef, p: P): PC = if r.s == "_" then pc else pc.updated(r, p)
    def bindSpace(sc: SC, r: SpaceMention, s: Set[P]): SC = if r.s == "_" then sc else sc.updated(r, WordSpaceValue(s))
    def traverse[X, Y](xs: Iterable[X])(f: X => TailRec[Y]): TailRec[Vector[Y]] =
      val iterator = xs.iterator
      val out = Vector.newBuilder[Y]
      def loop(): TailRec[Vector[Y]] =
        if !iterator.hasNext then done(out.result())
        else f(iterator.next()).flatMap { y => out += y; tailcall(loop()) }
      tailcall(loop())
    def path(p: WordPath[P], pc: PC, sc: SC): TailRec[P] = tailcall {
      p match
        case WordPath.Deref(r) => done(pc(r))
        case WordPath.Constant(p) => done(p)
        case WordPath.Concat(l, r) => for a <- path(l, pc, sc); b <- path(r, pc, sc) yield ops.concat(a, b)
        case WordPath.GroundedPP(p, f) => path(p, pc, sc).map(f)
        case WordPath.GroundedSP(s, f) => run(s, pc, sc).map(x => f(WordSpaceValue(x)))
    }
    def grouped(values: Set[P]): Vector[(P, Set[P])] =
      val groups = scala.collection.mutable.HashMap.empty[P, scala.collection.mutable.Set[P]]
      values.iterator.flatMap(ops.split).foreach { (h, t) => groups.getOrElseUpdate(h, scala.collection.mutable.Set.empty) += t }
      groups.iterator.map((h, t) => h -> t.toSet).toVector
    def run(s: WordSpace[P], pc: PC, sc: SC): TailRec[Set[P]] = tailcall {
      stats.foreach(x => x.nodes += 1)
      def rec(s: WordSpace[P]): TailRec[Set[P]] = run(s, pc, sc)
      def binary(x: WordSpace[P], y: WordSpace[P])(f: (Set[P], Set[P]) => Set[P]): TailRec[Set[P]] =
        for a <- rec(x); b <- rec(y) yield f(a, b)
      s match
        case Empty() => done(Set.empty)
        case Call(r, ps, ss) =>
          stats.foreach(x => x.calls += 1)
          val routine = routines(r)
          require(ps.size == routine.refs.size && ss.size == routine.mentions.size, s"arity mismatch: ${r.s}")
          for
            pathValues <- traverse(ps)(path(_, pc, sc))
            spaceValues <- traverse(ss)(rec)
            result <-
              if validateHints then routine.refs.zip(pathValues).foreach { (ref, value) =>
                val length = ops.length(value)
                require(ref.lengthHint < 0 || ref.lengthHint == length, s"${r.s}: ${ref.s} exact hint ${ref.lengthHint}, actual $length")
                require(ref.maxLengthHint < 0 || length <= ref.maxLengthHint, s"${r.s}: ${ref.s} upper hint ${ref.maxLengthHint}, actual $length")
              }
              val nextPC = routine.refs.zip(pathValues).toMap
              val nextSC = routine.mentions.zip(spaceValues.map(WordSpaceValue(_))).toMap
              routine.body match
                case Union(left, Call(next, nextPS, nextSS)) if next == r && nextPS == ps && nextSS == ss =>
                  for
                    actualPS <- traverse(nextPS)(path(_, nextPC, nextSC))
                    actualSS <- traverse(nextSS)(run(_, nextPC, nextSC))
                    out <- if actualPS == pathValues && actualSS == spaceValues then run(left, nextPC, nextSC) else run(routine.body, nextPC, nextSC)
                  yield out
                case _ => run(routine.body, nextPC, nextSC)
          yield result
        case Mention(m) => done(sc(m).paths)
        case Singleton(p) => path(p, pc, sc).map(Set(_))
        case Literal(v) => done(v.paths)
        case Union(x, y) => binary(x, y)(_ union _)
        case Intersection(x, y) => binary(x, y)(_ intersect _)
        case Subtraction(x, y) => binary(x, y)(_ diff _)
        case Restriction(x, y) => binary(x, y)((a, b) => a.filter(p => b.exists(ops.startsWith(p, _))))
        case Raffination(x, y) => binary(x, y)((a, b) => a.filterNot(p => b.exists(ops.startsWith(p, _))))
        case Composition(x, y) => rec(x).flatMap { a =>
          if a.isEmpty then done(Set.empty)
          else rec(y).map(b => for p <- a; q <- b yield ops.concat(p, q))
        }
        case Wrap(s, p) => for prefix <- path(p, pc, sc); values <- rec(s) yield values.map(ops.concat(prefix, _))
        case Unwrap(s, p) => for prefix <- path(p, pc, sc); values <- rec(s)
          yield values.filter(ops.startsWith(_, prefix)).map(ops.drop(_, ops.length(prefix)))
        case Iteration(s, h, t, body) => rec(s).flatMap { values =>
          traverse(grouped(values))((head, tail) => run(body, bindPath(pc, h, head), bindSpace(sc, t, tail))).map(_.iterator.flatten.toSet)
        }
        case Fold(s, initial, acc, h, t, body, update) =>
          for
            initialValue <- path(initial, pc, sc)
            values <- rec(s)
            result <-
              val iterator = grouped(values).sortBy(_._1).iterator
              val out = Set.newBuilder[P]
              def loop(a: P): TailRec[Set[P]] =
                if !iterator.hasNext then done(out.result())
                else
                  val (head, tail) = iterator.next()
                  val nextPC = bindPath(bindPath(pc, acc, a), h, head)
                  val nextSC = bindSpace(sc, t, tail)
                  for
                    value <- run(body, nextPC, nextSC)
                    next <- path(update, nextPC, nextSC)
                    result <- { out ++= value; tailcall(loop(next)) }
                  yield result
              tailcall(loop(initialValue))
          yield result
        case Fixpoint(initial, variable, step) => rec(initial).flatMap { start =>
          def loop(current: Set[P]): TailRec[Set[P]] = run(step, pc, bindSpace(sc, variable, current)).flatMap { value =>
            val next = current union value
            if next == current then done(current) else tailcall(loop(next))
          }
          tailcall(loop(start))
        }
        case TailsUnion(s) => rec(s).map(values => grouped(values).iterator.flatMap(_._2).toSet)
        case TailsIntersection(s) => rec(s).map(values => grouped(values).map(_._2).reduceOption(_ intersect _).getOrElse(Set.empty))
        case PrefixClosure(s) => rec(s).map(_.flatMap(p => (1 to ops.length(p)).map(ops.take(p, _))))
        case SuffixClosure(s) => rec(s).map(_.flatMap(p => (0 until ops.length(p)).map(ops.drop(p, _))))
        case TailsClosure(s) => rec(s).map(_.flatMap(p => (0 to ops.length(p)).map(ops.drop(p, _))))
        case GroundedPS(p, f) => path(p, pc, sc).map(x => f(x).paths)
        case GroundedSS(s, f) => rec(s).map(x => f(WordSpaceValue(x)).paths)
        case Range(s, start, end) => rec(s).map { values =>
          val (lo, hi) = RangeBounds.normalize(values.size, start, end)
          if hi <= lo then Set.empty
          else if lo == 0 && hi == values.size then values
          else if lo == 0 && hi == 1 then Set(values.min)
          else values.toVector.sorted.slice(lo, hi).toSet
        }
    }
    WordSpaceValue(run(expression, paths, spaces).result)

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
  /** A 256-bit result, independent of the number of paths in the subspace. */
  def headMask(value: ByteSpaceValue): BigInt = value.paths.iterator.flatMap(ByteWords.split).foldLeft(BigInt(0))((m, p) => m.setBit(ByteWords.digits(p._1).head))
