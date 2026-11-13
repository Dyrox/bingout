# Bingout / Draftout 模式交接文档

## 最新修正：命名及选牌提交（2026-11-13 17:xx）

- 用户明确要求：仅外部模组名、JAR 使用 **Bingout**；游戏内模式、filter、选牌标题、记分板名称使用 **Draftout**。资源/配置 ID 保持兼容。
- 用户在 LAN 内置服务器上报告当前队伍也不能点击选牌，生产日志确认 canPick=true 的回合仍全部超时。此前自动测试直接调用界面函数，并未验证真实鼠标事件；不能视为该问题已通过实机验证。
- 本次将界面选牌/重抽改为原生命令 `bingo draft choose <turn> <option>`，不再依赖自定义 C2S 通道可用性；统一通过 DraftController.choose 校验回合 token、队伍及重抽次数。保留原 C2S 入口兼容旧客户端。
- 请求没有换轮时，一秒后允许再次点击，避免 pending 状态一直锁住；有效点击会写入 `[Bingout] Click` 日志，便于确认真实鼠标事件是否到达界面。
- 相关单元测试覆盖 choose 的权限、旧请求、重抽、25 轮及超时；本次尚未在用户整合包中验证实体鼠标输入，不能宣称已经复现并证明根因。
- 项目目录已由 `.../2026/bingo` 改为 `.../2026/bingout`。旧工作目录会使 exec 报 No such file or directory，并非工具损坏。


> 最新状态（2026-11-13，Codex）：已在 **26.3 独立服务器 + 两个真实客户端**中跑完双队 draft 和 13 格获胜流程。已补全部 objective 的解析测试、3D 大厅 Draftout 按钮，并修复 Xaero’s 导致的服务端启动崩溃。所有改动仍在 `main`，未 commit。

## Bingout 共享选牌界面（2026-11-13，最新）

- 用户可见名称、模组名称、菜单分类、记分板和 JAR 名称改为 **Bingout**。使用 `/bingo mode bingout`；旧 `draftout` 命令、mod/data/config ID 保留兼容，素材来源仍注明 Draftout。
- 新增 `DraftPacket` / `DraftActionPacket`、客户端 `BingoutDraftController` / `BingoutDraftScreen`。参考原版 `DraftScreen` 的状态栏、进度条、两个图标候选、两队重抽按钮、5×5 棋盘结构；使用半透明背景且不模糊世界。候选及棋盘格可悬停查看目标描述。
- 两队及旁观者都持续收到同一轮候选和已选棋盘；仅当前队伍可选/重抽。服务端同时校验队伍、重抽次数和每轮随机 token，防止延迟/重复点击作用于下一轮。中途加入/重连会同步当前快照。旧客户端走聊天/原版 dialog 回退。
- 候选目标的自定义图片在展示前通过 CardImageService 发送；重抽后清理不用的图片。复用原版的两张 GUI 重抽图标。
- 每次 Gradle 调用自动生成一个 UTC 构建号，三个目标版本共享该编号；版本进入文件名、fabric.mod.json 和 Constants。`-Pbuild_id=<id>` 可显式复现固定编号。详见 `BINGOUT_BUILD.md`。
- 最终包：`mc26.x/build/libs/bingout-2.16.0-build.20261113163839736+mc26.x.jar`。26.1 / 26.2 / 26.3 assemble 均通过；逐包验证 PNG、407 个目标、客户端界面类、版本元数据和 ZIP CRC。
- 最终针对性测试 **411 项通过**：407 个资源读取检查，以及 4 项真实 DraftController 测试（双方/旁观者同步及权限、重抽和旧点击、完整 25 轮关闭、10 秒超时）。原来上游的两个配置失败不属于这次针对性运行。
- **26.3 独服 + 两个真实客户端完整走完 25 轮**：通过客户端界面点击选择/重抽，双方各重抽一次，实际发生并处理了超时随机选，中途重启一个客户端后恢复当前界面，最后自动关闭并进入 PLAYING。双客户端日志确认候选相同且 canPick 随队伍交替。读取游戏内部截图检查实际布局和图标。临时自动化驱动已从源码/发布 JAR 移除；最终重抽图标由小字符改为原版 PNG。
- 日志及截图：`build/bingout-validation/`。截图上的不安全聊天提示来自离线测试服的原版 toast。测试进程已停止、测试白名单已清空，未影响用户自己的 Minecraft 进程。
- 本轮恢复了之前死亡测试遗留的测试玩家状态；原有 `WaitUntilLoadedController` 超过 40 秒后可能一直等待的问题未改，后续可独立处理。

