package morkl

import morkl.finite.TranslationAudit
import java.nio.file.{Files, Paths}
import java.lang.reflect.Modifier
import scala.jdk.CollectionConverters.*
import scala.concurrent.Await
import scala.concurrent.duration.*

/** Execute the actual registered MUnit test bodies with translation auditing.
  * Differential evaluation intentionally exceeds benchmark timeout budgets;
  * the ordinary MUnit runner remains the normal correctness/timing gate. */
@main def translationAuditSuite(): Unit =
  val filter = Option(System.getProperty("morkl.translationAudit.filter")).getOrElse("")
  require(TranslationAudit.enabled, "run with -Dmorkl.translationAudit=true")
  val root = Paths.get(classOf[FiniteAlphabetTest].getProtectionDomain.getCodeSource.getLocation.toURI)
  val stream = Files.walk(root)
  val classes = try stream.iterator().asScala.filter(p => p.toString.endsWith(".class") && !p.getFileName.toString.contains("$"))
    .map(p => root.relativize(p).toString.stripSuffix(".class").replace(java.io.File.separatorChar, '.')).toVector.sorted
    finally stream.close()
  var tests = 0
  var ignored = 0
  for name <- classes if name.startsWith("morkl.") && (filter.isEmpty || name.contains(filter)) do
    val cls = Class.forName(name, false, classOf[FiniteAlphabetTest].getClassLoader)
    if classOf[munit.FunSuite].isAssignableFrom(cls) && !Modifier.isAbstract(cls.getModifiers) then
      val suite = cls.getDeclaredConstructor().newInstance().asInstanceOf[munit.FunSuite]
      require(suite.munitFixtures.isEmpty, s"add fixture handling before auditing $name")
      suite.beforeAll()
      try
        for test <- suite.munitTests() do
          if test.tags.contains(munit.Ignore) then ignored += 1
          else
            println(s"audit $name: ${test.name}")
            suite.beforeEach(munit.BeforeEach(test))
            try Await.result(test.body(), Duration.Inf)
            finally suite.afterEach(munit.AfterEach(test))
            tests += 1
      finally suite.afterAll()
  println(s"Translation suite: $tests tests passed, $ignored ignored")
