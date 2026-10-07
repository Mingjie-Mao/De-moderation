# 项目文件与文档表格核查

核查日期：2026 年 10 月 3 日（Australia/Sydney）。

本报告保留核查时的文件与表格快照；随后主 `README.md` 已按要求精简，独立 `README.zh-CN.md` 已删除，下方旧清单与字节数不自动刷新。

## 当前状态更新（2026-10-07）

下方 APK 地址、成员映射登录和 Render 限制均为 10 月 3 日的历史快照。
当前 Android 已提供真实服务器成员登录及注册；公开成员账号为 `1234` / `1234`，
管理员为 `12345` / `12345`，管理员权限由后端验证。默认 API 已迁移到
`https://p01--de-moderation-api--z48dx52bgz5k.code.run`，旧 Render 默认设置会迁移一次，
自定义地址保留。网页沿用 Cloudflare Pages 地址。两个账号在模拟器中登录并加载
11 条线上帖子已通过。最新部署、验证和回退步骤见 [Northflank 说明](../deploy/northflank/README.md)。

当前数据库为 V18，头像/语言/主题同步与加密会话恢复均已上线。审核列表显示内容摘要；最新完整回归见[当前验收](current-regression.zh-CN.md)。当前工作目录为 Desktop 下两个仓库。下面的文件清单、工作树路径、功能缺口和表格统计均为 10 月 3 日的历史审计，不是今日状态。

## 历史范围与结论（2026-10-03）

两个仓库的文件清单以 Git 已跟踪文件及未被忽略的新文件为准，删除项不计入。清单快照共 775 项（不含本报告），包含原有已跟踪的编译产物。`.git`、依赖目录、被忽略的构建缓存、私有 `.env` 和真实备份不作为公开项目文件登记。未读取或输出凭据。

后端核查使用正在编辑的 `codex/moderation-hardening` 工作树，路径为 `/Users/mingjie/.codex/worktrees/moderation-hardening/De-moderation`；桌面 `main` 是另一个 checkout，不是这些未提交改动的所在目录。Android 使用 `/Users/mingjie/Desktop/De-discussion`。

已检查原有 17 份 Markdown 中的 128 张表格（包含本次给 Android README 新增的两张地址表），没有列数不一致或本地文件链接缺失。主要评测结果表与 JSON 的状态、accuracy、Macro-F1、样本数和混淆矩阵总数相符；已修正下列文字事实问题。未对每张自动生成的逐样本内容表做人工语义裁决，未重新调用模型或运行压测。

文件登记不代表所有 Java、界面资源和历史编译产物已逐行审计；这次任务重点是文件组织、文档表格、描述与实现及已有证据的一致性。APK 地址和运行行为保持不变，未创建 Git 提交。

## 历史 APK 地址（2026-10-03）

| 情况 | 实际含义 |
|---|---|
| 新安装默认值 | `http://10.0.2.2:8080`，供 Android 模拟器访问电脑本地后端 |
| 已保存设置 | 优先于默认地址；需在 App 设置查看实际保存值 |
| Mac 浏览器 | 模拟器特殊地址不适用；本地服务使用 `http://localhost:8080` |
| 真机线上演示 | 可设置为 `https://de-moderation-api-demo.onrender.com` |
| Release | 不允许明文 HTTP，需要 HTTPS 地址 |

`1234` 是本地演示入口，选 Admin 不会获得线上权限。后端成员 API 真实读写数据库，但 App 使用自动生成的映射账号，尚无完整交互注册登录 UI。不能将后端 API 能力等同于当前 Android 登录界面的能力。

## 历史修正清单（2026-10-03）

| 项目 | 核查发现 | 已处理 |
|---|---|---|
| API 权限表 | 媒体被笼统写为登录用户可用，实际可见图片支持匿名读取 | 区分公开读取与登录上传，并保留可见性检查说明 |
| APK 默认地址 | 代码仍为 `http://10.0.2.2:8080`；已保存设置优先 | 保持代码不变，补充默认、本地、真机和线上地址表 |
| Android 登录 | `1234` 仅为本地入口；后台使用安装级映射成员账号 | 修正两份客户端 README、接入指南和完整报告；避免宣称完整账号 UI |
| 离线/在线演示 | README 在默认在线模式下直接介绍 App 内管理员队列 | 明确先关闭在线模式；线上管理员使用浏览器控制台 |
| 本地游戏功能 | Heat Market、代币、点赞、排行榜容易被理解为线上持久化 | 明确是本地演示功能，不宣称后端交易或跨设备同步 |
| 图片存储 | 报告仍称 Render 临时目录，实际使用私有 R2 | 中英文报告修正为 S3/R2，保留本地目录作为可选实现 |
| 数据库版本与迁移表 | 开发 PostgreSQL 16 与 Neon 18 混写；迁移表止于 V9 | 区分环境，并补全 V10–V13 与用途 |
| 验证记录 | 两份报告的旧 MinIO、测试数与压测数不一致 | 改为 2026-10-03 有证据的 23 项针对性验证；不冒充全量测试 |
| 压测结论 | 旧正文称全部通过，实际保存结果只有 7/8 阈值通过 | 引用真实汇总；登录失败率 92.3% 与限流冲突，保留为已知限制 |
| CORS 配置 | 接入指南要求把 API origin 放入允许列表 | 修正为管理网页 origin |
| 评测与演示脚本 | 脚本沿用 0.636，当前保存结果为 0.617 | 改为 0.617 → 0.924；管理员初始化改用现有 bootstrap |
| 调用保证与调查助手 | 一次案件被误写为模型只调用一次；只读工具被误写为不写任何东西 | 说明重试/恢复可再次调用；调查简报和调用记录仍写审计 |
| 退出与邮件 | 普通退出被写成服务端撤销；重置邮件未开启 | 说明普通退出清本地会话、logout-all 服务端撤销；演示不支持邮件找回 |
| 调查评测历史 | 16 场景表可能被当作当前 32 场景结果；预取工具描述错误 | 保留历史数字并标记范围；预取 authorHistory 和 similarResolvedCases |
| CI 与注释 | CI 仍称 MinIO；测试注释称生产也是 PostgreSQL 16；429 被说成完全不计失败 | 更新注释，未修改运行行为；未提交的版本无新云端 CI 结果 |

## 文档与表格总表

| 仓库 | 文件 | 表格数 | 核查结果 |
|---|---|---|---|
| backend | `README.md` | 4 | 修正数据库环境、模型调用保证、调查证据强度、邮件边界 |
| backend | `README.zh-CN.md` | 4 | 同步英文边界；文档索引表增加列名 |
| backend | `docs/api-client-guide.md` | 0 | 修正 CORS、刷新失败处理、Android 映射账号和地址 |
| backend | `docs/architecture.md` | 1 | 区分本地 PostgreSQL 16 / 演示 18 |
| backend | `docs/backend-project-report.md` | 14 | 修正当前部署、迁移表、账号入口、验证与压测记录 |
| backend | `docs/backend-project-report.zh-CN.md` | 14 | 与英文报告统一核心事实和当前证据 |
| backend | `docs/demo-script.md` | 0 | 修正当前评测数字、管理员初始化和降级演示范围 |
| backend | `docs/evaluation-heldout-v2v3.md` | 22 | 主要结果表已对照 JSON；保留自动生成报告 |
| backend | `docs/evaluation-heldout.md` | 24 | 主要结果表已对照 JSON；单次代表运行与跨运行均值不同是正常的 |
| backend | `docs/evaluation-notes.md` | 9 | 核心数字与报告一致；明确自建数据不等于线上准确率 |
| backend | `docs/evaluation.md` | 27 | 主要结果表已对照 JSON；保留自动生成报告 |
| backend | `docs/investigation.md` | 3 | 标记历史 16 场景数据；修正预取工具和样本规模表述 |
| backend | `docs/production-runbook.md` | 0 | 补充 Android 演示账号与地址边界 |
| backend | `docs/reliability.md` | 0 | 移除模型 exactly-once 的错误保证 |
| backend | `docs/security-decisions.md` | 0 | 修正管理员创建、普通退出与令牌撤销说明 |
| android | `readme.md` | 3 | 明确本地登录、玩法、离线队列和线上地址 |
| android | `readme.zh-CN.md` | 3 | 同步英文 README 的演示边界 |

## 历史限制（部分已由后续实现解决）

| 限制 | 对简历演示的影响 |
|---|---|
| 免费 Render 冷启动 | 演示前打开 readiness；不代表核心功能未完成 |
| 自建评测数据 | 可展示实验改进，不能声称线上准确率 |
| 调查助手完整新版评测未完成 | 默认关闭；只作为可选实验，不能声称当前全量实测通过 |
| 混合压测登录项与限流冲突 | 不能声称全部压测通过或据此宣称线上高并发容量 |
| 邮件关闭 | 不演示邮件找回密码；无需继续配置邮件与告警 |
| Android 真实账号 UI 未实现 | 演示时明确本地入口和映射成员账号；无需在文档中虚构完整登录界面 |
| 旧编译产物与 IDE 文件 | Android 清单含 78 个 `out/` 文件和 4 个 `.idea/` 文件；不属于简历亮点，本次未删除 |

## 全部项目文件清单

文档已检查文字与表格；评测输出检查主要指标；其他条目以登记分类为主。路径相对于各仓库根目录。

### backend（374 项）

