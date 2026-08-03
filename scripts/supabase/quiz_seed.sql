-- ============================================================================
-- Orange Island (橘子岛) — Initial quiz question bank seed
-- ----------------------------------------------------------------------------
-- Run after quiz_schema.sql. Idempotent: uses ON CONFLICT DO NOTHING on id.
--
-- To refresh a question, either delete it first or update via Supabase Studio.
-- ============================================================================

insert into public.quiz_questions (id, question, options, correct_answer, explanation, order_index, is_active)
values
(
    '11111111-1111-1111-1111-111111111111',
    '橘子岛是什么类型的应用？',
    '[{"label":"A","text":"官方提供 AI 服务的聊天应用"},{"label":"B","text":"BYOK（Bring Your Own Key）AI 客户端"},{"label":"C","text":"免费的通用 AI 助手"},{"label":"D","text":"仅支持单一 AI 提供商的工具"}]'::jsonb,
    'B',
    '橘子岛本身不提供 AI 服务，需要用户自备 API Key。',
    1, true
),
(
    '22222222-2222-2222-2222-222222222222',
    '首次使用橘子岛前，用户必须自行准备什么？',
    '[{"label":"A","text":"橘子岛会员账号"},{"label":"B","text":"至少一个 AI 服务提供商的 API Key"},{"label":"C","text":"Google Play 付费订阅"},{"label":"D","text":"开发者邀请码"}]'::jsonb,
    'B',
    '用户需要自行准备 API Key，橘子岛不售卖或赠送 Key。',
    2, true
),
(
    '33333333-3333-3333-3333-333333333333',
    '模型 ID 的标准格式是什么？',
    '[{"label":"A","text":"model-id@provider"},{"label":"B","text":"ProviderName:model-id"},{"label":"C","text":"[provider]model-id"},{"label":"D","text":"model-id#provider"}]'::jsonb,
    'B',
    '例如 OpenAI:gpt-4o。',
    3, true
),
(
    '44444444-4444-4444-4444-444444444444',
    '要让某个模型出现在聊天界面的模型选择器中，需要先在哪个页面做什么操作？',
    '[{"label":"A","text":"Settings → Provider 中添加 Key"},{"label":"B","text":"Settings → Models 中启用该模型"},{"label":"C","text":"Settings → Language 中切换语言"},{"label":"D","text":"Settings → Appearance 中开启动态取色"}]'::jsonb,
    'B',
    '同步模型后需在 Models 页面手动启用。',
    4, true
),
(
    '55555555-5555-5555-5555-555555555555',
    '在橘子岛中，长按模型回复选择 Regenerate 会产生什么效果？',
    '[{"label":"A","text":"删除该回复并重新生成"},{"label":"B","text":"生成一个同级分支回复，原回复保留"},{"label":"C","text":"清空整个对话"},{"label":"D","text":"切换到下一个模型"}]'::jsonb,
    'B',
    'Regenerate 会创建同级分支，便于保留多个回答版本。',
    5, true
),
(
    '66666666-6666-6666-6666-666666666666',
    'Thinking（推理模式）的等级有几个选项？',
    '[{"label":"A","text":"2 个"},{"label":"B","text":"3 个"},{"label":"C","text":"4 个"},{"label":"D","text":"5 个"}]'::jsonb,
    'B',
    'Low / Medium / High 三个等级。',
    6, true
),
(
    '77777777-7777-7777-7777-777777777777',
    '默认的联网搜索提供商是哪个，且无需 API Key？',
    '[{"label":"A","text":"Brave Search"},{"label":"B","text":"Serper"},{"label":"C","text":"Tavily"},{"label":"D","text":"DuckDuckGo Lite"}]'::jsonb,
    'D',
    'DuckDuckGo Lite 为默认搜索，不需要 Key。',
    7, true
),
(
    '88888888-8888-8888-8888-888888888888',
    '代码执行基于什么本地技术？',
    '[{"label":"A","text":"Docker"},{"label":"B","text":"PRoot + Alpine Linux 沙盒"},{"label":"C","text":"Termux"},{"label":"D","text":"Android Virtual Device"}]'::jsonb,
    'B',
    '本地代码执行使用 PRoot + Alpine Linux 沙盒。',
    8, true
),
(
    '99999999-9999-9999-9999-999999999999',
    '如果代码执行失败且你正在使用 VPN，建议首先尝试什么操作？',
    '[{"label":"A","text":"重启手机"},{"label":"B","text":"关闭 VPN 后重试"},{"label":"C","text":"重新安装应用"},{"label":"D","text":"清除应用数据"}]'::jsonb,
    'B',
    'VPN/tun 网络可能与 PRoot 网络冲突。',
    9, true
),
(
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '使用 Conch 远程 Shell 前，必须先在什么位置部署 Conch 服务端？',
    '[{"label":"A","text":"橘子岛官方服务器"},{"label":"B","text":"用户自己的设备上"},{"label":"C","text":"Google Cloud"},{"label":"D","text":"应用内置沙盒中"}]'::jsonb,
    'B',
    'Conch 需要用户自己部署服务端。',
    10, true
),
(
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    'Conch 协议使用哪种对称加密算法？',
    '[{"label":"A","text":"AES-128-CBC"},{"label":"B","text":"AES-256-GCM"},{"label":"C","text":"ChaCha20-Poly1305"},{"label":"D","text":"RSA-2048"}]'::jsonb,
    'B',
    'Conch 使用 AES-256-GCM 对称加密。',
    11, true
),
(
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    '活跃记忆（Active Memory）保存在哪个文件中？',
    '[{"label":"A","text":"memory_db/active.md"},{"label":"B","text":"active_memory.md"},{"label":"C","text":"system_memory.md"},{"label":"D","text":"prompt_memory.md"}]'::jsonb,
    'B',
    'Active Memory 保存在 active_memory.md。',
    12, true
),
(
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    'RAG 语义搜索基于哪种相似度计算？',
    '[{"label":"A","text":"欧氏距离"},{"label":"B","text":"曼哈顿距离"},{"label":"C","text":"余弦相似度"},{"label":"D","text":"杰卡德相似度"}]'::jsonb,
    'C',
    'RAG 语义搜索基于余弦相似度。',
    13, true
),
(
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    '本地 GGUF 模型通过什么技术运行在 Android 设备上？',
    '[{"label":"A","text":"TensorFlow Lite"},{"label":"B","text":"ONNX Runtime"},{"label":"C","text":"llama.cpp"},{"label":"D","text":"PyTorch Mobile"}]'::jsonb,
    'C',
    '本地模型通过 llama.cpp 运行。',
    14, true
),
(
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    '同一时间可以加载几个本地聊天模型？',
    '[{"label":"A","text":"无限制"},{"label":"B","text":"最多 3 个"},{"label":"C","text":"2 个"},{"label":"D","text":"1 个"}]'::jsonb,
    'D',
    '同一时间只能加载一个本地模型。',
    15, true
),
(
    '11111111-1111-1111-1111-111111111112',
    '系统提示词模板最多分为几段？',
    '[{"label":"A","text":"1 段"},{"label":"B","text":"2 段"},{"label":"C","text":"3 段"},{"label":"D","text":"4 段"}]'::jsonb,
    'C',
    'System Prompt + User Prepend + User Append 共三段。',
    16, true
),
(
    '22222222-2222-2222-2222-222222222223',
    '导出 .橘子岛 文件时，以下哪项不是可选包含的类别？',
    '[{"label":"A","text":"对话记录"},{"label":"B","text":"API Key"},{"label":"C","text":"应用缓存"},{"label":"D","text":"系统提示词"}]'::jsonb,
    'C',
    '可导出对话、记忆、提示词、设置、API Key，不含应用缓存。',
    17, true
),
(
    '33333333-3333-3333-3333-333333333334',
    'Linear 工作流创建后，需要经过什么步骤才会真正启用？',
    '[{"label":"A","text":"自动立即启用"},{"label":"B","text":"用户审批"},{"label":"C","text":"重启手机"},{"label":"D","text":"付费解锁"}]'::jsonb,
    'B',
    'Linear 工作流创建后需要用户审批。',
    18, true
),
(
    '44444444-4444-4444-4444-444444444445',
    '工作流中支持的触发条件不包括以下哪一项？',
    '[{"label":"A","text":"收到通知"},{"label":"B","text":"地理围栏进入"},{"label":"C","text":"发送短信"},{"label":"D","text":"应用启动"}]'::jsonb,
    'C',
    '支持通知、地理围栏、应用启动等，但不支持发送短信作为触发条件。',
    19, true
),
(
    '55555555-5555-5555-5555-555555555556',
    '如果模型列表为空，以下哪一项不是推荐排查步骤？',
    '[{"label":"A","text":"检查是否已添加有效 API Key"},{"label":"B","text":"点击 Sync from All Providers"},{"label":"C","text":"在 Models 页面启用模型"},{"label":"D","text":"卸载并重新安装 Android 系统"}]'::jsonb,
    'D',
    '卸载重装 Android 系统不是推荐排查步骤。',
    20, true
),
(
    '66666666-6666-6666-6666-666666666667',
    '关于橘子岛的数据处理，以下说法正确的是？',
    '[{"label":"A","text":"橘子岛官方服务器会存储所有聊天记录"},{"label":"B","text":"数据只存在用户设备本地，但消息会发往用户配置的第三方 AI 提供商"},{"label":"C","text":"API Key 存储在云端以便多端同步"},{"label":"D","text":"导出 .橘子岛 文件默认不包含任何敏感信息"}]'::jsonb,
    'B',
    '橘子岛不收集数据，但消息会发往你配置的第三方 AI 提供商。',
    21, true
),
(
    '77777777-7777-7777-7777-777777777778',
    '关于 API Key 安全，以下哪项行为是明确被禁止的？',
    '[{"label":"A","text":"在 Settings → Provider 中输入 Key"},{"label":"B","text":"将 Key 截图分享到公开群聊"},{"label":"C","text":"使用 Android Keystore 加密存储"},{"label":"D","text":"为不同提供商分别配置 Key"}]'::jsonb,
    'B',
    '禁止将 Key 截图或公开分享。',
    22, true
),
(
    '88888888-8888-8888-8888-888888888889',
    '关于内测版本，以下哪项行为是允许的？',
    '[{"label":"A","text":"将 APK 上传到公开网盘供人下载"},{"label":"B","text":"在小红书发布内测功能截图"},{"label":"C","text":"个人设备上安装并测试"},{"label":"D","text":"将内测资格出租给他人"}]'::jsonb,
    'C',
    '内测版仅限个人设备测试，禁止二传、泄露、商业化、出租资格。',
    23, true
),
(
    '99999999-9999-9999-9999-99999999999a',
    '连续多少天不登录测试，可能被移出内测？',
    '[{"label":"A","text":"7 天"},{"label":"B","text":"14 天"},{"label":"C","text":"30 天"},{"label":"D","text":"60 天"}]'::jsonb,
    'B',
    '连续 14 天不登录可能被移出内测。',
    24, true
),
(
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab',
    '以下哪项不属于本规则明确禁止的行为？',
    '[{"label":"A","text":"对安装包进行反编译"},{"label":"B","text":"将内测版用于商业收费服务"},{"label":"C","text":"在反馈群提交有效 Bug 反馈"},{"label":"D","text":"未经授权控制第三方设备"}]'::jsonb,
    'C',
    '提交有效 Bug 反馈是被鼓励的行为。',
    25, true
)
on conflict (id) do update set
    question       = excluded.question,
    options        = excluded.options,
    correct_answer = excluded.correct_answer,
    explanation    = excluded.explanation,
    order_index    = excluded.order_index,
    is_active      = excluded.is_active;
