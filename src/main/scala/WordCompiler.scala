package morkl

import scala.collection.mutable

private[morkl] object WordSyntax:
  case class Free(paths: Map[String, PathRef], spaces: Map[String, SpaceMention]):
    def ++(other: Free): Free = Free(paths ++ other.paths, spaces ++ other.spaces)
    def without(ps: PathRef*): Free = copy(paths = paths -- ps.map(_.s))
    def withoutSpace(ss: SpaceMention*): Free = copy(spaces = spaces -- ss.map(_.s))
  val empty: Free = Free(Map.empty, Map.empty)
  private case class Analysis(free: Free, names: Set[String], nodes: Long = 0):
    def ++(other: Analysis): Analysis = Analysis(free ++ other.free, names ++ other.names, nodes + other.nodes)
    def bind(ps: Vector[PathRef] = Vector.empty, ss: Vector[SpaceMention] = Vector.empty): Analysis =
      Analysis(free.without(ps.filterNot(_.s == "_")*).withoutSpace(ss.filterNot(_.s == "_")*), names ++ ps.map(_.s) ++ ss.map(_.s), nodes)
  private val zero = Analysis(empty, Set.empty)
  private def paths[P](p: WordPath[P]): Analysis =
    val result = p match
      case WordPath.Deref(r) => Analysis(Free(Map(r.s -> r), Map.empty), Set(r.s))
      case WordPath.Constant(_) => zero
      case WordPath.Concat(l, r) => paths(l) ++ paths(r)
      case WordPath.GroundedPP(p, _) => paths(p)
      case WordPath.GroundedSP(s, _) => analyze(s)
    result.copy(nodes = result.nodes + 1)
  /** One exhaustive, binder-aware walk supplies both capture analysis and all
    * reserved names. Adding a constructor cannot silently omit it from either.
    */
  private def analyze[P](s: WordSpace[P]): Analysis =
    import WordSpace.*
    val result = s match
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
    result.copy(nodes = result.nodes + 1)
  def free[P](s: WordSpace[P]): Free = analyze(s).free
  def path[P](p: WordPath[P]): Free = paths(p).free
  def names[P](s: WordSpace[P]): Set[String] = analyze(s).names
  def nodes[P](s: WordSpace[P]): Long = analyze(s).nodes

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
  private var decoderBudget = 512
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
  private def epsilon: WordSpace[B] = Literal(WordSpaceValue(Set(target.empty)))
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
    Iteration(test, PathRef("_"), SpaceMention("_"), body)
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
    // Small closed alphabets specialize the actual code trie, not a graph of
    // calls for every (state, depth). Leaves share one lifted continuation.
    if unrollBounded && code.shape == WordCodeShape.Fibonacci && code.orderedDigits.nonEmpty && code.orderedDigits.size <= 32 then
      val words = code.orderedDigits.map(code.code)
      def descend(input: WordSpace[B], candidates: Vector[Vector[Int]], depth: Int): WordSpace[B] =
        if candidates.isEmpty then Empty()
        else if candidates.head.size == depth then
          finish(constant(target.fromDigits(candidates.head)), input)
        else
          val digit = pr("trie_digit").known(1); val rest = sm("trie_rest")
          val branches = candidates.groupBy(_(depth)).toVector.sortBy(_._1).map { (b, group) =>
            val test = Intersection(Singleton(deref(digit)), Literal(WordSpaceValue(Set(target.fromDigits(Vector(b))))))
            guard(test, descend(mention(rest), group, depth + 1))
          }
          Iteration(input, digit, rest, union(branches))
      return descend(src, words, 0)
    code.shape match
      case WordCodeShape.Fixed(width) =>
        require(width > 0)
        def descend(s: WordSpace[B], prefix: WordPath[B], depth: Int): WordSpace[B] =
          if depth == width then finish(prefix, s)
          else
            val digit = pr("digit").known(1); val rest = sm("digits")
            Iteration(s, digit, rest, descend(mention(rest), WordPath.Concat(prefix, deref(digit)), depth + 1))
        descend(src, constant(target.empty), 0)
      case WordCodeShape.Terminated(b) if unrollBounded && limit.exists(_ <= 32) =>
        def descend(input: WordSpace[B], prefix: WordPath[B], depth: Int): WordSpace[B] =
          if depth >= limit.get then Empty()
          else
            val digit = pr("digit").known(1); val rest = sm("digits")
            val nextPrefix = WordPath.Concat(prefix, deref(digit))
            val head = Singleton(deref(digit))
            val terminal = Literal(WordSpaceValue(Set(target.fromDigits(Vector(b)))))
            Iteration(input, digit, rest, Union(
              guard(Intersection(head, terminal), finish(nextPrefix, mention(rest))),
              guard(Subtraction(head, terminal), descend(mention(rest), nextPrefix, depth + 1))))
        descend(src, constant(target.empty), 0)
      case shape =>
        val prefix = code.maxCodeLength.fold(pr("prefix")) { max => pr("prefix").bounded(max) }
        val remaining = sm("remaining")
        val bounded = unrollBounded && limit.exists(n => 2 * n <= decoderBudget)
        if bounded then decoderBudget -= 2 * limit.get
        val cache = mutable.Map.empty[(Boolean, Int), RoutinePtr]
        def scanner(previousOne: Boolean, depth: Int): RoutinePtr =
          val key = previousOne -> (if bounded then depth else -1)
          cache.getOrElseUpdate(key, {
            val name = rp("scan")
            cache(key) = name
            def arm(n: Int, keep: Boolean, state: Boolean, complete: Boolean): WordSpace[B] =
              val digit = pr("digit").known(1); val rest = sm("digits")
              val accepted = Literal(WordSpaceValue(Set(target.fromDigits(Vector(n)))))
              val selected = if keep then Restriction(mention(remaining), accepted) else Raffination(mention(remaining), accepted)
              val nextPrefix = WordPath.Concat(deref(prefix), deref(digit))
              val continuation =
                if complete then finish(nextPrefix, mention(rest))
                else if bounded && depth + 1 >= limit.get then Empty()
                else Call(scanner(state, depth + 1), nextPrefix +: cps.map(deref), mention(rest) +: css.map(mention))
              Iteration(selected, digit, rest, continuation)
            val branch = shape match
              case WordCodeShape.Fibonacci => Union(arm(0, true, false, false), arm(1, true, true, previousOne))
              case WordCodeShape.Terminated(b) => Union(arm(b, true, false, true), arm(b, false, false, false))
              case _ => throw IllegalStateException("fixed scanner dispatched as variable")
            definitions(name) = WordRoutine(name, prefix +: cps, remaining +: css, branch)
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
    while BigInt(alphabet.size).pow(width) < encoding.orderedDigits.size do width += 1
    val codes = encoding.orderedDigits.zipWithIndex.map { (digit, rank) =>
      digit -> Vector.tabulate(width)(i => alphabet((rank / BigInt(alphabet.size).pow(width - 1 - i).toInt) % alphabet.size))
    }.toMap
    val inverse = codes.iterator.map((id, word) => word -> id).toMap
    WordEncoding(encoding.source, target, codes.apply, inverse.apply,
      WordCodeShape.Fixed(width), Some(width), encoding.orderedDigits, true)

  private def recoder(from: WordEncoding[A, B], to: WordEncoding[A, B]): RoutinePtr =
    val name = rp("recode"); val input = sm("recode_input")
    val h = pr("head"); val t = sm("tail")
    // Balanced membership dispatch avoids scanning the entire alphabet for
    // every head, even for custom symbol injections with reversed ordering.
    def lookup(digits: Vector[Int]): WordSpace[B] =
      if digits.isEmpty then Empty()
      else if digits.size == 1 then Singleton(constant(target.fromDigits(to.code(digits.head))))
      else
        val (left, right) = digits.splitAt(digits.size / 2)
        val accepted = Literal(WordSpaceValue(left.map(d => target.fromDigits(from.code(d))).toSet))
        val head = Singleton(deref(h))
        Union(guard(Intersection(head, accepted), lookup(left)), guard(Subtraction(head, accepted), lookup(right)))
    val mapped = lookup(encoding.orderedDigits)
    val step = iterate(mention(input), h, t,
      Composition(mapped, Call(name, Vector.empty, Vector(mention(t)))), from)
    definitions(name) = WordRoutine(name, Vector.empty, Vector(input), Union(Intersection(mention(input), epsilon), step))
    name
  private lazy val toOrder: RoutinePtr = recoder(encoding, orderCode)
  private lazy val fromOrder: RoutinePtr = recoder(orderCode, encoding)
  private def orderedRange(src: WordSpace[B], start: Int, end: Int): WordSpace[B] =
    // These windows are independent of ordering; retain evaluation of src.
    if end == 0 && (start == 0 || start == 1) then return src
    if (start > 0 && end > 0 || start < 0 && end < 0) && end <= start then return Range(src, start, end)
    src match
      case Literal(v) if start == 0 && end >= v.paths.size => return src
      case _ => ()
    require(encoding.preservesOrder || encoding.orderedDigits.nonEmpty, "Range requires a frozen orderedVocabulary for literal interner IDs")
    if encoding.preservesOrder then Range(src, start, end)
    else Call(fromOrder, Vector.empty, Vector(Range(Call(toOrder, Vector.empty, Vector(src)), start, end)))

  /** Fold has already extracted complete heads. A closed dictionary can bind
    * one by balanced whole-word membership, without rescanning every digit.
    * The source and user continuation are each evaluated/stored once.
    */
  private def bindHead(src: WordSpace[B], h: PathRef, t: SpaceMention, body: WordSpace[B],
                       from: WordEncoding[A, B] = encoding): WordSpace[B] =
    if encoding.orderedDigits.isEmpty || ((from eq encoding) && encoding.shape != WordCodeShape.Fibonacci) then
      return iterate(src, h, t, body)
    val free = WordSyntax.free(body).without(h).withoutSpace(t)
    val cps = free.paths.values.toVector.sortBy(_.s); val css = free.spaces.values.toVector.sortBy(_.s)
    val leaf = rp("head_body"); val input = sm("selected_head"); val entry = rp("bind_head")
    definitions(leaf) = WordRoutine(leaf, h +: cps, t +: css, body)
    def lookup(ds: Vector[Int]): WordSpace[B] =
      if ds.size == 1 then
        Call(leaf, constant(target.fromDigits(encoding.code(ds.head))) +: cps.map(deref), epsilon +: css.map(mention))
      else
        val (left, right) = ds.splitAt(ds.size / 2)
        val accepted = Literal(WordSpaceValue(left.map(d => target.fromDigits(from.code(d))).toSet))
        Union(guard(Intersection(mention(input), accepted), lookup(left)),
          guard(Subtraction(mention(input), accepted), lookup(right)))
    definitions(entry) = WordRoutine(entry, cps, input +: css,
      guard(mention(input), lookup(encoding.orderedDigits)))
    Call(entry, cps.map(deref), src +: css.map(mention))

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
    require(encoding.preservesOrder || encoding.orderedDigits.nonEmpty, "Fold requires a frozen orderedVocabulary for literal interner IDs")
    val loop = rp("fold"); val input = sm("fold_input"); val heads = sm("fold_heads")
    val selected = pr("selected"); val ignored = sm("selected_tail")
    val step = rp("fold_step")
    definitions(step) = WordRoutine(step, Vector(acc, h) ++ cps, Vector(input, heads, t) ++ css,
      Union(body, Call(loop, update +: cps.map(deref),
        Vector(mention(input), Subtraction(mention(heads),
          if encoding.preservesOrder then Singleton(deref(h)) else Range(mention(heads), 0, 1))) ++ css.map(mention))))
    val next = Call(step, Vector(deref(acc), deref(selected)) ++ cps.map(deref),
      Vector(mention(input), mention(heads), Unwrap(mention(input), deref(selected))) ++ css.map(mention))
    definitions(loop) = WordRoutine(loop, acc +: cps, Vector(input, heads) ++ css,
      bindHead(Range(mention(heads), 0, 1), selected, ignored, next,
        if encoding.preservesOrder then encoding else orderCode))
    val prepare = rp("fold_prepare"); val head = pr("head"); val tail = sm("tail")
    val allHeads = iterate(mention(input), head, tail, Singleton(deref(head)))
    definitions(prepare) = WordRoutine(prepare, acc +: cps, input +: css,
      Call(loop, deref(acc) +: cps.map(deref), Vector(mention(input), if encoding.preservesOrder then allHeads else Call(toOrder, Vector.empty, Vector(allHeads))) ++ css.map(mention)))
    Call(prepare, initial +: cps.map(deref), src +: css.map(mention))

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
