# 动画与招式数据管线 · 0.5.0

本版生产动画由本地解包数据重定向生成。旧版的手工挥刀、腿部相位函数不再生成发布资源。三把金属试作型使用本项目原创 OBJ 和贴图。

斩斧的 19 个状态、两个树根、独立变形部件和双槽机制见 [斩斧迁移说明](SWITCHAXE.md)。`--weapon sa` 可只重建斩斧；省略该参数重建全部 51 对动画。

## 已接入的数据

| 数据 | 用途 |
| --- | --- |
| `wp00_00.motlist.992` / MOT 932 | 大剑骨架、位置、旋转、缩放和根运动；352 个可用片段 |
| `wp02_00.motlist.992` / MOT 932 | 双刀骨架和动作；358 个可用片段（wp03 是太刀，不用于双刀） |
| `wp08_00.motlist.992` / `wpg08_00.motlist.992` | 斩斧人物及独立 Grip／Axe／Sword 变形骨骼 |
| `wp08globalactionparam` / `actionguidedata_wp08` | 双槽参数、原作招式指引转换与输入 |
| MOT 内嵌 CLIP 85 | 武器碰撞开关、攻击编号、预输入、取消窗口、肩撞霸体范围 |
| `wp00globalactionparam.user.3` | 四个蓄力等级、过蓄阈值及肩撞伤害系数 |
| `wp00_00_mct` / `wp02_00_mct` | 动作插值帧数与源连接记录 |
| actionid / actionparam / BTable USER | 动作声明、GUID、命令参数和来源核对 |

完整来源路径、SHA-256、片段编号、逐 tick 源帧、命中区间、采样接触点、取消字段保存在 `src/main/resources/assets/slashblade_neomotions/motions/source_actions.json`。每次提取另在 `.local/wilds/*/extraction-manifest.json` 记录具体来源 PAK，补丁优先。

真蓄力使用片段 **248**，保留片段中的第一击、全身翻转、第二击及收势。原始武器判定区间为 **33–43、100–113 帧（60 fps）**。普通／强蓄力分别来自 213／237。双刀读取每条 `_On` 轨的全部开关，不把同一轨上的后续击打丢掉；700 系列附加判定不重复当作新挥刀。大剑不同蓄力等级的同一挥刀只生成一次命中。

## Minecraft 修改

- 骨架世界变换同时解码位置、旋转、缩放；保留髋部整体转动，腿取原作大腿到脚的方向。没有另加跑步正弦或固定 18° 腿部模板。
- Minecraft 没有原作肘、膝、手指。使用刚性肢体重定向；大剑保持原刀刃方向，把两只手投影到可达握柄位置。肩、颈、髋保持连接，源脚部高度用于调节全身高度。
- `body` 是渲染前的方块单位变换，肢体是像素单位。转换处理原版模型反射、0.9375 玩家缩放及 0.7 的根轴心，预览器也执行相同变换。
- 第三人称武器层继承已有全身变换，不再叠加一次 `setUserPose`。刀身及双刀副武器使用实际手腕原点与源武器方向；大剑鞘固定在躯干背部。
- 大剑武器局部基底在 0.4.2 校正：源 `R_Wep` 的 +Z 为刀尖、-X 为切削刃，映射到 OBJ 的 -X 刀尖、-Y 切削刃；OBJ 的 +Z 宽面法线对应源 -Y。旧映射错误地把源 +X 当作宽面法线，形成约 90° 的扭转，导致宽刀面拍向目标。基底记录在 manifest 的 `greatswordWeaponBasis`。回归同时检查刀尖、刃向和刀面，而非仅检查运动平面。
- 0.4.1 的第一人称大剑额外缩小到 65%，在第一人称渲染坐标中下移 0.18，减轻视野遮挡。该变换在原动画挂点之后应用，不改源动作和第三人称模型。
- 0.4.5 将相同的第一人称调整应用于双刀右手刀身和左手刀鞘；普通、自发光与附魔闪光使用同一变换，每个绘制批次恢复矩阵，避免叠加缩放。
- 0.4.6 自有动作的第一人称改为直接采样同一个 Player Animator 动画栈；双刀右手使用 `rightArm/rightWeapon`、左手使用 `leftArm/leftWeapon`。与第三人称共用 `WeaponPose.hand`，身体变换严格位于原版 X/Y 反射及玩家缩放之前，避免双刀回旋方向和轴心不同。相机只加固定取景及避让变换，不随全身翻滚；渲染不推进另一套时钟，也不读取可能过期的 PlayerRenderer 模型。原生 SA 与动画尚未就绪时保留本体渲染回退。
- Player Animator 每 tick 存姿态，运行时使用四元数插值。VMD 时间轴放大 4 倍、Combo 播放速度同步为 4，得到等效 120 Hz 挂点采样；实际动作时长不变。
- 四元数转 ZYX 欧拉角在两节点使用相同的显式公式，避开 Forge 的 JOML 1.10.5 中 X 角分母 Y² 符号错误，保证空中斩等组合旋转的方向不变。
- 刀光使用重定向后两帧刀刃的有向斩击平面，分别导出 roll 和 pitch。单一 roll 无法表达原作全部三维斩击。原始方向值也留在 manifest 中。
- 源碰撞是连续窗口，拔刀剑使用单次脉冲：在窗口内选择向前运动较强的刀刃样本，并量化到仍处于窗口内的游戏 tick。这个选点是适配规则，原始起止帧保留。
- 原作水平根轨迹缩放为 0.5 倍后通过服务端速度实施，单 tick 上限 0.45 方块；沿用实体碰撞，不直接传送。起跳高度由全身动画表达，空中／落地分支仍由 Minecraft 的着地状态控制。
- 蓄力参数来自 USER：0／0.8／1.55／2.3 秒，3 秒过蓄回到 Lv.2。普通斩到强蓄力的预输入／取消来自 `_Pre_STORNG_CHARGE=32`、`_STORNG_CHARGE=48` 帧。肩撞 `_HyperArmor` 为 0–40 帧，伤害系数为 0.3。

