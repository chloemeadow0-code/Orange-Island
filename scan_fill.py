files = [
    'app/src/main/java/com/orangeisland/app/ui/chat/ChatTopBar.kt',
    'app/src/main/java/com/orangeisland/app/ui/chat/search/DrawerSearchBar.kt',
    'app/src/main/java/com/orangeisland/app/ui/settings/PillTabSwitcher.kt',
    'app/src/main/java/com/orangeisland/app/ui/onboarding/WelcomeScreen.kt',
]
for f in files:
    with open(f, 'r', encoding='utf-8', errors='replace') as fh:
        for i, line in enumerate(fh, 1):
            if 'fillMaxHeight' in line or 'fillMaxSize' in line:
                print(f'{f}:{i}: {line.strip()}')
