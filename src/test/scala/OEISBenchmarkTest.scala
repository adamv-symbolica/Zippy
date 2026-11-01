package morkl

import munit.FunSuite

class OEISBenchmarkTest extends FunSuite:
  override val munitTimeout=scala.concurrent.duration.Duration(600,"s")
  test("OEIS build, ten offsets and Fibonacci prefix query match independent sequence sets") {
    val sequences=Vector(Vector[BigInt](0,1,2,3,5,8,13,21),Vector[BigInt](1,2,3,5,8,13),
      Vector[BigInt](-129,128,0,1,2),Vector.empty[BigInt],Vector[BigInt](1,2,3,5,8,13))
    val input=sequences.zipWithIndex.filter(_._1.nonEmpty).map((s,i)=>OEISBenchmark.record(i,s)).toSet
    val built=Zippy_bytes.eval(OEISBenchmark.build,spaceArgs=Map(OEISBenchmark.records->Zippy_bytes.SpaceValue(input)))
    val expected=sequences.filter(_.nonEmpty).map(s=>BytePaths.concat(BytePathValue(0),OEISBenchmark.encode(s))).toSet
    assertEquals(built.paths,expected)
    val indexed=Zippy_bytes.eval(OEISBenchmark.drophead,spaceArgs=Map(OEISBenchmark.index->built),routines=OEISBenchmark.routines)
    val expectedIndex=(0 to 10).flatMap(i=>sequences.filter(_.nonEmpty).filter(_.size>=i)
      .map(s=>BytePaths.concat(BytePathValue(i),OEISBenchmark.encode(s.drop(i))))).toSet
    assertEquals(indexed.paths,expectedIndex)
    val found=Zippy_bytes.eval(OEISBenchmark.query,spaceArgs=Map(OEISBenchmark.index->indexed))
    val q=Vector[BigInt](1,2,3,5,8,13)
    val expectedQuery=(0 to 10).flatMap(i=>sequences.filter(s=>s.nonEmpty && s.drop(i).startsWith(q))
      .map(s=>BytePaths.concat(BytePathValue(i),OEISBenchmark.encode(s.drop(i))))).toSet
    assertEquals(found.paths,expectedQuery)
    if java.lang.Boolean.getBoolean("morkl.pathmap.test") then
      val programs=OEISBenchmark.compile();val session=RustPathMapBackend.Session(programs)
      try
        assertEquals("drop_length_prefixes\\(".r.findAllIn(programs(1).code).size,1,programs(1).code)
        assertEquals(session.eval(0,programs(0))(Map.empty,Map(OEISBenchmark.records->input)),built.paths)
        assertEquals(session.eval(1,programs(1))(Map.empty,Map(OEISBenchmark.index->built.paths)),indexed.paths)
        assertEquals(session.eval(2,programs(2))(Map.empty,Map(OEISBenchmark.index->indexed.paths)),found.paths)
      finally session.close()
  }
