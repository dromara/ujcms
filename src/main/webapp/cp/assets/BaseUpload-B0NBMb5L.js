import{d as I,p as A,u as E,a as k,r as C,aa as c,b as d,e as p,K as L,i as _,w as N,bb as $,k as x,t as z,aj as T,E as V,j as f,ac as j,ad as M,f as O,h as D,_ as F}from"./index-B6OoDg6B.js";import{f as H,T as m,U as J,V as K,i as R}from"./config-DiPdhBV1.js";/* empty css                                                                   */const q=I({__name:"BaseUpload",props:{type:{type:String,default:"file",validator:a=>["image","video","audio","library","doc","file","any"].includes(a)},button:{type:String,default:null},data:{type:Object,default:null},uploadAction:{type:String,default:null},fileAccept:{type:String,default:null},fileMaxSize:{type:Number,default:null},multiple:{type:Boolean},disabled:{type:Boolean,default:!1},onSuccess:{type:Function,default:null}},setup(a){const v=a,{type:r,uploadAction:l,fileAccept:o,fileMaxSize:u}=A(v),{t:y}=E(),e=k(),s=C({}),g=c(()=>{if((l==null?void 0:l.value)!=null)return l.value;switch(r.value){case"image":return R;case"video":return K;case"audio":return J;case"library":return m;case"doc":return m;case"file":return H;default:throw new Error("Type not support: ".concat(r.value))}}),b=c(()=>{if((o==null?void 0:o.value)!=null)return o.value;switch(r.value){case"image":return e.upload.imageInputAccept;case"video":return e.upload.videoInputAccept;case"audio":return e.upload.audioInputAccept;case"library":return e.upload.libraryInputAccept;case"doc":return e.upload.docInputAccept;case"file":return e.upload.fileInputAccept;case"any":return;default:throw new Error("Type not support: ".concat(r.value))}}),n=c(()=>{if((u==null?void 0:u.value)!=null)return u.value;switch(r.value){case"image":return e.upload.imageLimitByte;case"video":return e.upload.videoLimitByte;case"audio":return e.upload.audioLimitByte;case"library":return e.upload.libraryLimitByte;case"doc":return e.upload.docLimitByte;case"file":return e.upload.fileLimitByte;default:return 0}}),B=t=>n.value>0&&t.size>n.value?(V.error(y("error.fileMaxSize",{size:"".concat(n.value/1024/1024," MB")})),!1):!0,U=t=>{T(JSON.parse(t.message))};return(t,G)=>{const S=d("el-upload"),h=d("el-progress");return p(),L("div",null,[_(S,{action:g.value,headers:{...f(M)(),...f(j)()},data:a.data,accept:b.value,"before-upload":B,"on-success":a.onSuccess,"on-progress":(i,w)=>s.value=w,"on-error":U,"show-file-list":!1,disabled:a.disabled,multiple:a.multiple,drag:""},{default:N(()=>[$(t.$slots,"default",{},()=>{var i;return[x("span",null,z((i=a.button)!=null?i:t.$t("clickOrDragToUpload")),1)]},!0)]),_:3},8,["action","headers","data","accept","on-success","on-progress","disabled","multiple"]),s.value.status==="uploading"?(p(),O(h,{key:0,percentage:parseInt(s.value.percentage,10)},null,8,["percentage"])):D("",!0)])}}}),X=F(q,[["__scopeId","data-v-bf818630"]]);export{X as B};