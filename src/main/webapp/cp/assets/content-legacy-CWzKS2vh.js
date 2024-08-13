System.register(["./index-legacy-D9wwV_LM.js"],(function(a,t){"use strict";var e;return{setters:[a=>{e=a.b0}],execute:function(){a("d","../api/backend/core/jod-convert/doc"),a("j","../api/backend/core/jod-convert/library"),a("k",(async()=>(await e.get("/backend/core/jod-convert/enabled")).data)),a("m",(async a=>(await e.get("/backend/core/channel",{params:a})).data)),a("I",(async a=>(await e.get(`/backend/core/channel/${a}`)).data)),a("J",(async a=>(await e.post("/backend/core/channel",a)).data)),a("K",(async a=>(await e.post("/backend/core/channel?_method=put",a)).data)),a("O",(async(a,t,c)=>(await e.post("/backend/core/channel/move?_method=put",{fromId:a,toId:t,type:c})).data)),a("P",(async()=>(await e.post("/backend/core/channel/tidy-tree?_method=put")).data)),a("L",(async a=>(await e.post("/backend/core/channel?_method=delete",a)).data)),a("N",(async()=>(await e.get("/backend/core/channel/channel-permissions")).data)),a("M",(async()=>(await e.get("/backend/core/channel/channel-templates")).data)),a("n",(async()=>(await e.get("/backend/core/channel/article-templates")).data)),a("H",(async a=>(await e.get("/backend/core/channel/alias-exist",{params:{alias:a}})).data)),a("D",(async a=>(await e.get("/backend/core/article",{params:a})).data)),a("a",(async a=>(await e.get("/backend/core/article/reject-count",{params:a})).data)),a("f",(async a=>(await e.get(`/backend/core/article/${a}`)).data)),a("z",(async(a,t,c)=>(await e.get("/backend/core/article/title-similarity",{params:{similarity:a,title:t,excludeId:c}})).data)),a("g",(async a=>(await e.post("/backend/core/article",a)).data)),a("u",(async a=>(await e.post("/backend/core/article?_method=put",a)).data)),a("E",(async(a,t)=>(await e.post("/backend/core/article/update-order",{fromId:a,toId:t})).data)),a("A",(async a=>(await e.post("/backend/core/article/internal-push",a)).data)),a("B",(async a=>(await e.post("/backend/core/article/external-push",a)).data)),a("F",(async(a,t,c)=>(await e.post("/backend/core/article/sticky?_method=put",{ids:a,sticky:t,stickyDate:c})).data)),a("v",(async a=>(await e.post("/backend/core/article/delete?_method=put",a)).data)),a("w",(async a=>(await e.post("/backend/core/article/submit?_method=put",a)).data)),a("x",(async a=>(await e.post("/backend/core/article/archive?_method=put",a)).data)),a("y",(async a=>(await e.post("/backend/core/article/offline?_method=put",a)).data)),a("h",(async a=>(await e.post("/backend/core/article?_method=delete",a)).data)),a("q",(async a=>(await e.get("/backend/core/article-review/pending-count",{params:a})).data)),a("e",(async a=>(await e.get(`/backend/core/article-review/${a}`)).data)),a("p",(async(a,t,c)=>(await e.post(`/backend/core/article-review/pass/${a}?_method=put`,{properties:t,comment:c})).data)),a("o",(async(a,t,c)=>(await e.post("/backend/core/article-review/delegate?_method=put",{taskId:a,toUserId:t,comment:c})).data)),a("t",(async(a,t,c)=>(await e.post("/backend/core/article-review/transfer?_method=put",{taskId:a,toUserId:t,comment:c})).data)),a("r",(async(a,t,c)=>(await e.post("/backend/core/article-review/back?_method=put",{taskId:a,activityId:t,comment:c})).data)),a("s",(async(a,t)=>(await e.post("/backend/core/article-review/reject?_method=put",{taskIds:a,reason:t})).data)),a("Q",(async a=>(await e.get("/backend/core/block-item",{params:a})).data)),a("T",(async a=>(await e.get(`/backend/core/block-item/${a}`)).data)),a("U",(async a=>(await e.post("/backend/core/block-item",a)).data)),a("S",(async a=>(await e.post("/backend/core/block-item?_method=put",a)).data)),a("R",(async(a,t)=>(await e.post("/backend/core/block-item/update-order",{fromId:a,toId:t})).data)),a("G",(async a=>(await e.post("/backend/core/block-item?_method=delete",a)).data)),a("C",(async a=>(await e.get("/backend/core/dict",{params:a})).data)),a("l",(async(a,t)=>(await e.get("/backend/core/dict/list-by-alias",{params:{alias:a,name:t}})).data)),a("V",(async a=>(await e.get(`/backend/core/dict/${a}`)).data)),a("W",(async a=>(await e.post("/backend/core/dict",a)).data)),a("X",(async a=>(await e.post("/backend/core/dict?_method=put",a)).data)),a("Z",(async a=>(await e.post("/backend/core/dict/order?_method=put",a)).data)),a("Y",(async a=>(await e.post("/backend/core/dict?_method=delete",a)).data)),a("a3",(async()=>(await e.post("/backend/core/generator/fulltext-reindex-all")).data)),a("a4",(async()=>(await e.post("/backend/core/generator/fulltext-reindex-site")).data)),a("a5",(async()=>(await e.post("/backend/core/generator/html-all")).data)),a("a6",(async()=>(await e.post("/backend/core/generator/html-home")).data)),a("a7",(async()=>(await e.post("/backend/core/generator/html-channel")).data)),a("a8",(async()=>(await e.post("/backend/core/generator/html-article")).data)),a("a2",(async a=>(await e.get("/backend/core/tag",{params:a})).data)),a("i",(async a=>(await e.get("/backend/core/tag/list",{params:a})).data)),a("_",(async a=>(await e.get(`/backend/core/tag/${a}`)).data)),a("$",(async a=>(await e.post("/backend/core/tag",a)).data)),a("a0",(async a=>(await e.post("/backend/core/tag?_method=put",a)).data)),a("a1",(async a=>(await e.post("/backend/core/tag?_method=delete",a)).data)),a("c",(async a=>(await e.get("/backend/ext/form/reject-count",{params:a})).data)),a("b",(async a=>(await e.get("/backend/ext/form-review/pending-count",{params:a})).data))}}}));