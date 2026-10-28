package morkl

import munit.FunSuite
import morkl.Syntax.{*, given}
import scala.language.implicitConversions

/** Executes the repository's actual cornerstone syntax through the new backend. */
class AlphabetExamplesTest extends FunSuite:
  private def symbolsIn(s: Space): Set[PathItem] =
    val out = scala.collection.mutable.Set.empty[PathItem]
    AlphabetSyntax.map[PathValue, PathValue](SymbolAlphabet.fromSpace(s))({
      case AlphabetPath.Constant(p) => out ++= p.items; None
      case _ => None
    }, {
      case AlphabetSpace.Literal(ps) => ps.foreach(p => out ++= p.items); None
      case _ => None
    }, identity, identity)
    out.toSet

  private def check(s: Space, functions: Vector[Routine], frame: Map[SpaceMention, SpaceValue] = Map.empty): Unit =
    val interner = new PathItemInterner
    val alphabet = symbolsIn(s) ++ functions.flatMap(r => symbolsIn(r.body)) ++ frame.values.flatMap(_.paths.flatMap(_.items))
    alphabet.toVector.sortBy(_.show).reverse.foreach(interner.intern)
    val expected = eval(s)(using PathContext.emptyMap, SpaceContextMap(frame), functions.map(r => r.name -> r).toMap).paths
    assert(expected.nonEmpty, "real-world fixture must be non-vacuous")
    def through[T](code: AlphabetEncoding[PathValue, T]): Unit =
      val translated = AlphabetTranslation.symbols(s, code, functions)
      val actual = translated.eval(spaces = frame.view.mapValues(_.paths.map(code.encode)).toMap).map(code.decode)
      assertEquals(actual, expected)
    through(AlphabetEncodings.internedBits(interner))
    through(AlphabetEncodings.internedBytes(interner))
    through(AlphabetEncodings.stringBytes)

  test("Aunt query uses the real routine and open family/people frames") {
    val routine = Routines.aunt_query_routine
    check(Space.Call(routine.name, Vector.empty, routine.mentions.map(Space.Mention(_))), Vector(routine), AuntQuery.context.m)
  }

  test("semi-naive Datalog converges through the existing recursive self-union convention") {
    val facts = SpaceValue("edge.a.b", "edge.b.c", "edge.c.d", "edge.a.z")
    val routine = DatalogExample.semiNaiveTransitive
    val initial = DatalogExample.semiNaiveInitial(Space.Literal(facts))
    check(routine.name(initial)("complete.path"), Vector(routine))
  }

  test("sliding puzzle runs all four pure 2x2 move functions") {
    val routines = SlidingPuzzleExample.routines(2)
    val initial = Space.Literal(SpaceValue(SlidingPuzzleExample.pathState(2, Vector(0, 1, 2, 3))))
    check(SlidingPuzzleExample.step(2, initial), routines.toVector)
  }
