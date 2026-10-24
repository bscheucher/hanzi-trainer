"""Writes the FSRS reference cases of spec 09 by running the upstream scheduler (py-fsrs, version pinned in
requirements.txt) with fuzzing off.

    python3 -m venv /tmp/fsrs-venv && /tmp/fsrs-venv/bin/pip install -r tools/fsrs-reference/requirements.txt
    /tmp/fsrs-venv/bin/python tools/fsrs-reference/generate.py src/test/resources/fsrs/reference.json

Each sequence starts from a new card at 2026-01-01T08:00:00Z; every review happens exactly at the previous due.
"""

import json
import sys
from datetime import datetime, timezone
from importlib.metadata import version

from fsrs import Card, Rating, Scheduler

START = datetime(2026, 1, 1, 8, 0, 0, tzinfo=timezone.utc)
SEQUENCES = [
    [3, 3, 3, 3, 3],
    [1, 3, 3, 4],
    [3, 1, 3, 3, 2, 3],
    [4, 4, 4],
    [2, 2, 2, 3],
]


def iso(moment: datetime) -> str:
    return moment.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def run(ratings: list[int]) -> dict:
    scheduler = Scheduler(enable_fuzzing=False)
    card = Card(card_id=1, due=START)
    steps = []
    for rating in ratings:
        reviewed_at = card.due
        card, _ = scheduler.review_card(card, Rating(rating), review_datetime=reviewed_at)
        steps.append({
            "rating": rating,
            "reviewedAt": iso(reviewed_at),
            "state": card.state.name.upper(),
            "step": card.step,
            "stability": card.stability,
            "difficulty": card.difficulty,
            "due": iso(card.due),
        })
    return {"ratings": ratings, "steps": steps}


def main() -> None:
    reference = {
        "upstream": "py-fsrs " + version("fsrs"),
        "start": iso(START),
        "fuzzing": False,
        "cases": [run(ratings) for ratings in SEQUENCES],
    }
    with open(sys.argv[1], "w", encoding="utf-8") as out:
        json.dump(reference, out, indent=2)
        out.write("\n")


if __name__ == "__main__":
    main()
