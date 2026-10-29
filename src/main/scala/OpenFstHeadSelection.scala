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
  private def choice(s: source.Space): Option[(source.Space,source.Space,source.Space)] = s match
    case S.Union(S.Composition(test,yes),S.Composition(S.Subtraction(eps,other),no)) if epsilon(eps) && test==other =>
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
