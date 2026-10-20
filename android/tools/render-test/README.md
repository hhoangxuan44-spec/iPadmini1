# render-test：离线 Canvas/Bitmap 渲染验证

在没有设备/AGP 的 Linux 上用 **Robolectric 4.16 + NATIVE 图形模式**（真 Skia）跑 Android 绘图代码，输出 PNG 后直接看图。

## 用法
```
android/tools/render-test/run.sh [SmokeRenderTest | SmokeRenderTest#chineseText]
```
PNG 默认写到 `/tmp/render-out`（`RENDER_OUT=/dir ./run.sh` 或 `-Drender.out=/dir` 改）。首次需联网（仅 repo1.maven.org）下载依赖到 `~/.m2`，之后 `mvn -o` 完全离线。
额外 mvn 参数用 `MVN_ARGS`，例如排除某个源文件：`MVN_ARGS='-Dapp.exclude.extra=**/data/Library.java' ./run.sh`。

## 工作原理
- `pom.xml` 用 build-helper 把 `../../app/src/main/java` 加为源码根，compiler 只编译 `-Dapp.include`（默认 `**/*.java`），并排除 `**/widget/**`、`**/MainActivity.java`、`-Dapp.exclude.extra`（单个 ant pattern）。**app 里不能引用 `R.`**（这里没有 R 类）；渲染类请保持无 R、无资源依赖。
- 编译期 android.* API 来自 `android-all:14-robolectric-10818077`(provided)；运行期 Robolectric 使用 `android-all-instrumented:...-i7`，由 dependency 插件拷到 `target/android-all`，并设 `robolectric.offline=true`、`robolectric.dependency.dir`，不再联网。
- 测试类加 `@RunWith(RobolectricTestRunner.class) @GraphicsMode(GraphicsMode.Mode.NATIVE)`；`src/test/resources/robolectric.properties` 固定 `sdk=34`。
- `Png.save(bitmap, "name")` 写 PNG 到 `render.out`。
- **androidx.test 桩**：Robolectric 需要 `androidx.test:monitor`，它只在 Google Maven（本环境被屏蔽）。`src/stubs/java/androidx/test/**` 是最小桩实现，足够 Robolectric 启动、画 Bitmap；Activity 生命周期监控/Espresso/Intent monitor 不可用。
- 注意：测试资源里不要建 `fonts/` 目录（会遮住 native runtime 自带的 `fonts/`，导致 Typeface 初始化 NPE）；自带字体放 `testfonts/`。

## CJK 字体
`Fonts.cjk()`（src/test/java/render/Fonts.java）返回 CJK `Typeface`：优先 `-Dcjk.font=路径`，否则用 `Typeface.Builder(file).setTtcIndex(0)` 直接读 `/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc`（Skia 原生可读 TTC，无需转换）。
`Fonts.digits()` 演示 `Typeface.createFromFile` 加载自定义 TTF（`src/test/resources/testfonts/digits.ttf`，DejaVu Sans Mono Bold 的数字子集）。

渲染代码请接受注入的 Typeface：
```java
// 产品代码： void draw(Canvas c, Typeface tf) { paint.setTypeface(tf); ... }
// 测试：     renderer.draw(canvas, Fonts.cjk());
```
另：native runtime 自带 NotoSansCJK 作为 fallback，不设 Typeface 时 `drawText` 中文也会出字形，但字体不是 wqy。

## 已知限制
- 只验证 Bitmap/Canvas/Paint/Path/Shader 等；RemoteViews 布局、资源(R)、Activity UI 不在范围。
- 字体度量与真机 Roboto/系统字体接近但不完全一致。
- 需要 JDK 17+、Linux x86_64（native runtime 随 `nativeruntime-dist-compat` 提供）。
