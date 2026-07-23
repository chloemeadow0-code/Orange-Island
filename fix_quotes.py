import re

langs = ['values', 'values-zh', 'values-zh-rTW', 'values-ar', 'values-de', 'values-es', 'values-fr', 'values-ja', 'values-ko', 'values-pt-rBR', 'values-ru']
for lang in langs:
    path = f'app/src/main/res/{lang}/strings.xml'
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    pattern = r'(<string name="mit_license_full">)(.*?)(</string>)'
    match = re.search(pattern, content, re.DOTALL)
    if match:
        prefix, body, suffix = match.groups()
        # Escape all unescaped double quotes
        # First unescape any already-escaped ones to avoid double-escaping
        fixed_body = body.replace('\\"', '"')
        fixed_body = fixed_body.replace('"', '\\"')
        if fixed_body != body:
            content = content[:match.start()] + prefix + fixed_body + suffix + content[match.end():]
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f'{lang}: escaped double quotes in mit_license_full')
        else:
            print(f'{lang}: no changes needed')
    else:
        print(f'{lang}: mit_license_full not found')
