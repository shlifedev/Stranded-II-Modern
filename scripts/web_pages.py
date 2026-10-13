"""Stage only verified WebPlay artifacts for GitHub Pages; never publishes or commits."""
import hashlib
import shutil

import web_build


def package():
    build, site = web_build.BUILD, web_build.SITE
    report = web_build.read(build / 'build-report.json')
    verification = web_build.read(build / 'verification.json')
    if (verification.get('web_check') != 'passed' or
            verification.get('artifact_sha256') != report['files']):
        raise ValueError('Run check --platform web successfully before packaging Pages.')
    if (report['source_sha256'] != web_build.source_hashes(web_build.ROOT, 'classic', 'web') or
            report['implementation_sha256'] != web_build.implementation_hashes()):
        raise ValueError('The web build is stale; build and check it again.')
    if set(report['files']) != set(web_build.SITE_FILES):
        raise ValueError('Unexpected files in the web build manifest.')
    for name, expected in report['files'].items():
        path = site / name
        if path.is_symlink() or not path.is_file() or web_build.digest(path) != expected:
            raise ValueError(f'Web artifact changed: {name}')

    version = web_build.validate_info(web_build.read(site / 'version.json'))
    payload = ''.join(report['files'][name] for name in web_build.SITE_FILES)
    key = (version['commit'] or 'local') + '-' + hashlib.sha256(payload.encode()).hexdigest()[:16]
    html = (site / 'index.html').read_text('utf8')
    old = '<script src="shell.js"></script>'
    if html.count(old) != 1:
        raise ValueError('Expected exactly one relative shell.js entrypoint.')
    html = html.replace('<script src="debugui.js"></script>', f'<script src="debugui.js?v={key}"></script>')
    html = html.replace(old, f'<script src="shell.js?v={key}"></script>')

    destination = build / 'pages'
    # This is an output directory, not the local preview (which may contain
    # isolated checks). Refuse unexpected content instead of recursively deleting it.
    if destination.is_symlink() or destination.resolve() != build.resolve() / 'pages':
        raise ValueError('Pages output must stay directly inside the web build directory.')
    destination.mkdir(exist_ok=True)
    for path in destination.iterdir():
        if (path.name not in web_build.SITE_FILES or not path.is_file() or
                path.is_symlink() or path.stat().st_nlink != 1):
            raise ValueError(f'Unexpected Pages output; preserve and move it before retrying: {path}')
    for name in web_build.SITE_FILES:
        if name == 'index.html':
            (destination / name).write_text(html, encoding='utf8', newline='\n')
        else:
            shutil.copyfile(site / name, destination / name)
    total = sum(path.stat().st_size for path in destination.iterdir())
    if total > 1024 ** 3:
        raise ValueError('Pages output exceeds the 1 GiB site limit.')
    web_build.write(build / 'pages-report.json', {
        'commit': version['commit'], 'cache_key': key, 'bytes': total,
        'files': {name: web_build.digest(destination / name) for name in web_build.SITE_FILES},
        'browser_gameplay': 'requires separate verification',
    })
    print(f'Prepared GitHub Pages site: {destination} ({total} bytes)')
    return destination


if __name__ == '__main__':
    try:
        package()
    except (ValueError, OSError, KeyError) as error:
        raise SystemExit(f'Error: {error}')