**范围说明：**这不是将原作整个程序状态机搬入 Minecraft。已读取的 BTable 使用 `_UseExportArgData=true`，表体已经编译进可执行文件，USER 中保存的是命令参数。没有把这些参数称为完整转换图。当前招式时序、蓄力、碰撞窗口、取消窗口和过渡取自数据；选招键位、双刀简化连段、SA、伤害倍率及实体移动限幅属于本模组适配。没有实现完整鬼人／鬼人强化、耐力和集中模式。

## 重建

本机原始游戏位置：`F:/SteamLibrary/steamapps/common/MonsterHunterWilds`。只读取，不修改安装目录。

```powershell
python tools/extract_wilds.py --game 'F:/SteamLibrary/steamapps/common/MonsterHunterWilds' --paths tools/wilds-combat-paths.txt --output .local/wilds/combat
python tools/extract_wilds.py --game 'F:/SteamLibrary/steamapps/common/MonsterHunterWilds' --paths tools/wilds-dualblade-paths.txt --output .local/wilds/dualblade
python tools/extract_wilds.py --game 'F:/SteamLibrary/steamapps/common/MonsterHunterWilds' --paths tools/wilds-motion-events-paths.txt --output .local/wilds/events
python tools/extract_wilds.py --game F:/SteamLibrary/steamapps/common/MonsterHunterWilds --paths tools/wilds-switchaxe-paths.txt --output .local/wilds/switchaxe
python tools/read_wilds_rsz.py .local/wilds/combat .local/wilds/dualblade .local/wilds/events .local/wilds/switchaxe
python tools/retarget_source_actions.py
python tools/verify_wilds_retarget.py
```

输入已存在时只执行最后两步。旧生成器入口也会转入该生产转换器。`retarget_wilds.py` 的独立入口保留为早期实验工具，其默认输出在 `.local`；不要用于生产资源。

解析参考：[RE-Asset-Library](https://github.com/NSACloud/RE-Asset-Library)、[RE-Noesis MOT 格式](https://github.com/AzurieWolf/fmt_RE_MESH-Noesis-Plugin-AzurieWolf)、[REasy RSZ/CLIP schema](https://github.com/seifhassine/REasy)。RSZ schema 位于 `.local/tools/REasy/resources/data/dumps/rszmhwilds.json`；解析器检查类型 CRC、对象引用和文件结尾，版本不匹配会报错。第三方工具保留各自许可证，不打入模组。

## 原作招式名称（0.4.4）

手册表格和招式 HUD 的名称读取本机原作猎人笔记 MSG.23 的简中／英文文本，选定条目、GUID 与文件 SHA-256 保存在 [move-name-sources.json](move-name-sources.json)。这是当前片段使用的名称，不是将完整原作的键位及鬼人模式搬入本模组。SA 为拔刀剑技能，单独标识。

```powershell
python tools/extract_wilds.py --game 'F:/SteamLibrary/steamapps/common/MonsterHunterWilds' --paths tools/wilds-move-names-paths.txt --output .local/wilds/move-names
python tools/read_wilds_move_names.py
```

MSG 格式参考 [REMSG_Converter](https://github.com/dtlnor/REMSG_Converter)；工具源码和 MIT 许可证留在 `.local/tools/REMSG_Converter/`。本项目只读取版本 23，检查条目名哈希和字符串边界，不修改原游戏文本。

## 动画验证

离线来源检查对照本地 MOT 骨架逐帧比较导出根旋转和腿方向，并验证真蓄力的倒置与回正。JUnit 使用两个版本真实 Player Animator 和本体 PMD/VMD 播放器检查握持、背鞘、刀光平面、时序和资源结构。预览读取实际发布资源，不是游戏截图。按 AGENTS.md，本轮不启动客户端或完整服务器测试；运行时碰撞、镜头及联机最终观感尚未实机验收。
