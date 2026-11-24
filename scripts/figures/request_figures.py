"""Figures of the request side: the adoption path, what a request becomes, and how its parts read.

Built with the svg-diagrams skill's scripts/docfigures/build.py from the repository root.
No component performs model work, so no figure carries an AI mark.
"""
from common import (BODY, CARD, EMPH, HAIRLINE, MICRO, STROKE, canvas, card, cards_row, centered_baseline,
                    disc, figure, heading, note, pill, pill_h, row, save, step_row, zone)
from figkit import LANGUAGES, LEFT, WIDTH, arrow, label, label_at_top, out, rule, say, tint, zone_height


@figure(plain=True)
def adoption_path(language):
    # Claim: adoption is one ordered path; each step builds on the one before it.
    steps = [
        (say(language, 'First search', '설치와 첫 검색'), say(language, 'one GET request', 'GET 요청 한 건')),
        (say(language, 'Search contract', '검색 계약 정의'), say(language, 'fields and operators', '필드와 연산자')),
        (say(language, 'Related data', '연관 데이터'), say(language, 'joins, collections', '조인과 컬렉션')),
        (say(language, 'Reading results', '결과 받기'), say(language, 'pages, scrolling', '페이지와 스크롤')),
        (say(language, 'Server control', '서버 통제'), say(language, 'scope and limits', '범위와 제한')),
        (say(language, 'Writes, figures', '변경과 통계'), say(language, 'update, aggregate', '수정과 집계')),
        (say(language, 'API docs, errors', '문서화와 오류'), say(language, 'OpenAPI and 400', 'OpenAPI와 400')),
        (say(language, 'Operations', '운영'), say(language, 'settings and speed', '설정과 성능')),
    ]
    c = canvas(1000, 320)
    xs, w = row(4, 34, width=WIDTH, x0=LEFT)
    top, channel = 24, 78
    h = step_row(c, xs, w, top, steps[:4], c.blue, sep='arrow', sub_size=BODY)
    second = top + h + channel
    step_row(c, xs, w, second, steps[4:], c.blue, sep='arrow', sub_size=BODY, start=5)
    # The path continues from the end of the first row to the start of the second.
    dim = c.t['fg_dim']
    c.ortho(xs[3] + w / 2, top + h, xs[0] + w / 2, second, exit='D', entry='U', lane=top + h + channel / 2,
            color=dim, marker=dim, sw=STROKE)
    save(c, out(language, 'adoption-path'))


@figure(plain=True)
def search_request_flow(language):
    # Claim: three input forms become one condition, and only what the DTO declares reaches SQL.
    c = canvas(1000, 320)
    inputs = [say(language, 'GET query string', 'GET 쿼리 문자열'),
              say(language, 'JSON body', 'JSON 본문'),
              say(language, 'Java builder', 'Java 빌더')]
    top, gap = 24, 18
    height = pill_h(BODY, 14)
    span = 3 * height + 2 * gap
    side, core = 190, 230
    channel = (WIDTH - 2 * side - 2 * core) / 3
    sources = [pill(c, LEFT, top + i * (height + gap), side, height, text, c.blue, rx=10)
               for i, text in enumerate(inputs)]
    condition = card(c, LEFT + side + channel, top, core, c.blue, 'SearchCondition',
                     [say(language, 'filter, sort, page', '조건 · 정렬 · 페이지')],
                     h=span, valign='middle', align='middle', wash=True)
    check = card(c, condition[0] + core + channel, top, core, c.purple, say(language, 'DTO check', 'DTO 검증'),
                 [say(language, 'declared rules', '선언한 규칙')],
                 h=span, valign='middle', align='middle', wash=True)
    result_x = check[0] + core + channel
    result_h = (span - gap) / 2
    accepted = card(c, result_x, top, side, c.green, say(language, 'Query runs', '조회 실행'),
                    [say(language, 'allowed request', '선언에 맞는 요청')],
                    h=result_h, valign='middle', align='middle', wash=True)
    refused = card(c, result_x, top + result_h + gap, side, c.red, say(language, 'Status 400', '400 응답'),
                   [say(language, 'refused request', '선언에 없는 요청')],
                   h=result_h, valign='middle', align='middle', wash=True)
    for source in sources:
        y = source[1] + source[3] / 2
        arrow(c, source[0] + source[2], y, condition[0], y)
    arrow(c, condition[0] + core, top + span / 2, check[0], top + span / 2)
    for result, color in ((accepted, c.green), (refused, c.red)):
        y = result[1] + result[3] / 2
        arrow(c, check[0] + core, y, result[0], y, color)
    save(c, out(language, 'search-request-flow'))


