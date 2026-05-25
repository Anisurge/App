# Anisurge API Integration Guide

This guide details only the core endpoints required to log in, manage watchlist items, and track watch progress on `https://db.anisurge.qzz.io`.

---

## 1. Login
Authenticates local credentials to acquire the `anisurgeToken` (JWT).

* **Method**: `POST`
* **Path**: `/v1/auth/login`
* **Content-Type**: `application/json`

#### Request Body:
```json
{
  "identifier": "your_username_or_email",
  "password": "your_password"
}
```

#### Response (200 OK):
```json
{
  "anisurgeToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0...",
  "anisurgeUserId": "4b4b7e81-6d20-49b4-acbe-e3554a9bdeea",
  "user": {
    "username": "your_username",
    "email": "user@example.com"
  }
}
```

#### Curl Command:
```bash
curl -s -X POST "https://db.anisurge.qzz.io/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"identifier":"your_username_or_email","password":"your_password"}'
```

---

## 2. Continue Watching (Episode Progress)

All endpoints below require the header: `Authorization: Bearer <anisurgeToken>`.

### Get Continue Watching List
Retrieves the user's continue-watching progress feed.

* **Method**: `GET`
* **Path**: `/v1/watch/continue`

#### Curl Command:
```bash
curl -s -X GET "https://db.anisurge.qzz.io/v1/watch/continue" \
  -H "Authorization: Bearer <anisurgeToken>"
```

### Save/Update Continue Watching Progress (Add/Update)
Pushes watch progress for an episode.

* **Method**: `POST`
* **Path**: `/v1/watch/progress`
* **Content-Type**: `application/json`

#### Request Body:
```json
{
  "animeId": "solo-leveling-1001",
  "episodeId": "ep-1",
  "currentTime": 350,   // seconds watched
  "duration": 1440,      // total episode length in seconds
  "language": "sub",     // "sub" or "dub"
  "server": "suzu",
  "forwardToReanime": true
}
```

#### Curl Command:
```bash
curl -s -X POST "https://db.anisurge.qzz.io/v1/watch/progress" \
  -H "Authorization: Bearer <anisurgeToken>" \
  -H "Content-Type: application/json" \
  -d '{"animeId":"solo-leveling-1001","episodeId":"ep-1","currentTime":350,"duration":1440,"language":"sub","server":"suzu","forwardToReanime":true}'
```

---

## 3. Watchlist

All endpoints below require the header: `Authorization: Bearer <anisurgeToken>`.

### Get Watchlist
Retrieves the user's watchlist items.

* **Method**: `GET`
* **Path**: `/v1/watchlist`
* **Query Parameters (Optional)**:
  - `folder`: Filter items by folder (`WATCHING`, `PLANNING`, `COMPLETED`, `PAUSED`, `DROPPED`)

#### Curl Command:
```bash
curl -s -X GET "https://db.anisurge.qzz.io/v1/watchlist?folder=WATCHING" \
  -H "Authorization: Bearer <anisurgeToken>"
```

### Add / Update Watchlist Item
Adds or updates an anime folder in the watchlist.

* **Method**: `POST`
* **Path**: `/v1/watchlist`
* **Content-Type**: `application/json`

#### Request Body:
```json
{
  "animeId": "solo-leveling-1001",
  "folder": "WATCHING", // WATCHING, PLANNING, COMPLETED, PAUSED, DROPPED
  "notes": "Optional notes here",
  "forwardToReanime": true
}
```

#### Curl Command:
```bash
curl -s -X POST "https://db.anisurge.qzz.io/v1/watchlist" \
  -H "Authorization: Bearer <anisurgeToken>" \
  -H "Content-Type: application/json" \
  -d '{"animeId":"solo-leveling-1001","folder":"WATCHING","notes":"Optional notes here","forwardToReanime":true}'
```
