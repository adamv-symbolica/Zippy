package morkl

import scala.collection.immutable.{AbstractSet, IntMap}

/** Persistent 256-way byte trie. Membership, focus and Head use the trie;
  * the child mask is derived from its actual children, never symbol metadata.
  */
final case class ByteTrie private (terminal: Boolean, children: IntMap[ByteTrie]):
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
  def slice(lo: Int, hi: Int): ByteTrie = ByteTrie.evaluate(ByteTrie.Slice(this, lo, hi))
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
  def union(that: ByteTrie): ByteTrie = ByteTrie.evaluate(ByteTrie.Merge(this, that, 0))
  def intersect(that: ByteTrie): ByteTrie = ByteTrie.evaluate(ByteTrie.Merge(this, that, 1))
  def subtract(that: ByteTrie): ByteTrie = ByteTrie.evaluate(ByteTrie.Merge(this, that, 2))
  def restrict(prefixes: ByteTrie, keep: Boolean): ByteTrie =
    ByteTrie.evaluate(ByteTrie.Merge(this, prefixes, if keep then 3 else 4))
  def compose(that: ByteTrie): ByteTrie = ByteTrie.evaluate(ByteTrie.Compose(this, that))
  def iterator: Iterator[BytePathValue] = new Iterator[BytePathValue]:
    private def edges(node: ByteTrie): Iterator[(Int, ByteTrie)] =
      if node.children.size <= 1 then node.children.iterator
      else node.children.iterator.toVector.sortBy(_._1).iterator
    private val pending = scala.collection.mutable.Stack(edges(ByteTrie.this))
    private val bytes = scala.collection.mutable.ArrayBuffer.empty[Byte]
    private var ready: Option[BytePathValue] = Option.when(terminal)(ByteWords.empty)
    private def advance(): Unit =
      while ready.isEmpty && pending.nonEmpty do
        if pending.top.hasNext then
          val (b, child) = pending.top.next()
          bytes += b.toByte
          pending.push(edges(child))
          if child.terminal then ready = Some(BytePathValue(bytes.toArray))
        else
          pending.pop()
          if pending.nonEmpty then bytes.remove(bytes.size - 1)
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
  // All algebra uses this postorder work stack. No operation consumes a JVM
  // frame per byte, including when composition merges its terminal suffix.
  private sealed trait Work
  private case class Merge(a: ByteTrie, b: ByteTrie, op: Int) extends Work
  private case class Slice(a: ByteTrie, lo: Int, hi: Int) extends Work
  private case class Compose(a: ByteTrie, b: ByteTrie) extends Work
  private case class Build(terminal: Boolean, keys: Vector[Int], suffix: Option[ByteTrie] = None) extends Work
  private def evaluate(initial: Work): ByteTrie =
    val pending = scala.collection.mutable.Stack(initial)
    val results = scala.collection.mutable.ArrayBuffer.empty[ByteTrie]
    def schedule(terminal: Boolean, jobs: Vector[(Int, Work)], suffix: Option[ByteTrie] = None): Unit =
      pending.push(Build(terminal, jobs.map(_._1), suffix))
      jobs.reverseIterator.foreach((_, job) => pending.push(job))
    while pending.nonEmpty do
      pending.pop() match
        case Build(terminal, keys, suffix) =>
          val start = results.size - keys.size
          val built = node(terminal, keys.indices.iterator.map(i => keys(i) -> results(start + i)))
          results.dropRightInPlace(keys.size)
          results += suffix.fold(built)(built.union)
        case Compose(a, b) =>
          if a.isEmpty || b.isEmpty then results += empty
          else schedule(false, a.children.iterator.map((k, c) => k -> Compose(c, b)).toVector,
            Option.when(a.terminal)(b))
        case Slice(a, lo, hi) =>
          if lo <= 0 && hi >= a.pathCount then results += a
          else if hi <= lo || hi <= 0 || lo >= a.pathCount then results += empty
          else
            var offset = if a.terminal then 1 else 0
            val jobs = a.children.iterator.toVector.sortBy(_._1).flatMap { (k, child) =>
              val start = offset; offset += child.pathCount
              Option.when(hi > start && lo < offset)(k -> Slice(child, (lo - start).max(0), (hi - start).min(child.pathCount)))
            }
            schedule(a.terminal && lo <= 0 && hi > 0, jobs)
        case Merge(a, b, op) =>
          val quick: Option[ByteTrie] = op match
            case 0 => if a eq b then Some(a) else if a.isEmpty then Some(b) else if b.isEmpty then Some(a) else None
            case 1 => if a eq b then Some(a) else if a.isEmpty || b.isEmpty then Some(empty) else None
            case 2 => if a eq b then Some(empty) else if a.isEmpty || b.isEmpty then Some(a) else None
            case 3 => if a.isEmpty || b.isEmpty then Some(empty) else if b.terminal then Some(a) else None
            case 4 => if a.isEmpty || b.terminal then Some(empty) else if b.isEmpty then Some(a) else None
          quick match
            case Some(value) => results += value
            case None =>
              val terminal = op match
                case 0 => a.terminal || b.terminal
                case 1 => a.terminal && b.terminal
                case 2 => a.terminal && !b.terminal
                case 3 => false
                case 4 => a.terminal
              val keys = if op == 0 then (a.children.keySet ++ b.children.keySet).toVector else a.children.keysIterator.toVector
              schedule(terminal, keys.map(k => k -> Merge(a.children.getOrElse(k, empty), b.children.getOrElse(k, empty), op)))
    results.head
  def fromPaths(paths: Iterable[BytePathValue]): ByteTrie = paths.foldLeft(empty)(_.insert(_))

private[morkl] final class ByteTrieSet(val trie: ByteTrie) extends AbstractSet[BytePathValue]:
  def contains(p: BytePathValue): Boolean = trie.contains(p)
  def iterator: Iterator[BytePathValue] = trie.iterator
  def incl(p: BytePathValue): Set[BytePathValue] = new ByteTrieSet(trie.insert(p))
  def excl(p: BytePathValue): Set[BytePathValue] = new ByteTrieSet(trie.subtract(ByteTrie.fromPaths(Vector(p))))
  override def isEmpty: Boolean = trie.isEmpty
  override def size: Int = trie.pathCount

private[morkl] final class ByteTrieBackend extends WordSetBackend[BytePathValue](ByteWords):
  private def tree(values: Set[BytePathValue]): ByteTrie = values match
    case t: ByteTrieSet => t.trie
    case _ => ByteTrie.fromPaths(values)
  private def set(t: ByteTrie): Set[BytePathValue] = new ByteTrieSet(t)
  override def singleton(word: BytePathValue): Set[BytePathValue] = set(ByteTrie.empty.insert(word))
  override def literal(values: Set[BytePathValue]): Set[BytePathValue] = from(values)
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
