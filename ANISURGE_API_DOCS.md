# Anisurge Full API Documentation

> For Flutter/friends building an Anisurge-compatible app.
> Covers: **BFF** (`db.anisurge.qzz.io`), **Project-R / ReAnime** (`api.reanime.to`), **Streaming Proxy** (`fec.anisurge.lol`), **anisurge.lol site**, and **external services**.

---

## Table of Contents

1. [Base URLs](#1-base-urls)
2. [Authentication & Dual-Token System](#2-authentication--dual-token-system)
3. [BFF API — Anisurge Backend](#3-bff-api--anisurge-backend)
4. [Project-R / ReAnime API](#4-project-r--reanime-api)
5. [Streaming Proxy](#5-streaming-proxy)
6. [anisurge.lol Site API](#6-anisurgelol-site-api)
7. [External Services](#7-external-services)
8. [Data Models](#8-data-models)

---

## 1. Base URLs

| Service | Base URL | Description |
|---------|----------|-------------|
| **BFF API** | `https://db.anisurge.qzz.io` | Auth, profile, watchlist, comments, chat, shop, games, rewards, announcements |
| **Project-R / ReAnime** | `https://api.reanime.to/api/v1` | Anime catalog, search, home feed, schedule, community forum |
| **Streaming Proxy** | `https://fec.anisurge.lol/api` | Batch scrape stream URLs |
| **anisurge.lol Site** | `https://www.anisurge.lol` | App updates, downloads, OAuth proxies |
| **AniSkip** | `https://api.aniskip.com` | Intro/outro skip timestamps |
| **AniList GraphQL** | `https://graphql.anilist.co` | Metadata queries |
| **MAL API** | `https://api.myanimelist.net/v2` | MyAnimeList sync |
| **AniZip** | `https://api.ani.zip/mappings` | Episode thumbnails |

---

## 2. Authentication & Dual-Token System

| Token | Prefix | Usage |
|-------|--------|-------|
| `projectRToken` | `project_r_...` | Project-R API calls (catalog, home, search, community) |
| `anisurgeToken` | JWT (`eyJ...`) | BFF API calls (profile, watchlist, comments, chat, shop) |

Both returned from `POST /v1/auth/login`, `POST /v1/auth/signup`, `POST /v1/auth/sync`.

**BFF headers:** `Authorization: Bearer <anisurgeToken>`  
**Project-R headers:** `Authorization: Bearer project_r_...`

Stored locally as `SessionInfo(token, anisurgeToken)` in DataStore.

