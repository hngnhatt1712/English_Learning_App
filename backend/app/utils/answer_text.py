"""Text normalization shared by payload validation and answer grading."""


def normalize_answer_text(text: str) -> str:
    """Casefold, trim and collapse whitespace while preserving punctuation."""
    return " ".join(text.casefold().split())
