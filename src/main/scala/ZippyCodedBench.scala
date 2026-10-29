package morkl

/** Durable benchmark of the STABLE-ID route (interner-4 with a frozen vocabulary) against the ORIGINAL
 *  symbol evaluator `eval`, at 1,000 / 4,000 / 20,000 input paths.  Compilation and encoding are
 *  outside timing; times are warm medians of 3 runs after a warm-up; `alloc_mb` is the bytes the
 *  evaluating thread allocated in one target run (a direct measure of copying, unlike heap-after).
 *    sbt "runMain morkl.ZippyCodedBench" */
object ZippyCodedBench:
  private val s = SpaceMention("s")
  private val a = PathRef("a"); private val h = PathRef("h"); private val t = SpaceMention("t")
  private def programs: Vector[(String, SymSpace)] = Vector(
    "tails" -> ZSpace.TailsUnion(ZSpace.Mention(s)),
    "range[10,20)" -> ZSpace.Range(ZSpace.Mention(s), 10, 20),
    "fold unchanged acc" -> ZSpace.Fold(ZSpace.Mention(s), ZPath.Constant(Vector()), a, h, t, ZSpace.Mention(t), ZPath.Deref(a)),
    "fold prev-head acc" -> ZSpace.Fold(ZSpace.Mention(s), ZPath.Constant(Vector()), a, h, t,
      ZSpace.Singleton(ZPath.Concat(ZPath.Deref(a), ZPath.Deref(h))), ZPath.Deref(h)),
    "fold changing acc" -> ZSpace.Fold(ZSpace.Mention(s), ZPath.Constant(Vector()), a, h, t,
      ZSpace.Wrap(ZSpace.Mention(t), ZPath.Deref(a)), ZPath.Deref(h)))
  private def median[T](f: => T): (T, Double) =
    f
    val runs = (0 until 3).map { _ => val t0 = System.nanoTime(); val r = f; (r, (System.nanoTime() - t0) / 1e6) }
    runs.sortBy(_._2).apply(1)

  def main(args: Array[String]): Unit =
    val mx = java.lang.management.ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]
    val tid = Thread.currentThread.getId
    println("program\tpaths\teval_ms\ttarget_ms\tratio\ttarget_steps\talloc_mb\tequal")
    for n <- Vector(1000, 4000, 20000) do
      val heads = (0 until math.max(1, n / 4)).map(i => f"k$i%06d").toVector
      val rng = scala.util.Random(n)
      val in = Set.from(Iterator.continually(Vector(heads(rng.nextInt(heads.length)), heads(rng.nextInt(heads.length)))).take(n))
      val vocab = heads; vocab.reverse.foreach(Interner.intern)       // interning order opposite to symbol order
      val code = Codes.InternerId4.ordered(vocab)
      val inT = in.map(p => ByteRepr.fromAtoms(code.encPath(p)))
      for (name, prog) <- programs do
        val orig = ZSym.to(prog); val inV = ZSym.toV(in)
        val (expV, ms0) = median(eval(orig)(using PathContextMap(Map()), SpaceContextMap(Map(s -> inV)), Map()))
        val exp = ZSym.fromV(expV)
        val (m, rs) = CodedTranslation(code, prog)
        val ev = ZEval.bytes(rs)
        val (got, ms1) = median(ev(m, sc = Map(s -> inT)))
        val steps = ev.steps / 4
        val a0 = mx.getThreadAllocatedBytes(tid); ev(m, sc = Map(s -> inT)); val alloc = mx.getThreadAllocatedBytes(tid) - a0
        val ok = got == exp.map(p => ByteRepr.fromAtoms(code.encPath(p)))
        println(f"$name\t$n\t$ms0%.1f\t$ms1%.1f\t${ms1 / math.max(ms0, 0.05)}%.1f\t$steps\t${alloc / 1048576.0}%.1f\t$ok")
        if !ok then sys.exit(1)
