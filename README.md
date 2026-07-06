# CyberRemote 赛博 Apple TV 遥控器

一款免费、开源、无广告的 Apple TV 安卓遥控器（tvOS 15+）。它直接在你的本地网络上
使用 Apple 的 Companion Link 协议——无需云端、无需中转服务器、无需常驻后台进程、
无任何遥测。手机与 Apple TV 直连通信。

> CyberRemote 是一个独立的开源项目，**与 Apple Inc. 无任何关联，未获其认可或赞助**。
> "Apple TV" 是 Apple Inc. 的商标，此处仅用于说明兼容性。

<!-- 在这里插入截图 · Add screenshots here -->

## 功能

- **完整键盘输入**——当电视上有输入框获得焦点时，手机键盘会自动弹出并直接输入
  （搜索框、密码、网址）。支持中日韩文字与 emoji。
- 方向键、确认、返回、主屏、播放/暂停、音量
- 唤醒与睡眠
- 触控板滑动导航
- 应用列表 + 启动
- 使用电视上显示的 PIN 码配对（HomeKit 式的 pair-setup）；你的配对凭据永不离开
  手机（由 Android Keystore 加密保存）

## 安装

在 [GitHub Releases](../../releases) 下载 APK 并安装（可能需要在系统设置里允许
"安装未知来源应用"）。

**环境要求：** Android 8.0+（API 26）、一台运行 tvOS 15–26 的 Apple TV HD/4K，
且手机与电视处于同一 Wi-Fi 网络 / 同一子网。

## 首次使用

1. 在 Apple TV 上，确认 **设置 → AirPlay 与 HomeKit → 允许访问** 已允许同一
   网络下的设备。
2. 打开 CyberRemote——你的 Apple TV 应会出现在列表中。
3. 点击它，输入电视上显示的 4 位 PIN 码，完成。

## 常见问题

**App 找不到我的 Apple TV。**
设备发现依赖 mDNS（Bonjour），它只在同一广播域内有效。手机和电视必须在同一
子网 / VLAN，且路由器不能屏蔽组播（开启了"AP 隔离 / 客户端隔离"会导致失效）。
作为兜底方案，可使用 *按 IP 连接*（⛓ 图标）——注意 Apple TV 每次重启后端口
都会变，你可以用任意 Bonjour 浏览工具查到当前端口。

**配对失败，或电视上不出现 PIN 码。**
检查电视上的 **设置 → AirPlay 与 HomeKit → 允许访问**。如果 PIN 出现了但配对
仍然失败，再试一次——若还不行，请提 issue。

