# V4 设计 Tokens（实现基准）

来源：`v4-re-conclusions.md`（截图像素采样 + ABDL 品牌色）。基调决策：**酷安暗色骨架 + 保留液态玻璃** + ABDL 婴儿蓝品牌强调。
深色优先；浅色主题从 furryfurry 参考适配（白底/浅灰分层/同强调色）。所有值为本文件最终拍板，实现处不得散落魔法色值。

## 1. 颜色（Compose `V4Colors`，深色主题）

| Token | 值 | 用途 |
|---|---|---|
| `pageBg` | `#101010` | 页面底色（酷安 page） |
| `surface` | `#1C1C1E` | 卡片/面板面 |
| `surfaceHigh` | `#2C2C2E` | 输入框、胶囊内芯、悬浮元素 |
| `surfaceGlass` | `#E6141414`（14% 黑 on blur） | 液态玻璃底栏/顶栏基底（blur 之上） |
| `textPrimary` | `#E8E8E8` | 正文与标题（取酷安/贴吧折中，保证 4.5:1） |
| `textEmphasis` | `#FFFFFF` | 用户名、强数字 |
| `textSecondary` | `#8E8E8E` | 次级说明、时间戳 |
| `textTertiary` | `#5A5A5C` | 禁用、占位 |
| `divider` | `#232325` | 细分割线（1px） |
| `accent` | `#64BFF2` | 品牌婴儿蓝（激活态、链接、主按钮） |
| `accentDeep` | `#128E83` 系改蓝 `#2E86C9` | 激活文字在浅背景上的可读变体 |
| `accentContainer` | `#1A2A36` | 强调色淡底（选中背景） |
| `accentSecondary` | `#FFD98E` | 赞助者/尊贵元素（奶油黄） |
| `danger` | `#F04030` | 删除/举报（酷安红） |
| `success` | `#30A063` | 在线/成功 |
| `warning` | `#F0B010` | 敏感提示 |
| `navActivePill` | `#242426` | 底栏激活页签胶囊 |

浅色主题：`pageBg #F6F6F6`、`surface #FFFFFF`、`surfaceHigh #F0F0F0`、`textPrimary #1A1A1A`、`textSecondary #8A8A8A`、`accent` 同值、`divider #E8E8E8`。

## 2. 几何（`V4Dimens`）

| Token | 值 | 说明 |
|---|---|---|
| `pagePaddingH` | 10dp | 页面左右留白 |
| `cardGap` | 10dp | 卡片纵向间距 |
| `cardPadding` | 14dp | 卡片内边距 |
| `cardRadius` | 14dp | 卡片圆角 |
| `chipRadius` | 8dp | 小标签/输入块圆角 |
| `stadium` | 50% | 搜索框、胶囊（全圆角） |
| `navBarHeight` | 60dp | 底栏胶囊高（含 icon 24dp + label 10sp） |
| `navBarMarginH` | 8dp | 底栏左右留白 |
| `navBarBottomPad` | 8dp+nav inset | 底栏距屏底 |
| `fabSize` | 56dp | 中央发布圆钮（略高于胶囊 4dp 视觉突出） |
| `tabBarHeight` | 40dp | 顶栏页签行 |
| `avatarFeed` | 42dp | 信息流头像 |
| `avatarNav` | 30dp | 底栏「我的」头像位 |

## 3. 字体（`V4Typography`，系统字体，est. 采样）

| Token | 值 | 用途 |
|---|---|---|
| `title` | 17sp / SemiBold | 卡片标题、页标题 |
| `body` | 15sp / Regular | 正文（信息流正文 16sp 可读性更好，取 15.5sp→用 16sp） |
| `bodySecondary` | 13sp / Regular | 次级信息 |
| `caption` | 11sp / Regular | 时间戳、计数 |
| `tabLabel` | 10sp / Medium | 底栏标签 |

行高 1.4×；正文最多 6 行折叠 + 「展开」。

## 4. 动效与玻璃（液态玻璃落点）

- **启用液态玻璃（API 33+）**：底栏胶囊、顶栏、底部 sheet 把手、发布键展开折扇背景、图片查看器顶部条。
- **降级（API < 33 / 低 RAM）**：同 Token 用 `#F2141414`（90% 不透明）纯色 + 1dp `divider` 描边，不跑 AGSL。
- 动效规范：页签切换内容淡入 120ms；卡片按压 scale 0.98；FAB 折扇展开 spring（沿用旧仓 ComposeFanMenu 参数：60ms stagger）。
- 触控目标 ≥48dp；正文对比 ≥4.5:1（继承 UX 计划无障碍硬门槛）。

## 5. 组件映射

| 组件 | 规格 |
|---|---|
| 信息流卡片 | surface 面、cardRadius、头像 42dp 左上、昵称 14sp + 徽章、正文 16sp、九宫格媒体（3 列、4dp gap、圆角 8dp）、底部操作行（转发/评论/赞，icon 20dp + caption） |
| 底栏 | 悬浮玻璃胶囊：首页/发现/＋发布/消息/我的；激活页签 navActivePill 胶囊 + accent 图标；未激活 textSecondary |
| 发布键 | accent 实心圆 56dp、白色 ＋；点按直接进发帖，长按/上滑展开折扇（发帖/交友申请/扩列卡/相册/小说/扫码） |
| 「我的」页 | 酷安式：头部（头像+名+等级）→ 三宫格统计（动态/关注/粉丝）→ 功能宫格（我的收藏/浏览历史/我的相册/宝宝认证/赞助中心/设置…分组「基础功能/社区服务」）→ 卡片流（我的卡片：扩列卡/话题） |
| 发现页 | 顶部搜索条 + 宫格入口（交友地图/扩列卡/纸尿裤/小说/NBW/扫一扫/省份时间线/话题）+ 下方推荐内容流 |
| 消息页 | 顶部页签「私信/通知/互动」；会话行（头像+名+摘要+时间+未读角标） |
