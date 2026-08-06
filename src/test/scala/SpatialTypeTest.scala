import munit.FunSuite
import morkl.Syntax.{*, given}

class SpatialTypeTest extends FunSuite:
  private val noPaths = PathContextMap(Map.empty)
  private val noRoutines = PartialFunction.empty[RoutinePtr, Routine]

  private def path(length: Int, prefix: String): PathValue =
    PathValue(List.tabulate(length)(index => PathItem(s"${prefix}_$index")))

  test("spatial restriction retains compatible length strata and prefix witnesses") {
    val xs = SpaceMention("xs")
    val ys = SpaceMention("ys")
    val k = PathRef("k")
    val xsType = SpatialType.lengths(
      2 -> ResultSizeEstimate.exact(SizeExpr.const(5)),
      4 -> ResultSizeEstimate.exact(SizeExpr.const(7)),
      6 -> ResultSizeEstimate.exact(SizeExpr.const(9))
    )
    val assumptions = SpatialAssumptions(
      spaces = Map(xs -> xsType, ys -> SpatialType.empty),
      paths = Map(k -> SpatialPathType.length(3, "k")),
      prefixCoverage = Set(SpatialPrefixCoverage(k, xs, Set(4, 6)))
    )
    val expression = Space.TailsUnion(Space.Restriction(
      Space.Mention(xs),
      Space.Union(Space.Mention(ys), Space.Singleton(Path.Deref(k)))
    ))

    val result = SpatialTypeAnalysis.output(expression, assumptions).collapseByLength
    assertEquals(result.map(_.exactLength), Vector(Some(3), Some(5)))
    assertEquals(result.map(_.cardinality.lower), Vector(SizeExpr.One, SizeExpr.One))
    assertEquals(result.map(_.cardinality.upper), Vector(SizeExpr.const(7), SizeExpr.const(9)))

    val spatial = SpatialTypeAnalysis.output(expression, assumptions)
    assertEquals(spatial.size.lower, SizeExpr.const(2))
    assertEquals(spatial.size.upper, SizeExpr.const(16))
    val lengthPc = PathContextMap(Map(k -> PathValue(List("a", "b", "c").map(PathItem(_)))))
    assertEquals(spatial.pathLength.lower.evaluate(using lengthPc, SpaceContextMap(Map.empty), noRoutines), Some(BigInt(3)))
    assertEquals(spatial.pathLength.upper.evaluate(using lengthPc, SpaceContextMap(Map.empty), noRoutines), Some(BigInt(5)))

    val kValue = PathValue(List("a", "b", "c").map(PathItem(_)))
    val values = SpaceValue(
      (Vector.tabulate(5)(index => path(2, s"two_$index")) ++
       Vector(PathValue(kValue.items :+ PathItem("four"))) ++
       Vector.tabulate(6)(index => path(4, s"four_$index")) ++
       Vector(PathValue(kValue.items ++ List(PathItem("six_0"), PathItem("six_1"), PathItem("six_2")))) ++
       Vector.tabulate(8)(index => path(6, s"six_$index"))).toSet
    )
    given PathContext = PathContextMap(Map(k -> kValue))
    given SpaceContext = SpaceContextMap(Map(xs -> values, ys -> SpaceValue()))
    val actual = eval(expression)
    assertEquals(actual.paths.map(_.items.length), Set(3, 5))
    assert(spatial.size.lower.evaluate.exists(_ <= actual.paths.size))
    assert(spatial.size.upper.evaluate.exists(_ >= actual.paths.size))
  }

  test("encoded if-empty control has a piecewise exact spatial type") {
    val source = SpaceMention("control_source")
    val fallback = SpaceMention("control_fallback")
    val sourceCount = SizeExpr.symbol("S")
    val fallbackCount = SizeExpr.symbol("G")
    val assumptions = SpatialAssumptions(spaces = Map(
      source -> SpatialType.lengths(2 -> ResultSizeEstimate.exact(sourceCount)),
      fallback -> SpatialType.lengths(4 -> ResultSizeEstimate.exact(fallbackCount))
    ))
    val expression = Space.Union(Space.Mention(source), Space.Mention(source).on_empty(Space.Mention(fallback)))
    val result = SpatialTypeAnalysis.output(expression, assumptions)

    assertEquals(result.size.lower, SizeExpr.ifZero(sourceCount, fallbackCount, sourceCount))
    assertEquals(result.size.upper, SizeExpr.ifZero(sourceCount, fallbackCount, sourceCount))
    assertEquals(result.strataAt(2).head.cardinality.upper, SizeExpr.ifZero(sourceCount, SizeExpr.Zero, sourceCount))
    assertEquals(result.strataAt(4).head.cardinality.upper, SizeExpr.ifZero(sourceCount, fallbackCount, SizeExpr.Zero))

    Vector(
      SpaceValue() -> SpaceValue(path(4, "g0"), path(4, "g1")),
      SpaceValue(path(2, "s0"), path(2, "s1"), path(2, "s2")) -> SpaceValue(path(4, "ignored"))
    ).foreach { (sourceValue, fallbackValue) =>
      given PathContext = noPaths
      given SpaceContext = SpaceContextMap(Map(source -> sourceValue, fallback -> fallbackValue))
      given PartialFunction[RoutinePtr, Routine] = noRoutines
      val concreteAssumptions = SpatialAssumptions(spaces = Map(source -> SpatialType.exact(sourceValue), fallback -> SpatialType.exact(fallbackValue)))
      val concreteType = SpatialTypeAnalysis.output(expression, concreteAssumptions)
      val actual = eval(expression)
      assertEquals(concreteType.size.lower.evaluate, Some(BigInt(actual.paths.size)))
      assertEquals(concreteType.size.upper.evaluate, Some(BigInt(actual.paths.size)))
    }
  }

  test("iteration counts constant-headed fibers as groups rather than paths") {
    val source = SpaceMention("constant_head_source")
    val head = PathRef("constant_head")
    val rest = SpaceMention("constant_tail")
    val count = SizeExpr.symbol("N")
    val sourceType = SpatialType.fromStrata(Vector(SpatialStratum(
      PathLengthEstimate.exact(PathLengthExpr.const(2)),
      ResultSizeEstimate.exact(count),
      Some(SpatialPattern(Vector(
        SpatialItem.Constant(PathItem("edge")),
        SpatialItem.Unknown("target"),
      ))),
    )))
    val expression = Space.Iteration(
      Space.Mention(source), head, rest, Space.Singleton(Path.Deref(head)))
    val result = SpatialTypeAnalysis.output(expression, SpatialAssumptions(spaces = Map(source -> sourceType)))

    assertEquals(result.size, ResultSizeEstimate.exact(SizeExpr.positive(count)))
    assertEquals(result.pathLength, PathLengthEstimate.exact(PathLengthExpr.One))
  }

  test("nested full-path iterator chains are bounded as pointwise maps") {
    val source = SpaceMention("pointwise_source")
    val x = PathRef("x")
    val xs = SpaceMention("xs")
    val y = PathRef("y")
    val ys = SpaceMention("ys")
    val count = SizeExpr.symbol("N")
    val sourceType = SpatialType.fromStrata(Vector(SpatialStratum(
      PathLengthEstimate.exact(PathLengthExpr.const(2)),
      ResultSizeEstimate.exact(count),
      Some(SpatialPattern(Vector(SpatialItem.Unknown("x"), SpatialItem.Unknown("y")))),
    )))
    val leaf = Space.Union(
      Space.Singleton(Path.Constant(Syntax.parse("cux")) x Path.Deref(x)),
      Space.Singleton(Path.Constant(Syntax.parse("cux")) x Path.Deref(y)),
    )
    val expression = Space.Iteration(
      Space.Mention(source), x, xs,
      Space.Iteration(Space.Mention(xs), y, ys, leaf),
    )
    val result = SpatialTypeAnalysis.output(expression, SpatialAssumptions(spaces = Map(source -> sourceType)))

    val pointwiseCap = SizeExpr.multiply(count, SizeExpr.const(2))
    result.size.upper match
      case SizeExpr.Minimum(terms) => assert(terms.contains(pointwiseCap))
      case value => assertEquals(value, pointwiseCap)
    assertEquals(result.pathLength, PathLengthEstimate.exact(PathLengthExpr.const(2)))
  }

  test("pure Game-of-Life neighbor arithmetic has eight length-two outputs") {
    val coordinate = LifeExample.neigh.refs.head
    val result = SpatialTypeAnalysis.outputRoutine(
      LifeExample.neigh,
      pathInputs = Map(coordinate -> SpatialPathType.numericPair("coord", -64, 64)),
      spaceInputs = Map.empty
    )
    val lengths = result.collapseByLength
    assertEquals(result.size, ResultSizeEstimate.exact(SizeExpr.const(8)))
    assertEquals(result.pathLength, PathLengthEstimate.exact(PathLengthExpr.const(2)))
    assertEquals(lengths.map(_.exactLength), Vector(Some(2)))
    assertEquals(lengths.head.cardinality, ResultSizeEstimate.exact(SizeExpr.const(8)))
    assertEquals(result.strata.flatMap(_.pattern).map(_.show).toSet.size, 8)
  }

  test("spatial types propagate through five Game-of-Life steps without result feedback") {
    val field = SpaceValue("Cell.0.1", "Cell.1.2", "Cell.2.0", "Cell.2.1", "Cell.2.2")
    val fieldMention = SpaceMention("initial_glider")
    val fiveSteps = (1 to 5).foldLeft[Space](Space.Mention(fieldMention)) { (current, _) =>
      LifeExample.nextStep.name(current)
    }
    given PathContext = noPaths
    given SpaceContext = SpaceContextMap(Map(fieldMention -> field))
    given PartialFunction[RoutinePtr, Routine] = mod(LifeExample.neigh, LifeExample.nextStep)

    val outputType = SpatialTypeAnalysis.output(
      fiveSteps,
      SpatialAssumptions(spaces = Map(fieldMention -> SpatialType.exact(field))),
      summon[PartialFunction[RoutinePtr, Routine]],
    )
    // Evaluation is deliberately downstream: it validates but cannot feed the
    // annotation or any intermediate abstract step.
    val actual = eval(fiveSteps)
    assertEquals(outputType.exactValue, None)
    assert(outputType.size.lower.annotatedBound(Z3BoundDirection.Lower).exists(_ <= actual.paths.size))
    assert(outputType.size.upper.annotatedBound(Z3BoundDirection.Upper).forall(_ >= actual.paths.size))
    assertEquals(outputType.pathLength, PathLengthEstimate.exact(PathLengthExpr.const(3)))
    assertEquals(actual.paths.size, 5)
  }

  test("exact spatial types expose graph fiber degree statistics") {
    val graph = SpatialType.exact(SpaceValue("edge.a.b", "edge.a.c", "edge.b.c"))
    val degree = graph.fiberDegree(prefixLength = 2)
    assertEquals(degree.minimum, ResultSizeEstimate.exact(SizeExpr.One))
    assertEquals(degree.maximum, ResultSizeEstimate.exact(SizeExpr.const(2)))
    assertEquals(degree.edges, ResultSizeEstimate.exact(SizeExpr.const(3)))
    assertEquals(degree.keys, ResultSizeEstimate.exact(SizeExpr.const(2)))
    assertEquals(degree.averageShow, "3..3 / 2..2")
  }

  test("spatial projections refine scalar size and path-length projections") {
    val x = SpaceMention("projection_x")
    val value = SpaceValue(path(2, "a"), path(4, "b"), path(4, "c"), path(6, "d"))
    val expressions = Vector[Space](
      Space.Mention(x),
      Space.Union(Space.Mention(x), Space.Singleton(Path.ZERO)),
      Space.Intersection(Space.Mention(x), Space.Literal(value)),
      Space.Subtraction(Space.Mention(x), Space.Singleton(Path.Constant(path(2, "a")))),
      Space.Composition(Space.TailsUnion(Space.Mention(x)), Space.Singleton(Path.Constant(path(2, "tail")))),
      Space.PrefixClosure(Space.Mention(x)),
      Space.Range(Space.Mention(x), 1, 3)
    )
    given PathContext = noPaths
    given SpaceContext = SpaceContextMap(Map(x -> value))
    given PartialFunction[RoutinePtr, Routine] = noRoutines
    val assumptions = SpatialAssumptions(spaces = Map(x -> SpatialType.exact(value)))

    expressions.foreach { expression =>
      def finite(value: Option[BigInt], label: String): BigInt =
        value.getOrElse(fail(s"$label was unbounded for ${expression.show}"))
      val spatial = SpatialTypeAnalysis.output(expression, assumptions)
      val size = ResultSpaceSize.estimate(expression, assumptions.sizeAssumptions)
      val length = ResultPathLength.estimate(expression, assumptions.lengthAssumptions,
        assumptions.pathLengthAssumptions, assumptions.sizeAssumptions)
      val actual = eval(expression)
      val spatialLower = finite(spatial.size.lower.evaluate, "spatial size lower")
      val spatialUpper = finite(spatial.size.upper.evaluate, "spatial size upper")
      assert(spatialLower <= actual.paths.size && spatialUpper >= actual.paths.size, expression.show)
      size.lower.evaluate.foreach(bound => assert(spatialLower >= bound, expression.show))
      size.upper.evaluate.foreach(bound => assert(spatialUpper <= bound, expression.show))
      if actual.paths.nonEmpty then
        val actualMin = BigInt(actual.paths.map(_.items.length).min)
        val actualMax = BigInt(actual.paths.map(_.items.length).max)
        val spatialMin = finite(spatial.pathLength.lower.evaluate, "spatial length lower")
        val spatialMax = finite(spatial.pathLength.upper.evaluate, "spatial length upper")
        assert(spatialMin <= actualMin && spatialMax >= actualMax, expression.show)
        length.lower.evaluate.foreach(bound => assert(spatialMin >= bound, expression.show))
        length.upper.evaluate.foreach(bound => assert(spatialMax <= bound, expression.show))
    }
  }
