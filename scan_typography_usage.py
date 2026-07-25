import os, re

root = 'app/src/main/java'
searches = [
    'displayLarge',
    'headlineLarge',
    'titleLarge',
    'bodyLarge',
    'labelLarge',
]

for term in searches:
    print(f'\n=== {term} ===')
    for dirpath, dirnames, filenames in os.walk(root):
        for fn in filenames:
            if not fn.endswith('.kt'):
                continue
            path = os.path.join(dirpath, fn)
            with open(path, 'r', encoding='utf-8', errors='replace') as f:
                lines = f.readlines()
            for i, line in enumerate(lines, 1):
                if term in line:
                    print(f'  {path}:{i}: {line.strip()}')
