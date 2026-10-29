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

/** Trampolined reference interpreter: codeword and source recursion consume
  * heap continuations rather than one JVM frame per bit or byte. */
final class Evaluator[A, P, V](val storage: Storage[A, P, V]):
  import Space.*
  import scala.util.control.TailCalls.{TailRec, done, tailcall}
  private val st = storage

  def apply(expr: Space[A], pathInputs: Map[PathRef, P] = Map.empty,
            spaceInputs: Map[SpaceMention, V] = Map.empty,
            routines: Map[RoutinePtr, Routine[A]] = Map.empty): V =
    val constantPaths = java.util.IdentityHashMap[Path[A], P]()
    val literalSpaces = java.util.IdentityHashMap[Space[A], V]()
    def sequence[X, Y](xs: Vector[X])(f: X => TailRec[Y]): TailRec[Vector[Y]] =
      xs.foldLeft(done(Vector.empty[Y]))((acc, x) => acc.flatMap(out => f(x).map(out :+ _)))
    def path(p: Path[A], pc: Map[PathRef, P], sc: Map[SpaceMention, V]): TailRec[P] = tailcall(p match
      case Path.Deref(ref) => done(pc(ref))
      case Path.Constant(items) =>
        val cached = constantPaths.get(p)
        if cached != null then done(cached)
        else
          val value = st.path(items)
          constantPaths.put(p, value)
          done(value)
      case Path.Concat(a, b) => for x <- path(a, pc, sc); y <- path(b, pc, sc) yield st.concat(x, y)
      case Path.GroundedPP(p, f) => path(p, pc, sc).map(v => st.path(f(st.items(v))))
      case Path.GroundedSP(s, f) => rec(s, pc, sc).map(v => st.path(f(st.paths(v).map(st.items))))
    )
    def bindP(pc: Map[PathRef, P], ref: PathRef, p: P) = if ref.s == "_" then pc else pc.updated(ref, p)
    def bindS(sc: Map[SpaceMention, V], ref: SpaceMention, s: V) = if ref.s == "_" then sc else sc.updated(ref, s)
    def groups(s: V): Vector[(P, V)] =
      st.paths(s).iterator.filter(st.length(_) > 0).toVector
        .groupMap(st.take(_, 1))(st.drop(_, 1)).iterator
        .map((h, tails) => h -> st.value(tails.toSet)).toVector
        .sortWith((a, b) => st.compare(a._1, b._1) < 0)
    def rec(s: Space[A], pc: Map[PathRef, P], sc: Map[SpaceMention, V]): TailRec[V] = tailcall(s match
      case Empty() => done(st.empty)
      case Call(rp, refs, mentions) =>
        val r = routines.getOrElse(rp, throw IllegalArgumentException(s"undefined routine ${rp.s}"))
        require(refs.size == r.refs.size && mentions.size == r.mentions.size, s"arity mismatch calling ${rp.s}")
        for
          ps <- sequence(refs)(path(_, pc, sc))
          ss <- sequence(mentions)(rec(_, pc, sc))
          out <-
            val nextP = r.refs.zip(ps).toMap
            val nextS = r.mentions.zip(ss).toMap
            r.body match
              case Union(left, Call(`rp`, tailP, tailS)) if tailP == refs && tailS == mentions =>
                for
                  againP <- sequence(tailP)(path(_, nextP, nextS))
                  againS <- if againP == ps then sequence(tailS)(rec(_, nextP, nextS)) else done(Vector.empty[V])
                  value <- rec(if againP == ps && againS == ss then left else r.body, nextP, nextS)
                yield value
              case _ => rec(r.body, nextP, nextS)
        yield out
      case Mention(ref) => done(sc(ref))
      case Singleton(p) => path(p, pc, sc).map(st.singleton)
      case Literal(ps) =>
        val cached = literalSpaces.get(s)
        if cached != null then done(cached)
        else
          val value = st.value(ps.map(st.path))
          literalSpaces.put(s, value)
          done(value)
      case Union(a, b) => for x <- rec(a, pc, sc); y <- rec(b, pc, sc) yield st.union(x, y)
      case Intersection(a, b) => for x <- rec(a, pc, sc); y <- rec(b, pc, sc) yield st.value(st.paths(x) intersect st.paths(y))
      case Subtraction(a, b) => for x <- rec(a, pc, sc); y <- rec(b, pc, sc) yield st.value(st.paths(x) diff st.paths(y))
      case Restriction(a, b) =>
        for
          prefixes <- rec(b, pc, sc)
          source <- rec(a, pc, sc)
        yield st.value(st.paths(source).filter(p => st.paths(prefixes).exists(st.startsWith(p, _))))
      case Raffination(a, b) =>
        for
          prefixes <- rec(b, pc, sc)
          source <- rec(a, pc, sc)
        yield st.value(st.paths(source).filterNot(p => st.paths(prefixes).exists(st.startsWith(p, _))))
      case Composition(a, b) => rec(a, pc, sc).flatMap { left =>
        if st.paths(left).isEmpty then done(st.empty)
        else rec(b, pc, sc).map(right => st.value(for p <- st.paths(left); q <- st.paths(right) yield st.concat(p, q)))
      }
      case Wrap(src, p) => for prefix <- path(p, pc, sc); value <- rec(src, pc, sc)
        yield st.value(st.paths(value).map(st.concat(prefix, _)))
      case Unwrap(src, p) => for prefix <- path(p, pc, sc); value <- rec(src, pc, sc)
        yield st.value(st.paths(value).filter(st.startsWith(_, prefix)).map(st.drop(_, st.length(prefix))))
      case TailsUnion(src) => rec(src, pc, sc).map(v => st.value(groups(v).flatMap(g => st.paths(g._2)).toSet))
      case TailsIntersection(src) => rec(src, pc, sc).map { v =>
        val gs = groups(v)
        if gs.isEmpty then st.empty else st.value(gs.map(g => st.paths(g._2)).reduce(_ intersect _))
      }
      case PrefixClosure(src) => rec(src, pc, sc).map(v =>
        st.value(st.paths(v).flatMap(p => (1 to st.length(p)).map(st.take(p, _)))))
      case SuffixClosure(src) => rec(src, pc, sc).map(v =>
        st.value(st.paths(v).flatMap(p => (0 until st.length(p)).map(st.drop(p, _)))))
      case TailsClosure(src) => rec(src, pc, sc).map(v =>
        st.value(st.paths(v).flatMap(p => (0 to st.length(p)).map(st.drop(p, _)))))
      case Iteration(src, h, rest, body) => rec(src, pc, sc).flatMap { value =>
        groups(value).foldLeft(done(st.empty)) { case (out, (head, tails)) =>
          out.flatMap(acc => rec(body, bindP(pc, h, head), bindS(sc, rest, tails)).map(st.union(acc, _)))
        }
      }
      case Fold(src, initial, acc, h, rest, body, update) =>
        for
          initialPath <- path(initial, pc, sc)
          value <- rec(src, pc, sc)
          result <- groups(value).foldLeft(done(initialPath -> st.empty)) { case (state, (head, tails)) =>
            state.flatMap { (current, out) =>
              val pctx = bindP(bindP(pc, acc, current), h, head)
              val sctx = bindS(sc, rest, tails)
              for emitted <- rec(body, pctx, sctx); next <- path(update, pctx, sctx)
                yield next -> st.union(out, emitted)
            }
          }
        yield result._2
      case Fixpoint(initial, variable, step) =>
        def rounds(current: V): TailRec[V] = rec(step, pc, bindS(sc, variable, current)).flatMap { stepped =>
          val next = st.union(current, stepped)
          if st.paths(next) == st.paths(current) then done(current) else tailcall(rounds(next))
        }
        rec(initial, pc, sc).flatMap(rounds)
      case GroundedPS(p, f) => path(p, pc, sc).map(v => st.value(f(st.items(v)).map(st.path)))
      case GroundedSS(src, f) => rec(src, pc, sc).map(v => st.value(f(st.paths(v).map(st.items)).map(st.path)))
      case Range(src, start, end) => rec(src, pc, sc).map { value =>
        val ps = st.paths(value)
        val (lo, hi) = RangeBounds.normalize(ps.size, start, end)
        st.value(ps.toVector.sortWith(st.compare(_, _) < 0).slice(lo, hi).toSet)
      }
    )
    rec(expr, pathInputs, spaceInputs).result

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
