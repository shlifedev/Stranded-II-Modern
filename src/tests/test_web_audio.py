"""Exercise the formats supported by the game audio runtime with real FFmpeg."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, skipUnless
import shutil
import struct
import sys
import wave

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'devtools'))
import web_audio


@skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'requires ffmpeg and ffprobe')
class AudioConversion(TestCase):
    def test_surround_rear_channels_reach_stereo_output(self):
        with TemporaryDirectory() as directory:
            source = Path(directory) / 'surround.wav'
            output = Path(directory) / 'stereo.wav'
            samples = [(0, 0, 0, 0, 16000, 0), (0, 0, 0, 0, 0, 16000)]
            with wave.open(str(source), 'wb') as audio:
                audio.setparams((6, 2, 48000, 0, 'NONE', 'not compressed'))
                audio.writeframes(b''.join(struct.pack('<6h', *frame) for frame in samples))
            original = source.read_bytes()
            properties = web_audio.convert_wav(source, output, 'ffmpeg', 'ffprobe')
            self.assertEqual(source.read_bytes(), original)
            self.assertTrue(web_audio.supported_wav(output))
            self.assertEqual(properties, {'input_codec': 'pcm_s16le', 'sample_rate': 48000,
                                         'channels': 2, 'frames': 2})
            with wave.open(str(output), 'rb') as decoded:
                left1, right1, left2, right2 = struct.unpack('<4h', decoded.readframes(2))
            self.assertGreater(left1, 0)
            self.assertEqual((right1, left2), (0, 0))
            self.assertGreater(right2, 0)

    def test_mono_and_stereo_keep_their_sample_rate_and_channels(self):
        with TemporaryDirectory() as directory:
            for channels in (1, 2):
                with self.subTest(channels=channels):
                    source = Path(directory) / f'{channels}.wav'
                    with wave.open(str(source), 'wb') as audio:
                        audio.setparams((channels, 3, 22050, 0, 'NONE', 'not compressed'))
                        audio.writeframes(bytes(23 * channels * 3))
                    web_audio.convert_wav(source, source, 'ffmpeg', 'ffprobe')
                    with wave.open(str(source), 'rb') as decoded:
                        self.assertEqual(decoded.getparams()[:4], (channels, 2, 22050, 23))
