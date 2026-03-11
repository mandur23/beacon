// ── 시계 ─────────────────────────────────────────────────────────────
(function clock() {
    const dateEl = document.getElementById('headerDate');
    const timeEl = document.getElementById('headerTime');
    if (!dateEl || !timeEl) return;
    function tick() {
        const now = new Date();
        dateEl.textContent = now.toLocaleDateString('ko-KR', {
            year: 'numeric', month: 'long', day: 'numeric', weekday: 'short'
        }) + ' ';
        timeEl.textContent = now.toLocaleTimeString('ko-KR');
    }
    tick();
    setInterval(tick, 1000);
})();

// ── 사이드바 토글 ─────────────────────────────────────────────────────
function toggleSidebar() {
    document.getElementById('sidebar')?.classList.toggle('collapsed');
    document.getElementById('mainWrap')?.classList.toggle('sidebar-collapsed');
}

// ── 캔버스 헬퍼 ──────────────────────────────────────────────────────
function getCtx(id) {
    const el = document.getElementById(id);
    return el ? el.getContext('2d') : null;
}

// ── 24시간 라인 차트 ─────────────────────────────────────────────────
function drawLineChart(id, data, currentHour) {
    const ctx = getCtx(id);
    if (!ctx) return;
    const canvas = ctx.canvas;
    canvas.width = canvas.offsetWidth * devicePixelRatio || 600;
    const W = canvas.width, H = canvas.height * devicePixelRatio || 80;
    canvas.height = H;
    ctx.scale(devicePixelRatio, devicePixelRatio);
    const w = canvas.offsetWidth || 600, h = canvas.offsetHeight || 80;

    const max = Math.max(...data);
    const pts = data.map((v, i) => ({
        x: (i / (data.length - 1)) * w,
        y: h - (v / max) * (h - 8) - 4
    }));

    // 그라디언트 채우기
    const grad = ctx.createLinearGradient(0, 0, 0, h);
    grad.addColorStop(0, 'rgba(255,61,90,0.25)');
    grad.addColorStop(1, 'rgba(255,61,90,0)');
    ctx.beginPath();
    ctx.moveTo(0, h);
    pts.forEach(p => ctx.lineTo(p.x, p.y));
    ctx.lineTo(w, h);
    ctx.closePath();
    ctx.fillStyle = grad;
    ctx.fill();

    // 라인
    ctx.beginPath();
    pts.forEach((p, i) => i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y));
    ctx.strokeStyle = '#ff3d5a';
    ctx.lineWidth = 1.5;
    ctx.stroke();

    // 현재 시간 포인트
    if (currentHour >= 0 && currentHour < pts.length) {
        const cp = pts[currentHour];
        ctx.beginPath();
        ctx.arc(cp.x, cp.y, 4, 0, Math.PI * 2);
        ctx.fillStyle = '#ff3d5a';
        ctx.fill();
    }
}

// ── 바 차트 ──────────────────────────────────────────────────────────
function drawBarChart(id, data, currentHour) {
    const ctx = getCtx(id);
    if (!ctx) return;
    const canvas = ctx.canvas;
    canvas.width = canvas.offsetWidth * devicePixelRatio || 600;
    const W = canvas.width, H = canvas.height * devicePixelRatio || 80;
    canvas.height = H;
    ctx.scale(devicePixelRatio, devicePixelRatio);
    const w = canvas.offsetWidth || 600, h = canvas.offsetHeight || 80;

    const max = Math.max(...data);
    const barW = (w / data.length) * 0.7;
    const gap  = (w / data.length) * 0.3;

    data.forEach((v, i) => {
        const barH = (v / max) * (h - 6);
        const x = i * (w / data.length) + gap / 2;
        const y = h - barH;
        ctx.fillStyle = i === currentHour
            ? '#ff3d5a'
            : i < currentHour
            ? 'rgba(0,229,255,0.25)'
            : '#1e2235';
        ctx.beginPath();
        ctx.roundRect(x, y, barW, barH, [2, 2, 0, 0]);
        ctx.fill();
    });
}

