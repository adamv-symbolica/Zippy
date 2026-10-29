package morkl

import scala.collection.immutable.ArraySeq

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
  override def startsWith(p: BigInt, q: BigInt): Boolean =
    val d = length(p) - length(q)
    d >= 0 && (p >> d) == q
  override def take(p: BigInt, n: Int): BigInt = p >> (length(p) - n.max(0).min(length(p)))
  override def drop(p: BigInt, n: Int): BigInt =
    val remaining = length(p) - n.max(0).min(length(p))
    (p & ((BigInt(1) << remaining) - 1)).setBit(remaining)

/** Immutable byte array with value equality (Array[Byte] itself has identity
  * equality). Construction and extraction copy, so set keys cannot mutate.
  */
final case class BytePathValue private (private val bytes: ArraySeq[Byte]):
  def toArray: Array[Byte] = bytes.toArray
  def unsigned: Vector[Int] = bytes.iterator.map(_ & 255).toVector
  def length: Int = bytes.length

object BytePathValue:
  def apply(bytes: Array[Byte]): BytePathValue = new BytePathValue(ArraySeq.from(bytes))
  def fromUnsigned(bytes: Iterable[Int]): BytePathValue =
    val values = bytes.iterator.map { b =>
      require(b >= 0 && b < 256, s"not a byte: $b")
      b.toByte
    }.toArray
    apply(values)
  val empty: BytePathValue = apply(Array.emptyByteArray)

object ByteWords extends WordOps[BytePathValue]:
  val alphabetSize = 256
  def digits(p: BytePathValue): Vector[Int] = p.unsigned
  def fromDigits(p: Iterable[Int]): BytePathValue = BytePathValue.fromUnsigned(p)
  override val empty: BytePathValue = BytePathValue.empty
  override def length(p: BytePathValue): Int = p.length

case class WordSpaceValue[P](paths: Set[P])

enum WordPath[P]:
  case Deref(pr: PathRef)
  case Constant(value: P)
  case Concat(l: WordPath[P], r: WordPath[P])
  case GroundedPP(p: WordPath[P], f: P => P)
  case GroundedSP(p: WordSpace[P], f: WordSpaceValue[P] => P)

enum WordSpace[P]:
  case Empty[P]() extends WordSpace[P]
  case Call(r: RoutinePtr, refs: Vector[WordPath[P]], mentions: Vector[WordSpace[P]])
  case Mention(variable: SpaceMention)
  case Singleton(p: WordPath[P])
  case Literal(value: WordSpaceValue[P])
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
  def eval(paths: Map[PathRef, P] = Map.empty[PathRef, P], spaces: Map[SpaceMention, WordSpaceValue[P]] = Map.empty[SpaceMention, WordSpaceValue[P]])(using ops: WordOps[P]): WordSpaceValue[P] =
    WordEvaluator.eval(entry, paths, spaces, routines)