def get_parameter_anatomy(language):
    # Claim: one GET parameter has three parts, and each part is checked by a different rule of the DTO.
    c = canvas(1000, 420)
    xs, w = row(3, 44, width=WIDTH, x0=LEFT)
    parts = [
        ('title', c.blue, [say(language, 'name of a DTO field', 'DTO 필드 이름'),
                           say(language, 'server sets the path', '엔티티 경로는 서버 지정')]),
        ('contains', c.purple, [say(language, 'operator name', '연산자 이름'),
                                say(language, 'declared ones only', '선언한 연산자만 허용')]),
        ('Spring', c.green, [say(language, 'search value', '검색 값'),
                             say(language, 'converted to its type', '필드 타입으로 변환'),
                             say(language, 'checked against constraints', '제약 조건 검증')]),
    ]
    top = 24
    boxes = cards_row(c, xs, top, w, [(name, lines) for name, _, lines in parts],
                      accents=[accent for _, accent, _ in parts], band=True, title_size=EMPH)
    for index, sign in enumerate(('.', '=')):
        label(c, xs[index] + w + 22, centered_baseline(top, 42, EMPH), sign, EMPH, weight=700, anchor='middle')
    below = boxes[0][1] + boxes[0][3] + 30
    start = heading(c, LEFT, below, say(language, 'Reserved parameters', '예약 파라미터'))
    reserved = [
        ('sort', [say(language, 'field.direction, comma separated', '필드.방향을 쉼표로 나열'),
                  say(language, 'sortable fields only', 'sortable 필드만 허용')]),
        ('page · size', [say(language, 'page number from 0', '페이지 번호는 0부터'),
                         say(language, 'default size 20', '크기 기본값 20')]),
        ('keyword', [say(language, 'all keyword fields', '키워드 필드 전체 검색'),
                     say(language, 'combined with AND', '다른 조건과 AND 결합')]),
    ]
    cards_row(c, xs, start, w, reserved, accents=c.t['fg_dim'])
    save(c, out(language, 'get-parameter-anatomy'))


@figure(plain=True)
def condition_group_evaluation(language):
    # Claim: a flat list combines from the left, so a different grouping needs a nested group.
    c = canvas(1000, 440)
    names = [say(language, 'title Spring', '제목 Spring'), say(language, 'title JPA', '제목 JPA'),
             say(language, 'status PUBLISHED', '상태 PUBLISHED')]
    widths = [210, 180, 270]
    rows = [
        (say(language, 'Flat list', '평면 목록'), say(language, 'combined from the left', '왼쪽부터 결합'), (0, 1),
         say(language, '(title Spring OR title JPA) AND status PUBLISHED',
             '(제목 Spring OR 제목 JPA) AND 상태 PUBLISHED')),
        (say(language, 'Nested group', '중첩 그룹'), say(language, 'group combined first', '그룹 먼저 결합'), (1, 2),
         say(language, 'title Spring OR (title JPA AND status PUBLISHED)',
             '제목 Spring OR (제목 JPA AND 상태 PUBLISHED)')),
    ]
    height = pill_h(BODY, 12)
    verdict_h = 46
    y = 34
    for name, tag, grouped, verdict in rows:
        zone_h = zone_height(height + 28 + 16 + verdict_h)
        inner = zone(c, LEFT, y, WIDTH, zone_h, c.blue, name, tag=tag)
        gap = 78
        x = LEFT + (WIDTH - sum(widths) - 2 * gap) / 2
        xs = []
        for width in widths:
            xs.append(x)
            x += width + gap
        first, last = grouped
        tint(c, xs[first] - 14, inner, xs[last] + widths[last] - xs[first] + 28, height + 28, c.orange)
        for index, text in enumerate(names):
            pill(c, xs[index], inner + 14, widths[index], height, text, c.blue, rx=10, fill=c.t['bg'])
        for index, word in enumerate(('OR', 'AND')):
            label(c, xs[index] + widths[index] + gap / 2, centered_baseline(inner + 14, height, CARD), word,
                  CARD, weight=700, anchor='middle')
        note(c, LEFT + 16, inner + height + 44, WIDTH - 32, verdict, c.orange, color=c.t['fg'])
        y += zone_h + 34
    save(c, out(language, 'condition-group-evaluation'))


@figure(plain=True)
def date_range_boundary(language):
    # Claim: a date-only end covers its whole day, while a timestamp end stops at that instant.
    c = canvas(1000, 330)
    day0, day = 120, 204
    dim = c.t['fg_dim']
    posts = [(day0 + day * 0.375, '10-01 09:00'), (day0 + day * 2.375, '10-03 09:00')]
    ranges = [
        ('createdAt.between=2026-10-01,2026-10-03', 3, c.blue,
         say(language, 'whole of Oct 3', '10월 3일 전체 포함')),
        ('createdAt.between=2026-10-01T00:00:00Z,2026-10-03T00:00:00Z', 2, c.orange,
         say(language, 'ends at Oct 3 00:00', '10월 3일 0시 종료')),
    ]
    y = 24
    for text, days, accent, end in ranges:
        label_at_top(c, LEFT, y, text, BODY, weight=600)
        label_at_top(c, LEFT + WIDTH, y, end, BODY, color=accent, weight=700, anchor='end')
        bar_y = y + BODY + 14
        c.rrect(day0, bar_y, day * days, 26, rx=4, fill=accent, stroke=accent, sw=HAIRLINE, opacity=0.3,
                measure='width')
        for x, _ in posts:
            disc(c, x, bar_y + 13, 7, c.t['fg'])
        y = bar_y + 26 + 38
    axis = y + 22
    rule(c, day0, axis, day0 + day * 4, axis)
    for index in range(5):
        x = day0 + day * index
        rule(c, x, axis - 7, x, axis + 7)
        label_at_top(c, x, axis + 14, f'10-0{index + 1}', MICRO, color=dim, anchor='middle')
    for x, created in posts:
        disc(c, x, axis, 7, c.t['fg'])
        label_at_top(c, x, axis - 14 - MICRO, say(language, 'post ', '게시글 ') + created, MICRO, anchor='middle')
    save(c, out(language, 'date-range-boundary'))


for language in LANGUAGES:
    adoption_path(language)
    search_request_flow(language)
    get_parameter_anatomy(language)
    condition_group_evaluation(language)
    date_range_boundary(language)
