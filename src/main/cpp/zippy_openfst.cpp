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
#if defined(__unix__) || defined(__APPLE__)
#include <sys/resource.h>
#endif

namespace zippy {
using Arc = fst::StdArc;
using Fst = fst::VectorFst<Arc>;
using State = Arc::StateId;
using Weight = Arc::Weight;
struct Acceptor { Fst fst; explicit Acceptor(Fst f) : fst(std::move(f)) {} };
static bool final(const Fst& f, State s) { return f.Final(s) != Weight::Zero(); }
static Space adopt(Fst f) {
  if (f.Properties(fst::kError, false)) throw std::runtime_error("OpenFst operation failed");
  if(f.Start()==fst::kNoStateId) return Space();
  return Space(std::make_shared<Acceptor>(std::move(f)));
}
static std::shared_ptr<const Acceptor> empty_acceptor() {
  static const auto value=std::make_shared<Acceptor>(Fst());return value;
}
Space::Space() : value(empty_acceptor()) {}
bool Space::empty() const { return value->fst.Start() == fst::kNoStateId; }
static Fst reduce_acyclic(const Fst& input) {
  if(input.Start()==fst::kNoStateId) return Fst();
  const State n=input.NumStates();std::vector<size_t> indegree(n);std::vector<State> order;
  for(State s=0;s<n;++s) for(fst::ArcIterator<Fst> it(input,s);!it.Done();it.Next()) ++indegree[it.Value().nextstate];
  for(State s=0;s<n;++s) if(!indegree[s]) order.push_back(s);
  for(size_t i=0;i<order.size();++i) for(fst::ArcIterator<Fst> it(input,order[i]);!it.Done();it.Next()) if(!--indegree[it.Value().nextstate]) order.push_back(it.Value().nextstate);
  if(order.size()!=size_t(n)) throw std::runtime_error("cyclic Zippy space");
  using Signature=std::pair<bool,std::vector<std::pair<int,State>>>;
  std::map<Signature,State> unique;std::vector<State> ids(n,fst::kNoStateId);Fst out;
  for(size_t i=order.size();i-- > 0;) {
    State old=order[i];Signature signature{final(input,old),{}};
    for(fst::ArcIterator<Fst> it(input,old);!it.Done();it.Next()) {auto a=it.Value();if(ids[a.nextstate]!=fst::kNoStateId) signature.second.emplace_back(a.ilabel,ids[a.nextstate]);}
    if(!signature.first && signature.second.empty()) continue;
    auto found=unique.find(signature);
    if(found!=unique.end()) ids[old]=found->second;
    else {State state=out.AddState();ids[old]=state;unique.emplace(signature,state);if(signature.first) out.SetFinal(state,Weight::One());for(auto [label,next]:signature.second) out.AddArc(state,Arc(label,label,Weight::One(),next));}
  }
  if(ids[input.Start()]==fst::kNoStateId) return Fst();
  out.SetStart(ids[input.Start()]);return out;
}
static Space normalize(Fst f) {
  fst::RmEpsilon(&f);
  Fst deterministic;
  fst::Determinize(f, &deterministic);
  fst::Connect(&deterministic);
  deterministic=reduce_acyclic(deterministic);
  fst::ArcSort(&deterministic, fst::ILabelCompare<Arc>());
  return adopt(std::move(deterministic));
}
static Fst rooted(const Fst& f, State start) {
  Fst result; std::map<State,State> ids; std::vector<State> pending{start};
  ids[start]=result.AddState(); result.SetStart(0);
  for(size_t i=0;i<pending.size();++i) {
    State old=pending[i], dest=ids.at(old); result.SetFinal(dest,f.Final(old));
    for(fst::ArcIterator<Fst> it(f,old);!it.Done();it.Next()) {
      auto arc=it.Value(); auto found=ids.find(arc.nextstate);
      if(found==ids.end()) { State next=result.AddState(); ids[arc.nextstate]=next; pending.push_back(arc.nextstate); arc.nextstate=next; }
      else arc.nextstate=found->second;
      result.AddArc(dest,arc);
    }
  } return result;
}
Env Env::bind_path(int key, const Path& value) const { Env r(*this); if(key >= 0) r.paths[key] = value; return r; }
Env Env::bind_space(int key, const Space& value) const { Env r(*this); if(key >= 0) r.spaces[key] = value; return r; }
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
// The inputs are deterministic, epsilon-free acceptors. A synchronous product
// computes finite set algebra directly on their graphs, avoiding epsilon
// removal and subset construction for operations that cannot need either.
enum class SetOp { Union, Intersection, Difference };
static Space combine(const Space& left,const Space& right,SetOp op) {
  const Fst& a=left.value->fst;const Fst& b=right.value->fst;
  using Pair=std::pair<State,State>;std::map<Pair,State> ids;std::vector<Pair> pending;Fst out;
  auto intern=[&](Pair pair) {auto found=ids.find(pair);if(found!=ids.end()) return found->second;State s=out.AddState();ids[pair]=s;pending.push_back(pair);return s;};
  out.SetStart(intern({a.Start(),b.Start()}));
  for(size_t i=0;i<pending.size();++i) {
    const auto [x,y]=pending[i];State state=ids.at(pending[i]);
    bool af=x!=fst::kNoStateId&&final(a,x),bf=y!=fst::kNoStateId&&final(b,y);
    bool terminal=op==SetOp::Union?af||bf:op==SetOp::Intersection?af&&bf:af&&!bf;
    if(terminal) out.SetFinal(state,Weight::One());
    std::vector<Arc> ax,bx;
    if(x!=fst::kNoStateId) for(fst::ArcIterator<Fst> it(a,x);!it.Done();it.Next()) ax.push_back(it.Value());
    if(y!=fst::kNoStateId) for(fst::ArcIterator<Fst> it(b,y);!it.Done();it.Next()) bx.push_back(it.Value());
    size_t l=0,r=0;
    while(l<ax.size()||r<bx.size()) {
      int label;State nexta=fst::kNoStateId,nextb=fst::kNoStateId;
      if(r==bx.size()||(l<ax.size()&&ax[l].ilabel<bx[r].ilabel)) {label=ax[l].ilabel;nexta=ax[l++].nextstate;}
      else if(l==ax.size()||bx[r].ilabel<ax[l].ilabel) {label=bx[r].ilabel;nextb=bx[r++].nextstate;}
      else {label=ax[l].ilabel;nexta=ax[l++].nextstate;nextb=bx[r++].nextstate;}
      if(op==SetOp::Intersection&&(nexta==fst::kNoStateId||nextb==fst::kNoStateId)) continue;
      if(op==SetOp::Difference&&nexta==fst::kNoStateId) continue;
      out.AddArc(state,Arc(label,label,Weight::One(),intern({nexta,nextb})));
    }
  } return adopt(reduce_acyclic(out));
}
Space unite(const Space& a,const Space& b) {
  if(a.empty()) return b;if(b.empty()||a.value==b.value) return a;return combine(a,b,SetOp::Union);
}
Space intersect(const Space& a,const Space& b) {
  if(a.value==b.value) return a;if(a.empty()||b.empty()) return Space();return combine(a,b,SetOp::Intersection);
}
Space subtract(const Space& a,const Space& b) {
  if(a.value==b.value) return Space();if(a.empty()||b.empty()) return a;return combine(a,b,SetOp::Difference);
}
bool equal(const Space& left,const Space& right) {
  if(left.value==right.value) return true;if(left.empty()||right.empty()) return left.empty()&&right.empty();
  const Fst& a=left.value->fst;const Fst& b=right.value->fst;std::set<std::pair<State,State>> seen;
  std::vector<std::pair<State,State>> pending{{a.Start(),b.Start()}};
  for(size_t i=0;i<pending.size();++i) {
    auto [x,y]=pending[i];if(!seen.emplace(x,y).second) continue;
    if(final(a,x)!=final(b,y)||a.NumArcs(x)!=b.NumArcs(y)) return false;
    fst::ArcIterator<Fst> ai(a,x),bi(b,y);
    for(;!ai.Done();ai.Next(),bi.Next()) {if(ai.Value().ilabel!=bi.Value().ilabel) return false;pending.emplace_back(ai.Value().nextstate,bi.Value().nextstate);}
  } return true;
}
Space compose(const Space& a,const Space& b) {
  if(a.empty()||b.empty()) return Space();
  const Fst& left=a.value->fst;const Fst& right=b.value->fst;
  if(final(left,left.Start())&&left.NumArcs(left.Start())==0) return b;
  if(final(right,right.Start())&&right.NumArcs(right.Start())==0) return a;
  bool prefix_free=true;
  for(State s=0;s<left.NumStates();++s) if(final(left,s)&&left.NumArcs(s)) {prefix_free=false;break;}
  if(prefix_free) {
    // No accepted word prefixes another. Replace every final leaf with the
    // same right root, preserving DAG sharing even for billion-path products.
    Fst out(right);std::vector<State> ids(left.NumStates());
    for(State s=0;s<left.NumStates();++s) ids[s]=final(left,s)?right.Start():out.AddState();
    for(State s=0;s<left.NumStates();++s) if(!final(left,s)) for(fst::ArcIterator<Fst> it(left,s);!it.Done();it.Next()) {auto arc=it.Value();arc.nextstate=ids[arc.nextstate];out.AddArc(ids[s],arc);}
    out.SetStart(ids[left.Start()]);return adopt(std::move(out));
  }
  Fst f(left);fst::Concat(&f,right);return normalize(std::move(f));
}
Space wrap(const Space& a, const Path& p) { return p.empty() ? a : compose(singleton(p), a); }
Space unwrap(const Space& a, const Path& p) {
  if(a.empty() || p.empty()) return a;
  const Fst& f = a.value->fst; State s = f.Start();
  for(int label : p) {
    State next = fst::kNoStateId;
    for(fst::ArcIterator<Fst> it(f,s); !it.Done(); it.Next()) if(it.Value().ilabel == label) { next = it.Value().nextstate; break; }
    if(next == fst::kNoStateId) return Space(); s = next;
  }
  return adopt(rooted(f,s));
}
static State follow_label(const Fst& f,State state,int label) {
  size_t lo=0,hi=f.NumArcs(state);fst::ArcIterator<Fst> arcs(f,state);
  while(lo<hi) {size_t mid=(lo+hi)/2;arcs.Seek(mid);if(arcs.Value().ilabel<label) lo=mid+1;else hi=mid;}
  if(lo==f.NumArcs(state)) return fst::kNoStateId;
  arcs.Seek(lo);return arcs.Value().ilabel==label?arcs.Value().nextstate:fst::kNoStateId;
}
Space first_listed(const Space& source,const std::vector<Path>& order,bool prefix) {
  if(source.empty()||order.empty()) return Space();
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
  const auto& plan=found->second;const auto& input=source.value->fst;
  struct Item {size_t rank;State input,code;bool goal;bool operator<(const Item& b) const {return rank>b.rank;}};
  std::priority_queue<Item> pending;pending.push({plan.first[0],input.Start(),0,false});
  while(!pending.empty()) {
    auto item=pending.top();pending.pop();if(item.goal) return singleton(order[item.rank]);
    size_t rank=plan.terminal[item.code];if(rank<order.size()&&(prefix||final(input,item.input))) pending.push({rank,item.input,item.code,true});
    for(fst::ArcIterator<Fst> it(plan.trie,item.code);!it.Done();it.Next()) {
      auto edge=it.Value();State next=follow_label(input,item.input,edge.ilabel);
      if(next!=fst::kNoStateId) pending.push({plan.first[edge.nextstate],next,edge.nextstate,false});
    }
  } return Space();
}
Space first_matching(const Space& source,const Space& allowed,bool prefix) {
  if(source.empty()||allowed.empty()) return Space();const Fst& a=source.value->fst;const Fst& b=allowed.value->fst;
  using Pair=std::pair<State,State>;struct Edge {int label;Pair next;};struct Frame {Pair states;size_t index=0;std::vector<Edge> edges;};
  auto frame=[&](Pair states) {
    Frame out{states,0,{}};fst::ArcIterator<Fst> left(a,states.first),right(b,states.second);
    while(!left.Done()&&!right.Done()) {
      if(left.Value().ilabel<right.Value().ilabel) left.Next();
      else if(right.Value().ilabel<left.Value().ilabel) right.Next();
      else {out.edges.push_back({left.Value().ilabel,{left.Value().nextstate,right.Value().nextstate}});left.Next();right.Next();}
    } return out;
  };
  auto accepted=[&](Pair states){return final(b,states.second)&&(prefix||final(a,states.first));};
  Pair root{a.Start(),b.Start()};if(accepted(root)) return singleton(Path{});
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
  if(source.empty()||!maximum||digits<2) return Space();const Fst& f=source.value->fst;
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
  std::vector<Node> nodes{{f.Start(),0,false,none,0}};
  std::priority_queue<Item> pending;pending.push({1,0,0});
  const size_t cells=size_t(f.NumStates())*size_t(digits+1)*2;
  std::vector<unsigned char> dense(cells<=1000000?cells:0);std::unordered_set<size_t> sparse;
  auto visited=[&](const Node& n) {size_t key=(size_t(n.state)*size_t(digits+1)+size_t(n.depth))*2+size_t(n.previous);if(dense.empty()) return !sparse.insert(key).second;bool old=dense[key];dense[key]=1;return old;};
  while(!pending.empty()) {
    Item item=pending.top();pending.pop();if(item.bound>maximum) break;
    Node node=nodes[item.node];if(visited(node)) continue;
    if(node.previous) {
      State end=follow_label(f,node.state,2);
      if(end!=fst::kNoStateId && (prefix||final(f,end))) {
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
  if(a.empty()||prefixes.empty()) return Space();
  const Fst& left=a.value->fst;const Fst& right=prefixes.value->fst;
  if(final(right,right.Start())) return a;
  constexpr State matched=-2;using Pair=std::pair<State,State>;std::map<Pair,State> ids;std::vector<Pair> pending;Fst out;
  auto intern=[&](Pair key){auto found=ids.find(key);if(found!=ids.end()) return found->second;State s=out.AddState();ids[key]=s;pending.push_back(key);return s;};
  out.SetStart(intern({left.Start(),right.Start()}));
  for(size_t i=0;i<pending.size();++i) {
    const auto [x,y]=pending[i];State dest=ids.at(pending[i]);const bool accepted=y==matched||final(right,y);
    if(accepted&&final(left,x)) out.SetFinal(dest,Weight::One());
    std::vector<Arc> filter;if(!accepted) for(fst::ArcIterator<Fst> it(right,y);!it.Done();it.Next()) filter.push_back(it.Value());
    size_t j=0;
    for(fst::ArcIterator<Fst> it(left,x);!it.Done();it.Next()) {
      auto arc=it.Value();State next=matched;
      if(!accepted) {while(j<filter.size()&&filter[j].ilabel<arc.ilabel) ++j;if(j==filter.size()||filter[j].ilabel!=arc.ilabel) continue;next=filter[j].nextstate;}
      arc.nextstate=intern({arc.nextstate,next});out.AddArc(dest,arc);
    }
  } return adopt(reduce_acyclic(out));
}
static std::vector<std::pair<int,Space>> groups(Context& c, const Space& a) {
  std::vector<std::pair<int,Space>> r; if(a.empty()) return r;
  const Fst& f=a.value->fst;std::map<State,Space> cache;
  for(fst::ArcIterator<Fst> it(f,f.Start()); !it.Done(); it.Next()) {
    const auto& arc=it.Value();auto found=cache.find(arc.nextstate);
    if(found==cache.end()) found=cache.emplace(arc.nextstate,adopt(rooted(f,arc.nextstate))).first;
    r.emplace_back(arc.ilabel,found->second);
  }
  std::sort(r.begin(),r.end(),[&](const auto& x,const auto& y){return c.less({x.first},{y.first});}); return r;
}
Space head_space(const Space& source) {
  if(source.empty()) return source;const Fst& f=source.value->fst;
  if(!f.NumArcs(f.Start())) return Space();
  Fst out;State start=out.AddState(),end=out.AddState();out.SetStart(start);out.SetFinal(end,Weight::One());
  for(fst::ArcIterator<Fst> it(f,f.Start());!it.Done();it.Next()) {int label=it.Value().ilabel;out.AddArc(start,Arc(label,label,Weight::One(),end));}
  return adopt(std::move(out));
}
Space tails_union(const Space& a) {
  if(a.empty()) return a;const Fst& source=a.value->fst;std::set<State> seen;
  for(fst::ArcIterator<Fst> it(source,source.Start());!it.Done();it.Next()) seen.insert(it.Value().nextstate);
  if(seen.empty()) return Space();if(seen.size()==1) return adopt(rooted(source,*seen.begin()));
  Fst f(source);State start=f.AddState();f.SetStart(start);
  for(State state:seen) f.AddArc(start,Arc(0,0,Weight::One(),state));
  return normalize(std::move(f));
}
Space tails_intersection(const Space& a) {
  if(a.empty()) return a; const Fst& f=a.value->fst; Space result; bool first=true;
  std::set<State> seen;
  for(fst::ArcIterator<Fst> it(f,f.Start()); !it.Done(); it.Next()) {
    if(!seen.insert(it.Value().nextstate).second) continue;
    auto next=adopt(rooted(f,it.Value().nextstate)); result=first ? next : intersect(result,next); first=false;
  } return result;
}
Space prefix_closure(const Space& a) {
  if(a.empty()) return a; Fst f(a.value->fst);
  // Connected finite acceptors have every state on some accepted path.
  for(fst::StateIterator<Fst> it(f); !it.Done(); it.Next()) f.SetFinal(it.Value(),it.Value()==f.Start()?Weight::Zero():Weight::One());
  fst::Connect(&f); return adopt(std::move(f));
}
Space suffix_closure(const Space& a, bool include_empty) {
  if(a.empty()) return a; Fst f(a.value->fst); const State n=f.NumStates(), start=f.AddState(); f.SetStart(start);
  for(State s=0;s<n;++s) f.AddArc(start,Arc(0,0,Weight::One(),s));
  auto r=normalize(std::move(f));
  if(include_empty || r.empty()) return r;
  Fst noempty(r.value->fst); noempty.SetFinal(noempty.Start(),Weight::Zero()); fst::Connect(&noempty); return adopt(std::move(noempty));
}
std::vector<Path> enumerate(const Space& a) {
  std::vector<Path> out; if(a.empty()) return out;
  // Explicit traversal also handles very long paths without native recursion.
  const Fst& f=a.value->fst; struct Frame { State state; size_t arc; std::vector<Arc> arcs; };
  auto frame=[&](State s){ Frame r{s,0,{}}; for(fst::ArcIterator<Fst> it(f,s);!it.Done();it.Next()) r.arcs.push_back(it.Value()); return r; };
  std::vector<Frame> stack{frame(f.Start())}; Path p; if(final(f,f.Start())) out.push_back(p);
  while(!stack.empty()) {
    auto& top=stack.back();
    if(top.arc==top.arcs.size()) { stack.pop_back(); if(!stack.empty()) p.pop_back(); }
    else { Arc a=top.arcs[top.arc++]; p.push_back(a.ilabel); if(final(f,a.nextstate)) out.push_back(p); stack.push_back(frame(a.nextstate)); }
  } return out;
}
using Cardinality = boost::multiprecision::cpp_int;
static std::vector<Cardinality> path_counts(const Fst& f) {
  const State n=f.NumStates(); std::vector<size_t> indegree(n); std::vector<State> order;
  for(State s=0;s<n;++s) for(fst::ArcIterator<Fst> it(f,s);!it.Done();it.Next()) ++indegree[it.Value().nextstate];
  for(State s=0;s<n;++s) if(!indegree[s]) order.push_back(s);
  for(size_t i=0;i<order.size();++i) for(fst::ArcIterator<Fst> it(f,order[i]);!it.Done();it.Next()) if(!--indegree[it.Value().nextstate]) order.push_back(it.Value().nextstate);
  if(order.size()!=size_t(n)) throw std::runtime_error("cyclic space: Zippy spaces must be finite");
  std::vector<Cardinality> count(n);
  for(size_t i=order.size();i-- > 0;) {
    State s=order[i];count[s]=final(f,s)?1:0;
    for(fst::ArcIterator<Fst> it(f,s);!it.Done();it.Next()) count[s]+=count[it.Value().nextstate];
  } return count;
}
SpaceStats inspect(const Space& space) {
  const Fst& f=space.value->fst;uint64_t arcs=0;
  for(State s=0;s<f.NumStates();++s) arcs+=f.NumArcs(s);
  auto count=path_counts(f);Cardinality paths=space.empty()?Cardinality(0):count[f.Start()];
  return {uint64_t(f.NumStates()),arcs,paths.convert_to<std::string>()};
}
Space range(Context& c, const Space& a, int start, int end) {
  if(a.empty()) return a;
  const Fst& f=a.value->fst; const auto count=path_counts(f); const Cardinality n=count[f.Start()];
  auto clamp=[&](Cardinality x) -> Cardinality {return x<0?Cardinality(0):x>n?n:x;};
  Cardinality lo=0,hi=n;
  if(start>0) lo=Cardinality(start)-1;else if(start<0) lo=n+start;
  if(end>0) hi=Cardinality(end)-(start==0?0:1);else if(end<0) hi=n+end;
  lo=clamp(lo);hi=clamp(hi);
  if(hi<=lo) return Space(); if(lo==0 && hi==n) return a;
  // Slice by subtree cardinalities. Whole interior subgraphs are copied once;
  // only the two boundary paths require partial traversal. No paths enumerate.
  Fst out; using Key=std::tuple<State,Cardinality,Cardinality>;std::map<Key,State> memo;
  std::function<State(State,Cardinality,Cardinality)> slice=[&](State s,Cardinality begin,Cardinality finish) {
    Key key{s,begin,finish};auto found=memo.find(key);if(found!=memo.end()) return found->second;
    State dest=out.AddState();memo[key]=dest;
    Cardinality offset=final(f,s)?1:0;if(final(f,s)&&begin==0&&finish>0) out.SetFinal(dest,Weight::One());
    std::vector<Arc> arcs;for(fst::ArcIterator<Fst> it(f,s);!it.Done();it.Next()) arcs.push_back(it.Value());
    std::sort(arcs.begin(),arcs.end(),[&](const Arc& x,const Arc& y){return c.less({x.ilabel},{y.ilabel});});
    for(auto arc:arcs) {
      const Cardinality length=count[arc.nextstate];
      if(finish>offset && begin<offset+length) {
        const Cardinality first=begin>offset?Cardinality(begin-offset):Cardinality(0);
        const Cardinality last=length<finish-offset?length:Cardinality(finish-offset);
        arc.nextstate=slice(arc.nextstate,first,last);out.AddArc(dest,arc);
      }
      offset+=length;if(offset>=finish) break;
    } return dest;
  };
  out.SetStart(slice(f.Start(),lo,hi));fst::ArcSort(&out,fst::ILabelCompare<Arc>());return adopt(std::move(out));
}
static Space join_outputs(std::vector<Space> values) {
  values.erase(std::remove_if(values.begin(),values.end(),[](const Space& s){return s.empty();}),values.end());
  if(values.empty()) return Space();if(values.size()==1) return values.front();
  size_t states=0,largest=0;for(const auto& value:values) {size_t n=value.value->fst.NumStates();states+=n;largest=std::max(largest,n);}
  if(values.size()<=4 || largest>1024 || states>100000) {
    // Large, overlapping outputs (notably complements in an encoded meet)
    // must not expand into a huge epsilon NFA before determinization.
    // Balanced deterministic unions keep those intermediates compact.
    while(values.size()>1) {
      std::vector<Space> next;next.reserve((values.size()+1)/2);
      for(size_t i=0;i<values.size();i+=2) next.push_back(i+1==values.size()?values[i]:unite(values[i],values[i+1]));
      values=std::move(next);
    } return values.front();
  }
  Fst out;for(const auto& value:values) fst::Union(&out,value.value->fst);return normalize(std::move(out));
}
Space iterate(Context& c,const Env& e,const Space& src,int h,int t,SFn body) {
  std::vector<Space> out;for(const auto& g:groups(c,src)) out.push_back(body(c,e.bind_path(h,{g.first}).bind_space(t,g.second)));return join_outputs(std::move(out));
}
Space fold(Context& c,const Env& e,const Space& src,const Path& initial,int acc,int h,int t,SFn body,PFn update) {
  std::vector<Space> out;Path value=initial;
  for(const auto& g:groups(c,src)) {auto next=e.bind_path(acc,value).bind_path(h,{g.first}).bind_space(t,g.second);out.push_back(body(c,next));value=update(c,next);}
  return join_outputs(std::move(out));
}
Space fixpoint(Context& c,const Env& e,const Space& initial,int v,SFn step) {
  Space current=initial; for(;;) { auto next=unite(current,step(c,e.bind_space(v,current))); if(equal(next,current)) return current; current=std::move(next); }
}
static int count(std::istream& in) { int64_t n; if(!(in>>n) || n<0 || n>std::numeric_limits<int>::max()) throw std::runtime_error("invalid native protocol count"); return n; }
static Path read_path(std::istream& in) { int n=count(in); Path p; p.reserve(n); while(n--) { int label=count(in); if(!label) throw std::runtime_error("epsilon label in path payload"); p.push_back(label); } return p; }
Space read_space(std::istream& in) { int n=count(in); std::vector<Path> ps; ps.reserve(n); while(n--) ps.push_back(read_path(in)); return literal(ps); }
void write_space(std::ostream& out,const Space& s) { auto ps=enumerate(s); out<<ps.size(); for(const auto& p:ps) { out<<' '<<p.size(); for(int x:p) out<<' '<<x; } }
static void read_symbols_from(Context& c,std::istream& input) { int n=count(input);while(n--) {int label=count(input),len=count(input);std::vector<int> units;while(len--) units.push_back(count(input));c.symbols[label]=std::move(units);} }
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
int run(Context& c,const std::function<Space(int,Context&,const Env&)>& dispatch) {
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
        for(unsigned i=0;i<warmups;++i) result=dispatch(id,c,e);
        auto start=std::chrono::steady_clock::now();
        for(int i=0;i<repeats;++i) result=dispatch(id,c,e);
        auto ns=std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now()-start).count();
        const Fst& f=result.value->fst;uint64_t arcs=0;for(State s=0;s<f.NumStates();++s) arcs+=f.NumArcs(s);
        auto counts=path_counts(f);Cardinality paths=result.empty()?Cardinality(0):counts[f.Start()];
        *c.output<<"T "<<ns/repeats<<' '<<f.NumStates()<<' '<<arcs<<' '<<paths<<std::endl;
      } else result=dispatch(id,c,e);
      if(command=="B") *c.output<<"N"<<std::endl;
      else {*c.output<<"O ";write_space(*c.output,result);*c.output<<std::endl;}
    }
    catch(const std::exception& ex) { *c.output<<"E "<<ex.what()<<std::endl; return 1; }
  } return 0;
}
} // namespace zippy
