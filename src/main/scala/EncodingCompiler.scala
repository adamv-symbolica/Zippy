package morkl

import scala.collection.mutable

/** Homomorphic lowering plus boundary-aware routines for non-homomorphic ops.
  * Generated code uses only the original Path/Space cases and explicit calls.
  * The compiler and its codebook are discarded after producing the Program.
  */
final class EncodingCompiler[S, T, SL <: DigitLanguage[S], TL <: DigitLanguage[T]](
  val source: SL, val target: TL, val encoding: PathEncoding[S, T]
):
  import target.{Path as P, Space as E, Routine as R}
  private val definitions = mutable.LinkedHashMap.empty[RoutinePtr, target.Routine]
  private val used = mutable.HashSet.empty[String]
  private var serial = 0
  private var compiled = false
  private def fresh(label: String): String =
    var n = ""
    while n.isEmpty || used(n) do
      n = s"__zippy_${label}_$serial"
      serial += 1
    used += n
    n
  private def pr(label: String): PathRef = PathRef(fresh(label))
  private def sm(label: String): SpaceMention = SpaceMention(fresh(label))
  private def rp(label: String): RoutinePtr = RoutinePtr(fresh(label))
  private def pd(r: PathRef): target.Path = P.Deref(r)
  private def sd(r: SpaceMention): target.Space = E.Mention(r)
  private val unitEncoding = encoding.framing == CodeFraming.Fixed(1)
  private val eps = target.epsilon
  private def one(p: target.Path): target.Space = E.Singleton(p)
  private def nonempty(x: target.Space): target.Space =
    E.Union(E.Intersection(x, eps), E.Iteration(x, pr("test_digit"), sm("test_tail"), eps))
  private def choose(test: target.Space, yes: target.Space, no: target.Space): target.Space =
    val exists = nonempty(test)
    E.Union(E.Composition(exists, yes), E.Composition(E.Subtraction(eps, exists), no))

  private case class Captures(ps: Vector[PathRef], ss: Vector[SpaceMention]):
    def pathArgs: Vector[target.Path] = ps.map(pd)
    def spaceArgs: Vector[target.Space] = ss.map(sd)
  private def free(x: target.Space): Captures =
    val ps = mutable.LinkedHashMap.empty[String, PathRef]
    val ss = mutable.LinkedHashMap.empty[String, SpaceMention]
    def p(v: target.Path, bp: Set[String], bs: Set[String]): Unit = v match
      case P.Deref(r) => if !bp(r.s) then ps.getOrElseUpdate(r.s, r)
      case P.Constant(_) => ()
      case P.Concat(a, b) => p(a, bp, bs); p(b, bp, bs)
      case P.GroundedPP(a, _) => p(a, bp, bs)
      case P.GroundedSP(a, _) => s(a, bp, bs)
    def s(v: target.Space, bp: Set[String], bs: Set[String]): Unit = v match
      case E.Empty | E.Literal(_) => ()
      case E.Mention(r) => if !bs(r.s) then ss.getOrElseUpdate(r.s, r)
      case E.Call(_, a, b) => a.foreach(p(_, bp, bs)); b.foreach(s(_, bp, bs))
      case E.Singleton(a) => p(a, bp, bs)
      case E.Union(a, b) => s(a, bp, bs); s(b, bp, bs)
      case E.Intersection(a, b) => s(a, bp, bs); s(b, bp, bs)
      case E.Subtraction(a, b) => s(a, bp, bs); s(b, bp, bs)
      case E.Restriction(a, b) => s(a, bp, bs); s(b, bp, bs)
      case E.Raffination(a, b) => s(a, bp, bs); s(b, bp, bs)
      case E.Composition(a, b) => s(a, bp, bs); s(b, bp, bs)
      case E.Iteration(a, h, t, b) => s(a, bp, bs); s(b, bp + h.s, bs + t.s)
      case E.Fold(a, i, acc, h, t, b, u) =>
        s(a, bp, bs); p(i, bp, bs)
        s(b, bp + acc.s + h.s, bs + t.s); p(u, bp + acc.s + h.s, bs + t.s)
      case E.Fixpoint(a, v, b) => s(a, bp, bs); s(b, bp, bs + v.s)
      case E.Wrap(a, b) => s(a, bp, bs); p(b, bp, bs)
      case E.Unwrap(a, b) => s(a, bp, bs); p(b, bp, bs)
      case E.TailsUnion(a) => s(a, bp, bs)
      case E.TailsIntersection(a) => s(a, bp, bs)
      case E.PrefixClosure(a) => s(a, bp, bs)
      case E.SuffixClosure(a) => s(a, bp, bs)
      case E.TailsClosure(a) => s(a, bp, bs)
      case E.GroundedPS(a, _) => p(a, bp, bs)
      case E.GroundedSS(a, _) => s(a, bp, bs)
      case E.Range(a, _, _) => s(a, bp, bs)
    s(x, Set.empty, Set.empty)
    Captures(ps.values.toVector.sortBy(_.s), ss.values.toVector.sortBy(_.s))
  private def without(c: Captures, ps: PathRef*)(ss: SpaceMention*): Captures =
    Captures(c.ps.filterNot(p => ps.exists(_.s == p.s)), c.ss.filterNot(s => ss.exists(_.s == s.s)))

  private def hinted(ref: PathRef): PathRef = encoding.framing match
    case CodeFraming.Fixed(n) if ref.lengthHint >= 0 =>
      val value = BigInt(ref.lengthHint) * n
      if value.isValidInt then ref.known(value.toInt) else PathRef(ref.s)
    case _ =>
      val length = if ref.lengthHint >= 0 then ref.lengthHint else ref.maxLengthHint
      (encoding.maxCodeLength, Option.when(length >= 0)(length)) match
        case (Some(n), Some(k)) if (BigInt(n) * k).isValidInt => PathRef(ref.s).bounded(n * k)
        case _ => PathRef(ref.s)

  /** Iterate complete source digits, grouping all their tails before the body. */
  private def iter(src: target.Space, h: PathRef, t: SpaceMention, body: target.Space): target.Space =
    encoding.framing match
      case CodeFraming.Fixed(1) => E.Iteration(src, h.known(1), t, body)
      case framing =>
        val captures = without(free(body), h)(t)
        val callback = rp("symbol_body")
        definitions(callback) = R(callback, Vector(h) ++ captures.ps, Vector(t) ++ captures.ss, body)
        def done(head: target.Path, tail: target.Space): target.Space =
          E.Call(callback, Vector(head) ++ captures.pathArgs, Vector(tail) ++ captures.spaceArgs)
        framing match
          case CodeFraming.Fixed(width) =>
            def unroll(rest: target.Space, depth: Int, head: target.Path): target.Space =
              if depth == width then done(head, rest)
              else
                val d = pr("block_digit").known(1)
                val r = sm("block_tail")
                E.Iteration(rest, d, r, unroll(sd(r), depth + 1, P.Concat(head, pd(d))))
            unroll(src, 0, P.ZERO)
          case CodeFraming.ZeroTerminated =>
            val scan = rp("terminated_iter")
            val a = pr("encoded_head")
            val s = sm("encoded_rest")
            val d = pr("byte").known(1)
            val r = sm("byte_tail")
            val next = P.Concat(pd(a), pd(d))
            val body = E.Iteration(sd(s), d, r,
              choose(E.Intersection(one(pd(d)), one(target.digit(0))), done(next, sd(r)),
                E.Call(scan, Vector(next) ++ captures.pathArgs, Vector(sd(r)) ++ captures.spaceArgs)))
            definitions(scan) = R(scan, Vector(a) ++ captures.ps, Vector(s) ++ captures.ss, body)
            // Enter through native iteration so an empty/epsilon-only source
            // never evaluates captures belonging to an unexecuted body.
            E.Iteration(src, d, r,
              choose(E.Intersection(one(pd(d)), one(target.digit(0))), done(pd(d), sd(r)),
                E.Call(scan, Vector(pd(d)) ++ captures.pathArgs, Vector(sd(r)) ++ captures.spaceArgs)))
          case CodeFraming.LengthPrefixed =>
            // A shared predecessor relation implements the bounded byte counter
            // using ordinary literals/unwrap/iteration and function abstraction.
            // No counter, length annotation or dictionary is attached to data.
            val predecessors=E.Literal(target.SpaceValue((1 to 255).map(n=>
              target.paths.fromDigits(Vector(n,n-1))).toSet))
            val scan=rp("length_iter")
            val a=pr("encoded_head").bounded(256)
            val left=pr("bytes_left").known(1)
            val s=sm("encoded_rest")
            val next=pr("next_length").known(1)
            val d=pr("byte").known(1);val r=sm("byte_tail")
            val step=bindPath(E.Unwrap(predecessors,pd(left)),next,
              E.Iteration(sd(s),d,r,E.Call(scan,Vector(P.Concat(pd(a),pd(d)),pd(next))++captures.pathArgs,
                Vector(sd(r))++captures.spaceArgs)))
            definitions(scan)=R(scan,Vector(a,left)++captures.ps,Vector(s)++captures.ss,
              choose(E.Intersection(one(pd(left)),one(target.digit(0))),done(pd(a),sd(s)),step))
            E.Iteration(src,d,r,E.Call(scan,Vector(pd(d),pd(d))++captures.pathArgs,Vector(sd(r))++captures.spaceArgs))
          case CodeFraming.Fibonacci(maxBits) =>
            val scans = (for depth <- 0 until maxBits; previous <- 0 to 1 yield
              (depth, previous) -> rp(s"fib_iter_$depth$previous")).toMap
            val a = pr("encoded_head")
            val s = sm("encoded_rest")
            for ((depth, previous), name) <- scans.toVector.sortBy(_._1) do
              val d = pr("bit").known(1)
              val r = sm("bit_tail")
              val next = P.Concat(pd(a.known(depth)), pd(d))
              def proceed(bit: Int): target.Space =
                if bit == 1 && previous == 1 then done(next, sd(r))
                else scans.get((depth + 1, bit)).fold[target.Space](E.Empty)(n =>
                  E.Call(n, Vector(next) ++ captures.pathArgs, Vector(sd(r)) ++ captures.spaceArgs))
              val body = E.Iteration(sd(s), d, r,
                choose(E.Intersection(one(pd(d)), one(target.digit(1))), proceed(1), proceed(0)))
              definitions(name) = R(name, Vector(a.known(depth)) ++ captures.ps, Vector(s) ++ captures.ss, body)
            val first = pr("first_bit").known(1)
            val rest = sm("first_tail")
            def enter(bit: Int): target.Space = scans.get((1, bit)).fold[target.Space](E.Empty)(name =>
              E.Call(name, Vector(pd(first)) ++ captures.pathArgs, Vector(sd(rest)) ++ captures.spaceArgs))
            E.Iteration(src, first, rest,
              choose(E.Intersection(one(pd(first)), one(target.digit(1))), enter(1), enter(0)))

  private def heads(src: target.Space): target.Space =
    val h = pr("head")
    iter(src, h, sm("tail"), one(pd(h)))
  private def tails(src: target.Space): target.Space =
    if encoding.framing==CodeFraming.LengthPrefixed then
      val length=pr("length").known(1);val tail=sm("payload")
      val branches=Vector.tabulate(256)(n=>(0 until n).foldLeft(sd(tail))((s,_)=>E.TailsUnion(s)))
      def select(lo: Int,hi: Int): target.Space =
        if lo==hi then branches(lo)
        else
          val mid=(lo+hi)/2
          val mask=E.Literal(target.SpaceValue((lo to mid).map(n=>target.paths.fromDigits(Vector(n))).toSet))
          choose(E.Intersection(one(pd(length)),mask),select(lo,mid),select(mid+1,hi))
      E.Iteration(src,length,tail,select(0,255))
    else
      val t = sm("tails")
      iter(src, pr("head"), t, sd(t))
  private val extremeHeadRoutines = mutable.Map.empty[Boolean, RoutinePtr]
  private def extremeHead(src: target.Space, last: Boolean): target.Space =
    val hs = heads(src)
    if encoding.framing==CodeFraming.LengthPrefixed then
      // Length order differs from source lexical order. Select payload bytes,
      // then recover their original length byte. CESU-8 preserves UTF-16 order.
      val payload=E.Range(E.TailsUnion(hs),if last then -1 else 0,if last then 0 else 1)
      val length=pr("selected_length").known(1);val tails=sm("selected_payloads")
      E.Iteration(hs,length,tails,E.Wrap(E.Intersection(sd(tails),payload),pd(length)))
    else if encoding.preservesOrder then E.Range(hs, if last then -1 else 0, if last then 0 else 1)
    else
      val name = extremeHeadRoutines.getOrElseUpdate(last, {
        val name = rp("ordered_head")
        val candidates = sm("head_candidates")
        val codes = if last then encoding.orderedCodewords.reverse else encoding.orderedCodewords
        def select(values: Vector[T]): target.Space = values match
          case Vector() => E.Empty
          case Vector(code) => E.Intersection(sd(candidates), one(P.Constant(code)))
          case _ =>
            val (left, right) = values.splitAt(values.size / 2)
            val found = E.Intersection(sd(candidates), E.Literal(target.SpaceValue(left.toSet)))
            choose(found, select(left), select(right))
        definitions(name) = R(name, Vector.empty, Vector(candidates), select(codes))
        name
      })
      // Compute the full head set once; every codebook decision shares it.
      E.Call(name, Vector.empty, Vector(hs))

  /** Reify a singleton path as a path parameter using digit iteration/calls.
    * This is also used to thread Fold's path accumulator without a host callback.
    */
  private def bindPath(value: target.Space, ref: PathRef, body: target.Space): target.Space =
    val captures = without(free(body), ref)()
    val callback = rp("path_body")
    definitions(callback) = R(callback, Vector(ref) ++ captures.ps, captures.ss, body)
    val loop = rp("bind_path")
    val a = pr("path_prefix")
    val s = sm("path_suffix")
    val h = pr("path_digit").known(1)
    val t = sm("path_tail")
    val terminal = E.Composition(E.Intersection(sd(s), eps),
      E.Call(callback, Vector(pd(a)) ++ captures.pathArgs, captures.spaceArgs))
    val step = E.Iteration(sd(s), h, t, E.Call(loop,
      Vector(P.Concat(pd(a), pd(h))) ++ captures.pathArgs, Vector(sd(t)) ++ captures.spaceArgs))
    definitions(loop) = R(loop, Vector(a) ++ captures.ps, Vector(s) ++ captures.ss, E.Union(terminal, step))
    E.Call(loop, Vector(P.ZERO) ++ captures.pathArgs, Vector(value) ++ captures.spaceArgs)

  private val extremeRoutines = mutable.Map.empty[Boolean, RoutinePtr]
  private def extreme(src: target.Space, last: Boolean): target.Space =
    val name = extremeRoutines.getOrElseUpdate(last, {
      val fn = rp(if last then "last_path" else "first_path")
      val s = sm("ordered_source")
      val h = pr("ordered_head")
      val group = E.Unwrap(sd(s), pd(h))
      val headed = bindPath(extremeHead(sd(s), last), h, E.Wrap(E.Call(fn, Vector.empty, Vector(group)), pd(h)))
      // Testing the recursively selected result here duplicated the complete
      // descent at every level. Test the local frontier and descend only once.
      val body = if last then choose(E.TailsUnion(sd(s)), headed, E.Intersection(sd(s), eps))
        else choose(E.Intersection(sd(s), eps), eps, headed)
      definitions(fn) = R(fn, Vector.empty, Vector(s), body)
      fn
    })
    E.Call(name, Vector.empty, Vector(src))

  private val dropRoutines = mutable.Map.empty[(Boolean, Int), RoutinePtr]
  private def drop(src: target.Space, count: BigInt, last: Boolean): target.Space =
    def power(k: Int): RoutinePtr = dropRoutines.getOrElseUpdate((last, k), {
      val name = rp(s"drop_${last}_$k")
      val s = sm("drop_source")
      val body = if k == 0 then E.Subtraction(sd(s), extreme(sd(s), last))
        else
          val previous = power(k - 1)
          E.Call(previous, Vector.empty, Vector(E.Call(previous, Vector.empty, Vector(sd(s)))))
      definitions(name) = R(name, Vector.empty, Vector(s), choose(sd(s), body, E.Empty))
      name
    })
    (0 until count.bitLength).filter(count.testBit).foldLeft(src)((s, k) => E.Call(power(k), Vector.empty, Vector(s)))
  private val rangeRoutines = mutable.Map.empty[(Int, Int), RoutinePtr]
  private def range(src: target.Space, start: Int, end: Int): target.Space =
    if encoding.preservesOrder then E.Range(src, start, end)
    else
      val name = rangeRoutines.getOrElseUpdate((start, end), {
        val name = rp("ordered_range")
        val parameter = sm("range_source")
        val value = sd(parameter)
        val lower = if start < 0 then E.Subtraction(value, drop(value, -BigInt(start), last = true))
          else drop(value, (BigInt(start) - 1).max(BigInt(0)), last = false)
        val upper = if end == 0 then value
          else if end < 0 then drop(value, -BigInt(end), last = true)
          else E.Subtraction(value, drop(value, BigInt(end) - (if start == 0 then 0 else 1), last = false))
        definitions(name) = R(name, Vector.empty, Vector(parameter), E.Intersection(lower, upper))
        name
      })
      // Bounds and repeated drops share the evaluated source argument.
      E.Call(name, Vector.empty, Vector(src))

  private val closureRoutines = mutable.Map.empty[Int, RoutinePtr]
  private def closure(src: target.Space, kind: Int): target.Space =
    val name = closureRoutines.getOrElseUpdate(kind, {
      val name = rp("closure")
      val s = sm("closure_source")
      val h = pr("closure_head")
      val t = sm("closure_tail")
      val recursive = E.Call(name, Vector.empty, Vector(sd(t)))
      val body = if kind == 0 then
        iter(sd(s), h, t, E.Union(one(pd(h)), E.Wrap(recursive, pd(h))))
      else E.Union(if kind == 1 then E.Subtraction(sd(s), eps) else sd(s), iter(sd(s), h, t, recursive))
      definitions(name) = R(name, Vector.empty, Vector(s), body)
      name
    })
    E.Call(name, Vector.empty, Vector(src))

  private def fold(src: target.Space, initial: target.Path, acc: PathRef, h: PathRef,
                   t: SpaceMention, body: target.Space, update: target.Path): target.Space =
    val captures = without(free(E.Union(body, one(update))), acc, h)(t)
    val loop = rp("ordered_fold")
    val s = sm("fold_source")
    val continuation = E.Call(loop, Vector(update) ++ captures.pathArgs,
      Vector(E.Raffination(sd(s), one(pd(h)))) ++ captures.spaceArgs)
    // Bind the complete tail group using an ordinary call, preserving sharing.
    val step = rp("fold_step")
    definitions(step) = R(step, Vector(acc, h) ++ captures.ps, Vector(t, s) ++ captures.ss,
      E.Union(body, continuation))
    val stepped = E.Call(step, Vector(pd(acc), pd(h)) ++ captures.pathArgs,
      Vector(E.Unwrap(sd(s), pd(h)), sd(s)) ++ captures.spaceArgs)
    definitions(loop) = R(loop, Vector(acc) ++ captures.ps, Vector(s) ++ captures.ss,
      bindPath(extremeHead(sd(s), last = false), h, stepped))
    E.Call(loop, Vector(initial) ++ captures.pathArgs, Vector(src) ++ captures.spaceArgs)

  def compile(entry: source.Space, routines: Map[RoutinePtr, source.Routine] = Map.empty): target.Program =
    require(!compiled, "use a fresh compiler for each program")
    compiled = true
    // Reserve every user name, including bound names, before generating helpers.
    def reserveP(x: source.Path): Unit = x match
      case source.Path.Deref(r) => used += r.s
      case source.Path.Constant(_) => ()
      case source.Path.Concat(a, b) => reserveP(a); reserveP(b)
      case source.Path.GroundedPP(a, _) => reserveP(a)
      case source.Path.GroundedSP(a, _) => reserveS(a)
    def reserveS(x: source.Space): Unit = x match
      case source.Space.Empty | source.Space.Literal(_) => ()
      case source.Space.Mention(r) => used += r.s
      case source.Space.Call(r, ps, ss) => used += r.s; ps.foreach(reserveP); ss.foreach(reserveS)
      case source.Space.Singleton(p) => reserveP(p)
      case source.Space.Union(a, b) => reserveS(a); reserveS(b)
      case source.Space.Intersection(a, b) => reserveS(a); reserveS(b)
      case source.Space.Subtraction(a, b) => reserveS(a); reserveS(b)
      case source.Space.Restriction(a, b) => reserveS(a); reserveS(b)
      case source.Space.Raffination(a, b) => reserveS(a); reserveS(b)
      case source.Space.Composition(a, b) => reserveS(a); reserveS(b)
      case source.Space.Iteration(a, h, t, b) => used += h.s; used += t.s; reserveS(a); reserveS(b)
      case source.Space.Fold(a, i, acc, h, t, b, u) =>
        used += acc.s; used += h.s; used += t.s; reserveS(a); reserveP(i); reserveS(b); reserveP(u)
      case source.Space.Fixpoint(a, v, b) => used += v.s; reserveS(a); reserveS(b)
      case source.Space.Wrap(a, p) => reserveS(a); reserveP(p)
      case source.Space.Unwrap(a, p) => reserveS(a); reserveP(p)
      case source.Space.TailsUnion(a) => reserveS(a)
      case source.Space.TailsIntersection(a) => reserveS(a)
      case source.Space.PrefixClosure(a) => reserveS(a)
      case source.Space.SuffixClosure(a) => reserveS(a)
      case source.Space.TailsClosure(a) => reserveS(a)
      case source.Space.GroundedPS(p, _) => reserveP(p)
      case source.Space.GroundedSS(a, _) => reserveS(a)
      case source.Space.Range(a, _, _) => reserveS(a)
    reserveS(entry)
    routines.keys.foreach(name => used += name.s)
    routines.values.foreach { r => used += r.name.s; r.refs.foreach(p => used += p.s); r.mentions.foreach(s => used += s.s); reserveS(r.body) }
    def p(x: source.Path, pe: Map[PathRef, PathRef], se: Map[SpaceMention, SpaceMention]): target.Path = x match
      case source.Path.Deref(r) => pd(pe.getOrElse(r, hinted(r)))
      case source.Path.Constant(v) => P.Constant(encoding.encode(v))
      case source.Path.Concat(a, b) => P.Concat(p(a, pe, se), p(b, pe, se))
      case source.Path.GroundedPP(a, f) => P.GroundedPP(p(a, pe, se), v => encoding.encode(f(encoding.decode(v))))
      case source.Path.GroundedSP(a, f) => P.GroundedSP(s(a, pe, se), v => encoding.encode(f(source.SpaceValue(v.paths.map(encoding.decode)))))
    def pbind(pe: Map[PathRef, PathRef], old: PathRef, next: PathRef): Map[PathRef, PathRef] =
      if old.s == "_" then pe else pe.updated(old, next)
    def sbind(se: Map[SpaceMention, SpaceMention], old: SpaceMention, next: SpaceMention): Map[SpaceMention, SpaceMention] =
      if old.s == "_" then se else se.updated(old, next)
    def s(x: source.Space, pe: Map[PathRef, PathRef], se: Map[SpaceMention, SpaceMention]): target.Space = x match
      case source.Space.Empty => E.Empty
      case source.Space.Call(r, ps, ss) => E.Call(r, ps.map(p(_, pe, se)), ss.map(s(_, pe, se)))
      case source.Space.Mention(r) => sd(se.getOrElse(r, r))
      case source.Space.Singleton(a) => one(p(a, pe, se))
      case source.Space.Literal(v) => E.Literal(target.SpaceValue(v.paths.map(encoding.encode)))
      case source.Space.Union(a, b) => E.Union(s(a, pe, se), s(b, pe, se))
      case source.Space.Intersection(a, b) => E.Intersection(s(a, pe, se), s(b, pe, se))
      case source.Space.Subtraction(a, b) => E.Subtraction(s(a, pe, se), s(b, pe, se))
      case source.Space.Restriction(a, b) => E.Restriction(s(a, pe, se), s(b, pe, se))
      case source.Space.Raffination(a, b) => E.Raffination(s(a, pe, se), s(b, pe, se))
      case source.Space.Composition(a, b) => E.Composition(s(a, pe, se), s(b, pe, se))
      case source.Space.Wrap(a, b) => E.Wrap(s(a, pe, se), p(b, pe, se))
      case source.Space.Unwrap(a, b) => E.Unwrap(s(a, pe, se), p(b, pe, se))
      case source.Space.Iteration(a, h, t, b) =>
        val nh = hinted(PathRef(fresh("symbol")).known(1))
        val nt = sm("rest")
        iter(s(a, pe, se), nh, nt, s(b, pbind(pe, h, nh), sbind(se, t, nt)))
      case source.Space.Fold(a, i, acc, h, t, b, u) =>
        val rawAcc = pr("accumulator")
        val na = hinted(if acc.lengthHint >= 0 then rawAcc.known(acc.lengthHint)
          else if acc.maxLengthHint >= 0 then rawAcc.bounded(acc.maxLengthHint) else rawAcc)
        val nh = hinted(PathRef(fresh("symbol")).known(1))
        val nt = sm("rest")
        val np = pbind(pbind(pe, acc, na), h, nh)
        val ns = sbind(se, t, nt)
        if unitEncoding && encoding.preservesOrder then
          E.Fold(s(a, pe, se), p(i, pe, se), na, nh, nt, s(b, np, ns), p(u, np, ns))
        else fold(s(a, pe, se), p(i, pe, se), na, nh, nt, s(b, np, ns), p(u, np, ns))
      case source.Space.Fixpoint(a, v, b) =>
        val nv = sm("fixpoint")
        E.Fixpoint(s(a, pe, se), nv, s(b, pe, sbind(se, v, nv)))
      case source.Space.TailsUnion(a) =>
        if unitEncoding then E.TailsUnion(s(a, pe, se)) else tails(s(a, pe, se))
      case source.Space.TailsIntersection(a) =>
        val src = s(a, pe, se)
        if unitEncoding then E.TailsIntersection(src)
        else
          // Share the source and union of tails through ordinary parameters.
          // Recomputing either inside each branch makes large encoded meets
          // quadratic in the number of heads before the actual set algebra.
          val sourceName = sm("intersection_source")
          val allName = sm("intersection_all")
          val t = sm("intersection_tail")
          val meet = rp("intersection_meet")
          definitions(meet) = R(meet, Vector.empty, Vector(sourceName, allName),
            E.Subtraction(sd(allName), iter(sd(sourceName), pr("intersection_head"), t,
              E.Subtraction(sd(allName), sd(t)))))
          val prepare = rp("intersection_prepare")
          definitions(prepare) = R(prepare, Vector.empty, Vector(sourceName),
            E.Call(meet, Vector.empty, Vector(sd(sourceName), tails(sd(sourceName)))))
          E.Call(prepare, Vector.empty, Vector(src))
      case source.Space.PrefixClosure(a) =>
        if unitEncoding then E.PrefixClosure(s(a, pe, se)) else closure(s(a, pe, se), 0)
      case source.Space.SuffixClosure(a) =>
        if unitEncoding then E.SuffixClosure(s(a, pe, se)) else closure(s(a, pe, se), 1)
      case source.Space.TailsClosure(a) =>
        if unitEncoding then E.TailsClosure(s(a, pe, se)) else closure(s(a, pe, se), 2)
      case source.Space.GroundedPS(a, f) => E.GroundedPS(p(a, pe, se), v => target.SpaceValue(f(encoding.decode(v)).paths.map(encoding.encode)))
      case source.Space.GroundedSS(a, f) => E.GroundedSS(s(a, pe, se), v => target.SpaceValue(f(source.SpaceValue(v.paths.map(encoding.decode))).paths.map(encoding.encode)))
      case source.Space.Range(a, lo, hi) => range(s(a, pe, se), lo, hi)
    routines.toVector.sortBy(_._1.s).foreach { (name, routine) =>
      val refs = routine.refs.map(hinted)
      definitions(name) = R(name, refs, routine.mentions, s(routine.body, (routine.refs zip refs).toMap, Map.empty))
    }
    val result = s(entry, Map.empty, Map.empty)
    target.Program(result, definitions.toMap)
