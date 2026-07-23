import xml.etree.ElementTree as ET
import os

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

def has_chinese(text):
    return any('\u4e00' <= c <= '\u9fff' for c in text)

# Extract truly English missing keys for each language
langs = [
    'values-zh',
    'values-zh-rTW',
    'values-ar',
    'values-de',
    'values-es',
    'values-fr',
    'values-ja',
    'values-ko',
    'values-pt-rBR',
    'values-ru',
]

print("=== Truly English missing keys (need translation) ===\n")
for lang in langs:
    lang_map = parse_keys(f'app/src/main/res/{lang}/strings.xml')
    missing = sorted(set(en) - set(lang_map))
    truly_en = [k for k in missing if not has_chinese(en[k])]
    print(f"{lang}: {len(truly_en)} truly English keys need translation")
    for k in truly_en[:10]:
        print(f"  {k}: {en[k][:70]}")
    if len(truly_en) > 10:
        print(f"  ... and {len(truly_en)-10} more")
    print()

# Show all truly English missing keys for zh
zh = parse_keys('app/src/main/res/values-zh/strings.xml')
missing_zh = sorted(set(en) - set(zh))
truly_en_zh = [k for k in missing_zh if not has_chinese(en[k])]
print(f"=== values-zh: all {len(truly_en_zh)} truly English missing keys ===")
for k in truly_en_zh:
    print(f"{k}: {en[k]}")
