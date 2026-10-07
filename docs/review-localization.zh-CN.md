# 审核内容中文显示与网页侧栏（2026-10-08）

## 网页：已上线

Cloudflare Pages 生产站点 `https://de-moderation-review-demo.pages.dev/` 左侧按钮改为：
审核工作台、裁决记录、申诉中心；底部按钮明确显示“退出登录”。
加宽侧栏、提高文字对比度，增加键盘焦点和当前页面状态。
窄屏底部导航同样显示完整名称并保留退出按钮。

ESLint、TypeScript、静态构建通过；生产部署为
`https://48419a80.de-moderation-review-demo.pages.dev`。
浏览器核对生产地址的新文字、页面切换、退出返回登录及重新登录通过。
最终截图保存于 `/private/tmp/de-review-sidebar-live.jpg`。

## Android：已部署、安装并完成真实线上验收

中文模式下审核队列的帖子标题、正文及评论显示机器译文。
卡片可切换查看举报原文；英文模式继续显示原文。
翻译在后台批量完成，最多每页 30 个案件，轮询次数有上限；
失败保留原文并提示刷新，不阻塞案件读取和裁决。

翻译必须属于**举报时快照**。旧 `/api/translations` 读取当前帖子版本，
不能直接当作审核证据译文。因此新增管理员接口：

```http
POST /api/admin/translations
{"language":"zh-CN","caseIds":["CASE_UUID"]}
```

返回 `cases` 与 `pending`；每条包含译文及对应原始 `sourceTitle/sourcePreview`。
客户端核对案件 ID 和原始预览，拒绝把编辑后的正文或另一案件的译文套到当前卡片。
缓存使用独立 `EVIDENCE` 类型及源文本哈希，后台调用复用既有 Gemini 翻译器。
角色由当前服务器 ADMIN 权限验证；普通成员和匿名请求被拒绝。
内容编辑、隐藏或删除不改变已保存的举报快照，翻译也不修改帖子、模型建议或审计。

V19 只扩展翻译缓存类型约束，不改动论坛内容；上线时由 Flyway 执行。

### 验证及发布状态

- 后端翻译与证据测试 8 项通过（真实 Testcontainers PostgreSQL，包含 V19）。
- 最终 Android 79 项单元测试通过，Lint 0 错误、284 警告；APK 构建成功。
- 2 项隔离设备测试通过：中文后台翻译、原文/译文切换、英文模式及现有裁决/申诉/审计流程。
- 初次设备验收的按钮选择器同时匹配了说明文字，已改为只选择 Button，最终两项通过。
- 镜像已构建：`ghcr.io/mingjie-mao/de-moderation-backend:review-translations-20261008`，linux/amd64。
- 初次 Docker 上传因旧认证返回 `unauthorized`；用户通过隐藏输入脚本上传成功。
- 仓库发布摘要：`sha256:85e61eac2924d68fd670343c0f5ea2620a14d39e287b94f419e7e8c3e8f7c8c8`，与本地构建一致。
- Northflank 已部署上述版本标签；实例 `de-moderation-api-66bdbfdb5-mg44h`，3/3 探针通过、0 次重启。
- 启动日志确认从 V18 成功迁移至 V19（1 项迁移）；应用启动耗时约 174.5 秒，进程约 182.4 秒。
- 公网 readiness 返回 UP/200；空案件翻译请求分别验证匿名 401、普通成员 403、管理员 200（空结果、pending=false），没有调用模型。
- 真实案件翻译验收初次被自动审批拦下，随后用户明确授权现有演示案件文本发送给当前 Gemini；授权后执行，没有绕过审批。
- `scripts/verify-admin-translations-live.py` 通过：3 个现有案件均有中文译文，举报版本匹配、缓存读回一致；匿名 401、成员 403。
- 首次缓存缺失请求 1876 ms 返回 pending，不等待模型；两个间隔 5 秒的轮询后译文就绪，缓存请求 1409 ms。以上为本次样本，不代表固定延迟。
- 真实截图发现模型标题多输出 `</i>`。客户端现在清除原文中不存在的格式标签；原文含标签时保留，原始证据及数据库译文缓存不改写，并加入对应单元测试。
- 最终修正版 APK 已覆盖安装，保留原数据；真实会话恢复及真实中文审核队列设备测试共 2 项通过（7.708 秒）。APK SHA-256 为 `3f5ca653df704093d83f0d4545a131e74f2e3d8183cc83e0ea79f454115104fb`。
- 最终中文截图 `/private/tmp/de-review-queue-chinese-live.png`，确认帖子标题、正文和两条评论均显示中文，异常标签不再显示。
- 实际模型验收结果保存在 Android 仓库忽略目录 `.local/admin-translations-live.json`；记录状态、计数和耗时，不保存令牌或案件正文。
- 部署截图 `/private/tmp/de-review-translations-deployed.jpg`；权限检查结果保存在 Android 仓库忽略目录 `.local/admin-translations-permissions-live.json`。
- 没有 Git commit 或 Git push；用户原有改动保留。

## 部署时额外观察

Northflank 现有 V18 实例显示一次 `OOMKilled` 重启，观察时仍为 3/3 探针通过，
内存约 456.81 MiB / 512 MiB，最近一小时约 2% 5xx。
这属于本次平台实际观察，尚未关联到用户历史失败请求；不能宣称历史超时已永久解决。
新实例部署完成后约 457.67 MiB / 512 MiB（89%），仍需关注内存余量。
运行时日志确认当前 `MaxRAMPercentage=60`；本次没有修改资源套餐或新增付费资源。
