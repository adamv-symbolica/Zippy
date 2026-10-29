#include "zippy_openfst.hpp"
#if defined(ZIPPY_OPENFST_LEGACY)
#include <fst/fstlib.h>
#elif __has_include(<openfst/lib/fstlib.h>)
#include <openfst/lib/fstlib.h>
#elif __has_include(<openfst/fstlib.h>)
#include <openfst/fstlib.h>
#else
#include <fst/fstlib.h>
#endif
#include <algorithm>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <utility>
#include <tuple>
#include <set>
#include <unordered_map>
#include <chrono>
#include <queue>
#include <unordered_set>
#include <boost/multiprecision/cpp_int.hpp>
#include <cstdlib>
#include <sstream>
#include <deque>
#include <array>
#if defined(__unix__) || defined(__APPLE__)
#include <sys/resource.h>
#endif

namespace zippy {
using Arc = fst::StdArc;
using Fst = fst::VectorFst<Arc>;
using State = Arc::StateId;
using Weight = Arc::Weight;
#include "zippy_lazy.hpp"
static bool final(const BaseFst& f, State s) { return f.Final(s) != Weight::Zero(); }
static Space adopt(Fst f) {
  if(f.Properties(fst::kError,false)) throw std::runtime_error("OpenFst operation failed");
  State root=f.Start();if(root==fst::kNoStateId) return Space();
  return Space(own_acceptor(new Acceptor(std::move(f))),root);
}
static std::shared_ptr<const Acceptor> empty_acceptor() {
  static const auto value=own_acceptor(new Acceptor(Fst()));return value;
}
Space::Space() : value(empty_acceptor()) {}
bool Space::empty() const {return !productive(*this);}
Context::Context(std::istream& in, std::ostream& out) : input(&in), output(&out) {}
bool Context::less(const Path& a, const Path& b) const {
  return std::lexicographical_compare(a.begin(), a.end(), b.begin(), b.end(), [&](int x, int y) {
    return x==y ? false : symbolic ? symbols.at(x) < symbols.at(y) : x < y;
  });
}
Path concat_path(const Path& a, const Path& b) { Path r(a); r.insert(r.end(), b.begin(), b.end()); return r; }
Space literal(const std::vector<Path>& paths) {
  if(paths.empty()) return Space();
  struct Node { std::map<int,size_t> arcs; bool terminal=false; };
  std::vector<Node> nodes(1);
  for(const auto& path : paths) {
    size_t state=0;
    for(int label : path) {
      if(label<=0) throw std::runtime_error("language labels must be positive (zero is OpenFst epsilon)");
      auto found=nodes[state].arcs.find(label);
      if(found==nodes[state].arcs.end()) {size_t next=nodes.size();nodes[state].arcs[label]=next;nodes.emplace_back();state=next;}
      else state=found->second;
    } nodes[state].terminal=true;
  }
  // Bottom-up suffix sharing creates a minimal acyclic acceptor directly.
  // Transient construction maps never become the runtime representation.
  Fst f; std::vector<State> ids(nodes.size());
  using Signature=std::pair<bool,std::vector<std::pair<int,State>>>;
  std::map<Signature,State> unique;
  for(size_t i=nodes.size();i-- > 0;) {
    Signature signature{nodes[i].terminal,{}};
    for(auto [label,next]:nodes[i].arcs) signature.second.emplace_back(label,ids[next]);
    auto found=unique.find(signature);
    if(found!=unique.end()) ids[i]=found->second;
    else {
      State state=f.AddState();ids[i]=state;unique.emplace(signature,state);
      if(nodes[i].terminal) f.SetFinal(state,Weight::One());
      for(auto [label,next]:signature.second) f.AddArc(state,Arc(label,label,Weight::One(),next));
    }
  }
  f.SetStart(ids[0]);
  fst::ArcSort(&f, fst::ILabelCompare<Arc>());
  return adopt(std::move(f));
}
Space singleton(const Path& p) {
  if(p.empty()) {static const Space epsilon=literal({Path{}});return epsilon;}
  if(p.size()==1 && p.front()>0 && p.front()<=256) {
    static const std::vector<Space> digits=[] {std::vector<Space> values;values.reserve(256);for(int label=1;label<=256;++label) values.push_back(literal({Path{label}}));return values;}();
    return digits[p.front()-1];
  }
  return literal({p});
}
// Compositions own delayed deterministic OpenFst machines. There is no
// intermediate VectorFst, determinization pass, or whole-graph minimization.
Space unite(const Space& a,const Space& b) {
  if(known_empty(a)) return b;if(known_empty(b)||same(a,b)) return a;
  return delayed(std::make_shared<UnionMachine>(std::vector<Space>{a,b}),a.value->coaccessible&&b.value->coaccessible);
}
Space intersect(const Space& a,const Space& b) {
  if(same(a,b)) return a;if(known_empty(a)||known_empty(b)) return Space();
  return delayed(std::make_shared<ProductMachine<ProductOp::Intersection>>(a,b));
}
Space subtract(const Space& a,const Space& b) {
  if(same(a,b)) return Space();if(known_empty(a)||known_empty(b)) return a;
  return delayed(std::make_shared<ProductMachine<ProductOp::Difference>>(a,b));
}
bool equal(const Space& a,const Space& b) {
  if(same(a,b)) return true;
  if(known_empty(a)) return b.empty();if(known_empty(b)) return a.empty();
  return delayed(std::make_shared<ProductMachine<ProductOp::SymmetricDifference>>(a,b)).empty();
}
Space compose(const Space& a,const Space& b) {
  if(known_empty(a)||known_empty(b)) return Space();
  // Control guards commonly concatenate the literal epsilon language. Cancel
  // that identity without forcing transitions of a delayed operand.
  auto epsilon=[](const Space& s) {
    const auto* f=dynamic_cast<const Fst*>(s.value->machine.get());
    return f&&accepting(*f,s.root)&&f->NumArcs(s.root)==0;
  };
  if(epsilon(a)) return b;if(epsilon(b)) return a;
  return delayed(std::make_shared<ConcatMachine>(a,b),a.value->coaccessible&&b.value->coaccessible);
}
Space wrap(const Space& a,const Path& p) {
  if(p.empty()||known_empty(a)) return a;
  return delayed(std::make_shared<WrapMachine>(a,p),a.value->coaccessible);
}
Space unwrap(const Space& a,const Path& p) {
  if(known_empty(a)||p.empty()) return a;
  State state=a.root;const auto& f=graph(a);
  for(int label:p) {state=transition(f,state,label);if(state<0) return Space();}
  return cursor(a,state);
}
Space child(const Space& a,int label) {
  if(known_empty(a)) return a;State state=transition(graph(a),a.root,label);
  return state<0?Space():cursor(a,state);
}
static bool one_word(const Space& input,Path& word) {
  if(known_empty(input)) return false;const auto& f=graph(input);State state=input.root;
  for(;;) {
    auto n=f.NumArcs(state);bool terminal=accepting(f,state);
    if(terminal) return n==0;if(n!=1) return false;
    fst::ArcIterator<BaseFst> edge(f,state);word.push_back(edge.Value().ilabel);state=edge.Value().nextstate;
  }
}
static Space branch_view(bool terminal,std::vector<std::pair<int,Space>> edges,bool trimmed,const Space* original=nullptr) {
  if(edges.empty()&&!terminal) return Space();
  // Hash-cons only the edited boundary, preserving suffix sharing without a
  // whole-automaton minimization. Weak values retain no obsolete languages.
  using Key=std::pair<bool,std::vector<std::tuple<int,uintptr_t,State>>>;
  struct WeakCursor {std::weak_ptr<const Acceptor> owner;State root;};
  static thread_local std::map<Key,WeakCursor> cache;
  Key key{terminal,{}};key.second.reserve(edges.size());
  for(const auto& [label,child]:edges) key.second.emplace_back(label,reinterpret_cast<uintptr_t>(child.value.get()),child.root);
  auto found=cache.find(key);
  if(found!=cache.end()) {
    if(auto owner=found->second.owner.lock()) return Space(std::move(owner),found->second.root);
    cache.erase(found);
  }
  Space result=original?*original:delayed(std::make_shared<BranchMachine>(terminal,std::move(edges)),trimmed);
  if(cache.size()>=131072) cache.clear();
  cache.emplace(std::move(key),WeakCursor{result.value,result.root});return result;
}
static Space remove_prefix(const Space& input,const Path& prefix) {
  std::vector<Space> route;Space current=input;
  for(int label:prefix) {route.push_back(current);current=child(current,label);if(known_empty(current)) return input;}
  Space result;
  for(size_t i=prefix.size();i-- > 0;) {
    const auto& source=route[i];const auto& f=graph(source);std::vector<std::pair<int,Space>> edges,original;
    for(fst::ArcIterator<BaseFst> it(f,source.root);!it.Done();it.Next()) {
      const auto arc=it.Value();Space old=cursor(source,arc.nextstate);original.emplace_back(arc.ilabel,old);
      Space next=arc.ilabel==prefix[i]?result:old;
      if(!known_empty(next)) edges.emplace_back(arc.ilabel,std::move(next));
    }
    bool terminal=accepting(f,source.root);
    branch_view(terminal,std::move(original),source.value->coaccessible,&source);
    result=branch_view(terminal,std::move(edges),source.value->coaccessible);
  }
  return result;
}
Space raffine(const Space& a,const Space& b) {
  if(known_empty(a)||known_empty(b)) return a;if(same(a,b)) return Space();
  Path prefix;if(one_word(b,prefix)) return remove_prefix(a,prefix);
  return delayed(std::make_shared<ProductMachine<ProductOp::Raffination>>(a,b));
}
static State follow_label(const BaseFst& f,State state,int label) {return transition(f,state,label);}
Space first_listed(const Space& source,const std::vector<Path>& order,bool prefix) {
  if(known_empty(source)||order.empty()) return Space();
  // Index the existing ordered control literal, never the paths of a space.
  // Value-keyed caching also handles mutable callers and reused vector addresses.
  struct Plan {Fst trie;std::vector<size_t> first,terminal;};
  static thread_local std::map<std::vector<Path>,Plan> plans;
  auto found=plans.find(order);
  if(found==plans.end()) {
    struct Node {std::map<int,size_t> arcs;size_t first,terminal;};
    const size_t absent=order.size();std::vector<Node> nodes{{{},absent,absent}};
    for(size_t rank=0;rank<order.size();++rank) {
      size_t state=0;nodes[state].first=std::min(nodes[state].first,rank);
      for(int label:order[rank]) {
        auto edge=nodes[state].arcs.find(label);
        if(edge==nodes[state].arcs.end()) {size_t next=nodes.size();nodes[state].arcs[label]=next;nodes.push_back({{},absent,absent});state=next;}
        else state=edge->second;
        nodes[state].first=std::min(nodes[state].first,rank);
      }
      nodes[state].terminal=std::min(nodes[state].terminal,rank);
    }
    Plan plan;for(size_t i=0;i<nodes.size();++i) plan.trie.AddState();plan.trie.SetStart(0);
    for(size_t i=0;i<nodes.size();++i) {
      plan.first.push_back(nodes[i].first);plan.terminal.push_back(nodes[i].terminal);
      if(nodes[i].terminal!=absent) plan.trie.SetFinal(i,Weight::One());
      for(auto [label,next]:nodes[i].arcs) plan.trie.AddArc(i,Arc(label,label,Weight::One(),next));
    }
    if(plans.size()>=16) plans.clear();found=plans.emplace(order,std::move(plan)).first;
  }
  const auto& plan=found->second;const auto& input=graph(source);
  struct Item {size_t rank;State input,code;bool goal;bool operator<(const Item& b) const {return rank>b.rank;}};
  std::priority_queue<Item> pending;pending.push({plan.first[0],source.root,0,false});
  while(!pending.empty()) {
    auto item=pending.top();pending.pop();if(item.goal) return singleton(order[item.rank]);
    size_t rank=plan.terminal[item.code];if(rank<order.size()&&((prefix&&productive(cursor(source,item.input)))||(!prefix&&accepting(input,item.input)))) pending.push({rank,item.input,item.code,true});
    for(fst::ArcIterator<Fst> it(plan.trie,item.code);!it.Done();it.Next()) {
      auto edge=it.Value();State next=follow_label(input,item.input,edge.ilabel);
      if(next!=fst::kNoStateId) pending.push({plan.first[edge.nextstate],next,edge.nextstate,false});
    }
  } return Space();
}
Space first_matching(const Space& source,const Space& allowed,bool prefix) {
  if(known_empty(source)||known_empty(allowed)) return Space();const BaseFst& a=graph(source);const BaseFst& b=graph(allowed);
  using Pair=std::pair<State,State>;struct Edge {int label;Pair next;};struct Frame {Pair states;size_t index=0;std::vector<Edge> edges;};
  auto frame=[&](Pair states) {
    Frame out{states,0,{}};fst::ArcIterator<BaseFst> left(a,states.first),right(b,states.second);
    while(!left.Done()&&!right.Done()) {
      if(left.Value().ilabel<right.Value().ilabel) left.Next();
      else if(right.Value().ilabel<left.Value().ilabel) right.Next();
      else {out.edges.push_back({left.Value().ilabel,{left.Value().nextstate,right.Value().nextstate}});left.Next();right.Next();}
    } return out;
  };
  auto accepted=[&](Pair states){return final(b,states.second)&&((prefix&&productive(cursor(source,states.first)))||(!prefix&&final(a,states.first)));};
  Pair root{source.root,allowed.root};if(accepted(root)) return singleton(Path{});
  std::set<Pair> dead;std::vector<Frame> stack{frame(root)};Path word;
  while(!stack.empty()) {
    auto& top=stack.back();
    if(top.index==top.edges.size()) {dead.insert(top.states);stack.pop_back();if(!stack.empty()) word.pop_back();}
    else {
      Edge edge=top.edges[top.index++];if(dead.count(edge.next)) continue;
      word.push_back(edge.label);if(accepted(edge.next)) return singleton(word);stack.push_back(frame(edge.next));
    }
  } return Space();
}
Space first_fibonacci(const Space& source,uint64_t maximum,int digits,bool prefix) {
  if(known_empty(source)||!maximum||digits<2) return Space();const BaseFst& f=graph(source);
  constexpr uint64_t infinity=std::numeric_limits<uint64_t>::max();
  const size_t none=std::numeric_limits<size_t>::max();
  std::vector<uint64_t> fibonacci(digits,1);fibonacci[1]=2;
  for(int i=2;i<digits;++i) fibonacci[i]=fibonacci[i-1]>infinity-fibonacci[i-2]?infinity:fibonacci[i-1]+fibonacci[i-2];
  // A* on (acceptor state, code position, previous bit). A nonterminal 1
  // contributes F[position] to the intern ordinal; the terminating 1 costs 0.
  // The consistent lower bound is 0 after a 1, otherwise F[position]. Thus
  // the first accepted code is the smallest ordinal without scanning all heads.
  struct Node {State state;int depth;bool previous;size_t parent;int label;};
  struct Item {uint64_t bound,cost;size_t node;bool operator<(const Item& b) const {return std::tie(bound,cost,node)>std::tie(b.bound,b.cost,b.node);}};
  std::vector<Node> nodes{{source.root,0,false,none,0}};
  std::priority_queue<Item> pending;pending.push({1,0,0});
  std::unordered_set<size_t> visited_states;
  auto visited=[&](const Node& n) {size_t key=(size_t(n.state)*size_t(digits+1)+size_t(n.depth))*2+size_t(n.previous);return !visited_states.insert(key).second;};
  while(!pending.empty()) {
    Item item=pending.top();pending.pop();if(item.bound>maximum) break;
    Node node=nodes[item.node];if(visited(node)) continue;
    if(node.previous) {
      State end=follow_label(f,node.state,2);
      if(end!=fst::kNoStateId && ((prefix&&productive(cursor(source,end)))||(!prefix&&accepting(f,end)))) {
        Path word;for(size_t at=item.node;nodes[at].parent!=none;at=nodes[at].parent) word.push_back(nodes[at].label);
        std::reverse(word.begin(),word.end());word.push_back(2);return singleton(word);
      }
    }
    for(int label:{2,1}) {
      if(node.previous&&label==2) continue; // first 11 always ends this code
      State next=follow_label(f,node.state,label);if(next==fst::kNoStateId) continue;
      int depth=node.depth+1;bool bit=label==2;
      if(depth>=digits || (!bit&&depth+1>=digits)) continue;
      uint64_t extra=bit?fibonacci[node.depth]:0;
      if(extra>maximum-item.cost) continue;
      uint64_t cost=item.cost+extra,lower=bit?0:fibonacci[depth];
      if(lower>maximum-cost) continue;
      size_t index=nodes.size();nodes.push_back({next,depth,bit,item.node,label});pending.push({cost+lower,cost,index});
    }
  } return Space();
}
Space restrict_to(const Space& a,const Space& prefixes) {
  if(known_empty(a)||known_empty(prefixes)) return Space();
  return delayed(std::make_shared<ProductMachine<ProductOp::Restriction>>(a,prefixes));
}
std::vector<std::pair<int,Space>> branches(Context& c,const Space& a) {
  std::vector<std::pair<int,Space>> out;if(known_empty(a)) return out;const auto& f=graph(a);
  for(fst::ArcIterator<BaseFst> it(f,a.root);!it.Done();it.Next()) {
    const auto& arc=it.Value();auto tail=cursor(a,arc.nextstate);
    if(!tail.empty()) out.emplace_back(arc.ilabel,std::move(tail));
  }
  if(c.symbolic) std::sort(out.begin(),out.end(),[&](const auto& x,const auto& y){return c.less({x.first},{y.first});});
  return out;
}
bool has_head(const Space& source) {
  if(known_empty(source)) return false;const auto& f=graph(source);
  for(fst::ArcIterator<BaseFst> it(f,source.root);!it.Done();it.Next())
    if(productive(cursor(source,it.Value().nextstate))) return true;
  return false;
}
Space head_space(const Space& source,int alphabet) {
  if(known_empty(source)) return Space();const auto& f=graph(source);
  if(!f.NumArcs(source.root)) return Space();
  if(alphabet>0&&alphabet<=256) {
    std::array<uint64_t,4> mask{};
    for(fst::ArcIterator<BaseFst> it(f,source.root);!it.Done();it.Next()) {
      const auto& arc=it.Value();if(productive(cursor(source,arc.nextstate))) {
        if(arc.ilabel<1||arc.ilabel>alphabet) throw std::runtime_error("head outside the dialect alphabet");
        unsigned bit=arc.ilabel-1;mask[bit/64]|=uint64_t(1)<<(bit%64);
      }
    }
    static thread_local std::map<std::array<uint64_t,4>,Space> cache;
    auto found=cache.find(mask);if(found!=cache.end()) return found->second;
    std::vector<Path> paths;for(int bit=0;bit<alphabet;++bit) if(mask[bit/64]&(uint64_t(1)<<(bit%64))) paths.push_back(Path{bit+1});
    auto value=literal(paths);if(cache.size()>=1024) cache.clear();cache.emplace(mask,value);return value;
  }
  Fst out;State start=out.AddState(),end=out.AddState();out.SetStart(start);out.SetFinal(end,Weight::One());
  for(fst::ArcIterator<BaseFst> it(f,source.root);!it.Done();it.Next()) {
    const auto& arc=it.Value();if(productive(cursor(source,arc.nextstate))) out.AddArc(start,Arc(arc.ilabel,arc.ilabel,Weight::One(),end));
  }
  if(!out.NumArcs(start)) return Space();
  return adopt(std::move(out));
}
Space join_outputs(std::vector<Space> values) {
  values.erase(std::remove_if(values.begin(),values.end(),known_empty),values.end());
  if(values.empty()) return Space();if(values.size()==1) return values.front();
  const bool trimmed=std::all_of(values.begin(),values.end(),[](const Space& s){return s.value->coaccessible;});
  return delayed(std::make_shared<UnionMachine>(std::move(values)),trimmed);
}
Space tails_union(const Space& a) {
  if(known_empty(a)) return a;std::vector<Space> tails;const auto& f=graph(a);
  for(fst::ArcIterator<BaseFst> it(f,a.root);!it.Done();it.Next()) tails.push_back(cursor(a,it.Value().nextstate));
  std::sort(tails.begin(),tails.end(),CursorLess());tails.erase(std::unique(tails.begin(),tails.end(),same),tails.end());
  return join_outputs(std::move(tails));
}
Space tails_intersection(const Space& a) {
  if(known_empty(a)) return a;const auto& f=graph(a);std::set<State> seen;Space result;bool first=true;
  for(fst::ArcIterator<BaseFst> it(f,a.root);!it.Done();it.Next()) {
    State state=it.Value().nextstate;if(!seen.insert(state).second) continue;
    auto tail=cursor(a,state);if(tail.empty()) continue;
    result=first?tail:intersect(result,tail);first=false;
  }
  return result;
}
Space prefix_closure(const Space& a) {
  if(known_empty(a)) return a;
  return delayed(std::make_shared<ClosureMachine>(a,true,true));
}
Space suffix_closure(const Space& a,bool include_empty) {
  if(known_empty(a)) return a;const auto& f=graph(a);std::set<State> seen;std::vector<State> pending{a.root};std::vector<Space> tails;
  for(size_t i=0;i<pending.size();++i) {
    State s=pending[i];if(!seen.insert(s).second) continue;tails.push_back(cursor(a,s));
    for(fst::ArcIterator<BaseFst> it(f,s);!it.Done();it.Next()) pending.push_back(it.Value().nextstate);
  }
  auto result=join_outputs(std::move(tails));
  return include_empty?result:delayed(std::make_shared<ClosureMachine>(result,false,true));
}
std::vector<Path> enumerate(const Space& a) {
  std::vector<Path> out;if(known_empty(a)) return out;const auto& f=graph(a);
  struct Frame {State state;size_t arc;};std::vector<Frame> stack{{a.root,0}};Path path;
  if(accepting(f,a.root)) out.push_back(path);
  while(!stack.empty()) {
    auto& top=stack.back();
    if(top.arc==f.NumArcs(top.state)) {stack.pop_back();if(!stack.empty()) path.pop_back();}
    else {fst::ArcIterator<BaseFst> it(f,top.state);it.Seek(top.arc++);auto arc=it.Value();
      if(!productive(cursor(a,arc.nextstate))) continue;
      path.push_back(arc.ilabel);if(accepting(f,arc.nextstate)) out.push_back(path);stack.push_back({arc.nextstate,0});}
  }
  return out;
}
Path singleton_path(const Space& s) {
  auto paths=enumerate(s);if(paths.size()!=1) throw std::runtime_error("expected a singleton path");return std::move(paths.front());
}
using Cardinality = boost::multiprecision::cpp_int;
SpaceStats inspect(const Space& space) {
  const auto& data=analysis(space);Cardinality paths=known_empty(space)?Cardinality(0):data.counts.at(space.root);
  return {data.states,data.arcs,paths.convert_to<std::string>()};
}
std::shared_ptr<const Context::SymbolOrder> Context::ordering() const {
  if(symbol_order && symbol_order->size()==symbols.size()) return symbol_order;
  std::vector<int> labels;labels.reserve(symbols.size());for(const auto& [id,text]:symbols) labels.push_back(id);
  std::sort(labels.begin(),labels.end(),[&](int a,int b){return symbols.at(a)<symbols.at(b);});
  auto result=std::make_shared<SymbolOrder>();result->reserve(labels.size());
  for(size_t rank=0;rank<labels.size();++rank) result->emplace(labels[rank],rank);
  symbol_order=result;return result;
}
Space range(Context& c,const Space& a,int start,int end) {
  if(known_empty(a)||(!start&&!end)) return a;
  const bool bounded=start>=0&&end>0;
  Cardinality cap=bounded?Cardinality(end)-(start==0?0:1):Cardinality(0);
  if(bounded&&cap<=0) return Space();
  auto counts=std::make_shared<CountOracle>(a,cap);
  const Cardinality n=counts->get(a.root);
  auto clamp=[&](Cardinality x)->Cardinality {return x<0?Cardinality(0):x>n?n:x;};
  Cardinality lo=0,hi=n;
  if(start>0) lo=Cardinality(start)-1;else if(start<0) lo=n+start;
  if(end>0) hi=Cardinality(end)-(start==0?0:1);else if(end<0) hi=n+end;
  lo=clamp(lo);hi=clamp(hi);
  if(hi<=lo) return Space();
  if(lo==0&&hi==n&&(!bounded||n<cap)) return a;
  return delayed(std::make_shared<RangeMachine>(a,lo,hi,std::move(counts),c.symbolic?c.ordering():nullptr),true);
}
static int count(std::istream& in) { int64_t n; if(!(in>>n) || n<0 || n>std::numeric_limits<int>::max()) throw std::runtime_error("invalid native protocol count"); return n; }
static Path read_path(std::istream& in) { int n=count(in); Path p; p.reserve(n); while(n--) { int label=count(in); if(!label) throw std::runtime_error("epsilon label in path payload"); p.push_back(label); } return p; }
Space read_space(std::istream& in) { int n=count(in); std::vector<Path> ps; ps.reserve(n); while(n--) ps.push_back(read_path(in)); return literal(ps); }
void write_space(std::ostream& out,const Space& s) { auto ps=enumerate(s); out<<ps.size(); for(const auto& p:ps) { out<<' '<<p.size(); for(int x:p) out<<' '<<x; } }
static void read_symbols_from(Context& c,std::istream& input) {
  int n=count(input);while(n--) {
    int label=count(input),len=count(input);std::vector<int> units;while(len--) units.push_back(count(input));
    auto found=c.symbols.find(label);
    if(found==c.symbols.end()||found->second!=units) {c.symbol_order.reset();c.symbols[label]=std::move(units);}
  }
}
void Context::read_symbols() {read_symbols_from(*this,*input);}
void configure_symbols(Context& c,const char* text) {std::istringstream input(text);read_symbols_from(c,input);}
Space literal_text(const char* text) {std::istringstream input(text);return read_space(input);}
std::vector<Path> paths_text(const char* text) {std::istringstream input(text);int n=count(input);std::vector<Path> paths;paths.reserve(n);while(n--) paths.push_back(read_path(input));return paths;}
Space Context::callback_space(int id,const Space& arg) {
  auto native=native_callbacks.find(id);if(native!=native_callbacks.end()) return native->second(*this,arg);
  *output<<"C "<<id<<' '; write_space(*output,arg); *output<<std::endl;
  std::string status; if(!(*input>>status) || status!="V") throw std::runtime_error("host callback failed"); read_symbols(); return read_space(*input);
}
Path Context::callback_path(int id,const Space& arg) { auto ps=enumerate(callback_space(id,arg)); if(ps.size()!=1) throw std::runtime_error("path callback requires one path"); return ps.front(); }
Env read_env(Context& c) { c.read_symbols(); Env e; int n=count(*c.input); while(n--) {int key=count(*c.input);e.paths[key]=read_path(*c.input);} n=count(*c.input);while(n--){int key=count(*c.input);e.spaces[key]=read_space(*c.input);}return e; }
int run(Context& c,Space (*dispatch)(int,Context&,const Env&)) {
#if defined(__unix__) || defined(__APPLE__)
  // Generated ordinary recursive functions use the native stack. Match the
  // reference's large-depth contract without inheriting a small shell limit.
  struct rlimit limit;
  if(getrlimit(RLIMIT_STACK,&limit)==0) {
    const char* configured=std::getenv("ZIPPY_STACK_MIB");
    unsigned long mib=configured?std::strtoul(configured,nullptr,10):256;
    rlim_t requested=rlim_t(mib)*1024*1024;
    if(limit.rlim_max!=RLIM_INFINITY) requested=std::min(requested,limit.rlim_max);
    if(limit.rlim_cur<requested) {limit.rlim_cur=requested;setrlimit(RLIMIT_STACK,&limit);}
  }
#endif
  std::string command; while(*c.input>>command) {
    if(command=="Q") return 0;
    try {
      if(command!="R" && command!="B") throw std::runtime_error("unknown command");
      int id=count(*c.input); int repeats=command=="B"?count(*c.input):1;
      if(repeats<1) throw std::runtime_error("benchmark repeats must be positive");
      Env e=read_env(c); Space result;
      if(command=="B") {
        const char* configured=std::getenv("ZIPPY_BENCH_WARMUPS");
        unsigned warmups=configured?std::min(10000UL,std::strtoul(configured,nullptr,10)):2;
        for(unsigned i=0;i<warmups;++i) {result=dispatch(id,c,e);realize(result);}
        auto start=std::chrono::steady_clock::now();
        for(int i=0;i<repeats;++i) {result=dispatch(id,c,e);realize(result);}
        auto ns=std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now()-start).count();
        const auto stat=inspect(result);
        *c.output<<"T "<<ns/repeats<<' '<<stat.states<<' '<<stat.arcs<<' '<<stat.paths<<std::endl;
      } else result=dispatch(id,c,e);
      if(command=="B") *c.output<<"N"<<std::endl;
      else {*c.output<<"O ";write_space(*c.output,result);*c.output<<std::endl;}
    }
    catch(const std::exception& ex) { *c.output<<"E "<<ex.what()<<std::endl; return 1; }
  } return 0;
}
} // namespace zippy
