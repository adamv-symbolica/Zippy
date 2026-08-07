package morkl

import scala.collection.mutable
import scala.util.DynamicVariable

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

case class SpatialDepthDegree(depth: Int, distinctItems: ResultSizeEstimate, fibers: SpatialDegreeEstimate)

/** Resolved, optimization-facing view of a spatial type. Consumers never
  * need to know how symbolic bounds are represented. Every positive answer is
  * justified solely by the annotation-derived abstract value. */
case class SpatialFacts(value: SpatialType):
  def isDead: Boolean = value.isBottom || value.isEmpty
  def definitelyNonEmpty: Boolean = definitelyAtLeast(1)
  def definitelyAtLeast(count: Int): Boolean =
    require(count >= 0)
    value.size.lower.annotatedBound(Z3BoundDirection.Lower).exists(_ >= count)
  def definiteSize: Option[BigInt] =
    for
      lower <- value.size.lower.annotatedValue
      upper <- value.size.upper.annotatedValue
      if lower == upper
    yield lower
  def maxDepth: Option[Int] = value.pathLength.upper.annotatedBound(Z3BoundDirection.Upper)
    .filter(_.isValidInt).map(_.toInt)
  def definitePathAt(index: Int): Option[PathValue] =
    if index < 0 then None else value.exactValue.flatMap(_.paths.toVector.sortBy(_.show).lift(index))
  def commonConstantPrefix: Option[PathValue] =
    val patterns = value.strata.flatMap(_.pattern)
    Option.when(patterns.nonEmpty && patterns.size == value.strata.size) {
      val commonLength = patterns.map(_.items.length).min
      val prefix = (0 until commonLength).takeWhile { index =>
        patterns.map(_.items(index)).distinct match
          case Vector(SpatialItem.Constant(_)) => true
          case _ => false
      }.flatMap(index => patterns.head.items(index) match
        case SpatialItem.Constant(item) => Some(item)
        case _ => None).toList
      PathValue(prefix)
    }.filter(_.items.nonEmpty)
  def depthProfile: Vector[SpatialDepthDegree] =
    maxDepth.toVector.flatMap { maximum =>
      (0 until maximum).map { depth =>
        val relevant = value.strata.filter(s => s.length.upper.annotatedBound(Z3BoundDirection.Upper).forall(_ > depth))
        val choices = relevant.map { stratum =>
          stratum.pattern.flatMap(_.items.lift(depth)).fold(stratum.cardinality.upper)(SpatialFacts.itemChoices)
        }
        val upper = SizeExpr.minimum(value.size.upper, SizeExpr.add(choices*))
        val lower = if relevant.exists(_.cardinality.lower.annotatedBound(Z3BoundDirection.Lower).exists(_ > 0))
          then SizeExpr.One else SizeExpr.Zero
        SpatialDepthDegree(depth, ResultSizeEstimate(upper, lower), value.fiberDegree(depth + 1))
      }
    }

object SpatialFacts:
  private[morkl] def itemChoices(item: SpatialItem): SizeExpr = item match
    case SpatialItem.Constant(_) => SizeExpr.One
    case SpatialItem.Affine(_, _, minimum, maximum) => SizeExpr.const(BigInt(maximum) - BigInt(minimum) + 1)
    case SpatialItem.Unknown(_) => SizeExpr.Infinity

enum SpatialSpecialization:
  case TrieUnroll(maxDepth: Int, profile: Vector[SpatialDepthDegree])
  case ZipperPrefocus(prefix: PathValue)
  case GraphConstantFold(value: SpaceValue)

object SpatialBackendSelection:
  def candidates(value: SpatialType): Vector[SpatialSpecialization] =
    val facts = value.facts
    Vector(
      facts.maxDepth.map(depth => SpatialSpecialization.TrieUnroll(depth, facts.depthProfile)),
      facts.commonConstantPrefix.map(SpatialSpecialization.ZipperPrefocus.apply),
      value.exactValue.map(SpatialSpecialization.GraphConstantFold.apply),
    ).flatten

enum SpatialBackend:
  case Reference, Trie, Zipper, Graph

case class SpatialCostInterval(
  workLower: SizeExpr,
  workUpper: SizeExpr,
  allocationLower: SizeExpr,
  allocationUpper: SizeExpr,
):
  def show: String =
    s"work=[${workLower.show},${workUpper.show}], alloc=[${allocationLower.show},${allocationUpper.show}]"

