# Flyway 마이그레이션 정리 가이드

이 프로젝트는 Flyway 버전 마이그레이션(`V__`)을 사용합니다.

## 1) 핵심 원칙

- 이미 배포/적용된 `V` 파일은 **수정/삭제/이름변경 금지**
- 스키마 변경은 항상 **새 버전 파일 추가**로 진행
- 하나의 파일은 **하나의 기능 목적**만 담기
- 롤백 SQL이 필요하면 별도 문서 또는 운영 스크립트로 관리

## 2) 기능별 작성 규칙

Flyway는 기본적으로 `db/migration` 폴더를 버전 순으로 실행하므로,
폴더 분리보다 **파일명 규칙으로 기능을 묶는 방식**을 권장합니다.

파일명 패턴:

- `V{version}__Auth_{action}.sql`
- `V{version}__Agent_{action}.sql`
- `V{version}__SecurityEvent_{action}.sql`
- `V{version}__Firewall_{action}.sql`
- `V{version}__Policy_{action}.sql`

예시:

- `V11__SecurityEvent_Add_Source.sql`
- `V12__Firewall_Add_Unique_Block_Index.sql`
- `V13__Agent_Add_Metadata_Columns.sql`

## 3) 현재 버전 맵 (기능 기준)

- `V1__Initial_Schema.sql`
  - users, user_sessions, security_events, traffic_logs, firewall_rules 초기 생성
- `V2__Ensure_Admin_Default_Password.sql`
  - Auth 기본 관리자 패스워드 정합성
- `V5__Add_Agents_Table.sql`
  - Agent 도메인 추가
- `V6__Add_Updated_At_To_Agents.sql`
  - Agent 컬럼 보강
- `V7__Event_Blocking_Policies.sql`
  - Event Blocking Policy 기능
- `V8__Add_Source_To_Security_Events.sql`
  - SecurityEvent source 컬럼 추가
- `V9__Firewall_Rules_Dedup_And_Unique_Index.sql`
  - Firewall 활성 block 규칙 중복 정리 + 유니크 인덱스 추가
- `V10__Security_Events_Indexes.sql`
  - SecurityEvent 조회 최적화를 위한 복합/단일 인덱스 추가

## 4) 새 마이그레이션 작성 체크리스트

1. 파일명에 기능명 포함 (`Auth`, `Agent`, `SecurityEvent`, `Firewall`, `Policy`)
2. DDL 실행 전 기존 데이터 영향 검토 (`NOT NULL`, `UNIQUE`, 인덱스 추가)
3. 대용량 테이블 변경 시 락/실행시간 고려
4. 운영 반영 전 스테이징에서 Flyway validate/migrate 확인

## 5) 금지 패턴

- 적용된 버전 파일 내부 SQL 수정
- 여러 기능 변경을 하나의 파일에 과도하게 혼합
- 애플리케이션 로직 변경과 DB 변경의 배포 순서를 무시한 병합

