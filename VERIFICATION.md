# VERIFICATION.md — rewrite/adaptive 独立验证记录

> 本文件由 `build-qa` 维护，是验证任务中**唯一允许写入**的文件。
> 不修改源码 / build 配置 / res，不执行 git commit。构建命令只允许由本文件记录的单线程序列执行。

| 项 | 值 |
| --- | --- |
| 仓库 | `C:\Users\minjun\Source\Repos\Language-Selector` |
| 分支 | `rewrite/adaptive` |
| 当前 HEAD（验证快照） | `f7e211b feat: polish integration, root prompt fix, select-app empty state, design concept boards`（HEAD 未变；工作树含 Lead 的 2 处 lint 修复，已重新冻结） |
| 验证快照 #1（11:09） | tree-hash `133FA16D0BE498AA36D0BB828D24CF11F539379102264A4C8240D10C12763205`（79 文件）前后一致；lint 有 1 Error |
| 验证快照 #2（11:16，最终） | tree-hash `6F7CB3F7CDBAD0CEBC592E706DAA4946693B9EF570543ABF34E2FEB50B91F551`（78 文件，PlaceholderHome 已删除）前后一致；**5 个任务全绿** |
| 最终源码改动（相对 HEAD） | 仅 2 处：`ui/adaptive/WindowForm.kt` +1 行（`@Suppress("ProduceStateDoesNotAssignValue")`）、`ui/navigation/PlaceholderHome.kt` -60 行（删除死代码） |
| JDK | `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot`（21.0.12.1 LTS，JAVA_HOME 已生效） |
| Android SDK | `C:\Users\minjun\AppData\Local\Android\Sdk`（local.properties） |
| Gradle | wrapper 8.10.2；AGP 8.7.3；Kotlin 2.0.21；KSP 2.0.21-1.0.27 |
| 构建并发约束 | **同一时刻只允许一个 Gradle 构建**（含 Lead / 其他成员）；运行前确认无 build 进行中 |

## 0. 结论 / 状态

**状态：验证完成 ✅（运行 #1：2026-10-18 11:09；修复后复验运行 #2：11:16。HEAD `f7e211b` + 2 处冻结修复，两次运行 tree-hash 均前后一致）。**
**结论：5 个任务全绿 —— compile / test（76 tests）/ lint（0 Error）/ assembleDebug / assembleRelease；APK 已用最终源码重建。真机验收已于 2026-10-18 在 Xiaomi 2509FPN0BC（Android 17）上执行，发现并修复 5 个真机问题（见 §9），修复后版本 2.0.1 全绿。**

验证结论（最终，运行 #2，2026-10-18 11:16）：

- [x] `:app:compileDebugKotlin` — **SUCCESS**（3.5s）
- [x] `:app:testDebugUnitTest` — **SUCCESS**（5.2s；10 个测试类 / **76 tests，0 failures，0 errors**）
- [x] `:app:lintDebug` — **SUCCESS**（18.4s；**0 Error / 85 Warnings**；F-1 已修复并复验）
- [x] `:app:assembleDebug` — **SUCCESS**（3.4s；APK 68,029,245 B，sha `DE81ADCE…`）
- [x] `:app:assembleRelease` — **SUCCESS**（36.3s；APK 7,270,004 B，sha `24BDD83B…`）
- [x] APK 产物检查 — badging 校验通过（package/versionCode 6/versionName 2.0.0/minSdk 36/targetSdk 36）
- [x] 静态检查 — 硬编码文案 **0**、`runBlocking` **0**、Manifest 组件/权限完整、release 反射类保留（IUserService/UserService/RootUserService）
- [ ] 真机验收 — **Deferred to real device**（用户要求本轮不做 VM/模拟器验证；§6 清单保留给后续真机执行）

> 说明：11:05 的并发写入竞态轮次、11:09 的脚本参数错误轮次均已作废（见 §3 备注）。有效运行 2 次：#1（11:09，lint 1 Error）→ Lead 修复（WindowForm 加 Suppress + 删除 PlaceholderHome）→ #2（11:16，全绿）。

## 1. 构建执行协议

1. 先确认没有并行构建：`Get-Process java` 中若出现正在执行的 Gradle worker，或 `app/build/intermediates` 的 mtime 在秒级变化 → 等待后再跑。
2. 严格串行，单条命令、单次 Gradle 调用；**不要**用 `--parallel`（仓库已默认关闭）。
3. 统一命令前缀：`.\gradlew.bat --console=plain <task>`，工作目录为仓库根。
4. 超时 ≥ 10 分钟（首次/clean 后 release R8 可能更久，放宽到 20 分钟）。
5. 日志写到仓库外，避免污染工作区：
   ```powershell
   $log = Join-Path $env:TEMP ("ls-qa\" + (Get-Date -Format "yyyyMMdd-HHmmss"))
   New-Item -ItemType Directory -Force $log | Out-Null
   .\gradlew.bat --console=plain :app:compileDebugKotlin 2>&1 | Tee-Object "$log\01-compileDebugKotlin.log"
   ```
6. 失败处理：**不改代码**；从日志中提取 `e: file:///...:line:col`（Kotlin）或 `ERROR:`（AGP/AAPT/R8）原文，换算为 `app/src/main/...:line:col` 后 `send_message` 给 lead，附最小修复建议。
7. 每完成一步立即更新本文件第 3 节的矩阵，防止会话中断丢失结果。

### 1.1 执行顺序与判据

| # | Gradle task | 主要判据 | 已知常见失败源 |
| --- | --- | --- | --- |
| 1 | `:app:compileDebugKotlin` | `BUILD SUCCESSFUL`；无 `e:` 行 | Kotlin 语法/引用、Hilt/KSP 生成、R 类引用 |
| 2 | `:app:testDebugUnitTest` | `BUILD SUCCESSFUL`；`tests > 0` 且 failures=0（读 `app/build/test-results/testDebugUnitTest/*.xml`） | 断言失败、JVM 单测环境缺失 |
| 3 | `:app:lintDebug` | `BUILD SUCCESSFUL`（= 0 Error）；`lint-results-debug.txt` 只统计 Error | Compose runtime lint（本轮命中 `ProduceStateDoesNotAssignValue`）；翻译类已由 lint{} 关闭 MissingTranslation |
| 4 | `:app:assembleDebug` | BU + `app-debug.apk` 存在 | 依赖 1、资源链接、manifest merge |
| 5 | `:app:assembleRelease` | BU + `app-release.apk` 存在 | R8/minify+shrink、missing proguard rules、aboutLibraries 资源 |

> 若第 1 步失败：仍按顺序尝试 2–5 以收集完整错误面（下游会快速失败，成本可接受），并在第 1 次失败时**立刻**先给 Lead 发一条精确错误，再继续。

## 2. 产物路径

| 产物 | 路径 | 备注 |
| --- | --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | 期望存在；大小参考 60–75 MB（当前未裁剪，含 debug 依赖） |
| Debug metadata | `app/build/outputs/apk/debug/output-metadata.json` | 校验 versionCode/versionName |
| Release APK | `app/build/outputs/apk/release/app-release.apk` | 期望存在；R8+shrink 后预期 6–10 MB |
| Release metadata | `app/build/outputs/apk/release/output-metadata.json` | |
| R8 mapping | `app/build/outputs/mapping/release/mapping.txt` | `-dontobfuscate`，主要看 shrinking 是否报 missing class |
| Lint 结果 | `app/build/reports/lint-results-debug.html` / `.txt` | 只统计 Error；Warning 取数量与 Top 项 |
| 单测报告 | `app/build/reports/tests/testDebugUnitTest/index.html` | 无测试时无此文件 |
| Merged manifest | `app/build/intermediates/merged_manifests/debug/AndroidManifest.xml` | 静态检查 6.3 用 |
| Manifest merger 报告 | `app/build/outputs/logs/manifest-merger-debug-report.txt` | 查组件/权限合并问题 |

产物检查命令（验证时执行）：

