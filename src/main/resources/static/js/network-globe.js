/*
 * BEACON Network Globe — 실시간 글로벌 트래픽 시각화
 *
 * 외부 정적 파일로 분리되어 있다. (Thymeleaf 인라인 토큰 [[...]]
 * 충돌을 영구적으로 회피하려는 목적.)
 *
 * /api/network/globe?windowMinutes=N 을 5초 간격으로 폴링해
 * cities + links 를 받아 SVG 위에 그린다.
 */
(() => {
    const svg = document.getElementById('globeSvg');
    if (!svg) return;   // 다른 탭에서는 실행하지 않음

    // ─── 상수 ───────────────────────────────────────────────
    const R = 300;
    const ARC_LIFT = 0.55;
    const ARC_STEPS = 36;
    const POLL_INTERVAL_MS = 2000;     // 2초 폴링 (실시간 느낌)
    const WINDOW_MIN = 5;
    const LIVE_THRESHOLD_SEC = 30;     // ≤30s = live
    const STALE_THRESHOLD_SEC = 120;   // ≤120s = idle, 그 이상 = stale
    const BIRTH_PULSE_MS = 1200;       // 신규 링크 등장 시 펄스 지속

    const SEVERITY = {
        normal:   { color: '#00f2ff', weight: 1.3 },
        behavior: { color: '#c084fc', weight: 1.3 },
        warning:  { color: '#f59e0b', weight: 1.6 },
        threat:   { color: '#ef4444', weight: 1.8 },
    };

    // ─── 대륙 / 바다 마스크 (d3-geo CDN 실패 시 폴백) ──────
    const continents = [
        [60,72,-168,-140],[49,70,-140,-100],[49,62,-100,-55],[65,72,-100,-65],[45,50,-85,-55],
        [33,49,-123,-100],[26,49,-100,-75],[25,31,-88,-80],[16,33,-117,-88],[8,18,-92,-77],[18,23,-85,-74],
        [75,83,-55,-22],[65,78,-55,-25],[60,67,-52,-42],
        [2,12,-80,-60],[-2,5,-72,-50],[-12,0,-75,-47],[-23,-10,-72,-40],[-23,-5,-45,-34],
        [-35,-22,-65,-39],[-45,-32,-72,-56],[-55,-45,-75,-65],[-30,-18,-75,-67],[-18,0,-82,-72],
        [36,44,-10,4],[43,55,-5,20],[50,59,-8,2],[55,68,5,24],[65,71,20,31],[44,56,20,50],
        [56,68,24,60],[36,46,7,20],[39,47,20,30],[36,43,26,45],
        [18,37,-10,35],[20,32,-17,-2],[4,18,-18,16],[-5,5,8,30],[-12,-3,12,40],[4,18,30,52],
        [-22,-12,12,40],[-35,-22,15,33],[-26,-12,43,51],
        [14,32,34,55],[25,40,42,62],[30,42,62,75],[36,56,48,85],[30,45,75,100],[40,55,85,130],
        [8,32,68,88],[20,30,88,97],[10,24,92,108],[22,40,95,123],[33,43,124,131],[30,46,130,146],
        [50,75,60,180],[60,74,30,65],[65,72,175,180],
        [-6,6,95,106],[-4,7,109,119],[-9,-5,105,115],[-6,2,119,126],[-10,0,130,151],[5,19,117,126],[-11,-8,120,127],
        [-39,-12,113,154],[-44,-40,144,149],[-42,-34,172,179],[-47,-41,166,174],
        [-85,-68,-180,180],[-78,-64,-75,-55],
    ];
    const water = [
        [41,47,-90,-76],[19,30,-98,-85],[11,16,-85,-82],[30,46,12,22],[30,47,0,8],[30,40,18,36],
        [36,46,27,42],[37,45,46,55],[12,30,32,44],[22,30,50,58],[5,22,60,72],[18,24,88,93],
        [-10,8,100,119],[-18,-8,140,144],[-38,-30,136,140],[56,66,8,30],[51,60,-2,10],[60,70,30,45],
    ];
    const inBox = (lat, lon, b) => lat >= b[0] && lat <= b[1] && lon >= b[2] && lon <= b[3];
    function isLand(lat, lon) {
        for (const b of water) if (inBox(lat, lon, b)) return false;
        for (const b of continents) if (inBox(lat, lon, b)) return true;
        return false;
    }
    const landPoints = [];
    {
        const STEP = 2.0;
        for (let lat = -84; lat <= 84; lat += STEP) {
            const dlon = STEP / Math.max(0.18, Math.cos(lat * Math.PI / 180));
            for (let lon = -180; lon < 180; lon += dlon) {
                if (isLand(lat, lon)) landPoints.push([lat, lon]);
            }
        }
    }

    // ─── 회전 상태 ─────────────────────────────────────────
    let rotLon = -30;
    let rotLat = 12;

    function project(lat, lon) {
        const phi = lat * Math.PI / 180;
        const lambda = (lon + rotLon) * Math.PI / 180;
        const cphi = Math.cos(phi), sphi = Math.sin(phi);
        const cl = Math.cos(lambda), sl = Math.sin(lambda);
        const x0 = cphi * sl;
        const y0 = -sphi;
        const z0 = cphi * cl;
        const theta = rotLat * Math.PI / 180;
        const ct = Math.cos(theta), st = Math.sin(theta);
        const y = y0 * ct - z0 * st;
        const z = y0 * st + z0 * ct;
        return { x: R * x0, y: R * y, z: R * z };
    }
    function unit3(lat, lon) {
        const phi = lat * Math.PI / 180;
        const lambda = (lon + rotLon) * Math.PI / 180;
        const cphi = Math.cos(phi);
        const x0 = cphi * Math.sin(lambda);
        const y0 = -Math.sin(phi);
        const z0 = cphi * Math.cos(lambda);
        const theta = rotLat * Math.PI / 180;
        const ct = Math.cos(theta), st = Math.sin(theta);
        return [x0, y0 * ct - z0 * st, y0 * st + z0 * ct];
    }
    function slerp(a, b, t) {
        const dot = Math.max(-1, Math.min(1, a[0]*b[0] + a[1]*b[1] + a[2]*b[2]));
        const om = Math.acos(dot);
        if (om < 1e-6) return a.slice();
        const so = Math.sin(om);
        const s1 = Math.sin((1 - t) * om) / so;
        const s2 = Math.sin(t * om) / so;
        return [s1*a[0] + s2*b[0], s1*a[1] + s2*b[1], s1*a[2] + s2*b[2]];
    }
    function buildArc(c1, c2) {
        const a = unit3(c1.lat, c1.lon);
        const b = unit3(c2.lat, c2.lon);
        const dot = Math.max(-1, Math.min(1, a[0]*b[0] + a[1]*b[1] + a[2]*b[2]));
        const angle = Math.acos(dot);
        const liftScale = Math.min(1, angle / Math.PI) * ARC_LIFT + 0.05;
        const pts = [];
        for (let i = 0; i <= ARC_STEPS; i++) {
            const t = i / ARC_STEPS;
            const v = slerp(a, b, t);
            const h = Math.sin(t * Math.PI);
            const r = R * (1 + liftScale * h);
            pts.push({ x: v[0]*r, y: v[1]*r, z: v[2]*r, t });
        }
        return { pts, angle };
    }

    // ─── DOM 참조 ─────────────────────────────────────────
    const SVG_NS = 'http://www.w3.org/2000/svg';
    const stageEl    = document.getElementById('globeStage');
    const gGratFront = document.getElementById('g-graticule-front');
    const gGratBack  = document.getElementById('g-graticule-back');
    const gLandFront = document.getElementById('g-land-front');
    const gLandBack  = document.getElementById('g-land-back');
    const gArcs      = document.getElementById('g-arcs');
    const gNodes     = document.getElementById('g-nodes');
    const gPackets   = document.getElementById('g-packets');
    const labelsEl   = document.getElementById('globeLabels');
    const hint       = document.getElementById('dragHint');
    const clockEl    = document.getElementById('gClock');
    const logStrip   = document.getElementById('gLogStrip');
    const topLinksEl = document.getElementById('gTopLinks');
    const sActiveLinks = document.getElementById('gActiveLinks');
    const sLiveLinks   = document.getElementById('gLiveLinks');
    const sThroughput  = document.getElementById('gThroughput');
    const sThreats     = document.getElementById('gThreats');
    const sTelNodes    = document.getElementById('gTelNodes');
    const sTelWindow   = document.getElementById('gTelWindow');
    const sTelUpdated  = document.getElementById('gTelUpdated');
    const sLastSeen    = document.getElementById('gLastSeen');
    const sWindow      = document.getElementById('gWindow');
    if (sWindow) sWindow.textContent = WINDOW_MIN;
    if (sTelWindow) sTelWindow.textContent = `${WINDOW_MIN} min`;

    function el(tag, attrs) {
        const e = document.createElementNS(SVG_NS, tag);
        for (const k in attrs) e.setAttribute(k, attrs[k]);
        return e;
    }

    // ─── Graticule ────────────────────────────────────────
    function rebuildGraticule() {
        gGratFront.innerHTML = '';
        gGratBack.innerHTML = '';
        const drawSeg = (lat, lon, segsF, segsB, lastF, lastB) => {
            const p = project(lat, lon);
            if (p.z >= 0) {
                segsF[segsF.length - 1].push(p);
                if (lastF.v === false) segsB.push([]);
                lastF.v = true; lastB.v = false;
            } else {
                segsB[segsB.length - 1].push(p);
                if (lastB.v === false) segsF.push([]);
                lastB.v = true; lastF.v = false;
            }
        };
        for (let lon = -180; lon < 180; lon += 30) {
            const segsF = [[]], segsB = [[]];
            const lastF = { v: null }, lastB = { v: null };
            for (let lat = -90; lat <= 90; lat += 4) drawSeg(lat, lon, segsF, segsB, lastF, lastB);
            addPolylines(segsF, gGratFront, 'rgba(0,242,255,0.18)', 1);
            addPolylines(segsB, gGratBack,  'rgba(0,242,255,0.05)', 1);
        }
        for (let lat = -60; lat <= 60; lat += 30) {
            const segsF = [[]], segsB = [[]];
            const lastF = { v: null }, lastB = { v: null };
            for (let lon = -180; lon <= 180; lon += 4) drawSeg(lat, lon, segsF, segsB, lastF, lastB);
            addPolylines(segsF, gGratFront, 'rgba(0,242,255,0.16)', 1);
            addPolylines(segsB, gGratBack,  'rgba(0,242,255,0.04)', 1);
        }
        // 적도
        const segsF = [[]], segsB = [[]];
        const lastF = { v: null }, lastB = { v: null };
        for (let lon = -180; lon <= 180; lon += 3) drawSeg(0, lon, segsF, segsB, lastF, lastB);
        addPolylines(segsF, gGratFront, 'rgba(0,242,255,0.28)', 1.2);
        addPolylines(segsB, gGratBack,  'rgba(0,242,255,0.08)', 1);
    }
    function addPolylines(segs, parent, color, w) {
        for (const seg of segs) {
            if (seg.length < 2) continue;
            const d = 'M' + seg.map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' L');
            parent.appendChild(el('path', { d, stroke: color, 'stroke-width': w, fill: 'none' }));
        }
    }

    // ─── Land (d3-geo + world-atlas, dot fallback) ─────────
    let landFeature = null, bordersFeature = null, d3Projection = null;
    if (window.d3 && window.d3.geoOrthographic) {
        d3Projection = window.d3.geoOrthographic().scale(R).translate([0, 0]).clipAngle(90).precision(0.5);
    }
    function rebuildLand() {
        gLandFront.innerHTML = '';
        gLandBack.innerHTML  = '';
        if (landFeature && d3Projection && window.d3) {
            d3Projection.rotate([rotLon, rotLat, 0]);
            const pathGen = window.d3.geoPath(d3Projection);
            const dLand = pathGen(landFeature);
            if (dLand) {
                gLandFront.appendChild(el('path', { d: dLand, fill: 'none', stroke: 'rgba(0,242,255,0.45)', 'stroke-width': 2.4, 'stroke-linejoin': 'round', opacity: '0.6', filter: 'blur(2px)' }));
                gLandFront.appendChild(el('path', { d: dLand, fill: 'rgba(0,242,255,0.16)', stroke: 'rgba(0,242,255,0.78)', 'stroke-width': 0.7, 'stroke-linejoin': 'round', 'stroke-linecap': 'round' }));
            }
            if (bordersFeature) {
                const dBord = pathGen(bordersFeature);
                if (dBord) gLandFront.appendChild(el('path', { d: dBord, fill: 'none', stroke: 'rgba(0,242,255,0.28)', 'stroke-width': 0.4, 'stroke-linejoin': 'round' }));
            }
            return;
        }
        for (const [lat, lon] of landPoints) {
            const p = project(lat, lon);
            if (p.z >= 0) {
                const facing = p.z / R;
                const a = (0.45 + facing * 0.45).toFixed(3);
                gLandFront.appendChild(el('circle', { cx: p.x.toFixed(1), cy: p.y.toFixed(1), r: 1.7, fill: `rgba(0,242,255,${a})` }));
            } else {
                const a = ((-p.z / R) * 0.08 + 0.03).toFixed(3);
                gLandBack.appendChild(el('circle', { cx: p.x.toFixed(1), cy: p.y.toFixed(1), r: 0.9, fill: `rgba(0,242,255,${a})` }));
            }
        }
    }
    (function loadWorld() {
        if (!window.topojson || !window.d3) return;
        fetch('https://cdn.jsdelivr.net/npm/world-atlas@2/countries-110m.json')
            .then(r => r.json())
            .then(world => {
                landFeature    = topojson.feature(world, world.objects.land);
                bordersFeature = topojson.mesh(world, world.objects.countries, (a, b) => a !== b);
                lastBuildLon = null;
            })
            .catch(() => { /* dot fallback */ });
    })();

    // ─── 데이터 (API에서 채워짐) ───────────────────────────
    let cities = [];
    let links  = [];

    async function refreshData() {
        try {
            const resp = await fetch(`/api/network/globe?windowMinutes=${WINDOW_MIN}`, { credentials: 'same-origin' });
            if (!resp.ok) return;
            const data = await resp.json();
            applyData(data);
        } catch (err) {
            // 무음 실패: 기존 화면 유지
        }
    }

    function applyData(data) {
        cities = (data.cities || []).map(c => ({
            code:    c.code || 'UNK',
            name:    c.name || 'Unknown',
            country: c.country || '',
            lat:     +c.lat,
            lon:     +c.lon,
            local:   !!c.local,
            ipCount: c.ipCount || 1,
        }));

        const nowMs = performance.now();
        // 기존 링크 캐시 (a-b 키)
        const prevByKey = new Map(links.map(l => [`${l.a}-${l.b}`, l]));

        const next = [];
        for (const l of (data.links || [])) {
            if (l.a == null || l.b == null) continue;
            if (l.a >= cities.length || l.b >= cities.length) continue;

            const sev   = l.sev || 'normal';
            const lastSec = (typeof l.lastSec === 'number' && l.lastSec >= 0) ? l.lastSec : -1;
            const live   = lastSec >= 0 && lastSec <= LIVE_THRESHOLD_SEC;
            const idle   = lastSec >  LIVE_THRESHOLD_SEC && lastSec <= STALE_THRESHOLD_SEC;
            const stale  = lastSec >  STALE_THRESHOLD_SEC || lastSec < 0;

            // live는 빠르게, idle은 절반, stale은 거의 정지
            let speed;
            if (sev === 'threat')      speed = live ? 1.05 : idle ? 0.55 : 0.20;
            else if (sev === 'warning') speed = live ? 0.85 : idle ? 0.45 : 0.16;
            else                        speed = live ? (0.45 + Math.random() * 0.25)
                                                     : idle ? 0.30 : 0.12;

            // 패킷 개수: live면 sev에 비례, stale은 0 (정지된 라인)
            let packetCount;
            if (stale) packetCount = 0;
            else if (sev === 'threat') packetCount = live ? 3 : 2;
            else if (sev === 'warning') packetCount = 2;
            else packetCount = 1;

            const key = `${l.a}-${l.b}`;
            const prev = prevByKey.get(key);
            next.push({
                a: l.a, b: l.b, sev,
                bytes: l.bytes || 0,
                count: l.count || 0,
                anomaly: l.anomaly || 0,
                lastSec, live, idle, stale,
                durSec: l.durSec || 0,
                speed,
                phase: prev ? prev.phase : Math.random(),
                packetCount,
                // 신규 등장이면 birth 시각 기록 (펄스 애니메이션)
                bornAt: prev ? prev.bornAt : nowMs,
            });
        }
        links = next;

        const stats = data.stats || {};
        const liveLinks = stats.liveLinks ?? links.filter(l => l.live).length;
        const minLastSec = (typeof stats.minLastSec === 'number') ? stats.minLastSec : -1;

        if (sActiveLinks) sActiveLinks.textContent = String(stats.activeLinks ?? links.length).padStart(2, '0');
        if (sThroughput)  sThroughput.textContent  = stats.throughput || '— b/s';
        if (sThreats)     sThreats.textContent     = String(stats.threats ?? 0).padStart(2, '0');
        if (sTelNodes)    sTelNodes.textContent    = String(stats.nodes ?? cities.length);
        if (sTelUpdated)  sTelUpdated.textContent  = new Date().toLocaleTimeString();
        if (sLiveLinks)   sLiveLinks.textContent   = String(liveLinks).padStart(2, '0');
        if (sLastSeen)    sLastSeen.textContent    = formatLastSeen(minLastSec);
        if (sLastSeen)    sLastSeen.dataset.state  = liveLinks > 0 ? 'live' : (minLastSec >= 0 ? 'idle' : 'stale');

        rebuildLogStrip();
        rebuildTopLinks();
    }

    function formatLastSeen(sec) {
        if (sec == null || sec < 0) return '—';
        if (sec < 1) return 'now';
        if (sec < 60) return `${sec}s ago`;
        if (sec < 3600) return `${Math.floor(sec/60)}m ${sec%60}s ago`;
        return `${Math.floor(sec/3600)}h ago`;
    }

    function rebuildLogStrip() {
        if (!logStrip) return;
        if (links.length === 0) {
            logStrip.innerHTML = '<span class="log-item dim">최근 5분 트래픽 데이터가 없습니다.</span>';
            return;
        }
        const items = links.slice(0, 18).map(l => {
            const a = cities[l.a], b = cities[l.b];
            if (!a || !b) return '';
            const sevDot = l.sev === 'threat' ? 'danger' : l.sev === 'warning' ? 'warn' : l.sev === 'behavior' ? 'behavior' : '';
            const liveClass = l.live ? 'live' : l.idle ? 'idle' : 'stale';
            const ageStr = l.lastSec >= 0 ? formatLastSeen(l.lastSec) : '';
            return `<span class="log-item ${liveClass}">
                <span class="dot ${sevDot}"></span>
                <span class="city">${a.code}</span>
                <span class="arrow">→</span>
                <span class="city">${b.code}</span>
                <span class="vol">${formatBytes(l.bytes)}</span>
                <span>${l.count}p</span>
                <span class="age">${ageStr}</span>
            </span>`;
        }).join('');
        logStrip.innerHTML = items + items;   // 무한 스크롤용 복제
    }

    function rebuildTopLinks() {
        if (!topLinksEl) return;
        const top = links.slice(0, 6);
        if (top.length === 0) {
            topLinksEl.innerHTML = '<div class="item">데이터 없음</div>';
            return;
        }
        topLinksEl.innerHTML = top.map(l => {
            const a = cities[l.a], b = cities[l.b];
            if (!a || !b) return '';
            const sevCls  = l.sev !== 'normal' ? l.sev : '';
            const liveCls = l.live ? 'live' : l.idle ? 'idle' : 'stale';
            const ageStr  = l.lastSec >= 0 ? formatLastSeen(l.lastSec) : '—';
            return `<div class="item ${sevCls} ${liveCls}">
                <span class="pair">${a.code} ↔ ${b.code}</span>
                <span class="vol">${formatBytes(l.bytes)}</span>
                <span class="age">${ageStr}</span>
            </div>`;
        }).join('');
    }

    function formatBytes(b) {
        if (b == null) return '—';
        if (b < 1024) return `${b} B`;
        if (b < 1048576) return `${(b/1024).toFixed(1)} KB`;
        if (b < 1073741824) return `${(b/1048576).toFixed(1)} MB`;
        return `${(b/1073741824).toFixed(2)} GB`;
    }

    // ─── 라벨 ─────────────────────────────────────────────
    let lastLabelTs = 0;
    function updateLabels() {
        labelsEl.innerHTML = '';
        const stageRect = stageEl.getBoundingClientRect();
        const svgRect   = svg.getBoundingClientRect();
        const cx = (svgRect.left - stageRect.left) + svgRect.width / 2;
        const cy = (svgRect.top  - stageRect.top)  + svgRect.height / 2;
        const scale = svgRect.width / 800;
        const visible = [];
        cities.forEach(c => {
            const p = project(c.lat, c.lon);
            if (p.z > 30) visible.push({ c, p });
        });
        visible.sort((a, b) => a.p.y - b.p.y);
        const step = Math.max(1, Math.floor(visible.length / 6));
        for (let i = 0; i < visible.length; i += step) {
            const { c, p } = visible[i];
            const x = cx + p.x * scale;
            const y = cy + p.y * scale;
            const lbl = document.createElement('div');
            lbl.className = 'city-label show';
            lbl.style.left = `${x}px`;
            lbl.style.top  = `${y}px`;
            lbl.textContent = `${c.code} · ${c.name}`;
            labelsEl.appendChild(lbl);
        }
    }

    // ─── 프레임 루프 ──────────────────────────────────────
    let lastTs = performance.now();
    let lastBuildLon = null, lastBuildLat = null;
    let dragging = false, pointerId = null, lastPx = 0, lastPy = 0;
    let velLon = 0, velLat = 0, lastMoveTs = 0;
    const INERTIA_DECAY = 1.6, DRAG_SENS = 0.35;

    function frame(now) {
        const dt = Math.min(0.1, (now - lastTs) / 1000);
        lastTs = now;
        if (!dragging && (Math.abs(velLon) > 0.05 || Math.abs(velLat) > 0.05)) {
            rotLon += velLon * dt;
            rotLat += velLat * dt;
            const k = Math.exp(-INERTIA_DECAY * dt);
            velLon *= k; velLat *= k;
        }
        if (rotLon > 360) rotLon -= 360;
        if (rotLon < -360) rotLon += 360;
        if (rotLat > 89) rotLat = 89;
        if (rotLat < -89) rotLat = -89;

        if (lastBuildLon === null
            || Math.abs(rotLon - lastBuildLon) > 0.4
            || Math.abs(rotLat - lastBuildLat) > 0.4) {
            rebuildGraticule();
            rebuildLand();
            lastBuildLon = rotLon; lastBuildLat = rotLat;
        }

        gArcs.innerHTML = '';
        gNodes.innerHTML = '';
        gPackets.innerHTML = '';

        cities.forEach((c, i) => {
            const p = project(c.lat, c.lon);
            if (p.z < -20) return;
            const front = p.z >= 0;
            const alpha = front ? 1 : 0.25;
            const pulse = 0.6 + 0.4 * Math.sin(now / 700 + i);
            const color = c.local ? '#10b981' : '#00f2ff';
            gNodes.appendChild(el('circle', {
                cx: p.x.toFixed(1), cy: p.y.toFixed(1),
                r: (3 + pulse * 4).toFixed(2),
                fill: 'none', stroke: color, 'stroke-width': 1,
                opacity: (0.35 * alpha).toFixed(2),
            }));
            gNodes.appendChild(el('circle', {
                cx: p.x.toFixed(1), cy: p.y.toFixed(1),
                r: front ? 2.6 : 1.8,
                fill: color, opacity: alpha.toFixed(2),
                class: 'node', style: `color:${color}`,
            }));
        });

        links.forEach(L => {
            const cA = cities[L.a], cB = cities[L.b];
            if (!cA || !cB) return;
            const arc = buildArc(cA, cB);
            const segs = [[]];
            let prevVisible = null;
            for (const pt of arc.pts) {
                const vis = pt.z >= -40;
                if (vis) {
                    segs[segs.length - 1].push(pt);
                    if (prevVisible === false) segs.push([]);
                    prevVisible = true;
                } else {
                    if (prevVisible === true) segs.push([]);
                    prevVisible = false;
                }
            }
            const meta = SEVERITY[L.sev] || SEVERITY.normal;

            // ── 실시간 활성도 기반 시각 변조 ──────────────────
            // live: 강한 색 + 펄스 굵기 / idle: 절반 / stale: dim + dashed
            const baseAlpha = L.live ? 0.95 : L.idle ? 0.55 : 0.22;
            const pulse = L.live ? (0.85 + 0.30 * Math.sin(now / 280 + L.phase * 7)) : 1;
            const widthMul = L.stale ? 0.7 : pulse;
            // 신규 링크 birth 펄스 (밝은 흰색 글로우 1회)
            const age = now - (L.bornAt || now);
            const birthBoost = age < BIRTH_PULSE_MS
                ? (1 - age / BIRTH_PULSE_MS)
                : 0;

            for (const seg of segs) {
                if (seg.length < 2) continue;
                const d = 'M' + seg.map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' L');
                gArcs.appendChild(el('path', {
                    d,
                    stroke: meta.color,
                    'stroke-width': (meta.weight * widthMul).toFixed(2),
                    'stroke-dasharray': L.stale ? '4 6' : 'none',
                    fill: 'none',
                    opacity: baseAlpha.toFixed(2),
                    class: 'arc',
                    style: `color:${meta.color}`,
                }));
                // birth flash: 흰색으로 한 번 덧그림
                if (birthBoost > 0) {
                    gArcs.appendChild(el('path', {
                        d,
                        stroke: '#ffffff',
                        'stroke-width': (meta.weight * 1.6 * birthBoost).toFixed(2),
                        fill: 'none',
                        opacity: (0.7 * birthBoost).toFixed(2),
                    }));
                }
            }

            // ── 패킷 (stale은 미동작) ─────────────────────────
            for (let k = 0; k < L.packetCount; k++) {
                const t = ((now / 1000) * L.speed + L.phase + k * 0.3) % 1;
                const exact = t * (arc.pts.length - 1);
                const i0 = Math.floor(exact);
                const i1 = Math.min(arc.pts.length - 1, i0 + 1);
                const f = exact - i0;
                const p0 = arc.pts[i0], p1 = arc.pts[i1];
                const x = p0.x + (p1.x - p0.x) * f;
                const y = p0.y + (p1.y - p0.y) * f;
                const z = p0.z + (p1.z - p0.z) * f;
                if (z < -30) continue;
                const baseR = L.live ? 3.6 : 2.8;
                const alpha = L.live ? (z < 0 ? 0.45 : 1) : (z < 0 ? 0.25 : 0.7);
                gPackets.appendChild(el('circle', {
                    cx: x.toFixed(1), cy: y.toFixed(1),
                    r: baseR.toFixed(1), fill: meta.color, opacity: alpha.toFixed(2),
                    class: 'packet', style: `color:${meta.color}`,
                }));
                gPackets.appendChild(el('circle', {
                    cx: x.toFixed(1), cy: y.toFixed(1),
                    r: (baseR * 2.4).toFixed(1),
                    fill: meta.color, opacity: (L.live ? 0.22 : 0.12).toFixed(2),
                }));
            }

            // ── live 링크의 노드에서 ping 발사 효과 ───────────
            if (L.live && Math.random() < 0.08) {
                const which = Math.random() < 0.5 ? cA : cB;
                const p = project(which.lat, which.lon);
                if (p.z > 0) {
                    gPackets.appendChild(el('circle', {
                        cx: p.x.toFixed(1), cy: p.y.toFixed(1),
                        r: 4 + Math.random() * 6,
                        fill: 'none', stroke: meta.color,
                        'stroke-width': 1, opacity: '0.55',
                    }));
                }
            }
        });

        if (now - lastLabelTs > 220) {
            updateLabels();
            lastLabelTs = now;
        }
        requestAnimationFrame(frame);
    }

    // ─── 드래그 ───────────────────────────────────────────
    function onDown(e) {
        if (dragging) return;
        dragging = true; pointerId = e.pointerId;
        lastPx = e.clientX; lastPy = e.clientY;
        velLon = 0; velLat = 0;
        lastMoveTs = performance.now();
        try { stageEl.setPointerCapture(e.pointerId); } catch (_) {}
        document.body.classList.add('dragging');
        if (hint) hint.classList.add('hide');
    }
    function onMove(e) {
        if (!dragging || e.pointerId !== pointerId) return;
        const dx = e.clientX - lastPx;
        const dy = e.clientY - lastPy;
        lastPx = e.clientX; lastPy = e.clientY;
        rotLon += dx * DRAG_SENS;
        rotLat -= dy * DRAG_SENS;
        if (rotLat > 89) rotLat = 89;
        if (rotLat < -89) rotLat = -89;
        const now = performance.now();
        const dt = Math.max(0.001, (now - lastMoveTs) / 1000);
        velLon = (dx * DRAG_SENS) / dt;
        velLat = -(dy * DRAG_SENS) / dt;
        lastMoveTs = now;
    }
    function onUp(e) {
        if (!dragging || e.pointerId !== pointerId) return;
        dragging = false;
        try { stageEl.releasePointerCapture(pointerId); } catch (_) {}
        pointerId = null;
        document.body.classList.remove('dragging');
        if (Math.abs(velLon) < 8) velLon = 0;
        if (Math.abs(velLat) < 8) velLat = 0;
    }
    stageEl.addEventListener('pointerdown', onDown);
    stageEl.addEventListener('pointermove', onMove);
    stageEl.addEventListener('pointerup', onUp);
    stageEl.addEventListener('pointercancel', onUp);
    stageEl.addEventListener('pointerleave', e => { if (dragging) onUp(e); });

    // ─── 시계 ─────────────────────────────────────────────
    function pad(n) { return String(n).padStart(2, '0'); }
    function tickClock() {
        if (!clockEl) return;
        const d = new Date();
        clockEl.textContent =
            `${d.getFullYear()}·${pad(d.getMonth()+1)}·${pad(d.getDate())} ` +
            `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
    }
    tickClock();
    setInterval(tickClock, 1000);
    setTimeout(() => { if (hint) hint.classList.add('hide'); }, 6000);

    // ─── 초기 렌더 + 폴링 시작 ────────────────────────────
    rebuildGraticule();
    rebuildLand();
    refreshData();
    setInterval(refreshData, POLL_INTERVAL_MS);
    requestAnimationFrame(frame);
})();
