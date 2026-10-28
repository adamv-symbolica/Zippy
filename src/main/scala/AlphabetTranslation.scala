package morkl

import scala.collection.mutable

/** Syntax-to-syntax, boundary-aware lowering. Generated functions are closure
  * converted, so no evaluator extension, host callback, or side table is needed. */
object AlphabetTranslation:
  def apply[S, T](program: AlphabetProgram[S], encoding: AlphabetEncoding[S, T]): AlphabetProgram[T] =
    new Compiler(program, encoding).compile()
  def symbols[T](expression: Space, encoding: AlphabetEncoding[PathValue, T], routines: Vector[Routine] = Vector.empty): AlphabetProgram[T] =
    apply(AlphabetProgram(SymbolAlphabet.fromSpace(ReferenceHints.tag(expression)), routines.map(r => r.name -> SymbolAlphabet.fromRoutine(ReferenceHints.tag(r))).toMap, SymbolPaths), encoding)

  private class Compiler[S, T](input: AlphabetProgram[S], code: AlphabetEncoding[S, T]):
    import AlphabetSpace.*
    type P = AlphabetPath[T]
    type E = AlphabetSpace[T]
    val d = code.target
    val output = mutable.LinkedHashMap.empty[RoutinePtr, AlphabetRoutine[T]]
    val used = mutable.HashSet.empty[String]
    var serial = 0
    def reserve(s: AlphabetSpace[S]): Unit =
      AlphabetSyntax.map[S, S](s)(p => {
        p match
          case AlphabetPath.Deref(r) => used += r.s
          case _ => ()
        None
      }, s => {
        s match
          case Mention(r) => used += r.s
          case Call(r, _, _) => used += r.s
          case Iteration(_, h, r, _) => used ++= Seq(h.s, r.s)
          case Fold(_, _, a, h, r, _, _) => used ++= Seq(a.s, h.s, r.s)
          case Fixpoint(_, v, _) => used += v.s
          case _ => ()
        None
      }, identity, identity)
    reserve(input.expression)
    input.routines.values.foreach { r => used += r.name.s; used ++= r.refs.map(_.s); used ++= r.mentions.map(_.s); reserve(r.body) }
    def fresh(label: String): String =
      serial += 1
      val name = s"#zippy_${label}_$serial"
      if used.add(name) then name else fresh(label)
    def pr(label: String): PathRef = PathRef(fresh(label))
    def sm(label: String): SpaceMention = SpaceMention(fresh(label))
    def pv(r: PathRef): P = AlphabetPath.Deref(r)
    def sv(r: SpaceMention): E = Mention(r)
    def constant(p: T): P = AlphabetPath.Constant(p)
    val epsilon: E = Singleton(constant(d.empty))
    def concat(a: P, b: P): P = AlphabetPath.Concat(a, b)
    def truth(s: E): E =
      Union(Intersection(s, epsilon), Iteration(s, pr("test"), sm("test"), epsilon))
    def guard(test: E, body: E): E = Composition(truth(test), body)
    def choose(test: E, yes: E, no: E): E = Union(guard(test, yes), Composition(Subtraction(epsilon, truth(test)), no))
    def isDigit(p: P, digit: Int): E = Intersection(Singleton(p), Singleton(constant(d.single(digit))))

    /** Function abstraction. Closure conversion runs over the completed graph. */
    def recursive(label: String, ps: Vector[PathRef], ss: Vector[SpaceMention])(build: ((Vector[P], Vector[E]) => E) => E): (Vector[P], Vector[E]) => E =
      val name = RoutinePtr(fresh(label))
      finishFunction(name, ps, ss, build((p, s) => Call(name, p, s)))
    def finishFunction(name: RoutinePtr, ps: Vector[PathRef], ss: Vector[SpaceMention], body: E): (Vector[P], Vector[E]) => E =
      output(name) = AlphabetRoutine(name, ps, ss, body)
      (p, s) => Call(name, p, s)

    /** Close the complete mutually recursive function graph to a fixed point.
      * Doing this after construction also captures variables used only by a
      * nested helper's call back into its enclosing recursive function. */
    def close(expression: E): AlphabetProgram[T] =
      var captures = output.keysIterator.map(_ -> (Set.empty[PathRef], Set.empty[SpaceMention])).toMap
      def rewrite(s: E): E =
        def p(x: P): P = rewrite(Singleton(x)) match
          case Singleton(v) => v
          case _ => throw IllegalStateException("path sort")
        AlphabetSyntax.map[T, T](s)(_ => None, {
          case Call(r, ps, ss) =>
            val c = captures.getOrElse(r, (Set.empty[PathRef], Set.empty[SpaceMention]))
            Some(Call(r, ps.map(p) ++ c._1.toVector.sortBy(_.s).map(pv), ss.map(rewrite) ++ c._2.toVector.sortBy(_.s).map(sv)))
          case _ => None
        }, identity, identity)
      var changed = true
      while changed do
        val next = output.iterator.map { (name, routine) =>
          val free = AlphabetSyntax.free(rewrite(routine.body))
          name -> (free._1 -- routine.refs, free._2 -- routine.mentions)
        }.toMap
        changed = next != captures
        captures = next
      val closed = output.iterator.map { (name, routine) =>
        val c = captures(name)
        name -> routine.copy(refs = routine.refs ++ c._1.toVector.sortBy(_.s), mentions = routine.mentions ++ c._2.toVector.sortBy(_.s), body = rewrite(routine.body))
      }.toMap
      AlphabetProgram(rewrite(expression), closed, d)

    /** Decode one source edge using only primitive target Iteration and calls.
      * A finite maxWidth produces acyclic, depth-specialized scanner functions.
      * Unbounded strings produce an ordinary recursive scanner. */
    def iterate(src: E, originalSymbol: PathRef, originalRest: SpaceMention, body: E): E =
      val symbol = if originalSymbol.s == "_" then pr("ignoredSymbol") else originalSymbol
      val rest = if originalRest.s == "_" then sm("ignoredRest") else originalRest
      val cont = finishFunction(RoutinePtr(fresh("body")), Vector(symbol), Vector(rest), body)
      val captures = AlphabetSyntax.free(body)
      val cp = (captures._1 - symbol).toVector.sortBy(_.s)
      val cs = (captures._2 - rest).toVector.sortBy(_.s)
      val acc = code.maxWidth.fold(pr("prefix"))(n => pr("prefix").bounded(n))
      val space = sm("subtree")
      val head = pr("digit").known(1)
      val tail = sm("tail")
      val names = mutable.Map.empty[(Int, Int), RoutinePtr]
      def scanner(state: Int, depth: Int): RoutinePtr =
        val key = state -> (if code.maxWidth.isDefined then depth else 0)
        names.getOrElseUpdate(key, {
          val name = RoutinePtr(fresh("scan"))
          // Install before descending: the unbounded zero-terminated scanner is recursive.
          names(key) = name
          val prefixRef = if code.maxWidth.isDefined then acc.known(depth) else acc
          val nextPrefix = concat(pv(prefixRef), pv(head))
          def callNext(nextState: Int): E =
            if code.maxWidth.exists(depth + 1 >= _) then Empty()
            else Call(scanner(nextState, depth + 1), Vector(nextPrefix) ++ cp.map(pv), Vector(sv(tail)) ++ cs.map(sv))
          val done = cont(Vector(nextPrefix), Vector(sv(tail)))
          val step: E = code.boundary match
            case CodeBoundary.Fixed(width) => if depth + 1 == width then done else callNext(0)
            case CodeBoundary.ZeroTerminated => choose(isDigit(pv(head), 0), done, callNext(0))
            case CodeBoundary.Fibonacci =>
              if state == 1 then choose(isDigit(pv(head), 1), done, callNext(0))
              else choose(isDigit(pv(head), 1), callNext(1), callNext(0))
          output(name) = AlphabetRoutine(name, Vector(prefixRef) ++ cp, Vector(space) ++ cs, Iteration(sv(space), head, tail, step))
          name
        })
      Call(scanner(0, 0), Vector(constant(d.empty)) ++ cp.map(pv), Vector(src) ++ cs.map(sv))

    def head(src: E): E =
      val h = pr("head"); val t = sm("rest")
      iterate(src, h, t, Singleton(pv(h)))

    /** Recode whole paths into fixed-width source-order ranks for Range. */
    def orderedRange(src: E, start: Int, end: Int): E =
      if code.preservesOrder then Range(src, start, end)
      else
        val atoms = code.orderedAtoms
        val width =
          var n = BigInt(atoms.size.max(1) - 1); var count = 1
          while n >= d.arity do { n /= d.arity; count += 1 }
          count
        def rank(i: Int): T = d.fromDigits((0 until width).reverse.map(k => ((BigInt(i) / BigInt(d.arity).pow(k)) % d.arity).toInt))
        val pairs = atoms.zipWithIndex.map((a, i) => code.encode(a) -> rank(i))
        def recode(value: E, forward: Boolean): E =
          val s = sm("recode")
          val helper = recursive("recode", Vector.empty, Vector(s)) { self =>
            val groups = pairs.foldLeft(Empty(): E) { case (out, (original, key)) =>
              val from = if forward then original else key
              val to = if forward then key else original
              val tails = Unwrap(sv(s), constant(from))
              Union(out, guard(tails, Wrap(self(Vector.empty, Vector(tails)), constant(to))))
            }
            Union(Intersection(sv(s), epsilon), groups)
          }
          helper(Vector.empty, Vector(value))
        recode(Range(recode(src, true), start, end), false)

    def firstHead(src: E): E = orderedRange(head(src), 0, 1)

    def fold(src: E, init: P, originalAcc: PathRef, originalHead: PathRef, originalRest: SpaceMention, body: E, update: P): E =
      val acc = if originalAcc.s == "_" then pr("ignoredAccumulator") else originalAcc
      val h = if originalHead.s == "_" then pr("ignoredHead") else originalHead
      val rest = if originalRest.s == "_" then sm("ignoredRest") else originalRest
      val s = sm("foldSource")
      val loop = recursive("fold", Vector(acc), Vector(s)) { self =>
        val continuation = finishFunction(RoutinePtr(fresh("foldBody")), Vector(h), Vector(rest),
          Union(body, self(Vector(update), Vector(Subtraction(sv(s), Wrap(sv(rest), pv(h)))))))
        val selected = pr("selected"); val ignored = sm("ignored")
        iterate(firstHead(sv(s)), selected, ignored, continuation(Vector(pv(selected)), Vector(Unwrap(sv(s), pv(selected)))))
      }
      loop(Vector(init), Vector(src))

    def tailsIntersection(src: E): E =
      val s = sm("intersectionSource"); val h = pr("intersectionHead"); val t = sm("intersectionTail")
      val loop = recursive("tailsIntersection", Vector.empty, Vector(s)) { self =>
        val r = sm("originalGroup")
        val remaining = Subtraction(sv(s), Wrap(sv(r), pv(h)))
        val more = Subtraction(remaining, epsilon)
        val group = finishFunction(RoutinePtr(fresh("intersectionGroup")), Vector.empty, Vector(r),
          choose(more, Intersection(sv(r), self(Vector.empty, Vector(remaining))), sv(r)))
        iterate(firstHead(sv(s)), h, t, group(Vector.empty, Vector(Unwrap(sv(s), pv(h)))))
      }
      loop(Vector.empty, Vector(src))

    def closure(src: E, kind: Int): E =
      val s = sm("closureSource"); val h = pr("closureHead"); val t = sm("closureTail")
      val loop = recursive("closure", Vector.empty, Vector(s)) { self =>
        val deeper = iterate(sv(s), h, t,
          if kind == 0 then Wrap(Union(epsilon, self(Vector.empty, Vector(sv(t)))), pv(h))
          else self(Vector.empty, Vector(sv(t))))
        if kind == 0 then deeper
        else Union(if kind == 1 then Subtraction(sv(s), epsilon) else sv(s), deeper)
      }
      loop(Vector.empty, Vector(src))

    def translatedRef(r: PathRef): PathRef =
      val out = PathRef(r.s)
      if r.lengthHint == 0 then out.known(0)
      else code.boundary match
        case CodeBoundary.Fixed(width) if r.lengthHint >= 0 && BigInt(r.lengthHint) * width <= Int.MaxValue => out.known(r.lengthHint * width)
        case _ => code.encodedUpperLength(r.maximumLengthHint).filter(_ => r.maximumLengthHint >= 0).fold(out)(out.bounded)

    def path(p: AlphabetPath[S]): P =
      // Reuse the exhaustive mapper for spaces nested inside grounded paths.
      translate(Singleton(p)) match
        case Singleton(p) => p
        case _ => throw IllegalStateException("path translation changed its sort")
    def translate(s: AlphabetSpace[S]): E =
      AlphabetSyntax.map[S, T](s)({
        case AlphabetPath.Deref(r) => Some(AlphabetPath.Deref(translatedRef(r)))
        case AlphabetPath.GroundedSP(s, f) => Some(AlphabetPath.GroundedSP(translate(s), qs => code.encode(f(qs.map(code.decode)))))
        case _ => None
      }, {
        case Iteration(src, h, r, b) => Some(iterate(translate(src), translatedRef(h.known(1)), r, translate(b)))
        case Fold(src, init, a, h, r, b, u) => Some(fold(translate(src), path(init), translatedRef(a), translatedRef(h.known(1)), r, translate(b), path(u)))
        case TailsUnion(src) =>
          val h = pr("tailsHead"); val t = sm("tailsRest")
          Some(iterate(translate(src), h, t, sv(t)))
        case TailsIntersection(src) => Some(tailsIntersection(translate(src)))
        case PrefixClosure(src) => Some(closure(translate(src), 0))
        case SuffixClosure(src) => Some(closure(translate(src), 1))
        case TailsClosure(src) => Some(closure(translate(src), 2))
        case Range(src, start, end) => Some(orderedRange(translate(src), start, end))
        case _ => None
      }, code.encode, code.decode)

    def compile(): AlphabetProgram[T] =
      val expression = translate(input.expression)
      input.routines.values.foreach { r =>
        output(r.name) = AlphabetRoutine(r.name, r.refs.map(translatedRef), r.mentions, translate(r.body))
      }
      close(expression)
