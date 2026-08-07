package morkl

import scala.collection.mutable

/** One symbolic path item. Affine items retain the arithmetic relation needed
  * by pure integer-relation programs such as Game of Life's `around` helper.
  * `minimum` and `maximum` bound the base variable, not the shifted value.
  */
enum SpatialItem:
  case Constant(value: PathItem)
  case Affine(variable: String, offset: Int, minimum: Int, maximum: Int)
  case Unknown(name: String)

  def show: String = this match
    case SpatialItem.Constant(value) => value.show
    case SpatialItem.Affine(variable, 0, minimum, maximum) => s"$variable[$minimum..$maximum]"
    case SpatialItem.Affine(variable, offset, minimum, maximum) =>
      val sign = if offset >= 0 then "+" else ""
      s"$variable$sign$offset[$minimum..$maximum]"
    case SpatialItem.Unknown(name) => s"?$name"

  def definitelyDifferent(that: SpatialItem): Boolean = (this, that) match
    case (SpatialItem.Constant(left), SpatialItem.Constant(right)) => left != right
    case (SpatialItem.Affine(lv, lo, _, _), SpatialItem.Affine(rv, ro, _, _)) => lv == rv && lo != ro
    case (SpatialItem.Constant(value), SpatialItem.Affine(_, offset, minimum, maximum)) =>
      value.show.toIntOption.exists(number => number < minimum + offset || number > maximum + offset)
    case (SpatialItem.Affine(_, offset, minimum, maximum), SpatialItem.Constant(value)) =>
      value.show.toIntOption.exists(number => number < minimum + offset || number > maximum + offset)
    case _ => false

  /** Language inclusion between item abstractions. This is deliberately
    * stronger than equality: a literal item is contained by an unknown item,
    * and an affine item is contained by a wider affine domain. */
  def isSubsumedBy(that: SpatialItem): Boolean = (this, that) match
    case (_, SpatialItem.Unknown(_)) => true
    case (SpatialItem.Constant(left), SpatialItem.Constant(right)) => left == right
    case (SpatialItem.Constant(value), SpatialItem.Affine(_, offset, minimum, maximum)) =>
      value.show.toIntOption.exists(number => number >= minimum + offset && number <= maximum + offset)
    case (SpatialItem.Affine(lv, lo, lmin, lmax), SpatialItem.Affine(rv, ro, rmin, rmax)) =>
      lv == rv && lmin + lo >= rmin + ro && lmax + lo <= rmax + ro
    case _ => false

case class SpatialPattern(items: Vector[SpatialItem]):
  def length: Int = items.length
  def show: String = if items.isEmpty then "EPS" else items.map(_.show).mkString(".")
  def concat(that: SpatialPattern): SpatialPattern = SpatialPattern(items ++ that.items)
  def tail: SpatialPattern = SpatialPattern(items.drop(1))
  def drop(count: Int): SpatialPattern = SpatialPattern(items.drop(count))
  def take(count: Int): SpatialPattern = SpatialPattern(items.take(count))

  def definitelyDifferent(that: SpatialPattern): Boolean =
    items.length != that.items.length || items.zip(that.items).exists((left, right) => left.definitelyDifferent(right))

  def definitelyDifferentPrefix(prefix: SpatialPattern): Boolean =
    prefix.items.length > items.length ||
      items.take(prefix.items.length).zip(prefix.items).exists((left, right) => left.definitelyDifferent(right))

  def definitelyHasPrefix(prefix: SpatialPattern): Boolean =
    prefix.items.length <= items.length && items.take(prefix.items.length) == prefix.items

  /** Every path represented by this pattern is represented by `that`. */
  def isSubsumedBy(that: SpatialPattern): Boolean =
    items.length == that.items.length && items.zip(that.items).forall((left, right) => left.isSubsumedBy(right))

  /** Exact membership, including consistency of repeated affine variables. */
  def matches(value: PathValue): Boolean =
    if items.length != value.items.length then false
    else
      val affineValues = mutable.Map.empty[String, Int]
      items.zip(value.items).forall {
        case (SpatialItem.Constant(expected), actual) => expected == actual
        case (SpatialItem.Unknown(_), _) => true
        case (SpatialItem.Affine(variable, offset, minimum, maximum), actual) =>
          actual.show.toIntOption.exists { number =>
            val base = number - offset
            base >= minimum && base <= maximum && affineValues.get(variable).forall(_ == base) && {
              affineValues.update(variable, base)
              true
            }
          }
      }

  def constantValue: Option[PathValue] =
    val values = items.map {
      case SpatialItem.Constant(value) => Some(value)
      case _ => None
    }
    Option.when(values.forall(_.nonEmpty))(PathValue(values.flatten.toList))

