import os, re

root = 'app/src/main/java'
patterns = [
    (r'\bchatFontScale\s*=\s*', 'chatFontScale assignment'),
    (r'\bfontScale\s*=\s*', 'fontScale assignment'),
]

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
