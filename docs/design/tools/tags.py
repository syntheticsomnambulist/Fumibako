"""Checks that a design-canvas board's markup (everything before its script) has balanced tags.
Usage: python3 tags.py Board.dc.html [...]"""
import sys
from html.parser import HTMLParser

VOID = {'meta', 'link', 'br', 'img', 'input', 'hr', 'source', 'rect', 'circle', 'path', 'polygon', 'ellipse', 'line'}


class Tags(HTMLParser):
    def __init__(self):
        super().__init__()
        self.stack, self.errors = [], []

    def handle_starttag(self, tag, attrs):
        if tag not in VOID:
            self.stack.append(tag)

    def handle_endtag(self, tag):
        if tag in VOID:
            return
        if not self.stack or self.stack[-1] != tag:
            self.errors.append((tag, self.getpos()))
        else:
            self.stack.pop()


for path in sys.argv[1:]:
    src = open(path).read()
    src = src[:src.index('<script type="text/x-dc"')]
    t = Tags()
    t.feed(src)
    left = [x for x in t.stack if x not in ('html', 'body')]
    print(path, 'balanced' if not t.errors and not left else f'errors {t.errors[:3]}, unclosed {left}')
