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
  def firstDigit(p: P): Int = digit(take(p, 1))
  final def headMask(paths: Iterable[P]): BigInt =
    paths.iterator.filter(length(_) > 0).foldLeft(BigInt(0))((mask, p) => mask.setBit(firstDigit(p)))
  final def heads(paths: Iterable[P]): Set[P] =
    var mask = headMask(paths)
    val out = Set.newBuilder[P]
    while mask != 0 do
      val digit = mask.lowestSetBit
      out += single(digit)
      mask = mask.clearBit(digit)
    out.result()

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
  override def firstDigit(p: BigInt): Int = if p.testBit(length(p) - 1) then 1 else 0
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
    val c = (a >> (la - n)).compare(b >> (lb - n))
    if c != 0 then c else la.compare(lb)

/** Immutable, content-equal byte array. Both ingress and egress copy. */
final class BytePath private (private val data: Array[Byte]):
  def toArray: Array[Byte] = data.clone()
  def unsigned: Vector[Int] = data.iterator.map(_ & 255).toVector
  def length: Int = data.length
  private[morkl] def first: Int = data(0) & 255
  private[morkl] def concat(other: BytePath): BytePath =
    val joined = java.util.Arrays.copyOf(data, data.length + other.data.length)
    System.arraycopy(other.data, 0, joined, data.length, other.data.length)
    new BytePath(joined)
  private[morkl] def take(n: Int): BytePath =
    if n >= length then this else new BytePath(java.util.Arrays.copyOf(data, n.max(0)))
  private[morkl] def drop(n: Int): BytePath =
    if n <= 0 then this else new BytePath(java.util.Arrays.copyOfRange(data, n.min(length), length))
  private[morkl] def compare(other: BytePath): Int = java.util.Arrays.compareUnsigned(data, other.data)
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
    p.first
  override def firstDigit(p: BytePath): Int = p.first
  def concat(a: BytePath, b: BytePath): BytePath = a.concat(b)
  def take(p: BytePath, n: Int): BytePath = p.take(n)
  def drop(p: BytePath, n: Int): BytePath = p.drop(n)
  def compare(a: BytePath, b: BytePath): Int = a.compare(b)

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
  /** A trampoline keeps language recursion independent of the JVM stack size.
    * Evaluation order and laziness still come from the original operations. */
  def eval[P](expression: AlphabetSpace[P], d: PathDomain[P], paths: Map[PathRef, P] = Map.empty[PathRef, P], spaces: Map[SpaceMention, Set[P]] = Map.empty[SpaceMention, Set[P]], routines: Map[RoutinePtr, AlphabetRoutine[P]] = Map.empty[RoutinePtr, AlphabetRoutine[P]]): Set[P] =
    import AlphabetSpace.*
    import scala.util.control.TailCalls.{TailRec, done, tailcall}
    def sequence[A, B](values: Iterable[A])(f: A => TailRec[B]): TailRec[Vector[B]] =
      val it = values.iterator
      val out = Vector.newBuilder[B]
      def next(): TailRec[Vector[B]] =
        if !it.hasNext then done(out.result())
        else tailcall(f(it.next())).flatMap { value => out += value; tailcall(next()) }
      tailcall(next())
    def forall[A](values: Iterable[A])(f: A => TailRec[Boolean]): TailRec[Boolean] =
      val it = values.iterator
      def next(): TailRec[Boolean] =
        if !it.hasNext then done(true)
        else tailcall(f(it.next())).flatMap(ok => if ok then tailcall(next()) else done(false))
      tailcall(next())
    def groups(values: Set[P]): Vector[(P, Set[P])] = d match
      case finite: FiniteDomain[P] @unchecked =>
        val children = new Array[collection.mutable.Set[P]](finite.arity)
        var mask = BigInt(0)
        values.foreach { p =>
          if d.length(p) > 0 then
            val digit = finite.firstDigit(p)
            if children(digit) == null then
              children(digit) = collection.mutable.Set.empty[P]
              mask = mask.setBit(digit)
            children(digit) += d.drop(p, 1)
        }
        val out = Vector.newBuilder[(P, Set[P])]
        while mask != 0 do
          val digit = mask.lowestSetBit
          out += finite.single(digit) -> children(digit).toSet
          mask = mask.clearBit(digit)
        out.result().sortWith((a, b) => d.compare(a._1, b._1) < 0)
      case _ =>
        values.iterator.filter(d.length(_) > 0).toVector.groupMap(p => d.take(p, 1))(p => d.drop(p, 1))
          .iterator.map((h, t) => h -> t.toSet).toVector.sortWith((a, b) => d.compare(a._1, b._1) < 0)
    def run(s: AlphabetSpace[P], pc: Map[PathRef, P], sc: Map[SpaceMention, Set[P]]): TailRec[Set[P]] = tailcall {
      def path(p: AlphabetPath[P]): TailRec[P] = tailcall { p match
        case AlphabetPath.Deref(r) => done(pc(r))
        case AlphabetPath.Constant(v) => d.length(v); done(v)
        case AlphabetPath.Concat(a, b) => path(a).flatMap(x => path(b).map(d.concat(x, _)))
        case AlphabetPath.GroundedPP(p, f) => path(p).map(f)
        case AlphabetPath.GroundedSP(s, f) => run(s, pc, sc).map(f)
      }
      def rec(x: AlphabetSpace[P]): TailRec[Set[P]] = run(x, pc, sc)
      def binary(a: AlphabetSpace[P], b: AlphabetSpace[P])(f: (Set[P], Set[P]) => Set[P]): TailRec[Set[P]] =
        rec(a).flatMap(x => rec(b).map(y => f(x, y)))
      def bindPath(r: PathRef, p: P): Map[PathRef, P] = if r.s == "_" then pc else pc.updated(r, p)
      def bindSpace(r: SpaceMention, s: Set[P]): Map[SpaceMention, Set[P]] = if r.s == "_" then sc else sc.updated(r, s)
      s match
        case Empty() => done(Set.empty)
        case Call(r, ps, ss) =>
          val f = routines(r)
          require(ps.size == f.refs.size && ss.size == f.mentions.size, s"arity mismatch: ${r.s}")
          sequence(ps)(path).flatMap { pValues => sequence(ss)(rec).flatMap { sValues =>
            val nextPC = (f.refs zip pValues).toMap
            val nextSC = (f.mentions zip sValues).toMap
            // Same stable self-union convention as the symbol evaluator.
            f.body match
              case Union(left, Call(`r`, `ps`, `ss`)) =>
                forall(ps zip pValues)((p, v) => run(Singleton(p), nextPC, nextSC).map(_ == Set(v))).flatMap { stablePaths =>
                  if !stablePaths then run(f.body, nextPC, nextSC)
                  else forall(ss zip sValues)((s, v) => run(s, nextPC, nextSC).map(_ == v)).flatMap { stableSpaces =>
                    run(if stableSpaces then left else f.body, nextPC, nextSC)
                  }
                }
              case body => run(body, nextPC, nextSC)
          }}
        case Mention(r) => done(sc(r))
        case Singleton(p) => path(p).map(Set(_))
        case Literal(ps) => ps.foreach(d.length); done(ps)
        case Union(a, b) => binary(a, b)((x, y) => if x.size >= y.size then x union y else y union x)
        case Intersection(a, b) => binary(a, b)(_ intersect _)
        case Subtraction(a, b) => binary(a, b)(_ diff _)
        case Restriction(a, b) => rec(b).flatMap(prefixes => rec(a).map(_.filter(p => prefixes.exists(d.startsWith(p, _)))))
        case Raffination(a, b) => rec(b).flatMap(prefixes => rec(a).map(_.filterNot(p => prefixes.exists(d.startsWith(p, _)))))
        case Composition(a, b) => rec(a).flatMap { as =>
          if as.isEmpty then done(Set.empty)
          else rec(b).map(bs => as.flatMap(x => bs.map(d.concat(x, _))))
        }
        case Wrap(src, p) => path(p).flatMap(prefix => rec(src).map(_.map(d.concat(prefix, _))))
        case Unwrap(src, p) => path(p).flatMap(prefix => rec(src).map(_.filter(d.startsWith(_, prefix)).map(d.drop(_, d.length(prefix)))))
        case TailsUnion(src) => rec(src).map(_.filter(d.length(_) > 0).map(d.drop(_, 1)))
        case TailsIntersection(src) => rec(src).map(values => groups(values).map(_._2).reduceOption(_ intersect _).getOrElse(Set.empty))
        case PrefixClosure(src) => rec(src).map(_.flatMap(p => (1 to d.length(p)).map(d.take(p, _))))
        case SuffixClosure(src) => rec(src).map(_.flatMap(p => (0 until d.length(p)).map(d.drop(p, _))))
        case TailsClosure(src) => rec(src).map(_.flatMap(p => (0 to d.length(p)).map(d.drop(p, _))))
        case Iteration(src, h, _, Singleton(AlphabetPath.Deref(ref))) if h == ref && h.s != "_" => rec(src).map { values =>
          d match
            case finite: FiniteDomain[P] @unchecked => finite.heads(values)
            case _ => values.iterator.filter(d.length(_) > 0).map(d.take(_, 1)).toSet
        }
        case Iteration(src, h, t, body) => rec(src).flatMap { values =>
          sequence(groups(values))((p, s) => run(body, bindPath(h, p), bindSpace(t, s))).map(_.flatten.toSet)
        }
        case Fold(src, initial, acc, h, t, body, update) => path(initial).flatMap { initialValue => rec(src).flatMap { values =>
          val it = groups(values).iterator
          val out = Set.newBuilder[P]
          def loop(a: P): TailRec[Set[P]] =
            if !it.hasNext then done(out.result())
            else
              val (p, s) = it.next()
              val innerPC = (if acc.s == "_" then pc else pc.updated(acc, a)) ++ (if h.s == "_" then Map.empty else Map(h -> p))
              val innerSC = bindSpace(t, s)
              run(body, innerPC, innerSC).flatMap { result =>
                out ++= result
                run(Singleton(update), innerPC, innerSC).flatMap(next => tailcall(loop(next.head)))
              }
          tailcall(loop(initialValue))
        }}
        case Fixpoint(initial, v, step) => rec(initial).flatMap { first =>
          def loop(current: Set[P]): TailRec[Set[P]] = run(step, pc, bindSpace(v, current)).flatMap { result =>
            val next = current union result
            if next == current then done(current) else tailcall(loop(next))
          }
          tailcall(loop(first))
        }
        case GroundedPS(p, f) => path(p).map(f)
        case GroundedSS(s, f) => rec(s).map(f)
        case Range(src, start, end) => rec(src).map { paths =>
          val (lo, hi) = RangeBounds.normalize(paths.size, start, end)
          if lo >= hi then Set.empty
          else if lo == 0 && hi == paths.size then paths
          else if lo == 0 && hi == 1 then Set(paths.reduce((a, b) => if d.compare(a, b) <= 0 then a else b))
          else if lo == paths.size - 1 then Set(paths.reduce((a, b) => if d.compare(a, b) >= 0 then a else b))
          else paths.toVector.sortWith(d.compare(_, _) < 0).slice(lo, hi).toSet
        }
    }
    run(expression, paths, spaces).result

type BitSpace = AlphabetSpace[BigInt]
type BitPath = AlphabetPath[BigInt]
type ByteSpace = AlphabetSpace[BytePath]
type BytePathExpr = AlphabetPath[BytePath]

object AlphabetSpace:
  def Head[P](src: AlphabetSpace[P]): AlphabetSpace[P] =
    val h = PathRef("#head").known(1)
    Iteration(src, h, SpaceMention("_"), Singleton(AlphabetPath.Deref(h)))
