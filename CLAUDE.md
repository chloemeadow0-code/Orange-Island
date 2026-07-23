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