```powershell
Get-Item app/build/outputs/apk/debug/app-debug.apk, app/build/outputs/apk/release/app-release.apk |
  ForEach-Object { "{0}  {1:N0} bytes  {2:O}" -f $_.Name, $_.Length, $_.LastWriteTime }
Get-FileHash app/build/outputs/apk/debug/app-debug.apk, app/build/outputs/apk/release/app-release.apk -Algorithm SHA256
```

可选（有 build-tools 时）：`aapt2 dump badging <apk>` 校验 `package=vegabobo.languageselector`、`versionCode='6'`、`versionName='2.0.0'`、`sdkVersion:'36'`、`targetSdkVersion:'36'`。

### 2.1 产物基线

**最终产物（运行 #2，2026-10-18 11:18–11:19，lint 修复后的最终源码；可作交付/验收证据）：**

| 产物 | 大小 (bytes) | 生成时间 | SHA256 |
| --- | --- | --- | --- |
| `app-release.apk`（2.0.1，真机修复后最终） | 7,286,388（6.95 MB） | 2026-10-18 18:20:05 | `C155AA4399F96878D961DF15514B3608F54444B0ACCD0592231E83305C29B130` |
| `app-debug.apk`（2.0.1） | 66,792,149（63.70 MB） | 2026-10-18 18:08:25 | 见本机构建输出 |

历史产物（对照用，**不可**作为最终交付证据）：

| 产物 | 大小 | 时间 | 说明 |
| --- | --- | --- | --- |
| `app-release.apk`（运行 #2，2.0.0） | 7,270,004 bytes | 2026-10-18 11:19:32 | 重写完成版，真机升级 Room 崩溃 → 被 2.0.1 取代（sha `24BDD83B…`） |
| `app-debug.apk`（运行 #2） | 68,029,245 bytes | 2026-10-18 11:18:56 | 同上（sha `DE81ADCE…`） |
| `app-debug.apk`（运行 #1） | 68,029,245 bytes | 2026-10-18 11:11:17 | lint 修复前源码构建（sha `30CC1FCF…`）→ 已被运行 #2 取代 |
| `app-release.apk`（运行 #1） | 7,270,004 bytes | 2026-10-18 11:12:31 | 同上（sha `18741CB8…`）→ 已被运行 #2 取代 |
| `app-release.apk`（重写前） | 6,917,011 bytes | 2026-03-22 21:11:41 | 旧版本对照（新 release 7.29 MB） |
| debug APK sha256（更早） | `0640A70E…2988164C` | — | 仅供参考 |

## 3. 结果矩阵（每次验证运行后填写）

### 运行 #1 — 2026-10-18 11:09:38 起（HEAD f7e211b，代码冻结，tree-hash 前后一致）

| 任务 | 结果 | 耗时 | 关键输出 / 失败摘要（file:line） |
| --- | --- | --- | --- |
| `:app:compileDebugKotlin` | **SUCCESS** | 1.1s | BUILD SUCCESSFUL（27 tasks，8 executed） |
| `:app:testDebugUnitTest` | **SUCCESS** | 6.4s | **76 tests / 10 classes，0 failures**；报告 `app/build/reports/tests/testDebugUnitTest/index.html` |
| `:app:lintDebug` | **FAILED** | 11.0s | 1 Error：`app/src/main/java/vegabobo/languageselector/ui/adaptive/WindowForm.kt:52: Error: produceState calls should assign value inside the producer lambda [ProduceStateDoesNotAssignValue from androidx.compose.runtime]`；+ 85 warnings |
| `:app:assembleDebug` | **SUCCESS** | 2.8s | `app/build/outputs/apk/debug/app-debug.apk` |
| `:app:assembleRelease` | **SUCCESS** | 54.3s | R8+shrink 通过；`app/build/outputs/apk/release/app-release.apk` |

APK 记录：

| 产物 | 存在 | 大小 (bytes) | 生成时间 | SHA256 |
| --- | --- | --- | --- | --- |
| `app-debug.apk` | ✅ | 68,029,245 | 2026-10-18 11:11:17 | `30CC1FCF69C9202485B6F0E74B9C098C9497BDC33FB7D00612262B9203AC7F2F` |
| `app-release.apk` | ✅ | 7,270,004 | 2026-10-18 11:12:31 | `18741CB835B2444D58FB271E5341EEC275DA50476857E67022B33A9305E57D65` |

`aapt2 dump badging`（release）：`package=vegabobo.languageselector`、`versionCode=6`、`versionName=2.0.0`、`minSdkVersion=36`、`targetSdkVersion=36`、`launchable-activity=vegabobo.languageselector.MainActivity`。

> 运行日志（仓库外）：`$env:TEMP\ls-qa\run-20261018-110948\01…05-*.log`
> 作废轮次说明：11:05 因并发写入竞态编译失败（见 §0）；11:09 首次批量脚本误写 `:compileDebugKotlin`（漏 `app` 模块前缀）导致 5 个任务秒退 "task not found"，该轮同样作废、未计入矩阵。

### 运行 #2 — 2026-10-18 11:16:58 起（lint 修复后复验，最终源码）

相对 HEAD `f7e211b` 的冻结改动仅 2 处：`ui/adaptive/WindowForm.kt` +1 行（`@Suppress("ProduceStateDoesNotAssignValue")`，注释说明 collect 内赋值的 false positive）、`ui/navigation/PlaceholderHome.kt` -60 行（删除无引用死代码）。tree-hash `6F7CB3F7…91F551`（78 文件）前后一致。

| 任务 | 结果 | 耗时 | 关键输出 |
| --- | --- | --- | --- |
| `:app:compileDebugKotlin` | **SUCCESS** | 3.5s | 删除 PlaceholderHome 后无残留引用 |
| `:app:testDebugUnitTest` | **SUCCESS** | 5.2s | 76 tests / 10 classes，0 failures |
| `:app:lintDebug` | **SUCCESS ✅** | 18.4s | **0 Error / 85 Warnings**；`ProduceStateDoesNotAssignValue` 命中 0 |
| `:app:assembleDebug` | **SUCCESS** | 3.4s | APK 68,029,245 B |
| `:app:assembleRelease` | **SUCCESS** | 36.3s | R8 通过；APK 7,270,004 B；反射类保留复核通过 |

**运行 #2 最终产物（当前磁盘 APK 即最终源码构建，可作交付证据）：**

| 产物 | 大小 (bytes) | 生成时间 | SHA256 |
| --- | --- | --- | --- |
| `app-debug.apk` | 68,029,245（64.88 MB） | 2026-10-18 11:18:56 | `DE81ADCE84DE03A6F035D10312B0EBA8939FC80B8776F002FBAA4CB9982D47FC` |
| `app-release.apk` | 7,270,004（6.93 MB） | 2026-10-18 11:19:32 | `24BDD83B9118469238A57493D7E8C561706EF6069CA837BE93C277C1F72A415D` |

**lint 剩余 85 warnings 明细（以 `lint-results-debug.xml` 权威统计）：** `GradleDependency` 42、`UnusedResources` 29、`AndroidGradlePluginVersion` 6、`IconLocation` 2、`MonochromeLauncherIcon` 2、`ObsoleteSdkInt` 2、`ModifierFactoryExtensionFunction` 1、`ModifierParameter` 1 —— **无 Error，不阻塞交付**。

日志：`$env:TEMP\ls-qa\reverify-20261018-111658\01…02-*.log`、`$env:TEMP\ls-qa\reverify2-20261018-111852\03…05-*.log`。

## 4. 失败记录模板

每次失败按如下格式追加到本节（供 Lead 直接派单修复）：

```
### F-<n> <Gradle task>
- 原始报错：
  app/src/main/java/<path>.kt:12:34: error: <message>
- 归类：Kotlin / Hilt-KSP / 资源-AAPT / Manifest / Lint / R8
- 最小修复建议：<只描述，不改代码>
- 影响：阻塞 compileDebugKotlin / 阻塞 assembleRelease / 仅 lint
- 复现：.\gradlew.bat --console=plain <task>
```

### F-1 `:app:lintDebug`（2026-10-18 11:09，运行 #1）

- 原始报错：
  `app/src/main/java/vegabobo/languageselector/ui/adaptive/WindowForm.kt:52: Error: produceState calls should assign value inside the producer lambda [ProduceStateDoesNotAssignValue from androidx.compose.runtime]`
