/** Symbolic natural-number expressions used by result-space cardinality analysis.
  * `None` from [[evaluate]] denotes an unbounded/unknown finite upper bound.
  */
enum SizeExpr:
  case Const(value: BigInt)
  case Symbol(name: String)
  case SizeOf(space: Space)
  case Add(terms: Vector[SizeExpr])
  case Multiply(factors: Vector[SizeExpr])
  case Maximum(terms: Vector[SizeExpr])
  case Minimum(terms: Vector[SizeExpr])
  case PositiveDifference(left: SizeExpr, right: SizeExpr)
  case Positive(value: SizeExpr)
  case RangeCardinality(value: SizeExpr, start: Int, end: Int)
  case IfZero(condition: SizeExpr, ifZero: SizeExpr, ifNonZero: SizeExpr)
  case Z3Cardinality(problem: Z3CardinalityProblem, direction: Z3BoundDirection, baseline: SizeExpr)
  case Infinity

  /** Resolve only information already present in the abstract expression.
    * Unlike `evaluate`, this never interprets a MORKL path or space. */
  def annotatedValue: Option[BigInt] = this match
    case SizeExpr.Const(value) => Some(value)
    case SizeExpr.Symbol(_) | SizeExpr.SizeOf(_) | SizeExpr.Infinity => None
    case SizeExpr.Add(terms) =>
      terms.foldLeft(Option(BigInt(0)))((sum, term) => for a <- sum; b <- term.annotatedValue yield a + b)
    case SizeExpr.Multiply(factors) =>
      val values = factors.map(_.annotatedValue)
      if values.contains(Some(BigInt(0))) then Some(BigInt(0))
      else values.foldLeft(Option(BigInt(1)))((product, value) => for a <- product; b <- value yield a * b)
    case SizeExpr.Maximum(terms) =>
      if terms.exists(_.annotatedValue.isEmpty) then None else terms.flatMap(_.annotatedValue).maxOption
    case SizeExpr.Minimum(terms) =>
      val values = terms.map(_.annotatedValue)
      if values.contains(Some(BigInt(0))) then Some(BigInt(0))
      else if values.forall(_.nonEmpty) then values.flatten.minOption
      else None
    case SizeExpr.PositiveDifference(left, right) =>
      (left.annotatedValue, right.annotatedValue) match
        case (Some(a), Some(b)) => Some((a - b).max(BigInt(0)))
        case (Some(a), None) if a == 0 => Some(BigInt(0))
        case _ => None
    case SizeExpr.Positive(value) =>
      value.annotatedValue.map(v => if v > 0 then BigInt(1) else BigInt(0))
    case SizeExpr.RangeCardinality(value, start, end) =>
      value.annotatedValue.map(SizeExpr.rangeCardinality(_, start, end))
    case SizeExpr.IfZero(condition, ifZero, ifNonZero) =>
      condition.annotatedValue match
        case Some(value) => (if value == 0 then ifZero else ifNonZero).annotatedValue
        case None if ifZero == ifNonZero => ifZero.annotatedValue
        case None => None
    case SizeExpr.Z3Cardinality(problem, direction, baseline) =>
      val base = baseline.annotatedValue
      val solved = problem.solveAnnotated(direction)
      direction match
        case Z3BoundDirection.Lower => (base, solved) match
          case (Some(a), Some(b)) => Some(a.max(b))
          case (Some(a), None) => Some(a)
          case (None, Some(b)) => Some(b)
          case (None, None) => None
        case Z3BoundDirection.Upper => (base, solved) match
          case (Some(a), Some(b)) => Some(a.min(b))
          case (Some(a), None) => Some(a)
          case (None, Some(b)) => Some(b)
          case (None, None) => None

  /** Resolve a sound one-sided bound using only annotations. Unknown natural
    * values contribute zero to lower bounds and infinity to upper bounds. */
  def annotatedBound(direction: Z3BoundDirection): Option[BigInt] =
    def lower(value: SizeExpr): BigInt = value match
      case SizeExpr.Const(n) => n
      case SizeExpr.Symbol(_) | SizeExpr.SizeOf(_) | SizeExpr.Infinity => BigInt(0)
      case SizeExpr.Add(terms) => terms.map(lower).sum
      case SizeExpr.Multiply(factors) => factors.map(lower).product
      case SizeExpr.Maximum(terms) => terms.map(lower).maxOption.getOrElse(BigInt(0))
      case SizeExpr.Minimum(terms) => terms.map(lower).minOption.getOrElse(BigInt(0))
      case SizeExpr.PositiveDifference(left, right) =>
        upper(right).fold(BigInt(0))(r => (lower(left) - r).max(BigInt(0)))
      case SizeExpr.Positive(inner) => if lower(inner) > 0 then BigInt(1) else BigInt(0)
      case SizeExpr.RangeCardinality(inner, start, end) =>
        inner.annotatedValue.map(SizeExpr.rangeCardinality(_, start, end)).getOrElse(BigInt(0))
      case SizeExpr.IfZero(condition, ifZero, ifNonZero) =>
        condition.annotatedValue match
          case Some(n) => lower(if n == 0 then ifZero else ifNonZero)
          case None => lower(ifZero).min(lower(ifNonZero))
      case value @ SizeExpr.Z3Cardinality(_, storedDirection, _) =>
        value.annotatedValue.orElse(z3Bound(value, storedDirection)).getOrElse(BigInt(0))
    def upper(value: SizeExpr): Option[BigInt] = value match
      case SizeExpr.Const(n) => Some(n)
      case SizeExpr.Symbol(_) | SizeExpr.SizeOf(_) | SizeExpr.Infinity => None
      case SizeExpr.Add(terms) =>
        terms.foldLeft(Option(BigInt(0)))((sum, term) => for a <- sum; b <- upper(term) yield a + b)
      case SizeExpr.Multiply(factors) =>
        val values = factors.map(upper)
        if values.contains(Some(BigInt(0))) then Some(BigInt(0))
        else values.foldLeft(Option(BigInt(1)))((product, factor) => for a <- product; b <- factor yield a * b)
      case SizeExpr.Maximum(terms) =>
        val values = terms.map(upper)
        if values.forall(_.nonEmpty) then values.flatten.maxOption else None
      case SizeExpr.Minimum(terms) => terms.flatMap(upper).minOption
      case SizeExpr.PositiveDifference(left, _) => upper(left)
      case SizeExpr.Positive(inner) => upper(inner).map(n => if n == 0 then BigInt(0) else BigInt(1)).orElse(Some(BigInt(1)))
      case SizeExpr.RangeCardinality(inner, start, end) =>
        inner.annotatedValue.map(SizeExpr.rangeCardinality(_, start, end)).orElse(upper(inner))
      case SizeExpr.IfZero(condition, ifZero, ifNonZero) =>
        condition.annotatedValue match
          case Some(n) => upper(if n == 0 then ifZero else ifNonZero)
          case None => for a <- upper(ifZero); b <- upper(ifNonZero) yield a.max(b)
      case value @ SizeExpr.Z3Cardinality(_, storedDirection, _) =>
        value.annotatedValue.orElse(z3Bound(value, storedDirection))
    def z3Bound(value: SizeExpr, storedDirection: Z3BoundDirection): Option[BigInt] = value match
      case SizeExpr.Z3Cardinality(problem, _, baseline) =>
        val base = storedDirection match
          case Z3BoundDirection.Lower => Some(lower(baseline))
          case Z3BoundDirection.Upper => upper(baseline)
        val solved = problem.solveAnnotated(storedDirection)
        storedDirection match
          case Z3BoundDirection.Lower => Some(base.getOrElse(BigInt(0)).max(solved.getOrElse(BigInt(0))))
          case Z3BoundDirection.Upper => (base, solved) match
            case (Some(a), Some(b)) => Some(a.min(b))
            case (some @ Some(_), None) => some
            case (None, some @ Some(_)) => some
            case _ => None
      case _ => None
    direction match
      case Z3BoundDirection.Lower => Some(lower(this))
      case Z3BoundDirection.Upper => upper(this)

  def show: String = this match
    case SizeExpr.Const(value) => value.toString
    case SizeExpr.Symbol(name) => name
    case SizeExpr.SizeOf(space) => s"|${space.show}|"
    case SizeExpr.Add(terms) => terms.map(_.show).mkString("(", " + ", ")")
    case SizeExpr.Multiply(factors) => factors.map(_.show).mkString("(", " * ", ")")
    case SizeExpr.Maximum(terms) => terms.map(_.show).mkString("max(", ", ", ")")
    case SizeExpr.Minimum(terms) => terms.map(_.show).mkString("min(", ", ", ")")
    case SizeExpr.PositiveDifference(left, right) => s"relu(${left.show} - ${right.show})"
    case SizeExpr.Positive(value) => s"positive(${value.show})"
    case SizeExpr.RangeCardinality(value, start, end) => s"rangeSize(${value.show}, $start, $end)"
    case SizeExpr.IfZero(condition, ifZero, ifNonZero) =>
      s"ifZero(${condition.show}, ${ifZero.show}, ${ifNonZero.show})"
    case SizeExpr.Z3Cardinality(problem, direction, baseline) =>
      s"z3${direction.toString.toLowerCase}(${problem.show}; baseline=${baseline.show})"
    case SizeExpr.Infinity => "∞"

  def evaluate(using
    pc: PathContext = PathContextMap(Map.empty),
    sc: SpaceContext = SpaceContextMap(Map.empty),
    rc: PartialFunction[RoutinePtr, Routine] = PartialFunction.empty
  ): Option[BigInt] = this match
    case SizeExpr.Const(value) => Some(value)
    case SizeExpr.Symbol(_) => None
    case SizeExpr.SizeOf(space) => Some(BigInt(eval(space).paths.size))
    case SizeExpr.Add(terms) =>
      terms.foldLeft(Option(BigInt(0)))((sum, term) => for a <- sum; b <- term.evaluate yield a + b)
    case SizeExpr.Multiply(factors) =>
      val values = factors.map(_.evaluate)
      if values.contains(Some(BigInt(0))) then Some(BigInt(0))
      else values.foldLeft(Option(BigInt(1)))((product, value) => for a <- product; b <- value yield a * b)
    case SizeExpr.Maximum(terms) =>
      terms.foldLeft(Option(BigInt(0)))((maximum, term) => for a <- maximum; b <- term.evaluate yield a.max(b))
    case SizeExpr.Minimum(terms) =>
      val finite = terms.flatMap(_.evaluate)
      if finite.nonEmpty then Some(finite.min) else None
    case SizeExpr.PositiveDifference(left, right) =>
      (left.evaluate, right.evaluate) match
        case (Some(a), Some(b)) => Some((a - b).max(BigInt(0)))
        case (None, Some(_)) => None
        case (Some(_), None) => Some(BigInt(0))
        case (None, None) => None
    case SizeExpr.Positive(value) =>
      value.evaluate.map(v => if v > 0 then BigInt(1) else BigInt(0)).orElse(Some(BigInt(1)))
    case SizeExpr.RangeCardinality(value, start, end) =>
      value.evaluate.map(SizeExpr.rangeCardinality(_, start, end))
    case SizeExpr.IfZero(condition, ifZero, ifNonZero) =>
      condition.evaluate match
        case Some(value) => (if value == 0 then ifZero else ifNonZero).evaluate
        case None if ifZero == ifNonZero => ifZero.evaluate
        case None => None
    case SizeExpr.Z3Cardinality(problem, direction, baseline) =>
      val base = baseline.evaluate
      val solved = problem.solve(direction)
      direction match
        case Z3BoundDirection.Lower => (base, solved) match
          case (Some(a), Some(b)) => Some(a.max(b))
          case (Some(a), None) => Some(a)
          case (None, Some(b)) => Some(b)
          case (None, None) => None
        case Z3BoundDirection.Upper => (base, solved) match
          case (Some(a), Some(b)) => Some(a.min(b))
          case (Some(a), None) => Some(a)
          case (None, Some(b)) => Some(b)
          case (None, None) => None
    case SizeExpr.Infinity => None

