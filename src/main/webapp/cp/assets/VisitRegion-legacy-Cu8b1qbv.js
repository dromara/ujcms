System.register(["./index-legacy-DywSgIaj.js","./stat-legacy-DZcOoiqc.js"],(function(t,e){"use strict";var a,r,n,l,i,s,o,u,c,d,m,y,v,p,f,Y,g,b,D;return{setters:[t=>{a=t.d,r=t.u,n=t.r,l=t.T,i=t.o,s=t.S,o=t.W,u=t.b,c=t.e,d=t.K,m=t.k,y=t.i,v=t.w,p=t.a7,f=t.a8,Y=t.n,g=t.t},t=>{b=t.c,D=t.d}],execute:function(){const e={class:"p-3 mt-3 app-block"},M={class:"p-3 mt-3 app-block"},h={class:"p-3 mt-3 app-block"};t("default",a({__name:"VisitRegion",setup(t){const{t:a,n:w}=r(),C=n("last30day"),_=t=>{switch(t){case"today":return s().format("YYYY-MM-DD");case"yesterday":return s().subtract(1,"day").format("YYYY-MM-DD");case"last7day":return s().subtract(6,"day").format("YYYY-MM-DD");case"last30day":return s().subtract(29,"day").format("YYYY-MM-DD");case"lastYear":return s().subtract(1,"year").format("YYYY-MM-DD");default:return}},k=t=>"yesterday"===t?s().subtract(1,"day").format("YYYY-MM-DD"):s().format("YYYY-MM-DD"),z=l(),x=l(),S=async t=>{(async t=>{const e=await b({begin:_(t),end:k(t)}),r=e.reduce(((t,e)=>t+Number(e.pvCount)),0),n={title:{text:a("menu.stat.visitCountry"),textStyle:{color:"#909399",fontWeight:"normal",fontSize:16}},tooltip:{trigger:"item",valueFormatter:t=>w(100*t/r,"decimal")+"%"},legend:{type:"scroll",orient:"vertical",right:"10%",top:16,bottom:16},series:[{name:a("menu.stat.visitCountry"),type:"pie",radius:"72%",center:["40%","56%"],data:e.map((t=>({value:Number(t.pvCount),name:t.name})))}]},l=z.value;if(null==l)return;let i=o.getInstanceByDom(l);null==i&&(i=o.init(l)),i.setOption(n),window.addEventListener("resize",(function(){i&&i.resize()}))})(t),(async t=>{const e=await D({begin:_(t),end:k(t)}),r=e.reduce(((t,e)=>t+Number(e.pvCount)),0),n={title:{text:a("menu.stat.visitProvince"),textStyle:{color:"#909399",fontWeight:"normal",fontSize:16}},tooltip:{trigger:"item",valueFormatter:t=>w(100*t/r,"decimal")+"%"},legend:{type:"scroll",orient:"vertical",right:"10%",top:16,bottom:16},series:[{name:a("menu.stat.visitProvince"),type:"pie",radius:"72%",center:["40%","56%"],data:e.map((t=>({value:Number(t.pvCount),name:t.name})))}]},l=x.value;if(null==l)return;let i=o.getInstanceByDom(l);null==i&&(i=o.init(l)),i.setOption(n),window.addEventListener("resize",(function(){i&&i.resize()}))})(t)};return i((async()=>{S(C.value)})),(t,a)=>{const r=u("el-radio-button"),n=u("el-radio-group"),l=u("el-col"),i=u("el-row");return c(),d("div",null,[m("div",e,[m("div",null,[y(n,{modelValue:C.value,"onUpdate:modelValue":a[0]||(a[0]=t=>C.value=t),onChange:a[1]||(a[1]=t=>S(t))},{default:v((()=>[(c(),d(p,null,f(["today","yesterday","last7day","last30day","lastYear","all"],(e=>y(r,{key:e,value:e},{default:v((()=>[Y(g(t.$t(`visit.${e}`)),1)])),_:2},1032,["value"]))),64))])),_:1},8,["modelValue"])])]),y(i,{gutter:12},{default:v((()=>[y(l,{span:12},{default:v((()=>[m("div",M,[m("div",{ref_key:"countryChartRef",ref:z,class:"h-80"},null,512)])])),_:1}),y(l,{span:12},{default:v((()=>[m("div",h,[m("div",{ref_key:"provinceChartRef",ref:x,class:"h-80"},null,512)])])),_:1})])),_:1})])}}}))}}}));