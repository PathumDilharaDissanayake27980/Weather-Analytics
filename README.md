# Weather Analytics — Comfort Index

A secure weather analytics application. It reads city codes from `cities.json`, fetches current
conditions from OpenWeatherMap, computes a custom **Comfort Index** (0–100) on the server,
ranks the cities from most to least comfortable, caches aggressively, and serves the result to
an authenticated React dashboard.

| | |
|---|---|
| **Backend** | Java 17, Spring Boot 3.5, Maven |
| **Frontend** | React 19, TypeScript, Vite |
| **Auth** | Auth0 (Authorization Code + PKCE, RS256 JWT validation, email MFA) |
| **Tests** | JUnit 5 + Mockito + MockMvc (170) · Vitest + Testing Library (35) |

---

## Table of contents

1. [Architecture](#architecture)
2. [Setup](#setup)
3. [The Comfort Index](#the-comfort-index)
4. [Reasoning behind the weights](#reasoning-behind-the-weights)
5. [Trade-offs considered](#trade-offs-considered)
6. [Cache design](#cache-design)
7. [Authentication and authorization](#authentication-and-authorization)
8. [API reference](#api-reference)
9. [Testing](#testing)
10. [Known limitations](#known-limitations)

---

## Architecture

```
Browser (React SPA)                  Server (Spring Boot)            External
───────────────────                  ────────────────────            ────────

[Auth0Provider] ──────────── login (Authorization Code + PKCE) ────▶ Auth0
      │                                                                │
      │◀───────────────── access token (RS256 JWT) ────────────────────┘
      │
      │  GET /api/weather
      │  Authorization: Bearer <token>
      └────────────────────▶ [SecurityFilterChain]
                              verifies signature, iss, exp, aud ──▶ Auth0 JWKS
                                     │                              (public keys)
                                     ▼
                              [WeatherController]
                                     │
                                     ▼
                              [WeatherService]
                                     │
                              processed cache ──── HIT ──▶ return (X-Cache: HIT)
                                     │ MISS
                                     ▼
                              [CityRepository] → 10 city codes
                                     │
                                     ▼
                              raw cache (per city) ── HIT ──▶ reuse
                                     │ MISS
                                     ▼
                              [OpenWeatherMapClient] ──────────▶ OpenWeatherMap
                                     │   (the API key lives           API
                                     │    only here)
                                     ▼
                              [ComfortIndexCalculator]  ← the pure function
                                     │
                                     ▼
                              rank, store, return
```

**Three properties this layout guarantees**, each of which is an explicit requirement:

1. **The OpenWeatherMap key never reaches the browser.** Only `OpenWeatherMapClient` holds it.
   This is the reason a backend exists at all rather than the SPA calling OpenWeatherMap.
2. **The Comfort Index is computed server-side.** The frontend receives finished scores, ranks
   and labels. It never recomputes any of them.
3. **The API is the security boundary.** The React login screen is a convenience; the token
   check on every `/api/**` request is the actual control.

### Package layout

```
server/src/main/java/com/fidenz/weather/
├── api/           controllers, DTOs, exception handling — HTTP concerns only
├── cache/         TtlCache, CacheLookup, CacheSnapshot — generic, knows nothing of weather
├── city/          City, CityRepository — loads cities.json
├── comfort/       ComfortIndexCalculator + properties — the scoring, free of Spring and HTTP
├── config/        security, CORS, clock, HTTP client settings
└── weather/       OpenWeatherMapClient, WeatherService, WeatherCaches, OWM model
```

Read that list for what each package *doesn't* know. `comfort` has no dependency on HTTP,
caching or OpenWeatherMap — it takes numbers and returns a number. `cache` doesn't know it
holds weather. That isolation is what makes the Comfort Index trivially unit-testable, and
what makes adding a parameter a small, safe change.

---

## Setup

### Prerequisites

- JDK 17+
- Node 20+
- An OpenWeatherMap API key — free at <https://openweathermap.org/api>
  (a new key can take up to two hours to activate; until then the API returns 401)
- An Auth0 tenant (free tier) — only needed for the secured configuration

### 1. Backend

```bash
cd server
```

Provide the API key as an environment variable. It is never committed.

```bash
# macOS / Linux
export OPENWEATHER_API_KEY=your_key_here

# Windows PowerShell
$env:OPENWEATHER_API_KEY = "your_key_here"
```

**Run without Auth0** (useful before a tenant exists, and for a quick look):

```bash
# macOS / Linux
APP_SECURITY_MODE=permissive ./mvnw spring-boot:run

# Windows PowerShell
$env:APP_SECURITY_MODE = "permissive"; .\mvnw.cmd spring-boot:run
```

**Run with Auth0** (the real configuration):

```bash
export AUTH0_DOMAIN=your-tenant.eu.auth0.com
export AUTH0_AUDIENCE=https://weather-analytics-api
./mvnw spring-boot:run
```

The API listens on **http://localhost:8081**. Verify:

```bash
curl http://localhost:8081/api/health
# {"status":"UP","cityCount":10,"serverTime":"..."}
```

### 2. Frontend

```bash
cd client
npm install
npm run dev
```

Open <http://localhost:5173>.

`client/.env` controls where the frontend points and whether it uses Auth0:

```env
VITE_API_URL=http://localhost:8081

# Omit these two and the app runs unauthenticated against a permissive-mode server,
# showing a prominent banner. Set them to enable Auth0.
VITE_AUTH0_DOMAIN=your-tenant.eu.auth0.com
VITE_AUTH0_CLIENT_ID=your_spa_client_id
VITE_AUTH0_AUDIENCE=https://weather-analytics-api
```

### 3. Auth0 configuration

**Create the API** — Applications → APIs → Create API
- Name: `Weather Analytics API`
- Identifier: `https://weather-analytics-api` (this becomes the `audience`)
- Signing algorithm: **RS256**

**Create the application** — Applications → Create Application → *Single Page Application*
- Allowed Callback URLs: `http://localhost:5173`
- Allowed Logout URLs: `http://localhost:5173`
- Allowed Web Origins: `http://localhost:5173`

**Enable email MFA** — Security → Multi-factor Auth → enable **Email**, then set the policy to
*Always* so every login requires a one-time code.

**Disable public sign-up** — Authentication → Database → `Username-Password-Authentication` →
Settings → enable **Disable Sign Ups**. New users can then only be created by an administrator,
which is the whitelist.

**Create the test user** — User Management → Users → Create User
- Email: `careers@fidenz.com`
- Password: `Pass#fidenz`
- Connection: `Username-Password-Authentication`

### 4. Cities

`server/src/main/resources/cities.json` holds **10 cities** — the 8 supplied with the
assignment plus Dubai and Reykjavik, added both to meet the ten-city minimum and to widen the
temperature range so the ranking has something meaningful to separate.

---

## The Comfort Index

### What kind of thing this is

The Comfort Index is a **composite indicator** — the same family as the Human Development
Index or an air-quality index. Its defining property is that **the thing it measures has no
direct instrument.** There is no comfort-meter, so there is no ground truth and no error rate
to report.

That does not make it arbitrary. Each parameter's curve is anchored to published findings
rather than to taste, and the whole thing is checked by properties that must hold regardless
of how the weights are tuned.

### Who it is for

> This index estimates outdoor comfort for **a healthy adult, lightly clothed, at rest or
> walking, over a short period.**

This sentence comes first because "comfort" is undefined without it. A hiker, an office worker
and someone with asthma want different weather. Every weight below is justified against that
subject.

### The formula

```
base     = Σ (weightᵢ × subScoreᵢ) / Σ weightᵢ        each subScore ∈ [0, 1]
modifier = Π multiplierⱼ                              each multiplier ∈ [0, 1]
score    = clamp(100 × base × modifier, 0, 100)
```

It is a **hybrid**: a weighted mean for the normal range, multiplied by penalties for
conditions that ruin comfort regardless of anything else.

#### Sub-scores — the additive half

Each parameter scores 1.0 inside a *plateau* of ideal values and falls linearly towards a floor
on each side. The two sides are configured independently, because comfort is not symmetric.

| Parameter | Weight | Ideal band | Zero / floor (low) | Zero / floor (high) | Anchor |
|---|---|---|---|---|---|
| Temperature | 0.35 | 20–26 °C | 0.0 at −5 °C | 0.0 at 40 °C | ASHRAE 55 comfort zone |
| Humidity | 0.25 | 40–60 % RH | 0.33 at 0 % | 0.0 at 100 % | ASHRAE / EPA guidance |
| Wind | 0.20 | 1.5–3.5 m/s | **0.80** at 0 m/s | 0.0 at 15 m/s | Beaufort 2, "light breeze" |
| Cloudiness | 0.20 | 10–40 % | 0.85 at 0 % | **0.35** at 100 % | judgement — see below |

A *plateau* rather than a single ideal point is deliberate: comfort is not a knife-edge, and it
means small measurement wobble does not reshuffle the rankings.

#### Multipliers — the non-compensatory half

| Multiplier | Trigger | Slope | Floor |
|---|---|---|---|
| `rain` | any rainfall | 0.06 per mm/h | 0.45 |
| `snow` | any snowfall | 0.10 per mm/h | 0.40 |
| `feelsGap` | \|feels_like − temp\| | 0.03 per °C | 0.80 |
| `heat` | above 35 °C | 0.07 per °C | 0.30 |
| `cold` | below 0 °C | 0.04 per °C | 0.40 |
| `gale` | above 12 m/s | 0.08 per m/s | 0.50 |

Every number above lives in `application.yml` under `comfort.*`. Nothing is hard-coded in the
calculator, so the index can be retuned or extended without touching Java.

### Worked example — why hybrid rather than a plain weighted sum

A city at 44 °C with ideal humidity, a light breeze and pleasant cloud cover:

```
sub-scores:  temperature 0.00   humidity 1.00   wind 1.00   cloudiness 1.00
base      =  0.35×0 + 0.25×1 + 0.20×1 + 0.20×1  =  0.65

A pure weighted sum would stop here and report 65 — "comfortable".

multipliers: heat ×0.37   feelsGap ×0.97   (others 1.0)
modifier  =  0.36
score     =  100 × 0.65 × 0.36  ≈  23
```

The additive half alone rates 44 °C as pleasant because three healthy parameters outvote one
catastrophic one. That is the **compensation problem**, and it is the single strongest argument
against the weighted sum that most implementations reach for. The multiplicative half fixes it.
This case is asserted directly in
`ComfortIndexCalculatorTest.extremeHeatIsNotCompensatedByOtherParameters`.

### Live output

Real data, showing the index behaving sensibly across a wide range:

```
rk city         temp  feels  hum  wind cloud  score  label
 1 Paris        28.15  28.81   52  6.17    23   88.2 Excellent
 2 Sydney        14.1  13.34   68  1.62    49   82.9 Excellent
 3 Boston       23.01  23.53   83  0.89     2   80.3 Excellent
 4 Shanghai     26.61  26.61   94     6    39   72.9 Comfortable
 5 Oslo         13.64  13.07   77  0.84   100   64.6 Moderate
 6 Liverpool       18  17.68   70 11.32    99   63.9 Moderate
 7 Reykjavik    11.81   10.9   71  6.17    98   62.7 Moderate
 8 Colombo      28.91  34.71   81  4.54    53   61.9 Moderate
 9 Tokyo        21.81  22.37   89  4.12   100   54.0 Moderate  (moderate rain)
10 Dubai        36.96  43.96   56  5.14     9   47.9 Uncomfortable
```

Note Colombo at 28.9 °C scoring *below* Oslo at 13.6 °C — humidity and a 5.8 °C feels-like gap
outweigh the more agreeable thermometer reading. That is the index doing its job; a
temperature-only ranking would get this backwards.

---

## Reasoning behind the weights

### The parameters were chosen from data, not intuition

Before designing anything, I sampled **130 cities** across every climate zone and measured
which fields are actually present and which actually vary. Three findings changed the design:

**`wind.gust` was excluded.** Present for only 42/130 cities (32%). Critically, cities with and
without it had near-identical mean wind speed (**4.32 vs 4.02 m/s**) — so absence is a
station-reporting artifact, *not* calm conditions. It cannot honestly be imputed to 0, and
imputing it would systematically reward two thirds of cities with a fictional reading.

**`visibility` was excluded.** 126/130 cities (**97%**) sat exactly at OpenWeatherMap's 10 000 m
cap. A parameter on which 97% of the dataset is identical cannot discriminate between cities no
matter what weight it is given.

**Absent `rain` is safely read as 0.** The `rain` field appeared for exactly 20/130 cities, and
exactly 20 cities reported the condition `"Rain"` — the same 20. The field is present if and
only if it is raining, so absence genuinely means no rain.

### Why these weights

**Temperature — 0.35, the largest.** Thermal stress is the dominant driver of outdoor comfort
and the one people report first. It also had the widest genuinely comfort-relevant spread in
the sample (5.3 °C to 44.0 °C).

The asymmetry matters more than the weight: zero at −5 °C but also at 40 °C, so heat is
punished roughly **2.2× faster per degree** than cold. The justification is physiological —
clothing mitigates cold almost indefinitely, whereas above about 35 °C the body's only
remaining cooling channel is sweat evaporation, which itself fails as humidity rises. Cold is
an inconvenience you can dress for; heat is not.

**Humidity — 0.25.** Second-largest because it *governs* the temperature term rather than
merely adding to it: at high humidity, evaporative cooling stops working and the same
temperature becomes far more punishing.

Asymmetric again, and in the same spirit. Dry air is irritating — chapped lips, static — but
you keep cooling efficiently, so 0% RH still scores 0.33. Humid air is physiologically
disabling, so 100% RH scores 0.

**Wind — 0.20.** The interesting parameter, because **zero is not the worst case.** Dead calm
scores 0.80, a light breeze scores 1.00, and a gale scores 0. Still air feels stuffy and blocks
convective cooling; a breeze at 1.5–3.5 m/s is actively pleasant. Every other parameter in the
index is monotonic away from an extreme — wind is the one with a genuine interior optimum, and
modelling it as "less is better" would be wrong.

**Cloudiness — 0.20.** The widest spread in the sample (sd 41.1 on a 0–100 scale), so it does
the most work separating cities. Ideal is 10–40% — scattered cloud, not clear skies — because
full sun means direct radiant load and UV while total overcast is gloomy.

**This is the most contestable choice in the index and I want to be explicit about that.**
"Partly cloudy is nicer than blazing sun" is a defensible generalisation for a lightly-clothed
adult outdoors, but it is closer to a cultural preference than the other three, which rest on
thermal physiology. It is also why cloudiness never scores 0: overcast is *drab*, not
*unbearable*, so its effective range is 0.35–1.00 rather than the full 0–1. That narrower range
means cloudiness is quieter in practice than its 0.20 weight suggests — a deliberate hedge
against the weakest-justified parameter.

**Weights need not sum to 1.** The calculator normalises by the total weight actually used.
This is not cosmetic: it means a new parameter can be added by appending one weight without
rebalancing every existing one, and it guarantees `base` stays within 0–1 whatever the
configuration says. Asserted in `weightsAreNormalised`.

### The `feels_like` decision

OpenWeatherMap already publishes `feels_like`, which folds in humidity and wind. There was a
real choice here:

- **Score `feels_like` directly** — less work, but you are then partly rating someone else's
  model, and justifying a separate humidity weight becomes incoherent when humidity is already
  baked into the temperature term.
- **Score `temp` and add your own humidity and wind terms** — every weight is yours to defend,
  which is what the assignment asks for.

I chose the second, then used `feels_like` for what it uniquely knows. Across the 130-city
sample the gap `|feels_like − temp|` had a median of **0.0 °C** but ranged from −3.4 to +7.0.
So `feels_like` is *mostly* a restatement of `temp` — and the informative part is precisely the
gap, which is large exactly when conditions amplify the raw reading (Colombo: 30 °C reading,
36.6 °C perceived).

The gap is therefore applied as a **multiplier**, not a weighted term. Structurally that is
what it is: a modifier of perception rather than an independent dimension of comfort.

**Disclosure:** humidity now influences the score twice — directly at weight 0.25, and
indirectly through the gap, since humidity is what drives the gap upward in hot climates. This
is a genuine methodological wrinkle. I kept both because they capture different things (the
direct term covers respiratory and skin discomfort at any temperature; the gap covers thermal
amplification specifically) and because the gap's floor of 0.80 caps its influence at 20%.
Dropping the gap entirely would also have been defensible.

---

## Trade-offs considered

**Hybrid scoring vs. weighted sum vs. purely multiplicative.**
A weighted sum is simple and explainable but compensatory — the 44 °C case above. Purely
multiplicative is correctly harsh at the extremes but unforgiving everywhere else: scores bunch
near zero, and "weights" become exponents, which are much harder to justify. The hybrid keeps
the additive half's smooth, explainable behaviour in the normal range and adds teeth only where
needed. Cost: more moving parts and two concepts to explain instead of one.

**Hand-written cache vs. Spring's `@Cacheable` + Caffeine.**
Spring's cache abstraction is the conventional answer and I started there. I removed it. The
assignment requires a debug endpoint reporting HIT/MISS, and the abstraction deliberately hides
that behind a proxy — surfacing it means reaching around the abstraction anyway. Roughly 100
lines of `TtlCache` gives explicit hit/miss accounting, an injectable `Clock`, and behaviour I
can fully explain. Cost: no eviction policy beyond TTL, no size bound, no distribution. Fine
for ten cities; the wrong choice for a large or unbounded key space.

**Spring Boot 3.5 vs. 4.1.**
Boot 4 is current, and Spring Initializr now defaults to it. I chose 3.5.16 because Boot 4
renamed the starters (`spring-boot-starter-webmvc`, not `-web`), while every tutorial, Stack
Overflow answer and Auth0 quickstart still uses the Boot 3 names. Matching the documentation
matters more here than being on the newest minor. 3.5.x is fully supported, not legacy.

**Explicit CORS vs. a Vite dev proxy.**
A dev proxy would make everything look same-origin and remove CORS from the picture during
development — and would then hide a real production concern behind a development convenience.
The two services genuinely are separate origins, so CORS is configured explicitly and tested
(`rejectsUntrustedOrigin`).

**Lazy vs. eager chart loading.**
Recharts is about two thirds of the bundle. Loading it eagerly is simpler; loading it via
`React.lazy` cut the initial bundle from 804 kB to 415 kB, so the ranked list — the actual
point of the page — paints without waiting for charting code. Cost: a brief placeholder.

**Sorting client-side vs. server-side.**
Ranking is server-side because the assignment requires it and because every client must agree.
Re-sorting by temperature or name is client-side: it is a presentation preference over ten rows
already in memory, and a round-trip for it would be pure latency. The score itself is never
recomputed in the browser.

**Read `cities.json` once vs. per request.**
Read once at startup. The file cannot change while the process runs, so re-reading would add
disk I/O to a hot path for nothing. Cost: editing the city list requires a restart.

**Failing whole vs. degrading.**
If one city's fetch fails, the dashboard returns the other nine plus an explicit `failures`
array, and the UI says which cities are missing. The alternative — failing the entire request —
would mean one flaky city takes down the whole feature. Cost: callers must notice `failures`
rather than assuming completeness.

---

## Cache design

Two caches, at different layers, expiring independently.

| | `raw-weather` | `processed-dashboard` |
|---|---|---|
| Key | city code (`"1248991"`) | constant (`"dashboard"`) |
| Value | the OpenWeatherMap response | the fully scored, ranked payload |
| TTL | 5 minutes (assignment requirement) | 5 minutes |
| Entries | one per city (10) | one |
| A hit saves | one HTTP call | ten HTTP calls, plus all scoring and sorting |

**Why two and not one.** They answer different questions. Caching only raw responses would
re-score and re-sort ten cities on every request. Caching only the processed payload would
refetch all ten cities the moment it expired, even for a city whose reading was still perfectly
fresh. Keeping them separate means the processed TTL can be shortened independently — the
ranking is rebuilt from still-valid raw data without touching the network. This is asserted in
`rawCacheOutlivesProcessedCache`.

**Eviction is lazy.** An expired entry is removed when it is next read, not by a background
thread. Trade-off: an entry that is never read again occupies memory until `purgeExpired()`
runs. Irrelevant for a fixed set of ten cities, and it avoids a scheduler and its lifecycle.
For an unbounded key space this would need revisiting.

**The `Clock` is injected**, not read from the system. This is why a 5-minute TTL can be tested
in microseconds — `MutableClock` simply jumps forward. Reaching for `Instant.now()` inside
business logic is the usual reason time-dependent behaviour ends up untested.

**Cache status is exposed two ways** — the `cacheStatus` field in the body and an `X-Cache`
response header, so behaviour is visible from `curl` without parsing JSON:

```bash
curl -i http://localhost:8081/api/weather | grep -i x-cache
# X-Cache: MISS      ← first call
# X-Cache: HIT       ← second call
```

`GET /api/debug/cache` reports both caches in full — size, TTL, cumulative hits and misses, hit
rate, and every live key with its age and remaining lifetime. `DELETE /api/debug/cache` empties
both and resets the counters, which makes cache behaviour demonstrable without waiting five
minutes for a TTL to expire.

**A known gap:** on a cold cache, ten concurrent requests can each miss and trigger their own
fetch (a "cache stampede"). With ten cities and a 60-calls-per-minute quota this is harmless.
The fix would be a per-key lock or a single-flight loader. It is not implemented, and I would
rather name it than pretend it isn't there.

---

## Authentication and authorization

**The single most important point:** the React login screen protects nothing on its own.
Anyone can open devtools, read the bundle, and call the API with `curl`. The real control is
that every `/api/**` request must carry an access token the server verifies.

**Flow.** The SPA uses **Authorization Code with PKCE**. A browser application cannot keep a
client secret — anything in the bundle is readable — so instead the app generates a random
verifier, sends only its hash to start the login, and presents the original verifier when
exchanging the code. An attacker who intercepts the code cannot use it without the verifier.

**Verification.** Auth0 signs tokens with the private half of an RS256 key pair and publishes
the public half at `https://<domain>/.well-known/jwks.json`. Spring fetches and caches those
keys, then checks the signature, `iss` and `exp`. The server never sees a client secret and
never calls Auth0 to check a token — which is what makes it scale.

**Audience validation is ours.** Spring's defaults do *not* check `aud`. Without
`AudienceValidator`, an access token minted for **any other API in the same tenant** would pass
every other check — correct signature, correct issuer, unexpired — and be accepted here. That
is a real vulnerability, not a theoretical one, and it has its own test class.

**Route rules:**

| Route | Access |
|---|---|
| `GET /api/health` | public — a health probe that needs a token is useless to a load balancer |
| `OPTIONS /**` | public — browsers send an unauthenticated CORS preflight |
| `/api/weather` | authenticated |
| `/api/debug/**` | authenticated — an open cache-flush endpoint is a denial-of-service lever |
| anything else | denied |

**CSRF is disabled, deliberately.** There is no cookie and no server-side session, so there is
no CSRF vector: an attacker's page cannot make the browser attach a bearer token. Disabling it
without that reasoning would be a bug; here it is correct.

**MFA and whitelisting** are configured in the Auth0 tenant rather than in code — email MFA
with an *Always* policy, and sign-ups disabled on the database connection so only an
administrator can create users. See [Setup](#3-auth0-configuration).

---

## API reference

### `GET /api/weather` 🔒

```json
{
  "generatedAt": "2026-09-04T13:40:31Z",
  "cacheStatus": "MISS",
  "cityCount": 10,
  "cities": [
    {
      "rank": 1,
      "cityCode": "2988507",
      "cityName": "Paris",
      "country": "FR",
      "description": "few clouds",
      "icon": "02d",
      "temperatureC": 28.15,
      "feelsLikeC": 28.81,
      "humidityPct": 52.0,
      "windSpeedMs": 6.17,
      "cloudinessPct": 23.0,
      "pressureHpa": 1016.0,
      "rainMmPerHour": 0.0,
      "comfortScore": 88.2,
      "comfortLabel": "Excellent",
      "subScores":   { "temperature": 0.845, "humidity": 1.0, "wind": 0.768, "cloudiness": 1.0 },
      "multipliers": { "rain": 1.0, "snow": 1.0, "feelsGap": 0.98, "heat": 1.0, "cold": 1.0, "gale": 1.0 },
      "observedAt": "2026-09-04T13:38:00Z"
    }
  ],
  "failures": []
}
```

Response header `X-Cache: HIT | MISS`.

`subScores` and `multipliers` are included so the UI can show *why* a city scored what it did.
A constructed index with no ground truth is only trustworthy if it can be interrogated.

### `GET /api/debug/cache` 🔒

```json
{
  "serverTime": "2026-09-04T13:41:00Z",
  "caches": [
    { "name": "raw-weather", "ttlSeconds": 300, "size": 10, "hits": 0, "misses": 10,
      "evictions": 0, "hitRate": 0.0,
      "entries": [ { "key": "1248991", "ageSeconds": 17, "expiresInSeconds": 282, "expired": false } ] },
    { "name": "processed-dashboard", "ttlSeconds": 300, "size": 1, "hits": 1, "misses": 1,
      "evictions": 0, "hitRate": 0.5, "entries": [ ... ] }
  ]
}
```

### `DELETE /api/debug/cache` 🔒
Empties both caches and resets counters. `204 No Content`.

### `GET /api/health`
Public. `{"status":"UP","cityCount":10,"serverTime":"..."}`

### Error responses

RFC 7807 `application/problem+json`.

| Status | Meaning |
|---|---|
| 401 | missing, malformed, expired or wrong-audience token |
| 403 | authenticated but the route is not permitted |
| 502 | OpenWeatherMap unreachable or rejecting us — *their* fault, not ours |
| 500 | unexpected server error; details are logged, never returned |

---

## Testing

```bash
cd server && ./mvnw test        # 170 tests
cd client && npm test           # 35 tests
```

Coverage report (JaCoCo): `server/target/site/jacoco/index.html`.

### How the Comfort Index is tested

A constructed index has no ground truth, so the tests assert **properties** rather than a table
of expected outputs:

- **Bounds** — the score is within 0–100 and never `NaN`, swept across the whole plausible
  input space and against deliberately absurd inputs (−273 °C, 200 m/s wind).
- **Monotonicity** — moving further from the ideal never *raises* the score. Checked for
  temperature in both directions, humidity, wind and rain.
- **Asymmetry** — heat is punished harder than the equivalent departure into cold.
- **Non-compensation** — the 44 °C case: `base > 0.60` but `score < 35`.
- **Interior optimum** — dead calm scores below a light breeze, but above a gale.
- **Configuration** — re-weighting changes the score (nothing is hard-coded), weights need not
  sum to 1, and all-zero weights do not divide by zero.

This matters practically: **property tests survive re-tuning.** A test asserting
`score == 73.2` would break every time a weight moved. These do not — which is exactly what is
needed when a parameter has to be added live.

- **Face validity** is the closest thing to a correctness check available, and it has its own
  test: real captured readings must rank in an order a person would recognise (mild Boston >
  cool Oslo > humid Colombo > desert Dubai).

### The rest of the suite

| Area | What it covers |
|---|---|
| `TtlCacheTest` (18) | hit/miss, TTL boundary, lazy eviction, statistics, invalidation, and an 8-thread concurrency test |
| `WeatherServiceTest` (36) | MISS→HIT, per-city keys, TTL expiry, raw outliving processed, ranking, ties, partial failure, mapping |
| `OpenWeatherMapClientTest` (8) | request construction incl. the API key, deserialisation, and 401/404/429/503/empty-body handling |
| `OwmWeatherResponseTest` (9) | deserialisation against **real captured payloads**, including Boston's missing `wind.gust` and Tokyo's present-only-when-raining `rain` |
| `ApiSecurityTest` (12) | 401 without a token, 200 with one, malformed and non-bearer tokens, protected debug routes, public health, CORS preflight and origin rejection |
| `AudienceValidatorTest` (5) | the confused-deputy case Spring does not check by default |
| `CityRepositoryTest` (9) | parsing, the ≥10-city minimum, duplicates, and fail-fast on a bad file |
| Frontend (35) | rendering, sort, filter, expand, loading/error states, cache badge, and that the browser never computes a score |

**Test fixtures are real OpenWeatherMap payloads**, captured during design. Hand-written
fixtures encode what you *believe* the API returns; these encode what it actually returned —
including the awkward cases invented fixtures would have quietly omitted.

---

## Known limitations

**Of the index itself**

1. **No ground truth exists.** This is a constructed index, not a measurement. It cannot be
   validated, only justified. Everything above is an argument, not a proof.
2. **Cloudiness is the weakest-justified parameter.** "Scattered cloud is ideal" is closer to a
   cultural preference than to physiology. Its narrow 0.35–1.00 range is a deliberate hedge.
3. **Humidity is double-counted**, directly and through the feels-like gap. Disclosed above,
   capped at 20% by the gap's floor, and a legitimate criticism.
4. **The subject is fixed.** A single index for "a lightly-clothed adult" cannot serve a
   marathon runner and an elderly resident. Per-persona weight profiles would be a natural
   extension, and the configuration already supports it.
5. **No time-of-day or seasonal adjustment.** 25 °C at midnight is not 25 °C at noon; the index
   treats them identically.
6. **No sensitivity analysis is shipped.** I checked by hand that ±10% weight shifts do not
   reshuffle the ranking, but this is not automated. It should be.

**Of the implementation**

7. **The cache is in-memory and per-instance.** Two instances behind a load balancer would keep
   independent caches and could serve different rankings. Redis would be the fix.
8. **Cache stampede on a cold start** — described under [Cache design](#cache-design).
9. **Cities are fetched sequentially.** Ten calls at roughly 200 ms each means a cold request
   takes about two seconds. Parallelising would be straightforward; with a 5-minute cache and a
   60-calls-per-minute quota it was not worth the added failure modes.
10. **`cities.json` is read once at startup.** Editing it requires a restart.
11. **`wind.gust` and `visibility` are unused** — justified above, but it does mean two
    available fields are ignored.
12. **Snow is untested against live data.** The 130-city sample was taken in September, so
    `snow` never appeared. The code path exists and is unit-tested, but has never seen a real
    snowy payload.
13. **No rate-limiting on our own API.** An authenticated client could hammer `/api/weather`;
    the cache absorbs it, but there is no explicit throttle.
14. **`app.security.mode=permissive` exists.** It is development-only and logs a loud warning at
    startup, but it is a foot-gun that a production deployment must never enable.
