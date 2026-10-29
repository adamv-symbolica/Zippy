#pragma once
#include <cstdint>
#include <functional>
#include <iosfwd>
#include <map>
#include <memory>
#include <string>
#include <vector>
#include <unordered_map>
#include <stdexcept>
#include <boost/container/small_vector.hpp>

namespace zippy {
// Heads and fixed-width byte symbols stay in registers/inline storage instead
// of allocating a vector buffer on every generated loop iteration.
using Path = boost::container::small_vector<int, 4>;
struct Acceptor;
// The sole persistent representation of a space is an OpenFst acceptor.
struct Space {
  std::shared_ptr<const Acceptor> value;
  int root = -1;
  Space();
  explicit Space(std::shared_ptr<const Acceptor> v, int state) : value(std::move(v)), root(state) {}
  bool empty() const;
};
struct Env {
  std::map<int, Path> paths;
  std::map<int, Space> spaces;
};
inline const Path* path_arg(const Env& env,int id) {auto i=env.paths.find(id);return i==env.paths.end()?nullptr:&i->second;}
inline const Space* space_arg(const Env& env,int id) {auto i=env.spaces.find(id);return i==env.spaces.end()?nullptr:&i->second;}
inline const Path& bound_path(const Path* p) {if(!p) throw std::out_of_range("unbound path input");return *p;}
inline const Space& bound_space(const Space* s) {if(!s) throw std::out_of_range("unbound space input");return *s;}
struct Context {
  // UTF-16 code units reproduce JVM String ordering, including lone surrogates.
  std::map<int, std::vector<int>> symbols;
  using SymbolOrder = std::unordered_map<int,size_t>;
  mutable std::shared_ptr<const SymbolOrder> symbol_order;
  std::shared_ptr<const SymbolOrder> ordering() const;
  bool symbolic = false;
  int program_id = -1;
  std::map<int,Space (*)(Context&,const Space&)> native_callbacks;
  std::istream* input;
  std::ostream* output;
  Context(std::istream&, std::ostream&);
  bool less(const Path&, const Path&) const;
  void read_symbols();
  Path callback_path(int, const Space&);
  Space callback_space(int, const Space&);
};
Path concat_path(const Path&, const Path&);
Space literal(const std::vector<Path>&);
Space literal_text(const char*);
std::vector<Path> paths_text(const char*);
Space first_listed(const Space&, const std::vector<Path>&, bool prefix);
Space first_matching(const Space&, const Space&, bool prefix);
Space first_fibonacci(const Space&, uint64_t maximum, int digits, bool prefix);
void configure_symbols(Context&, const char*);
Space singleton(const Path&);
Space unite(const Space&, const Space&);
Space intersect(const Space&, const Space&);
Space subtract(const Space&, const Space&);
Space restrict_to(const Space&, const Space&);
Space raffine(const Space&, const Space&);
Space compose(const Space&, const Space&);
Space wrap(const Space&, const Path&);
Space unwrap(const Space&, const Path&);
Space child(const Space&, int label);
Space head_space(const Space&, int alphabet = 0);
bool has_head(const Space&);
Space tails_union(const Space&);
Space tails_intersection(const Space&);
Space prefix_closure(const Space&);
Space suffix_closure(const Space&, bool include_empty);
Space range(Context&, const Space&, int, int);
// Iteration control flow is generated inline. These expose automaton cursors,
// not evaluator callbacks or copies of the residual automaton.
std::vector<std::pair<int,Space>> branches(Context&, const Space&);
Space join_outputs(std::vector<Space>);
Path singleton_path(const Space&);
void realize(const Space&);
struct StorageStats {uint64_t expanded_states, snapshots;};
StorageStats storage(const Space&);
bool equal(const Space&, const Space&);
struct SpaceStats { uint64_t states; uint64_t arcs; std::string paths; };
SpaceStats inspect(const Space&);
std::vector<Path> enumerate(const Space&);
void write_space(std::ostream&, const Space&);
Space read_space(std::istream&);
Env read_env(Context&);
int run(Context&, Space (*)(int, Context&, const Env&));
}  // namespace zippy
