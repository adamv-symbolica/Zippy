#![allow(unused_imports,unused_variables,unused_mut,unused_parens,dead_code)]
use zippy_pathmap::*;
mod program_0 { use super::*;
static CONSTANT_0: std::sync::LazyLock<Space> = std::sync::LazyLock::new(|| literal_text("1 0 "));
fn routine_0(c: &mut Context, s0: Option<&Space>, s1: Option<&Space>) -> Space {
  let v0_3: Space = subtract(&(*s0.expect("unbound native input")),&(*CONSTANT_0));
  let v0_4: Space = range_const::<{0},{1}>(&(v0_3));
  drop(v0_3);
  let mut v0_6 = Space::new();
  let mut v0_6_write = v0_6.write_zipper();
  let mut v0_6_cursor = (v0_4).read_zipper();
  let v0_6_origin = v0_6_cursor.path().len();
  for v0_6_byte in v0_6_cursor.child_mask().iter() {
    v0_6_cursor.descend_to_existing_byte(v0_6_byte);
    {
    let mut v1_2_branches = (*s1.expect("unbound native input")).read_zipper();
    join_branches(&mut v0_6_write,&mut v1_2_branches);
    }
    v0_6_cursor.ascend(1);
  }
  drop(v0_6_cursor);
  drop(v0_6_write);
  drop(v0_4);
  (v0_6).clone()
}

pub fn entry(c: &mut Context, e: &Env) -> Space { routine_0(c,e.space(0),e.space(1)) }

}
fn main() { run(|id,c,e| match id { 0 => program_0::entry(c,e), _ => panic!("unknown program") }); }
