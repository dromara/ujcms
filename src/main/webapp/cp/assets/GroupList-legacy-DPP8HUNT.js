System.register(["./index-legacy-DywSgIaj.js","./user-legacy-LBZ1IdSp.js","./QueryItem.vue_vue_type_script_setup_true_lang-legacy-Byo3duh5.js","./ListMove.vue_vue_type_script_setup_true_lang-legacy-TXxGRnhs.js","./DialogForm.vue_vue_type_script_setup_true_lang-legacy-C3VGk7HF.js","./tree-legacy-D1Cv8xKE.js","./content-legacy-C0HQ2q9C.js"],(function(e,l){"use strict";var a,t,u,o,s,d,n,i,r,p,c,m,v,b,f,y,g,_,h,V,k,$,w,C,P,A,U,x,j,q,I,S,F,G,D,z,L,R,B,E,K,N,Q,M,O,T,H;return{setters:[e=>{a=e.d,t=e.r,u=e.b,o=e.e,s=e.f,d=e.w,n=e.i,i=e.ao,r=e.K,p=e.a7,c=e.a8,m=e.j,v=e.p,b=e.u,f=e.aa,y=e.V,g=e.v,_=e.o,h=e.k,V=e.n,k=e.t,$=e.E,w=e.h,C=e.aA,P=e.c,A=e.aI,U=e.ae,x=e.aq,j=e.ai,q=e.aY,I=e.g},e=>{S=e.J,F=e.K,G=e.L,D=e.M,z=e.N,L=e.O,R=e.d,B=e.P},e=>{E=e.a,K=e._,N=e.b,Q=e.c},e=>{M=e._},e=>{O=e._},e=>{T=e.t},e=>{H=e.d}],execute:function(){const l=a({name:"GroupForm",__name:"GroupForm",props:{modelValue:{type:Boolean,required:!0},beanId:{type:String,default:null},beanIds:{type:Array,required:!0}},emits:{"update:modelValue":null,finished:null},setup(e){const l=t(),a=t({});return(t,v)=>{const b=u("el-input"),f=u("el-form-item"),y=u("el-switch"),g=u("el-option"),_=u("el-select");return o(),s(O,{values:a.value,"onUpdate:values":v[4]||(v[4]=e=>a.value=e),name:t.$t("menu.user.group"),"query-bean":m(D),"create-bean":m(G),"update-bean":m(F),"delete-bean":m(S),"bean-id":e.beanId,"bean-ids":e.beanIds,focus:l.value,"init-values":()=>({type:2,allAccessPermission:!0}),"to-values":e=>({...e}),"disable-delete":e=>e.id<=10,perms:"group","model-value":e.modelValue,"onUpdate:modelValue":v[5]||(v[5]=e=>t.$emit("update:modelValue",e)),onFinished:v[6]||(v[6]=()=>t.$emit("finished"))},{default:d((({})=>[n(f,{prop:"name",label:t.$t("group.name"),rules:{required:!0,message:()=>t.$t("v.required")}},{default:d((()=>[n(b,{ref_key:"focus",ref:l,modelValue:a.value.name,"onUpdate:modelValue":v[0]||(v[0]=e=>a.value.name=e),maxlength:"50"},null,8,["modelValue"])])),_:1},8,["label","rules"]),n(f,{prop:"description",label:t.$t("group.description")},{default:d((()=>[n(b,{modelValue:a.value.description,"onUpdate:modelValue":v[1]||(v[1]=e=>a.value.description=e),maxlength:"255"},null,8,["modelValue"])])),_:1},8,["label"]),n(f,{prop:"allAccessPermission"},{label:d((()=>[n(i,{message:"group.allAccessPermission"})])),default:d((()=>[n(y,{modelValue:a.value.allAccessPermission,"onUpdate:modelValue":v[2]||(v[2]=e=>a.value.allAccessPermission=e)},null,8,["modelValue"])])),_:1}),n(f,{prop:"type",label:t.$t("group.type"),rules:{required:!0,message:()=>t.$t("v.required")}},{default:d((()=>[n(_,{modelValue:a.value.type,"onUpdate:modelValue":v[3]||(v[3]=e=>a.value.type=e),disabled:1===a.value.type},{default:d((()=>[(o(),r(p,null,c([1,2],(e=>n(g,{key:e,disabled:1===e,label:t.$t(`group.type.${e}`),value:e},null,8,["disabled","label","value"]))),64))])),_:1},8,["modelValue","disabled"])])),_:1},8,["label","rules"])])),_:1},8,["values","name","query-bean","create-bean","update-bean","delete-bean","bean-id","bean-ids","focus","to-values","disable-delete","model-value"])}}}),J={class:"border-t"},Y={class:"flex items-center justify-between"},W=a({name:"GroupPermissionForm",__name:"GroupPermissionForm",props:{modelValue:{type:Boolean,required:!0},beanId:{type:String,default:null}},emits:{"update:modelValue":null,finished:null},setup(e,{emit:l}){const a=e,c=l,{beanId:m,modelValue:C}=v(a),{t:P}=b(),A=t(),U=t({}),x=t({}),j=t(!1),q=t(!0),I=t(!1),S=t(),F=t([]),G=f((()=>y.rank>U.value.rank));g(C,(()=>{C.value&&((async()=>{null!=m?.value&&(U.value=await D(m.value),x.value={...U.value})})(),(async()=>{if(null!=m?.value){const e=await z(m.value);S.value?.setCheckedKeys([]),e.forEach((e=>{S.value?.setChecked(e,!0,!1)}))}})())})),_((()=>{(async()=>{F.value=T(await H())})()}));const R=(e,l,a,t)=>{a.forEach((a=>{a.children&&(l.getNode(a[t]).expanded=e,R(e,l,a.children,t))}))},B=()=>{null!=S.value&&(x.value.accessPermissions=[...S.value.getCheckedNodes(),...S.value.getHalfCheckedNodes()].map((e=>e.id)))};return(l,a)=>{const t=u("el-switch"),m=u("el-form-item"),v=u("el-checkbox"),b=u("el-tree"),f=u("el-form"),y=u("el-tag"),g=u("el-button"),_=u("el-drawer");return o(),s(_,{title:l.$t("permissionSettings"),"model-value":e.modelValue,size:414,"onUpdate:modelValue":a[8]||(a[8]=e=>l.$emit("update:modelValue",e))},{default:d((()=>[n(f,{ref_key:"form",ref:A,model:x.value,disabled:G.value,"label-width":"150px"},{default:d((()=>[n(m,{prop:"allAccessPermission"},{label:d((()=>[n(i,{message:"group.allAccessPermission",help:""})])),default:d((()=>[n(t,{modelValue:x.value.allAccessPermission,"onUpdate:modelValue":a[0]||(a[0]=e=>x.value.allAccessPermission=e)},null,8,["modelValue"])])),_:1}),x.value.allAccessPermission?w("",!0):(o(),r(p,{key:0},[h("div",J,[n(v,{modelValue:q.value,"onUpdate:modelValue":a[1]||(a[1]=e=>q.value=e),label:l.$t("expand/collapse"),onChange:a[2]||(a[2]=e=>R(e,S.value,F.value,"id"))},null,8,["modelValue","label"]),n(v,{modelValue:I.value,"onUpdate:modelValue":a[3]||(a[3]=e=>I.value=e),label:l.$t("checkAll/uncheckAll"),onChange:a[4]||(a[4]=e=>{((e,l,a,t)=>{l.setCheckedKeys(e?a.map((e=>e[t])):[])})(e,S.value,F.value,"id"),B()})},null,8,["modelValue","label"])]),n(b,{ref_key:"accessPermissionTree",ref:S,data:F.value,"node-key":"id",props:{label:"name"},class:"border rounded","default-expand-all":"","show-checkbox":"",onCheck:a[5]||(a[5]=()=>B())},null,8,["data"])],64))])),_:1},8,["model","disabled"])])),footer:d((()=>[h("div",Y,[h("div",null,[n(y,null,{default:d((()=>[V(k(x.value?.name),1)])),_:1})]),h("div",null,[n(g,{onClick:a[6]||(a[6]=()=>l.$emit("update:modelValue",!1))},{default:d((()=>[V(k(l.$t("cancel")),1)])),_:1}),n(g,{type:"primary",loading:j.value,disabled:G.value,onClick:a[7]||(a[7]=()=>{A.value.validate((async e=>{if(e){j.value=!0;try{await L(x.value),c("finished"),c("update:modelValue",!1),$.success(P("success"))}finally{j.value=!1}}}))})},{default:d((()=>[V(k(l.$t("save")),1)])),_:1},8,["loading","disabled"])])])])),_:1},8,["title","model-value"])}}}),X={class:"mb-3"},Z={class:"mt-3 app-block"};e("default",a({name:"GroupList",__name:"GroupList",setup(e){const{t:a}=b(),i=t({}),p=t(),c=t(),v=t([]),g=t([]),F=t(!1),G=t(!1),D=t(!1),z=t(),L=f((()=>v.value.map((e=>e.id)))),O=t(!1),T=async()=>{F.value=!0;try{v.value=await R({...C(i.value),Q_OrderBy:p.value}),O.value=Object.values(i.value).filter((e=>void 0!==e&&""!==e)).length>0||void 0!==p.value}finally{F.value=!1}};_(T);const H=({column:e,prop:l,order:a})=>{p.value=l&&a?(e.sortBy??l)+("descending"===a?"_desc":""):void 0,T()},J=()=>T(),Y=()=>{c.value.clearSort(),A(i.value),p.value=void 0,T()},ee=()=>{z.value=void 0,G.value=!0},le=e=>{z.value=e,G.value=!0},ae=e=>e>10,te=async e=>{const l=e.filter((e=>ae(e)));l.length>0&&(await S(l),T(),$.success(a("success")))};return(e,a)=>{const t=u("el-button"),p=u("el-popconfirm"),b=u("el-table-column"),f=u("el-tag"),_=u("el-table"),$=P("loading");return o(),r("div",null,[h("div",X,[n(m(K),{params:i.value,onSearch:J,onReset:Y},{default:d((()=>[n(m(E),{label:e.$t("group.name"),name:"Q_Contains_name"},null,8,["label"]),n(m(E),{label:e.$t("group.description"),name:"Q_Contains_description"},null,8,["label"])])),_:1},8,["params"])]),h("div",null,[n(t,{type:"primary",disabled:m(x)("group:create"),icon:m(U),onClick:ee},{default:d((()=>[V(k(e.$t("add")),1)])),_:1},8,["disabled","icon"]),n(p,{title:e.$t("confirmDelete"),onConfirm:a[0]||(a[0]=()=>te(g.value.map((e=>e.id))))},{reference:d((()=>[n(t,{disabled:g.value.filter((e=>ae(e.id))).length<=0||m(x)("group:delete"),icon:m(j)},{default:d((()=>[V(k(e.$t("delete")),1)])),_:1},8,["disabled","icon"])])),_:1},8,["title"]),n(M,{class:"ml-2",disabled:g.value.length<=0||O.value||m(x)("org:update"),onMove:a[1]||(a[1]=e=>(async(e,l)=>{const a=q(e,v.value,l);await B(a.map((e=>e.id)))})(g.value,e))},null,8,["disabled"]),n(m(N),{name:"group",class:"ml-2"})]),h("div",Z,[I((o(),s(_,{ref_key:"table",ref:c,data:v.value,onSelectionChange:a[2]||(a[2]=e=>g.value=e),onRowDblclick:a[3]||(a[3]=e=>le(e.id)),onSortChange:H},{default:d((()=>[n(m(Q),{name:"group"},{default:d((()=>[n(b,{type:"selection",width:"50"}),n(b,{property:"id",label:"ID",width:"170",sortable:"custom"}),n(b,{property:"name",label:e.$t("group.name"),sortable:"custom","show-overflow-tooltip":""},null,8,["label"]),n(b,{property:"description",label:e.$t("group.description"),"min-width":"150",sortable:"custom","show-overflow-tooltip":""},null,8,["label"]),n(b,{property:"allAccessPermission",label:e.$t("group.allAccessPermission"),sortable:"custom"},{default:d((({row:l})=>[n(f,{type:l.allAccessPermission?"success":"info",size:"small"},{default:d((()=>[V(k(e.$t(l.allAccessPermission?"yes":"no")),1)])),_:2},1032,["type"])])),_:1},8,["label"]),n(b,{property:"type",label:e.$t("group.type"),sortable:"custom","show-overflow-tooltip":"",formatter:l=>e.$t(`group.type.${l.type}`)},null,8,["label","formatter"]),n(b,{label:e.$t("table.action")},{default:d((({row:l})=>[n(t,{type:"primary",disabled:m(x)("group:update"),size:"small",link:"",onClick:e=>le(l.id)},{default:d((()=>[V(k(e.$t("edit")),1)])),_:2},1032,["disabled","onClick"]),m(y).epRank>0||m(y).epDisplay?(o(),s(t,{key:0,type:"primary",disabled:m(x)("group:updatePermission")||m(y).epRank<=0,title:m(y).epRank<=0?e.$t("error.enterprise.short"):void 0,size:"small",link:"",onClick:()=>{return e=l.id,z.value=e,void(D.value=!0);var e}},{default:d((()=>[V(k(e.$t("permissionSettings")),1)])),_:2},1032,["disabled","title","onClick"])):w("",!0),n(p,{title:e.$t("confirmDelete"),onConfirm:()=>te([l.id])},{reference:d((()=>[n(t,{type:"primary",disabled:!ae(l.id)||m(x)("group:delete"),size:"small",link:""},{default:d((()=>[V(k(e.$t("delete")),1)])),_:2},1032,["disabled"])])),_:2},1032,["title","onConfirm"])])),_:1},8,["label"])])),_:1})])),_:1},8,["data"])),[[$,F.value]])]),n(l,{modelValue:G.value,"onUpdate:modelValue":a[4]||(a[4]=e=>G.value=e),"bean-id":z.value,"bean-ids":L.value,onFinished:T},null,8,["modelValue","bean-id","bean-ids"]),n(W,{modelValue:D.value,"onUpdate:modelValue":a[5]||(a[5]=e=>D.value=e),"bean-id":z.value,onFinished:T},null,8,["modelValue","bean-id"])])}}}))}}}));