# Hug Me! — animation + command edition

**作者 / Author**：xuwenmeimei（NekoXuwen） · <https://github.com/XuwenMeimei>
**本仓库 / This repo**：<https://github.com/XuwenMeimei/HugMe>

Minecraft **1.21.1** / **NeoForge 21.1.243** 版本的 `HugMe` 重构版。
以**互动菜单 / 指令**触发拥抱动画，拥抱期间锁定移动并在准心右侧给出按键提示。

## 来源与致谢

本项目基于 **[TunYuntuwuQWQ/HugMe](https://github.com/TunYuntuwuQWQ/HugMe)**（作者 **TuYw**）重构而来，
原作以 MIT 协议发布。感谢原作者制作了抱抱券与两套拥抱关键帧动画——**本项目的动画数据
（`assets/hugme/player_animations/*.json`）完全沿用原作**，仅更新了 Player Animator 2.x 要求的目录名。

在上游基础上重做的部分：

- 交互从"抱抱券 + 聊天栏点击"改为**右键互动菜单 + 按键确认**
- 站位改为**按键瞬间精确落位**（可配置距离），并修掉了"走位随机误差导致偏松/穿模"
- 移动锁改为**客户端源头清零移动冲量**，服务端零传送
- 渲染不再接管原版管线（改 yaw 字段），盔甲/披风/手持物/名牌全部回归
- 拥抱期间隐藏参演者的阴影与 nametag，配合动画自带的身体位移

原作版权归原作者所有，本项目同样以 **MIT** 协议发布，见 [LICENSE](LICENSE)。

## 与上游版本的差异

| 上游 (1.0 – 1.3) | 本版本 |
| --- | --- |
| 抱抱券物品 + 合成配方 + 首次进服赠送 + 创造栏注册 | 已移除 |
| 右键玩家发起请求 → 聊天栏 `[同意]/[拒绝]` 点击事件 | 已移除，改为指令直接触发 |
| `ScheduledExecutorService` 60 秒请求超时线程 | 已移除（不再需要请求生命周期） |
| `HugCommandHandler` 一个 15 KB 巨类 | 拆分为 `HugManager` / `HugSession` / `HugCommand` / `HugAnimation` |
| 动画枚举硬编码 & switch 三分支复制粘贴 | 枚举自带动画资源、时长与本地化 key |
| 掉线 / 退出时附近客户端不会收到解锁包，姿势永久卡住 | 掉线、`/hugme stop`、超时、服务器停止都会广播解锁 |
| 同一玩家可被卷入多场拥抱互相拉扯 | 一人同时只允许一场拥抱 |
| 每 tick 把双方 `teleportTo` 回固定坐标，造成瞬移 / 拉回 | 死区式移动锁：偏离起始站位超过 0.35 格才拉回一次 |
| 被抱的一方被瞬移到发起者**身后**（穿过发起者） | **不瞬移任何人**：被申请者自己走到发起者正前方一格 |
| 发起者位置被强制固定 | **发起者位置完全不变**，也不被旋转（被抱者走到他正前方，他本来就面朝对方） |
| 点下同意立刻开始动画 | 走到位才**自动触发**动画 |
| 客户端无兜底，服务端崩溃则姿态永不解锁 | 客户端锁带过期 tick，最长 `动画时长 + 40 tick` 自动释放 |
| 渲染时无视隐身状态 | 隐身玩家不再被绘制 |

## 交互方式

**右键玩家（主手空手）** → 打开互动菜单 → 选一个动作 → 对方在 **2 格内按 [V]** 即可开始。

| 入口 / 出口 | 实现 |
| --- | --- |
| 右键菜单 | 纯客户端 `HugMenuScreen`，每个动画一个按钮；点击发 `hugme:hug_request` |
| 指令（保留） | `/hugme hug <目标> [动作]`，与菜单共用 `HugManager.requestAndReport`，提示完全一致 |
| 接受键 | `key.hugme.accept`，默认 **V**，可在按键设置里改；发 `hugme:hug_accept` |
| 悬浮提示 | 请求挂起期间，被申请者**在 2 格内**时**准心右侧**显示两行提示（`Dev 想和你拥抱（摸头拥抱）` / `[V] 接受`），带半透明深色底衬；超出范围自动隐藏 |
| 强制结束 | 拥抱期间**双击 Shift**，发 `hugme:hug_stop` |

悬浮提示由服务端推的 `hugme:hug_prompt` 驱动（只发给被申请者），请求过期 / 被取消 / 已接受时都会推一条
撤回包；客户端另外带一层自己的过期兜底。显示条件用的是与服务端 `accept()` **同一个**
`HugManager.withinAcceptRange`，所以"看到提示"和"按了有效"永远一致——距离或高度不满足时提示不会出现。

## 拥抱流程与定位

1. 发起（菜单或指令）只**挂起**请求：不传送任何人、不播放动画，双方各收到一条带按键提示的消息。
2. 被申请者走到发起者 **2 格以内**（`ACCEPT_RANGE`，同时要求高度差 ≤ 1.2 格）后**按接受键**触发。
   原来的"走进范围就自动开始"已移除——改成人工确认。
3. 触发瞬间服务端把**被申请者**放到精确站位上（发起者正前方 `front_distance` 格，见下节）。
   这一步是必要的：玩家一格一格走（约 0.28 格/tick），靠"走进范围"触发的话实际间距会在
   `[目标 − 0.28, 目标]` 之间浮动，也就是"普通拥抱看着正好、摸头拥抱时而偏松时而穿模"的根源。
   **申请者永远不被移动。**
4. 动画期间**锁定移动**，分两层：
   * **客户端**在 `Input.tick` 末尾把 `leftImpulse/forwardImpulse/jumping` 清零 ——
     `LocalPlayer.aiStep` 在下一 tick 开头读取这些值，所以客户端**根本不会产生位移**，
     也就不会把位置发给服务端。这是"主动移动导致位置偏移"的真正修复：
     位置的权威在客户端，服务端只能靠传送去追，而传送必然产生偏移与插值错位。
   * **服务端**只留一层死区兜底（外力、作弊客户端），正常游戏下永远不会触发传送。
5. 动画期间**只禁用移动键**（前后左右 + 跳跃）：键盘层用 mixin 拦 `KeyboardHandler#keyPress`，
   其余按键（背包、聊天、快捷栏、丢弃、F5…）与鼠标左右键全部照常可用；
   锁开始时释放所有已按下的键，避免键位停在"按下"状态。
6. 动画开始时若玩家处于第一人称，**自动切到第三人称**（`THIRD_PERSON_BACK`）以便看到拥抱；
   **动画结束后恢复拥抱前的人称**（只有被切换过才恢复，站位不做任何还原）。
7. 双击 Shift 强制结束会同时**清空动画图层**（`ModifierLayer#setAnimation(null)`），
   否则关键帧还在播、动作不会立刻停。
8. 请求挂起期间，发起者**移动超过 0.5 格会取消本次申请**（双方都会收到提示）。
9. 动画播完 / 任一方掉线 / 被外力拉开超过 3 格 → 立即结束并解锁。

请求 30 秒内没人按接受键会自动过期，双方都会收到提示；`/hugme stop` 可随时取消。

## 渲染 / 阴影 / nametag

拥抱朝向**不接管原版渲染**：`HugFacingHandler` 每 tick 把 `yBodyRot` / `yHeadRot` 写成朝向对方
（含 `O` 值以避免插值滞后），`yRot` 一律不碰，视角保持自由。数学上与上游那串手写变换等价：
`Rx(180)·T(0,-1.5,0)·Ry(yaw)` 等于原版 `Ry(180-bodyYaw)·scale(-1,-1,1)·T(0,-1.501,0)` 取 `bodyYaw = yaw`
（写死的 180° 翻转只是在抵消原版自己的 180° 偏移）。好处是盔甲、披风、鞘翅、手持物、受击闪白全部回来。

### 阴影与 nametag 为什么会错位

`normal_r.json` / `touch_r.json` 里，被抱者的 `torso` 带一条**位置**关键帧：

```
normal_r  tick=20  "torso": { "z": -1.0 }
touch_r   tick=20  "torso": { "z": -0.75 }
```

Player Animator 的 `AnimationApplier` 对 `"torso"` 有特殊分支，`BodyPart.getBodyOffset()`
把该值**原样返回（不除以 16）**，而这条路径的结果直接进 `poseStack.translate(...)`，单位是**块**。
也就是说作者**故意把被抱者整个身体前移约 1 格**，让两人的"身体"贴住
（实体间距 1.3 − 1.0 = 0.3），同时碰撞箱仍相隔 1.3 格、不会互相推挤。

而阴影和 nametag 都是 `EntityRenderDispatcher` 在渲染器返回之后**按实体坐标**绘制的
（`renderShadow` 用的是 `entity.xOld/getX()` 的插值值），所以**它们永远追不上被移走的模型**——
这条路在原版管线里根本不存在。与其压平这个刻意做紧的拥抱，不如在动画期间干脆不画这两样：

* **nametag**：`RenderNameTagEvent.setCanRender(TriState.FALSE)` → `HugOverlayHandler`
* **阴影**：让 `LivingEntityRenderer#getShadowRadius` 返回 0（`EntityRenderDispatcher` 只在
  `f > 0` 时才绘制）→ `LivingEntityRendererMixin`

因此**动画数据一个字节都不用改**，观感与原来完全一致。

### 站位距离：按动画配置 + 触发时落位

每个动画的 `torso` z 位移不同，所以"正前方"的距离**必须按动画分别设置**：

| 动画 | `torso` z 位移 | 默认站位距离 | 可见身体间距 |
| --- | --- | --- | --- |
| `normal_hug`（`normal_r`） | 1.0 | 1.3 | 0.3 |
| `touch_head_hug`（`touch_r`） | 0.75 | 1.15 | 0.4 |

默认值写在 `HugAnimation` 里，**实际取值来自配置文件** `config/hugme-common.toml`：

```toml
[front_distance]
    normal_hug = 1.3
    touch_head_hug = 1.15
align_on_start = true
```

调松紧直接改这里 + 重启服务端即可，不用重新编译。新增动画时照着它的 `torso` z 填 `bodyOffset`。

**为什么还需要"落位对齐"**：玩家是一格一格走过来的（约 0.28 格/tick），触发发生在"跨进阈值的那一 tick"，
所以实际间距会在 `[目标 − 0.28, 目标]` 之间浮动。正是这个浮动造成了"普通拥抱看着正好、
摸头拥抱时而偏松时而穿模"——固定距离救不了随机误差。`align_on_start` 打开时，触发瞬间会把
**被申请者**放到精确距离上（最大位移 0.28 格，**申请者永远不动**，可直接在配置里关掉）。
（仍未处理的边角：玩家身上着火时的火焰 `renderFlame` 与拴绳仍按实体坐标绘制，属罕见情况。）

> 阴影错位的根源是"每 tick 强制传送"：服务端持续传送会让客户端实体的插值坐标 `xOld` 与实际坐标
> 脱节，而 `EntityRenderDispatcher` 恰好用插值坐标绘制阴影（阴影是在渲染器返回**之后**单独画的，
> 不受我们取消原版渲染影响）。死区修正把传送次数从"每 tick 一次"降到"越界才一次"，错位随之消失。


## 指令

```
/hugme hug <target> [animation]   与目标玩家拥抱（省略动画则使用普通拥抱）
/hugme stop                       提前结束自己的拥抱
/hugme stop <target>              结束他人的拥抱（需要权限等级 2）
```

`animation` 可选值：`normal_hug`、`touch_head_hug`（TAB 可补全）。

直接触发版仍保留了原来的玩法约束：**同一维度**、**相距不超过 5 格**、**目标身前必须有可站立空间**。

## 多人测试

开发环境里服务端 / 客户端各自使用独立的运行目录，互不干扰：

| 任务 | 运行目录 | 用户名 |
| --- | --- | --- |
| `runClient` | `run/` | `Dev` |
| `runClient2` | `run2/` | `Dev2` |
| `runServer` | `run-server/` | — |

```bash
./gradlew runServer            # 终端 1：开发服务器（监听 25565）
./gradlew runClient            # 终端 2：Dev 客户端
./gradlew runClient2           # 终端 3：Dev2 客户端
```

两个客户端启动后：**多人游戏 → 直接连接 → `localhost`** 即可。

三个进程要一起开，嫌手动点太慢时可以让客户端启动后自动连入：

```bash
./gradlew runClient  -PquickPlay=localhost:25565
./gradlew runClient2 -PquickPlay=localhost:25565
```

> 注意：同一工程的多个 Gradle 运行任务**必须共用默认的 project cache 目录**。
> 如果给其中某个加了 `--project-cache-dir`，`createMinecraftArtifacts` 会重新执行并尝试删除
> `build/moddev/artifacts/neoforge-*.jar`，而该文件正被已运行的游戏进程占用（Windows 会直接报
> `Unable to delete file`）。
>
> 另外 `run-server/server.properties` 里已经配好 `online-mode=false`、`enforce-secure-profile=false`、
> 超平坦世界、`eula.txt` 也接受过了，开箱即用。


## 动画可见性

拥抱开始与结束时，模组会把网络包广播给发起者 64 格内的所有玩家，因此周围玩家都能看到拥抱动画。
结束包会额外发给「开始时可见但中途走远」的玩家，避免残留姿态。

## 依赖

* NeoForge `21.1.243`
* [Player Animator](https://github.com/KosmX/minecraftPlayerAnimator) `2.0.4+1.21.1`（客户端必需，模组 ID `playeranimator`）

## 构建与运行

```bash
./gradlew build          # 产出 build/libs/hugme-neoforge-1.21.1-1.4.0.jar
./gradlew runClient      # 开发环境启动客户端
./gradlew runServer      # 开发环境启动服务端
```

## 目录结构

```
src/main/java/nya/tuyw/hugme/
├─ HugMe.java                      模组入口
├─ hug/HugAnimation.java           动画枚举（动画资源、时长、本地化 key）
├─ hug/HugSession.java             单场拥抱的状态
├─ hug/HugManager.java             服务端：对齐、锁位、可见性广播、清理
├─ command/HugCommand.java         /hugme 指令注册
├─ network/HugPayload.java         唯一的网络包（开始 / 结束）
├─ network/HugClientHandler.java   客户端收包
└─ client/
   ├─ HugAnimationManager.java     Player Animator 图层与动画播放
   ├─ HugClientState.java          客户端姿态锁 + 兜底过期
   └─ PlayerLockRenderer.java      接管渲染以套用拥抱姿态

src/main/resources/assets/hugme/
├─ lang/{en_us,zh_cn}.json
└─ player_animation/{normal,touch}_{s,r}.json   关键帧动画数据
```

`PlayerLockRenderer` 里那段 `mulPose(Axis.XP.rotationDegrees(180)) + translate(0, -1.5, 0)` 与
`yawTowards` 的角度算法是从上游**逐字保留**的：它与动画作者在 BlockBench 中使用的朝向基准绑定，
改动会让拥抱动画明显错位。