## Codex 接手后的验证与改动（2026-11-13）

### 后续图标修正（按用户提供的原版贴图）

- 203 张原版 PNG 按字节原样复制到 `scripts/draftout/assets/goal/`；`icons.json` 保存对照原版 goal 类和继承实现整理的贴图、叠加层、数量与备选帧映射。来源及静态帧约定见 `scripts/draftout/assets/README.md`。
- `gen_icons.py` 随 `generate.py` 生成 246 张卡面图，输出到数据包 `images/draftout/`；例如繁殖猪为猪头加爱心，死亡目标使用各自死因图，进度/效果/堆肥/命名等使用原版素材。服务端通过现有 CardImagePacket 分发，地图卡也读取同一份图片。
- 自定义图明确设置 `itemStyle=NORMAL`、`decoration=NONE`，避免组合目标网格覆盖主图及多余进度角标。统计类组合目标（包括吃遍所有汤）明确使用主图，修复其子目标没有物品图而导致的空白卡格。
- 水瓶、六种药水、染色皮甲补充真实物品显示组件；末地城图标改为原版使用的末地石砖，全套附魔盔甲改为带附魔光效的胸甲。
- Bingo 卡图目前为静态 PNG：轮换图使用原版一帧，`jeb_` 羊取精灵图首帧叠加命名牌；原版缺少素材的六种额外羊颜色使用对应羊毛加原版骷髅标记。旧客户端的原版选择 dialog 回退仍显示 `display.item`；新版 Bingout 客户端界面使用自定义卡图。
- 新增图片验证并入全部 407 个 objective 动态测试：检查引用资源存在、PNG 可解码且非全透明、自定义图不走子目标网格。407 项全部通过；三个版本 assemble 通过，jar 内均包含 407 个 objective 和全部 246 张被引用的卡图。生成器重复运行的 653 个 objective/图片文件逐字节一致。
- 26.3 真客户端连接实测成功加载 13 张自定义卡图，包括猪头爱心、袭击生物、击杀计数、`jeb_` 羊、仙人掌死亡；客户端和服务器无运行时 ERROR，地图生成无图片错误。对应日志已保存到 `build/draftout-validation/bingo-draftout-icons-*.log`，测试进程已停止。
- 可区分旧包的新产物位于 `build/draftout-icons/bingo-2.15.1+mc26.x-draftout-icons.jar`，三个版本均已生成。原路径 `mc26.x/build/libs/` 中的 jar 也已更新。

### 本轮新增代码

- `DraftoutObjectiveDataTest.kt`：从测试运行时资源中遍历全部 407 个生成 objective（含 placeholder），逐个使用生产环境的 `jsonStrict.decodeFromStream<ObjectiveData>` 解析；动态测试以文件名报告失败，并检查总数，避免漏加载资源后假通过。
- `menu/GameModes.kt`：增加 Draftout 按钮、说明和选中状态；调用现有 `toggleCardMode`。调整为三行两列，与 Features 页同样使用 0.6 格高，Draftout 和“更多功能”并排。
- `integration-xaeros/.../XaerosPlugin.kt`：只有客户端环境才初始化 `XaerosController`。已验证服务端正常启动、客户端正常连接。

### 实际运行验证

