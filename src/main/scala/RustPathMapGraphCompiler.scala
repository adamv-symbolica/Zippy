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
  // Compile-time provenance only: no cursor/path descriptor enters the runtime.
  // A lexical traversal restores its cursor, so values may refer to fixed byte
  // ranges and ancestor focuses without copying paths or materializing tries.
  private case class Depth(base: String, offset: Int = 0):
    def +(n: Int): Depth = copy(offset=offset+n)
    def code: String = if offset==0 then base else s"($base + $offset)"
  private case class Slice(cursor: String, start: Depth, end: Depth)
  private case class Focus(cursor: String, depth: Depth)
  private case class Residual(source: String, prefixes: Vector[String])
  private val slices=mutable.Map.empty[String,Slice]
  private val focuses=mutable.Map.empty[String,Focus]
  private val residuals=mutable.Map.empty[String,Residual]
  private val cursorDepth=mutable.Map.empty[String,Depth]
  private val emptyPaths=mutable.Set.empty[String]
  private val retained=mutable.Set.empty[String]
  private val writeSinks=mutable.Map.empty[String,String]
  private val reuse=java.lang.Boolean.parseBoolean(System.getProperty("morkl.pathmap.reuse","true"))
  private def focus(s: String): Option[Focus] = focuses.get(s).orElse(residuals.get(s).flatMap { r=>
    r.prefixes.foldLeft(focus(r.source)) { (at,p)=> for
      f<-at; slice<-slices.get(p)
      if slice.cursor==f.cursor && slice.start==f.depth
    yield Focus(f.cursor,slice.end) }
  })
  private def view(s: String): Boolean = focuses.contains(s) || residuals.contains(s) || nativeTypes.get(s).exists(_.startsWith("View<"))
  private def pathSlice(s: String): String = slices.get(s).fold(s"&($s)")(p=>s"&${p.cursor}.path()[${p.start.code}..${p.end.code}]")
  private def readCursor(s: String): String = focus(s) match
    case Some(f)=>s"cursor_at(&${f.cursor},${f.depth.code})"
    case _=>residuals.get(s) match
      case Some(r)=>r.prefixes.foldLeft(readCursor(r.source))((z,p)=>s"descend_cursor($z,${pathSlice(p)})")
      case _=>s"($s).read_zipper()"
  private def borrowed(s: String): String = if view(s) then focus(s) match
    case Some(f) if cursorDepth.get(f.cursor).contains(f.depth)=>s"&${f.cursor}"
    case _ if focuses.contains(s) || residuals.contains(s)=>s"&(${readCursor(s)})"
    case _=>s"&($s)"
  else pathSlice(s)
  private def copied(s: String): String =
    if view(s) then s"(${borrowed(s)}).make_map()"
    else if slices.contains(s) || nativeTypes.get(s).contains("PathSlice") then s"Path::from_slice(${pathSlice(s)})"
    else s"($s).clone()"
  private def nativeBorrow(s: String): String =
    if view(s) then s"&${copied(s)}" else borrowed(s)
  private def zipperBorrow(s: String): String = if view(s) then borrowed(s) else s"&(${readCursor(s)})"
  private def retain(s: String): Unit =
    retained+=s
    residuals.get(s).foreach(r=>{retain(r.source);r.prefixes.foreach(retain)})
  private def referenceType(t: String): Boolean = t=="Path" || t=="Space" || t.startsWith("View<") || t.startsWith("WriteCursor<")
  private val binaryKernels=Set("unite","intersect","subtract","restrict_to","raffine","compose","equal","first_matching","ordered_matching")
  private val unaryKernels=Set("wrap","unwrap","tails_union","tails_union_n","drop_length_prefixes","tails_intersection","prefix_closure","suffix_closure","head_space","has_head","range_const","first_terminated","first_length_prefixed","first_fibonacci","first_listed")
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
  private lazy val routineEdges=program.graphs.map { (name,graph)=>
    def calls(g: RecursiveOpGraph): Set[String] = g.nodes.iterator.flatMap {
      case Left(n) if n.operation=="Call"=>Iterator.single(n.constant)
      case Right(child)=>calls(child).iterator
      case _=>Iterator.empty
    }.toSet
    name->calls(graph)
  }.toMap
  private def inlinePathHelper(name: String,g: RecursiveOpGraph): Boolean =
    val (paths,spaces)=program.signatures(name)
    def nodes(g: RecursiveOpGraph): Int = g.nodes.iterator.map {
      case Left(_)=>1
      case Right(child)=>1+nodes(child)
    }.sum
    val seen=mutable.Set.empty[String]
    def recursive(n: String): Boolean = routineEdges.getOrElse(n,Set.empty).exists(next=>next==name || (seen.add(next) && recursive(next)))
    name!=program.root && paths.nonEmpty && paths.size<=2 && spaces.isEmpty &&
      nodes(g)<=128 && !recursive(name)
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
  private def pathConstant(p: PathValue): String =
    val n=constant("Path",s"Path::from_slice(&[${path(p).mkString(",")}])")
    if p.items.isEmpty then emptyPaths+=n
    n
  private def literalConstant(v: SpaceValue): String = constant("Space",s"literal_text(\"${text(v.paths.toVector.map(path).sorted)}\")")
  private def owner(g: RecursiveOpGraph, level: Int): RecursiveOpGraph =
    if g.level == level then g else owner(g.parent.get,level)
  private def output(g: RecursiveOpGraph): Pos = g.level -> (g.nodes.size - 1)
  private def alias(g: RecursiveOpGraph, pos: Pos): Pos = g.lookup(pos) match
    case Left(Node("Alias",_,_,Vector(input))) => alias(g,input)
    case _ => pos
  private def tailsChain(g: RecursiveOpGraph,p: Pos): (Pos,Int) =
    val q=alias(g,p)
    g.lookup(q) match
      case Left(Node("TailsUnion",_,_,Vector(in)))=>
        val (base,n)=tailsChain(g,in);base->(n+1)
      case _=>q->0
  private def epsilon(g: RecursiveOpGraph,p: Pos): Boolean = g.lookup(alias(g,p)) match
    case Left(Node("Literal",v,_,_))=>g.literalValue(v).paths==Set(PathValue(List.empty))
    case Left(Node("Singleton",_,_,Vector(x)))=>g.lookup(alias(g,x)) match
      case Left(Node("Constant",v,_,_))=>g.pathValue(v).items.isEmpty
      case _=>false
    case _=>false
  private def withoutEpsilon(g: RecursiveOpGraph,p: Pos): Option[Pos] = g.lookup(alias(g,p)) match
    case Left(Node("Subtraction",_,_,Vector(a,b))) if epsilon(g,b)=>Some(a)
    case _=>None
  private def terminalProjection(g: RecursiveOpGraph,p: Pos): Option[(Pos,Option[Pos])] =
    if !reuse || p!=output(g) || !Set("Iteration","NativePathIteration")(g.root.operation) then None
    else g.lookup(alias(g,p)) match
      case Left(Node("Wrap",_,_,Vector(s,prefix)))=>withoutEpsilon(g,s).map(_->Some(prefix))
      case Left(Node("Subtraction",_,_,_))=>withoutEpsilon(g,p).map(_->None)
      case _=>None

  /** Prove from the optimized graph that each byte branch removes that many
    * bytes from its tail. This is finite compile-time specialization, not a
    * runtime length opcode or a convention about generated routine names. */
  private val lengthDrops=java.util.IdentityHashMap[RecursiveOpGraph,java.lang.Boolean]()
  private def lengthDrop(g: RecursiveOpGraph): Boolean =
    val cached=lengthDrops.get(g)
    if cached!=null then return cached.booleanValue()
    if !reuse || g.root.operation!="Iteration" then return false
    val finite=mutable.Map.empty[(Pos,Int),Option[Set[Vector[Int]]]]
    val paths=mutable.Map.empty[(Pos,Int),Option[Vector[Int]]]
    val projections=mutable.Map.empty[(Pos,Int),Option[Option[Int]]]
    val epsilon=Set(Vector.empty[Int])
    def p(at: Pos,b: Int): Option[Vector[Int]] = paths.getOrElseUpdate(at->b, {
      val q=alias(g,at)
      if q==(g.level->0) then Some(Vector(b)) else g.lookup(q) match
        case Left(Node("Constant",v,_,_))=>Some(path(g.pathValue(v)))
        case Left(Node("Concat",_,_,Vector(a,c)))=>for x<-p(a,b);y<-p(c,b) yield x++y
        case _=>None
    })
    def s(at: Pos,b: Int): Option[Set[Vector[Int]]] = finite.getOrElseUpdate(at->b, {
      g.lookup(alias(g,at)) match
        case Left(Node("Empty",_,_,_))=>Some(Set.empty)
        case Left(Node("Literal",v,_,_))=>Some(g.literalValue(v).paths.map(path))
        case Left(Node("Singleton",_,_,Vector(x)))=>p(x,b).map(Set(_))
        case Left(Node(op,_,_,Vector(a,c))) if Set("Union","Intersection","Subtraction","Composition")(op)=>
          for x<-s(a,b);y<-s(c,b);if op!="Composition" || x.size.toLong*y.size<=4096 yield op match
            case "Union"=>x union y
            case "Intersection"=>x intersect y
            case "Subtraction"=>x diff y
            case _=>for l<-x;r<-y yield l++r
        case Left(Node(op,_,_,Vector(x))) if Set("NativeHasHead","NativeNonempty","NativeHead")(op)=>
          s(x,b).map { values=>
            if op=="NativeHead" then values.filter(_.nonEmpty).map(_.take(1))
            else if (if op=="NativeHasHead" then values.exists(_.nonEmpty) else values.nonEmpty) then epsilon else Set.empty
          }
        case _=>None
    })
    def projection(at: Pos,b: Int): Option[Option[Int]] = projections.getOrElseUpdate(at->b, {
      val q=alias(g,at)
      if q==(g.level->1) then Some(Some(0)) else g.lookup(q) match
        case Left(Node("Empty",_,_,_))=>Some(None)
        case Left(Node("TailsUnion",_,_,Vector(x)))=>projection(x,b).map(_.map(_+1))
        case Left(Node("Composition",_,_,Vector(a,c)))=>s(a,b).flatMap { guard=>
          if guard.isEmpty then Some(None) else if guard==epsilon then projection(c,b) else None
        }
        case Left(Node("Union",_,_,Vector(a,c)))=>for x<-projection(a,b);y<-projection(c,b);if x.isEmpty || y.isEmpty || x==y yield x.orElse(y)
        case _=>None
    })
    val result=(0 until 256).forall(b=>projection(output(g),b).contains(Some(b)))
    lengthDrops.put(g,java.lang.Boolean.valueOf(result));result
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
      case Left(_) if terminalProjection(g,pos).nonEmpty=>
        val (source,prefix)=terminalProjection(g,pos).get;mark(g,source);prefix.foreach(mark(g,_))
      case Left(node) if reuse && node.operation=="TailsUnion"=>mark(g,tailsChain(g,pos)._1)
      case Left(node) => node.inputs.foreach(mark(g,_))
      case Right(sub) =>
        sub.root.inputs.foreach(mark(g,_))
        if lengthDrop(sub) then ()
        else if sub.root.operation == "Fold" then
          val (body,update) = foldOutputs(sub);mark(sub,body);mark(sub,update)
        else mark(sub,output(sub))

  private def dependencies(g: RecursiveOpGraph, index: Int): Set[Pos] = dependencyCache.getOrElseUpdate(g->index,g.nodes(index) match
    case Left(_) if terminalProjection(g,g.level->index).nonEmpty=>
      val (source,prefix)=terminalProjection(g,g.level->index).get;Set(source)++prefix
    case Left(n) if reuse && n.operation=="TailsUnion"=>Set(tailsChain(g,g.level->index)._1)
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
          case "Empty" | "Singleton" | "FirstMatching" | "FirstListed" | "FirstFibonacci" | "FirstTerminated" | "FirstLengthPrefixed" | "NativeHasHead" | "NativeNonempty" => true
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
      if n.operation=="FirstFibonacci" || n.operation=="FirstTerminated" || n.operation=="FirstLengthPrefixed" then true
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
      case Left(Node("Composition",_,_,Vector(_,right))) if right._1 == g.level =>
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
                        selected: Option[Set[Int]] = None, sink: Option[String] = None): Map[Pos,String] =
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
    def borrow(s: String): String = borrowed(s)
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
        if !opaque(g,p._2) && owned(p) && !retained(bindings(p)) && owned.remove(p) then
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
          val mutated=free.filter(n=>referenceType(nativeTypes(n)) &&
            (cleaned.contains(s"$n.descend_") || cleaned.contains(s"$n.ascend(") ||
              cleaned.contains(s"$n.join_into(") || (s"&mut ${java.util.regex.Pattern.quote(n)}\\b").r.findFirstIn(cleaned).nonEmpty)).toSet
          val params=free.zipWithIndex.map((n,i)=>s"capture_$i: ${if mutated(n) then "&mut " else if referenceType(nativeTypes(n)) then "&" else ""}${nativeTypes(n)}")
          val resultType=if live.isEmpty then "()" else if live.size==1 then nativeTypes(live.head) else live.map(nativeTypes).mkString("(",",",",)")
          val result=if live.isEmpty then "()" else if live.size==1 then s"(${live.head}).clone()" else live.map(n=>s"($n).clone()").mkString("(",",",",)")
          nativeBlocks+=s"#[inline(never)]\nfn $helper(${(Vector("c: &mut Context")++params).mkString(",")}) -> $resultType {\n$code\n$result\n}\n"
          out.setLength(chunkStart)
          val call=s"$helper(c${free.map(n=>", "+(if mutated(n) then s"&mut $n" else if referenceType(nativeTypes(n)) then s"&$n" else n)).mkString})"
          if live.isEmpty then line(s"$call;")
          else if live.size==1 then line(s"let mut ${live.head}: $resultType = $call;")
          else line(s"let ${live.map(n=>s"mut $n").mkString("(",",",",)")} = $call;")
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
          line(s"let $name: Space = if (${borrow(a)}).is_empty() { Space::new() } else {")
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
          val chain=if reuse && n.operation=="TailsUnion" then Some(tailsChain(g,pos)) else None
          val projection=if sink.exists(writeSinks.contains) then terminalProjection(g,pos) else None
          val ins=projection.map((src,p)=>Vector(src)++p.toVector).getOrElse(chain.fold(n.inputs)(c=>Vector(c._1))).map(value)
          def call(fn: String): String = s"${kernel(fn,ins)}(${ins.map(borrow).mkString(",")})"
          val code=if projection.nonEmpty then
            val target=sink.get;val writer=writeSinks(target)
            projection.get._2.foreach(_=>line(s"$writer.descend_to(${borrow(ins(1))});"))
            focus(ins.head).filter(f=>cursorDepth.get(f.cursor).contains(f.depth)) match
              case Some(f)=>line(s"join_branches(&mut $writer,&mut ${f.cursor});")
              case _=>
                line(s"let mut ${name}_branches = ${readCursor(ins.head)};")
                line(s"join_branches(&mut $writer,&mut ${name}_branches);")
            projection.get._2.foreach(_=>line(s"$writer.ascend((${borrow(ins(1))}).len());"))
            bindings=bindings.updated(pos,target); ""
          else n.operation match
            case "ExtractPathRef" | "ExtractSpaceMention" => bindings=bindings.updated(pos,args.getOrElse(index,params(n.kind->n.constant))); ""
            case "Alias" => bindings=bindings.updated(pos,ins.head); ""
            case "Constant" => bindings=bindings.updated(pos,pathConstant(g.pathValue(n.constant))); ""
            case "Literal" => bindings=bindings.updated(pos,literalConstant(g.literalValue(n.constant))); ""
            case "Empty" => "Space::new()"
            case "Concat" if reuse && emptyPaths(ins.head) => bindings=bindings.updated(pos,ins(1)); ""
            case "Concat" if reuse && emptyPaths(ins(1)) => bindings=bindings.updated(pos,ins.head); ""
            case "Concat" if reuse && slices.get(ins.head).zip(slices.get(ins(1))).exists((a,b)=>a.cursor==b.cursor && a.end==b.start) =>
              val a=slices(ins.head);val b=slices(ins(1))
              slices(name)=a.copy(end=b.end);bindings=bindings.updated(pos,name);nativeTypes(name)="PathSlice"; ""
            case "Concat" => call("concat_path")
            case "Singleton" if reuse && pos==output(g) && sink.exists(writeSinks.contains) =>
              val target=sink.get
              line(s"write_path(&mut ${writeSinks(target)},${borrow(ins.head)});")
              bindings=bindings.updated(pos,target); ""
            case "Singleton" => call("singleton")
            case "Call" =>
              val invoke=if tailCall.contains(g->pos) then
                val tuple=if ins.isEmpty then "()" else ins.map(copied).mkString("(",",",",)")
                s"{ next_call = Some($tuple); Space::new() }"
              else
                val pc=program.signatures(n.constant)._1.size
                s"${functions(n.constant)}(c${ins.zipWithIndex.map((s,i)=>", "+(if i<pc then borrowed(s) else if view(s) then borrowed(s) else s"&(${readCursor(s)})")).mkString})"
              val stationary=g.level==0 && n.constant==emittingRoutine && (g.lookup(alias(g,output(g))) match
                case Left(Node("Union",_,_,Vector(_,right))) => alias(g,right)==pos
                case _ => false)
              if stationary then
                val (ps,ss)=program.signatures(n.constant)
                val checks=ps.zipWithIndex.map((p,i)=>s"${borrow(ins(i))} == ${borrow(params("path"->p.s))}") ++
                  ss.zipWithIndex.map((s,i)=>s"equal(${borrow(ins(ps.size+i))},${borrow(params("space"->s.s))})")
                s"if ${if checks.isEmpty then "true" else checks.mkString(" && ")} { Space::new() } else { $invoke }"
              else invoke
            case "Union" => call("unite")
            case "Intersection" => call("intersect")
            case "Subtraction" => call("subtract")
            case "Restriction" => call("restrict_to")
            case "Raffination" => call("raffine")
            case "Composition" => call("compose")
            case "Wrap" if reuse && pos==output(g) && sink.exists(writeSinks.contains) =>
              val target=sink.get
              line(s"join_wrapped_into(&mut ${writeSinks(target)},${zipperBorrow(ins.head)},${borrow(ins(1))});")
              bindings=bindings.updated(pos,target); ""
            case "Wrap" => call("wrap")
            case "Unwrap" if reuse =>
              residuals(name)=residuals.get(ins.head).fold(Residual(ins.head,Vector(ins(1))))(r=>r.copy(prefixes=r.prefixes:+ins(1)))
              ins.foreach(retain)
              bindings=bindings.updated(pos,name);nativeTypes(name)="View<'_, '_>"; ""
            case "Unwrap" => call("unwrap")
            case "TailsUnion" if chain.exists(_._2>1) => s"${kernel("tails_union_n",ins)}(${borrow(ins.head)},${chain.get._2})"
            case "TailsUnion" => call("tails_union")
            case "TailsIntersection" => call("tails_intersection")
            case "PrefixClosure" => call("prefix_closure")
            case "SuffixClosure" => s"${kernel("suffix_closure",ins)}(${borrow(ins.head)},false)"
            case "TailsClosure" => s"${kernel("suffix_closure",ins)}(${borrow(ins.head)},true)"
            case "NativeHead" => call("head_space")
            case "NativeHasHead" => s"if ${kernel("has_head",ins)}(${borrow(ins.head)}) { singleton(&[]) } else { Space::new() }"
            case "NativeNonempty" => s"if (${borrow(ins.head)}).is_empty() { Space::new() } else { singleton(&[]) }"
            case "Range" =>
              val (lo,hi)=g.rangeBounds(n.constant); s"${kernel("range_const",ins)}::<{$lo},{$hi}>(${borrow(ins.head)})"
            case "GroundedPP" => s"singleton_path(&grounded_${n.constant}(c,&singleton(${borrow(ins.head)})))"
            case "GroundedSP" => s"singleton_path(&grounded_${n.constant}(c,${nativeBorrow(ins.head)}))"
            case "GroundedPS" => s"grounded_${n.constant}(c,&singleton(${borrow(ins.head)}))"
            case "GroundedSS" => s"grounded_${n.constant}(c,${nativeBorrow(ins.head)})"
            case "FirstFibonacci" | "FirstMatching" | "FirstListed" | "FirstTerminated" | "FirstLengthPrefixed" if orderedHead.contains(g->pos) => bindings=bindings.updated(pos,"ordered_head"); ""
            case "FirstLengthPrefixed" => s"${kernel("first_length_prefixed",ins)}(${borrow(ins.head)})"
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
          val result=if reuse && pos==output(g) && Set("Iteration","NativePathIteration")(sub.root.operation) then sink.getOrElse(name) else name
          def freshResult(): Unit = if result==name then
            line(s"let mut $result = Space::new();")
            if reuse then
              val writer=s"${name}_write"
              line(s"let mut $writer = $result.write_zipper();")
              nativeTypes(writer)="WriteCursor<'_>";writeSinks(result)=writer
          def collect(body: String): Unit = if body!=result then
            if writeSinks.contains(result) then line(s"  join_wrapped_into(&mut ${writeSinks(result)},${zipperBorrow(body)},&[]);")
            else line(s"  $result.join_into(${copied(body)});")
          val input=sub.root.inputs.map(value)
          if lengthDrop(sub) then
            val call=s"${kernel("drop_length_prefixes",input)}(${borrow(input.head)})"
            if result==name then line(s"let $name: Space = $call;")
            else if writeSinks.contains(result) then line(s"join_wrapped_into(&mut ${writeSinks(result)},&($call).read_zipper(),&[]);")
            else line(s"$result.join_into($call);")
          else sub.root.operation match
            case "NativePathIteration" =>
              val pc=sub.root.constant.toInt
              freshResult()
              input.zipWithIndex.filter(_._2!=pc).foreach { (arg,i)=>
                line(s"let ${name}_arg_$i: ${if i<pc then "&[u8]" else "&Space"} = ${nativeBorrow(arg)};")
                nativeTypes(s"${name}_arg_$i")=if i<pc then "&[u8]" else "&Space"
              }
              val existing=if reuse then focus(input(pc)).filter(f=>cursorDepth.get(f.cursor).contains(f.depth)) else None
              val cursor=existing.fold(s"${name}_cursor")(_.cursor)
              if existing.isEmpty then
                line(s"let mut $cursor = ${readCursor(input(pc))};")
                nativeTypes(cursor)="View<'_, '_>"
              val origin=existing.fold {
                line(s"let ${name}_origin = $cursor.path().len();")
                nativeTypes(s"${name}_origin")="usize";Depth(s"${name}_origin")
              }(_.depth)
              line(s"let mut ${name}_more = $cursor.is_val() || next_value_within(&mut $cursor,${origin.code});")
              line(s"while ${name}_more {")
              line("  {")
              line(s"  let ${name}_depth = $cursor.path().len();")
              nativeTypes(s"${name}_depth")="usize"
              val end=Depth(s"${name}_depth");cursorDepth(cursor)=end
              val prefix=slices.get(input.head).filter(p=>p.cursor==cursor && p.end==origin)
              if reuse && (emptyPaths(input.head) || prefix.nonEmpty) then
                slices(s"${name}_path")=Slice(cursor,prefix.fold(origin)(_.start),end)
                nativeTypes(s"${name}_path")="PathSlice"
              else
                line(s"  let ${name}_path = concat_path(${name}_arg_0,&$cursor.path()[${origin.code}..]);")
                nativeTypes(s"${name}_path")="Path"
              if reuse then focuses(s"${name}_rest")=Focus(cursor,end)
              else line(s"  let ${name}_rest = $cursor.fork_read_zipper();")
              nativeTypes(s"${name}_rest")="View<'_, '_>"
              val bound=input.indices.map { i=>i -> (if i==0 then s"${name}_path" else if i==pc then s"${name}_rest" else s"*${name}_arg_$i") }.toMap
              bound.filter(_._1<pc).values.foreach(n=>nativeTypes(n)="PathSlice")
              val inner=emitBlock(sub,bindings.toMap,bound,params,out,indent+"  ",sink=Some(result))
              collect(inner(output(sub)))
              line("  }")
              line(s"  ${name}_more = next_value_within(&mut $cursor,${origin.code});")
              line("}")
              existing match
                case Some(f)=>cursorDepth(cursor)=f.depth
                case _=>line(s"drop($cursor);");cursorDepth.remove(cursor)
            case "Iteration" | "Fold" =>
              val fold=sub.root.operation=="Fold"
              freshResult()
              if fold then
                line(s"let mut ${name}_acc: Path = ${copied(input(1))};")
                nativeTypes(s"${name}_acc")="Path"
              val direct=focus(input.head).filter(f=>cursorDepth.get(f.cursor).contains(f.depth)).map(f=>f->Vector.empty[String])
              val descents=residuals.get(input.head).flatMap(r=>focus(r.source)
                .filter(f=>cursorDepth.get(f.cursor).contains(f.depth)).map(f=>f->r.prefixes))
              val plan=if reuse then direct.orElse(descents) else None
              val cursor=plan.fold(s"${name}_cursor")(_._1.cursor)
              val restore=plan.map(_._1.depth)
              if plan.isEmpty then
                line(s"let mut $cursor = ${readCursor(input.head)};")
                nativeTypes(cursor)="View<'_, '_>"
              plan.foreach { (_,prefixes)=>prefixes.foreach { p=>
                // A prefix borrowed from this very cursor must survive its
                // path-buffer mutation; every other prefix stays borrowed.
                if slices.get(p).exists(_.cursor==cursor) then
                  line(s"let ${name}_prefix = ${copied(p)};")
                  line(s"$cursor.descend_to(&${name}_prefix);")
                else line(s"$cursor.descend_to(${borrow(p)});")
              } }
              val origin=if plan.exists(_._2.isEmpty) then restore.get else
                line(s"let ${name}_origin = $cursor.path().len();")
                nativeTypes(s"${name}_origin")="usize"
                Depth(s"${name}_origin")
              cursorDepth(cursor)=origin
              line(s"for ${name}_byte in $cursor.child_mask().iter() {")
              line(s"  $cursor.descend_to_existing_byte(${name}_byte);")
              line("  {")
              cursorDepth(cursor)=origin+1
              if reuse then
                slices(s"${name}_head")=Slice(cursor,origin,origin+1)
                focuses(s"${name}_tail")=Focus(cursor,origin+1)
              else
                line(s"  let ${name}_tail = $cursor.fork_read_zipper();")
                line(s"  let ${name}_head = Path::from_slice(&[${name}_byte]);")
              nativeTypes(s"${name}_head")=if reuse then "PathSlice" else "Path"
              nativeTypes(s"${name}_tail")="View<'_, '_>"
              val bound=if fold then Map(0->s"${name}_acc",1->s"${name}_head",2->s"${name}_tail")
                else Map(0->s"${name}_head",1->s"${name}_tail")
              val inner=emitBlock(sub,bindings.toMap,bound,params,out,indent+"  ",sink=if fold then None else Some(result))
              val body=if fold then foldOutputs(sub)._1 else output(sub)
              collect(inner(body))
              if fold then line(s"  ${name}_acc = ${copied(inner(foldOutputs(sub)._2))};")
              line("  }")
              line(s"  $cursor.ascend(1);")
              line("}")
              restore match
                case Some(depth)=>
                  if depth!=origin then line(s"$cursor.ascend($cursor.path().len() - ${depth.code});")
                  cursorDepth(cursor)=depth
                case _=>line(s"drop($cursor);");cursorDepth.remove(cursor)
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
          if result==name then writeSinks.remove(result).foreach(writer=>line(s"drop($writer);"))
          bindings=bindings.updated(pos,result)
          nativeTypes(result)="Space"
          if result==name then owned+=pos
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
      val signature=(Vector("c: &mut Context") ++ ps.indices.map(i=>s"p$i: ${if root then "Option<&Path>" else "&[u8]"}") ++
        ss.indices.map(i=>s"s$i: ${if root then "Option<&Space>" else "&View<'_, '_>"}")).mkString(", ")
      ps.indices.foreach(i=>nativeTypes(s"p$i")=if root then "Option<&Path>" else "&[u8]")
      ss.indices.foreach(i=>nativeTypes(s"s$i")=if root then "Option<&Space>" else "&View<'_, '_>")
      def binding(kind: String, i: Int): String =
        val n=s"$kind$i"; if root then s"*$n.expect(\"unbound native input\")" else s"*$n"
      val params=ps.zipWithIndex.map((p,i)=>("path"->p.s)->binding("p",i)).toMap ++
        ss.zipWithIndex.map((s,i)=>("space"->s.s)->binding("s",i)).toMap
      ps.indices.foreach(i=>nativeTypes(binding("p",i))=if root then "Path" else "PathSlice")
      ss.indices.foreach(i=>nativeTypes(binding("s",i))=if root then "Space" else "View<'_, '_>")
      // Inlining after graph lowering avoids multiplying the source language's
      // parser loops. Small path helpers (e.g. GOL neigh) still optimize together
      // with callers, including their constructed coordinate paths.
      val out=StringBuilder((if inlinePathHelper(name,g) then "#[inline(always)]\n" else "")+s"fn ${functions(name)}($signature) -> Space {\n")
      if tailCall.isEmpty then
        val values=emitBlock(g,Map.empty,Map.empty,params,out,"  ")
        out.append(s"  ${copied(values(output(g)))}\n}\n").toString
      else
        val parameters=ps.indices.map(i=>s"p$i"->"Path").toVector++ss.indices.map(i=>s"s$i"->"Space")
        parameters.foreach((n,kind)=>out.append(s"  let mut loop_$n: $kind = ${copied(binding(n.take(1),n.drop(1).toInt))};\n"))
        parameters.foreach((n,kind)=>nativeTypes(s"loop_$n")=kind)
        ordering.foreach { plan=>
          val initial=plan.removed.fold(s"loop_s${plan.space}")(i=>s"map_map_kernels::raffine(&loop_s${plan.space},&singleton(&loop_p$i))")
          val n=plan.operation
          val selection=if n.operation=="FirstFibonacci" then s"map_kernels::ordered_fibonacci(&($initial),${n.constant})"
            else if n.operation=="FirstTerminated" then s"map_kernels::ordered_terminated(&($initial))"
            else if n.operation=="FirstLengthPrefixed" then s"map_kernels::ordered_length_prefixed(&($initial))"
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
        out.append(s"    loop_result.join_into(${copied(values(output(g)))});\n")
        val pattern=if parameters.isEmpty then "()" else parameters.map((n,_)=>s"next_$n").mkString("(",",",",)")
        out.append(s"    if let Some($pattern) = next_call {\n")
        parameters.foreach((n,_)=>out.append(s"      loop_$n = next_$n;\n"))
        out.append("    } else { return loop_result; }\n  }\n}\n").toString
    }
    val (ps,ss)=program.signatures(program.root)
    val entry=s"pub fn entry(c: &mut Context, e: &Env) -> Space { ${functions(program.root)}(c" +
      ps.indices.map(i=>s",e.path($i)").mkString + ss.indices.map(i=>s",e.space($i)").mkString + ") }"
    (declarations++nativeBlocks++definitions:+entry).mkString("\n")

