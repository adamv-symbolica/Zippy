package morkl

/** Durable benchmark of the STABLE-ID route (interner-4 with a frozen vocabulary) against the source
 *  symbol evaluator, at 1,000 / 4,000 / 20,000 input paths.  Single runs after one warm-up: elapsed
 *  milliseconds, evaluator work (AST nodes), and heap in use after the run — diagnostic, not a
 *  statistically controlled claim.   sbt "runMain morkl.ZippyCodedBench" */
object ZippyCodedBench:
  private val s = SpaceMention("s")
  private val a = PathRef("a"); private val h = PathRef("h"); private val t = SpaceMention("t")
  private def programs: Vector[(String, SymSpace)] = Vector(
    "tails" -> ZSpace.TailsUnion(ZSpace.Mention(s)),
    "range[10,20)" -> ZSpace.Range(ZSpace.Mention(s), 10, 20),
    "fold unchanged acc" -> ZSpace.Fold(ZSpace.Mention(s), ZPath.Constant(Vector()), a, h, t, ZSpace.Mention(t), ZPath.Deref(a)),
    "fold changing acc" -> ZSpace.Fold(ZSpace.Mention(s), ZPath.Constant(Vector()), a, h, t,
      ZSpace.Wrap(ZSpace.Mention(t), ZPath.Deref(a)), ZPath.Deref(h)))
  private def time[T](f: => T): (T, Double) = { val t0 = System.nanoTime(); val r = f; (r, (System.nanoTime() - t0) / 1e6) }

  def main(args: Array[String]): Unit =
    val rt = Runtime.getRuntime
    println("program\tpaths\tsource_ms\ttarget_ms\ttarget_steps\theap_mb\tequal")
    for round <- 0 until 2; n <- Vector(1000, 4000, 20000) do
      val heads = (0 until math.max(1, n / 4)).map(i => f"k$i%06d").toVector
      val rng = scala.util.Random(n)
      val in = Set.from(Iterator.continually(Vector(heads(rng.nextInt(heads.length)), heads(rng.nextInt(heads.length)))).take(n))
      val vocab = heads; vocab.reverse.foreach(Interner.intern)       // interning order opposite to symbol order
      val code = Codes.InternerId4.ordered(vocab)
      val inT = in.map(p => ByteRepr.fromAtoms(code.encPath(p)))
      for (name, prog) <- programs do
        val (exp, ms0) = time(ZEval.syms()(prog, sc = Map(s -> in)))
        val (m, rs) = CodedTranslation(code, prog)
        val ev = ZEval.bytes(rs)
        val (got, ms1) = time(ev(m, sc = Map(s -> inT)))
        val ok = got == exp.map(p => ByteRepr.fromAtoms(code.encPath(p)))
        if round == 1 then
          println(f"$name\t$n\t$ms0%.1f\t$ms1%.1f\t${ev.steps}\t${(rt.totalMemory - rt.freeMemory) / 1048576}\t$ok")
        if !ok then sys.exit(1)
