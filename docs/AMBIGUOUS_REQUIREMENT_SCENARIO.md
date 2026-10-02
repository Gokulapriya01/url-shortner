# Ambiguous requirement: “Make URLs more shareable”

## Unknowns and targeted questions

1. Does shareability mean shorter URLs, memorable aliases, previews, or social-platform metadata?
2. Which characters and maximum alias length should be allowed?
3. Should shared links remain permanent, or may the owner set an expiration?
4. Should duplicate aliases be rejected or automatically changed?
5. Are edit/delete access control, branded domains, or previews required?

## Explicit assumptions pending stakeholder confirmation

For this local acceptance demonstration, shareability means a memorable optional alias and optional expiration.
Aliases use ASCII letters/digits/hyphen/underscore, length 3–50. This avoids unsafe path characters.
Duplicate aliases return 409 rather than changing a user-selected name.
No expiration means permanent; provided TTL is in seconds, 1–31,536,000.
Expired links return 410 even if Redis retains an old entry.
Previews, branded domains, ownership/authentication and social metadata remain outside this assumption.
These are assumptions, not a record of human responses. Earlier illustrative Slack approvals are removed.

## Testable specification and trace

- Create with a valid alias: 201, response uses the exact alias, redirect points to original URL.
- Create same alias again: 409 ALIAS_EXISTS; no second database record.
- Unsafe scheme or invalid alias: descriptive 400 before database access.
- Expiring alias: 302 before expiration; 410 afterward, including cache-hit path.
- Non-expiring alias: 301 and configured normal cache TTL.

Evidence: UrlServiceTest, UrlClientErrorsTest, CacheServiceExpirationTest and live endpoint responses.
Formal scenario acceptance still needs actual stakeholder answers and approval of these assumptions.
