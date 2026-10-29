package morkl.finite

import morkl.{Path as SymbolPath, Space as SymbolSpace, Routine as SymbolRoutine, *}
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable

/** Optional whole-suite differential gate. Only host-boundary values are
  * recorded, to replay existing callbacks without repeating their side effects.
  * None of this state belongs to the languages or their reference backends. */
object TranslationAudit:
  val enabled: Boolean = java.lang.Boolean.getBoolean("morkl.translationAudit")
  private final class Observation:
    var replay = false
    val callbacks = java.util.IdentityHashMap[AnyRef, mutable.Map[Any, Any]]()
    val adapters = java.util.IdentityHashMap[AnyRef, java.lang.Boolean]()
  private val current = new ThreadLocal[Observation]()
  private val observations = AtomicLong(0)
  private val comparisons = AtomicLong(0)
  if enabled then Runtime.getRuntime.addShutdownHook(new Thread(() =>
    println(s"Zippy translation audit: ${observations.get()} source evaluations, ${comparisons.get()} successful route comparisons")
  ))

  def adapter[I, O](f: I => O): I => O =
    val state = current.get()
    if state != null then state.adapters.put(f, true)
    f

  def callback[I, O](f: AnyRef, input: I)(compute: => O): O =
    val state = current.get()
    if state == null || (state.replay && state.adapters.containsKey(f)) then compute
    else
      var values = state.callbacks.get(f)
      if values == null then
        values = mutable.Map.empty[Any, Any]
        state.callbacks.put(f, values)
      if state.replay then
        values.getOrElse(input, throw IllegalStateException("translation requested an unobserved grounded callback input")).asInstanceOf[O]
      else
        val value = compute
        values(input) = value
        value

  def evaluate(s: SymbolSpace)(work: => SpaceValue)(using pc: PathContext, sc: SpaceContext,
      rc: PartialFunction[RoutinePtr, SymbolRoutine]): SpaceValue =
    if !enabled || current.get() != null then work
    else
      val state = Observation()
      current.set(state)
      try
        val expected = work
        state.replay = true
        val expression = Symbols.fromSpace(s)
        val free = FreeVariables.space(expression)
        val ps = free.paths.iterator.map(r => r -> pc.resolve(r)).toMap
        val ss = free.spaces.iterator.map(r => r -> sc.resolve(r)).toMap
        val routines = Symbols.reachable(s, rc)
        val alphabet = mutable.Set.empty[PathItem]
        def collect(value: Any): Unit = value match
          case p: PathValue => alphabet ++= p.items
          case s: SpaceValue => s.paths.foreach(p => alphabet ++= p.items)
          case _ => ()
        collect(expected)
        val iterator = state.callbacks.values().iterator()
        while iterator.hasNext do iterator.next().foreach((input, output) => { collect(input); collect(output) })
        def check(actual: SpaceValue, route: String): Unit =
          require(actual == expected, s"$route translation mismatch for ${s.show}\nexpected ${expected.pretty}\nactual ${actual.pretty}")
          comparisons.incrementAndGet()
        val bits = ZippyTranslations.symbolsToBits(s, ps, ss, routines, alphabet)
        val bitValue = bits.run()
        check(bits.decode(bitValue), "symbols -> Fibonacci bits")
        val bytes = ZippyTranslations.stringsToBytes(s, ps, ss, routines)
        val byteValue = bytes.run()
        check(bytes.decode(byteValue), "strings -> terminated bytes")
        check(ZippyTranslations.internedStringsToBytes(s, ps, ss, routines, alphabet).runDecoded(), "interned symbols -> four bytes")
        val b2y = ZippyTranslations.bitsToBytes(bits.compiled.expression, bits.compiled.routines)
        val bv = ByteSpace.evaluator(b2y.expression,
          bits.pathInputs.view.mapValues(p => ByteStorage.path(b2y.code.encode(BitStorage.items(p)))).toMap,
          bits.spaceInputs.view.mapValues(v => ByteSpace.fromPaths(v.vectors.map(b2y.code.encode))).toMap, b2y.routines)
        check(Symbols.value(bits.compiled.decodePaths(b2y.decodePaths(bv.vectors))), "bits -> bytes")
        val y2b = ZippyTranslations.bytesToBits(bytes.compiled.expression, bytes.compiled.routines)
        val yv = BitSpace.evaluator(y2b.expression,
          bytes.pathInputs.view.mapValues(p => BitStorage.path(y2b.code.encode(p.unsigned))).toMap,
          bytes.spaceInputs.view.mapValues(v => BitSpace.fromPaths(v.vectors.map(y2b.code.encode))).toMap, y2b.routines)
        check(Symbols.value(bytes.compiled.decodePaths(y2b.decodePaths(yv.vectors))), "bytes -> eight bits")
        def symbols[S, P, V](source: SymbolTranslation[S, P, V], injection: Code[S, PathItem], label: String): Unit =
          val target = Translator(injection)(source.compiled.expression, source.compiled.routines)
          val pctx = PathContextMap(source.pathInputs.view.mapValues(p => PathValue(injection.encode(source.storage.items(p)).toList)).toMap)
          val sctx = SpaceContextMap(source.spaceInputs.view.mapValues(v => Symbols.value(source.storage.paths(v).map(p => injection.encode(source.storage.items(p))))).toMap)
          val defs = target.routines.view.mapValues(Symbols.toRoutine).toMap
          val value = eval(Symbols.toSpace(target.expression))(using pctx, sctx, defs)
          check(Symbols.value(source.compiled.decodePaths(target.decodePaths(Symbols.paths(value)))), label)
        symbols(bits, Codes.bitsToSymbols, "bits -> two symbols")
        symbols(bytes, Codes.bytesToSymbols, "bytes -> 256 symbols")
        observations.incrementAndGet()
        expected
      finally current.remove()
