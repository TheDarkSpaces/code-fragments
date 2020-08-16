# -*- coding: utf-8 -*-

import abc
import collections


class MetaABC(abc.ABCMeta):
    def __new__(metacls, name, bases, namespace):
        cls = super().__new__(metacls, name, bases, namespace)
        if not hasattr(cls, 'uids'):
            cls.uids = {}
        if 'uid' in namespace:
            if 0 <= namespace['uid'] < 0x7f:
                cls.uids[namespace['uid']] = cls
            else:
                raise ValueError('The UID is out of range!')
        return cls

class Metadata(metaclass=MetaABC):
    __slots__ = ()

    @classmethod
    def split(cls, data):
        last = int.from_bytes(data[:1], 'big', signed=False) & 0x80
        uid = int.from_bytes(data[:1], 'big', signed=False) & 0x7f
        length = int.from_bytes(data[1:4], 'big', signed=False)

        return bool(last), cls.uids[uid](data[4:length+4]), data[length+4:]

    @abc.abstractproperty
    def data(self):
        yield from ()
        raise NotImplementedError

    def raw(self, last=False):
        def values():
            value = (last<<7) | self.uid
            yield value.to_bytes(1, 'big', signed=False)
            yield len(self).to_bytes(3, 'big', signed=False)
            yield from self.data
        return b''.join(values())

    def __len__(self):
        return self.length

class StreamInfo(Metadata):
    uid = 0
    length = 34

    __slots__ = '_data'

    @property
    def data(self):
        yield self._data

    def __init__(self, body):
        if isinstance(body, self.__class__):
            self._data = body._data.copy()
        elif len(body) != self.length:
            raise ValueError
        else:
            self._data = bytearray(body)

class Padding(Metadata):
    uid = 1

    __slots__ = 'length'

    @property
    def data(self):
        yield bytes(self.length)

    def __init__(self, body):
        self.length = body if isinstance(body, int) else len(body)

class Application(Metadata):
    uid = 2

    __slots__ = 'identifier', 'binary'

    @property
    def length(self):
        return 4 + len(self.binary)

    @property
    def data(self):
        yield self.identifier.to_bytes(4, 'big', signed=False)
        yield self.binary

    def __init__(self, body):
        if isinstance(body, self.__class__):
            for slot in self.__slots__:
                setattr(self, slot, getattr(body, slot))
        else:
            self.identifier = int.from_bytes(body[:4], 'big', signed=False)
            self.binary = body[4:]

class Seektable(Metadata):
    uid = 3

    __slots__ = '_data'

    @property
    def length(self):
        return len(self._data)

    @property
    def data(self):
        yield self._data

    def __init__(self, body):
        if isinstance(body, self.__class__):
            self._data = body._data
        else:
            self._data = body

class Vorbis_Comment(Metadata):
    uid = 4

    __slots__ = 'vendor', 'order_key', 'tags'

    @staticmethod
    def split_number(binary, length=4, signed=False):
        value = int.from_bytes(binary[:length], 'little', signed=signed)
        return value, binary[length:]
    @staticmethod
    def code_number(value, length=4, signed=False):
        return value.to_bytes(length, 'little', signed=False)

    @classmethod
    def split_str(cls, binary):
        length, value = cls.split_number(binary)
        value = binary[:length].decode(encoding='utf-8')
        return value, binary[length:]
    @classmethod
    def code_str(cls, value):
        binary = value.encode(encoding='utf-8')
        yield cls.code_number(len(binary))
        yield binary

    @property
    def length(self):
        return sum(map(len, self.data))

    @property
    def data(self):
        yield from self.code_str(self.vendor)
        count = sum(map(len, self.tags.values()))
        yield self.code_number(count)

        keys = self.tags.keys()
        if self.order_key:
            keys = sorted(keys, key=self.order_key)

        for key, values in zip(keys, map(self.tags.__getitem__, keys)):
            for value in values:
                yield from self.code_str('{}={}'.format(key, value))

    def __init__(self, body, order_key=None):
        if isinstance(body, self.__class__):
            self.vendor = body.vendor
            self.order_key = order_key or body.order_key
            self.tags = body.tags.copy()
        else:
            self.order_key = order_key
            self.tags = collections.defaultdict(list)

            self.vendor, body = self.split_str(body)
            length, body = self.split_number(body)
            for _ in range(length):
                entry, body = self.split_str(body)
                key, value = str.split('=', maxsplit=1)
                self.tags[key].append(value)

class Cuesheet(Metadata):
    uid = 5

    __slots__ = '_data'

    @property
    def length(self):
        return len(self._data)

    @property
    def data(self):
        yield self._data

    def __init__(self, body):
        if isinstance(body, self.__class__):
            self._data = body._data
        else:
            self._data = body

class Picture(Metadata):
    uid = 6

    __slots__ = '_data'

    @property
    def length(self):
        return len(self._data)

    @property
    def data(self):
        yield self._data

    def __init__(self, body):
        if isinstance(body, self.__class__):
            self._data = body._data
        else:
            self._data = body

class File:
    header = b'fLaC'

    __slots__ = 'stream_info', 'metadata', 'audio_data'

    @staticmethod
    def split_blocks(data):
        while True:
            last, meta, data = Metadata.split(data)
            yield meta
            if last:
                break
        yield data

    def __init__(self, data):
        if isinstance(data, self.__class__):
            for slot in self.__slots__:
                setattr(self, slot, getattr(data, slot))
            return

        if isinstance(data, str):
            with open(data, 'rb') as file:
                data = file.read()

        with memoryview(data) as data:
            if data[:4] != self.header:
                raise ValueError('Incorrect file header!')

            data = data[4:].tobytes()

        self.stream_info, *self.metadata, self.audio_data = self.split_blocks(data)
