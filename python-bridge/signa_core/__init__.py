"""Signa-Word quantitative core."""

from .quant import (
    board_type,
    limit_pct,
    build_feature_vector,
    baseline_alpha,
    backtest_long_only,
)

__all__ = [
    "board_type",
    "limit_pct",
    "build_feature_vector",
    "baseline_alpha",
    "backtest_long_only",
    "build_supervised_dataset",
    "split_time_series",
    "train_lightgbm",
]

from .dataset import build_supervised_dataset, split_time_series, train_lightgbm
