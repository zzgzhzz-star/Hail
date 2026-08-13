# 上游项目与同步方法

雹-GKD 同时维护两个上游来源：

| 组件 | 上游仓库 | 分支 | 当前合并基线 |
|---|---|---|---|
| 雹 Hail 原版 | https://github.com/aistra0528/Hail | `master` | `d502b0a4f96917399e74658ae36bade2b6e309d2` |
| 雹二开基底 | https://github.com/zzgzhzz-star/Hail | `master` | `8485c131b5b601626ad5ee1156d837d0d7ef2296` |
| GKD | https://github.com/gkd-kit/gkd | `main` | `aec7c4b32b89aebef97c0ca622316f66f781f876`，2026-08-06 |

本仓库以雹的界面、包名和应用数据为外壳。GKD 被拆分为 `gkd-feature` 库模块，并引入其 `selector` 与 `hidden-api` 模块。GKD 不能作为独立 APK 在合并包内自行更新，因此已移除其应用版本检测/下载更新入口；规则订阅更新仍然保留。

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

发布 `雹-GKD-1.0.0` 时检测到的上游 HEAD：Hail `af2c313f65c767067b996e8a3413070d25bc892d`，GKD `6e99842dddd674953f355d3bdc1e53538f402432`。这些提交晚于本次已完成真机验证的合并基线，因此列为下一轮同步目标，不临时混入本次稳定发布。

由于两个上游属于不同工程历史，不能对 GKD 直接执行自动 cherry-pick；同步必须解决代码/资源冲突并完成真机回归。`.github/workflows/upstream-check.yml` 每周检查一次上游提交，有更新时会使工作流给出明确提示。

Hail 与 GKD 均按 GNU GPL v3 分发；GKD 许可副本见 `LICENSE-GKD`。