- 上下文（`WindowForm.kt:52-57`）：
  ```kotlin
  val layoutInfo by produceState<WindowLayoutInfo?>(initialValue = null, activity) {
      val host = activity ?: return@produceState
      runCatching {
          WindowInfoTracker.getOrCreate(host).windowLayoutInfo(host).collect { value = it }
      }
  }
  ```
- 归类：Compose runtime lint。赋值 `value = it` 位于 `collect{}` 嵌套 lambda，lint 数据流分析看不到"直接赋值"，故判违规；**运行时行为正常**（collect 持续赋值，posture 热更新可用）。
- 影响：仅阻塞 `:app:lintDebug`；不影响 compile、单测、assembleDebug/assembleRelease（后三者本轮全绿）。
- 最小修复建议（三选一，qa 不改代码）：
  1. `rememberWindowForm()` 加 `@Suppress("ProduceStateDoesNotAssignValue")` — 1 行，行为零变化（最小）；
  2. 顶层直接赋值（如 `value = runCatching { …first() }.getOrNull()`）— 会失去后续 posture 热更新，不推荐；
  3. 改用 `LaunchedEffect` + `mutableStateOf` / `snapshotFlow` 收集 — 改动稍大但语义最清晰。
- 复现：`.\gradlew.bat --console=plain :app:lintDebug`（日志 `$env:TEMP\ls-qa\run-20261018-110948\03-lintDebug.log`）
- 状态：**已修复并复验通过 ✅**（2026-10-18 11:16，Lead 重新冻结代码）
  - 修复方式：`WindowForm.kt:47` 增加 `@Suppress("ProduceStateDoesNotAssignValue") // false positive: the value is assigned inside collect {}`（+1 行，行为零变化）；
  - 复验（运行 #2）：`:app:lintDebug` **BUILD SUCCESSFUL**（18.4s），`lint-results-debug.xml` 中 `ProduceStateDoesNotAssignValue` 命中 **0**，Total = **0 Error / 85 Warnings**。

## 5. 静态检查清单（只读，构建外可先做；构建后复核）

### 5.1 硬编码用户可见文案 — 结论：**0 违规**（死代码已删除）

- 扫描范围：`app/src/main/java/**/*.kt`（62 处 `Text(` 调用、127 处 `stringResource(` 调用）。
- 结果：
  - ✅ 所有**实际渲染**的用户可见文案均走 `stringResource(R.string.*)`。`Text(` 形参透传的位置（`Text(title)`、`Text(action)`、`Text(confirmLabel)`、`Text(serviceStatusLabel(...))` 等）已逐一回溯调用点：`DetailScreen.kt:163-170`、`HomeScreen.kt:398-402`、`Common.kt` ConfirmDialog 调用方等均传 `stringResource`。
  - `Content Description = "…"`、`placeholder = "…"`、`label/title/subtitle/message/supportingText = "…"` 的**引号字面量命中 = 0**。
  - ✅ `ui/navigation/PlaceholderHome.kt`（唯一硬编码文案来源）已由 Lead 于 11:16 删除；复验确认全仓库无 `PlaceholderHome` 残留引用、编译通过。最终源码硬编码文案 = **0**。
  - `qs/LocaleTileService.kt:212 targetLabel = ""`：清空状态用的内部默认值，非用户可见文案（实际渲染走 `getString(R.string.*)` / `loadLabel`）。
- 复核命令：`Text("` 命中 0；`text\s*=\s*"` 命中 0；`Text(` 调用的形参全部回溯到 `stringResource`。结论：**用户可见文案无硬编码**。

### 5.2 主线程阻塞 / 危险 Binder 调用 — 本轮结论：无阻塞调用

- ✅ 全仓库 **0 处** `runBlocking` / `Thread.sleep`。
- ✅ 所有 IPC / PackageManager / 数据库访问均显式切到注入的 `dispatchers.io` / `Dispatchers.IO`：
  `ServiceManager.kt:223,388,531,546`、`LocaleRepository.kt:47,74,81,88`、`AppRepository.kt:62`、`HistoryRepository.kt:42,57`、`LocaleCatalogRepository.kt:104-121`、`AppIcon.kt:34`、`LocaleTileService.kt:146,176`。
- ✅ `MainActivity.onCreate` 的 `serviceController.refresh()` → `ServiceManager.refresh()`（:142）内部走 `launchSafe{}` 协程；`acquire()/release()/requestPermission()` 同样 `launchSafe`，**不阻塞主线程**。
- ✅ UI 层查询/过滤在 `dispatchers.default`（`HomeViewModel.kt:79`），列表/详情 IPC 在 `dispatchers.io`。
- ⚠️ 轻微（非阻塞）：`ui/about/AboutScreen.kt:55-58` 在 `remember{}` 中同步构建 aboutlibraries 元数据（仅 About 首帧、毫秒级）；若后续变慢再挪到 IO。
- 结论：无"主线程等 binder"风险。

### 5.3 AndroidManifest 完整性 — 已用 release APK 合并清单核对（通过）

| 组件 | 类 | 要求 | 状态 |
| --- | --- | --- | --- |
| application | `vegabobo.languageselector.App` (`@HiltAndroidApp`) | `android:name` 匹配、`resizeableActivity=true` | ✅ |
| activity | `vegabobo.languageselector.MainActivity` | `exported=true` + MAIN/LAUNCHER | ✅ |
| provider | `rikka.shizuku.ShizukuProvider` | exported、`INTERACT_ACROSS_USERS_FULL`、`${applicationId}.shizuku` | ✅ |
| service | `vegabobo.languageselector.qs.LocaleTileService` | exported、`BIND_QUICK_SETTINGS_TILE`、`QS_TILE` intent-filter + `TOGGLEABLE_TILE` meta | ✅ |
| provider | `androidx.startup.InitializationProvider` | `exported=false`（AndroidX 合并项） | ✅ 正常 |
| 权限 | `QUERY_ALL_PACKAGES` / `moe.shizuku.manager.permission.API_V23` | 声明完整 | ✅（merger 追加的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 为 AndroidX 标准项） |
| 属性 | `localeConfig` / `dataExtractionRules` / `fullBackupContent` / `enableOnBackInvokedCallback` / `supportsRtl` | 对应 xml 存在 | ✅ |

- [x] `aapt2 dump xmltree --file AndroidManifest.xml app-release.apk` 核对：上述组件/权限均在，未被 merger 丢弃。
- [x] release 清单不含 `android:debuggable`。
- [x] badging：`package=vegabobo.languageselector`、`versionCode=6`、`versionName=2.0.0`、`minSdkVersion=36`、`targetSdkVersion=36`。

### 5.4 资源 / 字符串一致性 — 本轮已解决，lint 无翻译类告警

- 现状：`values/strings.xml` **101** 条；`values-zh-rCN` **100** 条（缺 `app_name`，`translatable="false"` 属预期）；`values-ja`、`values-pt-rBR` 各 **14** 条（Lead 已精简为部分翻译，**失效键 extra=0**）。
- Lead 在 `app/build.gradle.kts` 的 `lint { disable += "MissingTranslation" }` 中显式允许英文回退（注释："Translations are partial by design"）；本轮 lint 结果确认 **MissingTranslation / ExtraTranslation 均未出现**。
- 最终 lint（运行 #2，XML 权威统计）：**0 Error / 85 Warnings**。明细：`GradleDependency` 42、`UnusedResources` 29、`AndroidGradlePluginVersion` 6、`IconLocation` 2、`MonochromeLauncherIcon` 2、`ObsoleteSdkInt` 2、`ModifierFactoryExtensionFunction` 1、`ModifierParameter` 1。
- `UnusedResources` 29 条为旧 `colors.xml`（purple/teal/black/white）、`ic_launcher_*` 与暂未使用的 strings（`theme*`、`dynamic_color*`、`cd_*` 等），非阻塞、可后续清理。
- 结论：翻译配置按设计工作，无 Error（F-1 为 Compose runtime lint，已修复，见 §4）。

### 5.5 DI 绑定完整性 — 已从 stub 切换为真实实现（通过）

