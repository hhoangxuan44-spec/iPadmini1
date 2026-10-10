# 光影台历 · iPad mini 1 桌面摆件

把老 iPad mini 1（iOS 9.3.5）变成一本每天都不一样的「光影台历」：

- **每天一页**：上滑翻到明天、下滑翻回昨天，翻页过程跟着手指走，松手自动完成或弹回
- **高质量图片**：自动从 Wikimedia Commons「精选图片」挑选风景、自然、天文、动物、名画与浮世绘，同一天永远是同一张图，翻到哪天都有新的惊喜
- **玻璃质感**：日期卡、月历卡、顶部按钮都是磨砂玻璃，背后是精确对齐、预先模糊过的同一张照片，并略微放大，模拟厚玻璃的折射；颜色深浅会按卡片背后的画面自动切换浅色/深色玻璃
- **倾角效果**：倾斜设备时，照片轻微视差移动，玻璃上的高光跟着转动（电脑上用鼠标模拟）
- **日期细节**：农历、干支生肖、二十四节气、传统和公历节日、一年中的第几天、月历中有节日的日期带小圆点
- **适配**：iPad 横屏 / 竖屏、手机竖屏 / 横屏自动排版；夜间自动调暗；午夜自动翻页；翻到别的日期几分钟没碰会自动回到今天

## 兼容性

- 代码全部是 ES5，CSS 动画全部带 `-webkit-` 前缀，不用 CSS 变量、grid、`backdrop-filter`，在 iOS 9 Safari 上能正常运行
- 玻璃模糊不在设备上实时计算，而是构建时生成小尺寸模糊图，老 iPad 也不卡
- 老设备自动加载 1280 像素的图片（省内存），高分屏手机加载 1920 像素版本

## 发布到网上（GitHub Pages）

仓库里的 GitHub Actions（`.github/workflows/pages.yml`）会在每次推送时：下载图片 → 生成各尺寸 → 发布到 GitHub Pages。

第一次需要在 GitHub 上打开一次 Pages：

1. 进入仓库 **Settings → Pages**
2. **Source** 选择 **GitHub Actions**
3. 到 **Actions** 页面重新运行一次「构建并发布台历」

完成后网址是 `https://<你的用户名>.github.io/iPadmini1/`。

> 私有仓库在免费账户下不能开 GitHub Pages，需要把仓库设为公开，或升级账户。

## 在 iPad 上安装成「App」

1. iPad 上用 Safari 打开网址
2. 点分享按钮 → **添加到主屏幕**
3. 从主屏幕图标打开，就是全屏、无地址栏的台历
4. **设置 → 通用 → 自动锁定 → 永不**，让屏幕一直亮着
5. （可选）**设置 → 通用 → 辅助功能 → 引导式访问**：打开后三击 Home 键锁定在台历里

## 操作

| 操作 | 效果 |
| --- | --- |
| 上滑 / 左滑 | 翻到下一天 |
| 下滑 / 右滑 | 翻回前一天 |
| 点月历里的日期 | 直接翻到那天 |
| 点左上角图片标题 | 展开作者和授权信息 |
| 点「今天」 | 回到今天 |
| 电脑：方向键 / 空格 / 滚轮 | 翻页 |

网址加 `?date=2026-02-17` 可以直接打开某一天。

## 设置

编辑 `js/config.js`：自动回到今天的时间、是否显示时钟、倾角效果开关、夜间调暗时段。

## 加入自己的照片

把照片放进仓库根目录的 `photos/` 文件夹（jpg / png），推送后会自动处理并加入台历轮换。

## 本地预览

```bash
pip install Pillow
python3 build/build_site.py            # 需要能访问 commons.wikimedia.org
# 或者离线：python3 build/build_site.py --fake 12   （生成测试图）
cd dist && python3 -m http.server 8000
```

然后浏览器打开 `http://localhost:8000`。同一局域网里的 iPad 也可以访问 `http://电脑IP:8000`。

## 图片版权

所有图片来自 [Wikimedia Commons](https://commons.wikimedia.org/) 的精选图片，均为公有领域或 Creative Commons 等自由授权。页面左上角显示每张图的标题，点开可以看到作者与授权协议。

## 文件结构

```
index.html               页面
css/calendar.css         样式（玻璃、翻页、排版）
js/calendar.js           主程序（翻页、手势、倾角、玻璃对齐）
js/lunar.js              农历、节气、节日
js/config.js             设置
build/build_site.py      选图、生成多尺寸与模糊图、输出 dist/
build/make_icons.py      生成主屏幕图标
.github/workflows/       自动构建与发布
```
