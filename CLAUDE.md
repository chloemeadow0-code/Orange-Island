# Orange Island — AI Agent Guidelines

## 多语言字符串规则（强制）

本项目支持 **11 种语言**：
- `values`（英文默认）
- `values-zh`（简体中文）
- `values-zh-Hant`（繁体中文）
- `values-ar`（阿拉伯语）
- `values-de`（德语）
- `values-es`（西班牙语）
- `values-fr`（法语）
- `values-ja`（日语）
- `values-ko`（韩语）
- `values-pt-rBR`（巴西葡萄牙语）
- `values-ru`（俄语）

### 任何一次改动只要涉及新增 / 修改 / 删除 `strings.xml` 里的字符串 key，必须在同一次改动里把这 11 份文件全部同步处理完：

- **新增 key** → 11 份文件都要加，翻译成对应语言，不能只加英文 / 只加中文。
- **删除 key** → 11 份文件都要删，不留孤儿 key。
- **修改文案** → 视情况判断是否需要连带更新其他语言的翻译。

### 提交前自查
- [ ] 这 11 份文件的 key 数量是否一致。
- [ ] 新增的 key 是否在所有语言文件里都已存在。
- [ ] 删除的 key 是否在所有语言文件里都已清除。
- [ ] `values-zh` 和 `values-zh-Hant` 的翻译是否准确、无简体/繁体混用。

### 批量检查脚本（推荐提交前运行）
```bash
# 以 values/strings.xml 为基准，检查其他语言文件缺失/孤儿 key
python3 -c "
import xml.etree.ElementTree as ET
import os

BASE = 'app/src/main/res'
EN = ET.parse(f'{BASE}/values/strings.xml').getroot()
en_keys = {e.get('name') for e in EN if e.tag == 'string'}

for lang in ['values-zh', 'values-zh-Hant', 'values-ar', 'values-de',
             'values-es', 'values-fr', 'values-ja', 'values-ko',
             'values-pt-rBR', 'values-ru']:
    path = f'{BASE}/{lang}/strings.xml'
    if not os.path.exists(path):
        print(f'{lang}: FILE MISSING')
        continue
    root = ET.parse(path).getroot()
    keys = {e.get('name') for e in root if e.tag == 'string'}
    missing = sorted(en_keys - keys)
    orphaned = sorted(keys - en_keys)
    print(f'{lang}: missing={len(missing)}, orphaned={len(orphaned)}')
"
```

> 若发现缺失/孤儿 key，必须在本轮改动内全部修复，禁止留到下一轮。

## UI 主题色规则（强制）

本项目使用 Jetpack Compose + Material3，所有页面颜色必须通过
`MaterialTheme.colorScheme.*` 取值（primary / onSurface / surfaceContainer 等），
禁止在 Composable 里硬编码颜色值，包括但不限于：
- `Color.Gray` / `Color.White` / `Color.Black` / `Color.LightGray` 等预定义颜色常量
- `Color(0xFF......)` 这类写死的十六进制颜色
- 任何不经过 `MaterialTheme.colorScheme` 的颜色来源

### 允许的例外（必须在代码注释里写明原因）
- 品牌资产本身固有的颜色（如某个 provider 的 logo 原色，见
  `ui/components/ProviderIcons.kt` 这类场景）
- 纯装饰性、不随主题变化的插画/贴图（如 `island_deco_*` 系列背景装饰图）
- 系统级 UI（状态栏、系统权限弹窗等安卓原生渲染的部分），这些不受 Compose
  主题控制，不算违规

### 新增页面 / 组件时的自查清单
- [ ] 是否所有背景色、文字色、边框色都来自 `MaterialTheme.colorScheme`？
- [ ] 是否测试过切换"配色预设"（设置 → 外观 → 配色方案）后这个页面颜色会跟着变？
- [ ] 是否测试过深色模式下颜色依然正确（不是写死的浅色值）？
- [ ] 如果用了硬编码颜色，是否属于上面"允许的例外"，并已在代码里注释说明原因？

> 每次新增 UI 页面或组件，提交前必须过一遍这份自查清单，禁止留到下一轮再补。
