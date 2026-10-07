"""Write a disposable CI game config with separate available loopback test ports."""
from pathlib import Path
import re
import socket
import sys

source, target = map(Path, sys.argv[1:3])
if source.resolve() == target.resolve():
    raise SystemExit('Source and test configuration must differ')
text = source.read_text(encoding='utf8')
sockets = []
try:
    for _ in range(3):
        listener = socket.socket()
        listener.bind(('127.0.0.1', 0))
        sockets.append(listener)
    game, http, link = [listener.getsockname()[1] for listener in sockets]
    text, count = re.subn(r'^game-port:.*$', f'game-port: {game}', text, count=1, flags=re.M)
    if count != 1:
        raise SystemExit('Missing game-port in example config')
    text, count = re.subn(r'^  link-port:.*$', f'  link-port: {link}', text, count=1, flags=re.M)
    if count != 1:
        raise SystemExit('Missing Central link-port in example config')
    if re.search(r'^  http-port:', text, re.M):
        text = re.sub(r'^  http-port:.*$', f'  http-port: {http}', text, count=1, flags=re.M)
    else:
        text, count = re.subn(r'^central:\n', f'central:\n  http-port: {http}\n', text, count=1, flags=re.M)
        if count != 1:
            raise SystemExit('Missing Central section in example config')
    target.write_text(text, encoding='utf8')
    print(f'CI test ports: game={game}, Central HTTP={http}, world-link={link}')
finally:
    for listener in sockets:
        listener.close()
