package morkl

/** Entry points for the existing symbol AST and for both finite alphabets. */
object ZippyTranslations:
  private def symbolProgram[B](entry: Space, interner: PathItemInterner,
      routines: Map[RoutinePtr, Routine], make: PathItemInterner => WordEncoding[PathValue, B],
      unrollBounded: Boolean): (WordEncoding[PathValue, B], WordProgram[B]) =
    // Register all syntactic constants before closing the compilation alphabet.
    // Open input and callback-produced symbols must also be registered by the caller.
    def registerPath(p: Path): Path = p match
      case Path.Constant(v) => interner.encode(v); p
      case _ => p
    subs(entry)(ppre = { case p @ Path.Constant(_) => registerPath(p) })
    routines.values.foreach(r => subs(r.body)(ppre = { case p @ Path.Constant(_) => registerPath(p) }))
    // Literals do not contain Path.Constant nodes.
    def registerLiteral(s: Space): Space = s match
      case Space.Literal(v) => v.paths.foreach(interner.encode); s
      case _ => s
    subs(entry)(spre = { case s @ Space.Literal(_) => registerLiteral(s) })
    routines.values.foreach(r => subs(r.body)(spre = { case s @ Space.Literal(_) => registerLiteral(s) }))
    val code = make(interner)
    val program = code.compile(SymbolWordLanguage.space(entry),
      routines.view.mapValues(r => SymbolWordLanguage.routine(r)).toMap, unrollBounded)
    code -> program

  def symbolsToBits(entry: Space, interner: PathItemInterner = new PathItemInterner,
      routines: Map[RoutinePtr, Routine] = Map.empty, unrollBounded: Boolean = true):
      (WordEncoding[PathValue, BigInt], WordProgram[BigInt]) =
    symbolProgram(entry, interner, routines, WordEncodings.internedSymbolsToBits, unrollBounded)

  def stringsToBytes(entry: Space, interner: PathItemInterner = new PathItemInterner,
      routines: Map[RoutinePtr, Routine] = Map.empty, unrollBounded: Boolean = true):
      (WordEncoding[PathValue, BytePathValue], WordProgram[BytePathValue]) =
    symbolProgram(entry, interner, routines, i => WordEncodings.stringSymbolsToBytes(i), unrollBounded)

  def internedStringsToBytes(entry: Space, interner: PathItemInterner = new PathItemInterner,
      routines: Map[RoutinePtr, Routine] = Map.empty, unrollBounded: Boolean = true):
      (WordEncoding[PathValue, BytePathValue], WordProgram[BytePathValue]) =
    symbolProgram(entry, interner, routines, WordEncodings.internedSymbolsToBytes, unrollBounded)

  def bitsToBytes(entry: BitSpace, routines: Map[RoutinePtr, BitRoutine] = Map.empty): WordProgram[BytePathValue] =
    WordEncodings.bitsToBytes.compile(entry, routines)
  def bytesToBits(entry: ByteSpace, routines: Map[RoutinePtr, ByteRoutine] = Map.empty): WordProgram[BigInt] =
    WordEncodings.bytesToBits.compile(entry, routines)

  case class SymbolProgram(entry: Space, routines: Map[RoutinePtr, Routine]):
    def eval(paths: Map[PathRef, PathValue] = Map.empty, spaces: Map[SpaceMention, SpaceValue] = Map.empty): SpaceValue =
      morkl.eval(entry)(using PathContextMap(paths), SpaceContextMap(spaces), routines)
  private def symbols(program: WordProgram[PathValue]): SymbolProgram =
    SymbolProgram(SymbolWordLanguage.symbolSpace(program.entry),
      program.routines.view.mapValues(SymbolWordLanguage.symbolRoutine).toMap)

  def bitsToSymbols(entry: BitSpace, routines: Map[RoutinePtr, BitRoutine] = Map.empty,
      falseSymbol: PathItem = PathItem("false"), trueSymbol: PathItem = PathItem("true")):
      (WordEncoding[BigInt, PathValue], SymbolProgram) =
    val code = WordEncodings.bitsToSymbols(falseSymbol, trueSymbol)
    code -> symbols(code.compile(entry, routines))
  def bytesToSymbols(entry: ByteSpace, routines: Map[RoutinePtr, ByteRoutine] = Map.empty,
      symbol: Int => PathItem = n => PathItem(f"byte_$n%03d")):
      (WordEncoding[BytePathValue, PathValue], SymbolProgram) =
    val code = WordEncodings.bytesToSymbols(symbol)
    code -> symbols(code.compile(entry, routines))
