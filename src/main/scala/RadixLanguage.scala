package morkl

import scala.util.control.TailCalls.{TailRec, done, tailcall}

/** Storage operations for a finite digit alphabet. */
trait DigitPaths[V]:
  def alphabetSize: Int
  def empty: V
  def fromDigits(digits: Iterable[Int]): V
  def digits(path: V): Vector[Int]
  def length(path: V): Int = digits(path).length
  def concat(left: V, right: V): V = fromDigits(digits(left) ++ digits(right))
  def take(path: V, n: Int): V = fromDigits(digits(path).take(n))
  def drop(path: V, n: Int): V = fromDigits(digits(path).drop(n))
  def startsWith(path: V, prefix: V): Boolean = digits(path).startsWith(digits(prefix))
  def compare(left: V, right: V): Int =
    val a = digits(left)
    val b = digits(right)
    a.zip(b).iterator.map((x, y) => x.compare(y)).find(_ != 0).getOrElse(a.length.compare(b.length))

/** Positive big integers with a leading sentinel: epsilon=1, 0=2, 1=3.
  * The sentinel preserves leading zeroes and is never a language digit.
  */
object BitPaths extends DigitPaths[BigInt]:
  val alphabetSize = 2
  val empty: BigInt = 1
  def fromDigits(digits: Iterable[Int]): BigInt = digits.foldLeft(empty) { (p, d) =>
    require(d == 0 || d == 1, s"not a bit: $d")
    (p << 1) | BigInt(d)
  }
  override def length(path: BigInt): Int =
    require(path > 0, "bit paths need a positive sentinel")
    path.bitLength - 1
  def digits(path: BigInt): Vector[Int] =
    val n = length(path)
    Vector.tabulate(n)(i => if path.testBit(n - i - 1) then 1 else 0)
  override def concat(left: BigInt, right: BigInt): BigInt =
    length(left)
    val n = length(right)
    (left << n) | right.clearBit(n)
  override def take(path: BigInt, n: Int): BigInt = path >> (length(path) - n.max(0).min(length(path)))
  override def drop(path: BigInt, n: Int): BigInt =
    val remaining = length(path) - n.max(0).min(length(path))
    (path & ((BigInt(1) << remaining) - 1)).setBit(remaining)
  override def startsWith(path: BigInt, prefix: BigInt): Boolean =
    length(path) >= length(prefix) && take(path, length(prefix)) == prefix

/** Immutable byte array with structural equality; array access always copies. */
final class BytePathValue private (private val data: Array[Byte]):
  def bytes: Array[Byte] = data.clone()
  def unsigned: Vector[Int] = data.iterator.map(_ & 255).toVector
  def length: Int = data.length
  override def equals(other: Any): Boolean = other match
    case that: BytePathValue => java.util.Arrays.equals(data, that.data)
    case _ => false
  override def hashCode(): Int = java.util.Arrays.hashCode(data)
  override def toString: String = unsigned.mkString("BytePath(", ",", ")")

object BytePathValue:
  def apply(bytes: Array[Byte]): BytePathValue = new BytePathValue(bytes.clone())
  def apply(bytes: Int*): BytePathValue = BytePaths.fromDigits(bytes)

object BytePaths extends DigitPaths[BytePathValue]:
  val alphabetSize = 256
  val empty: BytePathValue = BytePathValue(Array.emptyByteArray)
  def fromDigits(digits: Iterable[Int]): BytePathValue =
    BytePathValue(digits.iterator.map { d =>
      require(d >= 0 && d < 256, s"not a byte: $d")
      d.toByte
    }.toArray)
  def digits(path: BytePathValue): Vector[Int] = path.unsigned
  override def length(path: BytePathValue): Int = path.length

/** Exactly the cases of morkl.Path/Space, with a different path alphabet.
  * Encodings and their boundary automata belong to the compiler, not this AST.
  */