- 环境：JDK 25、MC 26.3、Fabric 开发服务器 `localhost:25598`；两个真实客户端 `draftred` / `draftblue`，分别加入红、蓝队。**本轮服务器为非 lobby 模式**。
- 通过服务器控制台以在线玩家身份执行选择命令；两个客户端日志均确认实际打开 `MultiButtonDialogScreen`。
- 走完 25 次选择，红蓝队交替，双方各重抽一次；重复请求重抽没有产生额外重抽。等待两次超时，日志确认每次 10 秒后自动选取。
- 状态完整经过 `loading → drafting → countdown → playing`；25 格选满后正常结束 draft。
- 用 `/give`、`/experience`、`/item replace` 构造完成条件，确认实际选出的物品、组合物品、经验等级和装备目标正确计分。蓝队后来获得红队已完成的比较器，没有抢到该格。
- 为单独验证 Kotlin 判定，比赛中用 `/bingo card set` 将两格替换为“获得一组 64 个物品”和“被仙人掌刺死”，再分别给予 64 圆石、施加致命 cactus 伤害；两者正确计分。没有直接用 `/advancement grant` 模拟完成。
- 共验证 15 个不同目标计分；红队 12 格时未结束，13 格时触发 FirstWin、保存统计并进入 postgame。
- 这属于真实客户端/服务端的命令辅助集成测试，**不等同于手工生存游玩测试**。当前桌面控制工具无法绑定这两个 Java 游戏窗口，故尚未目视检查 dialog 的 tooltip、布局或大厅按钮，也未用鼠标点击选择按钮。
- 测试客户端和服务端已停止；本轮添加的两个白名单条目已移除。客户端独立目录保存在 `/tmp/bingo-draftout-client-red` 和 `/tmp/bingo-draftout-client-blue`。测试使用的 `mc26.3/run` 世界、配置、统计会保留本轮运行结果。
- 日志已留在 `build/draftout-validation/`（构建目录，不提交）。

### 编译与自动测试

- 三版本 `:mc26.1:compileKotlin :mc26.2:compileKotlin :mc26.3:compileKotlin` 通过；26.3 客户端构建并启动通过。
- `:common:test`：**444 项，442 通过、2 失败**。407 个新解析测试全部通过；失败仍是原有两个 `ConfigManagerTest` 配置快照问题。
- `git diff --check` 通过。
- 不要在运行开发客户端/服务器时重新构建其依赖 jar；会造成已打开的 jar 偏移失效（`ZipFile invalid LOC header`）。本轮停止进程、完成构建并重新启动后，连接和比赛流程正常。

### 本轮发现 / 仍待处理

1. 非 lobby 模式的出生点冻结仍未实现（原交接已指出）；本轮没有验证 lobby 模式下冻结和侦察体验。
2. 3D 大厅按钮已实现，但还需在大厅中目视检查并点击验证；dialog 的鼠标选择、tooltip 和布局也需人工体验检查。
3. 13 格获胜后的纪念地图生成抛出 `UnsupportedTemporalTypeException: Unsupported field: HourOfDay`。位置是 `MapItemService.kt` 的 `DateTimeFormatter.ofPattern("H:mm dd-MM-yyyy").format(Instant)`，未设置时区；已对照 `git show HEAD` 确认该代码原本就存在，本轮没有修改。胜负与统计在异常前已正确保存。
4. 非 lobby 模式结算返回 pregame 时还会出现 `Attempted teleportToLobby, but the lobby world is null`；本轮未修改这部分逻辑。
5. 以下原交接记录中的旧版本进度兼容性、近似实现等事项仍然适用。

---

以下为接手前的原始架构、验证和待办记录；完成情况以本节为准。

## 目标（用户需求）

