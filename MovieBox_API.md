# MovieBox API Integration

This document describes the MovieBox backend integration used by mpvRx. It covers authentication, signing, metadata, paging, captions, direct MP4 resolution, playback, downloads, and observed CDN behavior.

The integration does not scrape `moviebox.net`. That domain is not part of the working request flow.

## Architecture

mpvRx combines three backend surfaces because no single surface currently provides reliable discovery, metadata, captions, and playable media.

| Surface | Base URL | Authentication | Purpose |
|---|---|---|---|
| H5 V2 | `https://h5-api.aoneroom.com` | JWT bearer token | Search and detail metadata |
| H5 V1 | `https://h5.aoneroom.com` | `account` cookie | Trending and captions |
| Mobile | Regional `api*.aoneroom.com` hosts | HMAC signature and bearer token | Direct playable MP4 resources |

Current flow:

```text
Search/trending
  -> H5 V2 search or H5 V1 trending
  -> subjectId + detailPath + subjectType

Details
  -> H5 V2 detail
  -> seasons, episodes, dubs, artwork, metadata

Episode resolution
  -> H5 V1 download metadata for captions
  -> Mobile auth bootstrap
  -> Mobile resource pages for each dub subjectId
  -> exact se/ep filtering
  -> signed bcdn.hakunaymatata.com MP4 URLs

Playback
  -> localhost range proxy backed by OkHttp
  -> mpv receives a seekable local MP4 stream
```

## Response Envelope

Successful responses generally use:

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

Validate both the HTTP status and `code`. HTTP `200` does not guarantee API success.

## Core Identifiers

| Field | Description |
|---|---|
| `subjectId` | Numeric content ID used by H5 and mobile endpoints |
| `detailPath` | H5 web slug used by detail requests and referers |
| `subjectType` | `1` for movie and `2` for TV in the H5 data used by mpvRx |
| `resourceId` | ID of a particular mobile resource encode |
| `se` | Season number; movies use `0` |
| `ep` | Episode number; movies use `0` |

mpvRx encodes provider references as:

```text
subjectId|detailPath|subjectType
```

Legacy IDs are resolved by searching pages for the matching `subjectId`, with trending as a fallback.

## H5 V2 Authentication

V2 search and detail requests require a JWT.

### Bootstrap

```http
GET /wefeed-h5api-bff/home?host=h5.aoneroom.com HTTP/1.1
Host: h5-api.aoneroom.com
Referer: https://h5.aoneroom.com/
User-Agent: <browser user agent>
Accept: application/json
```

The token can be returned in either location:

1. `x-user` JSON header: `{"token":"<jwt>", ...}`
2. `Set-Cookie: token=<url-encoded-jwt>`

URL-decode cookie tokens. A valid JWT has three dot-separated components.

Authenticated headers:

```http
Authorization: Bearer <jwt>
Cookie: token=<jwt>
Origin: https://h5.aoneroom.com
Referer: https://h5.aoneroom.com/
```

On HTTP `401` or `403`, invalidate only the token used by the failed request, bootstrap once, and retry once. This avoids one concurrent request deleting a newer token.

## H5 V1 Authentication

V1 calls use an `account` cookie independent of the V2 JWT.

```http
GET /wefeed-h5-bff/app/get-latest-app-pkgs?app_name=moviebox HTTP/1.1
Host: h5.aoneroom.com
Referer: https://h5.aoneroom.com/
User-Agent: <browser user agent>
Accept: application/json
```

Read `account` from `Set-Cookie`, then send:

```http
Cookie: account=<account-value>
```

V1 and V2 credentials are not interchangeable.

## Mobile Authentication

The mobile resource endpoint supplies the working direct MP4 URLs. Every request is signed; protected endpoints also require a bearer token.

### Host Pool

mpvRx tries the last successful host first, then:

```text
https://api6.aoneroom.com
https://api5.aoneroom.com
https://api4.aoneroom.com
https://api4sg.aoneroom.com
https://api3.aoneroom.com
https://api.inmoviebox.com
```

