package morkl

import munit.FunSuite
import scala.collection.immutable.ArraySeq

/** Zippy_symbols / Zippy_bits / Zippy_bytes: every translation in Encodings.scala, run on the real
 *  reference backends (`eval` for symbols, `WEval` over `Set[BigInt]` and `Set[ArraySeq[Byte]]`)
 *  and decoded back, on the whole 1000-program corpus plus hand-written routine/fixpoint/fold
 *  programs.  Every route must agree with `eval` on the original program. */
class EncodingsTest extends FunSuite:
  override val munitTimeout = scala.concurrent.duration.Duration(30, "min")
  type Sym = List[String]
  type Bytes = ArraySeq[Byte]

  /** symbols beyond the fuzzer's a..d: multi-byte UTF-8, a long name, the empty symbol */
  val extra = Vector("parent", "Zoë", "", "ab", "z")
  val A = SpaceFuzzer.alphabet ++ extra

  def randPath(rng: java.util.Random): PathValue = PathValue(List.fill(1 + rng.nextInt(3))(A(rng.nextInt(A.length))))
  def randSpace(rng: java.util.Random): SpaceValue =
    SpaceValue((0 until rng.nextInt(9)).map(_ => if rng.nextInt(12) == 0 then PathValue(Nil) else randPath(rng)).toSet)

  case class Env(pc: Map[PathRef, Sym], sc: Map[SpaceMention, Set[Sym]]):
    def symbols: Set[String] = pc.valuesIterator.flatten.toSet ++ sc.valuesIterator.flatMap(_.flatten)

  /** run `p` over `code`'s target, with encoded inputs, and decode the result */
  def via[S, T](code: Code[S, T], p: WProgram[S], pc: Map[PathRef, S], sc: Map[SpaceMention, Set[S]])
      : (WProgram[T], Map[PathRef, T], Map[SpaceMention, Set[T]]) =
    val (q, _) = CodeTranslate(code, p)
    (q, pc.map((k, v) => k -> code.encode(v)), sc.map((k, v) => k -> code.encodeSet(v)))

  var unsupported = 0
  /** all routes; returns the number of routes checked */
  def check(label: String, prog: WProgram[Sym], env: Env, expected: Set[Sym]): Int =
    var n = 0
    def agree(route: String, got: => Set[Sym]): Unit =
      try
        val g = got
        assertEquals(g, expected, s"$label via $route\nprogram: ${WSpaceOps.toSpace(prog.main).show}")
        n += 1
      catch case CodeTranslate.Unsupported(_) => unsupported += 1
    val universe = Codes.symbolsOf(prog.main) ++ prog.routines.valuesIterator.flatMap(r => Codes.symbolsOf(r.body)) ++ env.symbols
    // 0. the generic evaluator over the symbol alphabet is `eval`
    agree("WEval[symbols]", WEval(prog).run(env.pc, env.sc))
    // 1. interned symbols -> bits (Fibonacci), then the bits backend
    val fib = Codes.fibonacciBits(universe)
    lazy val (bp, bpc, bsc) = via(fib, prog, env.pc, env.sc)
    agree("symbols->bits(fib)", fib.decodeSet(WEval(bp).run(bpc, bsc)))
    // 2. string symbols -> bytes (\0-terminated), then the bytes backend
    val nul = Codes.nulTerminatedBytes(universe)
    lazy val (yp, ypc, ysc) = via(nul, prog, env.pc, env.sc)
    agree("symbols->bytes(\\0)", nul.decodeSet(WEval(yp).run(ypc, ysc)))
    // 3. interned symbols -> 4 fixed bytes
    val i4 = Codes.internedBytes(universe)
    lazy val (ip, ipc, isc) = via(i4, prog, env.pc, env.sc)
    agree("symbols->bytes(interned4)", i4.decodeSet(WEval(ip).run(ipc, isc)))
    // 4. bytes -> bits (one byte = eight bits), on top of 2
    lazy val (b8, b8pc, b8sc) = via(Codes.bytesToBits, yp, ypc, ysc)
    agree("symbols->bytes(\\0)->bits(8)", nul.decodeSet(Codes.bytesToBits.decodeSet(WEval(b8).run(b8pc, b8sc))))
    // 5. bits -> bytes injection, on top of 1
    lazy val (bb, bbpc, bbsc) = via(Codes.bitsToBytes, bp, bpc, bsc)
    agree("symbols->bits(fib)->bytes(0/1)", fib.decodeSet(Codes.bitsToBytes.decodeSet(WEval(bb).run(bbpc, bbsc))))
    // 6. bits -> symbols injection, on top of 1, run by the ORIGINAL symbol evaluator `eval`
    lazy val (bs, bspc, bssc) = via(Codes.bitsToSymbols, bp, bpc, bsc)
    agree("symbols->bits(fib)->symbols(0/1) [eval]", {
      val rc = bs.routines.map((k, r) => k -> WSpaceOps.toRoutine(r))
      val out = eval(WSpaceOps.toSpace(bs.main))(using PathContextMap(bspc.map((k, v) => k -> PathValue(v))),
        SpaceContextMap(bssc.map((k, v) => k -> SpaceValue(v.map(PathValue(_))))), rc)
      fib.decodeSet(Codes.bitsToSymbols.decodeSet(out.paths.map(_.items)))
    })
    // 7. bytes -> symbols injection, on top of 3, run by `eval`
    lazy val (ys, yspc, yssc) = via(Codes.bytesToSymbols, ip, ipc, isc)
    agree("symbols->bytes(interned4)->symbols(hex) [eval]", {
      val rc = ys.routines.map((k, r) => k -> WSpaceOps.toRoutine(r))
      val out = eval(WSpaceOps.toSpace(ys.main))(using PathContextMap(yspc.map((k, v) => k -> PathValue(v))),
        SpaceContextMap(yssc.map((k, v) => k -> SpaceValue(v.map(PathValue(_))))), rc)
      i4.decodeSet(Codes.bytesToSymbols.decodeSet(out.paths.map(_.items)))
    })
    n

  def reference(prog: WProgram[Sym], env: Env): Set[Sym] =
    val rc = prog.routines.map((k, r) => k -> WSpaceOps.toRoutine(r))
    eval(WSpaceOps.toSpace(prog.main))(using PathContextMap(env.pc.map((k, v) => k -> PathValue(v))),
      SpaceContextMap(env.sc.map((k, v) => k -> SpaceValue(v.map(PathValue(_))))), rc).paths.map(_.items)

  test("codes are prefix-free, order-preserving and round-trip") {
    val u = A.toSet
    val rng = new java.util.Random(3)
    val words = Vector.fill(300)(randPath(rng).items).distinct
    val sorted = words.sorted(using SymWords.ordering)
    def checkCode[T](c: Code[Sym, T]): Unit =
      for w <- words do assertEquals(c.decode(c.encode(w)), w, c.name)
      assertEquals(sorted.map(c.encode), words.map(c.encode).sorted(using c.tgt.ordering), s"${c.name} order")
      val cws = A.map(s => c.encode(List(s)))
      for x <- cws; y <- cws if x != y do assert(!c.tgt.startsWith(x, y), s"${c.name}: prefix")
    checkCode(Codes.fibonacciBits(u)); checkCode(Codes.nulTerminatedBytes(u)); checkCode(Codes.internedBytes(u))
    assertEquals(Codes.fibonacci(1), Vector(true, true))
    assertEquals(Codes.fibonacci(4), Vector(true, false, true, true))
  }

  test("all corpus programs agree on every translation route") {
    val recs = Corpus.load()
    val rng = new java.util.Random(2026)
    var routes = 0; var progs = 0
    for (rec, idx) <- recs.zipWithIndex do
      val prog = WProgram(SymWords, WSpaceOps.fromSpace(rec.prog), Map.empty)
      for _ <- 0 until 3 do
        val env = Env((0 until rec.nPath).map(j => PathRef("p" + j) -> randPath(rng).items).toMap,
                      (0 until rec.nSpace).map(i => SpaceMention("s" + i) -> randSpace(rng).paths.map(_.items)).toMap)
        routes += check(s"corpus[$idx]", prog, env, reference(prog, env))
      progs += 1
    println(s"ENCODINGS: $progs corpus programs x 3 environments, $routes route checks agreed, $unsupported unsupported")
    assertEquals(progs, recs.length)
  }

  test("routines, fixpoints, folds and recursion through every route") {
    import Space.*
    def P(s: String) = Path.Deref(PathRef(s)); def M(s: String) = Mention(SpaceMention(s))
    def C(xs: String*) = Path.Constant(PathValue(xs.toList))
    // transitive closure of an edge relation, as a Fixpoint and as a self-recursive routine
    val edges = SpaceMention("e")
    val tc = Fixpoint(M("e"), SpaceMention("acc"),
      Iteration(M("acc"), PathRef("x"), SpaceMention("xs"),
        Wrap(Iteration(M("xs"), PathRef("y"), SpaceMention("_"),
          Unwrap(Restriction(M("e"), Singleton(P("y"))), P("y"))), P("x"))))
    val reach = Routine(RoutinePtr("reach"), Vector(PathRef("from")), Vector(SpaceMention("g"), SpaceMention("seen")),
      Union(M("seen"), Iteration(Unwrap(Restriction(M("g"), Singleton(P("from"))), P("from")), PathRef("n"), SpaceMention("_"),
        Call(RoutinePtr("reachStep"), Vector(P("n")), Vector(M("g"), M("seen"))))))
    val reachStep = Routine(RoutinePtr("reachStep"), Vector(PathRef("n")), Vector(SpaceMention("g"), SpaceMention("seen")),
      Iteration(Subtraction(Singleton(P("n")), M("seen")), PathRef("m"), SpaceMention("_"),
        Call(RoutinePtr("reach"), Vector(P("m")), Vector(M("g"), Union(M("seen"), Singleton(P("m")))))))
    val heads = TailsIntersection(Composition(M("e"), M("e")))
    val tails = TailsUnion(TailsUnion(Union(M("e"), Singleton(C("parent", "Zoë", "ab")))))
    val ranged = Range(Union(M("e"), Literal(SpaceValue(Set(PathValue(List("", "z")), PathValue(Nil))))), 2, 4)
    val nested = Iteration(M("e"), PathRef("a"), SpaceMention("as"),
      Iteration(M("as"), PathRef("b"), SpaceMention("bs"),
        Union(Singleton(Path.Concat(P("b"), P("a"))), Wrap(TailsIntersection(Union(M("bs"), M("e"))), P("a")))))
    val fold = Fold(M("e"), C(), PathRef("acc"), PathRef("h"), SpaceMention("t"),
      Wrap(M("t"), Path.Concat(P("acc"), P("h"))), Path.Concat(P("h"), P("acc")))
    val rng = new java.util.Random(11)
    val progs = Vector(
      "transitive" -> WProgram(SymWords, WSpaceOps.fromSpace(tc), Map.empty),
      "reach" -> WProgram(SymWords, WSpaceOps.fromSpace(Call(RoutinePtr("reach"), Vector(C("a")), Vector(M("e"), Singleton(C("a"))))),
        Map(reach.name -> WSpaceOps.fromRoutine(reach), reachStep.name -> WSpaceOps.fromRoutine(reachStep))),
      "tailsIntersection" -> WProgram(SymWords, WSpaceOps.fromSpace(heads), Map.empty),
      "tailsUnion" -> WProgram(SymWords, WSpaceOps.fromSpace(tails), Map.empty),
      "range" -> WProgram(SymWords, WSpaceOps.fromSpace(ranged), Map.empty),
      "nested" -> WProgram(SymWords, WSpaceOps.fromSpace(nested), Map.empty),
      "fold" -> WProgram(SymWords, WSpaceOps.fromSpace(fold), Map.empty))
    var routes = 0
    for (name, p) <- progs; _ <- 0 until 25 do
      val es = (0 until rng.nextInt(7)).map(_ => List(A(rng.nextInt(A.length)), A(rng.nextInt(A.length)))).toSet
      val env = Env(Map.empty, Map(edges -> es))
      routes += check(name, p, env, reference(p, env))
    println(s"ENCODINGS: hand-written programs, $routes route checks agreed, $unsupported unsupported (Fold over multi-letter codes)")
    // every route over a multi-letter code rejects the Fold program; all other programs pass all 8 routes
    assertEquals(routes, 25 * (6 * 8 + 1))
  }

  test("depth bound: walk recursion never exceeds the universe's longest codeword") {
    import Space.*
    val u = A.toSet
    val fib = Codes.fibonacciBits(u)
    val prog = WProgram(SymWords, WSpaceOps.fromSpace(Iteration(Mention(SpaceMention("e")), PathRef("h"), SpaceMention("_"),
      Singleton(Path.Deref(PathRef("h"))))), Map.empty)
    lazy val (bp, t) = CodeTranslate(fib, prog)
    val maxD = bp.routines.keysIterator.flatMap(r => "_d(\\d+)$".r.findFirstMatchIn(r.s).map(_.group(1).toInt)).max
    assert(maxD < fib.maxDepth, s"walk depth $maxD vs bound ${fib.maxDepth}")
    assertEquals(t.walkRoutines, bp.routines.size - 1)
    println(s"ENCODINGS: fib universe |U|=${u.size}, longest codeword ${fib.maxDepth} bits, ${t.walkRoutines} walk routines")
  }