object SpatialPattern:
  def constant(value: PathValue): SpatialPattern =
    SpatialPattern(value.items.iterator.map(SpatialItem.Constant(_)).toVector)

/** Abstract value of a single path expression. `patterns` are alternatives,
  * not multiple paths; the expression still evaluates to exactly one path.
  */
case class SpatialPathType(length: PathLengthEstimate, patterns: Vector[SpatialPattern]):
  def show: String =
    val pattern = if patterns.isEmpty then "_" else patterns.map(_.show).mkString("{", " | ", "}")
    s"$pattern:${length.show}"

  def exactValue: Option[PathValue] = patterns match
    case Vector(pattern) => pattern.constantValue
    case _ => None

object SpatialPathType:
  def exact(pattern: SpatialPattern): SpatialPathType =
    val length = PathLengthExpr.const(pattern.length)
    SpatialPathType(PathLengthEstimate.exact(length), Vector(pattern))

  def constant(value: PathValue): SpatialPathType = exact(SpatialPattern.constant(value))

  def length(value: Int, name: String): SpatialPathType =
    require(value >= 0)
    val pattern = SpatialPattern(Vector.tabulate(value)(index => SpatialItem.Unknown(s"$name#$index")))
    exact(pattern)

  def numericPair(name: String, minimum: Int, maximum: Int): SpatialPathType =
    exact(SpatialPattern(Vector(
      SpatialItem.Affine(s"$name.x", 0, minimum, maximum),
      SpatialItem.Affine(s"$name.y", 0, minimum, maximum)
    )))

case class SpatialStratum(
  length: PathLengthEstimate,
  cardinality: ResultSizeEstimate,
  pattern: Option[SpatialPattern] = None
):
  def show: String =
    val shape = pattern.fold(length.show)(_.show)
    s"$shape -> [${cardinality.lower.show}, ${cardinality.upper.show}]"

  def exactLength: Option[Int] = (length.lower, length.upper) match
    case (PathLengthExpr.Const(lower), PathLengthExpr.Const(upper)) if lower == upper && lower.isValidInt =>
      Some(lower.toInt)
    case _ => None

/** A spatial output type: cardinality intervals are retained per path shape or
  * length stratum, together with sound scalar projections. Strata at provably
  * different lengths/patterns are disjoint and therefore add their lower as
  * well as upper cardinalities.
  */
