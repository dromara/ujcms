System.register(["./index-legacy-DywSgIaj.js"],(function(e,a){"use strict";var l,t,s,u,r,o,d,n,i,p,c,m,v,g,h,w,f,y,V,b,x,$,_,k,q,M,P,C,U,S,I,L,A,F,D,E,R,B,G,N,O,T,j;return{setters:[e=>{l=e.d,t=e.u,s=e.a,u=e.r,r=e.o,o=e.q,d=e.b,n=e.c,i=e.e,p=e.f,c=e.w,m=e.g,v=e.h,g=e.i,h=e.j,w=e.k,f=e.l,y=e.s,V=e.m,b=e.E,x=e.n,$=e.t,_=e.p,k=e.v,q=e.x,M=e.y,P=e.z,C=e.A,U=e.B,S=e.C,I=e.D,L=e.F,A=e.G,F=e.H,D=e.I,E=e.J,R=e.K,B=e.L,G=e.M,N=e.N,O=e.O,T=e.P,j=e._}],execute:function(){var a=document.createElement("style");a.textContent="[data-v-577d108e] .captcha .el-input-group__append{padding:0}\n/*$vite$:1*/",document.head.appendChild(a);const J=l({name:"ChangePassword",__name:"ChangePassword",props:{modelValue:{type:Boolean,required:!0}},emits:{"update:modelValue":null},setup(e,{emit:a}){const l=a,{t:_}=t(),k=s(),q=u({}),M=u(),P=u(),C=u(!1),U=u(!1),S=u(""),I=u();r((async()=>{C.value=!0;try{S.value=await o()}finally{C.value=!1}}));const L=()=>{M.value.validate((async e=>{if(e){U.value=!0;try{const e=y(q.value.password,S.value),a=y(q.value.newPassword,S.value),t=await V({...q.value,password:e,newPassword:a,passwordAgain:void 0});if(0!==t.status)return void(I.value=t.message);I.value=void 0,M.value.resetFields(),b.success(_("success")),l("update:modelValue",!1)}finally{U.value=!1}}}))};return(a,l)=>{const t=d("el-alert"),s=d("el-input"),u=d("el-form-item"),r=d("el-button"),o=d("el-form"),y=d("el-dialog"),V=n("loading");return i(),p(y,{title:a.$t("changePassword"),"model-value":e.modelValue,"onUpdate:modelValue":l[4]||(l[4]=e=>a.$emit("update:modelValue",e)),onOpened:l[5]||(l[5]=()=>{P.value?.focus(),M.value.resetFields()})},{default:c((()=>[m((i(),p(o,{ref_key:"form",ref:M,model:q.value,"label-width":"150px","label-position":"right"},{default:c((()=>[I.value?(i(),p(t,{key:0,title:I.value,type:"error",class:"mb-3",closable:!1,"show-icon":""},null,8,["title"])):v("",!0),g(u,{prop:"username",label:a.$t("user.username"),rules:[{required:!0,message:()=>a.$t("v.required")}]},{default:c((()=>[g(s,{ref_key:"focus",ref:P,modelValue:q.value.username,"onUpdate:modelValue":l[0]||(l[0]=e=>q.value.username=e),maxlength:"30"},null,8,["modelValue"])])),_:1},8,["label","rules"]),g(u,{prop:"password",label:a.$t("user.origPassword"),rules:[{required:!0,message:()=>a.$t("v.required")}]},{default:c((()=>[g(s,{modelValue:q.value.password,"onUpdate:modelValue":l[1]||(l[1]=e=>q.value.password=e),maxlength:"50","show-password":""},null,8,["modelValue"])])),_:1},8,["label","rules"]),g(u,{prop:"newPassword",label:a.$t("user.newPassword"),rules:[{required:!0,message:()=>a.$t("v.required")},{min:h(k).security.passwordMinLength,max:h(k).security.passwordMaxLength,message:()=>a.$t("user.error.passwordLength",{min:h(k).security.passwordMinLength,max:h(k).security.passwordMaxLength})},{pattern:new RegExp(h(k).security.passwordPattern),message:()=>a.$t(`user.error.passwordPattern.${h(k).security.passwordStrength}`)}]},{default:c((()=>[g(s,{modelValue:q.value.newPassword,"onUpdate:modelValue":l[2]||(l[2]=e=>q.value.newPassword=e),maxlength:h(k).security.passwordMaxLength,"show-password":""},null,8,["modelValue","maxlength"])])),_:1},8,["label","rules"]),g(u,{prop:"passwordAgain",label:a.$t("user.passwordAgain"),rules:[{required:!0,message:()=>a.$t("v.required")},{validator:(e,l,t)=>{l===q.value.newPassword?t():t(a.$t("user.error.passwordNotMatch"))}}]},{default:c((()=>[g(s,{modelValue:q.value.passwordAgain,"onUpdate:modelValue":l[3]||(l[3]=e=>q.value.passwordAgain=e),maxlength:"50","show-password":""},null,8,["modelValue"])])),_:1},8,["label","rules"]),w("div",null,[g(r,{loading:U.value,type:"primary","native-type":"submit",onClick:f(L,["prevent"])},{default:c((()=>[x($(a.$t("submit")),1)])),_:1},8,["loading"])])])),_:1},8,["model"])),[[V,C.value]])])),_:1},8,["title","model-value"])}}}),z=l({name:"GetShortMessage",__name:"GetShortMessage",props:{modelValue:{type:Boolean,required:!0}},emits:{"update:modelValue":null,finish:null},setup(e,{emit:a}){const l=e,s=a,{modelValue:r}=_(l),{t:o}=t(),n=u(),m=u(),y=u({}),V=u(!1),S=u(),I=u(),L=u(),A=async()=>{const{token:e,image:a}=await q();I.value="data:image/png;base64,"+a,L.value=e};k(r,(()=>{r.value&&A()}));const F=()=>{n.value.validate((async e=>{if(e){V.value=!0;try{const e=await U(L.value??"",y.value.captcha,y.value.mobile,3);if(0!==e.status)return void(S.value=e.message);S.value=void 0,n.value.resetFields(),b.success(o("success")),s("finish",e.result.shortMessageId),s("update:modelValue",!1)}finally{V.value=!1}}}))};return(a,l)=>{const t=d("el-alert"),s=d("el-input"),u=d("el-form-item"),r=d("el-image"),o=d("el-button"),b=d("el-form"),_=d("el-dialog");return i(),p(_,{title:a.$t("getShortMessage"),"model-value":e.modelValue,width:"576px","onUpdate:modelValue":l[3]||(l[3]=e=>a.$emit("update:modelValue",e)),onOpened:l[4]||(l[4]=()=>{m.value?.focus(),n.value.resetFields()})},{default:c((()=>[g(b,{ref_key:"form",ref:n,model:y.value,"label-width":"150px","label-position":"right"},{default:c((()=>[S.value?(i(),p(t,{key:0,title:S.value,type:"error",class:"mb-3",closable:!1,"show-icon":""},null,8,["title"])):v("",!0),g(u,{prop:"mobile",label:a.$t("mobile"),rules:[{required:!0,message:()=>a.$t("v.required")},{asyncValidator:async(e,l,t)=>{await h(M)(l)?t(a.$t("mobileNotExist")):t()},trigger:"blur"}]},{default:c((()=>[g(s,{ref_key:"focus",ref:m,modelValue:y.value.mobile,"onUpdate:modelValue":l[0]||(l[0]=e=>y.value.mobile=e),maxlength:"30"},null,8,["modelValue"])])),_:1},8,["label","rules"]),g(u,{prop:"captcha",label:a.$t("captcha"),rules:[{required:!0,message:()=>a.$t("v.required")},{asyncValidator:async(e,l,t)=>{null!=L.value&&await h(P)(L.value,l)?t():t(a.$t("captchaIncorrect"))},trigger:"blur"}],class:"captcha"},{default:c((()=>[g(s,{modelValue:y.value.captcha,"onUpdate:modelValue":l[2]||(l[2]=e=>y.value.captcha=e),name:"captcha",placeholder:a.$t("captcha"),"prefix-icon":h(C)},{append:c((()=>[g(r,{src:I.value,style:{height:"30px","margin-right":"1px"},class:"rounded-r cursor-pointer",title:a.$t("clickToRetrieveCaptcha"),onClick:l[1]||(l[1]=()=>A())},null,8,["src","title"])])),_:1},8,["modelValue","placeholder","prefix-icon"])])),_:1},8,["label","rules"]),w("div",null,[g(o,{loading:V.value,type:"primary","native-type":"submit",onClick:f(F,["prevent"])},{default:c((()=>[x($(a.$t("submit")),1)])),_:1},8,["loading"])])])),_:1},8,["model"])])),_:1},8,["title","model-value"])}}}),H={class:"h-full p-3 bg-gray-100"},K={class:"py-5 text-3xl font-bold text-center text-primary"},Q={class:"mt-2 text-right"},W={key:0,class:"mt-5 text-sm text-center text-gray-secondary"};e("default",j(l({__name:"LoginPage",setup(e){const{t:a}=t(),l=u(),s=u({}),n=u(),m=u(!1),V=u(!1),b=u(),_=u(),k=u(),M=u(),U=u(!1),j=u(),X=S(),Y=I(),Z=u(!1),ee=u(!1),ae=u(60),le=u(a("getShortMessage"));L(),A();const te=async()=>{const{token:e,image:a}=await q();b.value="data:image/png;base64,"+a,_.value=e},se=async()=>{m.value=await F(),m.value&&te()};r((async()=>{n.value.focus(),n.value.select(),se(),(async()=>{V.value=await D()})()})),E((()=>{j.value=X.query.redirect}));const ue=e=>{k.value=e,ae.value-=1,le.value=String(ae.value);const l=setInterval((()=>{ae.value-=1,le.value=String(ae.value),ae.value<=0&&(le.value=a("getShortMessage"),ae.value=60,clearInterval(l))}),1e3)},re=()=>{l.value.validate((async e=>{if(e){U.value=!0;try{const e=await o(),l=y(s.value.password,e),t=await O({...s.value,password:l,captchaToken:_.value,shortMessageId:k.value});if(0!==t.status)return se(),void(M.value=t.message);null!=t.result.remainingDays&&T.alert(a("passwordRemaingDays",{remainingDays:t.result.remainingDays}),{type:"warning"}),j.value?Y.push(j.value):window.location.reload()}finally{U.value=!1}}}))};return(e,a)=>{const t=d("el-alert"),u=d("el-input"),r=d("el-form-item"),o=d("el-button"),y=d("el-image"),k=d("el-form");return i(),R("div",H,[w("h3",K,$(h("UJCMS")),1),g(k,{ref_key:"form",ref:l,model:s.value,class:"mx-auto md:max-w-xs"},{default:c((()=>[M.value?(i(),p(t,{key:0,title:M.value,type:"error",class:"mb-3",closable:!1,"show-icon":""},null,8,["title"])):v("",!0),g(r,{prop:"username",rules:[{required:!0,message:()=>e.$t("v.required")}]},{default:c((()=>[g(u,{ref_key:"focus",ref:n,modelValue:s.value.username,"onUpdate:modelValue":a[0]||(a[0]=e=>s.value.username=e),name:"username",placeholder:e.$t("username"),"prefix-icon":h(B),autocomplete:"on"},null,8,["modelValue","placeholder","prefix-icon"])])),_:1},8,["rules"]),g(r,{prop:"password",rules:[{required:!0,message:()=>e.$t("v.required")}]},{default:c((()=>[g(u,{ref:"password",modelValue:s.value.password,"onUpdate:modelValue":a[1]||(a[1]=e=>s.value.password=e),type:"password",name:"password",placeholder:e.$t("password"),"prefix-icon":h(G),"show-password":""},null,8,["modelValue","placeholder","prefix-icon"])])),_:1},8,["rules"]),V.value?(i(),p(r,{key:1,prop:"shortMessageValue",rules:[{required:!0,message:()=>e.$t("v.required")}]},{default:c((()=>[g(u,{modelValue:s.value.shortMessageValue,"onUpdate:modelValue":a[3]||(a[3]=e=>s.value.shortMessageValue=e),name:"shortMessageValue",placeholder:e.$t("shortMessage"),"prefix-icon":h(N)},{append:c((()=>[g(o,{disabled:ae.value<60,onClick:a[2]||(a[2]=()=>ee.value=!0)},{default:c((()=>[x($(le.value),1)])),_:1},8,["disabled"])])),_:1},8,["modelValue","placeholder","prefix-icon"])])),_:1},8,["rules"])):v("",!0),m.value?(i(),p(r,{key:2,prop:"captcha",rules:[{required:!0,message:()=>e.$t("v.required")},{asyncValidator:async(a,l,t)=>{null!=_.value&&await h(P)(_.value,l)?t():t(e.$t("captchaIncorrect"))},trigger:"blur"}],class:"captcha"},{default:c((()=>[g(u,{modelValue:s.value.captcha,"onUpdate:modelValue":a[5]||(a[5]=e=>s.value.captcha=e),name:"captcha",placeholder:e.$t("captcha"),"prefix-icon":h(C)},{append:c((()=>[g(y,{src:b.value,style:{height:"30px","margin-right":"1px"},class:"rounded-r cursor-pointer",title:e.$t("clickToRetrieveCaptcha"),onClick:a[4]||(a[4]=()=>te())},null,8,["src","title"])])),_:1},8,["modelValue","placeholder","prefix-icon"])])),_:1},8,["rules"])):v("",!0),g(o,{type:"primary","native-type":"submit",class:"w-full",loading:U.value,onClick:f(re,["prevent"])},{default:c((()=>[x($(e.$t("login")),1)])),_:1},8,["loading"]),w("div",Q,[g(o,{type:"primary",link:"",onClick:a[6]||(a[6]=()=>Z.value=!0)},{default:c((()=>[x($(e.$t("changePassword")),1)])),_:1})])])),_:1},8,["model"]),"staging"===h("production")?(i(),R("div",W,a[9]||(a[9]=[w("p",null,"为避免数据被删改，演示用户登录后只拥有浏览后台功能，操作数据会显示无权访问（403）。",-1)]))):v("",!0),g(J,{modelValue:Z.value,"onUpdate:modelValue":a[7]||(a[7]=e=>Z.value=e)},null,8,["modelValue"]),g(z,{modelValue:ee.value,"onUpdate:modelValue":a[8]||(a[8]=e=>ee.value=e),onFinish:ue},null,8,["modelValue"])])}}}),[["__scopeId","data-v-577d108e"]]))}}}));