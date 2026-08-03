// Orange Island (橘子岛) — Supabase client wrapper for the review quiz website.
// Uses the global config from config.js.

(function () {
  const cfg = window.ORANGE_ISLAND_CONFIG || {};
  const SUPABASE_URL = cfg.SUPABASE_URL || '';
  const SUPABASE_ANON_KEY = cfg.SUPABASE_ANON_KEY || '';

  // If the Supabase library failed to load, expose an error object instead of crashing.
  if (typeof supabase === 'undefined' || !supabase.createClient) {
    window.OrangeIslandSupabase = {
      _error: 'Supabase JS library 未加载。请检查 assets/supabase.min.js 是否存在。',
      async getQuestions() { throw new Error(this._error); },
      async hasSubmitted() { throw new Error(this._error); },
      async submitQuiz() { throw new Error(this._error); },
    };
    return;
  }

  // If the config still contains placeholders, expose a clear error.
  if (!SUPABASE_URL || SUPABASE_URL.includes('YOUR_PROJECT_ID') || !SUPABASE_ANON_KEY || SUPABASE_ANON_KEY.includes('YOUR_ANON_KEY')) {
    window.OrangeIslandSupabase = {
      _error: 'Supabase 配置未填写。请检查 website/assets/config.js。',
      async getQuestions() { throw new Error(this._error); },
      async hasSubmitted() { throw new Error(this._error); },
      async submitQuiz() { throw new Error(this._error); },
    };
    return;
  }

  const client = supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    auth: {
      autoRefreshToken: false,
      persistSession: false,
      detectSessionInUrl: false,
    },
  });

  window.OrangeIslandSupabase = {
    client,

    /**
     * Fetch active questions from the server (randomized, no correct answers).
     * @param {number} limit
     */
    async getQuestions(limit) {
      const { data, error } = await client.rpc('get_active_questions', {
        p_limit: limit,
      });
      if (error) throw error;
      return data || [];
    },

    /**
     * Check whether a QQ number has already submitted.
     * @param {string} qqNumber
     */
    async hasSubmitted(qqNumber) {
      const { data, error } = await client.rpc('check_qq_submitted', {
        p_qq_number: qqNumber,
      });
      if (error) throw error;
      return data === true;
    },

    /**
     * Submit answers. Server scores and enforces the one-QQ-one-submission rule.
     * @param {string} qqNumber
     * @param {Object} answers — { questionId: 'A', ... }
     * @param {number} passThreshold — e.g. 0.96 means 24/25 correct
     */
    async submitQuiz(qqNumber, answers, passThreshold = 1.0) {
      const { data, error } = await client.rpc('submit_quiz', {
        p_qq_number: qqNumber,
        p_answers: answers,
        p_pass_threshold: passThreshold,
      });
      if (error) throw error;
      return data;
    },
  };
})();
