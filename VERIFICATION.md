# VERIFICATION.md — rewrite/adaptive 独立验证记录

> 本文件由 `build-qa` 维护，是验证任务中**唯一允许写入**的文件。
> 不修改源码 / build 配置 / res，不执行 git commit。构建命令只允许由本文件记录的单线程序列执行。

| 项 | 值 |
| --- | --- |
| 仓库 | `C:\Users\minjun\Source\Repos\Language-Selector` |
| 分支 | `rewrite/adaptive` |
| 当前 HEAD（快照） | `b11f722 feat: scaffold rewrite (contracts, models, theme, components, adaptive window form, DI stubs)` |
| 快照时间 | 2026-10-18 10:42 +08:00（工作树仍有并行改动：`ui/adaptive/AdaptiveScaffold.kt`、`ui/navigation/ServiceStatusDialog.kt` 未跟踪，`README.md` 已修改） |
| JDK | `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot`（21.0.12.1 LTS，JAVA_HOME 已生效） |
| Android SDK | `C:\Users\minjun\AppData\Local\Android\Sdk`（local.properties） |
| Gradle | wrapper 8.10.2；AGP 8.7.3；Kotlin 2.0.21；KSP 2.0.21-1.0.27 |
| 构建并发约束 | **同一时刻只允许一个 Gradle 构建**（含 Lead / 其他成员）；运行前确认无 build 进行中 |

## 0. 结论 / 状态

**状态：等待 Lead 的 `verify` 指令（准备阶段完成；真机/模拟器验收清单已于 6.x 细化，仍未运行任何 Gradle 构建）。**

验证结论（每次运行后更新）：

- [ ] `:app:compileDebugKotlin` — 待运行
- [ ] `:app:testDebugUnitTest` — 待运行
- [ ] `:app:lintDebug` — 待运行
- [ ] `:app:assembleDebug` — 待运行
- [ ] `:app:assembleRelease` — 待运行
- [ ] APK 产物检查 — 待运行
- [ ] 静态检查（硬编码文案 / 主线程阻塞 / Manifest / 资源） — 待运行
- [ ] 真机验收 — 待运行（依赖集成完成）

> 声明：本阶段仓库仍处于 **scaffold 阶段**（DI 全部绑定 stub、首页为 `PlaceholderHome`、service/data 层尚未实现）。
> 构建通过 ≠ 功能完成；真机验收 checklist 中依赖真实 service 层的条目在集成完成前无法判定。

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
| 2 | `:app:testDebugUnitTest` | `BUILD SUCCESSFUL`；当前预期 **NO-SOURCE**（`app/src/test` 不存在） | 无测试源 → 不产生失败；若之后新增测试则看 `app/build/reports/tests/testDebugUnitTest/index.html` |
| 3 | `:app:lintDebug` | `BUILD SUCCESSFUL`；`lint-results-debug.html/txt` 中 Error = 0 | **翻译类问题（见 6.4）风险最高**；其余多为 warning |
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

### 2.1 基线（陈旧产物，不能作为通过证据）

| 产物 | 大小 | 时间 | 说明 |
| --- | --- | --- | --- |
| `app-debug.apk` | 65,950,584 bytes | 2026-10-18 10:39:12 | 早于 HEAD 提交（10:40:07），且当时 `AdaptiveScaffold.kt` / `ServiceStatusDialog.kt` 尚未存在 → **陈旧** |
| `app-release.apk` | 6,917,011 bytes | 2026-03-22 21:11:41 | 重写前旧版本 → **陈旧**，验证时必须重新生成 |
| debug APK sha256（旧） | `0640A70EFD16EB8F37A00708AA89282D714162D27ED71C62C2D3A2122988164C` | — | 仅供参考 |

## 3. 结果矩阵（每次验证运行后填写）

### 运行 #1 — 待执行（Lead 指令后）

| 任务 | 结果（SUCCESS / FAILED / NO-SOURCE） | 耗时 | 关键输出 / 失败摘要（file:line） |
| --- | --- | --- | --- |
| `:app:compileDebugKotlin` | | | |
| `:app:testDebugUnitTest` | | | |
| `:app:lintDebug` | | | |
| `:app:assembleDebug` | | | |
| `:app:assembleRelease` | | | |

