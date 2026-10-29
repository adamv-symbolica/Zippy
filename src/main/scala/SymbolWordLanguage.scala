package morkl

/** The symbol language can also participate in the common, typed translation
  * machinery. These are syntax conversions, not alternate runtime operations.
  */
object SymbolWordLanguage:
  def path(p: Path): WordPath[PathValue] = p match
    case Path.Deref(pr) => WordPath.Deref(pr)
    case Path.Constant(p) => WordPath.Constant(p)
    case Path.Concat(l, r) => WordPath.Concat(path(l), path(r))
    case Path.GroundedPP(p, f) => WordPath.GroundedPP(path(p), f)
    case Path.GroundedSP(s, f) => WordPath.GroundedSP(space(s), x => f(SpaceValue(x.paths)))
  def space(s: Space): WordSpace[PathValue] = s match
    case Space.Empty => WordSpace.Empty()
    case Space.Call(r, ps, ss) => WordSpace.Call(r, ps.map(path), ss.map(space))
    case Space.Mention(m) => WordSpace.Mention(m)
    case Space.Singleton(p) => WordSpace.Singleton(path(p))
    case Space.Literal(v) => WordSpace.Literal(WordSpaceValue(v.paths))
    case Space.Union(x, y) => WordSpace.Union(space(x), space(y))
    case Space.Intersection(x, y) => WordSpace.Intersection(space(x), space(y))
    case Space.Subtraction(x, y) => WordSpace.Subtraction(space(x), space(y))
    case Space.Restriction(x, y) => WordSpace.Restriction(space(x), space(y))
    case Space.Raffination(x, y) => WordSpace.Raffination(space(x), space(y))
    case Space.Composition(x, y) => WordSpace.Composition(space(x), space(y))
    case Space.Iteration(s, h, t, b) => WordSpace.Iteration(space(s), h, t, space(b))
    case Space.Fold(s, i, a, h, t, b, u) => WordSpace.Fold(space(s), path(i), a, h, t, space(b), path(u))
    case Space.Fixpoint(i, v, s) => WordSpace.Fixpoint(space(i), v, space(s))
    case Space.Wrap(s, p) => WordSpace.Wrap(space(s), path(p))
    case Space.Unwrap(s, p) => WordSpace.Unwrap(space(s), path(p))
    case Space.TailsUnion(s) => WordSpace.TailsUnion(space(s))
    case Space.TailsIntersection(s) => WordSpace.TailsIntersection(space(s))
    case Space.PrefixClosure(s) => WordSpace.PrefixClosure(space(s))
    case Space.SuffixClosure(s) => WordSpace.SuffixClosure(space(s))
    case Space.TailsClosure(s) => WordSpace.TailsClosure(space(s))
    case Space.GroundedPS(p, f) => WordSpace.GroundedPS(path(p), x => WordSpaceValue(f(x).paths))
    case Space.GroundedSS(s, f) => WordSpace.GroundedSS(space(s), x => WordSpaceValue(f(SpaceValue(x.paths)).paths))
    case Space.Range(s, a, b) => WordSpace.Range(space(s), a, b)
  def routine(r: Routine): WordRoutine[PathValue] = WordRoutine(r.name, r.refs, r.mentions, space(r.body))

  def symbolPath(p: WordPath[PathValue]): Path = p match
    case WordPath.Deref(pr) => Path.Deref(pr)
    case WordPath.Constant(p) => Path.Constant(p)
    case WordPath.Concat(l, r) => Path.Concat(symbolPath(l), symbolPath(r))
    case WordPath.GroundedPP(p, f) => Path.GroundedPP(symbolPath(p), f)
    case WordPath.GroundedSP(s, f) => Path.GroundedSP(symbolSpace(s), x => f(WordSpaceValue(x.paths)))
  def symbolSpace(s: WordSpace[PathValue]): Space = s match
    case WordSpace.Empty() => Space.Empty
    case WordSpace.Call(r, ps, ss) => Space.Call(r, ps.map(symbolPath), ss.map(symbolSpace))
    case WordSpace.Mention(m) => Space.Mention(m)
    case WordSpace.Singleton(p) => Space.Singleton(symbolPath(p))
    case WordSpace.Literal(v) => Space.Literal(SpaceValue(v.paths))
    case WordSpace.Union(x, y) => Space.Union(symbolSpace(x), symbolSpace(y))
    case WordSpace.Intersection(x, y) => Space.Intersection(symbolSpace(x), symbolSpace(y))
    case WordSpace.Subtraction(x, y) => Space.Subtraction(symbolSpace(x), symbolSpace(y))
    case WordSpace.Restriction(x, y) => Space.Restriction(symbolSpace(x), symbolSpace(y))
    case WordSpace.Raffination(x, y) => Space.Raffination(symbolSpace(x), symbolSpace(y))
    case WordSpace.Composition(x, y) => Space.Composition(symbolSpace(x), symbolSpace(y))
    case WordSpace.Iteration(s, h, t, b) => Space.Iteration(symbolSpace(s), h, t, symbolSpace(b))
    case WordSpace.Fold(s, i, a, h, t, b, u) => Space.Fold(symbolSpace(s), symbolPath(i), a, h, t, symbolSpace(b), symbolPath(u))
    case WordSpace.Fixpoint(i, v, s) => Space.Fixpoint(symbolSpace(i), v, symbolSpace(s))
    case WordSpace.Wrap(s, p) => Space.Wrap(symbolSpace(s), symbolPath(p))
    case WordSpace.Unwrap(s, p) => Space.Unwrap(symbolSpace(s), symbolPath(p))
    case WordSpace.TailsUnion(s) => Space.TailsUnion(symbolSpace(s))
    case WordSpace.TailsIntersection(s) => Space.TailsIntersection(symbolSpace(s))
    case WordSpace.PrefixClosure(s) => Space.PrefixClosure(symbolSpace(s))
    case WordSpace.SuffixClosure(s) => Space.SuffixClosure(symbolSpace(s))
    case WordSpace.TailsClosure(s) => Space.TailsClosure(symbolSpace(s))
    case WordSpace.GroundedPS(p, f) => Space.GroundedPS(symbolPath(p), x => SpaceValue(f(x).paths))
    case WordSpace.GroundedSS(s, f) => Space.GroundedSS(symbolSpace(s), x => SpaceValue(f(WordSpaceValue(x.paths)).paths))
    case WordSpace.Range(s, a, b) => Space.Range(symbolSpace(s), a, b)
  def symbolRoutine(r: WordRoutine[PathValue]): Routine = Routine(r.name, r.refs, r.mentions, symbolSpace(r.body))

final class SymbolWords(val interner: PathItemInterner, closed: Boolean = false, admissibleVocabulary: Option[Set[PathItem]] = None) extends WordOps[PathValue]:
  private val allowed = admissibleVocabulary.map(_.iterator.map(item => item.show -> interner.lookup(item).get).toMap)
    .orElse(if closed then Some((0 until interner.size).map(i => interner.decodeItem(i).show -> i).toMap) else None)
  def alphabetSize: Int = interner.size
  def digits(p: PathValue): Vector[Int] = allowed match
    case None => interner.encode(p).toVector
    case Some(ids) => p.items.iterator.map(item => ids.getOrElse(item.show,
      throw IllegalArgumentException(s"symbol ${item.show} is outside the closed dictionary"))).toVector
  def fromDigits(p: Iterable[Int]): PathValue =
    val value = interner.decodePath(p)
    allowed.foreach(ids => require(value.items.forall(item => ids.contains(item.show)), "symbol outside frozen vocabulary"))
    value
  override def compare(p: PathValue, q: PathValue): Int = PathItemOrder.given_Ordering_PathValue.compare(p, q)
