"""D24 media validation and output-only URL serialization."""
from urllib.parse import urlsplit


def validate_media_path(value: str) -> str:
    parts = value.split("/")
    if (
        not value.startswith("media/") or len(parts) < 2
        or any(part in ("", ".", "..") for part in parts)
        or any(char in value for char in "\\:%?#")
        or any(char.isspace() or ord(char) < 32 or ord(char) == 127 for char in value)
    ):
        raise ValueError("Media must be a relative media/ path")
    return value


def validate_media_base(value: str) -> str:
    parsed = urlsplit(value)
    if (
        not parsed.hostname or parsed.username or parsed.password
        or parsed.query or parsed.fragment or not value.endswith("/")
        or parsed.scheme not in ("https", "http")
        or (parsed.scheme == "http" and parsed.hostname != "10.0.2.2")
        or "\\" in value
    ):
        raise ValueError("MEDIA_BASE_URL must end in / and use HTTPS (HTTP only for 10.0.2.2 dev)")
    # Validate the port as well; urlsplit defers that check.
    _ = parsed.port
    return value


def media_url(path: str | None) -> str | None:
    if path is None:
        return None
    from app.core.config import settings

    return validate_media_base(settings.MEDIA_BASE_URL) + validate_media_path(path)
