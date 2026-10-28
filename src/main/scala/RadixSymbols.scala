package morkl

/** Adapter to the existing symbol AST; the public symbol language remains morkl.Space. */
object SymbolSyntax extends DigitLanguage[morkl.PathValue](new DigitPaths[morkl.PathValue]:
  val alphabetSize = Int.MaxValue
  val empty = morkl.PathValue(Nil)
  def fromDigits(ds: Iterable[Int]): morkl.PathValue = PathItemInterner.global.decodePath(ds)
  def digits(p: morkl.PathValue): Vector[Int] = PathItemInterner.global.encode(p).toVector
  override def compare(a: morkl.PathValue, b: morkl.PathValue): Int =
    import PathItemOrder.given
    summon[Ordering[morkl.PathValue]].compare(a, b)
):
  def importPath(x: morkl.Path): Path = x match
    case morkl.Path.Deref(r) => Path.Deref(r)
    case morkl.Path.Constant(v) => Path.Constant(v)
    case morkl.Path.Concat(a, b) => Path.Concat(importPath(a), importPath(b))
    case morkl.Path.GroundedPP(a, f) => Path.GroundedPP(importPath(a), f)
    case morkl.Path.GroundedSP(a, f) => Path.GroundedSP(importSpace(a), v => f(morkl.SpaceValue(v.paths)))
  def importSpace(x: morkl.Space): Space = x match
    case morkl.Space.Empty => Space.Empty
    case morkl.Space.Call(r, ps, ss) => Space.Call(r, ps.map(importPath), ss.map(importSpace))
    case morkl.Space.Mention(r) => Space.Mention(r)
    case morkl.Space.Singleton(p) => Space.Singleton(importPath(p))
    case morkl.Space.Literal(v) => Space.Literal(SpaceValue(v.paths))
    case morkl.Space.Union(a, b) => Space.Union(importSpace(a), importSpace(b))
    case morkl.Space.Intersection(a, b) => Space.Intersection(importSpace(a), importSpace(b))
    case morkl.Space.Subtraction(a, b) => Space.Subtraction(importSpace(a), importSpace(b))
    case morkl.Space.Restriction(a, b) => Space.Restriction(importSpace(a), importSpace(b))
    case morkl.Space.Raffination(a, b) => Space.Raffination(importSpace(a), importSpace(b))
    case morkl.Space.Composition(a, b) => Space.Composition(importSpace(a), importSpace(b))
    case morkl.Space.Iteration(a, h, t, b) => Space.Iteration(importSpace(a), h, t, importSpace(b))
    case morkl.Space.Fold(a, i, acc, h, t, b, u) => Space.Fold(importSpace(a), importPath(i), acc, h, t, importSpace(b), importPath(u))
    case morkl.Space.Fixpoint(a, v, b) => Space.Fixpoint(importSpace(a), v, importSpace(b))
    case morkl.Space.Wrap(a, p) => Space.Wrap(importSpace(a), importPath(p))
    case morkl.Space.Unwrap(a, p) => Space.Unwrap(importSpace(a), importPath(p))
    case morkl.Space.TailsUnion(a) => Space.TailsUnion(importSpace(a))
    case morkl.Space.TailsIntersection(a) => Space.TailsIntersection(importSpace(a))
    case morkl.Space.PrefixClosure(a) => Space.PrefixClosure(importSpace(a))
    case morkl.Space.SuffixClosure(a) => Space.SuffixClosure(importSpace(a))
    case morkl.Space.TailsClosure(a) => Space.TailsClosure(importSpace(a))
    case morkl.Space.GroundedPS(p, f) => Space.GroundedPS(importPath(p), v => SpaceValue(f(v).paths))
    case morkl.Space.GroundedSS(a, f) => Space.GroundedSS(importSpace(a), v => SpaceValue(f(morkl.SpaceValue(v.paths)).paths))
    case morkl.Space.Range(a, start, end) => Space.Range(importSpace(a), start, end)
  def importRoutine(r: morkl.Routine): Routine = Routine(r.name, r.refs, r.mentions, importSpace(r.body))

  def exportPath(x: Path): morkl.Path = x match
    case Path.Deref(r) => morkl.Path.Deref(r)
    case Path.Constant(v) => morkl.Path.Constant(v)
    case Path.Concat(a, b) => morkl.Path.Concat(exportPath(a), exportPath(b))
    case Path.GroundedPP(a, f) => morkl.Path.GroundedPP(exportPath(a), f)
    case Path.GroundedSP(a, f) => morkl.Path.GroundedSP(exportSpace(a), v => f(SpaceValue(v.paths)))
  def exportSpace(x: Space): morkl.Space = x match
    case Space.Empty => morkl.Space.Empty
    case Space.Call(r, ps, ss) => morkl.Space.Call(r, ps.map(exportPath), ss.map(exportSpace))
    case Space.Mention(r) => morkl.Space.Mention(r)
    case Space.Singleton(p) => morkl.Space.Singleton(exportPath(p))
    case Space.Literal(v) => morkl.Space.Literal(morkl.SpaceValue(v.paths))
    case Space.Union(a, b) => morkl.Space.Union(exportSpace(a), exportSpace(b))
    case Space.Intersection(a, b) => morkl.Space.Intersection(exportSpace(a), exportSpace(b))
    case Space.Subtraction(a, b) => morkl.Space.Subtraction(exportSpace(a), exportSpace(b))
    case Space.Restriction(a, b) => morkl.Space.Restriction(exportSpace(a), exportSpace(b))
    case Space.Raffination(a, b) => morkl.Space.Raffination(exportSpace(a), exportSpace(b))
    case Space.Composition(a, b) => morkl.Space.Composition(exportSpace(a), exportSpace(b))
    case Space.Iteration(a, h, t, b) => morkl.Space.Iteration(exportSpace(a), h, t, exportSpace(b))
    case Space.Fold(a, i, acc, h, t, b, u) => morkl.Space.Fold(exportSpace(a), exportPath(i), acc, h, t, exportSpace(b), exportPath(u))
    case Space.Fixpoint(a, v, b) => morkl.Space.Fixpoint(exportSpace(a), v, exportSpace(b))
    case Space.Wrap(a, p) => morkl.Space.Wrap(exportSpace(a), exportPath(p))
    case Space.Unwrap(a, p) => morkl.Space.Unwrap(exportSpace(a), exportPath(p))
    case Space.TailsUnion(a) => morkl.Space.TailsUnion(exportSpace(a))
    case Space.TailsIntersection(a) => morkl.Space.TailsIntersection(exportSpace(a))
    case Space.PrefixClosure(a) => morkl.Space.PrefixClosure(exportSpace(a))
    case Space.SuffixClosure(a) => morkl.Space.SuffixClosure(exportSpace(a))
    case Space.TailsClosure(a) => morkl.Space.TailsClosure(exportSpace(a))
    case Space.GroundedPS(p, f) => morkl.Space.GroundedPS(exportPath(p), v => morkl.SpaceValue(f(v).paths))
    case Space.GroundedSS(a, f) => morkl.Space.GroundedSS(exportSpace(a), v => morkl.SpaceValue(f(SpaceValue(v.paths)).paths))
    case Space.Range(a, start, end) => morkl.Space.Range(exportSpace(a), start, end)
  def exportRoutine(r: Routine): morkl.Routine = morkl.Routine(r.name, r.refs, r.mentions, exportSpace(r.body))

case class SymbolProgram(entry: morkl.Space, routines: Map[RoutinePtr, morkl.Routine]):
  def eval()(using pc: PathContext = PathContext.emptyMap,
                 sc: SpaceContext = SpaceContextMap(Map.empty)): morkl.SpaceValue =
    morkl.eval(entry)(using pc, sc, routines)
