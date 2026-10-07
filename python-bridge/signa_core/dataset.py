"""Point-in-time dataset utilities for the Signa-Word AI-quant pipeline.

The dataset layer turns historical OHLCV rows plus upstream event/name/concept
features into supervised-learning samples. Labels are deliberately computed
from future prices while features are restricted to the information available
at the sample timestamp.
"""
from __future__ import annotations

from typing import Any, Dict, Iterable, List, Sequence

from .quant import _safe


FEATURE_COLUMNS = [
    "event_strength",
    "event_confidence",
    "event_direction",
    "name_resonance",
    "concept_heat",
    "change_pct",
    "volume_ratio",
    "intraday_range",
    "open_gap",
]


def build_supervised_dataset(
    rows: Iterable[Dict[str, Any]], *, horizon: int = 5,
    label: str = "forward_return",
) -> List[Dict[str, Any]]:
    """Build point-in-time samples from ordered daily rows.

    Each row is a feature snapshot. The label is calculated from the close at
    t+horizon divided by close at t minus one. No future value is copied into
    the feature columns.
    """
    data = list(rows)
    if horizon < 1:
        raise ValueError("horizon must be >= 1")
    result: List[Dict[str, Any]] = []
    for i, row in enumerate(data):
        j = i + horizon
        close = _safe(row.get("close"))
        future_close = _safe(data[j].get("close")) if j < len(data) else 0.0
        if close <= 0 or future_close <= 0:
            continue
        sample = {
            "date": str(row.get("date", ""))[:10],
            "code": str(row.get("code", "")),
            "label_horizon": horizon,
            label: future_close / close - 1.0,
        }
        for name in FEATURE_COLUMNS:
            sample[name] = _safe(row.get(name))
        result.append(sample)
    return result


def split_time_series(rows: Sequence[Dict[str, Any]], train_ratio: float = 0.8):
    """Chronological train/test split; never shuffles financial time series."""
    if not 0 < train_ratio < 1:
        raise ValueError("train_ratio must be between 0 and 1")
    data = list(rows)
    cut = max(1, min(len(data) - 1, int(len(data) * train_ratio))) if len(data) > 1 else len(data)
    return data[:cut], data[cut:]


def train_lightgbm(rows: Sequence[Dict[str, Any]], *, label: str = "forward_return") -> Dict[str, Any]:
    """Train an optional LightGBM regressor when the dependency is installed.

    The function intentionally refuses to fabricate a model when LightGBM is
    unavailable. It returns metadata that can be persisted by a later model
    registry layer.
    """
    try:
        import lightgbm as lgb  # type: ignore
    except Exception as exc:
        return {"status": "unavailable", "error": str(exc), "model": "lightgbm"}

    train, test = split_time_series(rows)
    if len(train) < 20 or not test:
        return {"status": "insufficient_data", "train_rows": len(train), "test_rows": len(test)}

    x_train = [[_safe(r.get(c)) for c in FEATURE_COLUMNS] for r in train]
    y_train = [_safe(r.get(label)) for r in train]
    x_test = [[_safe(r.get(c)) for c in FEATURE_COLUMNS] for r in test]
    y_test = [_safe(r.get(label)) for r in test]

    model = lgb.LGBMRegressor(
        n_estimators=200,
        learning_rate=0.03,
        num_leaves=31,
        random_state=42,
        verbosity=-1,
    )
    model.fit(x_train, y_train)
    predictions = model.predict(x_test)
    mse = sum((float(p) - y) ** 2 for p, y in zip(predictions, y_test)) / len(y_test)
    return {
        "status": "trained",
        "model": "lightgbm",
        "feature_columns": FEATURE_COLUMNS,
        "train_rows": len(train),
        "test_rows": len(test),
        "test_mse": mse,
    }
