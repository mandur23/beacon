# Beacon — GitHub 작업 기록

> **저장소:** https://github.com/mandur23/beacon  
> **브랜치:** `master`  
> **최종 커밋:** `ee32ae7` (2026-06-02) — 오류 수정  
> **기간:** 2026-03-11 ~ 2026-06-02

---

## 프로젝트 개요

Raspberry Pi 허브 + Spring Boot + Suricata + Python ML 기반 **통합 보안 모니터링 시스템**.  
네트워크 이벤트, 에이전트 상태, 트래픽 이상, 방화벽 정책을 중앙에서 수집·분석·조회한다.

| 항목 | 내용 |
|------|------|
| Backend | Java 21, Spring Boot 4.0.3, JPA, Flyway, Spring Security |
| DB | MySQL 8 (테이블 10개, Flyway V1~V16) |
| ML | Python Flask (`python-ml/`) |
| AI | Ollama 로컬 연동 (위험 요약·채팅) |
| IDS | Suricata EVE 폴링 / Syslog |
| 에이전트 | BeaconGuardian 클라이언트 (등록·하트비트·방화벽 동기화) |

---

## 마일스톤 요약

| 시기 | 주요 작업 |
|------|-----------|
| 2026-03 | 프로젝트 초기화, 대시보드·방화벽 UI, 계정 잠금, Syslog/Suricata 연동 |
| 2026-04 | 환경변수 외부화, Flyway 마이그레이션 체계, 방화벽 API·에이전트 동기화, 이벤트 소스 추적 |
| 2026-05 | MFA·HTTPS 기본, 2차 인증 웹/앱 호환, GeoIP·글로브 UI, 트래픽 분석·AI 모델 선택 |
| 2026-06 | 버그 수정, HikariCP 풀·설정 정리, 오류 수정 |

---

## 커밋 타임라인 (전체)

| 날짜 | 커밋 | 설명 |
|------|------|------|
| 2026-06-02 | `ee32ae7` | 오류 수정 |
| 2026-05-31 | `b5647bc` | 버전 업데이트 |
| 2026-05-23 | `32c18df` | `application-local.yaml.example` 삭제 |
| 2026-05-22 | `6e127d1` | 버그 픽스 |
| 2026-05-20 | `9844e6d` | 버그 픽스 |
| 2026-05-18 | `83d25e7` | GeoIP 서비스, 글로브 데이터 API, 네트워크 모니터링 UI 강화 |
| 2026-05-14 | `0996a67` | 트래픽 분석 API, 모델 선택 개선, 환경 설정 정리 |
| 2026-05-14 | `aca6902` | 수정 중 업데이트 금지 (WIP) |
| 2026-05-11 | `74c2f61` / `5ea358a` | 앱 2차 인증 호환, 계정 생성·삭제, MFA 웹 페이지 |
| 2026-05-11 | `a61c9f1` | MFA 설정·검증 API, HTTPS 기본 활성화, 문서 갱신 |
| 2026-05-07 | `991ee31` / `7c5c667` | 작업 스냅샷 |
| 2026-04-30 | `396e4dd` / `5ecf9d1` | 작업 스냅샷, admin 마이그레이션 스크립트 리팩터 |
| 2026-04-17 | `062b69e` | security/traffic 로그에 agent_name·metadata, 이벤트 소스, DB 스키마 |
| 2026-04-09 | `a0ea1cb` | 방화벽 관리 API·서비스 |
| 2026-04-02 | `4066d8f` | application 설정 환경변수 외부화 |
| 2026-04-02 | `aeddf4a` | 방화벽 이벤트 배치, 이벤트 소스 추적, Flyway 프레임워크 |
| 2026-04-01 | `373e928` | UI 개선 및 버그 픽스 |
| 2026-03-27 | `57cc2cd` | 역할 검증, 계정 잠금, HTML 이스케이프 보안 강화 |
| 2026-03-23 | `bce4f17` | README — Raspberry Pi·Suricata·에이전트 연동 문서화 |
| 2026-03-17 | `16a289e` | EveJsonFileWatcherService (Suricata eve.json) |
| 2026-03-17 | `25059a0` | Syslog 비보안 이벤트 필터링 |
| 2026-03-17 | `f1fb04d` | Syslog 연동, 방화벽 규칙 관리 기능 |
| 2026-03-16 | `8a33c43` | 방화벽 규칙 UI CRUD |
| 2026-03-12 | `929a121` | 계정 잠금 및 해제 |
| 2026-03-11 | `4df4e1a` | **Initial commit** |

---

## 기능별 구현 내역

### 인증·보안
- JWT 기반 API·대시보드 인증
- MFA(TOTP) 설정·검증, 앱·웹 2차 인증 호환
- HTTPS 기본 (`keystore.p12` / mkcert·keytool fallback)
- 계정 잠금·해제, 로그인 시도 제한, 역할(ADMIN/USER) 검증

### 이벤트·위협
- Suricata `eve.json` 폴링, Syslog 수신
- `security_events` — 심각도·차단·risk_score·source(SURICATA/SYSLOG/AGENT/MANUAL)
- `event_blocking_policies` — 서버 측 차단 정책 매칭
- 위협 화면 Source IP 차단 → `firewall_rules` + 에이전트 명령 큐

### 에이전트·방화벽
- 에이전트 등록·하트비트·오프라인 일괄 처리
- `firewall_rules` 중앙 SSoT, `firewall_sync_state.revision` 단조 증가
- `firewall_agent_commands` — 롱폴/폴링 명령 전달
- 에이전트별 `owner_user_id` 소유권

### 트래픽·네트워크
- `traffic_logs` 저장, ML 이상 탐지 연동
- GeoIP, 3D 글로브 네트워크 모니터링 UI
- 프로토콜·프로세스별 통계

### AI·ML
- Python ML 서비스 (Flask) 이상 탐지
- Ollama — 모델 자동 설치, 위험 요약·채팅 (`reports` 페이지)

### DB
- Flyway V1~V16 마이그레이션
- 10테이블 ERD: `docs/database-erd.png` (로컬 문서, 미커밋)

---

## 로컬 문서 (Git 미반영)

| 파일 | 설명 |
|------|------|
| `docs/database-erd.svg` / `.png` | PPT·발표용 DB ERD |
| `docs/ERD-PPT-README.md` | PowerPoint 삽입 가이드 |
| `docs/notion-github-work-log.md` | 본 작업 기록 (노션 동기화용) |

---

## 다음 작업 후보

- [ ] ERD·노션 문서 저장소 커밋
- [ ] HikariCP 풀 크기·`open-in-view` 운영 튜닝 반영
- [ ] Notion API 연동 시 자동 커밋 로그 동기화

---

*마지막 갱신: 2026-06-02 · `git log` 기준 자동 정리*
