package morkl

/** A read-only path-set projection of PathMapModel.Zip: root confinement,
  * arbitrary off-trie focus, terminal-preserving subspaces, unsigned byte masks.
  * Bare/dangling PathMap locations have no representation in a path set. */
case class ByteZipper(space: Set[BytePath], root: BytePath = BytePaths.empty, path: BytePath = BytePaths.empty):
  def originPath: BytePath = BytePaths.concat(root, path)
  def atRoot: Boolean = path.length == 0
  def isValue: Boolean = space.contains(originPath)
  def pathExists: Boolean = space.exists(BytePaths.startsWith(_, originPath))
  def makeSpace: Set[BytePath] = space.filter(BytePaths.startsWith(_, originPath)).map(BytePaths.drop(_, originPath.length))
  def childMask: BigInt = ByteSpace.headMask(makeSpace)
  def childCount: Int = childMask.bitCount
  def focusByte: Option[Int] = path.unsigned.lastOption
  def descendTo(suffix: BytePath): ByteZipper = copy(path = BytePaths.concat(path, suffix))
  def descendByte(byte: Int): ByteZipper = descendTo(BytePath(byte))
  def ascend(count: Int = 1): (Int, ByteZipper) =
    require(count >= 0, "negative ascent")
    val moved = count.min(path.length)
    moved -> copy(path = BytePaths.take(path, path.length - moved))
  def reset: ByteZipper = copy(path = BytePaths.empty)
  def descendIndexedByte(index: Int): Option[(Int, ByteZipper)] =
    val children = (0 until 256).filter(childMask.testBit)
    children.lift(index).map(b => b -> descendByte(b))
  def nextSiblingByte: Option[(Int, ByteZipper)] =
    if atRoot then None
    else
      val parent = ascend()._2
      (focusByte.get + 1 until 256).find(parent.childMask.testBit).map(b => b -> parent.descendByte(b))
  def previousSiblingByte: Option[(Int, ByteZipper)] =
    if atRoot then None
    else
      val parent = ascend()._2
      (focusByte.get - 1 to 0 by -1).find(parent.childMask.testBit).map(b => b -> parent.descendByte(b))
