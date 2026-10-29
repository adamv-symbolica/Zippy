#pragma once
// Private implementation of deterministic, epsilon-free delayed OpenFst
// acceptors. Operator construction only records cursors. A state's transitions
// are computed once, on demand, and owned by the automaton's OpenFst cache.

using BaseFst = fst::Fst<Arc>;
struct AutomatonAnalysis {
  std::unordered_map<State,boost::multiprecision::cpp_int> counts;
  uint64_t states=0,arcs=0;
};
struct Acceptor {
  std::shared_ptr<const BaseFst> machine;
  bool coaccessible=false;
  mutable std::map<State,std::shared_ptr<const AutomatonAnalysis>> analyses;
  mutable std::unordered_map<State,bool> productive;
  mutable const Acceptor* release_next=nullptr;
  explicit Acceptor(Fst f) : machine(std::make_shared<Fst>(std::move(f))),coaccessible(true) {}
  explicit Acceptor(std::shared_ptr<const BaseFst> f,bool trimmed=false) : machine(std::move(f)),coaccessible(trimmed) {}
};
static std::shared_ptr<const Acceptor> own_acceptor(Acceptor* value) {
  return std::shared_ptr<const Acceptor>(value,[](const Acceptor* p) {
    // Destroy persistent operator chains iteratively, including at shutdown.
    // The queue is intrusive and its TLS scalars need no allocation/destructor.
    static thread_local const Acceptor* pending=nullptr;
    static thread_local bool draining=false;
    p->release_next=pending;pending=p;if(draining) return;draining=true;
    while(pending) {const auto* next=pending;pending=next->release_next;delete next;}
    draining=false;
  });
}
static const BaseFst& graph(const Space& s) { return *s.value->machine; }
static bool same(const Space& a,const Space& b) { return a.value==b.value && a.root==b.root; }
static bool known_empty(const Space& s) { return s.root==fst::kNoStateId; }
static Space cursor(const Space&,State);
static bool accepting(const BaseFst& f,State s) { return s!=fst::kNoStateId && f.Final(s)!=Weight::Zero(); }
static State transition(const BaseFst& f,State state,int label) {
  if(state==fst::kNoStateId) return state;
  size_t lo=0,hi=f.NumArcs(state);fst::ArcIterator<BaseFst> arcs(f,state);
  while(lo<hi) {size_t mid=(lo+hi)/2;arcs.Seek(mid);if(arcs.Value().ilabel<label) lo=mid+1;else hi=mid;}
  if(lo==f.NumArcs(state)) return fst::kNoStateId;
  arcs.Seek(lo);return arcs.Value().ilabel==label?arcs.Value().nextstate:fst::kNoStateId;
}
static bool productive(const Space& source) {
  if(known_empty(source)) return false;
  if(source.value->coaccessible) return true;
  auto& cache=source.value->productive;
  auto found=cache.find(source.root);if(found!=cache.end()) return found->second;
  const auto& f=graph(source);std::vector<State> pending{source.root};std::unordered_set<State> seen;
  while(!pending.empty()) {
    State s=pending.back();pending.pop_back();if(!seen.insert(s).second) continue;
    auto previous=cache.find(s);
    if(previous!=cache.end()) {if(previous->second) {cache[source.root]=true;return true;}continue;}
    if(accepting(f,s)) {cache[s]=true;cache[source.root]=true;return true;}
    for(fst::ArcIterator<BaseFst> it(f,s);!it.Done();it.Next()) pending.push_back(it.Value().nextstate);
  }
  for(State s:seen) cache[s]=false;
  return false;
}
static const AutomatonAnalysis& analysis(const Space& source) {
  auto found=source.value->analyses.find(source.root);
  if(found!=source.value->analyses.end()) return *found->second;
  auto result=std::make_shared<AutomatonAnalysis>();
  if(!known_empty(source)) {
    const auto& f=graph(source);std::unordered_map<State,unsigned char> color;
    std::vector<std::pair<State,bool>> pending{{source.root,false}};
    while(!pending.empty()) {
      auto [s,finish]=pending.back();pending.pop_back();
      if(finish) {
        boost::multiprecision::cpp_int count=accepting(f,s)?1:0;
        uint64_t arcs=0;
        for(fst::ArcIterator<BaseFst> it(f,s);!it.Done();it.Next()) {
          const auto& n=result->counts.at(it.Value().nextstate);count+=n;arcs+=n!=0;
        }
        if(!source.value->coaccessible) source.value->productive[s]=count!=0;
        if(count!=0) {++result->states;result->arcs+=arcs;}
        result->counts.emplace(s,std::move(count));color[s]=2;
      } else {
        auto status=color[s];if(status==2) continue;
        if(status==1) throw std::runtime_error("cyclic Zippy automaton");
        color[s]=1;pending.emplace_back(s,true);
        for(fst::ArcIterator<BaseFst> it(f,s);!it.Done();it.Next())
          if(color[it.Value().nextstate]!=2) pending.emplace_back(it.Value().nextstate,false);
      }
    }
  }
  return *source.value->analyses.emplace(source.root,std::move(result)).first->second;
}
void realize(const Space& space) { (void)analysis(space); }

