import munit.FunSuite
import morkl.Syntax.{*, given}

class ResultSpaceSizeTest extends FunSuite:
  private val emptyPathContext = PathContextMap(Map.empty)
  private val emptyRoutines = PartialFunction.empty[RoutinePtr, Routine]

  private def evaluated(expression: SizeExpr, context: SpaceContext): BigInt =
    expression.evaluate(using emptyPathContext, context, emptyRoutines).getOrElse(
      fail(s"expected a finite expression, got ${expression.show}")
    )

  test("symbolic cardinality rules preserve useful path-set bounds") {
    val left = S"left"
    val right = S"right"
    val l = SizeExpr.sizeOf(left)
    val r = SizeExpr.sizeOf(right)

    assertEquals(
      ResultSpaceSize.estimate(left \/ right),
      ResultSizeEstimate(SizeExpr.add(l, r), SizeExpr.maximum(l, r))
    )
    assertEquals(
      ResultSpaceSize.estimate(left /\ right),
      ResultSizeEstimate(SizeExpr.minimum(l, r), SizeExpr.Zero)
    )
    assertEquals(
      ResultSpaceSize.estimate(left \ right),
      ResultSizeEstimate(l, SizeExpr.positiveDifference(l, r))
    )
    assertEquals(
      ResultSpaceSize.estimate(left <| right),
      ResultSizeEstimate(l, SizeExpr.Zero)
    )
    assertEquals(ResultSpaceSize.estimate(left \/ left), ResultSizeEstimate.exact(l))
    assertEquals(ResultSpaceSize.estimate(left /\ left), ResultSizeEstimate.exact(l))
    assertEquals(ResultSpaceSize.estimate(left \ left), ResultSizeEstimate.empty)

    val product = ResultSpaceSize.estimate(left x right)
    assertEquals(product.upper, SizeExpr.multiply(l, r))
    assertEquals(
      product.lower,
      SizeExpr.maximum(
        SizeExpr.multiply(l, SizeExpr.positive(r)),
        SizeExpr.multiply(r, SizeExpr.positive(l))
      )
    )

    // Concatenation by one fixed path is injective and therefore exact.
    assertEquals(ResultSpaceSize.estimate("prefix" x left), ResultSizeEstimate.exact(l))
    val suffix = Space.Singleton(Path.Constant(Syntax.parse("suffix")))
    assertEquals(ResultSpaceSize.estimate(left x suffix), ResultSizeEstimate.exact(l))

    val prefix = Path.Constant(Syntax.parse("a"))
    val unwrapped = Space.Unwrap(left, prefix)
    assertEquals(
      ResultSpaceSize.estimate(unwrapped),
      ResultSizeEstimate.exact(SizeExpr.sizeOf(unwrapped))
    )
    assertEquals(
      ResultSpaceSize.estimate(Space.TailsUnion(left)),
      ResultSizeEstimate(l, SizeExpr.Zero)
    )
    assertEquals(
      ResultSpaceSize.estimate(Space.Range(left, 0, 2)),
      ResultSizeEstimate.exact(SizeExpr.range(l, 0, 2))
    )
    assertEquals(Supercompiler.resultSize(left \/ right), ResultSpaceSize.estimate(left \/ right))

    val opaqueCall = Space.Call(RoutinePtr("unknown"), Vector.empty, Vector(left))
    assertEquals(
      ResultSpaceSize.estimate(opaqueCall),
      ResultSizeEstimate.exact(SizeExpr.sizeOf(opaqueCall))
    )
  }

  test("empty-control iteration has the expected exact symbolic size") {
    val source = S"s"
    val fallback = S"g"
    val sentinel = Path.Constant(Syntax.parse("E"))
    val innerHead = PathRef("h").known(1)
    val innerTail = SpaceMention("inner_tail")
    val outerHead = PathRef("ignored_head").known(1)
    val outerTail = SpaceMention("ignored_tail")

    val nonEmptyProbe = Space.Iteration(
      Space.Composition(Space.Singleton(sentinel), source),
      innerHead,
      innerTail,
      Space.Singleton(Path.Deref(innerHead))
    )
    val ifEmpty = Space.Iteration(
      Space.Subtraction(Space.Singleton(sentinel), nonEmptyProbe),
      outerHead,
      outerTail,
      fallback
    )
    val control = source \/ ifEmpty

    val sourceSize = SizeExpr.sizeOf(source)
    val fallbackSize = SizeExpr.sizeOf(fallback)
    val expected = SizeExpr.add(
      sourceSize,
      SizeExpr.multiply(SizeExpr.isZero(sourceSize), fallbackSize)
    )
    assertEquals(ResultSpaceSize.estimate(control), ResultSizeEstimate.exact(expected))

    val whenEmpty = ResultSpaceSize.estimate(
      control,
      Map(source.variable -> ResultSizeEstimate.empty)
    )
    assertEquals(whenEmpty, ResultSizeEstimate.exact(fallbackSize))

    val residual = S"s_tail"
    val definitelyNonEmpty = Space.Singleton(Path.Constant(Syntax.parse("x"))) \/ residual
    val nonEmptyControl = subs(control)(spost = { case Space.Mention(sm) if sm == source.variable => definitelyNonEmpty })
    assertEquals(ResultSpaceSize.estimate(nonEmptyControl), ResultSpaceSize.estimate(definitelyNonEmpty))
  }

  test("symbolic bounds contain exhaustive concrete denotations") {
    val x = S"x"
    val y = S"y"
    val h = PathRef("head").known(1)
    val acc = PathRef("acc")
    val tail = SpaceMention("tail")
    val fixed = Path.Constant(Syntax.parse("a"))
    val fixedPoint = SpaceMention("fixed")
    val expressions = Vector(
      Space.Empty,
      Space.Singleton(fixed),
      Space.Literal(SpaceValue(Set(Syntax.parse("a"), Syntax.parse("b")))),
      x \/ y,
      x /\ y,
      x \ y,
      x <| y,
      Space.Raffination(x, y),
      x x y,
      Space.Wrap(x, fixed),
      Space.Unwrap(x, fixed),
      Space.TailsUnion(x),
      Space.TailsIntersection(x),
      Space.PrefixClosure(x),
      Space.SuffixClosure(x),
      Space.TailsClosure(x),
      Space.Range(x, 0, 2),
      Space.Iteration(x, h, tail, Space.Singleton(Path.Deref(h))),
      Space.Iteration(x, h, tail, y),
      Space.Fold(x, Path.ZERO, acc, h, tail, Space.Singleton(Path.Deref(h)), Path.Deref(acc)),
      Space.Fixpoint(x, fixedPoint, Space.Empty),
      Space.GroundedPS(fixed, _ => SpaceValue(Set(Syntax.parse("a"), Syntax.parse("b")))),
      Space.GroundedSS(x, identity)
    )
    val universe = Vector(PathValue(Nil), Syntax.parse("a"), Syntax.parse("b"), Syntax.parse("a.b"))

    for
      leftMask <- 0 until (1 << universe.size)
      rightMask <- 0 until (1 << universe.size)
    do
      val left = SpaceValue(universe.indices.collect {
        case index if (leftMask & (1 << index)) != 0 => universe(index)
      }.toSet)
      val right = SpaceValue(universe.indices.collect {
        case index if (rightMask & (1 << index)) != 0 => universe(index)
      }.toSet)
      val context = SpaceContextMap(Map(x.variable -> left, y.variable -> right))

      expressions.foreach { expression =>
        val estimate = ResultSpaceSize.estimate(expression)
        val actual = BigInt(eval(expression)(using emptyPathContext, context, emptyRoutines).paths.size)
        val lower = evaluated(estimate.lower, context)
        assert(lower <= actual,
          s"lower bound failed for ${expression.show}: ${estimate.show}, lower=$lower, actual=$actual, x=${left.pretty}, y=${right.pretty}")
        estimate.upper.evaluate(using emptyPathContext, context, emptyRoutines).foreach { upper =>
          assert(actual <= upper,
            s"upper bound failed for ${expression.show}: ${estimate.show}, actual=$actual, x=${left.pretty}, y=${right.pretty}")
        }
      }
  }
