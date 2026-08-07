package morkl

import scala.collection.mutable

enum SpatialRewriteFact:
  case DeadEliminated(position: Vector[Int], expression: Space)
  case ConstantFolded(position: Vector[Int], value: SpaceValue)
  case EmptyIdentity(position: Vector[Int], operation: String)

/** A residual routine whose validity condition travels with it. */
case class SpecializedRoutine(
  precondition: Map[SpaceMention, SpatialType],
  residual: Routine,
  facts: Vector[SpatialRewriteFact],
  pathPrecondition: Map[PathRef, SpatialPathType] = Map.empty,
):
  def applicableTo(arguments: Map[SpaceMention, SpaceValue]): Boolean =
    precondition.forall((mention, spatialType) => arguments.get(mention).exists(spatialType.gammaContains))

  def applicableTo(
    spaces: Map[SpaceMention, SpaceValue],
    paths: Map[PathRef, PathValue],
  ): Boolean = applicableTo(spaces) && pathPrecondition.forall { (ref, spatialType) =>
    paths.get(ref).exists { value =>
      val length = value.items.length
      val lengthOkay = spatialType.length.lower.annotatedBound(Z3BoundDirection.Lower).forall(_ <= length) &&
        spatialType.length.upper.annotatedBound(Z3BoundDirection.Upper).forall(BigInt(length) <= _)
      lengthOkay && (spatialType.patterns.isEmpty || spatialType.patterns.exists(_.matches(value)))
    }
  }

object SpatialElimination:
  /** Specialize only from declared abstract inputs. Concrete evaluation never
    * feeds this pass; the residual is guarded by full γ-membership. */
  def specialize(
    routine: Routine,
    annotations: SpatialRoutineAnnotations,
    routines: PartialFunction[RoutinePtr, Routine] = PartialFunction.empty,
  ): SpecializedRoutine =
    val analysis = SpatialTypeAnalysis.outputDecorated(routine.body, annotations.assumptions, routines)
    val facts = mutable.ArrayBuffer.empty[SpatialRewriteFact]

    def resultAt(position: Vector[Int]): SpatialType =
      analysis.atPosition(position*).map(_.result).getOrElse(SpatialType.top)

    def rewrite(space: Space, position: Vector[Int]): Space =
      val abstractValue = resultAt(position)
      if abstractValue.facts.isDead then
          facts += SpatialRewriteFact.DeadEliminated(position, space)
          Space.Empty
      else abstractValue.exactValue match
        case Some(value) if !space.isInstanceOf[Space.Literal] =>
          facts += SpatialRewriteFact.ConstantFolded(position, value)
          Space.Literal(value)
        case _ =>
          def child(value: Space, index: Int): Space = rewrite(value, position :+ index)
          def emptyIdentity(operation: String, value: Space): Space =
            facts += SpatialRewriteFact.EmptyIdentity(position, operation)
            value
          space match
            case Space.Empty | Space.Mention(_) | Space.Singleton(_) | Space.Literal(_) |
                 Space.GroundedPS(_, _) => space
            case Space.Call(name, refs, mentions) => Space.Call(name, refs, mentions.zipWithIndex.map((value, index) => child(value, index)))
            case Space.Union(left, right) =>
              (child(left, 0), child(right, 1)) match
                case (Space.Empty, value) => emptyIdentity("union-left-empty", value)
                case (value, Space.Empty) => emptyIdentity("union-right-empty", value)
                case (l, r) if l == r => emptyIdentity("union-idempotent", l)
                case (l, r) => Space.Union(l, r)
            case Space.Intersection(left, right) =>
              (child(left, 0), child(right, 1)) match
                case (Space.Empty, _) | (_, Space.Empty) => emptyIdentity("intersection-empty", Space.Empty)
                case (l, r) if l == r => emptyIdentity("intersection-idempotent", l)
                case (l, r) => Space.Intersection(l, r)
            case Space.Subtraction(left, right) =>
              (child(left, 0), child(right, 1)) match
                case (Space.Empty, _) => emptyIdentity("subtract-empty-left", Space.Empty)
                case (value, Space.Empty) => emptyIdentity("subtract-empty-right", value)
                case (l, r) if l == r => emptyIdentity("subtract-self", Space.Empty)
                case (l, r) => Space.Subtraction(l, r)
            case Space.Restriction(left, right) =>
              (child(left, 0), child(right, 1)) match
                case (Space.Empty, _) | (_, Space.Empty) => emptyIdentity("restriction-empty", Space.Empty)
                case (l, r) => Space.Restriction(l, r)
            case Space.Raffination(left, right) =>
              (child(left, 0), child(right, 1)) match
                case (Space.Empty, _) => emptyIdentity("raffination-empty-left", Space.Empty)
                case (value, Space.Empty) => emptyIdentity("raffination-empty-prefix", value)
                case (l, r) => Space.Raffination(l, r)
            case Space.Composition(left, right) =>
              (child(left, 0), child(right, 1)) match
                case (Space.Empty, _) | (_, Space.Empty) => emptyIdentity("composition-empty", Space.Empty)
                case (l, r) => Space.Composition(l, r)
            case Space.Iteration(source, symbol, rest, body) =>
              child(source, 0) match
                case Space.Empty => emptyIdentity("iteration-empty", Space.Empty)
                case src => Space.Iteration(src, symbol, rest, child(body, 1))
            case Space.Fold(source, initial, acc, symbol, rest, body, update) =>
              Space.Fold(child(source, 0), initial, acc, symbol, rest, child(body, 1), update)
            case Space.Fixpoint(initial, variable, step) => Space.Fixpoint(child(initial, 0), variable, child(step, 1))
            case Space.Wrap(source, prefix) => child(source, 0) match
              case Space.Empty => emptyIdentity("wrap-empty", Space.Empty)
              case src => Space.Wrap(src, prefix)
            case Space.Unwrap(source, prefix) => child(source, 0) match
              case Space.Empty => emptyIdentity("unwrap-empty", Space.Empty)
              case src => Space.Unwrap(src, prefix)
            case Space.TailsUnion(source) => child(source, 0) match
              case Space.Empty => emptyIdentity("tails-union-empty", Space.Empty)
              case src => Space.TailsUnion(src)
            case Space.TailsIntersection(source) => child(source, 0) match
              case Space.Empty => emptyIdentity("tails-intersection-empty", Space.Empty)
              case src => Space.TailsIntersection(src)
            case Space.PrefixClosure(source) => child(source, 0) match
              case Space.Empty => emptyIdentity("prefix-closure-empty", Space.Empty)
              case src => Space.PrefixClosure(src)
            case Space.SuffixClosure(source) => child(source, 0) match
              case Space.Empty => emptyIdentity("suffix-closure-empty", Space.Empty)
              case src => Space.SuffixClosure(src)
            case Space.TailsClosure(source) => child(source, 0) match
              case Space.Empty => emptyIdentity("tails-closure-empty", Space.Empty)
              case src => Space.TailsClosure(src)
            case Space.GroundedSS(source, function) => Space.GroundedSS(child(source, 0), function)
            case Space.Range(source, start, end) => child(source, 0) match
              case Space.Empty => emptyIdentity("range-empty", Space.Empty)
              case src => Space.Range(src, start, end)

    SpecializedRoutine(annotations.spaces,
      routine.copy(body = rewrite(routine.body, Vector.empty)), facts.toVector, annotations.paths)
