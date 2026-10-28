package morkl

private[morkl] object AlphabetSyntax:
  type Free = (Set[PathRef], Set[SpaceMention])
  private val none: Free = (Set.empty, Set.empty)
  private def both(a: Free, b: Free): Free = (a._1 ++ b._1, a._2 ++ b._2)
  def freePath[P](p: AlphabetPath[P]): Free = p match
    case AlphabetPath.Deref(r) => (Set(r), Set.empty)
    case AlphabetPath.Constant(_) => none
    case AlphabetPath.Concat(a, b) => both(freePath(a), freePath(b))
    case AlphabetPath.GroundedPP(p, _) => freePath(p)
    case AlphabetPath.GroundedSP(s, _) => free(s)
  def free[P](s: AlphabetSpace[P]): Free =
    import AlphabetSpace.*
    def without(f: Free, ps: Set[PathRef], ss: Set[SpaceMention]): Free = (f._1 -- ps.filter(_.s != "_"), f._2 -- ss.filter(_.s != "_"))
    s match
      case Empty() | Literal(_) => none
      case Mention(r) => (Set.empty, Set(r))
      case Singleton(p) => freePath(p)
      case Call(_, ps, ss) => (ps.map(freePath) ++ ss.map(free)).foldLeft(none)(both)
      case Union(a, b) => both(free(a), free(b))
      case Intersection(a, b) => both(free(a), free(b))
      case Subtraction(a, b) => both(free(a), free(b))
      case Restriction(a, b) => both(free(a), free(b))
      case Raffination(a, b) => both(free(a), free(b))
      case Composition(a, b) => both(free(a), free(b))
      case Iteration(src, h, r, b) => both(free(src), without(free(b), Set(h), Set(r)))
      case Fold(src, init, a, h, r, b, u) => both(both(free(src), freePath(init)), without(both(free(b), freePath(u)), Set(a, h), Set(r)))
      case Fixpoint(init, v, step) => both(free(init), without(free(step), Set.empty, Set(v)))
      case Wrap(src, p) => both(free(src), freePath(p))
      case Unwrap(src, p) => both(free(src), freePath(p))
      case TailsUnion(src) => free(src)
      case TailsIntersection(src) => free(src)
      case PrefixClosure(src) => free(src)
      case SuffixClosure(src) => free(src)
      case TailsClosure(src) => free(src)
      case GroundedPS(p, _) => freePath(p)
      case GroundedSS(s, _) => free(s)
      case Range(src, _, _) => free(src)

  /** Exhaustive structural map, including spaces inside grounded path nodes. */
  def map[P, Q](s: AlphabetSpace[P])(pHook: AlphabetPath[P] => Option[AlphabetPath[Q]], sHook: AlphabetSpace[P] => Option[AlphabetSpace[Q]], value: P => Q, inverse: Q => P): AlphabetSpace[Q] =
    def p(x: AlphabetPath[P]): AlphabetPath[Q] = pHook(x).getOrElse(x match
      case AlphabetPath.Deref(r) => AlphabetPath.Deref(r)
      case AlphabetPath.Constant(v) => AlphabetPath.Constant(value(v))
      case AlphabetPath.Concat(a, b) => AlphabetPath.Concat(p(a), p(b))
      case AlphabetPath.GroundedPP(x, f) => AlphabetPath.GroundedPP(p(x), q => value(f(inverse(q))))
      case AlphabetPath.GroundedSP(x, f) => AlphabetPath.GroundedSP(rec(x), qs => value(f(qs.map(inverse))))
    )
    def rec(x: AlphabetSpace[P]): AlphabetSpace[Q] = sHook(x).getOrElse {
      import AlphabetSpace.*
      x match
        case Empty() => Empty()
        case Call(r, ps, ss) => Call(r, ps.map(p), ss.map(rec))
        case Mention(r) => Mention(r)
        case Singleton(x) => Singleton(p(x))
        case Literal(v) => Literal(v.map(value))
        case Union(a, b) => Union(rec(a), rec(b))
        case Intersection(a, b) => Intersection(rec(a), rec(b))
        case Subtraction(a, b) => Subtraction(rec(a), rec(b))
        case Restriction(a, b) => Restriction(rec(a), rec(b))
        case Raffination(a, b) => Raffination(rec(a), rec(b))
        case Composition(a, b) => Composition(rec(a), rec(b))
        case Iteration(src, h, r, b) => Iteration(rec(src), h, r, rec(b))
        case Fold(src, init, a, h, r, b, u) => Fold(rec(src), p(init), a, h, r, rec(b), p(u))
        case Fixpoint(init, v, step) => Fixpoint(rec(init), v, rec(step))
        case Wrap(src, x) => Wrap(rec(src), p(x))
        case Unwrap(src, x) => Unwrap(rec(src), p(x))
        case TailsUnion(src) => TailsUnion(rec(src))
        case TailsIntersection(src) => TailsIntersection(rec(src))
        case PrefixClosure(src) => PrefixClosure(rec(src))
        case SuffixClosure(src) => SuffixClosure(rec(src))
        case TailsClosure(src) => TailsClosure(rec(src))
        case GroundedPS(x, f) => GroundedPS(p(x), q => f(inverse(q)).map(value))
        case GroundedSS(x, f) => GroundedSS(rec(x), qs => f(qs.map(inverse)).map(value))
        case Range(src, a, b) => Range(rec(src), a, b)
    }
    rec(s)
