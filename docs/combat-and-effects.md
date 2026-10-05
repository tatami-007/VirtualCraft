# 战斗与世界特效公共接口

本轮使用者：蘑菇王、玩家防御反馈、无线光束。蛇王保留旧实现，等待重做，不改攻击编号或精准格挡规则。

## 分层

| 接口 | 职责 |
| --- | --- |
| `combat.AttackType` | HIGH / LOW / MID / ULTIMATE 决定防御方式，不决定近远程形状 |
| `combat.DefenseRules` | Boss 可配置跳跃窗口、格挡窗口、朝向、破盾冷却、失败减伤与耐久消耗 |
| `combat.DefenseState` | 纯逻辑、玩家级起跳和防御启动消费记录；跨装备、跨 Boss 共用 |
| `combat.CombatDefenseService` | 服务器输入确认、盾/虚拟剑适配、防御结算、成功消息、旧玩家数据迁移 |
| `combat.BossCombatant` | 声明攻击者自己结算防御，通用武器伤害事件不再重复弹反 |
| `combat.HitLedger<K>` / `AttackKey` | 每击去重；延迟攻击身份含战斗代次、动作序号、击序号和玩家 UUID |
| `combat.CombatTargeting` | 有效参战者和可配置水平扇形，远程锁定不能误套近战扇形 |
| `combat.AttackImpulse` / `CombatImpactService` | 抑制本次原版击退、一次配置冲击、抗击退减免和其他模组取消事件 |
| `client.CombatInputTracker` | 发送正常起跳意图，不发送“闪避成功”声明 |
| `network.CombatDefenseSuccessPayload` / `client.render.CombatDefenseEffects` | 通用服务器确认反馈：玩家残影、金屑、格挡火星和声音 |

标准防御仍是上段潜行、下段每击独立正常起跳（10 tick）、中段 0–14 tick 精准格挡，失败承受 40% 基础伤害并破盾 100 tick。配置不同窗口不会让已消费的输入被另一个 Boss 再次使用。

新 Boss 正常调用顺序：有效性与攻击范围/锁定 → `HitLedger.consume` → `CombatDefenseService.resolve` → 若未闪避，`CombatImpactService.hurt` → 伤害接受且目标仍有效时施加配置冲击与 Boss 自己的状态 → 关闭 `Resolution`。使用 try-with-resources，破盾耐久在伤害减免之后执行，关闭多次不会重复扣耐久。

专用伤害类型需要加入 `minecraft:bypasses_shield`，避免手动格挡后原版盾牌再结算；普通攻击不能使用绕过受伤无敌时间/护甲的类型。大招的锁定、死亡取消、存档防重复仍由 Boss 状态机负责。不要让动画回调决定伤害。

旧 `virtual_craft.mushroom_defense` 数据首次写入时迁移到 `virtual_craft.combat_defense`，不会通过升级洗掉破盾冷却。两个通用包保留旧 wire ID（`mushroom_jump_intent`、`mushroom_defense_success`）和字段布局；声音资源也保留旧 ID，避免不必要的网络/资源重命名。

## 泛光接入

`client.render.EffectBloom` 只收集显式注册的世界特效，不对场景截图做亮度提取，因此天空、白色地面、HUD 不会整体发光。

- 即时几何：原来的 `BufferUploader.drawWithShader(mesh)` 换成 `EffectBloom.drawWithBloom(mesh)`，同一次上传同时绘制本体和发光遮罩。
- 实体发光层：`EffectBloom.buffer(buffers, renderType)` 返回双写顶点消费者，原始外观不变。
- 物理攻击体的局部亮部：`EffectBloom.emissionBuffer(renderType)` 仅写泛光源，勿把整只普通实体当发光体。
- 自定义粒子可以将其消费者与 `emissionBuffer(EffectBloom.PARTICLE_EMISSION)` 合并；蛇王这轮未接。

玩家闪避残影明确排除在泛光之外，使用原始金色着色器直接绘制，不写入发光遮罩；金屑和格挡火星仍接入泛光。

遮罩使用场景深度，按 1/2、1/4、1/8 分辨率生成三层 RGBA16F 泛光中间缓冲，在世界渲染结束叠加近光晕、中距离柔光、宽范围淡光晕。浮点中间值保留亮部能量，合成时按共同亮度上限压缩，避免金色各通道分别饱和成白色。没有发光源的帧不做后处理。

性能：三次下采样代替四次分离模糊；模糊目标总像素由 0.625 屏减少为约 0.328 屏（不是 FPS 提升百分比）。复用全屏网格与 CPU 顶点缓冲池，最多缓存 32 个特效材质批次，闲置 120 帧回收；退出世界释放全部缓存。残影不再为泛光重复绘制；金屑顶点使用标量计算，不再逐粒子构造向量和顶点/UV 数组。跳过后方及不可见透明度的金屑，玩家有效性每 tick 每玩家仅查询一次。没有延迟实体批次时不重复拷贝场景深度。

支持尺寸变化和资源重载。无需外装光影包；这不是完整的场景光影系统，不向环境方块注入实际光照或生成动态阴影。第三方光影包/渲染替换兼容性和实际战斗帧耗时仍需游戏验证。

当前缩小闪避金屑为上一版的 68%，人物金色残影形状和贴图不变。缠足为 5/5/6/8 tick，混合根刺 6 tick，大招 8 tick；逆行孢幻保留 60% 主动移动，与滞步并存时取更慢者；原滞步 15%/5% 和偏向孢幻保持不变。
