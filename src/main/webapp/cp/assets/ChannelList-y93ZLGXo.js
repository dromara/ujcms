import{d as Ze,p as al,r as c,ac as Z,a3 as A,v as ge,b as y,e as m,f as h,w as a,i as e,j as p,aq as S,l as T,t as C,h as V,I as q,a9 as _,aa as L,k as ne,_ as nl,u as tl,o as ul,c as ol,aL as sl,g as Qe,as as Y,ak as rl,ah as dl,aK as il,E as ml,aM as pl,aN as vl,aJ as cl}from"./index-BgkR3Um2.js";import{c as fl,d as hl,t as xe}from"./tree-MP4T2jDQ.js";import{H as yl,I as bl,J as gl,K as Vl,L as el,m as Ve,M as wl,n as $l,N as ll,O as kl}from"./content-D9cimwRS.js";import{e as ql,f as _l}from"./system-CYHoyBnS.js";import{_ as Tl,a as He,b as Cl,c as Ml}from"./QueryItem.vue_vue_type_script_setup_true_lang-SYpjwARX.js";import{_ as Ul}from"./ListMove.vue_vue_type_script_setup_true_lang-DPvxSgMj.js";import{b as Il,q as We}from"./config-DJA2wLsE.js";import{a as Ge,m as Xe,g as Ye}from"./data-DYmAP5cj.js";import{_ as Dl}from"./FieldItem.vue_vue_type_script_setup_true_lang-Un2jr84j.js";import{_ as Kl}from"./DialogForm.vue_vue_type_script_setup_true_lang-wtbBfkW1.js";import{T as Sl}from"./Tinymce-DnVRVtZo.js";import{T as Ll}from"./TuiEditor-SyqYa3LQ.js";import{a as Pl}from"./FileListUpload.vue_vue_type_style_index_0_scoped_8f839ee5_lang-CYFJM6Lm.js";import"./vuedraggable.umd-C-c0fYvw.js";/* empty css                                                                   */import"./BaseUpload-DnjvaBl9.js";import"./index-Uqvazc4q.js";import"./sortable.esm-BmjBFecF.js";const zl={class:"w-full"},Bl=Ze({name:"ChannelForm",__name:"ChannelForm",props:{modelValue:{type:Boolean,required:!0},beanId:{type:String,default:null},beanIds:{type:Array,required:!0},parent:{type:Object,default:null}},emits:{"update:modelValue":null,finished:null},setup(x,{emit:ie}){const j=x,B=ie,{modelValue:J,parent:b}=al(j),P=c(),o=c({}),E=c([]),Q=c([]),H=c([]),M=c([]),F=c([]),W=c([]),G=c([]),U=c([]),I=c(),R=Z(()=>M.value.find(n=>n.id===I.value)),r=Z(()=>{var n;return Ge(Xe(Ye().channel.mains,(n=R.value)==null?void 0:n.mains,"channel"))}),g=Z(()=>{var n;return Ge(Xe(Ye().channel.asides,(n=R.value)==null?void 0:n.asides,"channel"))}),N=Z(()=>{var n;return JSON.parse(((n=R.value)==null?void 0:n.customs)||"[]")}),me=Z(()=>{const n=fl(H.value,o.value.id);return A.allChannelPermission?n:hl(n,W.value)}),X=async()=>{H.value=xe(await Ve())},O=async()=>{A.epRank>0&&(E.value=await ql({category:"sys_article",deployed:!0}))},pe=async()=>{A.epRank>=3&&(Q.value=await Il())},te=async()=>{await X(),B("finished")},ve=async()=>{M.value=await We({type:"channel"}),I.value==null&&M.value.length>0&&(I.value=M.value[0].id)},ce=async()=>{F.value=await We({type:"article"})},ee=async()=>{G.value=await wl()},fe=async()=>{U.value=await $l()},ue=async()=>{W.value=await ll()};ge(J,()=>{var n,u,d;J.value&&(I.value=(d=(n=b.value)==null?void 0:n.articleModelId)!=null?d:(u=M.value[0])==null?void 0:u.id,ue(),ve(),ce(),ee(),fe(),O(),pe())}),ge(N,()=>{oe(o.value.customs)});const oe=n=>(N.value.forEach(u=>{n[u.code]==null&&(n[u.code]=u.defaultValue,u.defaultValueKey!=null&&(n[u.code+"Key"]=u.defaultValueKey))}),n);return(n,u)=>{const d=y("el-input"),i=y("el-form-item"),f=y("el-col"),w=y("el-checkbox"),D=y("el-option"),K=y("el-select"),le=y("el-switch"),se=y("el-radio"),$=y("el-radio-group"),re=y("el-row"),he=y("el-tree-select"),ye=y("el-input-number"),be=y("el-tab-pane"),de=y("el-tabs");return m(),h(Kl,{values:o.value,"onUpdate:values":u[26]||(u[26]=t=>o.value=t),name:n.$t("menu.content.channel"),"query-bean":p(bl),"create-bean":p(gl),"update-bean":p(Vl),"delete-bean":p(el),"bean-id":x.beanId,"bean-ids":x.beanIds,focus:P.value,"init-values":t=>{var l,s,k,z,ae,v,we,$e,ke,qe,_e,Te,Ce,Me,Ue,Ie,De,Ke,Se,Le,Pe,ze,Be,Ee,Fe,Re,Ne,Oe,Ae,je,Je;return{parentId:(s=t==null?void 0:t.parentId)!=null?s:(l=p(b))==null?void 0:l.id,type:1,channelModelId:(v=(z=t==null?void 0:t.channelModelId)!=null?z:(k=p(b))==null?void 0:k.channelModelId)!=null?v:(ae=M.value[0])==null?void 0:ae.id,articleModelId:(qe=($e=t==null?void 0:t.articleModelId)!=null?$e:(we=p(b))==null?void 0:we.articleModelId)!=null?qe:(ke=F.value[0])==null?void 0:ke.id,nav:(Ce=(Te=t==null?void 0:t.nav)!=null?Te:(_e=p(b))==null?void 0:_e.nav)!=null?Ce:!0,channelTemplate:(Ie=(Ue=t==null?void 0:t.channelTemplate)!=null?Ue:(Me=p(b))==null?void 0:Me.channelTemplate)!=null?Ie:G.value[0],articleTemplate:(Se=(Ke=t==null?void 0:t.articleTemplate)!=null?Ke:(De=p(b))==null?void 0:De.articleTemplate)!=null?Se:U.value[0],pageSize:20,allowComment:(ze=(Pe=t==null?void 0:t.allowComment)!=null?Pe:(Le=p(b))==null?void 0:Le.allowComment)!=null?ze:!0,allowContribute:(Fe=(Ee=t==null?void 0:t.allowContribute)!=null?Ee:(Be=p(b))==null?void 0:Be.allowContribute)!=null?Fe:!0,allowSearch:(Oe=(Ne=t==null?void 0:t.allowSearch)!=null?Ne:(Re=p(b))==null?void 0:Re.allowSearch)!=null?Oe:!0,orderDesc:(Je=(je=t==null?void 0:t.orderDesc)!=null?je:(Ae=p(b))==null?void 0:Ae.orderDesc)!=null?Je:!0,customs:{}}},"to-values":t=>({...t}),perms:"channel","model-value":x.modelValue,"disable-edit":t=>!p(A).allChannelPermission&&t.id!=null&&!W.value.includes(t.id),"label-width":"120px",large:"","onUpdate:modelValue":u[27]||(u[27]=t=>n.$emit("update:modelValue",t)),onFinished:te,onBeanChange:u[28]||(u[28]=async t=>{var l,s,k,z;I.value=(z=(s=t==null?void 0:t.channelModelId)!=null?s:(l=p(b))==null?void 0:l.channelModelId)!=null?z:(k=F.value[0])==null?void 0:k.id,await X()})},{default:a(({bean:t})=>[e(re,null,{default:a(()=>[e(f,{span:18},{default:a(()=>[e(re,null,{default:a(()=>[e(f,{span:r.value.name.double?12:24},{default:a(()=>{var l;return[e(i,{prop:"name",label:(l=r.value.name.name)!=null?l:n.$t("channel.name"),rules:{required:!0,message:()=>n.$t("v.required")}},{default:a(()=>[e(d,{ref_key:"focus",ref:P,modelValue:o.value.name,"onUpdate:modelValue":u[0]||(u[0]=s=>o.value.name=s),maxlength:"50"},null,8,["modelValue"])]),_:1},8,["label","rules"])]}),_:1},8,["span"]),e(f,{span:r.value.alias.double?12:24},{default:a(()=>[e(i,{prop:"alias",rules:[{required:!0,message:()=>n.$t("v.required")},{pattern:/^[\w-]*$/,message:()=>n.$t("channel.error.aliasPattern")},{asyncValidator:async(l,s,k)=>{if(s!==t.alias&&await p(yl)(s)){k(n.$t("channel.error.aliasExist"));return}k()}}]},{label:a(()=>{var l;return[e(S,{label:(l=r.value.alias.name)!=null?l:n.$t("channel.alias"),message:"channel.alias",help:""},null,8,["label"])]}),default:a(()=>[e(d,{modelValue:o.value.alias,"onUpdate:modelValue":u[1]||(u[1]=l=>o.value.alias=l),maxlength:"50"},null,8,["modelValue"])]),_:2},1032,["rules"])]),_:2},1032,["span"]),o.value.type===3?(m(),h(f,{key:0,span:r.value.linkUrl.double?12:24},{default:a(()=>[e(i,{prop:"linkUrl",rules:[{required:!0,message:()=>n.$t("v.required")},{pattern:/^(http|\/).*$/,message:()=>n.$t("channel.error.linkUrl")}]},{label:a(()=>{var l;return[e(S,{label:(l=r.value.linkUrl.name)!=null?l:n.$t("channel.linkUrl"),message:"channel.linkUrl",help:""},null,8,["label"])]}),default:a(()=>[e(d,{modelValue:o.value.linkUrl,"onUpdate:modelValue":u[3]||(u[3]=l=>o.value.linkUrl=l),maxlength:"255"},{append:a(()=>[e(w,{modelValue:o.value.targetBlank,"onUpdate:modelValue":u[2]||(u[2]=l=>o.value.targetBlank=l)},{default:a(()=>[T(C(n.$t("channel.targetBlank")),1)]),_:1},8,["modelValue"])]),_:1},8,["modelValue"])]),_:1},8,["rules"])]),_:1},8,["span"])):V("",!0),o.value.type<3?(m(),q(_,{key:1},[r.value.seoTitle.show?(m(),h(f,{key:0,span:r.value.seoTitle.double?12:24},{default:a(()=>[e(i,{prop:"seoTitle",rules:r.value.seoTitle.required?{required:!0,message:()=>n.$t("v.required")}:void 0},{label:a(()=>{var l;return[e(S,{label:(l=r.value.seoTitle.name)!=null?l:n.$t("channel.seoTitle"),message:"channel.seoTitle",help:""},null,8,["label"])]}),default:a(()=>[e(d,{modelValue:o.value.seoTitle,"onUpdate:modelValue":u[4]||(u[4]=l=>o.value.seoTitle=l),maxlength:"150"},null,8,["modelValue"])]),_:1},8,["rules"])]),_:1},8,["span"])):V("",!0),r.value.seoKeywords.show?(m(),h(f,{key:1,span:r.value.seoKeywords.double?12:24},{default:a(()=>[e(i,{prop:"seoKeywords",rules:r.value.seoKeywords.required?{required:!0,message:()=>n.$t("v.required")}:void 0},{label:a(()=>{var l;return[e(S,{label:(l=r.value.seoKeywords.name)!=null?l:n.$t("channel.seoKeywords"),message:"channel.seoKeywords",help:""},null,8,["label"])]}),default:a(()=>[e(d,{modelValue:o.value.seoKeywords,"onUpdate:modelValue":u[5]||(u[5]=l=>o.value.seoKeywords=l),maxlength:"150"},null,8,["modelValue"])]),_:1},8,["rules"])]),_:1},8,["span"])):V("",!0),r.value.seoDescription.show?(m(),h(f,{key:2,span:r.value.seoDescription.double?12:24},{default:a(()=>[e(i,{prop:"seoDescription",rules:r.value.seoDescription.required?{required:!0,message:()=>n.$t("v.required")}:void 0},{label:a(()=>{var l;return[e(S,{label:(l=r.value.seoDescription.name)!=null?l:n.$t("channel.seoDescription"),message:"channel.seoDescription",help:""},null,8,["label"])]}),default:a(()=>[e(d,{modelValue:o.value.seoDescription,"onUpdate:modelValue":u[6]||(u[6]=l=>o.value.seoDescription=l),maxlength:"1000"},null,8,["modelValue"])]),_:1},8,["rules"])]),_:1},8,["span"])):V("",!0)],64)):V("",!0),r.value.image.show?(m(),h(f,{key:2,span:r.value.image.double?12:24},{default:a(()=>{var l;return[e(i,{prop:"image",label:(l=r.value.image.name)!=null?l:n.$t("channel.image"),rules:r.value.image.required?{required:!0,message:()=>n.$t("v.required")}:void 0},{default:a(()=>[e(p(Pl),{modelValue:o.value.image,"onUpdate:modelValue":u[7]||(u[7]=s=>o.value.image=s),height:r.value.image.imageHeight,width:r.value.image.imageWidth,mode:r.value.image.imageMode},null,8,["modelValue","height","width","mode"])]),_:1},8,["label","rules"])]}),_:1},8,["span"])):V("",!0),r.value.channelModel.show?(m(),h(f,{key:3,span:r.value.channelModel.double?12:24},{default:a(()=>{var l;return[e(i,{prop:"channelModelId",label:(l=r.value.channelModel.name)!=null?l:n.$t("channel.channelModel"),rules:{required:!0,message:()=>n.$t("v.required")}},{default:a(()=>[e(K,{modelValue:o.value.channelModelId,"onUpdate:modelValue":u[8]||(u[8]=s=>o.value.channelModelId=s),class:"w-full",onChange:u[9]||(u[9]=s=>{I.value=s})},{default:a(()=>[(m(!0),q(_,null,L(M.value,s=>(m(),h(D,{key:s.id,label:s.name,value:s.id},null,8,["label","value"]))),128))]),_:1},8,["modelValue"])]),_:1},8,["label","rules"])]}),_:1},8,["span"])):V("",!0),r.value.articleModel.show?(m(),h(f,{key:4,span:r.value.articleModel.double?12:24},{default:a(()=>{var l;return[e(i,{prop:"articleModelId",label:(l=r.value.articleModel.name)!=null?l:n.$t("channel.articleModel"),rules:{required:!0,message:()=>n.$t("v.required")}},{default:a(()=>[e(K,{modelValue:o.value.articleModelId,"onUpdate:modelValue":u[10]||(u[10]=s=>o.value.articleModelId=s),class:"w-full"},{default:a(()=>[(m(!0),q(_,null,L(F.value,s=>(m(),h(D,{key:s.id,label:s.name,value:s.id},null,8,["label","value"]))),128))]),_:1},8,["modelValue"])]),_:1},8,["label","rules"])]}),_:1},8,["span"])):V("",!0),[3,4,5].includes(o.value.type)?V("",!0):(m(),q(_,{key:5},[r.value.channelTemplate.show?(m(),h(f,{key:0,span:r.value.channelTemplate.double?12:24},{default:a(()=>{var l;return[e(i,{prop:"channelTemplate",label:(l=r.value.channelTemplate.name)!=null?l:n.$t("channel.channelTemplate"),rules:r.value.channelTemplate.required?{required:!0,message:()=>n.$t("v.required")}:void 0},{default:a(()=>[e(K,{modelValue:o.value.channelTemplate,"onUpdate:modelValue":u[11]||(u[11]=s=>o.value.channelTemplate=s),class:"w-full"},{default:a(()=>[(m(!0),q(_,null,L(G.value,s=>(m(),h(D,{key:s,label:s+".html",value:s},null,8,["label","value"]))),128))]),_:1},8,["modelValue"])]),_:1},8,["label","rules"])]}),_:1},8,["span"])):V("",!0),r.value.articleTemplate.show?(m(),h(f,{key:1,span:r.value.articleTemplate.double?12:24},{default:a(()=>{var l;return[e(i,{prop:"articleTemplate",label:(l=r.value.articleTemplate.name)!=null?l:n.$t("channel.articleTemplate"),rules:r.value.articleTemplate.required?{required:!0,message:()=>n.$t("v.required")}:void 0},{default:a(()=>[e(K,{modelValue:o.value.articleTemplate,"onUpdate:modelValue":u[12]||(u[12]=s=>o.value.articleTemplate=s),class:"w-full"},{default:a(()=>[(m(!0),q(_,null,L(U.value,s=>(m(),h(D,{key:s,label:s+".html",value:s},null,8,["label","value"]))),128))]),_:1},8,["modelValue"])]),_:1},8,["label","rules"])]}),_:1},8,["span"])):V("",!0)],64)),r.value.nav.show?(m(),h(f,{key:6,span:r.value.nav.double?12:24},{default:a(()=>[e(i,{prop:"nav",rules:r.value.nav.required?{required:!0,message:()=>n.$t("v.required")}:void 0},{label:a(()=>{var l;return[e(S,{label:(l=r.value.nav.name)!=null?l:n.$t("channel.nav"),message:"channel.nav",help:""},null,8,["label"])]}),default:a(()=>[e(le,{modelValue:o.value.nav,"onUpdate:modelValue":u[13]||(u[13]=l=>o.value.nav=l)},null,8,["modelValue"])]),_:1},8,["rules"])]),_:1},8,["span"])):V("",!0),r.value.allowSearch.show?(m(),h(f,{key:7,span:r.value.allowSearch.double?12:24},{default:a(()=>[e(i,{prop:"allowSearch",rules:r.value.allowSearch.required?{required:!0,message:()=>n.$t("v.required")}:void 0},{label:a(()=>{var l;return[e(S,{label:(l=r.value.allowSearch.name)!=null?l:n.$t("channel.allowSearch"),message:"channel.allowSearch",help:""},null,8,["label"])]}),default:a(()=>[e(le,{modelValue:o.value.allowSearch,"onUpdate:modelValue":u[14]||(u[14]=l=>o.value.allowSearch=l)},null,8,["modelValue"])]),_:1},8,["rules"])]),_:1},8,["span"])):V("",!0),(m(!0),q(_,null,L(N.value,l=>(m(),h(f,{key:l.code,span:l.double?12:24},{default:a(()=>[e(i,{prop:"customs.".concat(l.code),rules:l.required?{required:!0,message:()=>n.$t("v.required")}:void 0},{label:a(()=>[e(S,{label:l.name},null,8,["label"])]),default:a(()=>[e(Dl,{modelValue:o.value.customs[l.code],"onUpdate:modelValue":s=>o.value.customs[l.code]=s,"model-key":o.value.customs[l.code+"Key"],"onUpdate:modelKey":s=>o.value.customs[l.code+"Key"]=s,field:l},null,8,["modelValue","onUpdate:modelValue","model-key","onUpdate:modelKey","field"])]),_:2},1032,["prop","rules"])]),_:2},1032,["span"]))),128)),o.value.type===2?(m(),h(f,{key:8,span:r.value.text.double?12:24},{default:a(()=>{var l;return[e(i,{prop:"text",label:(l=r.value.text.name)!=null?l:n.$t("channel.text"),rules:r.value.text.required?{required:!0,message:()=>n.$t("v.required")}:void 0},{default:a(()=>[ne("div",zl,[r.value.text.editorSwitch?(m(),h($,{key:0,modelValue:o.value.editorType,"onUpdate:modelValue":u[15]||(u[15]=s=>o.value.editorType=s),class:"mr-6",onChange:u[16]||(u[16]=()=>o.value.markdown="")},{default:a(()=>[(m(),q(_,null,L([1,2],s=>e(se,{key:s,value:s},{default:a(()=>[T(C(n.$t("model.field.editorType.".concat(s))),1)]),_:2},1032,["value"])),64))]),_:1},8,["modelValue"])):V("",!0),o.value.editorType===2?(m(),h(p(Ll),{key:1,modelValue:o.value.markdown,"onUpdate:modelValue":u[17]||(u[17]=s=>o.value.markdown=s),html:o.value.text,"onUpdate:html":u[18]||(u[18]=s=>o.value.text=s),class:"leading-6"},null,8,["modelValue","html"])):(m(),h(p(Sl),{key:2,ref:"tinyText",modelValue:o.value.text,"onUpdate:modelValue":u[19]||(u[19]=s=>o.value.text=s)},null,8,["modelValue"]))])]),_:1},8,["label","rules"])]}),_:1},8,["span"])):V("",!0)]),_:2},1024)]),_:2},1024),e(f,{span:6,class:"el-form--label-top label-top"},{default:a(()=>[e(de,{type:"border-card",class:"ml-5"},{default:a(()=>[e(be,{label:n.$t("channel.tabs.setting")},{default:a(()=>{var l,s,k,z,ae;return[e(i,{prop:"parentId",label:(l=g.value.parent.name)!=null?l:n.$t("channel.parent")},{default:a(()=>[e(he,{modelValue:o.value.parentId,"onUpdate:modelValue":u[20]||(u[20]=v=>o.value.parentId=v),data:me.value,"node-key":"id",props:{label:"name",disabled:"disabled"},"render-after-expand":!1,"check-strictly":"",clearable:"",class:"w-full"},null,8,["modelValue","data"])]),_:1},8,["label"]),e(i,{prop:"type",label:(s=g.value.type.name)!=null?s:n.$t("channel.type"),rules:{required:!0,message:()=>n.$t("v.required")}},{default:a(()=>[e(K,{modelValue:o.value.type,"onUpdate:modelValue":u[21]||(u[21]=v=>o.value.type=v),class:"w-full"},{default:a(()=>[(m(),q(_,null,L([1,2,3,4],v=>e(D,{key:v,label:n.$t("channel.type.".concat(v)),value:v},null,8,["label","value"])),64))]),_:1},8,["modelValue"])]),_:1},8,["label","rules"]),e(i,{prop:"processKey",label:(k=g.value.processKey.name)!=null?k:n.$t("channel.processKey"),rules:g.value.processKey.required?{required:!0,message:()=>n.$t("v.required")}:void 0},{default:a(()=>[e(K,{modelValue:o.value.processKey,"onUpdate:modelValue":u[22]||(u[22]=v=>o.value.processKey=v),clearable:"",class:"w-full"},{default:a(()=>[(m(!0),q(_,null,L(E.value,v=>(m(),h(D,{key:v.key,label:v.name,value:v.key},null,8,["label","value"]))),128))]),_:1},8,["modelValue"])]),_:1},8,["label","rules"]),p(A).epRank>=3&&g.value.performanceType.show?(m(),h(i,{key:0,prop:"performanceType",label:(z=g.value.performanceType.name)!=null?z:n.$t("channel.performanceType"),rules:g.value.performanceType.required?{required:!0,message:()=>n.$t("v.required")}:void 0},{default:a(()=>[e(K,{modelValue:o.value.performanceTypeId,"onUpdate:modelValue":u[23]||(u[23]=v=>o.value.performanceTypeId=v),clearable:"",class:"w-full"},{default:a(()=>[(m(!0),q(_,null,L(Q.value,v=>(m(),h(D,{key:v.id,label:v.name,value:v.id},null,8,["label","value"]))),128))]),_:1},8,["modelValue"])]),_:1},8,["label","rules"])):V("",!0),e(i,{prop:"pageSize",label:(ae=g.value.pageSize.name)!=null?ae:n.$t("channel.pageSize"),rules:{required:!0,message:()=>n.$t("v.required")}},{default:a(()=>[e(ye,{modelValue:o.value.pageSize,"onUpdate:modelValue":u[24]||(u[24]=v=>o.value.pageSize=v),min:1,max:200},null,8,["modelValue"])]),_:1},8,["label","rules"]),e(i,{prop:"orderDesc",rules:{required:!0,message:()=>n.$t("v.required")}},{label:a(()=>{var v;return[e(S,{label:(v=g.value.orderDesc.name)!=null?v:n.$t("channel.orderDesc"),message:"channel.orderDesc",help:""},null,8,["label"])]}),default:a(()=>[e(le,{modelValue:o.value.orderDesc,"onUpdate:modelValue":u[25]||(u[25]=v=>o.value.orderDesc=v)},null,8,["modelValue"])]),_:1},8,["rules"])]}),_:1},8,["label"])]),_:1})]),_:1})]),_:2},1024)]),_:1},8,["values","name","query-bean","create-bean","update-bean","delete-bean","bean-id","bean-ids","focus","init-values","to-values","model-value","disable-edit"])}}}),El=nl(Bl,[["__scopeId","data-v-37ff9d5c"]]),Fl={class:"my-1 ml-2"},Rl={class:"mb-3"},Nl={class:"mt-3 app-block"},oa=Ze({name:"ChannelList",__name:"ChannelList",setup(x){const{t:ie}=tl(),j=c({}),B=c(),J=c(),b=c([]),P=c([]),o=c(!1),E=c(!1),Q=c(),H=c(),M=Z(()=>b.value.map(d=>d.id)),F=c(!1),W=c([]),G=c([]),U=c(),I=c([]),R=c(!1),r=c(),g=c(),N=d=>A.allChannelPermission||W.value.includes(d.id);ge(r,d=>{U.value.filter(d)});const me=async()=>{R.value=!0;try{I.value=xe(await Ve({isOnlyParent:!0})),vl(()=>{var d;r.value!=null&&U.value.filter(r.value),U.value.setCurrentKey((d=g.value)==null?void 0:d.id)})}finally{R.value=!1}},X=async()=>{var d;o.value=!0;try{const i=Object.values(j.value).filter(f=>f!==void 0&&f!=="").length>0;b.value=await Ve({...cl(j.value),parentId:(d=g.value)==null?void 0:d.id,isIncludeChildren:i,Q_OrderBy:B.value}),F.value=i||B.value!==void 0}finally{o.value=!1}},O=()=>{me(),X()},pe=async()=>{A.epRank>0&&(G.value=await _l({category:"sys_article",latestVersion:!0}))},te=async()=>{W.value=await ll()};ul(()=>{O(),pe(),te()});const ve=({column:d,prop:i,order:f})=>{var w;i&&f?B.value=((w=d.sortBy)!=null?w:i)+(f==="descending"?"_desc":""):B.value=void 0,O()},ce=()=>{O(),te()},ee=()=>X(),fe=()=>{J.value.clearSort(),il(j.value),B.value=void 0,X()},ue=d=>{H.value=void 0,Q.value=d!=null?d:g.value,E.value=!0},oe=d=>{H.value=d,Q.value=null,E.value=!0},n=async d=>{await el(d),O(),ml.success(ie("success"))},u=async(d,i)=>{const f=pl(d,b.value,i);await kl(f),await O(),d.forEach(()=>{J.value.toggleRowSelection(b.value)})};return(d,i)=>{const f=y("el-input"),w=y("el-button"),D=y("el-tree"),K=y("el-scrollbar"),le=y("el-aside"),se=y("el-popconfirm"),$=y("el-table-column"),re=y("el-tag"),he=y("el-table"),ye=y("el-main"),be=y("el-container"),de=ol("loading");return m(),h(be,null,{default:a(()=>[e(le,{width:"220px",class:"pr-3"},{default:a(()=>[e(K,{class:"p-2 bg-white rounded-sm"},{default:a(()=>[e(f,{modelValue:r.value,"onUpdate:modelValue":i[0]||(i[0]=t=>r.value=t),"suffix-icon":p(sl),size:"small"},null,8,["modelValue","suffix-icon"]),ne("div",Fl,[e(w,{type:g.value==null?"primary":void 0,link:"",onClick:i[1]||(i[1]=()=>{U.value.setCurrentKey(null),g.value=void 0,ee()})},{default:a(()=>[T(C(d.$t("channel.root")),1)]),_:1},8,["type"])]),Qe(e(D,{ref_key:"channelTree",ref:U,data:I.value,props:{label:"name"},"expand-on-click-node":!1,"node-key":"id","highlight-current":"","filter-node-method":(t,l)=>t?l.name.includes(t):!0,onNodeClick:i[2]||(i[2]=t=>{g.value=t,ee()})},null,8,["data","filter-node-method"]),[[de,R.value]])]),_:1})]),_:1}),e(ye,{class:"p-0"},{default:a(()=>[ne("div",Rl,[e(p(Tl),{params:j.value,onSearch:ee,onReset:fe},{default:a(()=>[e(p(He),{label:d.$t("channel.name"),name:"Q_Contains_name"},null,8,["label"]),e(p(He),{label:d.$t("channel.alias"),name:"Q_Contains_alias"},null,8,["label"])]),_:1},8,["params"])]),ne("div",null,[e(w,{type:"primary",disabled:p(Y)("channel:create"),icon:p(rl),onClick:i[3]||(i[3]=()=>ue(void 0))},{default:a(()=>[T(C(d.$t("add")),1)]),_:1},8,["disabled","icon"]),e(se,{title:d.$t("confirmDelete"),onConfirm:i[4]||(i[4]=()=>n(P.value.map(t=>t.id)))},{reference:a(()=>[e(w,{disabled:P.value.length<=0||p(Y)("channel:delete"),icon:p(dl)},{default:a(()=>[T(C(d.$t("delete")),1)]),_:1},8,["disabled","icon"])]),_:1},8,["title"]),e(Ul,{disabled:P.value.length<=0||F.value||p(Y)("channel:update"),class:"ml-2",onMove:i[5]||(i[5]=t=>u(P.value,t))},null,8,["disabled"]),e(p(Cl),{name:"channel",class:"ml-2"})]),ne("div",Nl,[Qe((m(),h(he,{ref_key:"table",ref:J,"row-key":"id",data:b.value,onSelectionChange:i[6]||(i[6]=t=>P.value=t),onRowDblclick:i[7]||(i[7]=t=>oe(t.id)),onSortChange:ve},{default:a(()=>[e(p(Ml),{name:"channel"},{default:a(()=>[e($,{type:"selection",selectable:N,width:"45"}),e($,{property:"name",label:d.$t("channel.name"),"min-width":"120",sortable:"custom"},null,8,["label"]),e($,{property:"alias",label:d.$t("channel.alias"),"min-width":"80",sortable:"custom"},null,8,["label"]),e($,{property:"channelModel.name",label:d.$t("channel.channelModel"),"sort-by":"channelModel@model-name",display:"none",sortable:"custom","min-width":"60"},null,8,["label"]),e($,{property:"articleModel.name",label:d.$t("channel.articleModel"),"sort-by":"articleModel@model-name",sortable:"custom","min-width":"60"},null,8,["label"]),e($,{property:"processKey",label:d.$t("channel.processKey"),"min-width":"60",sortable:"custom","show-overflow-tooltip":""},{default:a(({row:t})=>{var l;return[T(C(t.processKey!=null?(l=G.value.find(s=>s.key===t.processKey))==null?void 0:l.name:void 0),1)]}),_:1},8,["label"]),e($,{property:"nav",label:d.$t("channel.nav"),"min-width":"40"},{default:a(({row:t})=>[e(re,{type:t.nav?"success":"info",size:"small"},{default:a(()=>[T(C(d.$t(t.nav?"yes":"no")),1)]),_:2},1032,["type"])]),_:1},8,["label"]),e($,{property:"id",label:"ID",width:"170",sortable:"custom"}),e($,{label:d.$t("table.action")},{default:a(({row:t})=>[e(w,{type:"primary",disabled:p(Y)("channel:create")||!N(t),size:"small",link:"",onClick:()=>ue(t)},{default:a(()=>[T(C(d.$t("addChild")),1)]),_:2},1032,["disabled","onClick"]),e(w,{type:"primary",disabled:p(Y)("channel:update"),size:"small",link:"",onClick:()=>oe(t.id)},{default:a(()=>[T(C(d.$t("edit")),1)]),_:2},1032,["disabled","onClick"]),e(se,{title:d.$t("confirmDelete"),onConfirm:()=>n([t.id])},{reference:a(()=>[e(w,{type:"primary",disabled:p(Y)("channel:delete")||!N(t),size:"small",link:""},{default:a(()=>[T(C(d.$t("delete")),1)]),_:2},1032,["disabled"])]),_:2},1032,["title","onConfirm"])]),_:1},8,["label"])]),_:1})]),_:1},8,["data"])),[[de,o.value]])]),e(El,{modelValue:E.value,"onUpdate:modelValue":i[8]||(i[8]=t=>E.value=t),"bean-id":H.value,"bean-ids":M.value,parent:Q.value,onFinished:ce},null,8,["modelValue","bean-id","bean-ids","parent"])]),_:1})]),_:1})}}});export{oa as default};