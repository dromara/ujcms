# UJCMS 发版说明

## ujcms-12.0.2

* fix(startup): 使用 @Lazy 延迟 OperationLogAdvisor 依赖，消除启动时 BeanPostProcessorChecker 的告警
* fix(security): 修复 FreeMarker 模板注入（SSTI）可被提权为远程代码执行（RCE）的安全漏洞

## ujcms-12.0.1

* fix: 修复管理员默认密码错误问题

## ujcms-12.0.0

* build!: 开源许可证变更为 GPL-3.0，同时提供商业授权，实行双重许可