object SizeExpr:
  val Zero: SizeExpr = SizeExpr.Const(0)
  val One: SizeExpr = SizeExpr.Const(1)

  def const(value: BigInt): SizeExpr =
    require(value >= 0, s"size expressions must be non-negative, got $value")
    SizeExpr.Const(value)

  def sizeOf(space: Space): SizeExpr = SizeExpr.SizeOf(space)
  def symbol(name: String): SizeExpr =
    require(name.nonEmpty, "size symbol must have a name")
    SizeExpr.Symbol(name)

  private def sameInstance(left: SizeExpr, right: SizeExpr): Boolean =
    left.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef]

  def add(values: SizeExpr*): SizeExpr =
    val terms = values.iterator.flatMap {
      case SizeExpr.Add(nested) => nested
      case other => Vector(other)
    }.filterNot(_ == Zero).toVector
    terms match
      case Vector() => Zero
      case Vector(term) => term
      case result if result.forall(_.isInstanceOf[SizeExpr.Const]) =>
        const(result.collect { case SizeExpr.Const(value) => value }.sum)
      case result => SizeExpr.Add(result)

  def multiply(values: SizeExpr*): SizeExpr =
    val factors = values.iterator.flatMap {
      case SizeExpr.Multiply(nested) => nested
      case other => Vector(other)
    }.toVector
    if factors.contains(Zero) then Zero
    else
      val result = factors.filterNot(_ == One).foldLeft(Vector.empty[SizeExpr]) { (kept, factor) =>
        val idempotent = factor match
          case SizeExpr.Positive(_) => true
          case SizeExpr.PositiveDifference(SizeExpr.Const(one), _) if one == 1 => true
          case _ => false
        if idempotent && kept.contains(factor) then kept else kept :+ factor
      }
      if result.exists { factor =>
        result.exists {
          case SizeExpr.PositiveDifference(SizeExpr.Const(one), other) if one == 1 => other == factor
          case _ => false
        }
      } then Zero
      else result match
        case Vector() => One
        case Vector(factor) => factor
        case xs if xs.forall(_.isInstanceOf[SizeExpr.Const]) =>
          const(xs.collect { case SizeExpr.Const(value) => value }.product)
        case _ => SizeExpr.Multiply(result)

  private def exclusiveMaximum(left: SizeExpr, right: SizeExpr): Option[SizeExpr] =
    def guardedByZero(value: SizeExpr, guarded: SizeExpr): Boolean = guarded match
      case SizeExpr.PositiveDifference(SizeExpr.Const(one), `value`) if one == 1 => true
      case SizeExpr.Multiply(factors) => factors.exists {
        case SizeExpr.PositiveDifference(SizeExpr.Const(one), `value`) if one == 1 => true
        case _ => false
      }
      case _ => false
    if guardedByZero(left, right) then Some(add(left, right))
    else if guardedByZero(right, left) then Some(add(left, right))
    else None

  private def factors(value: SizeExpr): Vector[SizeExpr] = value match
    case SizeExpr.Multiply(values) => values
    case other => Vector(other)

  /** A deliberately incomplete, but sound, order decision procedure for
    * natural-valued expressions.  It is used only for normalization; failure
    * to prove an order leaves both alternatives in place.
    */
  private def noGreater(left: SizeExpr, right: SizeExpr): Boolean =
    if left == right || left == Zero || right == SizeExpr.Infinity then true
    else (left, right) match
      case (SizeExpr.Const(a), SizeExpr.Const(b)) => a <= b
      case (SizeExpr.Positive(value), other) if value == other => true
      case (SizeExpr.Minimum(leftTerms), SizeExpr.Minimum(rightTerms)) =>
        rightTerms.forall(rightTerm => leftTerms.exists(noGreater(_, rightTerm)))
      case (SizeExpr.Minimum(terms), other) => terms.exists(noGreater(_, other))
      case (other, SizeExpr.Minimum(terms)) => terms.forall(noGreater(other, _))
      case (SizeExpr.Maximum(terms), other) => terms.forall(noGreater(_, other))
      case (other, SizeExpr.Maximum(terms)) => terms.exists(noGreater(other, _))
      case (l, r) =>
        val remaining = collection.mutable.ArrayBuffer.from(factors(r))
        val unmatched = factors(l).filter { factor =>
          val index = remaining.indexOf(factor)
          if index < 0 then true
          else
            remaining.remove(index)
            false
        }
        if unmatched.size == factors(l).size then false
        else noGreater(multiply(unmatched*), multiply(remaining.toVector*))

  private def undominatedMinimum(values: Vector[SizeExpr]): Vector[SizeExpr] =
    values.filterNot(value => values.exists(other => other != value && noGreater(other, value)))

  private def undominatedMaximum(values: Vector[SizeExpr]): Vector[SizeExpr] =
    values.filterNot(value => values.exists(other => other != value && noGreater(value, other)))

  def maximum(values: SizeExpr*): SizeExpr =
    val flattened = values.iterator.flatMap {
      case SizeExpr.Maximum(nested) => nested
      case other => Vector(other)
    }.filterNot(_ == Zero).toVector.distinct
    val terms = undominatedMaximum(flattened)
    if terms.contains(SizeExpr.Infinity) then SizeExpr.Infinity
    else
      terms match
        case Vector() => Zero
        case Vector(value) => value
        case result if result.forall(_.isInstanceOf[SizeExpr.Const]) =>
          const(result.collect { case SizeExpr.Const(value) => value }.max)
        case Vector(left, right) if sameInstance(left, right) => left
        case Vector(left, right) => exclusiveMaximum(left, right).getOrElse(SizeExpr.Maximum(terms))
        case result => SizeExpr.Maximum(result)

  def minimum(values: SizeExpr*): SizeExpr =
    val flattened = values.iterator.flatMap {
      case SizeExpr.Minimum(nested) => nested
      case other => Vector(other)
    }.filterNot(_ == SizeExpr.Infinity).toVector.distinct
    val terms = undominatedMinimum(flattened)
    if terms.contains(Zero) then Zero
    else terms match
      case Vector() => SizeExpr.Infinity
      case Vector(value) => value
      case result if result.forall(_.isInstanceOf[SizeExpr.Const]) =>
        const(result.collect { case SizeExpr.Const(value) => value }.min)
      case Vector(left, right) if sameInstance(left, right) => left
      case Vector(left, right) if additiveSubset(left, right) => left
      case Vector(left, right) if additiveSubset(right, left) => right
      case result => SizeExpr.Minimum(result)

  private def additiveSubset(left: SizeExpr, right: SizeExpr): Boolean =
    def terms(value: SizeExpr): Vector[SizeExpr] = value match
      case SizeExpr.Add(values) => values
      case other => Vector(other)
    val remaining = collection.mutable.ArrayBuffer.from(terms(right))
    terms(left).forall { term =>
      val index = remaining.indexOf(term)
      if index < 0 then false
      else
        remaining.remove(index)
        true
    }

  def positiveDifference(left: SizeExpr, right: SizeExpr): SizeExpr = (left, right) match
    case (SizeExpr.Const(a), SizeExpr.Const(b)) => const((a - b).max(BigInt(0)))
    case (SizeExpr.Const(zero), _) if zero == 0 => Zero
    case (_, SizeExpr.Const(zero)) if zero == 0 => left
    case _ if left == right => Zero
    case (SizeExpr.Infinity, SizeExpr.Infinity) => SizeExpr.Infinity
    case (SizeExpr.Infinity, _) => SizeExpr.Infinity
    case (_, SizeExpr.Infinity) => Zero
    case _ => SizeExpr.PositiveDifference(left, right)

  def positive(value: SizeExpr): SizeExpr = value match
    case SizeExpr.Const(v) => if v > 0 then One else Zero
    case SizeExpr.Positive(_) => value
    case difference @ SizeExpr.PositiveDifference(SizeExpr.Const(one), _) if one == 1 => difference
    case SizeExpr.Infinity => One
    case _ if definitelyPositive(value) => One
    case _ => SizeExpr.Positive(value)

  def isZero(value: SizeExpr): SizeExpr =
    if definitelyPositive(value) then Zero else positiveDifference(One, value)

  def ifZero(condition: SizeExpr, ifZero: SizeExpr, ifNonZero: SizeExpr): SizeExpr = condition match
    case SizeExpr.Const(value) => if value == 0 then ifZero else ifNonZero
    case _ if ifZero == ifNonZero => ifZero
    case _ => SizeExpr.IfZero(condition, ifZero, ifNonZero)

  private def definitelyPositive(value: SizeExpr): Boolean = value match
    case SizeExpr.Const(v) => v > 0
    case SizeExpr.Add(terms) => terms.exists(definitelyPositive)
    case SizeExpr.Multiply(factors) => factors.forall(definitelyPositive)
    case SizeExpr.Maximum(terms) => terms.exists(definitelyPositive)
    case SizeExpr.Minimum(terms) => terms.forall(definitelyPositive)
    case SizeExpr.Positive(inner) => definitelyPositive(inner)
    case SizeExpr.IfZero(condition, ifZero, ifNonZero) =>
      definitelyPositive(ifZero) && definitelyPositive(ifNonZero)
    case SizeExpr.Infinity => true
    case _ => false

  def range(value: SizeExpr, start: Int, end: Int): SizeExpr = value match
    case SizeExpr.Const(size) => const(rangeCardinality(size, start, end))
    case _ if start == 0 && end == 0 => value
    case _ => SizeExpr.RangeCardinality(value, start, end)

  private def rangeCardinality(size: BigInt, start: Int, end: Int): BigInt =
    def lower(bound: Int): BigInt =
      if bound == 0 then BigInt(0)
      else if bound > 0 then BigInt(bound - 1)
      else size + bound
    def upper(bound: Int): BigInt =
      if bound == 0 then size
      else if start == 0 && bound > 0 then BigInt(bound)
      else if bound > 0 then BigInt(bound - 1)
      else size + bound
    val lo = lower(start).max(BigInt(0)).min(size)
    val hi = upper(end).max(BigInt(0)).min(size)
    (hi - lo).max(BigInt(0))

