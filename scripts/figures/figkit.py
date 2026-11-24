"""Shared pieces of the documentation figures: the two languages, labels and connectors."""
from common import BODY, HAIRLINE, PAD, STROKE, baseline_for_top, canvas, disc, zone
from svgkit import SANS

LANGUAGES = ('en', 'ko')
LEFT = 24
WIDTH = 952


def _zone_inset():
    """How far below its top a zone's content starts, measured once on a canvas that is never saved."""
    probe = canvas(200, 200)
    return zone(probe, 0, 0, 200, 200, probe.blue, 'x')


ZONE_INSET = _zone_inset()


def zone_height(content_height):
    """The height of a zone that holds content of this height with one inset on every side."""
    return ZONE_INSET + content_height + PAD


def say(language, en, ko):
    """The label of a figure in the language it is drawn for."""
    return ko if language == 'ko' else en


def out(language, name):
    """Where a figure of a language is written, under the documentation folder."""
    return f'{language}/_images/{name}'


def label(c, x, y, text, size=BODY, color=None, weight=400, anchor='start'):
    """One line of text in the document's typeface, on baseline y."""
    c.text(x, y, text, size=size, color=color or c.t['fg'], family=SANS, weight=weight, anchor=anchor)


def label_at_top(c, x, top, text, size=BODY, color=None, weight=400, anchor='start'):
    """One line of text whose glyph top sits at top."""
    label(c, x, baseline_for_top(top, size), text, size, color, weight, anchor)


def arrow(c, x1, y1, x2, y2, color=None):
    """A straight arrow in one colour."""
    color = color or c.t['fg_dim']
    c.line(x1, y1, x2, y2, color=color, sw=STROKE, marker=color)


def rule(c, x1, y1, x2, y2, color=None, sw=STROKE):
    """A line that is not a route: an axis, a bracket, a bus."""
    c.line(x1, y1, x2, y2, color=color or c.t['fg_dim'], sw=sw, marker=None)


def tint(c, x, y, w, h, color, opacity=0.14, rx=12):
    """A tinted ground behind a group of boxes, drawn as a path so it is not read as a box."""
    c.band(x, y, w, h, rx, color, opacity=opacity, corners='tl tr br bl')


def mark(c, cx, cy, chosen, color):
    """Whether a row is selected: a filled disc when it is, an empty ring when it is not."""
    if chosen:
        disc(c, cx, cy, 11, color)
    else:
        disc(c, cx, cy, 11, c.t['bg'], stroke=c.t['line'], sw=HAIRLINE)
