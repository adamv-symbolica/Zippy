#![allow(unused_imports,unused_variables,unused_mut,unused_parens,dead_code)]
use zippy_pathmap::*;
mod program_0 { use super::*;
fn routine_0(c: &mut Context, s0: Option<&Space>, s1: Option<&Space>) -> Space {
  let mut v0_4 = Space::new();
  let mut v0_4_write = v0_4.write_zipper();
  let mut v0_4_cursor = (*s0.expect("unbound native input")).read_zipper();
  let v0_4_origin = v0_4_cursor.path().len();
  for v0_4_byte in v0_4_cursor.child_mask().iter() {
    v0_4_cursor.descend_to_existing_byte(v0_4_byte);
    {
    v0_4_write.descend_to(&v0_4_cursor.path()[v0_4_origin..(v0_4_origin + 1)]);
    let mut v1_2_branches = (*s1.expect("unbound native input")).read_zipper();
    join_branches(&mut v0_4_write,&mut v1_2_branches);
    v0_4_write.ascend((&v0_4_cursor.path()[v0_4_origin..(v0_4_origin + 1)]).len());
    }
    v0_4_cursor.ascend(1);
  }
  drop(v0_4_cursor);
  drop(v0_4_write);
  let v0_5: Space = tails_union(&(v0_4));
  drop(v0_4);
  (v0_5).clone()
}

pub fn entry(c: &mut Context, e: &Env) -> Space { routine_0(c,e.space(0),e.space(1)) }

}
fn main() { run(|id,c,e| match id { 0 => program_0::entry(c,e), _ => panic!("unknown program") }); }
