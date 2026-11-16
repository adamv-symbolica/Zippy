#include "zippy_openfst.hpp"
#include <iostream>
#include <stdexcept>
using namespace zippy;
namespace program_0 {
static const Space constant_0 = literal_text("1 0 ");
static Space routine_0(Context& c, const Space* s0, const Space* s1);
static Space routine_0(Context& c, const Space* s0, const Space* s1) {
  const Space v0_3 = subtract(bound_space(s1),constant_0);
  const auto v0_4_branches = branches(c,bound_space(s0));
  std::vector<Space> v0_4_outputs;
  v0_4_outputs.reserve(v0_4_branches.size());
  for (const auto& v0_4_branch : v0_4_branches) {
    const Path v0_4_head{v0_4_branch.first};
    const Space& v0_4_tail = v0_4_branch.second;
    const Space v1_2 = wrap(v0_3,v0_4_head);
    v0_4_outputs.push_back(v1_2);
  }
  const Space v0_4 = join_outputs(std::move(v0_4_outputs));
  const Space v0_5 = tails_union(v0_4);
  return v0_5;
}

static Space entry(Context& c,const Env& e) { return routine_0(c,space_arg(e,0),space_arg(e,1)); }

}

int main() {
 Context c(std::cin,std::cout);
 return run(c, [](int id, Context& c, const Env& e) -> Space {
   switch(id) { case 0: { if(c.program_id!=0) { c.program_id=0; c.native_callbacks.clear();  c.symbolic=true; configure_symbols(c,"0 "); } return program_0::entry(c,e); } default: throw std::runtime_error("unknown program"); }
 });
}
