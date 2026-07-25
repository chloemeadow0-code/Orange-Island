import os, re

root = 'app/src/main/java'
height_re = re.compile(r'\.height\(\s*([\d_]+)\.dp\s*\)')
text_re = re.compile(r'\b(Text|stringResource|TextButton|Button|FilledTonalButton|OutlinedButton|ElevatedButton|FilterChip|SuggestionChip|InputChip|AssistChip|DropdownMenuItem|NavigationDrawerItem|ListItem|Checkbox|RadioButton|Switch|OutlinedTextField|TextField|BasicTextField|topAppBar|TopAppBar|NavigationBar|navigationBarItem|Tab|LeadingIconTab|TabRow|BottomAppBar)\b')

for dirpath, dirnames, filenames in os.walk(root):
    for fn in filenames:
        if not fn.endswith('.kt'):
            continue
        path = os.path.join(dirpath, fn)
        with open(path, 'r', encoding='utf-8', errors='replace') as f:
            lines = f.readlines()
        for i, line in enumerate(lines, 1):
            m = height_re.search(line)
            if not m:
                continue
            # Skip Spacer (explicit spacing, no text)
            if 'Spacer(' in line or 'Spacer (' in line:
                continue
            # Look at nearby lines for text-related tokens
            start = max(0, i - 10)
            end = min(len(lines), i + 10)
            context = ''.join(lines[start:end])
            if text_re.search(context):
                print(f'{path}:{i}: {line.strip()}')
                # also print a bit of context
                for j in range(start, end):
                    marker = '>>> ' if j == i - 1 else '    '
                    print(f'{marker}{j+1}: {lines[j].rstrip()}')
                print()
