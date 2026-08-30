package morkl

import morkl.Syntax.{*, given}

/** A direct, non-recursive-call SCC certificate program. It computes the
  * transitive closure and intersects it with its transpose, yielding exactly
  * the mutually reachable ordered pairs. */
object SccCornerstone:
  val edges: SpaceValue = SpaceValue(
    "a.b", "b.a", "b.c", "c.d", "d.c", "e.f",
  )

  def expression(source: Space): Space =
    val state = SpaceMention("scc_reachable")
    val from = PathRef("scc_from").known(1)
    val neighbors = SpaceMention("scc_neighbors")
    val closure = Space.Fixpoint(
      source,
      state,
      Space.Mention(state) \/ Space.Iteration(
        Space.Mention(state),
        from,
        neighbors,
        Space.Wrap(
          Space.TailsUnion(Space.Restriction(Space.Mention(state), Space.Mention(neighbors))),
          Path.Deref(from),
        ),
      ),
    )
    val target = PathRef("scc_target").known(1)
    val targetRest = SpaceMention("scc_target_rest")
    val transposed = Space.Iteration(
      closure,
      from,
      neighbors,
      Space.Iteration(
        Space.Mention(neighbors),
        target,
        targetRest,
        Space.Singleton(Path.Concat(Path.Deref(target), Path.Deref(from))),
      ),
    )
    Space.Intersection(closure, transposed)

  val body: Space = expression(Space.Literal(edges))
  val expected: SpaceValue = SpaceValue(
    "a.a", "a.b", "b.a", "b.b",
    "c.c", "c.d", "d.c", "d.d",
  )
