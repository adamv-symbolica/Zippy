package morkl

/** Path operations shared by the reference interpreters. No trie or symbol table
  * is hidden in a value: bits use a leading-one BigInt; bytes own an array. */
trait PathDomain[P]:
  def empty: P
  def length(p: P): Int
  def concat(a: P, b: P): P
  def take(p: P, n: Int): P
  def drop(p: P, n: Int): P
  def compare(a: P, b: P): Int
  final def startsWith(p: P, prefix: P): Boolean =
    length(p) >= length(prefix) && take(p, length(prefix)) == prefix
  final def atoms(p: P): Vector[P] = (0 until length(p)).map(i => take(drop(p, i), 1)).toVector

trait FiniteDomain[P] extends PathDomain[P]:
  def arity: Int
  def single(digit: Int): P
  def digit(singleton: P): Int
  def fromDigits(ds: Iterable[Int]): P = ds.foldLeft(empty)((p, d) => concat(p, single(d)))
  def digits(p: P): Vector[Int] = atoms(p).map(digit)

object BitPaths extends FiniteDomain[BigInt]:
  val arity = 2
  val empty: BigInt = 1
  override def fromDigits(ds: Iterable[Int]): BigInt =
    val bits = new java.lang.StringBuilder("1")
    ds.foreach { digit =>
      require(digit == 0 || digit == 1, s"not a bit: $digit")
      bits.append(if digit == 0 then '0' else '1')
    }
    BigInt(bits.toString, 2)
  override def digits(p: BigInt): Vector[Int] =
    valid(p)
    p.toString(2).iterator.drop(1).map(_ - '0').toVector
  private def valid(p: BigInt): Unit = require(p > 0, "bit paths need a leading-one sentinel")
  def length(p: BigInt): Int = { valid(p); p.bitLength - 1 }
  def single(digit: Int): BigInt =
    require(digit == 0 || digit == 1, s"not a bit: $digit")
    BigInt(2 + digit)
  def digit(p: BigInt): Int =
    require(length(p) == 1, "expected one bit")
    (p & 1).toInt
  def concat(a: BigInt, b: BigInt): BigInt =
    val n = length(b)
    valid(a)
    (a << n) | (b ^ (BigInt(1) << n))
  def take(p: BigInt, n: Int): BigInt = p >> (length(p) - n.max(0).min(length(p)))
  def drop(p: BigInt, n: Int): BigInt =
    val remaining = length(p) - n.max(0).min(length(p))
    (BigInt(1) << remaining) | (p & ((BigInt(1) << remaining) - 1))
  def compare(a: BigInt, b: BigInt): Int =
    val la = length(a); val lb = length(b); val n = la.min(lb)
    val c = take(a, n).compare(take(b, n))
    if c != 0 then c else la.compare(lb)

/** Immutable, content-equal byte array. Both ingress and egress copy. */
final class BytePath private (private val data: Array[Byte]):
  def toArray: Array[Byte] = data.clone()
  def unsigned: Vector[Int] = data.iterator.map(_ & 255).toVector
  def length: Int = data.length
  override def equals(other: Any): Boolean = other match
    case p: BytePath => java.util.Arrays.equals(data, p.data)
    case _ => false
  override def hashCode(): Int = java.util.Arrays.hashCode(data)
  override def toString: String = unsigned.mkString("BytePath(", ",", ")")
object BytePath:
  def apply(bytes: Array[Byte]): BytePath = new BytePath(bytes.clone())
  def apply(bytes: Int*): BytePath =
    require(bytes.forall(b => b >= 0 && b < 256), "not a byte")
    new BytePath(bytes.map(_.toByte).toArray)

object BytePaths extends FiniteDomain[BytePath]:
  val arity = 256
  val empty = BytePath()
  override def fromDigits(ds: Iterable[Int]): BytePath = BytePath(ds.iterator.map { digit =>
    require(digit >= 0 && digit < 256, s"not a byte: $digit")
    digit.toByte
  }.toArray)
  override def digits(p: BytePath): Vector[Int] = p.unsigned
  def length(p: BytePath): Int = p.length
  def single(digit: Int): BytePath = BytePath(digit)
  def digit(p: BytePath): Int =
    require(p.length == 1, "expected one byte")
    p.unsigned.head
  def concat(a: BytePath, b: BytePath): BytePath = BytePath(a.toArray ++ b.toArray)
  def take(p: BytePath, n: Int): BytePath = BytePath(p.toArray.take(n))
  def drop(p: BytePath, n: Int): BytePath = BytePath(p.toArray.drop(n))
  def compare(a: BytePath, b: BytePath): Int = java.util.Arrays.compareUnsigned(a.toArray, b.toArray)

