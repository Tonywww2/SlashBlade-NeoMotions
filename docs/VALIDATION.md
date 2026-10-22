# 验证记录

日期：2026-10-21。构建主机：Windows，构建守护进程 JDK 25，Java 17/21 编译 toolchain。

## 斩斧地面核心树与变形试作型（0.5.0）

完成斧／剑两个树根、19 个源动作状态、斩击槽与觉醒槽、45 秒高输出、斧强化、逐刀保存与同步、剑槽不足的转换限制及耗尽回斧。新增原创金属斩斧模型：主轴、刀鞘斧头、折叠剑刃分别绘制；第一／第三人称共用组件矩阵，原生回避等缺少部件轨道的动作回退到当前形态的导入待机姿态。

两节点 Java datagen 均成功，每端 15 份数据（新增斩斧刀定义、刀与手册配方、两份进度）。两节点 build 及各 **37 项 JUnit** 全部通过。新增测试覆盖 30 点门槛、斧恢复／剑停止恢复、觉醒增长与衰减、45 秒到期且不可续时、斧强化倍率、源主连段、源取消窗口、低槽回斧、全解放限制，以及两形态部件的第一／第三人称和原生 VMD 一致性。所有斩斧命中同时检查源有效窗口、刀光平面、切削刃与宽面方向。

新增部件原生 VMD 比较采用 0.001 方块（0.016 模型像素）容差，覆盖 native PMD 浮点插值；第一／第三人称矩阵检查保持 0.0001 方块。两招的连续碰撞窗口在量化为单次攻击时，接触点调整到窗口内最近的刃向领先帧，原接触帧留在 manifest。

`verify_wilds_retarget.py` 对照本机二进制验证 **2557 个源根骨／腿部姿态、51 个状态**，并核对斩斧人物及变形骨架 SHA-256。离线检查使用真实发布 OBJ、贴图、PA JSON 与 VMD，预览见 `docs/images/switchaxe-rig.png`。名称核对到解包 MSG GUID 与原文；基本主连段核对到招式指引的转换记录。

`tools/verify_release.py` 确认两个 JAR 中的全部模型、贴图、51 对动画、语言文件和 15 份 datagen 数据与工作区逐字节一致；版本 0.5.0、斩斧根与模型路径正确，无原始游戏文件、第三方 schema 或测试代码。发布于 `build/release/`，SHA-256 记录于 `checksums-0.5.0.json`。

日志：`build/switchaxe-datagen.log`、`build/switchaxe-build.log`、`build/switchaxe-provenance-verify.log`、`build/switchaxe-release-verify.log`。本轮没有启动游戏客户端或服务器 GameTest，也没有操作用户存档。多人网络下实际手感、资源包兼容性与实战画面尚未实机验收；已迁移逻辑及原作未覆盖部分见 [斩斧说明](SWITCHAXE.md)。

## 第一／第三人称动作统一（0.4.6）

根因是自有动作第三人称使用 Player Animator 的手腕及武器轨迹，第一人称仍走本体 VMD 挂点；两条路径还以不同的轴反射顺序和轴心施加 `body`，双刀回旋与全身转动时差异明显。

新增共用 `WeaponPose`，自有大剑／双刀第一人称直接采样当前 Player Animator 动画栈，与第三人称共用手腕到武器的变换。右手取 `rightArm/rightWeapon`，左手取 `leftArm/leftWeapon`，身体矩阵严格按照 PlayerRendererMixin → 原版模型反射 → 玩家缩放 → 模型偏移的顺序组合。渲染只设置 partial tick，不独立推进时钟，也不读取未在第一人称更新的共享 PlayerModel。保留 65% 武器缩放、渲染空间下移 0.18、大剑不显示背鞘，以及断刀、自发光和附魔闪光。原生 SA 与自有动画尚未安装的短暂阶段继续使用本体第一人称回退；这部分不纳入本次自有动作逐帧一致性结论。