case class SpatialType(
  strata: Vector[SpatialStratum],
  size: ResultSizeEstimate,
  pathLength: PathLengthEstimate,
  bottom: Boolean = false,
  cost: SpatialCostEstimate = SpatialCostEstimate.zero,
):
  lazy val shape: SpatialHeadShape = SpatialHeadShape.fromStrata(strata)
  def isBottom: Boolean = bottom
  def isEmpty: Boolean = !bottom && size.upper == SizeExpr.Zero
  def facts: SpatialFacts = SpatialFacts(this)
  def show: String =
    if bottom then "⊥"
    else
      val body = if strata.isEmpty then "∅" else strata.map(_.show).mkString(" ∪ ")
      s"$body; ${size.show}; ${pathLength.show}; ${shape.show}; ${cost.show}"

  def strataAt(length: Int): Vector[SpatialStratum] = strata.filter(_.exactLength.contains(length))

  def exactValue: Option[SpaceValue] =
    if bottom then None
    else
      val values = strata.map { stratum =>
        Option.when(stratum.cardinality == ResultSizeEstimate.exact(SizeExpr.One))(stratum.pattern.flatMap(_.constantValue)).flatten
      }
      Option.when(values.forall(_.nonEmpty))(SpaceValue(values.flatten.toSet))

  /** Degree of the suffix fibers selected by a fixed-length key prefix. */
  def fiberDegree(prefixLength: Int): SpatialDegreeEstimate =
    require(prefixLength >= 0, s"prefix length must be non-negative, got $prefixLength")
    exactValue match
      case Some(value) =>
        val degrees = value.paths.iterator.filter(_.items.length >= prefixLength)
          .toVector.groupBy(_.items.take(prefixLength)).valuesIterator.map(_.size).toVector
        if degrees.isEmpty then SpatialDegreeEstimate(
          ResultSizeEstimate.empty, ResultSizeEstimate.empty, ResultSizeEstimate.empty, ResultSizeEstimate.empty)
        else SpatialDegreeEstimate(
          ResultSizeEstimate.exact(SizeExpr.const(degrees.min)),
          ResultSizeEstimate.exact(SizeExpr.const(degrees.max)),
          ResultSizeEstimate.exact(SizeExpr.const(degrees.sum)),
          ResultSizeEstimate.exact(SizeExpr.const(degrees.size))
        )
      case None =>
        val eligible = strata.filter(s => s.length.upper.annotatedBound(Z3BoundDirection.Upper).forall(_ >= prefixLength))
        if eligible.isEmpty then SpatialDegreeEstimate(
          ResultSizeEstimate.empty, ResultSizeEstimate.empty, ResultSizeEstimate.empty, ResultSizeEstimate.empty)
        else
          val keyUppers = eligible.map { stratum =>
            val choices = stratum.pattern.toVector.flatMap(_.items.take(prefixLength)).map(SpatialFacts.itemChoices)
            if choices.size == prefixLength then SizeExpr.minimum(stratum.cardinality.upper, SizeExpr.multiply(choices*))
            else stratum.cardinality.upper
          }
          val stratumKeys = ResultSizeEstimate(SizeExpr.add(keyUppers*), SizeExpr.positive(size.lower))
          val shapeKeys = shape.prefixCount(prefixLength)
          val keys = ResultSizeEstimate(
            SizeExpr.minimum(stratumKeys.upper, shapeKeys.upper),
            SizeExpr.maximum(stratumKeys.lower, shapeKeys.lower),
          )
          val oneKey = keys.upper.annotatedValue.contains(BigInt(1))
          SpatialDegreeEstimate(
            ResultSizeEstimate(if oneKey then size.upper else size.upper, SizeExpr.positive(size.lower)),
            ResultSizeEstimate(size.upper, if oneKey then size.lower else SizeExpr.positive(size.lower)),
            size,
            keys,
          )

  def collapseByLength: Vector[SpatialStratum] =
    val (exact, other) = strata.partition(_.exactLength.nonEmpty)
    val collapsed = exact.groupBy(_.exactLength.get).toVector.sortBy(_._1).map { (length, values) =>
      val upper = SizeExpr.add(values.map(_.cardinality.upper)* )
      val disjoint = SpatialType.pairwiseDisjoint(values)
      val lower = if disjoint then SizeExpr.add(values.map(_.cardinality.lower)*)
        else SizeExpr.maximum(values.map(_.cardinality.lower)*)
      SpatialStratum(
        PathLengthEstimate.exact(PathLengthExpr.const(length)),
        ResultSizeEstimate(upper, lower)
      )
    }
    collapsed ++ other

