System.register(["./index-legacy-DZmnsqrR.js"],(function(e,i){"use strict";var t,l,a,c,n,o,s,r,h,d,v,p,w,u,_,x,f,g,b,m,y,k,I,V,S,z,T,C,A,B,E,P,N,$,j,K,L,F,W,q,D,G,H,J,M;return{setters:[e=>{t=e.ae,l=e.eE,a=e.ad,c=e.bW,n=e.ac,o=e.af,s=e.ag,r=e.ah,h=e.ak,d=e.cA,v=e.f1,p=e.al,w=e.d,u=e.aS,_=e.co,x=e.ap,f=e.cp,g=e.ao,b=e.ar,m=e.r,y=e.ck,k=e.B,I=e.ct,V=e.o,S=e.b,z=e.P,T=e.l,C=e.aA,A=e.bt,B=e.f2,E=e.au,P=e.k,N=e.ay,$=e.c,j=e.w,K=e.c9,L=e.a3,F=e.g,W=e.x,q=e.h,D=e.aE,G=e.aC,H=e.ax,J=e.n,M=e.aI}],execute:function(){var i=document.createElement("style");i.textContent=".el-switch{--el-switch-on-color:var(--el-color-primary);--el-switch-off-color:var(--el-border-color);align-items:center;display:inline-flex;font-size:14px;height:32px;line-height:20px;position:relative;vertical-align:middle}.el-switch.is-disabled .el-switch__core,.el-switch.is-disabled .el-switch__label{cursor:not-allowed}.el-switch__label{color:var(--el-text-color-primary);cursor:pointer;display:inline-block;font-size:14px;font-weight:500;height:20px;transition:var(--el-transition-duration-fast);vertical-align:middle}.el-switch__label.is-active{color:var(--el-color-primary)}.el-switch__label--left{margin-right:10px}.el-switch__label--right{margin-left:10px}.el-switch__label *{display:inline-block;font-size:14px;line-height:1}.el-switch__label .el-icon{height:inherit}.el-switch__label .el-icon svg{vertical-align:middle}.el-switch__input{height:0;margin:0;opacity:0;position:absolute;width:0}.el-switch__input:focus-visible~.el-switch__core{outline:2px solid var(--el-switch-on-color);outline-offset:1px}.el-switch__core{align-items:center;background:var(--el-switch-off-color);border:1px solid var(--el-switch-border-color,var(--el-switch-off-color));border-radius:10px;box-sizing:border-box;cursor:pointer;display:inline-flex;height:20px;min-width:40px;outline:none;position:relative;transition:border-color var(--el-transition-duration),background-color var(--el-transition-duration)}.el-switch__core .el-switch__inner{align-items:center;display:flex;height:16px;justify-content:center;overflow:hidden;padding:0 4px 0 18px;transition:all var(--el-transition-duration);width:100%}.el-switch__core .el-switch__inner .is-icon,.el-switch__core .el-switch__inner .is-text{color:var(--el-color-white);font-size:12px;overflow:hidden;text-overflow:ellipsis;-webkit-user-select:none;user-select:none;white-space:nowrap}.el-switch__core .el-switch__action{align-items:center;background-color:var(--el-color-white);border-radius:var(--el-border-radius-circle);color:var(--el-switch-off-color);display:flex;height:16px;justify-content:center;left:1px;position:absolute;transition:all var(--el-transition-duration);width:16px}.el-switch.is-checked .el-switch__core{background-color:var(--el-switch-on-color);border-color:var(--el-switch-border-color,var(--el-switch-on-color))}.el-switch.is-checked .el-switch__core .el-switch__action{color:var(--el-switch-on-color);left:calc(100% - 17px)}.el-switch.is-checked .el-switch__core .el-switch__inner{padding:0 18px 0 4px}.el-switch.is-disabled{opacity:.6}.el-switch--wide .el-switch__label.el-switch__label--left span{left:10px}.el-switch--wide .el-switch__label.el-switch__label--right span{right:10px}.el-switch .label-fade-enter-from,.el-switch .label-fade-leave-active{opacity:0}.el-switch--large{font-size:14px;height:40px;line-height:24px}.el-switch--large .el-switch__label{font-size:14px;height:24px}.el-switch--large .el-switch__label *{font-size:14px}.el-switch--large .el-switch__core{border-radius:12px;height:24px;min-width:50px}.el-switch--large .el-switch__core .el-switch__inner{height:20px;padding:0 6px 0 22px}.el-switch--large .el-switch__core .el-switch__action{height:20px;width:20px}.el-switch--large.is-checked .el-switch__core .el-switch__action{left:calc(100% - 21px)}.el-switch--large.is-checked .el-switch__core .el-switch__inner{padding:0 22px 0 6px}.el-switch--small{font-size:12px;height:24px;line-height:16px}.el-switch--small .el-switch__label{font-size:12px;height:16px}.el-switch--small .el-switch__label *{font-size:12px}.el-switch--small .el-switch__core{border-radius:8px;height:16px;min-width:30px}.el-switch--small .el-switch__core .el-switch__inner{height:12px;padding:0 2px 0 14px}.el-switch--small .el-switch__core .el-switch__action{height:12px;width:12px}.el-switch--small.is-checked .el-switch__core .el-switch__action{left:calc(100% - 13px)}.el-switch--small.is-checked .el-switch__core .el-switch__inner{padding:0 14px 0 2px}\n/*$vite$:1*/",document.head.appendChild(i);const O=s({modelValue:{type:[Boolean,String,Number],default:!1},disabled:Boolean,loading:Boolean,size:{type:String,validator:v},width:{type:[String,Number],default:""},inlinePrompt:Boolean,inactiveActionIcon:{type:d},activeActionIcon:{type:d},activeIcon:{type:d},inactiveIcon:{type:d},activeText:{type:String,default:""},inactiveText:{type:String,default:""},activeValue:{type:[Boolean,String,Number],default:!0},inactiveValue:{type:[Boolean,String,Number],default:!1},name:{type:String,default:""},validateEvent:{type:Boolean,default:!0},beforeChange:{type:h(Function)},id:String,tabindex:{type:[String,Number]},...r(["ariaLabel"])}),Q={[o]:e=>l(e)||a(e)||c(e),[n]:e=>l(e)||a(e)||c(e),[t]:e=>l(e)||a(e)||c(e)},R="ElSwitch",U=w({name:R}),X=w({...U,props:O,emits:Q,setup(e,{expose:i,emit:a}){const c=e,{formItem:s}=u(),r=_(),h=x("switch"),{inputId:d}=f(c,{formItemContext:s}),v=g(b((()=>c.loading))),p=m(!1!==c.modelValue),w=m(),M=m(),O=b((()=>[h.b(),h.m(r.value),h.is("disabled",v.value),h.is("checked",Z.value)])),Q=b((()=>[h.e("label"),h.em("label","left"),h.is("active",!Z.value)])),U=b((()=>[h.e("label"),h.em("label","right"),h.is("active",Z.value)])),X=b((()=>({width:y(c.width)})));k((()=>c.modelValue),(()=>{p.value=!0}));const Y=b((()=>!!p.value&&c.modelValue)),Z=b((()=>Y.value===c.activeValue));[c.activeValue,c.inactiveValue].includes(Y.value)||(a(o,c.inactiveValue),a(n,c.inactiveValue),a(t,c.inactiveValue)),k(Z,(e=>{var i;w.value.checked=e,c.validateEvent&&(null==(i=null==s?void 0:s.validate)||i.call(s,"change").catch((e=>I())))}));const ee=()=>{const e=Z.value?c.inactiveValue:c.activeValue;a(o,e),a(n,e),a(t,e),A((()=>{w.value.checked=Z.value}))},ie=()=>{if(v.value)return;const{beforeChange:e}=c;if(!e)return void ee();const i=e();[B(i),l(i)].includes(!0)||E(R,"beforeChange must return type `Promise<boolean>` or `boolean`"),B(i)?i.then((e=>{e&&ee()})).catch((e=>{})):i&&ee()};return V((()=>{w.value.checked=Z.value})),i({focus:()=>{var e,i;null==(i=null==(e=w.value)?void 0:e.focus)||i.call(e)},checked:Z}),(e,i)=>(S(),z("div",{class:N(P(O)),onClick:J(ie,["prevent"])},[T("input",{id:P(d),ref_key:"input",ref:w,class:N(P(h).e("input")),type:"checkbox",role:"switch","aria-checked":P(Z),"aria-disabled":P(v),"aria-label":e.ariaLabel,name:e.name,"true-value":e.activeValue,"false-value":e.inactiveValue,disabled:P(v),tabindex:e.tabindex,onChange:ee,onKeydown:C(ie,["enter"])},null,42,["id","aria-checked","aria-disabled","aria-label","name","true-value","false-value","disabled","tabindex","onKeydown"]),e.inlinePrompt||!e.inactiveIcon&&!e.inactiveText?F("v-if",!0):(S(),z("span",{key:0,class:N(P(Q))},[e.inactiveIcon?(S(),$(P(L),{key:0},{default:j((()=>[(S(),$(K(e.inactiveIcon)))])),_:1})):F("v-if",!0),!e.inactiveIcon&&e.inactiveText?(S(),z("span",{key:1,"aria-hidden":P(Z)},W(e.inactiveText),9,["aria-hidden"])):F("v-if",!0)],2)),T("span",{ref_key:"core",ref:M,class:N(P(h).e("core")),style:H(P(X))},[e.inlinePrompt?(S(),z("div",{key:0,class:N(P(h).e("inner"))},[e.activeIcon||e.inactiveIcon?(S(),$(P(L),{key:0,class:N(P(h).is("icon"))},{default:j((()=>[(S(),$(K(P(Z)?e.activeIcon:e.inactiveIcon)))])),_:1},8,["class"])):e.activeText||e.inactiveText?(S(),z("span",{key:1,class:N(P(h).is("text")),"aria-hidden":!P(Z)},W(P(Z)?e.activeText:e.inactiveText),11,["aria-hidden"])):F("v-if",!0)],2)):F("v-if",!0),T("div",{class:N(P(h).e("action"))},[e.loading?(S(),$(P(L),{key:0,class:N(P(h).is("loading"))},{default:j((()=>[q(P(D))])),_:1},8,["class"])):P(Z)?G(e.$slots,"active-action",{key:1},(()=>[e.activeActionIcon?(S(),$(P(L),{key:0},{default:j((()=>[(S(),$(K(e.activeActionIcon)))])),_:1})):F("v-if",!0)])):P(Z)?F("v-if",!0):G(e.$slots,"inactive-action",{key:2},(()=>[e.inactiveActionIcon?(S(),$(P(L),{key:0},{default:j((()=>[(S(),$(K(e.inactiveActionIcon)))])),_:1})):F("v-if",!0)]))],2)],6),e.inlinePrompt||!e.activeIcon&&!e.activeText?F("v-if",!0):(S(),z("span",{key:1,class:N(P(U))},[e.activeIcon?(S(),$(P(L),{key:0},{default:j((()=>[(S(),$(K(e.activeIcon)))])),_:1})):F("v-if",!0),!e.activeIcon&&e.activeText?(S(),z("span",{key:1,"aria-hidden":!P(Z)},W(e.activeText),9,["aria-hidden"])):F("v-if",!0)],2))],10,["onClick"]))}});e("E",M(p(X,[["__file","switch.vue"]])))}}}));