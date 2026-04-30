# Flyway 마이그레이션 통합 관리 가이드

이 프로젝트는 Flyway 버전 마이그레이션(`V__`)을 사용합니다.
기능별 통합 관리는 **파일 이동/이름변경**이 아니라 **기능 인덱스 기반 관리**로 수행합니다.

## 1) 핵심 원칙

- 이미 배포/적용된 `V` 파일은 **수정/삭제/이름변경 금지**
- 스키마 변경은 항상 **새 버전 파일 추가**로 진행
- 하나의 파일은 **하나의 기능 목적**만 담기
- 기능별 조회는 `MIGRATION_INDEX.md`와 `migration-groups.yaml` 기준으로 수행

## 2) 왜 파일 이동 대신 인덱스 관리인가

- Flyway 이력(`flyway_schema_history`)과 정합성을 안전하게 유지
- 운영/스테이징 DB의 기존 버전 추적을 깨지 않음
- 기능별 검색/검토/릴리즈 노트 작성은 인덱스 파일로 빠르게 가능

## 3) 기능별 파일명 규칙 (신규 파일)

- `V{version}__Auth_{action}.sql`
- `V{version}__Agent_{action}.sql`
- `V{version}__SecurityEvent_{action}.sql`
- `V{version}__Firewall_{action}.sql`
- `V{version}__Policy_{action}.sql`
- `V{version}__Traffic_{action}.sql`

## 4) 운영 절차

1. 신규 마이그레이션 추가 (`V{n}__...sql`)
2. `MIGRATION_INDEX.md` 기능 섹션에 버전 추가
3. `migration-groups.yaml`의 해당 기능 배열에 파일 추가
4. 스테이징에서 Flyway validate/migrate 확인

## 5) 금지 패턴

- 적용된 버전 파일 내부 SQL 수정
- 적용된 버전 파일 이동/이름변경
- 여러 기능 변경을 하나의 파일에 과도하게 혼합
- 애플리케이션 로직 변경과 DB 변경의 배포 순서를 무시한 병합