object SpatialType:
  val empty: SpatialType = SpatialType(Vector.empty, ResultSizeEstimate.empty, PathLengthEstimate.empty)
  val bottom: SpatialType = SpatialType(Vector.empty, ResultSizeEstimate.empty, PathLengthEstimate.empty, bottom = true)
  val top: SpatialType = fromStrata(Vector(SpatialStratum(
    PathLengthEstimate.unknown,
    ResultSizeEstimate.unknown,
  )), sizeOverride = Some(ResultSizeEstimate.unknown), lengthOverride = Some(PathLengthEstimate.unknown))

  private def constantContradiction(lower: SizeExpr, upper: SizeExpr): Boolean =
    (lower.annotatedValue, upper.annotatedValue) match
      case (Some(l), Some(u)) => l > u
      case _ => false

  private def constantLengthContradiction(lower: PathLengthExpr, upper: PathLengthExpr): Boolean =
    (lower.annotatedValue, upper.annotatedValue) match
      case (Some(l), Some(u)) => l > u
      case _ => false

  private def intervalsDisjoint(left: SpatialStratum, right: SpatialStratum): Boolean =
    (left.length.lower, left.length.upper, right.length.lower, right.length.upper) match
      case (PathLengthExpr.Const(ll), PathLengthExpr.Const(lu), PathLengthExpr.Const(rl), PathLengthExpr.Const(ru)) =>
        lu < rl || ru < ll
      case _ => false

  private def strataDisjoint(left: SpatialStratum, right: SpatialStratum): Boolean =
    intervalsDisjoint(left, right) || ((left.pattern, right.pattern) match
      case (Some(l), Some(r)) => l.definitelyDifferent(r)
      case _ => false)

  private[SpatialType] def pairwiseDisjoint(values: Vector[SpatialStratum]): Boolean =
    values.indices.forall(left => ((left + 1) until values.size).forall(right => strataDisjoint(values(left), values(right))))

  private def derivedSize(strata: Vector[SpatialStratum]): ResultSizeEstimate =
    if strata.isEmpty then ResultSizeEstimate.empty
    else
      val upper = SizeExpr.add(strata.map(_.cardinality.upper)*)
      val lower = if pairwiseDisjoint(strata) then SizeExpr.add(strata.map(_.cardinality.lower)*)
        else SizeExpr.maximum(strata.map(_.cardinality.lower)*)
      ResultSizeEstimate(upper, lower)

  private def derivedLength(strata: Vector[SpatialStratum]): PathLengthEstimate =
    if strata.isEmpty then PathLengthEstimate.empty
    else PathLengthEstimate(
      PathLengthExpr.minimum(strata.map(_.length.lower)*),
      PathLengthExpr.maximum(strata.map(_.length.upper)*)
    )

  /** Identical classes are one γ obligation. Their possibly-overlapping lower
    * bounds combine by max; uppers add unless the class denotes one exact
    * concrete path. */
  private def canonicalStrata(values: Vector[SpatialStratum]): Vector[SpatialStratum] =
    val seen = mutable.HashSet.empty[(PathLengthEstimate, Option[SpatialPattern])]
    val duplicate = values.exists(value => !seen.add(value.length -> value.pattern))
    val needsSingletonCap = values.exists(value =>
      value.pattern.flatMap(_.constantValue).nonEmpty &&
        (!SizeExpr.provablyNoGreater(value.cardinality.upper, SizeExpr.One) ||
          !SizeExpr.provablyNoGreater(value.cardinality.lower, SizeExpr.One)))
    if !duplicate && !needsSingletonCap then values
    else values.groupBy(value => value.length -> value.pattern).valuesIterator.map { group =>
      val prototype = group.head
      val upper = SizeExpr.add(group.map(_.cardinality.upper)*)
      val lower = SizeExpr.maximum(group.map(_.cardinality.lower)*)
      if prototype.pattern.flatMap(_.constantValue).nonEmpty then prototype.copy(cardinality = ResultSizeEstimate(
        SizeExpr.minimum(upper, SizeExpr.One), SizeExpr.minimum(lower, SizeExpr.One)))
      else prototype.copy(cardinality = ResultSizeEstimate(upper, lower))
    }.toVector.sortBy(stratum => stratum.exactLength -> stratum.pattern.map(_.show))

  /** Close the reduced product under its scalar/stratum projections.  This is
    * intentionally idempotent: every transfer may call it without changing a
    * previously reduced value. */
  def reduce(value: SpatialType): SpatialType =
    if value.bottom then bottom
    else if constantContradiction(value.size.lower, value.size.upper) then bottom
    else
      val inhabitedImpossible = value.strata.exists { stratum =>
        constantLengthContradiction(stratum.length.lower, stratum.length.upper) &&
          stratum.cardinality.lower.annotatedBound(Z3BoundDirection.Lower).exists(_ > 0)
      }
      if inhabitedImpossible then return bottom
      var strata = canonicalStrata(value.strata.filterNot(stratum =>
        stratum.cardinality.upper == SizeExpr.Zero ||
          constantLengthContradiction(stratum.length.lower, stratum.length.upper)))
      var size = value.size
      var length = value.pathLength
      var changed = true
      var rounds = 0
      while changed && rounds < 2 do
        rounds += 1
        val clamped = strata.map { stratum =>
          stratum.copy(cardinality = ResultSizeEstimate(
            SizeExpr.minimum(stratum.cardinality.upper, size.upper),
            stratum.cardinality.lower,
          ))
        }.filterNot(_.cardinality.upper == SizeExpr.Zero)
        if clamped.exists(s => constantContradiction(s.cardinality.lower, s.cardinality.upper)) then return bottom
        val projectedSize = derivedSize(clamped)
        val nextSize =
          if size.exact then size
          else ResultSizeEstimate(
            SizeExpr.minimum(size.upper, projectedSize.upper),
            SizeExpr.maximum(size.lower, projectedSize.lower),
          )
        if constantContradiction(nextSize.lower, nextSize.upper) then return bottom
        val nextLength =
          if nextSize.upper == SizeExpr.Zero then PathLengthEstimate.empty
          else
            val projectedLength = derivedLength(clamped)
            PathLengthEstimate(
              PathLengthExpr.maximum(length.lower, projectedLength.lower),
              PathLengthExpr.minimum(length.upper, projectedLength.upper),
            )
        if nextSize.upper != SizeExpr.Zero && constantLengthContradiction(nextLength.lower, nextLength.upper) then return bottom
        changed = clamped != strata || nextSize != size || nextLength != length
        strata = clamped
        size = nextSize
        length = nextLength
      if size.upper == SizeExpr.Zero then empty.copy(cost = value.cost)
      else SpatialType(strata, size, length, cost = value.cost)

  /** Lattice join of alternative abstract states (not MORKL set union). */
  def joinAlternatives(left: SpatialType, right: SpatialType): SpatialType =
    if left.bottom then right
    else if right.bottom then left
    else
      val l = left.strata.groupBy(s => s.length -> s.pattern)
      val r = right.strata.groupBy(s => s.length -> s.pattern)
      val strata = (l.keySet ++ r.keySet).toVector.map { key =>
        val ls = l.get(key).fold(ResultSizeEstimate.empty)(derivedSize)
        val rs = r.get(key).fold(ResultSizeEstimate.empty)(derivedSize)
        val prototype = l.get(key).orElse(r.get(key)).get.head
        prototype.copy(cardinality = ResultSizeEstimate(
          SizeExpr.maximum(ls.upper, rs.upper),
          SizeExpr.minimum(ls.lower, rs.lower),
        ))
      }
      reduce(fromStrata(strata, sizeOverride = Some(ResultSizeEstimate(
        SizeExpr.maximum(left.size.upper, right.size.upper),
        SizeExpr.minimum(left.size.lower, right.size.lower),
      )), lengthOverride = Some(PathLengthEstimate(
        PathLengthExpr.minimum(left.pathLength.lower, right.pathLength.lower),
        PathLengthExpr.maximum(left.pathLength.upper, right.pathLength.upper),
      ))).copy(cost = SpatialCostEstimate(
        SizeExpr.maximum(left.cost.workUpper, right.cost.workUpper),
        SizeExpr.maximum(left.cost.allocationUpper, right.cost.allocationUpper),
      )))

  /** Compatibility spelling. New code should make the alternative-state
    * meaning explicit with [[joinAlternatives]]. */
  def join(left: SpatialType, right: SpatialType): SpatialType = joinAlternatives(left, right)

  /** Lattice meet of two descriptions of the same concrete space. */
  def meet(left: SpatialType, right: SpatialType): SpatialType =
    if left.bottom || right.bottom then bottom
    else
      val size = ResultSizeEstimate(
        SizeExpr.minimum(left.size.upper, right.size.upper),
        SizeExpr.maximum(left.size.lower, right.size.lower),
      )
      val length = PathLengthEstimate(
        PathLengthExpr.maximum(left.pathLength.lower, right.pathLength.lower),
        PathLengthExpr.minimum(left.pathLength.upper, right.pathLength.upper),
      )
      val candidates = for
        l <- left.strata
        r <- right.strata
        if !((l.pattern, r.pattern) match
          case (Some(a), Some(b)) => a.definitelyDifferent(b)
          case _ => false)
      yield SpatialStratum(
        PathLengthEstimate(
          PathLengthExpr.maximum(l.length.lower, r.length.lower),
          PathLengthExpr.minimum(l.length.upper, r.length.upper),
        ),
        ResultSizeEstimate(
          SizeExpr.minimum(l.cardinality.upper, r.cardinality.upper),
          SizeExpr.maximum(l.cardinality.lower, r.cardinality.lower),
        ),
        if l.pattern == r.pattern then l.pattern else None,
      )
      reduce(fromStrata(candidates, Some(size), Some(length))).copy(cost = SpatialCostEstimate(
        SizeExpr.minimum(left.cost.workUpper, right.cost.workUpper),
        SizeExpr.minimum(left.cost.allocationUpper, right.cost.allocationUpper),
      ))

  /** Sound but incomplete order check, sufficient for convergence detection. */
  def lessOrEqual(left: SpatialType, right: SpatialType): Boolean =
    if left.bottom then true
    else if right.bottom then left.bottom
    else
      val scalar = SizeExpr.provablyNoGreater(left.size.upper, right.size.upper) &&
        SizeExpr.provablyNoGreater(right.size.lower, left.size.lower)
      def lengthInside(inner: PathLengthEstimate, outer: PathLengthEstimate): Boolean =
        val lower = (inner.lower.annotatedValue, outer.lower.annotatedValue) match
          case (Some(i), Some(o)) => i >= o
          case _ => inner.lower == outer.lower || outer.lower == PathLengthExpr.Zero
        val upper = (inner.upper.annotatedValue, outer.upper.annotatedValue) match
          case (Some(i), Some(o)) => i <= o
          case (_, None) => true
          case _ => inner.upper == outer.upper
        lower && upper
      scalar && lengthInside(left.pathLength, right.pathLength) && left.strata.forall { l =>
        right.strata.exists { r =>
          lengthInside(l.length, r.length) &&
            (r.pattern.isEmpty || l.pattern.exists(_.isSubsumedBy(r.pattern.get))) &&
            SizeExpr.provablyNoGreater(l.cardinality.upper, r.cardinality.upper) &&
            SizeExpr.provablyNoGreater(r.cardinality.lower, l.cardinality.lower)
        }
      }

  /** Widen growing cardinalities while retaining a shape invariant only when
    * a subsequent transfer confirms that no new shape escapes it. */
  def widenCardinalities(value: SpatialType): SpatialType =
    if value.bottom || value.isEmpty then value
    else fromStrata(value.strata.map(s => s.copy(cardinality = ResultSizeEstimate(
      SizeExpr.Infinity, SizeExpr.Zero,
    ))), sizeOverride = Some(ResultSizeEstimate(SizeExpr.Infinity, value.size.lower)),
      lengthOverride = Some(value.pathLength)).copy(cost = value.cost)

  def fromStrata(
    values: IterableOnce[SpatialStratum],
    sizeOverride: Option[ResultSizeEstimate] = None,
    lengthOverride: Option[PathLengthEstimate] = None
  ): SpatialType =
    val strata = canonicalStrata(values.iterator.filterNot(_.cardinality.upper == SizeExpr.Zero).toVector)
    SpatialType(strata, sizeOverride.getOrElse(derivedSize(strata)), lengthOverride.getOrElse(derivedLength(strata)))

  def exact(value: SpaceValue, patternLimit: Int = 64): SpatialType =
    if value.paths.isEmpty then empty
    else if value.paths.size <= patternLimit then
      fromStrata(value.paths.toVector.sortBy(_.show).map { path =>
        val pattern = SpatialPattern.constant(path)
        SpatialStratum(
          PathLengthEstimate.exact(PathLengthExpr.const(pattern.length)),
          ResultSizeEstimate.exact(SizeExpr.One),
          Some(pattern)
        )
      })
    else
      fromStrata(value.paths.groupBy(_.items.length).toVector.sortBy(_._1).map { (length, paths) =>
        SpatialStratum(
          PathLengthEstimate.exact(PathLengthExpr.const(length)),
          ResultSizeEstimate.exact(SizeExpr.const(paths.size))
        )
      })

  def lengths(values: (Int, ResultSizeEstimate)*): SpatialType =
    fromStrata(values.map { (length, cardinality) =>
      SpatialStratum(PathLengthEstimate.exact(PathLengthExpr.const(length)), cardinality)
    })