**tvOS 更新后就用不了了。**
Companion 协议是私有的、通过逆向工程得来的（由 [pyatv](https://github.com/postlund/pyatv)
项目完成）；tvOS 的更新随时可能让它失效。请查看已有的 issue——通常 pyatv 会
最先修复，我们再把修复移植过来。

**打的字没有传到电视上。**
键盘仅在电视上有输入框获得焦点时才起作用——此时 App 会显示"正在向 … 输入"。
某些 tvOS 版本的密码框会禁止读取当前文本；但替换 / 输入仍然可用。

**为什么这个 App 需要组播权限？**
Android 上的 mDNS 发现需要 `CHANGE_WIFI_MULTICAST_STATE`；没有组播锁，扫描会
悄无声息地什么都找不到。默认情况下，App 除了连接你的 Apple TV 之外不会发起任何
网络连接。

唯一的例外是完全可选、且默认关闭的：如果你打开 **设置 → 获取真实应用图标**，
应用网格会调用 Apple 公开的 iTunes 查询 / 搜索 API 来加载真实图标（并缓存到
本地磁盘）。保持关闭即可让 App 完全本地运行——此时应用网格会显示自动生成的
首字母图块，不向外发起任何请求。

**能看"正在播放"的信息 / 封面吗？**
v1 暂不支持——那需要另一套协议（MRP over AirPlay 2）。计划作为可能的 v2 功能。

## 构建

```bash
./gradlew :protocol:test          # 协议单元测试（纯 JVM）
./gradlew :app:assembleDebug      # APK 位于 app/build/outputs/apk/debug/
./gradlew :cli:run --args="scan"  # 用于协议调试的命令行工具
```

## 模块

- `protocol/` — Companion Link 协议的纯 Kotlin/JVM 实现：OPACK、TLV8、
  HAP pair-setup/pair-verify（SRP-6a）、ChaCha20-Poly1305 加密会话、
  HID/触控/应用/电源命令，以及 RTI 文本输入（二进制 plist keyed archive）。
  不依赖 Android——可被其他项目复用。
- `cli/` — 命令行工具（`scan`、`pair`、`command`、`text-set`、`focus-state`
  等）。针对真实硬件的主要集成测试工具。
- `app/` — 安卓 App（Jetpack Compose、Material 3）。
- `docs/protocol-notes.md` — 经核实的协议细节，以及每一部分的移植来源。

## 致谢与许可

MIT 许可。协议知识来自 [pyatv](https://github.com/postlund/pyatv)（MIT）出色的
逆向工程成果——本项目是遵循 pyatv 已记录行为、用 Kotlin 从零编写的独立实现。

---

# CyberRemote

*English below · 中文见下方*

A free, open-source, ad-free Android remote for Apple TV (tvOS 15+). It speaks
Apple's Companion Link protocol directly over your local network — no cloud, no
bridge server, no companion daemon, no telemetry. The phone talks straight to
the Apple TV.

> CyberRemote is an independent open-source project. It is **not affiliated
> with, endorsed by, or sponsored by Apple Inc.** "Apple TV" is a trademark of
> Apple Inc., used here only to describe compatibility.

<!-- 在这里插入截图 · Add screenshots here -->

## Features

- **Full keyboard input** — when a text field is focused on the TV, your
  phone's keyboard opens automatically and types straight into it (search
  boxes, passwords, URLs). CJK and emoji included.
- D-pad, select, back, home, play/pause, volume
- Wake and sleep
- Touchpad swipe navigation
- App list + launch
- Pairing with the PIN shown on the TV (HomeKit-style pair-setup); your
  credentials never leave the phone (encrypted with the Android Keystore)

## Install

Grab the APK from [GitHub Releases](../../releases) and install it (you may
need to allow installs from unknown sources).

**Requirements:** Android 8.0+ (API 26), an Apple TV HD/4K on tvOS 15–26, and
both devices on the same Wi-Fi network / subnet.

## First run

1. On the Apple TV, check **Settings → AirPlay and HomeKit → Allow Access**
   permits devices on your network.
2. Open CyberRemote — your Apple TV should appear in the list.
3. Tap it, enter the 4-digit PIN shown on the TV, done.

## FAQ

**The app doesn't find my Apple TV.**
Discovery uses mDNS (Bonjour), which only works within the same broadcast
domain. Phone and TV must be on the same subnet/VLAN, and your router must not
block multicast ("AP/client isolation" breaks it). As a fallback, use *Connect
by IP* (the ⛓ icon) — note the port changes after every Apple TV reboot; you
can find the current one with any Bonjour browser.

**Pairing fails or no PIN appears.**
Check **Settings → AirPlay and HomeKit → Allow Access** on the TV. If a PIN
appears but pairing still fails, try once more — then open an issue.

**It stopped working after a tvOS update.**
The Companion protocol is private and reverse-engineered (by the
[pyatv](https://github.com/postlund/pyatv) project); tvOS updates can break it
at any time. Check open issues — breakage is usually fixed in pyatv first, and
we port the fix.

**Typing doesn't reach the TV.**
The keyboard only works while a text field is focused on the TV — the app shows
"Typing on …" when it is. Password fields on some tvOS versions block reading
the current text; replacing/typing still works.

**Why does the app need the multicast permission?**
`CHANGE_WIFI_MULTICAST_STATE` is required for mDNS discovery on Android;
without a multicast lock the scan silently finds nothing. By default the app
makes no network connections other than to your Apple TV.

The one exception is entirely optional and off by default: if you turn on
**Settings → Fetch real app icons**, the Apps grid will call Apple's public
iTunes lookup/search API to load real artwork (cached on disk). Leave it off to
keep the app fully local — the Apps grid then shows generated initial tiles and
makes no outside requests.

**Now playing info / artwork?**
Not in v1 — that needs a different protocol (MRP over AirPlay 2). Planned as a
possible v2.

## Building

```bash
./gradlew :protocol:test          # protocol unit tests (pure JVM)
./gradlew :app:assembleDebug      # APK at app/build/outputs/apk/debug/
./gradlew :cli:run --args="scan"  # CLI harness for protocol debugging
```

## Modules

- `protocol/` — pure Kotlin/JVM implementation of the Companion Link protocol:
  OPACK, TLV8, HAP pair-setup/pair-verify (SRP-6a), the ChaCha20-Poly1305
  session, HID/touch/app/power commands and RTI text input (binary-plist keyed
  archives). No Android dependencies — reusable.
- `cli/` — command-line harness (`scan`, `pair`, `command`, `text-set`,
  `focus-state`, …). The primary integration-test tool against real hardware.
- `app/` — the Android app (Jetpack Compose, Material 3).
- `docs/protocol-notes.md` — the verified protocol details and where each piece
  was ported from.

## Credits & license

MIT. The protocol knowledge comes from the outstanding reverse-engineering work
of [pyatv](https://github.com/postlund/pyatv) (MIT) — this project is a
from-scratch Kotlin implementation following pyatv's documented behavior.
