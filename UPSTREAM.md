# 上游项目与同步方法

雹-GKD 同时维护两个上游来源：

| 组件 | 上游仓库 | 分支 | 当前合并基线 |
|---|---|---|---|
| 雹 Hail 原版 | https://github.com/aistra0528/Hail | `main` | `c43dc4d1c26f223cabfc5c87cfec17afaf84063b`，2026-08-27 |
| 雹二开基底 | https://github.com/zzgzhzz-star/Hail | `master` | `536ddb5a3a197583115257dd1e8d5e29a5688003` |
| GKD | https://github.com/gkd-kit/gkd | `main` | `f6c42e2f0a8926df295d33f6f086748424dc3111`，2026-09-07 |

本仓库以雹的界面、包名和应用数据为外壳。GKD 被拆分为 `gkd-feature` 库模块，并引入其 `gkd-db`、`selector` 与 `hidden-api` 模块。GKD 不能作为独立 APK 在合并包内自行更新，因此已移除其应用版本检测/下载更新入口；规则订阅更新仍然保留。

## 本地配置上游

```powershell
./scripts/setup-upstreams.ps1 -Fetch
```

脚本会配置：

- `hail-upstream` → `https://github.com/aistra0528/Hail.git`
- `gkd-upstream` → `https://github.com/gkd-kit/gkd.git`

## 更新流程

1. 运行 `./scripts/check-upstreams.ps1` 检查两个上游是否出现新提交。
2. Hail 更新必须按差异块合并到 `app`，不能直接用原版文件覆盖二开文件。保留 `com.aistra.hail`、多用户、小组件和 GKD 入口，特别核对 `PagerFragment` 和 `ApiActivity` 的每个操作是否保留 `userId`。
3. GKD 更新同步到 `gkd-feature`、`selector`、`hidden-api` 以及相关 Gradle 配置。
4. 继续移除 GKD 自更新逻辑，避免合并包下载并尝试安装独立 GKD APK。
5. 更新本文件和 `upstream-lock.json` 中的提交号。
6. 运行 `pwsh -File scripts/check-multi-user.ps1` 防止用户 ID 再次丢失；该脚本只做静态检查，不代替真机验证。
7. 至少执行 Release 编译、原应用与双开的独立冻结/解冻/启动、快捷方式和文件夹小组件、雹/GKD 页面往返切换、无障碍服务绑定、规则订阅与实际跳过测试。

2026-09-02 的 `雹-GKD-1.1.2` 修复了 1.1.0/1.1.1 同步时遗漏主页和 API 多用户调用链的问题。后续同步不得丢失目标用户的状态过滤、启动 UserHandle、快捷方式身份和延迟任务 ID。

`雹-GKD-1.2.0` 于 2026-09-17 完成 GKD 同步：GKD 更新至 `f6c42e2f0a8926df295d33f6f086748424dc3111`，包括选择器引擎、无障碍运行时、数据持久层和特权生命周期重构。Hail 上游检查到的 `c43dc4d` 之后 4 个提交均为 Weblate 翻译，按本项目规则未合并。同步继续保留二开版已有的多用户数据结构、文件夹小组件、公众号与 GKD 入口；GKD 应用更新检测与 APK 下载入口保持移除，规则订阅更新保留。合并应用继续将构建时间元数据按字符串读取，避免 Android 清单整数类型导致的启动崩溃。

Hail 上游已将默认分支从 `master` 改为 `main`，检查脚本和本地拉取脚本均已同步修正。GKD 本轮完成 `li.songe.gkd` → `li.gkd.app` 包迁移，并将数据库抽离到 Room 3/KMP 的 `gkd-db` 模块。

由于两个上游属于不同工程历史，不能对 GKD 直接执行自动 cherry-pick；同步必须解决代码/资源冲突并完成真机回归。`.github/workflows/upstream-check.yml` 每周检查一次上游提交，有更新时会使工作流给出明确提示。

Hail 与 GKD 均按 GNU GPL v3 分发；GKD 许可副本见 `LICENSE-GKD`。