1. 把 Draftout mod（`draftout-mod-original/draftout-1.18.0.jar`，目标 MC 26.3）里的 **316 个 goal** 搬进 bingo，作为一个新的 goal 分类 **`draftout`**。
2. 加一个 **Draftout 模式**：只能 **2 支队伍**，开局前两队**轮流 draft**（选择）棋盘上的 25 个 goal。
   - 规则参考 <https://draftoutmc.com/wiki/gameplay/drafting>：
     - 每轮给 2 个随机 goal，选 1 个。
     - 每轮限时 10 秒，超时随机选一个。
     - 每队整个 draft 可以重抽 1 次。
     - 两队交替选，直到 25 格选满。
     - Lockout 模式，先拿 13 格的一方获胜。
   - 用户说可以直接用原版 jar 里的东西（贴图等）。目前**没有用**它的代码和贴图，只参考了 goal 列表和规则。

## 架构概览

### 1. Goal 数据（生成器）：`scripts/draftout/`

- **`generate.py`**：入口，在仓库根目录运行 `python3 scripts/draftout/generate.py`。它会输出以下内容：
  - `common/src/main/resources/data/yet-another-minecraft-bingo/yet-another-minecraft-bingo/objectives/draftout/*.json`
    - 407 个 objective：316 种 goal，颜色变体展开成多个，再加 1 个 placeholder。
  - `common/src/main/resources/data/yet-another-minecraft-bingo/advancement/draftout/**.json`
    - 146 个**隐藏进度**（没有 `display`，所以不会弹 toast、也不会发聊天消息）。
  - `.../tierlists/draftout.tierlist.json`
    - 按 S/A/B/C/D 分级。
    - `groups` 保证同一组颜色变体（例如 "64 个 X 色羊毛"）只出现一个。
  - `en_us.json` / `zh_cn.json`
    - 所有 goal 名（`bingo.objective.draftout.*`）。
    - draft UI 文本（`bingo.draft.*`、`bingo.options.mode_draftout*`、`bingo.list.draftout`）。
    - UI 文本定义在 `gen_lib.py` 的 `UI_STRINGS` 里。
    - **改 lang 要改生成器**，否则重新生成时会被覆盖。
- **`gen_lib.py`**：工具函数。
  - `crit()` 和 `ent()` 会把简单写法转换成 **26.x 的实体谓词格式**。26.x 用的是带命名空间的键，例如 `minecraft:entity_type`、`minecraft:type_specific/player`、`minecraft:equipment`。
  - 条件里用 `"type"`，不用 `"condition"`。
- **`goals_items.py` / `goals_mobs.py` / `goals_misc.py`**：316 个 goal 的映射。
- **`draftout_goal_types.txt`**：从原 jar 的 `DefaultGoalRegister` 提取的全部 goal 常量名。生成器会报告覆盖率，当前为 316/316。

### Goal 的四种实现方式

| 方式 | 用于 |
|---|---|
| bingo 原生类型（`item` / `all_of` / `one_of` / `some_of` / `stats`） | 获得物品、挖掘、击杀特定生物、吃东西、使用工作站等 |
| 原版进度（`minecraft:...`） | 找要塞、Bullseye、Wax on 等 |
| 自定义隐藏进度（数据包触发器） | 驯服、繁殖、状态效果、穿全套盔甲、望远镜、到达高度、各种方块交互等 |
| Kotlin 判定 → 授予 `minecraft:impossible` 进度 / 写记分板 | 死法、背包类、拴绳、激怒、计数类 goal（见下） |

### 2. Kotlin 代码：`common/src/main/java/me/jfenn/bingo/common/draft/`

- **`DraftController.kt`**：draft 流程。
  - 新增游戏状态 `DRAFTING`，通过 `GameAction.START.insertAfter(LOADING, DRAFTING)` 插入（静态，只插一次）。玩家这时已经在出生点并被冻结，可以侦察。
  - 没开 draft 模式，或者队伍数不等于 2 时，直接进入下一个状态，使用正常生成的卡。
  - 卡片先填满 placeholder（`yet-another-minecraft-bingo:draftout/placeholder`，一个永远完不成的进度 goal，已加进 `tags/unobtainable.json`），每选一个就调用 `CardService.setEntries` 重建卡片。
  - UI 分三部分：
    - 选择方会收到 dialog，展示 2 个物品，带按钮"1.xxx / 2.xxx / 重抽"。
    - 聊天里也会有可点击的选项。
    - 所有人的 hotbar 显示倒计时。
  - 选满 25 个后关闭 dialog，进入 COUNTDOWN。
