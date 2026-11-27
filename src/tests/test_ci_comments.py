from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'devtools'))
import check_comments


class CommentBudget(unittest.TestCase):
    def test_counts_physical_lines_including_inline_and_blank_block_lines(self):
        source = b'auto url = R"tag(// data /*)tag"; // note\nint x /* a */ = 1; /* b\n\nc */\n'
        self.assertEqual(check_comments.comment_lines(Path('sample.cpp'), source), [1, 2, 3, 4])
        self.assertEqual(check_comments.comment_lines(Path('sample.h'), b"#define N 1'000 // note\n"), [1])

    def test_preserves_literals_directives_and_markup_examples(self):
        samples = (
            ('test.bb', b'Print "a;b"\nPrint 1 ; note\n', [2]),
            ('test.py', b'#!/usr/bin/env python3\ns = "# literal"\nvalue = 1 # note\n', [3]),
            ('test.tsx', b'const el = <a href="https://x">{/* note */}text</a>;\n', [1]),
            ('test.md', b'<!-- dev-docs-ai-translation: {} -->\n\n```html\n<!-- example -->\n```\n<!-- note -->\n', [6]),
            ('test.html', b'<script>var url = "https://x"; // note\n</script>\n', [1]),
            ('en.tsv', b'key\t#fff\n# note\n', [2]),
        )
        for name, data, expected in samples:
            with self.subTest(name=name):
                self.assertEqual(check_comments.comment_lines(Path(name), data), expected)

    def test_workflow_shell_comments_are_counted_inside_run_blocks(self):
        workflow = (
            b'jobs:\n  build:\n    runs-on: windows-2022\n    steps:\n'
            b'      - run: |\n          <# start\n\n          end #>\n'
            b'          Write-Host "# literal" # note\n'
            b'      - shell: bash\n        run: |\n'
            b'          cat <<"EOF"\n          # heredoc data\n          EOF\n'
            b'          echo done # note\n'
        )
        self.assertEqual(check_comments.comment_lines(Path('.github/workflows/test.yml'), workflow), [6, 7, 8, 9, 15])

    def test_repository_limit_includes_untracked_source_and_skips_ignored_output(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            subprocess.run(['git', 'init', '--quiet', folder], check=True)
            (root / '.gitignore').write_text('/output.py\n')
            (root / 'output.py').write_text('# ignored\n' * 50)
            (root / 'tracked.bb').write_text('; note\n' * 29)
            subprocess.run(['git', 'add', 'tracked.bb'], cwd=root, check=True)
            extra = root / 'new.py'
            with patch.object(check_comments, 'ROOT', root), redirect_stdout(StringIO()):
                extra.write_text('n = 3 # note\n')
                self.assertEqual(check_comments.main(), 0)
                extra.write_text('n = 3 # note\n# another\n')
                self.assertEqual(check_comments.main(), 1)
