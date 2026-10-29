package morkl

import scala.collection.mutable

private[morkl] object WordSyntax:
  case class Free(paths: Map[String, PathRef], spaces: Map[String, SpaceMention]):
    def ++(other: Free): Free = Free(paths ++ other.paths, spaces ++ other.spaces)
    def without(ps: PathRef*): Free = copy(paths = paths -- ps.map(_.s))
    def withoutSpace(ss: SpaceMention*): Free = copy(spaces = spaces -- ss.map(_.s))
  val empty: Free = Free(Map.empty, Map.empty)
  private case class Analysis(free: Free, names: Set[String]):
    def ++(other: Analysis): Analysis = Analysis(free ++ other.free, names ++ other.names)
    def bind(ps: Vector[PathRef] = Vector.empty, ss: Vector[SpaceMention] = Vector.empty): Analysis =
      Analysis(free.without(ps*).withoutSpace(ss*), names ++ ps.map(_.s) ++ ss.map(_.s))
  private val zero = Analysis(empty, Set.empty)
  private def paths[P](p: WordPath[P]): Analysis = p match
    case WordPath.Deref(r) => Analysis(Free(Map(r.s -> r), Map.empty), Set(r.s))
    case WordPath.Constant(_) => zero
    case WordPath.Concat(l, r) => paths(l) ++ paths(r)
    case WordPath.GroundedPP(p, _) => paths(p)
    case WordPath.GroundedSP(s, _) => analyze(s)
  /** One exhaustive, binder-aware walk supplies both capture analysis and all
    * reserved names. Adding a constructor cannot silently omit it from either.
    */
  private def analyze[P](s: WordSpace[P]): Analysis =
    import WordSpace.*
    s match
      case Empty() | Literal(_) => zero
      case Call(r, ps, ss) => Analysis(empty, Set(r.s)) ++ ps.map(paths).foldLeft(zero)(_ ++ _) ++ ss.map(analyze).foldLeft(zero)(_ ++ _)
      case Mention(m) => Analysis(Free(Map.empty, Map(m.s -> m)), Set(m.s))
      case Singleton(p) => paths(p)
      case Union(x, y) => analyze(x) ++ analyze(y)
      case Intersection(x, y) => analyze(x) ++ analyze(y)
      case Subtraction(x, y) => analyze(x) ++ analyze(y)
      case Restriction(x, y) => analyze(x) ++ analyze(y)
      case Raffination(x, y) => analyze(x) ++ analyze(y)
      case Composition(x, y) => analyze(x) ++ analyze(y)
      case Iteration(src, h, t, b) => analyze(src) ++ analyze(b).bind(Vector(h), Vector(t))
      case Fold(src, i, a, h, t, b, u) => analyze(src) ++ paths(i) ++ (analyze(b) ++ paths(u)).bind(Vector(a, h), Vector(t))
      case Fixpoint(i, v, step) => analyze(i) ++ analyze(step).bind(ss = Vector(v))
      case Wrap(src, p) => analyze(src) ++ paths(p)
      case Unwrap(src, p) => analyze(src) ++ paths(p)
      case TailsUnion(src) => analyze(src)
      case TailsIntersection(src) => analyze(src)
      case PrefixClosure(src) => analyze(src)
      case SuffixClosure(src) => analyze(src)
      case TailsClosure(src) => analyze(src)
      case GroundedPS(p, _) => paths(p)
      case GroundedSS(s, _) => analyze(s)
      case Range(s, _, _) => analyze(s)
  def free[P](s: WordSpace[P]): Free = analyze(s).free
  def path[P](p: WordPath[P]): Free = paths(p).free
  def names[P](s: WordSpace[P]): Set[String] = analyze(s).names

  /** Replace only free occurrences. Nested binders can shadow the names being
    * specialized, so a plain structural dereference replacement is unsound.
    */
  def substitute[P](s: WordSpace[P], ps: Map[String, WordPath[P]], ss: Map[String, WordSpace[P]]): WordSpace[P] =
    import WordSpace.*
    def path(p: WordPath[P]): WordPath[P] = p match
      case WordPath.Deref(r) => ps.getOrElse(r.s, p)
      case WordPath.Constant(_) => p
      case WordPath.Concat(l, r) => WordPath.Concat(path(l), path(r))
      case WordPath.GroundedPP(p, f) => WordPath.GroundedPP(path(p), f)
      case WordPath.GroundedSP(s, f) => WordPath.GroundedSP(rec(s), f)
    def rec(s: WordSpace[P]): WordSpace[P] = substitute(s, ps, ss)
    s match
      case Empty() | Literal(_) => s
      case Mention(m) => ss.getOrElse(m.s, s)
      case Call(r, p, s) => Call(r, p.map(path), s.map(rec))
      case Singleton(p) => Singleton(path(p))
      case Union(l, r) => Union(rec(l), rec(r))
      case Intersection(l, r) => Intersection(rec(l), rec(r))
      case Subtraction(l, r) => Subtraction(rec(l), rec(r))
      case Restriction(l, r) => Restriction(rec(l), rec(r))
      case Raffination(l, r) => Raffination(rec(l), rec(r))
      case Composition(l, r) => Composition(rec(l), rec(r))
      case Iteration(src, h, t, b) => Iteration(rec(src), h, t, substitute(b, ps - h.s, ss - t.s))
      case Fold(src, i, a, h, t, b, u) =>
        val inner = substitute(Singleton(u), ps -- Vector(a.s, h.s), ss - t.s).asInstanceOf[Singleton[P]].p
        Fold(rec(src), path(i), a, h, t, substitute(b, ps -- Vector(a.s, h.s), ss - t.s), inner)
      case Fixpoint(i, v, step) => Fixpoint(rec(i), v, substitute(step, ps, ss - v.s))
      case Wrap(s, p) => Wrap(rec(s), path(p))
      case Unwrap(s, p) => Unwrap(rec(s), path(p))
      case TailsUnion(s) => TailsUnion(rec(s))
      case TailsIntersection(s) => TailsIntersection(rec(s))
      case PrefixClosure(s) => PrefixClosure(rec(s))
      case SuffixClosure(s) => SuffixClosure(rec(s))
      case TailsClosure(s) => TailsClosure(rec(s))
      case GroundedPS(p, f) => GroundedPS(path(p), f)
      case GroundedSS(s, f) => GroundedSS(rec(s), f)
      case Range(s, a, b) => Range(rec(s), a, b)

