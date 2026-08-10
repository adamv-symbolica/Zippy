package morkl

import munit.FunSuite
import scala.collection.mutable

object SpatialCostCounterCorpus:
  private val leftMention = SpaceMention("cost_left")
  private val rightMention = SpaceMention("cost_right")

  case class CounterCase(name: String, expression: Space, left: SpaceValue, right: SpaceValue)
  case class CounterRow(
    name: String,
    backend: SpatialBackend,
    predicted: SpatialCostComponents,
    actual: ExecutorCostCounts,
  )

  private def under(head: String, count: Int): SpaceValue = SpaceValue(
    (0 until count).map(index => PathValue(List(PathItem(head), PathItem(index.toString)))).toSet,
  )

  private def wide(prefix: String, count: Int): SpaceValue = SpaceValue(
    (0 until count).map(index => PathValue(List(PathItem(s"$prefix$index")))).toSet,
  )

  private def headedWide(prefix: String, count: Int): SpaceValue = SpaceValue(
    (0 until count).map(index => PathValue(List(PathItem(s"$prefix$index"), PathItem("value")))).toSet,
  )

  private val maxRightPrefixes = 4096
  private val prefixSource = headedWide("p", maxRightPrefixes)

  def cases: Vector[CounterCase] =
    val selective = Vector(64, 512, 4096).flatMap { count =>
      val left = under("a", count / 2).paths ++ under("b", count - count / 2).paths
      Vector(
        CounterCase(
          s"restriction-left-$count",
          Space.Restriction(Space.Mention(leftMention), Space.Mention(rightMention)),
          SpaceValue(left),
          SpaceValue(Set(PathValue(List(PathItem("a"))), PathValue(List(PathItem("b"))))),
        ),
        CounterCase(
          s"intersection-left-$count",
          Space.Intersection(Space.Mention(leftMention), Space.Mention(rightMention)),
          under("a", count),
          under("b", 8),
        ),
        CounterCase(
          s"subtraction-left-$count",
          Space.Subtraction(Space.Mention(leftMention), Space.Mention(rightMention)),
          under("a", count),
          under("b", 8),
        ),
        CounterCase(
          s"raffination-left-$count",
          Space.Raffination(Space.Mention(leftMention), Space.Mention(rightMention)),
          SpaceValue(left),
          SpaceValue(Set(PathValue(List(PathItem("a"))), PathValue(List(PathItem("b"))))),
        ),
      )
    }
    val growingRight = Vector(64, 512, 4096).map { count =>
      CounterCase(
        s"restriction-right-$count",
        Space.Restriction(Space.Mention(leftMention), Space.Mention(rightMention)),
        prefixSource,
        SpaceValue((0 until count).map(index => PathValue(List(PathItem(s"p$index")))).toSet),
      )
    }
    val products = Vector(64, 256, 1024).flatMap { count =>
      Vector(
        CounterCase(
          s"composition-left-$count",
          Space.Composition(Space.Mention(leftMention), Space.Mention(rightMention)),
          wide("l", count),
          wide("r", 4),
        ),
        CounterCase(
          s"composition-right-$count",
          Space.Composition(Space.Mention(leftMention), Space.Mention(rightMention)),
          wide("l", 4),
          wide("r", count),
        ),
      )
    }
    selective ++ growingRight ++ products

  private def optimized(value: CounterCase): Space =
    Supercompiler.normalize(value.expression).space

  private def predicted(value: CounterCase, backend: SpatialBackend): SpatialCostComponents =
    val annotations = SpatialRoutineAnnotations(spaces = Map(
      leftMention -> SpatialType.exact(value.left),
      rightMention -> SpatialType.exact(value.right),
    ))
    val routine = Routine(RoutinePtr(s"abstract_${value.name}"), Vector.empty,
      Vector(leftMention, rightMention), value.expression)
    Supercompiler.optimizedSpatialType(routine, annotations, PartialFunction.empty)
      .cost.forBackend(backend).componentsUpper

  private def actualTrie(value: CounterCase, program: Space): (TrieSpace, ExecutorCostCounts) =
    val context = TrieSpaceContextMap(Map(
      leftMention -> TrieSpace.fromSpaceValue(value.left),
      rightMention -> TrieSpace.fromSpaceValue(value.right),
    ))
    ExecutorCostMeter.measure(evalTrie(program)(using PathContext.emptyMap, context, PartialFunction.empty))

  private def actualZipper(value: CounterCase, program: Space): (TrieSpace, ExecutorCostCounts) =
    val trieContext = TrieSpaceContextMap(Map(
      leftMention -> TrieSpace.fromSpaceValue(value.left),
      rightMention -> TrieSpace.fromSpaceValue(value.right),
    ))
    given ZipperSpaceContext = ZipperSpaceContext.fromTrie(trieContext)
    ExecutorCostMeter.measure(evalZ(program))

  private def actualGraph(value: CounterCase, program: Space): (TrieSpace, ExecutorCostCounts) =
    val routine = Routine(RoutinePtr(s"counter_${value.name}"), Vector.empty,
      Vector(leftMention, rightMention), program)
    val graph = transpile(routine)
    val arguments = Vector(TrieSpace.fromSpaceValue(value.left), TrieSpace.fromSpaceValue(value.right))
    def execute(): TrieSpace =
      val frame = new Array[List[Int] | TrieSpace | Null](graph.nodes.length)
      arguments.indices.foreach(index => frame(index) = arguments(index))
      val stack = mutable.Stack(frame)
      execT(graph, stack)
      frame.last.asInstanceOf[TrieSpace]
    execute() // populate graph literal/path caches outside the measured region
    ExecutorCostMeter.measure(execute())

  def rows: Vector[CounterRow] = cases.flatMap { value =>
    val program = optimized(value)
    val trie = actualTrie(value, program)
    val zipper = actualZipper(value, program)
    val graph = actualGraph(value, program)
    require(trie._1 == zipper._1 && trie._1 == graph._1, s"${value.name}: executor disagreement")
    Vector(
      CounterRow(value.name, SpatialBackend.Trie, predicted(value, SpatialBackend.Trie), trie._2),
      CounterRow(value.name, SpatialBackend.Zipper, predicted(value, SpatialBackend.Zipper), zipper._2),
      CounterRow(value.name, SpatialBackend.Graph, predicted(value, SpatialBackend.Graph), graph._2),
    )
  }

