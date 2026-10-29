package morkl

import java.nio.file.{Files,Paths}

/** Additional measurements for the three source cases the legacy report omits.
  * Compile against the benchmark JAR; this does not alter benchmark fixtures.
  */
@main def cornerstoneZipperProbe(output: String,index: Int): Unit =
  val c=TrieBenchmarks.cases()(index)
  require(c.zipperNote.nonEmpty,s"case $index is already measured by the normal report")
  val expected=evalTrieValue(c.expr)(using PathContext.emptyMap,c.tc,c.rc)
  val context=ZipperSpaceContext.fromTrie(c.tc)
  val start=System.nanoTime()
  val actual=evalZValue(c.expr)(using PathContext.emptyMap,context,c.rc)
  val first=(System.nanoTime()-start)/1e6
  assert(actual==expected,s"${c.name}/${c.variant}: zipper mismatch")
  Console.err.println(s"exact first evaluation: $first ms")
  // Match the original report: correctness, one initial evaluation, two warmups,
  // then one timed source evaluation (all three source cases use runs=1).
  for _ <- 0 until 3 do
    assert(evalZ(c.expr)(using PathContext.emptyMap,context,c.rc).pathCount==expected.paths.size)
  val timed=System.nanoTime()
  val count=evalZ(c.expr)(using PathContext.emptyMap,context,c.rc).pathCount
  val elapsed=(System.nanoTime()-timed)/1e6
  assert(count==expected.paths.size)
  def quote(s: String): String="\""+s.replace("\"","\"\"")+"\""
  Files.writeString(Paths.get(output),"name,variant,paths,evalZ_ms,first_ms,checked\n"+
    s"${quote(c.name)},${quote(c.variant)},$count,$elapsed,$first,exact\n")