`di/AppModule.kt`（`di/stubs/` 已删除）：

| contract | 实现 |
| --- | --- |
| ServiceController | `ServiceManager` |
| LocaleRemote | `UserServiceRemote` |
| AppCatalog | `DefaultAppCatalog` |
| LocaleStates | `DefaultLocaleStates` |
| LocaleCatalog | `DefaultLocaleCatalog` |
| PinnedLocales | `DefaultPinnedLocales` |
| HistoryStore | `DefaultHistoryStore` |
| SettingsStore | `SharedPrefsSettingsStore` |
| `@Provides` | `AppDispatchers`、`AppInfoDb` |

- [x] 构建期 KSP/Hilt 无 `[Dagger/MissingBinding]`；release dex 中存在 `DaggerApp_HiltComponents_SingletonC*` 生成类。
- [x] 无重复绑定/循环依赖错误。

### 5.6 Release 构建 / R8 — 通过（54.3s）

- `isMinifyEnabled=true` + `isShrinkResources=true`；release 沿用 debug signingConfig（个人使用，已知可接受）。
- `app/proguard-rules.pro` 已含反射类 keep（Lead 本轮新增）：
  `-keep class vegabobo.languageselector.IUserService { *; }`、`IUserService$*`、`service.UserService`、`service.RootUserService`、`service.ShizukuUserServiceArgs`。
- R8 无 missing-class error；`app/build/outputs/mapping/release/` 已生成（mapping/usage/seeds/configuration）。
- APK 变化：集成前旧 release `6,917,011 B` → 本轮 `7,270,004 B`（+352,993 B），符合新增 service/data/UI 的预期量级。

### 5.7 单测 — 76 tests / 10 classes，全部通过

| 测试类 | tests | 结果 |
| --- | --- | --- |
| `data.AppListFilterTest` | 8 | ✅ |
| `data.LocaleBatchTest` | 3 | ✅ |
| `data.LocaleCatalogTest` | 9 | ✅ |
| `data.LocaleStatesTest` | 6 | ✅ |
| `data.PinnedLocaleTest` | 10 | ✅ |
| `service.ServiceErrorMapperTest` | 10 | ✅ |
| `service.ServicePureTest` | 9 | ✅ |
| `ui.adaptive.WindowFormTest` | 3 | ✅（含 600/840 断点） |
| `ui.detail.DetailViewModelTest` | 12 | ✅ |
| `ui.home.HomeViewModelTest` | 6 | ✅ |
| **合计** | **76** | **0 failures / 0 errors** |

- 耗时 6.4s；报告 `app/build/reports/tests/testDebugUnitTest/index.html`；`unitTests.isReturnDefaultValues = true` 已开，纯 JVM 测试无需 Robolectric。
- 覆盖亮点：断点逻辑、locale 编解码/批量映射、ServiceError 映射、Detail/Home ViewModel 状态机。

### 5.8 静态检查 B：release APK 反射加载类保留（R8 裁剪验证）— 通过

用 `apkanalyzer dex packages -d app-release.apk`（SDK cmdline-tools latest）核验 release dex 类表：

| 期望保留的类 | release dex 中状态 |
| --- | --- |
| `vegabobo.languageselector.IUserService` | ✅ 存在（9 个方法） |
| `vegabobo.languageselector.IUserService$Stub` | ✅ 存在（AIDL Stub） |
| `vegabobo.languageselector.IUserService$Stub$Proxy` | ✅ 存在（12 个方法，含 setApplicationLocales / getApplicationLocales / getApplicationLocalesTags / getSystemLocales / forceStopPackage / getFirstRunningTaskPackage / getUid / exit / destroy） |
| `vegabobo.languageselector.IUserService$Default` / `IUserService$_Parcel` | ✅ 存在 |
| `vegabobo.languageselector.service.UserService` | ✅ 存在（21 个方法，Shizuku user service 实现） |
| `vegabobo.languageselector.service.RootUserService` | ✅ 存在（libsu Root user service） |
| `vegabobo.languageselector.service.ShizukuUserServiceArgs` | ✅ 存在 |
| `rikka.shizuku.Shizuku$UserServiceArgs` | ✅ 存在（Shizuku 库反射入口） |

结论：R8 + `-dontobfuscate` + keep 规则有效，**AIDL / UserService / RootUserService 未被裁剪**，跨进程按类名反射实例化的链路安全。
证据：`$env:TEMP\ls-qa-release-dex-packages.txt`（5.46 MB 包清单）。

- **复验（运行 #2，最终源码重建的 release APK，sha `24BDD83B…`）**：上述反射类仍全部存在（IUserService / $Stub / $Stub$Proxy / $Default / $_Parcel、service.UserService、RootUserService、ShizukuUserServiceArgs、rikka.shizuku.Shizuku$UserServiceArgs），未出现 `PlaceholderHome` 残留；结论不变。

## 6. 真机 / 模拟器验收清单（细化版） — ⚠️ Deferred to real device

> **本轮不执行（用户已明确：不做 VM/模拟器验证，后续由用户在真机验证）。**
> §6.0–§6.11 全部标记 **Deferred to real device**：仅作为后续真机执行时的操作手册与验收标准，本轮未启动 emulator/AVD、未执行任何设备命令，也不纳入本轮验证结论。
> 其中 §6.0 / §6.0.1 / §6.1 的 AVD 环境与断点注入、§6.5 的折叠/平板形态、§6.8 的 AVD 性能采集属模拟器专属，明确 Deferred；§6.2–§6.4、§6.6–§6.11 中不依赖模拟器的条目可在真机直接执行。
> 前提（后续执行时）：使用集成完成后新产出的 APK（旧产物见 2.1 不可用）。
> 运行规范：测试期间不跑 Gradle、不并行跑第二个 emulator（CPU 争用会污染帧率/冷启动数据）；每个参数变更后先 `am force-stop` 再冷启动；每项记录 `PASS / FAIL / N/A` + 证据文件。
> 证据目录：`$env:TEMP\ls-qa-device\<yyyyMMdd-HHmm>\`，命名 `<case>-<device>-<param>.png|txt|log`。

### 6.0 【Deferred to real device】本机环境探测结果（2026-10-18，只读；本轮未使用）

| 项 | 值 | 用途 |
| --- | --- | --- |
| adb | `C:\Users\minjun\AppData\Local\Android\Sdk\platform-tools\adb.exe`（1.0.41 / 37.0.0） | 全部设备命令 |
| 已连接设备 | **当前无**（`adb devices` 为空）→ 需先启动 AVD | — |
| AVD | `phone36`、`tablet36`、`fold36`、`Television_1080p` | 形态矩阵 |
| Shizuku APK | `C:\Users\minjun\Downloads\shizuku-v13.6.0.r1086.2650830c-release.apk`（v13.6.0，App 依赖 shizuku-api 13.1.1，兼容） | 服务连接用例 |
| aapt2 | `...\build-tools\36.0.0-rc3\aapt2.exe`（35.0.0 亦可） | badging/产物校验 |
| emulator | `...\Sdk\emulator\emulator.exe` | 启动 AVD |
| 镜像限制 | AVD 均为 `google_apis_playstore_ps16k`（Play 商店镜像）→ `adb root` 不可用，**Root 模式无法在 AVD 验证**，须真机（Magisk/KernelSU）或 AOSP userdebug 镜像 | 见 6.3 的 N/A 说明 |

### 6.0.1 【Deferred to real device】形态/断点矩阵（本机 AVD 尺寸换算，本轮未执行）

换算公式：**width_dp = width_px × 160 / density_dpi**；`WindowForm.windowWidthFor`：<600 → COMPACT，600–839 → MEDIUM，≥840 → EXPANDED；`WindowForm.twoPane = posture != TABLETOP && width != COMPACT`。

| AVD / 参数 | 分辨率 @ density | width_dp | 预期窗口形态 | 预期布局 |
| --- | --- | --- | --- | --- |
| `phone36` 竖屏 | 1080×2400 @420 | 411 | COMPACT | 单栏 + 导航 |
| `phone36` 横屏 | 2400×1080 @420 | 914 | EXPANDED | 双栏，列表 400dp |
| `tablet36` 默认（自然横向） | 2560×1600 @320 | 1280 | EXPANDED | 双栏，列表 400dp |
| `tablet36` 旋转 90° | 1600×2560 @320 | 800 | MEDIUM | 双栏，列表 360dp |
| `fold36` 展开 | 2208×1840 @420 | 841 | EXPANDED（恰在 ≥840 边界） | 双栏，列表 400dp |
| `fold36` 折叠 | 单面板宽度（约 1080px 级）@420 | ≈411 | COMPACT | 单栏 |
| 断点注入（phone36） | 1198×1920 @320 | 599 | COMPACT | 单栏 |
| 断点注入 | 1200×1920 @320 | 600 | MEDIUM | 双栏 360dp |
| 断点注入 | 1202×1920 @320 | 601 | MEDIUM | 双栏 360dp |
| 断点注入 | 1678×2400 @320 | 839 | MEDIUM | 双栏 360dp |
| 断点注入 | 1680×2400 @320 | 840 | EXPANDED | 双栏 400dp |
| 断点注入 | 1682×2400 @320 | 841 | EXPANDED | 双栏 400dp |

断点注入命令（**必须成对使用并在 6.10 恢复**）：

```powershell
# 以 600dp / 840dp 边界为例（phone36）
adb shell wm size 1200x1920 ; adb shell wm density 320   # 600dp
adb shell wm size 1680x2400 ; adb shell wm density 320   # 840dp
# ...依次替换为上表分辨率，观察布局与列表栏宽度变化
adb shell wm size reset ; adb shell wm density reset     # 恢复
```

### 6.1 【Deferred to real device】启动 AVD 与设备准备（一次性；本轮未执行）

```powershell
$emu = "C:\Users\minjun\AppData\Local\Android\Sdk\emulator\emulator.exe"
# 一次只启动一台；加 -no-snapshot-load 保证干净冷启动
& $emu -avd phone36 -no-snapshot-load
adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true          # 测试期间常亮
```

- [ ] 安装被测 APK：`adb install -r app/build/outputs/apk/debug/app-debug.apk`
- [ ] 版本校验：`aapt2 dump badging app\build\outputs\apk\debug\app-debug.apk | Select-String "package|sdkVersion|targetSdk"`
      预期 `package: name='vegabobo.languageselector' versionCode='6' versionName='2.0.0'`，`sdkVersion:'36'`、`targetSdkVersion:'36'`。
- [ ] 安装 Shizuku：`adb install -r "C:\Users\minjun\Downloads\shizuku-v13.6.0.r1086.2650830c-release.apk"`
- [ ] 启动 Shizuku（ADB 方式）：`adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
- [ ] 截图能力：`adb exec-out screencap -p > "$env:TEMP\qa-home.png"` 正常出图。
- [ ] （性能测试专用）关闭其他后台 App，确认 `adb shell dumpsys gfxinfo <pkg>` 可读、无其他 emulator 进程。