@main def spatialCostCounterReport(): Unit =
  SpatialCostCounterCorpus.rows.foreach { row =>
    println(s"${row.name},${row.backend},predicted=${row.predicted},actual=${row.actual}")
  }

class SpatialCostCounterTest extends FunSuite:
  private def constant(expr: SizeExpr, clue: String): Long =
    expr.annotatedBound(Z3BoundDirection.Upper).filter(_.isValidLong).map(_.toLong)
      .getOrElse(fail(s"$clue was not a finite concrete bound: ${expr.show}"))

  private def bounded(name: String, predicted: Long, actual: Long): Unit =
    assert(predicted >= actual, s"$name under-predicted: predicted=$predicted actual=$actual")
    if actual > 0 then
      assert(predicted <= actual * 8,
        s"$name was asymptotically or materially loose: predicted=$predicted actual=$actual")

  test("typed costs bound asymmetric optimized executor counters without fitted residuals") {
    val rows = SpatialCostCounterCorpus.rows
    rows.foreach { row =>
      bounded(s"${row.name}/${row.backend}/nodeVisits",
        constant(row.predicted.nodeVisits, s"${row.name} node visits"), row.actual.nodeVisits)
      bounded(s"${row.name}/${row.backend}/patriciaVisits",
        constant(row.predicted.patriciaVisits, s"${row.name} Patricia visits"), row.actual.patriciaVisits)
      bounded(s"${row.name}/${row.backend}/pathComparisons",
        constant(row.predicted.pathComparisons, s"${row.name} path comparisons"), row.actual.pathComparisons)
      bounded(s"${row.name}/${row.backend}/allocations",
        constant(row.predicted.allocations, s"${row.name} allocations"), row.actual.allocations)
      bounded(s"${row.name}/${row.backend}/rounds",
        constant(row.predicted.rounds, s"${row.name} rounds"), row.actual.rounds)
    }

    def nodeCounts(prefix: String, backend: SpatialBackend): Vector[(Long, Long)] = rows
      .filter(row => row.name.startsWith(prefix) && row.backend == backend)
      .sortBy(_.name.split('-').last.toInt)
      .map(row => constant(row.predicted.nodeVisits, s"${row.name} node visits") -> row.actual.nodeVisits)

    def patriciaCounts(prefix: String, backend: SpatialBackend): Vector[(Long, Long)] = rows
      .filter(row => row.name.startsWith(prefix) && row.backend == backend)
      .sortBy(_.name.split('-').last.toInt)
      .map(row => constant(row.predicted.patriciaVisits, s"${row.name} Patricia visits") -> row.actual.patriciaVisits)

    def predictedStable(values: Vector[Long]): Boolean = values.max <= values.min * 8
    def strictlyIncreasing(values: Vector[Long]): Boolean = values.sliding(2).forall {
      case Vector(left, right) => right > left
      case _ => true
    }

    SpatialBackend.values.filterNot(_ == SpatialBackend.Reference).foreach { backend =>
      Vector("restriction-left-", "intersection-left-", "subtraction-left-",
        "raffination-left-", "composition-right-").foreach { prefix =>
        val counts = nodeCounts(prefix, backend)
        val predicted = counts.map(_._1)
        assert(predicted.max <= predicted.min * 8,
          s"$backend/$prefix predicted cost grew asymptotically with an irrelevant operand: $counts")
        assertEquals(counts.map(_._2).distinct.size, 1,
          s"$backend/$prefix actual cost changed while irrelevant operand grew: $counts")
      }
      Vector("restriction-right-", "composition-left-").foreach { prefix =>
        val counts = nodeCounts(prefix, backend)
        assert(counts.map(_._1).sliding(2).forall {
          case Vector(left, right) => right > left
          case _ => true
        }, s"$backend/$prefix predicted cost did not grow with the traversed operand: $counts")
        assert(counts.map(_._2).sliding(2).forall {
          case Vector(left, right) => right > left
          case _ => true
        }, s"$backend/$prefix actual cost did not grow with the traversed operand: $counts")
      }
      Vector("restriction-left-", "intersection-left-", "subtraction-left-",
        "raffination-left-", "composition-right-").foreach { prefix =>
        val counts = patriciaCounts(prefix, backend)
        assert(predictedStable(counts.map(_._1)),
          s"$backend/$prefix predicted Patricia cost grew with an irrelevant operand: $counts")
        assertEquals(counts.map(_._2).distinct.size, 1,
          s"$backend/$prefix actual Patricia cost changed while irrelevant operand grew: $counts")
      }
      Vector("restriction-right-", "composition-left-").foreach { prefix =>
        val counts = patriciaCounts(prefix, backend)
        assert(strictlyIncreasing(counts.map(_._1)),
          s"$backend/$prefix predicted Patricia cost did not grow with the traversed operand: $counts")
        assert(strictlyIncreasing(counts.map(_._2)),
          s"$backend/$prefix actual Patricia cost did not grow with the traversed operand: $counts")
      }
    }
  }

  test("unwrap cost is prefix-only for optimized backends") {
    val sourceMention = SpaceMention("unwrap_source")
    val prefix = PathValue(List(PathItem("a"), PathItem("b"), PathItem("c")))
    val expression = Space.Unwrap(Space.Mention(sourceMention), Path.Constant(prefix))
    val small = SpaceValue(Set(PathValue(prefix.items :+ PathItem("0"))))
    val large = SpaceValue((0 until 4096).map(index =>
      PathValue(prefix.items ++ List(PathItem(index.toString), PathItem("value")))).toSet)

    def predicted(value: SpaceValue, backend: SpatialBackend): Long =
      val result = Supercompiler.optimizedSpatialType(expression,
        SpatialAssumptions(spaces = Map(sourceMention -> SpatialType.exact(value))))
      constant(result.cost.forBackend(backend).componentsUpper.nodeVisits, s"$backend unwrap")

    def trieActual(value: SpaceValue): Long =
      val context = TrieSpaceContextMap(Map(sourceMention -> TrieSpace.fromSpaceValue(value)))
      ExecutorCostMeter.measure(evalTrie(expression)(using
        PathContext.emptyMap, context, PartialFunction.empty))._2.nodeVisits

    def zipperActual(value: SpaceValue): Long =
      given ZipperSpaceContext = ZipperSpaceContext.fromTrie(
        TrieSpaceContextMap(Map(sourceMention -> TrieSpace.fromSpaceValue(value))))
      ExecutorCostMeter.measure(evalZ(expression))._2.nodeVisits

    assertEquals(Vector(predicted(small, SpatialBackend.Trie), predicted(large, SpatialBackend.Trie)), Vector(4L, 4L))
    assertEquals(Vector(trieActual(small), trieActual(large)), Vector(4L, 4L))
    assertEquals(Vector(predicted(small, SpatialBackend.Zipper), predicted(large, SpatialBackend.Zipper)), Vector(4L, 4L))
    assertEquals(Vector(zipperActual(small), zipperActual(large)), Vector(3L, 3L))
  }

  test("iteration round bounds match every executor") {
    val sourceMention = SpaceMention("round_source")
    val rest = SpaceMention("round_rest")
    val head = PathRef("round_head")
    val source = SpaceValue(Set(
      PathValue(List(PathItem("a"), PathItem("0"))),
      PathValue(List(PathItem("b"), PathItem("0"))),
      PathValue(List(PathItem("c"), PathItem("0"))),
    ))
    val expression = Space.Iteration(Space.Mention(sourceMention), head, rest,
      Space.Singleton(Path.Concat(Path.Deref(head),
        Path.Constant(PathValue(List(PathItem("tag")))))))
    val annotations = SpatialRoutineAnnotations(spaces = Map(sourceMention -> SpatialType.exact(source)))
    val routine = Routine(RoutinePtr("round_counter"), Vector.empty, Vector(sourceMention), expression)
    val spatial = Supercompiler.optimizedSpatialType(routine, annotations, PartialFunction.empty)
    SpatialBackend.values.foreach { backend =>
      assertEquals(constant(spatial.cost.forBackend(backend).componentsUpper.rounds,
        s"$backend iteration rounds"), 3L)
    }

    val referenceContext = SpaceContextMap(Map(sourceMention -> source))
    val reference = ExecutorCostMeter.measure(eval(expression)(using
      PathContext.emptyMap, referenceContext, PartialFunction.empty))._2
    val trieSource = TrieSpace.fromSpaceValue(source)
    val trieContext = TrieSpaceContextMap(Map(sourceMention -> trieSource))
    val trie = ExecutorCostMeter.measure(evalTrie(expression)(using
      PathContext.emptyMap, trieContext, PartialFunction.empty))._2
    val zipper = {
      given ZipperSpaceContext = ZipperSpaceContext.fromTrie(trieContext)
      ExecutorCostMeter.measure(evalZ(expression).toSpaceValue)._2
    }
    val graph = transpile(Routine(RoutinePtr("round_counter_graph"), Vector.empty,
      Vector(sourceMention), expression))
    def graphRun(): TrieSpace =
      val frame = new Array[List[Int] | TrieSpace | Null](graph.nodes.length)
      frame(0) = trieSource
      val stack = mutable.Stack(frame)
      execT(graph, stack)
      frame.last.asInstanceOf[TrieSpace]
    val graphCost = ExecutorCostMeter.measure(graphRun())._2

    assertEquals(Vector(reference.rounds, trie.rounds, zipper.rounds, graphCost.rounds),
      Vector(3L, 3L, 3L, 3L))
  }

  test("reference instrumentation counts comparisons and allocated product paths") {
    val restriction = SpatialCostCounterCorpus.cases.find(_.name == "restriction-left-512").get
    val restrictionContext = SpaceContextMap(Map(
      SpaceMention("cost_left") -> restriction.left,
      SpaceMention("cost_right") -> restriction.right,
    ))
    val (_, restrictionCost) = ExecutorCostMeter.measure(eval(restriction.expression)(using
      PathContext.emptyMap, restrictionContext, PartialFunction.empty))
    assert(restrictionCost.pathComparisons > 0)

    val composition = SpatialCostCounterCorpus.cases.find(_.name == "composition-left-256").get
    val compositionContext = SpaceContextMap(Map(
      SpaceMention("cost_left") -> composition.left,
      SpaceMention("cost_right") -> composition.right,
    ))
    val (_, compositionCost) = ExecutorCostMeter.measure(eval(composition.expression)(using
      PathContext.emptyMap, compositionContext, PartialFunction.empty))
    assertEquals(compositionCost.allocations, 256L * 4L)
  }
