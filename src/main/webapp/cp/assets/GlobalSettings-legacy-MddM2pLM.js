System.register(["./index-legacy-DZmnsqrR.js","./el-main-legacy-DkzIab54.js","./el-select-legacy-CXLjUU8_.js","./el-tree-legacy-B3cjMHEb.js","./el-tree-select-legacy-B32kpIHX.js","./el-radio-group-legacy-MGjCXhtG.js","./el-radio-button-legacy-C-wGwFOO.js","./el-divider-legacy-D7l6vpEP.js","./el-switch-legacy-PInRcY4o.js","./tree-legacy-D1Cv8xKE.js","./system-legacy-CGT-gNJ7.js","./config-legacy-pRs5ckn-.js","./FieldItem.vue_vue_type_script_setup_true_lang-legacy-BAJ00c5d.js","./el-checkbox-group-legacy-CFEJ959H.js","./el-radio-legacy-DPwh4a3j.js","./content-legacy-BEocrn8N.js","./Tinymce-legacy-UzfhaFpG.js","./FileListUpload.vue_vue_type_style_index_0_scoped_b3a09e2e_lang-legacy-BgppLoJB.js","./BaseUpload.vue_vue_type_style_index_0_scoped_248c0508_lang-legacy-Drm3lV15.js","./vuedraggable.umd-legacy-BVZV7bQD.js","./sortable.esm-legacy-xUcotENm.js","./BaseUpload-legacy-CUatelAi.js","./position-legacy-CrJ_VAEp.js"],(function(e,l){"use strict";var a,u,s,t,d,o,r,n,m,p,i,g,v,f,c,V,_,y,b,h,x,w,q,U,$,k,M,L,S,A,T,I,j,K,R,D,E,C,N,P,z,W,H,F,B,G,O,Z,J,Q,X,Y,ee,le,ae,ue,se,te,de,oe,re,ne,me,pe,ie,ge,ve,fe,ce,Ve,_e,ye;return{setters:[e=>{a=e.d,u=e.u,s=e.a,t=e.r,d=e.ar,o=e.Z,r=e.$,n=e.B,m=e.o,p=e.b,i=e.c,g=e.w,v=e.h,f=e.b8,c=e.P,V=e.aF,_=e.aG,y=e.b9,b=e.e,h=e.E,x=e.a6,w=e.a2,q=e.i,U=e.j,$=e.b3,k=e.k,M=e.f,L=e.l,S=e.g,A=e.v,T=e.x,I=e.m,j=e.n,K=e.t,R=e.b5,D=e.U,E=e.y,C=e._},e=>{N=e.E,P=e.a,z=e.b},e=>{W=e.c,H=e.E,F=e.a},null,e=>{B=e.E},e=>{G=e.E,O=e.b},null,e=>{Z=e.E},e=>{J=e.E},e=>{Q=e.t},e=>{X=e.d},e=>{Y=e.j,ee=e.k,le=e.l,ae=e.m,ue=e.n,se=e.o,te=e.p,de=e.s,oe=e.r,re=e.t,ne=e.v,me=e.w,pe=e.x,ie=e.y,ge=e.z,ve=e.A,fe=e.B,ce=e.C,Ve=e.D,_e=e.E},e=>{ye=e._},null,null,null,null,null,null,null,null,null,null],execute:function(){var l=document.createElement("style");l.textContent=".el-tabs[data-v-b0c4ffbd] .el-tabs__header{margin-right:1px}.el-tabs[data-v-b0c4ffbd] .el-tabs__content{flex-grow:0}\n/*$vite$:1*/",document.head.appendChild(l);const be=["innerHTML"],he={key:9};e("default",C(a({name:"GlobalSettings",__name:"GlobalSettings",setup(e){const{t:l}=u(),a=s(),C=t(),xe=t({}),we=t({}),qe=t(!1),Ue=t(!1),$e=t([]),ke=t(),Me=d((()=>JSON.parse(ke.value?.customs||"[]"))),Le=[];o("config:base:update")&&Le.push("base"),o("config:upload:update")&&Le.push("upload"),o("config:register:update")&&Le.push("register"),o("config:security:update")&&(r.epRank>=1||r.epDisplay)&&Le.push("security"),o("config:sms:update")&&Le.push("sms"),o("config:email:update")&&Le.push("email"),o("config:uploadStorage:update")&&Le.push("uploadStorage"),o("config:htmlStorage:update")&&Le.push("htmlStorage"),o("config:templateStorage:update")&&Le.push("templateStorage"),o("config:customs:update")&&Le.push("customs");const Se=t(Le[0]),Ae=async()=>{"upload"===Se.value?we.value=xe.value.upload:"register"===Se.value?we.value=xe.value.register:"security"===Se.value?we.value=xe.value.security:"customs"===Se.value?we.value=xe.value.customs:"sms"===Se.value?we.value=await Y():"email"===Se.value?we.value=await ee():"uploadStorage"===Se.value?we.value=await le():"htmlStorage"===Se.value?we.value=await ae():"templateStorage"===Se.value?we.value=await ue():we.value=xe.value};n(Se,(()=>Ae()));const Te=async()=>{ke.value=await te()},Ie=async()=>{xe.value=await se()},je=async()=>{$e.value=Q(await X())};m((async()=>{qe.value=!0;try{await Promise.all([Ie(),Te(),je()]),Ae()}finally{qe.value=!1}}));const Ke=async()=>{qe.value=!0;try{await Ie()}finally{qe.value=!1}},Re=e=>{const l=a.base.uploadsExtensionBlacklist.split(","),u=e.split(",");return l.findIndex((e=>u.includes(e)))>=0};return(e,u)=>{const s=y,t=f,d=N,o=U,n=q,m=w,Q=W,X=x,Y=J,ee=F,le=H,ae=M,ue=Z,se=O,te=G,xe=B,ke=I,Ae=h,Te=P,Ie=z,je=E;return p(),i(Ie,null,{default:g((()=>[v(d,{width:"180px",class:"pr-3"},{default:g((()=>[v(t,{modelValue:Se.value,"onUpdate:modelValue":u[0]||(u[0]=e=>Se.value=e),"tab-position":"left",stretch:"",class:"bg-white"},{default:g((()=>[(p(),c(V,null,_(Le,(l=>v(s,{key:l,name:l,label:e.$t(`config.settings.${l}`)},null,8,["name","label"]))),64))])),_:1},8,["modelValue"])])),_:1}),v(Te,{class:"p-3 app-block"},{default:g((()=>[b((p(),i(Ae,{ref_key:"form",ref:C,model:we.value,"label-width":"160px"},{default:g((()=>["upload"===Se.value?(p(),i(X,{key:0},{default:g((()=>[v(m,{span:12},{default:g((()=>[v(n,{prop:"imageTypes",rules:[{required:!0,message:()=>e.$t("v.required")},{validator:(l,a,u)=>{Re(a)?u(e.$t("config.upload.error.extensionNotAllowd")):u()}}]},{label:g((()=>[v($,{message:"config.upload.imageTypes",help:""})])),default:g((()=>[v(o,{modelValue:we.value.imageTypes,"onUpdate:modelValue":u[1]||(u[1]=e=>we.value.imageTypes=e),maxlength:"255"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"imageLimit",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.upload.imageLimit",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.imageLimit,"onUpdate:modelValue":u[2]||(u[2]=e=>we.value.imageLimit=e),min:0,max:65535},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"videoTypes",rules:[{required:!0,message:()=>e.$t("v.required")},{validator:(l,a,u)=>{Re(a)?u(e.$t("config.upload.error.extensionNotAllowd")):u()}}]},{label:g((()=>[v($,{message:"config.upload.videoTypes",help:""})])),default:g((()=>[v(o,{modelValue:we.value.videoTypes,"onUpdate:modelValue":u[3]||(u[3]=e=>we.value.videoTypes=e),maxlength:"255"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"videoLimit",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.upload.videoLimit",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.videoLimit,"onUpdate:modelValue":u[4]||(u[4]=e=>we.value.videoLimit=e),min:0,max:65535},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"audioTypes",rules:[{required:!0,message:()=>e.$t("v.required")},{validator:(l,a,u)=>{Re(a)?u(e.$t("config.upload.error.extensionNotAllowd")):u()}}]},{label:g((()=>[v($,{message:"config.upload.audioTypes",help:""})])),default:g((()=>[v(o,{modelValue:we.value.audioTypes,"onUpdate:modelValue":u[5]||(u[5]=e=>we.value.audioTypes=e),maxlength:"255"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"audioLimit",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.upload.audioLimit",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.audioLimit,"onUpdate:modelValue":u[6]||(u[6]=e=>we.value.audioLimit=e),min:0,max:65535},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"libraryTypes",rules:[{required:!0,message:()=>e.$t("v.required")},{validator:(l,a,u)=>{Re(a)?u(e.$t("config.upload.error.extensionNotAllowd")):u()}}]},{label:g((()=>[v($,{message:"config.upload.libraryTypes",help:""})])),default:g((()=>[v(o,{modelValue:we.value.libraryTypes,"onUpdate:modelValue":u[7]||(u[7]=e=>we.value.libraryTypes=e),maxlength:"255"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"libraryLimit",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.upload.libraryLimit",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.libraryLimit,"onUpdate:modelValue":u[8]||(u[8]=e=>we.value.libraryLimit=e),min:0,max:65535},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"docTypes",rules:[{required:!0,message:()=>e.$t("v.required")},{validator:(l,a,u)=>{Re(a)?u(e.$t("config.upload.error.extensionNotAllowd")):u()}}]},{label:g((()=>[v($,{message:"config.upload.docTypes",help:""})])),default:g((()=>[v(o,{modelValue:we.value.docTypes,"onUpdate:modelValue":u[9]||(u[9]=e=>we.value.docTypes=e),maxlength:"255"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"docLimit",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.upload.docLimit",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.docLimit,"onUpdate:modelValue":u[10]||(u[10]=e=>we.value.docLimit=e),min:0,max:65535},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"fileTypes",rules:[{required:!0,message:()=>e.$t("v.required")},{validator:(l,a,u)=>{Re(a)?u(e.$t("config.upload.error.extensionNotAllowd")):u()}}]},{label:g((()=>[v($,{message:"config.upload.fileTypes",help:""})])),default:g((()=>[v(o,{modelValue:we.value.fileTypes,"onUpdate:modelValue":u[11]||(u[11]=e=>we.value.fileTypes=e),maxlength:"255"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"fileLimit",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.upload.fileLimit",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.fileLimit,"onUpdate:modelValue":u[12]||(u[12]=e=>we.value.fileLimit=e),min:0,max:65535},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"imageMaxWidth",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.upload.imageMaxWidth",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.imageMaxWidth,"onUpdate:modelValue":u[13]||(u[13]=e=>we.value.imageMaxWidth=e),min:0,max:65535},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"imageMaxHeight",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.upload.imageMaxHeight",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.imageMaxHeight,"onUpdate:modelValue":u[14]||(u[14]=e=>we.value.imageMaxHeight=e),min:0,max:65535},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1})])),_:1})):"register"===Se.value?(p(),i(X,{key:1},{default:g((()=>[v(m,{span:12},{default:g((()=>[v(n,{prop:"enabled",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.register.enabled"})])),default:g((()=>[v(Y,{modelValue:we.value.enabled,"onUpdate:modelValue":u[15]||(u[15]=e=>we.value.enabled=e)},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"verifyMode",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.register.verifyMode"})])),default:g((()=>[v(le,{modelValue:we.value.verifyMode,"onUpdate:modelValue":u[16]||(u[16]=e=>we.value.verifyMode=e)},{default:g((()=>[(p(),c(V,null,_([1,2,3,4],(e=>v(ee,{key:e,label:k(l)(`config.register.verifyMode.${e}`),value:e},null,8,["label","value"]))),64))])),_:1},8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"usernameMinLength",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.register.usernameMinLength"})])),default:g((()=>[v(Q,{modelValue:we.value.usernameMinLength,"onUpdate:modelValue":u[17]||(u[17]=e=>we.value.usernameMinLength=e),min:1,max:12},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"usernameMaxLength",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.register.usernameMaxLength"})])),default:g((()=>[v(Q,{modelValue:we.value.usernameMaxLength,"onUpdate:modelValue":u[18]||(u[18]=e=>we.value.usernameMaxLength=e),min:1,max:30},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:24},{default:g((()=>[v(n,{prop:"usernameRegex",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.register.usernameRegex",help:""})])),default:g((()=>[v(o,{modelValue:we.value.usernameRegex,"onUpdate:modelValue":u[19]||(u[19]=e=>we.value.usernameRegex=e),maxlength:"100"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"smallAvatarSize",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.register.smallAvatarSize"})])),default:g((()=>[v(Q,{modelValue:we.value.smallAvatarSize,"onUpdate:modelValue":u[20]||(u[20]=e=>we.value.smallAvatarSize=e),min:1,max:9999},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"mediumAvatarSize",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.register.mediumAvatarSize"})])),default:g((()=>[v(Q,{modelValue:we.value.mediumAvatarSize,"onUpdate:modelValue":u[21]||(u[21]=e=>we.value.mediumAvatarSize=e),min:1,max:9999},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"largeAvatarSize",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.register.largeAvatarSize"})])),default:g((()=>[v(Q,{modelValue:we.value.largeAvatarSize,"onUpdate:modelValue":u[22]||(u[22]=e=>we.value.largeAvatarSize=e),min:1,max:9999},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:24},{default:g((()=>[v(n,{prop:"avatar",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.register.avatar"})])),default:g((()=>[v(o,{modelValue:we.value.avatar,"onUpdate:modelValue":u[23]||(u[23]=e=>we.value.avatar=e),maxlength:"255"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1})])),_:1})):"security"===Se.value&&k(r).epRank<1?(p(),i(ae,{key:2,type:"warning",closable:!1,"show-icon":!0},{title:g((()=>[L("span",{innerHTML:e.$t("error.enterprise")},null,8,be)])),_:1})):"security"===Se.value&&k(r).epRank>=1?(p(),i(X,{key:3},{default:g((()=>[v(m,{span:24},{default:g((()=>[v(n,{prop:"passwordStrength"},{label:g((()=>[v($,{message:"config.security.passwordStrength"})])),default:g((()=>[v(le,{modelValue:we.value.passwordStrength,"onUpdate:modelValue":u[24]||(u[24]=e=>we.value.passwordStrength=e),class:"w-full"},{default:g((()=>[(p(),c(V,null,_([0,1,2,3,4],(l=>v(ee,{key:l,value:l,label:e.$t(`config.security.passwordStrength.${l}`)},null,8,["value","label"]))),64))])),_:1},8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"passwordMinLength"},{label:g((()=>[v($,{message:"config.security.passwordMinLength",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.passwordMinLength,"onUpdate:modelValue":u[25]||(u[25]=e=>we.value.passwordMinLength=e),min:0,max:16},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"passwordMaxLength"},{label:g((()=>[v($,{message:"config.security.passwordMaxLength",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.passwordMaxLength,"onUpdate:modelValue":u[26]||(u[26]=e=>we.value.passwordMaxLength=e),min:16,max:64},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"userMaxAttempts"},{label:g((()=>[v($,{message:"config.security.userMaxAttempts",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.userMaxAttempts,"onUpdate:modelValue":u[27]||(u[27]=e=>we.value.userMaxAttempts=e),min:0,max:100},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"userLockMinutes"},{label:g((()=>[v($,{message:"config.security.userLockMinutes",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.userLockMinutes,"onUpdate:modelValue":u[28]||(u[28]=e=>we.value.userLockMinutes=e),min:1,max:1440},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"ipCaptchaAttempts"},{label:g((()=>[v($,{message:"config.security.ipCaptchaAttempts",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.ipCaptchaAttempts,"onUpdate:modelValue":u[29]||(u[29]=e=>we.value.ipCaptchaAttempts=e),min:0,max:100},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"ipMaxAttempts"},{label:g((()=>[v($,{message:"config.security.ipMaxAttempts",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.ipMaxAttempts,"onUpdate:modelValue":u[30]||(u[30]=e=>we.value.ipMaxAttempts=e),min:0,max:999},null,8,["modelValue"])])),_:1})])),_:1}),k(r).epRank>=2||k(r).epDisplay?(p(),c(V,{key:0},[v(ue,{"content-position":"left"}),v(m,{span:12},{default:g((()=>[v(n,{prop:"passwordMinDays"},{label:g((()=>[v($,{message:"config.security.passwordMinDays",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.passwordMinDays,"onUpdate:modelValue":u[31]||(u[31]=e=>we.value.passwordMinDays=e),min:0,max:998},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"passwordMaxDays"},{label:g((()=>[v($,{message:"config.security.passwordMaxDays",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.passwordMaxDays,"onUpdate:modelValue":u[32]||(u[32]=e=>we.value.passwordMaxDays=e),min:0,max:999},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"passwordWarnDays"},{label:g((()=>[v($,{message:"config.security.passwordWarnDays",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.passwordWarnDays,"onUpdate:modelValue":u[33]||(u[33]=e=>we.value.passwordWarnDays=e),min:0,max:90},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"passwordMaxHistory"},{label:g((()=>[v($,{message:"config.security.passwordMaxHistory",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.passwordMaxHistory,"onUpdate:modelValue":u[34]||(u[34]=e=>we.value.passwordMaxHistory=e),min:0,max:24},null,8,["modelValue"])])),_:1})])),_:1})],64)):S("",!0),k(r).epRank>=3||k(r).epDisplay?(p(),c(V,{key:1},[v(ue,{"content-position":"left"}),v(m,{span:12},{default:g((()=>[v(n,{prop:"twoFactor"},{label:g((()=>[v($,{message:"config.security.twoFactor",help:""})])),default:g((()=>[v(Y,{modelValue:we.value.twoFactor,"onUpdate:modelValue":u[35]||(u[35]=e=>we.value.twoFactor=e),disabled:k(r).epRank<3},null,8,["modelValue","disabled"])])),_:1})])),_:1})],64)):S("",!0),v(m,{span:24},{default:g((()=>[v(ue,{"content-position":"left"}),v(n,{prop:"ssrfWhiteList"},{label:g((()=>[v($,{message:"config.security.ssrfWhiteList",help:""})])),default:g((()=>[v(o,{modelValue:we.value.ssrfWhiteList,"onUpdate:modelValue":u[36]||(u[36]=e=>we.value.ssrfWhiteList=e),rows:12,type:"textarea"},null,8,["modelValue"])])),_:1})])),_:1})])),_:1})):"sms"===Se.value?(p(),i(X,{key:4},{default:g((()=>[v(m,{span:12},{default:g((()=>[v(n,{prop:"provider",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.sms.provider"})])),default:g((()=>[v(te,{modelValue:we.value.provider,"onUpdate:modelValue":u[37]||(u[37]=e=>we.value.provider=e)},{default:g((()=>[(p(),c(V,null,_([0,1,2],(l=>v(se,{key:l,value:l},{default:g((()=>[A(T(e.$t(`config.sms.provider.${l}`)),1)])),_:2},1032,["value"]))),64))])),_:1},8,["modelValue"])])),_:1},8,["rules"])])),_:1}),0!==we.value.provider?(p(),c(V,{key:0},[v(m,{span:12},{default:g((()=>[v(n,{prop:"maxPerIp"},{label:g((()=>[v($,{message:"config.sms.maxPerIp"})])),default:g((()=>[v(Q,{modelValue:we.value.maxPerIp,"onUpdate:modelValue":u[38]||(u[38]=e=>we.value.maxPerIp=e),min:1,max:99999},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"codeLength"},{label:g((()=>[v($,{message:"config.sms.codeLength"})])),default:g((()=>[v(Q,{modelValue:we.value.codeLength,"onUpdate:modelValue":u[39]||(u[39]=e=>we.value.codeLength=e),min:4,max:6},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"codeExpires"},{label:g((()=>[v($,{message:"config.sms.codeExpires",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.codeExpires,"onUpdate:modelValue":u[40]||(u[40]=e=>we.value.codeExpires=e),min:3,max:30},null,8,["modelValue"])])),_:1})])),_:1}),1===we.value.provider?(p(),c(V,{key:0},[v(m,{span:12},{default:g((()=>[v(n,{prop:"accessKeyId",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.sms.accessKeyId"})])),default:g((()=>[v(o,{modelValue:we.value.accessKeyId,"onUpdate:modelValue":u[41]||(u[41]=e=>we.value.accessKeyId=e),maxlength:"128","show-password":""},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"accessKeySecret",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.sms.accessKeySecret"})])),default:g((()=>[v(o,{modelValue:we.value.accessKeySecret,"onUpdate:modelValue":u[42]||(u[42]=e=>we.value.accessKeySecret=e),maxlength:"128","show-password":""},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"templateCode",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.sms.templateCode"})])),default:g((()=>[v(o,{modelValue:we.value.templateCode,"onUpdate:modelValue":u[43]||(u[43]=e=>we.value.templateCode=e),maxlength:"32"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"codeName",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.sms.codeName"})])),default:g((()=>[v(o,{modelValue:we.value.codeName,"onUpdate:modelValue":u[44]||(u[44]=e=>we.value.codeName=e),maxlength:"20"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1})],64)):2===we.value.provider?(p(),c(V,{key:1},[v(m,{span:12},{default:g((()=>[v(n,{prop:"secretId",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.sms.secretId"})])),default:g((()=>[v(o,{modelValue:we.value.secretId,"onUpdate:modelValue":u[45]||(u[45]=e=>we.value.secretId=e),maxlength:"128","show-password":""},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"secretKey",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.sms.secretKey"})])),default:g((()=>[v(o,{modelValue:we.value.secretKey,"onUpdate:modelValue":u[46]||(u[46]=e=>we.value.secretKey=e),maxlength:"128","show-password":""},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"templateId",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.sms.templateId"})])),default:g((()=>[v(o,{modelValue:we.value.templateId,"onUpdate:modelValue":u[47]||(u[47]=e=>we.value.templateId=e),maxlength:"32"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"sdkAppId",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.sms.sdkAppId"})])),default:g((()=>[v(o,{modelValue:we.value.sdkAppId,"onUpdate:modelValue":u[48]||(u[48]=e=>we.value.sdkAppId=e),maxlength:"64"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"region",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.sms.region",help:""})])),default:g((()=>[v(o,{modelValue:we.value.region,"onUpdate:modelValue":u[49]||(u[49]=e=>we.value.region=e),maxlength:"64"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1})],64)):S("",!0),v(m,{span:12},{default:g((()=>[v(n,{prop:"signName",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.sms.signName",help:""})])),default:g((()=>[v(o,{modelValue:we.value.signName,"onUpdate:modelValue":u[50]||(u[50]=e=>we.value.signName=e),maxlength:"50"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(ue,{"content-position":"left"},{default:g((()=>[A(T(e.$t("config.sms.test")),1)])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"testMobile"},{label:g((()=>[v($,{message:"config.sms.testMobile"})])),default:g((()=>[v(o,{modelValue:we.value.testMobile,"onUpdate:modelValue":u[51]||(u[51]=e=>we.value.testMobile=e),maxlength:"30"},null,8,["modelValue"])])),_:1})])),_:1})],64)):S("",!0)])),_:1})):"email"===Se.value?(p(),i(X,{key:5},{default:g((()=>[v(m,{span:12},{default:g((()=>[v(n,{prop:"host",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.email.host",help:""})])),default:g((()=>[v(o,{modelValue:we.value.host,"onUpdate:modelValue":u[52]||(u[52]=e=>we.value.host=e),maxlength:"50"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"port",rules:{type:"number",min:0,max:65535,message:()=>e.$t("v.range",{min:0,max:65535})}},{label:g((()=>[v($,{message:"config.email.port",help:""})])),default:g((()=>[v(o,{modelValue:we.value.port,"onUpdate:modelValue":u[53]||(u[53]=e=>we.value.port=e),modelModifiers:{number:!0}},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"username",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.email.username",help:""})])),default:g((()=>[v(o,{modelValue:we.value.username,"onUpdate:modelValue":u[54]||(u[54]=e=>we.value.username=e),maxlength:"50"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"password",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.email.password",help:""})])),default:g((()=>[v(o,{modelValue:we.value.password,"onUpdate:modelValue":u[55]||(u[55]=e=>we.value.password=e),maxlength:"50","show-password":""},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"from",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.email.from",help:""})])),default:g((()=>[v(o,{modelValue:we.value.from,"onUpdate:modelValue":u[56]||(u[56]=e=>we.value.from=e),maxlength:"50"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"timeout",rules:{type:"number",min:0,max:65535,message:()=>e.$t("v.range",{min:0,max:65535})}},{label:g((()=>[v($,{message:"config.email.timeout",help:""})])),default:g((()=>[v(o,{modelValue:we.value.timeout,"onUpdate:modelValue":u[57]||(u[57]=e=>we.value.timeout=e),modelModifiers:{number:!0}},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"ssl"},{label:g((()=>[v($,{message:"config.email.ssl",help:""})])),default:g((()=>[v(Y,{modelValue:we.value.ssl,"onUpdate:modelValue":u[58]||(u[58]=e=>we.value.ssl=e)},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"maxPerIp"},{label:g((()=>[v($,{message:"config.email.maxPerIp"})])),default:g((()=>[v(Q,{modelValue:we.value.maxPerIp,"onUpdate:modelValue":u[59]||(u[59]=e=>we.value.maxPerIp=e),min:1,max:99999},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"codeLength"},{label:g((()=>[v($,{message:"config.email.codeLength"})])),default:g((()=>[v(Q,{modelValue:we.value.codeLength,"onUpdate:modelValue":u[60]||(u[60]=e=>we.value.codeLength=e),min:4,max:6},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"codeExpires"},{label:g((()=>[v($,{message:"config.email.codeExpires",help:""})])),default:g((()=>[v(Q,{modelValue:we.value.codeExpires,"onUpdate:modelValue":u[61]||(u[61]=e=>we.value.codeExpires=e),min:3,max:30},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:24},{default:g((()=>[v(n,{prop:"subject",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.email.subject"})])),default:g((()=>[v(o,{modelValue:we.value.subject,"onUpdate:modelValue":u[62]||(u[62]=e=>we.value.subject=e),maxlength:"100"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:24},{default:g((()=>[v(n,{prop:"text",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.email.text",help:""})])),default:g((()=>[v(o,{modelValue:we.value.text,"onUpdate:modelValue":u[63]||(u[63]=e=>we.value.text=e),rows:3,type:"textarea",maxlength:"400"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(ue,{"content-position":"left"},{default:g((()=>[A(T(e.$t("config.email.testEmail")),1)])),_:1}),v(m,{span:24},{default:g((()=>[v(n,{prop:"testTo"},{label:g((()=>[v($,{message:"config.email.testTo"})])),default:g((()=>[v(o,{modelValue:we.value.testTo,"onUpdate:modelValue":u[64]||(u[64]=e=>we.value.testTo=e),maxlength:"50"},null,8,["modelValue"])])),_:1})])),_:1})])),_:1})):"uploadStorage"===Se.value||"htmlStorage"===Se.value||"templateStorage"===Se.value?(p(),i(X,{key:6},{default:g((()=>[v(m,{span:24},{default:g((()=>[v(n,{prop:"type",rules:[{required:!0,message:()=>e.$t("v.required")},{validator:(l,a,u)=>{0!==a&&k(r).epRank<1?u(e.$t("error.enterprise.short")):u()}}]},{label:g((()=>[v($,{message:"config.storage.type"})])),default:g((()=>[v(le,{modelValue:we.value.type,"onUpdate:modelValue":u[65]||(u[65]=e=>we.value.type=e),class:"w-full"},{default:g((()=>[(p(!0),c(V,null,_([0,1,10].filter((e=>0===e||k(r).epRank>=2||k(r).epDisplay)),(l=>(p(),i(ee,{key:l,value:l,label:e.$t(`config.storage.type.${l}`)},null,8,["value","label"])))),128))])),_:1},8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:24},{default:g((()=>[v(n,{prop:"path",rules:[{pattern:/^(?!.*\.\.).*$/,message:()=>e.$t("v.url")},{asyncValidator:async(l,a,u)=>{await k(de)(a)?u():u(e.$t("config.error.storagePathAllowed"))},trigger:"blur"}]},{label:g((()=>[v($,{message:"config.storage.path"})])),default:g((()=>[v(o,{modelValue:we.value.path,"onUpdate:modelValue":u[66]||(u[66]=e=>we.value.path=e),maxlength:"160"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:24},{default:g((()=>[v(n,{prop:"url",rules:{pattern:/^(?!.*\.\.).*$/,message:()=>e.$t("v.url")}},{label:g((()=>[v($,{message:"config.storage.url"})])),default:g((()=>[v(o,{modelValue:we.value.url,"onUpdate:modelValue":u[67]||(u[67]=e=>we.value.url=e),maxlength:"160"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),1===we.value.type?(p(),c(V,{key:0},[v(m,{span:12},{default:g((()=>[v(n,{prop:"hostname",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.storage.hostname"})])),default:g((()=>[v(o,{modelValue:we.value.hostname,"onUpdate:modelValue":u[68]||(u[68]=e=>we.value.hostname=e),maxlength:"160",disabled:k(r).epRank<1},null,8,["modelValue","disabled"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"port",rules:{type:"number",min:0,max:65535,message:()=>e.$t("v.range",{min:0,max:65535})}},{label:g((()=>[v($,{message:"config.storage.port",help:""})])),default:g((()=>[v(o,{modelValue:we.value.port,"onUpdate:modelValue":u[69]||(u[69]=e=>we.value.port=e),modelModifiers:{number:!0},disabled:k(r).epRank<1},null,8,["modelValue","disabled"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"username",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.storage.username"})])),default:g((()=>[v(o,{modelValue:we.value.username,"onUpdate:modelValue":u[70]||(u[70]=e=>we.value.username=e),maxlength:"50",disabled:k(r).epRank<1},null,8,["modelValue","disabled"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"password",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.storage.password"})])),default:g((()=>[v(o,{modelValue:we.value.password,"onUpdate:modelValue":u[71]||(u[71]=e=>we.value.password=e),type:"password","show-password":"",maxlength:"160",disabled:k(r).epRank<1},null,8,["modelValue","disabled"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"encoding"},{label:g((()=>[v($,{message:"config.storage.encoding",help:""})])),default:g((()=>[v(o,{modelValue:we.value.encoding,"onUpdate:modelValue":u[72]||(u[72]=e=>we.value.encoding=e),maxlength:"50",disabled:k(r).epRank<1},null,8,["modelValue","disabled"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"passive"},{label:g((()=>[v($,{message:"config.storage.passive",help:""})])),default:g((()=>[v(Y,{modelValue:we.value.passive,"onUpdate:modelValue":u[73]||(u[73]=e=>we.value.passive=e),disabled:k(r).epRank<1},null,8,["modelValue","disabled"])])),_:1})])),_:1}),v(m,{span:24},{default:g((()=>[v(n,{prop:"encryption",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.storage.encryption"})])),default:g((()=>[v(le,{modelValue:we.value.encryption,"onUpdate:modelValue":u[74]||(u[74]=e=>we.value.encryption=e),class:"w-full"},{default:g((()=>[(p(),c(V,null,_([0,1,2],(l=>v(ee,{key:l,label:e.$t(`config.storage.encryption.${l}`),value:l},null,8,["label","value"]))),64))])),_:1},8,["modelValue"])])),_:1},8,["rules"])])),_:1})],64)):S("",!0),10===we.value.type?(p(),c(V,{key:1},[v(m,{span:24},{default:g((()=>[v(n,{prop:"endpoint",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.storage.endpoint"})])),default:g((()=>[v(o,{modelValue:we.value.endpoint,"onUpdate:modelValue":u[75]||(u[75]=e=>we.value.endpoint=e),maxlength:"160",disabled:k(r).epRank<1},null,8,["modelValue","disabled"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"region",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.storage.region"})])),default:g((()=>[v(o,{modelValue:we.value.region,"onUpdate:modelValue":u[76]||(u[76]=e=>we.value.region=e),maxlength:"160",disabled:k(r).epRank<1},null,8,["modelValue","disabled"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"bucket",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.storage.bucket"})])),default:g((()=>[v(o,{modelValue:we.value.bucket,"onUpdate:modelValue":u[77]||(u[77]=e=>we.value.bucket=e),maxlength:"160",disabled:k(r).epRank<1},null,8,["modelValue","disabled"])])),_:1},8,["rules"])])),_:1}),v(m,{span:24},{default:g((()=>[v(n,{prop:"accessKey",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.storage.accessKey"})])),default:g((()=>[v(o,{modelValue:we.value.accessKey,"onUpdate:modelValue":u[78]||(u[78]=e=>we.value.accessKey=e),type:"password","show-password":"",maxlength:"160",disabled:k(r).epRank<1},null,8,["modelValue","disabled"])])),_:1},8,["rules"])])),_:1}),v(m,{span:24},{default:g((()=>[v(n,{prop:"secretKey",rules:{required:!0,message:()=>e.$t("v.required")}},{label:g((()=>[v($,{message:"config.storage.secretKey"})])),default:g((()=>[v(o,{modelValue:we.value.secretKey,"onUpdate:modelValue":u[79]||(u[79]=e=>we.value.secretKey=e),type:"password","show-password":"",maxlength:"160",disabled:k(r).epRank<1},null,8,["modelValue","disabled"])])),_:1},8,["rules"])])),_:1})],64)):S("",!0)])),_:1})):"customs"===Se.value?(p(),i(X,{key:7},{default:g((()=>[(p(!0),c(V,null,_(Me.value,(l=>(p(),i(m,{key:l.code,span:l.double?12:24},{default:g((()=>[v(n,{prop:l.code,rules:l.required?{required:!0,message:()=>e.$t("v.required")}:void 0},{label:g((()=>[v($,{label:l.name},null,8,["label"])])),default:g((()=>[v(ye,{modelValue:we.value[l.code],"onUpdate:modelValue":e=>we.value[l.code]=e,"model-key":we.value[l.code+"Key"],"onUpdate:modelKey":e=>we.value[l.code+"Key"]=e,field:l},null,8,["modelValue","onUpdate:modelValue","model-key","onUpdate:modelKey","field"])])),_:2},1032,["prop","rules"])])),_:2},1032,["span"])))),128))])),_:1})):(p(),i(X,{key:8},{default:g((()=>[v(m,{span:12},{default:g((()=>[v(n,{prop:"port",rules:{type:"number",min:0,max:65535,message:()=>e.$t("v.range",{min:0,max:65535})}},{label:g((()=>[v($,{message:"config.port",help:""})])),default:g((()=>[v(o,{modelValue:we.value.port,"onUpdate:modelValue":u[80]||(u[80]=e=>we.value.port=e),modelModifiers:{number:!0}},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"contextPath"},{label:g((()=>[v($,{message:"config.contextPath",help:""})])),default:g((()=>[v(o,{modelValue:we.value.contextPath,"onUpdate:modelValue":u[81]||(u[81]=e=>we.value.contextPath=e),maxlength:"50"},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"channelUrl",rules:{pattern:/^\/[\w-]+$/,message:()=>e.$t("config.error.channelUrlPattern")}},{label:g((()=>[v($,{message:"config.channelUrl",help:""})])),default:g((()=>[v(o,{modelValue:we.value.channelUrl,"onUpdate:modelValue":u[82]||(u[82]=e=>we.value.channelUrl=e),maxlength:"50"},null,8,["modelValue"])])),_:1},8,["rules"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"articleUrl"},{label:g((()=>[v($,{message:"config.articleUrl",help:""})])),default:g((()=>[v(o,{modelValue:we.value.articleUrl,"onUpdate:modelValue":u[83]||(u[83]=e=>we.value.articleUrl=e),maxlength:"50"},null,8,["modelValue"])])),_:1})])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"defaultSiteId",label:e.$t("config.defaultSite")},{default:g((()=>[v(xe,{modelValue:we.value.defaultSiteId,"onUpdate:modelValue":u[84]||(u[84]=e=>we.value.defaultSiteId=e),data:$e.value,"node-key":"id",props:{label:"name"},class:"w-full","render-after-expand":!1,"check-strictly":""},null,8,["modelValue","data"])])),_:1},8,["label"])])),_:1}),v(m,{span:12},{default:g((()=>[v(n,{prop:"multiDomain"},{label:g((()=>[v($,{message:"config.multiDomain",help:""})])),default:g((()=>[v(Y,{modelValue:we.value.multiDomain,"onUpdate:modelValue":u[85]||(u[85]=e=>we.value.multiDomain=e)},null,8,["modelValue"])])),_:1})])),_:1})])),_:1})),"security"!==Se.value||k(r).epRank>=1?b((p(),c("div",he,[v(ke,{disabled:k(R)(`config:${Se.value}:update`),type:"primary","native-type":"submit",onClick:u[86]||(u[86]=j((()=>{C.value.validate((async e=>{if(e){Ue.value=!0;try{"upload"===Se.value?await oe(we.value):"register"===Se.value?await re(we.value):"security"===Se.value?await ne(we.value):"customs"===Se.value?await me(we.value):"sms"===Se.value?await pe(we.value):"email"===Se.value?await ie(we.value):"uploadStorage"===Se.value?await ge(we.value):"htmlStorage"===Se.value?await ve(we.value):"templateStorage"===Se.value?await fe(we.value):await ce(we.value),a.initConfig(),K.success(l("success"))}finally{Ue.value=!1}Ke()}}))}),["prevent"]))},{default:g((()=>[A(T(e.$t("save")),1)])),_:1},8,["disabled"]),"sms"===Se.value&&0!==we.value.provider?(p(),i(ke,{key:0,disabled:k(R)("config:sms:update")||!we.value.testMobile?.trim(),onClick:u[87]||(u[87]=()=>{C.value.validate((async e=>{if(e){Ue.value=!0;try{const{status:e,message:a}=await Ve(we.value);-1===e?D.alert(a):K.success(l("success"))}finally{Ue.value=!1}Ke()}}))})},{default:g((()=>[A(T(e.$t("config.sms.op.testSend")),1)])),_:1},8,["disabled"])):S("",!0),"email"===Se.value?(p(),i(ke,{key:1,disabled:k(R)("config:email:update")||!we.value.testTo?.trim(),onClick:u[88]||(u[88]=()=>{C.value.validate((async e=>{if(e){Ue.value=!0;try{const{status:e,message:a}=await _e(we.value);-1===e?D.alert(a):K.success(l("success"))}finally{Ue.value=!1}Ke()}}))})},{default:g((()=>[A(T(e.$t("config.email.op.testSend")),1)])),_:1},8,["disabled"])):S("",!0)])),[[je,Ue.value]]):S("",!0)])),_:1},8,["model"])),[[je,qe.value]])])),_:1})])),_:1})}}}),[["__scopeId","data-v-b0c4ffbd"]]))}}}));