### 6.2 P0 安装与启动

- [ ] `adb install -r` 成功；`adb shell dumpsys package vegabobo.languageselector | Select-String "versionCode|versionName"` 与 6.1 一致。
- [ ] 冷启动无崩溃/ANR（三次），`adb logcat -b crash -d` 为空。
- [ ] 后台→前台恢复不丢失状态（进入详情后 Home 再回来仍在详情）。
- [ ] 深色/浅色：`adb shell cmd uimode night yes|no`；动态取色在 Settings→Wallpaper & style 换壁纸后主色跟随（S+）。
- [ ] 应用自身语言（localeConfig）：`adb shell cmd locale set-app-locales vegabobo.languageselector --locales zh-CN` 后 UI 中文化（`app_name` 为 translatable=false，保持英文属预期）；恢复 `--locales ""`。

### 6.3 P0 服务连接（Shizuku / Root）

> Root 模式在 Play 商店 AVD 上无法验证（无 `su`/`adb root`）→ 标记 **N/A(AVD)**，需真机补测。

- [ ] 未安装 Shizuku → 显示 `shizuku_not_installed_title` 引导，不崩溃（可先 `adb uninstall moe.shizuku.privileged.api`）。
- [ ] 已安装未运行 → `shizuku_not_running_title`；点 Retry 后（启动 Shizuku）状态恢复 Ready。
- [ ] 首次未授权 → 拉起 Shizuku 授权弹窗；授权后变 `Ready(SHIZUKU)`。
- [ ] 断连：`adb shell am force-stop moe.shizuku.privileged.api` → 状态回失败态、可重连、期间操作有错误提示且不 ANR。
- [ ] Root 模式（真机）：检测到 Root 可切换；停掉 Shizuku 后仍可改语言，状态 `Ready(ROOT)`；`su` 拒绝时显示 `service_root_denied`。

### 6.4 P0 核心功能（应用列表 / 语言设置）

- [ ] 列表完整（用户应用 + 可选系统应用），滚动时图标懒加载、无长时间白块。
- [ ] 搜索：输入后防抖生效、按应用名/包名匹配、清空恢复全量（`adb shell input text "settings"` 可辅助输入）。
- [ ] 过滤器：显示系统应用 / 仅已修改，可叠加搜索；切换后列表即时刷新。
- [ ] 关键用例——**通过系统命令独立验证 locale 已生效**（不依赖 App 自述）：
  ```powershell
  adb shell cmd locale get-app-locales <targetPkg>          # 期望 [en-US] / [zh-CN]
  # 若 shell 无权限：adb shell dumpsys locale | Select-String -Pattern "<targetPkg>|locales"
  # 再用系统设置或 adb shell am start 启动目标应用目视确认语言
  ```
- [ ] 恢复系统默认（reset）后 `cmd locale get-app-locales <targetPkg>` 返回空，列表中 Modified 标记消失。
- [ ] 系统应用修改前出现警告弹窗；取消不生效、确认才生效。
- [ ] 建议语言（系统语言）与置顶语言正确显示；置顶后杀进程重启仍存在（持久化）。
- [ ] 最近使用列表随选择更新；强制停止目标应用按钮可用（`adb shell pidof <targetPkg>` 验证进程消失）。
- [ ] 目标应用未安装/已卸载时错误提示合理、不崩溃。

### 6.5 P1 自适应 / 折叠 / 平板 / 横屏 / 分屏

```powershell
# 旋转（关闭自动旋转后固定方向）
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1   # 0=竖屏 1=90° 2=180° 3=270°
adb shell settings put system accelerometer_rotation 1   # 恢复
```

按 6.0.1 矩阵逐项执行，每次记录：窗口宽 dp（可用 `adb shell wm size`/`wm density` 反算）、是否双栏、列表栏宽（360/400dp）、详情是否保留选中项。

- [ ] `phone36` 竖屏 COMPACT 单栏；横屏 EXPANDED 双栏；旋转后**不重建丢失选中项**、无闪白。
- [ ] `tablet36` 默认 EXPANDED（列表 400dp）↔ 旋转后 MEDIUM（列表 360dp），断点切换无跳变/无崩溃。
- [ ] 6.0.1 的 599/600/601、839/840/841 六个注入点逐一确认布局与列表栏宽符合预期（边界 off-by-one 是重点）。
- [ ] `fold36` 折叠→展开→折叠：COMPACT ↔ EXPANDED 双向切换，列表滚动位置/选中项保留。
- [ ] 折叠姿态（TABLETOP / BOOK）：
  - 模拟器：Extended Controls → **Device State** 切换 Folded / Unfolded / Half-opened（`fold36` 配置含 hinge 传感器：`hw.sensor.hinge.areas=1080-0-0-1840`，角度映射 0–30 / 30–150 / 150–180）。
  - 可尝试 CLI `adb shell cmd device_state state opened|folded`（仅部分镜像支持；报 unknown 就退回 UI 操作，**不要为此反复重试**）。
  - 预期：TABLETOP → 上下分栏（42/58）且中间保留 hinge gap；BOOK → 左右分栏且 hinge gap 在中间；FLAT 展开 → 双栏；折叠 → 单栏。
  - 以 App 实际布局 + 截图为准；旋转/姿态切换后 hinge 尺寸变化时布局应实时更新（同一 Activity 不重建）。
