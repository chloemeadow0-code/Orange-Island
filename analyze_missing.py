import xml.etree.ElementTree as ET
import re

def parse_keys(filepath):
    tree = ET.parse(filepath)
    root = tree.getroot()
    result = {}
    for elem in root:
        if elem.tag == 'string':
            name = elem.get('name')
            text = elem.text or ''
            if name:
                result[name] = text
    return result

en = parse_keys('app/src/main/res/values/strings.xml')

langs = [
    ('values-zh', 'app/src/main/res/values-zh/strings.xml'),
    ('values-zh-rTW', 'app/src/main/res/values-zh-rTW/strings.xml'),
    ('values-ar', 'app/src/main/res/values-ar/strings.xml'),
    ('values-de', 'app/src/main/res/values-de/strings.xml'),
    ('values-es', 'app/src/main/res/values-es/strings.xml'),
    ('values-fr', 'app/src/main/res/values-fr/strings.xml'),
    ('values-ja', 'app/src/main/res/values-ja/strings.xml'),
    ('values-ko', 'app/src/main/res/values-ko/strings.xml'),
    ('values-pt-rBR', 'app/src/main/res/values-pt-rBR/strings.xml'),
    ('values-ru', 'app/src/main/res/values-ru/strings.xml'),
]

def has_chinese(text):
    return any('\u4e00' <= c <= '\u9fff' for c in text)

print("=== Missing keys in values-zh (first 50) ===")
zh = parse_keys('app/src/main/res/values-zh/strings.xml')
missing_zh = sorted(set(en) - set(zh))
for k in missing_zh[:50]:
    v = en[k]
    flag = "CN" if has_chinese(v) else "EN"
    print(f"  {k}: [{flag}] {v[:80]}")
if len(missing_zh) > 50:
    print(f"  ... and {len(missing_zh)-50} more")

print(f"\nTotal missing in zh: {len(missing_zh)}")

# Show orphan keys
orphan_zh = sorted(set(zh) - set(en))
print(f"\nOrphaned in zh: {len(orphan_zh)}")
for k in orphan_zh:
    print(f"  {k}")

# Summary for all languages
print("\n=== Summary ===")
for lang_name, filepath in langs:
    lang = parse_keys(filepath)
    missing = sorted(set(en) - set(lang))
    orphaned = sorted(set(lang) - set(en))
    cn_count = sum(1 for k in missing if has_chinese(en[k]))
    print(f"{lang_name}: missing={len(missing)} (cn={cn_count}), orphaned={len(orphaned)}")