- **`DraftCommand.kt`**：`/bingo draft pick <1|2>`、`/bingo draft reroll`。只在 DRAFTING 状态下可用。
- **`DraftoutTriggerController.kt`**：只在 PLAYING 状态、且卡上有 draftout goal 时运行，每 10 tick 检查一次。
  - **死法**：通过新的 `PlayerDeathEvent` 实现（Fabric `ServerLivingEntityEvents.AFTER_DEATH`）。
    - 主要按 `DamageSource.msgId` 和攻击者实体判断。
    - "从藤蔓上摔死"用死亡消息的翻译 key 判断。
  - **计数类**：写入 dummy 记分板，对应的 goal 使用 `scoreboard` 类型。
    - 记分板有：`draftout_bred` / `_spy` / `_compost` / `_food` / `_crafted` / `_undead` / `_arthropod` / `_advancements` / `_effects` / `_pumpkin` / `_leash`。
    - 统计类计数以开局时的数值为基准。
    - 繁殖、望远镜、堆肥这三类用"永远完不成的计数进度"（`counter/*.json`），由 Kotlin 读取已完成的 criteria 数量。
  - **布尔类**：授予进度。包括：
    - 拴铁傀儡、激怒末影人/僵尸猪灵、Dinnerbone/jeb_ 命名
    - 四种不同材质盔甲、四种不同颜色皮革盔甲
    - 末影珍珠穿下界门、饥饿值归零、锁定地图
    - 背包 36 种不同物品、一组 64 个物品
    - 盾牌被破、弩射烟花、间歇泉

### 3. 改动过的已有文件

- **`options/BingoCardOptions.kt`**：新增 `isDraftMode`。
- **`OptionsService.toggleCardMode`**：开启 draft 时自动设置 lockout、goal 改为 `Items(13)`、filter 改为 `+draftout -unobtainable`，然后重新生成卡片。
- **`BingoOptions.kt`**：模式名、图标 `✍`、SHA hash。
- **`commands/BingoOptionsCommands.kt`**：新增 `/bingo mode draftout [true|false]`。
- **`game/GameService.kt`**：draft 模式下队伍数不等于 2 时给出 severe warning，可以用 ignoreWarnings 跳过。
- **`card/CardService.kt`**：
  - 新增 `setEntries()`。
  - draftout tierlist 改为 **opt-in**：只有 filter 显式写了 `+draftout`（或 count）才会使用它。
  - draftout goal 永远不会作为 "uncategorized" 混进普通卡。
- **`card/filter/ObjectiveFilter.kt`**：新增常量 `DRAFTOUT`。
- **`filters.json`**：新增 `draftout` 预设。
- **`timer/CountdownController.kt`**：DRAFTING 状态下也冻结玩家（骑蝙蝠），并冻结 tick。
- **`map/CardViewService.kt`**：DRAFTING 状态下显示卡片。
- **`CommonModule.kt`**：注册新组件。
- **`platform`**：
  - 新增 `event/model/PlayerDeathEvent.kt`。
  - `IDialogManager.closeDialog()`（默认什么都不做）。
- **`mc26.1` / `mc26.2` / `mc26.3`**：
  - `ServerEventsImpl` 发出 `PlayerDeathEvent`。
  - `DialogManager.closeDialog`（发送 `ClientboundClearDialogPacket`）。

## 已验证

- `./gradlew :mc26.1:compileKotlin :mc26.2:compileKotlin :mc26.3:compileKotlin`：通过。
  - **需要 JDK 25**：`export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`