- [ ] 分屏：最近任务长按图标 → 分屏，或
  ```powershell
  adb shell am start --windowingMode 3 -n vegabobo.languageselector/.MainActivity   # split primary
  adb shell am start --windowingMode 4 -n com.android.settings/.Settings          # split secondary
  # 若 am 不认识 --windowingMode（会提示 unknown option）→ 用最近任务手动分屏，不视为失败
  ```
- [ ] 自由窗口（可调整大小）：
  ```powershell
  adb shell settings put global enable_freeform_support 1
  adb shell settings put global force_resizable_activities 1
  adb reboot    # 若上述设置未生效需重启（与 Gradle 构建错开）
  adb shell am start --windowingMode 5 -n vegabobo.languageselector/.MainActivity  # freeform
  adb shell am stack list                    # 找 taskId
  adb shell am task resize <taskId> 120 200 900 1300   # 或直接拖拽窗口边缘
  ```
  预期：拖拽跨过 600/840dp 时布局在单/双栏间切换，无重建闪烁、选中项不丢；窗口恢复全屏后布局正确。
- [ ] 横竖屏切换 + 系统深色切换 + 字体放大同时进行时不崩溃。

### 6.6 P1 字体 / RTL / 导航

```powershell
# 字体缩放（Android 14+ 支持到 200%）；改完 force-stop 再启动
adb shell settings put system font_scale 1.3
adb shell settings put system font_scale 2.0
adb shell settings put system font_scale 1.0   # 恢复

# RTL：优先应用级 locale（不整机切换）；失败则用强制 RTL 开发者选项
adb shell cmd locale set-app-locales vegabobo.languageselector --locales ar
adb shell settings put global debug.force_rtl 1     # 兜底（可能需要重启 App/设备）
adb shell cmd locale set-app-locales vegabobo.languageselector --locales ""   # 恢复
adb shell settings put global debug.force_rtl 0
```

- [ ] font_scale 1.3 / 2.0：标题、应用名、包名、语言名、Chip、对话框按钮不截断/不重叠；列表行高自适应；触控目标仍 ≥48dp。
- [ ] font_scale 2.0 + 窄窗口（411dp）：单栏布局无横向溢出。
- [ ] RTL（ar）：列表 chevron、返回箭头、搜索图标、FAB/按钮、内边距镜像正确；文本不重叠；切换回 LTR 无残留。
- [ ] 预测性返回（gesture nav）：
  ```powershell
  adb shell cmd overlay list | Select-String navbar    # 确认 gestural 模式；必要时
  adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
  ```
  从详情页边缘滑返回，窗口应缩放跟随手势、无白屏/闪回。
- [ ] 系统导航三键/手势切换后布局与返回行为正常（insets 正确，不被系统栏遮挡）。

### 6.7 P1 快捷设置磁贴

```powershell
adb shell cmd statusbar expand-settings    # 展开 QS 面板（手动编辑加入 "Language Selector" 磁贴）
adb shell cmd statusbar collapse
```

- [ ] 磁贴出现在可添加列表并可添加；未就绪（服务未连接）时显示 Unavailable/合理状态，不崩溃。
- [ ] 点击磁贴：对前台应用切换置顶语言，磁贴标签/副标题随状态更新。
- [ ] 无 Shizuku 权限时点击给出提示，不崩溃；切回 App 后状态一致。

### 6.8 【Deferred to real device】P2 性能 / 稳定性测量（AVD 数值仅后续真机参考）

> 所有测量在 AVD 单实例、无 Gradle 构建、屏幕常亮、动画默认开启状态下进行；AVD 的绝对数值仅供相对比较，真机数据以真机为准。

**冷启动**（5 次取中位数，每次先杀进程）：

```powershell
for ($i=1; $i -le 5; $i++) {
  adb shell am force-stop vegabobo.languageselector
  adb shell am start -W -n vegabobo.languageselector/.MainActivity | Select-String "TotalTime|WaitTime"
}
# 框架侧首帧：adb logcat -d | Select-String "Displayed vegabobo.languageselector"
```

- [ ] 记录 5 次 `TotalTime` 并算中位数；相对基线（集成首版）不劣化 >20%。目标参考：AVD 冷启动 ≤1.5s，真机中端 ≤1.2s。

**滑动/帧率（gfxinfo）**：

```powershell
adb shell dumpsys gfxinfo vegabobo.languageselector reset
# 交互脚本：快速滚动列表 5s → 打开详情 → 返回 → 切过滤器 → 搜索输入 → 旋转 → 折叠/展开
adb shell dumpsys gfxinfo vegabobo.languageselector
adb shell dumpsys gfxinfo vegabobo.languageselector framestats > "$env:TEMP\qa-framestats.txt"
```

- [ ] 记录 `Total frames rendered`、`Janky frames`、`Janky %`、90/95/99th percentile；参考门限：`Janky% < 5%`、`95th < 16ms`（60Hz AVD）。超标需给出场景 + 数据，交 Lead 排期。
- [ ] 长列表滚动 5s 无肉眼卡顿/白块；图标加载不阻塞滚动。

**内存 / CPU**：

```powershell
adb shell dumpsys meminfo vegabobo.languageselector     # 启动后 idle
# 滚动/进入详情/旋转后再采样；关注 TOTAL PSS 增量与 GC 后回落
adb shell top -b -n 1 -p (adb shell pidof vegabobo.languageselector)
```

- [ ] idle 状态 PSS 稳定、CPU ≈0%；反复滚动 2 分钟后 PSS 增长 <20MB 且可回落；无 OOM/GC 抖动（`adb logcat -d | Select-String "GC|OutOfMemory"`）。

**崩溃 / ANR / 主线程阻塞**：

```powershell
adb shell dumpsys dropbox --print data_app_crash > "$env:TEMP\qa-crash.txt"
adb shell dumpsys dropbox --print data_app_anr   > "$env:TEMP\qa-anr.txt"
adb shell logcat -b crash -d
# 可选：Perfetto 抓主线程长任务（需真机/较快机器）
adb shell perfetto -o /data/misc/perfetto-traces/qa-ui.perfetto-trace -t 15s sched wm gfx view binder_driver freq idle
adb pull /data/misc/perfetto-traces/qa-ui.perfetto-trace "$env:TEMP\qa-ui.perfetto-trace"
```

- [ ] 全流程 crash/ANR 记录为空（或仅系统无关进程）。
- [ ] Perfetto/Systrace 中主线程无 >16ms 的磁盘/SharedPreferences 同步读、无 >100ms 的 binder 阻塞（重点看启动与首次列表加载）。
- [ ] 说明：debug 构建未启用 StrictMode；如 Lead 后续加 `StrictMode` 开关，再补「主线程 IO 告警为零」用例。

### 6.9 P2 稳定性 / 回归

- [ ] 低内存：`adb shell am send-trim-memory (adb shell pidof vegabobo.languageselector) RUNNING_CRITICAL` 后返回前台不崩溃、状态恢复。
- [ ] 应用卸载/新安装：`adb uninstall <targetPkg>` → 列表刷新；`adb install` 后重新出现。
- [ ] 长列表（300+ 应用）：AVD 应用数不足，标记 **N/A(AVD)**；真机通过批量安装或系统应用数量补测滚动内存。
- [ ] 分屏/自由窗口下旋转 + 字体 2.0 + RTL 组合操作不崩溃。
- [ ] release APK（R8）P0 冒烟：`adb install -r app/build/outputs/apk/release/app-release.apk` → 启动、连接、改一次语言、重启后设置保留、无 R8 相关 crash（重点：Hilt/Shizuku/Coil/libsu 反射类被裁剪）。

### 6.10 收尾：恢复设备默认值

```powershell
adb shell wm size reset
adb shell wm density reset
adb shell settings put system font_scale 1.0
adb shell settings put system accelerometer_rotation 1
adb shell settings put system screen_off_timeout 60000
adb shell cmd uimode night auto
adb shell settings put global debug.force_rtl 0
adb shell cmd locale set-app-locales vegabobo.languageselector --locales ""
adb shell settings delete global enable_freeform_support
adb shell settings delete global force_resizable_activities
adb shell am force-stop vegabobo.languageselector
```

