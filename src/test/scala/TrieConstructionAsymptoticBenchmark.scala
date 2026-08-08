package morkl

import munit.FunSuite

object TrieConstructionAsymptoticData:
  case class Row(size: Int, wideMs: Double, deepMs: Double)

  def wide(size: Int): Vector[List[Int]] =
    Vector.tabulate(size)(index => List(index + 2, 0))

  def deep(size: Int): Vector[List[Int]] =
    val width = Integer.numberOfTrailingZeros(Integer.highestOneBit(size))
    Vector.tabulate(size) { index =>
      (0 until width).reverseIterator.map(bit => (index >>> bit) & 1).toList
    }

  private def medianMillis(paths: Vector[List[Int]]): Double =
    def run(): Double =
      val started = System.nanoTime()
      val result = TrieSpace.fromEncodedPaths(paths)
      val elapsed = (System.nanoTime() - started).toDouble / 1_000_000.0
      require(result.pathCount == paths.size)
      elapsed
    Vector.fill(3)(run())
    Vector.fill(7)(run()).sorted.apply(3)

  def rows: Vector[Row] = Vector(256, 1024, 4096, 16384).map { size =>
    Row(size, medianMillis(wide(size)), medianMillis(deep(size)))
  }

@main def trieConstructionAsymptoticBenchmark(): Unit =
  println("size,wide_ms,deep_ms")
  TrieConstructionAsymptoticData.rows.foreach { row =>
    println(f"${row.size},${row.wideMs}%.3f,${row.deepMs}%.3f")
  }

class TrieConstructionAsymptoticTest extends FunSuite:
  test("persistent insertion derives aggregates without rescanning wide parents") {
    Vector(256, 1024, 4096, 16384).foreach { size =>
      val (wide, wideCost) = ExecutorCostMeter.measure(
        TrieSpace.fromEncodedPaths(TrieConstructionAsymptoticData.wide(size)))
      assertEquals(wide.pathCount, size)
      assertEquals(wideCost.nodeVisits, 0L,
        s"wide construction rescanned a completed child map at size $size")
      assertEquals(wideCost.allocations, size.toLong * 3L)

      val deepPaths = TrieConstructionAsymptoticData.deep(size)
      val (deep, deepCost) = ExecutorCostMeter.measure(TrieSpace.fromEncodedPaths(deepPaths))
      assertEquals(deep.pathCount, size)
      assertEquals(deepCost.nodeVisits, 0L,
        s"deep construction rescanned a completed child map at size $size")
      assertEquals(deepCost.allocations, size.toLong * (deepPaths.head.length + 1L))
    }
  }

  test("bulk join scans once and zipper insertion inherits delta construction") {
    val size = 4096
    val paths = TrieConstructionAsymptoticData.wide(size)
    val singletons = paths.map(path => TrieSpace.fromEncodedPaths(Vector(path)))
    val (joined, joinCost) = ExecutorCostMeter.measure(TrieSpace.joinAll(singletons))
    assertEquals(joined.pathCount, size)
    assertEquals(joinCost.nodeVisits, size.toLong)
    assertEquals(joinCost.allocations, 1L)

    val (zipper, zipperCost) = ExecutorCostMeter.measure {
      paths.foldLeft(TrieSpace.Zipper(TrieSpace.empty))((cursor, path) => cursor.insertItemsAtFocus(path))
    }
    assertEquals(zipper.whole.pathCount, size)
    assertEquals(zipperCost.nodeVisits, 0L)
    assertEquals(zipperCost.allocations, size.toLong * 3L)
  }
