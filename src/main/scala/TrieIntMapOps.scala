package scala.collection.immutable

import morkl.{AlgebraicEmptyReason, AlgebraicResult, RestrictionResult, TrieSpace}

/** Patricia-trie child-map algebra with provenance propagation. */
object TrieIntMapOps:
  import IntMapUtils.{hasMatch, join, shorter, zero}

  private def valueOf(
    result: AlgebraicResult[IntMap[TrieSpace]],
    left: IntMap[TrieSpace],
    right: IntMap[TrieSpace]
  ): IntMap[TrieSpace] = result match
    case AlgebraicResult.Empty(_) => IntMap.Nil
    case AlgebraicResult.Identity(arguments) =>
      if (arguments & AlgebraicResult.Left) != 0 then left else right
    case AlgebraicResult.Bespoke(value) => value

  private def identities(left: Boolean, right: Boolean): Int =
    (if left then AlgebraicResult.Left else 0) |
      (if right then AlgebraicResult.Right else 0)

  private def nonEmptyResult(
    value: => IntMap[TrieSpace],
    sameLeft: Boolean,
    sameRight: Boolean
  ): AlgebraicResult[IntMap[TrieSpace]] =
    val mask = identities(sameLeft, sameRight)
    if mask != 0 then AlgebraicResult.Identity(mask)
    else AlgebraicResult.Bespoke(value)

  def unionTriesResult(
    a: IntMap[TrieSpace],
    b: IntMap[TrieSpace]
  ): AlgebraicResult[IntMap[TrieSpace]] = (a, b) match
    case (IntMap.Nil, IntMap.Nil) =>
      AlgebraicResult.Empty(AlgebraicEmptyReason.AllArguments)
    case (IntMap.Nil, _) => AlgebraicResult.Identity(AlgebraicResult.Right)
    case (_, IntMap.Nil) => AlgebraicResult.Identity(AlgebraicResult.Left)
    case _ if a eq b => AlgebraicResult.Identity(AlgebraicResult.Both)
    case (IntMap.Tip(k, v), _) =>
      b.get(k) match
        case None => AlgebraicResult.Bespoke(b.updated(k, v))
        case Some(w) =>
          val childResult = v.unionResult(w)
          val child = TrieSpace.binaryValue(childResult, v, w)
          b match
            case IntMap.Tip(_, _) =>
              nonEmptyResult(
                IntMap.Tip(k, child),
                AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Left),
                AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Right)
              )
            case _ =>
              if AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Right) then
                AlgebraicResult.Identity(AlgebraicResult.Right)
              else AlgebraicResult.Bespoke(b.updated(k, child))
    case (_, IntMap.Tip(k, w)) =>
      a.get(k) match
        case None => AlgebraicResult.Bespoke(a.updated(k, w))
        case Some(v) =>
          val childResult = v.unionResult(w)
          if AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Left) then
            AlgebraicResult.Identity(AlgebraicResult.Left)
          else AlgebraicResult.Bespoke(a.updated(k, TrieSpace.binaryValue(childResult, v, w)))
    case (IntMap.Bin(p1, m1, l1, r1), IntMap.Bin(p2, m2, l2, r2)) =>
      if shorter(m1, m2) then
        if !hasMatch(p2, p1, m1) then AlgebraicResult.Bespoke(join(p1, a, p2, b))
        else
          val recurseLeft = zero(p2, m1)
          val childResult = unionTriesResult(if recurseLeft then l1 else r1, b)
          if AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Left) then
            AlgebraicResult.Identity(AlgebraicResult.Left)
          else
            val child = valueOf(childResult, if recurseLeft then l1 else r1, b)
            AlgebraicResult.Bespoke(
              if recurseLeft then IntMap.Bin(p1, m1, child, r1)
              else IntMap.Bin(p1, m1, l1, child)
            )
      else if shorter(m2, m1) then
        if !hasMatch(p1, p2, m2) then AlgebraicResult.Bespoke(join(p1, a, p2, b))
        else
          val recurseLeft = zero(p1, m2)
          val rightBranch = if recurseLeft then l2 else r2
          val childResult = unionTriesResult(a, rightBranch)
          if AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Right) then
            AlgebraicResult.Identity(AlgebraicResult.Right)
          else
            val child = valueOf(childResult, a, rightBranch)
            AlgebraicResult.Bespoke(
              if recurseLeft then IntMap.Bin(p2, m2, child, r2)
              else IntMap.Bin(p2, m2, l2, child)
            )
      else if p1 == p2 then
        val leftResult = unionTriesResult(l1, l2)
        val rightResult = unionTriesResult(r1, r2)
        nonEmptyResult(
          IntMap.Bin(p1, m1, valueOf(leftResult, l1, l2), valueOf(rightResult, r1, r2)),
          AlgebraicResult.equalsArgument(leftResult, AlgebraicResult.Left) &&
            AlgebraicResult.equalsArgument(rightResult, AlgebraicResult.Left),
          AlgebraicResult.equalsArgument(leftResult, AlgebraicResult.Right) &&
            AlgebraicResult.equalsArgument(rightResult, AlgebraicResult.Right)
        )
      else AlgebraicResult.Bespoke(join(p1, a, p2, b))

  def unionTries(a: IntMap[TrieSpace], b: IntMap[TrieSpace]): IntMap[TrieSpace] =
    valueOf(unionTriesResult(a, b), a, b)

  def intersectTriesResult(
    a: IntMap[TrieSpace],
    b: IntMap[TrieSpace]
  ): AlgebraicResult[IntMap[TrieSpace]] = (a, b) match
    case (IntMap.Nil, IntMap.Nil) =>
      AlgebraicResult.Empty(AlgebraicEmptyReason.EmptyArguments(AlgebraicResult.Both))
    case (IntMap.Nil, _) =>
      AlgebraicResult.Empty(AlgebraicEmptyReason.EmptyArguments(AlgebraicResult.Left))
    case (_, IntMap.Nil) =>
      AlgebraicResult.Empty(AlgebraicEmptyReason.EmptyArguments(AlgebraicResult.Right))
    case _ if a eq b => AlgebraicResult.Identity(AlgebraicResult.Both)
    case (IntMap.Tip(k, v), _) =>
      b.get(k) match
        case None => AlgebraicResult.Empty(AlgebraicEmptyReason.Disjoint)
        case Some(w) =>
          val childResult = v.intersectionResult(w)
          childResult match
            case AlgebraicResult.Empty(_) => AlgebraicResult.Empty(AlgebraicEmptyReason.Disjoint)
            case _ =>
              val child = TrieSpace.binaryValue(childResult, v, w)
              b match
                case IntMap.Tip(_, _) =>
                  nonEmptyResult(
                    IntMap.Tip(k, child),
                    AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Left),
                    AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Right)
                  )
                case _ =>
                  nonEmptyResult(
                    IntMap.Tip(k, child),
                    AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Left),
                    sameRight = false
                  )
    case (_, IntMap.Tip(k, w)) =>
      a.get(k) match
        case None => AlgebraicResult.Empty(AlgebraicEmptyReason.Disjoint)
        case Some(v) =>
          val childResult = v.intersectionResult(w)
          childResult match
            case AlgebraicResult.Empty(_) => AlgebraicResult.Empty(AlgebraicEmptyReason.Disjoint)
            case _ =>
              nonEmptyResult(
                IntMap.Tip(k, TrieSpace.binaryValue(childResult, v, w)),
                sameLeft = false,
                AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Right)
              )
    case (IntMap.Bin(p1, m1, l1, r1), IntMap.Bin(p2, m2, l2, r2)) =>
      if shorter(m1, m2) then
        if !hasMatch(p2, p1, m1) then AlgebraicResult.Empty(AlgebraicEmptyReason.Disjoint)
        else
          val leftBranch = if zero(p2, m1) then l1 else r1
          val childResult = intersectTriesResult(leftBranch, b)
          childResult match
            case AlgebraicResult.Empty(_) => childResult
            case _ =>
              nonEmptyResult(
                valueOf(childResult, leftBranch, b),
                sameLeft = false,
                AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Right)
              )
      else if shorter(m2, m1) then
        if !hasMatch(p1, p2, m2) then AlgebraicResult.Empty(AlgebraicEmptyReason.Disjoint)
        else
          val rightBranch = if zero(p1, m2) then l2 else r2
          val childResult = intersectTriesResult(a, rightBranch)
          childResult match
            case AlgebraicResult.Empty(_) => childResult
            case _ =>
              nonEmptyResult(
                valueOf(childResult, a, rightBranch),
                AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Left),
                sameRight = false
              )
      else if p1 == p2 then
        val leftResult = intersectTriesResult(l1, l2)
        val rightResult = intersectTriesResult(r1, r2)
        val left = valueOf(leftResult, l1, l2)
        val right = valueOf(rightResult, r1, r2)
        if (left eq IntMap.Nil) && (right eq IntMap.Nil) then
          AlgebraicResult.Empty(AlgebraicEmptyReason.Disjoint)
        else
          nonEmptyResult(
            binPrune(p1, m1, left, right),
            AlgebraicResult.equalsArgument(leftResult, AlgebraicResult.Left) &&
              AlgebraicResult.equalsArgument(rightResult, AlgebraicResult.Left),
            AlgebraicResult.equalsArgument(leftResult, AlgebraicResult.Right) &&
              AlgebraicResult.equalsArgument(rightResult, AlgebraicResult.Right)
          )
      else AlgebraicResult.Empty(AlgebraicEmptyReason.Disjoint)

  def intersectTries(a: IntMap[TrieSpace], b: IntMap[TrieSpace]): IntMap[TrieSpace] =
    valueOf(intersectTriesResult(a, b), a, b)

  def diffTriesResult(
    a: IntMap[TrieSpace],
    b: IntMap[TrieSpace]
  ): AlgebraicResult[IntMap[TrieSpace]] = (a, b) match
    case (IntMap.Nil, _) =>
      AlgebraicResult.Empty(AlgebraicEmptyReason.EmptyArguments(AlgebraicResult.Left))
    case (_, IntMap.Nil) => AlgebraicResult.Identity(AlgebraicResult.Left)
    case _ if a eq b => AlgebraicResult.Empty(AlgebraicEmptyReason.LeftCovered)
    case (IntMap.Tip(k, v), _) =>
      b.get(k) match
        case None => AlgebraicResult.Identity(AlgebraicResult.Left)
        case Some(w) =>
          v.subtractionResult(w) match
            case AlgebraicResult.Empty(_) => AlgebraicResult.Empty(AlgebraicEmptyReason.LeftCovered)
            case childResult if AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Left) =>
              AlgebraicResult.Identity(AlgebraicResult.Left)
            case childResult =>
              AlgebraicResult.Bespoke(IntMap.Tip(k, TrieSpace.binaryValue(childResult, v, w)))
    case (_, IntMap.Tip(k, w)) =>
      a.get(k) match
        case None => AlgebraicResult.Identity(AlgebraicResult.Left)
        case Some(v) =>
          val childResult = v.subtractionResult(w)
          if AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Left) then
            AlgebraicResult.Identity(AlgebraicResult.Left)
          else
            val replacement = TrieSpace.binaryValue(childResult, v, w)
            AlgebraicResult.Bespoke(if replacement.isEmpty then a - k else a.updated(k, replacement))
    case (IntMap.Bin(p1, m1, l1, r1), IntMap.Bin(p2, m2, l2, r2)) =>
      if shorter(m1, m2) then
        if !hasMatch(p2, p1, m1) then AlgebraicResult.Identity(AlgebraicResult.Left)
        else
          val recurseLeft = zero(p2, m1)
          val leftBranch = if recurseLeft then l1 else r1
          val childResult = diffTriesResult(leftBranch, b)
          if AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Left) then
            AlgebraicResult.Identity(AlgebraicResult.Left)
          else
            val child = valueOf(childResult, leftBranch, b)
            AlgebraicResult.Bespoke(
              if recurseLeft then binPrune(p1, m1, child, r1)
              else binPrune(p1, m1, l1, child)
            )
      else if shorter(m2, m1) then
        if !hasMatch(p1, p2, m2) then AlgebraicResult.Identity(AlgebraicResult.Left)
        else diffTriesResult(a, if zero(p1, m2) then l2 else r2)
      else if p1 == p2 then
        val leftResult = diffTriesResult(l1, l2)
        val rightResult = diffTriesResult(r1, r2)
        val left = valueOf(leftResult, l1, l2)
        val right = valueOf(rightResult, r1, r2)
        if (left eq IntMap.Nil) && (right eq IntMap.Nil) then
          AlgebraicResult.Empty(AlgebraicEmptyReason.LeftCovered)
        else if AlgebraicResult.equalsArgument(leftResult, AlgebraicResult.Left) &&
            AlgebraicResult.equalsArgument(rightResult, AlgebraicResult.Left)
        then AlgebraicResult.Identity(AlgebraicResult.Left)
        else AlgebraicResult.Bespoke(binPrune(p1, m1, left, right))
      else AlgebraicResult.Identity(AlgebraicResult.Left)

  def diffTries(a: IntMap[TrieSpace], b: IntMap[TrieSpace]): IntMap[TrieSpace] =
    valueOf(diffTriesResult(a, b), a, b)

  def restrictTriesResult(
    x: IntMap[TrieSpace],
    p: IntMap[TrieSpace]
  ): RestrictionResult[IntMap[TrieSpace]] = (x, p) match
    case (IntMap.Nil, IntMap.Nil) =>
      RestrictionResult(AlgebraicResult.Empty(AlgebraicEmptyReason.AllArguments), allPrefixesMatched = true)
    case (IntMap.Nil, _) =>
      RestrictionResult(
        AlgebraicResult.Empty(AlgebraicEmptyReason.EmptyArguments(AlgebraicResult.Left)),
        allPrefixesMatched = false
      )
    case (_, IntMap.Nil) =>
      RestrictionResult(
        AlgebraicResult.Empty(AlgebraicEmptyReason.EmptyArguments(AlgebraicResult.Right)),
        allPrefixesMatched = true
      )
    case _ if x eq p =>
      RestrictionResult(AlgebraicResult.Identity(AlgebraicResult.Both), allPrefixesMatched = true)
    case (IntMap.Tip(k, v), _) =>
      p.get(k) match
        case None =>
          RestrictionResult(AlgebraicResult.Empty(AlgebraicEmptyReason.NoPrefixMatch), allPrefixesMatched = false)
        case Some(w) =>
          val child = v.restrictionResult(w)
          child.result match
            case AlgebraicResult.Empty(_) =>
              RestrictionResult(AlgebraicResult.Empty(AlgebraicEmptyReason.NoPrefixMatch), allPrefixesMatched = false)
            case childResult =>
              p match
                case IntMap.Tip(_, _) =>
                  RestrictionResult(
                    nonEmptyResult(
                      IntMap.Tip(k, TrieSpace.binaryValue(childResult, v, w)),
                      AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Left),
                      AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Right)
                    ),
                    child.allPrefixesMatched
                  )
                case _ =>
                  RestrictionResult(
                    nonEmptyResult(
                      IntMap.Tip(k, TrieSpace.binaryValue(childResult, v, w)),
                      AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Left),
                      sameRight = false
                    ),
                    allPrefixesMatched = false
                  )
    case (_, IntMap.Tip(k, w)) =>
      x.get(k) match
        case None =>
          RestrictionResult(AlgebraicResult.Empty(AlgebraicEmptyReason.NoPrefixMatch), allPrefixesMatched = false)
        case Some(v) =>
          val child = v.restrictionResult(w)
          child.result match
            case AlgebraicResult.Empty(_) =>
              RestrictionResult(AlgebraicResult.Empty(AlgebraicEmptyReason.NoPrefixMatch), child.allPrefixesMatched)
            case childResult =>
              RestrictionResult(
                nonEmptyResult(
                  IntMap.Tip(k, TrieSpace.binaryValue(childResult, v, w)),
                  sameLeft = false,
                  AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Right)
                ),
                child.allPrefixesMatched
              )
    case (IntMap.Bin(p1, m1, l1, r1), IntMap.Bin(p2, m2, l2, r2)) =>
      if shorter(m1, m2) then
        if !hasMatch(p2, p1, m1) then
          RestrictionResult(AlgebraicResult.Empty(AlgebraicEmptyReason.NoPrefixMatch), allPrefixesMatched = false)
        else
          val leftBranch = if zero(p2, m1) then l1 else r1
          val child = restrictTriesResult(leftBranch, p)
          child.result match
            case AlgebraicResult.Empty(_) => child
            case childResult =>
              RestrictionResult(
                nonEmptyResult(
                  valueOf(childResult, leftBranch, p),
                  sameLeft = false,
                  AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Right)
                ),
                child.allPrefixesMatched
              )
      else if shorter(m2, m1) then
        if !hasMatch(p1, p2, m2) then
          RestrictionResult(AlgebraicResult.Empty(AlgebraicEmptyReason.NoPrefixMatch), allPrefixesMatched = false)
        else
          val rightBranch = if zero(p1, m2) then l2 else r2
          val child = restrictTriesResult(x, rightBranch)
          child.result match
            case AlgebraicResult.Empty(_) =>
              RestrictionResult(child.result, allPrefixesMatched = false)
            case childResult =>
              RestrictionResult(
                nonEmptyResult(
                  valueOf(childResult, x, rightBranch),
                  AlgebraicResult.equalsArgument(childResult, AlgebraicResult.Left),
                  sameRight = false
                ),
                allPrefixesMatched = false
              )
      else if p1 == p2 then
        val leftOutcome = restrictTriesResult(l1, l2)
        val rightOutcome = restrictTriesResult(r1, r2)
        val left = valueOf(leftOutcome.result, l1, l2)
        val right = valueOf(rightOutcome.result, r1, r2)
        if (left eq IntMap.Nil) && (right eq IntMap.Nil) then
          RestrictionResult(
            AlgebraicResult.Empty(AlgebraicEmptyReason.NoPrefixMatch),
            leftOutcome.allPrefixesMatched && rightOutcome.allPrefixesMatched
          )
        else
          RestrictionResult(
            nonEmptyResult(
              binPrune(p1, m1, left, right),
              AlgebraicResult.equalsArgument(leftOutcome.result, AlgebraicResult.Left) &&
                AlgebraicResult.equalsArgument(rightOutcome.result, AlgebraicResult.Left),
              AlgebraicResult.equalsArgument(leftOutcome.result, AlgebraicResult.Right) &&
                AlgebraicResult.equalsArgument(rightOutcome.result, AlgebraicResult.Right)
            ),
            leftOutcome.allPrefixesMatched && rightOutcome.allPrefixesMatched
          )
      else RestrictionResult(
        AlgebraicResult.Empty(AlgebraicEmptyReason.NoPrefixMatch),
        allPrefixesMatched = false
      )

  def restrictTries(x: IntMap[TrieSpace], p: IntMap[TrieSpace]): IntMap[TrieSpace] =
    valueOf(restrictTriesResult(x, p).result, x, p)

  def prefixRelation(x: IntMap[TrieSpace], p: IntMap[TrieSpace]): (Boolean, Boolean) = (x, p) match
    case (IntMap.Nil, IntMap.Nil) => true -> true
    case (_, IntMap.Nil) => true -> false
    case (IntMap.Nil, _) => false -> false
    case _ if x eq p => true -> true
    case (IntMap.Tip(k, v), _) =>
      p.get(k) match
        case None => false -> false
        case Some(w) =>
          val relation = v.prefixRelation(w)
          val onlyKey = p.isInstanceOf[IntMap.Tip[?]]
          (onlyKey && relation._1) -> (onlyKey && relation._2)
    case (_, IntMap.Tip(k, w)) =>
      x.get(k) match
        case None => false -> false
        case Some(v) => v.prefixRelation(w)._1 -> false
    case (IntMap.Bin(p1, m1, l1, r1), IntMap.Bin(p2, m2, l2, r2)) =>
      if shorter(m1, m2) then
        if !hasMatch(p2, p1, m1) then false -> false
        else prefixRelation(if zero(p2, m1) then l1 else r1, p)._1 -> false
      else if shorter(m2, m1) then false -> false
      else if p1 == p2 then
        val left = prefixRelation(l1, l2)
        val right = prefixRelation(r1, r2)
        (left._1 && right._1) -> (left._2 && right._2)
      else false -> false

  private inline def binPrune(
    p: Int,
    m: Int,
    l: IntMap[TrieSpace],
    r: IntMap[TrieSpace]
  ): IntMap[TrieSpace] =
    if r eq IntMap.Nil then l
    else if l eq IntMap.Nil then r
    else IntMap.Bin(p, m, l, r)
