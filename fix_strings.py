import re

# Translations for each language
# about_developer_name_line1 and line2: "Original: newo-ether" / "Modified: 小橘、猫猫"
# mit_license_full is the same English MIT text in ALL languages (license text not translated)

MIT_TEXT = r'MIT License\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.'

translations = {
    'values-zh-rTW': {
        'about_developer': '開發者',
        'line1': '原創：newo-ether',
        'line2': '二改：小橘、貓貓',
        'licenses': '授權',
        'oss_title': '開源授權聲明',
        'oss_body': '本應用基於開源軟體二次修改而成，遵循 MIT 授權條款，原始版權聲明與授權全文見下方。本二改版本與原作者及原專案不存在隸屬關係，如使用中遇到問題，請透過本應用內的回饋管道聯絡我們，不要打擾原作者。',
        'third_party': '第三方開源授權',
        'third_party_desc': '檢視本應用使用的所有第三方函式庫的開源授權',
    },
    'values-ar': {
        'about_developer': 'المطوّر',
        'line1': 'Original: newo-ether',
        'line2': 'Modified: 小橘、猫猫',
        'licenses': 'التراخيص',
        'oss_title': 'إشعار ترخيص المصدر المفتوح',
        'oss_body': 'هذا التطبيق مبني على برمجيات مفتوحة المصدر ومُعدّل بموجب ترخيص MIT. إشعار حقوق النشر الأصلي ونص الترخيص الكامل موضّح أدناه. هذا الإصدار المُعدّل غير تابع للمؤلف الأصلي أو المشروع الأصلي. إذا واجهت مشاكل، يرجى الاتصال بنا عبر قناة التعليقات داخل التطبيق — لا تتواصل مع المؤلف الأصلي.',
        'third_party': 'تراخيص المصادر المفتوحة للجهات الخارجية',
        'third_party_desc': 'عرض تراخيص جميع مكتبات الجهات الخارجية المستخدمة في هذا التطبيق',
    },
    'values-de': {
        'about_developer': 'Entwickler',
        'line1': 'Original: newo-ether',
        'line2': 'Modified: 小橘、猫猫',
        'licenses': 'Lizenzen',
        'oss_title': 'Open-Source-Lizenzhinweis',
        'oss_body': 'Diese Anwendung basiert auf Open-Source-Software und wurde unter der MIT-Lizenz modifiziert. Der urspruengliche Copyright-Hinweis und der vollstaendige Lizenztext sind unten angegeben. Diese modifizierte Version ist weder mit dem urspruenglichen Autor noch dem urspruenglichen Projekt verbunden. Bei Problemen wenden Sie sich bitte ueber den Feedback-Kanal der App an uns - nicht an den urspruenglichen Autor.',
        'third_party': 'Open-Source-Lizenzen von Drittanbietern',
        'third_party_desc': 'Lizenzen aller in dieser App verwendeten Drittanbieter-Bibliotheken anzeigen',
    },
    'values-es': {
        'about_developer': 'Desarrollador',
        'line1': 'Original: newo-ether',
        'line2': 'Modified: 小橘、猫猫',
        'licenses': 'Licencias',
        'oss_title': 'Aviso de licencia de codigo abierto',
        'oss_body': 'Esta aplicacion esta basada en software de codigo abierto y modificada bajo la Licencia MIT. El aviso de copyright original y el texto completo de la licencia se muestran a continuacion. Esta version modificada no esta afiliada con el autor original ni con el proyecto original. Si encuentra problemas, contactenos a traves del canal de comentarios de la aplicacion - no contacte al autor original.',
        'third_party': 'Licencias de codigo abierto de terceros',
        'third_party_desc': 'Ver las licencias de todas las bibliotecas de terceros utilizadas en esta aplicacion',
    },
    'values-fr': {
        'about_developer': 'Developpeur',
        'line1': 'Original: newo-ether',
        'line2': 'Modified: 小橘、猫猫',
        'licenses': 'Licences',
        'oss_title': 'Avis de licence open source',
        'oss_body': 'Cette application est basee sur un logiciel open source et modifiee sous la licence MIT. L\'avis de copyright original et le texte complet de la licence sont affiches ci-dessous. Cette version modifiee n\'est pas affiliee a l\'auteur original ni au projet original. En cas de probleme, contactez-nous via le canal de retour de l\'application - ne contactez pas l\'auteur original.',
        'third_party': 'Licences open source tierces',
        'third_party_desc': 'Afficher les licences de toutes les bibliotheques tierces utilisees dans cette application',
    },
    'values-ja': {
        'about_developer': '開発者',
        'line1': 'Original: newo-ether',
        'line2': 'Modified: 小橘、猫猫',
        'licenses': 'ライセンス',
        'oss_title': 'オープンソースライセンス通知',
        'oss_body': 'このアプリはオープンソースソフトウェアに基づき、MITライセンスに従って改変されています。元の著作権表示とライセンス全文は以下の通りです。この改変版は原作者または元のプロジェクトと提携関係にありません。問題が発生した場合は、アプリ内のフィードバックチャネルからお問い合わせください。原作者に連絡しないでください。',
        'third_party': 'サードパーティオープンソースライセンス',
        'third_party_desc': 'このアプリで使用されているすべてのサードパーティライブラリのライセンスを表示',
    },
    'values-ko': {
        'about_developer': '개발자',
        'line1': 'Original: newo-ether',
        'line2': 'Modified: 小橘、猫猫',
        'licenses': '라이선스',
        'oss_title': '오픈소스 라이선스 고지',
        'oss_body': '이 애플리케이션은 오픈소스 소프트웨어를 기반으로 MIT 라이선스에 따라 수정되었습니다. 원본 저작권 고지 및 전체 라이선스 텍스트는 아래에 표시됩니다. 이 수정 버전은 원저자 또는 원본 프로젝트와 제휴 관계가 없습니다. 문제가 발생하면 앱 내 피드백 채널을 통해 문의해 주세요. 원저자에게 연락하지 마세요.',
        'third_party': '서드파티 오픈소스 라이선스',
        'third_party_desc': '이 앱에 사용된 모든 서드파티 라이브러리의 라이선스 보기',
    },
    'values-pt-rBR': {
        'about_developer': 'Desenvolvedor',
        'line1': 'Original: newo-ether',
        'line2': 'Modified: 小橘、猫猫',
        'licenses': 'Licencas',
        'oss_title': 'Aviso de licenca de codigo aberto',
        'oss_body': 'Este aplicativo e baseado em software de codigo aberto e modificado sob a Licenca MIT. O aviso de copyright original e o texto completo da licenca sao exibidos abaixo. Esta versao modificada nao e afiliada ao autor original ou ao projeto original. Se encontrar problemas, entre em contato atraves do canal de feedback do aplicativo - nao contate o autor original.',
        'third_party': 'Licencas de codigo aberto de terceiros',
        'third_party_desc': 'Ver licencas de todas as bibliotecas de terceiros usadas neste aplicativo',
    },
    'values-ru': {
        'about_developer': 'Разработчик',
        'line1': 'Original: newo-ether',
        'line2': 'Modified: 小橘、猫猫',
        'licenses': 'Лицензии',
        'oss_title': 'Уведомление о лицензии с открытым исходным кодом',
        'oss_body': 'Это приложение основано на программном обеспечении с открытым исходным кодом и модифицировано по лицензии MIT. Исходное уведомление об авторских правах и полный текст лицензии приведены ниже. Эта модифицированная версия не связана с оригинальным автором или оригинальным проектом. При возникновении проблем свяжитесь с нами через канал обратной связи в приложении — не обращайтесь к оригинальному автору.',
        'third_party': 'Лицензии сторонних разработчиков с открытым исходным кодом',
        'third_party_desc': 'Просмотреть лицензии всех сторонних библиотек, используемых в этом приложении',
    },
}

