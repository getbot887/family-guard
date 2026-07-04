# KidSafe 孩子端 UI 页面设计

Tech: Kotlin + Jetpack Compose + Material 3
Date: 2026-07-02

## 一、页面结构

孩子端页面极少，主要是一个轻量管理界面 + 拦截全屏页面。

Pages:
1. 主页 (HomeScreen) - 显示绑定状态和基本设置
2. 拦截页面 (BlockActivity) - 全屏覆盖，阻止App使用
3. 配对页面 (PairScreen) - 显示配对码用于家长绑定

Note: 孩子端大部分功能在后台运行（AccessibilityService），用户交互很少。

## 二、各页面详细设计

### 2.1 主页 (HomeScreen)

孩子端应用非常精简，主要用于显示状态信息和提供基本设置入口。

Layout:
- 顶部应用栏: "KidSafe" 标题
- 状态卡片（顶部醒目区域）:
  - 若未绑定: 红色状态提示 "未绑定设备" + "等待家长绑定..." 文字 + 二维码或6位验证码显示
  - 若已绑定: 绿色状态提示 "已绑定" + 家长账户信息 + "解绑" 按钮（小型、次要样式）
- "服务状态" 区块:
  - AccessibilityService 状态: 绿色勾选 "运行中" 或 红色叉号 "已关闭" + "去开启" 按钮
  - DeviceAdmin 状态: 绿色勾选 "已激活" 或 警告提示 "未激活" + "去激活" 按钮
  - 最后同步时间: "上次同步: 5分钟前"
- "已安装应用" 区块:
  - 数量显示: "共安装了 X 个应用"
  - "同步应用列表" 按钮
- "关于" 区块:
  - 版本号
  - "帮助" 链接
- 页脚: "KidSafe - 保护孩子的安全" 小号文字

Wireframe (未绑定状态):

```
+---------------------------------------+
|  = KidSafe                            |
+---------------------------------------+
|                                       |
|  +-------------------------------+  |
|  |  (!)  未绑定设备               |  |
|  |  等待家长绑定...               |  |
|  |                               |  |
|  |    +-------------------+      |  |
|  |    |                   |      |  |
|  |    |   [  QR  CODE  ]  |      |  |
|  |    |                   |      |  |
|  |    +-------------------+      |  |
|  |                               |  |
|  |  验证码: 4 8 2 9 1 6         |  |
|  +-------------------------------+  |
|                                       |
|  --- 服务状态 ---                    |
|                                       |
|  AccessibilityService    [V] 运行中  |
|  Device Admin            [!] 未激活  |
|                            [去激活]  |
|  上次同步: 5分钟前                   |
|                                       |
|  --- 已安装应用 ---                  |
|  共安装了 23 个应用                   |
|  [        同步应用列表        ]       |
|                                       |
|  --- 关于 ---                        |
|  版本 1.0.0                          |
|  帮助                                |
|                                       |
|  KidSafe - 保护孩子的安全            |
+---------------------------------------+
```

Wireframe (已绑定状态):

```
+---------------------------------------+
|  = KidSafe                            |
+---------------------------------------+
|                                       |
|  +-------------------------------+  |
|  |  [V]  已绑定                   |  |
|  |  家长: 张妈妈                  |  |
|  |                       [解绑]   |  |
|  +-------------------------------+  |
|                                       |
|  --- 服务状态 ---                    |
|                                       |
|  AccessibilityService    [V] 运行中  |
|  Device Admin            [V] 已激活  |
|  上次同步: 2分钟前                   |
|                                       |
|  --- 已安装应用 ---                  |
|  共安装了 23 个应用                   |
|  [        同步应用列表        ]       |
|                                       |
|  --- 关于 ---                        |
|  版本 1.0.0                          |
|  帮助                                |
|                                       |
|  KidSafe - 保护孩子的安全            |
+---------------------------------------+
```

### 2.2 拦截页面 (BlockActivity)

这是最关键的页面 -- 全屏覆盖的拦截界面，阻止孩子使用被限制的应用。

Layout (全屏，无法轻易退出):
- 全屏不透明背景（深色遮罩或纯色背景）
- 居中内容:
  - 大型拦截图标（盾牌 + X）
  - 被拦截应用的图标
  - 主信息: "此应用当前不可使用"
  - 说明: "该应用在当前时间段内已被限制"
  - 规则信息: "规则：学习时间限制"
  - 时间信息: "可用时间：17:00 - 18:00"
