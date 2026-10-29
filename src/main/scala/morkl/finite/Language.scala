package morkl.finite

import morkl.{PathRef, SpaceMention, RoutinePtr, RangeBounds}

/** The same path-set language over any alphabet. No encoded-symbol operations
  * occur in this syntax: translation helpers are ordinary routines. */
enum Path[A]:
  case Deref(ref: PathRef)
  case Constant(items: Vector[A])
  case Concat(left: Path[A], right: Path[A])
  case GroundedPP(path: Path[A], f: Vector[A] => Vector[A])
  case GroundedSP(space: Space[A], f: Set[Vector[A]] => Vector[A])

object Path:
  def zero[A]: Path[A] = Constant(Vector.empty)

enum Space[A]:
  case Empty()
  case Call(routine: RoutinePtr, refs: Vector[Path[A]], mentions: Vector[Space[A]])
  case Mention(variable: SpaceMention)
  case Singleton(path: Path[A])
  case Literal(paths: Set[Vector[A]])
  case Union(x: Space[A], y: Space[A])
  case Intersection(x: Space[A], y: Space[A])
  case Subtraction(x: Space[A], y: Space[A])
  case Restriction(x: Space[A], y: Space[A])
  case Raffination(x: Space[A], y: Space[A])
  case Composition(x: Space[A], y: Space[A])
  case Iteration(src: Space[A], symbol: PathRef, rest: SpaceMention, templates: Space[A])
  case Fold(src: Space[A], initial: Path[A], acc: PathRef, symbol: PathRef,
            rest: SpaceMention, templates: Space[A], update: Path[A])
  case Fixpoint(initial: Space[A], variable: SpaceMention, step: Space[A])
  case Wrap(src: Space[A], path: Path[A])
  case Unwrap(src: Space[A], path: Path[A])
  case TailsUnion(src: Space[A])
  case TailsIntersection(src: Space[A])
  case PrefixClosure(src: Space[A])
  case SuffixClosure(src: Space[A])
  case TailsClosure(src: Space[A])
  case GroundedPS(path: Path[A], f: Vector[A] => Set[Vector[A]])
  case GroundedSS(src: Space[A], f: Set[Vector[A]] => Set[Vector[A]])
  case Range(src: Space[A], start: Int, end: Int)

object Space:
  /** Derived, just as Head in the symbol language. */
  def Head[A](src: Space[A]): Space[A] =
    val h = PathRef("$head")
    Iteration(src, h, SpaceMention("$head_rest"), Singleton(Path.Deref(h)))

case class Routine[A](name: RoutinePtr, refs: Vector[PathRef], mentions: Vector[SpaceMention], body: Space[A])

/** Storage operations used by the evaluator. Paths and spaces stay in native
  * storage, including during recursion, iteration and set operations. */
trait Storage[A, P, V]:
  def emptyPath: P
  def path(items: Vector[A]): P
  def items(path: P): Vector[A]
  def concat(left: P, right: P): P
  def length(path: P): Int
  def take(path: P, count: Int): P
  def drop(path: P, count: Int): P
  def startsWith(path: P, prefix: P): Boolean
  def compare(left: P, right: P): Int
  def empty: V
  def paths(value: V): Set[P]
  def value(paths: Set[P]): V
  final def singleton(path: P): V = value(Set(path))
  final def union(a: V, b: V): V = value(paths(a) union paths(b))

