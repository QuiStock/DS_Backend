"""Functional smoke against direct or gateway-rewritten deployment bases."""
import http.cookiejar
import json
import os
import urllib.request
from urllib.parse import urlsplit


def base(name):
    value = os.environ[name].rstrip('/')
    parsed = urlsplit(value)
    if parsed.scheme not in ('http', 'https') or not parsed.netloc:
        raise ValueError(name + ' must be a complete HTTP(S) URL')
    return value


auth = base('SMOKE_AUTH_BASE_URL')
backend = base('SMOKE_BACKEND_BASE_URL')
jar = http.cookiejar.CookieJar()
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def request(url, method='GET', payload=None, token=None):
    headers = {}
    if payload is not None:
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = None if payload is None else json.dumps(payload).encode()
    with client.open(urllib.request.Request(url, data=data, headers=headers, method=method), timeout=30) as response:
        return response.status, response.read()


def access_token():
    return next(cookie.value for cookie in jar if cookie.name == 'access_token' and cookie.value)


status, body = request(auth + '/.well-known/jwks.json')
assert status == 200 and json.loads(body)['keys'], 'JWKS unavailable'
status, _ = request(auth + '/auth/login', 'POST', {
    'email': os.environ['SMOKE_EMAIL'],
    'password': os.environ['SMOKE_PASSWORD'],
    'platform': os.environ.get('SMOKE_PLATFORM', 'mobile'),
})
assert status == 200, 'Login failed'
assert request(backend + '/branches', token=access_token())[0] == 200
assert request(auth + '/auth/refresh', 'POST')[0] == 200
assert request(backend + '/branches', token=access_token())[0] == 200
assert request(auth + '/auth/logout', 'POST')[0] == 204
assert not any(cookie.value for cookie in jar if cookie.name in ('access_token', 'refresh_token'))
print('JWKS, login, JWT, refresh and logout passed')
