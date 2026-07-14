# UJCMS 发版说明

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
