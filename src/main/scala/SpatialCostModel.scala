package morkl

enum SpatialBackend:
  case Reference, Trie, Zipper, Graph

case class SpatialCostInterval(
  workLower: SizeExpr,
  workUpper: SizeExpr,
  allocationLower: SizeExpr,
  allocationUpper: SizeExpr,
  roundsLower: SizeExpr = SizeExpr.Zero,
  roundsUpper: SizeExpr = SizeExpr.Zero,
):
  def show: String =
    s"work=[${workLower.show},${workUpper.show}], alloc=[${allocationLower.show},${allocationUpper.show}], rounds=[${roundsLower.show},${roundsUpper.show}]"

case class SpatialCostEstimate(
  workUpper: SizeExpr,
  allocationUpper: SizeExpr,
  workLower: SizeExpr = SizeExpr.Zero,
  allocationLower: SizeExpr = SizeExpr.Zero,
  backend: Map[SpatialBackend, SpatialCostInterval] = Map.empty,
  roundsUpper: SizeExpr = SizeExpr.Zero,
  roundsLower: SizeExpr = SizeExpr.Zero,
):
  def generic: SpatialCostInterval =
    SpatialCostInterval(workLower, workUpper, allocationLower, allocationUpper, roundsLower, roundsUpper)
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
  val unknown: SpatialCostEstimate = SpatialCostEstimate(SizeExpr.Infinity, SizeExpr.Infinity,
    roundsUpper = SizeExpr.Infinity)

  /** Exact finite sums in a shallow chunked tree: no notation-driven
    * `TermLimit => Infinity`, and no repeated copying of one flat vector. */
  private def treeAdd(values: Seq[SizeExpr]): SizeExpr =
    val direct = values.filterNot(_ == SizeExpr.Zero).flatMap {
      case SizeExpr.Add(terms) => terms
      case value => Vector(value)
    }
    if direct.contains(SizeExpr.Infinity) then SizeExpr.Infinity
    else if direct.exists(_.nodeCount(65) > 64) then
      // A named atom denotes this finite sum without distributing/copying its
      // representation. Unlike the removed cliff, this is not semantic ∞.
      SizeExpr.symbol(s"finiteCostSum(${direct.size})")
    else
      val (constants, symbolic) = direct.partition(_.isInstanceOf[SizeExpr.Const])
      val constant = constants.collect { case SizeExpr.Const(value) => value }.sum
      val terms = (Option.when(constant > 0)(SizeExpr.const(constant)).toVector ++ symbolic).toVector
      def chunk(items: Vector[SizeExpr]): SizeExpr = items match
        case Vector() => SizeExpr.Zero
        case Vector(value) => value
        case values if values.size <= 32 => SizeExpr.Add(values)
        case values => SizeExpr.Add(values.grouped(32).map(group => chunk(group.toVector)).toVector)
      chunk(terms)

  private def lowerAdd(values: Seq[SizeExpr]): SizeExpr = treeAdd(values)

  def sequential(values: SpatialCostEstimate*): SpatialCostEstimate = SpatialCostEstimate(
    treeAdd(values.map(_.workUpper)), treeAdd(values.map(_.allocationUpper)),
    lowerAdd(values.map(_.workLower)), lowerAdd(values.map(_.allocationLower)),
    SpatialBackend.values.map { backend =>
      val intervals = values.map(_.forBackend(backend))
      backend -> SpatialCostInterval(
        lowerAdd(intervals.map(_.workLower)), treeAdd(intervals.map(_.workUpper)),
        lowerAdd(intervals.map(_.allocationLower)), treeAdd(intervals.map(_.allocationUpper)),
        lowerAdd(intervals.map(_.roundsLower)), treeAdd(intervals.map(_.roundsUpper)),
      )
    }.toMap,
    treeAdd(values.map(_.roundsUpper)), lowerAdd(values.map(_.roundsLower)),
  )

  def scale(value: SpatialCostEstimate, lower: SizeExpr, upper: SizeExpr): SpatialCostEstimate =
    def scaleInterval(interval: SpatialCostInterval): SpatialCostInterval = SpatialCostInterval(
      SizeExpr.multiply(lower, interval.workLower), SizeExpr.multiply(upper, interval.workUpper),
      SizeExpr.multiply(lower, interval.allocationLower), SizeExpr.multiply(upper, interval.allocationUpper),
      SizeExpr.multiply(lower, interval.roundsLower), SizeExpr.multiply(upper, interval.roundsUpper),
    )
    val generic = scaleInterval(value.generic)
    SpatialCostEstimate(generic.workUpper, generic.allocationUpper, generic.workLower, generic.allocationLower,
      SpatialBackend.values.map(backend => backend -> scaleInterval(value.forBackend(backend))).toMap,
      generic.roundsUpper, generic.roundsLower)

  def withRounds(value: SpatialCostEstimate, lower: SizeExpr, upper: SizeExpr): SpatialCostEstimate =
    value.copy(roundsLower = lower, roundsUpper = upper,
      backend = value.backend.view.mapValues(_.copy(roundsLower = lower, roundsUpper = upper)).toMap)

  def add(left: SizeExpr, right: SizeExpr): SizeExpr = treeAdd(Seq(left, right))

  def bounded(workLower: SizeExpr, workUpper: SizeExpr,
    allocationLower: SizeExpr, allocationUpper: SizeExpr): SpatialCostEstimate =
    val interval = SpatialCostInterval(workLower, workUpper, allocationLower, allocationUpper)
    SpatialCostEstimate(workUpper, allocationUpper, workLower, allocationLower,
      SpatialBackend.values.map(_ -> interval).toMap)

