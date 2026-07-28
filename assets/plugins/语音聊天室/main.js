function getConfig() {
  try {
    var cfg = (typeof __OI_PLUGIN_CONFIG !== 'undefined') ? __OI_PLUGIN_CONFIG
          : ((typeof __AGORA_PLUGIN_CONFIG !== 'undefined') ? __AGORA_PLUGIN_CONFIG : undefined);
    if (typeof cfg === 'object' && cfg) return cfg;
    if (typeof cfg === 'string') return JSON.parse(cfg);
  } catch (e) {}
  return {};
}

exports.open_voice_room = function(params) {
  var cfg = getConfig();
  var hasTTS = cfg.tts_provider && cfg.tts_key && cfg.tts_voice_id;
  return {
    success: true,
    data: {
      message: '语音聊天室已准备好，请点击插件列表的 🌐 图标进入。',
      has_tts: !!hasTTS,
      hint: hasTTS
        ? 'TTS 已配置，AI 回复将自动语音朗读。消息与主聊天互通。'
        : 'TTS 未配置，语音聊天室将只显示文字。消息仍与主聊天互通。'
    }
  };
};
