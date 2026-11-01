package morkl

import scala.collection.mutable

/** Recognize the existing decision-tree spelling of ordered head selection.
  * Prefix fusion is conservative: scanner grammar proves that every output is
  * the consumed prefix, all control predicates are independent of residuals,
  * and every recursive scanner cycle consumes a digit. Abstract execution on
  * each codeword with an opaque, nonempty tail then proves completeness without
  * inspecting that tail. Thus prefix membership is equivalent even on malformed
  * input words outside the original encoding's image.
  *
  * This is a compiler optimization of ordinary functions/ops, not an AST case
  * or a runtime encoding side table.
  */
private[morkl] final class OpenFstHeadSelection[V,L <: DigitLanguage[V]](
    val source: L, encode: V=>Vector[Int], routines: Map[RoutinePtr,source.Routine]):
  import source.{Path as P,Space as S}
  case class Plan(input: source.Space, codes: Vector[Vector[Int]], prefix: Boolean)
  private val selectors = java.util.IdentityHashMap[source.Space,Option[(SpaceMention,Vector[V])]]()
  private def epsilon(s: source.Space): Boolean = s match
    case S.Singleton(P.Constant(v)) => source.paths.length(v)==0
    case _ => false
  // A second translation freshens lexical iteration binders independently in
  // the two occurrences of a condition. Compare those conditions modulo alpha
  // renaming; free inputs and grounded operations still have to match exactly.
  private def sameTest(a: source.Space,b: source.Space,
      ps: Map[PathRef,PathRef]=Map.empty,ss: Map[SpaceMention,SpaceMention]=Map.empty): Boolean =
    def path(a: source.Path,b: source.Path): Boolean = (a,b) match
      case (P.Deref(x),P.Deref(y)) => ps.getOrElse(x,x)==y
      case (P.Constant(x),P.Constant(y)) => x==y
      case (P.Concat(x,y),P.Concat(z,w)) => path(x,z)&&path(y,w)
      case _ => false
    def next(a: source.Space,b: source.Space)=sameTest(a,b,ps,ss)
    (a,b) match
      case (S.Empty,S.Empty) => true
      case (S.Mention(x),S.Mention(y)) => ss.getOrElse(x,x)==y
      case (S.Literal(x),S.Literal(y)) => x==y
      case (S.Singleton(x),S.Singleton(y)) => path(x,y)
      case (S.Union(x,y),S.Union(z,w)) => next(x,z)&&next(y,w)
      case (S.Intersection(x,y),S.Intersection(z,w)) => next(x,z)&&next(y,w)
      case (S.Subtraction(x,y),S.Subtraction(z,w)) => next(x,z)&&next(y,w)
      case (S.Composition(x,y),S.Composition(z,w)) => next(x,z)&&next(y,w)
      case (S.Iteration(x,h,t,y),S.Iteration(z,j,u,w)) =>
        next(x,z) && sameTest(y,w,if h.s=="_" then ps else ps.updated(h,j),if t.s=="_" then ss else ss.updated(t,u))
      case _ => a==b && ps.isEmpty && ss.isEmpty
  private def choice(s: source.Space): Option[(source.Space,source.Space,source.Space)] = s match
    case S.Union(S.Composition(test,yes),S.Composition(S.Subtraction(eps,other),no)) if epsilon(eps) && sameTest(test,other) =>
      test match
        case S.Union(S.Intersection(value,unit),S.Iteration(same,_,_,body)) if value==same && epsilon(unit) && epsilon(body) =>
          Some((value,yes,no))
        case _ => None
    case _ => None
  private def selector(s: source.Space): Option[(SpaceMention,Vector[V])] =
    if selectors.containsKey(s) then selectors.get(s)
    else
      val result = s match
        case S.Intersection(S.Mention(ref),S.Singleton(P.Constant(value))) => Some(ref->Vector(value))
        case _ => choice(s).flatMap { (test,yes,no) => test match
          case S.Intersection(S.Mention(ref),S.Literal(values)) =>
            for
              left <- selector(yes)
              right <- selector(no)
              if left._1==ref && right._1==ref && left._2.toSet==values.paths
            yield ref -> (left._2++right._2)
          case _ => None
        }
      selectors.put(s,result);result
  private def refs(p: source.Path): Option[Vector[String]] = p match
    case P.Deref(r) => Some(Vector(r.s))
    case P.Constant(v) if source.paths.length(v)==0 => Some(Vector.empty)
    case P.Concat(a,b) => for x<-refs(a);y<-refs(b) yield x++y
    case _ => None
  /** Prove the ordinary zero-terminated scanner used by ordered string folds.
    * No assumption is made about runtime input encoding: incomplete words are
    * ignored, and each output ends at its first zero even on malformed input.
    */
  def terminatedFirst(x: source.Space): Option[source.Space] =
    def zero(test: source.Space,h: PathRef): Boolean = test match
      case S.Intersection(S.Singleton(P.Deref(r)),S.Singleton(P.Constant(v))) => r==h && encode(v)==Vector(1)
      case _=>false
    def result(x: source.Space,word: Vector[String],tail: SpaceMention): Boolean = x match
      case S.Singleton(p)=>refs(p).contains(word)
      case S.Call(name,Vector(p),Vector(S.Mention(r))) if r==tail && refs(p).contains(word)=>routines.get(name).exists { fn=>
        fn.refs.size==1 && fn.mentions.size==1 && (fn.body match
          case S.Singleton(P.Deref(r))=>r==fn.refs.head
          case _=>false)
      }
      case _=>false
    def recurse(x: source.Space,word: Vector[String],tail: SpaceMention): Option[RoutinePtr] = x match
      case S.Call(name,Vector(p),Vector(S.Mention(r))) if r==tail && refs(p).contains(word)=>Some(name)
      case _=>None
    x match
      case S.Range(S.Iteration(input,h,t,body),0,1)=>choice(body).flatMap { (test,yes,no)=>
        if h.s=="_" || t.s=="_" || !zero(test,h) || !result(yes,Vector(h.s),t) then None
        else recurse(no,Vector(h.s),t).flatMap { name=>routines.get(name).filter { fn=>
          fn.refs.size==1 && fn.mentions.size==1 && fn.refs.head.s!="_" && fn.mentions.head.s!="_" && (fn.body match
            case S.Iteration(S.Mention(s),d,r,next) if s==fn.mentions.head=>choice(next).exists { (test,yes,no)=>
              val word=Vector(fn.refs.head.s,d.s)
              d.s!="_" && r.s!="_" && d!=fn.refs.head && r!=fn.mentions.head &&
                zero(test,d) && result(yes,word,r) && recurse(no,word,r).contains(name)
            }
            case _=>false)
        }.map(_=>input) }
      }
      case _=>None
  /** Inductive proof of a length-byte scanner. Negative abstract digits stand
    * for an arbitrary accumulator, arbitrary payload byte and continuation.
    * Set predicates may inspect only concrete digits; an opaque suffix may not
    * be inspected at all. For every counter byte the scanner must either return
    * its accumulator (zero), or consume exactly one byte and recurse with n-1.
    */
  def lengthFirst(x: source.Space): Option[source.Space] =
    def prove(heads: source.Space): Option[source.Space] = heads match
      case S.Iteration(input,h,t,S.Call(name,Vector(P.Deref(a),P.Deref(b)),Vector(S.Mention(r))))
          if h.s!="_" && t.s!="_" && a==h && b==h && r==t =>
        routines.get(name).filter(fn=>fn.refs.size==2 && fn.mentions.size==1).flatMap { scan=>
          import Value.*
          type PC=Map[PathRef,Vector[Int]]
          type SC=Map[SpaceMention,Value]
          def sequence[A](xs: Vector[Option[A]]): Option[Vector[A]] =
            if xs.forall(_.nonEmpty) then Some(xs.map(_.get)) else None
          def path(p: source.Path,pc: PC): Option[Vector[Int]] = p match
            case P.Constant(v)=>Some(encode(v))
            case P.Deref(r)=>pc.get(r)
            case P.Concat(a,b)=>for x<-path(a,pc);y<-path(b,pc) yield x++y
            case _=>None
          var fuel=0
          var counter=0
          def concrete(ps: Set[Vector[Int]])=ps.forall(_.forall(_>0))
          def eval(x: source.Space,pc: PC,sc: SC): Option[Value] =
            fuel-=1
            if fuel<0 then None
            else x match
              case S.Empty=>Some(Known(Set.empty))
              case S.Mention(r)=>sc.get(r)
              case S.Singleton(p)=>path(p,pc).map(p=>Known(Set(p)))
              case S.Literal(v)=>Some(Known(v.paths.map(encode)))
              case S.Union(a,b)=>for case Known(l)<-eval(a,pc,sc);case Known(r)<-eval(b,pc,sc) yield Known(l union r)
              case S.Intersection(a,b)=>
                for
                  case Known(l)<-eval(a,pc,sc);case Known(r)<-eval(b,pc,sc)
                  if concrete(l) && concrete(r)
                yield Known(l intersect r)
              case S.Subtraction(a,b)=>
                for
                  case Known(l)<-eval(a,pc,sc);case Known(r)<-eval(b,pc,sc)
                  if concrete(l) && concrete(r)
                yield Known(l diff r)
              case S.Unwrap(a,p)=>
                for
                  case Known(ps)<-eval(a,pc,sc);prefix<-path(p,pc)
                  if concrete(ps) && prefix.forall(_>0)
                yield Known(ps.filter(_.startsWith(prefix)).map(_.drop(prefix.size)))
              case S.Composition(a,b)=>eval(a,pc,sc).flatMap {
                case Known(l) if l.isEmpty=>Some(Known(Set.empty))
                case Known(l) if concrete(l)=>eval(b,pc,sc).collect {case Known(r)=>Known(for a<-l;b<-r yield a++b)}
                case _=>None
              }
              case S.Iteration(a,h,t,b)=>eval(a,pc,sc).flatMap { value=>
                def run(head: Vector[Int],tail: Value)=eval(b,
                  if h.s=="_" then pc else pc.updated(h,head),if t.s=="_" then sc else sc.updated(t,tail))
                value match
                  case Residual(prefix) if prefix.nonEmpty=>run(prefix.take(1),Residual(prefix.tail))
                  case Residual(_)=>None
                  case Known(ps) if concrete(ps)=>sequence(ps.filter(_.nonEmpty).groupBy(_.head).toVector.map((h,ps)=>run(Vector(h),Known(ps.map(_.tail)))))
                    .filter(_.forall {case Known(_)=>true;case _=>false})
                    .map(rs=>Known(rs.collect {case Known(ps)=>ps}.foldLeft(Set.empty[Vector[Int]])(_ union _)))
                  case _=>None
              }
              case S.Call(c,ps,ss)=>
                for
                  pv<-sequence(ps.map(path(_,pc)));sv<-sequence(ss.map(eval(_,pc,sc)))
                  result<-if c==name then
                    Option.when(counter>0 && pv==Vector(Vector(-1,-2),Vector(counter)) && sv==Vector(Residual(Vector.empty)))(Known(Set(Vector(-3))))
                  else routines.get(c).filter(fn=>fn.refs.size==pv.size && fn.mentions.size==sv.size)
                    .flatMap(fn=>eval(fn.body,(fn.refs zip pv).toMap,(fn.mentions zip sv).toMap))
                yield result
              case _=>None
          val valid=(0 to 255).forall { n=>
            fuel=10000;counter=n
            eval(scan.body,Map(scan.refs(0)->Vector(-1),scan.refs(1)->Vector(n+1)),
              Map(scan.mentions.head->Residual(Vector(-2)))).contains(Known(Set(Vector(if n==0 then -1 else -3))))
          }
          Option.when(valid)(input)
        }
      case _=>None
    def captures(value: Any,h: PathRef,t: SpaceMention): Boolean = value match
      case p: PathRef=>p==h
      case s: SpaceMention=>s==t
      case xs: Iterable[?]=>xs.exists(captures(_,h,t))
      case p: Product=>p.productIterator.exists(captures(_,h,t))
      case _=>false
    x match
      case S.Iteration(heads,h,t,S.Wrap(S.Intersection(S.Mention(r),S.Range(S.TailsUnion(other),0,1)),P.Deref(p)))
          if h.s!="_" && t.s!="_" && r==t && p==h && heads==other && !captures(heads,h,t)=>prove(heads)
      case _=>None

  private def closedPath(p: source.Path, bound: Set[String]): Boolean = p match
    case P.Deref(r) => bound(r.s)
    case P.Constant(_) => true
    case P.Concat(a,b) => closedPath(a,bound)&&closedPath(b,bound)
    case _ => false
  private def closed(s: source.Space,bound: Set[String]): Boolean = s match
    case S.Empty | S.Literal(_) => true
    case S.Singleton(p) => closedPath(p,bound)
    case S.Union(a,b) => closed(a,bound)&&closed(b,bound)
    case S.Intersection(a,b) => closed(a,bound)&&closed(b,bound)
    case S.Subtraction(a,b) => closed(a,bound)&&closed(b,bound)
    case S.Composition(a,b) => closed(a,bound)&&closed(b,bound)
    case S.Iteration(a,h,_,b) => closed(a,bound)&&closed(b,if h.s=="_" then bound else bound+h.s)
    case _ => false

  private enum Value:
    case Known(paths: Set[Vector[Int]])
    case Residual(prefix: Vector[Int]) // arbitrary nonempty set of suffixes after prefix
  private def scanner(root: source.Space,codes: Vector[Vector[Int]]): Option[source.Space] = root match
    case S.Iteration(input,first,tail,body) if first.s!="_" && tail.s!="_" =>
      val visited=mutable.HashMap.empty[RoutinePtr,Int]
      def routine(name: RoutinePtr): Boolean = visited.get(name) match
        case Some(status) => status>=0
        case None => routines.get(name) match
          case Some(fn) if fn.refs.size==1 && fn.mentions.size==1 =>
            visited(name)=0
            val permitted=fn.body match
              case S.Empty => true
              case S.Singleton(p) => refs(p).contains(Vector(fn.refs.head.s))
              case S.Iteration(S.Mention(r),_,_,_) if r==fn.mentions.head =>
                output(fn.body,Vector(fn.refs.head.s),fn.mentions.head)
              case _ => false
            visited(name)=if permitted then 1 else -1
            permitted
          case _ => false
      def output(x: source.Space, accumulated: Vector[String], residual: SpaceMention): Boolean =
        choice(x) match
          case Some((test,yes,no)) => closed(test,accumulated.toSet)&&output(yes,accumulated,residual)&&output(no,accumulated,residual)
          case None => x match
            case S.Empty => true
            case S.Singleton(p) => refs(p).contains(accumulated)
            case S.Union(a,b) => output(a,accumulated,residual)&&output(b,accumulated,residual)
            case S.Iteration(S.Mention(r),h,t,next) if r==residual && h.s!="_" && t.s!="_" && !accumulated.contains(h.s) && t!=residual =>
              output(next,accumulated:+h.s,t)
            case S.Call(name,Vector(p),Vector(S.Mention(r))) if r==residual && refs(p).contains(accumulated) => routine(name)
            case _ => false
      if !output(body,Vector(first.s),tail) then None
      else
        import Value.*
        type PC=Map[PathRef,Vector[Int]]
        type SC=Map[SpaceMention,Value]
        def p(x: source.Path,pc: PC): Option[Vector[Int]] = x match
          case P.Constant(v) => Some(encode(v))
          case P.Deref(r) => pc.get(r)
          case P.Concat(a,b) => for x<-p(a,pc);y<-p(b,pc) yield x++y
          case _ => None
        def sequence[A](xs: Vector[Option[A]]): Option[Vector[A]] =
          if xs.forall(_.nonEmpty) then Some(xs.map(_.get)) else None
        var remaining=0
        def s(x: source.Space,pc: PC,sc: SC): Option[Value] =
          remaining-=1
          if remaining<0 then None
          else x match
            case S.Empty => Some(Known(Set.empty))
            case S.Mention(r) => sc.get(r)
            case S.Singleton(v) => p(v,pc).map(v=>Known(Set(v)))
            case S.Literal(v) => Some(Known(v.paths.map(encode)))
            case S.Union(a,b) => for case Known(left)<-s(a,pc,sc);case Known(right)<-s(b,pc,sc) yield Known(left union right)
            case S.Intersection(a,b) => for case Known(left)<-s(a,pc,sc);case Known(right)<-s(b,pc,sc) yield Known(left intersect right)
            case S.Subtraction(a,b) => for case Known(left)<-s(a,pc,sc);case Known(right)<-s(b,pc,sc) yield Known(left diff right)
            case S.Composition(a,b) => s(a,pc,sc).flatMap {
              case Known(left) if left.isEmpty => Some(Known(Set.empty))
              case Known(left) => s(b,pc,sc).collect {case Known(right)=>Known(for l<-left;r<-right yield l++r)}
              case _ => None
            }
            case S.Iteration(a,h,t,b) => s(a,pc,sc).flatMap { value =>
              def run(head: Vector[Int],rest: Value): Option[Value] =
                s(b,if h.s=="_" then pc else pc.updated(h,head),if t.s=="_" then sc else sc.updated(t,rest))
              value match
                case Residual(prefix) if prefix.nonEmpty => run(prefix.take(1),Residual(prefix.tail))
                case Residual(_) => None // inspecting opaque suffixes is forbidden
                case Known(paths) =>
                  val groups=paths.filter(_.nonEmpty).groupBy(_.head).toVector
                  sequence(groups.map((h,ps)=>run(Vector(h),Known(ps.map(_.tail))))).flatMap { results =>
                    if results.forall {case Known(_)=>true;case _=>false} then
                      Some(Known(results.collect {case Known(ps)=>ps}.foldLeft(Set.empty[Vector[Int]])(_ union _)))
                    else None
                  }
            }
            case S.Call(name,ps,ss) => routines.get(name).flatMap { fn =>
              for
                pv<-sequence(ps.map(p(_,pc)))
                sv<-sequence(ss.map(s(_,pc,sc)))
                result<-s(fn.body,(fn.refs zip pv).toMap,(fn.mentions zip sv).toMap)
              yield result
            }
            case _ => None
        val valid=codes.forall { code =>
          remaining=100000
          code.nonEmpty && s(body,Map(first->code.take(1)),Map(tail->Residual(code.tail))).contains(Known(Set(code)))
        }
        Option.when(valid)(input)
    case _ => None
  def plan(x: source.Space): Option[Plan] = x match
    case S.Call(name,Vector(),Vector(argument)) => routines.get(name).flatMap { fn =>
      selector(fn.body).filter((ref,_)=>fn.refs.isEmpty && fn.mentions==Vector(ref)).map { (_,values) =>
        val codes=values.map(encode)
        val input=scanner(argument,codes)
        Plan(input.getOrElse(argument),codes,input.nonEmpty)
      }
    }.orElse(selector(x).map((ref,values)=>Plan(S.Mention(ref),values.map(encode),false)))
    case _ => selector(x).map((ref,values)=>Plan(S.Mention(ref),values.map(encode),false))
