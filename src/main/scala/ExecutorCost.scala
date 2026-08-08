package morkl

import java.util.concurrent.atomic.AtomicInteger

/** Representation-level operations counted by executors under an explicit
  * test/benchmark scope. Production execution pays only one disabled flag
  * branch at each instrumented operation boundary. */
case class ExecutorCostCounts(
  nodeVisits: Long = 0L,
  pathComparisons: Long = 0L,
  allocations: Long = 0L,
  rounds: Long = 0L,
):
  def +(that: ExecutorCostCounts): ExecutorCostCounts = ExecutorCostCounts(
    nodeVisits + that.nodeVisits,
    pathComparisons + that.pathComparisons,
    allocations + that.allocations,
    rounds + that.rounds,
  )

object ExecutorCostMeter:
  private final class MutableCounts:
    var nodeVisits = 0L
    var pathComparisons = 0L
    var allocations = 0L
    var rounds = 0L
    def snapshot: ExecutorCostCounts =
      ExecutorCostCounts(nodeVisits, pathComparisons, allocations, rounds)

  private val current = ThreadLocal[MutableCounts]()
  private val activeScopes = AtomicInteger(0)

  def measure[A](operation: => A): (A, ExecutorCostCounts) =
    val previous = current.get()
    val counts = MutableCounts()
    current.set(counts)
    activeScopes.incrementAndGet()
    try operation -> counts.snapshot
    finally
      current.set(previous)
      activeScopes.decrementAndGet()

  inline private def active: MutableCounts | Null =
    if activeScopes.get() != 0 then current.get() else null

  def visitNode(count: Long = 1L): Unit =
    val value = active
    if value != null then value.nodeVisits += count

  def comparePath(count: Long = 1L): Unit =
    val value = active
    if value != null then value.pathComparisons += count

  def allocate(count: Long = 1L): Unit =
    val value = active
    if value != null then value.allocations += count

  def round(count: Long = 1L): Unit =
    val value = active
    if value != null then value.rounds += count