case class SpatialPrefixCoverage(prefix: PathRef, space: SpaceMention, lengths: Set[Int] = Set.empty):
  def covers(length: Int): Boolean = lengths.isEmpty || lengths(length)

case class SpatialAssumptions(
  spaces: Map[SpaceMention, SpatialType] = Map.empty,
  paths: Map[PathRef, SpatialPathType] = Map.empty,
  prefixCoverage: Set[SpatialPrefixCoverage] = Set.empty,
  config: SpatialAnalysisConfig = SpatialAnalysisConfig(),
):
  def sizeAssumptions: Map[SpaceMention, ResultSizeEstimate] = spaces.view.mapValues(_.size).toMap
  def lengthAssumptions: Map[SpaceMention, PathLengthEstimate] = spaces.view.mapValues(_.pathLength).toMap
  def pathLengthAssumptions: Map[PathRef, PathLengthEstimate] = paths.view.mapValues(_.length).toMap

/** Semantic cardinality laws supplied as part of an abstract routine's input
  * annotation. These are reduced-product facts, not observations of a run:
  * they only intersect the structural result with a proved envelope.
  */
enum SpatialBoundLaw:
  /** Every output path is in an image with at most `multiplicity` outputs per
    * input path. */
  case SubsetOfImage(input: SpaceMention, multiplicity: SizeExpr)
  /** The output contains the annotated input set. */
  case ContainsInput(input: SpaceMention)
  /** Output is the non-empty directed transitive closure of distinct input
    * edges.  E edges contribute E direct paths and at most E^2 reachable
    * ordered pairs. */
  case DirectedTransitiveClosure(input: SpaceMention)
  /** Output is contained in a finite semantic universe. */
  case FiniteUniverse(capacity: SizeExpr)
  /** Any non-empty legal seed set within the named component saturates that
    * connected finite component. */
  case ConnectedFiniteComponent(seed: SpaceMention, capacity: SizeExpr)
  /** A symbolic upper envelope supplied with its semantic type annotation. */
  case ProvedUpperBound(value: SizeExpr)
  /** Exact solution count derived from annotated finite domains and
    * relational constraints, without executing the MORKL routine. */
  case FiniteConstraintSolutions(problem: FiniteIntConstraintProblem, nodeBudget: Long = 1000000L)

