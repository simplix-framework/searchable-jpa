"""Figures of the query side: matching through a collection, reading windows, strategy, scope and writes.

Built with the svg-diagrams skill's scripts/docfigures/build.py from the repository root.
No component performs model work, so no figure carries an AI mark.
"""
from common import (BODY, CARD, HAIRLINE, MICRO, PAD, canvas, card, card_h, cards_row, centered_baseline,
                    figure, heading, note, pill, pill_h, row, save, step_row, zone)
from figkit import (LANGUAGES, LEFT, WIDTH, arrow, label, label_at_top, mark, out, rule, say, tint,
                    zone_height)

POSTS = ['Spring Boot basics', 'JPA paging in depth', 'Query tuning draft', 'Spring Security notes']


def collection_match_modes(language):
    # Claim: over the same comments, the three ways of matching select different posts.
    c = canvas(1000, 620)
    lead_w, col_w, gap = 300, 200, 17
    xs = [LEFT + lead_w + gap + index * (col_w + gap) for index in range(3)]
    modes = [
        ('JOINED', c.blue, [say(language, 'Good and kim in one comment', 'Good과 kim이 같은 댓글')]),
        ('ANY', c.purple, [say(language, 'Good in a comment', 'Good 댓글 있음'),
                           say(language, 'kim in a comment', 'kim 댓글 있음')]),
        ('NONE', c.orange, [say(language, 'kim in no comment', 'kim 댓글 없음')]),
    ]
    top = 24
    lead = card(c, LEFT, top, lead_w, c.t['fg_dim'], say(language, 'Posts and comments', '게시글과 댓글'),
                [say(language, 'content, then writer', '내용, 작성자 순서')])
    heads = cards_row(c, xs, top, col_w, [(name, lines) for name, _, lines in modes],
                      accents=[accent for _, accent, _ in modes], wash=True)
    posts = [
        (POSTS[0], ['Good intro, lee', 'Too short, kim'], (False, True, False)),
        (POSTS[1], ['Good examples, kim', 'Needs update, lee'], (True, True, False)),
        (POSTS[2], [say(language, 'no comments', '댓글 없음')], (False, False, True)),
        (POSTS[3], ['Good, lee'], (False, False, True)),
    ]
    y = top + max(lead[3], heads[0][3]) + 18
    for title, comments, chosen in posts:
        box = card(c, LEFT, y, lead_w, c.t['fg_dim'], title, comments, title_size=BODY)
        for index, selected in enumerate(chosen):
            mark(c, xs[index] + col_w / 2, y + box[3] / 2, selected, modes[index][1])
        y += box[3] + 14
    save(c, out(language, 'collection-match-modes'))


@figure(plain=True)
def offset_versus_keyset(language):
    # Claim: an offset page reads and discards every earlier row, a keyset window starts after the last one.
    c = canvas(1000, 330)
    cell, gap, count = 56, 6, 12
    x0 = LEFT + WIDTH - (count * cell + (count - 1) * gap)
    dim = c.t['fg_dim']
    rows = [
        (say(language, 'Offset page', '오프셋 페이지'), 'page=2, size=3',
         say(language, 'rows read and discarded', '읽고 버리는 행'), dim, 0.28),
        (say(language, 'Keyset window', 'keyset 스크롤'), say(language, 'cursor of row 6', '6번 행의 커서'),
         say(language, 'rows never read', '읽지 않는 행'), None, None),
    ]
    y = 24
    for title, sub, skipped, fill, opacity in rows:
        label_at_top(c, LEFT, y, title, CARD, weight=700)
        label_at_top(c, LEFT, y + CARD + 8, sub, BODY, color=dim)
        for index in range(count):
            x = x0 + index * (cell + gap)
            if 6 <= index < 9:
                c.rrect(x, y, cell, 48, rx=6, fill=c.blue, stroke=c.blue, sw=HAIRLINE, opacity=0.35)
            elif index < 6 and fill:
                c.rrect(x, y, cell, 48, rx=6, fill=fill, stroke=fill, sw=HAIRLINE, opacity=opacity)
            else:
                c.rrect(x, y, cell, 48, rx=6, fill=c.t['bg'], stroke=c.t['line'], sw=HAIRLINE)
            label(c, x + cell / 2, centered_baseline(y, 48, MICRO), str(index + 1), MICRO, anchor='middle')
        bracket = y + 48 + 12
        spans = [(0, 5, skipped, dim), (6, 8, say(language, 'rows returned', '반환하는 행'), c.blue)]
        for first, last, text, color in spans:
            left = x0 + first * (cell + gap)
            right = x0 + last * (cell + gap) + cell
            rule(c, left, bracket, right, bracket, color)
            rule(c, left, bracket - 6, left, bracket, color)
            rule(c, right, bracket - 6, right, bracket, color)
            label_at_top(c, (left + right) / 2, bracket + 10, text, BODY, color=c.t['fg'], weight=600,
                         anchor='middle')
        y = bracket + 10 + BODY + 40
    save(c, out(language, 'offset-versus-keyset'))


