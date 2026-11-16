package morkl

import scala.collection.mutable

/** Simplify a union-preserving consumer together with its producer. All length
  * facts refer to items of the current dialect, before any encoding of them.
  * No arbitrary iteration is distributed over its source's union: a body can
  * observe the complete group of tails belonging to a head.
  */
object EvidenceElimination:
  def apply(space: Space): Space = new Pass().run(ReferenceHints.tag(space))

  private enum Consumer:
    case Tail
    case Prefix(path: Path)

  private final class Pass:
    import Consumer.*
    private type Term = Space | Path
    private val epsilon = Space.Singleton(Path.ZERO)
    private val spaces = java.util.IdentityHashMap[Space, Space]()
    private val paths = java.util.IdentityHashMap[Path, Path]()
    private val projections = java.util.IdentityHashMap[Space, mutable.Map[Consumer, Space]]()
    private val sizes = java.util.IdentityHashMap[Term, java.lang.Long]()
    private val purity = java.util.IdentityHashMap[Term, java.lang.Boolean]()
    private val reserved = mutable.Set.empty[String]
    private var serial = 0

    private def children(term: Term): List[Term] = term match
      case Path.Concat(a, b) => List(a, b)
      case Path.GroundedPP(p, _) => List(p)
      case Path.GroundedSP(s, _) => List(s)
      case Space.Call(_, ps, ss) => ps.toList ++ ss.toList
      case Space.Singleton(p) => List(p)
      case Space.Union(a, b) => List(a, b)
      case Space.Intersection(a, b) => List(a, b)
      case Space.Subtraction(a, b) => List(a, b)
      case Space.Restriction(a, b) => List(a, b)
      case Space.Raffination(a, b) => List(a, b)
      case Space.Composition(a, b) => List(a, b)
      case Space.Iteration(s, _, _, b) => List(s, b)
      case Space.Fold(s, p, _, _, _, b, u) => List(s, p, b, u)
      case Space.Fixpoint(s, _, b) => List(s, b)
      case Space.Wrap(s, p) => List(s, p)
      case Space.Unwrap(s, p) => List(s, p)
      case Space.TailsUnion(s) => List(s)
      case Space.TailsIntersection(s) => List(s)
      case Space.PrefixClosure(s) => List(s)
      case Space.SuffixClosure(s) => List(s)
      case Space.TailsClosure(s) => List(s)
      case Space.GroundedPS(p, _) => List(p)
      case Space.GroundedSS(s, _) => List(s)
      case Space.Range(s, _, _) => List(s)
      case _ => Nil

    def run(space: Space): Space =
      val seen = java.util.IdentityHashMap[Term, java.lang.Boolean]()
      def reserve(term: Term): Unit = if seen.put(term, true) == null then
        term match
          case Space.Iteration(_, h, t, _) => reserved ++= Set(h.s, t.s)
          case Space.Fold(_, _, acc, h, t, _, _) => reserved ++= Set(acc.s, h.s, t.s)
          case Space.Fixpoint(_, v, _) => reserved += v.s
          case Space.Mention(v) => reserved += v.s
          case Path.Deref(v) => reserved += v.s
          case _ => ()
        children(term).foreach(reserve)
      reserve(space)
      recs(space)

    private def fresh(): PathRef =
      var name = s"#evidence$$$serial"
      while reserved(name) do
        serial += 1
        name = s"#evidence$$$serial"
      serial += 1
      reserved += name
      PathRef(name)

    private def pure(s: Term): Boolean =
      val cached = purity.get(s)
      if cached != null then cached.booleanValue
      else
        val result = s match
          case Space.Call(_, _, _) | Space.GroundedPS(_, _) | Space.GroundedSS(_, _) |
              Path.GroundedPP(_, _) | Path.GroundedSP(_, _) => false
          case _ => children(s).forall(pure)
        purity.put(s, result)
        result

    private def independent(s: Space, h: PathRef, t: SpaceMention): Boolean =
      (h.s == "_" || !Matching.freeRefs(s)(h)) && (t.s == "_" || !Matching.freeMentions(s)(t))

    private def size(s: Term): Long =
      val cached = sizes.get(s)
      if cached != null then cached.longValue
      else
        // Count logical occurrences without walking a shared DAG repeatedly;
        // saturation makes the growth check conservative even on huge trees.
        val n = children(s).foldLeft(1L)((n, c) => (n + size(c)).min(Long.MaxValue / 2))
        sizes.put(s, n)
        n

    private def factors(p: Path): List[Path] = p.factors.filterNot(_ == Path.ZERO)
    private def path(ps: List[Path]): Path = Path.fromFactors(ps)

    /** Cancel across concat association and partial constant factors. Unknown
      * path lengths never justify deleting one of their items.
      */
    private def strip(full: Path, prefix: Path): Option[Path] =
      def loop(fs: List[Path], ps: List[Path]): Option[Path] = (fs, ps) match
        case (_, Nil) => Some(path(fs))
        case (f :: ft, p :: pt) if f == p => loop(ft, pt)
        case (Path.Constant(PathValue(f)) :: ft, Path.Constant(PathValue(p)) :: pt) =>
          if f.startsWith(p) then loop(factors(Path.Constant(PathValue(f.drop(p.length)))) ++ ft, pt)
          else if p.startsWith(f) then loop(ft, factors(Path.Constant(PathValue(p.drop(f.length)))) ++ pt)
          else None
        case _ => None
      loop(factors(full), factors(prefix))

    private def tail(p: Path): Option[Path] = factors(p) match
      case Path.Constant(PathValue(_ :: rest)) :: more =>
        Some(path(factors(Path.Constant(PathValue(rest))) ++ more))
      case Path.Deref(h) :: more if h.lengthHint == 1 => Some(path(more))
      case Path.Deref(h) :: more if h.lengthHint == 0 => tail(path(more))
      case _ => None

    private def wrap(s: Space, p: Path): Space =
      if s == Space.Empty && pure(p) then s
      else if p == Path.ZERO then s
      else Space.Wrap(s, p)

    private def union(a: Space, b: Space): Space = (a, b) match
      case (Space.Empty, _) => b
      case (_, Space.Empty) => a
      case _ if a == b && pure(a) => a
      case (Space.Wrap(x, p), Space.Wrap(y, q)) if p == q && pure(p) => wrap(union(x, y), p)
      case _ => Space.Union(a, b)

    private def headed(s: Space): Boolean = s match
      case Space.Singleton(Path.Constant(PathValue(_ :: _))) => true
      case Space.Literal(v) => v.paths.exists(_.items.nonEmpty)
      case Space.Union(a, b) => headed(a) || headed(b)
      case _ => false

    private def iteration(src: Space, h: PathRef, t: SpaceMention, body: Space): Space =
      if src == Space.Empty || (body == Space.Empty && pure(src)) then Space.Empty
      else
        val emitted = body match
          case Space.Wrap(Space.Mention(rest), p) if h.s != "_" && t.s != "_" && rest == t => Some(p)
          case Space.Composition(Space.Singleton(p), Space.Mention(rest)) if h.s != "_" && t.s != "_" && rest == t => Some(p)
          case _ => None
        val prefix = emitted.flatMap { p => factors(p).reverse match
          case Path.Deref(ref) :: more if ref == h =>
            val p = path(more.reverse)
            Option.when(independent(Space.Singleton(p), h, t) && pure(p))(p)
          case _ => None
        }
        prefix match
          case Some(p) => wrap(Space.Subtraction(src, epsilon), p)
          case None if independent(body, h, t) && pure(body) =>
            if headed(src) && pure(src) then body
            else src match
              // A singleton window already runs the body at most once, even
              // when its selected path is epsilon. A second selection here
              // becomes another full symbol scanner after variable encoding.
              case Space.Range(_, lo, hi) if RangeBounds.cardinalityUpperBound(lo, hi).exists(_ <= 1) =>
                Space.Iteration(src, h, t, body)
              case _ => Space.Iteration(Space.Range(Space.Subtraction(src, epsilon), 0, 1), h, t, body)
          case _ => Space.Iteration(src, h, t, body)

    private def raw(s: Space, c: Consumer): Space = c match
      case Tail => Space.TailsUnion(s)
      case Prefix(p) => Space.Unwrap(s, p)

    private def project(s: Space, c: Consumer): Space =
      var byConsumer = projections.get(s)
      if byConsumer == null then
        byConsumer = mutable.Map.empty
        projections.put(s, byConsumer)
      byConsumer.getOrElseUpdate(c, projectUncached(s, c))

    private def projectUncached(s: Space, c: Consumer): Space =
      def cancel(value: Space, p: Path): Option[Space] = c match
        case Prefix(q) => strip(p, q).map(wrap(recs(value), _))
        case Tail => tail(p).map(wrap(recs(value), _))
      (s, c) match
        case (_, Prefix(p)) if p == Path.ZERO => recs(s)
        case (Space.Empty, _) => Space.Empty
        case (Space.Wrap(value, p), _) if p == Path.ZERO => project(value, c)
        case (Space.Wrap(value, p), _) if pure(p) =>
          cancel(value, recp(p)).getOrElse(raw(wrap(recs(value), recp(p)), c))
        case (Space.Singleton(p), _) if pure(p) =>
          cancel(epsilon, recp(p)).getOrElse(raw(Space.Singleton(recp(p)), c))
        case (Space.Iteration(src, h, t, body), Prefix(p))
            if !independent(Space.Singleton(p), h, t) =>
          val marker = fresh()
          val scoped = Space.Iteration(src, h, t, Space.Unwrap(body, Path.Deref(marker)))
          // Substitution alpha-renames only the binders that would capture p.
          recs(Matching.subst(scoped, pm = Map(marker -> p)))
        case (Space.Iteration(src, h, t, body), _) =>
          iteration(recs(src), h, t, project(body, c))
        case (Space.Union(a, b), _) =>
          val candidate = union(project(a, c), project(b, c))
          val retained = raw(recs(s), c)
          // Distribution must pay for itself through cancellation/factoring.
          // This also avoids fighting normalizeUnion's common-prefix factoring.
          if size(candidate) < size(retained) then candidate else retained
        case _ =>
          val simplified = recs(s)
          if simplified != s then project(simplified, c) else raw(simplified, c)

    private def recp(p: Path): Path =
      val cached = paths.get(p)
      if cached != null then cached
      else
        val out = p match
          case Path.Deref(_) | Path.Constant(_) => p
          case Path.Concat(a, b) =>
            (recp(a), recp(b)) match
              case (Path.Constant(PathValue(Nil)), q) => q
              case (q, Path.Constant(PathValue(Nil))) => q
              case (a, b) => Path.Concat(a, b)
          case Path.GroundedPP(a, f) => Path.GroundedPP(recp(a), f)
          case Path.GroundedSP(a, f) => Path.GroundedSP(recs(a), f)
        val result = if out == p then p else out
        paths.put(p, result)
        result

    private def recs(s: Space): Space =
      val cached = spaces.get(s)
      if cached != null then cached
      else
        val out = s match
          case Space.Empty | Space.Mention(_) | Space.Literal(_) => s
          case Space.Call(r, ps, ss) => Space.Call(r, ps.map(recp), ss.map(recs))
          case Space.Singleton(p) => Space.Singleton(recp(p))
          case Space.Union(a, b) => union(recs(a), recs(b))
          case Space.Intersection(a, b) => Space.Intersection(recs(a), recs(b))
          case Space.Subtraction(a, b) => Space.Subtraction(recs(a), recs(b))
          case Space.Restriction(a, b) => Space.Restriction(recs(a), recs(b))
          case Space.Raffination(a, b) => Space.Raffination(recs(a), recs(b))
          case Space.Composition(a, b) => Space.Composition(recs(a), recs(b))
          case Space.Iteration(a, h, t, b) => iteration(recs(a), h, t, recs(b))
          case Space.Fold(a, p, acc, h, t, b, u) => Space.Fold(recs(a), recp(p), acc, h, t, recs(b), recp(u))
          case Space.Fixpoint(a, v, b) => Space.Fixpoint(recs(a), v, recs(b))
          case Space.Wrap(a, p) => wrap(recs(a), recp(p))
          case Space.Unwrap(a, p) =>
            val q = recp(p)
            if pure(q) then project(a, Prefix(q)) else Space.Unwrap(recs(a), q)
          case Space.TailsUnion(a) => project(a, Tail)
          case Space.TailsIntersection(a) => Space.TailsIntersection(recs(a))
          case Space.PrefixClosure(a) => Space.PrefixClosure(recs(a))
          case Space.SuffixClosure(a) => Space.SuffixClosure(recs(a))
          case Space.TailsClosure(a) => Space.TailsClosure(recs(a))
          case Space.GroundedPS(p, f) => Space.GroundedPS(recp(p), f)
          case Space.GroundedSS(a, f) => Space.GroundedSS(recs(a), f)
          case Space.Range(a, lo, hi) => Space.Range(recs(a), lo, hi)
        val result = if out == s then s else out
        spaces.put(s, result)
        result
