package morkl

import scala.collection.mutable

/** Rust statements and lexical loops emitted directly from optimized executor
  * graph coordinates. Spaces contain only PathMap values, never instructions.
  */
private[morkl] final class RustPathMapGraphCompiler(program: NativeGraphProgram[BytePathValue]):
  private type Pos = (Int,Int)
  private val demands = java.util.IdentityHashMap[RecursiveOpGraph,mutable.BitSet]()
  private val constants = mutable.LinkedHashMap.empty[String,String]
  private val declarations = mutable.ArrayBuffer.empty[String]
  private val functions = program.graphs.map(_._1).zipWithIndex.map((n,i)=>n -> s"routine_$i").toMap
  private val lifetimes = java.util.IdentityHashMap[RecursiveOpGraph,Map[Int,Vector[Pos]]]()
  private var serial = 0
  private var emittingRoutine = ""
  private def path(p: PathValue): Vector[Int] = p.items.map { item =>
    require(item.show.startsWith("#digit:"),s"non-byte graph path: ${item.show}")
    val b=item.show.substring(7).toInt; require(b>=0 && b<256); b
  }.toVector
  private def text(ps: Vector[Vector[Int]]): String = s"${ps.size} " + ps.map(p=>s"${p.size} ${p.mkString(" ")}").mkString(" ")
  private def constant(kind: String, code: String): String = constants.getOrElseUpdate(s"$kind:$code", {
    val n=s"CONSTANT_${constants.size}"
    declarations += s"static $n: std::sync::LazyLock<$kind> = std::sync::LazyLock::new(|| $code);"
    s"*$n"
  })
  private def pathConstant(p: PathValue): String = constant("Path",s"Path::from_slice(&[${path(p).mkString(",")}])")
  private def literalConstant(v: SpaceValue): String = constant("Space",s"literal_text(\"${text(v.paths.toVector.map(path).sorted)}\")")
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

  /** Place partial input reads, recursive calls and opaque callbacks inside
    * the composition guard that demands them. Exclusive regions become Rust
    * lexical blocks; no dynamic thunk or environment is constructed.
    */
  private def deferredRegions(g: RecursiveOpGraph, wanted: Set[Int]): Map[Int,Set[Int]] =
    val deps = wanted.map(i => i -> dependencies(g,i)).toMap
    val inputReads=mutable.Map.empty[(RecursiveOpGraph,Pos),Boolean]
    def readsInput(scope: RecursiveOpGraph,pos: Pos): Boolean = inputReads.getOrElseUpdate(scope->pos, {
      val source=owner(scope,pos._1)
      source.lookup(pos) match
        case Left(n) => (source.level==0 && n.operation.startsWith("Extract")) || n.inputs.exists(readsInput(source,_))
        case Right(child) =>
          child.root.inputs.exists(readsInput(source,_)) ||
            Option(demands.get(child)).exists(_.exists(i=>readsInput(child,child.level->i)))
    })
    def effect(index: Int, seen: Set[Int] = Set.empty): Boolean =
      if seen(index) then false
      else g.nodes(index) match
        case Left(n) => readsInput(g,g.level->index) || n.operation == "Call" || n.operation.startsWith("Grounded") ||
          n.inputs.exists(p => p._1 == g.level && effect(p._2,seen+index))
        case Right(child) =>
          def opaque(s: RecursiveOpGraph): Boolean = s.nodes.exists {
            case Left(n) => n.operation == "Call" || n.operation.startsWith("Grounded")
            case Right(nested) => opaque(nested)
          }
          readsInput(g,g.level->index) || opaque(child)
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
        if current(right._2) || readsInput(g,right) then result(i) = current
      case _ => ()
    result.toMap

  private def emitBlock(g: RecursiveOpGraph, inherited: Map[Pos,String], args: Map[Int,String],
                        params: Map[(String,String),String], out: StringBuilder, indent: String,
                        selected: Option[Set[Int]] = None): Map[Pos,String] =
    val bindings = mutable.Map.from(inherited)
    val owned = mutable.Set.empty[Pos]
    val id = serial; serial += 1
    def value(p: Pos): String = bindings.getOrElse(p, {
      val scope = owner(g,p._1)
      val selection = mutable.Set.empty[Int]
      def collect(index: Int): Unit =
        if !bindings.contains(scope.level -> index) && selection.add(index) then
          dependencies(scope,index).filter(_._1 == scope.level).foreach(q=>collect(q._2))
      collect(p._2)
      require(selection.nonEmpty,s"unbound Rust graph coordinate $p")
      bindings ++= emitBlock(scope,bindings.toMap,if scope eq g then args else Map.empty,params,out,indent,Some(selection.toSet))
      bindings(p)
    })
    def line(s: String): Unit = out.append(indent).append(s).append('\n')
    def borrow(s: String): String = s"&($s)"
    val wanted = selected.getOrElse(Option(demands.get(g)).map(_.toSet).getOrElse(Set.empty))
    val releases=Option(lifetimes.get(g)).getOrElse {
      val uses=mutable.Map.empty[Pos,Int]
      Option(demands.get(g)).foreach(_.foreach { i =>
        dependencies(g,i).map(alias(g,_)).filter(_._1==g.level).foreach(p=>uses(p)=uses.getOrElse(p,-1).max(i))
      })
      val returns=if g.root.operation=="Fold" then { val (b,u)=foldOutputs(g);Vector(b,u) } else Vector(output(g))
      returns.map(alias(g,_)).foreach(p=>uses(p)=Int.MaxValue)
      val result=uses.toVector.sortBy(_._1).groupMap(_._2)(_._1);lifetimes.put(g,result);result
    }
    def release(index: Int): Unit =
      releases.getOrElse(index,Vector.empty).foreach { p=>
        if owned.remove(p) then line(s"drop(${bindings(p)});")
      }
    val regions = deferredRegions(g,wanted)
    val deferred = regions.toVector.sortBy(_._1).flatMap((owner,is)=>is.map(_->owner)).toMap
    for (entry,index) <- g.nodes.zipWithIndex if wanted(index) && !deferred.contains(index) && !bindings.contains(g.level -> index) do
      val pos = g.level -> index
      val name = s"v${id}_$index"
      entry match
        case Left(Node("Composition",_,_,Vector(left,right))) if regions.contains(index) =>
          val a=value(left)
          line(s"let $name: Space = if ($a).is_empty() { Space::new() } else {")
          val inner=emitBlock(g,bindings.toMap,args,params,out,indent+"  ",Some(regions(index)))
          // Shared dependencies may belong to a different guarded region. The
          // result must still be materialized in this lexical scope unless a
          // dominating binding is present. Do not leak this binding outside it.
          val resolved=if inner.contains(right) then inner else
            val selection=mutable.Set.empty[Int]
            def collect(i: Int): Unit =
              if !inner.contains(g.level->i) && selection.add(i) then
                dependencies(g,i).filter(_._1==g.level).foreach(p=>collect(p._2))
            collect(right._2)
            emitBlock(g,inner,args,params,out,indent+"  ",Some(selection.toSet))
          line(s"  compose(${borrow(a)},${borrow(resolved(right))})")
          line("};")
          bindings(pos)=name
          owned+=pos
        case Left(n) =>
          val ins=n.inputs.map(value)
          def call(fn: String): String = s"$fn(${ins.map(borrow).mkString(",")})"
          val code=n.operation match
            case "ExtractPathRef" | "ExtractSpaceMention" => bindings(pos)=args.getOrElse(index,params(n.kind->n.constant)); ""
            case "Alias" => bindings(pos)=ins.head; ""
            case "Constant" => bindings(pos)=pathConstant(g.pathValue(n.constant)); ""
            case "Literal" => bindings(pos)=literalConstant(g.literalValue(n.constant)); ""
            case "Empty" => "Space::new()"
            case "Concat" => call("concat_path")
            case "Singleton" => call("singleton")
            case "Call" =>
              val invoke=s"${functions(n.constant)}(c${ins.map(s=>", "+borrow(s)).mkString})"
              val stationary=g.level==0 && n.constant==emittingRoutine && (g.lookup(alias(g,output(g))) match
                case Left(Node("Union",_,_,Vector(_,right))) => alias(g,right)==pos
                case _ => false)
              if stationary then
                val (ps,ss)=program.signatures(n.constant)
                val checks=ps.zipWithIndex.map((p,i)=>s"(${ins(i)}) == (${params("path"->p.s)})") ++
                  ss.zipWithIndex.map((s,i)=>s"equal(${borrow(ins(ps.size+i))},${borrow(params("space"->s.s))})")
                s"if ${if checks.isEmpty then "true" else checks.mkString(" && ")} { Space::new() } else { $invoke }"
              else invoke
            case "Union" => call("unite")
            case "Intersection" => call("intersect")
            case "Subtraction" => call("subtract")
            case "Restriction" => call("restrict_to")
            case "Raffination" => call("raffine")
            case "Composition" => call("compose")
            case "Wrap" => call("wrap")
            case "Unwrap" => call("unwrap")
            case "TailsUnion" => call("tails_union")
            case "TailsIntersection" => call("tails_intersection")
            case "PrefixClosure" => call("prefix_closure")
            case "SuffixClosure" => s"suffix_closure(${borrow(ins.head)},false)"
            case "TailsClosure" => s"suffix_closure(${borrow(ins.head)},true)"
            case "NativeHead" => call("head_space")
            case "NativeHasHead" => s"if has_head(${borrow(ins.head)}) { singleton(&[]) } else { Space::new() }"
            case "NativeNonempty" => s"if (${ins.head}).is_empty() { Space::new() } else { singleton(&[]) }"
            case "Range" =>
              val (lo,hi)=g.rangeBounds(n.constant); s"range(${borrow(ins.head)},$lo,$hi)"
            case "GroundedPP" => s"singleton_path(&grounded_${n.constant}(c,&singleton(${borrow(ins.head)})))"
            case "GroundedSP" => s"singleton_path(&grounded_${n.constant}(c,${borrow(ins.head)}))"
            case "GroundedPS" => s"grounded_${n.constant}(c,&singleton(${borrow(ins.head)}))"
            case "GroundedSS" => s"grounded_${n.constant}(c,${borrow(ins.head)})"
            case "FirstFibonacci" => s"first_fibonacci(${borrow(ins.head)},${n.constant})"
            case "FirstMatching" | "FirstListed" =>
              val Array(prefix,encoded)=n.constant.split("\\|",2): @unchecked
              val tokens=encoded.split("\\s+").filter(_.nonEmpty).map(_.toInt).iterator
              val paths=Vector.fill(tokens.next()) {Vector.fill(tokens.next())(tokens.next()-1)}
              val allowed=if n.operation=="FirstMatching" then constant("Space",s"literal_text(\"${text(paths)}\")")
                else constant("Vec<Path>",s"paths_text(\"${text(paths)}\")")
              val fn=if n.operation=="FirstMatching" then "first_matching" else "first_listed"
              s"$fn(${borrow(ins.head)},${borrow(allowed)},$prefix)"
            case other => throw IllegalArgumentException(s"unsupported byte Rust operation $other")
          if code.nonEmpty then
            line(s"let $name: ${if n.kind=="path" then "Path" else "Space"} = $code;")
            bindings(pos)=name
            owned+=pos
        case Right(sub) =>
          val input=sub.root.inputs.map(value)
          sub.root.operation match
            case "Iteration" | "Fold" =>
              val fold=sub.root.operation=="Fold"
              line(s"let mut $name = Space::new();")
              if fold then line(s"let mut ${name}_acc: Path = (${input(1)}).clone();")
              // A single reusable cursor and a four-word ByteMask; no branch
              // vector or callback is allocated for the iterator body.
              line(s"let mut ${name}_cursor = (${input.head}).read_zipper();")
              line(s"for ${name}_byte in ${name}_cursor.child_mask().iter() {")
              line(s"  ${name}_cursor.descend_to_byte(${name}_byte);")
              line(s"  let ${name}_tail = ${name}_cursor.make_map();")
              line(s"  ${name}_cursor.ascend(1);")
              line(s"  let ${name}_head = Path::from_slice(&[${name}_byte]);")
              val bound=if fold then Map(0->s"${name}_acc",1->s"${name}_head",2->s"${name}_tail")
                else Map(0->s"${name}_head",1->s"${name}_tail")
              val inner=emitBlock(sub,bindings.toMap,bound,params,out,indent+"  ")
              val body=if fold then foldOutputs(sub)._1 else output(sub)
              line(s"  $name.join_into((${inner(body)}).clone());")
              if fold then line(s"  ${name}_acc = (${inner(foldOutputs(sub)._2)}).clone();")
              line("}")
              line(s"drop(${name}_cursor);")
            case "Fixpoint" =>
              line(s"let mut $name: Space = (${input.head}).clone();")
              line("loop {")
              val inner=emitBlock(sub,bindings.toMap,Map(0->name),params,out,indent+"  ")
              line(s"  let ${name}_next = unite(&$name,${borrow(inner(output(sub)))});")
              line(s"  if equal(&${name}_next,&$name) { break; }")
              line(s"  $name = ${name}_next;")
              line("}")
            case other => throw IllegalArgumentException(s"unsupported Rust graph scope $other")
          bindings(pos)=name
          owned+=pos
      release(index)
    bindings.toMap

  def compile(): String =
    val definitions=program.graphs.map { (name,g) =>
      emittingRoutine=name; mark(g,output(g))
      val (ps,ss)=program.signatures(name)
      val root=name==program.root
      val signature=(Vector("c: &mut Context") ++ ps.indices.map(i=>s"p$i: ${if root then "Option<&Path>" else "&Path"}") ++
        ss.indices.map(i=>s"s$i: ${if root then "Option<&Space>" else "&Space"}")).mkString(", ")
      def binding(kind: String, i: Int): String =
        val n=s"$kind$i"; if root then s"*$n.expect(\"unbound native input\")" else s"*$n"
      val params=ps.zipWithIndex.map((p,i)=>("path"->p.s)->binding("p",i)).toMap ++
        ss.zipWithIndex.map((s,i)=>("space"->s.s)->binding("s",i)).toMap
      val out=StringBuilder(s"fn ${functions(name)}($signature) -> Space {\n")
      val values=emitBlock(g,Map.empty,Map.empty,params,out,"  ")
      out.append(s"  (${values(output(g))}).clone()\n}\n").toString
    }
    val (ps,ss)=program.signatures(program.root)
    val entry=s"pub fn entry(c: &mut Context, e: &Env) -> Space { ${functions(program.root)}(c" +
      ps.indices.map(i=>s",e.path($i)").mkString + ss.indices.map(i=>s",e.space($i)").mkString + ") }"
    (declarations++definitions:+entry).mkString("\n")