/** Operation classes whose costs are derived from executor behaviour. */
enum SpatialCostOperation:
  case Generic, Union, Intersection, Subtraction, Restriction, Composition,
    Wrap, Unwrap, Range, GroupCollect, Fixpoint

case class SpatialCostMeasure(size: ResultSizeEstimate, length: PathLengthEstimate, heads: ResultSizeEstimate):
  def nodesUpper: SizeExpr = SizeExpr.multiply(size.upper, lengthUpper)
  def nodesLower: SizeExpr = SizeExpr.multiply(size.lower, lengthLower)
  def lengthUpper: SizeExpr = length.upper.annotatedBound(Z3BoundDirection.Upper)
    .fold[SizeExpr](SizeExpr.symbol(s"len(${length.show})"))(SizeExpr.const)
  def lengthLower: SizeExpr = SizeExpr.const(length.lower.annotatedBound(Z3BoundDirection.Lower).getOrElse(BigInt(0)))

object SpatialCostMeasure:
  def apply(value: SpatialType): SpatialCostMeasure = SpatialCostMeasure(value.size, value.pathLength,
    ResultSizeEstimate(value.size.upper, SizeExpr.positive(value.size.lower)))

/** One independently testable model per concrete executor. */
trait SpatialCostModel:
  def backend: SpatialBackend
  def operation(
    kind: SpatialCostOperation,
    inputs: Vector[SpatialCostMeasure],
    result: SpatialCostMeasure,
    fallback: SpatialCostInterval,
  ): SpatialCostInterval

