package morkl

import scala.collection.mutable

private[morkl] object WordSyntax:
  case class Free(paths: Map[String, PathRef], spaces: Map[String, SpaceMention]):
    def ++(other: Free): Free = Free(paths ++ other.paths, spaces ++ other.spaces)
    def without(ps: PathRef*): Free = copy(paths = paths -- ps.map(_.s))
    def withoutSpace(ss: SpaceMention*): Free = copy(spaces = spaces -- ss.map(_.s))
  val empty: Free = Free(Map.empty, Map.empty)
  def path[P](p: WordPath[P]): Free = p match
    case WordPath.Deref(r) => Free(Map(r.s -> r), Map.empty)
    case WordPath.Constant(_) => empty
    case WordPath.Concat(l, r) => path(l) ++ path(r)
    case WordPath.GroundedPP(p, _) => path(p)
    case WordPath.GroundedSP(s, _) => free(s)
  def free[P](s: WordSpace[P]): Free =
    import WordSpace.*
    s match
      case Empty() | Literal(_) => empty
      case Call(_, ps, ss) => ps.map(path).foldLeft(empty)(_ ++ _) ++ ss.map(free).foldLeft(empty)(_ ++ _)
      case Mention(m) => Free(Map.empty, Map(m.s -> m))
      case Singleton(p) => path(p)
      case Union(x, y) => free(x) ++ free(y)
      case Intersection(x, y) => free(x) ++ free(y)
      case Subtraction(x, y) => free(x) ++ free(y)
      case Restriction(x, y) => free(x) ++ free(y)
      case Raffination(x, y) => free(x) ++ free(y)
      case Composition(x, y) => free(x) ++ free(y)
      case Iteration(src, h, t, body) => free(src) ++ free(body).without(h).withoutSpace(t)
      case Fold(src, initial, a, h, t, body, update) =>
        free(src) ++ path(initial) ++ (free(body) ++ path(update)).without(a, h).withoutSpace(t)
      case Fixpoint(initial, v, step) => free(initial) ++ free(step).withoutSpace(v)
      case Wrap(src, p) => free(src) ++ path(p)
      case Unwrap(src, p) => free(src) ++ path(p)
      case TailsUnion(src) => free(src)
      case TailsIntersection(src) => free(src)
      case PrefixClosure(src) => free(src)
      case SuffixClosure(src) => free(src)
      case TailsClosure(src) => free(src)
      case GroundedPS(p, _) => path(p)
      case GroundedSS(s, _) => free(s)
      case Range(s, _, _) => free(s)

  /** Includes bound names, so generated names cannot capture nested binders. */
  def names[P](s: WordSpace[P]): Set[String] =
    import WordSpace.*
    val own = free(s).paths.keySet ++ free(s).spaces.keySet
    val nested: Set[String] = s match
      case Call(r, ps, ss) => Set(r.s) ++ ss.flatMap(names) ++ ps.flatMap(pathNames)
      case Singleton(p) => pathNames(p)
      case Union(x, y) => names(x) ++ names(y)
      case Intersection(x, y) => names(x) ++ names(y)
      case Subtraction(x, y) => names(x) ++ names(y)
      case Restriction(x, y) => names(x) ++ names(y)
      case Raffination(x, y) => names(x) ++ names(y)
      case Composition(x, y) => names(x) ++ names(y)
      case Iteration(src, h, t, b) => names(src) ++ names(b) ++ Set(h.s, t.s)
      case Fold(src, i, a, h, t, b, u) => names(src) ++ names(b) ++ pathNames(i) ++ pathNames(u) ++ Set(a.s, h.s, t.s)
      case Fixpoint(i, v, s) => names(i) ++ names(s) + v.s
      case Wrap(s, p) => names(s) ++ pathNames(p)
      case Unwrap(s, p) => names(s) ++ pathNames(p)
      case TailsUnion(s) => names(s)
      case TailsIntersection(s) => names(s)
      case PrefixClosure(s) => names(s)
      case SuffixClosure(s) => names(s)
      case TailsClosure(s) => names(s)
      case GroundedPS(p, _) => pathNames(p)
      case GroundedSS(s, _) => names(s)
      case Range(s, _, _) => names(s)
      case _ => Set.empty
    own ++ nested
  def pathNames[P](p: WordPath[P]): Set[String] = p match
    case WordPath.Deref(r) => Set(r.s)
    case WordPath.Constant(_) => Set.empty
    case WordPath.Concat(l, r) => pathNames(l) ++ pathNames(r)
    case WordPath.GroundedPP(p, _) => pathNames(p)
    case WordPath.GroundedSP(s, _) => names(s)

