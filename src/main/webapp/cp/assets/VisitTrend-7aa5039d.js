import{d as _,u as w,U as e,r as C,V as x,o as O,a as y,c as p,I as m,j as d,h as f,w as v,ac as V,ad as k,k as B,t as D,$ as g}from"./index-0b297134.js";import{d as T,q as I}from"./stat-f2e5dc4d.js";const R={class:"p-3 mt-3 app-block"},S=_({__name:"VisitTrend",setup(E){const{t:s}=w();e.extend(T);const i=C("last30day"),b=n=>{switch(n){case"now":return e().subtract(2,"hour").format();case"today":return e().startOf("day").format();case"yesterday":return e().startOf("day").subtract(1,"day").format();case"last7day":return e().startOf("day").subtract(6,"day").format();case"last30day":return e().startOf("day").subtract(29,"day").format();case"lastYear":return e().startOf("day").subtract(1,"year").format();default:return}},h=n=>n==="now"?e().format():n==="yesterday"?e().endOf("day").subtract(1,"day").format():e().endOf("day").format(),u=x(),c=async n=>{const a=await I({begin:b(n),end:h(n)}),l={tooltip:{trigger:"axis"},legend:{data:[s("visit.pv"),s("visit.uv"),s("visit.ip")]},grid:{left:16,right:24,top:40,bottom:8,containLabel:!0},xAxis:{type:"category",boundaryGap:!1,axisTick:{show:!1},data:a.map(r=>r.dateString)},yAxis:{type:"value",minInterval:1},series:[{name:s("visit.pv"),type:"line",symbol:a.length>30?"none":"circle",data:a.map(r=>r.pvCount)},{name:s("visit.uv"),type:"line",symbol:a.length>30?"none":"circle",data:a.map(r=>r.uvCount)},{name:s("visit.ip"),type:"line",symbol:a.length>30?"none":"circle",data:a.map(r=>r.ipCount)}]},o=u.value;if(o==null)return;let t=g.getInstanceByDom(o);t==null&&(t=g.init(o)),t.setOption(l),window.addEventListener("resize",function(){t&&t.resize()})};return O(async()=>{c(i.value)}),(n,a)=>{const l=y("el-radio-button"),o=y("el-radio-group");return p(),m("div",null,[d("div",R,[d("div",null,[f(o,{modelValue:i.value,"onUpdate:modelValue":a[0]||(a[0]=t=>i.value=t),onChange:a[1]||(a[1]=t=>c(t))},{default:v(()=>[(p(),m(V,null,k(["now","today","yesterday","last7day","last30day","lastYear","all"],t=>f(l,{key:t,label:t},{default:v(()=>[B(D(n.$t(`visit.${t}`)),1)]),_:2},1032,["label"])),64))]),_:1},8,["modelValue"])]),d("div",{ref_key:"trendChart",ref:u,class:"h-80"},null,512)])])}}});export{S as default};