| 文件 | 类型 | 字节数 |
|---|---|---|
| `.claude/launch.json` | 构建 / 其他项目文件 | 207 |
| `.dockerignore` | 构建 / 其他项目文件 | 180 |
| `.env.example` | 构建 / 其他项目文件 | 5435 |
| `.github/workflows/ci.yml` | CI 配置 | 1367 |
| `.gitignore` | 构建 / 其他项目文件 | 812 |
| `Dockerfile` | 配置 / 部署 | 660 |
| `README.md` | 说明文档 | 18203 |
| `README.zh-CN.md` | 说明文档 | 16722 |
| `admin-web/.dockerignore` | 管理网页 / 构建配置 | 99 |
| `admin-web/.gitignore` | 管理网页 / 构建配置 | 392 |
| `admin-web/Dockerfile` | 管理网页 / 构建配置 | 948 |
| `admin-web/app/api-request.ts` | 管理网页 / 构建配置 | 917 |
| `admin-web/app/globals.css` | 管理网页 / 构建配置 | 11214 |
| `admin-web/app/layout.tsx` | 管理网页 / 构建配置 | 741 |
| `admin-web/app/page.tsx` | 管理网页 / 构建配置 | 27207 |
| `admin-web/app/reset-password/page.tsx` | 管理网页 / 构建配置 | 3373 |
| `admin-web/eslint.config.mjs` | 管理网页 / 构建配置 | 345 |
| `admin-web/next.config.ts` | 管理网页 / 构建配置 | 341 |
| `admin-web/package-lock.json` | 管理网页 / 构建配置 | 302659 |
| `admin-web/package.json` | 管理网页 / 构建配置 | 989 |
| `admin-web/public/favicon.svg` | 管理网页 / 构建配置 | 712 |
| `admin-web/tsconfig.json` | 管理网页 / 构建配置 | 689 |
| `admin-web/vite.config.ts` | 管理网页 / 构建配置 | 222 |
| `deploy/Caddyfile` | 配置 / 部署 | 598 |
| `docker-compose.prod.yml` | 配置 / 部署 | 3774 |
| `docker-compose.yml` | 配置 / 部署 | 557 |
| `docs/api-client-guide.md` | 说明文档 | 4265 |
| `docs/architecture.md` | 说明文档 | 6602 |
| `docs/backend-project-report.md` | 说明文档 | 42096 |
| `docs/backend-project-report.zh-CN.md` | 说明文档 | 36737 |
| `docs/demo-script.md` | 说明文档 | 5364 |
| `docs/evaluation-heldout-samples.csv` | 评测数据 / 输出 | 87274 |
| `docs/evaluation-heldout-samples.json` | 评测数据 / 输出 | 28612 |
| `docs/evaluation-heldout-v2v3-samples.csv` | 评测数据 / 输出 | 66191 |
| `docs/evaluation-heldout-v2v3.json` | 评测数据 / 输出 | 37883 |
| `docs/evaluation-heldout-v2v3.md` | 说明文档 | 23741 |
| `docs/evaluation-heldout.json` | 评测数据 / 输出 | 44990 |
| `docs/evaluation-heldout.md` | 说明文档 | 36142 |
| `docs/evaluation-notes.md` | 说明文档 | 21847 |
| `docs/evaluation-samples.csv` | 评测数据 / 输出 | 157594 |
| `docs/evaluation-samples.json` | 评测数据 / 输出 | 58446 |
| `docs/evaluation.json` | 评测数据 / 输出 | 66764 |
| `docs/evaluation.md` | 说明文档 | 38110 |
| `docs/investigation-benchmark-inv-v4-consensus3.json` | 评测数据 / 输出 | 13249 |
| `docs/investigation-benchmark-inv-v4.json` | 评测数据 / 输出 | 15413 |
| `docs/investigation-benchmark-inv-v5.json` | 评测数据 / 输出 | 15770 |
| `docs/investigation.md` | 说明文档 | 9968 |
| `docs/production-runbook.md` | 说明文档 | 4554 |
| `docs/reliability.md` | 说明文档 | 6439 |
| `docs/security-decisions.md` | 说明文档 | 7297 |
| `load/k6-mixed.js` | 手动脚本 / 压测 | 15756 |
| `load/k6-smoke.js` | 手动脚本 / 压测 | 606 |
| `pom.xml` | 构建 / 其他项目文件 | 7895 |
| `scripts/backup.sh` | 手动脚本 / 压测 | 2439 |
| `scripts/restore-drill.sh` | 手动脚本 / 压测 | 8329 |
| `scripts/restore.sh` | 手动脚本 / 压测 | 1060 |
| `scripts/verify-backup.sh` | 手动脚本 / 压测 | 982 |
| `src/main/java/com/campusguard/CampusGuardApplication.java` | Java 实现 | 329 |
| `src/main/java/com/campusguard/appeal/AdminAppealController.java` | Java 实现 | 1081 |
| `src/main/java/com/campusguard/appeal/Appeal.java` | Java 实现 | 2240 |
| `src/main/java/com/campusguard/appeal/AppealController.java` | Java 实现 | 981 |
| `src/main/java/com/campusguard/appeal/AppealDecision.java` | Java 实现 | 81 |
| `src/main/java/com/campusguard/appeal/AppealDecisionRequest.java` | Java 实现 | 231 |
| `src/main/java/com/campusguard/appeal/AppealRepository.java` | Java 实现 | 905 |
| `src/main/java/com/campusguard/appeal/AppealService.java` | Java 实现 | 4437 |
| `src/main/java/com/campusguard/appeal/AppealStatus.java` | Java 实现 | 90 |
| `src/main/java/com/campusguard/appeal/AppealView.java` | Java 实现 | 595 |
| `src/main/java/com/campusguard/appeal/CreateAppealRequest.java` | Java 实现 | 296 |
| `src/main/java/com/campusguard/audit/AuditActorType.java` | Java 实现 | 409 |
| `src/main/java/com/campusguard/audit/AuditEntry.java` | Java 实现 | 3268 |
| `src/main/java/com/campusguard/audit/AuditEntryRepository.java` | Java 实现 | 373 |
| `src/main/java/com/campusguard/audit/AuditLogger.java` | Java 实现 | 2498 |
| `src/main/java/com/campusguard/auth/AuthConfig.java` | Java 实现 | 279 |
| `src/main/java/com/campusguard/auth/AuthController.java` | Java 实现 | 4483 |
| `src/main/java/com/campusguard/auth/AuthRateLimitProperties.java` | Java 实现 | 809 |
| `src/main/java/com/campusguard/auth/AuthService.java` | Java 实现 | 4691 |
| `src/main/java/com/campusguard/auth/ChangePasswordRequest.java` | Java 实现 | 270 |
| `src/main/java/com/campusguard/auth/LoginRequest.java` | Java 实现 | 165 |
| `src/main/java/com/campusguard/auth/PasswordResetConfirmRequest.java` | Java 实现 | 266 |
| `src/main/java/com/campusguard/auth/PasswordResetDelivery.java` | Java 实现 | 130 |
| `src/main/java/com/campusguard/auth/PasswordResetDeliveryConfig.java` | Java 实现 | 2147 |
| `src/main/java/com/campusguard/auth/PasswordResetRequest.java` | Java 实现 | 205 |
| `src/main/java/com/campusguard/auth/PasswordResetService.java` | Java 实现 | 3051 |
| `src/main/java/com/campusguard/auth/PasswordResetToken.java` | Java 实现 | 1546 |
| `src/main/java/com/campusguard/auth/PasswordResetTokenRepository.java` | Java 实现 | 446 |
| `src/main/java/com/campusguard/auth/RefreshToken.java` | Java 实现 | 1593 |
| `src/main/java/com/campusguard/auth/RefreshTokenRepository.java` | Java 实现 | 467 |
| `src/main/java/com/campusguard/auth/RefreshTokenRequest.java` | Java 实现 | 148 |
| `src/main/java/com/campusguard/auth/RefreshTokenService.java` | Java 实现 | 2204 |
| `src/main/java/com/campusguard/auth/RegisterRequest.java` | Java 实现 | 888 |
| `src/main/java/com/campusguard/auth/RequestRateLimiter.java` | Java 实现 | 3687 |
| `src/main/java/com/campusguard/auth/TokenResponse.java` | Java 实现 | 491 |
| `src/main/java/com/campusguard/comment/Comment.java` | Java 实现 | 3674 |
| `src/main/java/com/campusguard/comment/CommentContent.java` | Java 实现 | 589 |
| `src/main/java/com/campusguard/comment/CommentContentValidator.java` | Java 实现 | 1146 |
| `src/main/java/com/campusguard/comment/CommentController.java` | Java 实现 | 3454 |
| `src/main/java/com/campusguard/comment/CommentPage.java` | Java 实现 | 787 |
| `src/main/java/com/campusguard/comment/CommentRepository.java` | Java 实现 | 2567 |
| `src/main/java/com/campusguard/comment/CommentResponse.java` | Java 实现 | 787 |
| `src/main/java/com/campusguard/comment/CommentService.java` | Java 实现 | 8749 |
| `src/main/java/com/campusguard/comment/CreateCommentRequest.java` | Java 实现 | 414 |
| `src/main/java/com/campusguard/comment/UpdateCommentRequest.java` | Java 实现 | 220 |
| `src/main/java/com/campusguard/common/ApiExceptionHandler.java` | Java 实现 | 8197 |
| `src/main/java/com/campusguard/common/AuthorView.java` | Java 实现 | 536 |
| `src/main/java/com/campusguard/common/ConflictException.java` | Java 实现 | 322 |
| `src/main/java/com/campusguard/common/ContentRateLimitConfig.java` | Java 实现 | 297 |
| `src/main/java/com/campusguard/common/ContentRateLimitProperties.java` | Java 实现 | 1445 |
| `src/main/java/com/campusguard/common/NotFoundException.java` | Java 实现 | 171 |
| `src/main/java/com/campusguard/common/PageCursor.java` | Java 实现 | 1965 |
| `src/main/java/com/campusguard/common/TargetType.java` | Java 实现 | 349 |
| `src/main/java/com/campusguard/common/TooManyRequestsException.java` | Java 实现 | 414 |
| `src/main/java/com/campusguard/config/OpenApiConfig.java` | Java 实现 | 1643 |
| `src/main/java/com/campusguard/evaluation/BenchmarkReport.java` | Java 实现 | 1692 |
| `src/main/java/com/campusguard/evaluation/BenchmarkService.java` | Java 实现 | 7025 |
| `src/main/java/com/campusguard/evaluation/ConfusionMatrix.java` | Java 实现 | 5269 |
| `src/main/java/com/campusguard/evaluation/CostEstimator.java` | Java 实现 | 1767 |
| `src/main/java/com/campusguard/evaluation/EngineComparator.java` | Java 实现 | 3920 |
| `src/main/java/com/campusguard/evaluation/EngineRunStatus.java` | Java 实现 | 627 |
| `src/main/java/com/campusguard/evaluation/EngineRuns.java` | Java 实现 | 7650 |
| `src/main/java/com/campusguard/evaluation/EvaluationCommand.java` | Java 实现 | 4544 |
| `src/main/java/com/campusguard/evaluation/EvaluationCsvWriter.java` | Java 实现 | 2998 |
| `src/main/java/com/campusguard/evaluation/EvaluationDataset.java` | Java 实现 | 2171 |
| `src/main/java/com/campusguard/evaluation/EvaluationJsonWriter.java` | Java 实现 | 10649 |
| `src/main/java/com/campusguard/evaluation/EvaluationProperties.java` | Java 实现 | 2842 |
| `src/main/java/com/campusguard/evaluation/EvaluationReportWriter.java` | Java 实现 | 33715 |
| `src/main/java/com/campusguard/evaluation/EvaluationResult.java` | Java 实现 | 7924 |
| `src/main/java/com/campusguard/evaluation/EvaluationRunner.java` | Java 实现 | 10302 |
| `src/main/java/com/campusguard/evaluation/EvaluationUsageCollector.java` | Java 实现 | 2312 |
| `src/main/java/com/campusguard/evaluation/LabelledSample.java` | Java 实现 | 4029 |
| `src/main/java/com/campusguard/evaluation/SampleOutcome.java` | Java 实现 | 2096 |
| `src/main/java/com/campusguard/evaluation/SampleProvenance.java` | Java 实现 | 1778 |
| `src/main/java/com/campusguard/evaluation/corpus/DecisionCorpusCommand.java` | Java 实现 | 5216 |
| `src/main/java/com/campusguard/evaluation/corpus/DecisionCorpusExporter.java` | Java 实现 | 10323 |
| `src/main/java/com/campusguard/evaluation/corpus/DecisionSample.java` | Java 实现 | 3583 |
| `src/main/java/com/campusguard/evaluation/corpus/LabelBasis.java` | Java 实现 | 1946 |
| `src/main/java/com/campusguard/media/AdminMediaController.java` | Java 实现 | 2374 |
| `src/main/java/com/campusguard/media/FilesystemMediaStorage.java` | Java 实现 | 4787 |
| `src/main/java/com/campusguard/media/MediaConfig.java` | Java 实现 | 3439 |
| `src/main/java/com/campusguard/media/MediaController.java` | Java 实现 | 2278 |
| `src/main/java/com/campusguard/media/MediaObject.java` | Java 实现 | 1906 |
| `src/main/java/com/campusguard/media/MediaObjectRepository.java` | Java 实现 | 2363 |
| `src/main/java/com/campusguard/media/MediaProperties.java` | Java 实现 | 4974 |
| `src/main/java/com/campusguard/media/MediaResponse.java` | Java 实现 | 414 |
| `src/main/java/com/campusguard/media/MediaService.java` | Java 实现 | 6729 |
| `src/main/java/com/campusguard/media/MediaStorage.java` | Java 实现 | 3470 |
| `src/main/java/com/campusguard/media/MediaStorageException.java` | Java 实现 | 607 |
| `src/main/java/com/campusguard/media/MediaSweep.java` | Java 实现 | 7092 |
| `src/main/java/com/campusguard/media/MediaSweepScheduler.java` | Java 实现 | 2131 |
| `src/main/java/com/campusguard/media/MediaUrls.java` | Java 实现 | 297 |
| `src/main/java/com/campusguard/media/S3MediaStorage.java` | Java 实现 | 6845 |
| `src/main/java/com/campusguard/moderation/CaseStatus.java` | Java 实现 | 893 |
| `src/main/java/com/campusguard/moderation/ContentLocator.java` | Java 实现 | 7878 |
| `src/main/java/com/campusguard/moderation/FinalAction.java` | Java 实现 | 425 |
| `src/main/java/com/campusguard/moderation/ModerationCase.java` | Java 实现 | 9396 |
| `src/main/java/com/campusguard/moderation/ModerationCaseProcessor.java` | Java 实现 | 11678 |
| `src/main/java/com/campusguard/moderation/ModerationCaseRepository.java` | Java 实现 | 10691 |
| `src/main/java/com/campusguard/moderation/ModerationCaseService.java` | Java 实现 | 2981 |
| `src/main/java/com/campusguard/moderation/ModerationConfig.java` | Java 实现 | 1050 |
| `src/main/java/com/campusguard/moderation/ModerationDecision.java` | Java 实现 | 664 |
| `src/main/java/com/campusguard/moderation/ModerationMetrics.java` | Java 实现 | 1281 |
| `src/main/java/com/campusguard/moderation/ModerationProperties.java` | Java 实现 | 1227 |
| `src/main/java/com/campusguard/moderation/ModerationStatusController.java` | Java 实现 | 1464 |
| `src/main/java/com/campusguard/moderation/ModerationStatusResponse.java` | Java 实现 | 589 |
| `src/main/java/com/campusguard/moderation/ModerationWorker.java` | Java 实现 | 1835 |
| `src/main/java/com/campusguard/moderation/ModerationWorkerScheduler.java` | Java 实现 | 1720 |
| `src/main/java/com/campusguard/moderation/admin/AdminModerationController.java` | Java 实现 | 3387 |
| `src/main/java/com/campusguard/moderation/admin/AdminModerationService.java` | Java 实现 | 18139 |
| `src/main/java/com/campusguard/moderation/admin/CaseAuditEntryView.java` | Java 实现 | 633 |
| `src/main/java/com/campusguard/moderation/admin/CaseDecisionRequest.java` | Java 实现 | 517 |
| `src/main/java/com/campusguard/moderation/admin/ModerationCaseDetail.java` | Java 实现 | 597 |
| `src/main/java/com/campusguard/moderation/admin/ModerationCaseView.java` | Java 实现 | 2152 |
| `src/main/java/com/campusguard/moderation/admin/ReportedContentView.java` | Java 实现 | 559 |
| `src/main/java/com/campusguard/moderation/engine/EngineRegistry.java` | Java 实现 | 4673 |
| `src/main/java/com/campusguard/moderation/engine/KeywordModerationEngine.java` | Java 实现 | 4704 |
| `src/main/java/com/campusguard/moderation/engine/ModerationEngine.java` | Java 实现 | 1887 |
| `src/main/java/com/campusguard/moderation/engine/ModerationEngineBundle.java` | Java 实现 | 557 |
| `src/main/java/com/campusguard/moderation/engine/ModerationRequest.java` | Java 实现 | 1923 |
| `src/main/java/com/campusguard/moderation/engine/ModerationVerdict.java` | Java 实现 | 1656 |
| `src/main/java/com/campusguard/moderation/engine/ai/AiEngineConfig.java` | Java 实现 | 3412 |
| `src/main/java/com/campusguard/moderation/engine/ai/AiInvocation.java` | Java 实现 | 3885 |
| `src/main/java/com/campusguard/moderation/engine/ai/AiInvocationRecorder.java` | Java 实现 | 3246 |
| `src/main/java/com/campusguard/moderation/engine/ai/AiInvocationRepository.java` | Java 实现 | 1837 |
| `src/main/java/com/campusguard/moderation/engine/ai/AiProperties.java` | Java 实现 | 2268 |
| `src/main/java/com/campusguard/moderation/engine/ai/ChatCompletionPort.java` | Java 实现 | 1563 |
| `src/main/java/com/campusguard/moderation/engine/ai/EngineInvocationStats.java` | Java 实现 | 702 |
| `src/main/java/com/campusguard/moderation/engine/ai/GeminiModerationEngine.java` | Java 实现 | 5646 |
| `src/main/java/com/campusguard/moderation/engine/ai/InvalidVerdictException.java` | Java 实现 | 527 |
| `src/main/java/com/campusguard/moderation/engine/ai/InvocationStatus.java` | Java 实现 | 912 |
| `src/main/java/com/campusguard/moderation/engine/ai/ModelCallException.java` | Java 实现 | 793 |
| `src/main/java/com/campusguard/moderation/engine/ai/ModelPolicies.java` | Java 实现 | 2200 |
| `src/main/java/com/campusguard/moderation/engine/ai/ModerationPrompt.java` | Java 实现 | 1264 |
| `src/main/java/com/campusguard/moderation/engine/ai/ModerationPromptV1.java` | Java 实现 | 3367 |
| `src/main/java/com/campusguard/moderation/engine/ai/ModerationPromptV2.java` | Java 实现 | 8463 |
| `src/main/java/com/campusguard/moderation/engine/ai/ModerationPromptV3.java` | Java 实现 | 8955 |
| `src/main/java/com/campusguard/moderation/engine/ai/ProviderFailures.java` | Java 实现 | 3039 |
| `src/main/java/com/campusguard/moderation/engine/ai/ResiliencePolicy.java` | Java 实现 | 6874 |
| `src/main/java/com/campusguard/moderation/engine/ai/ResilientChatCompletion.java` | Java 实现 | 2001 |
| `src/main/java/com/campusguard/moderation/engine/ai/SpringAiChatCompletion.java` | Java 实现 | 4555 |
| `src/main/java/com/campusguard/moderation/engine/ai/VerdictParser.java` | Java 实现 | 5822 |
| `src/main/java/com/campusguard/moderation/investigation/AdminInvestigationController.java` | Java 实现 | 3296 |
| `src/main/java/com/campusguard/moderation/investigation/AuthorHistoryTool.java` | Java 实现 | 5972 |
| `src/main/java/com/campusguard/moderation/investigation/BriefParser.java` | Java 实现 | 7973 |
| `src/main/java/com/campusguard/moderation/investigation/CaseDetailTool.java` | Java 实现 | 1770 |
| `src/main/java/com/campusguard/moderation/investigation/CaseInvestigator.java` | Java 实现 | 15085 |
| `src/main/java/com/campusguard/moderation/investigation/ConsensusInvestigator.java` | Java 实现 | 7157 |
| `src/main/java/com/campusguard/moderation/investigation/EvidenceStrength.java` | Java 实现 | 2080 |
| `src/main/java/com/campusguard/moderation/investigation/InvalidBriefException.java` | Java 实现 | 465 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationBrief.java` | Java 实现 | 2729 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationBriefView.java` | Java 实现 | 2481 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationConfig.java` | Java 实现 | 4615 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationLease.java` | Java 实现 | 1372 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationNotEnabledException.java` | Java 实现 | 575 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationPrompt.java` | Java 实现 | 1618 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationPromptV1.java` | Java 实现 | 7622 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationPromptV2.java` | Java 实现 | 8083 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationPromptV3.java` | Java 实现 | 7432 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationPromptV4.java` | Java 实现 | 10363 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationPromptV5.java` | Java 实现 | 8565 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationRecorder.java` | Java 实现 | 4507 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationService.java` | Java 实现 | 5629 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigationTool.java` | Java 实现 | 1910 |
| `src/main/java/com/campusguard/moderation/investigation/Investigator.java` | Java 实现 | 521 |
| `src/main/java/com/campusguard/moderation/investigation/InvestigatorProperties.java` | Java 实现 | 2521 |
| `src/main/java/com/campusguard/moderation/investigation/ResilientToolCalling.java` | Java 实现 | 1549 |
| `src/main/java/com/campusguard/moderation/investigation/RuleTextTool.java` | Java 实现 | 2124 |
| `src/main/java/com/campusguard/moderation/investigation/SimilarResolvedCasesTool.java` | Java 实现 | 7187 |
| `src/main/java/com/campusguard/moderation/investigation/SpringAiToolCompletion.java` | Java 实现 | 10300 |
| `src/main/java/com/campusguard/moderation/investigation/ToolArguments.java` | Java 实现 | 1121 |
| `src/main/java/com/campusguard/moderation/investigation/ToolCall.java` | Java 实现 | 768 |
| `src/main/java/com/campusguard/moderation/investigation/ToolCallingPort.java` | Java 实现 | 2402 |
| `src/main/java/com/campusguard/moderation/investigation/ToolRegistry.java` | Java 实现 | 4437 |
| `src/main/java/com/campusguard/moderation/investigation/ToolResult.java` | Java 实现 | 1845 |
| `src/main/java/com/campusguard/moderation/investigation/ToolSpec.java` | Java 实现 | 1382 |
| `src/main/java/com/campusguard/moderation/rule/CachingRuleProvider.java` | Java 实现 | 2160 |
| `src/main/java/com/campusguard/moderation/rule/ModerationRule.java` | Java 实现 | 2615 |
| `src/main/java/com/campusguard/moderation/rule/ModerationRuleRepository.java` | Java 实现 | 301 |
| `src/main/java/com/campusguard/moderation/rule/RuleProvider.java` | Java 实现 | 838 |
| `src/main/java/com/campusguard/moderation/rule/RuleSeverity.java` | Java 实现 | 101 |
| `src/main/java/com/campusguard/notification/Notification.java` | Java 实现 | 2085 |
| `src/main/java/com/campusguard/notification/NotificationController.java` | Java 实现 | 1648 |
| `src/main/java/com/campusguard/notification/NotificationRepository.java` | Java 实现 | 820 |
| `src/main/java/com/campusguard/notification/NotificationService.java` | Java 实现 | 1562 |
| `src/main/java/com/campusguard/notification/NotificationView.java` | Java 实现 | 513 |
| `src/main/java/com/campusguard/post/CreatePostRequest.java` | Java 实现 | 538 |
| `src/main/java/com/campusguard/post/FeedPage.java` | Java 实现 | 910 |
| `src/main/java/com/campusguard/post/Post.java` | Java 实现 | 3210 |
| `src/main/java/com/campusguard/post/PostController.java` | Java 实现 | 3414 |
| `src/main/java/com/campusguard/post/PostRepository.java` | Java 实现 | 4931 |
| `src/main/java/com/campusguard/post/PostResponse.java` | Java 实现 | 942 |
| `src/main/java/com/campusguard/post/PostService.java` | Java 实现 | 7293 |
| `src/main/java/com/campusguard/post/UpdatePostRequest.java` | Java 实现 | 295 |
| `src/main/java/com/campusguard/report/CreateReportRequest.java` | Java 实现 | 530 |
| `src/main/java/com/campusguard/report/Report.java` | Java 实现 | 3727 |
| `src/main/java/com/campusguard/report/ReportConfig.java` | Java 实现 | 277 |
| `src/main/java/com/campusguard/report/ReportController.java` | Java 实现 | 1925 |
| `src/main/java/com/campusguard/report/ReportProperties.java` | Java 实现 | 657 |
| `src/main/java/com/campusguard/report/ReportReason.java` | Java 实现 | 106 |
| `src/main/java/com/campusguard/report/ReportRepository.java` | Java 实现 | 1237 |
| `src/main/java/com/campusguard/report/ReportResponse.java` | Java 实现 | 810 |
| `src/main/java/com/campusguard/report/ReportService.java` | Java 实现 | 6572 |
| `src/main/java/com/campusguard/report/ReportStatus.java` | Java 实现 | 589 |
| `src/main/java/com/campusguard/security/AccountStateFilter.java` | Java 实现 | 5141 |
| `src/main/java/com/campusguard/security/AuthenticatedUser.java` | Java 实现 | 584 |
| `src/main/java/com/campusguard/security/CorsConfig.java` | Java 实现 | 1290 |
| `src/main/java/com/campusguard/security/CorsProperties.java` | Java 实现 | 367 |
| `src/main/java/com/campusguard/security/JwtProperties.java` | Java 实现 | 1916 |
| `src/main/java/com/campusguard/security/ProblemDetailAuthErrorHandler.java` | Java 实现 | 2852 |
| `src/main/java/com/campusguard/security/SecurityConfig.java` | Java 实现 | 10417 |
| `src/main/java/com/campusguard/security/TokenIssuer.java` | Java 实现 | 1996 |
| `src/main/java/com/campusguard/user/AdminBootstrap.java` | Java 实现 | 2961 |
| `src/main/java/com/campusguard/user/AdminProperties.java` | Java 实现 | 889 |
| `src/main/java/com/campusguard/user/MyProfileView.java` | Java 实现 | 658 |
| `src/main/java/com/campusguard/user/UpdateProfileRequest.java` | Java 实现 | 198 |
| `src/main/java/com/campusguard/user/User.java` | Java 实现 | 3917 |
| `src/main/java/com/campusguard/user/UserConfig.java` | Java 实现 | 272 |
| `src/main/java/com/campusguard/user/UserController.java` | Java 实现 | 1336 |
| `src/main/java/com/campusguard/user/UserRepository.java` | Java 实现 | 393 |
| `src/main/java/com/campusguard/user/UserRole.java` | Java 实现 | 78 |
| `src/main/java/com/campusguard/user/UserService.java` | Java 实现 | 1363 |
| `src/main/java/com/campusguard/user/UserStatus.java` | Java 实现 | 96 |
| `src/main/java/com/campusguard/user/UserView.java` | Java 实现 | 527 |
| `src/main/resources/application-prod.yml` | 配置 / 部署 | 1929 |
| `src/main/resources/application.yml` | 配置 / 部署 | 13826 |
| `src/main/resources/db/migration/V10__moderation_content_provenance.sql` | 数据库迁移 | 1201 |
| `src/main/resources/db/migration/V11__case_evidence_snapshot.sql` | 数据库迁移 | 981 |
| `src/main/resources/db/migration/V12__investigation_lease.sql` | 数据库迁移 | 337 |
| `src/main/resources/db/migration/V13__comment_page_index.sql` | 数据库迁移 | 174 |
| `src/main/resources/db/migration/V1__init_core_schema.sql` | 数据库迁移 | 2083 |
| `src/main/resources/db/migration/V2__reports.sql` | 数据库迁移 | 1753 |
| `src/main/resources/db/migration/V3__moderation.sql` | 数据库迁移 | 5482 |
| `src/main/resources/db/migration/V4__report_lifecycle.sql` | 数据库迁移 | 902 |
| `src/main/resources/db/migration/V5__ai_invocations.sql` | 数据库迁移 | 2713 |
| `src/main/resources/db/migration/V6__rate_limited_status.sql` | 数据库迁移 | 751 |
| `src/main/resources/db/migration/V7__comment_depth.sql` | 数据库迁移 | 2041 |
| `src/main/resources/db/migration/V8__production_capabilities.sql` | 数据库迁移 | 4547 |
| `src/main/resources/db/migration/V9__investigation_indexes.sql` | 数据库迁移 | 2165 |
| `src/main/resources/evaluation/starter-samples.json` | 构建 / 其他项目文件 | 6323 |
| `src/test/java/com/campusguard/AbstractIntegrationTest.java` | 测试 / 测试资源 | 6956 |
| `src/test/java/com/campusguard/CampusGuardApplicationTests.java` | 测试 / 测试资源 | 1671 |
| `src/test/java/com/campusguard/appeal/AppealWorkflowIntegrationTest.java` | 测试 / 测试资源 | 6131 |
| `src/test/java/com/campusguard/auth/AuthApiIntegrationTest.java` | 测试 / 测试资源 | 7927 |
| `src/test/java/com/campusguard/auth/OneTimeTokenConcurrencyIntegrationTest.java` | 测试 / 测试资源 | 3016 |
| `src/test/java/com/campusguard/auth/SessionLifecycleIntegrationTest.java` | 测试 / 测试资源 | 3910 |
| `src/test/java/com/campusguard/comment/CommentApiIntegrationTest.java` | 测试 / 测试资源 | 5642 |
| `src/test/java/com/campusguard/comment/CommentThreadBoundsTest.java` | 测试 / 测试资源 | 8052 |
| `src/test/java/com/campusguard/common/ContentRateLimitIntegrationTest.java` | 测试 / 测试资源 | 5142 |
| `src/test/java/com/campusguard/evaluation/BenchmarkPairingTest.java` | 测试 / 测试资源 | 3476 |
| `src/test/java/com/campusguard/evaluation/BenchmarkRunsTest.java` | 测试 / 测试资源 | 7375 |
| `src/test/java/com/campusguard/evaluation/ConfusionMatrixTest.java` | 测试 / 测试资源 | 4162 |
| `src/test/java/com/campusguard/evaluation/CostEstimatorTest.java` | 测试 / 测试资源 | 1804 |
| `src/test/java/com/campusguard/evaluation/EngineComparatorTest.java` | 测试 / 测试资源 | 4570 |
| `src/test/java/com/campusguard/evaluation/EngineRunsTest.java` | 测试 / 测试资源 | 9004 |
| `src/test/java/com/campusguard/evaluation/EvaluationPacingTest.java` | 测试 / 测试资源 | 3633 |
| `src/test/java/com/campusguard/evaluation/EvaluationResultTest.java` | 测试 / 测试资源 | 5865 |
| `src/test/java/com/campusguard/evaluation/EvaluationRunnerFailureTest.java` | 测试 / 测试资源 | 5731 |
| `src/test/java/com/campusguard/evaluation/HeldOutDatasetTest.java` | 测试 / 测试资源 | 6523 |
| `src/test/java/com/campusguard/evaluation/LabelledSampleTest.java` | 测试 / 测试资源 | 1934 |
| `src/test/java/com/campusguard/evaluation/MinimalPairScoreTest.java` | 测试 / 测试资源 | 5218 |
| `src/test/java/com/campusguard/evaluation/StratifiedReportTest.java` | 测试 / 测试资源 | 4365 |
| `src/test/java/com/campusguard/evaluation/corpus/DecisionCorpusExporterIntegrationTest.java` | 测试 / 测试资源 | 9018 |
| `src/test/java/com/campusguard/evaluation/investigation/InvestigationBenchmark.java` | 测试 / 测试资源 | 10977 |
| `src/test/java/com/campusguard/evaluation/investigation/InvestigationBenchmarkIntegrationTest.java` | 测试 / 测试资源 | 13478 |
| `src/test/java/com/campusguard/evaluation/investigation/InvestigationBenchmarkReport.java` | 测试 / 测试资源 | 5774 |
| `src/test/java/com/campusguard/evaluation/investigation/InvestigationScenario.java` | 测试 / 测试资源 | 3793 |
| `src/test/java/com/campusguard/evaluation/investigation/InvestigationScenarios.java` | 测试 / 测试资源 | 1587 |
| `src/test/java/com/campusguard/evaluation/investigation/RealModelInvestigationBenchmarkTest.java` | 测试 / 测试资源 | 6609 |
| `src/test/java/com/campusguard/evaluation/investigation/ScenarioFixture.java` | 测试 / 测试资源 | 11043 |
| `src/test/java/com/campusguard/media/FilesystemMediaStorageTest.java` | 测试 / 测试资源 | 2734 |
| `src/test/java/com/campusguard/media/MediaApiIntegrationTest.java` | 测试 / 测试资源 | 4207 |
| `src/test/java/com/campusguard/media/MediaStorageContract.java` | 测试 / 测试资源 | 6275 |
| `src/test/java/com/campusguard/media/MediaSweepIntegrationTest.java` | 测试 / 测试资源 | 6987 |
| `src/test/java/com/campusguard/media/RealBucketMediaStorageTest.java` | 测试 / 测试资源 | 4877 |
| `src/test/java/com/campusguard/media/S3MediaStorageTest.java` | 测试 / 测试资源 | 6376 |
| `src/test/java/com/campusguard/moderation/CaseEvidenceIntegrationTest.java` | 测试 / 测试资源 | 6363 |
| `src/test/java/com/campusguard/moderation/CaseHistoryQueryIntegrationTest.java` | 测试 / 测试资源 | 12137 |
| `src/test/java/com/campusguard/moderation/MissingMediaReviewIntegrationTest.java` | 测试 / 测试资源 | 3451 |
| `src/test/java/com/campusguard/moderation/ModerationCaseAggregationTest.java` | 测试 / 测试资源 | 6721 |
| `src/test/java/com/campusguard/moderation/ModerationDegradationIntegrationTest.java` | 测试 / 测试资源 | 7794 |
| `src/test/java/com/campusguard/moderation/ModerationRevisionIntegrationTest.java` | 测试 / 测试资源 | 15207 |
| `src/test/java/com/campusguard/moderation/ModerationStatusControllerTest.java` | 测试 / 测试资源 | 2490 |
| `src/test/java/com/campusguard/moderation/ModerationWorkflowIntegrationTest.java` | 测试 / 测试资源 | 12916 |
| `src/test/java/com/campusguard/moderation/StalledCaseSweepIntegrationTest.java` | 测试 / 测试资源 | 5607 |
| `src/test/java/com/campusguard/moderation/admin/AdminCaseListQueryCountTest.java` | 测试 / 测试资源 | 4989 |
| `src/test/java/com/campusguard/moderation/admin/CaseAssignmentIntegrationTest.java` | 测试 / 测试资源 | 3479 |
| `src/test/java/com/campusguard/moderation/engine/KeywordModerationEngineTest.java` | 测试 / 测试资源 | 5178 |
| `src/test/java/com/campusguard/moderation/engine/ai/GeminiModerationEngineTest.java` | 测试 / 测试资源 | 7015 |
| `src/test/java/com/campusguard/moderation/engine/ai/ModelPoliciesTest.java` | 测试 / 测试资源 | 2366 |
| `src/test/java/com/campusguard/moderation/engine/ai/ModerationPromptV3Test.java` | 测试 / 测试资源 | 2448 |
| `src/test/java/com/campusguard/moderation/engine/ai/ResilientChatCompletionTest.java` | 测试 / 测试资源 | 7454 |
| `src/test/java/com/campusguard/moderation/engine/ai/SpringAiChatCompletionTest.java` | 测试 / 测试资源 | 4931 |
| `src/test/java/com/campusguard/moderation/engine/ai/VerdictParserTest.java` | 测试 / 测试资源 | 4618 |
| `src/test/java/com/campusguard/moderation/investigation/CaseInvestigatorIntegrationTest.java` | 测试 / 测试资源 | 26979 |
| `src/test/java/com/campusguard/moderation/investigation/ConsensusInvestigatorTest.java` | 测试 / 测试资源 | 8615 |
| `src/test/java/com/campusguard/moderation/investigation/InvestigationApiIntegrationTest.java` | 测试 / 测试资源 | 25410 |
| `src/test/java/com/campusguard/moderation/investigation/InvestigationDisabledApiTest.java` | 测试 / 测试资源 | 3573 |
| `src/test/java/com/campusguard/moderation/investigation/InvestigationWiringTest.java` | 测试 / 测试资源 | 5709 |
| `src/test/java/com/campusguard/moderation/investigation/RealModelInvestigationSmokeTest.java` | 测试 / 测试资源 | 14029 |
| `src/test/java/com/campusguard/moderation/investigation/SharedResiliencePolicyTest.java` | 测试 / 测试资源 | 7619 |
| `src/test/java/com/campusguard/moderation/investigation/SpringAiToolCompletionTest.java` | 测试 / 测试资源 | 14084 |
| `src/test/java/com/campusguard/moderation/investigation/ToolRegistryIntegrationTest.java` | 测试 / 测试资源 | 16560 |
| `src/test/java/com/campusguard/moderation/investigation/ToolRegistryTransactionBoundaryTest.java` | 测试 / 测试资源 | 3786 |
| `src/test/java/com/campusguard/post/FeedPaginationTest.java` | 测试 / 测试资源 | 5783 |
| `src/test/java/com/campusguard/post/PostApiIntegrationTest.java` | 测试 / 测试资源 | 5909 |
| `src/test/java/com/campusguard/post/PostDeletionTest.java` | 测试 / 测试资源 | 6808 |
| `src/test/java/com/campusguard/report/ReportApiIntegrationTest.java` | 测试 / 测试资源 | 7815 |
| `src/test/java/com/campusguard/report/ReportRateLimitTest.java` | 测试 / 测试资源 | 3904 |
| `src/test/java/com/campusguard/security/AccountStateIntegrationTest.java` | 测试 / 测试资源 | 5278 |
| `src/test/java/com/campusguard/security/ActuatorExposureIntegrationTest.java` | 测试 / 测试资源 | 3541 |
| `src/test/java/com/campusguard/security/ApiDocsExposureTest.java` | 测试 / 测试资源 | 2259 |
| `src/test/java/com/campusguard/security/AuthorizationBoundaryTest.java` | 测试 / 测试资源 | 7274 |
| `src/test/java/com/campusguard/security/ProductionMonitoringConfigTest.java` | 测试 / 测试资源 | 1213 |
| `src/test/java/com/campusguard/user/AdminBootstrapTest.java` | 测试 / 测试资源 | 3969 |
| `src/test/java/com/campusguard/user/UserProfileIntegrationTest.java` | 测试 / 测试资源 | 1797 |
| `src/test/resources/evaluation/investigation-scenarios.json` | 测试 / 测试资源 | 29161 |