/** A syntax-to-syntax compiler. All boundary recognition, closure traversal,
  * ordering repair and fold sequencing use the original algebra and routines.
  * Host adapters occur only where the source already had a grounded callback.
  */
final class WordCompiler[A, B](encoding: WordEncoding[A, B], unrollBounded: Boolean = false):
  import WordSpace.*
  private val target = encoding.target
  private val definitions = mutable.LinkedHashMap.empty[RoutinePtr, WordRoutine[B]]
  private var used = Set.empty[String]
  private var serial = 0
  private def fresh(tag: String): String =
    var name = ""
    while name.isEmpty || used(name) do
      name = s"__zippy_${tag}_$serial"
      serial += 1
    used += name
    name
  private def pr(tag: String): PathRef = PathRef(fresh(tag))
  private def sm(tag: String): SpaceMention = SpaceMention(fresh(tag))
  private def rp(tag: String): RoutinePtr = RoutinePtr(fresh(tag))
  private def constant(p: B): WordPath[B] = WordPath.Constant(p)
  private def deref(p: PathRef): WordPath[B] = WordPath.Deref(p)
  private def mention(p: SpaceMention): WordSpace[B] = Mention(p)
  private def epsilon: WordSpace[B] = Singleton(constant(target.empty))
  private def union(xs: Iterable[WordSpace[B]]): WordSpace[B] = xs.reduceOption(Union(_, _)).getOrElse(Empty())
  private def nonEmpty(s: WordSpace[B]): WordSpace[B] =
    Union(Intersection(s, epsilon), Iteration(Range(s, 0, 1), pr("exists"), sm("exists"), epsilon))
  private def guard(test: WordSpace[B], body: WordSpace[B]): WordSpace[B] = Composition(nonEmpty(test), body)
  private def encodedRef(p: PathRef): PathRef = encoding.shape match
    case WordCodeShape.Fixed(width) if p.lengthHint >= 0 => p.known(Math.multiplyExact(p.lengthHint, width))
    case _ if p.maxLengthHint >= 0 && encoding.maxCodeLength.nonEmpty =>
      p.bounded(Math.multiplyExact(p.maxLengthHint, encoding.maxCodeLength.get))
    case _ => PathRef(p.s)

  def compile(entry: WordSpace[A], routines: Map[RoutinePtr, WordRoutine[A]] = Map.empty): WordProgram[B] =
    require(definitions.isEmpty, "a compiler instance is single-use")
    used = WordSyntax.names(entry) ++ routines.valuesIterator.flatMap(r =>
      WordSyntax.names(r.body) ++ r.refs.map(_.s) ++ r.mentions.map(_.s) + r.name.s).toSet
    val result = space(WordReferenceHints.tag(entry))
    routines.values.toVector.sortBy(_.name.s).foreach { r =>
      val body = space(WordReferenceHints.tag(r.body, r.refs))
      definitions(r.name) = WordRoutine(r.name, r.refs.map(encodedRef), r.mentions, body)
    }
    WordProgram(result, definitions.toMap)

  private def path(p: WordPath[A]): WordPath[B] = p match
    case WordPath.Deref(r) => deref(encodedRef(r))
    case WordPath.Constant(p) => constant(encoding.encode(p))
    case WordPath.Concat(l, r) => WordPath.Concat(path(l), path(r))
    case WordPath.GroundedPP(p, f) => WordPath.GroundedPP(path(p), x => encoding.encode(f(encoding.decode(x))))
    case WordPath.GroundedSP(s, f) => WordPath.GroundedSP(space(s), x => encoding.encode(f(encoding.decodeSpace(x))))

  /** Recognize one source symbol. The continuation receives the whole encoded
    * head and the grouped suffix space, rather than one target digit at a time.
    */
  private def iterate(src: WordSpace[B], h: PathRef, t: SpaceMention, body: WordSpace[B],
                      code: WordEncoding[A, B] = encoding): WordSpace[B] =
    val free = WordSyntax.free(body).without(h).withoutSpace(t)
    val cps = free.paths.values.toVector.sortBy(_.s)
    val css = free.spaces.values.toVector.sortBy(_.s)
    val leaf = rp("symbol_body")
    val boundH = code.shape match
      case WordCodeShape.Fixed(n) => h.known(n)
      case _ => code.maxCodeLength.fold(PathRef(h.s))(h.bounded)
    val limit = Option.when(boundH.maxLengthHint >= 0)(boundH.maxLengthHint).orElse(code.maxCodeLength)
    definitions(leaf) = WordRoutine(leaf, boundH +: cps, t +: css, body)
    def finish(prefix: WordPath[B], tail: WordSpace[B]): WordSpace[B] =
      Call(leaf, prefix +: cps.map(deref), tail +: css.map(mention))
    code.shape match
      case WordCodeShape.Fixed(width) =>
        require(width > 0)
        def descend(s: WordSpace[B], prefix: WordPath[B], depth: Int): WordSpace[B] =
          if depth == width then finish(prefix, s)
          else
            val digit = pr("digit").known(1); val rest = sm("digits")
            Iteration(s, digit, rest, descend(mention(rest), WordPath.Concat(prefix, deref(digit)), depth + 1))
        descend(src, constant(target.empty), 0)
      case shape =>
        val prefix = code.maxCodeLength.fold(pr("prefix")) { max => pr("prefix").bounded(max) }
        val remaining = sm("remaining")
        val cache = mutable.Map.empty[(Boolean, Int), RoutinePtr]
        def scanner(previousOne: Boolean, depth: Int): RoutinePtr =
          val key = previousOne -> (if unrollBounded && limit.nonEmpty then depth else -1)
          cache.getOrElseUpdate(key, {
            val name = rp("scan")
            // Install the key before constructing mutually recursive bodies.
            cache(key) = name
            val digit = pr("digit").known(1); val rest = sm("digits")
            val nextPrefix = WordPath.Concat(deref(prefix), deref(digit))
            def next(state: Boolean): WordSpace[B] =
              if unrollBounded && limit.exists(depth + 1 >= _) then Empty()
              else Call(scanner(state, depth + 1), nextPrefix +: cps.map(deref), mention(rest) +: css.map(mention))
            def isDigit(n: Int): WordSpace[B] = Intersection(Singleton(deref(digit)), Singleton(constant(target.fromDigits(Vector(n)))))
            val zero = isDigit(0)
            val one = isDigit(1)
            val branch = shape match
              case WordCodeShape.Fibonacci =>
                Union(guard(zero, next(false)), guard(one, if previousOne then finish(nextPrefix, mention(rest)) else next(true)))
              case WordCodeShape.Terminated(b) =>
                val terminal = isDigit(b)
                Union(guard(terminal, finish(nextPrefix, mention(rest))),
                  guard(Subtraction(Singleton(deref(digit)), terminal), next(false)))
              case _ => throw IllegalStateException("fixed scanner dispatched as variable")
            definitions(name) = WordRoutine(name, prefix +: cps, remaining +: css,
              Iteration(mention(remaining), digit, rest, branch))
            name
          })
        Call(scanner(false, 0), constant(target.empty) +: cps.map(deref), src +: css.map(mention))

  private def tails(src: WordSpace[B]): WordSpace[B] =
    val h = pr("head"); val t = sm("tail")
    iterate(src, h, t, mention(t))

  private def closure(src: WordSpace[B], kind: String): WordSpace[B] =
    val name = rp(kind); val input = sm("closure_input")
    val h = pr("head"); val t = sm("tail")
    val recurse = Call(name, Vector.empty, Vector(mention(t)))
    val body = kind match
      case "prefix" => iterate(mention(input), h, t, Union(Singleton(deref(h)), Wrap(recurse, deref(h))))
      case "suffix" => Union(Subtraction(mention(input), epsilon), iterate(mention(input), h, t, recurse))
      case "tails" => Union(mention(input), iterate(mention(input), h, t, recurse))
    definitions(name) = WordRoutine(name, Vector.empty, Vector(input), body)
    Call(name, Vector.empty, Vector(src))

  /** Recode into a lexicographically ordered fixed-width alphabet, apply the
    * target's existing Range, then recode back. Intern ids and Fibonacci codes
    * generally do not have the source symbol ordering.
    */
  private lazy val orderCode: WordEncoding[A, B] =
    val alphabet = (0 until target.alphabetSize).toVector.sortWith { (a, b) =>
      target.ordering.compare(target.fromDigits(Vector(a)), target.fromDigits(Vector(b))) < 0
    }
    require(alphabet.size >= 2, "ordered projection needs two target digits")
    var width = 1
    while (BigInt(1) << width) < encoding.orderedDigits.size do width += 1
    val codes = encoding.orderedDigits.zipWithIndex.map { (digit, rank) =>
      digit -> Vector.tabulate(width)(i => alphabet((rank >>> (width - 1 - i)) & 1))
    }.toMap
    WordEncoding(encoding.source, target, codes.apply, ds => codes.find(_._2 == ds).get._1,
      WordCodeShape.Fixed(width), Some(width), encoding.orderedDigits, true)

  private def recoder(from: WordEncoding[A, B], to: WordEncoding[A, B]): RoutinePtr =
    val name = rp("recode"); val input = sm("recode_input")
    val h = pr("head"); val t = sm("tail")
    val mapped = union(encoding.orderedDigits.map { d =>
      guard(Intersection(Singleton(deref(h)), Singleton(constant(target.fromDigits(from.code(d))))),
        Singleton(constant(target.fromDigits(to.code(d)))))
    })
    val step = iterate(mention(input), h, t,
      Composition(mapped, Call(name, Vector.empty, Vector(mention(t)))), from)
    definitions(name) = WordRoutine(name, Vector.empty, Vector(input), Union(Intersection(mention(input), epsilon), step))
    name
  private lazy val toOrder: RoutinePtr = recoder(encoding, orderCode)
  private lazy val fromOrder: RoutinePtr = recoder(orderCode, encoding)
  private def orderedRange(src: WordSpace[B], start: Int, end: Int): WordSpace[B] =
    if encoding.preservesOrder then Range(src, start, end)
    else Call(fromOrder, Vector.empty, Vector(Range(Call(toOrder, Vector.empty, Vector(src)), start, end)))

  private def fold(src: WordSpace[B], initial: WordPath[B], acc: PathRef, h: PathRef,
                   t: SpaceMention, body: WordSpace[B], update: WordPath[B]): WordSpace[B] =
    val free = (WordSyntax.free(body) ++ WordSyntax.path(update)).without(acc, h).withoutSpace(t)
    val cps = free.paths.values.toVector.sortBy(_.s); val css = free.spaces.values.toVector.sortBy(_.s)
    val loop = rp("fold"); val input = sm("fold_input")
    val selected = pr("selected"); val ignored = sm("selected_tail")
    val head = pr("head"); val tail = sm("tail")
    val heads = iterate(mention(input), head, tail, Singleton(deref(head)))
    val step = rp("fold_step"); val leftover = sm("fold_remaining")
    definitions(step) = WordRoutine(step, Vector(acc, h) ++ cps, Vector(t, leftover) ++ css,
      Union(body, Call(loop, update +: cps.map(deref), mention(leftover) +: css.map(mention))))
    val next = Call(step, Vector(deref(acc), deref(selected)) ++ cps.map(deref),
      Vector(Unwrap(mention(input), deref(selected)), Raffination(mention(input), Singleton(deref(selected)))) ++ css.map(mention))
    definitions(loop) = WordRoutine(loop, acc +: cps, input +: css,
      iterate(orderedRange(heads, 0, 1), selected, ignored, next))
    Call(loop, initial +: cps.map(deref), src +: css.map(mention))

  private def space(s: WordSpace[A]): WordSpace[B] = s match
    case Empty() => Empty()
    case Call(r, ps, ss) => Call(r, ps.map(path), ss.map(space))
    case Mention(m) => Mention(m)
    case Singleton(p) => Singleton(path(p))
    case Literal(v) => Literal(encoding.encodeSpace(v))
    case Union(x, y) => Union(space(x), space(y))
    case Intersection(x, y) => Intersection(space(x), space(y))
    case Subtraction(x, y) => Subtraction(space(x), space(y))
    case Restriction(x, y) => Restriction(space(x), space(y))
    case Raffination(x, y) => Raffination(space(x), space(y))
    case Composition(x, y) => Composition(space(x), space(y))
    case Iteration(src, h, t, b) => iterate(space(src), encodedRef(h.known(1)), t, space(b))
    case Fold(src, i, a, h, t, b, u) => fold(space(src), path(i), encodedRef(a), encodedRef(h.known(1)), t, space(b), path(u))
    case Fixpoint(i, v, step) => Fixpoint(space(i), v, space(step))
    case Wrap(src, p) => Wrap(space(src), path(p))
    case Unwrap(src, p) => Unwrap(space(src), path(p))
    case TailsUnion(src) => tails(space(src))
    case TailsIntersection(src) =>
      val source = space(src)
      val all = tails(source); val h = pr("head"); val t = sm("tail")
      Subtraction(all, iterate(source, h, t, Subtraction(all, mention(t))))
    case PrefixClosure(src) => closure(space(src), "prefix")
    case SuffixClosure(src) => closure(space(src), "suffix")
    case TailsClosure(src) => closure(space(src), "tails")
    case GroundedPS(p, f) => GroundedPS(path(p), x => encoding.encodeSpace(f(encoding.decode(x))))
    case GroundedSS(s, f) => GroundedSS(space(s), x => encoding.encodeSpace(f(encoding.decodeSpace(x))))
    case Range(src, start, end) => orderedRange(space(src), start, end)
