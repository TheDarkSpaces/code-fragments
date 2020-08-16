# -*- coding: utf-8 -*-

from operator import mul, itemgetter, attrgetter
from itertools import starmap
from functools import partial
from collections import deque


class Track:
    __slots__ = 'title', 'indices', 'end', 'isrc'

    @staticmethod
    def time_to_frame(time):
        return sum(map(mul, (1, 75, 60*75), map(int, time.split(':')[::-1])))

    @staticmethod
    def frame_to_time(frame):
        seconds, frames = divmod(frame, 75)
        minutes, seconds = divmod(seconds, 60)
        return ':'.join(map('{:02d}'.format, (minutes, seconds, frames)))

    @staticmethod
    def frame_to_sample(frame, sample_rate=44100):
        if frame is not None:
            s, mod = divmod(frame*sample_rate, 75)
            return s + (mod>37) # rounding

    @property
    def start(self):
        return self.indices[0]
    @start.setter
    def start(self, x):
        if self.indices:
            self.indices[0] = x
        else:
            self.indices = [x]

    @property
    def times(self):
        return tuple(map(self.frame_to_time, self.indices))
    @property
    def endtime(self):
        return self.frame_to_time(self.end) if self.end is not None else None
    @property
    def slices(self):
        yield from map(slice, _pair(self.indices))
        yield slice(self.indices[-1], self.end)

    def audio_slices(self, sample_rate=44100):
        op = partial(self.frame_to_sample, sample_rate=sample_rate)
        op = partial(map, op)

        pairs = map(attrgetter('start', 'stop'), self.slices)
        pairs = map(op, pairs)
        yield from starmap(slice, pairs)

    def __init__(self, title=None, isrc=None, indices=None, end=None):
        self.title = title
        self.isrc = isrc
        self.indices = indices if indices is not None else []
        self.end = end

    def __repr__(self):
        v = (self.__slots__[0], 'times', 'endtime', self.__slots__[-1])
        v = zip(v, map(partial(getattr, self), v))
        v = filter(itemgetter(1), v)
        v = ', '.join(starmap('{}={!r}'.format, filter(itemgetter(1), v)))
        return '''Track({})'''.format(v)


def _pair(iterable):
    iterator = iter(iterable)
    d = deque([next(iterator)], maxlen=2)
    while True:
        d.append(next(iterator))
        yield tuple(d)

def _prepare(line):
    try:
        head, body = line.split(maxsplit=1)
    except ValueError:
        return
    head = head.lower()
    if body[0] == body[-1] == '"':
        body = body[1:-1].replace("''", '"')

    return (head.lower(), (body[1:-1] if body[0]==body[-1]=='"' else body))

def parse(text):
    tracks = []
    previous, current = None, Track()
    for head, body in filter(None, map(_prepare, text.splitlines())):
        if head == 'track':
            previous, current = current, Track()
            tracks.append(current)
        elif head in ('isrc', 'title'):
            setattr(current, head, body)
        elif head == 'index':
            number, time = body.split()
            time = Track.time_to_frame(time)
            if int(number):
                current.indices.append(time)
            if previous:
                previous.end = time
                previous = None

    return tracks
