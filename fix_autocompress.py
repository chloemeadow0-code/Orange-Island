import sys

path = 'app/src/main/java/com/orangeisland/app/viewmodel/MessageGenerationController.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old = 'if (settings.autoCompressEnabled.value) {'
new = 'if (settings.autoCompressModel.value != null) {'

if old in content:
    content = content.replace(old, new)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Replaced successfully')
else:
    print('String not found!')