class DigitLanguage[V](val paths: DigitPaths[V]):
  type PathValue = V
  case class SpaceValue(paths: Set[V])
  enum Path:
    case Deref(pr: PathRef)
    case Constant(pi: V)
    case Concat(l: Path, r: Path)
    case GroundedPP(p: Path, f: V => V)
    case GroundedSP(p: Space, f: SpaceValue => V)
  object Path:
    val ZERO: Path = Constant(paths.empty)
  enum Space:
    case Empty
    case Call(r: RoutinePtr, refs: Vector[Path], mentions: Vector[Space])
    case Mention(variable: SpaceMention)
    case Singleton(p: Path)
    case Literal(p: SpaceValue)
    case Union(x: Space, y: Space)
    case Intersection(x: Space, y: Space)
    case Subtraction(x: Space, y: Space)
    case Restriction(x: Space, y: Space)
    case Raffination(x: Space, y: Space)
    case Composition(x: Space, y: Space)
    case Iteration(src: Space, symbol: PathRef, rest: SpaceMention, templates: Space)
    case Fold(src: Space, initial: Path, acc: PathRef, symbol: PathRef, rest: SpaceMention, templates: Space, update: Path)
    case Fixpoint(initial: Space, variable: SpaceMention, step: Space)
    case Wrap(src: Space, p: Path)
    case Unwrap(src: Space, p: Path)
    case TailsUnion(src: Space)
    case TailsIntersection(src: Space)
    case PrefixClosure(src: Space)
    case SuffixClosure(src: Space)
    case TailsClosure(src: Space)
    case GroundedPS(p: Path, f: V => SpaceValue)
    case GroundedSS(p: Space, f: SpaceValue => SpaceValue)
    case Range(x: Space, start: Int, end: Int)
  object Space:
    def Head(src: Space): Space =
      val h = PathRef("__radix_head").known(1)
      Iteration(src, h, SpaceMention("_"), Singleton(Path.Deref(h)))
    val headCardinalityBound: Int = paths.alphabetSize
  case class Routine(name: RoutinePtr, refs: Vector[PathRef], mentions: Vector[SpaceMention], body: Space)
  case class Program(entry: Space, routines: Map[RoutinePtr, Routine] = Map.empty):
    def eval(pathArgs: Map[PathRef, V] = Map.empty, spaceArgs: Map[SpaceMention, SpaceValue] = Map.empty): SpaceValue =
      DigitLanguage.this.eval(entry, pathArgs, spaceArgs, routines)
  def literal(values: V*): Space = Space.Literal(SpaceValue(values.toSet))
  def digit(value: Int): Path = Path.Constant(paths.fromDigits(Vector(value)))
  val epsilon: Space = Space.Singleton(Path.ZERO)
  def headMask(value: SpaceValue): BigInt = value.paths.foldLeft(BigInt(0)) { (mask, p) =>
    if paths.length(p) == 0 then mask else mask.setBit(paths.digits(p).head)
  }
  def eval(expression: Space,
           pathArgs: Map[PathRef, V] = Map.empty,
           spaceArgs: Map[SpaceMention, SpaceValue] = Map.empty,
           routines: PartialFunction[RoutinePtr, Routine] = PartialFunction.empty): SpaceValue =
    def bind[K, A](env: Map[K, A], key: K, name: String, value: A): Map[K, A] =
      if name == "_" then env else env.updated(key, value)
    // Both path and space continuations are trampolined: generated code may
    // nest digit iterations and sequential folds much deeper than its source.
    def sequence[A, B](values: Vector[A])(f: A => TailRec[B]): TailRec[Vector[B]] =
      values.foldLeft(done(Vector.empty[B])) { (acc, value) =>
        for previous <- acc; next <- tailcall(f(value)) yield previous :+ next
      }
    def p(x: Path, pc: Map[PathRef, V], sc: Map[SpaceMention, SpaceValue]): TailRec[V] = tailcall {
      x match
        case Path.Deref(ref) => done(pc(ref))
        case Path.Constant(value) => done(value)
        case Path.Concat(a, b) => for left <- p(a, pc, sc); right <- p(b, pc, sc) yield paths.concat(left, right)
        case Path.GroundedPP(a, f) => p(a, pc, sc).map(f)
        case Path.GroundedSP(a, f) => s(a, pc, sc).map(v => f(SpaceValue(v)))
    }
    def groups(value: Set[V]): Vector[(V, Set[V])] =
      value.iterator.filter(paths.length(_) > 0).toVector
        .groupMap(paths.take(_, 1))(paths.drop(_, 1)).toVector
        .sortWith((a, b) => paths.compare(a._1, b._1) < 0).map((h, t) => h -> t.toSet)
    def s(x: Space, pc: Map[PathRef, V], sc: Map[SpaceMention, SpaceValue]): TailRec[Set[V]] = tailcall {
      x match
        case Space.Empty => done(Set.empty)
        case Space.Call(r, refs, mentions) =>
          val fn = routines(r)
          require(refs.size == fn.refs.size && mentions.size == fn.mentions.size, s"arity mismatch: ${r.s}")
          for
            pv <- sequence(refs)(p(_, pc, sc))
            sv <- sequence(mentions)(m => s(m, pc, sc).map(SpaceValue(_)))
            value <-
              val nextP = (fn.refs zip pv).toMap
              val nextS = (fn.mentions zip sv).toMap
              fn.body match
                case Space.Union(left, Space.Call(`r`, `refs`, `mentions`)) =>
                  for
                    nextPV <- sequence(refs)(p(_, nextP, nextS))
                    nextSV <- sequence(mentions)(m => s(m, nextP, nextS).map(SpaceValue(_)))
                    result <- s(if nextPV == pv && nextSV == sv then left else fn.body, nextP, nextS)
                  yield result
                case other => s(other, nextP, nextS)
          yield value
        case Space.Mention(ref) => done(sc(ref).paths)
        case Space.Singleton(value) => p(value, pc, sc).map(Set(_))
        case Space.Literal(value) => done(value.paths)
        case Space.Union(a, b) => for left <- s(a, pc, sc); right <- s(b, pc, sc) yield left union right
        case Space.Intersection(a, b) => for left <- s(a, pc, sc); right <- s(b, pc, sc) yield left intersect right
        case Space.Subtraction(a, b) => for left <- s(a, pc, sc); right <- s(b, pc, sc) yield left diff right
        case Space.Restriction(a, b) =>
          for prefixes <- s(b, pc, sc); values <- s(a, pc, sc)
          yield values.filter(v => prefixes.exists(paths.startsWith(v, _)))
        case Space.Raffination(a, b) => s(Space.Subtraction(a, Space.Restriction(a, b)), pc, sc)
        case Space.Composition(a, b) =>
          s(a, pc, sc).flatMap { left =>
            if left.isEmpty then done(Set.empty)
            else s(b, pc, sc).map(right => for l <- left; r <- right yield paths.concat(l, r))
          }
        case Space.Wrap(a, prefix) =>
          for pref <- p(prefix, pc, sc); values <- s(a, pc, sc) yield values.map(paths.concat(pref, _))
        case Space.Unwrap(a, prefix) =>
          for pref <- p(prefix, pc, sc); values <- s(a, pc, sc)
          yield values.filter(paths.startsWith(_, pref)).map(paths.drop(_, paths.length(pref)))
        case Space.TailsUnion(a) => s(a, pc, sc).map(v => groups(v).flatMap(_._2).toSet)
        case Space.TailsIntersection(a) => s(a, pc, sc).map(v => groups(v).map(_._2).reduceOption(_ intersect _).getOrElse(Set.empty))
        case Space.PrefixClosure(a) => s(a, pc, sc).map(_.flatMap(v => (1 to paths.length(v)).map(paths.take(v, _))))
        case Space.SuffixClosure(a) => s(a, pc, sc).map(_.flatMap(v => (0 until paths.length(v)).map(paths.drop(v, _))))
        case Space.TailsClosure(a) => s(a, pc, sc).map(_.flatMap(v => (0 to paths.length(v)).map(paths.drop(v, _))))
        case Space.Iteration(a, h, t, body) =>
          s(a, pc, sc).flatMap { values =>
            sequence(groups(values)) { (head, tail) =>
              s(body, bind(pc, h, h.s, head), bind(sc, t, t.s, SpaceValue(tail)))
            }.map(_.foldLeft(Set.empty[V])(_ union _))
          }
        case Space.Fold(a, initial, acc, h, t, body, update) =>
          for
            initialValue <- p(initial, pc, sc)
            values <- s(a, pc, sc)
            result <-
              val ordered = groups(values)
              val out = Set.newBuilder[V]
              def loop(index: Int, current: V): TailRec[Set[V]] = tailcall {
                if index == ordered.size then done(out.result())
                else
                  val (head, tail) = ordered(index)
                  val nextP = bind(bind(pc, acc, acc.s, current), h, h.s, head)
                  val nextS = bind(sc, t, t.s, SpaceValue(tail))
                  for
                    result <- s(body, nextP, nextS)
                    next <- p(update, nextP, nextS)
                    rest <-
                      out ++= result
                      loop(index + 1, next)
                  yield rest
              }
              loop(0, initialValue)
          yield result
        case Space.Fixpoint(initial, variable, step) =>
          def loop(current: Set[V]): TailRec[Set[V]] = tailcall {
            s(step, pc, bind(sc, variable, variable.s, SpaceValue(current))).flatMap { result =>
              val next = current union result
              if next == current then done(current) else loop(next)
            }
          }
          s(initial, pc, sc).flatMap(loop)
        case Space.GroundedPS(a, f) => p(a, pc, sc).map(v => f(v).paths)
        case Space.GroundedSS(a, f) => s(a, pc, sc).map(v => f(SpaceValue(v)).paths)
        case Space.Range(a, start, end) => s(a, pc, sc).map { values =>
          val ordered = values.toVector.sortWith(paths.compare(_, _) < 0)
          val (lo, hi) = RangeBounds.normalize(ordered.size, start, end)
          ordered.slice(lo, hi).toSet
        }
    }
    SpaceValue(s(expression, pathArgs, spaceArgs).result)