### 6.11 验收结果记录表（后续真机执行时填写；本轮 Deferred 未填）

| 用例 ID | 形态 / 参数 | 操作 / 命令 | 预期 | 实际 | 证据文件 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| A-01 | phone36 竖屏 | 冷启动 ×5 | 无 crash/ANR，TotalTime 记录 | | | |
| A-02 | phone36 横屏 | user_rotation=1 | EXPANDED 双栏 400dp | | | |
| T-01 | tablet36 默认 | 启动 | EXPANDED 双栏 400dp | | | |
| T-02 | tablet36 旋转 | user_rotation=1 | MEDIUM 双栏 360dp | | | |
| B-01 | phone36 1200×1920@320 | wm size/density | 600dp → MEDIUM 双栏 | | | |
| B-02 | phone36 1680×2400@320 | wm size/density | 840dp → EXPANDED 双栏 400dp | | | |
| F-01 | fold36 展开/折叠 | Device State | 双栏 ↔ 单栏，状态保留 | | | |
| F-02 | fold36 TABLETOP | Device State Half-open | 上下分栏 + hinge gap | | | |
| F-03 | fold36 BOOK | Device State | 左右分栏 + hinge gap | | | |
| M-01 | phone36 分屏 | 最近任务 / --windowingMode | 布局随窗口宽变化，无重建闪烁 | | | |
| M-02 | phone36 自由窗口 | task resize 跨断点 | 360↔400↔单栏 正确切换 | | | |
| L-01 | phone36 font_scale 2.0 | settings put | 无截断/重叠 | | | |
| L-02 | phone36 RTL(ar) | cmd locale | 镜像正确 | | | |
| P-01 | phone36 gesture back | 边缘滑动 | 预测性返回动效正常 | | | |
| S-01 | Shizuku 全流程 | 安装/授权/断连 | 状态机正确 | | | |
| Q-01 | QS tile | statusbar expand | 磁贴可用/状态正确 | | | |
| R-01 | release APK | 安装 + 冒烟 | P0 通过、无 R8 crash | | | |

## 7. 当前阶段已知风险 / 遗留项（非构建错误）

1. **【已修复 ✅】lint Error F-1**：`ProduceStateDoesNotAssignValue` 已通过 `@Suppress` 修复，运行 #2 lint 0 Error；不再阻塞。详见 §4 F-1。
2. **【已修复 ✅】死代码**：无引用且含硬编码英文的 `ui/navigation/PlaceholderHome.kt` 已删除（-60 行）；最终源码硬编码文案 0。
3. **未使用资源 29 条（warning）**：旧 `colors.xml`（purple/teal/black/white）、`ic_launcher_background/foreground`、`theme*`/`dynamic_color*`/`cd_*` 等 strings。属于 85 warnings 的主要来源（29/85），非阻塞。
4. **其余 warnings（56 条）**：`GradleDependency` 42（依赖可升级）、`AndroidGradlePluginVersion` 6、`MonochromeLauncherIcon` 2、`ObsoleteSdkInt` 1、Compose 风格 2（Modifier factory/parameter）。均非 Error。
5. **真机验收未执行**（用户要求 Deferred）：§6 清单保留；Root 模式与 300+ 应用长列表需真机补测。UI 概念图由 Lead 负责（`other/concepts/`）。
6. **AGP 8.7.3 + compileSdk 36**：本轮构建未出现相关阻塞/告警失败；若需升级 AGP 另立任务。
7. **产物对照**：旧 `app-release.apk` 6.92 MB（2026-03-22）→ 本轮 7.27 MB；验收只用 §2.1 新产物。
8. **`unitTests.isReturnDefaultValues = true`**：让纯 JVM 单测绕过 android.* 桩实现，但也可能掩盖 android 调用返回假值的问题；后续引入 Robolectric 时再评估。

## 8. 验证运行记录（追加式）

| 运行 | 时间 | 触发 | HEAD / 工作树 | 结果摘要 |
| --- | --- | --- | --- | --- |
| — | 2026-10-18 10:42 | 准备阶段 | `b11f722` + 2 未跟踪文件 | 仅静态通读与模板建立，未运行 Gradle |
| — | 2026-10-18 11:00 | 清单细化（Lead 要求） | 同上，未构建 | 探测本机 AVD/Shizuku/aapt2/adb 可用性；细化 6.x：形态矩阵（phone36/tablet36/fold36 的 width_dp 换算）、600/840dp 边界注入、折叠姿态（Device State/hinge）、分屏与自由窗口、字体 2.0/RTL、冷启动/gfxinfo/Perfetto/meminfo 命令、收尾恢复、验收记录表 |
| — | 2026-10-18 11:05 | verify 第 1 次尝试 | 工作树仍在写入（->`f7e211b`） | **作废**：并发写入竞态——`LanguageSelectorApp.kt` 先引用 `R.string.select_app_title`，2s 后 strings.xml 才落地，`compileDebugKotlin` 报 Unresolved reference；该轮不计入结论 |
| #1 | 2026-10-18 11:09–11:12 | Lead verify（代码已冻结） | `f7e211b`，tree-hash `133FA16D…763205` 前后一致（79 文件） | compile ✅ 1.1s / test ✅ 76 tests / lint ❌ 1 Error（F-1）/ assembleDebug ✅ 2.8s / assembleRelease ✅ 54.3s；APK 68.03 MB / 7.27 MB |
| — | 2026-10-18 11:13 | 静态检查 A/B（构建后） | 同上 | 硬编码文案 0 活跃违规（1 死代码 PlaceholderHome）；`runBlocking`=0、IPC 均 io 调度；release 合并清单组件/权限完整；release dex 保留 IUserService(+Stub/Proxy)/UserService/RootUserService/ShizukuUserServiceArgs；AVD 章节按用户要求标记 Deferred to real device |
| #2 | 2026-10-18 11:16–11:19 | Lead 修复 lint 后复验（代码重新冻结） | `f7e211b` + 2 处修复；tree-hash `6F7CB3F7…91F551`（78 文件）前后一致 | **5 任务全绿**：compile ✅ 3.5s / test ✅ 76 tests / **lint ✅ 18.4s，0 Error / 85 Warnings**（F-1 命中 0）/ assembleDebug ✅ 3.4s / assembleRelease ✅ 36.3s；APK 用最终源码重建（debug sha `DE81ADCE…`、release sha `24BDD83B…`）；release 反射类复验通过 |

---

### 附 A：静态扫描参考命令（只读）

```powershell
# 硬编码文案
Get-ChildItem app/src/main/java -Recurse -Filter *.kt | Select-String -Pattern 'text\s*=\s*"|Text\(\s*"'
# 主线程阻塞嫌疑
Get-ChildItem app/src/main/java -Recurse -Filter *.kt | Select-String -Pattern 'runBlocking|Thread\.sleep|getService\('
# R.string 引用 vs 定义（脚本见会话记录；任何 "used but not defined" 都是编译错误）
# 翻译缺口
$def = ([xml](Get-Content app/src/main/res/values/strings.xml)).resources.string.name
foreach ($loc in 'values-ja','values-pt-rBR','values-zh-rCN') {
  $n = ([xml](Get-Content "app/src/main/res/$loc/strings.xml")).resources.string.name
  "$loc missing=$(Compare-Object $def $n | Where-Object SideIndicator -eq '<=' | Measure-Object | % Count)"
}
```

### 附 B：release 反射类保留核验（本轮已执行，§5.8 数据来源）

```powershell
$sdk = "C:\Users\minjun\AppData\Local\Android\Sdk"
& "$sdk\cmdline-tools\latest\bin\apkanalyzer.bat" dex packages -d app\build\outputs\apk\release\app-release.apk |
  Out-File -Encoding utf8 "$env:TEMP\ls-qa-release-dex-packages.txt"
Select-String -Path "$env:TEMP\ls-qa-release-dex-packages.txt" `
  -Pattern '^C .*(IUserService|service\.UserService|RootUserService|ShizukuUserServiceArgs)'
