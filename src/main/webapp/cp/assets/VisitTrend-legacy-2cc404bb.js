System.register(["./index-legacy-b678d1bc.js","./stat-legacy-4f174737.js"],(function(t,a){"use strict";var e,n,r,s,l,i,d,o,u,y,c,m,f,p,v,b,g,h,w;return{setters:[t=>{e=t.d,n=t.u,r=t.U,s=t.r,l=t.V,i=t.o,d=t.a,o=t.c,u=t.I,y=t.j,c=t.h,m=t.w,f=t.ac,p=t.ad,v=t.k,b=t.t,g=t.$},t=>{h=t.d,w=t.q}],execute:function(){const a={class:"p-3 mt-3 app-block"};t("default",e({__name:"VisitTrend",setup(t){const{t:e}=n();r.extend(h);const x=s("last30day"),O=t=>{switch(t){case"now":return r().subtract(2,"hour").format();case"today":return r().startOf("day").format();case"yesterday":return r().startOf("day").subtract(1,"day").format();case"last7day":return r().startOf("day").subtract(6,"day").format();case"last30day":return r().startOf("day").subtract(29,"day").format();case"lastYear":return r().startOf("day").subtract(1,"year").format();default:return}},k=t=>"now"===t?r().format():"yesterday"===t?r().endOf("day").subtract(1,"day").format():r().endOf("day").format(),C=l(),V=async t=>{const a=await w({begin:O(t),end:k(t)}),n={tooltip:{trigger:"axis"},legend:{data:[e("visit.pv"),e("visit.uv"),e("visit.ip")]},grid:{left:16,right:24,top:40,bottom:8,containLabel:!0},xAxis:{type:"category",boundaryGap:!1,axisTick:{show:!1},data:a.map((t=>t.dateString))},yAxis:{type:"value",minInterval:1},series:[{name:e("visit.pv"),type:"line",symbol:a.length>30?"none":"circle",data:a.map((t=>t.pvCount))},{name:e("visit.uv"),type:"line",symbol:a.length>30?"none":"circle",data:a.map((t=>t.uvCount))},{name:e("visit.ip"),type:"line",symbol:a.length>30?"none":"circle",data:a.map((t=>t.ipCount))}]},r=C.value;if(null==r)return;let s=g.getInstanceByDom(r);null==s&&(s=g.init(r)),s.setOption(n),window.addEventListener("resize",(function(){s&&s.resize()}))};return i((async()=>{V(x.value)})),(t,e)=>{const n=d("el-radio-button"),r=d("el-radio-group");return o(),u("div",null,[y("div",a,[y("div",null,[c(r,{modelValue:x.value,"onUpdate:modelValue":e[0]||(e[0]=t=>x.value=t),onChange:e[1]||(e[1]=t=>V(t))},{default:m((()=>[(o(),u(f,null,p(["now","today","yesterday","last7day","last30day","lastYear","all"],(a=>c(n,{key:a,label:a},{default:m((()=>[v(b(t.$t(`visit.${a}`)),1)])),_:2},1032,["label"]))),64))])),_:1},8,["modelValue"])]),y("div",{ref_key:"trendChart",ref:C,class:"h-80"},null,512)])])}}}))}}}));