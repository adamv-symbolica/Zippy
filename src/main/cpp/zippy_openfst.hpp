#pragma once
#include <cstdint>
#include <functional>
#include <iosfwd>
#include <map>
#include <memory>
#include <string>
#include <vector>

namespace zippy {
using Path = std::vector<int>;
struct Acceptor;
// The sole persistent representation of a space is an OpenFst acceptor.
struct Space {
  std::shared_ptr<const Acceptor> value;
  Space();
  explicit Space(std::shared_ptr<const Acceptor> v) : value(std::move(v)) {}
  bool empty() const;
};
struct Env {
  std::map<int, Path> paths;
  std::map<int, Space> spaces;
  Env bind_path(int, const Path&) const;
  Env bind_space(int, const Space&) const;
};
struct Context {
  // UTF-16 code units reproduce JVM String ordering, including lone surrogates.
  std::map<int, std::vector<int>> symbols;
  bool symbolic = false;
  int program_id = -1;
  std::map<int,std::function<Space(Context&,const Space&)>> native_callbacks;
  std::istream* input;
  std::ostream* output;
  Context(std::istream&, std::ostream&);
  bool less(const Path&, const Path&) const;
  void read_symbols();
  Path callback_path(int, const Space&);
  Space callback_space(int, const Space&);
};
using PFn = Path (*)(Context&, const Env&);
using SFn = Space (*)(Context&, const Env&);
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
Space compose(const Space&, const Space&);
Space wrap(const Space&, const Path&);
Space unwrap(const Space&, const Path&);
Space head_space(const Space&);
Space tails_union(const Space&);
Space tails_intersection(const Space&);
Space prefix_closure(const Space&);
Space suffix_closure(const Space&, bool include_empty);
Space range(Context&, const Space&, int, int);
Space iterate(Context&, const Env&, const Space&, int, int, SFn);
Space fold(Context&, const Env&, const Space&, const Path&, int, int, int, SFn, PFn);
Space fixpoint(Context&, const Env&, const Space&, int, SFn);
bool equal(const Space&, const Space&);
struct SpaceStats { uint64_t states; uint64_t arcs; std::string paths; };
SpaceStats inspect(const Space&);
std::vector<Path> enumerate(const Space&);
void write_space(std::ostream&, const Space&);
Space read_space(std::istream&);
Env read_env(Context&);
int run(Context&, const std::function<Space(int, Context&, const Env&)>&);
}  // namespace zippy
