# TinyFish · 自定义钓鱼

> 钓到的鱼有品种、有克重、有价格；把鱼投入收集箱能换永久属性。
> **单个 jar 支持 1.13 ~ 26.x**，零依赖，没装任何前置插件也能跑。

---

## 特性

| 功能 | 说明 |
| --- | --- |
| 🎣 **按权重抽鱼种** | 权重总和不必等于 100，会自动归一化 |
| ⚖️ **克重系统** | 每种鱼有自己的克重区间，克重决定售价与属性 |
| 💰 **出售换钱** | 售价 = 基础价 + 每克价 × 克重 |
| 📦 **虚拟鱼箱** | 鱼获直接进服务端仓库，不占背包、**无法伪造** |
| 🏆 **鱼获收集箱** | 每种鱼投入一条，按克重换取永久属性 |
| 📊 **排行榜** | 单条最大克重 / 累计克重两种口径 |
| 🤖 **自动钓鱼** | 水边持竿即可挂机，有失败率与超时保护 |
| ⚡ **钓鱼加成** | 公式 + 鱼竿 lore 双来源，可加速咬钩、增加鱼获 |
| 🎨 **图形界面** | 主菜单 / 鱼箱 / 收集箱 / 排行榜四页 |

---

## 兼容性

| 项 | 值 |
| --- | --- |
| 服务端 | Paper / Purpur / Spigot（推荐 Paper） |
| 版本范围 | **1.13 ~ 26.x** |
| 插件字节码 | **Java 8** |
| 需要的服务端 JVM | 服务端本身的 Java 要求决定下限：<br>1.13–1.16.5 需 Java 8/11 · 1.17 需 16 · 1.18–1.20.4 需 17 · 1.20.5–1.21.11 需 21 · **26.x 需 25** |
| 前置依赖 | **无**（以下全部可选） |

### 可选软依赖

| 插件 | 装了有什么用 | 不装会怎样 |
| --- | --- | --- |
| **AttributePlus** | 收集箱属性真的加到玩家身上 | 属性只显示在界面里，不生效 |
| **NeigeItems** | 用自定义物品做鱼获外观 | 用原版鱼材质 |
| **Vault** / 自家 Economy | 用服务器统一货币 | 用插件内置货币 |
| **PlaceholderAPI** | 公式里可用 `%变量%` | 公式只能写数字和四则运算 |

**以上任何一个没装，插件都能正常启动，只是对应功能降级。**

---

## 安装

1. 把 `TinyFish-x.x.x.jar` 放进服务器 `plugins/`
2. 重启服务器
3. 看控制台，应该出现：

```
[TinyFish] TinyFish v2.0.0 —— 自定义钓鱼
[TinyFish]  支持范围: 1.13 ~ 26.x（Java 8 字节码, api-version 1.13）
[TinyFish]  投递模式: virtual（鱼获进虚拟鱼箱，不可交易）
[TinyFish]  鱼种数量: 5
[TinyFish]  属性输出: auto → 实际 lore | 每属性上限: 50.0
[TinyFish]  经济来源: 内置
[TinyFish]  自动钓鱼: 已启用 | 咬钩加速: timeUntilBite
```

4. 进游戏敲 `/tf gui`

---

## 指令

主命令 `/tf`（别名 `/tinyfish`、`/钓鱼`）

| 指令 | 作用 | 权限 |
| --- | --- | --- |
| `/tf gui` | 打开图形界面（推荐） | tinyfish.use |
| `/tf auto` | 开关自动钓鱼 | tinyfish.auto |
| `/tf box` | 文本查看鱼箱 | tinyfish.use |
| `/tf sell` | 卖出全部鱼获 | tinyfish.sell |
| `/tf collect` | 文本查看收集箱与属性 | tinyfish.collect |
| `/tf deposit <鱼种>` | 投入收集箱 | tinyfish.collect |
| `/tf withdraw <鱼种>` | 从收集箱取回 | tinyfish.collect |
| `/tf info` | 当前配置概览 | tinyfish.use |
| `/tf reload` | 重载配置 | tinyfish.admin |
| `/tf give <鱼种> [克重]` | 发一条鱼（测试用） | tinyfish.admin |

---

## 配置

### 一、鱼获怎么交付（最重要的一个选项）

```yaml
fishing:
  delivery: virtual    # virtual（默认）| item
```

| 模式 | 鱼去哪 | 能交易吗 | 会不会被伪造 |
| --- | --- | --- | --- |
| `virtual` | 服务端虚拟鱼箱 | ❌ | ❌ 不可能（物品不存在） |
| `item` | 生成真实物品进背包 | ✅ | 带 HMAC 签名，改数据就失效 |

**怎么选**：
- 服里没有物品交易需求 → 用 `virtual`（默认，最省心）
- 想开鱼市、玩家之间能买卖 → 用 `item`

两种模式**可以随时切换，已有数据不会丢**：
- `virtual → item`：老鱼留在鱼箱里，玩家在鱼箱界面点「取出」才变成物品
- `item → virtual`：背包里的老鱼物品仍然有效，可以主动存入

