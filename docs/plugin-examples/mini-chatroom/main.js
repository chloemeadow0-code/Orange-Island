/**
 * 迷你聊天室 — QuickJS 工具
 *
 * 用户需在插件配置中填写：
 *   projectId      → 项目 ID（从项目编辑详情页复制）
 *   modelId        → 模型 ID（可选）
 *   systemPromptId → 提示词 ID（可选）
 *
 * 读取项目记忆：
 *   var cfg = __OI_PLUGIN_CONFIG;
 *   var memories = JSON.parse(await readProjectMemories(cfg.projectId));
 */

async function fetchProjectMemories(args) {
    var cfg = (typeof __OI_PLUGIN_CONFIG !== 'undefined') ? __OI_PLUGIN_CONFIG : {};
    var projectId = cfg.projectId || '';
    if (!projectId) {
        return { error: '未配置 projectId，请在插件设置中填写。' };
    }
    var raw = await readProjectMemories(projectId);
    var list = JSON.parse(raw);
    return {
        projectId: projectId,
        count: list.length,
        memories: list
    };
}

exports.fetch_project_memories = fetchProjectMemories;