APK 记录：

| 产物 | 存在 | 大小 (bytes) | 生成时间 | SHA256 |
| --- | --- | --- | --- | --- |
| `app-debug.apk` | | | | |
| `app-release.apk` | | | | |

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

当前无失败记录（尚未运行构建）。

## 5. 静态检查清单（只读，构建外可先做；构建后复核）

### 5.1 硬编码用户可见文案

- [x] 已扫描（快照 b11f722）：Kotlin 侧仅 1 处真实违规
  - `app/src/main/java/vegabobo/languageselector/ui/navigation/PlaceholderHome.kt:46` — `text = "Service status: $status"`，英文硬编码且直接插值 `ServiceStatus`（会输出 `Ready(mode=SHIZUKU)` 之类的内部类名，非本地化）。属临时占位页，集成时须替换为 `stringResource(...)` + `serviceStatusLabel()`（后者已在 `ServiceStatusDialog.kt` 实现）。
  - 其余 `Text(...)` 均走 `stringResource(R.string.*)`。
- [ ] 复核：`.kt` 中 `Text("`、`text = "`、`contentDescription = "` 均为空；XML 布局无硬编码文案（本项目无 layout XML）。
- 参考命令：`grep -n 'text\s*=\s*"\|Text(\s*"' app/src/main/java -r`

### 5.2 主线程阻塞 / 危险 Binder 调用

- [x] 快照结论：
  - 图标加载 `ui/components/AppIcon.kt:34` 正确使用 `withContext(Dispatchers.IO)`，`PackageManager.getApplicationInfo` 不在主线程。
  - `core/dispatchers/AppDispatchers.kt` 显式注入 IO/Default/Main，符合后续 service/data 层要求。
  - `MainActivity.kt:24` 在 `onCreate` 同步调用 `serviceController.refresh()`；当前 stub 为空实现，但真实实现接入后**禁止**在此做 binder 等待（应 fire-and-forget 到 IO + StateFlow）。列为集成期回归点。
  - `data/prefs/SharedPrefsSettingsStore.kt:20-30` 在构造时同步读 SharedPreferences（Hilt 单例首次注入发生在主线程）→ 首帧有磁盘读风险；建议后续在 IO 上初始化或接受（Android 首次 getSharedPreferences 为异步加载，通常可接受）。非阻塞项。
  - 未发现 `runBlocking` / `Thread.sleep` / `getService(...)` 轮询。
- [ ] 复核：`runBlocking|Thread\.sleep|\.get\(\)` 在 `app/src/main/java` 中为空或均已注释说明用途。

### 5.3 AndroidManifest 完整性

快照 b11f722 清单：

| 组件 | 类 | 要求 | 状态 |
| --- | --- | --- | --- |
| application | `.App` (`@HiltAndroidApp`) | `android:name` 与类全名匹配 | ✅ |
| activity | `.MainActivity` | `exported=true` + MAIN/LAUNCHER | ✅ |
| provider | `rikka.shizuku.ShizukuProvider` | authorities `${applicationId}.shizuku`、exported、permission | ✅（Shizuku 标准写法） |
| service | `.qs.LocaleTileService` | exported、BIND_QUICK_SETTINGS_TILE、`QS_TILE` intent-filter + TOGGLEABLE_TILE meta | ✅（注意：与旧 `QSTile` 的迁移已完成） |
| 权限 | `QUERY_ALL_PACKAGES` | `tools:ignore="QueryAllPackagesPermission"` | ✅（Play 上架需说明用途） |
| 属性 | `localeConfig` / `dataExtractionRules` / `fullBackupContent` / `enableOnBackInvokedCallback` / `resizeableActivity` / `supportsRtl` | 对应 xml 资源存在 | ✅（`res/xml/locales_config.xml` 需与 `values-*` 实际语言一致，见 6.4） |

- [ ] 构建后对照 `app/build/intermediates/merged_manifests/debug/AndroidManifest.xml`：确认 `.qs.LocaleTileService`、`ShizukuProvider`、权限均未被 merger 丢弃；确认无 `tools:` 属性残留告警。
- [ ] 确认 release variant 的 merged manifest 中 `android:debuggable` 不存在。

### 5.4 资源 / 字符串一致性（高风险，预计阻塞 lintDebug）