- 生成的所有物品/实体/方块/统计/进度 ID 都已对照 MC 26.3 注册表校验（用 server jar 的 `--reports` 生成）。
- 146 个自定义进度已作为数据包放进**原版 26.3 服务器**加载，没有解析错误。
- `:common:test`：37 个测试中 2 个失败（`ConfigManagerTest`，`defaultPostGameReadyOption` AUTO 和 NEXT_ROUND 不一致）。这是上游最近一次 commit 留下的问题，**与本改动无关**。

## 未验证 / 下一步（按优先级）

1. **在真实游戏里跑一遍**：开 2 队，`/bingo mode draftout`，开始游戏，走完整个 draft 流程，然后打几个 goal。
   - 注意：`./gradlew :mc26.3:runServer` 目前会崩，报 `NoDefinitionFoundException: XaerosController`（客户端专用的集成被加载到了服务器端）。这是已有问题，和本改动无关。要么修这个问题，要么用 `runClient` 开局域网测试。
2. **补一个单元测试**：用 `jsonStrict.decodeFromStream<ObjectiveData>` 解析所有 `objectives/draftout/*.json`，确认 bingo 的序列化器能接受（例如 `count` / `min` 写成纯整数、内联的 `stats`）。这一步本来正要做。
3. **检查 dialog 体验**：
   - 物品的 tooltip 是否显示 goal 名。
   - 多个按钮的列数（`columns = 2`）是否合适。
   - 被关闭的 dialog 再点击时，命令会报"不是你的回合"。
4. **非 lobby 模式**（没有大厅世界）：DRAFTING 期间玩家不会被冻结（`CountdownController` 只在 lobby 模式下冻结）。可以考虑一并处理。
5. **是否用原版贴图做 goal 图标**：用户允许。原 jar 里有 `assets/draftout/textures/goal/**`。bingo 支持 `display.image`，需要研究一下 bingo 的 images 机制（`yet-another-minecraft-bingo/images`）。
6. **3D 大厅菜单**（`menu/GameModes.kt`）还没有 draft 按钮，目前只能用命令开启。
7. **26.1/26.2 不存在的新内容**（sulfur cube、cushion 等）：
   - objective 找不到时会被跳过（draft 池里也会过滤掉）。
   - 但对应的自定义进度在旧版本上会加载失败并在日志里报错。可以加 `fabric:load_conditions`。

## 有意的近似实现（和原版 Draftout 不完全一致）

- **Use Anvil / Stonecutter / Loom / Grindstone / Smithing / Cartography**：用"打开界面"的统计判定，原版是"取出产物"。
- **Use Brush**：用刷子使用次数判定，不检查是否刷完。
- **Duplicate Allay**：对悦灵使用紫水晶碎片即算，不检查是否在跳舞。
- **Sit on Colored Cushion**：坐上任意颜色的坐垫都算。
- **Colored Leather Armor Piece**：任意部位，颜色为单一染料染出的颜色即可。
- **Fill Decorated Pot**：数量为 64 或 16 才算。
- **Fill Campfire**：用方块 NBT 判定 4 个槽位。
- **Ride Geyser**：玩家在硫磺尖刺上方、并且向上速度大于 0.6。
- **Dinnerbone / jeb_**：玩家 16 格内存在该名字的对应生物就算，不检查是谁命名的。
- **Kill 7/10/13/15 unique hostile**：用 bingo 的 `some_of`，覆盖 37 种敌对生物的击杀统计。
- **Get 10/20/30 advancements**：统计 `minecraft` 命名空间下有 display、非 recipe 的进度。

## 小贴士

- 改 goal 只改 `scripts/draftout/*.py`，然后重新运行生成器，**不要手改生成出来的 JSON**。
- 26.x 的数据格式和旧版差异很大。不确定时，从 `~/.gradle/caches/fabric-loom/26.3/minecraft-extracted_server.jar` 解压 `data/minecraft/advancement` 对照原版写法，或者用 `javap` 查看 codec 的字段名。