/** A syntax-to-syntax compiler. All boundary recognition, closure traversal,
  * ordering repair and fold sequencing use the original algebra and routines.
  * Host adapters occur only where the source already had a grounded callback.
  */
final class WordCompiler[A, B](encoding: WordEncoding[A, B], unrollBounded: Boolean = true):
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
  private def union(xs: Iterable[WordSpace[B]]): WordSpace[B] =
    val values = xs.toVector
    def balanced(lo: Int, hi: Int): WordSpace[B] =
      if lo == hi then Empty()
      else if hi - lo == 1 then values(lo)
      else
        val mid = lo + (hi - lo) / 2
        Union(balanced(lo, mid), balanced(mid, hi))
    balanced(0, values.size)
  // Every dispatch test is empty or a singleton nonempty word. Iteration binds
  // control flow to the filtered input, including on eager target backends.
  private def guard(test: WordSpace[B], body: WordSpace[B]): WordSpace[B] =
    Iteration(test, pr("dispatch"), sm("dispatch"), body)
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
    if code.shape == WordCodeShape.Fixed(1) then return Iteration(src, h.known(1), t, body)
    // Small closed alphabets specialize the actual code trie, not a graph of
    // calls for every (state, depth). Constant complete heads need no lifting.
    if unrollBounded && code.shape == WordCodeShape.Fibonacci && code.orderedDigits.size <= 32 then
      val words = code.orderedDigits.map(code.code)
      def descend(input: WordSpace[B], candidates: Vector[Vector[Int]], depth: Int): WordSpace[B] =
        if candidates.isEmpty then Empty()
        else if candidates.head.size == depth then
          WordSyntax.substitute(body, Map(h.s -> constant(target.fromDigits(candidates.head))), Map(t.s -> input))
        else
          val digit = pr("trie_digit").known(1); val rest = sm("trie_rest")
          val branches = candidates.groupBy(_(depth)).toVector.sortBy(_._1).map { (b, group) =>
            val test = Intersection(Singleton(deref(digit)), Singleton(constant(target.fromDigits(Vector(b)))))
            guard(test, descend(mention(rest), group, depth + 1))
          }
          Iteration(input, digit, rest, union(branches))
      return descend(src, words, 0)
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
    if encoding.shape == WordCodeShape.Fixed(1) then return TailsUnion(src)
    val h = pr("head"); val t = sm("tail")
    iterate(src, h, t, mention(t))

  private def closure(src: WordSpace[B], kind: String): WordSpace[B] =
    if encoding.shape == WordCodeShape.Fixed(1) then return kind match
      case "prefix" => PrefixClosure(src)
      case "suffix" => SuffixClosure(src)
      case "tails" => TailsClosure(src)
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
  /** UTF-8 order differs from JVM UTF-16 string order only at supplementary
    * scalars: F0..F4 must sort after ED 9F.. and before EE. Insert ED before
    * those lead bytes. ED F0..F4 cannot occur in valid UTF-8, so this is an
    * injective, reversible byte transducer, with no symbol dictionary.
    */
  private def utf8OrderRecoder(inverse: Boolean): RoutinePtr =
    val name = rp("utf8_order"); val input = sm("utf8_input")
    val h = pr("utf8_byte").known(1); val t = sm("utf8_tail")
    val lead = Literal(WordSpaceValue((0xF0 to 0xF4).map(n => target.fromDigits(Vector(n))).toSet))
    val ed = Singleton(constant(target.fromDigits(Vector(0xED))))
    def recurse(s: WordSpace[B]): WordSpace[B] = Call(name, Vector.empty, Vector(s))
    val head = Singleton(deref(h))
    val step =
      if !inverse then
        Union(guard(Intersection(head, lead), Wrap(recurse(mention(t)), WordPath.Concat(constant(target.fromDigits(Vector(0xED))), deref(h)))),
          guard(Subtraction(head, lead), Wrap(recurse(mention(t)), deref(h))))
      else
        val next = pr("utf8_next").known(1); val rest = sm("utf8_rest")
        val nextHead = Singleton(deref(next))
        val afterED = Iteration(mention(t), next, rest,
          Union(guard(Intersection(nextHead, lead), Wrap(recurse(mention(rest)), deref(next))),
            guard(Subtraction(nextHead, lead), Wrap(recurse(mention(rest)), WordPath.Concat(deref(h), deref(next))))))
        Union(guard(Intersection(head, ed), afterED),
          guard(Subtraction(head, ed), Wrap(recurse(mention(t)), deref(h))))
    definitions(name) = WordRoutine(name, Vector.empty, Vector(input),
      Union(Intersection(mention(input), epsilon), Iteration(mention(input), h, t, step)))
    name
  private lazy val toOrder: RoutinePtr = if encoding.utf8StringOrder then utf8OrderRecoder(false) else recoder(encoding, orderCode)
  private lazy val fromOrder: RoutinePtr = if encoding.utf8StringOrder then utf8OrderRecoder(true) else recoder(orderCode, encoding)
  private def orderedRange(src: WordSpace[B], start: Int, end: Int): WordSpace[B] =
    if encoding.preservesOrder then Range(src, start, end)
    else Call(fromOrder, Vector.empty, Vector(Range(Call(toOrder, Vector.empty, Vector(src)), start, end)))

  private def fold(src: WordSpace[B], initial: WordPath[B], acc: PathRef, h: PathRef,
                   t: SpaceMention, body: WordSpace[B], update: WordPath[B]): WordSpace[B] =
    if encoding.shape == WordCodeShape.Fixed(1) && encoding.preservesOrder then
      return Fold(src, initial, acc, h.known(1), t, body, update)
    val free = (WordSyntax.free(body) ++ WordSyntax.path(update)).without(acc, h).withoutSpace(t)
    val cps = free.paths.values.toVector.sortBy(_.s); val css = free.spaces.values.toVector.sortBy(_.s)
    if update == deref(acc) then
      val name = rp("fold_invariant"); val input = sm("fold_input")
      definitions(name) = WordRoutine(name, acc +: cps, input +: css,
        iterate(mention(input), h, t, body))
      return Call(name, initial +: cps.map(deref), src +: css.map(mention))
    val loop = rp("fold"); val input = sm("fold_input")
    val selected = pr("selected"); val ignored = sm("selected_tail")
    // Pick one full path, then decode only its first codeword. It has the
    // smallest head; decoding every remaining group on every step is quadratic.
    val selectedPath = orderedRange(Subtraction(mention(input), epsilon), 0, 1)
    val step = rp("fold_step"); val leftover = sm("fold_remaining")
    definitions(step) = WordRoutine(step, Vector(acc, h) ++ cps, Vector(t, leftover) ++ css,
      Union(body, Call(loop, update +: cps.map(deref), mention(leftover) +: css.map(mention))))
    val next = Call(step, Vector(deref(acc), deref(selected)) ++ cps.map(deref),
      Vector(Unwrap(mention(input), deref(selected)), Raffination(mention(input), Singleton(deref(selected)))) ++ css.map(mention))
    definitions(loop) = WordRoutine(loop, acc +: cps, input +: css,
      iterate(selectedPath, selected, ignored, next))
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
    case Iteration(src, h, t, b) =>
      val head = if h.s == "_" then pr("ignored_head") else encodedRef(h.known(1))
      val tail = if t.s == "_" then sm("ignored_tail") else t
      iterate(space(src), head, tail, space(b))
    case Fold(src, i, a, h, t, b, u) =>
      val acc = if a.s == "_" then pr("ignored_acc") else encodedRef(a)
      val head = if h.s == "_" then pr("ignored_head") else encodedRef(h.known(1))
      val tail = if t.s == "_" then sm("ignored_tail") else t
      fold(space(src), path(i), acc, head, tail, space(b), path(u))
    case Fixpoint(i, v, step) => Fixpoint(space(i), v, space(step))
    case Wrap(src, p) => Wrap(space(src), path(p))
    case Unwrap(src, p) => Unwrap(space(src), path(p))
    case TailsUnion(src) => tails(space(src))
    case TailsIntersection(src) if encoding.shape == WordCodeShape.Fixed(1) => TailsIntersection(space(src))
    case TailsIntersection(src) =>
      val input = sm("meet_input"); val all = sm("meet_tails")
      val outer = rp("meet"); val inner = rp("meet_groups")
      val h = pr("head"); val t = sm("tail")
      definitions(inner) = WordRoutine(inner, Vector.empty, Vector(input, all),
        Subtraction(mention(all), iterate(mention(input), h, t, Subtraction(mention(all), mention(t)))))
      definitions(outer) = WordRoutine(outer, Vector.empty, Vector(input),
        Call(inner, Vector.empty, Vector(mention(input), tails(mention(input)))))
      Call(outer, Vector.empty, Vector(space(src)))
    case PrefixClosure(src) => closure(space(src), "prefix")
    case SuffixClosure(src) => closure(space(src), "suffix")
    case TailsClosure(src) => closure(space(src), "tails")
    case GroundedPS(p, f) => GroundedPS(path(p), x => encoding.encodeSpace(f(encoding.decode(x))))
    case GroundedSS(s, f) => GroundedSS(space(s), x => encoding.encodeSpace(f(encoding.decodeSpace(x))))
    case Range(src, start, end) => orderedRange(space(src), start, end)