def join_strategy_decision(language):
    # Claim: only the sort, the fetch and the key decide the strategy; a filter never does.
    c = canvas(1000, 420)
    gate_w, channel = 240, 64
    result_x = LEFT + gate_w + channel
    result_w = WIDTH - gate_w - channel
    results = [
        (c.green, say(language, 'Direct query', '직접 조회'),
         [say(language, 'single key, no collection in sort or fetch', '단일 키, 정렬·fetch에 컬렉션 없음'),
          say(language, 'one pageable entity query', '엔티티 페이지 조회 한 번')]),
        (c.orange, say(language, 'Keys first, then entities', '키 선택 후 엔티티 로딩'),
         [say(language, 'sort or fetch through a collection, or a composite key',
              '정렬·fetch가 컬렉션 경유, 또는 복합키'),
          say(language, 'key page, then loading in batches', '키 페이지 조회 후 배치 로딩')]),
    ]
    top, gap = 24, 18
    heights = [card_h(result_w, title, lines) for _, title, lines in results]
    span = sum(heights) + gap
    gate = card(c, LEFT, top, gate_w, c.purple, say(language, 'Decided by', '결정 기준'),
                [say(language, 'sort paths', '정렬 경로'), say(language, 'fetch paths', 'fetch 경로'),
                 say(language, 'primary key', '기본키 구성')], h=span, valign='middle', wash=True)
    y = top
    for (accent, title, lines), height in zip(results, heights):
        card(c, result_x, y, result_w, accent, title, lines)
        arrow(c, gate[0] + gate_w, y + height / 2, result_x, y + height / 2, accent)
        y += height + gap
    note(c, LEFT, top + span + 22, WIDTH,
         say(language, 'A condition on a collection is one EXISTS: no effect on the choice',
             '컬렉션 조건은 EXISTS 하나로 적용: 방식 선택과 무관'), c.blue, color=c.t['fg'])
    save(c, out(language, 'join-strategy-decision'))


@figure(plain=True)
def two_phase_query_flow(language):
    # Claim: keys are paged first, entities are loaded by those keys, and the key order is restored.
    c = canvas(1000, 330)
    steps = [
        (say(language, 'Select keys', '키 페이지 선택'), say(language, 'filter, sort, page', '조건 · 정렬 · 페이지')),
        (say(language, 'Count', '전체 건수'), say(language, 'when needed', '짧은 페이지는 생략')),
        (say(language, 'Load entities', '엔티티 로딩'), say(language, 'IN batches by key', '키 배치 IN 조회')),
        (say(language, 'Restore order', '순서 복원'), say(language, 'order of step 1', '1단계 키 순서')),
    ]
    samples = [say(language, 'keys 4, 3', '키 4, 3'), say(language, '4 in total', '전체 4건'),
               say(language, 'rows 3, 4', '행 3, 4'), say(language, 'rows 4, 3', '행 4, 3')]
    xs, w = row(4, 34, width=WIDTH, x0=LEFT)
    top = 24
    h = step_row(c, xs, w, top, steps, c.blue, sep='arrow', sub_size=BODY)
    start = heading(c, LEFT, top + h + 30,
                    say(language, 'Example: newest first, two per page', '예: 최신순, 페이지 크기 2'), size=CARD)
    height = pill_h(BODY, 12)
    for index, text in enumerate(samples):
        pill(c, xs[index], start, w, height, text, c.orange, rx=10)
    save(c, out(language, 'two-phase-query-flow'))


