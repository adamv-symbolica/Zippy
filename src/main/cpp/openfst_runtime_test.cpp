#include "zippy_openfst.hpp"
#include <algorithm>
#include <iostream>
#include <limits>
#include <map>
#include <random>
#include <set>
#include <sstream>
#include <stdexcept>
using namespace zippy;
using Model=std::set<Path>;
static bool prefix(const Path& p,const Path& q) {return p.size()>=q.size()&&std::equal(q.begin(),q.end(),p.begin());}
static Space native(const Model& m) {return literal(std::vector<Path>(m.begin(),m.end()));}
static void check(const char* name,const Space& value,const Model& expected) {
  const auto paths=enumerate(value);const Model actual(paths.begin(),paths.end());
  if(actual!=expected) throw std::runtime_error(std::string(name)+" differs from independent set model");
  if(inspect(value).paths!=std::to_string(expected.size())) throw std::runtime_error("graph cardinality mismatch");
}
int main() {
  std::istringstream input;std::ostringstream output;Context context(input,output);Env env;
  std::mt19937 random(240629);size_t checks=0;
  for(int test=0;test<300;++test) {
    auto generate=[&] {Model value;for(unsigned i=0,n=random()%10;i<n;++i) {Path p;for(unsigned j=0,k=random()%6;j<k;++j) p.push_back(1+random()%4);value.insert(p);}return value;};
    Model a=generate(),b=generate(),u=a,i,d,r,cat,wrapped,unwrapped,tu,ti,pc,sc,tc;
    auto left=native(a),right=native(b);u.insert(b.begin(),b.end());
    for(const auto& p:a) {if(b.count(p)) i.insert(p);else d.insert(p);if(std::any_of(b.begin(),b.end(),[&](const Path& q){return prefix(p,q);})) r.insert(p);for(const auto& q:b) cat.insert(concat_path(p,q));}
    check("union",unite(left,right),u);check("intersection",intersect(left,right),i);check("difference",subtract(left,right),d);check("restriction",restrict_to(left,right),r);check("composition",compose(left,right),cat);checks+=5;
    Model refined;std::set_difference(a.begin(),a.end(),r.begin(),r.end(),std::inserter(refined,refined.end()));check("raffination",raffine(left,right),refined);++checks;
    std::vector<Path> order(b.begin(),b.end());std::shuffle(order.begin(),order.end(),random);
    for(bool pref:{false,true}) {
      auto selected=[&](const std::vector<Path>& choices) {Model expected;for(const auto& word:choices) {bool present=pref?std::any_of(a.begin(),a.end(),[&](const Path& p){return prefix(p,word);}):a.count(word);if(present) {expected.insert(word);break;}}return expected;};
      check("listed selection",first_listed(left,order,pref),selected(order));
      check("lexical selection",first_matching(left,right,pref),selected(std::vector<Path>(b.begin(),b.end())));checks+=2;
    }
    Path before{1,2};std::map<int,Model> groups;
    Model removed;for(const auto& p:a) if(!prefix(p,before)) removed.insert(p);
    check("persistent prefix removal",raffine(left,singleton(before)),removed);++checks;
    for(const auto& p:a) {
      wrapped.insert(concat_path(before,p));if(prefix(p,before)) unwrapped.emplace(p.begin()+before.size(),p.end());
      for(size_t n=1;n<=p.size();++n) pc.emplace(p.begin(),p.begin()+n);
      for(size_t n=0;n<p.size();++n) sc.emplace(p.begin()+n,p.end());
      for(size_t n=0;n<=p.size();++n) tc.emplace(p.begin()+n,p.end());
      if(!p.empty()) {Path tail(p.begin()+1,p.end());tu.insert(tail);groups[p.front()].insert(tail);}
    }
    Model heads;for(const auto& [h,t]:groups) heads.insert(Path{h});check("head",head_space(left),heads);++checks;
    bool first=true;for(const auto& [h,t]:groups) {if(first) ti=t;else {Model next;std::set_intersection(ti.begin(),ti.end(),t.begin(),t.end(),std::inserter(next,next.end()));ti=std::move(next);}first=false;}
    check("wrap",wrap(left,before),wrapped);check("unwrap",unwrap(left,before),unwrapped);check("tails union",tails_union(left),tu);check("tails intersection",tails_intersection(left),ti);check("prefix closure",prefix_closure(left),pc);check("suffix closure",suffix_closure(left,false),sc);check("tails closure",suffix_closure(left,true),tc);checks+=7;
    Model without_epsilon=a;without_epsilon.erase(Path{});std::vector<Space> iterated;
    for(const auto& [h,t]:branches(context,left)) iterated.push_back(wrap(t,Path{h}));
    check("iteration",join_outputs(std::move(iterated)),without_epsilon);
    Model folded;Path acc;for(const auto& [h,t]:groups) {acc.push_back(h);folded.insert(acc);}
    std::vector<Space> fold_outputs;Path folded_acc;
    for(const auto& [h,t]:branches(context,left)) {folded_acc.push_back(h);fold_outputs.push_back(singleton(folded_acc));}
    check("fold",join_outputs(std::move(fold_outputs)),folded);
    Space current=left;for(;;) {auto next=unite(current,tails_union(current));if(equal(next,current)) break;current=std::move(next);}
    check("fixpoint",current,tc);checks+=3;
    for(auto [lo,hi]:std::vector<std::pair<int,int>>{{0,0},{0,1},{1,3},{-2,0},{0,-1},{-3,-1},{4,2},{std::numeric_limits<int>::min(),std::numeric_limits<int>::max()}}) {
      int64_t n=a.size(),begin=lo==0?0:lo>0?int64_t(lo)-1:n+lo,end=hi==0?n:hi>0?int64_t(hi)-(lo==0?0:1):n+hi;
      begin=std::clamp(begin,int64_t(0),n);end=std::clamp(end,int64_t(0),n);Model expected;
      if(end>begin) {auto it=a.begin();std::advance(it,begin);for(int64_t k=begin;k<end;++k,++it) expected.insert(*it);}
      check("range",range(context,left,lo,hi),expected);++checks;
    }
  }
  auto fibonacci_word=[](unsigned n) {std::vector<unsigned> weights{1,2};while(weights.back()<=n) weights.push_back(weights[weights.size()-1]+weights[weights.size()-2]);weights.pop_back();Path out(weights.size()+1,1);for(size_t i=weights.size();i-- > 0;) if(weights[i]<=n) {out[i]=2;n-=weights[i];}out.back()=2;return out;};
  for(unsigned test=0;test<200;++test) {
    unsigned maximum=1+random()%60;std::vector<Path> words;for(unsigned n=1;n<=maximum;++n) words.push_back(fibonacci_word(n));
    Model input;for(unsigned k=0;k<12;++k) {Path p=fibonacci_word(1+random()%(maximum+20));for(unsigned j=0,n=random()%4;j<n;++j) p.push_back(1+random()%4);input.insert(p);}
    auto space=native(input);int digits=0;for(const auto& word:words) digits=std::max(digits,int(word.size()));
    for(bool pref:{false,true}) {Model expected;for(const auto& word:words) {bool present=pref?std::any_of(input.begin(),input.end(),[&](const Path& p){return prefix(p,word);}):input.count(word);if(present) {expected.insert(word);break;}}check("Fibonacci selection",first_fibonacci(space,maximum,digits,pref),expected);++checks;}
  }
  auto broad=literal({Path{1},Path{2},Path{3}});
  auto product=compose(broad,compose(broad,broad));
  auto joined=unite(product,wrap(product,Path{4}));
  if(storage(product).expanded_states||storage(joined).expanded_states||storage(joined).snapshots)
    throw std::runtime_error("automaton construction eagerly expanded or materialized states");
  auto prefixed=wrap(joined,Path{8,9});auto residual=unwrap(prefixed,Path{8,9});
  if((prefixed.value!=residual.value&&joined.value!=residual.value)||storage(prefixed).snapshots) throw std::runtime_error("unwrap copied a residual automaton");
  check("selective delayed product",intersect(residual,literal({Path{1,2,3}})),Model{Path{1,2,3}});
  auto dead=intersect(literal({Path{1,2}}),literal({Path{1,3}}));
  check("dead branches do not become heads",head_space(dead),{});
  if(!equal(dead,Space())||!branches(context,dead).empty()) throw std::runtime_error("unproductive delayed states observed as paths");
  checks+=5;
  Model original;for(int n=0;n<256;++n) original.insert(Path{1+n/16,1+n%16,17});
  auto persistent=native(original),edited=persistent;
  for(const auto& p:original) edited=raffine(edited,singleton(Path(p.begin(),p.end()-1)));
  check("successive prefix removals",edited,{});check("prefix removal preserves old cursors",persistent,original);checks+=2;
  std::cout<<checks<<" independent native checks passed\n";
}
