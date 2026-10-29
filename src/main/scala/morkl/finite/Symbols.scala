package morkl.finite

import morkl.{Path as SymbolPath, Space as SymbolSpace, Routine as SymbolRoutine, *}

/** Lossless syntax bridge; the generic symbol instance is exactly Zippy_symbols. */
object Symbols:
  private final class FromMemo:
    val paths = java.util.IdentityHashMap[SymbolPath, Path[PathItem]]()
    val spaces = java.util.IdentityHashMap[SymbolSpace, Space[PathItem]]()
  private val fromMemo = new ThreadLocal[FromMemo]()
  private def withFrom[A](f: FromMemo => A): A =
    val existing = fromMemo.get()
    if existing != null then f(existing)
    else
      val memo = FromMemo()
      fromMemo.set(memo)
      try f(memo) finally fromMemo.remove()
  def fromPath(p: SymbolPath): Path[PathItem] = withFrom { memo =>
    val cached = memo.paths.get(p)
    if cached != null then cached
    else
      val result = fromPathUncached(p)
      memo.paths.put(p, result)
      result
  }
  def fromSpace(s: SymbolSpace): Space[PathItem] = withFrom { memo =>
    val cached = memo.spaces.get(s)
    if cached != null then cached
    else
      val result = fromSpaceUncached(s)
      memo.spaces.put(s, result)
      result
  }
  private def fromPathUncached(p: SymbolPath): Path[PathItem] = p match
    case SymbolPath.Deref(r) => Path.Deref(r)
    case SymbolPath.Constant(p) => Path.Constant(p.items.toVector)
    case SymbolPath.Concat(a, b) => Path.Concat(fromPath(a), fromPath(b))
    case SymbolPath.GroundedPP(p, f) => Path.GroundedPP(fromPath(p), xs => { val input = PathValue(xs.toList); TranslationAudit.callback(f, input)(f(input)).items.toVector })
    case SymbolPath.GroundedSP(s, f) => Path.GroundedSP(fromSpace(s), xs => { val input = value(xs); TranslationAudit.callback(f, input)(f(input)).items.toVector })
  private def fromSpaceUncached(s: SymbolSpace): Space[PathItem] = s match
    case SymbolSpace.Empty => Space.Empty()
    case SymbolSpace.Call(r, ps, ss) => Space.Call(r, ps.map(fromPath), ss.map(fromSpace))
    case SymbolSpace.Mention(r) => Space.Mention(r)
    case SymbolSpace.Singleton(p) => Space.Singleton(fromPath(p))
    case SymbolSpace.Literal(v) => Space.Literal(paths(v))
    case SymbolSpace.Union(a, b) => Space.Union(fromSpace(a), fromSpace(b))
    case SymbolSpace.Intersection(a, b) => Space.Intersection(fromSpace(a), fromSpace(b))
    case SymbolSpace.Subtraction(a, b) => Space.Subtraction(fromSpace(a), fromSpace(b))
    case SymbolSpace.Restriction(a, b) => Space.Restriction(fromSpace(a), fromSpace(b))
    case SymbolSpace.Raffination(a, b) => Space.Raffination(fromSpace(a), fromSpace(b))
    case SymbolSpace.Composition(a, b) => Space.Composition(fromSpace(a), fromSpace(b))
    case SymbolSpace.Iteration(src, h, r, body) => Space.Iteration(fromSpace(src), h, r, fromSpace(body))
    case SymbolSpace.Fold(src, initial, acc, h, r, body, update) =>
      Space.Fold(fromSpace(src), fromPath(initial), acc, h, r, fromSpace(body), fromPath(update))
    case SymbolSpace.Fixpoint(initial, v, step) => Space.Fixpoint(fromSpace(initial), v, fromSpace(step))
    case SymbolSpace.Wrap(src, p) => Space.Wrap(fromSpace(src), fromPath(p))
    case SymbolSpace.Unwrap(src, p) => Space.Unwrap(fromSpace(src), fromPath(p))
    case SymbolSpace.TailsUnion(src) => Space.TailsUnion(fromSpace(src))
    case SymbolSpace.TailsIntersection(src) => Space.TailsIntersection(fromSpace(src))
    case SymbolSpace.PrefixClosure(src) => Space.PrefixClosure(fromSpace(src))
    case SymbolSpace.SuffixClosure(src) => Space.SuffixClosure(fromSpace(src))
    case SymbolSpace.TailsClosure(src) => Space.TailsClosure(fromSpace(src))
    case SymbolSpace.GroundedPS(p, f) => Space.GroundedPS(fromPath(p), xs => { val input = PathValue(xs.toList); paths(TranslationAudit.callback(f, input)(f(input))) })
    case SymbolSpace.GroundedSS(s, f) => Space.GroundedSS(fromSpace(s), xs => { val input = value(xs); paths(TranslationAudit.callback(f, input)(f(input))) })
    case SymbolSpace.Range(src, a, b) => Space.Range(fromSpace(src), a, b)
  def fromRoutine(r: SymbolRoutine): Routine[PathItem] = Routine(r.name, r.refs, r.mentions, fromSpace(r.body))
  def paths(v: SpaceValue): Set[Vector[PathItem]] = v.paths.map(_.items.toVector)
  def value(ps: Set[Vector[PathItem]]): SpaceValue = SpaceValue(ps.map(xs => PathValue(xs.toList)))

  private final class ToMemo:
    val paths = java.util.IdentityHashMap[Path[PathItem], SymbolPath]()
    val spaces = java.util.IdentityHashMap[Space[PathItem], SymbolSpace]()
  private val toMemo = new ThreadLocal[ToMemo]()
  private def withTo[A](f: ToMemo => A): A =
    val existing = toMemo.get()
    if existing != null then f(existing)
    else
      val memo = ToMemo()
      toMemo.set(memo)
      try f(memo) finally toMemo.remove()
  def toPath(p: Path[PathItem]): SymbolPath = withTo { memo =>
    val cached = memo.paths.get(p)
    if cached != null then cached
    else
      val result = toPathUncached(p)
      memo.paths.put(p, result)
      result
  }
  def toSpace(s: Space[PathItem]): SymbolSpace = withTo { memo =>
    val cached = memo.spaces.get(s)
    if cached != null then cached
    else
      val result = toSpaceUncached(s)
      memo.spaces.put(s, result)
      result
  }
  private def toPathUncached(p: Path[PathItem]): SymbolPath = p match
    case Path.Deref(r) => SymbolPath.Deref(r)
    case Path.Constant(xs) => SymbolPath.Constant(PathValue(xs.toList))
    case Path.Concat(a, b) => SymbolPath.Concat(toPath(a), toPath(b))
    case Path.GroundedPP(p, f) => SymbolPath.GroundedPP(toPath(p), TranslationAudit.adapter((xs: PathValue) => PathValue(f(xs.items.toVector).toList)))
    case Path.GroundedSP(s, f) => SymbolPath.GroundedSP(toSpace(s), TranslationAudit.adapter((xs: SpaceValue) => PathValue(f(paths(xs)).toList)))
  private def toSpaceUncached(s: Space[PathItem]): SymbolSpace = s match
    case Space.Empty() => SymbolSpace.Empty
    case Space.Call(r, ps, ss) => SymbolSpace.Call(r, ps.map(toPath), ss.map(toSpace))
    case Space.Mention(r) => SymbolSpace.Mention(r)
    case Space.Singleton(p) => SymbolSpace.Singleton(toPath(p))
    case Space.Literal(v) => SymbolSpace.Literal(value(v))
    case Space.Union(a, b) => SymbolSpace.Union(toSpace(a), toSpace(b))
    case Space.Intersection(a, b) => SymbolSpace.Intersection(toSpace(a), toSpace(b))
    case Space.Subtraction(a, b) => SymbolSpace.Subtraction(toSpace(a), toSpace(b))
    case Space.Restriction(a, b) => SymbolSpace.Restriction(toSpace(a), toSpace(b))
    case Space.Raffination(a, b) => SymbolSpace.Raffination(toSpace(a), toSpace(b))
    case Space.Composition(a, b) => SymbolSpace.Composition(toSpace(a), toSpace(b))
    case Space.Iteration(src, h, r, body) => SymbolSpace.Iteration(toSpace(src), h, r, toSpace(body))
    case Space.Fold(src, initial, acc, h, r, body, update) =>
      SymbolSpace.Fold(toSpace(src), toPath(initial), acc, h, r, toSpace(body), toPath(update))
    case Space.Fixpoint(initial, v, step) => SymbolSpace.Fixpoint(toSpace(initial), v, toSpace(step))
    case Space.Wrap(src, p) => SymbolSpace.Wrap(toSpace(src), toPath(p))
    case Space.Unwrap(src, p) => SymbolSpace.Unwrap(toSpace(src), toPath(p))
    case Space.TailsUnion(src) => SymbolSpace.TailsUnion(toSpace(src))
    case Space.TailsIntersection(src) => SymbolSpace.TailsIntersection(toSpace(src))
    case Space.PrefixClosure(src) => SymbolSpace.PrefixClosure(toSpace(src))
    case Space.SuffixClosure(src) => SymbolSpace.SuffixClosure(toSpace(src))
    case Space.TailsClosure(src) => SymbolSpace.TailsClosure(toSpace(src))
    case Space.GroundedPS(p, f) => SymbolSpace.GroundedPS(toPath(p), TranslationAudit.adapter((xs: PathValue) => value(f(xs.items.toVector))))
    case Space.GroundedSS(s, f) => SymbolSpace.GroundedSS(toSpace(s), TranslationAudit.adapter((xs: SpaceValue) => value(f(paths(xs)))))
    case Space.Range(src, a, b) => SymbolSpace.Range(toSpace(src), a, b)
  def toRoutine(r: Routine[PathItem]): SymbolRoutine = SymbolRoutine(r.name, r.refs, r.mentions, toSpace(r.body))

  /** Visit syntax, including space expressions nested inside grounded paths. */
  def foreach[A](s: Space[A])(onSpace: Space[A] => Unit, onPath: Path[A] => Unit): Unit =
    val seenPaths = java.util.IdentityHashMap[Path[A], java.lang.Boolean]()
    val seenSpaces = java.util.IdentityHashMap[Space[A], java.lang.Boolean]()
    def p(x: Path[A]): Unit =
      if seenPaths.put(x, true) != null then return
      onPath(x)
      x match
        case Path.Concat(a, b) => p(a); p(b)
        case Path.GroundedPP(a, _) => p(a)
        case Path.GroundedSP(a, _) => rec(a)
        case _ => ()
    def rec(x: Space[A]): Unit =
      if seenSpaces.put(x, true) != null then return
      onSpace(x)
      x match
        case Space.Call(_, ps, ss) => ps.foreach(p); ss.foreach(rec)
        case Space.Singleton(a) => p(a)
        case Space.Union(a, b) => rec(a); rec(b)
        case Space.Intersection(a, b) => rec(a); rec(b)
        case Space.Subtraction(a, b) => rec(a); rec(b)
        case Space.Restriction(a, b) => rec(a); rec(b)
        case Space.Raffination(a, b) => rec(a); rec(b)
        case Space.Composition(a, b) => rec(a); rec(b)
        case Space.Iteration(src, _, _, body) => rec(src); rec(body)
        case Space.Fold(src, initial, _, _, _, body, update) => rec(src); p(initial); rec(body); p(update)
        case Space.Fixpoint(initial, _, step) => rec(initial); rec(step)
        case Space.Wrap(src, a) => rec(src); p(a)
        case Space.Unwrap(src, a) => rec(src); p(a)
        case Space.TailsUnion(src) => rec(src)
        case Space.TailsIntersection(src) => rec(src)
        case Space.PrefixClosure(src) => rec(src)
        case Space.SuffixClosure(src) => rec(src)
        case Space.TailsClosure(src) => rec(src)
        case Space.GroundedPS(a, _) => p(a)
        case Space.GroundedSS(src, _) => rec(src)
        case Space.Range(src, _, _) => rec(src)
        case _ => ()
    rec(s)

  def reachable(s: SymbolSpace, context: PartialFunction[RoutinePtr, SymbolRoutine]): Map[RoutinePtr, SymbolRoutine] =
    val found = scala.collection.mutable.Map.empty[RoutinePtr, SymbolRoutine]
    def visit(s: Space[PathItem]): Unit = foreach(s)({
      case Space.Call(rp, _, _) if !found.contains(rp) =>
        val routine = context(rp)
        found(rp) = routine
        visit(fromSpace(routine.body))
      case _ => ()
    }, _ => ())
    visit(fromSpace(s))
    found.toMap

