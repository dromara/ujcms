import{d as G,p as Y,r,v as Z,a as g,c as B,e as R,w as u,i as o,h as a,g as M,u as x,a1 as ee,o as ae,b as le,I as te,j as T,f as oe,av as ne,aB as se,E as de,aC as ue,k as I,t as q,aa as re,a7 as ie,ah as S,aq as me}from"./index-0b297134.js";import{b as pe,t as P,f as ve,e as be}from"./tree-73f11865.js";import{q as K,x as fe,y as ce,z as ge,A as H,B as ye}from"./user-a8d75470.js";import{a as N,_ as _e,b as $e,c as we}from"./QueryItem.vue_vue_type_script_setup_true_lang-a71c7d8e.js";import{_ as Ve}from"./ListMove.vue_vue_type_script_setup_true_lang-05d5e01c.js";import{_ as he}from"./DialogForm.vue_vue_type_script_setup_true_lang-99772bc8.js";const ke={name:"OrgForm"},Ce=G({...ke,props:{modelValue:{type:Boolean,required:!0},beanId:{type:Number,default:null},beanIds:{type:Array,required:!0},parentId:{type:Number,required:!0},showGlobalData:{type:Boolean,required:!0}},emits:{"update:modelValue":null,finished:null},setup(U,{emit:E}){const V=U,{parentId:y,beanId:D,showGlobalData:v,modelValue:c}=Y(V),h=r(),d=r({}),f=r([]),O=async n=>{f.value=pe(P(await K({current:!v.value})),n==null?void 0:n.id)},k=async n=>{await O(n),E("finished")};return Z(c,()=>{c.value&&O()}),(n,t)=>{const L=g("el-tree-select"),i=g("el-form-item"),C=g("el-input");return B(),R(he,{values:d.value,"onUpdate:values":t[5]||(t[5]=p=>d.value=p),name:n.$t("menu.user.org"),"query-bean":o(fe),"create-bean":o(ce),"update-bean":o(ge),"delete-bean":o(H),"bean-id":o(D),"bean-ids":U.beanIds,focus:h.value,"init-values":p=>({parentId:o(y)}),"to-values":p=>({...p}),"disable-delete":p=>{var _;return p.id<=1||p.id===((_=f.value[0])==null?void 0:_.id)},perms:"org","model-value":U.modelValue,"onUpdate:modelValue":t[6]||(t[6]=p=>n.$emit("update:modelValue",p)),onFinished:k,onBeanChange:t[7]||(t[7]=()=>O())},{default:u(({isEdit:p})=>{var _;return[!p||d.value.id!==((_=f.value[0])==null?void 0:_.id)?(B(),R(i,{key:0,prop:"parentId",label:n.$t("org.parent"),rules:{required:!0,message:()=>n.$t("v.required")}},{default:u(()=>[a(L,{modelValue:d.value.parentId,"onUpdate:modelValue":t[0]||(t[0]=m=>d.value.parentId=m),data:f.value,"node-key":"id",props:{label:"name",disabled:"disabled"},"default-expanded-keys":f.value.map(m=>m.id),"render-after-expand":!1,"check-strictly":"",class:"w-full"},null,8,["modelValue","data","default-expanded-keys"])]),_:1},8,["label","rules"])):M("",!0),a(i,{prop:"name",label:n.$t("org.name"),rules:{required:!0,message:()=>n.$t("v.required")}},{default:u(()=>[a(C,{ref_key:"focus",ref:h,modelValue:d.value.name,"onUpdate:modelValue":t[1]||(t[1]=m=>d.value.name=m),maxlength:"50"},null,8,["modelValue"])]),_:1},8,["label","rules"]),a(i,{prop:"address",label:n.$t("org.address")},{default:u(()=>[a(C,{modelValue:d.value.address,"onUpdate:modelValue":t[2]||(t[2]=m=>d.value.address=m),maxlength:"255"},null,8,["modelValue"])]),_:1},8,["label"]),a(i,{prop:"phone",label:n.$t("org.phone")},{default:u(()=>[a(C,{modelValue:d.value.phone,"onUpdate:modelValue":t[3]||(t[3]=m=>d.value.phone=m),maxlength:"100"},null,8,["modelValue"])]),_:1},8,["label"]),a(i,{prop:"contacts",label:n.$t("org.contacts")},{default:u(()=>[a(C,{modelValue:d.value.contacts,"onUpdate:modelValue":t[4]||(t[4]=m=>d.value.contacts=m),maxlength:"100"},null,8,["modelValue"])]),_:1},8,["label"])]}),_:1},8,["values","name","query-bean","create-bean","update-bean","delete-bean","bean-id","bean-ids","focus","init-values","to-values","disable-delete","model-value"])}}}),Ie={class:"mb-3"},qe={class:"mt-3 app-block"},De={name:"OrgList"},Ee=G({...De,setup(U){const{t:E}=x(),V=r({}),y=r(),D=r(),v=r([]),c=r([]),h=r(!1),d=r(!1),f=r(),O=ee(()=>v.value.map(e=>e.id)),k=r(!1),n=r(1),t=r(!1),L=r(["1"]),i=async()=>{var e;h.value=!0;try{v.value=await K({...ne(V.value),current:!t.value,Q_OrderBy:y.value}),k.value=Object.values(V.value).filter(l=>l!==void 0&&l!=="").length>0||y.value!==void 0,k.value||(v.value=P(v.value)),n.value=(e=v.value[0])==null?void 0:e.id}finally{h.value=!1}};ae(i);const C=({column:e,prop:l,order:b})=>{var $;l&&b?y.value=(($=e.sortBy)!=null?$:l)+(b==="descending"?"_desc":""):y.value=void 0,i()},p=()=>i(),_=()=>{D.value.clearSort(),se(V.value),y.value=void 0,i()},m=e=>{f.value=void 0,e!=null&&(n.value=e),d.value=!0},z=e=>{f.value=e,d.value=!0},j=async e=>{console.log(e),await H(e),i(),de.success(E("success"))},A=e=>e.id>1,J=async(e,l)=>{const b=ue(e,ve(v.value),l);await ye(b),await i(),e.forEach($=>{D.value.toggleRowSelection(be(v.value,Q=>Q.id===$.id))})};return(e,l)=>{const b=g("el-button"),$=g("el-popconfirm"),Q=g("el-checkbox"),w=g("el-table-column"),W=g("el-table"),X=le("loading");return B(),te("div",null,[T("div",Ie,[a(o(_e),{params:V.value,onSearch:p,onReset:_},{default:u(()=>[a(o(N),{label:e.$t("org.name"),name:"Q_Contains_name"},null,8,["label"]),a(o(N),{label:e.$t("org.address"),name:"Q_Contains_address"},null,8,["label"]),a(o(N),{label:e.$t("org.phone"),name:"Q_Contains_phone"},null,8,["label"]),a(o(N),{label:e.$t("org.contacts"),name:"Q_Contains_contacts"},null,8,["label"])]),_:1},8,["params"])]),T("div",null,[a(b,{type:"primary",icon:o(re),onClick:l[0]||(l[0]=()=>m())},{default:u(()=>[I(q(e.$t("add")),1)]),_:1},8,["icon"]),a($,{title:e.$t("confirmDelete"),onConfirm:l[1]||(l[1]=()=>j(c.value.map(s=>s.id)))},{reference:u(()=>[a(b,{disabled:c.value.length<=0,icon:o(ie)},{default:u(()=>[I(q(e.$t("delete")),1)]),_:1},8,["disabled","icon"])]),_:1},8,["title"]),a(Ve,{class:"ml-2",disabled:c.value.length<=0||k.value||o(S)("org:update"),onMove:l[2]||(l[2]=s=>J(c.value,s))},null,8,["disabled"]),o(me).globalPermission?(B(),R(Q,{key:0,modelValue:t.value,"onUpdate:modelValue":l[3]||(l[3]=s=>t.value=s),class:"ml-2 align-middle",label:e.$t("globalData"),border:!0,onChange:l[4]||(l[4]=()=>i())},null,8,["modelValue","label"])):M("",!0),a(o($e),{name:"org",class:"ml-2"})]),T("div",qe,[oe((B(),R(W,{ref_key:"table",ref:D,"row-key":"id",data:v.value,"expand-row-keys":L.value,onSelectionChange:l[5]||(l[5]=s=>c.value=s),onRowDblclick:l[6]||(l[6]=s=>z(s.id)),onSortChange:C},{default:u(()=>[a(o(we),{name:"org"},{default:u(()=>[a(w,{type:"selection",selectable:A,width:"45"}),a(w,{property:"name",label:e.$t("org.name"),sortable:"custom","min-width":"120","show-overflow-tooltip":""},{default:u(({row:s})=>{var F;return[I(q(k.value?(F=s.names)==null?void 0:F.join(" / "):s.name),1)]}),_:1},8,["label"]),a(w,{property:"address",label:e.$t("org.address"),sortable:"custom",display:"none","min-width":"100","show-overflow-tooltip":""},null,8,["label"]),a(w,{property:"phone",label:e.$t("org.phone"),sortable:"custom","min-width":"100","show-overflow-tooltip":""},null,8,["label"]),a(w,{property:"contacts",label:e.$t("org.contacts"),sortable:"custom","show-overflow-tooltip":""},null,8,["label"]),a(w,{property:"id",label:"ID",width:"64",sortable:"custom"}),a(w,{label:e.$t("table.action"),width:"160"},{default:u(({row:s})=>[a(b,{type:"primary",disabled:o(S)("org:create"),size:"small",link:"",onClick:()=>m(s.id)},{default:u(()=>[I(q(e.$t("addChild")),1)]),_:2},1032,["disabled","onClick"]),a(b,{type:"primary",disabled:o(S)("org:update"),size:"small",link:"",onClick:()=>z(s.id)},{default:u(()=>[I(q(e.$t("edit")),1)]),_:2},1032,["disabled","onClick"]),a($,{title:e.$t("confirmDelete"),onConfirm:()=>j([s.id])},{reference:u(()=>[a(b,{type:"primary",disabled:!A(s)||o(S)("org:delete"),size:"small",link:""},{default:u(()=>[I(q(e.$t("delete")),1)]),_:2},1032,["disabled"])]),_:2},1032,["title","onConfirm"])]),_:1},8,["label"])]),_:1})]),_:1},8,["data","expand-row-keys"])),[[X,h.value]])]),a(Ce,{modelValue:d.value,"onUpdate:modelValue":l[7]||(l[7]=s=>d.value=s),"bean-id":f.value,"bean-ids":O.value,"parent-id":n.value,"show-global-data":t.value,onFinished:i},null,8,["modelValue","bean-id","bean-ids","parent-id","show-global-data"])])}}});export{Ee as default};