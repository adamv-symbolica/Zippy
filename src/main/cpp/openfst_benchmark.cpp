#include "zippy_openfst.hpp"
#include <algorithm>
#include <chrono>
#include <iostream>
#include <stdexcept>
using namespace zippy;
using Clock=std::chrono::steady_clock;
static Path encode(int dialect,int value,int width) {
  if(dialect==0) return Path{value+3,1};
  Path p;
  if(dialect==1) {for(int bit=width-1;bit>=0;--bit) p.push_back(((value>>bit)&1)+1);}
  else {for(int byte=3;byte>=0;--byte) p.push_back(((value>>(byte*8))&255)+1);}
  p.push_back(1);return p;
}
static int width(int n) {int w=1;while((uint64_t(1)<<w)<uint64_t(n)) ++w;return w;}
static void row(const std::string& dialect,const std::string& operation,int size,const std::function<Space()>& run,uint64_t expected,bool expensive=false) {
  Space result;std::vector<double> samples;
  const int count=expensive?1:5;
  if(!expensive) result=run();
  for(int i=0;i<count;++i) {auto start=Clock::now();result=run();samples.push_back(std::chrono::duration<double,std::nano>(Clock::now()-start).count());}
  auto stats=inspect(result);
  if(stats.paths!=std::to_string(expected)) throw std::runtime_error("microbenchmark cardinality mismatch");
  std::sort(samples.begin(),samples.end());
  std::cout<<dialect<<','<<operation<<','<<size<<','<<samples[samples.size()/2]<<','<<stats.states<<','<<stats.arcs<<','<<stats.paths<<','<<count<<std::endl;
}
int main(int argc,char**) {
  const bool smoke=argc>1;
  std::cout<<"dialect,operation,size,native_ns,states,arcs,paths,samples"<<std::endl;
  for(int dialect=0;dialect<3;++dialect) {
    const std::string name=dialect==0?"symbols":dialect==1?"bits":"bytes";
    for(int size:smoke?std::vector<int>{64}:std::vector<int>{256,1024,4096,16384}) {
      std::vector<Path> wide,deep;
      for(int i=0;i<size;++i) {wide.push_back(encode(dialect,i,width(size)));Path p;for(int bit=width(size)-1;bit>=0;--bit) p.push_back(((i>>bit)&1)+1);deep.push_back(p);}
      row(name,"construction-wide",size,[&]{return literal(wide);},size);
      row(name,"construction-deep",size,[&]{return literal(deep);},size);
    }
    for(int size:smoke?std::vector<int>{64}:std::vector<int>{1024,4096,16384,65536}) {
      std::vector<Path> all,subset,prefixes,other;
      for(int i=0;i<size;++i) {
        auto p=encode(dialect,i,width(size*2));all.push_back(p);other.push_back(encode(dialect,size+i,width(size*2)));
        if(i%4==0) {subset.push_back(p);p.pop_back();prefixes.push_back(p);}
      }
      auto a=literal(all),b=literal(subset),p=literal(prefixes),d=literal(other);
      row(name,"union-subset",size,[&]{return unite(a,b);},size);
      row(name,"intersection-subset",size,[&]{return intersect(a,b);},size/4);
      row(name,"restriction-quarter",size,[&]{return restrict_to(a,p);},size/4);
      row(name,"subtraction-disjoint",size,[&]{return subtract(a,d);},size);
    }
    for(int size:smoke?std::vector<int>{64}:std::vector<int>{256,1024,4096,16384}) {
      for(int regime=0;regime<4;++regime) {
        std::vector<Path> av,bv;for(int index=0;index<size;++index) {
          int left=index,right=index;
          if(regime==1) right+=(1<<28);
          if(regime==2) {left=index<size/4?index*2:index-size/4+(1<<27);right=index<size/4?index*2+1:index-size/4+(1<<29);}
          if(regime==3) {left=index*2;right=index*2+1;}
          av.push_back(encode(dialect,left,31));bv.push_back(encode(dialect,right,31));
        }
        auto a=literal(av),b=regime==0?a:literal(bv);
        const char* names[]={"union-identity","union-root-disjoint","union-mixed-quarter","union-interwoven"};
        row(name,names[regime],size,[&]{return unite(a,b);},regime==0?size:2*size);
      }
    }
    for(int size:smoke?std::vector<int>{64}:std::vector<int>{512,2048,8192,32768}) {
      std::vector<Space> values;for(int i=0;i<size;++i) values.push_back(singleton(encode(dialect,i,width(size))));
      row(name,"incremental-union",size,[&]{Space out;for(const auto& value:values) out=unite(out,value);return out;},size,true);
    }
  }
}