// ── 게이지 ───────────────────────────────────────────────────────────
function drawGauge(id, value) {
    const ctx = getCtx(id);
    if (!ctx) return;
    const canvas = ctx.canvas;
    const w = canvas.width, h = canvas.height;
    const cx = w / 2, cy = h * 0.62;
    const r = Math.min(w, h) * 0.38;
    const startAngle = Math.PI * (1 + 1/6);
    const totalAngle = Math.PI * (5/3);
    const endAngle   = startAngle + totalAngle * (value / 100);
    const color = value >= 80 ? '#06d6a0' : value >= 60 ? '#ffd166' : '#ff3d5a';

    // 배경 호
    ctx.beginPath();
    ctx.arc(cx, cy, r, startAngle, startAngle + totalAngle);
    ctx.strokeStyle = '#1e2235';
    ctx.lineWidth = 12;
    ctx.lineCap = 'round';
    ctx.stroke();

    // 값 호
    ctx.beginPath();
    ctx.arc(cx, cy, r, startAngle, endAngle);
    ctx.strokeStyle = color;
    ctx.lineWidth = 12;
    ctx.lineCap = 'round';
    ctx.stroke();

    // 숫자
    ctx.fillStyle = '#ffffff';
    ctx.font = `bold ${Math.round(r * 0.42)}px 'Courier New', monospace`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(value, cx, cy - 4);

    ctx.fillStyle = '#4a5570';
    ctx.font = `${Math.round(r * 0.2)}px 'Courier New', monospace`;
    ctx.fillText('SECURITY SCORE', cx, cy + r * 0.28);

    ctx.fillStyle = color;
    ctx.font = `${Math.round(r * 0.22)}px 'Courier New', monospace`;
    ctx.fillText(value >= 80 ? '양호' : value >= 60 ? '주의' : '위험', cx, cy + r * 0.52);
}

// ── 월별 점수 차트 ───────────────────────────────────────────────────
function drawMonthlyChart(id, scores) {
    const ctx = getCtx(id);
    if (!ctx || !scores || !scores.length) return;
    const canvas = ctx.canvas;
    canvas.width = canvas.offsetWidth * devicePixelRatio || 600;
    const W = canvas.width, H = canvas.height * devicePixelRatio || 90;
    canvas.height = H;
    ctx.scale(devicePixelRatio, devicePixelRatio);
    const w = canvas.offsetWidth || 600, h = canvas.offsetHeight || 90;

    const min = 70, max = 100;
    const barW = (w / scores.length) * 0.6;
    const gap  = (w / scores.length) * 0.4;

    scores.forEach((v, i) => {
        const barH = ((v - min) / (max - min)) * (h - 10) + 8;
        const x = i * (w / scores.length) + gap / 2;
        const y = h - barH;
        const color = v >= 88 ? '#06d6a0' : v >= 83 ? '#00e5ff' : '#ffd166';
        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.roundRect(x, y, barW, barH, [3, 3, 0, 0]);
        ctx.fill();
    });
}

// ── 토스트 알림 ──────────────────────────────────────────────────────
function showToast(msg, type = 'info') {
    let el = document.getElementById('toast');
    if (!el) {
        el = document.createElement('div');
        el.id = 'toast';
        document.body.appendChild(el);
    }
    el.textContent = msg;
    el.className = 'show ' + type;
    clearTimeout(el._timer);
    el._timer = setTimeout(() => { el.className = ''; }, 3500);
}

// ── 시스템 리소스 실시간 폴링 ────────────────────────────────────────
function pollSystemStats() {
    fetch('/api/system/stats')
        .then(r => r.json())
        .then(d => {
            const cpuVal = document.getElementById('sysCpu');
            const memVal = document.getElementById('sysMem');
            const netVal = document.getElementById('sysNet');
            const cpuBar = document.getElementById('sysCpuBar');
            const memBar = document.getElementById('sysMemBar');
            const netBar = document.getElementById('sysNetBar');
            if (cpuVal) cpuVal.textContent = d.cpu + '%';
            if (memVal) memVal.textContent = d.mem + '%';
            if (netVal) netVal.textContent = d.net + '%';
            if (cpuBar) cpuBar.style.width = d.cpu + '%';
            if (memBar) memBar.style.width = d.mem + '%';
            if (netBar) netBar.style.width = d.net + '%';
        })
        .catch(() => {});
}

// ── 페이지 로드 시 캔버스 리사이즈 대응 ──────────────────────────────
window.addEventListener('load', () => {
    // drawLineChart / drawGauge 는 각 페이지 인라인 <script>에서 직접 호출
    pollSystemStats();
    setInterval(pollSystemStats, 5000);
});