object SpatialBoundLaw:
  private def input(
    mention: SpaceMention,
    inputs: Map[SpaceMention, SpatialType]
  ): ResultSizeEstimate = inputs.get(mention).map(_.size).getOrElse(ResultSizeEstimate.unknown)

  def reduce(
    result: SpatialType,
    inputs: Map[SpaceMention, SpatialType],
    laws: IterableOnce[SpatialBoundLaw]
  ): SpatialType =
    laws.iterator.foldLeft(result) { (current, law) =>
      if current.isBottom then current
      else
        val asserted = law match
          case SpatialBoundLaw.SubsetOfImage(source, multiplicity) =>
            val sourceSize = input(source, inputs)
            ResultSizeEstimate(
              SizeExpr.multiply(sourceSize.upper, multiplicity),
              SizeExpr.Zero,
            )
          case SpatialBoundLaw.ContainsInput(source) =>
            val sourceSize = input(source, inputs)
            ResultSizeEstimate(SizeExpr.Infinity, sourceSize.lower)
          case SpatialBoundLaw.DirectedTransitiveClosure(source) =>
            val edgeSize = input(source, inputs)
            ResultSizeEstimate(
              SizeExpr.multiply(edgeSize.upper, edgeSize.upper),
              edgeSize.lower,
            )
          case SpatialBoundLaw.FiniteUniverse(capacity) =>
            ResultSizeEstimate(capacity, SizeExpr.Zero)
          case SpatialBoundLaw.ConnectedFiniteComponent(seed, capacity) =>
            val seedSize = input(seed, inputs)
            if seedSize.exact then
              val exact = SizeExpr.ifZero(seedSize.upper, SizeExpr.Zero, capacity)
              ResultSizeEstimate.exact(exact)
            else ResultSizeEstimate(capacity, SizeExpr.Zero)
          case SpatialBoundLaw.ProvedUpperBound(value) =>
            ResultSizeEstimate(value, SizeExpr.Zero)
          case SpatialBoundLaw.FiniteConstraintSolutions(problem, nodeBudget) =>
            problem.countWithin(nodeBudget).fold(ResultSizeEstimate.unknown)(count =>
              ResultSizeEstimate.exact(SizeExpr.const(count)))
        val next = ResultSizeEstimate(
          SizeExpr.minimum(current.size.upper, asserted.upper),
          SizeExpr.maximum(current.size.lower, asserted.lower),
        )
        if asserted.exact then
          val exact = asserted.upper.annotatedValue
          val currentLower = current.size.lower.annotatedValue
          val currentUpper = current.size.upper.annotatedValue
          if exact.exists(value => currentLower.exists(_ > value) || currentUpper.exists(_ < value)) then
            SpatialType.bottom
          else SpatialType.reduce(current.copy(size = asserted))
        else SpatialType.reduce(current.copy(size = next))
    }

