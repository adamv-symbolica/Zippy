package morkl

/** Reuse the symbol language's compositional analyses through a one-digit per
  * symbol syntax view. This does not evaluate the expression or flatten edges. */
object AlphabetAnalysis:
  def symbolView[P](s: AlphabetSpace[P], d: FiniteDomain[P]): Space =
    def encode(p: P): PathValue = PathValue(d.digits(p).map(i => PathItem(f"digit$i%03d")).toList)
    def decode(p: PathValue): P = d.fromDigits(p.items.map { item =>
      require(item.show.startsWith("digit"), "not an analysis digit")
      item.show.drop(5).toInt
    })
    SymbolAlphabet.toSpace(AlphabetSyntax.map[P, PathValue](s)(_ => None, _ => None, encode, decode))

  def pathLengths[P](s: AlphabetSpace[P], d: FiniteDomain[P]): PathLengthEstimate =
    ResultPathLength.estimateBaseline(symbolView(s, d))

  def resultSize[P](s: AlphabetSpace[P], d: FiniteDomain[P]): ResultSizeEstimate =
    ResultSpaceSize.estimate(symbolView(s, d))