@figure(plain=True)
def scope_combination(language):
    # Claim: the server scope is combined outside the whole client expression, so an OR cannot widen it.
    c = canvas(1000, 360)
    height = pill_h(BODY, 12)
    outer_w = 420
    inner_content = height
    inner_h = zone_height(inner_content)
    outer_h = zone_height(height + 26 + inner_h)
    top = 34
    y = zone(c, LEFT, top, outer_w, outer_h, c.green, say(language, 'Server scope', '서버 범위'),
             tag=say(language, 'always AND', '항상 AND'))
    pill(c, LEFT + PAD, y, outer_w - 2 * PAD, height, 'tenantId = 1', c.green, rx=10)
    inner_y = y + height + 26
    content = zone(c, LEFT + PAD, inner_y, outer_w - 2 * PAD, inner_h, c.blue,
                   say(language, 'Client conditions', '클라이언트 조건'))
    inner_w = outer_w - 4 * PAD
    word_w = 50
    pill_w = (inner_w - word_w) / 2
    pill(c, LEFT + 2 * PAD, content, pill_w, height, 'status DRAFT', c.blue, rx=10)
    label(c, LEFT + 2 * PAD + pill_w + word_w / 2, centered_baseline(content, height, CARD), 'OR', CARD,
          weight=700, anchor='middle')
    pill(c, LEFT + 2 * PAD + pill_w + word_w, content, pill_w, height, 'title Spring', c.blue, rx=10)

    table_x = LEFT + outer_w + 30
    title_w = 232
    columns = [say(language, 'match', '조건'), say(language, 'scope', '범위'), say(language, 'result', '반환')]
    column_w = (LEFT + WIDTH - table_x - title_w) / 3
    centres = [table_x + title_w + column_w * (index + 0.5) for index in range(3)]
    rows = [(POSTS[0], (True, True, True)), (POSTS[1], (False, True, False)),
            (POSTS[2], (True, True, True)), (POSTS[3], (True, False, False))]
    step = (outer_h - 40) / len(rows)
    for centre, text in zip(centres, columns):
        label_at_top(c, centre, top, text, BODY, weight=700, anchor='middle')
    colors = [c.blue, c.green, c.t['fg']]
    for index, (title, marks) in enumerate(rows):
        centre_y = top + 40 + step * (index + 0.5)
        label(c, table_x, centered_baseline(centre_y - 12, 24, BODY), title, BODY)
        for column, chosen in enumerate(marks):
            mark(c, centres[column], centre_y, chosen, colors[column])
    save(c, out(language, 'scope-combination'))


def bulk_write_chunks(language):
    # Claim: a search-based write walks the keys in chunks inside one transaction, entity by entity.
    c = canvas(1000, 460)
    chunks = [(say(language, 'Chunk 1', '처리 단위 1'), say(language, 'keys 1 to 200', '키 1~200')),
              (say(language, 'Chunk 2', '처리 단위 2'), say(language, 'keys 201 to 400', '키 201~400')),
              (say(language, 'Chunk 3', '처리 단위 3'), say(language, 'keys from 401', '키 401 이후'))]
    inner_x, inner_w = LEFT + PAD, WIDTH - 2 * PAD
    xs, w = row(3, 44, width=inner_w, x0=inner_x)
    probe = canvas(400, 200)
    step_h = step_row(probe, [0], w, 0, chunks[:1], probe.blue, numbered=False, sep=None, sub_size=BODY)
    note_h = 46
    top = 34
    zone_h = zone_height(step_h + 16 + note_h)
    y = zone(c, LEFT, top, WIDTH, zone_h, c.blue, say(language, 'One transaction', '한 트랜잭션'),
             tag=say(language, 'the caller\'s when one is open', '호출자 트랜잭션에 참여'))
    step_row(c, xs, w, y, chunks, c.blue, numbered=False, sep='arrow', sub_size=BODY)
    note(c, inner_x, y + step_h + 16, inner_w,
         say(language, 'Per chunk: read by key, apply to each entity, flush',
             '처리 단위마다 키 순서 조회, 엔티티별 적용, flush'), c.blue, color=c.t['fg'])
    below = top + zone_h + 26
    halves, half_w = row(2, 34, width=WIDTH, x0=LEFT)
    cards_row(c, halves, below, half_w, [
        (say(language, 'Left unchanged by an update', '수정에서 제외하는 값'),
         [say(language, 'identifier and version', '식별자와 버전'),
          say(language, 'audit fields', '감사 필드'),
          say(language, 'columns marked updatable=false', 'updatable=false 컬럼'),
          say(language, 'properties that are null in the patch', '패치에서 null인 속성')]),
        (say(language, 'Start key of the next chunk', '다음 처리 단위의 시작 키'),
         [say(language, 'taken before the chunk is changed', '현재 단위 수정 전에 확보'),
          say(language, 'no row skipped by the change', '조건 필드를 수정해도 누락 없음')]),
    ], accents=[c.orange, c.green])
    save(c, out(language, 'bulk-write-chunks'))


for language in LANGUAGES:
    collection_match_modes(language)
    offset_versus_keyset(language)
    join_strategy_decision(language)
    two_phase_query_flow(language)
    scope_combination(language)
    bulk_write_chunks(language)
