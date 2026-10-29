package morkl

import morkl.finite.{Storage, Evaluator}

/** A leading sentinel 1 encodes length, so epsilon=1, 0=2, 1=3,
  * 00=4. Every positive BigInt denotes exactly one bit path. */
final case class BitSpace(paths: Set[BigInt]):
  require(paths.forall(_ > 0), "bit paths must contain a leading sentinel")
  def headMask: Int = paths.iterator.filter(_.bitLength > 1).foldLeft(0) { (mask, p) =>
    mask | (if p.testBit(p.bitLength - 2) then 2 else 1)
  }
  def head: Set[Boolean] = Set(false, true).filter(b => (headMask & (if b then 2 else 1)) != 0)
  def vectors: Set[Vector[Boolean]] = paths.map(BitStorage.items)

object BitSpace:
  val empty: BitSpace = BitSpace(Set.empty)
  def fromPaths(paths: Iterable[Vector[Boolean]]): BitSpace = BitSpace(paths.iterator.map(BitStorage.path).toSet)
  val evaluator = Evaluator(BitStorage)

object BitStorage extends Storage[Boolean, BigInt, BitSpace]:
  def emptyPath: BigInt = BigInt(1)
  def path(items: Vector[Boolean]): BigInt = items.foldLeft(BigInt(1))((p, b) => (p << 1) | (if b then BigInt(1) else BigInt(0)))
  def items(p: BigInt): Vector[Boolean] =
    require(p > 0, "bit paths must contain a leading sentinel")
    Vector.tabulate(length(p))(i => p.testBit(length(p) - i - 1))
  def length(p: BigInt): Int = p.bitLength - 1
  def concat(a: BigInt, b: BigInt): BigInt = (a << length(b)) | b.clearBit(length(b))
  def take(p: BigInt, n: Int): BigInt = p >> (length(p) - n.max(0).min(length(p)))
  def drop(p: BigInt, n: Int): BigInt =
    val remain = length(p) - n.max(0).min(length(p))
    (p & ((BigInt(1) << remain) - 1)).setBit(remain)
  def startsWith(p: BigInt, prefix: BigInt): Boolean = length(p) >= length(prefix) && take(p, length(prefix)) == prefix
  def compare(a: BigInt, b: BigInt): Int =
    val shared = length(a).min(length(b))
    val c = take(a, shared).compare(take(b, shared))
    if c != 0 then c else length(a).compare(length(b))
  def empty: BitSpace = BitSpace.empty
  def paths(v: BitSpace): Set[BigInt] = v.paths
  def value(ps: Set[BigInt]): BitSpace = BitSpace(ps)

/** Immutable content-equal byte array. Constructors and accessors copy, so
  * arrays cannot invalidate a set's hash buckets. Ordering is unsigned. */
final class BytePath private (private val data: Array[Byte]):
  def toArray: Array[Byte] = data.clone()
  def unsigned: Vector[Int] = data.iterator.map(_ & 255).toVector
  def length: Int = data.length
  override def equals(other: Any): Boolean = other match
    case p: BytePath => java.util.Arrays.equals(data, p.data)
    case _ => false
  override def hashCode: Int = java.util.Arrays.hashCode(data)
  override def toString: String = unsigned.mkString("BytePath(", ",", ")")

object BytePath:
  def apply(bytes: Array[Byte]): BytePath = new BytePath(bytes.clone())
  def unsigned(items: Iterable[Int]): BytePath =
    val xs = items.toVector
    require(xs.forall(x => x >= 0 && x < 256), "byte out of range")
    new BytePath(xs.map(_.toByte).toArray)

final case class ByteSpace(paths: Set[BytePath]):
  /** A bounded 256-bit child mask, independent of the subspace cardinality. */
  def headMask: BigInt = paths.iterator.filter(_.length > 0).foldLeft(BigInt(0))((m, p) => m.setBit(p.unsigned.head))
  def head: Set[Int] = (0 until 256).filter(headMask.testBit).toSet
  def vectors: Set[Vector[Int]] = paths.map(_.unsigned)

object ByteSpace:
  val empty: ByteSpace = ByteSpace(Set.empty)
  def fromPaths(paths: Iterable[Vector[Int]]): ByteSpace = ByteSpace(paths.iterator.map(BytePath.unsigned).toSet)
  def fromArrays(paths: Iterable[Array[Byte]]): ByteSpace = ByteSpace(paths.iterator.map(BytePath(_)).toSet)
  val evaluator = Evaluator(ByteStorage)

object ByteStorage extends Storage[Int, BytePath, ByteSpace]:
  def emptyPath: BytePath = BytePath.unsigned(Vector.empty)
  def path(items: Vector[Int]): BytePath = BytePath.unsigned(items)
  def items(p: BytePath): Vector[Int] = p.unsigned
  def concat(a: BytePath, b: BytePath): BytePath = BytePath(a.toArray ++ b.toArray)
  def length(p: BytePath): Int = p.length
  def take(p: BytePath, n: Int): BytePath = BytePath(p.toArray.take(n))
  def drop(p: BytePath, n: Int): BytePath = BytePath(p.toArray.drop(n))
  def startsWith(p: BytePath, prefix: BytePath): Boolean = p.unsigned.startsWith(prefix.unsigned)
  def compare(a: BytePath, b: BytePath): Int = java.util.Arrays.compareUnsigned(a.toArray, b.toArray)
  def empty: ByteSpace = ByteSpace.empty
  def paths(v: ByteSpace): Set[BytePath] = v.paths
  def value(ps: Set[BytePath]): ByteSpace = ByteSpace(ps)

/** Snapshot zipper following PathMapModel: root bounds ascent, descent may
  * move off-trie, and makeMap retains the value at the focus. */
final case class ByteZipper(trie: ByteSpace, root: BytePath = ByteStorage.emptyPath, path: BytePath = ByteStorage.emptyPath):
  def originPath: BytePath = ByteStorage.concat(root, path)
  def makeMap: ByteSpace = ByteSpace(trie.paths.filter(ByteStorage.startsWith(_, originPath)).map(ByteStorage.drop(_, originPath.length)))
  def pathExists: Boolean = makeMap.paths.nonEmpty
  def isValue: Boolean = trie.paths(originPath)
  def childMask: BigInt = makeMap.headMask
  def descendTo(segment: BytePath): ByteZipper = copy(path = ByteStorage.concat(path, segment))
  def ascend(count: Int): (Int, ByteZipper) =
    val moved = count.max(0).min(path.length)
    moved -> copy(path = ByteStorage.take(path, path.length - moved))
  def reset: ByteZipper = copy(path = ByteStorage.emptyPath)
  def fork: ByteZipper = ByteZipper(trie, originPath)