object WordEvaluator:
  def eval[P](expression: WordSpace[P], paths: Map[PathRef, P] = Map.empty[PathRef, P],
              spaces: Map[SpaceMention, WordSpaceValue[P]] = Map.empty[SpaceMention, WordSpaceValue[P]],
              routines: Map[RoutinePtr, WordRoutine[P]] = Map.empty[RoutinePtr, WordRoutine[P]])(using ops: WordOps[P]): WordSpaceValue[P] =
    import WordSpace.*
    import ops.ordering
    def run(s: WordSpace[P], pc: Map[PathRef, P], sc: Map[SpaceMention, WordSpaceValue[P]]): Set[P] =
      def path(p: WordPath[P]): P = p match
        case WordPath.Deref(r) => pc(r)
        case WordPath.Constant(p) => p
        case WordPath.Concat(l, r) => ops.concat(path(l), path(r))
        case WordPath.GroundedPP(p, f) => f(path(p))
        case WordPath.GroundedSP(s, f) => f(WordSpaceValue(rec(s)))
      def rec(s: WordSpace[P]): Set[P] = run(s, pc, sc)
      def groups(s: WordSpace[P]): Vector[(P, Set[P])] =
        rec(s).iterator.flatMap(ops.split).toVector.groupMap(_._1)(_._2).iterator.map((h, t) => h -> t.toSet).toVector
      s match
        case Empty() => Set.empty
        case Call(r, ps, ss) =>
          val routine = routines(r)
          require(ps.size == routine.refs.size && ss.size == routine.mentions.size, s"arity mismatch: ${r.s}")
          run(routine.body, routine.refs.zip(ps.map(path)).toMap, routine.mentions.zip(ss.map(x => WordSpaceValue(rec(x)))).toMap)
        case Mention(m) => sc(m).paths
        case Singleton(p) => Set(path(p))
        case Literal(v) => v.paths
        case Union(x, y) => rec(x) union rec(y)
        case Intersection(x, y) => rec(x) intersect rec(y)
        case Subtraction(x, y) => rec(x) diff rec(y)
        case Restriction(x, y) =>
          val prefixes = rec(y)
          rec(x).filter(p => prefixes.exists(ops.startsWith(p, _)))
        case Raffination(x, y) => rec(x) diff rec(Restriction(x, y))
        case Composition(x, y) => for p <- rec(x); q <- rec(y) yield ops.concat(p, q)
        case Wrap(s, p) => val prefix = path(p); rec(s).map(ops.concat(prefix, _))
        case Unwrap(s, p) =>
          val prefix = path(p)
          rec(s).filter(ops.startsWith(_, prefix)).map(ops.drop(_, ops.length(prefix)))
        case Iteration(s, h, t, body) =>
          groups(s).flatMap((a, b) => run(body, pc.updated(h, a), sc.updated(t, WordSpaceValue(b)))).toSet
        case Fold(s, initial, acc, h, t, body, update) =>
          var a = path(initial)
          val out = Set.newBuilder[P]
          groups(s).sortBy(_._1).foreach { (head, tail) =>
            val nextPC = pc.updated(acc, a).updated(h, head)
            val nextSC = sc.updated(t, WordSpaceValue(tail))
            out ++= run(body, nextPC, nextSC)
            a = run(Singleton(update), nextPC, nextSC).head
          }
          out.result()
        case Fixpoint(initial, variable, step) =>
          var current = rec(initial)
          var done = false
          while !done do
            val next = current union run(step, pc, sc.updated(variable, WordSpaceValue(current)))
            done = next == current
            current = next
          current
        case TailsUnion(s) => groups(s).flatMap(_._2).toSet
        case TailsIntersection(s) => groups(s).map(_._2).reduceOption(_ intersect _).getOrElse(Set.empty)
        case PrefixClosure(s) => rec(s).flatMap(p => (1 to ops.length(p)).map(ops.take(p, _)))
        case SuffixClosure(s) => rec(s).flatMap(p => (0 until ops.length(p)).map(ops.drop(p, _)))
        case TailsClosure(s) => rec(s).flatMap(p => (0 to ops.length(p)).map(ops.drop(p, _)))
        case GroundedPS(p, f) => f(path(p)).paths
        case GroundedSS(s, f) => f(WordSpaceValue(rec(s))).paths
        case Range(s, start, end) =>
          val values = rec(s).toVector.sorted
          val (lo, hi) = RangeBounds.normalize(values.size, start, end)
          values.slice(lo, hi).toSet
    WordSpaceValue(run(expression, paths, spaces))

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
  def apply(paths: Set[BigInt]): BitSpaceValue = WordSpaceValue(paths)
type BitRoutine = WordRoutine[BigInt]
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
type ByteRoutine = WordRoutine[BytePathValue]

object Zippy_bits:
  given WordOps[BigInt] = BitWords
  def eval(s: BitSpace, paths: Map[PathRef, BigInt] = Map.empty, spaces: Map[SpaceMention, BitSpaceValue] = Map.empty,
           routines: Map[RoutinePtr, BitRoutine] = Map.empty): BitSpaceValue = WordEvaluator.eval(s, paths, spaces, routines)
  def headMask(value: BitSpaceValue): Int = value.paths.iterator.flatMap(BitWords.split).foldLeft(0)((m, p) => m | (1 << BitWords.digits(p._1).head))

object Zippy_bytes:
  given WordOps[BytePathValue] = ByteWords
  def eval(s: ByteSpace, paths: Map[PathRef, BytePathValue] = Map.empty, spaces: Map[SpaceMention, ByteSpaceValue] = Map.empty,
           routines: Map[RoutinePtr, ByteRoutine] = Map.empty): ByteSpaceValue = WordEvaluator.eval(s, paths, spaces, routines)
  /** A 256-bit result, independent of the number of paths in the subspace. */
  def headMask(value: ByteSpaceValue): BigInt = value.paths.iterator.flatMap(ByteWords.split).foldLeft(BigInt(0))((m, p) => m.setBit(ByteWords.digits(p._1).head))
