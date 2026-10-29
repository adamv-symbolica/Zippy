package morkl

import munit.FunSuite
import scala.concurrent.duration.*

class ByteTrieDeepTest extends FunSuite:
  override val munitTimeout = 300.seconds
  private def smallStack(body: => Unit): Unit =
    val failure = new java.util.concurrent.atomic.AtomicReference[Throwable]()
    val thread = new Thread(null, () => try body catch { case t: Throwable => failure.set(t) }, "byte-trie-small-stack", 512 * 1024)
    thread.start(); thread.join()
    if failure.get() != null then throw failure.get()
  private def valid(root: ByteTrie): Unit =
    val pending = scala.collection.mutable.Stack(root)
    while pending.nonEmpty do
      val node = pending.pop()
      node.children.foreach { (b, child) =>
        assert(b >= 0 && b < 256 && !child.isEmpty)
        pending.push(child)
      }

  test("100000-byte overlapping trie algebra runs on a 512 KiB thread") {
    smallStack {
      val q = BytePathValue(Array.fill[Byte](100000)(7))
      val one = BytePathValue(Array[Byte](1)); val two = BytePathValue(Array[Byte](2))
      val q1 = ByteWords.concat(q, one); val q2 = ByteWords.concat(q, two)
      val a = ByteTrie.fromPaths(Vector(q, q1)); val b = ByteTrie.fromPaths(Vector(q, q2))
      val results = Vector(
        a.union(b) -> Set(q, q1, q2),
        a.intersect(b) -> Set(q),
        a.subtract(b) -> Set(q1),
        a.restrict(b, true) -> Set(q, q1),
        a.restrict(b, false) -> Set.empty[BytePathValue],
        a.compose(b) -> Set(ByteWords.concat(q,q), ByteWords.concat(q,q2), ByteWords.concat(q1,q), ByteWords.concat(q1,q2)),
        a.union(b).slice(1, 3) -> Set(q1, q2))
      results.foreach { (trie, expected) =>
        valid(trie)
        assertEquals(trie.pathCount, expected.size)
        assertEquals(trie.iterator.toSet, expected)
      }
      // Traversal of disjoint prefixes and focused long subtries also stays iterative.
      assert(a.subtree(q).terminal)
    }
  }

  test("byte-trie iteration scales linearly through 320000-byte paths") {
    val samples = Vector(20000, 80000, 320000).map { n =>
      val word = BytePathValue(Array.fill[Byte](n)(9))
      val trie = ByteTrie.fromPaths(Vector(word))
      def run(): Unit =
        val it = trie.iterator
        assertEquals(it.next(), word)
        assert(!it.hasNext)
      (0 until 4).foreach(_ => run())
      val time = Vector.fill(7) { val start = System.nanoTime(); run(); (System.nanoTime()-start).toDouble / 1e6 }.sorted.apply(3)
      println(f"byte-trie iterator/$n $time%.3f ms")
      n -> time
    }
    val exponent = math.log(samples.last._2 / samples.head._2) / math.log(16)
    assert(exponent <= 1.2, s"iterator growth exponent $exponent: $samples")
  }
