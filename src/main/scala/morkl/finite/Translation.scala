package morkl.finite

import morkl.{PathRef, SpaceMention, RoutinePtr}
import scala.collection.mutable

case class FreeVariables(paths: Set[PathRef], spaces: Set[SpaceMention]):
  def ++(other: FreeVariables): FreeVariables = FreeVariables(paths ++ other.paths, spaces ++ other.spaces)
  def bind(ps: PathRef*)(ss: SpaceMention*): FreeVariables =
    FreeVariables(paths -- ps.filterNot(_.s == "_"), spaces -- ss.filterNot(_.s == "_"))

object FreeVariables:
  val empty: FreeVariables = FreeVariables(Set.empty, Set.empty)
  def path[A](p: Path[A]): FreeVariables = p match
    case Path.Deref(ref) => FreeVariables(Set(ref), Set.empty)
    case Path.Constant(_) => empty
    case Path.Concat(a, b) => path(a) ++ path(b)
    case Path.GroundedPP(p, _) => path(p)
    case Path.GroundedSP(s, _) => space(s)
  def space[A](s: Space[A]): FreeVariables = s match
    case Space.Empty() | Space.Literal(_) => empty
    case Space.Call(_, ps, ss) => (ps.map(path) ++ ss.map(space)).foldLeft(empty)(_ ++ _)
    case Space.Mention(ref) => FreeVariables(Set.empty, Set(ref))
    case Space.Singleton(p) => path(p)
    case Space.Union(a, b) => space(a) ++ space(b)
    case Space.Intersection(a, b) => space(a) ++ space(b)
    case Space.Subtraction(a, b) => space(a) ++ space(b)
    case Space.Restriction(a, b) => space(a) ++ space(b)
    case Space.Raffination(a, b) => space(a) ++ space(b)
    case Space.Composition(a, b) => space(a) ++ space(b)
    case Space.Iteration(src, h, r, body) => space(src) ++ space(body).bind(h)(r)
    case Space.Fold(src, initial, acc, h, r, body, update) =>
      space(src) ++ path(initial) ++ (space(body) ++ path(update)).bind(acc, h)(r)
    case Space.Fixpoint(initial, v, step) => space(initial) ++ space(step).bind()(v)
    case Space.Wrap(src, p) => space(src) ++ path(p)
    case Space.Unwrap(src, p) => space(src) ++ path(p)
    case Space.TailsUnion(src) => space(src)
    case Space.TailsIntersection(src) => space(src)
    case Space.PrefixClosure(src) => space(src)
    case Space.SuffixClosure(src) => space(src)
    case Space.TailsClosure(src) => space(src)
    case Space.GroundedPS(p, _) => path(p)
    case Space.GroundedSS(src, _) => space(src)
    case Space.Range(src, _, _) => space(src)

case class ScanBound(routine: RoutinePtr, maximumDepth: Option[Int], unrolled: Boolean)
case class Translation[S, T](expression: Space[T], routines: Map[RoutinePtr, Routine[T]],
                              code: Code[S, T], scanBounds: Vector[ScanBound]):
  def encodePaths(paths: Set[Vector[S]]): Set[Vector[T]] = paths.map(code.encode)
  def decodePaths(paths: Set[Vector[T]]): Set[Vector[S]] = paths.map(code.decode)

/** Structural compiler. The result contains only the constructors of Space
  * and Path. Scanners, folds, boundary closures and order adapters are calls
  * to ordinary target routines; there is no runtime symbol dictionary. */
