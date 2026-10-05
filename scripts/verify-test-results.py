"""Reject missing test reports or silently skipped mandatory tests in CI."""
from pathlib import Path
import xml.etree.ElementTree as ET

reports = list(Path('build/test-results/test').glob('TEST-*.xml'))
if not reports:
    raise SystemExit('No JUnit test reports found')
tests = 0
for report in reports:
    suite = ET.parse(report).getroot()
    tests += int(suite.get('tests', 0))
    if any(int(suite.get(name, 0)) for name in ('skipped', 'failures', 'errors')):
        raise SystemExit(f'{report}: mandatory tests failed or were skipped')
if not tests:
    raise SystemExit('No tests executed')
print(f'{tests} tests executed; none skipped')