/** Upper and lower bounds for the cardinality of a result path set. */
case class ResultSizeEstimate(upper: SizeExpr, lower: SizeExpr):
  def exact: Boolean = upper == lower
  def show: String = s"⌊result⌋=${lower.show}, ⌈result⌉=${upper.show}"

object ResultSizeEstimate:
  def exact(value: SizeExpr): ResultSizeEstimate = ResultSizeEstimate(value, value)
  val empty: ResultSizeEstimate = exact(SizeExpr.Zero)
  val unknown: ResultSizeEstimate = ResultSizeEstimate(SizeExpr.Infinity, SizeExpr.Zero)

/** Abstract interpretation of result path-set cardinality.
  *
  * Bounds are symbolic in the cardinalities of free space mentions. Operations
  * that depend on path contents rather than total cardinality (for example a
  * general Unwrap) remain exact opaque atoms instead of inventing a relation
  * from insufficient information.
  */
object ResultSpaceSize:
  def estimate(
    space: Space,
    assumptions: Map[SpaceMention, ResultSizeEstimate] = Map.empty
  ): ResultSizeEstimate = Z3ResultSpaceSize.estimate(space, assumptions)

  /** Original compositional interval analysis, retained as the mandatory
    * solver fallback and as a pointwise baseline for refinement checks.
    */
  def estimateBaseline(
    space: Space,
    assumptions: Map[SpaceMention, ResultSizeEstimate] = Map.empty
  ): ResultSizeEstimate =
    analyze(space, assumptions, Set.empty, Set.empty, operationLaws = false)

  /** Compositional operation laws used by the mixed Z3 graph.  This is kept
    * separate from [[estimateBaseline]] so every refinement can still be
    * checked pointwise against the original interval analysis.
    */
  def estimateOperationLaws(
    space: Space,
    assumptions: Map[SpaceMention, ResultSizeEstimate] = Map.empty
  ): ResultSizeEstimate =
    analyze(space, assumptions, Set.empty, Set.empty, operationLaws = true)

  private def dependsOnBound(
    space: Space,
    boundSpaces: Set[SpaceMention],
    boundPaths: Set[PathRef]
  ): Boolean =
    val (spaces, paths) = collect(space)(
      { case Space.Mention(sm) if boundSpaces(sm) => () },
      { case Path.Deref(pr) if boundPaths(pr) => () }
    )
    spaces.nonEmpty || paths.nonEmpty

  private def opaque(
    space: Space,
    boundSpaces: Set[SpaceMention],
    boundPaths: Set[PathRef]
  ): ResultSizeEstimate =
    if dependsOnBound(space, boundSpaces, boundPaths) then ResultSizeEstimate.unknown
    else ResultSizeEstimate.exact(SizeExpr.sizeOf(space))

  private def exactZero(estimate: ResultSizeEstimate): Boolean = estimate.upper == SizeExpr.Zero

  private def sameSpaceInstance(left: Space, right: Space): Boolean =
    left.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef]

  private def exactOne(estimate: ResultSizeEstimate): Boolean =
    estimate.upper == SizeExpr.One && estimate.lower == SizeExpr.One

  private def pathDefinitelyHeaded(path: Path): Boolean = path match
    case Path.Constant(PathValue(items)) => items.nonEmpty
    case Path.Deref(ref) => ref.lengthHint > 0
    case Path.Concat(left, right) => pathDefinitelyHeaded(left) || pathDefinitelyHeaded(right)
    case Path.GroundedPP(_, _) | Path.GroundedSP(_, _) => false

  /** A known path length is enough to bound a lookup into a finite relation,
    * even when the path's value is supplied by an iterator.  Path references
    * introduced by iteration carry a one-item length hint.
    */
  private def knownPathLength(path: Path): Option[Int] = path match
    case Path.Constant(PathValue(items)) => Some(items.length)
    case Path.Deref(ref) if ref.lengthHint >= 0 => Some(ref.lengthHint)
    case Path.Concat(left, right) =>
      for
        leftLength <- knownPathLength(left)
        rightLength <- knownPathLength(right)
      yield leftLength + rightLength
    case _ => None

  /** Largest result fiber of a literal relation for an arbitrary prefix of a
    * fixed length.  Missing prefixes have an empty fiber, so only represented
    * prefixes need to be counted.
    */
  private def maximumLiteralFiber(value: SpaceValue, prefixLength: Int): BigInt =
    if prefixLength < 0 then BigInt(value.paths.size)
    else
      value.paths.iterator
        .filter(_.items.length >= prefixLength)
        .toVector
        .groupBy(_.items.take(prefixLength))
        .valuesIterator
        .map(_.size)
        .maxOption
        .fold(BigInt(0))(BigInt(_))

  /** True when every possible member is headed (the empty set is vacuously so). */
  private def headedOnly(space: Space): Boolean = space match
    case Space.Empty => true
    case Space.Singleton(path) => pathDefinitelyHeaded(path)
    case Space.Literal(SpaceValue(paths)) => paths.forall(_.items.nonEmpty)
    case Space.Union(left, right) => headedOnly(left) && headedOnly(right)
    case Space.Intersection(left, right) => headedOnly(left) || headedOnly(right)
    case Space.Subtraction(left, _) => headedOnly(left)
    case Space.Restriction(left, _) => headedOnly(left)
    case Space.Raffination(left, _) => headedOnly(left)
    case Space.Composition(left, right) => headedOnly(left) || headedOnly(right)
    case Space.Iteration(_, _, _, body) => headedOnly(body)
    case Space.Fold(_, _, _, _, _, body, _) => headedOnly(body)
    case Space.Fixpoint(initial, _, step) => headedOnly(initial) && headedOnly(step)
    case Space.Wrap(src, prefix) => pathDefinitelyHeaded(prefix) || headedOnly(src)
    case Space.Range(src, _, _) => headedOnly(src)
    case Space.PrefixClosure(_) | Space.SuffixClosure(_) => true
    case _ => false

  private def matchIfEmpty(space: Space): Option[(Space, Space)] = space match
    case Space.Iteration(
          Space.Subtraction(
            Space.Singleton(Path.Constant(sentinel)),
            Space.Iteration(innerSource, innerHead, _, Space.Singleton(Path.Deref(emittedHead)))
          ),
          outerHead,
          outerRest,
          fallback
        ) if sentinel.items.length == 1 && innerHead == emittedHead &&
             !dependsOnBound(fallback, Set(outerRest), Set(outerHead)) =>
      innerSource match
        case Space.Composition(Space.Singleton(Path.Constant(prefix)), condition) if prefix == sentinel =>
          Some(condition -> fallback)
        case Space.Wrap(condition, Path.Constant(prefix)) if prefix == sentinel =>
          Some(condition -> fallback)
        case _ => None
    case _ => None

  private def analyze(
    space: Space,
    assumptions: Map[SpaceMention, ResultSizeEstimate],
    boundSpaces: Set[SpaceMention],
    boundPaths: Set[PathRef],
    operationLaws: Boolean
  ): ResultSizeEstimate =
    def rec(next: Space): ResultSizeEstimate = analyze(next, assumptions, boundSpaces, boundPaths, operationLaws)
    space match
      case Space.Empty => ResultSizeEstimate.empty
      case Space.Mention(variable) =>
        assumptions.getOrElse(variable,
          if variable.sizeHint >= 0 then ResultSizeEstimate.exact(SizeExpr.const(variable.sizeHint))
          else ResultSizeEstimate.exact(SizeExpr.sizeOf(space)))
      case Space.Singleton(_) => ResultSizeEstimate.exact(SizeExpr.One)
      case Space.Literal(value) => ResultSizeEstimate.exact(SizeExpr.const(value.paths.size))
      case Space.Union(left, right) =>
        val l = rec(left)
        if sameSpaceInstance(left, right) then l
        else
          val r = rec(right)
          if exactZero(l) then r
          else if exactZero(r) then l
          else ResultSizeEstimate(SizeExpr.add(l.upper, r.upper), SizeExpr.maximum(l.lower, r.lower))
      case Space.Intersection(left, right) =>
        val l = rec(left)
        if sameSpaceInstance(left, right) then l
        else
          val r = rec(right)
          if exactZero(l) || exactZero(r) then ResultSizeEstimate.empty
          else ResultSizeEstimate(SizeExpr.minimum(l.upper, r.upper), SizeExpr.Zero)
      case Space.Subtraction(left, right) =>
        val l = rec(left)
        if sameSpaceInstance(left, right) || exactZero(l) then ResultSizeEstimate.empty
        else
          val r = rec(right)
          if exactZero(r) then l
          else ResultSizeEstimate(l.upper, SizeExpr.positiveDifference(l.lower, r.upper))
      case Space.Restriction(left, prefixes) =>
        val l = rec(left)
        val p = rec(prefixes)
        if exactZero(l) || exactZero(p) then ResultSizeEstimate.empty
        else ResultSizeEstimate(l.upper, SizeExpr.Zero)
      case Space.Raffination(left, prefixes) =>
        val l = rec(left)
        val p = rec(prefixes)
        if exactZero(l) then ResultSizeEstimate.empty
        else if exactZero(p) then l
        else ResultSizeEstimate(l.upper, SizeExpr.Zero)
      case Space.Composition(left, right) =>
        val l = rec(left)
        val r = rec(right)
        if exactZero(l) || exactZero(r) then ResultSizeEstimate.empty
        else if exactOne(l) then r
        else if exactOne(r) then l
        else
          val upper = SizeExpr.multiply(l.upper, r.upper)
          val lower = SizeExpr.maximum(
            SizeExpr.multiply(l.lower, SizeExpr.positive(r.lower)),
            SizeExpr.multiply(r.lower, SizeExpr.positive(l.lower))
          )
          ResultSizeEstimate(upper, lower)
      case iteration @ Space.Iteration(src, symbol, rest, body) =>
        matchIfEmpty(iteration) match
          case Some((condition, fallback)) =>
            val conditionSize = rec(condition)
            val fallbackSize = rec(fallback)
            if conditionSize.exact && fallbackSize.exact then
              ResultSizeEstimate.exact(SizeExpr.multiply(SizeExpr.isZero(conditionSize.upper), fallbackSize.upper))
            else
              ResultSizeEstimate(
                SizeExpr.multiply(SizeExpr.isZero(conditionSize.lower), fallbackSize.upper),
                SizeExpr.multiply(SizeExpr.isZero(conditionSize.upper), fallbackSize.lower)
              )
          case None =>
            val source = rec(src)
            if exactZero(source) then ResultSizeEstimate.empty
            else
              val restEstimate =
                if rest.sizeHint >= 0 then ResultSizeEstimate.exact(SizeExpr.const(rest.sizeHint))
                else ResultSizeEstimate(source.upper, SizeExpr.One)
              val bodyAssumptions = assumptions.updated(rest, restEstimate)
              val branch = analyze(body, bodyAssumptions, boundSpaces + rest, boundPaths + symbol, operationLaws)
              val independent = operationLaws && !dependsOnBound(body, Set(rest), Set(symbol))
              // ∪ src.map(x => ∪ tails(x).map(y => body(x, y))) has at most
              // |src| * |body| results when body does not inspect the inner
              // tail set.  The tail groups partition the headed source paths;
              // multiplying by |src| once more would count that partition
              // twice.
              val flattenedMapUpper =
                if operationLaws then body match
                  case Space.Iteration(Space.Mention(variable), innerSymbol, innerRest, innerBody)
                      if variable == rest && !dependsOnBound(innerBody, Set(innerRest), Set.empty) =>
                    val innerAssumptions = bodyAssumptions.updated(
                      innerRest,
                      ResultSizeEstimate(source.upper, SizeExpr.One)
                    )
                    val perElement = analyze(
                      innerBody,
                      innerAssumptions,
                      boundSpaces ++ Set(rest, innerRest),
                      boundPaths ++ Set(symbol, innerSymbol),
                      operationLaws
                    )
                    Some(perElement.upper)
                  case _ => None
                else None
              val upper =
                flattenedMapUpper match
                  case Some(perElement) => SizeExpr.multiply(source.upper, perElement)
                  case None if independent => SizeExpr.multiply(SizeExpr.positive(source.upper), branch.upper)
                  case None => SizeExpr.multiply(source.upper, branch.upper)
              val headedGuard =
                if headedOnly(src) then SizeExpr.positive(source.lower)
                else if operationLaws then SizeExpr.positive(SizeExpr.positiveDifference(source.lower, SizeExpr.One))
                else SizeExpr.Zero
              val lower =
                SizeExpr.multiply(headedGuard, branch.lower)
              ResultSizeEstimate(upper, lower)
      case Space.Fold(src, _, acc, symbol, rest, body, _) =>
        val source = rec(src)
        if exactZero(source) then ResultSizeEstimate.empty
        else
          val restEstimate =
            if rest.sizeHint >= 0 then ResultSizeEstimate.exact(SizeExpr.const(rest.sizeHint))
            else ResultSizeEstimate(source.upper, SizeExpr.One)
          val bodyAssumptions = assumptions.updated(rest, restEstimate)
          val branch = analyze(body, bodyAssumptions, boundSpaces + rest, boundPaths ++ Set(acc, symbol), operationLaws)
          ResultSizeEstimate(
            SizeExpr.multiply(source.upper, branch.upper),
            if headedOnly(src) then SizeExpr.multiply(SizeExpr.positive(source.lower), branch.lower) else SizeExpr.Zero
          )
      case Space.Fixpoint(initial, _, _) =>
        val base = rec(initial)
        ResultSizeEstimate(SizeExpr.Infinity, base.lower)
      case Space.Wrap(src, _) => rec(src)
      case Space.Unwrap(src, Path.Constant(PathValue(Nil))) if operationLaws => rec(src)
      case Space.Unwrap(Space.Literal(value), prefix) if operationLaws && knownPathLength(prefix).nonEmpty =>
        val fiberUpper = SizeExpr.const(maximumLiteralFiber(value, knownPathLength(prefix).get))
        val exact = opaque(space, boundSpaces, boundPaths)
        ResultSizeEstimate(SizeExpr.minimum(exact.upper, fiberUpper), exact.lower)
      case Space.Unwrap(src, _) if operationLaws =>
        val source = rec(src)
        val exact = opaque(space, boundSpaces, boundPaths)
        ResultSizeEstimate(SizeExpr.minimum(exact.upper, source.upper), exact.lower)
      case Space.Unwrap(_, _) => opaque(space, boundSpaces, boundPaths)
      case Space.TailsUnion(src) =>
        val source = rec(src)
        if exactZero(source) then ResultSizeEstimate.empty
        else ResultSizeEstimate(
          source.upper,
          if headedOnly(src) then SizeExpr.positive(source.lower)
          else if operationLaws then SizeExpr.positive(SizeExpr.positiveDifference(source.lower, SizeExpr.One))
          else SizeExpr.Zero
        )
      case Space.TailsIntersection(src) =>
        val source = rec(src)
        if exactZero(source) then ResultSizeEstimate.empty
        else ResultSizeEstimate(source.upper, SizeExpr.Zero)
      case Space.PrefixClosure(src) =>
        val source = rec(src)
        if exactZero(source) then ResultSizeEstimate.empty
        else ResultSizeEstimate(
          SizeExpr.Infinity,
          if operationLaws then
            if headedOnly(src) then source.lower
            else SizeExpr.positiveDifference(source.lower, SizeExpr.One)
          else if headedOnly(src) then SizeExpr.positive(source.lower)
          else SizeExpr.Zero
        )
      case Space.SuffixClosure(src) =>
        val source = rec(src)
        if exactZero(source) then ResultSizeEstimate.empty
        else ResultSizeEstimate(
          SizeExpr.Infinity,
          if operationLaws then
            if headedOnly(src) then source.lower
            else SizeExpr.positiveDifference(source.lower, SizeExpr.One)
          else if headedOnly(src) then SizeExpr.positive(source.lower)
          else SizeExpr.Zero
        )
      case Space.TailsClosure(src) =>
        val source = rec(src)
        if exactZero(source) then ResultSizeEstimate.empty
        else ResultSizeEstimate(
          SizeExpr.Infinity,
          if operationLaws then source.lower else SizeExpr.positive(source.lower)
        )
      case Space.Range(src, start, end) =>
        val source = rec(src)
        if exactZero(source) then ResultSizeEstimate.empty
        else if source.exact then ResultSizeEstimate.exact(SizeExpr.range(source.upper, start, end))
        else ResultSizeEstimate(source.upper, SizeExpr.Zero)
      case Space.Call(_, _, _) | Space.GroundedPS(_, _) | Space.GroundedSS(_, _) =>
        opaque(space, boundSpaces, boundPaths)

