package morkl

import java.nio.file.{Files,Path as FilePath,Paths}

/** The key-set form of PathMap's OEIS benchmark. Numeric payloads are signed
  * big-endian integers; the index tag is one raw byte, just as upstream. */
object OEISBenchmark:
  import Zippy_bytes.{Space as S,Path as P}
  val records=SpaceMention("oeis_records")
  val index=SpaceMention("oeis_index")
  private val source=SpaceMention("oeis_source")
  private val drop=RoutinePtr("oeis_drop_symbol")
  private val translated=RadixTranslations.lengthPrefixedStringsToBytes(Space.TailsUnion(Space.Mention(source)))
  // Boundary operations depend only on lengths, so this same byte program
  // accepts arbitrary numeric payload bytes, including zero and 0xff.
  val routines=translated.program.routines.updated(drop,Zippy_bytes.Routine(drop,Vector.empty,Vector(source),translated.program.entry))
  def encode(numbers: Seq[BigInt]): BytePathValue = BytePaths.fromDigits(numbers.flatMap { n=>
    val bytes=n.toByteArray.toVector.map(_&255)
    require(bytes.size<=255,"OEIS integer payload exceeds one-byte length")
    bytes.size+:bytes
  })
  def record(id: Int,numbers: Seq[BigInt]): BytePathValue =
    BytePaths.concat(BytePaths.fromDigits(Vector.tabulate(4)(i=>(id>>>(24-8*i))&255)),encode(numbers))
  val build: ByteSpace =
    def descend(s: ByteSpace,n: Int): ByteSpace =
      if n==0 then S.Wrap(s,Zippy_bytes.digit(0))
      else
        val tail=SpaceMention(s"oeis_record_tail_$n")
        S.Iteration(s,PathRef("_"),tail,descend(S.Mention(tail),n-1))
    descend(S.Mention(records),4)
  val drophead: ByteSpace =
    val base=S.Unwrap(S.Mention(index),Zippy_bytes.digit(0))
    val offsets=Iterator.iterate(base)(s=>S.Call(drop,Vector.empty,Vector(s))).take(11).toVector
    offsets.zipWithIndex.map((s,i)=>S.Wrap(s,Zippy_bytes.digit(i))).reduce(S.Union(_,_))
  val query: ByteSpace =
    val q=encode(Vector(1,2,3,5,8,13).map(BigInt(_)))
    val prefixes=Zippy_bytes.SpaceValue((0 to 10).map(i=>BytePaths.concat(BytePaths.fromDigits(Vector(i)),q)).toSet)
    S.Restriction(S.Mention(index),S.Literal(prefixes))
  val contents: Vector[ByteSpace]=(0 to 10).map(i=>S.Unwrap(S.Mention(index),Zippy_bytes.digit(i))).toVector
  val names: Vector[String]=Vector("build_map","drophead","query")++(0 to 10).map(i=>s"contents_$i")
  def compile(): Vector[RustPathMapBackend.Compiled] =
    (Vector(build,drophead,query)++contents).map(e=>RustPathMapBackend.bytes(e,routines))
  def writeBundle(output: FilePath): Unit =
    Files.createDirectories(output)
    val programs=compile()
    val modules=programs.zipWithIndex.map { (p,i)=>
      require(p.callbackCount==0)
      Files.writeString(output.resolve(names(i)+".rs"),p.source)
      Files.writeString(output.resolve(names(i)+".graph.txt"),p.graphDescription)
      val code=if RustPathMapBackend.optimizationFor(p.source)==3 then
        raw"\b(?:map|view)(?:_(?:map|view))?_kernels::".r.replaceAllIn(p.code,"") else p.code
      s"mod stage_$i { use super::*;\n$code\n}"
    }
    val template=Files.readString(Paths.get("src/test/rust/oeis_harness.rs"))
    val execute=programs.indices.map(i=>s"$i => stage_$i::entry(c,e),").mkString("\n")
    Files.writeString(output.resolve("oeis.rs"),template.replace("// GENERATED_MODULES",modules.mkString("\n"))
      .replace("// GENERATED_DISPATCH",execute))

@main def exportOEISRust(output: String = "output/oeis-rust"): Unit = OEISBenchmark.writeBundle(Paths.get(output))