final class Evaluator[A, P, V](val storage: Storage[A, P, V]):
  import Space.*
  private val st = storage

  def apply(expr: Space[A], pathInputs: Map[PathRef, P] = Map.empty,
            spaceInputs: Map[SpaceMention, V] = Map.empty,
            routines: Map[RoutinePtr, Routine[A]] = Map.empty): V =
    def evalPath(p: Path[A], pc: Map[PathRef, P], sc: Map[SpaceMention, V]): P = p match
      case Path.Deref(ref) => pc(ref)
      case Path.Constant(items) => st.path(items)
      case Path.Concat(a, b) => st.concat(evalPath(a, pc, sc), evalPath(b, pc, sc))
      case Path.GroundedPP(p, f) => st.path(f(st.items(evalPath(p, pc, sc))))
      case Path.GroundedSP(s, f) => st.path(f(st.paths(rec(s, pc, sc)).map(st.items)))
    def bindP(pc: Map[PathRef, P], ref: PathRef, p: P) = if ref.s == "_" then pc else pc.updated(ref, p)
    def bindS(sc: Map[SpaceMention, V], ref: SpaceMention, s: V) = if ref.s == "_" then sc else sc.updated(ref, s)
    def groups(s: V): Vector[(P, V)] =
      st.paths(s).iterator.filter(st.length(_) > 0).toVector
        .groupMap(st.take(_, 1))(st.drop(_, 1)).iterator
        .map((h, tails) => h -> st.value(tails.toSet)).toVector
        .sortWith((a, b) => st.compare(a._1, b._1) < 0)
    def rec(s: Space[A], pc: Map[PathRef, P], sc: Map[SpaceMention, V]): V = s match
      case Empty() => st.empty
      case Call(rp, refs, mentions) =>
        val r = routines.getOrElse(rp, throw IllegalArgumentException(s"undefined routine ${rp.s}"))
        require(refs.size == r.refs.size && mentions.size == r.mentions.size, s"arity mismatch calling ${rp.s}")
        val ps = refs.map(evalPath(_, pc, sc))
        val ss = mentions.map(rec(_, pc, sc))
        val nextP = r.refs.zip(ps).toMap
        val nextS = r.mentions.zip(ss).toMap
        // Match the symbol evaluator's stable union-tail-call convention.
        r.body match
          case Union(left, Call(`rp`, tailP, tailS)) if tailP == refs && tailS == mentions &&
              tailP.map(evalPath(_, nextP, nextS)) == ps && tailS.map(rec(_, nextP, nextS)) == ss =>
            rec(left, nextP, nextS)
          case _ => rec(r.body, nextP, nextS)
      case Mention(ref) => sc(ref)
      case Singleton(p) => st.singleton(evalPath(p, pc, sc))
      case Literal(ps) => st.value(ps.map(st.path))
      case Union(a, b) => st.union(rec(a, pc, sc), rec(b, pc, sc))
      case Intersection(a, b) => st.value(st.paths(rec(a, pc, sc)) intersect st.paths(rec(b, pc, sc)))
      case Subtraction(a, b) => st.value(st.paths(rec(a, pc, sc)) diff st.paths(rec(b, pc, sc)))
      case Restriction(a, b) =>
        val prefixes = st.paths(rec(b, pc, sc))
        st.value(st.paths(rec(a, pc, sc)).filter(p => prefixes.exists(st.startsWith(p, _))))
      case Raffination(a, b) =>
        val prefixes = st.paths(rec(b, pc, sc))
        st.value(st.paths(rec(a, pc, sc)).filterNot(p => prefixes.exists(st.startsWith(p, _))))
      case Composition(a, b) =>
        val left = st.paths(rec(a, pc, sc))
        if left.isEmpty then st.empty
        else
          val right = st.paths(rec(b, pc, sc))
          st.value(for p <- left; q <- right yield st.concat(p, q))
      case Wrap(src, p) =>
        val prefix = evalPath(p, pc, sc)
        st.value(st.paths(rec(src, pc, sc)).map(st.concat(prefix, _)))
      case Unwrap(src, p) =>
        val prefix = evalPath(p, pc, sc)
        st.value(st.paths(rec(src, pc, sc)).filter(st.startsWith(_, prefix)).map(st.drop(_, st.length(prefix))))
      case TailsUnion(src) => st.value(groups(rec(src, pc, sc)).flatMap(g => st.paths(g._2)).toSet)
      case TailsIntersection(src) =>
        val gs = groups(rec(src, pc, sc))
        if gs.isEmpty then st.empty else st.value(gs.map(g => st.paths(g._2)).reduce(_ intersect _))
      case PrefixClosure(src) =>
        st.value(st.paths(rec(src, pc, sc)).flatMap(p => (1 to st.length(p)).map(st.take(p, _))))
      case SuffixClosure(src) =>
        st.value(st.paths(rec(src, pc, sc)).flatMap(p => (0 until st.length(p)).map(st.drop(p, _))))
      case TailsClosure(src) =>
        st.value(st.paths(rec(src, pc, sc)).flatMap(p => (0 to st.length(p)).map(st.drop(p, _))))
      case Iteration(src, h, rest, body) =>
        groups(rec(src, pc, sc)).foldLeft(st.empty) { case (out, (head, tails)) =>
          st.union(out, rec(body, bindP(pc, h, head), bindS(sc, rest, tails)))
        }
      case Fold(src, initial, acc, h, rest, body, update) =>
        var current = evalPath(initial, pc, sc)
        var out = st.empty
        for (head, tails) <- groups(rec(src, pc, sc)) do
          val pctx = bindP(bindP(pc, acc, current), h, head)
          val sctx = bindS(sc, rest, tails)
          out = st.union(out, rec(body, pctx, sctx))
          current = evalPath(update, pctx, sctx)
        out
      case Fixpoint(initial, variable, step) =>
        var current = rec(initial, pc, sc)
        var changed = true
        while changed do
          val next = st.union(current, rec(step, pc, bindS(sc, variable, current)))
          changed = st.paths(next) != st.paths(current)
          current = next
        current
      case GroundedPS(p, f) => st.value(f(st.items(evalPath(p, pc, sc))).map(st.path))
      case GroundedSS(src, f) => st.value(f(st.paths(rec(src, pc, sc)).map(st.items)).map(st.path))
      case Range(src, start, end) =>
        val ps = st.paths(rec(src, pc, sc))
        val (lo, hi) = RangeBounds.normalize(ps.size, start, end)
        st.value(ps.toVector.sortWith(st.compare(_, _) < 0).slice(lo, hi).toSet)
    rec(expr, pathInputs, spaceInputs)

object VectorStorage:
  def apply[A](ordering: Ordering[A]): Storage[A, Vector[A], Set[Vector[A]]] = new Storage[A, Vector[A], Set[Vector[A]]]:
    def emptyPath = Vector.empty[A]
    def path(items: Vector[A]) = items
    def items(path: Vector[A]) = path
    def concat(a: Vector[A], b: Vector[A]) = a ++ b
    def length(p: Vector[A]) = p.length
    def take(p: Vector[A], n: Int) = p.take(n)
    def drop(p: Vector[A], n: Int) = p.drop(n)
    def startsWith(p: Vector[A], prefix: Vector[A]) = p.startsWith(prefix)
    def compare(a: Vector[A], b: Vector[A]): Int =
      a.iterator.zip(b.iterator).map((x, y) => ordering.compare(x, y)).find(_ != 0).getOrElse(a.size.compare(b.size))
    def empty = Set.empty[Vector[A]]
    def paths(v: Set[Vector[A]]) = v
    def value(ps: Set[Vector[A]]) = ps