两加载器各 **28 项 JUnit** 及 build 通过，日志 `build/first-person-rig-build.log`。新增回归读取真实 Player Animator 资源，对 32 个自有状态逐 1/4 tick 采样，将第一人称结果与原第三人称挂点的独立矩阵参考比较，覆盖左右握点、刀尖、切削刃、宽面法线和三组镜头角度；另检查连段身体混合、待机返回、蓄力／空中停帧与延迟动作同步，并断言渲染不推进动画时钟。矩阵比较容差为 0.0001 方块（0.0016 模型像素）。验证中发现并移除了法线的矩阵→四元数往返转换，避免翻身阶段的数值误差。

本轮没有启动客户端、GameTest 或 datagen；视野舒适度、光影包与游戏内实际观感未进行实机验收。原动作、模型、战斗规则和刀定义未重新生成。安装包 `build/release/*0.4.6*.jar`，校验值 `checksums-0.4.6.json`；打包检查见 `build/first-person-rig-release-verify.log`。

## 双刀第一人称视野（0.4.5）

双刀接入既有的第一人称渲染上下文，右手刀身和左手刀鞘都缩小至 65%，沿第一人称渲染坐标下移 0.18。覆盖刀身／断刀／刀鞘及对应自发光、附魔闪光；每次绘制后恢复矩阵。第三人称继续使用原模型和源动作，物品图标不参与第一人称调整。

两版本 build 及现有各 **26 项 JUnit** 通过，日志 `build/dual-first-person-build.log`。本轮仅修改客户端绘制条件，未新增镜像条件的测试，也未启动游戏客户端、GameTest 或 datagen；实际第一人称画面舒适度尚未实机评估。

最终安装包位于 `build/release/*0.4.5*.jar`，SHA-256 见 `checksums-0.4.5.json`。版本元数据、运行时类、64 个动画文件和每端 10 份 datagen 资源核对见 `build/dual-first-person-release-verify.log`。本次没有重新生成动作、模型或刀定义。

## 剑术手册出招表（0.4.4）

两本剑术手册通过自定义 TooltipComponent 展示“输入／招式／后续”三列表格，大剑 13 行、双刀 10 行。客户端工厂在两加载器的 MOD 事件总线注册；物品和表格数据不引用客户端渲染类。表格按当前 Font 的实际宽度计算列位置，窄窗口逐格换行，可用高度不足时缩放整个表格。表尾仅有三行按键与学习说明。

从本机原作提取猎人笔记 `actionguidedatanametext_wp00.msg.23`、`wp02.msg.23` 及相关 action-guide USER，解析版本、字符串偏移、条目名哈希和中英文文本。大剑使用原作“直斩、横扫斩、强力横扫斩、飞身跃入横扫斩、冲撞攻击、真·蓄力斩”；双刀使用“二连斩、二连回斩、车轮斩、鬼人连斩Ⅰ、鬼人六连斩、挥斩、上捞斩、跳跃二连斩”。手册与当前招式 HUD 统一名称。SA 仍为本体技能，不伪称原作招式。来源路径、SHA-256 和 GUID 见 `docs/move-name-sources.json`；原始文本仍留在 `.local`。

两版本 build 及现有各 **26 项 JUnit** 通过，日志 `build/manual-tooltip-build.log`。使用本地 Minecraft 默认字体位图与 Unihex 字形生成离线预览 `docs/images/manual-tooltips-offline.png` 并查看；中文表格原始列宽合计分别为 277、218 个 GUI 像素。离线预览未模拟物品栏背景和实际悬停位置，不是游戏截图；窄屏、英文自动换行和资源包字体的实际画面未启动客户端验收。本轮没有运行 GameTest、datagen 或游戏客户端。

最终包位于 `build/release/*0.4.4*.jar`，校验值 `checksums-0.4.4.json`；新 Tooltip 类、语言文件、64 个动画及 10 份 datagen 资源的打包核对见 `build/manual-tooltip-release-verify.log`。此次未改战斗规则、动画及刀定义。

## 可选的当前招式信息（0.4.3）

新增加载器原生 CLIENT 配置 `config/slashblade_neomotions-client.toml`，`[hud] showMoveInfo = false`。Forge 使用 ForgeConfigSpec，NeoForge 使用 ModConfigSpec；每位玩家独立选择。原服务端直接发送的蓄力／SA 动作栏提示已移除，由客户端读取同步的当前 Combo 状态统一生成。