### 二、鱼种定义（fish.yml）

```yaml
fish:
  tuna:
    display-name: "&b金枪鱼"
    chance: 8              # 权重，不必凑 100
    min-weight: 2000
    max-weight: 8000
    base-price: 10.0
    price-per-gram: 0.020  # 售价 = 10 + 0.02 × 克重
    fallback-material: "SALMON"
    ni-item-id: "fish_tuna"     # 装了 NeigeItems 才用到
    lore:
      - "&7放置到收集箱可获得属性"
    collect-attribute:
      - "攻击力+6"              # 实际生效 = 6 × 克重档位系数
```

### 三、属性为什么不是「单克 × 克重」

老版本是线性乘：一条 8000g 金枪鱼给 +48000 攻击力，直接把数值打穿。
现在按克重分档：

```yaml
attributes:
  max-total: 50        # 每种属性的总上限，0 = 不限制
  tiers:
    - { max-weight: 499,        factor: 0.2 }
    - { max-weight: 1499,       factor: 0.4 }
    - { max-weight: 2999,       factor: 0.7 }
    - { max-weight: 5999,       factor: 1.0 }
    - { max-weight: 2147483647, factor: 1.5 }
```

同一条 8000g 金枪鱼 = `6 × 1.5` = **+9**。

> 最后一档的 `max-weight` 必须足够大（兜住所有更大的值），否则大鱼的系数会算错。

### 四、货币

```yaml
economy:
  provider: auto      # auto | internal | own | vault
  currency-name: "金币"
```

`auto` 的探测顺序：自家 Economy → Vault → 内置。

---

## 在线更新检查

启动后 5 秒会异步查一次有没有新版本（BumpAPI）。

```yaml
update-check:
  enabled: true
  endpoint: "https://bump.tinyaii.cn"
  app-key: "app_dcb01c804910adade6c5812d"   # 公开标识，不是密钥
  channel: "stable"
  timeout-seconds: 3
  interval-hours: 6
  notify-permission: "tinyfish.admin"
```

**三条保证**：

1. **只提示，不下载、不替换 jar** —— 换 jar 必须你自己来
2. **失败静默** —— 服务器连不上外网时最多等 3 秒（在异步线程里），不会影响启动，也不会刷屏报错
3. **完全可关** —— `enabled: false` 就彻底不联网

有更新时，控制台会打印版本号和更新说明；有 `tinyfish.admin` 权限的玩家上线时会收到一条提示。

> `app-key` 是应用的公开标识（客户端本来就要带它查询），**不是密钥**，
> 所以它可以放心出现在 jar 和配置里。真正的 `bp_` API 密钥只用于发布，不会进插件。

---

## 常见问题

**Q：`/tf gui` 没反应 / 界面是空的**
A：看控制台有没有报错。另外确认你用的是 1.13+ 且没有其它插件占用同一个界面。

**Q：收集箱里没变成属性**
A：先看 `/tf info` 里的「属性输出」。如果是 `lore`，说明没装 AttributePlus —— 属性只显示不生效。装上 AttributePlus 后重启即可（`auto` 会自动识别）。

**Q：卖鱼的钱没到账**
A：看 `/tf info` 的「经济来源」。显示 `内置` 说明用的是插件自己的钱包，钱在 `plugins/TinyFish/data.yml` 里，不会进 Vault。想接入服务器货币就把 `economy.provider` 设成 `vault`。

**Q：物品模式下玩家改了鱼的价格能卖更多吗**
A：不能。每个鱼获物品的 lore 末行有 HMAC 签名，克重/售价/鱼种任何一项被改都会验签失败，物品会被当成普通物品（不能卖、不能进收集箱）。

**Q：`security.secret` 是什么**
A：物品签名密钥，首次启动自动生成。**视同后台密码，不要外泄**。改掉它会让服务器上所有已有的鱼物品失效。

**Q：自动钓鱼为什么不动**
A：检查三件事：主手是否拿着鱼竿、附近 3 格内有没有水、`auto-fishing.enabled` 是否为 true。三个条件任一不满足就不会抛竿（这是防误触设计）。

**Q：能不能和别的钓鱼插件一起用**
A：不建议。本插件会接管 `PlayerFishEvent` 的钓到鱼事件（删掉原版掉落换成自定义鱼获），两个钓鱼插件同时开会互相抢。

---

## 数据文件

| 文件 | 内容 |
| --- | --- |
| `config.yml` | 主配置 |
| `fish.yml` | 鱼种定义 |
| `data.yml` | 玩家余额、鱼箱、收集箱、最佳记录 |

`data.yml` 每 60 秒自动落盘一次，关服时强制保存。

---

## 编译

```bash
mvn clean package
```

依赖 `spigot-api 1.13-R0.1-SNAPSHOT`（provided），编译目标 Java 8。
产物在 `target/TinyFish-x.x.x.jar`。

---

## 作者

**TinyAII**
