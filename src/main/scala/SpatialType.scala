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

case class SpatialDegreeEstimate(
  minimum: ResultSizeEstimate,
  maximum: ResultSizeEstimate,
  edges: ResultSizeEstimate,
  keys: ResultSizeEstimate
):
  def averageShow: String = s"${edges.lower.show}..${edges.upper.show} / ${keys.lower.show}..${keys.upper.show}"
  def show: String =
    s"degree(min=${minimum.show}, max=${maximum.show}, avg=$averageShow)"

/** A spatial output type: cardinality intervals are retained per path shape or
  * length stratum, together with sound scalar projections. Strata at provably
  * different lengths/patterns are disjoint and therefore add their lower as
  * well as upper cardinalities.
  */
case class SpatialType(
  strata: Vector[SpatialStratum],
  size: ResultSizeEstimate,
  pathLength: PathLengthEstimate
):
  def isEmpty: Boolean = size.upper == SizeExpr.Zero
  def show: String =
    val body = if strata.isEmpty then "∅" else strata.map(_.show).mkString(" ∪ ")
    s"$body; ${size.show}; ${pathLength.show}"

  def strataAt(length: Int): Vector[SpatialStratum] = strata.filter(_.exactLength.contains(length))

  def exactValue: Option[SpaceValue] =
    val values = strata.map { stratum =>
      Option.when(stratum.cardinality == ResultSizeEstimate.exact(SizeExpr.One))(stratum.pattern.flatMap(_.constantValue)).flatten
    }
    Option.when(values.forall(_.nonEmpty))(SpaceValue(values.flatten.toSet))

  /** Degree of the suffix fibers selected by a fixed-length key prefix. This
    * is exact for constant-pattern types and conservative otherwise.
    */
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
        val possible = ResultSizeEstimate(size.upper, SizeExpr.Zero)
        SpatialDegreeEstimate(possible, possible, size, possible)

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

  def fromStrata(
    values: IterableOnce[SpatialStratum],
    sizeOverride: Option[ResultSizeEstimate] = None,
    lengthOverride: Option[PathLengthEstimate] = None
  ): SpatialType =
    val strata = values.iterator.filterNot(_.cardinality.upper == SizeExpr.Zero).toVector
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
  prefixCoverage: Set[SpatialPrefixCoverage] = Set.empty
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
  case FiniteConstraintSolutions(problem: FiniteIntConstraintProblem)

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
      val next = law match
        case SpatialBoundLaw.SubsetOfImage(source, multiplicity) =>
          val sourceSize = input(source, inputs)
          ResultSizeEstimate(
            SizeExpr.minimum(current.size.upper, SizeExpr.multiply(sourceSize.upper, multiplicity)),
            current.size.lower,
          )
        case SpatialBoundLaw.ContainsInput(source) =>
          val sourceSize = input(source, inputs)
          ResultSizeEstimate(current.size.upper, SizeExpr.maximum(current.size.lower, sourceSize.lower))
        case SpatialBoundLaw.DirectedTransitiveClosure(source) =>
          val edgeSize = input(source, inputs)
          ResultSizeEstimate(
            SizeExpr.minimum(current.size.upper, SizeExpr.multiply(edgeSize.upper, edgeSize.upper)),
            SizeExpr.maximum(current.size.lower, edgeSize.lower),
          )
        case SpatialBoundLaw.FiniteUniverse(capacity) =>
          ResultSizeEstimate(SizeExpr.minimum(current.size.upper, capacity), current.size.lower)
        case SpatialBoundLaw.ConnectedFiniteComponent(seed, capacity) =>
          val seedSize = input(seed, inputs)
          if seedSize.exact then
            val exact = SizeExpr.ifZero(seedSize.upper, SizeExpr.Zero, capacity)
            ResultSizeEstimate.exact(exact)
          else ResultSizeEstimate(
            SizeExpr.minimum(current.size.upper, capacity), current.size.lower)
        case SpatialBoundLaw.ProvedUpperBound(value) =>
          ResultSizeEstimate(SizeExpr.minimum(current.size.upper, value), current.size.lower)
        case SpatialBoundLaw.FiniteConstraintSolutions(problem) =>
          ResultSizeEstimate.exact(SizeExpr.const(problem.count))
      val strata = current.strata.map { stratum =>
        stratum.copy(cardinality = stratum.cardinality.copy(
          upper = SizeExpr.minimum(stratum.cardinality.upper, next.upper),
        ))
      }.filterNot(_.cardinality.upper == SizeExpr.Zero)
      current.copy(
        strata = strata,
        size = next,
        pathLength = if next.upper == SizeExpr.Zero then PathLengthEstimate.empty else current.pathLength,
      )
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

  lazy val count: BigInt =
    val values = Array.ofDim[Int](domains.size)
    def loop(index: Int): BigInt =
      if index == domains.size then BigInt(1)
      else domains(index).iterator.map { value =>
        values(index) = value
        if consistent(values, index + 1) then loop(index + 1) else BigInt(0)
      }.sum
    loop(0)

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
):
  def assumptions: SpatialAssumptions = SpatialAssumptions(spaces, paths, prefixCoverage)