开启后包含全部 32 个自有状态的中英文名称、动作阶段、进度、大剑蓄力等级和 SA 名称／精准窗口；进入 SA 预备时沿用之前的蓄力起点。本体后续特殊动作用通用名称显示，不暴露注册 ID。关闭、换刀、离开动作树、死亡、旁观、隐藏 GUI 或退出世界后清理本显示器的提示。默认关闭时不发送持续状态文字，音效、粒子及服务器战斗规则保持原有行为。

两版本 build 及现有各 **26 项 JUnit** 通过，日志 `build/move-info-build.log`。另校验中英文覆盖 manifest 全部 32 个自有状态。此次未新增镜像配置实现的低收益测试，也没有启动客户端或服务器 GameTest；实际 HUD 画面和运行中的配置重载未实机验收。源码与构建检查不能代替这些运行时验收。

0.4.3 安装包及 SHA-256 位于 `build/release/`；打包检查新配置／显示类、语言文件、版本元数据，以及全部既有动画和 datagen 资源。日志 `build/move-info-release-verify.log`。没有修改或重新生成动画、模型、配方及刀定义。

## 大剑持握与切削刃朝向（0.4.2）

定位到源武器局部基底与 OBJ 基底映射错了 90°：旧转换将源 +X 映射成宽刀面法线，而源碰撞窗口内的挥动主要沿 -X。三段蓄力斩命中时，刀刃样本运动与源 -X 的点积约为 0.95–1.00，表明源 X 是刃向所在的轴，不能用作宽面法线。修正为源 +Z→模型 -X（刀尖）、源 -X→模型 -Y（刃）、源 -Y→模型 +Z（宽面法线）。

重新生成 20 对大剑人物 JSON／武器 VMD，并在 manifest 保存基底；12 对双刀资源逐字节保持一致。斩击、蓄力、待机和空中状态共用正确的映射。命中时间、手臂握点、刀尖轨迹、全身翻转和第一人称缩放沿用原有数据与规则。

两节点各 **26 项 JUnit 通过，0 失败、0 错误、0 跳过**，含新增的实际 PMD／VMD 播放器刃向回归：逐个大剑武器命中点检查切削刃朝向挥动方向（点积 > 0.85），宽刀面法线避开挥动方向（绝对点积 < 0.4），覆盖普通／强／真蓄力与其他斩击。原有握持、背鞘、刀光及翻身回归继续通过。构建日志 `build/greatsword-edge-build.log`。

源数据核对仍通过 1564 个根骨／腿姿态，日志 `build/greatsword-edge-source-verify.log`。已查看重新生成的 `docs/images/wilds-greatsword-source.png` 离线预览。本轮没有启动游戏客户端或服务器 GameTest，未将离线检查称为实机验收。

安装包为 `build/release/*0.4.2*.jar`，校验值 `checksums-0.4.2.json`，打包核对日志 `build/greatsword-edge-release-verify.log`。配方、刀定义与模型几何没有修改，无需重跑 datagen。

## 大剑第一人称视野（0.4.1）

第一人称大剑在原动画挂点处缩小到 65%，并沿第一人称渲染坐标下移 0.18。使用 `RenderHandEvent` 限定本地玩家主手的第一人称绘制，再在本体 `RenderOverrideEvent` 中应用变换，避免本体重置手部矩阵后丢失偏移。普通、断刀、自发光和附魔闪光使用同一变换，逐次恢复矩阵，避免多遍绘制累积缩放。

第三人称尺寸、背鞘、双刀、物品图标及全部动作／命中数据保持不变。改动复用本体第一人称渲染器，不引入 Mixin。两版本构建及现有各 25 项 JUnit 通过；日志 `build/first-person-build.log`。未新增低收益的测试或启动游戏；实际视野舒适度未实机评估。

安装包为 `build/release/*0.4.1*.jar`，校验值为 `checksums-0.4.1.json`。打包核对见 `build/first-person-release-verify.log`。本次没有修改或重新生成动作、模型、配方和刀定义。

## 解包动作与事件重定向（0.4.0）

