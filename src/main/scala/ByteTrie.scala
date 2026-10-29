package morkl

import scala.collection.immutable.{AbstractSet, IntMap}

/** Persistent 256-way byte trie. Membership, focus and Head use the trie;
  * the child mask is derived from its actual children, never symbol metadata.
  */
final case class ByteTrie private (terminal: Boolean, children: IntMap[ByteTrie]):
  lazy val childMask: BigInt = children.keysIterator.foldLeft(BigInt(0))(_.setBit(_))
  private var cachedCount = -1
  def pathCount: Int =
    if cachedCount < 0 then
      val pending = scala.collection.mutable.Stack((this, false))
      while pending.nonEmpty do
        val (node, visited) = pending.pop()
        if node.cachedCount < 0 then
          if visited then node.cachedCount = (if node.terminal then 1 else 0) + node.children.valuesIterator.map(_.cachedCount).sum
          else
            pending.push(node -> true)
            node.children.valuesIterator.filter(_.cachedCount < 0).foreach(c => pending.push(c -> false))
    cachedCount
  def slice(lo: Int, hi: Int): ByteTrie =
    if lo <= 0 && hi >= pathCount then this
    else if hi <= lo then ByteTrie.empty
    else
      var offset = if terminal then 1 else 0
      val entries = children.iterator.toVector.sortBy(_._1).iterator.flatMap { (b, child) =>
        val start = offset
        offset += child.pathCount
        if hi <= start || lo >= offset then None
        else Some(b -> child.slice((lo - start).max(0), (hi - start).min(child.pathCount)))
      }
      ByteTrie.node(terminal && lo == 0 && hi > 0, entries)
  def isEmpty: Boolean = !terminal && children.isEmpty
  def subtree(prefix: BytePathValue): ByteTrie =
    var node = this
    val bytes = prefix.toArray
    var i = 0
    while i < bytes.length && !node.isEmpty do
      node = node.children.getOrElse(bytes(i) & 255, ByteTrie.empty)
      i += 1
    node
  def contains(path: BytePathValue): Boolean = subtree(path).terminal
  def insert(path: BytePathValue): ByteTrie =
    val bytes = path.toArray
    val frames = scala.collection.mutable.ArrayBuffer.empty[(ByteTrie, Int)]
    var node = this
    bytes.foreach { b =>
      frames += node -> (b & 255)
      node = node.children.getOrElse(b & 255, ByteTrie.empty)
    }
    var result = ByteTrie(true, node.children)
    var i = frames.size - 1
    while i >= 0 do
      val (parent, b) = frames(i)
      result = ByteTrie(parent.terminal, parent.children.updated(b, result))
      i -= 1
    result
  def wrap(prefix: BytePathValue): ByteTrie =
    if isEmpty then this
    else
      val bytes = prefix.toArray
      var result = this
      var i = bytes.length - 1
      while i >= 0 do
        result = ByteTrie(false, IntMap((bytes(i) & 255) -> result))
        i -= 1
      result
  def union(that: ByteTrie): ByteTrie =
    if this eq that then this
    else if isEmpty then that
    else if that.isEmpty then this
    else
      var next = children
      that.children.foreach { (b, child) => next = next.updated(b, next.get(b).fold(child)(_.union(child))) }
      ByteTrie(terminal || that.terminal, next)
  def intersect(that: ByteTrie): ByteTrie =
    if this eq that then this
    else if isEmpty || that.isEmpty then ByteTrie.empty
    else ByteTrie.node(terminal && that.terminal, children.iterator.flatMap { (b, child) =>
      that.children.get(b).map(other => b -> child.intersect(other))
    })
  def subtract(that: ByteTrie): ByteTrie =
    if this eq that then ByteTrie.empty
    else if isEmpty || that.isEmpty then this
    else ByteTrie.node(terminal && !that.terminal, children.iterator.map { (b, child) =>
      b -> that.children.get(b).fold(child)(child.subtract)
    })
  def restrict(prefixes: ByteTrie, keep: Boolean): ByteTrie =
    if prefixes.terminal then if keep then this else ByteTrie.empty
    else if prefixes.isEmpty then if keep then ByteTrie.empty else this
    else ByteTrie.node(terminal && !keep, children.iterator.map { (b, child) =>
      b -> child.restrict(prefixes.children.getOrElse(b, ByteTrie.empty), keep)
    })
  def compose(that: ByteTrie): ByteTrie =
    if isEmpty || that.isEmpty then ByteTrie.empty
    else
      val descendants = ByteTrie.node(false, children.iterator.map((b, c) => b -> c.compose(that)))
      if terminal then descendants.union(that) else descendants
  def iterator: Iterator[BytePathValue] = new Iterator[BytePathValue]:
    private val pending = scala.collection.mutable.Stack((ByteWords.empty, ByteTrie.this))
    private var ready: Option[BytePathValue] = None
    private def advance(): Unit =
      while ready.isEmpty && pending.nonEmpty do
        val (prefix, node) = pending.pop()
        node.children.iterator.toVector.sortBy(_._1).reverseIterator.foreach { (b, child) =>
          pending.push(ByteWords.concat(prefix, ByteWords.fromDigits(Vector(b))) -> child)
        }
        if node.terminal then ready = Some(prefix)
    def hasNext: Boolean = { advance(); ready.nonEmpty }
    def next(): BytePathValue =
      advance()
      val result = ready.getOrElse(throw new NoSuchElementException("empty byte trie iterator"))
      ready = None
      result
  def toSpaceValue: ByteSpaceValue = ByteSpaceValue(iterator.toSet)