/** Input-to-output abstract interpretation over the MORKL AST. */
object SpatialTypeAnalysis:
  private val patternLimit = Option(System.getProperty("morkl.spatial.patternLimit"))
    .flatMap(_.toIntOption).getOrElse(64).max(4)

  def output(
    space: Space,
    assumptions: SpatialAssumptions = SpatialAssumptions(),
    routines: PartialFunction[RoutinePtr, Routine] = PartialFunction.empty
  ): SpatialType =
    constrain(space, analyze(space, assumptions, routines, Set.empty), assumptions)

  def outputRoutine(
    routine: Routine,
    pathInputs: Map[PathRef, SpatialPathType],
    spaceInputs: Map[SpaceMention, SpatialType],
    routines: PartialFunction[RoutinePtr, Routine] = PartialFunction.empty,
    prefixCoverage: Set[SpatialPrefixCoverage] = Set.empty
  ): SpatialType =
    outputRoutineAbstract(routine, pathInputs, spaceInputs, routines, prefixCoverage)

  /** Interpret a routine from abstract argument annotations. This explicit
    * name remains as a compatibility alias for callers that want to emphasize
    * the always-abstract contract; `outputRoutine` now has the same contract.
    */
  def outputRoutineAbstract(
    routine: Routine,
    pathInputs: Map[PathRef, SpatialPathType],
    spaceInputs: Map[SpaceMention, SpatialType],
    routines: PartialFunction[RoutinePtr, Routine] = PartialFunction.empty,
    prefixCoverage: Set[SpatialPrefixCoverage] = Set.empty,
    boundLaws: Vector[SpatialBoundLaw] = Vector.empty
  ): SpatialType =
    outputRoutineAbstract(
      routine,
      SpatialRoutineAnnotations(pathInputs, spaceInputs, prefixCoverage, boundLaws),
      routines,
    )

  /** Strict abstract entry point. Its dataflow is deliberately one-way:
    * annotated arguments and routine syntax -> spatial type. Concrete
    * evaluation is reserved for external validation and cannot feed this
    * method.
    */
  def outputRoutineAbstract(
    routine: Routine,
    annotations: SpatialRoutineAnnotations,
    routines: PartialFunction[RoutinePtr, Routine],
  ): SpatialType =
    val assumptions = annotations.assumptions
    SpatialBoundLaw.reduce(
      analyze(routine.body, assumptions, routines, Set(routine.name)),
      annotations.spaces,
      annotations.resultLaws,
    )

  private def constrain(space: Space, raw: SpatialType, assumptions: SpatialAssumptions): SpatialType =
    val scalarSize = ResultSpaceSize.estimate(space, assumptions.sizeAssumptions)
    val scalarLength = ResultPathLength.estimate(
      space,
      assumptions.lengthAssumptions,
      assumptions.pathLengthAssumptions,
      assumptions.sizeAssumptions
    )
    raw.copy(
      size = if raw.size.exact then raw.size else ResultSizeEstimate(
        SizeExpr.minimum(raw.size.upper, scalarSize.upper),
        SizeExpr.maximum(raw.size.lower, scalarSize.lower)
      ),
      pathLength = if raw.pathLength.exact then raw.pathLength else PathLengthEstimate(
        PathLengthExpr.maximum(raw.pathLength.lower, scalarLength.lower),
        PathLengthExpr.minimum(raw.pathLength.upper, scalarLength.upper)
      )
    )

  private def exactLength(length: PathLengthEstimate): Option[Int] = (length.lower, length.upper) match
    case (PathLengthExpr.Const(lower), PathLengthExpr.Const(upper)) if lower == upper && lower.isValidInt => Some(lower.toInt)
    case _ => None

  private def path(
    value: Path,
    assumptions: SpatialAssumptions,
    routines: PartialFunction[RoutinePtr, Routine],
    active: Set[RoutinePtr]
  ): SpatialPathType = value match
    case Path.Deref(ref) => assumptions.paths.getOrElse(ref,
      if ref.lengthHint >= 0 then SpatialPathType.length(ref.lengthHint, ref.s)
      else SpatialPathType(PathLengthEstimate.exact(PathLengthExpr.PathLengthOf(value)), Vector.empty))
    case Path.Constant(constant) => SpatialPathType.constant(constant)
    case Path.Concat(left, right) =>
      val l = path(left, assumptions, routines, active)
      val r = path(right, assumptions, routines, active)
      val product = for a <- l.patterns; b <- r.patterns yield a.concat(b)
      val patterns = if product.size <= patternLimit then product else Vector.empty
      SpatialPathType(
        PathLengthEstimate(PathLengthExpr.add(l.length.lower, r.length.lower), PathLengthExpr.add(l.length.upper, r.length.upper)),
        patterns
      )
    case Path.GroundedPP(_, _) | Path.GroundedSP(_, _) =>
      SpatialPathType(ResultPathLength.estimate(Space.Singleton(value), assumptions.lengthAssumptions,
        assumptions.pathLengthAssumptions, assumptions.sizeAssumptions), Vector.empty)

  private def singleton(value: SpatialPathType): SpatialType =
    value.patterns match
      case Vector(pattern) => SpatialType.fromStrata(Vector(SpatialStratum(value.length,
        ResultSizeEstimate.exact(SizeExpr.One), Some(pattern))))
      case patterns if patterns.nonEmpty =>
        val optional = ResultSizeEstimate(SizeExpr.One, SizeExpr.Zero)
        SpatialType.fromStrata(patterns.map(pattern => SpatialStratum(value.length, optional, Some(pattern))),
          sizeOverride = Some(ResultSizeEstimate.exact(SizeExpr.One)), lengthOverride = Some(value.length))
      case _ => SpatialType.fromStrata(Vector(SpatialStratum(value.length, ResultSizeEstimate.exact(SizeExpr.One))))

  private def unionCount(left: ResultSizeEstimate, right: ResultSizeEstimate): ResultSizeEstimate =
    ResultSizeEstimate(SizeExpr.add(left.upper, right.upper), SizeExpr.maximum(left.lower, right.lower))

  private def capStrata(values: Vector[SpatialStratum]): Vector[SpatialStratum] =
    if values.size <= patternLimit then values
    else
      val (exact, ranged) = values.partition(_.exactLength.nonEmpty)
      val exactCollapsed = exact.groupBy(_.exactLength.get).toVector.sortBy(_._1).map { (length, group) =>
        val groupType = SpatialType.fromStrata(group)
        SpatialStratum(PathLengthEstimate.exact(PathLengthExpr.const(length)), groupType.size)
      }
      val rangedCollapsed =
        if ranged.isEmpty then Vector.empty
        else
          val groupType = SpatialType.fromStrata(ranged)
          Vector(SpatialStratum(groupType.pathLength, groupType.size))
      exactCollapsed ++ rangedCollapsed

  private def union(left: SpatialType, right: SpatialType): SpatialType =
    if left.isEmpty then right
    else if right.isEmpty then left
    else
      val grouped = (left.strata ++ right.strata).groupBy(value => value.length -> value.pattern)
      val strata = grouped.valuesIterator.map { values =>
        values.reduce { (a, b) => a.copy(cardinality = unionCount(a.cardinality, b.cardinality)) }
      }.toVector
      SpatialType.fromStrata(capStrata(strata),
        sizeOverride = Some(unionCount(left.size, right.size)))

  private def intersection(left: SpatialType, right: SpatialType): SpatialType =
    val strata = for
      l <- left.strata
      r <- right.strata
      if !lengthsDisjoint(l.length, r.length)
      if !((l.pattern, r.pattern) match
        case (Some(a), Some(b)) => a.definitelyDifferent(b)
        case _ => false)
    yield
      val samePattern = l.pattern.nonEmpty && l.pattern == r.pattern
      val exactSingleton = samePattern && l.cardinality.lower == SizeExpr.One && l.cardinality.upper == SizeExpr.One &&
        r.cardinality.lower == SizeExpr.One && r.cardinality.upper == SizeExpr.One
      SpatialStratum(
        PathLengthEstimate(PathLengthExpr.maximum(l.length.lower, r.length.lower),
          PathLengthExpr.minimum(l.length.upper, r.length.upper)),
        ResultSizeEstimate(SizeExpr.minimum(l.cardinality.upper, r.cardinality.upper),
          if exactSingleton then SizeExpr.One else SizeExpr.Zero),
        if samePattern then l.pattern else None
      )
    val raw = SpatialType.fromStrata(capStrata(strata))
    raw.copy(size = ResultSizeEstimate(
      SizeExpr.minimum(raw.size.upper, left.size.upper, right.size.upper), raw.size.lower))

  private def subtraction(left: SpatialType, right: SpatialType): SpatialType =
    val strata = left.strata.flatMap { l =>
      val relevant = right.strata.filter { r =>
        !lengthsDisjoint(l.length, r.length) && !((l.pattern, r.pattern) match
          case (Some(a), Some(b)) => a.definitelyDifferent(b)
          case _ => false)
      }
      if relevant.isEmpty then Vector(l)
      else
        val exactMatches = relevant.filter(r => l.pattern.nonEmpty && l.pattern == r.pattern)
        val removedUpper = SizeExpr.add(relevant.map(_.cardinality.upper)*)
        val removedLower = SpatialType.fromStrata(exactMatches).size.lower
        val cardinality = ResultSizeEstimate(
          SizeExpr.positiveDifference(l.cardinality.upper, removedLower),
          SizeExpr.positiveDifference(l.cardinality.lower, removedUpper)
        )
        Option.when(cardinality.upper != SizeExpr.Zero)(l.copy(cardinality = cardinality)).toVector
    }
    val raw = SpatialType.fromStrata(capStrata(strata))
    raw.copy(size = ResultSizeEstimate(SizeExpr.minimum(raw.size.upper, left.size.upper), raw.size.lower))

  private def composition(left: SpatialType, right: SpatialType): SpatialType =
    val strata = for l <- left.strata; r <- right.strata yield
      val pattern = for a <- l.pattern; b <- r.pattern yield a.concat(b)
      SpatialStratum(
        PathLengthEstimate(PathLengthExpr.add(l.length.lower, r.length.lower), PathLengthExpr.add(l.length.upper, r.length.upper)),
        ResultSizeEstimate(
          SizeExpr.multiply(l.cardinality.upper, r.cardinality.upper),
          SizeExpr.maximum(
            SizeExpr.multiply(l.cardinality.lower, SizeExpr.positive(r.cardinality.lower)),
            SizeExpr.multiply(r.cardinality.lower, SizeExpr.positive(l.cardinality.lower))
          )
        ),
        pattern
      )
    val raw = SpatialType.fromStrata(capStrata(strata))
    raw.copy(size = ResultSizeEstimate(
      SizeExpr.minimum(raw.size.upper, SizeExpr.multiply(left.size.upper, right.size.upper)), raw.size.lower))

  private def lengthsDisjoint(left: PathLengthEstimate, right: PathLengthEstimate): Boolean =
    (left.lower, left.upper, right.lower, right.upper) match
      case (PathLengthExpr.Const(ll), PathLengthExpr.Const(lu), PathLengthExpr.Const(rl), PathLengthExpr.Const(ru)) =>
        lu < rl || ru < ll
      case _ => false

  private def singletonRefs(space: Space): Set[PathRef] = space match
    case Space.Singleton(Path.Deref(ref)) => Set(ref)
    case Space.Union(left, right) => singletonRefs(left) ++ singletonRefs(right)
    case _ => Set.empty

  private def directMention(space: Space): Option[SpaceMention] = space match
    case Space.Mention(mention) => Some(mention)
    case _ => None

  private def restriction(
    leftExpression: Space,
    prefixExpression: Space,
    left: SpatialType,
    prefixes: SpatialType,
    assumptions: SpatialAssumptions
  ): SpatialType =
    val prefixRefs = singletonRefs(prefixExpression)
    val sourceMention = directMention(leftExpression)
    val coverage = for
      ref <- prefixRefs
      mention <- sourceMention.toSet
      fact <- assumptions.prefixCoverage
      if fact.prefix == ref && fact.space == mention
    yield fact
    val strata = left.strata.flatMap { l =>
      val compatible = prefixes.strata.filter { p =>
        val lengthPossible = (l.length.upper, p.length.lower) match
          case (PathLengthExpr.Const(leftUpper), PathLengthExpr.Const(prefixLower)) => leftUpper >= prefixLower
          case _ => true
        lengthPossible && !((l.pattern, p.pattern) match
          case (Some(value), Some(prefix)) => value.definitelyDifferentPrefix(prefix)
          case _ => false)
      }
      if compatible.isEmpty then Vector.empty
      else
        val retainedGuard = SizeExpr.maximum(compatible.map { p =>
          (l.pattern, p.pattern) match
            case (Some(value), Some(prefix)) if value.definitelyHasPrefix(prefix) =>
              SizeExpr.positive(p.cardinality.lower)
            case _ => SizeExpr.Zero
        }*)
        val dependentCoverage = l.exactLength.exists(length => coverage.exists(_.covers(length)))
        val lower =
          if retainedGuard != SizeExpr.Zero then SizeExpr.multiply(l.cardinality.lower, retainedGuard)
          else if dependentCoverage then SizeExpr.positive(l.cardinality.lower)
          else SizeExpr.Zero
        Vector(l.copy(cardinality = ResultSizeEstimate(l.cardinality.upper, lower)))
    }
    val raw = SpatialType.fromStrata(strata)
    raw.copy(size = ResultSizeEstimate(SizeExpr.minimum(raw.size.upper, left.size.upper), raw.size.lower))

  private def affineFiber(value: SpaceValue, prefix: SpatialPattern): Option[SpatialPattern] =
    if prefix.items.size != 1 || value.paths.isEmpty || !value.paths.forall(_.items.size == 2) then None
    else
      val rows = value.paths.toVector.flatMap { path =>
        for
          input <- path.items.head.show.toIntOption
          output <- path.items(1).show.toIntOption
        yield input -> output
      }
      if rows.size != value.paths.size then None
      else
        val mapping = rows.toMap
        val deltas = rows.map((input, output) => output - input).distinct
        val keys = mapping.keys.toVector.sorted
        if mapping.size != rows.size || deltas.size != 1 || keys.isEmpty || keys != (keys.head to keys.last).toVector then None
        else prefix.items.head match
          case SpatialItem.Constant(item) =>
            item.show.toIntOption.flatMap(mapping.get).map(output =>
              SpatialPattern(Vector(SpatialItem.Constant(PathItem(output.toString)))))
          case SpatialItem.Affine(variable, offset, minimum, maximum)
              if minimum + offset >= keys.head && maximum + offset <= keys.last =>
            Some(SpatialPattern(Vector(SpatialItem.Affine(variable, offset + deltas.head, minimum, maximum))))
          case _ => None

  private def unwrap(sourceExpression: Space, source: SpatialType, prefix: SpatialPathType): SpatialType =
    sourceExpression match
      case Space.Literal(value) if prefix.patterns.size == 1 =>
        affineFiber(value, prefix.patterns.head) match
          case Some(pattern) => SpatialType.fromStrata(Vector(SpatialStratum(
            PathLengthEstimate.exact(PathLengthExpr.const(pattern.length)),
            ResultSizeEstimate.exact(SizeExpr.One), Some(pattern))))
          case None => unwrapGeneral(source, prefix)
      case _ => unwrapGeneral(source, prefix)

  private def unwrapGeneral(source: SpatialType, prefix: SpatialPathType): SpatialType =
    val strata = for
      value <- source.strata
      prefixPattern <- if prefix.patterns.nonEmpty then prefix.patterns else Vector.empty
      if !value.pattern.exists(_.definitelyDifferentPrefix(prefixPattern))
      prefixLength <- exactLength(prefix.length).toVector
      if value.exactLength.forall(_ >= prefixLength)
    yield
      val retained = value.pattern.exists(_.definitelyHasPrefix(prefixPattern))
      SpatialStratum(
        PathLengthEstimate(
          PathLengthExpr.positiveDifference(value.length.lower, prefix.length.upper),
          PathLengthExpr.positiveDifference(value.length.upper, prefix.length.lower)
        ),
        ResultSizeEstimate(value.cardinality.upper, if retained then value.cardinality.lower else SizeExpr.Zero),
        value.pattern.map(_.drop(prefixLength))
      )
    if strata.nonEmpty then
      val raw = SpatialType.fromStrata(strata)
      raw.copy(size = ResultSizeEstimate(SizeExpr.minimum(raw.size.upper, source.size.upper), raw.size.lower))
    else
      val length = PathLengthEstimate(
        PathLengthExpr.positiveDifference(source.pathLength.lower, prefix.length.upper),
        PathLengthExpr.positiveDifference(source.pathLength.upper, prefix.length.lower)
      )
      SpatialType.fromStrata(Vector(SpatialStratum(length, ResultSizeEstimate(source.size.upper, SizeExpr.Zero))))

  private def tails(source: SpatialType, intersection: Boolean): SpatialType =
    val strata = source.strata.flatMap { value =>
      val headed = value.exactLength.forall(_ > 0)
      Option.when(headed)(SpatialStratum(
        PathLengthEstimate(
          PathLengthExpr.positiveDifference(value.length.lower, PathLengthExpr.One),
          PathLengthExpr.positiveDifference(value.length.upper, PathLengthExpr.One)
        ),
        ResultSizeEstimate(value.cardinality.upper,
          if intersection then SizeExpr.Zero else SizeExpr.positive(value.cardinality.lower)),
        value.pattern.map(_.tail)
      ))
    }
    val raw = SpatialType.fromStrata(strata)
    raw.copy(size = ResultSizeEstimate(SizeExpr.minimum(raw.size.upper, source.size.upper), raw.size.lower))

  private def closure(source: SpatialType, prefixes: Boolean, suffixes: Boolean, includeEpsilon: Boolean): SpatialType =
    val strata = source.strata.flatMap { value =>
      value.pattern match
        case Some(pattern) =>
          val start = if includeEpsilon then 0 else 1
          (start to pattern.length).map { length =>
            val result = if prefixes then pattern.take(length) else if suffixes then SpatialPattern(pattern.items.takeRight(length))
              else SpatialPattern(pattern.items.drop(pattern.length - length))
            SpatialStratum(PathLengthEstimate.exact(PathLengthExpr.const(length)),
              ResultSizeEstimate(value.cardinality.upper, SizeExpr.positive(value.cardinality.lower)), Some(result))
          }
        case None => Vector(SpatialStratum(
          PathLengthEstimate(if includeEpsilon then PathLengthExpr.Zero else PathLengthExpr.One, value.length.upper),
          ResultSizeEstimate(SizeExpr.Infinity, SizeExpr.Zero)
        ))
    }
    SpatialType.fromStrata(capStrata(strata))

  private def matchIfEmpty(space: Space): Option[(Space, Space)] = space match
    case Space.Iteration(
          Space.Subtraction(
            Space.Singleton(Path.Constant(sentinel)),
            Space.Iteration(innerSource, innerHead, _, Space.Singleton(Path.Deref(emittedHead)))
          ), _, _, fallback
        ) if sentinel.items.length == 1 && innerHead == emittedHead =>
      innerSource match
        case Space.Composition(Space.Singleton(Path.Constant(prefix)), condition) if prefix == sentinel => Some(condition -> fallback)
        case Space.Wrap(condition, Path.Constant(prefix)) if prefix == sentinel => Some(condition -> fallback)
        case _ => None
    case _ => None

  /** Number of distinct first-item groups represented by a stratum.  Iteration
    * visits groups, not paths: a relation whose first item is the constant
    * `edge` has one group regardless of its edge cardinality.  Affine items
    * retain a finite domain cap; only unconstrained items fall back to the
    * path-count upper bound.
    */
  private def iterationGroups(stratum: SpatialStratum): ResultSizeEstimate =
    val count = stratum.cardinality
    val upper = stratum.pattern.flatMap(_.items.headOption) match
      case Some(SpatialItem.Constant(_)) => SizeExpr.positive(count.upper)
      case Some(SpatialItem.Affine(_, _, minimum, maximum)) =>
        SizeExpr.minimum(count.upper, SizeExpr.const(BigInt(maximum) - BigInt(minimum) + 1))
      case _ => count.upper
    ResultSizeEstimate(upper, SizeExpr.positive(count.lower))

  /** Cardinality of one tail fiber selected by Iteration.  A constant head
    * selects the whole represented stratum; otherwise a visited group is known
    * non-empty but may contain any number of the source paths.
    */
  private def iterationTail(stratum: SpatialStratum): ResultSizeEstimate =
    stratum.pattern.flatMap(_.items.headOption) match
      case Some(SpatialItem.Constant(_)) => stratum.cardinality
      case _ => ResultSizeEstimate(stratum.cardinality.upper, SizeExpr.positive(stratum.cardinality.lower))

  /** Analyze the leaf of a canonical nested iterator chain for one source
    * path.  Such a chain is a map over paths, not a Cartesian product of the
    * maximum sizes of every intermediate tail fiber.  Returning `None` keeps
    * the general grouped-iteration transfer for templates that stop before
    * consuming the complete path or iterate some unrelated space.
    */
  private def pointwiseIterationLeaf(
    body: Space,
    currentRest: SpaceMention,
    remainingPattern: Option[SpatialPattern],
    remainingLength: Int,
    assumptions: SpatialAssumptions,
    routines: PartialFunction[RoutinePtr, Routine],
    active: Set[RoutinePtr]
  ): Option[SpatialType] =
    if remainingLength == 0 then Some(analyze(body, assumptions, routines, active))
    else body match
      case Space.Iteration(Space.Mention(source), symbol, nextRest, nextBody) if source == currentRest =>
        val headPattern = remainingPattern.flatMap(_.items.headOption).map(item => SpatialPattern(Vector(item)))
        val tailPattern = remainingPattern.map(_.tail)
        val headType = SpatialPathType(PathLengthEstimate.exact(PathLengthExpr.One), headPattern.toVector)
        val tailType = SpatialType.fromStrata(Vector(SpatialStratum(
          PathLengthEstimate.exact(PathLengthExpr.const(remainingLength - 1)),
          ResultSizeEstimate.exact(SizeExpr.One),
          tailPattern,
        )))
        val nested = assumptions.copy(
          spaces = assumptions.spaces.updated(nextRest, tailType),
          paths = assumptions.paths.updated(symbol, headType),
        )
        pointwiseIterationLeaf(nextBody, nextRest, tailPattern, remainingLength - 1, nested, routines, active)
      case _ => None

  private def pointwiseIterationUpper(
    source: SpatialType,
    symbol: PathRef,
    rest: SpaceMention,
    body: Space,
    assumptions: SpatialAssumptions,
    routines: PartialFunction[RoutinePtr, Routine],
    active: Set[RoutinePtr]
  ): Option[SizeExpr] =
    val bounds = source.strata.map { stratum =>
      stratum.exactLength.filter(_ > 0).flatMap { length =>
        val headPattern = stratum.pattern.flatMap(_.items.headOption).map(item => SpatialPattern(Vector(item)))
        val tailPattern = stratum.pattern.map(_.tail)
        val headType = SpatialPathType(PathLengthEstimate.exact(PathLengthExpr.One), headPattern.toVector)
        val tailType = SpatialType.fromStrata(Vector(SpatialStratum(
          PathLengthEstimate.exact(PathLengthExpr.const(length - 1)),
          ResultSizeEstimate.exact(SizeExpr.One),
          tailPattern,
        )))
        val nested = assumptions.copy(
          spaces = assumptions.spaces.updated(rest, tailType),
          paths = assumptions.paths.updated(symbol, headType),
        )
        pointwiseIterationLeaf(body, rest, tailPattern, length - 1, nested, routines, active)
          .map(leaf => SizeExpr.multiply(stratum.cardinality.upper, leaf.size.upper))
      }
    }
    Option.when(bounds.forall(_.nonEmpty))(SizeExpr.add(bounds.flatten*))

  private def conditional(condition: SpatialType, ifZero: SpatialType, ifNonZero: SpatialType): SpatialType =
    val exactCondition = Option.when(condition.size.exact)(condition.size.upper)
    exactCondition match
      case None => union(ifZero, ifNonZero).copy(size = ResultSizeEstimate(
        SizeExpr.maximum(ifZero.size.upper, ifNonZero.size.upper),
        SizeExpr.minimum(ifZero.size.lower, ifNonZero.size.lower)
      ))
      case Some(test) =>
        val groupedZero = ifZero.strata.groupBy(value => value.length -> value.pattern)
        val groupedNonZero = ifNonZero.strata.groupBy(value => value.length -> value.pattern)
        val keys = groupedZero.keySet ++ groupedNonZero.keySet
        val strata = keys.toVector.map { key =>
          def cardinality(group: Option[Vector[SpatialStratum]]): ResultSizeEstimate =
            group.fold(ResultSizeEstimate.empty)(values => SpatialType.fromStrata(values).size)
          val zero = cardinality(groupedZero.get(key))
          val nonzero = cardinality(groupedNonZero.get(key))
          val prototype = groupedZero.get(key).flatMap(_.headOption).orElse(groupedNonZero.get(key).flatMap(_.headOption)).get
          prototype.copy(cardinality = ResultSizeEstimate(
            SizeExpr.ifZero(test, zero.upper, nonzero.upper),
            SizeExpr.ifZero(test, zero.lower, nonzero.lower)
          ))
        }
        SpatialType.fromStrata(strata, sizeOverride = Some(ResultSizeEstimate(
          SizeExpr.ifZero(test, ifZero.size.upper, ifNonZero.size.upper),
          SizeExpr.ifZero(test, ifZero.size.lower, ifNonZero.size.lower)
        )))

  private def analyze(
    space: Space,
    assumptions: SpatialAssumptions,
    routines: PartialFunction[RoutinePtr, Routine],
    active: Set[RoutinePtr]
  ): SpatialType =
    def rec(next: Space): SpatialType = analyze(next, assumptions, routines, active)
    space match
      case Space.Empty => SpatialType.empty
      case Space.Mention(mention) => assumptions.spaces.getOrElse(mention,
        SpatialType.fromStrata(Vector(SpatialStratum(
          ResultPathLength.estimate(space, assumptions.lengthAssumptions, assumptions.pathLengthAssumptions,
            assumptions.sizeAssumptions),
          ResultSpaceSize.estimate(space, assumptions.sizeAssumptions)
        ))))
      case Space.Call(name, refs, mentions) if routines.isDefinedAt(name) && !active(name) =>
        val routine = routines(name)
        val pathValues = refs.map(path(_, assumptions, routines, active))
        val spaceValues = mentions.map(rec)
        val nested = assumptions.copy(
          spaces = assumptions.spaces ++ routine.mentions.zip(spaceValues),
          paths = assumptions.paths ++ routine.refs.zip(pathValues)
        )
        analyze(routine.body, nested, routines, active + name)
      case Space.Call(_, _, _) =>
        SpatialType.fromStrata(Vector(SpatialStratum(
          ResultPathLength.estimate(space, assumptions.lengthAssumptions, assumptions.pathLengthAssumptions,
            assumptions.sizeAssumptions),
          ResultSpaceSize.estimate(space, assumptions.sizeAssumptions)
        )))
      case Space.Singleton(value) => singleton(path(value, assumptions, routines, active))
      case Space.Literal(value) => SpatialType.exact(value, patternLimit)
      case Space.Union(left, right) =>
        right match
          case candidate if matchIfEmpty(candidate).exists(_._1 == left) =>
            val fallback = matchIfEmpty(candidate).get._2
            conditional(rec(left), analyze(fallback, assumptions, routines, active), rec(left))
          case _ if matchIfEmpty(left).exists(_._1 == right) =>
            val fallback = matchIfEmpty(left).get._2
            conditional(rec(right), analyze(fallback, assumptions, routines, active), rec(right))
          case _ if left.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef] => rec(left)
          case _ => union(rec(left), rec(right))
      case Space.Intersection(left, right) =>
        if left.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef] then rec(left)
        else intersection(rec(left), rec(right))
      case Space.Subtraction(left, right) =>
        if left.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef] then SpatialType.empty
        else subtraction(rec(left), rec(right))
      case Space.Restriction(left, prefixes) => restriction(left, prefixes, rec(left), rec(prefixes), assumptions)
      case Space.Raffination(left, prefixes) =>
        val l = rec(left)
        subtraction(l, restriction(left, prefixes, l, rec(prefixes), assumptions))
      case Space.Composition(left, right) => composition(rec(left), rec(right))
      case iteration @ Space.Iteration(src, symbol, rest, body) =>
        matchIfEmpty(iteration) match
          case Some((condition, fallback)) => conditional(rec(condition), analyze(fallback, assumptions, routines, active), SpatialType.empty)
          case None =>
            val source = rec(src)
            val branches = source.strata.flatMap { stratum =>
              val length = stratum.exactLength
              if length.contains(0) then Vector.empty
              else
                val headPattern = stratum.pattern.flatMap(_.items.headOption).map(item => SpatialPattern(Vector(item)))
                val headType = SpatialPathType(PathLengthEstimate.exact(PathLengthExpr.One), headPattern.toVector)
                val tailLength = PathLengthEstimate(
                  PathLengthExpr.positiveDifference(stratum.length.lower, PathLengthExpr.One),
                  PathLengthExpr.positiveDifference(stratum.length.upper, PathLengthExpr.One)
                )
                val tailCardinality = iterationTail(stratum)
                val tailType = SpatialType.fromStrata(Vector(SpatialStratum(tailLength, tailCardinality, stratum.pattern.map(_.tail))))
                val nested = assumptions.copy(
                  spaces = assumptions.spaces.updated(rest, tailType),
                  paths = assumptions.paths.updated(symbol, headType)
                )
                val branch = analyze(body, nested, routines, active)
                val groups = iterationGroups(stratum)
                branch.strata.map(value => value.copy(cardinality = ResultSizeEstimate(
                  SizeExpr.multiply(groups.upper, value.cardinality.upper),
                  SizeExpr.multiply(groups.lower, value.cardinality.lower)
                )))
            }
            val general = SpatialType.fromStrata(capStrata(branches))
            pointwiseIterationUpper(source, symbol, rest, body, assumptions, routines, active) match
              case Some(upper) => general.copy(size = ResultSizeEstimate(
                SizeExpr.minimum(general.size.upper, upper), general.size.lower))
              case None => general
      case Space.Fold(src, initial, acc, symbol, rest, body, _) =>
        val source = rec(src)
        val initialType = path(initial, assumptions, routines, active)
        val nested = assumptions.copy(
          spaces = assumptions.spaces.updated(rest, source),
          paths = assumptions.paths.updated(acc, initialType).updated(symbol, SpatialPathType.length(1, symbol.s))
        )
        val branch = analyze(body, nested, routines, active)
        branch.copy(size = ResultSizeEstimate(SizeExpr.multiply(source.size.upper, branch.size.upper), SizeExpr.Zero))
      case Space.Fixpoint(initial, variable, step) =>
        val base = rec(initial)
        val nested = assumptions.copy(spaces = assumptions.spaces.updated(variable, base))
        val next = analyze(step, nested, routines, active)
        union(base, next).copy(size = ResultSizeEstimate(SizeExpr.Infinity, base.size.lower))
      case Space.Wrap(src, prefix) => composition(singleton(path(prefix, assumptions, routines, active)), rec(src))
      case Space.Unwrap(src, prefix) => unwrap(src, rec(src), path(prefix, assumptions, routines, active))
      case Space.TailsUnion(src) => tails(rec(src), intersection = false)
      case Space.TailsIntersection(src) => tails(rec(src), intersection = true)
      case Space.PrefixClosure(src) => closure(rec(src), prefixes = true, suffixes = false, includeEpsilon = false)
      case Space.SuffixClosure(src) => closure(rec(src), prefixes = false, suffixes = true, includeEpsilon = false)
      case Space.TailsClosure(src) => closure(rec(src), prefixes = false, suffixes = true, includeEpsilon = true)
      case Space.Range(src, start, end) =>
        val source = rec(src)
        val total =
          if source.size.exact then ResultSizeEstimate.exact(SizeExpr.range(source.size.upper, start, end))
          else ResultSizeEstimate(source.size.upper, SizeExpr.Zero)
        SpatialType.fromStrata(
          source.strata.map(value => value.copy(cardinality = ResultSizeEstimate(value.cardinality.upper, SizeExpr.Zero))),
          sizeOverride = Some(total)
        )
      case Space.GroundedPS(_, _) | Space.GroundedSS(_, _) =>
        SpatialType.fromStrata(Vector(SpatialStratum(
          ResultPathLength.estimate(space, assumptions.lengthAssumptions, assumptions.pathLengthAssumptions,
            assumptions.sizeAssumptions),
          ResultSpaceSize.estimate(space, assumptions.sizeAssumptions)
        )))
