// Orange Island (橘子岛) — Review quiz logic

(function () {
  const cfg = window.ORANGE_ISLAND_CONFIG || {};
  const QUESTION_COUNT = cfg.QUESTION_COUNT || 25;
  const TIME_LIMIT_MINUTES = cfg.TIME_LIMIT_MINUTES || 20;
  const PASS_THRESHOLD = cfg.PASS_THRESHOLD || 1.0;

  const els = {
    loading: document.getElementById('quiz-loading'),
    stepLogin: document.getElementById('step-login'),
    stepQuiz: document.getElementById('step-quiz'),
    stepResult: document.getElementById('step-result'),
    qqInput: document.getElementById('qq-input'),
    startBtn: document.getElementById('start-btn'),
    submitBtn: document.getElementById('submit-btn'),
    restartBtn: document.getElementById('restart-btn'),
    questionsEl: document.getElementById('questions'),
    timerEl: document.getElementById('timer'),
    progressEl: document.getElementById('progress-bar'),
    resultBox: document.getElementById('result-box'),
    alertBox: document.getElementById('quiz-alert'),
  };

  let questions = [];
  let qqNumber = '';
  let timerInterval = null;
  let timeRemainingSeconds = TIME_LIMIT_MINUTES * 60;

  // ── Helpers ────────────────────────────────────────────────────────────────

  function showStep(name) {
    [els.stepLogin, els.stepQuiz, els.stepResult].forEach((el) =>
      el.classList.remove('active')
    );
    const target =
      name === 'login'
        ? els.stepLogin
        : name === 'quiz'
        ? els.stepQuiz
        : els.stepResult;
    target.classList.add('active');
  }

  function showAlert(message, type = 'error') {
    els.alertBox.className = `alert alert-${type} show`;
    els.alertBox.textContent = message;
  }

  function hideAlert() {
    els.alertBox.className = 'alert';
    els.alertBox.textContent = '';
  }

  function setLoading(isLoading, btn = null) {
    if (btn) {
      btn.disabled = isLoading;
      btn.dataset.originalText = btn.dataset.originalText || btn.textContent;
      btn.innerHTML = isLoading
        ? '<span class="loading"></span> 请稍候...'
        : btn.dataset.originalText;
    }
    if (els.loading) {
      els.loading.style.display = isLoading ? 'block' : 'none';
    }
  }

  function shuffleArray(arr) {
    const copy = arr.slice();
    for (let i = copy.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [copy[i], copy[j]] = [copy[j], copy[i]];
    }
    return copy;
  }

  function formatTime(totalSeconds) {
    const m = Math.floor(totalSeconds / 60)
      .toString()
      .padStart(2, '0');
    const s = (totalSeconds % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  }

  // ── Step 1: Login / QQ check ───────────────────────────────────────────────

  async function handleStart() {
    hideAlert();

    if (!window.OrangeIslandSupabase) {
      showAlert('Supabase 客户端未初始化。请检查 assets/supabase.min.js 和 config.js 是否加载成功。', 'error');
      return;
    }

    const raw = els.qqInput.value.trim();
    if (!/^\d{5,11}$/.test(raw)) {
      showAlert('请输入有效的 QQ 号码（5-11 位数字）。');
      return;
    }

    qqNumber = raw;
    setLoading(true, els.startBtn);

    try {
      const already = await window.OrangeIslandSupabase.hasSubmitted(qqNumber);
      if (already) {
        showAlert('该 QQ 号码已经参与过审核，不能重复提交。', 'error');
        setLoading(false, els.startBtn);
        return;
      }

      questions = await window.OrangeIslandSupabase.getQuestions(QUESTION_COUNT);
      if (!questions.length) {
        showAlert('题库暂时为空，请稍后再试。', 'error');
        setLoading(false, els.startBtn);
        return;
      }

      renderQuestions();
      showStep('quiz');
      startTimer();
    } catch (err) {
      console.error(err);
      showAlert('加载失败：' + (err.message || '网络错误'), 'error');
    } finally {
      setLoading(false, els.startBtn);
    }
  }

  // ── Step 2: Render questions ─────────────────────────────────────────────

  function renderQuestions() {
    els.questionsEl.innerHTML = '';
    questions.forEach((q, idx) => {
      const options = shuffleArray(q.options || []);

      const card = document.createElement('div');
      card.className = 'question-card';
      card.dataset.id = q.id;

      const number = document.createElement('div');
      number.className = 'question-number';
      number.textContent = `第 ${idx + 1} / ${questions.length} 题`;

      const text = document.createElement('div');
      text.className = 'question-text';
      text.textContent = q.question;

      const opts = document.createElement('div');
      opts.className = 'options';

      options.forEach((opt) => {
        const label = document.createElement('label');
        const input = document.createElement('input');
        input.type = 'radio';
        input.name = `q-${q.id}`;
        input.value = opt.label;
        input.required = true;

        const spanLabel = document.createElement('span');
        spanLabel.className = 'option-label';
        spanLabel.textContent = opt.label + '.';

        const spanText = document.createElement('span');
        spanText.textContent = opt.text;

        label.appendChild(input);
        label.appendChild(spanLabel);
        label.appendChild(spanText);
        opts.appendChild(label);
      });

      card.appendChild(number);
      card.appendChild(text);
      card.appendChild(opts);
      els.questionsEl.appendChild(card);
    });
  }

  function updateProgress() {
    const total = questions.length;
    const answered = document.querySelectorAll(
      'input[type="radio"]:checked'
    ).length;
    const pct = total ? Math.round((answered / total) * 100) : 0;
    els.progressEl.style.width = pct + '%';
  }

  // ── Timer ─────────────────────────────────────────────────────────────────

  function startTimer() {
    timeRemainingSeconds = TIME_LIMIT_MINUTES * 60;
    els.timerEl.textContent = formatTime(timeRemainingSeconds);
    els.timerEl.classList.remove('warning');

    timerInterval = setInterval(() => {
      timeRemainingSeconds--;
      els.timerEl.textContent = formatTime(timeRemainingSeconds);

      if (timeRemainingSeconds <= 300) {
        els.timerEl.classList.add('warning');
      }
      if (timeRemainingSeconds <= 0) {
        clearInterval(timerInterval);
        handleSubmit(true);
      }
    }, 1000);
  }

  function stopTimer() {
    if (timerInterval) {
      clearInterval(timerInterval);
      timerInterval = null;
    }
  }

  // ── Step 3: Submit ─────────────────────────────────────────────────────────

  async function handleSubmit(isTimeout = false) {
    hideAlert();

    const answers = {};
    let unanswered = 0;
    questions.forEach((q) => {
      const selected = document.querySelector(`input[name="q-${q.id}"]:checked`);
      if (selected) {
        answers[q.id] = selected.value;
      } else {
        unanswered++;
      }
    });

    if (!isTimeout && unanswered > 0) {
      showAlert(`还有 ${unanswered} 道题未作答，请完成后再提交。`, 'error');
      return;
    }

    setLoading(true, els.submitBtn);
    stopTimer();

    try {
      const result = await window.OrangeIslandSupabase.submitQuiz(
        qqNumber,
        answers,
        PASS_THRESHOLD
      );

      if (!result.success) {
        const msgMap = {
          already_submitted: '该 QQ 号码已经提交过，不能重复提交。',
          missing_qq_number: 'QQ 号码不能为空。',
          no_valid_questions: '没有有效题目，请刷新重试。',
        };
        showAlert(
          msgMap[result.error] || '提交失败：' + (result.error || '未知错误'),
          'error'
        );
        setLoading(false, els.submitBtn);
        return;
      }

      showResult(result);
    } catch (err) {
      console.error(err);
      showAlert('提交失败：' + (err.message || '网络错误'), 'error');
      setLoading(false, els.submitBtn);
    }
  }

  function showResult(result) {
    showStep('result');

    const isPass = result.passed;
    const titleClass = isPass ? 'result-pass' : 'result-fail';
    const titleText = isPass ? '✅ 审核通过' : '❌ 未通过审核';
    const message = isPass
      ? cfg.PASS_MESSAGE || '恭喜你通过审核！'
      : cfg.FAIL_MESSAGE || '未通过审核，请重新学习后再次申请。';

    els.resultBox.innerHTML = `
      <div class="result-box">
        <h2 class="${titleClass}">${titleText}</h2>
        <div class="result-score">
          得分：${result.score} / ${result.total}
        </div>
        <div class="mb-4">${message}</div>
        ${
          isPass
            ? `<button class="btn btn-primary" id="copy-qq-btn">复制 QQ 号：${qqNumber}</button>`
            : `<button class="btn btn-secondary" id="restart-btn">返回首页</button>`
        }
      </div>
    `;

    const copyBtn = document.getElementById('copy-qq-btn');
    if (copyBtn) {
      copyBtn.addEventListener('click', () => {
        navigator.clipboard.writeText(qqNumber).then(() => {
          copyBtn.textContent = '已复制';
          setTimeout(() => {
            copyBtn.textContent = `复制 QQ 号：${qqNumber}`;
          }, 1500);
        });
      });
    }

    const restart = document.getElementById('restart-btn');
    if (restart) {
      restart.addEventListener('click', () => {
        window.location.href = 'index.html';
      });
    }
  }

  // ── Init ──────────────────────────────────────────────────────────────────

  function init() {
    if (!els.startBtn || !els.qqInput) return;

    els.startBtn.addEventListener('click', handleStart);
    els.qqInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') handleStart();
    });

    if (els.submitBtn) {
      els.submitBtn.addEventListener('click', () => handleSubmit(false));
    }

    if (els.questionsEl) {
      els.questionsEl.addEventListener('change', updateProgress);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