enum FiniteIntConstraint:
  case AllDifferent(indices: Vector[Int])
  case NotEqual(left: Int, right: Int)
  case AbsDifferenceNotEqual(left: Int, right: Int, distance: Int)

/** Exact finite constraint cardinality used as a small relational abstract
  * domain.  It counts assignments, not MORKL paths, and therefore remains
  * independent of the concrete Space evaluator.
  */
case class FiniteIntConstraintProblem(
  domains: Vector[Vector[Int]],
  constraints: Vector[FiniteIntConstraint]
):
  private def consistent(values: Array[Int], assigned: Int): Boolean =
    constraints.forall {
      case FiniteIntConstraint.AllDifferent(indices) =>
        val present = indices.filter(_ < assigned).map(values)
        present.distinct.size == present.size
      case FiniteIntConstraint.NotEqual(left, right) =>
        left >= assigned || right >= assigned || values(left) != values(right)
      case FiniteIntConstraint.AbsDifferenceNotEqual(left, right, distance) =>
        left >= assigned || right >= assigned || math.abs(values(left) - values(right)) != distance
    }

  /** Count solutions within a deterministic search-node budget. `None` means
    * the relational refinement was abandoned and its caller must retain the
    * structural bound. */
  def countWithin(nodeBudget: Long): Option[BigInt] =
    require(nodeBudget >= 0, s"node budget must be non-negative, got $nodeBudget")
    val values = Array.ofDim[Int](domains.size)
    var visited = 0L
    var exhausted = false
    def loop(index: Int): BigInt =
      if exhausted then BigInt(0)
      else if index == domains.size then BigInt(1)
      else
        var total = BigInt(0)
        val iterator = domains(index).iterator
        while iterator.hasNext && !exhausted do
          if visited >= nodeBudget then exhausted = true
          else
            visited += 1
            values(index) = iterator.next()
            if consistent(values, index + 1) then total += loop(index + 1)
        total
    val result = loop(0)
    Option.unless(exhausted)(result)

  lazy val count: BigInt = countWithin(Long.MaxValue).get