本轮替换旧版手工动作生成流程：从本机 Wilds PAK 读取大剑 wp00、双刀 wp02 的 MOTLIST 992／MOT 932 骨架和动作，解析内嵌 CLIP 85 碰撞、取消、预输入、霸体事件，以及 USER 3／RSZ 16 蓄力与过渡参数。提取器记录补丁来源和 SHA-256，RSZ 解析检查类型 CRC、引用和文件结尾。共生成 32 对 JSON／VMD 及运行时 `source_actions.json`。

`tools/verify_wilds_retarget.py` 对照原始二进制，核对 **1564 个根骨／腿部姿态**、源标识及片段编号。真蓄力片段 248 保留倒置和回正，原始两段判定为 33–43、100–113 帧；不是只给手臂补一个旋转。双刀逐个读取同一碰撞轨上的多次开关，落地片段无攻击事件，因此移除旧版额外命中。日志：`build/wilds-source-verify.log`。

两节点各 **25 项 JUnit 通过，0 失败、0 错误、0 跳过**，其中 12 项骨骼回归使用对应版本实际 Player Animator 和 SlashBlade PMD／VMD 播放器。覆盖每一招的握持与背鞘、四分之一 tick 的双刀挂点、髋／肩连接、真蓄力完整翻转、空中保持、衔接连续性、源事件量化和全部自有斩击的刀光平面及方向。蓄力斩两段刀光均按向下纵斩检查。

回归发现 Forge 所用 JOML 1.10.5 的 `getEulerAnglesZYX` 在 X 角分母中错误地加上 Y²，组合旋转会偏移。本模组改为归一化四元数后的正确转换，两版本执行同一实现；新增实际播放器的组合旋转插值回归。第三人称武器层也移除重复施加的玩家全身变换，改用实际手腕原点与源武器方向。VMD 用等效 120 Hz 采样保持快速双刀的亚 tick 握持。

两版本 build 成功，日志 `build/wilds-source-build.log`。已查看发布资源生成的离线预览 `docs/images/wilds-greatsword-source.png`、`wilds-dualblade-source.png`。**本轮未启动 Minecraft 客户端或服务器 GameTest**；碰撞后的步态、第一人称镜头、联机和实战手感尚未实机验收。旧版本以下实机记录不能视为 0.4.0 的验收。

最终安装包位于 `build/release/`，SHA-256 为 `checksums-0.4.0.json`。逐字节核对每端 64 个动画文件、事件 manifest、语言文件和 10 份 datagen JSON；确认新运行时类与版本元数据，排除测试类、测试结构、缓存、原始 MOT／USER／PAK。日志 `build/wilds-release-verify.log`。配方与刀定义未改动，没有重新启动 datagen。

动作时序来自提取的数据；完整 BTable 表体编译在原作程序中，当前未移植其完整状态机。按键映射、简化组合、SA、伤害平衡与实体移动限幅为 Minecraft 适配。已在 [出招表](MOVESET.md) 和 [动画管线](ANIMATIONS.md) 区分来源与适配，不再将手工动作或部分参数称为原作完整复刻。

## 刀光方向与动作状态（0.3.4）

大剑纵斩、三段蓄力斩（含真蓄力两击）、空中下劈改用向下的刀光，上捞斩向上，横扫保持水平。双刀逐击按刀／鞘的斜斩平面重建轨迹，回旋两击保持水平；肩撞和落地冲击单独核验。原生 SA 的动作及效果由拔刀剑本体提供。

腿部改为预备／蓄力保持／出刀／收招／衔接状态，乱舞各击共享支撑姿态；移除独立平移髋部和逐击交替前脚。状态切换仅混合腿部 4 tick，空中末姿保持到服务端落地。躯干前倾围绕髋部，肩点与头部同步移动，避免腰腿脱节。全部 30 对人物 JSON／武器 VMD 已同步生成。

两节点各 **25 项 JUnit 通过，0 失败、0 跳过**，包括 12 项骨骼回归。使用各版本真实 Player Animator 及本体 PMD/VMD 播放器检查握持、背鞘、髋部与肩点连接、腿部幅度及衔接连续性。斩击检查同时比较刀身实际运动平面、挥动方向与本体刀光渲染矩阵，覆盖每个自有挥刀命中；状态阶段与 Java 命中／衔接时间一致。空中保持测试包含超过动画时长才到达的同步。

