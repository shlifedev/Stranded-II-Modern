"""PCM chunk layouts accepted without package conversion."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
import struct
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'devtools'))
from web_audio import supported_wav


def chunk(tag, payload):
    return tag + struct.pack('<I', len(payload)) + payload + bytes(len(payload) % 2)


class PCMChunks(TestCase):
    def test_metadata_padding_and_incomplete_audio(self):
        fmt = chunk(b'fmt ', struct.pack('<HHIIHH', 1, 1, 22050, 44100, 2, 16))
        data = chunk(b'data', bytes(32))
        metadata = chunk(b'JUNK', b'odd')
        with TemporaryDirectory() as directory:
            path = Path(directory) / 'sample.wav'
            for contents, expected in ((fmt + data, True), (metadata + fmt + metadata * 80 + data, True),
                                       (fmt + data[:-1], False), (fmt, False), (data + fmt, False)):
                with self.subTest(size=len(contents), expected=expected):
                    path.write_bytes(b'RIFF' + struct.pack('<I', len(contents)+4) + b'WAVE' + contents)
                    self.assertEqual(supported_wav(path), expected)
