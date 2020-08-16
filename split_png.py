#! /usr/bin/env python3
# -*- coding: utf-8 -*-


import logging

from functools import partial
from itertools import chain, product

import numpy as np
import scipy.ndimage as ndimage

import png


def read_file(file, normal=False):
    reader = png.Reader(file)
    width, height, data, meta = reader.asFloat() if normal else reader.asDirect()

    if normal:
        dtype = np.float64
    else:
        depth = meta['bitdepth']
        dtype = np.uint16 if depth > 8 else np.uint8 if depth > 1 else np.bool

    data = np.vstack(map(dtype, data)).reshape((height, width, meta['planes']))
    return data, meta

def get_bounds(mask):
    for dimension in mask.nonzero():
        yield slice(dimension.min(), dimension.max()+1)

def mark_visible(data, diagonal=False, nested=True):
    r = range(3)
    structure = np.hstack(diagonal or (1 in c) for c in product(r, r))
    structure.shape = 3, 3

    logging.info('Beginning region labeling using the struct')
    logging.info(np.array2string(structure))

    regions, count = ndimage.label(data, structure=structure)
    if nested:
        zero, zerocount = ndimage.label(regions==0, structure=structure)
        parts = set(chain(np.flatiter(zero[[0, -1], :]), np.flatiter(zero[:, [0, -1]])))
        for p in parts:
            zero[zero==p] = 0
        zero = zero.astype(np.bool)
        if zero.any():
            regions[zero] = 1
            regions, count = ndimage.label(regions, structure=structure)
    logging.info('Region labeling has finished, recognized {} regions'.format(count))

    return regions, count

def get_region(data, regions, number):
    y, x = get_bounds(regions == number)
    img = data[y, x, :].copy()
    img[regions[y, x] != number] = 0
    img[img[:, :, -1] == 0] = 0

    return img

def write_file(path, data, bitdepth=None, gamma=None, compression=None, interlace=False):
    height, width, planes = data.shape
    greyscale = planes < 3
    alpha = not (planes % 2)
    bitdepth = bitdepth or 8 * data.dtype.itemsize
    logging.info('determined that the PNG file shall be written as {}-bit image (greyscale={}, alpha={})'.format(bitdepth, greyscale, alpha))

    writer = png.Writer(width=width, height=height, greyscale=greyscale, alpha=alpha,
                        bitdepth=bitdepth, gamma=gamma, compression=compression,
                        interlace=interlace)
    data = data.reshape((height, width*planes))
    with open(path, 'wb') as file:
        writer.write(file, data)


def main():
    import os
    from argparse import ArgumentParser, FileType

    desc = 'Separates a PNG image into its components, as split by transparency boundaries.'
    parser = ArgumentParser(description=desc)

    parser.add_argument('file', help='The PNG file to read and separate into its components',
                        type=FileType('rb'))
    parser.add_argument('-p', '--pattern', help="The filename pattern used to create the output files, as a Format String. The first positional argument is the input file's directory, the second one is the filename (without extension), and the third is the extension (including a dot). The keyword argument 'i' is used to specify the number of the current component.",
                        type=str, default=os.path.join('{0}', '{1}_{i:02d}.png'))
    parser.add_argument('-d', '--diagonal', help='Treat diagonal connections between points as valid',
                        action='store_true', default=False)
    parser.add_argument('-n', '--no-nested', help="Treat nested portions of the image as stand-alone images rather than as part of their root element",
                        action='store_false', default=True, dest='nested')
    parser.add_argument('-o', '--no-original', help="Don't output the cropped original image before the individual components",
                        action='store_false', default=True, dest='crop')
    parser.add_argument('-c', '--compression', metavar='{-1 - 9}', help='The zlib compression factor',
                        type=int, choices=range(-1, 10), default=-1)
    parser.add_argument('-l', '--interlace', help='Store the resulting PNG images in interlaced format',
                        action='store_true', default=False)

    args = parser.parse_args()
    logging.debug('arguments parsed: {}'.format(args))
    path = os.path.dirname(args.file.name) or '.'
    logging.debug('input file: directory="{}"'.format(path))
    name, ext = os.path.splitext(os.path.basename(args.file.name))
    logging.debug('input file: name="{}", extension="{}"'.format(name, ext))

    with args.file as file:
        data, meta = read_file(file)
    logging.info('Finished reading a {0[0]}x{0[1]} image'.format(meta['size']))
    logging.info('Image metadata is {}'.format(meta))
    if (data.shape[2] % 2):
        raise ValueError('The PNG image has no alpha channel!')

    y, x = get_bounds(data[:, :, -1])
    data = data[y, x, :].copy() # no longer reference the original memory, which contains the whole image
    logging.info('The image has been cropped to its active area, now its dimensions are {0[1]}x{0[0]}'.format(data.shape))
    gamma = meta.get('gamma', None)
    bitdepth = meta['bitdepth']

    if args.crop:
        filepath = args.pattern.format(path, name, ext, i=args.start-args.step)
        logging.info('Writing cropped image with dimensions {0[1]}x{0[0]} to "{1}"'.format(data.shape, filepath))
        write_file(filepath, data, bitdepth=bitdepth, gamma=gamma, interlace=args.interlace, compression=args.compression)
        logging.info('Finished writing of image')

    logging.debug('Segmenting the image into areas, diagonal paths are set to {}'.format(args.diagonal))
    regions, count = mark_visible(data, diagonal=args.diagonal, nested=args.nested)
    logging.info('Segmented the image into {} regions'.format(regions.max()))

    if count < 2 and args.crop:
        return

    region_get = partial(get_region, data, regions)
    numbers = range(1, count+1)
    for number, region in zip(numbers, map(region_get, numbers)):
        filepath = args.pattern.format(path, name, ext, i=number)
        logging.info('Writing region #{0} with dimensions {1[1]}x{1[0]} to "{2}"'.format(number, region.shape, filepath))
        write_file(filepath, region, gamma=gamma, interlace=args.interlace, compression=args.compression)
        logging.info('Finished writing of region')

if __name__ == '__main__':
    import time
    logging.basicConfig(level=logging.NOTSET)
    logging.info('Started at {}'.format(time.asctime()))
    try:
        main()
    except:
        t = 'Terminated'
        logging.exception('Script terminated in an unusual manner...')
    else:
        t = 'Finished'
    logging.info('{} at {}'.format(t, time.asctime()))
    input('Press <Enter> to close! ')