构建命令为 `:1.20.1-forge:build :1.21.1-neoforge:build`，日志 `build/slash-state-build.log`。已检查更新后的两张离线预览 `docs/images/greatsword-rig-preview.png`、`dualblade-rig-preview.png`。本轮没有启动 Minecraft 客户端或服务器 GameTest；这些检查不等同于实战、多人同步或第一人称画面验收。

0.3.4 安装包位于 `build/release/`，SHA-256 见 `checksums-0.3.4.json`。逐字节核对每端 10 份 datagen JSON 和 60 个动画文件，检查新类与版本元数据，排除测试类、测试结构和缓存。配方／刀定义没有修改，继续使用已生成的数据，没有重复运行 datagen。当前完整操作见 [大剑与双刀出招表](MOVESET.md)。

## 腿部动作与测试范围（0.3.3）

已将减少不必要实机测试的约定写入根目录 `AGENTS.md`，后续开发默认使用针对性检查。此次只运行骨骼离线回归、两节点构建和离线预览，没有启动 Minecraft 客户端、服务器 GameTest 或批量截图。以下旧版本的实机记录保留各自版本号，不代表 0.3.3 再次执行。

`tools/lower_body.py` 为 28 个非待机状态增加随动作阶段变化的左右腿轨道，覆盖大剑蓄力／踏步／肩撞／两段真蓄力，双刀交替斩／乱舞／回旋／突进／后撤，以及两树的空中收腿和落地。待机不控制腿，原版走路动画可继续生效。全部 30 个 VMD 与修改前逐字节一致，人物上半身与战斗逻辑未改动。

两版本各 8 项 `WeaponRigTest` 通过，含原有 5 项握持／轨迹回归和新增 3 项腿部检查：28 个状态的两腿都产生变化，四分之一 tick 采样时支撑脚与地面的误差不超过 0.25 模型像素，真蓄力与双刀复合斩交替前脚，待机保留输入的原版腿部旋转与位置。检查使用各节点真实 Player Animator 解析与播放器，不依赖 GPU。

两节点 build 成功，日志 `build/lower-body-build.log`。离线预览为 `docs/images/greatsword-rig-preview.png`、`dualblade-rig-preview.png`，已检查；不是游戏截图。安装包位于 `build/release/`，版本 0.3.3，校验值为 `checksums-0.3.3.json`。本次没有更改配方或刀定义，无需重新运行 datagen。

## 握持、背鞘、轨迹和预输入（0.3.2）

先修复并验证大剑，再修复双刀。定位到原生成器误把躯干作为手臂父骨骼、遗漏原生渲染基准旋转，以及大剑未维持待机玩家动画。已重新生成全部 30 对 JSON/VMD；大剑双手约束到同一刀柄，双刀按各自手腕计算挂点，回旋通过 `body` 转动整个人。背鞘使用独立的 `StyleWeaponLayer`，覆盖普通刀套用大剑树、试作型及原生 SA。原生 SA 第三人称武器也跟随实际手腕。

两个版本各 17 项 JUnit 通过。新增的 5 项 `WeaponRigTest` 使用本体实际 PMD、VMD 与骨骼蒙皮播放器，对照真实 Player Animator 采样结果，覆盖双手握持、背部挂点、纵斩／横扫／上挑平面和双刀全身回旋。双刀还按四分之一 tick 检查插值时的握点保持在拳头范围内。

真实服务器新增 2 项大剑回归：通过物品原生 `use()` 在后摇中按住右键，窗口前保持当前斩击，窗口打开后自动进入下一段蓄力且计时从零开始；提前释放或复制栈换刀不能触发残留预输入。两端各 35 项测试全部通过，最终日志为 `build/rig-final-gametest.log`。

