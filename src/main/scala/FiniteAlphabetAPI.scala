package morkl

object BitSpace:
  val Empty: BitSpace = AlphabetSpace.Empty()
  def Call(routine: RoutinePtr, refs: Vector[AlphabetPath[BigInt]], mentions: Vector[AlphabetSpace[BigInt]]): BitSpace = AlphabetSpace.Call(routine, refs, mentions)
  def Mention(ref: SpaceMention): BitSpace = AlphabetSpace.Mention(ref)
  def Singleton(path: AlphabetPath[BigInt]): BitSpace = AlphabetSpace.Singleton(path)
  def Literal(paths: Set[BigInt]): BitSpace = AlphabetSpace.Literal(paths)
  def Union(left: AlphabetSpace[BigInt], right: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.Union(left, right)
  def Intersection(left: AlphabetSpace[BigInt], right: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.Intersection(left, right)
  def Subtraction(left: AlphabetSpace[BigInt], right: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.Subtraction(left, right)
  def Restriction(left: AlphabetSpace[BigInt], right: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.Restriction(left, right)
  def Raffination(left: AlphabetSpace[BigInt], right: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.Raffination(left, right)
  def Composition(left: AlphabetSpace[BigInt], right: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.Composition(left, right)
  def Iteration(src: AlphabetSpace[BigInt], symbol: PathRef, rest: SpaceMention, body: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.Iteration(src, symbol, rest, body)
  def Fold(src: AlphabetSpace[BigInt], initial: AlphabetPath[BigInt], acc: PathRef, symbol: PathRef, rest: SpaceMention, body: AlphabetSpace[BigInt], update: AlphabetPath[BigInt]): BitSpace = AlphabetSpace.Fold(src, initial, acc, symbol, rest, body, update)
  def Fixpoint(initial: AlphabetSpace[BigInt], variable: SpaceMention, step: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.Fixpoint(initial, variable, step)
  def Wrap(src: AlphabetSpace[BigInt], prefix: AlphabetPath[BigInt]): BitSpace = AlphabetSpace.Wrap(src, prefix)
  def Unwrap(src: AlphabetSpace[BigInt], prefix: AlphabetPath[BigInt]): BitSpace = AlphabetSpace.Unwrap(src, prefix)
  def TailsUnion(src: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.TailsUnion(src)
  def TailsIntersection(src: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.TailsIntersection(src)
  def PrefixClosure(src: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.PrefixClosure(src)
  def SuffixClosure(src: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.SuffixClosure(src)
  def TailsClosure(src: AlphabetSpace[BigInt]): BitSpace = AlphabetSpace.TailsClosure(src)
  def GroundedPS(path: AlphabetPath[BigInt], f: BigInt => Set[BigInt]): BitSpace = AlphabetSpace.GroundedPS(path, f)
  def GroundedSS(space: AlphabetSpace[BigInt], f: Set[BigInt] => Set[BigInt]): BitSpace = AlphabetSpace.GroundedSS(space, f)
  def Range(src: AlphabetSpace[BigInt], start: Int, end: Int): BitSpace = AlphabetSpace.Range(src, start, end)
  def Head(src: BitSpace): BitSpace = AlphabetSpace.Head(src)
  val maxHeadSize: Int = BitPaths.arity
  def eval(src: BitSpace, paths: Map[PathRef, BigInt] = Map.empty, spaces: Map[SpaceMention, Set[BigInt]] = Map.empty, routines: Map[RoutinePtr, AlphabetRoutine[BigInt]] = Map.empty): Set[BigInt] =
    AlphabetEvaluator.eval(src, BitPaths, paths, spaces, routines)
  def headMask(paths: Set[BigInt]): BigInt =
    paths.iterator.filter(BitPaths.length(_) > 0).foldLeft(BigInt(0))((mask, p) => mask.setBit(BitPaths.digit(BitPaths.take(p, 1))))

object ByteSpace:
  val Empty: ByteSpace = AlphabetSpace.Empty()
  def Call(routine: RoutinePtr, refs: Vector[AlphabetPath[BytePath]], mentions: Vector[AlphabetSpace[BytePath]]): ByteSpace = AlphabetSpace.Call(routine, refs, mentions)
  def Mention(ref: SpaceMention): ByteSpace = AlphabetSpace.Mention(ref)
  def Singleton(path: AlphabetPath[BytePath]): ByteSpace = AlphabetSpace.Singleton(path)
  def Literal(paths: Set[BytePath]): ByteSpace = AlphabetSpace.Literal(paths)
  def Union(left: AlphabetSpace[BytePath], right: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.Union(left, right)
  def Intersection(left: AlphabetSpace[BytePath], right: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.Intersection(left, right)
  def Subtraction(left: AlphabetSpace[BytePath], right: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.Subtraction(left, right)
  def Restriction(left: AlphabetSpace[BytePath], right: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.Restriction(left, right)
  def Raffination(left: AlphabetSpace[BytePath], right: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.Raffination(left, right)
  def Composition(left: AlphabetSpace[BytePath], right: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.Composition(left, right)
  def Iteration(src: AlphabetSpace[BytePath], symbol: PathRef, rest: SpaceMention, body: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.Iteration(src, symbol, rest, body)
  def Fold(src: AlphabetSpace[BytePath], initial: AlphabetPath[BytePath], acc: PathRef, symbol: PathRef, rest: SpaceMention, body: AlphabetSpace[BytePath], update: AlphabetPath[BytePath]): ByteSpace = AlphabetSpace.Fold(src, initial, acc, symbol, rest, body, update)
  def Fixpoint(initial: AlphabetSpace[BytePath], variable: SpaceMention, step: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.Fixpoint(initial, variable, step)
  def Wrap(src: AlphabetSpace[BytePath], prefix: AlphabetPath[BytePath]): ByteSpace = AlphabetSpace.Wrap(src, prefix)
  def Unwrap(src: AlphabetSpace[BytePath], prefix: AlphabetPath[BytePath]): ByteSpace = AlphabetSpace.Unwrap(src, prefix)
  def TailsUnion(src: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.TailsUnion(src)
  def TailsIntersection(src: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.TailsIntersection(src)
  def PrefixClosure(src: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.PrefixClosure(src)
  def SuffixClosure(src: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.SuffixClosure(src)
  def TailsClosure(src: AlphabetSpace[BytePath]): ByteSpace = AlphabetSpace.TailsClosure(src)
  def GroundedPS(path: AlphabetPath[BytePath], f: BytePath => Set[BytePath]): ByteSpace = AlphabetSpace.GroundedPS(path, f)
  def GroundedSS(space: AlphabetSpace[BytePath], f: Set[BytePath] => Set[BytePath]): ByteSpace = AlphabetSpace.GroundedSS(space, f)
  def Range(src: AlphabetSpace[BytePath], start: Int, end: Int): ByteSpace = AlphabetSpace.Range(src, start, end)
  def Head(src: ByteSpace): ByteSpace = AlphabetSpace.Head(src)
  val maxHeadSize: Int = BytePaths.arity
  def eval(src: ByteSpace, paths: Map[PathRef, BytePath] = Map.empty, spaces: Map[SpaceMention, Set[BytePath]] = Map.empty, routines: Map[RoutinePtr, AlphabetRoutine[BytePath]] = Map.empty): Set[BytePath] =
    AlphabetEvaluator.eval(src, BytePaths, paths, spaces, routines)
  def headMask(paths: Set[BytePath]): BigInt =
    paths.iterator.filter(BytePaths.length(_) > 0).foldLeft(BigInt(0))((mask, p) => mask.setBit(BytePaths.digit(BytePaths.take(p, 1))))

object BitPath:
  def Deref(ref: PathRef): AlphabetPath[BigInt] = AlphabetPath.Deref(ref)
  def Constant(value: BigInt): AlphabetPath[BigInt] = AlphabetPath.Constant(value)
  def Concat(left: AlphabetPath[BigInt], right: AlphabetPath[BigInt]): AlphabetPath[BigInt] = AlphabetPath.Concat(left, right)
  def GroundedPP(path: AlphabetPath[BigInt], f: BigInt => BigInt): AlphabetPath[BigInt] = AlphabetPath.GroundedPP(path, f)
  def GroundedSP(space: AlphabetSpace[BigInt], f: Set[BigInt] => BigInt): AlphabetPath[BigInt] = AlphabetPath.GroundedSP(space, f)

object BytePathExpr:
  def Deref(ref: PathRef): AlphabetPath[BytePath] = AlphabetPath.Deref(ref)
  def Constant(value: BytePath): AlphabetPath[BytePath] = AlphabetPath.Constant(value)
  def Concat(left: AlphabetPath[BytePath], right: AlphabetPath[BytePath]): AlphabetPath[BytePath] = AlphabetPath.Concat(left, right)
  def GroundedPP(path: AlphabetPath[BytePath], f: BytePath => BytePath): AlphabetPath[BytePath] = AlphabetPath.GroundedPP(path, f)
  def GroundedSP(space: AlphabetSpace[BytePath], f: Set[BytePath] => BytePath): AlphabetPath[BytePath] = AlphabetPath.GroundedSP(space, f)

object Zippy_bits:
  type Path = BitPath
  val Path = BitPath
  type Space = BitSpace
  val Space = BitSpace

object Zippy_bytes:
  type Path = BytePathExpr
  val Path = BytePathExpr
  type Space = ByteSpace
  val Space = ByteSpace
