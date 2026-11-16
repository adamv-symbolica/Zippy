package morkl

object RadixTranslations:
  // Eliminate logical evidence fields while one item still means one symbol.
  // Doing this only after lowering would mistake a four-byte atom for a byte.
  private def source(entry: Space, routines: Map[RoutinePtr, Routine]): (SymbolSyntax.Space, Map[RoutinePtr, SymbolSyntax.Routine]) =
    (SymbolSyntax.importSpace(EvidenceElimination(entry)), routines.view.mapValues { r =>
      SymbolSyntax.importRoutine(r.copy(body = EvidenceElimination(r.body)))
    }.toMap)
  /** Intern input symbols before compiling. Constants in the whole module are
    * interned automatically; the returned encoding freezes this alphabet.
    */
  case class SymbolBits(program: Zippy_bits.Program, encoding: PathEncoding[PathValue, BigInt]):
    def encode(value: SpaceValue): BitSpaceValue = BitSpaceValue(value.paths.map(encoding.encode))
    def decode(value: BitSpaceValue): SpaceValue = SpaceValue(value.paths.map(encoding.decode))
  case class SymbolBytes(program: Zippy_bytes.Program, encoding: PathEncoding[PathValue, BytePathValue]):
    def encode(value: SpaceValue): ByteSpaceValue = ByteSpaceValue(value.paths.map(encoding.encode))
    def decode(value: ByteSpaceValue): SpaceValue = SpaceValue(value.paths.map(encoding.decode))
  private def internConstants(entry: Space, routines: Map[RoutinePtr, Routine], interner: PathItemInterner): Unit =
    import PathItemOrder.given
    def scan(s: Space): Unit =
      subs(s)(spost = { case v @ Space.Literal(value) => value.paths.toVector.sorted.foreach(interner.encode); v },
        ppost = { case p @ Path.Constant(value) => interner.encode(value); p })
      ()
    scan(entry)
    routines.toVector.sortBy(_._1.s).foreach((_, r) => scan(r.body))
  def symbolsToBits(entry: Space, routines: Map[RoutinePtr, Routine] = Map.empty,
                    interner: PathItemInterner = PathItemInterner()): SymbolBits =
    internConstants(entry, routines, interner)
    val encoding = PathEncodings.fibonacci(interner)
    val compiler = new EncodingCompiler(SymbolSyntax, Zippy_bits, encoding)
    val (body, definitions) = source(entry, routines)
    SymbolBits(compiler.compile(body, definitions), encoding)
  def stringsToBytes(entry: Space, routines: Map[RoutinePtr, Routine] = Map.empty): SymbolBytes =
    val compiler = new EncodingCompiler(SymbolSyntax, Zippy_bytes, PathEncodings.strings)
    val (body, definitions) = source(entry, routines)
    SymbolBytes(compiler.compile(body, definitions), PathEncodings.strings)
  def lengthPrefixedStringsToBytes(entry: Space, routines: Map[RoutinePtr, Routine] = Map.empty): SymbolBytes =
    val encoding=PathEncodings.lengthPrefixed
    val compiler=new EncodingCompiler(SymbolSyntax,Zippy_bytes,encoding)
    val (body, definitions) = source(entry, routines)
    SymbolBytes(compiler.compile(body,definitions),encoding)
  def internedSymbolsToBytes(entry: Space, routines: Map[RoutinePtr, Routine] = Map.empty,
                            interner: PathItemInterner = PathItemInterner()): SymbolBytes =
    internConstants(entry, routines, interner)
    val encoding = PathEncodings.internedBytes(interner)
    val compiler = new EncodingCompiler(SymbolSyntax, Zippy_bytes, encoding)
    val (body, definitions) = source(entry, routines)
    SymbolBytes(compiler.compile(body, definitions), encoding)
  def bitsToBytes(entry: BitSpace, routines: Map[RoutinePtr, Zippy_bits.Routine] = Map.empty): Zippy_bytes.Program =
    new EncodingCompiler(Zippy_bits, Zippy_bytes, PathEncodings.bitsToBytes).compile(entry, routines)
  def bytesToBits(entry: ByteSpace, routines: Map[RoutinePtr, Zippy_bytes.Routine] = Map.empty): Zippy_bits.Program =
    new EncodingCompiler(Zippy_bytes, Zippy_bits, PathEncodings.bytesToBits).compile(entry, routines)
  private def exportSymbols(p: SymbolSyntax.Program): SymbolProgram =
    SymbolProgram(SymbolSyntax.exportSpace(p.entry), p.routines.view.mapValues(SymbolSyntax.exportRoutine).toMap)
  def bitsToSymbols(entry: BitSpace, routines: Map[RoutinePtr, Zippy_bits.Routine] = Map.empty): SymbolProgram =
    exportSymbols(new EncodingCompiler(Zippy_bits, SymbolSyntax, PathEncodings.bitsToSymbols).compile(entry, routines))
  def bytesToSymbols(entry: ByteSpace, routines: Map[RoutinePtr, Zippy_bytes.Routine] = Map.empty): SymbolProgram =
    exportSymbols(new EncodingCompiler(Zippy_bytes, SymbolSyntax, PathEncodings.bytesToSymbols).compile(entry, routines))
