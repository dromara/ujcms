import{d as R,u as B,r as S,T as b,o as x,S as r,W as m,b as p,e as C,K as M,k as s,i as c,w as u,a7 as N,a8 as V,n as z,t as E}from"./index-B6OoDg6B.js";import{c as P,d as F}from"./stat-CFOZ0IOn.js";const I={class:"p-3 mt-3 app-block"},L={class:"p-3 mt-3 app-block"},W={class:"p-3 mt-3 app-block"},$=R({__name:"VisitRegion",setup(q){const{t:d,n:v}=B(),y=S("last30day"),Y=e=>{switch(e){case"today":return r().format("YYYY-MM-DD");case"yesterday":return r().subtract(1,"day").format("YYYY-MM-DD");case"last7day":return r().subtract(6,"day").format("YYYY-MM-DD");case"last30day":return r().subtract(29,"day").format("YYYY-MM-DD");case"lastYear":return r().subtract(1,"year").format("YYYY-MM-DD");default:return}},_=e=>e==="yesterday"?r().subtract(1,"day").format("YYYY-MM-DD"):r().format("YYYY-MM-DD"),g=b(),w=async e=>{const n=await P({begin:Y(e),end:_(e)}),i=n.reduce((t,f)=>t+Number(f.pvCount),0),l={title:{text:d("menu.stat.visitCountry"),textStyle:{color:"#909399",fontWeight:"normal",fontSize:16}},tooltip:{trigger:"item",valueFormatter:t=>v(t*100/i,"decimal")+"%"},legend:{type:"scroll",orient:"vertical",right:"10%",top:16,bottom:16},series:[{name:d("menu.stat.visitCountry"),type:"pie",radius:"72%",center:["40%","56%"],data:n.map(t=>({value:Number(t.pvCount),name:t.name}))}]},o=g.value;if(o==null)return;let a=m.getInstanceByDom(o);a==null&&(a=m.init(o)),a.setOption(l),window.addEventListener("resize",function(){a&&a.resize()})},h=b(),k=async e=>{const n=await F({begin:Y(e),end:_(e)}),i=n.reduce((t,f)=>t+Number(f.pvCount),0),l={title:{text:d("menu.stat.visitProvince"),textStyle:{color:"#909399",fontWeight:"normal",fontSize:16}},tooltip:{trigger:"item",valueFormatter:t=>v(t*100/i,"decimal")+"%"},legend:{type:"scroll",orient:"vertical",right:"10%",top:16,bottom:16},series:[{name:d("menu.stat.visitProvince"),type:"pie",radius:"72%",center:["40%","56%"],data:n.map(t=>({value:Number(t.pvCount),name:t.name}))}]},o=h.value;if(o==null)return;let a=m.getInstanceByDom(o);a==null&&(a=m.init(o)),a.setOption(l),window.addEventListener("resize",function(){a&&a.resize()})},D=async e=>{w(e),k(e)};return x(async()=>{D(y.value)}),(e,n)=>{const i=p("el-radio-button"),l=p("el-radio-group"),o=p("el-col"),a=p("el-row");return C(),M("div",null,[s("div",I,[s("div",null,[c(l,{modelValue:y.value,"onUpdate:modelValue":n[0]||(n[0]=t=>y.value=t),onChange:n[1]||(n[1]=t=>D(t))},{default:u(()=>[(C(),M(N,null,V(["today","yesterday","last7day","last30day","lastYear","all"],t=>c(i,{key:t,value:t},{default:u(()=>[z(E(e.$t("visit.".concat(t))),1)]),_:2},1032,["value"])),64))]),_:1},8,["modelValue"])])]),c(a,{gutter:12},{default:u(()=>[c(o,{span:12},{default:u(()=>[s("div",L,[s("div",{ref_key:"countryChartRef",ref:g,class:"h-80"},null,512)])]),_:1}),c(o,{span:12},{default:u(()=>[s("div",W,[s("div",{ref_key:"provinceChartRef",ref:h,class:"h-80"},null,512)])]),_:1})]),_:1})])}}});export{$ as default};