独立的 Forge、NeoForge 客户端分别完成 13 组实际游戏截图，位于 `build/rig-client-forge/screenshots/`、`build/rig-client-neoforge/screenshots/`。覆盖自动待机、背视图、蓄力、纵斩、横扫、普通太刀套用大剑树、原生 SA 时背鞘，以及双刀待机、左右斩击、乱舞与原生 SA。待机镜头直接换刀，使用生产代码的待机监听器；攻击镜头固定到指定动画 tick，检查实际游戏渲染。两个客户端实际构建的战斗栏和全局搜索列表也断言两把刀各出现一次。日志 `build/rig-client-final.log` 含两次 `RIG_QA_COMPLETE 13 screenshots` 和 `RIG_QA_CREATIVE_PASS`。

本次确认的是握持、方向、指定姿势的实际画面和服务器衔接规则；未把固定姿势截图等同于完整实战、第一人称遮挡或多人同步验收，也未宣称原作逐帧复刻。所有测试均使用 `build/` 下复制的专用世界，未操作用户正在运行的客户端或存档。

最终两端 clean build 通过，各 17 项 JUnit 无失败。正式包位于 `build/release/`，SHA-256 为 `checksums-0.3.2.json`。逐字节核对每端 10 份 datagen JSON 和 60 个动画文件；不包含 GameTest、客户端截图测试类、测试结构及缓存。构建日志为 `build/rig-release-build.log`。文档中保留的实际截图为 `docs/images/greatsword-ingame.png`、`greatsword-back-ingame.png` 和 `dualblade-ingame.png`。

## 创造栏填充修复（0.3.1）

0.3.0 只核验了刀定义的创造栏归属字段，未检查最终物品列表；本模组也未主动填充该命名空间下的刀。0.3.1 在模组事件总线上补充 `slashblade_neomotions` 命名刀的填充，将两把刀加入定义指定的分页及战斗栏。定义通过 Java datagen 重新生成，使用本体默认的 `slashblade:slashblade` 分页。现有本体不需要附属模组登记命名空间白名单，也不应重复注册 `slashblade:named_blades` 数据注册表。

先添加真实 `CreativeModeTab.buildContents` 回归测试，修改前 Forge 运行 32 项测试，其中新增项失败：战斗栏中大剑计数为 0。修改后两端各 33 项真实服务器 GameTest 全部通过，包括：

- 实际构建战斗栏与拔刀剑分页，检查两把刀在物品列表及分页的搜索条目中各出现一次；无管理员权限、有管理员权限、重新取消权限后均成立。
- 使用加载器原生 `ForgeHooks`／`EventHooks.onCreativeModeTabBuildContents` 派发事件，预先加入实际刀栈，分别覆盖父分页、仅搜索、两者可见三种状态。模组监听器补全可见性且不产生重复，兼容本体客户端先填充的情况。
- 原有刀定义、配方、存档、动作树及 SA 的测试继续通过。

日志：`build/creative-before-fix.log`、`build/creative-datagen.log`、`build/creative-tests.log`。这些检查实际执行原生创造栏生成和事件派发，不依赖 GPU；未将其描述为客户端画面验收。更新 JAR 或编译后，运行中的旧客户端需重启才能加载新的监听器。

两端 clean build 通过，JUnit 各 12 项、0 失败。正式 JAR 已逐字节核对各自的 10 个 datagen JSON，包含新创造栏监听器且不含 GameTest 类、测试结构或缓存。安装包位于 `build/release/`，校验值为 `checksums-0.3.1.json`；构建日志为 `build/creative-release-build.log`。

## 金属试作型与 datagen（0.3.0）

两节点各 12 项 JUnit 与 31 项真实服务器 GameTest 通过。新增验证：

- 用本体的 `WavefrontObject` 解析两套真实 OBJ，检查刀、鞘、断刀、碎片和背包组、有限坐标、UV、断刃长度与图标边界。NeoForge JUnit 使用主源码的 Minecraft 编译类路径，确保实际执行其 Vec3/JOML 法线计算。
- 读取实际 PNG 贴图并核验两把刀的中文名称完全匹配。
- 动态注册表加载 datagen 生成的两份刀定义，检查出厂动作、模型、纹理、创造栏归属字段、耐久和默认附魔。
- 真实本体配方匹配和 assemble，两种合成结果保留材料刀的击杀数、耀魂和精炼。
- 两种版本的真实 ItemStack 序列化往返，反复切换手册后恢复该试作型的原始动作树。
- 原厂大剑释放纵向剑气 SA，原厂双刀释放回旋斩 SA，均正常结算本体耀魂消耗。

