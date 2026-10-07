# 当前版本完整回归（2026-10-07）

本轮针对 Desktop 两个仓库的当前未提交工作区，后端线上 V18。
不执行 Git commit/push，不触发云端 CI，不迁移地区。

## 执行范围

- 后端 `mvn verify`：全部默认单元及 Testcontainers PostgreSQL/S3Mock 集成测试。
- Android：全部单元测试、完整 Debug Lint、APK/测试 APK 构建；独立测试包执行全部交互、图片和 Keystore 设备场景。
- 真实进程终止/恢复及线上审核列表验证；保留用户原会话和偏好数据。
- 网页管理端：ESLint、TypeScript、Cloudflare Pages 静态构建与线上访问。
- 发布脚本测试；线上原始数据、角色权限、资料/头像/R2、翻译缓存和审核流程验收。
- 文档当前版本、迁移清单、链接和历史记录范围核对。

## 回归发现及处理

1. Android 头像加载代理使用普通 ImageView，被完整 Lint 判为错误；改为 AppCompatImageView，保持弱引用及加载行为。
2. 两个后端集成测试错误地假设目标案件总在首屏。共享数据库累计案件超过页面上限，改为逐页定位并保留内容/证据断言。
3. “成员不能进入管理员工作台”设备测试实际点了 Member；改为明确选择 Admin，验证服务器成员身份被拒绝。
4. 网页预渲染需要回环端口，首次被沙箱拒绝；允许本次本地构建监听后通过，不属于线上服务缺陷。

5. 设备回归复现了刷新撤销与首页创建/恢复的竞态：账号绑定可能在初次登录检查之后失效。首页现在在同一会话锁内绑定/读取身份，确认撤销时返回登录，避免抛异常崩溃。
6. 真实翻译模型对注入指令可能原样引用而非翻译；测试调整为正常文字必须中文、恶意内容只能翻译或精确引用，不能执行 `PWNED`。未改变生产 Prompt 或放宽普通翻译断言。

## 最终结果

| 检查 | 结果 |
| --- | --- |
| 后端默认 `mvn verify` | 420 项，407 通过、13 个外部凭据门控测试跳过；0 失败/错误，构建成功 |
| 补跑真实 R2 | 10 项通过；仅随机 `contract-test/` 前缀写入、列举、读取、删除及清理 |
| 补跑真实 Gemini 翻译 | 1 项通过；普通文字译成中文，注入文本不作为指令执行 |
| 后端最终唯一测试计数 | 418 项通过、2 项可选真实调查测试未执行；不是把多次运行数量相加 |
| Android 单元测试 | 71 项通过、无跳过 |
| Android Debug Lint | 0 错误、283 条警告 |
| Android APK 与测试 APK | 最终构建成功，已覆盖安装 emulator-5556，保留原 App 数据 |
| Android 设备方法 | 26 项通过：19 项独立交互/图片/Keystore、2 项跨进程夹具、4 项真实两角色会话/审核、1 项应用上下文 |
| 网页管理端 | ESLint、TypeScript、Pages 静态构建成功；线上刷新、案件正文、模型依据和审计读取通过 |
| Python 发布脚本 | 4 项通过，默认检查不会提交、推送或索要令牌 |
| 文档链接 | 本轮文档本地链接校验无缺失；历史清单明确标注快照日期 |
| Git | 两个仓库 diff 格式检查通过；HEAD 未改变，不 commit、不 push |

默认回归使用真实 PostgreSQL 16 与 S3Mock；线上数据库为 Neon 18.6/V18。
本轮真实桶和模型补测只读取必要环境变量到子进程内存，未公开凭据。
未运行的 2 项是 `RealModelInvestigationBenchmarkTest` 与
`RealModelInvestigationSmokeTest`；公开演示默认关闭调查 Agent，未重做整套
模型评测、负载/容量测试或云端 CI。

## 线上功能验收

- 原始数据读回：47 篇可见帖子；成员本人 3 篇帖子、16 条评论，9 个点赞、8 个收藏、3 个关注、10 个粉丝。
- 通知、4 个虚拟市场、24 条导入行情、8 位原始榜单成员和 18 条导入交易记录通过。
- 独立 QA 账号的语言、主题、头像归属、重新登录读回、公开资料隐私和 R2 图片字节读取通过。
- 翻译 API、既有帖子/评论翻译及持久缓存读回通过。缓存命中并不证明每次在线读取都重新调用模型；另补跑了直接真实模型测试。
- 新建一条独立 QA 临时帖子：举报聚合 → `gemini-3.5-flash-lite/v2` 建议 → 人工隐藏 → 作者申诉 → 管理员恢复 → 审计读回，完整通过。
- 成员访问管理 API 返回 403。模型完成后帖子仍可见，直到人工决定。
- QA 临时帖子已软删除；案件、申诉和审计作为真实操作记录保留。原有内容未修改。
- 用户 App 真实进程停止后恢复原选中账号；真实审核列表显示正文和状态，没有 UUID/模型调试信息。

## 剩余限制

- 283 条 Lint 警告尚未全部清理：主要为 120 条未使用资源、27 条复数候选、26 条图片可访问性说明，另有样式、过度绘制和依赖版本提示。这次没有屏蔽检查或建立 baseline 来隐藏问题。
- 后端美国中部、数据库悉尼；登录和评论首次加载仍有秒级等待。历史偶发超时没有完整关联证据，不宣称根因全部解决。
- 当前改动仍未提交/推送。别人克隆 GitHub 不会自动拿到本轮修复；此次结果是本地与线上验收，不能写成新云端 CI 已通过。
- Render 旧 V14 镜像不包含当前全部 API，不能直接作为新版 App 的完整备用后端。

## 复现与证据

```bash
# 在 De-moderation 根目录（JDK 21，Docker 可用）
mvn verify
(cd admin-web && npm run lint && npx tsc --noEmit && npm run build:pages)

# 在 De-discussion 根目录（已配置 JDK 21/Android SDK）
(cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest)
python3 -m unittest discover -s scripts/tests -v
```

Android 夹具设备测试必须使用独立 applicationId，不能在用户原 App 上运行会清理会话的夹具。
`SessionProcessRestartTest` 的两个方法分别运行，期间仅停止测试包进程。
真实服务器测试必须显式指定 `allowLive=true` 和匹配的 `liveOrigin`；原 App
只运行只读验收与现有账号恢复，不清除用户数据。临时测试包已卸载。

脱敏汇总在 Android 仓库忽略目录 `.local/current-regression-verification.json`。
默认 Maven 日志 `/private/tmp/de-full-backend-regression.log`，外部测试最终结果
在 Surefire XML；Android 日志 `/private/tmp/de-full-android-regression.log`、
`/private/tmp/de-full-isolated-device.log` 和 `/private/tmp/de-full-device-processes.log`。
中间设备运行曾有一次申诉审计跳转等待未达成；修复会话启动竞态后的最终完整 19 项套件通过。保留中间失败日志，不能据单次通过承诺所有网络/设备环境无偶发等待。部分失败诊断日志含测试数据库生成的临时 JWT，不应作为公开附件提交。

网页截图 `/private/tmp/de-current-web-regression.png`；Android 真实案件截图
`/private/tmp/de-current-android-regression.png`。镜像仍为已验收的 V18 digest；
本轮生产行为修复仅发生在 Android，已构建并安装，无需重新发布后端镜像。


最终 APK SHA-256：`634fbba7fd5501ad089c2ea19a06690466efac85196c4d7bdda1beb4528749a1`。