object ByteTrie:
  val empty: ByteTrie = ByteTrie(false, IntMap.empty)
  private def node(terminal: Boolean, entries: Iterator[(Int, ByteTrie)]): ByteTrie =
    ByteTrie(terminal, IntMap.from(entries.filterNot(_._2.isEmpty)))
  def fromPaths(paths: Iterable[BytePathValue]): ByteTrie = paths.foldLeft(empty)(_.insert(_))

private[morkl] final class ByteTrieSet(val trie: ByteTrie) extends AbstractSet[BytePathValue]:
  def contains(p: BytePathValue): Boolean = trie.contains(p)
  def iterator: Iterator[BytePathValue] = trie.iterator
  def incl(p: BytePathValue): Set[BytePathValue] = new ByteTrieSet(trie.insert(p))
  def excl(p: BytePathValue): Set[BytePathValue] = new ByteTrieSet(trie.subtract(ByteTrie.fromPaths(Vector(p))))
  override def isEmpty: Boolean = trie.isEmpty
  override def size: Int = trie.pathCount

private[morkl] object ByteTrieBackend extends WordSetBackend[BytePathValue](ByteWords):
  private def tree(values: Set[BytePathValue]): ByteTrie = values match
    case t: ByteTrieSet => t.trie
    case _ => ByteTrie.fromPaths(values)
  private def set(t: ByteTrie): Set[BytePathValue] = new ByteTrieSet(t)
  override def from(values: Iterable[BytePathValue]): Set[BytePathValue] = values match
    case t: ByteTrieSet => t
    case _ => set(ByteTrie.fromPaths(values))
  override def union(a: Set[BytePathValue], b: Set[BytePathValue]): Set[BytePathValue] = set(tree(a).union(tree(b)))
  override def intersect(a: Set[BytePathValue], b: Set[BytePathValue]): Set[BytePathValue] = set(tree(a).intersect(tree(b)))
  override def subtract(a: Set[BytePathValue], b: Set[BytePathValue]): Set[BytePathValue] = set(tree(a).subtract(tree(b)))
  override def groups(values: Set[BytePathValue]): Vector[(BytePathValue, Set[BytePathValue])] =
    tree(values).children.iterator.map((b, child) => ByteWords.fromDigits(Vector(b)) -> set(child)).toVector
  override def unwrap(values: Set[BytePathValue], prefix: BytePathValue): Set[BytePathValue] = set(tree(values).subtree(prefix))
  override def wrap(values: Set[BytePathValue], prefix: BytePathValue): Set[BytePathValue] = set(tree(values).wrap(prefix))
  override def restrict(a: Set[BytePathValue], b: Set[BytePathValue], keep: Boolean): Set[BytePathValue] = set(tree(a).restrict(tree(b), keep))
  override def compose(a: Set[BytePathValue], b: Set[BytePathValue]): Set[BytePathValue] = set(tree(a).compose(tree(b)))
  override def range(values: Set[BytePathValue], start: Int, end: Int): Set[BytePathValue] =
    val root = tree(values)
    val (lo, hi) = RangeBounds.normalize(root.pathCount, start, end)
    set(root.slice(lo, hi))
