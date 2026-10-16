# UJCMS 发版说明

## ujcms-13.1.5

* fix(console): 修复切换角色、组织权限设置时权限树回显错误及保存后丢失权限的问题；加载期间禁用保存，切换“全部”时保留已选权限

## ujcms-13.1.4

* feat(article): 文章管理重做，新增 Excel 导出、表格导入（商业版）及相关文章功能
* feat(article): DOC 导入内置纯 Java 引擎，支持 .docx/.doc/.wps，不再依赖 LibreOffice（商业版）
* feat(channel): 栏目管理重做，新增分支合并、批量新增、批量设置与大纲导出
* feat(model): 模型管理重做，新增字段定义校验、使用情况展示及音频上传字段
* feat(word): 敏感词、易错词词典重做，支持粘贴导入、导出及易错词一键修正
* feat(console): 控制台菜单、首页与外壳重做，新增 ⌘K 命令面板、快捷入口、个人外观设置及我的通知
* feat(console): 页面刷新后保持所在目录、树节点与页签；部分“类型 + 数据”页面合并为一个页面
* feat(data-migration): 数据迁移改为后台任务，支持预览与停止（商业版）
* feat(oauth): 第三方登录改在全局设置中配置
* feat(install): 安装向导新增 AI 服务商、机器翻译步骤及设置回顾
* fix(permission): 文章、表单的数据权限落实到全部操作，后台查询条件加固

## ujcms-13.1.2

* feat(auth): 新增短信验证码两步登录，含失败锁定与应急关闭开关
* feat(sms): 验证码绑定用途并限制尝试次数，加强发送频率限制
* feat(security): 加固 IPv6 支持
* fix(ui): 重新设计主题登录、注册等认证页面

## ujcms-13.1.1

* feat(console): 所有列表支持按 ID 搜索，ID 列默认隐藏；文章筛选条件更丰富
* fix(permission): 补齐专题权限
* fix(db): slug 迁移脚本兼容 Oracle 模式

## ujcms-13.1.0

* feat(permalinks)!: 文章、栏目、专题的 alias 更名为 slug，新增站点级固定链接模式
* feat(multilingual): 新增多语言翻译工作流，支持 AI 预翻译与批量新增译文（商业版）
* feat(ai): AI 改写、AI 标题等功能增强，AI 设置拆分为 AI服务商 与 AI功能（商业版）
* feat(dict)!: 字典整合为统一的字典管理
* feat(sitemap): 站点地图重做，新增搜索引擎推送
* feat(wechat): 微信公众号支持多图文群发与排版预设（商业版）
* feat(commercial): 新增 EaseBar 无障碍工具条（旗舰版）；部分商业功能调整所属版本
* feat(console): 完成英文控制台
* fix(common): 雪花 ID 加固时钟回拨处理

## ujcms-13.0.0

* feat(composition)!: 新增页面可视化构建器，页面由区域与组件拼装
* feat(theme)!: 主题改为结构化布局并以 Tailwind 重写默认主题，目录 webapp/templates 更名为 webapp/themes
* feat(theme): 新增主题市场、主题种子数据导出及主题沙箱预览
* feat(install): 新增安装向导
* feat(template): 新增模板编辑工作台，支持编辑历史对比与恢复
* feat(topic): 新增专题与扩展栏目
* feat(link)!: 友情链接替换为链接组
* feat(resource-library): 新增资源库
* feat(console): TinyMCE 升级至 6.8，Markdown 编辑器替换为 ByteMD
* feat(security): 新增等保三级合规套件（默认关闭）；敏感配置与用户手机号、邮箱加密存储
* build: Spring Boot 升级至 3.5.14

## ujcms-12.3.3

* build: Spring Boot 从 3.5.10 升级至 3.5.16
* fix(api): 加固前台公开接口，防止敏感数据探测与泄露：查询条件及排序字段禁止涉及用户、组织、角色等敏感表和密码、IP、联系方式等敏感字段（违规返回 400）；前台文章查询仅返回已发布/已归档文章，区块、字典、友情链接、表单类型、站点仅返回已启用数据；留言板仅展示已审核且公开的留言，并隐藏 IP、联系方式、电话、邮箱、地址等隐私字段；栏目、站点接口校验栏目访问权限及站点启用状态
* build(deps): 瀚高数据库驱动 HgdbJdbc 从 6.2.4 升级至 6.2.5

## ujcms-12.3.2

* fix(message): 私信会话记录按用户 ID 顺序先行写入，避免并发发送私信时产生死锁，并修复达梦、openGauss 等数据库下会话摘要为空导致私信发送失败的问题
* fix(domain): 修复区块项、文章、栏目的链接地址清空后保存时校验失败（400 错误）的问题
* fix(file): MinIO 共享 HTTP 客户端并缩短超时时间，对象存储请求异常时快速失败；修复获取文件时临时文件被提前删除的问题；编辑器上传音视频时显示上传进度提示，多站点下上传归属当前站点；后台模型列表显示模型类型

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
