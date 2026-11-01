package morkl

import java.nio.file.{Files,Paths}

/** Same runtime/counting code on both sides; only cursor/path/output reuse is
  * switched. This separates traversal gains from cardinality improvements. */
object CursorReuseBenchmark:
  import Zippy_bytes.{Space as S,Path as P}
  private val input=SpaceMention("cursor_input")
  private def expression(width: Int): ByteSpace =
    def loop(source: ByteSpace,prefix: BytePath,n: Int): ByteSpace =
      if n==0 then S.Singleton(prefix)
      else
        val h=PathRef(s"cursor_head_$n").known(1);val t=SpaceMention(s"cursor_tail_$n")
        S.Iteration(source,h,t,loop(S.Mention(t),P.Concat(prefix,P.Deref(h)),n-1))
    loop(S.Mention(input),P.ZERO,width)
  def run(output: String): Unit =
    val folder=Paths.get(output);Files.createDirectories(folder)
    val writer=Files.newBufferedWriter(folder.resolve("native.csv"))
    writer.write("width,inputs,reuse,paths,nanos,samples,source_bytes,read_cursors,forks,concats,result_maps\n")
    val previous=Option(System.getProperty("morkl.pathmap.reuse"))
    try
      for width<-Vector(4,8,16);size<-Vector(1024,16384,65536) do
        val values=(0 until size).map { i=>
          val digits=Vector.tabulate(width)(j=>if j<4 then (i>>>(24-8*j))&255 else (i*31+j*17)&255)
          BytePaths.fromDigits(digits:+255)
        }.toSet
        val expected=values.map(p=>BytePaths.take(p,width))
        for reuse<-Vector(false,true) do
          System.setProperty("morkl.pathmap.reuse",reuse.toString)
          val p=RustPathMapBackend.bytes(expression(width))
          val session=RustPathMapBackend.Session(Vector(p))
          try
            assert(session.eval(0,p)(Map.empty,Map(input->values))==expected)
            session.benchmark(0,p,1)(Map.empty,Map(input->values))
            val first=session.benchmark(0,p,1)(Map.empty,Map(input->values))
            val repeats=(20000000L/first.nanos.max(1)).max(1).min(64).toInt
            val samples=Vector.fill(5)(session.benchmark(0,p,repeats)(Map.empty,Map(input->values)))
            assert(samples.forall(_.paths==expected.size))
            val best=samples.sortBy(_.nanos).apply(samples.size/2)
            def occurrences(text: String)=java.util.regex.Pattern.quote(text).r.findAllIn(p.code).size
            writer.write(Vector(width,size,reuse,expected.size,best.nanos,samples.size,p.source.length,
              occurrences(".read_zipper()"),occurrences(".fork_read_zipper()"),occurrences("concat_path("),occurrences("Space::new()")).mkString(",")+"\n")
            writer.flush()
            Files.writeString(folder.resolve(s"width-$width-size-$size-reuse-$reuse.rs"),p.source)
          finally session.close()
    finally
      writer.close()
      previous.fold(System.clearProperty("morkl.pathmap.reuse"))(v=>System.setProperty("morkl.pathmap.reuse",v))

@main def cursorReuseBenchmarkReport(output: String): Unit = CursorReuseBenchmark.run(output)
