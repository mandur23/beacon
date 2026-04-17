// ── Clock ─────────────────────────────────────────────────────────────
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

// ── Sidebar Toggle ─────────────────────────────────────────────────────
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const mainWrap = document.getElementById('mainWrap');
    if (sidebar) sidebar.classList.toggle('collapsed');
}

// ── Canvas Helper ──────────────────────────────────────────────────────
function getCtx(id) {
    const el = document.getElementById(id);
    if (!el) return null;
    const ctx = el.getContext('2d');
    if (!ctx) return null;
    
    // Handle Retina displays
    const dpr = window.devicePixelRatio || 1;
    const rect = el.getBoundingClientRect();
    el.width = rect.width * dpr;
    el.height = rect.height * dpr;
    ctx.scale(dpr, dpr);
    el.style.width = `${rect.width}px`;
    el.style.height = `${rect.height}px`;
    
    return ctx;
}

// ── Line Chart ─────────────────────────────────────────────────────────
function drawLineChart(id, data, currentHour) {
    const ctx = getCtx(id);
    if (!ctx) return;
    const canvas = ctx.canvas;
    const w = canvas.clientWidth, h = canvas.clientHeight;

    const max = Math.max(...data, 1);
    const pts = data.map((v, i) => ({
        x: (i / (data.length - 1)) * w,
        y: h - (v / max) * (h - 20) - 10
    }));

    // Area Gradient
    const grad = ctx.createLinearGradient(0, 0, 0, h);
    grad.addColorStop(0, 'rgba(0, 242, 255, 0.15)');
    grad.addColorStop(1, 'rgba(0, 242, 255, 0)');
    
    ctx.beginPath();
    ctx.moveTo(0, h);
    pts.forEach(p => ctx.lineTo(p.x, p.y));
    ctx.lineTo(w, h);
    ctx.fillStyle = grad;
    ctx.fill();

    // Line
    ctx.beginPath();
    pts.forEach((p, i) => i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y));
    ctx.strokeStyle = '#00f2ff';
    ctx.lineWidth = 3;
    ctx.lineJoin = 'round';
    ctx.stroke();

    // Current Highlight
    if (currentHour >= 0 && currentHour < pts.length) {
        const p = pts[currentHour];
        ctx.beginPath();
        ctx.arc(p.x, p.y, 6, 0, Math.PI * 2);
        ctx.fillStyle = '#ff2d55';
        ctx.strokeStyle = '#fff';
        ctx.lineWidth = 2;
        ctx.fill();
        ctx.stroke();
    }
}

// ── Bar Chart ──────────────────────────────────────────────────────────
function drawBarChart(id, data, currentHour) {
    const ctx = getCtx(id);
    if (!ctx) return;
    const w = ctx.canvas.clientWidth, h = ctx.canvas.clientHeight;

    const max = Math.max(...data, 1);
    const barW = (w / data.length) * 0.6;
    const gap = (w / data.length) * 0.4;

    data.forEach((v, i) => {
        const barH = (v / max) * (h - 10);
        const x = i * (w / data.length) + gap / 2;
        const y = h - barH;
        
        ctx.fillStyle = (i === currentHour) ? '#00f2ff' : 'rgba(255, 255, 255, 0.05)';
        ctx.beginPath();
        if (ctx.roundRect) {
            ctx.roundRect(x, y, barW, barH, [4, 4, 0, 0]);
        } else {
            ctx.rect(x, y, barW, barH);
        }
        ctx.fill();
    });
}

// ── Gauge Chart ────────────────────────────────────────────────────────
function drawGauge(id, value) {
    const ctx = getCtx(id);
    if (!ctx) return;
    const w = ctx.canvas.clientWidth, h = ctx.canvas.clientHeight;
    const cx = w / 2, cy = h * 0.7;
    const r = Math.min(w, h) * 0.4;
    
    const startAngle = Math.PI * 0.8;
    const endAngle = Math.PI * 2.2;
    const valAngle = startAngle + (endAngle - startAngle) * (value / 100);

    // Track
    ctx.beginPath();
    ctx.arc(cx, cy, r, startAngle, endAngle);
    ctx.strokeStyle = 'rgba(255,255,255,0.05)';
    ctx.lineWidth = 14;
    ctx.lineCap = 'round';
    ctx.stroke();

    // Progress
    const grad = ctx.createLinearGradient(cx - r, 0, cx + r, 0);
    grad.addColorStop(0, '#7000ff');
    grad.addColorStop(1, '#00f2ff');
    
    ctx.beginPath();
    ctx.arc(cx, cy, r, startAngle, valAngle);
    ctx.strokeStyle = grad;
    ctx.lineWidth = 14;
    ctx.lineCap = 'round';
    ctx.stroke();

    // Value Text
    ctx.fillStyle = '#fff';
    ctx.font = "bold 36px 'Outfit'";
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(value, cx, cy - 10);

    ctx.font = "600 12px 'Inter', 'Noto Sans KR'";
    ctx.fillStyle = 'rgba(255,255,255,0.4)';
    ctx.fillText("보안 지수 (SCORE)", cx, cy + 24);
}

