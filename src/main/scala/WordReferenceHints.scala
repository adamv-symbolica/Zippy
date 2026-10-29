package morkl

/** Source-side binder facts, before one source symbol becomes several digits.
  * Exact length and maximum length remain distinct during encoding.
  */
object WordReferenceHints:
  def tag[P](expression: WordSpace[P], parameters: Vector[PathRef] = Vector.empty): WordSpace[P] =
    import WordSpace.*
    def path(p: WordPath[P], env: Map[String, PathRef]): WordPath[P] = p match
      case WordPath.Deref(r) => WordPath.Deref(env.getOrElse(r.s, r))
      case WordPath.Constant(_) => p
      case WordPath.Concat(l, r) => WordPath.Concat(path(l, env), path(r, env))
      case WordPath.GroundedPP(p, f) => WordPath.GroundedPP(path(p, env), f)
      case WordPath.GroundedSP(s, f) => WordPath.GroundedSP(space(s, env), f)
    def space(s: WordSpace[P], env: Map[String, PathRef]): WordSpace[P] = s match
      case Empty() | Literal(_) | Mention(_) => s
      case Call(r, ps, ss) => Call(r, ps.map(path(_, env)), ss.map(space(_, env)))
      case Singleton(p) => Singleton(path(p, env))
      case Union(l, r) => Union(space(l, env), space(r, env))
      case Intersection(l, r) => Intersection(space(l, env), space(r, env))
      case Subtraction(l, r) => Subtraction(space(l, env), space(r, env))
      case Restriction(l, r) => Restriction(space(l, env), space(r, env))
      case Raffination(l, r) => Raffination(space(l, env), space(r, env))
      case Composition(l, r) => Composition(space(l, env), space(r, env))
      case Iteration(src, h, t, b) => Iteration(space(src, env), h.known(1), t, space(b, if h.s == "_" then env else env.updated(h.s, h.known(1))))
      case Fold(src, i, a, h, t, b, u) =>
        val inner = (env - a.s).updated(h.s, h.known(1))
        Fold(space(src, env), path(i, env), a, h.known(1), t, space(b, inner), path(u, inner))
      case Fixpoint(i, v, step) => Fixpoint(space(i, env), v, space(step, env))
      case Wrap(src, p) => Wrap(space(src, env), path(p, env))
      case Unwrap(src, p) => Unwrap(space(src, env), path(p, env))
      case TailsUnion(src) => TailsUnion(space(src, env))
      case TailsIntersection(src) => TailsIntersection(space(src, env))
      case PrefixClosure(src) => PrefixClosure(space(src, env))
      case SuffixClosure(src) => SuffixClosure(space(src, env))
      case TailsClosure(src) => TailsClosure(space(src, env))
      case GroundedPS(p, f) => GroundedPS(path(p, env), f)
      case GroundedSS(s, f) => GroundedSS(space(s, env), f)
      case Range(s, start, end) => Range(space(s, env), start, end)
    space(expression, parameters.map(r => r.s -> r).toMap)

object WordPathLength:
  /** A sound upper bound, not an assertion that every encoded symbol has the
    * maximum length. This distinction matters for variable Fibonacci codes.
    */
  def upper[P](p: WordPath[P])(using ops: WordOps[P]): Option[BigInt] = p match
    case WordPath.Deref(r) => Option.when(r.maxLengthHint >= 0)(BigInt(r.maxLengthHint))
    case WordPath.Constant(p) => Some(BigInt(ops.length(p)))
    case WordPath.Concat(l, r) => for a <- upper(l); b <- upper(r) yield a + b
    case WordPath.GroundedPP(_, _) | WordPath.GroundedSP(_, _) => None