object SpatialCostModels:
  private def add(values: SizeExpr*): SizeExpr = SizeExpr.add(values*)
  private def mul(values: SizeExpr*): SizeExpr = SizeExpr.multiply(values*)
  private def binary(inputs: Vector[SpatialCostMeasure]): Option[(SpatialCostMeasure, SpatialCostMeasure)] =
    inputs match
      case Vector(left, right, _*) => Some(left -> right)
      case _ => None

  object Reference extends SpatialCostModel:
    val backend = SpatialBackend.Reference
    def operation(kind: SpatialCostOperation, inputs: Vector[SpatialCostMeasure], result: SpatialCostMeasure,
      fallback: SpatialCostInterval): SpatialCostInterval =
      binary(inputs) match
        case Some((left, right)) =>
          val work = kind match
            case SpatialCostOperation.Composition => mul(left.size.upper, right.size.upper, add(left.lengthUpper, right.lengthUpper))
            case SpatialCostOperation.Restriction => mul(left.size.upper, right.size.upper, right.lengthUpper)
            case SpatialCostOperation.Union | SpatialCostOperation.Intersection | SpatialCostOperation.Subtraction =>
              add(left.size.upper, right.size.upper)
            case _ => fallback.workUpper
          val allocation = kind match
            case SpatialCostOperation.Intersection => SizeExpr.minimum(left.size.upper, right.size.upper)
            case SpatialCostOperation.Subtraction => left.size.upper
            case SpatialCostOperation.Composition | SpatialCostOperation.Restriction => result.size.upper
            case SpatialCostOperation.Union => add(left.size.upper, right.size.upper)
            case _ => fallback.allocationUpper
          fallback.copy(workUpper = work, allocationUpper = allocation)
        case None => kind match
          case SpatialCostOperation.Range if inputs.nonEmpty =>
            val source = inputs.head
            val log = SizeExpr.symbol(s"log2ceil(${source.size.upper.show})")
            fallback.copy(workUpper = mul(source.size.upper, log, source.lengthUpper))
          case _ => fallback

  object Trie extends SpatialCostModel:
    val backend = SpatialBackend.Trie
    def operation(kind: SpatialCostOperation, inputs: Vector[SpatialCostMeasure], result: SpatialCostMeasure,
      fallback: SpatialCostInterval): SpatialCostInterval =
      binary(inputs) match
        case Some((left, right)) =>
          val work = kind match
            case SpatialCostOperation.Composition => add(left.nodesUpper, mul(left.size.upper, right.nodesUpper))
            case SpatialCostOperation.Restriction => add(left.nodesUpper, right.nodesUpper)
            case SpatialCostOperation.Union | SpatialCostOperation.Intersection | SpatialCostOperation.Subtraction =>
              add(left.nodesUpper, right.nodesUpper)
            case _ => fallback.workUpper
          fallback.copy(workUpper = work, allocationUpper = result.nodesUpper)
        case None => kind match
          case SpatialCostOperation.Range if inputs.nonEmpty =>
            fallback.copy(workUpper = add(inputs.head.size.upper, inputs.head.lengthUpper), allocationUpper = result.nodesUpper)
          case _ => fallback.copy(allocationUpper = result.nodesUpper)

  object Zipper extends SpatialCostModel:
    val backend = SpatialBackend.Zipper
    def operation(kind: SpatialCostOperation, inputs: Vector[SpatialCostMeasure], result: SpatialCostMeasure,
      fallback: SpatialCostInterval): SpatialCostInterval =
      val trie = Trie.operation(kind, inputs, result, fallback)
      kind match
        case SpatialCostOperation.Unwrap => trie.copy(workUpper = result.lengthUpper)
        case _ => trie.copy(workUpper = add(trie.workUpper, result.lengthUpper))

  object Graph extends SpatialCostModel:
    val backend = SpatialBackend.Graph
    def operation(kind: SpatialCostOperation, inputs: Vector[SpatialCostMeasure], result: SpatialCostMeasure,
      fallback: SpatialCostInterval): SpatialCostInterval = kind match
      case SpatialCostOperation.Union => fallback.copy(workUpper = SizeExpr.const(inputs.size), allocationUpper = SizeExpr.One)
      case SpatialCostOperation.Composition => fallback.copy(
        workUpper = add(inputs.map(_.nodesUpper)*), allocationUpper = SizeExpr.One)
      case _ => fallback.copy(workUpper = add((inputs.map(_.nodesUpper) :+ SizeExpr.One)*), allocationUpper = SizeExpr.One)

  val all: Vector[SpatialCostModel] = Vector(Reference, Trie, Zipper, Graph)

/** Closed forms used by recursive and fixed-point cost transfers. */
object SpatialRecurrence:
  def solve(additive: SizeExpr, branching: SizeExpr, rounds: SizeExpr): SizeExpr =
    branching.annotatedValue match
      case Some(value) if value == 0 => additive
      case Some(value) if value == 1 => SizeExpr.multiply(additive, rounds)
      case Some(value) => SizeExpr.multiply(additive, SizeExpr.symbol(s"geom($value,${rounds.show})"))
      case None => SizeExpr.multiply(additive, SizeExpr.symbol(s"geom(${branching.show},${rounds.show})"))