- 底部按钮: "我知道了"（返回主屏幕）
- 无返回按钮，无法进入被拦截的应用
- Activity 设置为不出现在最近任务列表中

Wireframe (拦截页面):

```
+=======================================+
|                                       |
|                                       |
|                                       |
|              +-----------+            |
|              |   | |     |            |
|              |   |X|     |            |
|              |   ---     |            |
|              +-----------+            |
|              (盾牌拦截图标)           |
|                                       |
|              +-----------+            |
|              |           |            |
|              | [ APP ICON ]           |
|              |           |            |
|              +-----------+            |
|            (被拦截应用图标)           |
|                                       |
|        此应用当前不可使用             |
|                                       |
|    该应用在当前时间段内已被限制       |
|                                       |
|         规则：学习时间限制            |
|                                       |
|         可用时间：17:00 - 18:00       |
|                                       |
|                                       |
|                                       |
|   +-----------------------------------+  |
|   |          我 知 道 了             |  |
|   +-----------------------------------+  |
|                                       |
+=======================================+
```

Implementation notes:
- BlockActivity launches with FLAG_ACTIVITY_NEW_TASK
- Uses WindowManager to overlay on top of blocked app
- Cannot be dismissed by back button
- "我知道了" calls finish() to return to launcher
- Activity theme: NoActionBar + fullscreen + translucent status bar disabled
- Background color: #1A1A1A (deep dark)
- Text color: #FFFFFF (white)

### 2.3 配对页面 (PairScreen)

配对页面用于显示验证码，让家长端扫描或输入以完成设备绑定。

Layout:
- 标题 "等待绑定"
- 居中显示大型6位验证码（大号字体、等宽字体）
- 验证码刷新倒计时: "验证码 XX:XX 后刷新"
- 使用说明: "请让家长在KidSafe家长端输入此验证码完成绑定"
- "刷新验证码" 按钮
- 连接状态指示器（等待服务器响应）

Wireframe:

```
+---------------------------------------+
|  = KidSafe                            |
+---------------------------------------+
|                                       |
|                                       |
|           等待绑定                    |
|                                       |
|         +-------------------+         |
|         |                   |         |
|         |    4  8  2  9  1  6         |
|         |                   |         |
|         +-------------------+         |
|                                       |
|      验证码 14:32 后刷新              |
|                                       |
|   请让家长在KidSafe家长端             |
|   输入此验证码完成绑定                |
|                                       |
|                                       |
|   [        刷新验证码        ]        |
|                                       |
|                                       |
|      (*) 等待服务器响应...            |
|                                       |
+---------------------------------------+
```

## 三、拦截页面实现要点

### 3.1 Window flag 设置

拦截页面需要正确设置 WindowManager.LayoutParams flags，以确保全屏覆盖且不可被轻易绕过。

需要的 flags:

```
FLAG_SHOW_WHEN_LOCKED   - 在锁屏上显示
FLAG_TURN_SCREEN_ON     - 自动亮屏
FLAG_NOT_TOUCH_MODAL    - 拦截所有触摸事件
FLAG_LAYOUT_IN_SCREEN   - 布局覆盖整个屏幕（包含状态栏区域）
```

不应设置的 flags:

```
FLAG_NOT_FOCUSABLE      - 不设置此标志，以拦截所有按键事件（包括返回键）
```

完整配置:

```kotlin
window.apply {
    addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    )
    // 不设置 FLAG_NOT_FOCUSABLE，确保拦截所有输入
    // 设置全屏
    decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    )
}
```

### 3.2 拦截流程

完整的拦截流程如下:

```
1. AccessibilityService 检测到前台应用变化
2. 查询本地 Room 数据库中的规则缓存
3. 如果该应用在当前时间段内被阻止:
   a. 启动 BlockActivity，传递被阻止应用的信息
   b. BlockActivity 显示全屏拦截界面
4. 用户点击 "我知道了" -> finish() -> 返回桌面启动器
5. 如果用户尝试切换回被阻止的应用:
   a. AccessibilityService 再次检测到前台应用变化
   b. 再次启动 BlockActivity
   c. 形成循环阻止效果
```

流程图:

```
+-------------------+
| 孩子点击被限制    |
| 的应用程序        |
+--------+----------+
         |
         v
+-------------------+
| AccessibilityService|
| 检测到前台应用变化 |
+--------+----------+
         |
         v
+-------------------+
| 查询本地 Room     |
| 规则缓存          |
+--------+----------+
         |
         v
    +-----------+
    | 是否被阻止? |
    +-----+-----+
     Yes  |  No
     v    +-----> 放行，不干预
+-------------------+
| 启动 BlockActivity |
| 显示拦截界面       |
+--------+----------+
         |
         v
+-------------------+
| 用户点击          |
| "我知道了"        |
+--------+----------+
         |
         v
+-------------------+
| finish()          |
| 返回桌面启动器    |
+-------------------+
```

### 3.3 防绕过措施

为了确保拦截不被轻易绕过，需要实现以下防护:

1. **Recent Apps 隐藏**: 在 AndroidManifest 中为 BlockActivity 设置 `android:excludeFromRecents="true"`，使其不出现在最近任务列表中

2. **Task Affinity**: 设置 `android:taskAffinity=""` 确保 BlockActivity 不与被阻止的应用共享任务栈

3. **返回键拦截**: 在 BlockActivity 中重写 `onBackPressed()`，不执行默认返回操作，而是调用 `finish()` 返回桌面

4. **切换应用监听**: AccessibilityService 持续监听 `TYPE_WINDOW_STATE_CHANGED` 事件，一旦检测到被阻止的应用回到前台，立即重新启动 BlockActivity

5. **不可卸载**: 通过 DeviceAdmin API 阻止孩子自行卸载应用

## 四、设计规范

### 4.1 颜色方案 (Material 3)

```
Primary:        #1976D2  (深蓝 - 信任、安全感)
PrimaryVariant: #1565C0  (较深蓝色)
Secondary:      #43A047  (绿色 - 正常状态)
Error:          #D32F2F  (红色 - 拦截、警告)
Surface:        #FFFFFF  (白色)
Background:     #FAFAFA  (浅灰背景)

拦截页面专用:
BlockBackground:  #1A1A1A  (深色背景)
BlockText:        #FFFFFF  (白色文字)
BlockAccent:      #FF5252  (拦截强调色，红色)
BlockSecondary:   #B0BEC5  (次要信息，灰色)
```

### 4.2 字体规范

```
拦截页面标题:    24sp, Bold,     白色
拦截页面正文:    16sp, Regular,   白色
拦截页面次要:    14sp, Regular,   灰色 (#B0BEC5)
配对码:          48sp, Monospace, Bold,  深蓝 (#1976D2)
普通页面标题:    20sp, Medium,    黑色
卡片标题:        16sp, Medium,    黑色
卡片正文:        14sp, Regular,   灰色
按钮文字:        14sp, Medium,    白色或 Primary 色
```

### 4.3 拦截页面动画

拦截页面使用以下动画效果，增强视觉反馈:

```
背景渐变:
  屏幕从半透明 (#80000000) 渐变到不透明 (#FF1A1A1A)
  Duration: 300ms
  Easing: FastOutSlowIn

图标弹入:
  Scale from 0.5 to 1.0
  Duration: 400ms
  Easing: Spring (dampingRatio = 0.6f)
  Delay: 100ms (背景渐变开始后)

文字逐行淡入:
  逐行 staggered 淡入 (fade in)
  每行延迟 100ms
  Duration per line: 200ms
  顺序: 标题 -> 说明 -> 规则 -> 时间 -> 按钮

按钮弹入:
  从底部滑入 + 淡入
  Duration: 300ms
  Delay: 最后执行
```

Compose 动画实现示意:

```kotlin
// 背景渐变动画
val backgroundColor by animateColorAsState(
    targetValue = if (visible) Color(0xFF1A1A1A) else Color(0x80000000),
    animationSpec = tween(300)
)

// 图标弹入动画
val iconScale by animateFloatAsState(
    targetValue = if (visible) 1f else 0.5f,
    animationSpec = spring(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessLow
    )
)

// 文字逐行淡入
val textAlpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(
        durationMillis = 200,
        delayMillis = index * 100
    )
)
```

## 五、无障碍服务配置

### 5.1 AndroidManifest.xml 配置

```xml
<!-- 无障碍服务 - 核心拦截能力 -->
<service
    android:name=".service.BlockService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>

<!-- 拦截页面 Activity -->
<activity
    android:name=".ui.block.BlockActivity"
    android:theme="@style/Theme.BlockActivity"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:launchMode="singleInstance"
    android:screenOrientation="portrait"
    android:configChanges="orientation|screenSize" />

<!-- 设备管理员 - 防止卸载 -->
<receiver
    android:name=".admin.KidSafeAdmin"
    android:permission="android.permission.BIND_DEVICE_ADMIN">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/device_admin_policies" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

### 5.2 accessibility_service_config.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:description="@string/accessibility_service_description"
    android:settingsActivity=".ui.settings.AccessibilitySettingsActivity" />
```

