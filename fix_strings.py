import sys

filepath = 'app/src/main/res/values/strings.xml'

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

insert = '''    <string name="privacy_policy_title">用户协议与隐私声明</string>
    <string name="privacy_policy_body">欢迎使用橘子岛。\n\n1. 本应用使用设备本地存储保存您的对话记录与记忆内容，所有数据仅在您的设备上处理。\n2. 调用第三方大模型 API 时，仅传输必要的对话内容，我们不会收集或存储您的个人信息。\n3. 位置、通知、使用统计等敏感权限仅在您主动开启相关功能时申请，您可以随时在系统设置中撤回。\n4. 本应用提供的自动化工作流、UI 操作等功能仅供个人辅助使用，因使用不当造成的任何后果由用户自行承担。\n5. 未成年人应在监护人指导下使用本应用。\n\n点击同意即表示您已阅读并同意以上内容。如不同意，请退出应用。</string>
    <string name="privacy_policy_agree">同意</string>
    <string name="privacy_policy_disagree">不同意并退出</string>
'''

if 'privacy_policy_title' in content:
    print('Already exists')
    sys.exit(0)

content = content.replace('</resources>', insert + '</resources>')

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

print('Inserted')
