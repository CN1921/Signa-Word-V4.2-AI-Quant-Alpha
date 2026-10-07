"""Optional Qlib adapter.

Qlib is intentionally optional in V3.1. Signa-Word remains usable without it;
when installed, this module exposes capability information for later dataset/model
integration.
"""
try:
    import qlib  # type: ignore
    QLIB_AVAILABLE = True
    QLIB_VERSION = getattr(qlib, "__version__", "unknown")
except Exception:
    QLIB_AVAILABLE = False
    QLIB_VERSION = ""


def status():
    return {
        "available": QLIB_AVAILABLE,
        "version": QLIB_VERSION,
        "role": "alpha/model/backtest/portfolio adapter",
    }
