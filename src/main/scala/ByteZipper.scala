package morkl

/** Read-only reference cursor over the byte-array set backend. Like the
  * PathMapModel Zip, root is absolute, path is relative to root, movement may
  * visit absent locations, and ascend cannot escape the root. This is a view
  * of the same reference backend, not a new operation in Zippy_bytes.
  */
case class ByteZipper(space: ByteSpaceValue, root: BytePathValue = ByteWords.empty,
                      path: BytePathValue = ByteWords.empty):
  def focus: BytePathValue = ByteWords.concat(root, path)
  def atRoot: Boolean = path.length == 0
  def focusByte: Option[Int] = path.unsigned.lastOption
  def isVal: Boolean = space.paths(focus)
  def pathExists: Boolean = space.paths.exists(ByteWords.startsWith(_, focus))
  def makeMap: ByteSpaceValue =
    val prefix = focus
    ByteSpaceValue(space.paths.filter(ByteWords.startsWith(_, prefix)).map(ByteWords.drop(_, prefix.length)))
  /** get_focus excludes the value at the current focus in the Lean model. */
  def focusNode: ByteSpaceValue = ByteSpaceValue(makeMap.paths - ByteWords.empty)
  def childMask: BigInt = Zippy_bytes.headMask(makeMap)
  def childCount: Int = childMask.bitCount
  def moveToPath(relative: BytePathValue): ByteZipper = copy(path = relative)
  def descend(bytes: BytePathValue): ByteZipper = copy(path = ByteWords.concat(path, bytes))
  def descendByte(byte: Int): ByteZipper = descend(ByteWords.fromDigits(Vector(byte)))
  def ascend(count: Int = 1): (ByteZipper, Int) =
    require(count >= 0)
    val moved = count.min(path.length)
    copy(path = ByteWords.take(path, path.length - moved)) -> moved
  def descendIndexedByte(index: Int): Option[(ByteZipper, Int)] =
    require(index >= 0)
    val mask = childMask
    (0 until 256).iterator.filter(mask.testBit).drop(index).nextOption().map(b => descendByte(b) -> b)
