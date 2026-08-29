# 上游项目与同步方法

雹-GKD 同时维护两个上游来源：

| 组件 | 上游仓库 | 分支 | 当前合并基线 |
|---|---|---|---|
| 雹 Hail 原版 | https://github.com/aistra0528/Hail | `main` | `c43dc4d1c26f223cabfc5c87cfec17afaf84063b`，2026-08-27 |
| 雹二开基底 | https://github.com/zzgzhzz-star/Hail | `master` | `536ddb5a3a197583115257dd1e8d5e29a5688003` |
| GKD | https://github.com/gkd-kit/gkd | `main` | `1517080e46f9f72d3bf53f81fc0348e117a47902`，2026-08-27 |

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
2. Hail 更新优先按文件级差异合并到 `app`，保留 `com.aistra.hail`、多用户、小组件和 GKD 入口。
3. GKD 更新同步到 `gkd-feature`、`selector`、`hidden-api` 以及相关 Gradle 配置。
4. 继续移除 GKD 自更新逻辑，避免合并包下载并尝试安装独立 GKD APK。
5. 更新本文件和 `upstream-lock.json` 中的提交号。
6. 至少执行 Release 编译、雹/GKD 页面往返切换、无障碍服务绑定、规则订阅与实际跳过测试。

`雹-GKD-1.1.0` 于 2026-08-29 完成同步：Hail 更新至 `c43dc4d1c26f223cabfc5c87cfec17afaf84063b`，GKD 更新至 `1517080e46f9f72d3bf53f81fc0348e117a47902`。本轮只移植功能、修复、架构与依赖更新，跳过与功能无关的单纯翻译提交；同时保留二开版已有的多用户数据结构、文件夹小组件、公众号与 GKD 入口。

Hail 上游已将默认分支从 `master` 改为 `main`，检查脚本和本地拉取脚本均已同步修正。GKD 本轮完成 `li.songe.gkd` → `li.gkd.app` 包迁移，并将数据库抽离到 Room 3/KMP 的 `gkd-db` 模块。

由于两个上游属于不同工程历史，不能对 GKD 直接执行自动 cherry-pick；同步必须解决代码/资源冲突并完成真机回归。`.github/workflows/upstream-check.yml` 每周检查一次上游提交，有更新时会使工作流给出明确提示。

Hail 与 GKD 均按 GNU GPL v3 分发；GKD 许可副本见 `LICENSE-GKD`。
