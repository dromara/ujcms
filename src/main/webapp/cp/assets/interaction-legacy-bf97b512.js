System.register(["./index-legacy-b678d1bc.js"],(function(a,t){"use strict";var e;return{setters:[a=>{e=a.aO}],execute:function(){a("a",(async a=>(await e.get("/backend/ext/message-board",{params:a})).data)),a("q",(async a=>(await e.get(`/backend/ext/message-board/${a}`)).data)),a("c",(async a=>(await e.post("/backend/ext/message-board",a)).data)),a("u",(async a=>(await e.post("/backend/ext/message-board?_method=put",a)).data)),a("b",(async(a,t)=>(await e.post("/backend/ext/message-board/status?_method=put",{ids:a,status:t})).data)),a("d",(async a=>(await e.post("/backend/ext/message-board?_method=delete",a)).data)),a("i",(async a=>(await e.get("/backend/ext/vote",{params:a})).data)),a("e",(async a=>(await e.get(`/backend/ext/vote/${a}`)).data)),a("f",(async a=>(await e.post("/backend/ext/vote",a)).data)),a("g",(async a=>(await e.post("/backend/ext/vote?_method=put",a)).data)),a("h",(async a=>(await e.post("/backend/ext/vote?_method=delete",a)).data))}}}));