"""Quant foundation for Signa-Word V3.1.

This module deliberately contains no fabricated market data and no trained AI model.
It defines the point-in-time feature/score/backtest contracts that later AI models
(Qlib/LightGBM/etc.) can consume.
"""
from __future__ import annotations

import math
from typing import Any, Dict, Iterable, List, Optional


def board_type(code: str, name: str = "") -> str:
    c = str(code or "").lower().replace("sh", "").replace("sz", "")
    if name.startswith("ST") or name.startswith("*ST"):
        return "ST"
    if c.startswith("300"):
        return "CHINEXT"
    if c.startswith("688"):
        return "STAR"
    if c.startswith(("000", "001", "002", "003", "600", "601", "603", "605")):
        return "MAIN"
    return "UNKNOWN"


def limit_pct(code: str, name: str = "") -> float:
    b = board_type(code, name)
    return {"MAIN": 0.10, "ST": 0.10, "CHINEXT": 0.20, "STAR": 0.20}.get(b, 0.0)


def _clip(x: float, lo: float = -1.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, float(x)))


def _safe(x: Any, default: float = 0.0) -> float:
    try:
        v = float(x)
        return default if not math.isfinite(v) else v
    except (TypeError, ValueError):
        return default


def build_feature_vector(
    stock: Dict[str, Any],
    *,
    event_strength: float = 0.0,
    event_confidence: float = 0.0,
    event_direction: float = 0.0,
    name_resonance: float = 0.0,
    concept_heat: float = 0.0,
) -> Dict[str, Any]:
    """Create a point-in-time feature vector.

    event/name/concept features are supplied by upstream engines. Market features
    come only from the stock snapshot passed to this function.
    """
    code = str(stock.get("code", ""))
    name = str(stock.get("name", ""))
    change_pct = _safe(stock.get("changePct"))
    volume = _safe(stock.get("volume"))
    amount = _safe(stock.get("amount"))
    close = _safe(stock.get("close"))
    open_ = _safe(stock.get("open"))
    high = _safe(stock.get("high"))
    low = _safe(stock.get("low"))
    intraday_range = ((high - low) / close) if close > 0 else 0.0
    open_gap = ((close - open_) / open_) if open_ > 0 else 0.0

    return {
        "code": code,
        "name": name,
        "board": board_type(code, name),
        "limit_pct": limit_pct(code, name),
        "event_strength": _clip(event_strength, 0.0, 1.0),
        "event_confidence": _clip(event_confidence, 0.0, 1.0),
        "event_direction": _clip(event_direction),
        "name_resonance": _clip(name_resonance, 0.0, 1.0),
        "concept_heat": _clip(concept_heat, 0.0, 1.0),
        "change_pct": change_pct,
        "volume": volume,
        "amount": amount,
        "close": close,
        "open": open_,
        "high": high,
        "low": low,
        "intraday_range": intraday_range,
        "open_gap": open_gap,
    }


def baseline_alpha(features: Dict[str, Any]) -> float:
    """Transparent V0 score, not an AI prediction.

    It provides a stable target/interface for later ML models and is intentionally
    interpretable so backtests can distinguish model gains from rule gains.
    """
    event = _safe(features.get("event_strength")) * _safe(features.get("event_confidence"))
    direction = _safe(features.get("event_direction"))
    resonance = _safe(features.get("name_resonance"))
    concept = _safe(features.get("concept_heat"))
    momentum = _clip(_safe(features.get("change_pct")) / 10.0)
    volume = _safe(features.get("volume_ratio"), 0.0)
    volume_signal = _clip((volume - 1.0) / 2.0) if volume else 0.0
    return _clip(0.35 * event * direction + 0.25 * resonance + 0.15 * concept + 0.15 * momentum + 0.10 * volume_signal)


def _max_drawdown(equity: List[float]) -> float:
    peak = -float("inf")
    max_dd = 0.0
    for value in equity:
        peak = max(peak, value)
        if peak > 0:
            max_dd = min(max_dd, value / peak - 1.0)
    return max_dd


def backtest_long_only(rows: Iterable[Dict[str, Any]], *, initial_cash: float = 100000.0,
                       score_threshold: float = 0.0, fee_rate: float = 0.0003,
                       slippage: float = 0.0005) -> Dict[str, Any]:
    """Minimal daily point-in-time backtest.

    Rows must already be ordered by date and contain close/signal. This engine is
    intentionally conservative: position changes execute at the next row's open,
    so today's signal cannot use tomorrow's price.
    """
    data = list(rows)
    if len(data) < 2:
        return {"status": "insufficient_data", "rows": len(data)}

    cash = initial_cash
    shares = 0.0
    equity_curve: List[float] = []
    trades = 0

    for i, row in enumerate(data):
        close = _safe(row.get("close"))
        equity_curve.append(cash + shares * close)
        if i + 1 >= len(data):
            continue
        next_row = data[i + 1]
        next_open = _safe(next_row.get("open"))
        signal = _safe(row.get("signal"))
        if next_open <= 0:
            continue
        target_long = signal > score_threshold
        if target_long and shares == 0:
            buy_price = next_open * (1.0 + slippage)
            shares = math.floor(cash / (buy_price * (1.0 + fee_rate)) / 100.0) * 100.0
            if shares > 0:
                cash -= shares * buy_price * (1.0 + fee_rate)
                trades += 1
        elif not target_long and shares > 0:
            sell_price = next_open * (1.0 - slippage)
            cash += shares * sell_price * (1.0 - fee_rate)
            shares = 0.0
            trades += 1

    final_close = _safe(data[-1].get("close"))
    final_equity = cash + shares * final_close
    equity_curve.append(final_equity)
    total_return = final_equity / initial_cash - 1.0
    return {
        "status": "ok",
        "rows": len(data),
        "trades": trades,
        "initial_cash": initial_cash,
        "final_equity": final_equity,
        "total_return": total_return,
        "max_drawdown": _max_drawdown(equity_curve),
        "fee_rate": fee_rate,
        "slippage": slippage,
        "execution": "next_open",
    }
