package morkl

import munit.FunSuite
import scala.collection.mutable

object SpatialCostCorrelation:
  private def value(prefix: String, count: Int, length: Int = 2): SpaceValue = SpaceValue(
    (0 until count).map { index =>
      PathValue((Vector(prefix + index) ++ (1 until length).map(i => s"t$i")).map(PathItem(_)).toList)
    }.toSet)

  /** Scaling correlation is measured within one executor operation family;
    * cross-operator discrimination is checked separately by the winner cases. */
  private def examples: Vector[Space] = Vector(16, 32, 64, 96).map { count =>
    val left = Space.Literal(value("l", count))
    val right = Space.Literal(value("r", count))
    Space.Composition(left, right)
  }

  private def graphValue(graph: RecursiveOpGraph): SpaceValue =
    val stack = mutable.Stack(new Array[List[Int] | TrieSpace | Null](graph.nodes.length))
    execT(graph, stack)
    stack.top.last.asInstanceOf[TrieSpace].toSpaceValue

  private def medianNanos(run: () => SpaceValue, repetitions: Int = 3): Double =
    run()
    Vector.fill(repetitions) {
      val started = System.nanoTime()
      run()
      (System.nanoTime() - started).toDouble
    }.sorted.apply(repetitions / 2)

  private def rank(values: Vector[Double]): Vector[Double] =
    values.map(value =>
      val below = values.count(_ < value)
      val equal = values.count(_ == value)
      below + (equal - 1) / 2.0)

  private def spearman(left: Vector[Double], right: Vector[Double]): Double =
    val x = rank(left)
    val y = rank(right)
    val xm = x.sum / x.size
    val ym = y.sum / y.size
    val numerator = x.zip(y).map((a, b) => (a - xm) * (b - ym)).sum
    val denominator = math.sqrt(x.map(a => (a - xm) * (a - xm)).sum * y.map(b => (b - ym) * (b - ym)).sum)
    if denominator == 0 then 0 else numerator / denominator

  def correlations: Map[SpatialBackend, Double] =
    val rows = examples.map { expression =>
      val spatial = SpatialTypeAnalysis.output(expression)
      val graph = transpile(Routine(RoutinePtr("cost_correlation"), Vector.empty, Vector.empty, expression))
      val runs: Map[SpatialBackend, () => SpaceValue] = Map(
        SpatialBackend.Reference -> (() => eval(expression)),
        SpatialBackend.Trie -> (() => evalTrieValue(expression)),
        SpatialBackend.Zipper -> (() => evalZValue(expression)),
        SpatialBackend.Graph -> (() => graphValue(graph)),
      )
      SpatialBackend.values.map { backend =>
        val predicted = spatial.cost.forBackend(backend).workUpper.annotatedBound(Z3BoundDirection.Upper)
          .getOrElse(BigInt(Long.MaxValue)).toDouble
        backend -> (predicted -> medianNanos(runs(backend)))
      }.toMap
    }
    SpatialBackend.values.map { backend =>
      val values = rows.map(_(backend))
      backend -> spearman(values.map(_._1), values.map(_._2))
    }.toMap

@main def spatialCostCorrelationReport(): Unit =
  SpatialCostCorrelation.correlations.toVector.sortBy(_._1.ordinal).foreach { (backend, correlation) =>
    println(f"spatial cost correlation: $backend%-9s Spearman=$correlation%.3f")
  }

class SpatialCostCorrelationTest extends FunSuite:
  test("cost predictions correlate with every executor and models each win") {
    val correlations = SpatialCostCorrelation.correlations
    correlations.foreach { (backend, correlation) =>
      assert(correlation >= 0.25, s"$backend Spearman correlation $correlation was too weak")
    }

    def winner(kind: SpatialCostOperation, left: SpatialType, right: Option[SpatialType], result: SpatialType): SpatialBackend =
      val inputs = Vector(left) ++ right.toVector
      val measures = inputs.map(SpatialCostMeasure.apply)
      val fallback = SpatialCostInterval(SizeExpr.Zero, left.size.upper, SizeExpr.Zero, result.size.upper)
      SpatialCostModels.all.minBy(_.operation(kind, measures, SpatialCostMeasure(result), fallback)
        .workUpper.annotatedBound(Z3BoundDirection.Upper).getOrElse(BigInt(Long.MaxValue))).backend

    val manyLong = SpatialType.lengths(5 -> ResultSizeEstimate.exact(SizeExpr.const(100)))
    val manyShort = SpatialType.lengths(1 -> ResultSizeEstimate.exact(SizeExpr.const(100)))
    val oneShort = SpatialType.lengths(1 -> ResultSizeEstimate.exact(SizeExpr.One))
    val winners = Set(
      winner(SpatialCostOperation.Intersection, manyLong, Some(manyLong), manyLong),
      winner(SpatialCostOperation.Restriction, manyShort, Some(manyShort), manyShort),
      winner(SpatialCostOperation.Unwrap, manyLong, None, oneShort),
      winner(SpatialCostOperation.Union, manyLong, Some(manyLong), manyLong),
    )
    assertEquals(winners, SpatialBackend.values.toSet)
  }