// ── Polling & Integration ──────────────────────────────────────────────
async function pollStats() {
    try {
        const res = await fetch('/api/system/stats');
        if (!res.ok) {
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        const d = await res.json();
        
        const cpuBar = document.getElementById('sysCpuBar');
        const memBar = document.getElementById('sysMemBar');
        
        if (cpuBar) cpuBar.style.width = d.cpu + '%';
        if (memBar) memBar.style.width = d.mem + '%';
    } catch (e) {
        console.error('시스템 통계 조회 실패:', e.message);
        // 사용자에게 알림 (옵션)
        // showSecurityToast('연결 오류', '시스템 통계를 가져올 수 없습니다.', 'warning');
    }
}

// ── Visual Animations ────────────────────────────────────────────────
function animateKPIs() {
    const rings = document.querySelectorAll('.kpi-ring');
    rings.forEach(ring => {
        const valEl = ring.closest('.ring-wrap')?.querySelector('.ring-val');
        if (!valEl) return;
        
        const valText = valEl.textContent || "0";
        const valNum = parseInt(valText.replace(/[^0-9]/g, '')) || 75; // Default 75 if not numeric
        
        // circumference is 2 * PI * 29 ~= 182
        const offset = 182 - (182 * (Math.min(valNum, 100) / 100));
        ring.style.strokeDashoffset = offset;
    });
}

// ── Security Alert Toast ──────────────────────────────────────────────
function showSecurityToast(title, message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast-card ${type}`;
    
    // Icon selection
    let icon = 'info';
    if (type === 'high') icon = 'alert-octagon';
    if (type === 'neural') icon = 'brain';
    if (type === 'warning') icon = 'alert-triangle';

    toast.innerHTML = `
        <div class="icon-box" style="width:32px; height:32px; font-size:16px;">
            <i data-lucide="${icon}"></i>
        </div>
        <div class="toast-body">
            <div class="toast-title">${title}</div>
            <div class="toast-text">${message}</div>
        </div>
    `;

    container.appendChild(toast);
    if (typeof lucide !== 'undefined') lucide.createIcons();

    // Remove after 5 seconds
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(100%)';
        toast.style.transition = 'all 0.5s ease';
        setTimeout(() => toast.remove(), 500);
    }, 5000);
}

// Global exposure
window.showSecurityToast = showSecurityToast;

// ── Live Threat Polling ──────────────────────────────────────────────
let lastSeenEventId = null;

async function pollSecurityEvents() {
    try {
        const res = await fetch('/api/security-events?size=5&sortBy=id&direction=DESC');
        if (!res.ok) {
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        
        const data = await res.json();
        const events = data.content || [];
        
        if (events.length > 0) {
            // Initial load - don't toast everything
            if (lastSeenEventId === null) {
                lastSeenEventId = events[0].id;
                return;
            }

            // Find new events
            const newEvents = events.filter(e => e.id > lastSeenEventId).reverse();
            newEvents.forEach(e => {
                let type = 'info';
                if (e.severityLabel === 'HIGH' || e.severityLabel === 'CRITICAL') type = 'high';
                if (e.type === 'BEHAVIORAL' || e.type === 'BIOMETRIC_ANOMALY') type = 'neural';
                
                const title = `[${e.severityLabel}] 위협 탐지됨`;
                const message = `${e.agentName}: ${e.type} - ${e.description}`;
                showSecurityToast(title, message, type);
            });

            if (newEvents.length > 0) {
                lastSeenEventId = events[0].id;
            }
        }
    } catch (e) {
        console.error("보안 이벤트 폴링 실패:", e.message);
    }
}

// ── Interval Management (메모리 누수 방지) ──────────────────────────
let statsInterval = null;
let eventsInterval = null;
let heroInterval = null;

function startPolling() {
    // 기존 interval 정리
    stopPolling();
    
    // 새로운 interval 시작
    statsInterval = setInterval(pollStats, 5000);
    eventsInterval = setInterval(pollSecurityEvents, 3000);
    
    // Periodically re-trigger scan-line for "alive" feel
    heroInterval = setInterval(() => {
        const hero = document.querySelector('.hero-scan-line');
        if (hero) {
            hero.style.animation = 'none';
            void hero.offsetWidth; // Reflow
            hero.style.animation = 'scan 3s infinite linear';
        }
    }, 10000);
}

function stopPolling() {
    if (statsInterval) {
        clearInterval(statsInterval);
        statsInterval = null;
    }
    if (eventsInterval) {
        clearInterval(eventsInterval);
        eventsInterval = null;
    }
    if (heroInterval) {
        clearInterval(heroInterval);
        heroInterval = null;
    }
}

// Initial Run
window.addEventListener('load', () => {
    animateKPIs();
    startPolling();
    
    // Welcome Toast
    setTimeout(() => showSecurityToast("보안 관제 시작", "지능형 에이전트가 실시간 위협 보고를 시작했습니다.", "info"), 1000);
});

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    stopPolling();
});
