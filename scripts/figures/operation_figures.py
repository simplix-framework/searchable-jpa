"""Figures of the operating side: modules, settings, time slices, error answers and API documents.

Built with the svg-diagrams skill's scripts/docfigures/build.py from the repository root.
No component performs model work, so no figure carries an AI mark.
"""
from common import (BODY, HAIRLINE, MICRO, PAD, canvas, card, card_h, figure, note, save, v_stack, zone)
from figkit import LANGUAGES, LEFT, WIDTH, arrow, label_at_top, out, rule, say, zone_height


def module_choice(language):
    # Claim: the starter holds both library modules and wires them; with the core alone the application wires.
    c = canvas(1000, 460)
    zone_w = 590
    inner_x, inner_w = LEFT + PAD, zone_w - 2 * PAD
    items = [
        (c.purple, say(language, 'Auto-configuration', '자동 설정'),
         [say(language, 'settings handed to JPA and Spring MVC', 'JPA와 Spring MVC에 설정 전달'),
          say(language, 'aggregate and time count beans', '집계와 시간 구간 집계 빈')]),
        (c.blue, 'searchable-jpa-openapi',
         [say(language, 'search parameters in the API document', 'API 문서에 검색 파라미터 기재')]),
        (c.blue, 'searchable-jpa-core',
         [say(language, 'conditions, validation, queries', '조건, 검증, 조회'),
          say(language, 'no web or SpringDoc dependency', '웹과 SpringDoc 의존성 없음')]),
    ]
    gap = 14
    heights = [card_h(inner_w, title, lines) for _, title, lines in items]
    content = sum(heights) + gap * (len(items) - 1)
    top = 34
    zone_h = zone_height(content)
    y = zone(c, LEFT, top, zone_w, zone_h, c.blue, 'spring-boot-starter-searchable-jpa')
    v_stack(c, inner_x, y, inner_w, [(accent, title, lines, {}) for accent, title, lines in items], gap=gap,
            arrow=False)
    side_x = LEFT + zone_w + 34
    card(c, side_x, top, LEFT + WIDTH - side_x, c.orange, say(language, 'Core alone: wire it', 'core만 사용: 직접 연결'),
         [say(language, 'time zone', '시간대'), say(language, 'pattern case', '패턴 대소문자'),
          say(language, 'conversion service', '변환 서비스'), say(language, 'request limits', '요청 구조 제한'),
          say(language, 'message source', '메시지 소스'),
          say(language, 'persistence unit settings', '영속성 단위 설정')],
         h=zone_h, valign='middle')
    save(c, out(language, 'module-choice'))


@figure(plain=True)
def time_bucket_kinds(language):
    # Claim: equal buckets divide the period evenly, calendar buckets cut it at the midnights of a zone.
    c = canvas(1000, 330)
    start, hour = 120, 816 / 72
    dim = c.t['fg_dim']
    kinds = [
        ('count(..., buckets = 3)', say(language, '24 hours each', '구간마다 24시간'), c.blue, [0, 24, 48, 72]),
        ('countByCalendar(..., DAYS, zone)', say(language, 'cut at midnight', '자정에서 분할'), c.orange,
         [0, 12, 36, 60, 72]),
    ]
    y = 24
    for text, verdict, accent, cuts in kinds:
        label_at_top(c, LEFT, y, text, BODY, weight=600)
        label_at_top(c, LEFT + WIDTH, y, verdict, BODY, color=accent, weight=700, anchor='end')
        bar_y = y + BODY + 14
        for index in range(len(cuts) - 1):
            left = start + cuts[index] * hour
            width = (cuts[index + 1] - cuts[index]) * hour
            c.rrect(left + 2, bar_y, width - 4, 30, rx=4, fill=accent, stroke=accent, sw=HAIRLINE,
                    opacity=0.3, measure='width')
        y = bar_y + 30 + 38
    axis = y + 6
    rule(c, start, axis, start + 72 * hour, axis)
    ticks = [(0, '10-01 12:00'), (12, '10-02'), (36, '10-03'), (60, '10-04'), (72, '12:00')]
    for at, text in ticks:
        x = start + at * hour
        rule(c, x, axis - 7, x, axis + 7)
        label_at_top(c, x, axis + 14, text, MICRO, color=dim, anchor='middle')
    note(c, LEFT, axis + 14 + MICRO + 22, WIDTH,
         say(language, 'Every bucket: start included, end excluded', '모든 구간: 시작 포함, 끝 제외'), c.blue,
         color=c.t['fg'])
    save(c, out(language, 'time-bucket-kinds'))


def auto_configuration_wiring(language):
    # Claim: one block of settings reaches four places, and each place applies them with a different reach.
    c = canvas(1000, 560)
    source_w, channel = 200, 64
    target_x = LEFT + source_w + channel
    target_w = WIDTH - source_w - channel
    targets = [
        (c.blue, say(language, 'Whole process', '프로세스 전체'),
         [say(language, 'request limits, time zone, pattern case', '요청 구조 제한, 시간대, 패턴 대소문자'),
          say(language, 'message source and conversion service', '메시지 소스와 변환 서비스')]),
        (c.green, say(language, 'Each persistence unit', '영속성 단위별'),
         [say(language, 'page sizes, IN batch, write chunk', '페이지 크기, IN 배치, 쓰기 처리 단위'),
          say(language, 'aggregate and bucket ceilings', '집계와 구간 상한')]),
        (c.orange, say(language, 'Hibernate defaults', 'Hibernate 기본값'),
         [say(language, 'batch fetch, JDBC batch, ordering', '배치 fetch, JDBC 배치, 정렬'),
          say(language, 'application values take precedence', '애플리케이션 설정 우선')]),
        (c.purple, 'Spring MVC',
         [say(language, 'search parameter resolver', '검색 파라미터 리졸버'),
          say(language, 'status 400 handler and API document, when enabled', '400 응답 처리기와 API 문서: 활성화 시')]),
    ]
    top, gap = 24, 14
    heights = [card_h(target_w, title, lines) for _, title, lines in targets]
    span = sum(heights) + gap * (len(targets) - 1)
    source = card(c, LEFT, top, source_w, c.t['fg_dim'], 'application.yml', ['searchable.*'], h=span,
                  valign='middle', wash=True, bullet=False)
    y = top
    for (accent, title, lines), height in zip(targets, heights):
        card(c, target_x, y, target_w, accent, title, lines)
        arrow(c, source[0] + source_w, y + height / 2, target_x, y + height / 2, accent)
        y += height + gap
    save(c, out(language, 'auto-configuration-wiring'))