Java datagen 的 `runData` 已在两个节点运行，每端生成 4 配方、4 解锁进度、2 刀定义。最终 clean → runData → build 通过，再生成的 20 个 JSON 与前一次 SHA-256 全部一致。正式 JAR 的刀定义／配方逐字节匹配对应 datagen 输出，包含完整模型和贴图，不包含测试类、测试结构或 datagen 缓存；安装包与校验值在 `build/release/`。已移除 `src/main/resources/data` 下手写的同名数据文件。Loom 的运行配置中需使用 `project.property("mod.id")`，避免误调用 RunConfig 的同名 setter；`--existing` 使用已处理的资源目录，避免把 `${packFormat}` 模板直接作为 JSON 读取。

验证日志：`build/prototypes-datagen.log`、`build/prototypes-tests.log`，最终构建记录为 `build/prototypes-final-build.log`。模型预览 `docs/images/prototype-models.png` 来自发布 OBJ 与贴图的离线渲染，已检查轮廓、金属分区、两种武器区分度和文字。

本次未完成世界内第一／第三人称视觉验收；握柄与玩家手的位置、巨大刀身遮挡和动画中的穿模仍需客户端确认。旧 0.2.0 客户端需重启才能加载新模型和刀定义。

## 双刀动作树（0.2.0）

两节点的 JUnit 各 10 项通过，真实服务器 GameTest 各 27 项通过（原有大剑 17 项 + 新增双刀 10 项）。本次资源覆盖 30 个 Player Animator JSON 与 30 个 VMD，包括双刀待机循环、刀和鞘两个独立挂点、运动帧和归一化旋转。测试使用对应版本的真实 Player Animator 解析器。

新增服务器覆盖：

1. 12 个 Combo 注册，实际手册使用，大剑／双刀反复切换后恢复原树。
2. 通过本体输入完成完整五段连招，攻击硬直不能切入 SA。
3. 两名玩家独立动作；乱舞六击的左右顺序、重复 tick 去重和一次耐久消耗。
4. 鞘斩生成真实 `EntitySlashEffect`，执行本体攻击后使测试目标扣血。
5. 突进和后撤的一次性移动推进。
6. 空中分支与只执行一次的落地转换。
7. 复制栈换刀、断刀、封印不能恢复旧攻击。
8. 原生右键进入 SA、精准释放和只扣一次耀魂。
9. 蓄力不足、超时及其他模组取消 SA 时不消耗资源。
10. 离地、换刀、封印、断刀或停止使用清除 SA 预备。

正式包通过两节点 clean build，随后核查了 30 对动画资源、双刀类、手册配方以及测试类／测试结构未进入发布包。最终 JUnit 结果为各 10 项、0 失败。正式包位于 `build/release/`，SHA-256 记录在 `checksums-0.2.0.json`。

0.2.0 的两版本 `runClient` 均完成资源加载，Minecraft 窗口正常响应；保留客户端供游戏内验收。日志为 `build/dual-runclient-forge.log`、`build/dual-runclient-neoforge.log`。Player Animator 2.x 仍会输出旧目录名兼容警告，资源已加载；未进行世界内画面验收。

服务器日志：`build/dual-gametest.log`。初次测试发现测试监听器的嵌套类同名触发 Forge 事件分发动态类冲突；改为唯一类名后，两端全量复测通过。动画解析测试发现循环标志还需要 `returnTick`，已修正并验证。

这些测试确认逻辑、原生伤害路径、资源格式及加载器兼容性。不同刀模型的握持位置、第一人称遮挡、多人观看姿态与手感仍待游戏内视觉验收。

## runClient 启动修复（0.1.0）

2026-10-21 补充验证：用户终端的 `JAVA_HOME` 为 Java 19，原配置在解析 Stonecutter 时失败；其要求至少 Java 21，而同项目的 ModStitch 0.8.5 实际要求 Java 25。现由 `gradle/gradle-daemon-jvm.properties` 自动选择 JDK 25，并将 Foojay resolver 从 0.8.0 更新至 1.0.0，解决旧 resolver 调用 Gradle 9 已移除的 `IBM_SEMERU` 字段的问题。

