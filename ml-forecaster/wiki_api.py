"""Address and identity for the wiki price API, for the Python side.

This mirrors ``com.flippingfriend.core.WikiApi`` on the Java side. It is duplicated rather than
shared because the two runtimes cannot see each other's constants — so **if you change one, change
the other**. Both are checked by ``test_wiki_api.py``, which fails if the strings drift apart.

A descriptive User-Agent carrying a contact is the only thing the wiki asks in return for the data,
and the check is automated. Measured 2 September 2026::

    curl -A 'curl/8.0'  .../api/v1/osrs/latest  ->  403
    curl -A USER_AGENT  .../api/v1/osrs/latest  ->  200

Longer term this module should not need to exist: audit item 40 has the ML service reading history
from the companion's ``/v1/market/series`` over loopback, so exactly one process in the system talks
to the wiki. Until then, this at least makes the Python client identify itself the same way.
"""

VERSION = "v1"

BASE = f"https://prices.runescape.wiki/api/{VERSION}/osrs"

# Bump when request behaviour changes, not on every release. Keep in step with WikiApi.VERSION_TAG.
VERSION_TAG = "1.2"

CONTACT = "flippingfriend.dev@gmail.com"

USER_AGENT = f"FlippingFriend/{VERSION_TAG} ({CONTACT})"

HEADERS = {"User-Agent": USER_AGENT}