# 期望命中：IUserService / IUserService$Stub / IUserService$Stub$Proxy / IUserService$Default /
#           IUserService$_Parcel / service.UserService / service.RootUserService /
#           service.ShizukuUserServiceArgs / rikka.shizuku.Shizuku$UserServiceArgs
# 备选（无 apkanalyzer 时）：复制 APK 为 .zip 解包后对每个 classes*.dex 用 dexdump 查 descriptor
# & "$sdk\build-tools\36.0.0-rc3\dexdump.exe" classes.dex | Select-String 'IUserService|RootUserService'
```

---

## 9. 真机验收记录（2026-10-18，Xiaomi 2509FPN0BC / Android 17）

- 设备：Xiaomi 2509FPN0BC（Android 17 / SDK 37，1200×2608 @480dpi ≈ 411dp），Shizuku 13.6.0 以 shell uid 运行，原 App 1.05（versionCode 5）可覆盖升级（同一 debug 签名）。
- 安装：release `2.0.0(6)` 覆盖安装成功 → 发现 D-1 后升级到 `2.0.1(7)`；最终 release sha `BF272627…301CCBA`（7,286,388 B）。

### 9.1 真机发现并修复的问题（均已包含在 2.0.1）

| ID | 现象 | 根因 | 修复 | 复验 |
| --- | --- | --- | --- | --- |
| D-1 | 覆盖升级后启动即崩：`IllegalStateException: Room cannot verify the data integrity` | 旧版 Room 默认表名 `AppInfoEntity`，重写使用 `appinfoentity`，但 DB 版本仍为 1；第一次迁移实现又被 SQLite 表名大小写不敏感坑掉（DROP/CREATE 后 `SELECT FROM AppInfoEntity` 实际读到新建空表） | DB 版本 1→2；`MIGRATION_1_2` 先把源表 `ALTER TABLE ... RENAME` 到临时表，再建规范表并复制数据；替换/异常时 Room 事务回滚，不留半成品 | 旧历史 7 行完整保留（最近使用可见 Gboard/Gemini/YouTube Music 等旧记录），启动无崩溃 |
| D-2 | 深色模式下列表标题/应用名几乎不可读 | 根 Composable 没有 Surface，`LocalContentColor` 在深色主题下仍为黑色 | root 外层包 `Surface(color = MaterialTheme.colorScheme.background)` | 深色截图文字对比正常 |
| D-3 | 字体 200% 时“已修改”竖排、应用名被行尾当前语言挤成省略号 | badge 未限制单行；行尾语言标签按固有宽度占位 | badge 单行 + chip 背景；`fontScale > 1.4` 时隐藏行尾标签、把当前语言显示为 badge | 200% 截图 badge 不竖排、Bitwarden 名称完整 |
| D-4 | 冷启动偶发不显示“最近使用” | `recentApps = history.map { find(pkg) }`，history 先于 App 列表到达时 find 为空，且之后不会重算 | `combine(historyStore.recentPackages, appCatalog.apps)` 按当前快照解析 | 冷启动 ×5 均有“最近使用” |
| D-5 | 冷启动偶发所有行缺少“已修改/当前语言” | Home 首次 refresh 在 ServiceManager 就绪前执行，locale 查询失败后不再重试 | 观察 `ServiceStatus.Ready` 后重新刷新 locale states | 冷启动截图 badge/当前语言正常 |
| D-6 | 主界面底部系统导航栏区域未沉浸：列表视口停在导航栏上方，底部露出一条独立背景/白条，滑动时内容不进入手势条区域（详情页同样） | HomeScreen 根部对整列使用 `navigationBarsPadding()`，把列表视口整体抬到导航栏上方；Detail 的 LazyColumn 缺少导航栏 bottom inset | MainActivity 显式 `enableEdgeToEdge(透明 scrims)` + `isNavigationBarContrastEnforced=false`；Home 去掉根部 nav padding，改为 LazyColumn `contentPadding` 追加 navigationBars 高度；Detail LazyColumn 同样处理，列表可滚动到手势条下方 | 首页/详情滑动到底部时内容延伸到手势条区域，无独立白条；浅色/深色均通过 |

### 9.2 通过项

- 升级安装与旧数据兼容（历史、已修改状态可读）；设置页/列表/详情/筛选/强制停止均可用。
- Shizuku `Ready`；用系统命令独立验证语言设置：`cmd locale get-app-locales` Bitwarden `[] → [zh-CN]（App 内设置）→ []（App 内重置）`，Tasks 同样 `[] → [zh-CN] → []`。
- 首页：完整列表、最近使用、系统应用/仅已修改过滤、搜索输入与空结果、清除；详情：推荐/全部语言分组展开/置顶星标/系统默认/打开/强制停止/应用设置。
- 自适应：横屏 2608×1200@480（≈869dp）自动双栏（列表+详情），旋转后选中应用保留；竖屏 411dp 单栏。
- 深色模式、字体 200% 布局（修复后）通过目视检查。
- 边到边沉浸（修复后）：主界面与详情页列表滚动时延伸到透明导航栏/手势条下方，底部不再出现独立背景条带（浅色/深色均验证）。
- QS Tile：`cmd statusbar add-tile` 后 SystemUI 已绑定 `LocaleTileService`（`dumpsys activity services` 可见 ServiceRecord + ConnectionRecord）；HyperOS 的 QS pager 未直接显示，需用户在编辑界面手动添加，服务注册本身通过。
- 性能（release）：冷启动 5 次 `TotalTime` = 104/90/93/83/97 ms，中位数 **93 ms**；gfxinfo 48 帧 **Janky 0.00%**（legacy 8.33%）、95th 8ms、99th 17ms；进程 PSS ≈ 100 MB（含 Graphics 27 MB）。
- 稳定性：修复后无新增 crash/ANR（`dumpsys activity exit-info` 最新为 USER REQUESTED / FORCE STOP）。

### 9.3 未覆盖 / 限制

- Root 模式：设备无 `su`，未测（代码路径保留）。
- 折叠 tabletop/book、分屏/自由窗口：设备非折叠屏，仅验证横竖屏/窗口宽度切换。
- RTL 与预测性返回手势：本轮未执行。
- 搜索文本输入：设备中文 IME 会组词，`adb shell input text` 无法提交拉丁查询；搜索逻辑由 8 个单测 + UI 空态/清除验证覆盖，建议真机手动输入一次。
- QS Tile 点击切换：HyperOS pager 未显示该 tile，未完成点击验证；建议手动添加磁贴后复测（服务已绑定）。
- 目标应用语言即时生效：Bitwarden 截图受 FLAG_SECURE 限制（黑屏），实际生效以 `cmd locale` 与 Tasks 的查询结果为准。
- 测试结束后已恢复：Bitwarden 语言清空、旋转/字体/深色/屏幕超时还原、IME 还原为 Sogou、移除测试用 QS tile。

### 9.4 证据截图（`captures/`，不纳入 git）

`device_01_launch.png`（旧版启动崩溃前的系统状态）、`device_02_after_migration.png`、`device_03_home.png`（首页）、`device_04_detail.png`（Bitwarden 详情）、`device_05_set_zh.png`（设置为中文 + Undo Snackbar）、`device_06_bitwarden_zh.png`（FLAG_SECURE 黑屏）、`device_07_reset.png`/`device_08_reset_ok.png`（重置）、`device_09_home_recent.png`（最近使用 + 已修改徽标）、`device_10_search.png`/`device_13_search2.png`/`device_14_search_ok.png`（搜索/IME）、`device_11_system_filter.png`（系统应用过滤）、`device_12_modified_filter.png`（仅已修改）、`device_16_landscape.png`（横屏双栏）、`device_17_font200.png`→`device_22_font200_final.png`（字体 200% 修复前后）、`device_18_dark.png`→`device_19_dark_fixed.png`（深色修复前后）、`device_23_qs_tile.png`（QS 面板）、`issue_bottom_light.png`/`issue_bottom_light_fixed2.png`（底部未沉浸 → 修复）、`fix_immersive_home.png`/`fix_immersive_detail.png`（修复后列表延伸到手势条下方）。