### 5.3 BlockActivity Theme

```xml
<style name="Theme.BlockActivity" parent="Theme.MaterialComponents.DayNight.NoActionBar">
    <item name="android:windowFullscreen">true</item>
    <item name="android:windowNoTitle">true</item>
    <item name="android:windowBackground">@color/block_background</item>
    <item name="android:windowIsTranslucent">false</item>
    <item name="android:windowAnimationStyle">@style/BlockActivityAnimation</item>
    <item name="android:excludeFromRecents">true</item>
</style>

<style name="BlockActivityAnimation">
    <item name="android:windowEnterAnimation">@anim/fade_in</item>
    <item name="android:windowExitAnimation">@anim/fade_out</item>
</style>
```

## 六、Compose 组件结构

### 6.1 Screen 层级

```
screens/
  HomeScreen.kt          // 主页 - 绑定状态 + 服务状态
  PairScreen.kt          // 配对码显示页

activity/
  BlockActivity.kt       // 拦截页面 - 全屏覆盖 (非Compose, 使用 Activity + ComposeView)
```

### 6.2 公共组件

```
components/
  StatusCard.kt          // 状态信息卡片（绑定状态）
  ServiceStatusItem.kt   // 服务状态行 (图标 + 名称 + 状态 + 操作按钮)
  CodeDisplay.kt         // 6位验证码显示组件（等宽字体，大号数字）
  BlockOverlay.kt        // 拦截页面内容 Composable (供 BlockActivity 使用)
  CountdownTimer.kt      // 倒计时组件（验证码刷新倒计时）
```

### 6.3 Navigation 结构

```kotlin
// App NavHost (孩子端导航非常简单)
NavHost(navController, startDestination = "home") {
    composable("home") { HomeScreen() }
    composable("pair") { PairScreen() }
    // 注意: BlockActivity 不在 NavHost 中，它是独立的 Activity
}
```

### 6.4 ViewModel 结构

```
viewmodel/
  HomeViewModel.kt       // 管理绑定状态、服务状态、应用列表
  PairViewModel.kt       // 管理验证码生成、刷新、绑定状态轮询
```

HomeViewModel 核心状态:

```kotlin
data class HomeUiState(
    val isBound: Boolean = false,
    val parentName: String = "",
    val accessibilityServiceRunning: Boolean = false,
    val deviceAdminActive: Boolean = false,
    val installedAppCount: Int = 0,
    val lastSyncTime: LocalDateTime? = null,
    val isLoading: Boolean = false
)
```

PairViewModel 核心状态:

```kotlin
data class PairUiState(
    val code: String = "",
    val remainingSeconds: Int = 300,
    val connectionStatus: ConnectionStatus = ConnectionStatus.WAITING,
    val isRefreshing: Boolean = false
)

enum class ConnectionStatus {
    WAITING,      // 等待服务器响应
    CONNECTED,    // 已连接
    BINDING,      // 正在绑定
    BOUND,        // 绑定成功
    ERROR         // 连接错误
}
```

## 七、技术实现注意事项

### 7.1 AccessibilityService 最佳实践

- `onAccessibilityEvent()` 中只处理 `TYPE_WINDOW_STATE_CHANGED` 事件
- 使用 PackageManager 获取前台应用包名
- 本地 Room 缓存规则，避免每次查询网络
- 使用 Handler.postDelayed 实现防抖（100ms），避免频繁触发

### 7.2 BlockActivity 生命周期

- `onCreate()`: 初始化 ComposeView，设置 Window flags
- `onResume()`: 启动入场动画
- `onBackPressed()`: 调用 `finish()` 返回桌面，不执行默认返回
- `onDestroy()`: 清理资源
- 使用 `singleInstance` launchMode 确保只有一个实例

### 7.3 数据同步策略

- 应用列表: 启动时 + 手动触发同步
- 规则缓存: 每5分钟从服务器拉取，存储在 Room 数据库
- 设备状态: 每次服务状态变化时上报服务器
- 验证码: 有效期5分钟，过期自动刷新

### 7.4 离线模式

- 所有规则在本地 Room 数据库缓存
- 无网络时使用本地缓存的规则继续拦截
- 网络恢复后自动同步最新规则和状态
