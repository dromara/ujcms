# UJCMS 发版说明

## ujcms-12.3.1

* fix(db): 修复初始化数据中友情链接类型晚于友情链接插入导致外键约束失败的问题

## ujcms-12.3.0

* feat(templates): 页脚上方新增全站友情链接栏，按类型分组下拉展示，图片类型链接支持展示 Logo
* feat(schedule-task): 定时任务新增可视化调度配置（每小时/每天/每周/每月），自定义模式支持 Quartz 表达式并即时校验合法性
* feat(friend-link): 新增友情链接类型，支持按类型分组，并可配置文字/图片展示方式及 Logo 裁剪尺寸
* fix(console): 修复角色权限树中“数据迁移”复选框保存后重新打开不回显的问题
* feat(cms): 新增定时任务功能，支持定时执行 HTML 生成、全文索引及定时采集（商业版）；增强数据采集稳定性，支持运行中停止/暂停、增量采集去重及 HTTP 超时控制
* feat(cms): 新增友情链接功能，支持后台管理、[@FriendLinkList] 模板标签及前台接口

## ujcms-12.2.1

* fix(templates): 修复文库 PDF 阅读器因 pdf.js 资源地址携带 ?v= 后缀导致无法打开的问题，并接入本地字体、wasm 等资源目录
* fix(templates): 评论、收藏、关注等操作提示改为即时弹出，验证码错误时返回友好提示并自动刷新验证码
* fix(freemarker): 模板安全策略放行 Class 的名称类安全方法，避免误拦截正常的模板调用
* fix(scope): 角色、区块的共享范围未修改时不再校验，避免存量数据不符合规则导致无法保存

## ujcms-12.2.0

* feat(recommend): “猜你喜欢”接入浏览历史作为推荐依据，未登录用户也可查看，首页右侧栏新增推荐卡片并支持“换一批”
* feat(view-history): 新增会员浏览历史记录，会员中心新增“最近浏览”页面，支持单条删除及清空
* build(ci): 精简 pnpm 构建输出，修正演示站流水线路径

## ujcms-12.1.0

* feat(member-center): 新增会员中心，包含个人主页、我的评论、资料完善及统一的设置入口
* feat(follow-users): 新增用户关注功能，支持关注用户及粉丝列表
* feat(comment): 评论支持 @ 提及用户，并发送提醒通知
* feat(comment): 评论支持上传图片，并校验附件归属
* feat(message): 新增私信功能，支持后台发送私信及批量群发
* feat(friend): 新增好友功能，支持好友申请、同意流程及会员好友页面
* feat(follow): 新增文章、栏目关注功能，并推送关注通知
* feat(notification): 新增通知系统，支持发布、审核等场景触发通知
* feat(favorite): 新增文章收藏功能，支持个人收藏夹分组
* feat(comment): 新增文章评论功能，支持按站点独立配置
* fix(site): 修复子目录站点的 URL，改为根相对路径

## ujcms-12.0.2

* fix(startup): 使用 @Lazy 延迟 OperationLogAdvisor 依赖，消除启动时 BeanPostProcessorChecker 的告警
* fix(security): 修复 FreeMarker 模板注入（SSTI）可被提权为远程代码执行（RCE）的安全漏洞

## ujcms-12.0.1

* fix: 修复管理员默认密码错误问题

## ujcms-12.0.0

* build!: 开源许可证变更为 GPL-3.0，同时提供商业授权，实行双重许可