def error_response_path(language):
    # Claim: who answers an invalid search depends on where it fails and on one setting.
    c = canvas(1000, 480)
    source_w, exception_w, channel = 210, 380, 40
    outcome_w = WIDTH - source_w - exception_w - 2 * channel
    exception_x = LEFT + source_w + channel
    outcome_x = exception_x + exception_w + channel
    top, gap = 24, 22
    outcomes = [
        (c.green, say(language, 'Library answers 400', '라이브러리의 400 응답'),
         [say(language, 'problem-details on', 'problem-details 켬')]),
        (c.orange, say(language, 'Application handler', '애플리케이션 처리기'),
         [say(language, 'problem-details off (default)', 'problem-details 끔(기본값)')]),
    ]
    heights = [card_h(outcome_w, title, lines) for _, title, lines in outcomes]
    span = sum(heights) + gap
    source = card(c, LEFT, top, source_w, c.blue, say(language, 'Search request', '검색 요청'),
                  [say(language, 'GET parameters', 'GET 파라미터'), say(language, 'service call', '서비스 호출')],
                  h=span, valign='middle')
    raised = card(c, exception_x, top, exception_w, c.purple, say(language, 'Library exceptions', '라이브러리 예외'),
                  ['SearchableParseException', 'SearchableValidationException'], h=span, valign='middle',
                  wash=True)
    arrow(c, source[0] + source_w, top + span / 2, raised[0], top + span / 2)
    y = top
    for (accent, title, lines), height in zip(outcomes, heights):
        card(c, outcome_x, y, outcome_w, accent, title, lines)
        arrow(c, raised[0] + exception_w, y + height / 2, outcome_x, y + height / 2, accent)
        y += height + gap
    second = top + span + gap
    body_lines = [say(language, 'message conversion', '메시지 변환')]
    mvc_lines = ['HttpMessageNotReadableException']
    answer_lines = [say(language, 'Spring MVC default', 'Spring MVC 기본 처리')]
    row_h = max(card_h(source_w, 'JSON', body_lines), card_h(exception_w, 'Spring MVC', mvc_lines),
                card_h(outcome_w, 'Status 400', answer_lines))
    body = card(c, LEFT, second, source_w, c.blue, say(language, 'JSON body', 'JSON 본문'), body_lines, h=row_h,
                valign='middle')
    mvc = card(c, exception_x, second, exception_w, c.purple, 'Spring MVC', mvc_lines, h=row_h, valign='middle',
               wash=True)
    answer = card(c, outcome_x, second, outcome_w, c.green, say(language, 'Status 400', '400 응답'), answer_lines,
                  h=row_h, valign='middle')
    arrow(c, body[0] + source_w, second + row_h / 2, mvc[0], second + row_h / 2)
    arrow(c, mvc[0] + exception_w, second + row_h / 2, answer[0], second + row_h / 2, c.green)
    save(c, out(language, 'error-response-path'))


def openapi_customizer_wiring(language):
    # Claim: one declared field becomes several documented GET parameters and one typed body schema.
    c = canvas(1000, 420)
    declared_w, maker_w, channel = 250, 330, 36
    made_w = WIDTH - declared_w - maker_w - 2 * channel
    maker_x = LEFT + declared_w + channel
    made_x = maker_x + maker_w + channel
    top, gap = 24, 16
    made = [
        (c.green, say(language, 'GET document', 'GET 문서'),
         ['title.equals', 'title.contains', 'title.startsWith', 'sort, page, size']),
        (c.orange, say(language, 'POST document', 'POST 문서'),
         ['SearchCondition<D>', say(language, 'schema with an example', '스키마와 예제')]),
    ]
    heights = [card_h(made_w, title, lines) for _, title, lines in made]
    span = sum(heights) + gap
    declared = card(c, LEFT, top, declared_w, c.blue, say(language, 'Declared field', '필드 선언'),
                    ['title', 'EQUALS, CONTAINS, STARTS_WITH', 'sortable'], h=span, valign='middle')
    maker = card(c, maker_x, top, maker_w, c.purple, 'searchConditionCustomizer',
                 [say(language, 'OperationCustomizer bean', 'OperationCustomizer 빈'),
                  say(language, 'added to each API group', 'API 그룹마다 등록')],
                 h=span, valign='middle', wash=True, title_size=BODY)
    arrow(c, declared[0] + declared_w, top + span / 2, maker[0], top + span / 2)
    y = top
    for (accent, title, lines), height in zip(made, heights):
        card(c, made_x, y, made_w, accent, title, lines)
        arrow(c, maker[0] + maker_w, y + height / 2, made_x, y + height / 2, accent)
        y += height + gap
    save(c, out(language, 'openapi-customizer-wiring'))


for language in LANGUAGES:
    module_choice(language)
    time_bucket_kinds(language)
    auto_configuration_wiring(language)
    error_response_path(language)
    openapi_customizer_wiring(language)