保持终端 Java 19 不变，分别执行两版本 `runClient`，均完成模组和客户端资源加载，Minecraft 窗口正常响应。Forge 游戏进程使用 Java 17，NeoForge 使用 Java 21。启动日志保存在 `build/runclient-forge.log` 与 `build/runclient-neoforge.log`。此项仅验证启动，不代表已验收进世界后的动作表现。

非致命日志仍包括本体的模型资源解析消息，以及 Player Animator 2.x 对旧 `player_animation` 目录名的兼容提示；它们未阻止本次启动。

## 大剑 SA 验证（0.1.1）

| 验证 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| 完整 clean build | 通过 | 通过 |
| 蓄力阈值、过蓄回落、连段窗口、输入分支单测 | 通过 | 通过 |
| 18 个 Player Animator JSON 的真实解析器读取 | 通过 | 通过 |
| 18 个 VMD 的结构与时间轴检查 | 通过 | 通过 |
| 真实专用服务器 GameTest | 17/17 通过 | 17/17 通过 |

GameTest 加载了对应版本真实的 SlashBlade 和 Player Animator，覆盖：

1. Combo 注册、装备与恢复原树、重复装备保留原树。
2. 本体输入分支与后摇限制。
3. 真蓄力两段攻击及同 tick 重复执行的去重。
4. 多玩家独立计时与满蓄倍率。
5. 换成另一把刀或复制栈后中止旧动作。
6. 肩撞减伤、抗击退与跳蓄力段。
7. 离地蓄力、断刀中止。
8. 空中下劈的落地转换与单次攻击。
9. 本体 `InputCommandEvent` 的左键按下沿在三段蓄力中进入 SA，重复输入不重置。
10. 继承蓄力时间，在精准窗口起点调用刀自身的精准 SA，重复释放仅结算一次。
11. 精准窗口结束时使用普通 SA。
12. 耀魂不足的本体耐久回退，以及创造模式免费释放。
13. 其他模组取消 SA 事件时不扣费，释放权限不会遗留。
14. SA 预备中的潜行肩撞保持原段数，并清除 SA 预备。
15. 离地、断刀、封印、换刀、停止使用及 54 tick 超时均不释放 SA。
16. SA 输入不能取消真蓄力的攻击硬直。
17. 本体附魔使用条件、多玩家独立选择，以及普通大剑出刀保持有效。

大剑攻击 GameTest 通过监听并取消本体 `DoSlashEvent` 记录攻击调用和倍率；SA GameTest 实际经过 `LivingEntity.releaseUsingItem()`、物品停止使用事件、原生 `ItemSlashBlade.releaseUsing()` 和 `PerformSlashArtEvent`，检查最终 Combo、耀魂、耐久与精准类型。它们验证服务端状态机和 SA 消耗，不验证剑气实体最终碰撞、伤害结算或画面。JUnit 的 6 项测试包含规则检查以及真实 Player Animator 解析器读取，不会启动客户端。

0.1.1 的本机结果可查看 `build/sa-final-build.log`、`build/sa-unit-tests.log`、各版本 `build/reports/tests/test/index.html`，以及两版本的最终复测日志 `build/sa-gametest-final.log`。

Wilds 的本地批量转换已完成 352 个片段；未逐个观看或标注动作内容。

## 待客户端验收

- 本地玩家和远端玩家的动画同步、握刀与鞘位置、第三人称左右视角、第一人称表现。
- 不同刀模型和玩家皮肤宽度下的双手姿态；走动、冲刺、跳跃与动作的混合。
- 对真实目标的攻击范围、实际伤害、无敌帧、耐久扣除与连段手感。
- 真蓄力的两次攻击是否符合预期命中位置；落地动作的时机。
- 联机延迟下的蓄力释放和肩撞；其他改动动画层或动作树的附属模组兼容性。
- SA 预备的姿态、颜色与精准窗口提示，取消后的动画清除，以及对本体 SA 动画的衔接。
- 重新加载世界后的每把刀动作树持久化与原树恢复。

这些项目尚未执行，因此当前版本称为玩法原型。没有将格式测试或服务器测试描述为完整实机验收。