- 快照统计：`values/strings.xml` 定义 99 条；Kotlin 实际引用 24 条（其余为尚未接入的界面预留，`UnusedResources` 只会是 warning）。
- `values-zh-rCN/strings.xml`：98 条，缺 `app_name`（`translatable="false"`，**不算 MissingTranslation**）→ 基本齐全。
- `values-ja/strings.xml`、`values-pt-rBR/strings.xml`：各仅 **28** 条，且相对默认语言 **缺 84 条**、另有 **13 条已失效键**：
  `all_languages, cancel, close, open, pinned, pinned_ok, proceed, region, settings, show_only_user_apps, show_system_apps, unpinned, user_languages`
- 预计 lint 结果：`MissingTranslation`（84×2 ≈ 168 条）与 `ExtraTranslation`（13×2 = 26 条）默认均为 **Error** 级别 → `:app:lintDebug` 预计失败。
- 最小修复建议（交 Lead 决策，qa 不改）：
  1. 若暂不维护日/葡翻译：删除 `values-ja`、`values-pt-rBR` 目录（并同步 `res/xml/locales_config.xml`），或
  2. 补齐两语言的新键集（推荐但工作量大），或
  3. 在 `app/build.gradle.kts` 的 `lint { }` 中显式 `disable += "MissingTranslation" / "ExtraTranslation"`（**不推荐**用于发布，会掩盖真实缺陷）。
- [ ] 构建后核对 `app/build/reports/lint-results-debug.txt` 的 Error 段落。

### 5.5 DI 绑定完整性

`di/AppModule.kt` 已绑定全部 8 个 contract：ServiceController、LocaleRemote、AppCatalog、LocaleStates、LocaleCatalog、PinnedLocales、HistoryStore、SettingsStore。其中 7 个指向 `di/stubs/Stubs.kt`，仅 SettingsStore 为真实实现。

- [ ] 构建期：KSP/Hilt 无 `[Dagger/MissingBinding]`、无 `@Binds` 重复绑定错误。
- [ ] 集成期：stub 替换为真实 service/data 实现时，需同步复查绑定与构造注入（本文件按 Lead 的集成批次追加记录）。

### 5.6 Release 构建 / R8 风险

- `release`：`isMinifyEnabled = true` + `isShrinkResources = true`，签名沿用 debug signingConfig（个人使用，已知可接受）。
- `app/proguard-rules.pro` 仅 `-dontobfuscate`，无显式 keep；依赖方（Hilt、Compose、Coil、libsu、Shizuku、Room、aboutLibraries）应自带 consumer rules，但需实测。
- 重点关注：R8 的 `Missing class` / `Warning: ... can't find referenced class`；若只出 warning 且 BUILD SUCCESSFUL 可接受，但需记录数量；若 `errors` 则阻塞。
- [ ] 构建后检查 `app/build/outputs/mapping/release/` 存在（mapping/usage/seeds/configuration），`app-release.apk` 可被 `aapt2 dump badging` 解析。

### 5.7 单测现状

- 当前 `app/src/test`、`app/src/androidTest` **均不存在** → 若 verify 时仍如此，`:app:testDebugUnitTest` 为 `NO-SOURCE`（不算失败，但等于零测试覆盖，须在结论中标注）。
- Lead 已确认：各实现者补齐单测、Lead 本人补 `WindowForm` 断点测试；因此 verify 时预期 test task **会真正执行**，需核对测试数量与通过率（`app/build/reports/tests/testDebugUnitTest/index.html`）。
- 建议（供 Lead 排期，qa 不实现）：优先对纯 JVM 逻辑补测：
  - `ui/adaptive/WindowForm.kt:38` `windowWidthFor(widthDp)`（600/840 边界）；
  - `core/util/LocaleDisplay.kt` `localeDisplayName` / `localeLanguageName`（空串、未知 tag、大小写）；
  - `core/model/Models.kt` `AppLocaleState.isModified/primaryTag`。
  - `testImplementation` 已具备 `junit` 与 `kotlinx-coroutines-test`，`unitTests.isReturnDefaultValues = true` 已开。

## 6. 真机 / 模拟器验收清单（细化版）