case class SpatialAnalysisConfig(
  patternLimit: Int = 64,
  fixpointIterations: Int = 6,
  fixpointWidenAfter: Int = 3,
  fixpointAstBudget: Int = 256,
  analysisNodeBudget: Int = 1000000,
):
  require(patternLimit >= 4, s"pattern limit must be at least four, got $patternLimit")
  require(fixpointIterations > 0, s"fixpoint iteration limit must be positive, got $fixpointIterations")
  require(fixpointWidenAfter > 0 && fixpointWidenAfter <= fixpointIterations,
    s"fixpoint widening threshold must be in 1..$fixpointIterations, got $fixpointWidenAfter")
  require(fixpointAstBudget > 0, s"fixpoint AST budget must be positive, got $fixpointAstBudget")
  require(analysisNodeBudget > 0, s"analysis node budget must be positive, got $analysisNodeBudget")

/** The complete abstract input to an open routine. `resultLaws` are semantic
  * type annotations on that routine (for example, that it computes a closure
  * in a stated finite universe). The analyzer never populates these fields
  * from the routine's output; the strict entry point below consumes only this
  * value and the routine syntax.
  */
case class SpatialRoutineAnnotations(
  paths: Map[PathRef, SpatialPathType] = Map.empty,
  spaces: Map[SpaceMention, SpatialType] = Map.empty,
  prefixCoverage: Set[SpatialPrefixCoverage] = Set.empty,
  resultLaws: Vector[SpatialBoundLaw] = Vector.empty,
  config: SpatialAnalysisConfig = SpatialAnalysisConfig(),
):
  def assumptions: SpatialAssumptions = SpatialAssumptions(spaces, paths, prefixCoverage, config)

case class SpatialNodeAnalysis(
  expression: Space,
  result: SpatialType,
  spaces: Map[SpaceMention, SpatialType],
  paths: Map[PathRef, SpatialPathType],
  position: Vector[Int] = Vector.empty,
  observations: Vector[SpatialNodeObservation] = Vector.empty,
)

case class SpatialNodeObservation(
  result: SpatialType,
  spaces: Map[SpaceMention, SpatialType],
  paths: Map[PathRef, SpatialPathType],
)

case class DecoratedSpatialAnalysis(root: SpatialType, nodes: Vector[SpatialNodeAnalysis]):
  def at(expression: Space): Vector[SpatialNodeAnalysis] = nodes.filter(_.expression == expression)
  def atPosition(position: Int*): Option[SpatialNodeAnalysis] = nodes.find(_.position == position.toVector)

/** Input-to-output abstract interpretation over the MORKL AST. */
