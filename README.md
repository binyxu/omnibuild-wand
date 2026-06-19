# OmniBuild Wand (Minecraft Mod) · 全能建造杖

> 为**大规模建造**而生的 Fabric 建筑手杖。圈一片范围、囤好材料、一键量产。
> *A Fabric building wand built for **large-scale construction** — select a region, stock the materials, mass-produce in one click.*

![OmniBuild Wand](docs/images/worksite-building.png)

<p align="center">
<b>Minecraft 26.2 · Fabric</b> ·
<a href="https://modrinth.com/mod/omni-build-wand">Modrinth</a> ·
必需依赖 / Required: <a href="https://modrinth.com/mod/fabric-api">Fabric API</a> · <a href="https://modrinth.com/mod/litematica">Litematica</a> · <a href="https://modrinth.com/mod/malilib">MaLiLib</a>
</p>

<p align="center"><b>🌐 点击下方语言展开 / Click a language below to expand</b></p>

<!-- ============================== 中文 ============================== -->
<details open>
<summary><b>🇨🇳 中文教程（点击展开 / 收起）</b></summary>

<br>

## 📦 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) 与 [Fabric API](https://modrinth.com/mod/fabric-api)。
2. 安装 [Litematica](https://modrinth.com/mod/litematica) + [MaLiLib](https://modrinth.com/mod/malilib)（蓝图与幽灵预览所需，**必需**）。
3. 把 `omnibuild-wand-x.x.x.jar` 放进 `.minecraft/mods/`。
4. 进游戏即可。

## 🛠️ 合成

钻石 ×1 + 烈焰棒 ×2，斜向排布：

```
· · D        D = 钻石 Diamond
· B ·        B = 烈焰棒 Blaze Rod
B · ·
```

![合成配方](docs/images/crafting.png)

## 🎮 基础概念

- **手持手杖右键**＝选角 / 执行操作。大多数操作是「右键选第一个角 → 再右键选第二个角 → 完成」。
- 按 **`N`** 在五种模式间循环切换：**填充 → 复制/粘贴 → 替换 → 移动 → 供料链接**。
- 按 **`V`** 在 **方形选区 ↔ 智能选区** 间切换。
- **`Shift` + 右键**＝重置当前选区，重新开始。
- 生存模式会**真实扣除背包材料**（可从快捷栏或副手的**潜影盒**里取）；材料不够时可改用「工地」慢慢囤料建造（见下文）。

---

## 🟦 填充模式 Fill

把一个长方体范围内的空气填成你指定的方块。

1. 把要填充的方块放进**副手或快捷栏**。
2. 右键点第一个角 → 右键点对角，范围内的空气立即填满。

![填充模式](docs/images/fill.png)

## 📋 复制 / 粘贴模式 Copy

原样复制一片结构，粘到别处，支持旋转和镜像。

1. 右键选两个角，框住要复制的结构 → 自动记录到剪贴板。
2. 移动到目标位置，右键即可**粘贴**。
3. 粘贴前可按 **`R` 旋转**、**`B` 镜像** 调整朝向（会有幽灵预览）。

![复制模式](docs/images/copy.png)

## ♻️ 替换模式 Replace

把范围内某一种方块批量换成另一种。

1. 把**要替换成**的新方块放进副手 / 快捷栏。
2. 右键选两个角框定范围。
3. 第三次右键**点击你想被替换掉的那种方块**，范围内所有同类方块即被替换。

| 替换前 | 替换后 |
| --- | --- |
| ![替换前](docs/images/replace-before.png) | ![替换后](docs/images/replace-after.png) |

## 🚚 移动模式 Move

把整块建筑连根挪到新位置（原地清空），支持旋转 / 镜像。

1. 右键选两个角框住要搬的建筑 → 记录。
2. 右键点目标位置即可**整体移动**过去；同样支持 `R` / `B`。

| 移动前 | 移动后 |
| --- | --- |
| ![移动前](docs/images/move-before.png) | ![移动后](docs/images/move-after.png) |

## 🧠 智能选区 Smart Selection

不想手点角落框范围？按 **`V`** 切到智能选区，右键一个方块，它会**自动识别整片连续的建筑体**并锁定，再次右键确认即可执行（复制 / 替换 / 移动 都能用）。适合形状不规则的结构。

![智能选区](docs/images/smart-selection.png)

> 扫描 / 方块上限可在 `/wand settings` 里调（默认扫描 80,000 格、最多选中 25,000 方块）。

---

## 🏗️ 工地系统（招牌功能）

大型工程不想一次性掏空仓库？把它变成「工地」，慢慢囤料、自动施工。

1. 在复制 / 粘贴或蓝图模式下，材料不足时会**自动转为工地**（也可主动选择）。地面上会立起一个**工地牌**。

   ![生成工地](docs/images/worksite-created.png)

2. **右键工地牌**打开管理站：左侧列出所需材料与已投入数量。把材料丢进去「存入材料」。

   ![工地管理站](docs/images/worksite-dashboard.png)

3. 材料够了点「**开始施工**」，工地就会**自动逐块建造**，直到完工。

   ![自动建造中](docs/images/worksite-building.png)

> 管理站还有两个按钮：**「取回材料」**把已存入工地的材料退回背包（施工中不可用）；**「从箱子补料」**配合下面的「供料链接」一键补料。

## 🔗 供料链接 Supply Link（接箱子自动供料）

懒得手动往工地搬材料？把附近的箱子接上，工地施工时**自动取料**。

1. 按 `N` 切到 **供料链接** 模式。
2. 右键框选两个角，把装材料的**箱子 / 木桶 / 潜影盒**圈进去（**箱子里的潜影盒也算**）。
3. 再**右键工地牌**完成链接，提示「已链接 N 个容器」。
4. 在管理站点 **「从箱子补料」**，或直接**开始施工**——施工时每秒自动从这些容器取料边抽边盖。

> 🛡️ 安全机制：每个容器、每种材料**至少保留 1 个**，绝不把箱子抽空。

## 📐 Litematica 蓝图导入

把外部 `.litematic` 蓝图直接变成工地来施工：

1. 手持手杖，输入命令：`/wand load <蓝图文件路径>`（路径可带引号）。
2. 加载成功后会显示方块总数，右键地面即可**生成对应工地**，之后流程同上。

![加载蓝图](docs/images/blueprint-load.png)

## ⌨️ 按键速查

| 按键 | 作用 |
| --- | --- |
| 右键 | 选角 / 执行 |
| `Shift` + 右键 | 重置选区 |
| `R` | 旋转 |
| `B` | 镜像 |
| `N` | 切换模式（填充/复制/替换/移动/供料链接） |
| `V` | 方形 / 智能选区 切换 |
| `/wand settings` | 打开设置（智能选区上限） |
| `/wand load <path>` | 加载 Litematica 蓝图 |

</details>

<!-- ============================== English ============================== -->
<details>
<summary><b>🇬🇧 English Guide (click to expand)</b></summary>

<br>

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Install [Litematica](https://modrinth.com/mod/litematica) + [MaLiLib](https://modrinth.com/mod/malilib) (**required** — for blueprints & ghost preview).
3. Drop `omnibuild-wand-x.x.x.jar` into `.minecraft/mods/`.
4. Launch the game.

## 🛠️ Crafting

1× Diamond + 2× Blaze Rod, diagonal layout:

```
· · D        D = Diamond
· B ·        B = Blaze Rod
B · ·
```

![Crafting](docs/images/crafting.png)

## 🎮 Core concepts

- **Right-click with the wand** = pick a corner / execute. Most actions are *right-click corner 1 → right-click corner 2 → done*.
- Press **`N`** to cycle the five modes: **Fill → Copy/Paste → Replace → Move → Supply Link**.
- Press **`V`** to toggle **Box ↔ Smart Selection**.
- **`Shift` + Right-click** = reset the current selection.
- In Survival it **actually consumes materials** from your hotbar/offhand (including **shulker boxes**). Short on materials? Build it as a *worksite* instead (see below).

---

## 🟦 Fill Mode

Fill all air in a box region with a chosen block.

1. Put the fill block in your **offhand or hotbar**.
2. Right-click corner 1 → right-click the opposite corner. The air fills instantly.

![Fill](docs/images/fill.png)

## 📋 Copy / Paste Mode

Copy a structure and paste it elsewhere, with rotate & flip.

1. Right-click two corners around the structure → it's saved to the clipboard.
2. Move to the target spot and right-click to **paste**.
3. Before pasting, press **`R` to rotate** / **`B` to flip** (a ghost preview shows the result).

![Copy](docs/images/copy.png)

## ♻️ Replace Mode

Swap one block type for another in bulk within a region.

1. Put the **replacement** block in your offhand / hotbar.
2. Right-click two corners to define the region.
3. Third right-click **on the block type you want replaced** — every matching block in the region is swapped.

| Before | After |
| --- | --- |
| ![Before](docs/images/replace-before.png) | ![After](docs/images/replace-after.png) |

## 🚚 Move Mode

Relocate a whole build to a new spot (source is cleared), with rotate / flip.

1. Right-click two corners around the build → recorded.
2. Right-click the target position to **move** it there. `R` / `B` supported.

| Before | After |
| --- | --- |
| ![Before](docs/images/move-before.png) | ![After](docs/images/move-after.png) |

## 🧠 Smart Selection

Don't want to click corners? Press **`V`** for Smart Selection: right-click one block and it **auto-detects the entire contiguous structure** and locks it; right-click again to confirm and execute (works with Copy / Replace / Move). Great for irregular shapes.

![Smart Selection](docs/images/smart-selection.png)

> Tune the scan / block limits in `/wand settings` (defaults: 80,000-cell scan, 25,000 blocks max).

---

## 🏗️ Worksite System (the headline feature)

Big project, don't want to drain your whole inventory at once? Turn it into a *worksite* — stock materials over time and let it build itself.

1. In Copy/Paste or Blueprint mode, if you're short on materials it **auto-converts to a worksite** (you can also choose this). A **worksite post** appears on the ground.

   ![Worksite created](docs/images/worksite-created.png)

2. **Right-click the post** to open the dashboard: it lists required materials and how much you've deposited. Deposit materials there.

   ![Worksite dashboard](docs/images/worksite-dashboard.png)

3. Once materials are sufficient, hit **Start Construction** and the worksite **builds itself block by block** until done.

   ![Auto-building](docs/images/worksite-building.png)

> The dashboard also has two buttons: **Withdraw Materials** returns deposited materials to your inventory (disabled while building); **Pull from Chests** works with Supply Link below.

## 🔗 Supply Link (auto-feed from chests)

Don't want to haul materials to the worksite by hand? Link nearby chests and it **pulls materials automatically** while building.

1. Press `N` to switch to **Supply Link** mode.
2. Right-click two corners around your **chests / barrels / shulker boxes** (shulker boxes *inside* chests count too).
3. Then **right-click the worksite post** to link — it reports "Linked N containers".
4. Click **Pull from Chests** in the dashboard, or just **Start Construction** — it pulls from those containers every second as it builds.

> 🛡️ Safety: each container always keeps **at least one** of every material — chests are never fully emptied.

## 📐 Litematica Blueprint Import

Turn an external `.litematic` schematic straight into a worksite:

1. Hold the wand and run: `/wand load <path to .litematic>` (quotes allowed).
2. On success it reports the block count; right-click the ground to **spawn the matching worksite**, then proceed as above.

![Load blueprint](docs/images/blueprint-load.png)

## ⌨️ Controls cheat sheet

| Key | Action |
| --- | --- |
| Right-click | Pick corner / execute |
| `Shift` + Right-click | Reset selection |
| `R` | Rotate |
| `B` | Flip |
| `N` | Switch mode (Fill/Copy/Replace/Move/Supply Link) |
| `V` | Toggle Box / Smart Selection |
| `/wand settings` | Open settings (Smart Selection limits) |
| `/wand load <path>` | Load a Litematica schematic |

</details>

---

## 🧱 从源码构建 / Build from source

```bash
./gradlew clean build
# 产物 / Output: build/libs/omnibuild-wand-x.x.x.jar
```

需 JDK 21+（本项目用 JDK 25）。编辑中文资源后务必 `clean build`，并保持 `lang/zh_cn.json` 用 `\uXXXX` 转义。

## 📄 License

MIT — see [LICENSE](LICENSE). Litematica / MaLiLib are external runtime dependencies; their code is not included here.
