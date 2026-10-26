# V4 设计 Tokens

视觉方向：酷安卡片流、悬浮胶囊导航与 ABDL 婴儿蓝品牌，保留局部液态玻璃。参考像素记录见 `v4-re-conclusions.md`；以下是 V4 自己的实现值，不宣称从竞品源码复原。几何为设计选择，流畅度必须用 release 基准验证。

## 颜色

| 语义 | 深色 | 浅色 |
|---|---|---|
| 页面背景 | `#101010` | `#F6F6F6` |
| 卡片面 | `#1C1C1E` | `#FFFFFF` |
| 浮起面 | `#2C2C2E` | `#F0F0F0` |
| 主文字 | `#E8E8E8` | `#1A1A1A` |
| 次级文字 | `#969696` | `#676767` |
| 分割线 | `#232325` | `#E8E8E8` |
| 激活页签胶囊 | `#242426` | `#DCEEFB` |
| 链接、激活文字（Material primary） | `#64BFF2` | `#1B6A9A` |
| onPrimary | `#0A141C` | `#FFFFFF` |
| primaryContainer | `#1A2A36` | `#DCEEFB` |
| 玻璃 tint（ARGB，90.2% 不透明） | `#E6141414` | `#E6FFFFFF` |
| 纯色降级（ARGB，100% 不透明） | `#FF141414` | `#FFFFFFFF` |

品牌色 `Accent #64BFF2` 在两主题中保持一致，发布键固定使用 `Accent` 和深色 `OnAccent #0A141C`，不直接采用浅色主题已加深的 Material primary。辅助赞助色 `#FFD98E`，危险色 `#F04030`，成功色 `#30A063`，警示色 `#F0B010`。

### 无障碍约束

正文、次级文字、链接都要在实际所在表面上达到 4.5:1。大图标至少 3:1。浅色 Accent 原色在白底对比不足，故文字使用独立深蓝；白色发布图标在婴儿蓝上也不达标，必须使用 OnAccent。次级灰不仅检查普通 surface，还检查浮起面与透明 tint 叠极端底色后的最差情况。颜色改动需通过真实语义色组合测试，不靠删断言过门禁。

## 几何

| Token | 值 |
|---|---|
| pagePaddingH / cardGap | 10dp / 10dp |
| cardPadding / cardRadius | 14dp / 14dp |
| chipRadius | 8dp |
| navBarHeight | 最小 60dp，字号增大时允许增高 |
| navBarMarginH / navBarBottomPad | 8dp / 8dp + navigationBars inset |
| navBarInnerPadding | 4dp |
| navItemPaddingH / navItemPaddingV | 4dp / 4dp |
| navIcon / minimumTouchTarget | 24dp / 48dp |
| fabSize / fabLift | 56dp / 4dp |
| tabBarHeight | 最小 40dp，触控区不低于 48dp |
| avatarFeed / avatarNav | 42dp / 30dp |
| glassBlurRadius / glassBorder | 20dp / 1dp |

搜索框和底栏为全圆角胶囊；正文区域必须为底栏、IME 和系统 inset 预留空间，不把最后一项藏在底栏后。

## 字体

系统字体：title 17sp/SemiBold（24sp 行高）；body 16sp/Regular（22sp）；secondary 13sp（18sp）；caption 11sp（15sp）；底栏 label 10sp/Medium（13sp）。允许系统字号 100–200%；不能用固定高度裁切文字。标题溢出可换行或使用有完整语义的省略。

## 玻璃与动效

玻璃仅用于底栏、局部浮层。API 33+、非低 RAM、硬件渲染、用户未开启减少透明效果四项同时成立才可用；不确定或读取失败时关闭。设置尚未载入也关闭。provider 只捕获页面内容，不捕获底栏自身，不把 `Modifier.blur` 糊自身伪装成背景玻璃。

低版本使用完全不透明 fallback 与 1dp 描边，代码路径不实例化 API 33 运行时对象。构建、Robolectric 和逻辑测试都不能替代真实 GPU 玻璃运行验收。

页签内容转场默认短淡入；开启减少动画时取消。发布键短按进入编辑，长按打开分类入口；触控区至少 48dp，发布不是可选中的第五页签。折扇 spring/stagger 待视觉验证，不为复杂动效牺牲滚动帧率。

## 核心组件

- 信息流：surface 卡片、头像/昵称/正文/媒体/互动分区；加载、空态与失败必须明确。未接后端时不得填假统计、假动态或假聊天。
- 底栏：首页、发现、发布动作、消息、我的；激活背景胶囊与颜色双重区分，Tab 语义正确。
- 我的：身份区、动态/关注/粉丝统计（有真实数据才显示）、基础功能与社区服务分组，不堆叠重复入口。
- 发现：地图、扩列卡、纸尿裤、小说、NBW、扫一扫、同城、话题的聚合。未迁移入口标明状态，不把导航说明页算作功能迁移完成。
