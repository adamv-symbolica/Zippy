package morkl

import munit.FunSuite
import scala.collection.mutable
import java.lang.management.ManagementFactory

object SpatialCostCorrelation:
  case class MagnitudeFit(scaleNanosPerUnit: Double, maximumResidualFactor: Double,
    residualFactors: Vector[(String, Double)], observations: Vector[(String, Double, Double)])

  private def value(prefix: String, count: Int, length: Int = 2): SpaceValue = SpaceValue(
    (0 until count).map { index =>
      PathValue((Vector(prefix + index) ++ (1 until length).map(i => s"t$i")).map(PathItem(_)).toList)
    }.toSet)

  /** Scaling correlation is measured within one executor operation family;
    * cross-operator discrimination is checked separately by the winner cases. */
  private def examples: Vector[Space] = Vector(32, 64, 128, 256).map { count =>
    val left = Space.Literal(value("l", count))
    val right = Space.Literal(value("r", count))
    Space.Composition(left, right)
  }

  private def graphValue(graph: RecursiveOpGraph): SpaceValue =
    val stack = mutable.Stack(new Array[List[Int] | TrieSpace | Null](graph.nodes.length))
    execT(graph, stack)
    stack.top.last.asInstanceOf[TrieSpace].toSpaceValue

  private val threadClock = ManagementFactory.getThreadMXBean
  private def executorNanos(): Long =
    if threadClock.isCurrentThreadCpuTimeSupported && threadClock.isThreadCpuTimeEnabled then
      threadClock.getCurrentThreadCpuTime
    else System.nanoTime()

  private def medianNanos(run: () => SpaceValue, repetitions: Int = 3, warmups: Int = 3): Double =
    // Warm the exact executor/operation pair before sampling. The mixed corpus
    // uses a longer warmup so operations are compared at the same JIT maturity.
    Vector.fill(warmups)(run())
    Vector.fill(repetitions) {
      // Current-thread CPU time excludes unrelated scheduler, JIT, and stop-the-
      // world pauses. The fallback remains wall time on a JVM without that clock.
      val started = executorNanos()
      run()
      (executorNanos() - started).toDouble
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
        backend -> (predicted -> medianNanos(runs(backend), repetitions = 5))
      }.toMap
    }
    SpatialBackend.values.map { backend =>
      val values = rows.map(_(backend))
      backend -> spearman(values.map(_._1), values.map(_._2))
    }.toMap

  /** Mixed-operation calibration corpus. One multiplicative unit conversion is
    * fitted per backend; the residual is therefore sensitive to relative
    * magnitude errors that a rank correlation cannot observe. */
  def magnitudeFits: Map[SpatialBackend, MagnitudeFit] =
    val count = 96
    val left = Space.Literal(value("m", count))
    val right = Space.Literal(value("n", count))
    val prefixes = Space.Literal(SpaceValue((0 until count).map(index =>
      PathValue(List(PathItem(s"m$index")))).toSet))
    val expressions = Vector(
      "union" -> Space.Union(left, right),
      "restriction" -> Space.Restriction(left, prefixes),
      "composition" -> Space.Composition(left, right),
      "range" -> Space.Range(left, 1, count / 2),
    )
    val rows = expressions.map { (label, expression) =>
      val spatial = SpatialTypeAnalysis.output(expression)
      val graph = transpile(Routine(RoutinePtr(s"cost_magnitude_$label"), Vector.empty, Vector.empty, expression))
      val runs: Map[SpatialBackend, () => SpaceValue] = Map(
        SpatialBackend.Reference -> (() => eval(expression)),
        SpatialBackend.Trie -> (() => evalTrieValue(expression)),
        SpatialBackend.Zipper -> (() => evalZValue(expression)),
        SpatialBackend.Graph -> (() => graphValue(graph)),
      )
      SpatialBackend.values.map { backend =>
        val predicted = spatial.cost.forBackend(backend).workUpper
          .annotatedBound(Z3BoundDirection.Upper).getOrElse(BigInt(Long.MaxValue)).toDouble.max(1.0)
        backend -> (label, predicted, medianNanos(runs(backend), repetitions = 9, warmups = 16).max(1.0))
      }.toMap
    }
    SpatialBackend.values.map { backend =>
      val values = rows.map(_(backend))
      val logScale = values.map((_, predicted, measured) => math.log(measured / predicted)).sum / values.size
      val scale = math.exp(logScale)
      val residuals = values.map { (label, predicted, measured) =>
        val ratio = measured / (scale * predicted)
        label -> ratio.max(1.0 / ratio)
      }
      backend -> MagnitudeFit(scale, residuals.map(_._2).max, residuals,
        values.map((label, predicted, measured) => (label, predicted, measured)))
    }.toMap

@main def spatialCostCorrelationReport(): Unit =
  SpatialCostCorrelation.correlations.toVector.sortBy(_._1.ordinal).foreach { (backend, correlation) =>
    println(f"spatial cost correlation: $backend%-9s Spearman=$correlation%.3f")
  }
  SpatialCostCorrelation.magnitudeFits.toVector.sortBy(_._1.ordinal).foreach { (backend, fit) =>
    val residuals = fit.residualFactors.map((name, factor) => f"$name=$factor%.2fx").mkString(",")
    println(f"spatial cost magnitude: $backend%-9s scale=${fit.scaleNanosPerUnit}%.3f ns/unit maxResidual=${fit.maximumResidualFactor}%.2fx {$residuals}")
    fit.observations.foreach { (name, predicted, measured) =>
      println(f"  $name%-12s predicted=$predicted%.0f measured=${measured / 1_000_000.0}%.4f ms")
    }
  }

class SpatialCostCorrelationTest extends FunSuite:
  test("cost predictions correlate with every executor and models each win") {
    val correlations = SpatialCostCorrelation.correlations
    correlations.foreach { (backend, correlation) =>
      assert(correlation >= 0.25, s"$backend Spearman correlation $correlation was too weak")
    }
    SpatialCostCorrelation.magnitudeFits.foreach { (backend, fit) =>
      assert(fit.maximumResidualFactor <= 4.0,
        s"$backend calibrated cost magnitude residual ${fit.maximumResidualFactor} exceeded 4x: " +
          s"residuals=${fit.residualFactors}, observations=${fit.observations}")
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
