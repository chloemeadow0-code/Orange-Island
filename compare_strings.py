import os
import re
import xml.etree.ElementTree as ET

BASE_DIR = "app/src/main/res"
ENGLISH_FILE = os.path.join(BASE_DIR, "values/strings.xml")

LANGUAGES = [
    "values-zh",
    "values-zh-rTW",
    "values-ar",
    "values-de",
    "values-es",
    "values-fr",
    "values-ja",
    "values-ko",
    "values-pt-rBR",
    "values-ru",
]

def extract_keys(filepath):
    """Extract all string name keys from an XML file."""
    keys = set()
    try:
        tree = ET.parse(filepath)
        root = tree.getroot()
        for elem in root:
            if elem.tag == 'string':
                name = elem.get('name')
                if name:
                    keys.add(name)
    except Exception as e:
        print(f"Error parsing {filepath}: {e}")
    return keys

english_keys = extract_keys(ENGLISH_FILE)
print(f"English keys: {len(english_keys)}")
print()

for lang in LANGUAGES:
    filepath = os.path.join(BASE_DIR, lang, "strings.xml")
    if not os.path.exists(filepath):
        print(f"{lang}: FILE NOT FOUND")
        continue
    lang_keys = extract_keys(filepath)
    missing = sorted(english_keys - lang_keys)
    orphaned = sorted(lang_keys - english_keys)
    print(f"=== {lang} ===")
    print(f"  Total keys: {len(lang_keys)}")
    print(f"  Missing: {len(missing)}")
    if missing:
        for k in missing:
            print(f"    - {k}")
    print(f"  Orphaned (in {lang} but not English): {len(orphaned)}")
    if orphaned:
        for k in orphaned:
            print(f"    - {k}")
    print()