class Machine {
 public:
  struct Cached {bool final_known=false,terminal=false,expanded=false;std::vector<Arc> arcs;};
  mutable std::deque<Cached> states;
  virtual ~Machine()=default;
  virtual bool terminal(State) const=0;
  virtual void expand(State) const=0;
  State add_state() const {State s=states.size();states.emplace_back();return s;}
  const Cached& arcs(State s) const {
    if(!states[s].expanded) {expand(s);states[s].expanded=true;}
    return states[s];
  }
  bool final(State s) const {
    auto& c=states[s];if(!c.final_known) {c.terminal=terminal(s);c.final_known=true;}return c.terminal;
  }
};
class CachedFst final : public BaseFst {
 public:
  std::shared_ptr<Machine> impl;
  explicit CachedFst(std::shared_ptr<Machine> m):impl(std::move(m)) {}
  State Start() const override {return 0;}
  Weight Final(State s) const override {return impl->final(s)?Weight::One():Weight::Zero();}
  size_t NumArcs(State s) const override {return impl->arcs(s).arcs.size();}
  size_t NumInputEpsilons(State) const override {return 0;}
  size_t NumOutputEpsilons(State) const override {return 0;}
  uint64_t Properties(uint64_t mask,bool) const override {
    return mask & (fst::kAcceptor|fst::kIDeterministic|fst::kODeterministic|fst::kNoEpsilons|
      fst::kNoIEpsilons|fst::kNoOEpsilons|fst::kILabelSorted|fst::kOLabelSorted|fst::kUnweighted|
      fst::kAcyclic|fst::kInitialAcyclic);
  }
  const std::string& Type() const override {static const std::string name="zippy-delayed";return name;}
  BaseFst* Copy(bool safe=false) const override {return safe?static_cast<BaseFst*>(new fst::VectorFst<Arc>(*this)):new CachedFst(impl);}
  const fst::SymbolTable* InputSymbols() const override {return nullptr;}
  const fst::SymbolTable* OutputSymbols() const override {return nullptr;}
  void InitStateIterator(fst::StateIteratorData<Arc>* data) const override {
    for(State s=0;size_t(s)<impl->states.size();++s) impl->arcs(s);
    data->base=nullptr;data->nstates=impl->states.size();
  }
  void InitArcIterator(State s,fst::ArcIteratorData<Arc>* data) const override {
    const auto& arcs=impl->arcs(s).arcs;data->base=nullptr;data->arcs=arcs.data();data->narcs=arcs.size();data->ref_count=nullptr;
  }
};
static Space delayed(std::shared_ptr<Machine> machine,bool trimmed=false) {
  return Space(own_acceptor(new Acceptor(std::make_shared<CachedFst>(std::move(machine)),trimmed)),0);
}
StorageStats storage(const Space& space) {
  uint64_t expanded=0;
  if(const auto* delayed=dynamic_cast<const CachedFst*>(space.value->machine.get()))
    for(const auto& state:delayed->impl->states) expanded+=state.expanded;
  return {expanded,0}; // No operator or final consumer creates a VectorFst snapshot.
}

