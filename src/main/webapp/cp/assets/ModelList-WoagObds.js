import{d as j,r as y,b as r,c as _,w as a,h as e,i as te,j as de,P as U,aF as S,aG as O,v as q,x as M,k as d,A as oe,u as ue,B as ce,$ as fe,E as ge,aP as $e,aQ as _e,b6 as Ee,g as k,l as K,m as ne,n as ke,t as ie,z as Ue,O as be,b3 as X,aD as Me,a2 as we,ay as ye,a3 as Ce,bb as qe,b8 as Ie,b9 as Fe,_ as Le,ar as He,o as De,bi as Oe,bs as ze,aW as Be,b5 as A,aZ as Re,bI as Ne,e as We,y as Ae,aO as Ke}from"./index-B_1EBDED.js";import{E as me,a as Pe,b as he}from"./el-radio-group-CsyrtihM.js";/* empty css                        */import{E as Je}from"./el-popover-BPV9oilb.js";import{M as Te,N as Y,O as Qe,P as se,c as je,q as Ge,Q as Ze}from"./config-BqSwhr2s.js";import{a as Xe,_ as Ye,b as xe,c as el}from"./QueryItem.vue_vue_type_script_setup_true_lang-DvrrNKEc.js";import{_ as ll}from"./ListMove.vue_vue_type_script_setup_true_lang-yld2uCIe.js";import{E as pe,a as re,c as al}from"./el-select-C46euWiD.js";/* empty css                 */import{_ as tl}from"./DialogForm.vue_vue_type_script_setup_true_lang-B3BSDHof.js";import{E as Se}from"./el-switch-Dz1XCM2P.js";import{g as ve,m as Z}from"./data-De8R3DQH.js";import{b as dl,E as ol,c as ul,a as nl}from"./el-main-BWaKeUmA.js";import{d as Ve}from"./vuedraggable.umd-DsKFJ5cW.js";import{a as il,_ as ml}from"./FieldItem.vue_vue_type_script_setup_true_lang-5Rnj3Bs-.js";import{C as sl}from"./content-BAV5UQMQ.js";import"./sortable.esm-DEaZq8gs.js";/* empty css                          */import"./Tinymce-DwkXQ6by.js";import"./FileListUpload.vue_vue_type_style_index_0_scoped_b3a09e2e_lang-D6qex1Ks.js";import"./BaseUpload.vue_vue_type_style_index_0_scoped_248c0508_lang-ChzpL2ir.js";import"./BaseUpload-n9vZc6H3.js";import"./position-B7mUaqBz.js";const pl=j({name:"ModelForm",__name:"ModelForm",props:{modelValue:{type:Boolean,required:!0},beanId:{type:String,default:null},beanIds:{type:Array,required:!0},modelType:{type:String,required:!0}},emits:{"update:modelValue":null,finished:null},setup(F){const B=y(),l=y({});return(h,b)=>{const D=de,n=te,o=Pe,E=me,p=re,g=pe;return r(),_(tl,{values:l.value,"onUpdate:values":b[3]||(b[3]=s=>l.value=s),name:h.$t("menu.config.model"),"query-bean":d(se),"create-bean":d(Qe),"update-bean":d(Y),"delete-bean":d(Te),"bean-id":F.beanId,"bean-ids":F.beanIds,focus:B.value,"init-values":()=>({type:F.modelType,scope:0}),"to-values":s=>({...s}),"disable-delete":s=>s.id<=10,perms:"model","model-value":F.modelValue,"onUpdate:modelValue":b[4]||(b[4]=s=>h.$emit("update:modelValue",s)),onFinished:b[5]||(b[5]=()=>h.$emit("finished"))},{default:a(({})=>[e(n,{prop:"name",label:h.$t("model.name"),rules:{required:!0,message:()=>h.$t("v.required")}},{default:a(()=>[e(D,{ref_key:"focus",ref:B,modelValue:l.value.name,"onUpdate:modelValue":b[0]||(b[0]=s=>l.value.name=s),maxlength:"50"},null,8,["modelValue"])]),_:1},8,["label","rules"]),e(n,{prop:"scope",label:h.$t("model.scope"),rules:{required:!0,message:()=>h.$t("v.required")}},{default:a(()=>[e(E,{modelValue:l.value.scope,"onUpdate:modelValue":b[1]||(b[1]=s=>l.value.scope=s),disabled:l.value.id<10},{default:a(()=>[(r(),U(S,null,O([0,2],s=>e(o,{key:s,value:s},{default:a(()=>[q(M(h.$t("model.scope.".concat(s))),1)]),_:2},1032,["value"])),64))]),_:1},8,["modelValue","disabled"])]),_:1},8,["label","rules"]),e(n,{prop:"type",label:h.$t("model.type")},{default:a(()=>[e(g,{modelValue:l.value.type,"onUpdate:modelValue":b[2]||(b[2]=s=>l.value.type=s),disabled:""},{default:a(()=>[e(p,{value:F.modelType,label:h.$t("model.type.".concat(F.modelType))},null,8,["value","label"])]),_:1},8,["modelValue"])]),_:1},8,["label"])]),_:1},8,["values","name","query-bean","create-bean","update-bean","delete-bean","bean-id","bean-ids","focus","init-values","to-values","disable-delete","model-value"])}}}),rl={class:"mt-3"},fl=j({name:"ModelSystemFields",__name:"ModelSystemFields",props:{modelValue:{type:Boolean,required:!0},beanId:{type:String,default:null}},emits:{"update:modelValue":null},setup(F,{emit:B}){const l=F,h=B,{beanId:b,modelValue:D}=oe(l),{t:n}=ue(),o=y({}),E=y(!1),p=y([]),g=y([]);ce(D,async()=>{var i;if(D.value&&b.value){o.value=await se(b.value);const I=ve()[o.value.type];p.value=Z(I.mains.filter(c=>{var v;return fe.epRank>=((v=c.epRank)!=null?v:0)}),o.value.mains,o.value.type),((i=I.asides)==null?void 0:i.length)>0&&(g.value=Z(I.asides.filter(c=>{var v;return fe.epRank>=((v=c.epRank)!=null?v:0)}),o.value.asides,o.value.type))}});const s=async()=>{E.value=!0;try{p.value=p.value.map(i=>({...i,name:i.name===""?null:i.name})),g.value=g.value.map(i=>({...i,name:i.name===""?null:i.name})),await Y({...o.value,mains:JSON.stringify(p.value),asides:JSON.stringify(g.value)}),ie.success(n("success"))}finally{E.value=!1,h("update:modelValue",!1)}},w=()=>{var I;const i=ve()[o.value.type];p.value=Z(i.mains,null,o.value.type),((I=i.asides)==null?void 0:I.length)>0&&(g.value=Z(i.asides,null,o.value.type))};return(i,I)=>{const c=_e,v=de,t=Se,L=re,R=pe,J=Ee,P=$e,$=ne,T=ge,z=Ue;return r(),_(z,{title:i.$t("model.fun.systemFields"),"model-value":F.modelValue,top:"5vh",width:"1024px","onUpdate:modelValue":I[0]||(I[0]=m=>i.$emit("update:modelValue",m))},{default:a(()=>[e(T,null,{default:a(()=>[e(P,{data:p.value},{default:a(()=>[e(c,{prop:"code",label:i.$t("model.field.code"),"min-width":"110"},null,8,["label"]),e(c,{prop:"name",label:i.$t("model.field.name"),"min-width":"140"},{default:a(({row:m})=>[e(v,{modelValue:m.name,"onUpdate:modelValue":u=>m.name=u,placeholder:i.$t(m.label),class:"w-11/12"},null,8,["modelValue","onUpdate:modelValue","placeholder"])]),_:1},8,["label"]),e(c,{prop:"show",label:i.$t("model.field.show")},{default:a(({row:m})=>[e(t,{modelValue:m.show,"onUpdate:modelValue":u=>m.show=u,disabled:m.must},null,8,["modelValue","onUpdate:modelValue","disabled"])]),_:1},8,["label"]),e(c,{prop:"double",label:i.$t("model.field.double")},{default:a(({row:m})=>[e(t,{modelValue:m.double,"onUpdate:modelValue":u=>m.double=u},null,8,["modelValue","onUpdate:modelValue"])]),_:1},8,["label"]),e(c,{prop:"required",label:i.$t("model.field.required")},{default:a(({row:m})=>[e(t,{modelValue:m.required,"onUpdate:modelValue":u=>m.required=u,disabled:m.must},null,8,["modelValue","onUpdate:modelValue","disabled"])]),_:1},8,["label"]),e(c,{label:i.$t("model.field.attribute"),"min-width":"180"},{default:a(({row:m})=>[m.type==="image"?(r(),U(S,{key:0},[e(v,{modelValue:m.imageHeight,"onUpdate:modelValue":u=>m.imageHeight=u,modelModifiers:{number:!0}},{prepend:a(()=>[q(M(i.$t("model.field.imageHeight")),1)]),_:2},1032,["modelValue","onUpdate:modelValue"]),e(v,{modelValue:m.imageWidth,"onUpdate:modelValue":u=>m.imageWidth=u,modelModifiers:{number:!0}},{prepend:a(()=>[q(M(i.$t("model.field.imageWidth")),1)]),_:2},1032,["modelValue","onUpdate:modelValue"]),e(R,{modelValue:m.imageMode,"onUpdate:modelValue":u=>m.imageMode=u,placeholder:i.$t("model.field.imageMode"),class:"w-full"},{default:a(()=>[(r(),U(S,null,O(["manual","cut","resize","none"],u=>e(L,{key:u,value:u,label:i.$t("model.field.imageMode.".concat(u))},null,8,["value","label"])),64))]),_:2},1032,["modelValue","onUpdate:modelValue","placeholder"])],64)):m.type==="imageList"?(r(),U(S,{key:1},[e(v,{modelValue:m.imageMaxHeight,"onUpdate:modelValue":u=>m.imageMaxHeight=u,modelModifiers:{number:!0}},{prepend:a(()=>[q(M(i.$t("model.field.imageMaxHeight")),1)]),_:2},1032,["modelValue","onUpdate:modelValue"]),e(v,{modelValue:m.imageMaxWidth,"onUpdate:modelValue":u=>m.imageMaxWidth=u,modelModifiers:{number:!0}},{prepend:a(()=>[q(M(i.$t("model.field.imageMaxWidth")),1)]),_:2},1032,["modelValue","onUpdate:modelValue"]),e(R,{modelValue:m.imageListType,"onUpdate:modelValue":u=>m.imageListType=u,placeholder:i.$t("model.field.imageListType"),class:"w-full"},{default:a(()=>[(r(),U(S,null,O(["pictureCard","picture"],u=>e(L,{key:u,value:u,label:i.$t("model.field.imageListType.".concat(u))},null,8,["value","label"])),64))]),_:2},1032,["modelValue","onUpdate:modelValue","placeholder"])],64)):m.type==="editor"?(r(),U(S,{key:2},[e(R,{modelValue:m.editorType,"onUpdate:modelValue":u=>m.editorType=u,placeholder:i.$t("model.field.editorType"),class:"w-full"},{default:a(()=>[(r(),U(S,null,O([1,2],u=>e(L,{key:u,value:u,label:i.$t("model.field.editorType.".concat(u))},null,8,["value","label"])),64))]),_:2},1032,["modelValue","onUpdate:modelValue","placeholder"]),e(J,{modelValue:m.editorSwitch,"onUpdate:modelValue":u=>m.editorSwitch=u},{default:a(()=>[q(M(i.$t("model.field.editorSwitch")),1)]),_:2},1032,["modelValue","onUpdate:modelValue"])],64)):k("",!0)]),_:1},8,["label"])]),_:1},8,["data"]),g.value.length>0?(r(),_(P,{key:0,data:g.value,class:"mt-5"},{default:a(()=>[e(c,{prop:"code",label:i.$t("model.field.code"),"min-width":"100"},null,8,["label"]),e(c,{prop:"name",label:i.$t("model.field.name"),"min-width":"120"},{default:a(({row:m})=>[e(v,{modelValue:m.name,"onUpdate:modelValue":u=>m.name=u,placeholder:i.$t(m.label),class:"w-11/12"},null,8,["modelValue","onUpdate:modelValue","placeholder"])]),_:1},8,["label"]),e(c,{prop:"show",label:i.$t("model.field.show")},{default:a(({row:m})=>[e(t,{modelValue:m.show,"onUpdate:modelValue":u=>m.show=u,disabled:m.must},null,8,["modelValue","onUpdate:modelValue","disabled"])]),_:1},8,["label"]),e(c,{prop:"required",label:i.$t("model.field.required")},{default:a(({row:m})=>[e(t,{modelValue:m.required,"onUpdate:modelValue":u=>m.required=u,disabled:m.must},null,8,["modelValue","onUpdate:modelValue","disabled"])]),_:1},8,["label"])]),_:1},8,["data"])):k("",!0),K("div",rl,[e($,{loading:E.value,type:"primary","native-type":"submit",onClick:ke(s,["prevent"])},{default:a(()=>[q(M(i.$t("save")),1)]),_:1},8,["loading"]),e($,{onClick:w},{default:a(()=>[q(M(i.$t("restoreInitialSettings")),1)]),_:1})])]),_:1})]),_:1},8,["title","model-value"])}}}),bl=j({__name:"FieldAttribute",props:{selected:{type:Object,required:!0}},setup(F){const B=F,{selected:l}=oe(B),h=y([]),b=y([]);be(async()=>{l.value.type==="date"&&!l.value.dateType&&(l.value.dateType="date"),l.value.type==="switch"&&!l.value.inactiveValue&&(l.value.inactiveValue="0"),l.value.type==="switch"&&!l.value.activeValue&&(l.value.activeValue="1"),["radio","checkbox"].includes(l.value.type)&&!l.value.checkStyle&&(l.value.checkStyle="default"),["checkbox","multipleSelect"].includes(l.value.type)&&!l.value.defaultValue&&(l.value.defaultValue=[]),["checkbox","multipleSelect"].includes(l.value.type)&&(l.value.multiple=!0),["radio","checkbox","select","multipleSelect"].includes(l.value.type)&&(h.value=await je())}),be(async()=>{l.value.dictTypeId!=null&&(b.value=await sl({typeId:l.value.dictTypeId}))});const D=n=>{var o;l.value.defaultValue=l.value.multiple?[]:void 0,l.value.defaultValueKey=l.value.multiple?[]:void 0,l.value.dataType=((o=h.value.find(E=>E.id===n))==null?void 0:o.dataType)===1?"number":"string"};return(n,o)=>{const E=de,p=te,g=Se,s=al,w=re,i=pe,I=il,c=he,v=me;return r(),U(S,null,[e(p,{prop:"code",label:n.$t("model.field.code"),rules:{required:!0,message:()=>n.$t("v.required")}},{default:a(()=>[e(E,{modelValue:d(l).code,"onUpdate:modelValue":o[0]||(o[0]=t=>d(l).code=t),maxlength:"50"},null,8,["modelValue"])]),_:1},8,["label","rules"]),e(p,{prop:"name",label:n.$t("model.field.name"),rules:{required:!0,message:()=>n.$t("v.required")}},{default:a(()=>[e(E,{modelValue:d(l).name,"onUpdate:modelValue":o[1]||(o[1]=t=>d(l).name=t),maxlength:"50"},null,8,["modelValue"])]),_:1},8,["label","rules"]),["tinyEditor","textarea"].includes(d(l).type)?(r(),_(p,{key:0,prop:"clob",label:n.$t("model.field.clob")},{label:a(()=>[e(X,{message:"model.field.clob",help:""})]),default:a(()=>[e(g,{modelValue:d(l).clob,"onUpdate:modelValue":o[2]||(o[2]=t=>d(l).clob=t)},null,8,["modelValue"])]),_:1},8,["label"])):k("",!0),e(p,{prop:"double",label:n.$t("model.field.double")},{default:a(()=>[e(g,{modelValue:d(l).double,"onUpdate:modelValue":o[3]||(o[3]=t=>d(l).double=t)},null,8,["modelValue"])]),_:1},8,["label"]),["text","textarea","number","date","color","slider","switch","radio","checkbox","select","multipleSelect"].includes(d(l).type)?(r(),_(p,{key:1,prop:"required",label:n.$t("model.field.showInList")},{default:a(()=>[e(g,{modelValue:d(l).showInList,"onUpdate:modelValue":o[4]||(o[4]=t=>d(l).showInList=t)},null,8,["modelValue"])]),_:1},8,["label"])):k("",!0),e(p,{prop:"required",label:n.$t("model.field.required")},{default:a(()=>[e(g,{modelValue:d(l).required,"onUpdate:modelValue":o[5]||(o[5]=t=>d(l).required=t)},null,8,["modelValue"])]),_:1},8,["label"]),["text","textarea","number","select","multipleSelect","videoUpload","audioUpload","fileUpload","tinyEditor"].includes(d(l).type)?(r(),_(p,{key:2,prop:"placeholder",label:n.$t("model.field.placeholder")},{default:a(()=>[e(E,{modelValue:d(l).placeholder,"onUpdate:modelValue":o[6]||(o[6]=t=>d(l).placeholder=t),maxlength:"100"},null,8,["modelValue"])]),_:1},8,["label"])):k("",!0),["text","textarea"].includes(d(l).type)?(r(),U(S,{key:3},[e(p,{prop:"defaultValue",label:n.$t("model.field.defaultValue")},{default:a(()=>[e(E,{modelValue:d(l).defaultValue,"onUpdate:modelValue":o[7]||(o[7]=t=>d(l).defaultValue=t),maxlength:"100"},null,8,["modelValue"])]),_:1},8,["label"]),e(p,{prop:"minlength",label:n.$t("model.field.minlength")},{default:a(()=>[e(s,{modelValue:d(l).minlength,"onUpdate:modelValue":o[8]||(o[8]=t=>d(l).minlength=t),min:1},null,8,["modelValue"])]),_:1},8,["label"]),e(p,{prop:"maxlength",label:n.$t("model.field.maxlength")},{default:a(()=>[e(s,{modelValue:d(l).maxlength,"onUpdate:modelValue":o[9]||(o[9]=t=>d(l).maxlength=t),min:1},null,8,["modelValue"])]),_:1},8,["label"])],64)):k("",!0),["textarea"].includes(d(l).type)?(r(),_(p,{key:4,prop:"rows",label:n.$t("model.field.rows")},{default:a(()=>[e(s,{modelValue:d(l).rows,"onUpdate:modelValue":o[10]||(o[10]=t=>d(l).rows=t)},null,8,["modelValue"])]),_:1},8,["label"])):k("",!0),["number","slider"].includes(d(l).type)?(r(),U(S,{key:5},[e(p,{prop:"defaultValue",label:n.$t("model.field.defaultValue")},{default:a(()=>[e(s,{modelValue:d(l).defaultValue,"onUpdate:modelValue":o[11]||(o[11]=t=>d(l).defaultValue=t),min:d(l).min,max:d(l).max},null,8,["modelValue","min","max"])]),_:1},8,["label"]),e(p,{prop:"min",label:n.$t("model.field.min")},{default:a(()=>[e(s,{modelValue:d(l).min,"onUpdate:modelValue":o[12]||(o[12]=t=>d(l).min=t)},null,8,["modelValue"])]),_:1},8,["label"]),e(p,{prop:"max",label:n.$t("model.field.max")},{default:a(()=>[e(s,{modelValue:d(l).max,"onUpdate:modelValue":o[13]||(o[13]=t=>d(l).max=t)},null,8,["modelValue"])]),_:1},8,["label"]),e(p,{prop:"step",label:n.$t("model.field.step")},{default:a(()=>[e(s,{modelValue:d(l).step,"onUpdate:modelValue":o[14]||(o[14]=t=>d(l).step=t)},null,8,["modelValue"])]),_:1},8,["label"])],64)):k("",!0),["number"].includes(d(l).type)?(r(),_(p,{key:6,prop:"precision",label:n.$t("model.field.precision")},{default:a(()=>[e(s,{modelValue:d(l).precision,"onUpdate:modelValue":o[15]||(o[15]=t=>d(l).precision=t),precision:0,min:0,max:4},null,8,["modelValue"])]),_:1},8,["label"])):k("",!0),["slider"].includes(d(l).type)?(r(),_(p,{key:7,prop:"showInput",label:n.$t("model.field.showInput")},{default:a(()=>[e(g,{modelValue:d(l).showInput,"onUpdate:modelValue":o[16]||(o[16]=t=>d(l).showInput=t)},null,8,["modelValue"])]),_:1},8,["label"])):k("",!0),["date"].includes(d(l).type)?(r(),_(p,{key:8,prop:"dateType",label:n.$t("model.field.dateType")},{default:a(()=>[e(i,{modelValue:d(l).dateType,"onUpdate:modelValue":o[17]||(o[17]=t=>d(l).dateType=t)},{default:a(()=>[(r(),U(S,null,O(["date","datetime"],t=>e(w,{key:t,label:n.$t("model.field.dateType.".concat(t)),value:t},null,8,["label","value"])),64))]),_:1},8,["modelValue"])]),_:1},8,["label"])):k("",!0),["color"].includes(d(l).type)?(r(),_(p,{key:9,prop:"defaultValue",label:n.$t("model.field.defaultValue")},{default:a(()=>[e(I,{modelValue:d(l).defaultValue,"onUpdate:modelValue":o[18]||(o[18]=t=>d(l).defaultValue=t)},null,8,["modelValue"])]),_:1},8,["label"])):k("",!0),["switch"].includes(d(l).type)?(r(),_(p,{key:10,prop:"defaultValue",label:n.$t("model.field.defaultValue")},{default:a(()=>[e(g,{modelValue:d(l).defaultValue,"onUpdate:modelValue":o[19]||(o[19]=t=>d(l).defaultValue=t)},null,8,["modelValue"])]),_:1},8,["label"])):k("",!0),["radio","checkbox"].includes(d(l).type)?(r(),_(p,{key:11,prop:"checkStyle",label:n.$t("model.field.checkStyle")},{default:a(()=>[e(v,{modelValue:d(l).checkStyle,"onUpdate:modelValue":o[20]||(o[20]=t=>d(l).checkStyle=t)},{default:a(()=>[(r(),U(S,null,O(["default","button"],t=>e(c,{key:t,value:t},{default:a(()=>[q(M(n.$t("model.field.checkStyle.".concat(t))),1)]),_:2},1032,["value"])),64))]),_:1},8,["modelValue"])]),_:1},8,["label"])):k("",!0),["select","multipleSelect"].includes(d(l).type)?(r(),_(p,{key:12,prop:"clearable",label:n.$t("model.field.clearable")},{default:a(()=>[e(g,{modelValue:d(l).clearable,"onUpdate:modelValue":o[21]||(o[21]=t=>d(l).clearable=t)},null,8,["modelValue"])]),_:1},8,["label"])):k("",!0),["radio","checkbox","select","multipleSelect"].includes(d(l).type)?(r(),U(S,{key:13},[e(p,{prop:"dictTypeId",label:n.$t("model.field.dictType"),rules:{required:!0,message:()=>n.$t("v.required")}},{default:a(()=>[e(i,{modelValue:d(l).dictTypeId,"onUpdate:modelValue":o[22]||(o[22]=t=>d(l).dictTypeId=t),class:"w-full",onChange:o[23]||(o[23]=t=>D(t))},{default:a(()=>[(r(!0),U(S,null,O(h.value,t=>(r(),_(w,{key:t.id,value:t.id,label:"".concat(t.name,"(").concat(t.alias,")")},null,8,["value","label"]))),128))]),_:1},8,["modelValue"])]),_:1},8,["label","rules"]),e(p,{prop:"dataType",label:n.$t("model.field.dataType")},{default:a(()=>[e(i,{modelValue:d(l).dataType,"onUpdate:modelValue":o[24]||(o[24]=t=>d(l).dataType=t),class:"w-full",disabled:""},{default:a(()=>[(r(),U(S,null,O(["string","number"],t=>e(w,{key:t,value:t,label:n.$t("model.field.dataType.".concat(t))},null,8,["value","label"])),64))]),_:1},8,["modelValue"])]),_:1},8,["label"])],64)):k("",!0),["radio","select"].includes(d(l).type)?(r(),_(p,{key:14,prop:"defaultValue",label:n.$t("model.field.defaultValue")},{default:a(()=>[e(i,{modelValue:d(l).defaultValueKey,"onUpdate:modelValue":o[25]||(o[25]=t=>d(l).defaultValueKey=t),clearable:"",class:"w-full",onChange:o[26]||(o[26]=t=>{var L;return d(l).defaultValue=(L=b.value.find(R=>R.value===t))==null?void 0:L.name})},{default:a(()=>[(r(!0),U(S,null,O(b.value,t=>(r(),_(w,{key:t.id,value:t.value,label:t.name},null,8,["value","label"]))),128))]),_:1},8,["modelValue"])]),_:1},8,["label"])):k("",!0),["checkbox","multipleSelect"].includes(d(l).type)?(r(),_(p,{key:15,prop:"defaultValue",label:n.$t("model.field.defaultValue")},{default:a(()=>[e(i,{modelValue:d(l).defaultValueKey,"onUpdate:modelValue":o[27]||(o[27]=t=>d(l).defaultValueKey=t),clearable:"",class:"w-full",multiple:"",onChange:o[28]||(o[28]=t=>d(l).defaultValue=b.value.filter(L=>t.indexOf(L.value)!==-1).map(L=>L.name))},{default:a(()=>[(r(!0),U(S,null,O(b.value,t=>(r(),_(w,{key:t.id,value:t.value,label:t.name},null,8,["value","label"]))),128))]),_:1},8,["modelValue"])]),_:1},8,["label"])):k("",!0),["imageUpload"].includes(d(l).type)?(r(),U(S,{key:16},[e(p,{prop:"imageWidth",label:n.$t("model.field.imageWidth")},{default:a(()=>[e(s,{modelValue:d(l).imageWidth,"onUpdate:modelValue":o[29]||(o[29]=t=>d(l).imageWidth=t),min:1,max:65535},null,8,["modelValue"])]),_:1},8,["label"]),e(p,{prop:"imageHeight",label:n.$t("model.field.imageHeight")},{default:a(()=>[e(s,{modelValue:d(l).imageHeight,"onUpdate:modelValue":o[30]||(o[30]=t=>d(l).imageHeight=t),min:1,max:65535},null,8,["modelValue"])]),_:1},8,["label"]),e(p,{prop:"imageMode",label:n.$t("model.field.imageMode")},{default:a(()=>[e(i,{modelValue:d(l).imageMode,"onUpdate:modelValue":o[31]||(o[31]=t=>d(l).imageMode=t),clearable:"",class:"w-full"},{default:a(()=>[(r(),U(S,null,O(["cut","resize","manual","none"],t=>e(w,{key:t,value:t,label:n.$t("model.field.imageMode.".concat(t))},null,8,["value","label"])),64))]),_:1},8,["modelValue"])]),_:1},8,["label"])],64)):k("",!0),["imageUpload","videoUpload","audioUpload","fileUpload"].includes(d(l).type)?(r(),U(S,{key:17},[e(p,{prop:"fileAccept"},{label:a(()=>[e(X,{message:"model.field.fileAccept",help:"","fix-width":!1})]),default:a(()=>[e(E,{modelValue:d(l).fileAccept,"onUpdate:modelValue":o[32]||(o[32]=t=>d(l).fileAccept=t),maxlength:"255"},null,8,["modelValue"])]),_:1}),e(p,{prop:"fileMaxSize",rules:{type:"number",min:0,max:65535,message:()=>n.$t("v.range",{min:0,max:65535})}},{label:a(()=>[e(X,{message:"model.field.fileMaxSize",help:"","fix-width":!1})]),default:a(()=>[e(E,{modelValue:d(l).fileMaxSize,"onUpdate:modelValue":o[33]||(o[33]=t=>d(l).fileMaxSize=t),modelModifiers:{number:!0},maxlength:"5"},null,8,["modelValue"])]),_:1},8,["rules"])],64)):k("",!0),["tinyEditor"].includes(d(l).type)?(r(),U(S,{key:18},[e(p,{prop:"minHeight",label:n.$t("model.field.minHeight")},{default:a(()=>[e(s,{modelValue:d(l).minHeight,"onUpdate:modelValue":o[34]||(o[34]=t=>d(l).minHeight=t),min:1,max:65535},null,8,["modelValue"])]),_:1},8,["label"]),e(p,{prop:"maxHeight",label:n.$t("model.field.maxHeight")},{default:a(()=>[e(s,{modelValue:d(l).maxHeight,"onUpdate:modelValue":o[35]||(o[35]=t=>d(l).maxHeight=t),min:1,max:65535},null,8,["modelValue"])]),_:1},8,["label"])],64)):k("",!0)],64)}}}),yl={class:"dialog-full"},vl={class:"drag-component"},Vl=["onClick"],cl=["onClick"],gl=j({name:"ModelCustomFields",__name:"ModelCustomFields",props:{modelValue:{type:Boolean,required:!0},beanId:{type:String,default:null}},emits:{"update:modelValue":null},setup(F,{emit:B}){const l=F,h=B,{beanId:b,modelValue:D}=oe(l),{t:n}=ue(),o=y({}),E=y({}),p=y(),g=y(!1),s=y(),w=y(),i=y("attribute"),I=y(!1),c=y([{label:n("model.fieldType.text"),type:"text"},{label:n("model.fieldType.textarea"),type:"textarea"},{label:n("model.fieldType.number"),type:"number"},{label:n("model.fieldType.slider"),type:"slider"},{label:n("model.fieldType.date"),type:"date"},{label:n("model.fieldType.color"),type:"color"},{label:n("model.fieldType.radio"),type:"radio"},{label:n("model.fieldType.checkbox"),type:"checkbox"},{label:n("model.fieldType.select"),type:"select"},{label:n("model.fieldType.multipleSelect"),type:"multipleSelect"},{label:n("model.fieldType.switch"),type:"switch"},{label:n("model.fieldType.imageUpload"),type:"imageUpload"},{label:n("model.fieldType.videoUpload"),type:"videoUpload"},{label:n("model.fieldType.audioUpload"),type:"audioUpload"},{label:n("model.fieldType.fileUpload"),type:"fileUpload"},{label:n("model.fieldType.tinyEditor"),type:"tinyEditor"}]),v=y([]);ce(D,async()=>{D.value&&(b==null?void 0:b.value)!=null&&(o.value=await se(b.value),v.value=JSON.parse(o.value.customs||"[]"),v.value.length>0?[s.value]=v.value:s.value=void 0)});const t=async $=>{if(!s.value){s.value=$;return}p.value.validate(T=>{T&&(s.value=$)})},L=$=>{const T={code:"field".concat(Date.now()),type:$.type,name:$.label,double:!1,dataType:["number","slider"].indexOf($.type)!==-1?"number":"string",clob:["tinyEditor"].indexOf($.type)!==-1};return w.value=T,T},R=$=>{$.from!==$.to&&(s.value=w.value)},J=$=>{const T=v.value.indexOf($);v.value.splice(T,1);const{length:z}=v.value;z<=0?s.value=void 0:T<z?s.value=v.value[T]:s.value=v.value[z-1]},P=async()=>{I.value=!0;try{v.value.length>0?p.value.validate(async $=>{$&&await Y({...o.value,customs:JSON.stringify(v.value)})}):await Y({...o.value,customs:JSON.stringify(v.value)}),ie.success(n("success"))}finally{I.value=!1,h("update:modelValue",!1)}};return($,T)=>{const z=Me,m=ol,u=ne,V=ul,H=te,N=Ce,x=we,G=ge,W=nl,Q=dl,ee=Fe,le=Ie,f=Ue;return r(),U("div",yl,[e(f,{title:$.$t("model.fun.customFields"),"model-value":F.modelValue,"destroy-on-close":"",fullscreen:"","onUpdate:modelValue":T[3]||(T[3]=C=>$.$emit("update:modelValue",C))},{default:a(()=>[e(Q,{class:"border-t",style:{height:"calc(100vh - 65px)"}},{default:a(()=>[e(m,{width:"240px"},{default:a(()=>[e(z,{class:"h-full"},{default:a(()=>[e(d(Ve),{list:c.value,group:{name:"components",pull:"clone",put:!1},sort:!1,clone:L,"item-key":"label",onEnd:R},{item:a(({element:C})=>[K("div",vl,M(C.label),1)]),_:1},8,["list"])]),_:1})]),_:1}),e(Q,{class:"border-l border-r"},{default:a(()=>[e(V,{height:"auto",class:"px-2 py-1"},{default:a(()=>[e(u,{loading:I.value,type:"primary",onClick:ke(P,["prevent"])},{default:a(()=>[q(M($.$t("save")),1)]),_:1},8,["loading"])]),_:1}),e(W,{class:"p-0 border-t"},{default:a(()=>[e(z,{class:"h-full p-2 drawing-board"},{default:a(()=>[e(G,{model:E.value,"label-width":"150px",class:"h-full"},{default:a(()=>[e(d(Ve),{list:v.value,class:"content-start min-h-full mx-0",tag:"el-row","component-data":{gutter:8},animation:250,group:"components","item-key":"code",onStart:T[0]||(T[0]=()=>g.value=!0),onEnd:T[1]||(T[1]=()=>g.value=!1)},{item:a(({element:C})=>[e(x,{span:C.double?12:24,class:"relative"},{default:a(()=>[e(H,{required:C.required,"show-message":!1,class:"py-3 mb-0"},{label:a(()=>[e(X,{label:C.name},null,8,["label"])]),default:a(()=>[e(ml,{modelValue:C.defaultValue,"onUpdate:modelValue":ae=>C.defaultValue=ae,"model-key":C.defaultValueKey,"onUpdate:modelKey":ae=>C.defaultValueKey=ae,field:C},null,8,["modelValue","onUpdate:modelValue","model-key","onUpdate:modelKey","field"])]),_:2},1032,["required"]),K("div",{class:ye(["drag-mask",!g.value&&s.value!==C?"hover:opacity-10":null,s.value===C?"drag-mask-selected":null]),onClick:()=>t(C)},null,10,Vl),K("div",{class:ye(["drag-close",s.value!==C?"hidden":null]),onClick:()=>J(C)},[e(N,{class:"text-danger"},{default:a(()=>[e(d(qe))]),_:1})],10,cl)]),_:2},1032,["span"])]),_:1},8,["list"])]),_:1},8,["model"])]),_:1})]),_:1})]),_:1}),e(m,{class:"w-64 right-panel"},{default:a(()=>[e(z,{class:"h-full pt-0.5 pb-3"},{default:a(()=>[e(le,{modelValue:i.value,"onUpdate:modelValue":T[2]||(T[2]=C=>i.value=C),stretch:""},{default:a(()=>[e(ee,{label:$.$t("model.attribute"),name:"attribute",class:"px-2"},{default:a(()=>[e(G,{ref_key:"selectedForm",ref:p,model:s.value},{default:a(()=>[s.value?(r(),_(bl,{key:0,selected:s.value},null,8,["selected"])):k("",!0)]),_:1},8,["model"])]),_:1},8,["label"])]),_:1},8,["modelValue"])]),_:1})]),_:1})]),_:1})]),_:1},8,["title","model-value"])])}}}),$l=Le(gl,[["__scopeId","data-v-bca848c2"]]),_l={class:"mb-3"},kl={class:"app-block"},Jl=j({name:"ModelList",__name:"ModelList",setup(F){const{t:B}=ue(),l=y({}),h=y("article"),b=y(),D=y(),n=y([]),o=y([]),E=y(!1),p=y(!1),g=y(!1),s=y(!1),w=y(),i=He(()=>n.value.map(u=>u.id)),I=y(!1),c=async()=>{E.value=!0;try{n.value=await Ge({...Oe(l.value),type:h.value,Q_OrderBy:b.value}),I.value=Object.values(l.value).filter(u=>u!==void 0&&u!=="").length>0||b.value!==void 0}finally{E.value=!1}};De(c);const v=({column:u,prop:V,order:H})=>{var N;V&&H?b.value=((N=u.sortBy)!=null?N:V)+(H==="descending"?"_desc":""):b.value=void 0,c()},t=()=>c(),L=()=>{D.value.clearSort(),ze(l.value),b.value=void 0,c()},R=u=>{w.value=u,g.value=!0},J=u=>{w.value=u,s.value=!0},P=()=>{w.value=void 0,p.value=!0},$=u=>{w.value=u,p.value=!0},T=async u=>{await Te(u),c(),ie.success(B("success"))},z=async(u,V)=>{const H=Ne(u,n.value,V);await Ze(H.map(N=>N.id))},m=u=>u.id>10;return(u,V)=>{const H=ne,N=Je,x=he,G=me,W=_e,Q=Ke,ee=$e,le=Ae;return r(),U("div",null,[K("div",_l,[e(d(Ye),{params:l.value,onSearch:t,onReset:L},{default:a(()=>[e(d(Xe),{label:u.$t("model.name"),name:"Q_Contains_name"},null,8,["label"])]),_:1},8,["params"])]),K("div",null,[e(H,{type:"primary",disabled:d(A)("model:create"),icon:d(Be),onClick:V[0]||(V[0]=()=>P())},{default:a(()=>[q(M(u.$t("add")),1)]),_:1},8,["disabled","icon"]),e(N,{title:u.$t("confirmDelete"),onConfirm:V[1]||(V[1]=()=>T(o.value.map(f=>f.id)))},{reference:a(()=>[e(H,{disabled:o.value.length<=0||d(A)("model:delete"),icon:d(Re)},{default:a(()=>[q(M(u.$t("delete")),1)]),_:1},8,["disabled","icon"])]),_:1},8,["title"]),e(ll,{disabled:o.value.length<=0||I.value||d(A)("org:update"),class:"ml-2",onMove:V[2]||(V[2]=f=>z(o.value,f))},null,8,["disabled"]),e(d(xe),{name:"model",class:"ml-2"})]),e(G,{modelValue:h.value,"onUpdate:modelValue":V[3]||(V[3]=f=>h.value=f),class:"mt-3",onChange:V[4]||(V[4]=()=>c())},{default:a(()=>[(r(),U(S,null,O(["article","channel","form","site","global"],f=>e(x,{key:f,value:f},{default:a(()=>[q(M(u.$t("model.type.".concat(f))),1)]),_:2},1032,["value"])),64))]),_:1},8,["modelValue"]),K("div",kl,[We((r(),_(ee,{ref_key:"table",ref:D,data:n.value,onSelectionChange:V[5]||(V[5]=f=>o.value=f),onRowDblclick:V[6]||(V[6]=f=>$(f.id)),onSortChange:v},{default:a(()=>[e(d(el),{name:"model"},{default:a(()=>[e(W,{type:"selection",selectable:m,width:"45"}),e(W,{property:"id",label:"ID",width:"170",sortable:"custom"}),e(W,{property:"name",label:u.$t("model.name"),sortable:"custom","show-overflow-tooltip":""},null,8,["label"]),e(W,{property:"type",label:u.$t("model.type"),sortable:"custom",formatter:f=>u.$t("model.type.".concat(f.type))},null,8,["label","formatter"]),e(W,{property:"scope",label:u.$t("model.scope"),sortable:"custom"},{default:a(({row:f})=>[f.scope===2?(r(),_(Q,{key:0,type:"success",size:"small"},{default:a(()=>[q(M(u.$t("model.scope.".concat(f.scope))),1)]),_:2},1024)):(r(),_(Q,{key:1,type:"info",size:"small"},{default:a(()=>[q(M(u.$t("model.scope.".concat(f.scope))),1)]),_:2},1024))]),_:1},8,["label"]),e(W,{label:u.$t("table.action")},{default:a(({row:f})=>[e(H,{type:"primary",disabled:d(A)("model:update"),size:"small",link:"",onClick:()=>$(f.id)},{default:a(()=>[q(M(u.$t("edit")),1)]),_:2},1032,["disabled","onClick"]),["form","global","site"].includes(f.type)?k("",!0):(r(),_(H,{key:0,type:"primary",disabled:d(A)("model:update"),size:"small",link:"",onClick:()=>R(f.id)},{default:a(()=>[q(M(u.$t("model.fun.systemFields")),1)]),_:2},1032,["disabled","onClick"])),e(H,{type:"primary",disabled:d(A)("model:update"),size:"small",link:"",onClick:()=>J(f.id)},{default:a(()=>[q(M(u.$t("model.fun.customFields")),1)]),_:2},1032,["disabled","onClick"]),m(f)?(r(),_(N,{key:1,title:u.$t("confirmDelete"),onConfirm:()=>T([f.id])},{reference:a(()=>[e(H,{type:"primary",disabled:d(A)("model:delete"),size:"small",link:""},{default:a(()=>[q(M(u.$t("delete")),1)]),_:1},8,["disabled"])]),_:2},1032,["title","onConfirm"])):k("",!0)]),_:1},8,["label"])]),_:1})]),_:1},8,["data"])),[[le,E.value]])]),e(pl,{modelValue:p.value,"onUpdate:modelValue":V[7]||(V[7]=f=>p.value=f),"bean-id":w.value,"bean-ids":i.value,"model-type":h.value,onFinished:c},null,8,["modelValue","bean-id","bean-ids","model-type"]),e(fl,{modelValue:g.value,"onUpdate:modelValue":V[8]||(V[8]=f=>g.value=f),"bean-id":w.value},null,8,["modelValue","bean-id"]),e($l,{modelValue:s.value,"onUpdate:modelValue":V[9]||(V[9]=f=>s.value=f),"bean-id":w.value},null,8,["modelValue","bean-id"])])}}});export{Jl as default};