case class SymbolTranslation[T, P, V](compiled: Translation[PathItem, T], storage: Storage[T, P, V],
                                     pathInputs: Map[PathRef, P], spaceInputs: Map[SpaceMention, V]):
  def run(): V = Evaluator(storage)(compiled.expression, pathInputs, spaceInputs, compiled.routines)
  def decode(value: V): SpaceValue = Symbols.value(compiled.decodePaths(storage.paths(value).map(storage.items)))
  def runDecoded(): SpaceValue = decode(run())

object ZippyTranslations:
  private def prepare(expression: SymbolSpace, ps: Map[PathRef, PathValue], ss: Map[SpaceMention, SpaceValue],
                      routines: Map[RoutinePtr, SymbolRoutine], extra: Iterable[PathItem]): PathItemInterner =
    val interner = PathItemInterner()
    extra.foreach(interner.intern)
    ps.values.foreach(_.items.foreach(interner.intern))
    ss.values.foreach(_.paths.foreach(_.items.foreach(interner.intern)))
    def visit(s: Space[PathItem]): Unit = Symbols.foreach(s)({
      case Space.Literal(paths) => paths.foreach(_.foreach(interner.intern))
      case _ => ()
    }, {
      case Path.Constant(items) => items.foreach(interner.intern)
      case _ => ()
    })
    visit(Symbols.fromSpace(expression))
    routines.values.foreach(r => visit(Symbols.fromSpace(r.body)))
    interner
  private def build[T, P, V](expression: SymbolSpace, ps: Map[PathRef, PathValue], ss: Map[SpaceMention, SpaceValue],
                             routines: Map[RoutinePtr, SymbolRoutine], code: Code[PathItem, T],
                             storage: Storage[T, P, V], unrollLimit: Int): SymbolTranslation[T, P, V] =
    val compiled = Translator(code, unrollLimit)(Symbols.fromSpace(expression), routines.view.mapValues(Symbols.fromRoutine).toMap)
    SymbolTranslation(compiled, storage, ps.view.mapValues(p => storage.path(code.encode(p.items.toVector))).toMap,
      ss.view.mapValues(v => storage.value(Symbols.paths(v).map(p => storage.path(code.encode(p))))).toMap)

  def symbolsToBits(expression: SymbolSpace, pathInputs: Map[PathRef, PathValue] = Map.empty,
                    spaceInputs: Map[SpaceMention, SpaceValue] = Map.empty,
                    routines: Map[RoutinePtr, SymbolRoutine] = Map.empty,
                    extraSymbols: Iterable[PathItem] = Vector.empty, unrollLimit: Int = 64): SymbolTranslation[Boolean, BigInt, BitSpace] =
    build(expression, pathInputs, spaceInputs, routines,
      Codes.internedBits(prepare(expression, pathInputs, spaceInputs, routines, extraSymbols)), BitStorage, unrollLimit)

  def stringsToBytes(expression: SymbolSpace, pathInputs: Map[PathRef, PathValue] = Map.empty,
                     spaceInputs: Map[SpaceMention, SpaceValue] = Map.empty,
                     routines: Map[RoutinePtr, SymbolRoutine] = Map.empty, unrollLimit: Int = 64): SymbolTranslation[Int, BytePath, ByteSpace] =
    build(expression, pathInputs, spaceInputs, routines, Codes.stringBytes, ByteStorage, unrollLimit)

  def internedStringsToBytes(expression: SymbolSpace, pathInputs: Map[PathRef, PathValue] = Map.empty,
                             spaceInputs: Map[SpaceMention, SpaceValue] = Map.empty,
                             routines: Map[RoutinePtr, SymbolRoutine] = Map.empty,
                             extraSymbols: Iterable[PathItem] = Vector.empty, unrollLimit: Int = 64): SymbolTranslation[Int, BytePath, ByteSpace] =
    build(expression, pathInputs, spaceInputs, routines,
      Codes.internedBytes(prepare(expression, pathInputs, spaceInputs, routines, extraSymbols)), ByteStorage, unrollLimit)

  def bitsToBytes(expression: Space[Boolean], routines: Map[RoutinePtr, Routine[Boolean]] = Map.empty): Translation[Boolean, Int] =
    Translator(Codes.bitsToBytes)(expression, routines)
  def bytesToBits(expression: Space[Int], routines: Map[RoutinePtr, Routine[Int]] = Map.empty): Translation[Int, Boolean] =
    Translator(Codes.bytesToBits)(expression, routines)
  def bitsToSymbols(expression: Space[Boolean], routines: Map[RoutinePtr, Routine[Boolean]] = Map.empty): Translation[Boolean, PathItem] =
    Translator(Codes.bitsToSymbols)(expression, routines)
  def bytesToSymbols(expression: Space[Int], routines: Map[RoutinePtr, Routine[Int]] = Map.empty): Translation[Int, PathItem] =
    Translator(Codes.bytesToSymbols)(expression, routines)