enum class ProductOp {Union,Intersection,Difference,Restriction,Raffination,SymmetricDifference};
template<ProductOp Op> class ProductMachine final : public Machine {
  Space a,b;
  using Pair=std::pair<State,State>;
  mutable std::map<Pair,State> ids;
  mutable std::vector<Pair> keys;
  State intern(Pair key) const {
    auto [it,added]=ids.emplace(key,keys.size());if(added) {keys.push_back(key);add_state();}return it->second;
  }
 public:
  ProductMachine(Space left,Space right):a(std::move(left)),b(std::move(right)) {intern({a.root,b.root});}
  bool terminal(State s) const override {
    auto [x,y]=keys[s];bool af=accepting(graph(a),x),bf=y==-2||accepting(graph(b),y);
    if constexpr(Op==ProductOp::Union) return af||bf;
    if constexpr(Op==ProductOp::Intersection||Op==ProductOp::Restriction) return af&&bf;
    if constexpr(Op==ProductOp::Difference||Op==ProductOp::Raffination) return af&&!bf;
    return af!=bf;
  }
  void expand(State s) const override {
    auto [x,y]=keys[s];
    if constexpr(Op==ProductOp::Raffination) if(accepting(graph(b),y)) return;
    if constexpr(Op==ProductOp::Restriction) if(y!=-2 && accepting(graph(b),y)) y=-2;
    std::vector<Arc> ax,bx;
    if(x>=0) for(fst::ArcIterator<BaseFst> it(graph(a),x);!it.Done();it.Next()) ax.push_back(it.Value());
    if(y>=0) for(fst::ArcIterator<BaseFst> it(graph(b),y);!it.Done();it.Next()) bx.push_back(it.Value());
    size_t l=0,r=0;
    while(l<ax.size()||r<bx.size()) {
      int label;State nx=-1,ny=-1;
      if(r==bx.size()||(l<ax.size()&&ax[l].ilabel<bx[r].ilabel)) {label=ax[l].ilabel;nx=ax[l++].nextstate;}
      else if(l==ax.size()||bx[r].ilabel<ax[l].ilabel) {label=bx[r].ilabel;ny=bx[r++].nextstate;}
      else {label=ax[l].ilabel;nx=ax[l++].nextstate;ny=bx[r++].nextstate;}
      if constexpr(Op==ProductOp::Intersection) if(nx<0||ny<0) continue;
      if constexpr(Op==ProductOp::Difference||Op==ProductOp::Raffination) if(nx<0) continue;
      if constexpr(Op==ProductOp::Restriction) {if(nx<0||(y!=-2&&ny<0)) continue;if(y==-2) ny=-2;}
      states[s].arcs.emplace_back(label,label,Weight::One(),intern({nx,ny}));
    }
  }
};
class ConcatMachine final : public Machine {
  Space a,b;
  using Key=std::pair<State,std::vector<State>>;
  mutable std::map<Key,State> ids;
  mutable std::vector<Key> keys;
  State intern(Key key) const {
    std::sort(key.second.begin(),key.second.end());key.second.erase(std::unique(key.second.begin(),key.second.end()),key.second.end());
    auto [it,added]=ids.emplace(key,keys.size());if(added) {keys.push_back(std::move(key));add_state();}return it->second;
  }
 public:
  ConcatMachine(Space left,Space right):a(std::move(left)),b(std::move(right)) {
    intern({a.root,{}});
  }
  bool terminal(State s) const override {
    if(s==0&&accepting(graph(a),a.root)&&accepting(graph(b),b.root)) return true;
    for(State r:keys[s].second) if(accepting(graph(b),r)) return true;return false;
  }
  void expand(State s) const override {
    auto key=keys[s];if(s==0&&accepting(graph(a),a.root)) key.second.push_back(b.root);
    std::map<int,Key> next;
    if(key.first>=0) for(fst::ArcIterator<BaseFst> it(graph(a),key.first);!it.Done();it.Next()) {
      auto edge=it.Value();next.try_emplace(edge.ilabel,Key{-1,{}}).first->second.first=edge.nextstate;
    }
    for(State r:key.second) for(fst::ArcIterator<BaseFst> it(graph(b),r);!it.Done();it.Next()) {
      auto edge=it.Value();next.try_emplace(edge.ilabel,Key{-1,{}}).first->second.second.push_back(edge.nextstate);
    }
    for(auto& [label,k]:next) {
      if(accepting(graph(a),k.first)) k.second.push_back(b.root);
      states[s].arcs.emplace_back(label,label,Weight::One(),intern(std::move(k)));
    }
  }
};
class WrapMachine final : public Machine {
  Space source;Path prefix;
  mutable std::map<State,State> ids;
  mutable std::vector<State> keys;
  State intern(State old) const {
    auto [it,added]=ids.emplace(old,keys.size()+prefix.size());if(added) {keys.push_back(old);add_state();}return it->second;
  }
 public:
  bool residual(State state,Space& out) const {
    if(size_t(state)<prefix.size()) return false;
    out=Space(source.value,keys[state-prefix.size()]);return true;
  }
  WrapMachine(Space src,Path p):source(std::move(src)),prefix(std::move(p)) {
    for(size_t i=0;i<prefix.size();++i) add_state();intern(source.root);
  }
  bool terminal(State s) const override {return size_t(s)>=prefix.size()&&accepting(graph(source),keys[s-prefix.size()]);}
  void expand(State s) const override {
    if(size_t(s)<prefix.size()) {int label=prefix[s];states[s].arcs.emplace_back(label,label,Weight::One(),s+1);return;}
    State old=keys[s-prefix.size()];
    for(fst::ArcIterator<BaseFst> it(graph(source),old);!it.Done();it.Next()) {
      auto arc=it.Value();arc.nextstate=intern(arc.nextstate);states[s].arcs.push_back(arc);
    }
  }
};
struct CursorLess {
  bool operator()(const Space& a,const Space& b) const {
    if(a.value!=b.value) return std::less<const Acceptor*>()(a.value.get(),b.value.get());return a.root<b.root;
  }
};
// A persistent edit of one state's outgoing cursors. Only the prefix boundary
// is copied; unchanged residuals stay shared. Cursor normalization bypasses
// these views, preventing successive prefix removals from accumulating layers.
class BranchMachine final : public Machine {
  bool accepted;
  std::vector<std::pair<int,Space>> edges;
  mutable std::map<Space,State,CursorLess> ids;
  mutable std::vector<Space> keys;
  State intern(Space value) const {
    auto [it,added]=ids.emplace(value,keys.size()+1);if(added) {keys.push_back(std::move(value));add_state();}return it->second;
  }
 public:
  BranchMachine(bool terminal,std::vector<std::pair<int,Space>> children):accepted(terminal),edges(std::move(children)) {add_state();}
  bool residual(State s,Space& out) const {if(s==0) return false;out=keys[s-1];return true;}
  bool terminal(State s) const override {return s==0?accepted:accepting(graph(keys[s-1]),keys[s-1].root);}
  void expand(State s) const override {
    if(s==0) {for(const auto& [label,child]:edges) states[s].arcs.emplace_back(label,label,Weight::One(),intern(child));return;}
    Space source=keys[s-1];for(fst::ArcIterator<BaseFst> it(graph(source),source.root);!it.Done();it.Next()) {
      auto arc=it.Value();arc.nextstate=intern(cursor(source,arc.nextstate));states[s].arcs.push_back(arc);
    }
  }
};
class UnionMachine final : public Machine {
  using Key=std::vector<Space>;
  struct Less {bool operator()(const Key& a,const Key& b) const {return std::lexicographical_compare(a.begin(),a.end(),b.begin(),b.end(),CursorLess());}};
  mutable std::map<Key,State,Less> ids;
  mutable std::vector<Key> keys;
  mutable std::map<State,Key> flattened;
  const Key& inputs(State s) const {
    auto known=flattened.find(s);if(known!=flattened.end()) return known->second;
    Key pending=keys[s],result;std::set<Space,CursorLess> seen;
    while(!pending.empty()) {
      Space current=std::move(pending.back());pending.pop_back();
      if(known_empty(current)) continue;
      if(!seen.insert(current).second) continue;
      const auto* cached=dynamic_cast<const CachedFst*>(current.value->machine.get());
      const auto* nested=cached?dynamic_cast<const UnionMachine*>(cached->impl.get()):nullptr;
      if(nested&&!nested->states[current.root].expanded) {
        const auto& children=nested->keys[current.root];pending.insert(pending.end(),children.begin(),children.end());
      } else result.push_back(std::move(current));
    }
    std::sort(result.begin(),result.end(),CursorLess());
    return flattened.emplace(s,std::move(result)).first->second;
  }
  State intern(Key key) const {
    std::sort(key.begin(),key.end(),CursorLess());key.erase(std::unique(key.begin(),key.end(),same),key.end());
    auto [it,added]=ids.emplace(key,keys.size());if(added) {keys.push_back(std::move(key));add_state();}return it->second;
  }
 public:
  explicit UnionMachine(Key source) {intern(std::move(source));}
  bool terminal(State s) const override {for(const auto& c:inputs(s)) if(accepting(graph(c),c.root)) return true;return false;}
  void expand(State s) const override {
    auto key=inputs(s);std::map<int,Key> next;
    for(const auto& c:key) for(fst::ArcIterator<BaseFst> it(graph(c),c.root);!it.Done();it.Next()) {
      auto edge=it.Value();next[edge.ilabel].push_back(cursor(c,edge.nextstate));
    }
    for(auto& [label,k]:next) states[s].arcs.emplace_back(label,label,Weight::One(),intern(std::move(k)));
  }
};
class ClosureMachine final : public Machine {
  Space source;bool prefixes,nonempty;
  mutable std::map<State,State> ids;
  mutable std::vector<State> keys;
  State intern(State old) const {auto [it,added]=ids.emplace(old,keys.size());if(added) {keys.push_back(old);add_state();}return it->second;}
 public:
  ClosureMachine(Space input,bool prefix,bool no_epsilon):source(std::move(input)),prefixes(prefix),nonempty(no_epsilon) {intern(source.root);}
  bool terminal(State s) const override {
    if(nonempty&&s==0) return false;
    return prefixes?productive(cursor(source,keys[s])):accepting(graph(source),keys[s]);
  }
  void expand(State s) const override {
    State old=keys[s];for(fst::ArcIterator<BaseFst> it(graph(source),old);!it.Done();it.Next()) {auto arc=it.Value();arc.nextstate=intern(arc.nextstate);states[s].arcs.push_back(arc);}
  }
};
class CountOracle {
  using Count=boost::multiprecision::cpp_int;
  Space source;Count cap;
  mutable std::unordered_map<State,Count> counts;
 public:
  // cap==0 requests exact, arbitrary-precision counts. Positive windows only
  // need counts saturated at their right boundary, so unrelated suffixes stay lazy.
  CountOracle(Space input,Count maximum):source(std::move(input)),cap(std::move(maximum)) {}
  const Count& get(State state) const {
    if(cap==0) return analysis(source).counts.at(state);
    auto known=counts.find(state);if(known!=counts.end()) return known->second;
    const auto& f=graph(source);
    struct Frame {State state;size_t arc;Count count;};
    std::vector<Frame> stack{{state,0,accepting(f,state)?1:0}};std::unordered_set<State> active{state};
    while(!stack.empty()) {
      auto& frame=stack.back();
      if(frame.count>=cap || frame.arc==f.NumArcs(frame.state)) {
        source.value->productive[frame.state]=frame.count!=0;
        counts.emplace(frame.state,frame.count>=cap?cap:std::move(frame.count));active.erase(frame.state);stack.pop_back();continue;
      }
      fst::ArcIterator<BaseFst> it(f,frame.state);it.Seek(frame.arc);State next=it.Value().nextstate;
      auto previous=counts.find(next);
      if(previous!=counts.end()) {frame.count+=previous->second;++frame.arc;}
      else {
        if(!active.insert(next).second) throw std::runtime_error("cyclic Zippy automaton");
        stack.push_back({next,0,accepting(f,next)?1:0});
      }
    }
    return counts.at(state);
  }
};
class RangeMachine final : public Machine {
  using Count=boost::multiprecision::cpp_int;
  using Key=std::tuple<State,Count,Count>;
  Space source;
  std::shared_ptr<CountOracle> counts;
  std::shared_ptr<const Context::SymbolOrder> order;
  mutable std::map<Key,State> ids;
  mutable std::vector<Key> keys;
  State intern(Key key) const {
    auto [it,added]=ids.emplace(key,keys.size());if(added) {keys.push_back(std::move(key));add_state();}return it->second;
  }
 public:
  RangeMachine(Space input,Count lo,Count hi,std::shared_ptr<CountOracle> counter,std::shared_ptr<const Context::SymbolOrder> ordering)
      :source(std::move(input)),counts(std::move(counter)),order(std::move(ordering)) {intern({source.root,std::move(lo),std::move(hi)});}
  bool terminal(State s) const override {
    const auto& [old,lo,hi]=keys[s];return lo==0&&hi>0&&accepting(graph(source),old);
  }
  void expand(State s) const override {
    auto [old,begin,finish]=keys[s];const auto& f=graph(source);
    Count offset=accepting(f,old)?1:0;std::vector<Arc> arcs;
    for(fst::ArcIterator<BaseFst> it(f,old);!it.Done();it.Next()) arcs.push_back(it.Value());
    if(order) std::sort(arcs.begin(),arcs.end(),[&](const Arc& a,const Arc& b){return order->at(a.ilabel)<order->at(b.ilabel);});
    for(auto arc:arcs) {
      const auto& length=counts->get(arc.nextstate);
      if(finish>offset&&begin<offset+length) {
        Count first=begin>offset?Count(begin-offset):Count(0),last=length<finish-offset?length:Count(finish-offset);
        arc.nextstate=intern({arc.nextstate,std::move(first),std::move(last)});states[s].arcs.push_back(arc);
      }
      offset+=length;if(offset>=finish) break;
    }
    if(order) std::sort(states[s].arcs.begin(),states[s].arcs.end(),[](const Arc& a,const Arc& b){return a.ilabel<b.ilabel;});
  }
};
static Space cursor(const Space& s,State state) {
  if(state<0) return Space();Space result(s.value,state);
  for(;;) {
    const auto* cached=dynamic_cast<const CachedFst*>(result.value->machine.get());
    const auto* wrapped=cached?dynamic_cast<const WrapMachine*>(cached->impl.get()):nullptr;
    const auto* edited=cached?dynamic_cast<const BranchMachine*>(cached->impl.get()):nullptr;
    Space next;if(!(wrapped&&wrapped->residual(result.root,next))&&!(edited&&edited->residual(result.root,next))) return result;
    result=std::move(next);
  }
}