/** Opt-in corpus audit for [[ResultSpaceSize]]. Set
  * `morkl.resultSizeAudit.path` to audit every reference-evaluator result and
  * write a Markdown summary at JVM shutdown. Direct callers can use
  * [[observeExplicit]] independently of the system property.
  */
object ResultSpaceSizeAudit:
  import java.nio.charset.StandardCharsets
  import java.nio.file.{Files, Paths}
  import scala.collection.mutable

  private case class Worst(actual: BigInt, bound: String, gap: Option[BigInt], expression: String, estimate: String)

  private val active = ThreadLocal.withInitial(() => false)
  private val outputPath = Option(System.getProperty("morkl.resultSizeAudit.path")).filter(_.nonEmpty)
  private var auditLabel = Option(System.getProperty("morkl.resultSizeAudit.label")).getOrElse("result-space size audit")
  private var observations = 0L
  private var finiteUpper = 0L
  private var unboundedUpper = 0L
  private var unknownLower = 0L
  private var exactUpper = 0L
  private var exactLower = 0L
  private var improvedUpper = 0L
  private var improvedLower = 0L
  private var upperGapSum = BigInt(0)
  private var lowerGapSum = BigInt(0)
  private val upperGapCounts = mutable.LinkedHashMap.from(
    Vector("0", "1", "2-3", "4-7", "8-15", "16-31", "32-63", "64-255", "256+").map(_ -> 0L)
  )
  private val lowerGapCounts = mutable.LinkedHashMap.from(upperGapCounts.keysIterator.map(_ -> 0L))
  private val upperRatioCounts = mutable.LinkedHashMap.from(
    Vector("exact", "≤1.25x", "≤1.5x", "≤2x", "≤4x", "≤10x", ">10x", "zero→positive").map(_ -> 0L)
  )
  private val lowerCoverageCounts = mutable.LinkedHashMap.from(
    Vector("exact", "≥75%", "≥50%", "≥25%", ">0%", "0%").map(_ -> 0L)
  )
  private val worstFiniteUpper = mutable.HashMap.empty[String, Worst]
  private val worstUnboundedUpper = mutable.LinkedHashMap.empty[String, Worst]
  private val worstLower = mutable.HashMap.empty[String, Worst]

  outputPath.foreach { path =>
    Runtime.getRuntime.addShutdownHook(new Thread(() =>
      val target = Paths.get(path)
      Option(target.getParent).foreach(Files.createDirectories(_))
      Files.writeString(target, render, StandardCharsets.UTF_8)
      ()
    ))
  }

  def enabled: Boolean = outputPath.nonEmpty

  private def short(value: String, limit: Int = 480): String =
    val oneLine = value.replace('\n', ' ')
    if oneLine.length <= limit then oneLine else oneLine.take(limit - 1) + "…"

  private def gapBucket(gap: BigInt): String =
    if gap == 0 then "0"
    else if gap == 1 then "1"
    else if gap <= 3 then "2-3"
    else if gap <= 7 then "4-7"
    else if gap <= 15 then "8-15"
    else if gap <= 31 then "16-31"
    else if gap <= 63 then "32-63"
    else if gap <= 255 then "64-255"
    else "256+"

  private def upperRatioBucket(actual: BigInt, upper: BigInt): String =
    if upper == actual then "exact"
    else if actual == 0 then "zero→positive"
    else
      val ratio = BigDecimal(upper) / BigDecimal(actual)
      if ratio <= BigDecimal("1.25") then "≤1.25x"
      else if ratio <= BigDecimal("1.5") then "≤1.5x"
      else if ratio <= BigDecimal(2) then "≤2x"
      else if ratio <= BigDecimal(4) then "≤4x"
      else if ratio <= BigDecimal(10) then "≤10x"
      else ">10x"

  private def lowerCoverageBucket(actual: BigInt, lower: BigInt): String =
    if lower == actual then "exact"
    else if lower == 0 then "0%"
    else
      val coverage = BigDecimal(lower) / BigDecimal(actual)
      if coverage >= BigDecimal("0.75") then "≥75%"
      else if coverage >= BigDecimal("0.5") then "≥50%"
      else if coverage >= BigDecimal("0.25") then "≥25%"
      else ">0%"

  private def remember(target: mutable.Map[String, Worst], value: Worst): Unit =
    val key = value.expression + "\u0000" + value.estimate
    target.get(key) match
      case Some(previous) if previous.gap.getOrElse(BigInt(-1)) >= value.gap.getOrElse(BigInt(-1)) => ()
      case _ => target(key) = value

  private def record(
    space: Space,
    actual: BigInt,
    estimate: ResultSizeEstimate,
    lower: Option[BigInt],
    upper: Option[BigInt],
    baselineLower: Option[BigInt],
    baselineUpper: Option[BigInt]
  ): Unit = synchronized {
    (baselineLower, lower) match
      case (Some(base), Some(refined)) if refined < base =>
        throw AssertionError(s"refined lower bound $refined is weaker than baseline $base for ${space.show}")
      case (Some(_), None) =>
        throw AssertionError(s"refined lower bound became unknown while baseline was finite for ${space.show}")
      case (Some(base), Some(refined)) if refined > base => improvedLower += 1
      case _ => ()
    (baselineUpper, upper) match
      case (Some(base), Some(refined)) if refined > base =>
        throw AssertionError(s"refined upper bound $refined is weaker than baseline $base for ${space.show}")
      case (Some(_), None) =>
        throw AssertionError(s"refined upper bound became unbounded while baseline was finite for ${space.show}")
      case (Some(base), Some(refined)) if refined < base => improvedUpper += 1
      case (None, Some(_)) => improvedUpper += 1
      case _ => ()
    observations += 1
    val expression = short(space.show)
    val shownEstimate = short(estimate.show)
    lower match
      case Some(value) =>
        if value > actual then
          throw AssertionError(s"result-size lower bound $value exceeds actual $actual for ${space.show}; ${estimate.show}")
        val gap = actual - value
        if gap == 0 then exactLower += 1
        lowerGapSum += gap
        lowerGapCounts(gapBucket(gap)) += 1
        lowerCoverageCounts(lowerCoverageBucket(actual, value)) += 1
        remember(worstLower, Worst(actual, value.toString, Some(gap), expression, shownEstimate))
      case None => unknownLower += 1
    upper match
      case Some(value) =>
        finiteUpper += 1
        if value < actual then
          throw AssertionError(s"result-size upper bound $value is below actual $actual for ${space.show}; ${estimate.show}")
        val gap = value - actual
        if gap == 0 then exactUpper += 1
        upperGapSum += gap
        upperGapCounts(gapBucket(gap)) += 1
        upperRatioCounts(upperRatioBucket(actual, value)) += 1
        remember(worstFiniteUpper, Worst(actual, value.toString, Some(gap), expression, shownEstimate))
      case None =>
        unboundedUpper += 1
        val value = Worst(actual, "∞", None, expression, shownEstimate)
        worstUnboundedUpper.getOrElseUpdate(expression + "\u0000" + shownEstimate, value)
  }

  def observe(
    space: Space,
    result: SpaceValue
  )(using pc: PathContext, sc: SpaceContext, rc: PartialFunction[RoutinePtr, Routine]): Unit =
    if enabled then observeExplicit(space, result)

  def observeExplicit(
    space: Space,
    result: SpaceValue
  )(using pc: PathContext, sc: SpaceContext, rc: PartialFunction[RoutinePtr, Routine]): Unit =
    if !active.get() then
      active.set(true)
      try
        val estimate = ResultSpaceSize.estimate(space)
        val baseline = ResultSpaceSize.estimateBaseline(space)
        val lower = estimate.lower.evaluate
        val upper = estimate.upper.evaluate
        val baselineLower = baseline.lower.evaluate
        val baselineUpper = baseline.upper.evaluate
        record(space, BigInt(result.paths.size), estimate, lower, upper, baselineLower, baselineUpper)
      finally active.set(false)

  def reset(label: String): Unit = synchronized {
    auditLabel = label
    observations = 0L
    finiteUpper = 0L
    unboundedUpper = 0L
    unknownLower = 0L
    exactUpper = 0L
    exactLower = 0L
    improvedUpper = 0L
    improvedLower = 0L
    upperGapSum = BigInt(0)
    lowerGapSum = BigInt(0)
    upperGapCounts.keys.foreach(upperGapCounts(_) = 0L)
    lowerGapCounts.keys.foreach(lowerGapCounts(_) = 0L)
    upperRatioCounts.keys.foreach(upperRatioCounts(_) = 0L)
    lowerCoverageCounts.keys.foreach(lowerCoverageCounts(_) = 0L)
    worstFiniteUpper.clear()
    worstUnboundedUpper.clear()
    worstLower.clear()
  }

  private def percent(count: Long, total: Long): String =
    if total == 0 then "0.00%" else f"${count.toDouble * 100.0 / total}%.2f%%"

  private def mean(sum: BigInt, count: Long): String =
    if count == 0 then "n/a" else (BigDecimal(sum) / BigDecimal(count)).setScale(3, BigDecimal.RoundingMode.HALF_UP).toString

  private def distribution(title: String, values: collection.Map[String, Long], total: Long): String =
    val rows = values.iterator.map { (bucket, count) => s"| $bucket | $count | ${percent(count, total)} |" }.mkString("\n")
    s"## $title\n\n| Bucket | Count | Share |\n|---|---:|---:|\n$rows\n"

  private def worstTable(title: String, values: Iterable[Worst], unbounded: Boolean = false): String =
    val ordered =
      if unbounded then values.toVector.sortBy(v => -v.actual).take(10)
      else values.toVector.sortBy(v => (v.gap.getOrElse(BigInt(0)), v.actual)).reverse.take(10)
    val rows = ordered.map { value =>
      val gap = value.gap.fold("∞")(_.toString)
      s"| ${value.actual} | ${value.bound} | $gap | `${value.expression.replace("|", "\\|")}` | `${value.estimate.replace("|", "\\|")}` |"
    }.mkString("\n")
    s"## $title\n\n| Actual | Bound | Additive gap | Expression | Estimate |\n|---:|---:|---:|---|---|\n$rows\n"

  def render: String = synchronized {
    val sound = unknownLower == 0
    s"""# $auditLabel
       |
       |- Observations: $observations
       |- Sound: ${if sound then "yes" else "not fully checkable"} (all finite bounds contained the concrete result)
       |- Finite upper bounds: $finiteUpper (${percent(finiteUpper, observations)})
       |- Unbounded upper bounds: $unboundedUpper (${percent(unboundedUpper, observations)})
       |- Exact finite upper bounds: $exactUpper (${percent(exactUpper, finiteUpper)})
       |- Exact lower bounds: $exactLower (${percent(exactLower, observations - unknownLower)})
       |- Upper bounds improved over baseline: $improvedUpper (${percent(improvedUpper, observations)})
       |- Lower bounds improved over baseline: $improvedLower (${percent(improvedLower, observations)})
       |- Unknown lower bounds: $unknownLower
       |- Mean finite upper overestimate: ${mean(upperGapSum, finiteUpper)} paths
       |- Mean lower underestimate: ${mean(lowerGapSum, observations - unknownLower)} paths
       |
       |${distribution("Finite upper-bound additive overestimate", upperGapCounts, finiteUpper)}
       |${distribution("Finite upper-bound multiplicative overestimate", upperRatioCounts, finiteUpper)}
       |${distribution("Lower-bound additive underestimate", lowerGapCounts, observations - unknownLower)}
       |${distribution("Lower-bound coverage of the actual result", lowerCoverageCounts, observations - unknownLower)}
       |${worstTable("Least-tight unbounded upper bounds", worstUnboundedUpper.values, unbounded = true)}
       |${worstTable("Least-tight finite upper bounds", worstFiniteUpper.values)}
       |${worstTable("Least-tight lower bounds", worstLower.values)}
       |""".stripMargin
  }
