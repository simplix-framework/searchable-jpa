# 문서 작성·검증 가이드

## 구성

영어와 한국어 문서는 `docs/en/`, `docs/ko/`에 같은 파일명으로 관리하며, 언어별 페이지는 제목 구조, 예제, 그림, 표의 형태가 같아야 합니다. Docsify 설정은 `docs/index.html`에 있고 `docs/assets/`에는 공통 버전 선택 코드가 있습니다. 사이드바의 순서가 읽는 순서입니다. 각 페이지는 앞 페이지의 내용만 전제하고, 마지막 절에서 다음 페이지로 연결합니다.

안내 페이지는 다음 순서로 작성합니다.

1. 이 페이지에서 얻는 것과 전제
2. 그림
3. 최소 예제와 변형
4. 동작 규칙 표
5. 거부되는 요청
6. 관련 설정
7. 다음 단계

새 페이지는 각 언어의 디렉터리와 사이드바에 함께 추가하세요. 같은 디렉터리의 문서는 `./basic-usage.md`, 그림은 `./_images/name.svg`로 연결합니다. 과거 버전의 변경 사항은 당시 동작을 기록한 것이므로 현재 동작으로 고치지 마세요.

## 예제

Java, HTTP, JSON 예제는 문서에 직접 쓰지 않고 테스트가 컴파일하고 실행하는 파일의 내용을 사용합니다.

| 예제 | 위치 |
| --- | --- |
| Java | `spring-boot-starter-searchable-jpa/src/test/java/dev/simplecore/searchable/docs` |
| 요청 | `spring-boot-starter-searchable-jpa/src/test/resources/docs-examples/requests`, `rejected` |
| 응답 | `spring-boot-starter-searchable-jpa/src/test/resources/docs-examples/responses` |

문서에는 예제 위에 출처를 표시하고 코드 블록은 비워 둡니다.

````markdown
<!-- snippet: PostService.java#service -->
```java
```
````

`#` 뒤는 영역 이름이며 생략하면 파일 전체입니다. 소스에서는 영역을 주석으로 표시하고, 같은 이름의 영역이 여러 번 있으면 순서대로 이어 붙입니다.

```text
// docs:start service
...
// docs:end service
```

```bash
node scripts/doc-snippets.mjs
./gradlew :spring-boot-starter-searchable-jpa:test --tests '*DocumentationExamplesTest'
```

첫 명령은 표시한 블록을 소스의 내용으로 채웁니다. 두 번째 명령의 `DocumentationExamplesTest`는 `requests`의 모든 요청이 성공하고 `rejected`의 모든 요청이 400으로 거부되는지, 문서에 적은 결과와 실제 결과가 같은지 검사합니다. 요청 예제를 추가하면 기대하는 결과를 이 테스트에 함께 적으세요.

## 그림

그림은 `scripts/figures/`의 모듈이 생성하며 SVG를 직접 고치지 않습니다. 설정은 `scripts/figures/document-figures.json`에 있으며 파일 안의 경로는 그 폴더를 기준으로 합니다. 그리기 라이브러리와 검사는 svg-diagrams 스킬의 `scripts/docfigures`를 사용합니다. 아래 `<skill>`은 그 스킬의 디렉터리입니다.

```bash
python3 <skill>/scripts/docfigures/build.py --config scripts/figures/document-figures.json
python3 scripts/verify-figures.py <skill>/scripts/docfigures --render build/figures
```

언어별 그림은 같은 함수가 언어별 문구로 생성합니다. 그림은 본문 문장으로 전달하기 어려운 관계 하나를 보여 주도록 구성하고, 모듈의 함수마다 그 주장을 주석으로 적습니다.

## 기능 대장

`scripts/docs-ledger.json`은 문서가 다뤄야 할 기능과 그 기능을 설명하는 페이지의 목록입니다. 연산자, 어노테이션 속성, 서비스 메서드, 설정 키, 메시지 키 같은 이름은 소스에서 읽으므로, 소스에 이름을 추가하면 문서에 반영하기 전까지 검사가 실패합니다. 이름이 없는 동작은 `features`에 그 동작을 설명하는 페이지와 페이지에 있어야 할 코드 표기를 등록합니다.

기능은 안내 페이지에 코드로 표시되고 레퍼런스 페이지에 항목이 있을 때 반영된 것으로 판정합니다. 모든 언어에서 충족해야 합니다.