final class Translator[S, T](code: Code[S, T], unrollLimit: Int = 64):
  import Space.*
  private val output = mutable.LinkedHashMap.empty[RoutinePtr, Routine[T]]
  private val scans = mutable.ArrayBuffer.empty[ScanBound]
  private val reserved = mutable.HashSet.empty[String]
  private var serial = 0
  private var used = false
  private val pathMemo = java.util.IdentityHashMap[Path[S], Path[T]]()
  private val spaceMemo = java.util.IdentityHashMap[Space[S], Space[T]]()
  private val seenNamesP = java.util.IdentityHashMap[Path[S], java.lang.Boolean]()
  private val seenNamesS = java.util.IdentityHashMap[Space[S], java.lang.Boolean]()
  private def fresh(label: String): String =
    var s = ""
    while s.isEmpty || reserved(s) do
      serial += 1
      s = s"__zippy_${label}_$serial"
    reserved += s
    s
  private def pr(label: String) = PathRef(fresh(label))
  private def sm(label: String) = SpaceMention(fresh(label))
  private def rp(label: String) = RoutinePtr(fresh(label))
  private def p(ref: PathRef): Path[T] = Path.Deref(ref)
  private def m(ref: SpaceMention): Space[T] = Mention(ref)
  private def eps: Space[T] = Singleton(Path.zero[T])
  private def call(name: RoutinePtr, ps: Vector[PathRef], ss: Vector[SpaceMention]): Space[T] =
    Call(name, ps.map(p), ss.map(m))
  private def remember(r: Routine[T]): Unit = output(r.name) = r

  // Reserve binder names as well as free names, including names below callbacks.
  private def namesPath(p: Path[S]): Unit =
    if seenNamesP.put(p, true) == null then namesPathUncached(p)
  private def namesPathUncached(p: Path[S]): Unit = p match
    case Path.Deref(r) => reserved += r.s
    case Path.Constant(_) => ()
    case Path.Concat(a, b) => namesPath(a); namesPath(b)
    case Path.GroundedPP(p, _) => namesPath(p)
    case Path.GroundedSP(s, _) => names(s)
  private def names(s: Space[S]): Unit =
    if seenNamesS.put(s, true) == null then namesUncached(s)
  private def namesUncached(s: Space[S]): Unit = s match
    case Empty() | Literal(_) => ()
    case Call(r, ps, ss) => reserved += r.s; ps.foreach(namesPath); ss.foreach(names)
    case Mention(v) => reserved += v.s
    case Singleton(p) => namesPath(p)
    case Union(a, b) => names(a); names(b)
    case Intersection(a, b) => names(a); names(b)
    case Subtraction(a, b) => names(a); names(b)
    case Restriction(a, b) => names(a); names(b)
    case Raffination(a, b) => names(a); names(b)
    case Composition(a, b) => names(a); names(b)
    case Iteration(src, h, r, body) => reserved ++= Vector(h.s, r.s); names(src); names(body)
    case Fold(src, initial, acc, h, r, body, update) =>
      reserved ++= Vector(acc.s, h.s, r.s); names(src); namesPath(initial); names(body); namesPath(update)
    case Fixpoint(initial, v, step) => reserved += v.s; names(initial); names(step)
    case Wrap(src, p) => names(src); namesPath(p)
    case Unwrap(src, p) => names(src); namesPath(p)
    case TailsUnion(src) => names(src)
    case TailsIntersection(src) => names(src)
    case PrefixClosure(src) => names(src)
    case SuffixClosure(src) => names(src)
    case TailsClosure(src) => names(src)
    case GroundedPS(p, _) => namesPath(p)
    case GroundedSS(src, _) => names(src)
    case Range(src, _, _) => names(src)

  private def lowerPath(x: Path[S]): Path[T] =
    val cached = pathMemo.get(x)
    if cached != null then cached
    else
      val result = lowerPathUncached(x)
      pathMemo.put(x, result)
      result
  private def lowerPathUncached(x: Path[S]): Path[T] = x match
    case Path.Deref(ref) =>
      val hint = for n <- Option.when(ref.lengthHint >= 0)(ref.lengthHint); width <- code.fixedUnits
                     if BigInt(n) * width <= Int.MaxValue
                 yield n * width
      Path.Deref(hint.fold(PathRef(ref.s))(PathRef(ref.s).known))
    case Path.Constant(items) => Path.Constant(code.encode(items))
    case Path.Concat(a, b) => Path.Concat(lowerPath(a), lowerPath(b))
    case Path.GroundedPP(path, f) => Path.GroundedPP(lowerPath(path), xs => code.encode(f(code.decode(xs))))
    case Path.GroundedSP(src, f) => Path.GroundedSP(lower(src), xs => code.encode(f(xs.map(code.decode))))

  /** Bind one logical source head by recognizing a complete target codeword.
    * All free references are passed explicitly, so helpers retain lexical scope. */
  private def iterate(src: Space[T], h: PathRef, rest: SpaceMention, body: Space[T],
                      scanner: Code[?, T] = code): Space[T] =
    if scanner.fixedUnits.contains(1) then Iteration(src, h.known(1), rest, body)
    else
      val free = FreeVariables.space(body).bind(h)(rest)
      val cps = free.paths.toVector.sortBy(_.s)
      val css = free.spaces.toVector.sortBy(_.s)
      val rawH = if h.s == "_" then pr("ignored_head") else PathRef(h.s)
      val boundH = scanner.fixedUnits.fold(rawH)(rawH.known)
      val boundR = if rest.s == "_" then sm("ignored_rest") else rest
      val cont = rp("symbol_body")
      remember(Routine(cont, cps :+ boundH, css :+ boundR, body))
      val prefix = pr("code_prefix")
      val source = sm("code_source")
      val unit = pr("unit").known(1)
      val tail = sm("unit_tail")
      val limit = scanner.maxUnits.filter(_ <= unrollLimit)
      val memo = mutable.Map.empty[(Int, Int), RoutinePtr]
      def stateRoutine(state: Int, depth: Int): RoutinePtr =
        val key = state -> (if limit.isDefined then depth else 0)
        memo.getOrElseUpdate(key, {
          val name = rp("scan")
          // Install before descending, to close recursive recognizers.
          memo(key) = name
          val atDepth = if limit.isDefined then prefix.known(depth) else prefix
          val accumulated = Path.Concat(p(atDepth), p(unit))
          val transitions = scanner.targetAlphabet.flatMap(u => scanner.transition(state, u).map(_ -> u)).groupMap(_._1)(_._2)
          val branches = transitions.toVector.sortBy(_._1.toString).map { (step, units) =>
            val next: Space[T] = step match
              case ScanStep.Accept => Call(cont, cps.map(p) :+ accumulated, css.map(m) :+ m(tail))
              case ScanStep.Continue(s) =>
                if limit.exists(depth + 1 >= _) then Empty()
                else Call(stateRoutine(s, depth + 1), cps.map(p) :+ accumulated, css.map(m) :+ m(tail))
            if units.size == scanner.targetAlphabet.size then next
            else
              val filter = Intersection(Singleton(p(unit)), Literal(units.map(u => Vector(u)).toSet))
              Iteration(filter, pr("guard"), sm("guard_tail"), next)
          }
          val dispatch = branches.reduceOption(Union(_, _)).getOrElse(Empty())
          remember(Routine(name, cps :+ atDepth, css :+ source, Iteration(m(source), unit, tail, dispatch)))
          name
        })
      val start = stateRoutine(0, 0)
      scans += ScanBound(start, scanner.maxUnits, limit.isDefined)
      Call(start, cps.map(p) :+ Path.zero[T], css.map(m) :+ src)

  private var orderAdapters: Option[(RoutinePtr, RoutinePtr)] = None
  private def adapters(): (RoutinePtr, RoutinePtr) = orderAdapters match
    case Some(pair) => pair
    case None =>
      val symbols = code.orderedSymbols.getOrElse(throw IllegalArgumentException("unordered code requires a finite source alphabet"))
      val digits = code.targetAlphabet
      require(digits.size >= 2, "order adapter needs at least two target units")
      var width = 1
      var capacity = BigInt(digits.size)
      while capacity < symbols.size do
        width += 1
        capacity *= digits.size
      def key(n: Int): Vector[T] =
        var value = n
        val result = Array.fill(width)(0)
        for i <- result.indices.reverse do
          result(i) = value % digits.size
          value /= digits.size
        result.toVector.map(digits)
      val pairs = symbols.zipWithIndex.map((s, i) => code.encodeSymbol(s) -> key(i))
      val forward: Space[T] = Literal(pairs.map((a, b) => a ++ b).toSet)
      val backward: Space[T] = Literal(pairs.map((a, b) => b ++ a).toSet)
      val to = rp("to_ordered")
      val from = rp("from_ordered")
      orderAdapters = Some(to -> from)
      val src = sm("order_source")
      val h = pr("order_head")
      val r = sm("order_tail")
      val orderedScanner = new Code[Int, T]:
        val targetAlphabet = digits
        val maxUnits = Some(width)
        override val fixedUnits = Some(width)
        val orderPreserving = true
        def encodeSymbol(n: Int) = key(n)
        def decodeSymbol(p: Vector[T]) = throw UnsupportedOperationException("compile-time scanner only")
        def transition(state: Int, unit: T) = Some(if state + 1 == width then ScanStep.Accept else ScanStep.Continue(state + 1))
      def trans(name: RoutinePtr, table: Space[T], scanner: Code[?, T]): Space[T] =
        Union(Intersection(m(src), eps), iterate(m(src), h, r,
          Composition(Unwrap(table, p(h)), Call(name, Vector.empty, Vector(m(r)))), scanner))
      remember(Routine(to, Vector.empty, Vector(src), trans(to, forward, code)))
      remember(Routine(from, Vector.empty, Vector(src), trans(from, backward, orderedScanner)))
      to -> from

  private def range(src: Space[T], start: Int, end: Int): Space[T] =
    if code.orderPreserving then Range(src, start, end)
    else
      val (to, from) = adapters()
      Call(from, Vector.empty, Vector(Range(Call(to, Vector.empty, Vector(src)), start, end)))

  private def closure(src: Space[S], kind: Int): Space[T] =
    val name = rp("boundary_closure")
    val source = sm("closure_source")
    val h = pr("closure_head")
    val r = sm("closure_tail")
    val recurse: Space[T] = Call(name, Vector.empty, Vector(m(r)))
    val body = if kind == 0 then Union(Singleton(p(h)), Wrap(recurse, p(h))) else recurse
    val base = if kind == 0 then Empty[T]() else if kind == 1 then Subtraction(m(source), eps) else m(source)
    remember(Routine(name, Vector.empty, Vector(source), Union(base, iterate(m(source), h, r, body))))
    Call(name, Vector.empty, Vector(lower(src)))

  private def fold(src: Space[S], initial: Path[S], acc: PathRef, h: PathRef,
                   rest: SpaceMention, body: Space[S], update: Path[S]): Space[T] =
    // Re-express an ordered fold with calls, logical iteration and Range.
    // The accumulator is still a path, passed to the next call as update.
    val free = (FreeVariables.space(body) ++ FreeVariables.path(update)).bind(acc, h)(rest)
    val cps = free.paths.toVector.sortBy(_.s)
    val css = free.spaces.toVector.sortBy(_.s)
    val loop = rp("fold")
    val step = rp("fold_step")
    val remaining = sm("fold_remaining")
    val ignored = sm("fold_ignored")
    val head = pr("fold_head")
    val tail = sm("fold_tail")
    val currentAcc = pr("fold_accumulator")
    val accFormal = if acc.s == "_" then pr("ignored_acc") else acc
    val headFormal = if h.s == "_" then pr("ignored_fold_head") else h
    val restFormal = if rest.s == "_" then sm("ignored_fold_rest") else rest
    def sp(r: PathRef): Path[S] = Path.Deref(r)
    def ss(r: SpaceMention): Space[S] = Mention(r)
    val heads: Space[S] = Iteration(ss(remaining), head, tail, Singleton(sp(head)))
    val stepCall: Space[S] = Call(step, cps.map(sp) ++ Vector(sp(currentAcc), sp(head)),
      css.map(ss) ++ Vector(ss(remaining), Unwrap(ss(remaining), sp(head))))
    val loopBody: Space[S] = Iteration(Range(heads, 0, 1), head, ignored, stepCall)
    val next: Space[S] = Call(loop, cps.map(sp) :+ update,
      css.map(ss) :+ Raffination(ss(remaining), Singleton(sp(headFormal))))
    val stepBody: Space[S] = Union(body, next)
    remember(Routine(loop, cps :+ currentAcc, css :+ remaining, lower(loopBody)))
    remember(Routine(step, cps ++ Vector(accFormal, headFormal), css ++ Vector(remaining, restFormal), lower(stepBody)))
    Call(loop, cps.map(r => lowerPath(sp(r))) :+ lowerPath(initial), css.map(m) :+ lower(src))

  private def lower(s: Space[S]): Space[T] =
    val cached = spaceMemo.get(s)
    if cached != null then cached
    else
      val result = lowerUncached(s)
      spaceMemo.put(s, result)
      result
  private def lowerUncached(s: Space[S]): Space[T] = s match
    case Empty() => Empty()
    case Call(r, ps, ss) => Call(r, ps.map(lowerPath), ss.map(lower))
    case Mention(ref) => Mention(ref)
    case Singleton(path) => Singleton(lowerPath(path))
    case Literal(paths) => Literal(paths.map(code.encode))
    case Union(a, b) => Union(lower(a), lower(b))
    case Intersection(a, b) => Intersection(lower(a), lower(b))
    case Subtraction(a, b) => Subtraction(lower(a), lower(b))
    case Restriction(a, b) => Restriction(lower(a), lower(b))
    case Raffination(a, b) => Raffination(lower(a), lower(b))
    case Composition(a, b) => Composition(lower(a), lower(b))
    case Iteration(src, h, rest, body) => iterate(lower(src), h, rest, lower(body))
    case Fold(src, initial, acc, h, rest, body, update) => fold(src, initial, acc, h, rest, body, update)
    case Fixpoint(initial, v, step) => Fixpoint(lower(initial), v, lower(step))
    case Wrap(src, path) => Wrap(lower(src), lowerPath(path))
    case Unwrap(src, path) => Unwrap(lower(src), lowerPath(path))
    case TailsUnion(src) =>
      val h = pr("tails_head"); val r = sm("tails_rest")
      iterate(lower(src), h, r, m(r))
    case TailsIntersection(src) =>
      val h = pr("intersect_head"); val r = sm("intersect_rest")
      val source = lower(src)
      val tails = iterate(source, h, r, m(r))
      Subtraction(tails, iterate(source, h, r, Subtraction(tails, m(r))))
    case PrefixClosure(src) => closure(src, 0)
    case SuffixClosure(src) => closure(src, 1)
    case TailsClosure(src) => closure(src, 2)
    case GroundedPS(path, f) => GroundedPS(lowerPath(path), xs => f(code.decode(xs)).map(code.encode))
    case GroundedSS(src, f) => GroundedSS(lower(src), xs => f(xs.map(code.decode)).map(code.encode))
    case Range(src, start, end) => range(lower(src), start, end)

  def apply(expression: Space[S], routines: Map[RoutinePtr, Routine[S]] = Map.empty): Translation[S, T] =
    require(!used, "a Translator instance compiles one program; create a fresh instance for the next")
    used = true
    names(expression)
    routines.values.foreach { r =>
      reserved += r.name.s
      reserved ++= r.refs.map(_.s)
      reserved ++= r.mentions.map(_.s)
      names(r.body)
    }
    val result = lower(expression)
    routines.values.toVector.sortBy(_.name.s).foreach { r =>
      remember(Routine(r.name, r.refs.map(x => lowerPath(Path.Deref(x)) match
        case Path.Deref(ref) => ref
        case _ => throw IllegalStateException("dereference changed constructor")), r.mentions, lower(r.body)))
    }
    Translation(result, output.toMap, code, scans.toVector)
