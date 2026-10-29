package morkl.bytes

/** Bytes are unsigned integers in [0,255], validated at the storage boundary. */
type Path = morkl.finite.Path[Int]
val Path = morkl.finite.Path
type Space = morkl.finite.Space[Int]
val Space = morkl.finite.Space
type Routine = morkl.finite.Routine[Int]
val Routine = morkl.finite.Routine
val evaluator = morkl.ByteSpace.evaluator