/** Pointwise cost intervals plus backend-indexed views. The generic interval is
  * retained for compatibility; backend entries may be refined independently.
  */
case class SpatialCostEstimate(
  workUpper: SizeExpr,
  allocationUpper: SizeExpr,
  workLower: SizeExpr = SizeExpr.Zero,
  allocationLower: SizeExpr = SizeExpr.Zero,
  backend: Map[SpatialBackend, SpatialCostInterval] = Map.empty,
):
  def generic: SpatialCostInterval =
    SpatialCostInterval(workLower, workUpper, allocationLower, allocationUpper)
  def forBackend(value: SpatialBackend): SpatialCostInterval = backend.getOrElse(value, generic)
  def show: String =
    val base = generic.show
    if backend.isEmpty then base
    else
      val details = SpatialBackend.values.map(value =>
        s"${value.toString.toLowerCase}={${forBackend(value).show}}").mkString(", ")
      s"$base; $details"

object SpatialCostEstimate:
  val zero: SpatialCostEstimate = SpatialCostEstimate(SizeExpr.Zero, SizeExpr.Zero)
  val unknown: SpatialCostEstimate = SpatialCostEstimate(SizeExpr.Infinity, SizeExpr.Infinity)
  private val TermLimit = 128
  private def treeAdd(values: Seq[SizeExpr]): SizeExpr =
    val nonzero = values.filterNot(_ == SizeExpr.Zero)
    val sum = SizeExpr.add(nonzero*)
    if sum.nodeCount(TermLimit + 1) > TermLimit then SizeExpr.Infinity else sum

  private def lowerAdd(values: Seq[SizeExpr]): SizeExpr =
    val nonzero = values.filterNot(_ == SizeExpr.Zero)
    val sum = SizeExpr.add(nonzero*)
    if sum.nodeCount(TermLimit + 1) > TermLimit then SizeExpr.maximum(nonzero*) else sum

  def sequential(values: SpatialCostEstimate*): SpatialCostEstimate = SpatialCostEstimate(
    treeAdd(values.map(_.workUpper)),
    treeAdd(values.map(_.allocationUpper)),
    lowerAdd(values.map(_.workLower)),
    lowerAdd(values.map(_.allocationLower)),
    SpatialBackend.values.map { backend =>
      val intervals = values.map(_.forBackend(backend))
      backend -> SpatialCostInterval(
        lowerAdd(intervals.map(_.workLower)),
        treeAdd(intervals.map(_.workUpper)),
        lowerAdd(intervals.map(_.allocationLower)),
        treeAdd(intervals.map(_.allocationUpper)),
      )
    }.toMap,
  )

  def add(left: SizeExpr, right: SizeExpr): SizeExpr = treeAdd(Seq(left, right))

  def bounded(
    workLower: SizeExpr,
    workUpper: SizeExpr,
    allocationLower: SizeExpr,
    allocationUpper: SizeExpr,
  ): SpatialCostEstimate =
    val interval = SpatialCostInterval(workLower, workUpper, allocationLower, allocationUpper)
    SpatialCostEstimate(workUpper, allocationUpper, workLower, allocationLower,
      SpatialBackend.values.map(_ -> interval).toMap)

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
  def isBottom: Boolean = bottom
  def isEmpty: Boolean = !bottom && size.upper == SizeExpr.Zero
  def facts: SpatialFacts = SpatialFacts(this)
  def show: String =
    if bottom then "⊥"
    else
      val body = if strata.isEmpty then "∅" else strata.map(_.show).mkString(" ∪ ")
      s"$body; ${size.show}; ${pathLength.show}; ${cost.show}"

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
          val keys = ResultSizeEstimate(
            SizeExpr.add(keyUppers*),
            SizeExpr.positive(size.lower),
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
      var strata = value.strata.filterNot(stratum =>
        stratum.cardinality.upper == SizeExpr.Zero ||
          constantLengthContradiction(stratum.length.lower, stratum.length.upper))
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
            (r.pattern.isEmpty || r.pattern == l.pattern) &&
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
object SpatialTypeAnalysis:
  private val analysisTrace = DynamicVariable(Option.empty[mutable.ArrayBuffer[SpatialNodeAnalysis]])
  private final class AnalysisFuel(var remaining: Int)
  private val analysisFuel = DynamicVariable(Option.empty[AnalysisFuel])

  private def withinBudget[A](config: SpatialAnalysisConfig)(body: => A): A =
    analysisFuel.value match
      case Some(_) => body
      case None => analysisFuel.withValue(Some(AnalysisFuel(config.analysisNodeBudget)))(body)

  def output(
    space: Space,
    assumptions: SpatialAssumptions = SpatialAssumptions(),
    routines: PartialFunction[RoutinePtr, Routine] = PartialFunction.empty
  ): SpatialType =
    withinBudget(assumptions.config) {
      constrain(space, analyze(space, assumptions, routines, Set.empty), assumptions)
    }

  /** Analyze once while retaining every intermediate result together with the
    * lexical bindings under which it was computed. Repeated observations are
    * intentional for loop/fixpoint nodes. */
  def outputDecorated(
    space: Space,
    assumptions: SpatialAssumptions = SpatialAssumptions(),
    routines: PartialFunction[RoutinePtr, Routine] = PartialFunction.empty,
  ): DecoratedSpatialAnalysis =
    val nodes = mutable.ArrayBuffer.empty[SpatialNodeAnalysis]
    val root = analysisTrace.withValue(Some(nodes)) {
      output(space, assumptions, routines)
    }
    def children(value: Space): Vector[Space] = value match
      case Space.Call(_, _, mentions) => mentions
      case Space.Union(left, right) => Vector(left, right)
      case Space.Intersection(left, right) => Vector(left, right)
      case Space.Subtraction(left, right) => Vector(left, right)
      case Space.Restriction(left, right) => Vector(left, right)
      case Space.Raffination(left, right) => Vector(left, right)
      case Space.Composition(left, right) => Vector(left, right)
      case Space.Iteration(source, _, _, body) => Vector(source, body)
      case Space.Fold(source, _, _, _, _, body, _) => Vector(source, body)
      case Space.Fixpoint(initial, _, step) => Vector(initial, step)
      case Space.Wrap(source, _) => Vector(source)
      case Space.Unwrap(source, _) => Vector(source)
      case Space.TailsUnion(source) => Vector(source)
      case Space.TailsIntersection(source) => Vector(source)
      case Space.PrefixClosure(source) => Vector(source)
      case Space.SuffixClosure(source) => Vector(source)
      case Space.TailsClosure(source) => Vector(source)
      case Space.GroundedSS(source, _) => Vector(source)
      case Space.Range(source, _, _) => Vector(source)
      case _ => Vector.empty
    def occurrences(value: Space, position: Vector[Int]): Vector[(Space, Vector[Int])] =
      (value -> position) +: children(value).zipWithIndex.flatMap { (child, index) =>
        occurrences(child, position :+ index)
      }
    val raw = nodes.toVector
    val positional = occurrences(space, Vector.empty).map { (expression, position) =>
      val identity = raw.filter(node => node.expression.asInstanceOf[AnyRef] eq expression.asInstanceOf[AnyRef])
      val matches = if identity.nonEmpty then identity else raw.filter(_.expression == expression)
      val observations = matches.map(node => SpatialNodeObservation(node.result, node.spaces, node.paths))
      val summary = if position.isEmpty then root else observations.map(_.result).reduceOption(SpatialType.joinAlternatives)
        .getOrElse(SpatialType.top)
      val bindings = observations.headOption
      SpatialNodeAnalysis(expression, summary,
        bindings.fold(assumptions.spaces)(_.spaces), bindings.fold(assumptions.paths)(_.paths),
        position, observations)
    }
    DecoratedSpatialAnalysis(root, positional)

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
    withinBudget(assumptions.config) {
      SpatialBoundLaw.reduce(
        SpatialType.reduce(analyze(routine.body, assumptions, routines, Set(routine.name))),
        annotations.spaces,
        annotations.resultLaws,
      )
    }

  private def constrain(space: Space, raw: SpatialType, assumptions: SpatialAssumptions): SpatialType =
    val scalarSize = ResultSpaceSize.estimate(space, assumptions.sizeAssumptions)
    val scalarLength = ResultPathLength.estimate(
      space,
      assumptions.lengthAssumptions,
      assumptions.pathLengthAssumptions,
      assumptions.sizeAssumptions
    )
    SpatialType.reduce(raw.copy(
      size = if raw.size.exact then raw.size else ResultSizeEstimate(
        SizeExpr.minimum(raw.size.upper, scalarSize.upper),
        SizeExpr.maximum(raw.size.lower, scalarSize.lower)
      ),
      pathLength = if raw.pathLength.exact then raw.pathLength else PathLengthEstimate(
        PathLengthExpr.maximum(raw.pathLength.lower, scalarLength.lower),
        PathLengthExpr.minimum(raw.pathLength.upper, scalarLength.upper)
      )
    ))

  private def exactLength(length: PathLengthEstimate): Option[Int] = (length.lower, length.upper) match
    case (PathLengthExpr.Const(lower), PathLengthExpr.Const(upper)) if lower == upper && lower.isValidInt => Some(lower.toInt)
    case _ => None

  private def definitelyHeaded(length: PathLengthEstimate): Boolean =
    length.lower.annotatedBound(Z3BoundDirection.Lower).exists(_ > 0)

  private def mayBeHeaded(length: PathLengthEstimate): Boolean =
    length.upper.annotatedBound(Z3BoundDirection.Upper).forall(_ > 0)

  private def mayHaveLengthAtLeast(length: PathLengthEstimate, required: Int): Boolean =
    length.upper.annotatedBound(Z3BoundDirection.Upper).forall(_ >= required)

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
      val patterns = if product.size <= assumptions.config.patternLimit then product else Vector.empty
      SpatialPathType(
        PathLengthEstimate(PathLengthExpr.add(l.length.lower, r.length.lower), PathLengthExpr.add(l.length.upper, r.length.upper)),
        patterns
      )
    case Path.GroundedPP(_, _) | Path.GroundedSP(_, _) =>
      SpatialPathType(ResultPathLength.estimate(Space.Singleton(value), assumptions.lengthAssumptions,
        assumptions.pathLengthAssumptions, assumptions.sizeAssumptions), Vector.empty)

  private def singleton(value: SpatialPathType): SpatialType =
    val result = value.patterns match
      case Vector(pattern) => SpatialType.fromStrata(Vector(SpatialStratum(value.length,
        ResultSizeEstimate.exact(SizeExpr.One), Some(pattern))))
      case patterns if patterns.nonEmpty =>
        val optional = ResultSizeEstimate(SizeExpr.One, SizeExpr.Zero)
        SpatialType.fromStrata(patterns.map(pattern => SpatialStratum(value.length, optional, Some(pattern))),
          sizeOverride = Some(ResultSizeEstimate.exact(SizeExpr.One)), lengthOverride = Some(value.length))
      case _ => SpatialType.fromStrata(Vector(SpatialStratum(value.length, ResultSizeEstimate.exact(SizeExpr.One))))
    result.copy(cost = SpatialCostEstimate.bounded(SizeExpr.One, SizeExpr.One, SizeExpr.One, SizeExpr.One))

  private def unionCount(left: ResultSizeEstimate, right: ResultSizeEstimate): ResultSizeEstimate =
    ResultSizeEstimate(SizeExpr.add(left.upper, right.upper), SizeExpr.maximum(left.lower, right.lower))

  private def charge(
    result: SpatialType,
    inputs: SpatialType*,
  )(
    work: SizeExpr,
    allocation: SizeExpr = SizeExpr.Zero,
    workLower: SizeExpr = SizeExpr.Zero,
    allocationLower: SizeExpr = SizeExpr.Zero,
  ): SpatialType =
    val inputCost = SpatialCostEstimate.sequential(inputs.map(_.cost)*)
    val generic = SpatialCostInterval(workLower, work, allocationLower, allocation)
    val depth = result.pathLength.upper.annotatedBound(Z3BoundDirection.Upper)
      .fold[SizeExpr](SizeExpr.Infinity)(value => SizeExpr.const(value.max(BigInt(1))))
    val trie = SpatialCostInterval(
      workLower,
      SizeExpr.multiply(work, depth),
      allocationLower,
      allocation,
    )
    val zipper = trie.copy(workUpper = SpatialCostEstimate.add(trie.workUpper, depth))
    val graph = if result.exactValue.nonEmpty then
      SpatialCostInterval(SizeExpr.Zero, SizeExpr.One, SizeExpr.Zero, SizeExpr.One)
    else SpatialCostInterval(workLower, SpatialCostEstimate.add(work, SizeExpr.One),
      SizeExpr.Zero, SizeExpr.One)
    val operation = SpatialCostEstimate(work, allocation, workLower, allocationLower, Map(
      SpatialBackend.Reference -> generic,
      SpatialBackend.Trie -> trie,
      SpatialBackend.Zipper -> zipper,
      SpatialBackend.Graph -> graph,
    ))
    result.copy(cost = SpatialCostEstimate.sequential(inputCost, operation))

  private def capStrata(values: Vector[SpatialStratum], limit: Int = 64): Vector[SpatialStratum] =
    if values.size <= limit then values
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
    val result =
      if left.isBottom || right.isBottom then SpatialType.bottom
      else if left == right then left
      else if left.isEmpty then right
      else if right.isEmpty then left
      else
        val grouped = (left.strata ++ right.strata).groupBy(value => value.length -> value.pattern)
        val strata = grouped.valuesIterator.map { values =>
          val combined = values.reduce { (a, b) => a.copy(cardinality = unionCount(a.cardinality, b.cardinality)) }
          val singletonPattern = combined.pattern.flatMap(_.constantValue).nonEmpty
          if singletonPattern then combined.copy(cardinality = ResultSizeEstimate(
            SizeExpr.One,
            SizeExpr.minimum(combined.cardinality.lower, SizeExpr.One),
          ))
          else combined
        }.toVector
        SpatialType.fromStrata(capStrata(strata),
          sizeOverride = Some(unionCount(left.size, right.size)))
    charge(result, left, right)(
      SizeExpr.add(left.size.upper, right.size.upper), result.size.upper,
      SizeExpr.add(left.size.lower, right.size.lower), result.size.lower)

  private def intersection(left: SpatialType, right: SpatialType): SpatialType =
    if left.isBottom || right.isBottom then return SpatialType.bottom
    if left.isEmpty || right.isEmpty then return SpatialType.empty
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
    charge(SpatialType.reduce(raw.copy(size = ResultSizeEstimate(
      SizeExpr.minimum(raw.size.upper, left.size.upper, right.size.upper), raw.size.lower))), left, right)(
      SizeExpr.minimum(left.size.upper, right.size.upper), workLower = SizeExpr.minimum(left.size.lower, right.size.lower))

  private def subtraction(left: SpatialType, right: SpatialType): SpatialType =
    if left.isBottom || right.isBottom then return SpatialType.bottom
    if left.isEmpty || right.isEmpty then return left
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
    charge(SpatialType.reduce(raw.copy(size = ResultSizeEstimate(
      SizeExpr.minimum(raw.size.upper, left.size.upper), raw.size.lower))), left, right)(left.size.upper,
      workLower = left.size.lower)

  private def composition(left: SpatialType, right: SpatialType): SpatialType =
    if left.isBottom || right.isBottom then return SpatialType.bottom
    if left.isEmpty || right.isEmpty then return SpatialType.empty
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
    charge(SpatialType.reduce(raw.copy(size = ResultSizeEstimate(
      SizeExpr.minimum(raw.size.upper, SizeExpr.multiply(left.size.upper, right.size.upper)), raw.size.lower))), left, right)(
      SizeExpr.multiply(left.size.upper, right.size.upper), raw.size.upper)

  private def lengthsDisjoint(left: PathLengthEstimate, right: PathLengthEstimate): Boolean =
    val leftLower = left.lower.annotatedBound(Z3BoundDirection.Lower)
    val leftUpper = left.upper.annotatedBound(Z3BoundDirection.Upper)
    val rightLower = right.lower.annotatedBound(Z3BoundDirection.Lower)
    val rightUpper = right.upper.annotatedBound(Z3BoundDirection.Upper)
    (for l <- leftUpper; r <- rightLower yield l < r).contains(true) ||
      (for r <- rightUpper; l <- leftLower yield r < l).contains(true)

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
    charge(SpatialType.reduce(raw.copy(size = ResultSizeEstimate(
      SizeExpr.minimum(raw.size.upper, left.size.upper), raw.size.lower))), left, prefixes)(
      SizeExpr.multiply(left.size.upper, prefixes.size.upper))

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
      if mayHaveLengthAtLeast(value.length, prefixLength)
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
      charge(SpatialType.reduce(raw.copy(size = ResultSizeEstimate(
        SizeExpr.minimum(raw.size.upper, source.size.upper), raw.size.lower))), source)(source.size.upper, raw.size.upper)
    else
      val length = PathLengthEstimate(
        PathLengthExpr.positiveDifference(source.pathLength.lower, prefix.length.upper),
        PathLengthExpr.positiveDifference(source.pathLength.upper, prefix.length.lower)
      )
      charge(SpatialType.fromStrata(Vector(SpatialStratum(
        length, ResultSizeEstimate(source.size.upper, SizeExpr.Zero)))), source)(source.size.upper, source.size.upper)

  private def tails(source: SpatialType, intersection: Boolean): SpatialType =
    val strata = source.strata.flatMap { value =>
      Option.when(mayBeHeaded(value.length))(SpatialStratum(
        PathLengthEstimate(
          PathLengthExpr.positiveDifference(value.length.lower, PathLengthExpr.One),
          PathLengthExpr.positiveDifference(value.length.upper, PathLengthExpr.One)
        ),
        ResultSizeEstimate(value.cardinality.upper,
          if intersection || !definitelyHeaded(value.length) then SizeExpr.Zero
          else SizeExpr.positive(value.cardinality.lower)),
        value.pattern.map(_.tail)
      ))
    }
    val raw = SpatialType.fromStrata(strata)
    charge(SpatialType.reduce(raw.copy(size = ResultSizeEstimate(
      SizeExpr.minimum(raw.size.upper, source.size.upper), raw.size.lower))), source)(source.size.upper, raw.size.upper)

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
    val result = SpatialType.fromStrata(capStrata(strata))
    charge(result, source)(SizeExpr.multiply(source.size.upper, source.pathLength.upper match
      case PathLengthExpr.Const(value) => SizeExpr.const(value)
      case _ => SizeExpr.Infinity), result.size.upper)

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
  private def iterationGroups(stratum: SpatialStratum, definitelyNonEmptyPath: Boolean): ResultSizeEstimate =
    val count = stratum.cardinality
    val upper = stratum.pattern.flatMap(_.items.headOption) match
      case Some(SpatialItem.Constant(_)) => SizeExpr.positive(count.upper)
      case Some(SpatialItem.Affine(_, _, minimum, maximum)) =>
        SizeExpr.minimum(count.upper, SizeExpr.const(BigInt(maximum) - BigInt(minimum) + 1))
      case _ => count.upper
    ResultSizeEstimate(upper,
      if definitelyNonEmptyPath then SizeExpr.positive(count.lower) else SizeExpr.Zero)

  /** Cardinality of one tail fiber selected by Iteration.  A constant head
    * selects the whole represented stratum; otherwise a visited group is known
    * non-empty but may contain any number of the source paths.
    */
  private def iterationTail(stratum: SpatialStratum, definitelyNonEmptyPath: Boolean): ResultSizeEstimate =
    val lower = if definitelyNonEmptyPath then SizeExpr.positive(stratum.cardinality.lower) else SizeExpr.Zero
    stratum.pattern.flatMap(_.items.headOption) match
      case Some(SpatialItem.Constant(_)) => ResultSizeEstimate(stratum.cardinality.upper, lower)
      case _ => ResultSizeEstimate(stratum.cardinality.upper, lower)

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
      case None => SpatialType.reduce(union(ifZero, ifNonZero).copy(size = ResultSizeEstimate(
        SizeExpr.maximum(ifZero.size.upper, ifNonZero.size.upper),
        SizeExpr.minimum(ifZero.size.lower, ifNonZero.size.lower)
      )))
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

  private def fixpoint(
    initial: Space,
    variable: SpaceMention,
    step: Space,
    assumptions: SpatialAssumptions,
    routines: PartialFunction[RoutinePtr, Routine],
    active: Set[RoutinePtr],
  ): SpatialType =
    def nodes(value: Space, remaining: Int): Int =
      if remaining <= 0 then 1
      else value match
        case Space.Empty | Space.Mention(_) | Space.Singleton(_) | Space.Literal(_) |
             Space.GroundedPS(_, _) => 1
        case Space.Call(_, _, mentions) => 1 + mentions.foldLeft(0) { (sum, child) =>
          sum + nodes(child, remaining - sum - 1)
        }
        case binary @ (Space.Union(_, _) | Space.Intersection(_, _) |
             Space.Subtraction(_, _) | Space.Restriction(_, _) |
             Space.Raffination(_, _) | Space.Composition(_, _)) =>
          val (left, right) = binary match
            case Space.Union(l, r) => l -> r
            case Space.Intersection(l, r) => l -> r
            case Space.Subtraction(l, r) => l -> r
            case Space.Restriction(l, r) => l -> r
            case Space.Raffination(l, r) => l -> r
            case Space.Composition(l, r) => l -> r
          val leftSize = nodes(left, remaining - 1)
          1 + leftSize + nodes(right, remaining - leftSize - 1)
        case Space.Iteration(source, _, _, body) =>
          val sourceSize = nodes(source, remaining - 1)
          1 + sourceSize + nodes(body, remaining - sourceSize - 1)
        case Space.Fold(source, _, _, _, _, body, _) =>
          val sourceSize = nodes(source, remaining - 1)
          1 + sourceSize + nodes(body, remaining - sourceSize - 1)
        case Space.Fixpoint(seed, _, body) =>
          val seedSize = nodes(seed, remaining - 1)
          1 + seedSize + nodes(body, remaining - seedSize - 1)
        case unary @ (Space.Wrap(_, _) | Space.Unwrap(_, _) | Space.TailsUnion(_) |
             Space.TailsIntersection(_) | Space.PrefixClosure(_) |
             Space.SuffixClosure(_) | Space.TailsClosure(_) |
             Space.GroundedSS(_, _) | Space.Range(_, _, _)) =>
          val source = unary match
            case Space.Wrap(s, _) => s
            case Space.Unwrap(s, _) => s
            case Space.TailsUnion(s) => s
            case Space.TailsIntersection(s) => s
            case Space.PrefixClosure(s) => s
            case Space.SuffixClosure(s) => s
            case Space.TailsClosure(s) => s
            case Space.GroundedSS(s, _) => s
            case Space.Range(s, _, _) => s
          1 + nodes(source, remaining - 1)
    val budget = assumptions.config.fixpointAstBudget
    if nodes(initial, budget) + nodes(step, budget) > budget then
      return SpatialType.top.copy(cost = SpatialCostEstimate.unknown)
    var current = analyze(initial, assumptions, routines, active)
    var iteration = 0
    while iteration < assumptions.config.fixpointIterations do
      val nested = assumptions.copy(spaces = assumptions.spaces.updated(variable, current))
      val next = analyze(step, nested, routines, active)
      val candidate = union(current, next)
      if SpatialType.lessOrEqual(candidate, current) then return current.copy(cost = SpatialCostEstimate.unknown)
      iteration += 1
      if iteration >= assumptions.config.fixpointWidenAfter then
        val widened = SpatialType.widenCardinalities(candidate)
        val widenedAssumptions = assumptions.copy(spaces = assumptions.spaces.updated(variable, widened))
        val widenedStep = analyze(step, widenedAssumptions, routines, active)
        val post = union(widened, widenedStep)
        if SpatialType.lessOrEqual(post, widened) then return widened.copy(cost = SpatialCostEstimate.unknown)
        current = post
      else current = candidate
    // A finite cap is a resource guard, never a semantic assumption. If the
    // post-fixpoint check did not succeed, forget every component.
    SpatialType.top.copy(cost = SpatialCostEstimate.unknown)

  private def analyze(
    space: Space,
    assumptions: SpatialAssumptions,
    routines: PartialFunction[RoutinePtr, Routine],
    active: Set[RoutinePtr]
  ): SpatialType =
    analysisFuel.value match
      case Some(fuel) if fuel.remaining <= 0 =>
        return SpatialType.top.copy(cost = SpatialCostEstimate.unknown)
      case Some(fuel) => fuel.remaining -= 1
      case None => ()
    def rec(next: Space): SpatialType = analyze(next, assumptions, routines, active)
    val result = space match
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
      case Space.Literal(value) => SpatialType.exact(value, assumptions.config.patternLimit).copy(
        cost = SpatialCostEstimate.bounded(
          SizeExpr.const(value.paths.size), SizeExpr.const(value.paths.size),
          SizeExpr.const(value.paths.size), SizeExpr.const(value.paths.size)))
      case Space.Union(left, right) =>
        right match
          case candidate if matchIfEmpty(candidate).exists(_._1 == left) =>
            val fallback = matchIfEmpty(candidate).get._2
            conditional(rec(left), analyze(fallback, assumptions, routines, active), rec(left))
          case _ if matchIfEmpty(left).exists(_._1 == right) =>
            val fallback = matchIfEmpty(left).get._2
            conditional(rec(right), analyze(fallback, assumptions, routines, active), rec(right))
          case _ if left == right => rec(left)
          case _ => union(rec(left), rec(right))
      case Space.Intersection(left, right) =>
        if left == right then rec(left)
        else intersection(rec(left), rec(right))
      case Space.Subtraction(left, right) =>
        if left == right then SpatialType.empty
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
              if !mayBeHeaded(stratum.length) then Vector.empty
              else
                val definitelyNonEmptyPath = definitelyHeaded(stratum.length)
                val headPattern = stratum.pattern.flatMap(_.items.headOption).map(item => SpatialPattern(Vector(item)))
                val headType = SpatialPathType(PathLengthEstimate.exact(PathLengthExpr.One), headPattern.toVector)
                val tailLength = PathLengthEstimate(
                  PathLengthExpr.positiveDifference(stratum.length.lower, PathLengthExpr.One),
                  PathLengthExpr.positiveDifference(stratum.length.upper, PathLengthExpr.One)
                )
                val tailCardinality = iterationTail(stratum, definitelyNonEmptyPath)
                val tailType = SpatialType.fromStrata(Vector(SpatialStratum(tailLength, tailCardinality, stratum.pattern.map(_.tail))))
                val nested = assumptions.copy(
                  spaces = assumptions.spaces.updated(rest, tailType),
                  paths = assumptions.paths.updated(symbol, headType)
                )
                val branch = analyze(body, nested, routines, active)
                val groups = iterationGroups(stratum, definitelyNonEmptyPath)
                branch.strata.map(value => value.copy(cardinality = ResultSizeEstimate(
                  SizeExpr.multiply(groups.upper, value.cardinality.upper),
                  SizeExpr.multiply(groups.lower, value.cardinality.lower)
                )))
            }
            val general = SpatialType.fromStrata(capStrata(branches, assumptions.config.patternLimit))
            pointwiseIterationUpper(source, symbol, rest, body, assumptions, routines, active) match
              case Some(upper) => SpatialType.reduce(general.copy(size = ResultSizeEstimate(
                SizeExpr.minimum(general.size.upper, upper), general.size.lower)))
              case None => general
      case Space.Fold(src, initial, acc, symbol, rest, body, _) =>
        val source = rec(src)
        val initialType = path(initial, assumptions, routines, active)
        val nested = assumptions.copy(
          spaces = assumptions.spaces.updated(rest, source),
          paths = assumptions.paths.updated(acc, initialType).updated(symbol, SpatialPathType.length(1, symbol.s))
        )
        val branch = analyze(body, nested, routines, active)
        val optionalBranch = branch.copy(
          strata = branch.strata.map(stratum => stratum.copy(cardinality =
            ResultSizeEstimate(stratum.cardinality.upper, SizeExpr.Zero))),
          size = ResultSizeEstimate(SizeExpr.multiply(source.size.upper, branch.size.upper), SizeExpr.Zero),
        )
        charge(SpatialType.reduce(optionalBranch), source, branch)(
          SizeExpr.multiply(source.size.upper, branch.cost.workUpper))
      case Space.Fixpoint(initial, variable, step) =>
        fixpoint(initial, variable, step, assumptions, routines, active)
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
        charge(SpatialType.fromStrata(
          source.strata.map(value => value.copy(cardinality = ResultSizeEstimate(value.cardinality.upper, SizeExpr.Zero))),
          sizeOverride = Some(total)
        ), source)(source.size.upper, total.upper)
      case Space.GroundedPS(_, _) | Space.GroundedSS(_, _) =>
        SpatialType.fromStrata(Vector(SpatialStratum(
          ResultPathLength.estimate(space, assumptions.lengthAssumptions, assumptions.pathLengthAssumptions,
            assumptions.sizeAssumptions),
          ResultSpaceSize.estimate(space, assumptions.sizeAssumptions)
        ))).copy(cost = SpatialCostEstimate.unknown)
    analysisTrace.value.foreach(_ += SpatialNodeAnalysis(
      space, result, assumptions.spaces, assumptions.paths))
    result
