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
  private val nativeBlocks = mutable.ArrayBuffer.empty[String]
  private val nativeTypes = mutable.Map.empty[String,String]
  private def view(s: String): Boolean = nativeTypes.get(s).exists(_.startsWith("View<"))
  private def copied(s: String): String = if view(s) then s"($s).make_space()" else s"($s).clone()"
  private def nativeBorrow(s: String): String = if view(s) then s"&($s).make_space()" else s"&($s)"
  private def referenceType(t: String): Boolean = t=="Path" || t=="Space" || t.startsWith("View<")
  private val binaryKernels=Set("unite","intersect","subtract","restrict_to","raffine","compose","equal","first_matching","ordered_matching")
  private val unaryKernels=Set("wrap","unwrap","tails_union","tails_intersection","prefix_closure","suffix_closure","head_space","has_head","range_const","first_terminated","first_fibonacci","first_listed")
  private def kernel(fn: String,inputs: Vector[String]): String =
    if binaryKernels(fn) then inputs.take(2).map(s=>if view(s) then "view" else "map").mkString("","_","_kernels::")+fn
    else if unaryKernels(fn) then (if view(inputs.head) then "view_kernels::" else "map_kernels::")+fn
    else fn
  private val identifiers = raw"\b(?:v\d+_\d+(?:_[a-zA-Z0-9_]+)?|p\d+|s\d+|loop_[ps]\d+|next_call|ordered_head)\b".r
  private val blockLimit=Integer.getInteger("morkl.pathmap.blockBytes",32000).intValue().max(512)
  private val dependencyCache=mutable.Map.empty[(RecursiveOpGraph,Int),Set[Pos]]
  private val released=mutable.Set.empty[String]
  private val opaqueCache=mutable.Map.empty[(RecursiveOpGraph,Int),Boolean]
  private def available(bindings: Map[Pos,String],p: Pos): Boolean = bindings.get(p).exists(n=> !released(n))
  private def opaque(g: RecursiveOpGraph,i: Int): Boolean = opaqueCache.getOrElseUpdate(g->i,g.nodes(i) match
    case Left(n)=>n.operation=="Call" || n.operation.startsWith("Grounded") || n.inputs.exists(p=>opaque(owner(g,p._1),p._2))
    case Right(child)=>child.root.inputs.exists(p=>opaque(owner(g,p._1),p._2)) || child.nodes.indices.exists(j=>opaque(child,j)))
  private val functions = program.graphs.map(_._1).zipWithIndex.map((n,i)=>n -> s"routine_$i").toMap
  private val lifetimes = java.util.IdentityHashMap[RecursiveOpGraph,Map[Int,Vector[Pos]]]()
  private var serial = 0
  private var emittingRoutine = ""
  private var tailCall = Option.empty[(RecursiveOpGraph,Pos)]
  private var orderedHead = Option.empty[(RecursiveOpGraph,Pos)]
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

  private def dependencies(g: RecursiveOpGraph, index: Int): Set[Pos] = dependencyCache.getOrElseUpdate(g->index,g.nodes(index) match
    case Left(n) => n.inputs.toSet
    case Right(child) =>
      def captures(s: RecursiveOpGraph): Set[Pos] =
        val wanted = Option(demands.get(s)).getOrElse(mutable.BitSet.empty)
        s.root.inputs.toSet ++ wanted.toSet.flatMap(i=>dependencies(s,i))
      captures(child).filter(_._1 <= g.level)
  )

  /** Prove a single tail continuation through union and single-path loops.
    * This turns translated ordered folds into native loops without a trampoline
    * or a runtime stack of language continuations.
    */
  private def tailContinuation(top: RecursiveOpGraph,name: String): Option[(RecursiveOpGraph,Pos)] =
    val memo=mutable.Map.empty[(RecursiveOpGraph,Pos),Boolean]
    val selfMemo=mutable.Map.empty[(RecursiveOpGraph,Pos),Boolean]
    def one(g: RecursiveOpGraph,p: Pos): Boolean = memo.getOrElseUpdate(g->p, {
      val original=owner(g,p._1);val pos=alias(original,p);val scope=owner(original,pos._1)
      scope.lookup(pos) match
        case Left(n) => n.operation match
          case "Empty" | "Singleton" | "FirstMatching" | "FirstListed" | "FirstFibonacci" | "FirstTerminated" | "NativeHasHead" | "NativeNonempty" => true
          case "Literal" => scope.literalValue(n.constant).paths.size<=1
          case "Intersection" => n.inputs.exists(one(scope,_))
          case "Wrap" | "Unwrap" | "Subtraction" | "Restriction" | "Raffination" => one(scope,n.inputs.head)
          case "Composition" => n.inputs.forall(one(scope,_))
          case "Range" =>
            val (lo,hi)=scope.rangeBounds(n.constant)
            RangeBounds.cardinalityUpperBound(lo,hi).exists(_<=1) || one(scope,n.inputs.head)
          case "ExtractSpaceMention" if scope.parent.nonEmpty =>
            scope.root.operation match
              case "Iteration" => one(scope.parent.get,scope.root.inputs.head)
              case "NativePathIteration" => one(scope.parent.get,scope.root.inputs(pos._2))
              case _ => false
          case _ => false
        case Right(_) => false
    })
    def self(g: RecursiveOpGraph,p: Pos): Boolean = selfMemo.getOrElseUpdate(g->p, {
      val original=owner(g,p._1);val pos=alias(original,p);val scope=owner(original,pos._1)
      scope.lookup(pos) match
        case Left(n) => (n.operation=="Call" && n.constant==name) || n.inputs.exists(self(scope,_))
        case Right(child) => self(child,output(child))
    })
    def tail(g: RecursiveOpGraph,p: Pos): Option[(RecursiveOpGraph,Pos)] =
      val original=owner(g,p._1);val pos=alias(original,p);val scope=owner(original,pos._1)
      scope.lookup(pos) match
        case Left(Node("Call",target,_,_)) if target==name => Some(scope->pos)
        case Left(Node("Union",_,_,Vector(left,right))) if !self(scope,left) => tail(scope,right)
        case Right(child) if child.root.operation=="Iteration" && one(scope,child.root.inputs.head) => tail(child,output(child))
        case Right(child) if child.root.operation=="NativePathIteration" && one(scope,child.root.inputs(child.root.constant.toInt)) => tail(child,output(child))
        case _ => None
    tail(top,output(top))

  private case class OrderedFold(head: Pos, space: Int, removed: Option[Int], operation: Node[Pos])
  private def selectionConstant(n: Node[Pos]): (String,String) =
    val Array(prefix,encoded)=n.constant.split("\\|",2): @unchecked
    val tokens=encoded.split("\\s+").filter(_.nonEmpty).map(_.toInt).iterator
    val paths=Vector.fill(tokens.next()) {Vector.fill(tokens.next())(tokens.next()-1)}
    val allowed=if n.operation=="FirstMatching" then constant("Space",s"literal_text(\"${text(paths)}\")")
      else constant("&'static [&'static [u8]]",paths.map(p=>s"&[${p.mkString(",")}]").mkString("&[",",","]"))
    allowed->prefix

  /** Hoist a static ordering only if continuation removes exactly the selected
    * prefix. Prefix-free codewords make those removals independent. Also accept
    * the step routine produced by inlining: it removes the current head before
    * selecting the next, and must pass that next head to the same path parameter.
    */
  private def orderedFold(top: RecursiveOpGraph,target: (RecursiveOpGraph,Pos),paths: Vector[PathRef],
                          spaces: Vector[SpaceMention]): Option[OrderedFold] =
    def located(g: RecursiveOpGraph,p: Pos): (RecursiveOpGraph,Pos) =
      val a=owner(g,p._1);val q=alias(a,p);owner(a,q._1)->q
    def lookup(g: RecursiveOpGraph,p: Pos) = {val (s,q)=located(g,p);s.lookup(q)}
    def origin(g: RecursiveOpGraph,p: Pos): (RecursiveOpGraph,Pos) =
      val (s,q)=located(g,p)
      s.lookup(q) match
        case Left(Node("ExtractSpaceMention",_,_,_)) if s.parent.nonEmpty && s.root.operation=="NativePathIteration" && q._2>s.root.constant.toInt =>
          origin(s.parent.get,s.root.inputs(q._2))
        case _ => s->q
    def loopAt(p: Pos): Option[RecursiveOpGraph] = lookup(top,p) match
      case Right(loop) if loop.root.operation=="NativePathIteration" => Some(loop)
      case Left(Node("Union",_,_,Vector(_,right))) => loopAt(right)
      case _ => None
    def prefixFree(n: Node[Pos]): Boolean =
      if n.operation=="FirstFibonacci" || n.operation=="FirstTerminated" then true
      else if Set("FirstMatching","FirstListed")(n.operation) then
        val tokens=n.constant.split("\\|",2)(1).split("\\s+").filter(_.nonEmpty).map(_.toInt).iterator
        val codes=Vector.fill(tokens.next()) {Vector.fill(tokens.next())(tokens.next())}.sorted
        codes.sliding(2).forall(pair=>pair.size<2 || !pair(1).startsWith(pair(0)))
      else false
    for
      loop<-loopAt(output(top))
      pc=loop.root.constant.toInt
      if lookup(top,loop.root.inputs.head) match
        case Left(Node("Constant",p,_,_))=>top.pathValue(p).items.isEmpty
        case _=>false
      selected=alias(top,loop.root.inputs(pc))
      if selected._1==top.level
      n<-lookup(top,selected).left.toOption
      if prefixFree(n) && n.inputs.size==1
      input=n.inputs.head
      shape<-(lookup(top,input) match
        case Left(Node("ExtractSpaceMention",ref,_,_))=>Some((ref,Option.empty[Int]))
        case Left(Node("Raffination",_,_,Vector(src,head)))=>
          (lookup(top,src),lookup(top,head)) match
            case (Left(Node("ExtractSpaceMention",ref,_,_)),Left(Node("Singleton",_,_,Vector(p))))=>lookup(top,p) match
              case Left(Node("ExtractPathRef",name,_,_))=>paths.indexWhere(_.s==name) match
                case index if index>=0=>Some((ref,Some(index)))
                case _=>None
              case _=>None
            case _=>None
        case _=>None)
      index=spaces.indexWhere(_.s==shape._1)
      if index>=0
      (scope,pos)=target
      call=scope.lookup(pos).left.toOption.get
      next=call.inputs(paths.size+index)
      if shape._2 match
        case Some(pi)=>origin(scope,next)==origin(top,input) && located(scope,call.inputs(pi))==(loop->(loop.level->0))
        case None=>
          def removed(p: Pos): Boolean=lookup(scope,p) match
            case Left(Node("Singleton",_,_,Vector(path)))=>located(scope,path)==(loop->(loop.level->0))
            case _=>false
          lookup(scope,next) match
            case Left(Node("Raffination",_,_,Vector(src,head)))=>origin(scope,src)==origin(top,input) && removed(head)
            case _=>false
    yield OrderedFold(selected,index,shape._2,n)

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
    // Persistent compiler bindings share their unchanged prefix across lexical
    // scopes. Copying a growing mutable table for every guard was quadratic.
    var bindings = inherited
    val owned = mutable.Set.empty[Pos]
    val id = serial; serial += 1
    var chunkStart=out.length
    var chunkNodes=0
    var lastEmitted = -1
    def value(p: Pos): String = bindings.get(p).filterNot(released).getOrElse {
      val scope = owner(g,p._1)
      val selection = mutable.Set.empty[Int]
      def collect(index: Int): Unit =
        if !available(bindings,scope.level -> index) && selection.add(index) then
          dependencies(scope,index).filter(_._1 == scope.level).foreach(q=>collect(q._2))
      collect(p._2)
      require(selection.nonEmpty,s"unbound Rust graph coordinate $p")
      bindings = emitBlock(scope,bindings,if scope eq g then args else Map.empty,params,out,indent,Some(selection.toSet))
      bindings(p)
    }
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
        if !opaque(g,p._2) && owned.remove(p) then
          val n=bindings(p);line(s"drop($n);");released+=n
      }
    // Bound Rust's MIR/drop elaboration and LLVM work without introducing a
    // node dispatcher or a runtime environment. Regions pass typed references
    // and return their live SSA values. Loops remain ordinary generated loops.
    def flush(index: Int): Unit =
      val body=out.substring(chunkStart)
      if body.length>=blockLimit && !body.contains("next_call") then
        val declared=(raw"\blet\s+(?:mut\s+)?([^=;{}\n]+)=".r.findAllMatchIn(body).flatMap(m=>identifiers.findAllIn(m.group(1))) ++
          raw"\bfor\s+(v\d+_\d+_[a-zA-Z0-9_]+)\s+in\b".r.findAllMatchIn(body).map(_.group(1))).toSet
        val delayed=mutable.ArrayBuffer.empty[String]
        val cleaned=raw"(?m)^\s*drop\((v\d+_\d+(?:_[a-zA-Z0-9_]+)?)\);\s*$$".r.replaceAllIn(body,m=>
          if !declared(m.group(1)) then {delayed+=m.group(1);""} else m.matched)
        val free=identifiers.findAllIn(cleaned).filterNot(declared).toSet.toVector.sorted
        val dropped=raw"\bdrop\((v\d+_\d+(?:_[a-zA-Z0-9_]+)?)\)".r.findAllMatchIn(cleaned).map(_.group(1)).toSet
        // Adaptive placement may reuse these bindings from a later guarded
        // region even after their last direct edge. Return every still-live
        // binding, including values computed by a dependency replay.
        val live=bindings.valuesIterator.filter(n=>declared(n) && !released(n) && !dropped(n) && nativeTypes.get(n).exists(t=>t=="Path"||t=="Space")).toSet.toVector.sorted
        if free.forall(nativeTypes.contains) then
          val helper=s"native_block_${nativeBlocks.size}"
          val replacements=free.zipWithIndex.map { (n,i)=>
            val t=nativeTypes(n);n -> (if referenceType(t) then s"(*capture_$i)" else s"capture_$i")
          }.toMap
          val code=identifiers.replaceAllIn(cleaned,m=>java.util.regex.Matcher.quoteReplacement(replacements.getOrElse(m.matched,m.matched)))
          val params=free.zipWithIndex.map((n,i)=>s"capture_$i: ${if referenceType(nativeTypes(n)) then "&" else ""}${nativeTypes(n)}")
          val resultType=if live.isEmpty then "()" else if live.size==1 then nativeTypes(live.head) else live.map(nativeTypes).mkString("(",",",",)")
          val result=if live.isEmpty then "()" else if live.size==1 then s"(${live.head}).clone()" else live.map(n=>s"($n).clone()").mkString("(",",",",)")
          nativeBlocks+=s"#[inline(never)]\nfn $helper(${(Vector("c: &mut Context")++params).mkString(",")}) -> $resultType {\n$code\n$result\n}\n"
          out.setLength(chunkStart)
          val call=s"$helper(c${free.map(n=>", "+(if referenceType(nativeTypes(n)) then s"&$n" else n)).mkString})"
          if live.isEmpty then line(s"$call;")
          else if live.size==1 then line(s"let ${live.head}: $resultType = $call;")
          else line(s"let ${live.mkString("(",",",",)")} = $call;")
          delayed.foreach(n=>line(s"drop($n);"))
      chunkStart=out.length;chunkNodes=0
    val regions = deferredRegions(g,wanted)
    val deferred = regions.toVector.sortBy(_._1).flatMap((owner,is)=>is.map(_->owner)).toMap
    for (entry,index) <- g.nodes.zipWithIndex if wanted(index) && !deferred.contains(index) && !available(bindings,g.level -> index) do
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
          val resolved=if available(inner,right) then inner else
            val selection=mutable.Set.empty[Int]
            def collect(i: Int): Unit =
              if !available(inner,g.level->i) && selection.add(i) then
                dependencies(g,i).filter(_._1==g.level).foreach(p=>collect(p._2))
            collect(right._2)
            emitBlock(g,inner,args,params,out,indent+"  ",Some(selection.toSet))
          line(s"  ${kernel("compose",Vector(a,resolved(right)))}(${borrow(a)},${borrow(resolved(right))})")
          line("};")
          bindings=bindings.updated(pos,name)
          nativeTypes(name)="Space"
          owned+=pos
        case Left(n) =>
          val ins=n.inputs.map(value)
          def call(fn: String): String = s"${kernel(fn,ins)}(${ins.map(borrow).mkString(",")})"
          val code=n.operation match
            case "ExtractPathRef" | "ExtractSpaceMention" => bindings=bindings.updated(pos,args.getOrElse(index,params(n.kind->n.constant))); ""
            case "Alias" => bindings=bindings.updated(pos,ins.head); ""
            case "Constant" => bindings=bindings.updated(pos,pathConstant(g.pathValue(n.constant))); ""
            case "Literal" => bindings=bindings.updated(pos,literalConstant(g.literalValue(n.constant))); ""
            case "Empty" => "Space::new()"
            case "Concat" => call("concat_path")
            case "Singleton" => call("singleton")
            case "Call" =>
              val invoke=if tailCall.contains(g->pos) then
                val tuple=if ins.isEmpty then "()" else ins.map(copied).mkString("(",",",",)")
                s"{ next_call = Some($tuple); Space::new() }"
              else s"${functions(n.constant)}(c${ins.map(s=>", "+nativeBorrow(s)).mkString})"
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
            case "SuffixClosure" => s"${kernel("suffix_closure",ins)}(${borrow(ins.head)},false)"
            case "TailsClosure" => s"${kernel("suffix_closure",ins)}(${borrow(ins.head)},true)"
            case "NativeHead" => call("head_space")
            case "NativeHasHead" => s"if ${kernel("has_head",ins)}(${borrow(ins.head)}) { singleton(&[]) } else { Space::new() }"
            case "NativeNonempty" => s"if (${ins.head}).is_empty() { Space::new() } else { singleton(&[]) }"
            case "Range" =>
              val (lo,hi)=g.rangeBounds(n.constant); s"${kernel("range_const",ins)}::<{$lo},{$hi}>(${borrow(ins.head)})"
            case "GroundedPP" => s"singleton_path(&grounded_${n.constant}(c,&singleton(${borrow(ins.head)})))"
            case "GroundedSP" => s"singleton_path(&grounded_${n.constant}(c,${nativeBorrow(ins.head)}))"
            case "GroundedPS" => s"grounded_${n.constant}(c,&singleton(${borrow(ins.head)}))"
            case "GroundedSS" => s"grounded_${n.constant}(c,${nativeBorrow(ins.head)})"
            case "FirstFibonacci" | "FirstMatching" | "FirstListed" | "FirstTerminated" if orderedHead.contains(g->pos) => bindings=bindings.updated(pos,"ordered_head"); ""
            case "FirstTerminated" => s"${kernel("first_terminated",ins)}(${borrow(ins.head)})"
            case "FirstFibonacci" => s"${kernel("first_fibonacci",ins)}(${borrow(ins.head)},${n.constant})"
            case "FirstMatching" | "FirstListed" =>
              val (allowed,prefix)=selectionConstant(n)
              val fn=if n.operation=="FirstMatching" then "first_matching" else "first_listed"
              s"${kernel(fn,Vector(ins.head,allowed))}(${borrow(ins.head)},${borrow(allowed)},$prefix)"
            case other => throw IllegalArgumentException(s"unsupported byte Rust operation $other")
          if code.nonEmpty then
            line(s"let $name: ${if n.kind=="path" then "Path" else "Space"} = $code;")
            bindings=bindings.updated(pos,name)
            nativeTypes(name)=if n.kind=="path" then "Path" else "Space"
            owned+=pos
        case Right(sub) =>
          nativeTypes(name)="Space"
          val input=sub.root.inputs.map(value)
          sub.root.operation match
            case "NativePathIteration" =>
              val pc=sub.root.constant.toInt
              line(s"let mut $name = Space::new();")
              input.zipWithIndex.foreach { (arg,i)=>
                line(s"let ${name}_arg_$i: &${if i<pc then "Path" else "Space"} = ${nativeBorrow(arg)};")
                nativeTypes(s"${name}_arg_$i")=if i<pc then "&Path" else "&Space"
              }
              line(s"let mut ${name}_cursor = ${name}_arg_$pc.read_zipper();")
              line(s"let mut ${name}_more = ${name}_cursor.is_val() || ${name}_cursor.to_next_val();")
              line(s"while ${name}_more {")
              line("  {")
              line(s"  let ${name}_path = concat_path(${name}_arg_0,${name}_cursor.path());")
              line(s"  let ${name}_rest = ${name}_cursor.fork_read_zipper();")
              nativeTypes(s"${name}_path")="Path";nativeTypes(s"${name}_rest")="View<'_, '_>"
              val bound=input.indices.map { i=>i -> (if i==0 then s"${name}_path" else if i==pc then s"${name}_rest" else s"*${name}_arg_$i") }.toMap
              val inner=emitBlock(sub,bindings.toMap,bound,params,out,indent+"  ")
              line(s"  $name.join_into(${copied(inner(output(sub)))});")
              line("  }")
              line(s"  ${name}_more = ${name}_cursor.to_next_val();")
              line("}")
              line(s"drop(${name}_cursor);")
            case "Iteration" | "Fold" =>
              val fold=sub.root.operation=="Fold"
              line(s"let mut $name = Space::new();")
              if fold then
                line(s"let mut ${name}_acc: Path = (${input(1)}).clone();")
                nativeTypes(s"${name}_acc")="Path"
              // A single reusable cursor and a four-word ByteMask; no branch
              // vector or callback is allocated for the iterator body.
              line(s"let mut ${name}_cursor = (${input.head}).read_zipper();")
              line(s"for ${name}_byte in ${name}_cursor.child_mask().iter() {")
              line(s"  ${name}_cursor.descend_to_byte(${name}_byte);")
              line("  {")
              line(s"  let ${name}_tail = ${name}_cursor.fork_read_zipper();")
              line(s"  let ${name}_head = Path::from_slice(&[${name}_byte]);")
              nativeTypes(s"${name}_head")="Path";nativeTypes(s"${name}_tail")="View<'_, '_>"
              val bound=if fold then Map(0->s"${name}_acc",1->s"${name}_head",2->s"${name}_tail")
                else Map(0->s"${name}_head",1->s"${name}_tail")
              val inner=emitBlock(sub,bindings.toMap,bound,params,out,indent+"  ")
              val body=if fold then foldOutputs(sub)._1 else output(sub)
              line(s"  $name.join_into(${copied(inner(body))});")
              if fold then line(s"  ${name}_acc = (${inner(foldOutputs(sub)._2)}).clone();")
              line("  }")
              line(s"  ${name}_cursor.ascend(1);")
              line("}")
              line(s"drop(${name}_cursor);")
            case "Fixpoint" =>
              line(s"let mut $name: Space = ${copied(input.head)};")
              line("loop {")
              val inner=emitBlock(sub,bindings.toMap,Map(0->name),params,out,indent+"  ")
              // Native status normally eliminates the equality traversal. For
              // opaque steps retain exact callback counts even if a physical
              // representation change conservatively reports Element.
              if program.compiled.callbackCount>0 && sub.nodes.indices.exists(i=>opaque(sub,i)) then
                line(s"  let ${name}_before = $name.clone();")
                line(s"  if !$name.join_into(${copied(inner(output(sub)))}).is_element() || equal(&$name,&${name}_before) { break; }")
              else line(s"  if !$name.join_into(${copied(inner(output(sub)))}).is_element() { break; }")
              line("}")
            case other => throw IllegalArgumentException(s"unsupported Rust graph scope $other")
          bindings=bindings.updated(pos,name)
          nativeTypes(name)="Space"
          owned+=pos
      release(index)
      lastEmitted=index
      chunkNodes+=1
      if out.length-chunkStart>=blockLimit || chunkNodes>=256 then flush(index)
    flush(lastEmitted)
    bindings.toMap

  def compile(): String =
    val definitions=program.graphs.map { (name,g) =>
      emittingRoutine=name; mark(g,output(g))
      tailCall=tailContinuation(g,name)
      val (ps,ss)=program.signatures(name)
      val ordering=tailCall.flatMap(t=>orderedFold(g,t,ps,ss))
      orderedHead=ordering.map(o=>(g,o.head))
      val root=name==program.root
      val signature=(Vector("c: &mut Context") ++ ps.indices.map(i=>s"p$i: ${if root then "Option<&Path>" else "&Path"}") ++
        ss.indices.map(i=>s"s$i: ${if root then "Option<&Space>" else "&Space"}")).mkString(", ")
      ps.indices.foreach(i=>nativeTypes(s"p$i")=if root then "Option<&Path>" else "&Path")
      ss.indices.foreach(i=>nativeTypes(s"s$i")=if root then "Option<&Space>" else "&Space")
      def binding(kind: String, i: Int): String =
        val n=s"$kind$i"; if root then s"*$n.expect(\"unbound native input\")" else s"*$n"
      val params=ps.zipWithIndex.map((p,i)=>("path"->p.s)->binding("p",i)).toMap ++
        ss.zipWithIndex.map((s,i)=>("space"->s.s)->binding("s",i)).toMap
      val out=StringBuilder(s"fn ${functions(name)}($signature) -> Space {\n")
      if tailCall.isEmpty then
        val values=emitBlock(g,Map.empty,Map.empty,params,out,"  ")
        out.append(s"  (${values(output(g))}).clone()\n}\n").toString
      else
        val parameters=ps.indices.map(i=>s"p$i"->"Path").toVector++ss.indices.map(i=>s"s$i"->"Space")
        parameters.foreach((n,kind)=>out.append(s"  let mut loop_$n: $kind = (${binding(n.take(1),n.drop(1).toInt)}).clone();\n"))
        parameters.foreach((n,kind)=>nativeTypes(s"loop_$n")=kind)
        ordering.foreach { plan=>
          val initial=plan.removed.fold(s"loop_s${plan.space}")(i=>s"map_map_kernels::raffine(&loop_s${plan.space},&singleton(&loop_p$i))")
          val n=plan.operation
          val selection=if n.operation=="FirstFibonacci" then s"map_kernels::ordered_fibonacci(&($initial),${n.constant})"
            else if n.operation=="FirstTerminated" then s"map_kernels::ordered_terminated(&($initial))"
            else
              val (allowed,prefix)=selectionConstant(n)
              val fn=if n.operation=="FirstMatching" then "map_map_kernels::ordered_matching" else "map_kernels::ordered_listed"
              s"$fn(&($initial),&($allowed),$prefix)"
          out.append(s"  let mut ordered_heads = $selection.into_iter();\n")
        }
        val tuple=if parameters.isEmpty then "()" else parameters.map(_._2).mkString("(",",",",)")
        val loopParams=ps.zipWithIndex.map((p,i)=>("path"->p.s)->s"loop_p$i").toMap ++ ss.zipWithIndex.map((s,i)=>("space"->s.s)->s"loop_s$i").toMap
        out.append(s"  let mut loop_result = Space::new();\n  loop {\n    let mut next_call: Option<$tuple> = None;\n")
        ordering.foreach { _=>
          out.append("    let ordered_head = ordered_heads.next().map_or_else(Space::new,|p| singleton(&p));\n")
          nativeTypes("ordered_head")="Space"
        }
        val values=emitBlock(g,Map.empty,Map.empty,loopParams,out,"    ")
        out.append(s"    loop_result.join_into((${values(output(g))}).clone());\n")
        val pattern=if parameters.isEmpty then "()" else parameters.map((n,_)=>s"next_$n").mkString("(",",",",)")
        out.append(s"    if let Some($pattern) = next_call {\n")
        parameters.foreach((n,_)=>out.append(s"      loop_$n = next_$n;\n"))
        out.append("    } else { return loop_result; }\n  }\n}\n").toString
    }
    val (ps,ss)=program.signatures(program.root)
    val entry=s"pub fn entry(c: &mut Context, e: &Env) -> Space { ${functions(program.root)}(c" +
      ps.indices.map(i=>s",e.path($i)").mkString + ss.indices.map(i=>s",e.space($i)").mkString + ") }"
    (declarations++nativeBlocks++definitions:+entry).mkString("\n")