for lang, t in translations.items():
    filepath = f'app/src/main/res/{lang}/strings.xml'
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    new_strings_before_github = (
        f'    <string name="about_developer">{t["about_developer"]}</string>\n'
        f'    <string name="about_developer_name_line1">{t["line1"]}</string>\n'
        f'    <string name="about_developer_name_line2">{t["line2"]}</string>\n'
        f'    <string name="about_licenses">{t["licenses"]}</string>\n'
        f'    <string name="about_oss_notice_title">{t["oss_title"]}</string>\n'
        f'    <string name="about_oss_notice_body">{t["oss_body"]}</string>\n'
        f'    <string name="mit_license_full">{MIT_TEXT}</string>\n'
        f'    <string name="about_third_party_licenses">{t["third_party"]}</string>\n'
        f'    <string name="about_third_party_licenses_desc">{t["third_party_desc"]}</string>\n'
    )

    old_pattern = re.compile(
        r'    <string name="about_developer">[^<]*</string>\n'
        r'    <string name="about_developer_name">[^<]*</string>\n'
        r'    <string name="about_github">'
    )

    if old_pattern.search(content):
        content = old_pattern.sub(new_strings_before_github + '    <string name="about_github">', content)
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f'{lang}: replaced about_developer_name -> done')
    else:
        has_dev = '<string name="about_developer">' in content
        has_dev_name = '<string name="about_developer_name">' in content
        has_github = '<string name="about_github">' in content

        if has_dev and not has_dev_name and has_github:
            # Just insert new keys before about_github
            content = content.replace(
                '    <string name="about_github">',
                new_strings_before_github + '    <string name="about_github">',
                1
            )
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f'{lang}: inserted before about_github -> done')
        elif not has_dev and has_github:
            content = content.replace(
                '    <string name="about_github">',
                new_strings_before_github + '    <string name="about_github">',
                1
            )
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f'{lang}: inserted all before about_github -> done')
        elif not has_github:
            content = content.replace('</resources>', new_strings_before_github + '</resources>')
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f'{lang}: inserted before </resources> -> done')
        else:
            print(f'{lang}: UNKNOWN PATTERN - manual check needed')

print('\nAll 9 remaining language files processed.')
