package morkl
@main def graphCalleeProbe(): Unit =
  val input=SpaceMention("input");val h=PathRef("h");val t=SpaceMention("t")
  val callee=Routine(RoutinePtr("double"),Vector.empty,Vector(input),Space.Iteration(Space.Mention(input),h,t,Space.Singleton(Path.Concat(Path.Deref(h),Path.Deref(h)))))
  val entry=Routine(RoutinePtr("entry"),Vector.empty,Vector(input),Space.Call(callee.name,Vector.empty,Vector(Space.Mention(input))))
  val data=Set(PathValue(List(PathItem("a"))),PathValue(List(PathItem("b"),PathItem("a"))))
  val compiled=OpenFstBackend.fromGraph(optimize(transpile(entry)),Map("double"->optimize(transpile(callee))))
  assert(compiled.eval(spaces=Map(input->data))==Set(PathValue(List(PathItem("a"),PathItem("a"))),PathValue(List(PathItem("b"),PathItem("b")))))
  val rec=Routine(RoutinePtr("rec"),Vector.empty,Vector(input),Space.Union(Space.Mention(input),Space.Call(RoutinePtr("rec"),Vector.empty,Vector(Space.Mention(input)))))
  val graph=optimize(transpile(rec))
  assert(OpenFstBackend.fromGraph(graph,Map("rec"->graph)).eval(spaces=Map(input->data))==data)
  println("direct graph callees and stationary graph recursion passed")
