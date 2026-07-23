import xml.etree.ElementTree as ET

def parse(filepath):
    root = ET.parse(filepath).getroot()
    return {e.get('name'): e.text or '' for e in root if e.tag == 'string'}

en = parse('app/src/main/res/values/strings.xml')

def has_chinese(text):
    return any('\u4e00' <= c <= '\u9fff' for c in text)

for lang in ['values-zh', 'values-zh-rTW']:
    lang_map = parse(f'app/src/main/res/{lang}/strings.xml')
    missing = sorted(set(en) - set(lang_map))
    truly_en = [(k, en[k]) for k in missing if not has_chinese(en[k])]
    with open(f'{lang}_missing_en.txt', 'w', encoding='utf-8') as f:
        f.write(f'{lang} truly English missing: {len(truly_en)}\n')
        for k, v in truly_en:
            f.write(f'{k}={v}\n')
    print(f'{lang}: {len(truly_en)} truly English keys dumped')