HTTP `403`, `407`, `500`, `502`, `503`, and `504` trigger host failover. HTTP/API `401` and `441` trigger one authentication refresh.

### Client Identity

`X-Client-Info` is compact JSON containing package/version, Android version, install channel, device ID, GAID, model, language, network, region, timezone, and carrier code. mpvRx creates one UUID per client lifetime and uses it for `device_id` and `gaid`.

### Canonical Signature

Build this newline-delimited string:

```text
UPPERCASE_METHOD
Accept
Content-Type
Body-Length
Timestamp-Milliseconds
Body-MD5
Canonical-Path-And-Query
```

Rules:

- For GET with no body, body length and body MD5 are empty strings, not `0` or the empty-body MD5.
- URL-decode query keys and values, sort by key then value, and join as `key=value&key=value`.
- Include only path and sorted query, not scheme or host.
- For non-null bodies, hash at most the first `102400` bytes with MD5 and lowercase hexadecimal.
- Base64-decode the embedded signing key, then calculate HMAC-MD5 over the UTF-8 canonical string.
- Base64-encode the HMAC result.

Headers:

```text
x-tr-signature: <timestampMs>|2|<base64-hmac-md5>
X-Client-Token: <timestampMs>,<md5(reverse(timestampMs))>
```

Also send:

```http
Accept: application/json
Content-Type: application/json
Connection: keep-alive
X-Client-Info: <compact-json>
X-Client-Status: 0
User-Agent: <mobile application user agent>
```

Protected resource calls add:

```http
Authorization: Bearer <mobile-jwt>
X-Play-Mode: 2
```

### Token Bootstrap

```http
GET /wefeed-mobile-bff/tab-operating?page=1&tabId=0&version= HTTP/1.1
Host: api6.aoneroom.com
<signed headers without Authorization and X-Play-Mode>
```

Extract the token from `x-user`:

```json
{
  "token": "<jwt>",
  "userId": "...",
  "userType": 0
}
```

Calling a protected endpoint without bootstrap returns `441`, commonly with `miss token`.

## Endpoints

### Search

```http
POST https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/search
Authorization: Bearer <v2-jwt>
Content-Type: application/json

{
  "keyword": "Avatar",
  "page": 1,
  "perPage": 24,
  "subjectType": 0
}
```

Important response fields:

```json
{
  "items": [
    {
      "title": "Avatar",
      "subjectId": "8906247916759695608",
      "detailPath": "avatar-WLDIi21IUBa",
      "subjectType": 1,
      "hasResource": true,
      "releaseDate": "2009-12-18",
      "countryName": "United States",
      "genre": "Action,Adventure,Fantasy",
      "imdbRatingValue": "7.9",
      "cover": { "url": "https://pbcdnw.aoneroom.com/..." }
    }
  ],
  "pager": {
    "page": 1,
    "perPage": 24,
    "totalCount": 16,
    "hasMore": false,
    "nextPage": 2
  }
}
```

Use `hasMore` and `nextPage`; do not infer pagination only from result count. Series search can return one result per season while sharing `subjectId` and `detailPath`.

### Trending

```http
GET https://h5.aoneroom.com/wefeed-h5-bff/web/subject/trending?page=0&perPage=20
Cookie: account=<v1-account>
Referer: https://h5.aoneroom.com/
```

This endpoint is zero-based while the app UI is one-based. Normalize the next-page cursor for UI state.

### Detail

```http
GET https://h5-api.aoneroom.com/wefeed-h5api-bff/detail?detailPath=avatar-WLDIi21IUBa
Authorization: Bearer <v2-jwt>
```

The `subject` object contains metadata, artwork, `resource.seasons`, and `dubs`. Each dub can have its own `subjectId`; resolve resources independently for each dub.

Generate TV episodes from each season's `se` and `maxEp`. Movies expose one synthetic `Movie` episode and always use `se=0`, `ep=0`.