object SymbolPaths extends PathDomain[PathValue]:
  val empty = PathValue(Nil)
  def length(p: PathValue): Int = p.items.length
  def concat(a: PathValue, b: PathValue): PathValue = PathValue(a.items ++ b.items)
  def take(p: PathValue, n: Int): PathValue = PathValue(p.items.take(n))
  def drop(p: PathValue, n: Int): PathValue = PathValue(p.items.drop(n))
  def compare(a: PathValue, b: PathValue): Int =
    import PathItemOrder.given
    summon[Ordering[PathValue]].compare(a, b)

/** Exactly the five cases of Path, parameterized only by path representation. */
enum AlphabetPath[P]:
  case Deref(ref: PathRef)
  case Constant(value: P)
  case Concat(left: AlphabetPath[P], right: AlphabetPath[P])
  case GroundedPP(path: AlphabetPath[P], f: P => P)
  case GroundedSP(space: AlphabetSpace[P], f: Set[P] => P)

/** Exactly the cases of Space. Head is a derived operation, as in Zippy_symbols. */
enum AlphabetSpace[P]:
  case Empty()
  case Call(routine: RoutinePtr, refs: Vector[AlphabetPath[P]], mentions: Vector[AlphabetSpace[P]])
  case Mention(ref: SpaceMention)
  case Singleton(path: AlphabetPath[P])
  case Literal(paths: Set[P])
  case Union(left: AlphabetSpace[P], right: AlphabetSpace[P])
  case Intersection(left: AlphabetSpace[P], right: AlphabetSpace[P])
  case Subtraction(left: AlphabetSpace[P], right: AlphabetSpace[P])
  case Restriction(left: AlphabetSpace[P], right: AlphabetSpace[P])
  case Raffination(left: AlphabetSpace[P], right: AlphabetSpace[P])
  case Composition(left: AlphabetSpace[P], right: AlphabetSpace[P])
  case Iteration(src: AlphabetSpace[P], symbol: PathRef, rest: SpaceMention, body: AlphabetSpace[P])
  case Fold(src: AlphabetSpace[P], initial: AlphabetPath[P], acc: PathRef, symbol: PathRef, rest: SpaceMention, body: AlphabetSpace[P], update: AlphabetPath[P])
  case Fixpoint(initial: AlphabetSpace[P], variable: SpaceMention, step: AlphabetSpace[P])
  case Wrap(src: AlphabetSpace[P], prefix: AlphabetPath[P])
  case Unwrap(src: AlphabetSpace[P], prefix: AlphabetPath[P])
  case TailsUnion(src: AlphabetSpace[P])
  case TailsIntersection(src: AlphabetSpace[P])
  case PrefixClosure(src: AlphabetSpace[P])
  case SuffixClosure(src: AlphabetSpace[P])
  case TailsClosure(src: AlphabetSpace[P])
  case GroundedPS(path: AlphabetPath[P], f: P => Set[P])
  case GroundedSS(space: AlphabetSpace[P], f: Set[P] => Set[P])
  case Range(src: AlphabetSpace[P], start: Int, end: Int)

case class AlphabetRoutine[P](name: RoutinePtr, refs: Vector[PathRef], mentions: Vector[SpaceMention], body: AlphabetSpace[P])
case class AlphabetProgram[P](expression: AlphabetSpace[P], routines: Map[RoutinePtr, AlphabetRoutine[P]], domain: PathDomain[P]):
  def eval(paths: Map[PathRef, P] = Map.empty, spaces: Map[SpaceMention, Set[P]] = Map.empty): Set[P] =
    AlphabetEvaluator.eval(expression, domain, paths, spaces, routines)