object Zippy_bits extends DigitLanguage[BigInt](BitPaths)
object Zippy_bytes extends DigitLanguage[BytePathValue](BytePaths)
type BitPath = Zippy_bits.Path
val BitPath = Zippy_bits.Path
type BitSpace = Zippy_bits.Space
val BitSpace = Zippy_bits.Space
type BitSpaceValue = Zippy_bits.SpaceValue
val BitSpaceValue = Zippy_bits.SpaceValue
type BytePath = Zippy_bytes.Path
val BytePath = Zippy_bytes.Path
type ByteSpace = Zippy_bytes.Space
val ByteSpace = Zippy_bytes.Space
type ByteSpaceValue = Zippy_bytes.SpaceValue
val ByteSpaceValue = Zippy_bytes.SpaceValue

/** Focus is relative to a fixed root and may be absent, as in PathMapModel.Zip. */
case class ByteZipper(trie: ByteSpaceValue,
                      root: BytePathValue = BytePaths.empty,
                      path: BytePathValue = BytePaths.empty):
  def originPath: BytePathValue = BytePaths.concat(root, path)
  def descendTo(relative: BytePathValue): ByteZipper = copy(path = BytePaths.concat(path, relative))
  def ascend(count: Int): (ByteZipper, Int) =
    require(count >= 0)
    val moved = count.min(path.length)
    copy(path = BytePaths.take(path, path.length - moved)) -> moved
  def focus: ByteSpaceValue = ByteSpaceValue(trie.paths.filter(BytePaths.startsWith(_, originPath))
    .map(BytePaths.drop(_, originPath.length)))
  def pathExists: Boolean = focus.paths.nonEmpty
  def isTerminal: Boolean = trie.paths(originPath)
  def childMask: BigInt = Zippy_bytes.headMask(focus)
  def descendFirstByte: Option[(ByteZipper, Int)] =
    val mask = childMask
    Option.when(mask != 0) {
      val d = mask.lowestSetBit
      descendTo(BytePathValue(d)) -> d
    }
