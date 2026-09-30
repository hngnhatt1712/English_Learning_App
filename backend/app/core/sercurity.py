"""Hash & verify password bằng Argon2id (argon2-cffi)."""
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError, VerifyMismatchError

# Tham số mặc định của argon2-cffi đã là Argon2id với cấu hình an toàn (RFC 9106).
_ph = PasswordHasher()


def hash_password(password: str) -> str:
    """Trả về chuỗi hash để lưu vào users.password_hash"""
    return _ph.hash(password)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """True nếu mật khẩu khớp hash; không bao giờ raise vì sai mật khẩu/hash lỗi"""
    try:
        return _ph.verify(hashed_password, plain_password)
    except (VerifyMismatchError, VerificationError, InvalidHashError):
        return False


def needs_rehash(hashed_password: str) -> bool:
    """True nếu hash cũ dùng tham số yếu hơn cấu hình hiện tại (nên hash lại khi user login)"""
    return _ph.check_needs_rehash(hashed_password)