object AlphabetEvaluator:
  def eval[P](expression: AlphabetSpace[P], d: PathDomain[P], paths: Map[PathRef, P] = Map.empty[PathRef, P], spaces: Map[SpaceMention, Set[P]] = Map.empty[SpaceMention, Set[P]], routines: Map[RoutinePtr, AlphabetRoutine[P]] = Map.empty[RoutinePtr, AlphabetRoutine[P]]): Set[P] =
    import AlphabetSpace.*
    def run(s: AlphabetSpace[P], pc: Map[PathRef, P], sc: Map[SpaceMention, Set[P]]): Set[P] =
      def path(p: AlphabetPath[P]): P = p match
        case AlphabetPath.Deref(r) => pc(r)
        case AlphabetPath.Constant(v) => d.length(v); v
        case AlphabetPath.Concat(a, b) => d.concat(path(a), path(b))
        case AlphabetPath.GroundedPP(p, f) => f(path(p))
        case AlphabetPath.GroundedSP(s, f) => f(run(s, pc, sc))
      def rec(x: AlphabetSpace[P]): Set[P] = run(x, pc, sc)
      def groups(src: AlphabetSpace[P]): Vector[(P, Set[P])] =
        rec(src).iterator.filter(d.length(_) > 0).toVector.groupMap(p => d.take(p, 1))(p => d.drop(p, 1))
          .iterator.map((h, t) => h -> t.toSet).toVector.sortWith((a, b) => d.compare(a._1, b._1) < 0)
      def bindPath(r: PathRef, p: P): Map[PathRef, P] = if r.s == "_" then pc else pc.updated(r, p)
      def bindSpace(r: SpaceMention, s: Set[P]): Map[SpaceMention, Set[P]] = if r.s == "_" then sc else sc.updated(r, s)
      s match
        case Empty() => Set.empty
        case Call(r, ps, ss) =>
          val f = routines(r)
          require(ps.size == f.refs.size && ss.size == f.mentions.size, s"arity mismatch: ${r.s}")
          val pValues = ps.map(path)
          val sValues = ss.map(rec)
          val nextPC = (f.refs zip pValues).toMap
          val nextSC = (f.mentions zip sValues).toMap
          // Same stable self-union convention as the symbol reference evaluator.
          val body = f.body match
            case Union(left, Call(`r`, `ps`, `ss`)) if
                (ps zip pValues).forall((p, v) => run(Singleton(p), nextPC, nextSC) == Set(v)) &&
                (ss zip sValues).forall((s, v) => run(s, nextPC, nextSC) == v) => left
            case other => other
          run(body, nextPC, nextSC)
        case Mention(r) => sc(r)
        case Singleton(p) => Set(path(p))
        case Literal(ps) => ps.foreach(d.length); ps
        case Union(a, b) => rec(a) union rec(b)
        case Intersection(a, b) => rec(a) intersect rec(b)
        case Subtraction(a, b) => rec(a) diff rec(b)
        case Restriction(a, b) => val prefixes = rec(b); rec(a).filter(p => prefixes.exists(d.startsWith(p, _)))
        case Raffination(a, b) => val prefixes = rec(b); rec(a).filterNot(p => prefixes.exists(d.startsWith(p, _)))
        case Composition(a, b) =>
          val as = rec(a)
          if as.isEmpty then Set.empty
          else
            val bs = rec(b)
            as.flatMap(x => bs.map(d.concat(x, _)))
        case Wrap(src, p) => val prefix = path(p); rec(src).map(d.concat(prefix, _))
        case Unwrap(src, p) => val prefix = path(p); rec(src).filter(d.startsWith(_, prefix)).map(d.drop(_, d.length(prefix)))
        case TailsUnion(src) => rec(src).filter(d.length(_) > 0).map(d.drop(_, 1))
        case TailsIntersection(src) => groups(src).map(_._2).reduceOption(_ intersect _).getOrElse(Set.empty)
        case PrefixClosure(src) => rec(src).flatMap(p => (1 to d.length(p)).map(d.take(p, _)))
        case SuffixClosure(src) => rec(src).flatMap(p => (0 until d.length(p)).map(d.drop(p, _)))
        case TailsClosure(src) => rec(src).flatMap(p => (0 to d.length(p)).map(d.drop(p, _)))
        case Iteration(src, h, t, body) => groups(src).flatMap((p, s) => run(body, bindPath(h, p), bindSpace(t, s))).toSet
        case Fold(src, initial, acc, h, t, body, update) =>
          var a = path(initial)
          val out = Set.newBuilder[P]
          groups(src).foreach { (p, s) =>
            val innerPC = (if acc.s == "_" then pc else pc.updated(acc, a)) ++ (if h.s == "_" then Map.empty else Map(h -> p))
            val innerSC = bindSpace(t, s)
            out ++= run(body, innerPC, innerSC)
            a = run(Singleton(update), innerPC, innerSC).head
          }
          out.result()
        case Fixpoint(initial, v, step) =>
          var current = rec(initial)
          var changed = true
          while changed do
            val next = current union run(step, pc, bindSpace(v, current))
            changed = next != current
            current = next
          current
        case GroundedPS(p, f) => f(path(p))
        case GroundedSS(s, f) => f(rec(s))
        case Range(src, start, end) =>
          val values = rec(src).toVector.sortWith(d.compare(_, _) < 0)
          val (lo, hi) = RangeBounds.normalize(values.size, start, end)
          values.slice(lo, hi).toSet
    run(expression, paths, spaces)

type BitSpace = AlphabetSpace[BigInt]
type BitPath = AlphabetPath[BigInt]
type ByteSpace = AlphabetSpace[BytePath]
type BytePathExpr = AlphabetPath[BytePath]

object AlphabetSpace:
  def Head[P](src: AlphabetSpace[P]): AlphabetSpace[P] =
    val h = PathRef("#head").known(1)
    Iteration(src, h, SpaceMention("_"), Singleton(AlphabetPath.Deref(h)))