### H5 Download Metadata and Captions

```http
GET https://h5.aoneroom.com/wefeed-h5-bff/web/subject/download
    ?subjectId=8906247916759695608
    &se=0
    &ep=0
Cookie: account=<v1-account>
Referer: https://h5.aoneroom.com/movies/avatar-WLDIi21IUBa
```

Response data contains `downloads` and `captions`. mpvRx uses captions but intentionally ignores the returned `bcdnxw.hakunaymatata.com` videos because that CDN returned `403 ACCESS DENIED` during verification.

Caption entry:

```json
{
  "url": "https://.../subtitle.vtt",
  "lan": "en",
  "lanName": "English"
}
```

All captions are exposed to playback. The preferred English caption is downloaded beside the video when available.

### Mobile Resource Pages

```http
GET https://api6.aoneroom.com/wefeed-mobile-bff/subject-api/resource
    ?subjectId=8906247916759695608
    &resolution=0
    &page=1
    &perPage=20
Authorization: Bearer <mobile-jwt>
X-Play-Mode: 2
<mobile signature headers>
```

Response data:

```json
{
  "pager": {
    "hasMore": false,
    "nextPage": "2",
    "page": "1",
    "perPage": 20,
    "totalCount": 4
  },
  "list": [
    {
      "episode": 0,
      "title": "Avatar-360P",
      "resourceLink": "https://bcdn.hakunaymatata.com/resource/h265/...mp4?sign=...&t=...",
      "linkType": 2,
      "size": "356567015",
      "resourceId": "4358647173001663160",
      "se": 0,
      "ep": 0,
      "resolution": 360,
      "codecName": "hevc",
      "duration": 10689
    }
  ]
}
```

Resolution algorithm:

1. Request `resolution=0`, `perPage=20`.
2. Follow `pager.hasMore` and numeric/string `pager.nextPage`.
3. Stop on `hasMore=false`, a non-increasing cursor, or the safety cap.
4. Filter by exact `se` and `ep`.
5. Build one server for each matching `resourceLink`.
6. Sort by numeric resolution descending.
7. Repeat for each dub `subjectId`.

The live API identifies variants with fields such as `lanName: "Original Audio"`, `lanName: "Hindi dub"`, `lanCode: "hi"`, and `original: true`. mpvRx normalizes these to clean labels using `original` and `lanCode`: Original, English, Hindi, then other language names with trailing `dub`/`sub` removed. Within each language, higher resolutions appear first.

This flow was verified for a movie (`0/0`) and Breaking Bad S1E1 (`1/1`, found after four pages).

### Mobile Play Info (Diagnostic Only)

```http
GET /wefeed-mobile-bff/subject-api/play-info?subjectId=<id>&se=<n>&ep=<n>
```

This endpoint currently returns a CloudFront-cookie-authenticated DASH MPD on `sacdn.hakunaymatata.com`. The manifest works, but mpvRx's bundled FFmpeg does not include the MPEG-DASH demuxer. Therefore this endpoint is not used for playback.

## CDN Behavior

| CDN | Observed behavior | Usage |
|---|---|---|
| `pbcdnw.aoneroom.com` | Artwork works | Posters/backdrops |
| `bcdnxw.hakunaymatata.com` | H5 video links returned `403 ACCESS DENIED` | Not used |
| `sacdn.hakunaymatata.com` | DASH MPD returned `200` | Not used; player lacks DASH demuxer |
| `bcdn.hakunaymatata.com` | Direct resource MP4 returned `206 Partial Content` | Current video source |

Signed URLs expire. Cache resource pages briefly and resolve again before downloads or after transfer failures.

## Android Playback

The direct MP4 works through OkHttp. Playback registers the signed URL and headers with a localhost `NanoHTTPD` range proxy:

```text
mpv -> http://127.0.0.1:<port>/<id>.mp4
    -> OkHttp GET bcdn.hakunaymatata.com with forwarded Range
    -> 200/206 video/mp4 back to mpv
```

