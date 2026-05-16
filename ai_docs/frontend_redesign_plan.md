# 全局前端 UI 重构计划 (基于设计稿还原)

根据您提供的 `ui.png` 设计图，这套全新的 UI 方案采用了非常现代的暗黑模式（Dark Theme），融合了毛玻璃（Glassmorphism）、丰富的微渐变色、以及数据可视化的科技感布局。设计稿展示了五个核心页面：首页、空间详情、后台工作台、AI 助手和个人主页。

为了“得到完全相同的效果”，我将结合 Naive UI 现有的组件能力与定制化的高级 SCSS 样式进行深度重构。

## User Review Required

> [!IMPORTANT]
> 由于这次重构涉及整个前端的视觉底层和几乎所有高频使用页面，工作量较大，我将按照以下**分阶段计划**进行交付：
>
> 1.  **阶段 1：底层视觉系统铺设**。修改 `App.vue` 开启全局暗色模式，并抽象出设计稿中的核心 CSS Tokens（如霓虹发光边框、卡片深色渐变底色、毛玻璃效果）。
> 2.  **阶段 2：重构首页（Home）**。实现顶部的全屏 Hero 区域、数据统计看板以及下方精美的“热门学习空间”卡片流。
> 3.  **阶段 3：重构左中右经典布局页面（空间详情、个人主页）**。高度还原空间详情页的顶部横幅及右侧数据小卡片；还原个人主页的极光背景及成就徽章卡片。
> 4.  **阶段 4：重构系统级页面（后台工作台、AI 助手）**。
>
> **请确认是否同意按照此步骤实施？**

## Open Questions

> [!WARNING]
> 1.  **关于素材生成**：设计图中包含了高品质的 **3D 校园建筑插画（首页）**、**极光背景图（个人主页）** 和 **AI 机器人插画（AI助手页）**。原代码中目前没有这些素材，我将使用内置的 **AI 图像生成工具** 为您当场生成并无缝接入到页面中。您觉得可以吗？
> 2.  **关于样式方案**：遵循本项目的技术栈，我将避免引入额外的 TailwindCSS 等库，直接使用 `SCSS / CSS Variables` 进行高度灵活的样式还原。

## Proposed Changes

---

### 全局架构与样式 (Global System)

#### [MODIFY] `frontend/src/App.vue`
-   配置 Naive UI 的 `NConfigProvider`，引入 `darkTheme`。
-   注入设计稿相关的 `themeOverrides`（全局主题色定制为类似蓝紫霓虹的主色调 `#3b82f6` 至 `#8b5cf6` 的过渡）。

#### [NEW] `frontend/src/assets/styles/theme.scss`
-   定义全局的混合宏（mixins）和变量：`.glass-card`, `.neon-text`, `.gradient-bg` 等。

---

### 核心页面 (Core Pages)

#### [MODIFY] `frontend/src/pages/Home.vue`
-   重绘顶部导航栏为悬浮透明态。
-   实现左侧标题区与右侧 3D 建筑插图的非对称排版。
-   实现四列的数据统计模块（高校入驻、注册用户等）及卡片 Hover 浮起动画。

#### [MODIFY] `frontend/src/pages/SpaceDetail.vue`
-   左侧加入系统级的折叠侧边栏（包含系统核心导航）。
-   中央主内容区增加具有空间图标的 Banner 头图。
-   右侧新增“活跃成员”和“空间数据”面板（使用纯 CSS 或简单 SVG 模拟设计图中的折线图效果，保持轻量）。

#### [MODIFY] `frontend/src/pages/Profile.vue`
-   实现顶部的极光半透明封面背景图，头像采用绝对定位悬浮效果。
-   重构 Tabs 样式，右侧新增“成就展示区”（包含渐变的徽章图标）及仿 GitHub 的“近期打卡矩阵图”。

#### [MODIFY] `frontend/src/pages/admin/AdminDashboard.vue`
-   彻底抛弃传统的表格罗列展示，重构为卡片化的数据统计概览。
-   实现各种深色模式下的渐变折线图容器、环形图容器和进度条分布面板。

#### [MODIFY] `frontend/src/pages/AiAssistant.vue`
-   重构左侧功能菜单选项卡。
-   中部改为大型对话式输入框组合页面，右侧插入生成的 AI 机器人引导图。

## Verification Plan

### Manual Verification
1.  **阶段性检查**：每完成一个页面的重构，我将在本地启动 Vite 开发服务器。
2.  **视觉比对**：由于我能看到您发的截图，我会对齐每一处阴影、边框圆角、字体粗细与原图的质感。
3.  **最终验收**：您可以在本地运行 `npm run dev` 亲自感受动效与响应式的完美还原。