### android（401 项）

| 文件 | 类型 | 字节数 |
|---|---|---|
| `.DS_Store` | 构建 / 其他项目文件 | 6148 |
| `.github/workflows/android-ci.yml` | CI 配置 | 394 |
| `.idea/.gitignore` | IDE 文件 | 251 |
| `.idea/misc.xml` | IDE 文件 | 271 |
| `.idea/modules.xml` | IDE 文件 | 258 |
| `.idea/vcs.xml` | IDE 文件 | 167 |
| `LICENSE` | 构建 / 其他项目文件 | 1927 |
| `android/.gitignore` | 构建 / 其他项目文件 | 269 |
| `android/app/.gitignore` | 构建 / 其他项目文件 | 6 |
| `android/app/build.gradle.kts` | 构建 / 其他项目文件 | 1617 |
| `android/app/proguard-rules.pro` | 构建 / 其他项目文件 | 750 |
| `android/app/src/androidTest/java/com/example/myapplication/ExampleInstrumentedTest.java` | 测试 / 测试资源 | 764 |
| `android/app/src/debug/AndroidManifest.xml` | 构建 / 其他项目文件 | 330 |
| `android/app/src/main/AndroidManifest.xml` | 构建 / 其他项目文件 | 1894 |
| `android/app/src/main/java/backend/BackendAccounts.java` | Java 实现 | 4004 |
| `android/app/src/main/java/backend/BackendClient.java` | Java 实现 | 6249 |
| `android/app/src/main/java/backend/BackendConfig.java` | Java 实现 | 2479 |
| `android/app/src/main/java/backend/BackendException.java` | Java 实现 | 1919 |
| `android/app/src/main/java/backend/BackendForumGateway.java` | Java 实现 | 11494 |
| `android/app/src/main/java/backend/BackendIdentity.java` | Java 实现 | 3130 |
| `android/app/src/main/java/backend/BackendMappings.java` | Java 实现 | 2420 |
| `android/app/src/main/java/backend/BackendMedia.java` | Java 实现 | 1494 |
| `android/app/src/main/java/backend/BackendMemberGateway.java` | Java 实现 | 5893 |
| `android/app/src/main/java/backend/BackendModerationGateway.java` | Java 实现 | 9965 |
| `android/app/src/main/java/backend/BackendModerationStatus.java` | Java 实现 | 291 |
| `android/app/src/main/java/backend/BackendReportTarget.java` | Java 实现 | 791 |
| `android/app/src/main/java/backend/BackendReviewCase.java` | Java 实现 | 798 |
| `android/app/src/main/java/backend/BackendRuntime.java` | Java 实现 | 1769 |
| `android/app/src/main/java/backend/BackendText.java` | Java 实现 | 2083 |
| `android/app/src/main/java/backend/KeyValueStore.java` | Java 实现 | 596 |
| `android/app/src/main/java/backend/PreferencesStore.java` | Java 实现 | 880 |
| `android/app/src/main/java/com/example/myapplication/AppData.java` | Java 实现 | 140266 |
| `android/app/src/main/java/com/example/myapplication/AppSheet.java` | Java 实现 | 11541 |
| `android/app/src/main/java/com/example/myapplication/BackendReportedCaseAdapter.java` | Java 实现 | 4997 |
| `android/app/src/main/java/com/example/myapplication/CampusMarketRepository.java` | Java 实现 | 9145 |
| `android/app/src/main/java/com/example/myapplication/ChannelsFragment.java` | Java 实现 | 20149 |
| `android/app/src/main/java/com/example/myapplication/CreatePostActivity.java` | Java 实现 | 16055 |
| `android/app/src/main/java/com/example/myapplication/ImageAttachmentViewer.java` | Java 实现 | 6895 |
| `android/app/src/main/java/com/example/myapplication/ImageStorage.java` | Java 实现 | 1904 |
| `android/app/src/main/java/com/example/myapplication/LeaderboardFragment.java` | Java 实现 | 8436 |
| `android/app/src/main/java/com/example/myapplication/LoginActivity.java` | Java 实现 | 4593 |
| `android/app/src/main/java/com/example/myapplication/MainActivity.java` | Java 实现 | 51050 |
| `android/app/src/main/java/com/example/myapplication/MainPagerAdapter.java` | Java 实现 | 1294 |
| `android/app/src/main/java/com/example/myapplication/MarketCandleChartView.java` | Java 实现 | 6234 |
| `android/app/src/main/java/com/example/myapplication/MarketFragment.java` | Java 实现 | 40514 |
| `android/app/src/main/java/com/example/myapplication/MarketPortfolioStore.java` | Java 实现 | 25889 |
| `android/app/src/main/java/com/example/myapplication/MessageAdapter.java` | Java 实现 | 14297 |
| `android/app/src/main/java/com/example/myapplication/MessageFragment.java` | Java 实现 | 593 |
| `android/app/src/main/java/com/example/myapplication/ModerationLabels.java` | Java 实现 | 2413 |
| `android/app/src/main/java/com/example/myapplication/ModerationQueueActivity.java` | Java 实现 | 11153 |
| `android/app/src/main/java/com/example/myapplication/ModerationRecordsActivity.java` | Java 实现 | 13558 |
| `android/app/src/main/java/com/example/myapplication/NotificationAdapter.java` | Java 实现 | 4373 |
| `android/app/src/main/java/com/example/myapplication/NotificationsFragment.java` | Java 实现 | 11567 |
| `android/app/src/main/java/com/example/myapplication/PostAdapter.java` | Java 实现 | 10020 |
| `android/app/src/main/java/com/example/myapplication/PostViewerActivity.java` | Java 实现 | 40372 |
| `android/app/src/main/java/com/example/myapplication/ProfileInsightDialog.java` | Java 实现 | 11874 |
| `android/app/src/main/java/com/example/myapplication/RefreshablePage.java` | Java 实现 | 100 |
| `android/app/src/main/java/com/example/myapplication/RemoteImageLoader.java` | Java 实现 | 1809 |
| `android/app/src/main/java/com/example/myapplication/ReportedMessageAdapter.java` | Java 实现 | 4127 |
| `android/app/src/main/java/com/example/myapplication/SettingsActivity.java` | Java 实现 | 12085 |
| `android/app/src/main/java/com/example/myapplication/UiPreferences.java` | Java 实现 | 5176 |
| `android/app/src/main/java/com/example/myapplication/UserProfileActivity.java` | Java 实现 | 13637 |
| `android/app/src/main/java/com/example/myapplication/YouFragment.java` | Java 实现 | 14349 |
| `android/app/src/main/java/dao/DAO.java` | Java 实现 | 1506 |
| `android/app/src/main/java/dao/MessageComparator.java` | Java 实现 | 1294 |
| `android/app/src/main/java/dao/PostDAO.java` | Java 实现 | 1924 |
| `android/app/src/main/java/dao/RandomContentGenerator.java` | Java 实现 | 3121 |
| `android/app/src/main/java/dao/ReportDao.java` | Java 实现 | 454 |
| `android/app/src/main/java/dao/UserDAO.java` | Java 实现 | 2530 |
| `android/app/src/main/java/dao/model/HasUUID.java` | Java 实现 | 196 |
| `android/app/src/main/java/dao/model/Message.java` | Java 实现 | 139 |
| `android/app/src/main/java/dao/model/Post.java` | Java 实现 | 1325 |
| `android/app/src/main/java/dao/model/Report.java` | Java 实现 | 732 |
| `android/app/src/main/java/dao/model/TimestampFormatter.java` | Java 实现 | 97 |
| `android/app/src/main/java/dao/model/TimestampFormatterTimeSinceEnglish.java` | Java 实现 | 1819 |
| `android/app/src/main/java/dao/model/User.java` | Java 实现 | 379 |
| `android/app/src/main/java/moderation/ModerationTools.java` | Java 实现 | 4737 |
| `android/app/src/main/java/moderation/MostStrategy.java` | Java 实现 | 1336 |
| `android/app/src/main/java/moderation/OldestStrategy.java` | Java 实现 | 1597 |
| `android/app/src/main/java/moderation/ReportedMessageIterator.java` | Java 实现 | 606 |
| `android/app/src/main/java/moderation/ReportedMessageIteratorFactory.java` | Java 实现 | 302 |
| `android/app/src/main/java/persistentdata/DataManager.java` | Java 实现 | 2598 |
| `android/app/src/main/java/persistentdata/DataPipeline.java` | Java 实现 | 1755 |
| `android/app/src/main/java/persistentdata/PersistentDataException.java` | Java 实现 | 169 |
| `android/app/src/main/java/persistentdata/formatted/CSVFormat.java` | Java 实现 | 1254 |
| `android/app/src/main/java/persistentdata/formatted/CSVFormattedFactory.java` | Java 实现 | 546 |
| `android/app/src/main/java/persistentdata/formatted/CSVReader.java` | Java 实现 | 3044 |
| `android/app/src/main/java/persistentdata/formatted/CSVWriter.java` | Java 实现 | 1743 |
| `android/app/src/main/java/persistentdata/formatted/FormattedFactory.java` | Java 实现 | 234 |
| `android/app/src/main/java/persistentdata/formatted/FormattedReader.java` | Java 实现 | 656 |
| `android/app/src/main/java/persistentdata/formatted/FormattedWriter.java` | Java 实现 | 421 |
| `android/app/src/main/java/persistentdata/io/ComputerIOFactory.java` | Java 实现 | 923 |
| `android/app/src/main/java/persistentdata/io/IOFactory.java` | Java 实现 | 195 |
| `android/app/src/main/java/persistentdata/serialization/HiddenMessageSerializer.java` | Java 实现 | 364 |
| `android/app/src/main/java/persistentdata/serialization/MessageSerializer.java` | Java 实现 | 798 |
| `android/app/src/main/java/persistentdata/serialization/PostSerializer.java` | Java 实现 | 633 |
| `android/app/src/main/java/persistentdata/serialization/ReportSerializer.java` | Java 实现 | 529 |
| `android/app/src/main/java/persistentdata/serialization/Serializer.java` | Java 实现 | 582 |
| `android/app/src/main/java/persistentdata/serialization/UserSerializer.java` | Java 实现 | 889 |
| `android/app/src/main/java/sorteddata/SortedData.java` | Java 实现 | 2347 |
| `android/app/src/main/java/sorteddata/SortedDataFactory.java` | Java 实现 | 303 |
| `android/app/src/main/java/sorteddata/SortedDataSlice.java` | Java 实现 | 3402 |
| `android/app/src/main/java/sorteddata/SortedDataSubject.java` | Java 实现 | 92 |
| `android/app/src/main/java/sorteddata/avltree/AVLIterator.java` | Java 实现 | 2428 |
| `android/app/src/main/java/sorteddata/avltree/AVLNode.java` | Java 实现 | 2483 |
| `android/app/src/main/java/sorteddata/avltree/AVLNodeEmpty.java` | Java 实现 | 651 |
| `android/app/src/main/java/sorteddata/avltree/AVLNodeFilled.java` | Java 实现 | 3387 |
| `android/app/src/main/java/sorteddata/avltree/AVLTestBuilder.java` | Java 实现 | 2419 |
| `android/app/src/main/java/sorteddata/avltree/AVLTree.java` | Java 实现 | 1320 |
| `android/app/src/main/java/sorteddata/avltree/AVLTreeSlice.java` | Java 实现 | 855 |
| `android/app/src/main/java/sorteddata/bstree/BSNode.java` | Java 实现 | 465 |
| `android/app/src/main/java/sorteddata/bstree/BSNodeEmpty.java` | Java 实现 | 597 |
| `android/app/src/main/java/sorteddata/bstree/BSNodeFilled.java` | Java 实现 | 1891 |
| `android/app/src/main/java/sorteddata/bstree/BSTree.java` | Java 实现 | 1213 |
| `android/app/src/main/java/sorteddata/sortedarraylist/SortedArrayList.java` | Java 实现 | 1879 |
| `android/app/src/main/java/sorteddata/sortedarraylist/SortedArrayListIterator.java` | Java 实现 | 722 |
| `android/app/src/main/java/userstate/AdminState.java` | Java 实现 | 142 |
| `android/app/src/main/java/userstate/GuestState.java` | Java 实现 | 709 |
| `android/app/src/main/java/userstate/MemberState.java` | Java 实现 | 680 |
| `android/app/src/main/java/userstate/StateManager.java` | Java 实现 | 1307 |
| `android/app/src/main/java/userstate/UserState.java` | Java 实现 | 292 |
| `android/app/src/main/res/animator/lb_bar1_anim.xml` | 界面资源 / 截图 | 699 |
| `android/app/src/main/res/animator/lb_bar2_anim.xml` | 界面资源 / 截图 | 703 |
| `android/app/src/main/res/animator/lb_bar3_anim.xml` | 界面资源 / 截图 | 703 |
| `android/app/src/main/res/animator/market_bar1_anim.xml` | 界面资源 / 截图 | 699 |
| `android/app/src/main/res/animator/market_bar2_anim.xml` | 界面资源 / 截图 | 701 |
| `android/app/src/main/res/animator/market_bar3_anim.xml` | 界面资源 / 截图 | 700 |
| `android/app/src/main/res/animator/market_bar4_anim.xml` | 界面资源 / 截图 | 701 |
| `android/app/src/main/res/animator/market_bar5_anim.xml` | 界面资源 / 截图 | 703 |
| `android/app/src/main/res/color/bottom_nav_colors.xml` | 界面资源 / 截图 | 250 |
| `android/app/src/main/res/drawable-night/bg_card.xml` | 界面资源 / 截图 | 232 |
| `android/app/src/main/res/drawable-night/bg_mode_pill.xml` | 界面资源 / 截图 | 319 |
| `android/app/src/main/res/drawable-night/bg_nav_container.xml` | 界面资源 / 截图 | 224 |
| `android/app/src/main/res/drawable-night/bg_pill.xml` | 界面资源 / 截图 | 227 |
| `android/app/src/main/res/drawable-night/bg_tab_bar.xml` | 界面资源 / 截图 | 229 |
| `android/app/src/main/res/drawable-night/bg_tab_indicator.xml` | 界面资源 / 截图 | 229 |
| `android/app/src/main/res/drawable-nodpi/avatar_anu.jpg` | 界面资源 / 截图 | 126412 |
| `android/app/src/main/res/drawable-nodpi/avatar_um.jpg` | 界面资源 / 截图 | 164150 |
| `android/app/src/main/res/drawable-nodpi/avatar_unsw.jpg` | 界面资源 / 截图 | 150213 |
| `android/app/src/main/res/drawable-nodpi/avatar_usyd.jpg` | 界面资源 / 截图 | 206631 |
| `android/app/src/main/res/drawable-nodpi/ic_launcher_wordmark.png` | 界面资源 / 截图 | 12655 |
| `android/app/src/main/res/drawable/bg_ai_answer.xml` | 界面资源 / 截图 | 307 |
| `android/app/src/main/res/drawable/bg_avatar_circle.xml` | 界面资源 / 截图 | 187 |
| `android/app/src/main/res/drawable/bg_avatar_picker_dialog.xml` | 界面资源 / 截图 | 301 |
| `android/app/src/main/res/drawable/bg_button_outlined.xml` | 界面资源 / 截图 | 285 |
| `android/app/src/main/res/drawable/bg_card.xml` | 界面资源 / 截图 | 313 |
| `android/app/src/main/res/drawable/bg_community_avatar.xml` | 界面资源 / 截图 | 321 |
| `android/app/src/main/res/drawable/bg_community_avatar_um.xml` | 界面资源 / 截图 | 323 |
| `android/app/src/main/res/drawable/bg_fab.xml` | 界面资源 / 截图 | 277 |
| `android/app/src/main/res/drawable/bg_image_attach_button.xml` | 界面资源 / 截图 | 316 |
| `android/app/src/main/res/drawable/bg_login_logo.xml` | 界面资源 / 截图 | 196 |
| `android/app/src/main/res/drawable/bg_mode_pill.xml` | 界面资源 / 截图 | 319 |
| `android/app/src/main/res/drawable/bg_nav_container.xml` | 界面资源 / 截图 | 313 |
| `android/app/src/main/res/drawable/bg_notification_badge.xml` | 界面资源 / 截图 | 193 |
| `android/app/src/main/res/drawable/bg_pill.xml` | 界面资源 / 截图 | 313 |
| `android/app/src/main/res/drawable/bg_preview_remove.xml` | 界面资源 / 截图 | 274 |
| `android/app/src/main/res/drawable/bg_reply_dialog.xml` | 界面资源 / 截图 | 309 |
| `android/app/src/main/res/drawable/bg_reply_input.xml` | 界面资源 / 截图 | 230 |
| `android/app/src/main/res/drawable/bg_reply_send.xml` | 界面资源 / 截图 | 318 |
| `android/app/src/main/res/drawable/bg_reply_tool.xml` | 界面资源 / 截图 | 426 |
| `android/app/src/main/res/drawable/bg_search_bar.xml` | 界面资源 / 截图 | 320 |
| `android/app/src/main/res/drawable/bg_tab_bar.xml` | 界面资源 / 截图 | 318 |
| `android/app/src/main/res/drawable/bg_tab_indicator.xml` | 界面资源 / 截图 | 438 |
| `android/app/src/main/res/drawable/fg_tab_press.xml` | 界面资源 / 截图 | 440 |
| `android/app/src/main/res/drawable/ic_add_24.xml` | 界面资源 / 截图 | 345 |
| `android/app/src/main/res/drawable/ic_at_24.xml` | 界面资源 / 截图 | 974 |
| `android/app/src/main/res/drawable/ic_bell_24.xml` | 界面资源 / 截图 | 447 |
| `android/app/src/main/res/drawable/ic_bookmark_24.xml` | 界面资源 / 截图 | 510 |
| `android/app/src/main/res/drawable/ic_bookmark_filled_24.xml` | 界面资源 / 截图 | 344 |
| `android/app/src/main/res/drawable/ic_channel_24.xml` | 界面资源 / 截图 | 402 |
| `android/app/src/main/res/drawable/ic_check_24.xml` | 界面资源 / 截图 | 376 |
| `android/app/src/main/res/drawable/ic_chevron_right_24.xml` | 界面资源 / 截图 | 357 |
| `android/app/src/main/res/drawable/ic_close_24.xml` | 界面资源 / 截图 | 471 |
| `android/app/src/main/res/drawable/ic_comment_outline_24.xml` | 界面资源 / 截图 | 605 |
| `android/app/src/main/res/drawable/ic_delete_24.xml` | 界面资源 / 截图 | 731 |
| `android/app/src/main/res/drawable/ic_edit_24.xml` | 界面资源 / 截图 | 446 |
| `android/app/src/main/res/drawable/ic_emoji_24.xml` | 界面资源 / 截图 | 648 |
| `android/app/src/main/res/drawable/ic_flag_24.xml` | 界面资源 / 截图 | 438 |
| `android/app/src/main/res/drawable/ic_flag_outline_24.xml` | 界面资源 / 截图 | 772 |
| `android/app/src/main/res/drawable/ic_hidden_24.xml` | 界面资源 / 截图 | 1239 |
| `android/app/src/main/res/drawable/ic_image_24.xml` | 界面资源 / 截图 | 1071 |
| `android/app/src/main/res/drawable/ic_language_24.xml` | 界面资源 / 截图 | 1271 |
| `android/app/src/main/res/drawable/ic_launcher_background.xml` | 界面资源 / 截图 | 335 |
| `android/app/src/main/res/drawable/ic_launcher_foreground.xml` | 界面资源 / 截图 | 375 |
| `android/app/src/main/res/drawable/ic_leaderboard_24.xml` | 界面资源 / 截图 | 768 |
| `android/app/src/main/res/drawable/ic_leaderboard_animated.xml` | 界面资源 / 截图 | 426 |
| `android/app/src/main/res/drawable/ic_logout_24.xml` | 界面资源 / 截图 | 420 |
| `android/app/src/main/res/drawable/ic_market_24.xml` | 界面资源 / 截图 | 1046 |
| `android/app/src/main/res/drawable/ic_market_animated.xml` | 界面资源 / 截图 | 621 |
| `android/app/src/main/res/drawable/ic_menu_24.xml` | 界面资源 / 截图 | 318 |
| `android/app/src/main/res/drawable/ic_message_vote_down_filled_24.xml` | 界面资源 / 截图 | 350 |
| `android/app/src/main/res/drawable/ic_message_vote_down_outline_24.xml` | 界面资源 / 截图 | 482 |
| `android/app/src/main/res/drawable/ic_message_vote_up_filled_24.xml` | 界面资源 / 截图 | 348 |
| `android/app/src/main/res/drawable/ic_message_vote_up_outline_24.xml` | 界面资源 / 截图 | 482 |
| `android/app/src/main/res/drawable/ic_more_horiz_24.xml` | 界面资源 / 截图 | 425 |
| `android/app/src/main/res/drawable/ic_palette_24.xml` | 界面资源 / 截图 | 819 |
| `android/app/src/main/res/drawable/ic_search_24.xml` | 界面资源 / 截图 | 559 |
| `android/app/src/main/res/drawable/ic_settings_24.xml` | 界面资源 / 截图 | 1174 |
| `android/app/src/main/res/drawable/ic_shield_outline_24.xml` | 界面资源 / 截图 | 727 |
| `android/app/src/main/res/drawable/ic_user_24.xml` | 界面资源 / 截图 | 405 |
| `android/app/src/main/res/drawable/ic_vote_down_filled_24.xml` | 界面资源 / 截图 | 353 |
| `android/app/src/main/res/drawable/ic_vote_down_outline_24.xml` | 界面资源 / 截图 | 479 |
| `android/app/src/main/res/drawable/ic_vote_up_filled_24.xml` | 界面资源 / 截图 | 351 |
| `android/app/src/main/res/drawable/ic_vote_up_outline_24.xml` | 界面资源 / 截图 | 479 |
| `android/app/src/main/res/layout/activity_create_post.xml` | 界面资源 / 截图 | 10179 |
| `android/app/src/main/res/layout/activity_login.xml` | 界面资源 / 截图 | 5530 |
| `android/app/src/main/res/layout/activity_main.xml` | 界面资源 / 截图 | 16964 |
| `android/app/src/main/res/layout/activity_moderation_queue.xml` | 界面资源 / 截图 | 3895 |
| `android/app/src/main/res/layout/activity_moderation_records.xml` | 界面资源 / 截图 | 4533 |
| `android/app/src/main/res/layout/activity_post_viewer.xml` | 界面资源 / 截图 | 16322 |
| `android/app/src/main/res/layout/activity_settings.xml` | 界面资源 / 截图 | 10722 |
| `android/app/src/main/res/layout/activity_user_profile.xml` | 界面资源 / 截图 | 13618 |
| `android/app/src/main/res/layout/dialog_settings.xml` | 界面资源 / 截图 | 2293 |
| `android/app/src/main/res/layout/fragment_channels.xml` | 界面资源 / 截图 | 10737 |
| `android/app/src/main/res/layout/fragment_leaderboard.xml` | 界面资源 / 截图 | 6259 |
| `android/app/src/main/res/layout/fragment_market.xml` | 界面资源 / 截图 | 17519 |
| `android/app/src/main/res/layout/fragment_message.xml` | 界面资源 / 截图 | 9767 |
| `android/app/src/main/res/layout/fragment_notifications.xml` | 界面资源 / 截图 | 3503 |
| `android/app/src/main/res/layout/fragment_you.xml` | 界面资源 / 截图 | 14614 |
| `android/app/src/main/res/layout/item_leaderboard_entry.xml` | 界面资源 / 截图 | 3109 |
| `android/app/src/main/res/layout/item_market_position.xml` | 界面资源 / 截图 | 2923 |
| `android/app/src/main/res/layout/item_market_selector.xml` | 界面资源 / 截图 | 1810 |
| `android/app/src/main/res/layout/item_notification.xml` | 界面资源 / 截图 | 3084 |
| `android/app/src/main/res/layout/item_post.xml` | 界面资源 / 截图 | 8996 |
| `android/app/src/main/res/layout/item_reported_message.xml` | 界面资源 / 截图 | 2587 |
| `android/app/src/main/res/menu/bottom_navigation_menu.xml` | 界面资源 / 截图 | 832 |
| `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | 界面资源 / 截图 | 343 |
| `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | 界面资源 / 截图 | 343 |
| `android/app/src/main/res/mipmap-hdpi/ic_launcher.png` | 界面资源 / 截图 | 1743 |
| `android/app/src/main/res/mipmap-hdpi/ic_launcher_round.png` | 界面资源 / 截图 | 2331 |
| `android/app/src/main/res/mipmap-mdpi/ic_launcher.png` | 界面资源 / 截图 | 1205 |
| `android/app/src/main/res/mipmap-mdpi/ic_launcher_round.png` | 界面资源 / 截图 | 1458 |
| `android/app/src/main/res/mipmap-xhdpi/ic_launcher.png` | 界面资源 / 截图 | 2473 |
| `android/app/src/main/res/mipmap-xhdpi/ic_launcher_round.png` | 界面资源 / 截图 | 3182 |
| `android/app/src/main/res/mipmap-xxhdpi/ic_launcher.png` | 界面资源 / 截图 | 3696 |
| `android/app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png` | 界面资源 / 截图 | 5129 |
| `android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` | 界面资源 / 截图 | 4958 |
| `android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png` | 界面资源 / 截图 | 7014 |
| `android/app/src/main/res/values-night/colors.xml` | 界面资源 / 截图 | 2518 |
| `android/app/src/main/res/values-night/themes.xml` | 界面资源 / 截图 | 1811 |
| `android/app/src/main/res/values-zh-rCN/strings.xml` | 界面资源 / 截图 | 27853 |
| `android/app/src/main/res/values/colors.xml` | 界面资源 / 截图 | 2626 |
| `android/app/src/main/res/values/strings.xml` | 界面资源 / 截图 | 27636 |
| `android/app/src/main/res/values/themes.xml` | 界面资源 / 截图 | 2288 |
| `android/app/src/main/res/xml/backup_rules.xml` | 界面资源 / 截图 | 478 |
| `android/app/src/main/res/xml/data_extraction_rules.xml` | 界面资源 / 截图 | 551 |
| `android/app/src/test/java/backend/BackendConfigTest.java` | 测试 / 测试资源 | 1857 |
| `android/app/src/test/java/backend/BackendExceptionTest.java` | 测试 / 测试资源 | 2660 |
| `android/app/src/test/java/backend/BackendIdentityTest.java` | 测试 / 测试资源 | 958 |
| `android/app/src/test/java/backend/BackendMappingsTest.java` | 测试 / 测试资源 | 3764 |
| `android/app/src/test/java/backend/BackendResponseTextTest.java` | 测试 / 测试资源 | 2852 |
| `android/app/src/test/java/com/example/myapplication/ExampleUnitTest.java` | 测试 / 测试资源 | 386 |
| `android/build.gradle.kts` | 构建 / 其他项目文件 | 167 |
| `android/gradle.properties` | 构建 / 其他项目文件 | 1255 |
| `android/gradle/libs.versions.toml` | 构建 / 其他项目文件 | 1065 |
| `android/gradle/wrapper/gradle-wrapper.jar` | 构建 / 其他项目文件 | 45457 |
| `android/gradle/wrapper/gradle-wrapper.properties` | 构建 / 其他项目文件 | 370 |
| `android/gradlew` | 构建 / 其他项目文件 | 8728 |
| `android/gradlew.bat` | 构建 / 其他项目文件 | 2937 |
| `android/settings.gradle.kts` | 构建 / 其他项目文件 | 625 |
| `app/src/dao/DAO.java` | Java 实现 | 1506 |
| `app/src/dao/MessageComparator.java` | Java 实现 | 1294 |
| `app/src/dao/PostDAO.java` | Java 实现 | 1924 |
| `app/src/dao/RandomContentGenerator.java` | Java 实现 | 3068 |
| `app/src/dao/ReportDao.java` | Java 实现 | 454 |
| `app/src/dao/UserDAO.java` | Java 实现 | 2530 |
| `app/src/dao/model/HasUUID.java` | Java 实现 | 196 |
| `app/src/dao/model/Message.java` | Java 实现 | 139 |
| `app/src/dao/model/Post.java` | Java 实现 | 1325 |
| `app/src/dao/model/Report.java` | Java 实现 | 732 |
| `app/src/dao/model/TimestampFormatter.java` | Java 实现 | 97 |
| `app/src/dao/model/TimestampFormatterTimeSinceEnglish.java` | Java 实现 | 1265 |
| `app/src/dao/model/User.java` | Java 实现 | 379 |
| `app/src/moderation/ModerationTools.java` | Java 实现 | 4737 |
| `app/src/moderation/MostStrategy.java` | Java 实现 | 1023 |
| `app/src/moderation/OldestStrategy.java` | Java 实现 | 1166 |
| `app/src/moderation/ReportedMessageIterator.java` | Java 实现 | 606 |
| `app/src/moderation/ReportedMessageIteratorFactory.java` | Java 实现 | 302 |
| `app/src/persistentdata/DataManager.java` | Java 实现 | 2629 |
| `app/src/persistentdata/DataPipeline.java` | Java 实现 | 1755 |
| `app/src/persistentdata/PersistentDataException.java` | Java 实现 | 169 |
| `app/src/persistentdata/formatted/CSVFormat.java` | Java 实现 | 1254 |
| `app/src/persistentdata/formatted/CSVFormattedFactory.java` | Java 实现 | 546 |
| `app/src/persistentdata/formatted/CSVReader.java` | Java 实现 | 3163 |
| `app/src/persistentdata/formatted/CSVWriter.java` | Java 实现 | 1743 |
| `app/src/persistentdata/formatted/FormattedFactory.java` | Java 实现 | 234 |
| `app/src/persistentdata/formatted/FormattedReader.java` | Java 实现 | 656 |
| `app/src/persistentdata/formatted/FormattedWriter.java` | Java 实现 | 421 |
| `app/src/persistentdata/io/ComputerIOFactory.java` | Java 实现 | 887 |
| `app/src/persistentdata/io/IOFactory.java` | Java 实现 | 195 |
| `app/src/persistentdata/serialization/HiddenMessageSerializer.java` | Java 实现 | 364 |
| `app/src/persistentdata/serialization/MessageSerializer.java` | Java 实现 | 798 |
| `app/src/persistentdata/serialization/PostSerializer.java` | Java 实现 | 633 |
| `app/src/persistentdata/serialization/ReportSerializer.java` | Java 实现 | 529 |
| `app/src/persistentdata/serialization/Serializer.java` | Java 实现 | 582 |
| `app/src/persistentdata/serialization/UserSerializer.java` | Java 实现 | 889 |
| `app/src/sorteddata/SortedData.java` | Java 实现 | 2347 |
| `app/src/sorteddata/SortedDataFactory.java` | Java 实现 | 303 |
| `app/src/sorteddata/SortedDataSlice.java` | Java 实现 | 3402 |
| `app/src/sorteddata/SortedDataSubject.java` | Java 实现 | 92 |
| `app/src/sorteddata/avltree/AVLIterator.java` | Java 实现 | 2428 |
| `app/src/sorteddata/avltree/AVLNode.java` | Java 实现 | 2483 |
| `app/src/sorteddata/avltree/AVLNodeEmpty.java` | Java 实现 | 651 |
| `app/src/sorteddata/avltree/AVLNodeFilled.java` | Java 实现 | 3353 |
| `app/src/sorteddata/avltree/AVLTree.java` | Java 实现 | 1282 |
| `app/src/sorteddata/avltree/AVLTreeSlice.java` | Java 实现 | 855 |
| `app/src/sorteddata/bstree/BSNode.java` | Java 实现 | 465 |
| `app/src/sorteddata/bstree/BSNodeEmpty.java` | Java 实现 | 597 |
| `app/src/sorteddata/bstree/BSNodeFilled.java` | Java 实现 | 1857 |
| `app/src/sorteddata/bstree/BSTree.java` | Java 实现 | 1175 |
| `app/src/sorteddata/sortedarraylist/SortedArrayList.java` | Java 实现 | 1879 |
| `app/src/sorteddata/sortedarraylist/SortedArrayListIterator.java` | Java 实现 | 722 |
| `app/test/ModerationToolsAddReportTests.java` | 测试 / 测试资源 | 3014 |
| `app/test/ModerationToolsGetReportsTests.java` | 测试 / 测试资源 | 7841 |
| `hackathon.iml` | IDE 文件 | 505 |
| `out/production/project_files/censor/CensorUsageDemo.class` | 旧编译产物（已跟踪） | 587 |
| `out/production/project_files/censor/ICensor.class` | 旧编译产物（已跟踪） | 167 |
| `out/production/project_files/censor/censeralgerithm.class` | 旧编译产物（已跟踪） | 10512 |
| `out/production/project_files/dao/DAO.class` | 旧编译产物（已跟踪） | 1721 |
| `out/production/project_files/dao/MessageComparator.class` | 旧编译产物（已跟踪） | 1235 |
| `out/production/project_files/dao/PostDAO$1.class` | 旧编译产物（已跟踪） | 1572 |
| `out/production/project_files/dao/PostDAO.class` | 旧编译产物（已跟踪） | 1859 |
| `out/production/project_files/dao/RandomContentGenerator.class` | 旧编译产物（已跟踪） | 4564 |
| `out/production/project_files/dao/UserDAO.class` | 旧编译产物（已跟踪） | 2938 |
| `out/production/project_files/dao/model/HasUUID.class` | 旧编译产物（已跟踪） | 144 |
| `out/production/project_files/dao/model/Message.class` | 旧编译产物（已跟踪） | 1936 |
| `out/production/project_files/dao/model/Post.class` | 旧编译产物（已跟踪） | 1043 |
| `out/production/project_files/dao/model/TimestampFormatter.class` | 旧编译产物（已跟踪） | 168 |
| `out/production/project_files/dao/model/TimestampFormatterTimeSinceEnglish.class` | 旧编译产物（已跟踪） | 1449 |
| `out/production/project_files/dao/model/User$Role.class` | 旧编译产物（已跟踪） | 1100 |
| `out/production/project_files/dao/model/User.class` | 旧编译产物（已跟踪） | 2230 |
| `out/production/project_files/persistentdata/DataManager.class` | 旧编译产物（已跟踪） | 3719 |
| `out/production/project_files/persistentdata/DataPipeline$AddToDAO.class` | 旧编译产物（已跟踪） | 341 |
| `out/production/project_files/persistentdata/DataPipeline.class` | 旧编译产物（已跟踪） | 3828 |
| `out/production/project_files/persistentdata/PersistentDataException.class` | 旧编译产物（已跟踪） | 398 |
| `out/production/project_files/persistentdata/formatted/CSVFormat.class` | 旧编译产物（已跟踪） | 903 |
| `out/production/project_files/persistentdata/formatted/CSVFormattedFactory.class` | 旧编译产物（已跟踪） | 1391 |
| `out/production/project_files/persistentdata/formatted/CSVReader$CSVIOException.class` | 旧编译产物（已跟踪） | 525 |
| `out/production/project_files/persistentdata/formatted/CSVReader.class` | 旧编译产物（已跟踪） | 2994 |
| `out/production/project_files/persistentdata/formatted/CSVWriter.class` | 旧编译产物（已跟踪） | 2457 |
| `out/production/project_files/persistentdata/formatted/FormattedFactory.class` | 旧编译产物（已跟踪） | 513 |
| `out/production/project_files/persistentdata/formatted/FormattedReader.class` | 旧编译产物（已跟踪） | 280 |
| `out/production/project_files/persistentdata/formatted/FormattedWriter.class` | 旧编译产物（已跟踪） | 304 |
| `out/production/project_files/persistentdata/io/ComputerIOFactory.class` | 旧编译产物（已跟踪） | 1330 |
| `out/production/project_files/persistentdata/io/IOFactory.class` | 旧编译产物（已跟踪） | 229 |
| `out/production/project_files/persistentdata/serialization/MessageSerializer.class` | 旧编译产物（已跟踪） | 1643 |
| `out/production/project_files/persistentdata/serialization/PostSerializer.class` | 旧编译产物（已跟踪） | 1402 |
| `out/production/project_files/persistentdata/serialization/Serializer.class` | 旧编译产物（已跟踪） | 334 |
| `out/production/project_files/persistentdata/serialization/UserSerializer$1.class` | 旧编译产物（已跟踪） | 747 |
| `out/production/project_files/persistentdata/serialization/UserSerializer.class` | 旧编译产物（已跟踪） | 2002 |
| `out/production/project_files/sorteddata/SortedData.class` | 旧编译产物（已跟踪） | 938 |
| `out/production/project_files/sorteddata/SortedDataFactory.class` | 旧编译产物（已跟踪） | 729 |
| `out/production/project_files/sorteddata/SortedDataSlice.class` | 旧编译产物（已跟踪） | 442 |
| `out/production/project_files/sorteddata/SortedDataSubject.class` | 旧编译产物（已跟踪） | 246 |
| `out/production/project_files/sorteddata/avltree/AVLIterator.class` | 旧编译产物（已跟踪） | 2860 |
| `out/production/project_files/sorteddata/avltree/AVLNode.class` | 旧编译产物（已跟踪） | 1009 |
| `out/production/project_files/sorteddata/avltree/AVLNodeEmpty.class` | 旧编译产物（已跟踪） | 1896 |
| `out/production/project_files/sorteddata/avltree/AVLNodeFilled.class` | 旧编译产物（已跟踪） | 3462 |
| `out/production/project_files/sorteddata/avltree/AVLTestBuilder.class` | 旧编译产物（已跟踪） | 3435 |
| `out/production/project_files/sorteddata/avltree/AVLTree.class` | 旧编译产物（已跟踪） | 4484 |
| `out/production/project_files/sorteddata/avltree/AVLTreeSlice.class` | 旧编译产物（已跟踪） | 1488 |
| `out/production/project_files/sorteddata/bstree/BSNode.class` | 旧编译产物（已跟踪） | 974 |
| `out/production/project_files/sorteddata/bstree/BSNodeEmpty.class` | 旧编译产物（已跟踪） | 1783 |
| `out/production/project_files/sorteddata/bstree/BSNodeFilled.class` | 旧编译产物（已跟踪） | 3036 |
| `out/production/project_files/sorteddata/bstree/BSTree.class` | 旧编译产物（已跟踪） | 2972 |
| `out/production/project_files/sorteddata/sortedarraylist/SortedArrayList.class` | 旧编译产物（已跟踪） | 3506 |
| `out/production/project_files/sorteddata/sortedarraylist/SortedArrayListIterator.class` | 旧编译产物（已跟踪） | 1606 |
| `out/production/project_files/userstate/AdminState.class` | 旧编译产物（已跟踪） | 337 |
| `out/production/project_files/userstate/GuestState.class` | 旧编译产物（已跟踪） | 1575 |
| `out/production/project_files/userstate/MemberState.class` | 旧编译产物（已跟踪） | 1469 |
| `out/production/project_files/userstate/StateManager.class` | 旧编译产物（已跟踪） | 1422 |
| `out/production/project_files/userstate/UserState.class` | 旧编译产物（已跟踪） | 498 |
| `out/test/project_files/AVLIteratorTests.class` | 旧编译产物（已跟踪） | 2860 |
| `out/test/project_files/AVLSliceTests.class` | 旧编译产物（已跟踪） | 6947 |
| `out/test/project_files/AVLTreeTests.class` | 旧编译产物（已跟踪） | 2980 |
| `out/test/project_files/CSVReaderTests.class` | 旧编译产物（已跟踪） | 5942 |
| `out/test/project_files/CSVWriterTests.class` | 旧编译产物（已跟踪） | 3746 |
| `out/test/project_files/CensorDesignTests.class` | 旧编译产物（已跟踪） | 14102 |
| `out/test/project_files/CensorLogicTests.class` | 旧编译产物（已跟踪） | 2272 |
| `out/test/project_files/PostDAOIteratorTests.class` | 旧编译产物（已跟踪） | 4086 |
| `out/test/project_files/ReflectionUtils.class` | 旧编译产物（已跟踪） | 5343 |
| `out/test/project_files/SortedDataEfficiencyComparison$TestStructure.class` | 旧编译产物（已跟踪） | 2026 |
| `out/test/project_files/SortedDataEfficiencyComparison.class` | 旧编译产物（已跟踪） | 2903 |
| `out/test/project_files/SortedDataEfficiencyTests$SortedDataConstructor.class` | 旧编译产物（已跟踪） | 410 |
| `out/test/project_files/SortedDataEfficiencyTests.class` | 旧编译产物（已跟踪） | 5069 |
| `out/test/project_files/UserDAODaoTests.class` | 旧编译产物（已跟踪） | 2635 |
| `out/test/project_files/UserDAOSingletonTests.class` | 旧编译产物（已跟踪） | 1893 |
| `out/test/project_files/UserSerializationTests.class` | 旧编译产物（已跟踪） | 2571 |
| `out/test/project_files/UserStateTests.class` | 旧编译产物（已跟踪） | 3092 |
| `out/test/project_files/meta/AVLTreeSliceMetaTests.class` | 旧编译产物（已跟踪） | 3916 |
| `out/test/project_files/meta/TestCaseRunner$1.class` | 旧编译产物（已跟踪） | 1132 |
| `out/test/project_files/meta/TestCaseRunner.class` | 旧编译产物（已跟踪） | 1828 |
| `out/test/project_files/meta/TimeoutBlock.class` | 旧编译产物（已跟踪） | 1034 |
| `picture/.gitkeep` | 界面资源 / 截图 | 1 |
| `picture/comments.png` | 界面资源 / 截图 | 497866 |
| `picture/feed.png` | 界面资源 / 截图 | 617409 |
| `picture/leaderboard.png` | 界面资源 / 截图 | 480352 |
| `picture/market.png` | 界面资源 / 截图 | 454891 |
| `readme.md` | 说明文档 | 5938 |
| `readme.zh-CN.md` | 说明文档 | 5347 |
| `uml.png` | 构建 / 其他项目文件 | 256077 |
