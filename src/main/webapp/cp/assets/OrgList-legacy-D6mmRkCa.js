System.register(["./index-legacy-DywSgIaj.js","./sortable.esm-legacy-xUcotENm.js","./tree-legacy-D1Cv8xKE.js","./user-legacy-LBZ1IdSp.js","./QueryItem.vue_vue_type_script_setup_true_lang-legacy-Byo3duh5.js","./DialogForm.vue_vue_type_script_setup_true_lang-legacy-C3VGk7HF.js","./data-legacy-BFsWLfCW.js","./content-legacy-C0HQ2q9C.js"],(function(e,l){"use strict";var a,t,d,n,o,u,i,s,r,c,v,m,p,b,g,f,h,y,k,_,V,w,C,$,x,I,U,P,q,D,S,j,E,A,B,F,O,T,N,z,K,G,Q,R,L,H,J,M,W,X,Y,Z,ee,le,ae,te,de,ne;return{setters:[e=>{a=e.d,t=e.p,d=e.r,n=e.v,o=e.b,u=e.e,i=e.f,s=e.w,r=e.i,c=e.h,v=e.j,m=e.u,p=e.V,b=e.aa,g=e.o,f=e.k,h=e.n,y=e.t,k=e.E,_=e.aJ,V=e.aA,w=e.aB,C=e.c,$=e.aK,x=e.aL,I=e.g,U=e.U,P=e.ae,q=e.ai,D=e.aO,S=e.aq,j=e.aP,E=e.af,A=e.aD,B=e.aI},e=>{F=e.S},e=>{O=e.c,T=e.t,N=e.e,z=e.f},e=>{K=e.b,G=e.z,Q=e.A,R=e.B,L=e.C,H=e.D,J=e.E,M=e.F,W=e.G,X=e.H,Y=e.I},e=>{Z=e._,ee=e.a,le=e.b,ae=e.c},e=>{te=e._},e=>{de=e.b},e=>{ne=e.d}],execute:function(){var l=document.createElement("style");l.textContent=".sortable-chosen td{border-top-width:2px;--tw-border-opacity: 1;border-top-color:rgb(243 209 158 / var(--tw-border-opacity, 1))}\n/*$vite$:1*/",document.head.appendChild(l);const oe=a({name:"OrgForm",__name:"OrgForm",props:{modelValue:{type:Boolean,required:!0},beanId:{type:String,default:null},beanIds:{type:Array,required:!0},parentId:{type:String,required:!0},showGlobalData:{type:Boolean,required:!0}},emits:{"update:modelValue":null,finished:null},setup(e,{emit:l}){const a=e,{parentId:m,beanId:p,showGlobalData:b,modelValue:g}=t(a),f=l,h=d(),y=d({}),k=d([]),_=async e=>{k.value=O(T(await K({current:!b.value})),e?.id)},V=async e=>{await _(e),f("finished")};return n(g,(()=>{g.value&&_()})),(l,a)=>{const t=o("el-tree-select"),d=o("el-form-item"),n=o("el-input");return u(),i(te,{values:y.value,"onUpdate:values":a[5]||(a[5]=e=>y.value=e),name:l.$t("menu.user.org"),"query-bean":v(L),"create-bean":v(R),"update-bean":v(Q),"delete-bean":v(G),"bean-id":v(p),"bean-ids":e.beanIds,focus:h.value,"init-values":e=>({parentId:v(m)}),"to-values":e=>({...e}),"disable-delete":e=>e.id<=1||e.id===k.value[0]?.id,perms:"org","model-value":e.modelValue,"onUpdate:modelValue":a[6]||(a[6]=e=>l.$emit("update:modelValue",e)),onFinished:V,onBeanChange:a[7]||(a[7]=()=>_())},{default:s((({isEdit:e})=>[e&&y.value.id===k.value[0]?.id?c("",!0):(u(),i(d,{key:0,prop:"parentId",label:l.$t("org.parent"),rules:{required:!0,message:()=>l.$t("v.required")}},{default:s((()=>[r(t,{modelValue:y.value.parentId,"onUpdate:modelValue":a[0]||(a[0]=e=>y.value.parentId=e),data:k.value,"node-key":"id",props:{label:"name",disabled:"disabled"},"default-expanded-keys":k.value.map((e=>e.id)),"render-after-expand":!1,disabled:e,"check-strictly":"",class:"w-full"},null,8,["modelValue","data","default-expanded-keys","disabled"])])),_:2},1032,["label","rules"])),r(d,{prop:"name",label:l.$t("org.name"),rules:{required:!0,message:()=>l.$t("v.required")}},{default:s((()=>[r(n,{ref_key:"focus",ref:h,modelValue:y.value.name,"onUpdate:modelValue":a[1]||(a[1]=e=>y.value.name=e),maxlength:"50"},null,8,["modelValue"])])),_:1},8,["label","rules"]),r(d,{prop:"address",label:l.$t("org.address")},{default:s((()=>[r(n,{modelValue:y.value.address,"onUpdate:modelValue":a[2]||(a[2]=e=>y.value.address=e),maxlength:"255"},null,8,["modelValue"])])),_:1},8,["label"]),r(d,{prop:"phone",label:l.$t("org.phone")},{default:s((()=>[r(n,{modelValue:y.value.phone,"onUpdate:modelValue":a[3]||(a[3]=e=>y.value.phone=e),maxlength:"100"},null,8,["modelValue"])])),_:1},8,["label"]),r(d,{prop:"contacts",label:l.$t("org.contacts")},{default:s((()=>[r(n,{modelValue:y.value.contacts,"onUpdate:modelValue":a[4]||(a[4]=e=>y.value.contacts=e),maxlength:"100"},null,8,["modelValue"])])),_:1},8,["label"])])),_:1},8,["values","name","query-bean","create-bean","update-bean","delete-bean","bean-id","bean-ids","focus","init-values","to-values","disable-delete","model-value"])}}}),ue={class:"border-t"},ie={class:"border-t"},se={class:"border-t"},re={class:"flex items-center justify-between"},ce=a({name:"OrgPermissionForm",__name:"OrgPermissionForm",props:{modelValue:{type:Boolean,required:!0},beanId:{type:String,default:null},showGlobalData:{type:Boolean,required:!0}},emits:{"update:modelValue":null,finished:null},setup(e,{emit:l}){const a=e,c=l,{beanId:_,modelValue:V}=t(a),{t:w}=m(),C=d("articlePermission"),$=d(),x=d({}),I=d({}),U=d(!1),P=d(!1),q=d(!1),D=d(),S=d(!1),j=d(!1),E=d(),A=d(!1),B=d(!1),F=d(),O=de();N(O,p.grantPermissions??[]);const G=d([]),Q=d([]),R=b((()=>x.value.global&&!p.globalPermission||p.rank>x.value.rank));n(V,(async()=>{V.value&&((async()=>{null!=_.value&&(x.value=await L(_.value),I.value={...x.value})})(),(async()=>{if(null!=_.value){const e=await H(_.value);D.value?.setCheckedKeys([]),e.forEach((e=>{D.value?.setChecked(e,!0,!1)}))}})(),(async()=>{if(null!=_.value){const e=await J(_.value);E.value?.setCheckedKeys([]),e.forEach((e=>{E.value?.setChecked(e,!0,!1)}))}})(),(async()=>{if(null!=_.value){const e=await M(_.value,a.showGlobalData);F.value?.setCheckedKeys([]),e.forEach((e=>{F.value?.setChecked(e,!0,!1)}))}})())})),g((()=>{(async()=>{G.value=T(await ne())})(),(async()=>{Q.value=T(await K({current:!0}))})()}));const X=(e,l,a,t)=>{a.forEach((a=>{a.children&&(l.getNode(a[t]).expanded=e,X(e,l,a.children,t))}))},Y=(e,l,a,t)=>{l.setCheckedKeys(e?a.map((e=>e[t])):[])},Z=()=>{null!=D.value&&(I.value.articlePermissions=[...D.value.getCheckedNodes(),...D.value.getHalfCheckedNodes()].map((e=>e.id)))},ee=()=>{null!=E.value&&(I.value.channelPermissions=[...E.value.getCheckedNodes(),...E.value.getHalfCheckedNodes()].map((e=>e.id)))},le=()=>{null!=F.value&&(I.value.orgPermissions=F.value.getCheckedNodes().map((e=>e.id)))};return(l,a)=>{const t=o("el-checkbox"),d=o("el-tree"),n=o("el-tab-pane"),m=o("el-tabs"),p=o("el-form"),b=o("el-tag"),g=o("el-button"),_=o("el-drawer");return u(),i(_,{title:l.$t("permissionSettings"),"with-header":!1,"model-value":e.modelValue,size:576,"onUpdate:modelValue":a[18]||(a[18]=e=>l.$emit("update:modelValue",e))},{default:s((()=>[r(p,{ref_key:"form",ref:$,model:I.value,disabled:R.value,"label-width":"150px"},{default:s((()=>[r(m,{modelValue:C.value,"onUpdate:modelValue":a[15]||(a[15]=e=>C.value=e)},{default:s((()=>[r(n,{label:l.$t("org.articlePermission"),name:"articlePermission"},{default:s((()=>[f("div",ue,[r(t,{modelValue:P.value,"onUpdate:modelValue":a[0]||(a[0]=e=>P.value=e),label:l.$t("expand/collapse"),onChange:a[1]||(a[1]=e=>X(e,D.value,G.value,"id"))},null,8,["modelValue","label"]),r(t,{modelValue:q.value,"onUpdate:modelValue":a[2]||(a[2]=e=>q.value=e),label:l.$t("checkAll/uncheckAll"),onChange:a[3]||(a[3]=e=>{Y(e,D.value,v(z)(G.value),"id"),Z()})},null,8,["modelValue","label"])]),r(d,{ref_key:"articlePermissionTree",ref:D,data:G.value,"node-key":"id",props:{label:"name"},class:"border rounded","show-checkbox":"",onCheck:a[4]||(a[4]=()=>Z())},null,8,["data"])])),_:1},8,["label"]),r(n,{label:l.$t("role.channelPermission"),name:"channelPermission"},{default:s((()=>[f("div",ie,[r(t,{modelValue:S.value,"onUpdate:modelValue":a[5]||(a[5]=e=>S.value=e),label:l.$t("expand/collapse"),onChange:a[6]||(a[6]=e=>X(e,E.value,G.value,"id"))},null,8,["modelValue","label"]),r(t,{modelValue:j.value,"onUpdate:modelValue":a[7]||(a[7]=e=>j.value=e),label:l.$t("checkAll/uncheckAll"),onChange:a[8]||(a[8]=e=>{Y(e,E.value,v(z)(G.value),"id"),ee()})},null,8,["modelValue","label"])]),r(d,{ref_key:"channelPermissionTree",ref:E,data:G.value,"node-key":"id",props:{label:"name"},class:"border rounded","check-strictly":"","show-checkbox":"",onCheck:a[9]||(a[9]=()=>ee())},null,8,["data"])])),_:1},8,["label"]),r(n,{label:l.$t("org.orgPermission"),name:"orgPermission"},{default:s((()=>[f("div",se,[r(t,{modelValue:A.value,"onUpdate:modelValue":a[10]||(a[10]=e=>A.value=e),label:l.$t("expand/collapse"),onChange:a[11]||(a[11]=e=>X(e,F.value,Q.value,"id"))},null,8,["modelValue","label"]),r(t,{modelValue:B.value,"onUpdate:modelValue":a[12]||(a[12]=e=>B.value=e),label:l.$t("checkAll/uncheckAll"),onChange:a[13]||(a[13]=e=>{Y(e,F.value,v(z)(Q.value),"id"),le()})},null,8,["modelValue","label"])]),r(d,{ref_key:"orgPermissionTree",ref:F,data:Q.value,"node-key":"id",props:{label:"name"},class:"border rounded","default-expanded-keys":Q.value.map((e=>e.id)),"show-checkbox":"","check-strictly":"",onCheck:a[14]||(a[14]=()=>le())},null,8,["data","default-expanded-keys"])])),_:1},8,["label"])])),_:1},8,["modelValue"])])),_:1},8,["model","disabled"])])),footer:s((()=>[f("div",re,[f("div",null,[r(b,null,{default:s((()=>[h(y(I.value?.name),1)])),_:1})]),f("div",null,[r(g,{onClick:a[16]||(a[16]=()=>l.$emit("update:modelValue",!1))},{default:s((()=>[h(y(l.$t("cancel")),1)])),_:1}),r(g,{type:"primary",loading:U.value,disabled:R.value,onClick:a[17]||(a[17]=()=>{$.value.validate((async e=>{if(e){U.value=!0;try{Z(),ee(),le(),await W(I.value),c("finished"),c("update:modelValue",!1),k.success(w("success"))}finally{U.value=!1}}}))})},{default:s((()=>[h(y(l.$t("save")),1)])),_:1},8,["loading","disabled"])])])])),_:1},8,["title","model-value"])}}}),ve={class:"flex justify-between mx-2 mt-1"},me={class:"mb-3"},pe={class:"mt-3 app-block"};e("default",a({name:"OrgList",__name:"OrgList",setup(e){const{t:l}=m(),a=d({}),t=d(),O=d(),N=d([]),Q=d([]),R=d(!1),L=d(!1),H=d(!1),J=d(),M=b((()=>z(N.value).map((e=>e.id)))),W=d("1"),te=d(!1),de=d(),ne=d(!1),ue=d(!1),ie=d(),se=d([]),re=d(!1),be=d(),ge=d();n(be,(e=>{ie.value.filter(e)}));const fe=async()=>{re.value=!0;try{se.value=T(await K({current:!te.value})),_((()=>{null!=be.value&&ie.value.filter(be.value),ie.value.setCurrentKey(ge.value?.id)}))}finally{re.value=!1}},he=async()=>{R.value=!0;try{ne.value=void 0!==t.value;const e=Object.values(a.value).filter((e=>void 0!==e&&""!==e)).length>0;N.value=await K({...V(a.value),parentId:ge.value?.id,current:!te.value,isIncludeSelf:!0,isIncludeChildren:e,Q_OrderBy:t.value}),e||ne.value?de.value=void 0:(N.value=T(N.value),de.value=N.value.map((e=>e.id))),W.value=N.value[0]?.id}finally{R.value=!1}},ye=()=>{fe(),he()};let ke;g((()=>{ye(),(()=>{const e=document.querySelector("#dataTable .el-table__body-wrapper tbody");ke=F.create(e,{handle:".drag-handle",draggable:".drag-draggable",animation:200,chosenClass:"sortable-chosen",onEnd:async function(e){const{oldIndex:a,newIndex:t}=e;if(a!==t){let e=a,d=t,n=N.value;const o=n[0]?.children;o?.length>0&&(n=o,e-=1,d-=1),await X(n[e].id,n[d].id,e>d?"before":"after"),fe(),n.splice(d,0,n.splice(e,1)[0]),k.success(l("success"))}}})})()})),w((()=>{void 0!==ke&&ke.destroy()}));const _e=({column:e,prop:l,order:a})=>{t.value=l&&a?(e.sortBy??l)+("descending"===a?"_desc":""):void 0,ye()},Ve=()=>he(),we=()=>{O.value.clearSort(),B(a.value),t.value=void 0,ye()},Ce=e=>{J.value=void 0,null!=e&&(W.value=e),L.value=!0},$e=e=>{J.value=e,L.value=!0},xe=async e=>{await G(e),ye(),k.success(l("success"))},Ie=e=>e.id>1,Ue=async(e,l,a)=>{"none"!==a&&(await X(e.data.id,l.data.id,a),he())},Pe=async e=>{ue.value?(ge.value!=e.parent&&(ge.value=e.parent,await he()),$e(e.id)):(ge.value=e,Ve())},qe=(e,l)=>!e||l.name.includes(e),De=()=>{ie.value.setCurrentKey(null),ge.value=void 0,Ve()},Se=e=>-1===se.value.findIndex((l=>l.id===e.data.id)),je=(e,l)=>-1===se.value.findIndex((e=>e.id===l.data.id));return(e,t)=>{const d=o("el-input"),n=o("el-button"),m=o("el-switch"),b=o("el-tooltip"),g=o("el-tree"),_=o("el-scrollbar"),V=o("el-aside"),w=o("el-popconfirm"),B=o("el-icon"),F=o("el-checkbox"),T=o("el-table-column"),z=o("el-table"),K=o("el-main"),G=o("el-container"),X=C("loading");return u(),i(G,null,{default:s((()=>[r(V,{width:"220px",class:"pr-3"},{default:s((()=>[r(_,{class:"p-2 bg-white rounded-sm"},{default:s((()=>[r(d,{modelValue:be.value,"onUpdate:modelValue":t[0]||(t[0]=e=>be.value=e),"suffix-icon":v($),size:"small"},null,8,["modelValue","suffix-icon"]),f("div",ve,[r(n,{type:null==ge.value?"primary":void 0,link:"",onClick:De},{default:s((()=>[h(y(e.$t("org.root")),1)])),_:1},8,["type"]),r(b,{content:e.$t("editMode"),placement:"top"},{default:s((()=>[r(m,{modelValue:ue.value,"onUpdate:modelValue":t[1]||(t[1]=e=>ue.value=e),"active-action-icon":v(x),"inactive-action-icon":v(x)},null,8,["modelValue","active-action-icon","inactive-action-icon"])])),_:1},8,["content"])]),I(r(g,{ref_key:"orgTree",ref:ie,data:se.value,props:{label:"name"},"default-expanded-keys":de.value,"expand-on-click-node":!1,"node-key":"id","highlight-current":"",draggable:v(U)("org:update"),"allow-drag":Se,"allow-drop":je,"filter-node-method":qe,onNodeClick:Pe,onNodeDragEnd:Ue},null,8,["data","default-expanded-keys","draggable"]),[[X,re.value]])])),_:1})])),_:1}),r(K,{class:"p-0"},{default:s((()=>[f("div",me,[r(v(Z),{params:a.value,onSearch:Ve,onReset:we},{default:s((()=>[r(v(ee),{label:e.$t("org.name"),name:"Q_Contains_name"},null,8,["label"]),r(v(ee),{label:e.$t("org.address"),name:"Q_Contains_address"},null,8,["label"]),r(v(ee),{label:e.$t("org.phone"),name:"Q_Contains_phone"},null,8,["label"]),r(v(ee),{label:e.$t("org.contacts"),name:"Q_Contains_contacts"},null,8,["label"])])),_:1},8,["params"])]),f("div",null,[r(n,{type:"primary",icon:v(P),onClick:t[2]||(t[2]=()=>Ce())},{default:s((()=>[h(y(e.$t("add")),1)])),_:1},8,["icon"]),r(w,{title:e.$t("confirmDelete"),onConfirm:t[3]||(t[3]=()=>xe(Q.value.map((e=>e.id))))},{reference:s((()=>[r(n,{disabled:Q.value.length<=0,icon:v(q)},{default:s((()=>[h(y(e.$t("delete")),1)])),_:1},8,["disabled","icon"])])),_:1},8,["title"]),r(n,{disabled:v(S)("org:tidyTree"),icon:v(D),onClick:t[4]||(t[4]=()=>(async()=>{await Y(),ye(),k.success(l("success"))})())},{default:s((()=>[h(y(e.$t("tidyTree")),1)])),_:1},8,["disabled","icon"]),r(b,{placement:"top",content:e.$t("tidyTree.tooltip")},{default:s((()=>[r(B,{class:"ml-1 text-base align-text-bottom"},{default:s((()=>[r(v(j))])),_:1})])),_:1},8,["content"]),v(p).globalPermission?(u(),i(F,{key:0,modelValue:te.value,"onUpdate:modelValue":t[5]||(t[5]=e=>te.value=e),class:"ml-2 align-middle",label:e.$t("globalData"),border:!0,onChange:t[6]||(t[6]=()=>ye())},null,8,["modelValue","label"])):c("",!0),r(v(le),{name:"org",class:"ml-2"})]),f("div",pe,[I((u(),i(z,{id:"dataTable",ref_key:"table",ref:O,"row-key":"id","default-expand-all":"",data:N.value,"row-class-name":({row:e})=>e.children?.length>0||"0"===e.id||"1"===e.id?void 0:"drag-draggable",onSelectionChange:t[7]||(t[7]=e=>Q.value=e),onRowDblclick:t[8]||(t[8]=e=>$e(e.id)),onSortChange:_e},{default:s((()=>[r(v(ae),{name:"org"},{default:s((()=>[r(T,{type:"selection",selectable:Ie,width:"45"}),r(T,{property:"name",label:e.$t("org.name"),sortable:"custom","min-width":"120"},null,8,["label"]),r(T,{property:"address",label:e.$t("org.address"),sortable:"custom",display:"none","min-width":"100"},null,8,["label"]),r(T,{property:"phone",label:e.$t("org.phone"),sortable:"custom","min-width":"100"},null,8,["label"]),r(T,{property:"contacts",label:e.$t("org.contacts"),sortable:"custom"},null,8,["label"]),r(T,{property:"id",label:"ID",width:"170",sortable:"custom"}),r(T,{width:"42"},{default:s((({row:e})=>[r(B,{class:E(["text-lg align-middle",ne.value||v(S)("org:update")||e.children?.length>0||"0"===e.id||"1"===e.id?["cursor-not-allowed","text-gray-disabled"]:["cursor-move","text-gray-secondary","drag-handle"]])},{default:s((()=>[r(v(A))])),_:2},1032,["class"])])),_:1}),r(T,{label:e.$t("table.action"),width:"230"},{default:s((({row:l})=>[r(n,{type:"primary",disabled:v(S)("org:create"),size:"small",link:"",onClick:()=>Ce(l.id)},{default:s((()=>[h(y(e.$t("addChild")),1)])),_:2},1032,["disabled","onClick"]),r(n,{type:"primary",disabled:v(S)("org:update"),size:"small",link:"",onClick:()=>$e(l.id)},{default:s((()=>[h(y(e.$t("edit")),1)])),_:2},1032,["disabled","onClick"]),v(p).epRank>=3||v(p).epDisplay?(u(),i(n,{key:0,title:v(p).epRank<3?e.$t("error.enterprise.short"):void 0,disabled:v(S)("org:updatePermission")||v(p).epRank<3,type:"primary",size:"small",link:"",onClick:()=>{return e=l.id,J.value=e,void(H.value=!0);var e}},{default:s((()=>[h(y(e.$t("permissionSettings")),1)])),_:2},1032,["title","disabled","onClick"])):c("",!0),r(w,{title:e.$t("confirmDelete"),onConfirm:()=>xe([l.id])},{reference:s((()=>[r(n,{type:"primary",disabled:!Ie(l)||v(S)("org:delete"),size:"small",link:""},{default:s((()=>[h(y(e.$t("delete")),1)])),_:2},1032,["disabled"])])),_:2},1032,["title","onConfirm"])])),_:1},8,["label"])])),_:1})])),_:1},8,["data","row-class-name"])),[[X,R.value]])]),r(oe,{modelValue:L.value,"onUpdate:modelValue":t[9]||(t[9]=e=>L.value=e),"bean-id":J.value,"bean-ids":M.value,"parent-id":W.value,"show-global-data":te.value,onFinished:ye},null,8,["modelValue","bean-id","bean-ids","parent-id","show-global-data"]),r(ce,{modelValue:H.value,"onUpdate:modelValue":t[10]||(t[10]=e=>H.value=e),"bean-id":J.value,"show-global-data":te.value,onFinished:ye},null,8,["modelValue","bean-id","show-global-data"])])),_:1})])),_:1})}}}))}}}));