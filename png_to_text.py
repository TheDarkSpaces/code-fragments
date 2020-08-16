#! python3
# -*- coding: utf-8 -*-

from math import sqrt
from tkinter import Tk
from operator import sub
from itertools import repeat
from fractions import Fraction
from collections import deque # builtins

from numpy import ndarray, float64, matrix # http://numpy.scipy.org/

from png import Reader # https://pypi.python.org/pypi/pypng


def png_to_normalized(file):
    r = Reader(file)
    width, height, pixels, metadata = r.asFloat()
    grey = metadata['greyscale']
    channels = 2*(not grey) + metadata['alpha'] + 1

    y_rows = enumerate(map(tuple, pixels))
    columns = range(0, width*channels, channels)
    channels = (range if grey else repeat)(0, 3)
    x_c = ((x, c) for x in columns for c in channels)

    data = ndarray((width, height, 3), dtype=float64)
    for *xyc, v in ((x, y, c, row[x+c]) for y, row in y_rows for x, c in x_c):
        data[xyc] = v
    return data

def sRGB_to_Lab(sRGB):
    """Converts normalized sRGB data to L*a*b* (CIELAB)."""
    def linearize(channel):
        """Converts a channel from sRGB to linear light."""
        if channel <= 0.0404482362771082:
            return channel / 12.92
        else:
            return ((channel+0.055) / 1.055) ** 2.4
    def to_XZY(rgb):
        """Converts RGB to XYZ."""
        return matrix([[0.4124, 0.3576, 0.1805],
                       [0.2126, 0.7152, 0.0722],
                       [0.0193, 0.1192, 0.9505]]).dot(rgb).A[0]
    def f(t):
        """Pre-Transforms an XYZ channel for conversion to L*a*b*."""
        if t > Fraction(6, 29)**3:
            return t ** Fraction(1, 3)
        else:
            return Fraction(1, 3) * Fraction(29, 6)**2 * t + Fraction(4, 29)
    def join_2(iterable):
        iterator = iter(iterable)
        d = deque([next(iterator)], maxlen=2)
        while True:
            d.append(next(iterator))
            yield tuple(d)

    pre = tuple(map(f, to_XZY(list(map(linearize, sRGB))) / to_XZY(3*[1])))
    lab = [116*pre[1] - 16] + [m * sub(*pair) for m, pair in zip((500, 200),
                                                                 join_2(pre))]
    return lab

def quantize_image(data, colors):
    for x, col in enumerate(data):
        for y, point in enumerate(map(sRGB_to_Lab, col)):
            def color_distance(c):
                return sqrt(sum(sub(*pair) ** 2 for pair in zip(c, point)))
            col[x] = min(colors, key=color_distance)

            # dithering with Sierra-2-4A
            error = point - col[x]
            if x+1 < len(data):
                data[x+1, y] += error * 0.5
            if y+1 < len(data):
                for c in (x-i for i in range(2) if x-i >= 0):
                    data[c, y+1] += error * 0.25

def convert_image_into_text(data, colors, newline='\n'):
    return newline.join(''.join(map(colors.get, map(tuple, c))) for c in data)


def _get_Lab_at(i, div):
    off, mod = divmod(i, 8)
    values = (off + 3*int(c) for c in '{:03b}'.format(mod))
    return tuple(sRGB_to_Lab(v/div for v in values))

def _text_to_clipboard(text):
    root = Tk()
    root.withdraw()
    root.clipboard_clear()
    root.clipboard_append(text)
    root.destroy()


foreground = {_get_Lab_at(i,  4): '&{i:1x}[x]'.format(i=i) for i in range(16)}
background = {_get_Lab_at(i, 16): '&{i:1x}[x]'.format(i=i) for i in range(16)}

if __name__ == '__main__':
    from argparse import ArgumentParser, FileType

    desc = 'Converts a PNG image to text.'
    hlp = 'The PNG file to convert to text.'
    parser = ArgumentParser(description=desc)
    parser.add_argument('file', help=hlp, type=FileType('rb'))
    parser.add_argument('-b', '--background', help='Use background colors',
                        action='store_true', default=False)
    args = parser.parse_args()

    color = background if args.background else foreground
    with args.file as png:
        data = png_to_normalized(png.read())
    quantize_image(data, foreground)

    text = convert_image_into_text(data, foreground)
    _text_to_clipboard(text)
