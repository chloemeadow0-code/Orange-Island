import os, re

root = 'app/src/main/java'
height_re = re.compile(r'\.height\(\s*([\d_]+)\.dp\s*\)')
text_re = re.compile(r'\b(Text|stringResource|Button|TextButton|FilledTonalButton|OutlinedButton|ElevatedButton|FilterChip|SuggestionChip|InputChip|AssistChip|DropdownMenuItem|NavigationDrawerItem|ListItem|Checkbox|RadioButton|Switch|OutlinedTextField|TextField|BasicTextField|TopAppBar|NavigationBar|Tab|BottomAppBar|Row)\b')
skip_re = re.compile(r'(Spacer\(|LinearProgressIndicator|CircularProgressIndicator|StepsBarChart|HeartRateChart|SleepBarChart|VideoSlice|HtmlCodePreview|Image\(|AsyncImage\(|Icon\()')

matches = []
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
            if skip_re.search(line):
                continue
            start = max(0, i - 8)
            end = min(len(lines), i + 8)
            context = ''.join(lines[start:end])
            if text_re.search(context):
                matches.append((path, i, line.strip()))

for path, i, line in matches:
    print(f'{path}:{i}: {line}')
print(f'\nTotal: {len(matches)}')
