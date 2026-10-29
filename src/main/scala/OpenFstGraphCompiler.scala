package morkl

import scala.collection.mutable

/** Native control flow is emitted from optimized RecursiveOpGraph coordinates.
  * Graph positions become C++ locals; nested graph scopes become lexical loop
  * bodies. There is no generated node dispatcher, value stack or environment.
  */
private[morkl] final class OpenFstGraphCompiler[V, L <: DigitLanguage[V]](
    val dialect: L, labels: OpenFstBackend.Labels[V]):
  private type Pos = (Int, Int)
  private val callbacks = mutable.ArrayBuffer.empty[Vector[Vector[Int]] => Vector[Vector[Int]]]
  private val constants = mutable.LinkedHashMap.empty[String, String]
  private val declarations = mutable.ArrayBuffer.empty[String]
  private val definitions = mutable.ArrayBuffer.empty[String]
  private val needed = mutable.LinkedHashSet.empty[String]
  private val graphs = mutable.LinkedHashMap.empty[String, RecursiveOpGraph]
  private var suppliedGraphs = Map.empty[String, RecursiveOpGraph]
  private val signatures = mutable.LinkedHashMap.empty[String, (Vector[PathRef], Vector[SpaceMention])]
  private val names = mutable.LinkedHashMap.empty[String, String]
  private val demands = java.util.IdentityHashMap[RecursiveOpGraph, mutable.BitSet]()
  private var module = Map.empty[RoutinePtr, Routine]
  private var serial = 0
  private var entryName = ""
  private var emittingRoutine = ""
  private var reservedRoutines = Set.empty[RoutinePtr]
  private var selector: OpenFstHeadSelection[V,dialect.type] = null
  private val intrinsicCalls = mutable.LinkedHashMap.empty[RoutinePtr,(String,String)]
  private val converted = java.util.IdentityHashMap[dialect.Space,Space]()

  private def graphPath(v: V): PathValue =
    if labels.symbolic then v.asInstanceOf[PathValue]
    else PathValue(dialect.paths.digits(v).map(d => PathItem(f"#digit:$d%03d")).toList)
  private def nativePath(v: PathValue): V =
    if labels.symbolic then v.asInstanceOf[V]
    else dialect.paths.fromDigits(v.items.map { p =>
      require(p.show.startsWith("#digit:"), s"internal graph marker escaped native lowering: ${p.show}")
      p.show.substring(7).toInt
    })
  private def convertPath(p: dialect.Path): Path = p match
    case dialect.Path.Deref(r) => Path.Deref(r)
    case dialect.Path.Constant(v) => Path.Constant(graphPath(v))
    case dialect.Path.Concat(a,b) => Path.Concat(convertPath(a),convertPath(b))
    case dialect.Path.GroundedPP(a,f) => Path.GroundedPP(convertPath(a),v => graphPath(f(nativePath(v))))
    case dialect.Path.GroundedSP(a,f) => Path.GroundedSP(convertSpace(a),v => graphPath(f(dialect.SpaceValue(v.paths.map(nativePath)))))
  private def convertSpace(s: dialect.Space): Space =
    val previous = converted.get(s)
    if previous != null then previous
    else
      val result = Option(selector).flatMap(_.plan(s)) match
        case Some(plan) =>
          val codes = plan.codes
          val fibonacci = codes.nonEmpty && codes.zipWithIndex.forall { (code,index) =>
            scala.util.Try(FibonacciCode.decode(code.map(_-1)) == Vector(BigInt(index+1))).getOrElse(false)
          }
          val operation = if fibonacci then "FirstFibonacci" -> s"${codes.size},${codes.map(_.size).max},${plan.prefix}"
            else if codes == codes.sorted then "FirstMatching" -> s"${plan.prefix}|${text(codes)}"
            else "FirstListed" -> s"${plan.prefix}|${text(codes)}"
          val name = Iterator.iterate(s"__openfst_proven_selection_${intrinsicCalls.size}")(_+"_")
            .map(RoutinePtr(_)).find(p => !reservedRoutines(p) && !intrinsicCalls.contains(p)).get
          intrinsicCalls(name) = operation
          // A proven native operation is protected across source rewriting by
          // ordinary function abstraction, then lowered to an operation-graph
          // node. Emission still consumes only the optimized graph.
          Space.Call(name,Vector.empty,Vector(convertSpace(plan.input)))
        case None => plainSpace(s)
      converted.put(s,result);result

  private def plainSpace(s: dialect.Space): Space =
    import dialect.{Space as S}
    s match
      case S.Empty => Space.Empty
      case S.Call(r,ps,ss) => Space.Call(r,ps.map(convertPath),ss.map(convertSpace))
      case S.Mention(r) => Space.Mention(r)
      case S.Singleton(p) => Space.Singleton(convertPath(p))
      case S.Literal(v) => Space.Literal(SpaceValue(v.paths.map(graphPath)))
      case S.Union(a,b) => Space.Union(convertSpace(a),convertSpace(b))
      case S.Intersection(a,b) => Space.Intersection(convertSpace(a),convertSpace(b))
      case S.Subtraction(a,b) => Space.Subtraction(convertSpace(a),convertSpace(b))
      case S.Restriction(a,b) => Space.Restriction(convertSpace(a),convertSpace(b))
      case S.Raffination(a,b) => Space.Raffination(convertSpace(a),convertSpace(b))
      case S.Composition(a,b) => Space.Composition(convertSpace(a),convertSpace(b))
      case S.Iteration(a,h,t,b) => Space.Iteration(convertSpace(a),h,t,convertSpace(b))
      case S.Fold(a,i,acc,h,t,b,u) => Space.Fold(convertSpace(a),convertPath(i),acc,h,t,convertSpace(b),convertPath(u))
      case S.Fixpoint(a,v,b) => Space.Fixpoint(convertSpace(a),v,convertSpace(b))
      case S.Wrap(a,p) => Space.Wrap(convertSpace(a),convertPath(p))
      case S.Unwrap(a,p) => Space.Unwrap(convertSpace(a),convertPath(p))
      case S.TailsUnion(a) => Space.TailsUnion(convertSpace(a))
      case S.TailsIntersection(a) => Space.TailsIntersection(convertSpace(a))
      case S.PrefixClosure(a) => Space.PrefixClosure(convertSpace(a))
      case S.SuffixClosure(a) => Space.SuffixClosure(convertSpace(a))
      case S.TailsClosure(a) => Space.TailsClosure(convertSpace(a))
      case S.Range(a,lo,hi) => Space.Range(convertSpace(a),lo,hi)
      case S.GroundedPS(p,f) => Space.GroundedPS(convertPath(p),v => SpaceValue(f(nativePath(v)).paths.map(graphPath)))
      case S.GroundedSS(a,f) => Space.GroundedSS(convertSpace(a),v => SpaceValue(f(dialect.SpaceValue(v.paths.map(nativePath))).paths.map(graphPath)))

  private def register(f: Vector[Vector[Int]] => Vector[Vector[Int]]): String =
    val id = callbacks.size; callbacks += f; id.toString
  private def encoded(p: PathValue): Vector[Int] = labels.encode(nativePath(p))
  private def decode(p: Vector[Int]): PathValue = graphPath(labels.decode(p))
  private def text(paths: Vector[Vector[Int]]): String = s"${paths.size} " + paths.map(p => s"${p.size} ${p.mkString(" ")}").mkString(" ")
  private def constant(kind: String, value: String): String =
    constants.getOrElseUpdate(s"$kind:$value", {
      val name = s"constant_${constants.size}"
      declarations += s"static const $kind $name = $value;"
      name
    })
  private def pathConstant(p: PathValue): String = constant("Path",encoded(p).mkString("Path{",",","}"))
  private def literalConstant(v: SpaceValue): String =
    constant("Space",s"""literal_text("${text(v.paths.toVector.map(encoded).sorted)}")""")

  private def hooks: NativeGraphLowering = new NativeGraphLowering:
    private def epsilon(s: Space): Boolean = s match
      case Space.Singleton(Path.Constant(p)) => p.items.isEmpty
      case Space.Literal(v) => v.paths == Set(PathValue(Nil))
      case _ => false
    def pp(f: PathValue => PathValue): String = register(ps => Vector(encoded(f(decode(ps.head)))))
    def sp(f: SpaceValue => PathValue): String = register(ps => Vector(encoded(f(SpaceValue(ps.map(decode).toSet)))))
    def ps(f: PathValue => SpaceValue): String = register(ps => f(decode(ps.head)).paths.toVector.map(encoded))
    def ss(f: SpaceValue => SpaceValue): String = register(ps => f(SpaceValue(ps.map(decode).toSet)).paths.toVector.map(encoded))
    override def space(s: Space): Option[(String,String,Vector[Space])] = s match
      case Space.Call(name,Vector(),Vector(input)) if intrinsicCalls.contains(name) =>
        val (operation,payload) = intrinsicCalls(name)
        Some((operation,payload,Vector(input)))
      case Space.Iteration(a,h,_,Space.Singleton(Path.Deref(r))) if h.s != "_" && r == h => Some(("NativeHead","",Vector(a)))
      case Space.Iteration(a,_,t,Space.Mention(r)) if t.s != "_" && r == t => Some(("TailsUnion","",Vector(a)))
      case Space.Iteration(a,_,_,body) if epsilon(body) => Some(("NativeHasHead","",Vector(a)))
      case Space.Union(Space.Intersection(value,unit),Space.Iteration(same,_,_,body))
          if value == same && epsilon(unit) && epsilon(body) && (value match
            case Space.Mention(_) | Space.TailsUnion(Space.Mention(_)) => true
            case _ => false) => Some(("NativeNonempty","",Vector(value)))
      case _ => None

  private def oversizedProduct(s: Space): Boolean =
    // An arithmetic safety check, not a solver query or an evaluation. The
    // shared optimizer may fold closed expressions; never ask it to enumerate
    // an explicitly exponential constant product (e.g. the 2^80 range tests).
    val cache = java.util.IdentityHashMap[Space,Option[Long]]()
    val limit = 250001L
    def upper(s: Space): Option[Long] =
      val previous = cache.get(s)
      if previous != null then previous
      else
        val result: Option[Long] = s match
          case Space.Empty => Some(0L)
          case Space.Literal(v) => Some(v.paths.size.toLong.min(limit))
          case Space.Singleton(_) => Some(1L)
          case Space.Union(a,b) => for x <- upper(a);y <- upper(b) yield (x+y).min(limit)
          case Space.Composition(a,b) => for x <- upper(a);y <- upper(b) yield (x*y).min(limit)
          case Space.Intersection(a,b) => (upper(a).toVector ++ upper(b).toVector).minOption
          case Space.Wrap(a,_) => upper(a)
          case Space.Unwrap(a,_) => upper(a)
          case Space.Subtraction(a,_) => upper(a)
          case Space.Restriction(a,_) => upper(a)
          case Space.Raffination(a,_) => upper(a)
          case Space.Range(a,_,_) => upper(a)
          case Space.TailsUnion(a) => upper(a)
          case _ => None
        cache.put(s,result);result
    var tooLarge = false
    subs(s)(spost = { case c @ Space.Composition(_,_) =>
      if upper(c).exists(_ >= limit) then tooLarge = true
      c
    })
    tooLarge
  private def optimized(r: Routine): RecursiveOpGraph = OpenFstAudit.withoutCapture {
    RadixTranslationAudit.withoutCapture { optimizeRoutine(r) }
  }
  private def optimizeRoutine(r: Routine): RecursiveOpGraph =
    val ctx = module.lift.unlift
    val hasGrounded = Supercompiler.backendUnsupported(r.body).nonEmpty ||
      module.valuesIterator.exists(r => Supercompiler.backendUnsupported(r.body).nonEmpty)
    lazy val structuralLowering =
      val all = ctx.orElse[RoutinePtr,Routine] { case p if p == r.name => r }
      val noSelf = module.filter((p,_) => p != r.name).lift.unlift
      r.copy(body = Lower.inline(using noSelf)(Supercompiler.lowerFixpointCalls(r.body,all)))
    val lowered = if !hasGrounded && !oversizedProduct(r.body) then
      try Supercompiler.compile(r,ctx=ctx,buildGraph=false).routine
      catch
        // Source specialization is optional. A large expanded translation must
        // still compile when that optimization exhausts its own time budget.
        case e: RuntimeException if e.getMessage.startsWith("compile time cap ") => structuralLowering
    else structuralLowering
    val graphBudget = java.lang.Long.getLong("morkl.openfst.graphCompileMillis",300000L).longValue()
    optimizeTimed(transpile(lowered,native=Some(hooks)),deadline=CompileBudget(graphBudget).start()).graph

  private def signature(name: String): String =
    val (ps,ss) = signatures(name)
    val binding = if name == entryName then "*" else "&"
    (Vector("Context& c") ++ ps.indices.map(i => s"const Path$binding p$i") ++ ss.indices.map(i => s"const Space$binding s$i")).mkString(", ")
  private def function(name: String): String = names.getOrElseUpdate(name,s"routine_${names.size}")
  private def owner(g: RecursiveOpGraph, level: Int): RecursiveOpGraph =
    if g.level == level then g else owner(g.parent.get,level)
  private def output(g: RecursiveOpGraph): Pos = g.level -> (g.nodes.size - 1)
  private def alias(g: RecursiveOpGraph, pos: Pos): Pos = g.lookup(pos) match
    case Left(Node("Alias",_,_,Vector(input))) => alias(g,input)
    case _ => pos
  /** Fold's executor framing is removed at compile time. No tag symbols or
    * packed update spaces are ever emitted into the bit/byte automata.
    */
  private def foldOutputs(g: RecursiveOpGraph): (Pos,Pos) =
    def wrapped(pos: Pos, tag: PathValue): Option[Pos] = g.lookup(alias(g,pos)) match
      case Left(Node("Wrap",_,_,Vector(value,prefix))) => g.lookup(alias(g,prefix)) match
        case Left(Node("Constant",encoded,_,_)) if g.pathValue(encoded) == tag => Some(value)
        case _ => None
      case _ => None
    val root = alias(g,output(g))
    val parts = g.lookup(root) match
      case Left(Node("Union",_,_,Vector(a,b))) => Vector(a,b)
      case _ => throw IllegalArgumentException("optimized Fold graph lost body/update framing")
    val body = parts.flatMap(wrapped(_,GraphFoldTags.Body)).head
    val updateSpace = parts.flatMap(wrapped(_,GraphFoldTags.Update)).head
    val update = g.lookup(alias(g,updateSpace)) match
      case Left(Node("Singleton",_,_,Vector(p))) => p
      case other => throw IllegalArgumentException(s"Fold update is not a singleton: $other")
    body -> update
  private def mark(scope: RecursiveOpGraph, pos: Pos): Unit =
    val g = owner(scope,pos._1)
    var wanted = demands.get(g)
    if wanted == null then
      wanted = mutable.BitSet.empty
      demands.put(g,wanted)
    if wanted.add(pos._2) then g.nodes(pos._2) match
      case Left(node) => node.inputs.foreach(mark(g,_))
      case Right(sub) =>
        sub.root.inputs.foreach(mark(g,_))
        if sub.root.operation == "Fold" then
          val (body,update) = foldOutputs(sub);mark(sub,body);mark(sub,update)
        else mark(sub,output(sub))

  private def dependencies(g: RecursiveOpGraph, index: Int): Set[Pos] = g.nodes(index) match
    case Left(n) => n.inputs.toSet
    case Right(child) =>
      def captures(s: RecursiveOpGraph): Set[Pos] =
        val wanted = Option(demands.get(s)).getOrElse(mutable.BitSet.empty)
        s.root.inputs.toSet ++ wanted.toSet.flatMap { i => s.nodes(i) match
          case Left(n) => n.inputs.toSet
          case Right(nested) => captures(nested)
        }
      captures(child).filter(_._1 <= g.level)

  /** Place potentially recursive calls and opaque callbacks inside the guard
    * that demands them. Pure automaton compositions stay lazy and need no
    * emptiness probe. Exclusive regions become ordinary lexical C++ blocks.
    */
  private def deferredRegions(g: RecursiveOpGraph, wanted: Set[Int]): Map[Int,Set[Int]] =
    val deps = wanted.map(i => i -> dependencies(g,i)).toMap
    def effect(index: Int, seen: Set[Int] = Set.empty): Boolean =
      if seen(index) then false
      else g.nodes(index) match
        case Left(n) => n.operation == "Call" || n.operation.startsWith("Grounded") ||
          n.inputs.exists(p => p._1 == g.level && effect(p._2,seen+index))
        case Right(child) =>
          def opaque(s: RecursiveOpGraph): Boolean = s.nodes.exists {
            case Left(n) => n.operation == "Call" || n.operation.startsWith("Grounded")
            case Right(nested) => opaque(nested)
          }
          opaque(child)
    val result = mutable.Map.empty[Int,Set[Int]]
    for i <- wanted.toVector.sorted do g.nodes(i) match
      case Left(Node("Composition",_,_,Vector(_,right))) if right._1 == g.level && effect(right._2) =>
        val closure = mutable.Set.empty[Int]
        def collect(index: Int): Unit =
          if wanted(index) && !closure(index) then g.nodes(index) match
            case Left(Node("ExtractPathRef" | "ExtractSpaceMention" | "Literal" | "Constant",_,_,_)) => ()
            case _ =>
              closure += index
              deps(index).filter(_._1 == g.level).foreach(p => collect(p._2))
        collect(right._2)
        val guarded = g.nodes(i).left.toOption.get.inputs.head
        val guardDependencies = mutable.Set.empty[Int]
        def protect(index: Int): Unit =
          if guardDependencies.add(index) then
            deps.getOrElse(index,Set.empty).filter(_._1 == g.level).foreach(p => protect(p._2))
        if guarded._1 == g.level then protect(guarded._2)
        val current = closure.toSet -- guardDependencies
        if current(right._2) then result(i) = current
      case _ => ()
    result.toMap

  private def emitBlock(g: RecursiveOpGraph, inherited: Map[Pos,String], args: Map[Int,String],
                        params: Map[(String,String),String], out: StringBuilder, indent: String,
                        selected: Option[Set[Int]] = None): Map[Pos,String] =
    val bindings = mutable.Map.from(inherited)
    val id = serial; serial += 1
    def value(p: Pos): String = bindings.getOrElse(p, {
      // A shared graph node may be demanded by multiple guarded regions. Clone
      // its native statements at a use site unless an existing definition
      // already dominates that use. This needs no runtime value/cache table.
      val scope = owner(g,p._1)
      val selection = mutable.Set.empty[Int]
      def collect(index: Int): Unit =
        if !bindings.contains(scope.level -> index) && selection.add(index) then
          dependencies(scope,index).filter(_._1 == scope.level).foreach(q => collect(q._2))
      collect(p._2)
      require(selection.nonEmpty,s"unbound native graph coordinate $p in ${g.show}")
      val computed = emitBlock(scope,bindings.toMap,if scope eq g then args else Map.empty,params,out,indent,Some(selection.toSet))
      bindings ++= computed
      bindings.getOrElse(p,throw IllegalStateException(s"unable to place native graph coordinate $p"))
    })
    def line(code: String): Unit = out.append(indent).append(code).append('\n')
    val wanted = selected.getOrElse(Option(demands.get(g)).map(_.toSet).getOrElse(Set.empty))
    val regions = deferredRegions(g,wanted)
    val deferred = regions.toVector.sortBy(_._1).flatMap((owner,indices) => indices.map(_ -> owner)).toMap
    for (entry,index) <- g.nodes.zipWithIndex if wanted(index) && !deferred.contains(index) && !bindings.contains(g.level -> index) do
      val pos = g.level -> index
      val name = s"v${id}_$index"
      entry match
        case Left(n @ Node("Composition",_,_,Vector(left,right))) if regions.contains(index) =>
          line(s"Space $name;")
          line(s"if (!${value(left)}.empty()) {")
          val inner = emitBlock(g,bindings.toMap,args,params,out,indent+"  ",Some(regions(index)))
          line(s"  $name = compose(${value(left)},${inner(right)});")
          line("}")
          bindings(pos) = name
        case Left(n) =>
          val ins = n.inputs.map(value)
          val code = n.operation match
            case "ExtractPathRef" | "ExtractSpaceMention" =>
              bindings(pos) = args.getOrElse(index,params(n.kind -> n.constant)); ""
            case "Alias" => bindings(pos) = ins.head; ""
            case "Constant" => bindings(pos) = pathConstant(g.pathValue(n.constant)); ""
            case "Literal" => bindings(pos) = literalConstant(g.literalValue(n.constant)); ""
            case "Empty" => "Space{}"
            case "Concat" => s"concat_path(${ins(0)},${ins(1)})"
            case "Singleton" => s"singleton(${ins.head})"
            case "Call" =>
              needed += n.constant
              val call = s"${function(n.constant)}(c${ins.map(", "+_).mkString})"
              // Preserve the evaluator's stationary positive self-union rule.
              // This is a graph-level comparison of the next arguments with
              // the current typed parameters, not a recursive-call memo table.
              val stationary = g.level == 0 && n.constant == emittingRoutine && (g.lookup(alias(g,output(g))) match
                case Left(Node("Union",_,_,Vector(_,right))) => alias(g,right) == pos
                case _ => false)
              if stationary then
                val (ps,ss) = signatures(n.constant)
                val checks = ps.zipWithIndex.map((p,i) => s"(${ins(i)} == ${params("path" -> p.s)})") ++
                  ss.zipWithIndex.map((s,i) => s"equal(${ins(ps.size+i)},${params("space" -> s.s)})")
                s"((${if checks.isEmpty then "true" else checks.mkString(" && ")}) ? Space{} : $call)"
              else call
            case "Union" => s"unite(${ins(0)},${ins(1)})"
            case "Intersection" => s"intersect(${ins(0)},${ins(1)})"
            case "Subtraction" => s"subtract(${ins(0)},${ins(1)})"
            case "Restriction" => s"restrict_to(${ins(0)},${ins(1)})"
            case "Raffination" => s"raffine(${ins(0)},${ins(1)})"
            case "Composition" => s"compose(${ins(0)},${ins(1)})"
            case "Wrap" => s"wrap(${ins(0)},${ins(1)})"
            case "Unwrap" => s"unwrap(${ins(0)},${ins(1)})"
            case "TailsUnion" => s"tails_union(${ins.head})"
            case "TailsIntersection" => s"tails_intersection(${ins.head})"
            case "PrefixClosure" => s"prefix_closure(${ins.head})"
            case "SuffixClosure" => s"suffix_closure(${ins.head},false)"
            case "TailsClosure" => s"suffix_closure(${ins.head},true)"
            case "NativeHead" => s"head_space(${ins.head},${dialect.paths.alphabetSize})"
            case "NativeHasHead" => s"(has_head(${ins.head}) ? ${constant("Space","singleton(Path{})")} : Space{})"
            case "NativeNonempty" => s"(${ins.head}.empty() ? Space{} : ${constant("Space","singleton(Path{})")})"
            case "Range" =>
              val (lo,hi) = g.rangeBounds(n.constant);s"range(c,${ins.head},$lo,$hi)"
            case "GroundedPP" => s"c.callback_path(${n.constant},singleton(${ins.head}))"
            case "GroundedSP" => s"c.callback_path(${n.constant},${ins.head})"
            case "GroundedPS" => s"c.callback_space(${n.constant},singleton(${ins.head}))"
            case "GroundedSS" => s"c.callback_space(${n.constant},${ins.head})"
            case "FirstFibonacci" => s"first_fibonacci(${ins.head},${n.constant})"
            case "FirstMatching" | "FirstListed" =>
              val Array(prefix,encoded) = n.constant.split("\\|",2): @unchecked
              val kind = if n.operation == "FirstMatching" then "Space" else "std::vector<Path>"
              val read = if n.operation == "FirstMatching" then "literal_text" else "paths_text"
              val allowed = constant(kind,s"""$read("$encoded")""")
              val call = if n.operation == "FirstMatching" then "first_matching" else "first_listed"
              s"$call(${ins.head},$allowed,$prefix)"
            case other => throw IllegalArgumentException(s"unsupported native graph operation: $other")
          if code.nonEmpty then
            line(s"const ${if n.kind == "path" then "Path" else "Space"} $name = $code;")
            bindings(pos) = name
        case Right(sub) =>
          val input = sub.root.inputs.map(value)
          sub.root.operation match
            case "Iteration" | "Fold" =>
              val fold = sub.root.operation == "Fold"
              val bits = dialect.paths.alphabetSize == 2
              if bits then line(s"Space ${name}_result;")
              else
                line(s"const auto ${name}_branches = branches(c,${input.head});")
                line(s"std::vector<Space> ${name}_outputs;")
                line(s"${name}_outputs.reserve(${name}_branches.size());")
              if fold then line(s"Path ${name}_acc = ${input(1)};")
              if bits then
                line(s"for (int ${name}_label = 1; ${name}_label <= 2; ++${name}_label) {")
                line(s"  const Space ${name}_tail = child(${input.head},${name}_label);")
                line(s"  if (${name}_tail.empty()) continue;")
                line(s"  const Path ${name}_head{${name}_label};")
              else
                line(s"for (const auto& ${name}_branch : ${name}_branches) {")
                line(s"  const Path ${name}_head{${name}_branch.first};")
                line(s"  const Space& ${name}_tail = ${name}_branch.second;")
              val bound = if fold then Map(0 -> s"${name}_acc",1 -> s"${name}_head",2 -> s"${name}_tail")
                else Map(0 -> s"${name}_head",1 -> s"${name}_tail")
              val inner = emitBlock(sub,bindings.toMap,bound,params,out,indent+"  ")
              if fold then
                val (body,update) = foldOutputs(sub)
                if bits then line(s"  ${name}_result = unite(${name}_result,${inner(body)});")
                else line(s"  ${name}_outputs.push_back(${inner(body)});")
                line(s"  ${name}_acc = ${inner(update)};")
              else if bits then line(s"  ${name}_result = unite(${name}_result,${inner(output(sub))});")
              else line(s"  ${name}_outputs.push_back(${inner(output(sub))});")
              line("}")
              if bits then line(s"const Space $name = std::move(${name}_result);")
              else line(s"const Space $name = join_outputs(std::move(${name}_outputs));")
            case "Fixpoint" =>
              line(s"Space $name = ${input.head};")
              line("for (;;) {")
              val inner = emitBlock(sub,bindings.toMap,Map(0 -> name),params,out,indent+"  ")
              line(s"  Space ${name}_next = unite($name,${inner(output(sub))});")
              line(s"  if (equal(${name}_next,$name)) break;")
              line(s"  $name = std::move(${name}_next);")
              line("}")
            case other => throw IllegalArgumentException(s"unsupported native subgraph: $other")
          bindings(pos) = name
    bindings.toMap

  private def emit(name: String, graph: RecursiveOpGraph): Unit =
    emittingRoutine = name
    graphs(name) = graph
    mark(graph,output(graph))
    val (ps,ss) = signatures(name)
    val params = ps.zipWithIndex.map((p,i) => ("path" -> p.s) -> (if name == entryName then s"bound_path(p$i)" else s"p$i")).toMap ++
      ss.zipWithIndex.map((s,i) => ("space" -> s.s) -> (if name == entryName then s"bound_space(s$i)" else s"s$i")).toMap
    val code = StringBuilder(s"static Space ${function(name)}(${signature(name)}) {\n")
    val values = emitBlock(graph,Map.empty,Map.empty,params,code,"  ")
    code.append(s"  return ${values(output(graph))};\n}\n")
    definitions += code.toString

  def compile(entry: dialect.Space, routines: Map[RoutinePtr,dialect.Routine]): OpenFstBackend.Compiled[V] =
    val start = System.nanoTime()
    reservedRoutines = routines.keySet
    selector = new OpenFstHeadSelection[V,dialect.type](dialect,labels.encode,routines)
    module = routines.view.mapValues { r =>
      val body = convertSpace(r.body)
      // The language's stationary self-union convention must be resolved
      // before ordinary source rewrites can erase its enclosing Union.
      val lowered = body match
        case Space.Union(left,Space.Call(name,ps,ss)) if name == r.name &&
            ps == r.refs.map(Path.Deref(_)) && ss == r.mentions.map(Space.Mention(_)) => left
        case _ => body
      Routine(r.name,r.refs,r.mentions,lowered)
    }.toMap
    val body = convertSpace(entry)
    val rootName = Iterator.iterate("__openfst_entry")(_+"_").find(n => !module.contains(RoutinePtr(n))).get
    entryName = rootName
    val allPs = Matching.freeRefsV(body);val allSs = Matching.freeMentionsV(body)
    val wrapper = Routine(RoutinePtr(rootName),allPs,allSs,body)
    val rootGraph = optimized(wrapper)
    mark(rootGraph,output(rootGraph))
    val live = Option(demands.get(rootGraph)).getOrElse(mutable.BitSet.empty).toVector.flatMap(i => rootGraph.nodes(i).left.toOption)
      .collect {case Node(op,name,kind,_) if op.startsWith("Extract") => kind -> name}.toSet
    val ps = allPs.filter(p => live("path" -> p.s));val ss = allSs.filter(s => live("space" -> s.s))
    signatures(rootName) = ps -> ss
    module.foreach((ptr,r) => signatures(ptr.s) = r.refs -> r.mentions)
    emit(rootName,rootGraph)
    finish(rootName,ps,ss,start)

  /** Consume an already optimized executor graph without reconstructing an
    * expression tree. Called routines are compiled from their graph index too.
    */
  def compileGraph(top: RecursiveOpGraph, index: Map[String,RecursiveOpGraph]): OpenFstBackend.Compiled[V] =
    require(labels.symbolic,"executor graphs use the symbolic alphabet")
    val start = System.nanoTime()
    suppliedGraphs = index
    def inputs(g: RecursiveOpGraph): (Vector[PathRef],Vector[SpaceMention]) =
      val nodes = g.nodes.toVector.flatMap(_.left.toOption)
      nodes.collect {case Node("ExtractPathRef",name,_,_) => PathRef(name)} ->
        nodes.collect {case Node("ExtractSpaceMention",name,_,_) => SpaceMention(name)}
    val rootName = Iterator.iterate("__openfst_graph_entry")(_+"_").find(n => !index.contains(n)).get
    entryName = rootName
    index.foreach((name,g) => signatures(name) = inputs(g))
    val (ps,ss) = inputs(top)
    signatures(rootName) = ps -> ss
    emit(rootName,top)
    finish(rootName,ps,ss,start)

  private def finish(rootName: String, ps: Vector[PathRef], ss: Vector[SpaceMention], start: Long): OpenFstBackend.Compiled[V] =
    val emitted = mutable.Set(rootName)
    while needed.exists(n => !emitted(n)) do
      val n = needed.find(n => !emitted(n)).get
      val graph = suppliedGraphs.getOrElse(n, {
        val r = module.getOrElse(RoutinePtr(n),throw IllegalArgumentException(s"undefined routine: $n"))
        optimized(r)
      })
      emitted += n;emit(n,graph)
    val pathIds = ps.zipWithIndex.toMap
    val spaceIds = ss.zipWithIndex.toMap
    val entryCode = s"static Space entry(Context& c,const Env& e) { return ${function(rootName)}(c" +
      ps.indices.map(i => s",path_arg(e,$i)").mkString + ss.indices.map(i => s",space_arg(e,$i)").mkString + "); }"
    val prototypes = emitted.toVector.sorted.map(n => s"static Space ${function(n)}(${signature(n)});")
    val code = (declarations ++ prototypes ++ definitions :+ entryCode).mkString("\n")
    val dump = graphs.iterator.map((n,g) => s"routine $n\n${g.show}").mkString("\n\n")
    def liveNodes(g: RecursiveOpGraph): Int =
      val live = Option(demands.get(g)).getOrElse(mutable.BitSet.empty)
      live.size + g.nodes.zipWithIndex.collect {case (Right(child),i) if live(i) => liveNodes(child)}.sum
    val count = graphs.valuesIterator.map(liveNodes).sum
    val folded = graphs(rootName).nodes.lastOption.exists {
      case Left(Node("Literal" | "Empty",_,_,_)) => count == 1
      case _ => false
    }
    new OpenFstBackend.Compiled(code,"entry",pathIds,spaceIds,labels,callbacks.toVector,
      graphDescription=dump,compileNanos=System.nanoTime()-start,graphNodes=count,precomputed=folded)