> 前提：Lead 完成 DI/导航/service 集成并自测 `compileDebugKotlin` 绿，产出**新** APK（旧产物见 2.1 不可用）。
> 运行规范：测试期间**不跑 Gradle、不并行跑第二个 emulator**（CPU 争用会污染帧率/冷启动数据）；每个参数变更后先 `am force-stop` 再冷启动；每项记录 `PASS / FAIL / N/A` + 证据文件。
> 证据目录：`$env:TEMP\ls-qa-device\<yyyyMMdd-HHmm>\`，命名 `<case>-<device>-<param>.png|txt|log`。

### 6.0 本机环境探测结果（2026-10-18，只读）

| 项 | 值 | 用途 |
| --- | --- | --- |
| adb | `C:\Users\minjun\AppData\Local\Android\Sdk\platform-tools\adb.exe`（1.0.41 / 37.0.0） | 全部设备命令 |
| 已连接设备 | **当前无**（`adb devices` 为空）→ 需先启动 AVD | — |
| AVD | `phone36`、`tablet36`、`fold36`、`Television_1080p` | 形态矩阵 |
| Shizuku APK | `C:\Users\minjun\Downloads\shizuku-v13.6.0.r1086.2650830c-release.apk`（v13.6.0，App 依赖 shizuku-api 13.1.1，兼容） | 服务连接用例 |
| aapt2 | `...\build-tools\36.0.0-rc3\aapt2.exe`（35.0.0 亦可） | badging/产物校验 |
| emulator | `...\Sdk\emulator\emulator.exe` | 启动 AVD |
| 镜像限制 | AVD 均为 `google_apis_playstore_ps16k`（Play 商店镜像）→ `adb root` 不可用，**Root 模式无法在 AVD 验证**，须真机（Magisk/KernelSU）或 AOSP userdebug 镜像 | 见 6.3 的 N/A 说明 |

### 6.0.1 形态/断点矩阵（本机 AVD 实测尺寸换算）

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

### 6.1 启动 AVD 与设备准备（一次性）

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

### 6.8 P2 性能 / 稳定性测量

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

### 6.11 验收结果记录表（验证时填写）

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

## 7. 当前阶段已知风险（交接给 Lead，非构建错误）

1. **功能未集成**：`di/stubs/Stubs.kt` 提供全部 service/data 实现，主界面为 `PlaceholderHome`；`AdaptiveScaffold.kt`、`ServiceStatusDialog.kt` 尚未接入调用点。构建通过不代表功能可用。
2. **lint 预计因翻译失败**（见 5.4）：`values-ja` / `values-pt-rBR` 严重滞后（缺 84、多 13）。建议在集成提交前处理，否则 `:app:lintDebug` 会持续红。
3. **零单测（已排期）**：当前 `app/src/test` 不存在；Lead 已安排实现者补齐 + 自补 `WindowForm` 断点测试。verify 时若仍未就绪，按 5.7 标注覆盖缺失而非判 FAILED。
4. **MainActivity 同步 refresh**（见 5.2）：真实 service 层接入后需改为异步状态机，避免主线程 binder 等待。
5. **AGP 8.7.3 + compileSdk 36**：可能出现 "compileSdk 36 not supported by this AGP" 的构建警告（默认非致命），若 AGP 升级需另立任务。
6. 旧产物（尤其 `app-release.apk`）来自重写前，禁止作为验收证据；必须重新构建。

## 8. 验证运行记录（追加式）

| 运行 | 时间 | 触发 | HEAD / 工作树 | 结果摘要 |
| --- | --- | --- | --- | --- |
| — | 2026-10-18 10:42 | 准备阶段 | `b11f722` + 2 未跟踪文件 | 仅静态通读与模板建立，未运行 Gradle |
| — | 2026-10-18 11:00 | 清单细化（Lead 要求） | 同上，未构建 | 探测本机 AVD/Shizuku/aapt2/adb 可用性；细化 6.x：形态矩阵（phone36/tablet36/fold36 的 width_dp 换算）、600/840dp 边界注入、折叠姿态（Device State/hinge）、分屏与自由窗口、字体 2.0/RTL、冷启动/gfxinfo/Perfetto/meminfo 命令、收尾恢复、验收记录表 |

---

### 附：静态扫描参考命令（只读）

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