The proxy forwards byte ranges and returns `Accept-Ranges`, `Content-Range`, `Content-Length`, and `Content-Type`. mpv receives a normal seekable local MP4 while CDN transport remains in the same OkHttp stack proven by downloads.

The proxy is playback-only. Downloads continue using the direct signed URL.

## Download Layout

The selected anime directory is the root. All episodes of the same normalized series title share one sanitized title directory. There are no per-episode directories.

```text
<selected anime root>/
  Breaking Bad/
    Breaking Bad - EpS1E1.mp4
    Breaking Bad - EpS1E1.srt
    Breaking Bad - EpS1E2.mp4
    Breaking Bad - EpS1E2.srt
```

Movies omit the episode marker. Invalid filename characters and control characters become `_`; surrounding whitespace and trailing dots are removed; blank names become `Untitled`; canonical names are capped at 120 UTF-8 bytes so multibyte titles remain safe for Android document providers and common external filesystems.

The preferred subtitle uses the exact video basename and differs only by extension. It is stored in the same title directory so mpv and external players can auto-detect it.

Trailing season/bundle decorations such as `S1`, `S1-S6`, `Season 1`, and accidental episode suffixes such as `S1E4` are removed from the canonical folder name. Folder creation is synchronized and existing directories are matched case-insensitively, preventing concurrent episode downloads from creating `Title`, `Title (1)`, and `Title (2)`.

## Caching

| Data | TTL |
|---|---:|
| Search | 60 seconds |
| Trending | 2 minutes |
| Detail | 10 minutes |
| H5 captions/download metadata | 60 seconds |
| Mobile resource page | 60 seconds |

Caches are bounded to 128 entries. Expired entries are removed first; if still full, one existing key is evicted. Endpoint types use separate mutexes so unrelated calls are not serialized.

## Error Handling

- Rethrow coroutine cancellation.
- Refresh H5 credentials once on authentication errors.
- Refresh mobile bearer auth on HTTP/API `401`, `441`, or token/auth messages.
- Fail over mobile hosts for signature/proxy/server statuses.
- Preserve `Retry-After` for HTTP `429`.
- Keep successful dubs if another dub fails; surface the underlying error if all fail.
- Reject non-increasing page cursors.

## Verified Checks

- Mobile bootstrap returned HTTP `200` and an `x-user` token.
- Mobile resource API returned code `0`.
- Avatar 360p returned HTTP `206`, `video/mp4`, and `x-oss-cdn-auth: success`.
- Full Avatar 360p download completed at exactly `356567015` bytes.
- The downloaded file starts with a valid `ftypisom` MP4 header.
- Breaking Bad S1E1 was found after four resource pages; its 1080p URL returned HTTP `206` and one MiB of media bytes.
- Bundled `libavformat` contains the MP4 demuxer.
- Bundled `libavcodec` contains native and Android MediaCodec HEVC decoders.

## Source Map

| File | Responsibility |
|---|---|
| `MovieBoxSigning.kt` | Mobile canonicalization, HMAC signature, client token, `x-user` parsing |
| `MovieBoxClient.kt` | H5 auth, mobile bootstrap, host failover, requests, caching |
| `MovieBoxAnimeProvider.kt` | Search/detail mapping, episodes, dubs, resources, captions |
| `HttpStreamingProxy.kt` | Local seekable playback proxy backed by OkHttp |
| `AnimeViewModel.kt` | UI state, page cursors, stream-link conversion |
| `AnimeDownloadRepository.kt` | Quality choice, resumable transfer, naming, subtitle sidecars |

## Operational Rules

- Do not use `moviebox.net` for API discovery.
- Do not restore H5 `bcdnxw` links without re-verifying them from the target network.
- Do not send direct MP4 links through yt-dlp.
- Do not call mobile resources before bootstrapping the mobile bearer token.
- Do not pass `1/1` for movies; use `0/0`.
- Do not persist signed media URLs permanently.
