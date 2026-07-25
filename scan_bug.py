import os, re

patterns = [
    (r'\.value\.dp', '.value.dp'),
    (r'lineHeight\.value|fontSize\.value', 'lineHeight.value or fontSize.value'),
    (r'LocalTextStyle|ProvideTextStyle', 'LocalTextStyle/ProvideTextStyle'),
    (r'heightIn\(.*chatFontScale|heightIn\(.*fontScale', 'heightIn with fontScale'),
    (r'height\(.*chatFontScale|height\(.*fontScale', 'height with fontScale'),
    (r'padding\(.*chatFontScale|padding\(.*fontScale', 'padding with fontScale'),
]

root = 'app/src/main/java'
for dirpath, dirnames, filenames in os.walk(root):
    for fn in filenames:
        if not fn.endswith('.kt'):
            continue
        path = os.path.join(dirpath, fn)
        with open(path, 'r', encoding='utf-8', errors='replace') as f:
            lines = f.readlines()
        for i, line in enumerate(lines, 1):
            for pattern, desc in patterns:
                if re.search(pattern, line):
                    print(f'{path}:{i}: [{desc}] {line.strip()}')
                    break
