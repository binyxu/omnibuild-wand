# OmniBuild Wand · 全能建造杖

> 为**大规模建造**而生的 Fabric 建筑手杖。圈一片范围、囤好材料、一键量产。
> *A Fabric building wand built for **large-scale construction** — select a region, stock the materials, mass-produce in one click.*

<p align="center"><b>🌐 点击下方语言展开 / Click a language below to expand</b></p>

<!-- ====================== 中文 ====================== -->
<details open>
<summary><b>🇨🇳 中文（点击展开 / 收起）</b></summary>

<br>

**Minecraft 26.1.2 · Fabric**
**必需依赖：[Fabric API](https://modrinth.com/mod/fabric-api) · [Litematica](https://www.curseforge.com/minecraft/mc-mods/litematica) · [MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib)**

### ✨ 核心特性

| 模式 | 说明 |
| --- | --- |
| 🟦 **填充** | 框选两角，把范围内的空气填成手里的方块。 |
| 📋 **复制 / 粘贴** | 复制一片结构，支持**旋转(R)**、**镜像(B)**，原样粘贴到别处。 |
| ♻️ **替换** | 框选范围 → 选目标方块，整片批量替换成新方块。 |
| 🚚 **移动** | 把整块建筑连根挪到新位置，支持旋转 / 镜像。 |

- **方形选区 / 智能选区**：按 `V` 切换。智能选区自动识别连续建筑体，不用手点角落。
- **🏗️ 工地系统（招牌功能）**：材料不够时一键变「工地」——放置工地牌、往里囤料、点「开始施工」即自动建造。**专为不想手摆几万个方块的大型工程设计。**
- **📐 Litematica 蓝图导入**：`/wand load <文件路径>` 读取 `.litematic` 蓝图，直接生成工地施工。
- **生存友好**：生存模式真实扣除背包材料（支持从**潜影盒**里取），不是凭空造方块。
- **大体量**：智能选区默认上限 25,000 方块、扫描 80,000 格，可在设置里调。

### ⌨️ 操作

| 按键 | 作用 |
| --- | --- |
| 右键 | 选角 / 执行（填充、粘贴、移动、替换） |
| `Shift` + 右键 | 重置当前选区 |
| `R` | 旋转 |
| `B` | 镜像 |
| `N` | 切换模式 |
| `V` | 方形 / 智能选区 切换 |
| `/wand settings` | 打开设置（智能选区上限） |
| `/wand load <path>` | 加载 Litematica 蓝图 |

### 🛠️ 合成

钻石 ×1 + 烈焰棒 ×2，斜向排布：

```
· · D
· B ·
B · ·
```
`D` = 钻石　`B` = 烈焰棒

### 📦 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) 与 [Fabric API](https://modrinth.com/mod/fabric-api)。
2. 安装 [Litematica](https://www.curseforge.com/minecraft/mc-mods/litematica) + [MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib)（蓝图与幽灵预览所需）。
3. 把 `omnibuild-wand-x.x.x.jar` 放进 `.minecraft/mods/`。
4. 进游戏，合成「全能建造杖」开造。

### 🧱 从源码构建

```bash
./gradlew clean build
# 产物在 build/libs/omnibuild-wand-x.x.x.jar
```

> 编辑中文资源后务必 `clean build`，并保持 `lang/zh_cn.json` 用 `\uXXXX` 转义，避免乱码。

</details>

<!-- ====================== English ====================== -->
<details>
<summary><b>🇬🇧 English (click to expand)</b></summary>

<br>

**Minecraft 26.1.2 · Fabric**
**Required: [Fabric API](https://modrinth.com/mod/fabric-api) · [Litematica](https://www.curseforge.com/minecraft/mc-mods/litematica) · [MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib)**

### ✨ Features

| Mode | What it does |
| --- | --- |
| 🟦 **Fill** | Select two corners; fill all air in the region with the block in your hand. |
| 📋 **Copy / Paste** | Copy a structure, **rotate (R)** and **flip (B)**, paste it elsewhere. |
| ♻️ **Replace** | Select a region → pick a target block; swap it for a new block in bulk. |
| 🚚 **Move** | Relocate a whole build to a new spot, with rotate / flip. |

- **Box / Smart Selection**: toggle with `V`. Smart Selection auto-detects a contiguous structure — no corner clicking.
- **🏗️ Worksite System (the headline feature)**: out of materials? Turn the job into a *worksite* — place a worksite post, deposit materials, hit **Start Construction**, and it builds itself. **Made for huge projects you don't want to hand-place block by block.**
- **📐 Litematica blueprint import**: `/wand load <file path>` reads a `.litematic` schematic straight into a worksite.
- **Survival-friendly**: in Survival it actually consumes materials from your inventory (including **shulker boxes**) — no free blocks.
- **Built for scale**: Smart Selection defaults to 25,000 blocks / 80,000-cell scan, adjustable in settings.

### ⌨️ Controls

| Key | Action |
| --- | --- |
| Right-click | Pick corner / execute (fill, paste, move, replace) |
| `Shift` + Right-click | Reset current selection |
| `R` | Rotate |
| `B` | Flip |
| `N` | Switch mode |
| `V` | Toggle Box / Smart Selection |
| `/wand settings` | Open settings (Smart Selection limits) |
| `/wand load <path>` | Load a Litematica schematic |

### 🛠️ Crafting

1× Diamond + 2× Blaze Rod, diagonal layout:

```
· · D
· B ·
B · ·
```
`D` = Diamond　`B` = Blaze Rod

### 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Install [Litematica](https://www.curseforge.com/minecraft/mc-mods/litematica) + [MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib) (needed for blueprints and ghost preview).
3. Drop `omnibuild-wand-x.x.x.jar` into `.minecraft/mods/`.
4. Launch, craft the OmniBuild Wand, and start building.

### 🧱 Build from source

```bash
./gradlew clean build
# Output: build/libs/omnibuild-wand-x.x.x.jar
```

</details>

---

## 📄 License

MIT — see [LICENSE](LICENSE). Litematica / MaLiLib are external runtime dependencies; their code is not included in this repository.
