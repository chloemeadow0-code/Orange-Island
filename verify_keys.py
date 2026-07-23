import os
langs = ['values', 'values-zh', 'values-zh-rTW', 'values-ar', 'values-de', 'values-es', 'values-fr', 'values-ja', 'values-ko', 'values-pt-rBR', 'values-ru']
keys = ['about_developer_name_line1', 'about_developer_name_line2', 'about_licenses', 'about_oss_notice_title', 'about_oss_notice_body', 'mit_license_full', 'about_third_party_licenses', 'about_third_party_licenses_desc']
for lang in langs:
    path = f'app/src/main/res/{lang}/strings.xml'
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    missing = [k for k in keys if k not in content]
    old = 'about_developer_name"' in content
    status = 'OK' if not missing else 'MISSING: ' + str(missing)
    old_status = 'OLD KEY EXISTS' if old else 'old removed'
    print(lang + ': ' + status + ' | ' + old_status)