## 로컬 미리보기와 검사

```bash
./scripts/build-docs.sh
python3 -m http.server 3000 --directory build-docs
```

`http://localhost:3000/#/en/` 또는 `http://localhost:3000/#/ko/`를 여세요. `file://`로 직접 열지 말고 HTTP 서버를 사용하세요. 빌드는 각 언어의 문서와 공통 파일을 복사하고, `build.gradle`이나 `DOCS_VERSION`의 버전을 표지에 반영하며, 링크를 Docsify 형식으로 변환합니다. `build-docs/`는 다시 생성할 수 있는 출력 디렉터리입니다.

```bash
npm ci
npm run test:docs
npm run test:docs:e2e
```

| 검사 | 내용 |
| --- | --- |
| 예제 일치 | 표시한 예제가 소스와 같은지, Java·HTTP·JSON 예제에 출처가 있는지 |
| 기능 반영 | 기능 대장의 모든 항목이 모든 언어의 페이지에 있는지 |
| 구성 일치 | 언어 간에 페이지, 제목 구조, 예제, 그림, 표, 사이드바가 같은지와 링크가 유효한지 |
| 버전 이동 | 버전 선택기의 경로 계산과 배포 준비 |

문서 검사에는 Node.js 22 이상이 필요합니다. e2e 검사는 8764번 포트에서 테스트 서버를 실행하므로 포트를 비워 두세요.
## 버전별 배포

`Deploy Documentation` GitHub Actions 워크플로는 `gh-pages`에 배포합니다. `master`의 문서가 변경되면 `snapshot/`을 갱신합니다. 수동 실행 시 `2.0.0` 같은 정식 버전을 입력하면 `v2.0.0` 태그의 문서를 빌드합니다. 최신 이동 기능은 별도로 가져오므로 이전 버전의 본문을 유지하면서 버전 선택 오류를 수정할 수 있습니다.

`scripts/prepare-docs.mjs`는 기존 사이트와 새 빌드를 합칩니다. 선택한 버전 디렉터리만 교체하고 나머지 릴리스와 개발 문서를 보존합니다. 이전 HTML의 공통 버전 선택 파일을 갱신하고 `versions.json`을 생성합니다. 릴리스는 숫자 기준 내림차순으로 정렬합니다. 정식 버전을 배포해도 개발 문서는 삭제하지 않습니다.

사이트 루트는 가장 높은 정식 버전으로 이동하며 정식 버전이 없으면 개발 버전으로 이동합니다. URL의 쿼리와 해시는 유지합니다. 선택기는 같은 상위 경로의 버전 디렉터리로 이동하며 언어·페이지·제목 위치를 유지합니다. 이동 전에 페이지 존재 여부를 확인하고, 페이지가 없으면 해당 언어의 시작 페이지, 영어 시작 페이지 순서로 확인합니다. 네트워크 요청이 실패하면 현재 페이지를 유지합니다.

배포 주소는 [Searchable JPA 문서](https://simplix-framework.github.io/searchable-jpa/)입니다. GitHub Pages에서 `gh-pages` 브랜치의 루트를 제공하도록 설정해야 합니다. 배포 워크플로에는 해당 브랜치의 쓰기 권한이 필요합니다.

## 문제 해결

- 버전 디렉터리가 없는 로컬 미리보기에는 선택기를 표시하지 않습니다. `npm run test:docs:e2e` 또는 `node scripts/docs-test-server.mjs`로 버전별 경로를 확인하세요.
- 버전 목록 조회가 실패하면 재시도 버튼과 상위 경로의 `versions.json` 응답을 확인하세요. 도메인 루트와 프로젝트 하위 경로를 모두 검사하세요.
- 본문이나 검색 결과가 오래된 경우 선택한 버전과 언어를 확인하고 페이지를 다시 로드하세요. CDN·플러그인 요청도 확인하세요. Docsify 5 플러그인은 `dist/plugins/` 경로를 사용합니다.
- 그림이 없으면 파일명 대소문자와 `en`·`ko` 디렉터리의 SVG 파일을 확인하세요. 빌드 결과는 다시 생성하므로 `build-docs/` 대신 `docs/`를 수정하세요.
- 특정 릴리스 배포가 실패하면 정확한 `vX.Y.Z` 태그가 있는지 확인하세요. 버전 입력에는 숫자 세 부분만 사용하세요.
