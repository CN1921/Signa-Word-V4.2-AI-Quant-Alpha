#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
AKShare Bridge Server - 多数据源版
优先使用东方财富(em)概念成分数据；新浪(sina)仅用于实时行情。
供 Java Spring Boot 调用，获取 A 股实盘数据。
"""
import os
import sys
import json
import time
import threading
import logging

try:
    from signa_core.quant import build_feature_vector, baseline_alpha, backtest_long_only
    from signa_core.dataset import build_supervised_dataset, train_lightgbm
    from signa_core.qlib_adapter import status as qlib_status
    QUANT_AVAILABLE = True
except Exception as e:
    QUANT_AVAILABLE = False
    _QUANT_IMPORT_ERROR = str(e)
from contextlib import contextmanager
from datetime import datetime, timedelta

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger(__name__)

try:
    from flask import Flask, jsonify, request
    FLASK_AVAILABLE = True
except ImportError:
    FLASK_AVAILABLE = False
    logger.error("Flask not installed. Run: pip install flask")

try:
    import akshare as ak
    import pandas as pd
    AKSHARE_AVAILABLE = True
    logger.info("AKShare version: %s", ak.__version__)
except ImportError:
    AKSHARE_AVAILABLE = False
    logger.warning("AKShare not available. Install: pip install akshare")

# ---------- 缓存 ----------
_realtime_cache = []
_realtime_cache_time = 0
_realtime_source = ''
_concept_to_stocks = {}
_stock_to_concepts = {}
_concepts_loaded = False
_concepts_building = False
_em_fail_time = 0          # 东方财富最近失败时间 (用于跳过重试)
_refreshing = False        # 是否正在刷新中
_concept_last_error = ''
_concept_updated_at = ''
_concept_provider = ''
_concept_progress = {'done': 0, 'total': 0}

CACHE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'cache')
CONCEPT_CACHE_FILE = os.path.join(CACHE_DIR, 'concepts.json')
REALTIME_CACHE_TTL = 300   # 5 分钟缓存 (盘后数据不变)
EM_RETRY_INTERVAL = 300    # 东方财富失败后 5 分钟内不再重试
CONCEPT_RETRY_INTERVAL = 300
CONCEPT_MAX_BUILD = int(os.environ.get('SIGNAL_CONCEPT_MAX_BUILD', '0'))  # 0=全部

app = Flask(__name__) if FLASK_AVAILABLE else None


def safe_float(val, default=0.0):
    try:
        if val is None or (isinstance(val, float) and val != val):
            return default
        return float(val)
    except:
        return default


def safe_int(val, default=0):
    try:
        if val is None or (isinstance(val, float) and val != val):
            return default
        return int(val)
    except:
        return default


def normalize_code(raw_code):
    """归一化股票代码: 'sh600519' -> '600519', 'sz000001' -> '000001'"""
    code = str(raw_code).strip().lower()
    for prefix in ('sh', 'sz', 'bj'):
        if code.startswith(prefix):
            return code[2:]
    return code


def is_target_stock(code):
    """A股目标池: 主板 + 创业板 + 科创板; 排除北交所、基金、ETF等。"""
    c = normalize_code(code)
    if len(c) != 6 or not c.isdigit():
        return False
    return c.startswith(('000','001','002','003','600','601','603','605','300','688'))

def filter_target(stocks):
    return [s for s in stocks if is_target_stock(s.get('code'))]


@contextmanager
def direct_network():
    """临时绕过 HTTP(S) 环境代理，避免代理断开导致东方财富接口直接失败。"""
    keys = ('HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'http_proxy', 'https_proxy', 'all_proxy')
    old = {k: os.environ.get(k) for k in keys}
    try:
        for k in keys:
            os.environ.pop(k, None)
        yield
    finally:
        for k, v in old.items():
            if v is not None:
                os.environ[k] = v


def call_akshare(func, *args, direct=False, **kwargs):
    if direct:
        try:
            with direct_network():
                return func(*args, **kwargs)
        except Exception as direct_error:
            # 直连不可用时恢复原环境代理再试一次，兼容需要代理的网络。
            try:
                return func(*args, **kwargs)
            except Exception:
                raise direct_error
    return func(*args, **kwargs)


# ---------- 实时行情 ----------
def fetch_realtime_em():
    """从东方财富获取实时行情"""
    df = ak.stock_zh_a_spot_em()
    stocks = []
    for _, row in df.iterrows():
        code = str(row.get('代码', ''))
        name = str(row.get('名称', ''))
        if not code or not name or name == 'nan':
            continue
        stocks.append({
            'code': code,
            'name': name,
            'open': safe_float(row.get('今开')),
            'close': safe_float(row.get('最新价')),
            'high': safe_float(row.get('最高')),
            'low': safe_float(row.get('最低')),
            'changePct': safe_float(row.get('涨跌幅')),
            'volume': safe_int(row.get('成交量')),
            'amount': safe_float(row.get('成交额')),
        })
    return filter_target(stocks), 'em'


def fetch_realtime_sina():
    """从新浪获取实时行情"""
    df = ak.stock_zh_a_spot()
    stocks = []
    for _, row in df.iterrows():
        raw_code = str(row.get('代码', ''))
        name = str(row.get('名称', ''))
        if not raw_code or not name or name == 'nan':
            continue
        code = normalize_code(raw_code)
        stocks.append({
            'code': code,
            'name': name,
            'open': safe_float(row.get('今开')),
            'close': safe_float(row.get('最新价')),
            'high': safe_float(row.get('最高')),
            'low': safe_float(row.get('最低')),
            'changePct': safe_float(row.get('涨跌幅')),
            'volume': safe_int(row.get('成交量')),
            'amount': safe_float(row.get('成交额')),
        })
    return filter_target(stocks), 'sina'


def refresh_realtime():
    """获取实时行情, 优先东方财富, 降级新浪"""
    global _realtime_cache, _realtime_cache_time, _realtime_source, _em_fail_time, _refreshing
    if not AKSHARE_AVAILABLE:
        return
    if _refreshing:
        return
    _refreshing = True

    # 尝试东方财富 (失败后 5 分钟内跳过)
    now = time.time()
    if now - _em_fail_time > EM_RETRY_INTERVAL:
        try:
            logger.info("正在从东方财富获取 A 股实时行情...")
            stocks, source = fetch_realtime_em()
            if stocks:
                _realtime_cache = stocks
                _realtime_cache_time = time.time()
                _realtime_source = source
                _em_fail_time = 0
                logger.info("东方财富行情获取成功: %d 只股票", len(stocks))
                _refreshing = False
                return
        except Exception as e:
            _em_fail_time = time.time()
            logger.warning("东方财富行情获取失败: %s, 尝试新浪...", str(e)[:100])

    # 降级到新浪
    try:
        logger.info("正在从新浪获取 A 股实时行情...")
        stocks, source = fetch_realtime_sina()
        if stocks:
            _realtime_cache = stocks
            _realtime_cache_time = time.time()
            _realtime_source = source
            logger.info("新浪行情获取成功: %d 只股票", len(stocks))
    except Exception as e:
        logger.error("新浪行情获取也失败: %s", str(e)[:100])

    _refreshing = False


# ---------- 概念板块 ----------
def load_concept_cache():
    global _concept_to_stocks, _stock_to_concepts, _concepts_loaded, _concept_provider, _concept_updated_at
    try:
        if os.path.exists(CONCEPT_CACHE_FILE):
            with open(CONCEPT_CACHE_FILE, 'r', encoding='utf-8') as f:
                data = json.load(f)
            if data.get('source') != 'eastmoney' or data.get('version') not in (2, 3):
                logger.warning("概念缓存不是可信的真实成分数据，忽略旧缓存")
                return False
            raw_c2s = data.get('concept_to_stocks', {})
            raw_s2c = data.get('stock_to_concepts', {})
            _concept_to_stocks = {k: [normalize_code(c) for c in v if is_target_stock(c)] for k, v in raw_c2s.items()}
            _concept_to_stocks = {k: v for k, v in _concept_to_stocks.items() if v}
            _stock_to_concepts = {normalize_code(k): list(v) for k, v in raw_s2c.items() if is_target_stock(k)}
            _concepts_loaded = bool(_concept_to_stocks)
            _concept_provider = data.get('source', '')
            _concept_updated_at = data.get('updated_at', '')
            logger.info("从可信缓存加载概念板块: %d 个概念, %d 只目标股票",
                        len(_concept_to_stocks), len(_stock_to_concepts))
            return _concepts_loaded
    except Exception as e:
        logger.error("加载概念缓存失败: %s", e)
    return False


def _concept_names_em():
    return call_akshare(ak.stock_board_concept_name_em, direct=True)


def _concept_cons_em(name):
    return call_akshare(ak.stock_board_concept_cons_em, symbol=name, direct=True)


def _extract_concept_name(row):
    for key in ('板块名称', 'name', '概念名称', '概念'):
        value = row.get(key)
        if value is not None and str(value).strip() and str(value).strip().lower() != 'nan':
            return str(value).strip()
    return ''


def _extract_codes(df):
    if df is None or len(df) == 0:
        return []
    code_col = next((c for c in ('代码', 'code', '股票代码') if c in df.columns), None)
    if not code_col:
        return []
    codes = [normalize_code(c) for c in df[code_col].astype(str).tolist()]
    return list(dict.fromkeys(c for c in codes if is_target_stock(c)))


def _build_with_provider(provider, names_df):
    concept_to_stocks = {}
    stock_to_concepts = {}
    rows = list(names_df.iterrows())
    total = len(rows)
    _concept_progress.update(done=0, total=total)
    max_build = total if CONCEPT_MAX_BUILD <= 0 else min(total, CONCEPT_MAX_BUILD)
    for idx, (_, row) in enumerate(rows[:max_build]):
        concept_name = _extract_concept_name(row)
        if not concept_name:
            continue
        try:
            if provider == 'eastmoney':
                cons_df = _concept_cons_em(concept_name)
            else:
                raise RuntimeError('当前没有可靠的同花顺概念成分股接口')
            codes = _extract_codes(cons_df)
            if codes:
                concept_to_stocks[concept_name] = codes
                for code in codes:
                    stock_to_concepts.setdefault(code, []).append(concept_name)
        except Exception as e:
            logger.debug('%s 概念 %s 获取失败: %s', provider, concept_name, str(e)[:120])
        finally:
            _concept_progress['done'] = idx + 1
        if (idx + 1) % 25 == 0:
            logger.info('  %s 概念进度: %d/%d', provider, idx + 1, max_build)
    return concept_to_stocks, stock_to_concepts, max_build


def build_concept_map():
    """构建真实概念映射：仅使用 AKShare/EastMoney 概念名称+成分接口。
    不使用同花顺失效接口，也绝不按股票名称猜概念。"""
    global _concept_to_stocks, _stock_to_concepts, _concepts_loaded
    global _concepts_building, _concept_last_error, _concept_updated_at, _concept_provider
    if not AKSHARE_AVAILABLE or _concepts_building:
        return
    _concepts_building = True
    _concept_last_error = ''
    _concept_progress.update(done=0, total=0)
    try:
        errors = []
        providers = [
            ('eastmoney', _concept_names_em),
        ]
        for provider, name_func in providers:
            try:
                logger.info('尝试 %s 概念数据源...', provider)
                names_df = name_func()
                if names_df is None or len(names_df) == 0:
                    raise RuntimeError('概念名称列表为空')
                c2s, s2c, built = _build_with_provider(provider, names_df)
                if c2s:
                    _concept_to_stocks = c2s
                    _stock_to_concepts = s2c
                    _concepts_loaded = True
                    _concept_provider = provider
                    _concept_updated_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                    save_concept_cache(c2s, s2c, provider)
                    logger.info('%s 概念映射完成: %d 概念, %d 只目标股票', provider, len(c2s), len(s2c))
                    return
                errors.append('%s: 成分股为空（已处理 %d 个概念）' % (provider, built))
            except Exception as e:
                msg = str(e)[:500]
                errors.append('%s: %s' % (provider, msg))
                logger.warning('%s 概念接口失败: %s', provider, msg)
        _concept_last_error = ' | '.join(errors)[:1000]
        logger.error('所有真实概念数据源均失败；不会使用名称模糊匹配或旧样例数据')
    finally:
        _concepts_building = False


def fetch_single_concept(name):
    """按需获取单个真实概念成分，并合并进内存缓存。"""
    global _concept_to_stocks, _stock_to_concepts, _concepts_loaded
    global _concept_last_error, _concept_updated_at, _concept_provider
    name = str(name or '').strip()
    if not name or not AKSHARE_AVAILABLE:
        return []
    try:
        names_df = _concept_names_em()
        if names_df is None or len(names_df) == 0:
            raise RuntimeError('概念名称列表为空')
        names = []
        for _, row in names_df.iterrows():
            concept_name = _extract_concept_name(row)
            if concept_name:
                names.append(concept_name)
        if name not in names:
            # 允许用户输入带“概念/板块”等后缀的常见名称，只做精确规范化匹配。
            def strip_concept_suffix(value):
                value = str(value or '').strip()
                return value[:-2] if value.endswith('概念') else value
            candidates = [x for x in names if strip_concept_suffix(x) == strip_concept_suffix(name)]
            if len(candidates) != 1:
                raise RuntimeError('未找到唯一概念: %s' % name)
            name = candidates[0]
        codes = _extract_codes(_concept_cons_em(name))
        if not codes:
            raise RuntimeError('概念成分为空: %s' % name)
        _concept_to_stocks[name] = codes
        for code in codes:
            current = _stock_to_concepts.setdefault(code, [])
            if name not in current:
                current.append(name)
        _concepts_loaded = bool(_concept_to_stocks)
        _concept_provider = 'eastmoney'
        _concept_updated_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        save_concept_cache(_concept_to_stocks, _stock_to_concepts, 'eastmoney')
        _concept_last_error = ''
        return codes
    except Exception as e:
        _concept_last_error = str(e)[:1000]
        logger.warning('按需获取概念失败 %s: %s', name, _concept_last_error)
        return []


def extract_keywords(concept_name):
    """从概念名提取匹配关键词"""
    # 去掉常见后缀
    name = concept_name.replace('概念', '').replace('板块', '').replace('指数', '').strip()
    if len(name) >= 2:
        return [name, name[:2]]
    return [name] if name else []


def save_concept_cache(c2s, s2c, provider="eastmoney"):
    try:
        os.makedirs(CACHE_DIR, exist_ok=True)
        with open(CONCEPT_CACHE_FILE, 'w', encoding='utf-8') as f:
            json.dump({
                'concept_to_stocks': c2s,
                'stock_to_concepts': s2c,
                'updated_at': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                'source': provider,
                'version': 3
            }, f, ensure_ascii=False)
    except Exception as e:
        logger.error("保存概念缓存失败: %s", e)


# ---------- Flask 路由 ----------
if FLASK_AVAILABLE:

    @app.route('/health')
    def health():
        return jsonify({
            'status': 'ok',
            'akshare_available': AKSHARE_AVAILABLE,
            'realtime_loaded': len(_realtime_cache) > 0,
            'realtime_source': _realtime_source,
            'realtime_count': len(_realtime_cache),
            'concepts_loaded': _concepts_loaded,
            'concepts_building': _concepts_building,
            'concept_count': len(_concept_to_stocks),
            'stock_count': len(_stock_to_concepts),
            'concept_last_error': _concept_last_error,
            'concept_updated_at': _concept_updated_at,
            'concept_provider': _concept_provider,
            'concept_progress': _concept_progress.copy(),
            'server_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'target_universe': 'MAIN+CHINEXT+STAR'
        })

    @app.route('/api/stocks/realtime')
    def get_realtime():
        global _realtime_cache, _realtime_cache_time
        if not AKSHARE_AVAILABLE:
            return jsonify({'error': 'AKShare not available'}), 503
        # 如果缓存过期，后台异步刷新，但仍返回当前缓存数据
        if _realtime_cache and time.time() - _realtime_cache_time > REALTIME_CACHE_TTL:
            if not _refreshing:
                threading.Thread(target=refresh_realtime, daemon=True).start()
        elif not _realtime_cache:
            # 首次加载, 同步等待
            refresh_realtime()
        if not _realtime_cache:
            return jsonify({'error': 'No data available'}), 503
        return jsonify({
            'count': len(_realtime_cache),
            'source': _realtime_source,
            'updated_at': datetime.fromtimestamp(_realtime_cache_time).strftime('%Y-%m-%d %H:%M:%S'),
            'stocks': _realtime_cache
        })

    @app.route('/api/concepts')
    @app.route('/concepts')
    def get_concepts():
        if not _concepts_loaded:
            load_concept_cache()
        return jsonify({
            'status': 'ready' if _concepts_loaded else ('building' if _concepts_building else 'empty'),
            'stock_to_concepts': _stock_to_concepts,
            'concept_to_stocks': _concept_to_stocks,
            'concept_count': len(_concept_to_stocks),
            'stock_count': len(_stock_to_concepts),
            'updated_at': _concept_updated_at,
            'provider': _concept_provider,
            'progress': _concept_progress.copy(),
            'error': _concept_last_error
        })

    @app.route('/api/concept/<path:name>/stocks')
    @app.route('/concept/<path:name>/stocks')
    def get_concept_stocks(name):
        if not _concepts_loaded:
            load_concept_cache()
        stocks = _concept_to_stocks.get(name, [])
        # 全量构建尚未完成时，允许对指定概念进行一次真实按需查询。
        if not stocks and not _concepts_building and AKSHARE_AVAILABLE:
            stocks = fetch_single_concept(name)
        return jsonify({
            'status': 'ready' if stocks else ('building' if _concepts_building else 'empty'),
            'concept': name,
            'count': len(stocks),
            'stocks': stocks,
            'updated_at': _concept_updated_at,
            'provider': _concept_provider,
            'progress': _concept_progress.copy(),
            'error': _concept_last_error
        })

    @app.route('/api/concepts/refresh', methods=['POST', 'GET'])
    @app.route('/concepts/refresh', methods=['POST', 'GET'])
    def refresh_concepts():
        if _concepts_building:
            return jsonify({'status': 'already_building'})
        threading.Thread(target=build_concept_map, daemon=True, name='concept-builder').start()
        return jsonify({'status': 'refreshing_in_background'})

    @app.route('/api/stocks/refresh', methods=['POST', 'GET'])
    def refresh_stocks():
        refresh_realtime()
        return jsonify({'status': 'refreshed', 'count': len(_realtime_cache), 'source': _realtime_source})

    @app.route('/api/trend')
    def get_trend():
        keyword = request.args.get('keyword', '').strip()
        days = min(int(request.args.get('days', 60)), 365)
        if not keyword or not AKSHARE_AVAILABLE:
            return jsonify({'keyword': keyword, 'concept': '', 'points': []})

        # 查找匹配的概念
        matching = [c for c in _concept_to_stocks.keys() if keyword in c]
        if not matching:
            try:
                ths_df = ak.stock_board_concept_name_ths()
                for _, row in ths_df.iterrows():
                    cn = str(row['name'])
                    if keyword in cn and cn not in matching:
                        matching.append(cn)
            except:
                pass
        if not matching:
            return jsonify({'keyword': keyword, 'concept': '', 'points': []})

        concept = matching[0]

        # 尝试东方财富概念历史
        try:
            end_date = datetime.now().strftime('%Y%m%d')
            start_date = (datetime.now() - timedelta(days=days + 15)).strftime('%Y%m%d')
            df = ak.stock_board_concept_hist_em(symbol=concept, period='daily',
                                                start_date=start_date, end_date=end_date, adjust='')
            points = parse_hist_points(df)
            if points:
                return jsonify({'keyword': keyword, 'concept': concept, 'matched_concepts': matching[:10], 'points': points[-days:]})
        except Exception as e:
            logger.warning("东方财富概念历史失败: %s", str(e)[:100])

        # 降级: 取概念成分股的最新行情计算简单趋势
        try:
            codes = _concept_to_stocks.get(concept, [])
            if codes and _realtime_cache:
                # 返回最近一个交易日的数据 (实时数据无法给历史趋势)
                # 使用成分股涨跌信息作为当日快照
                today_str = datetime.now().strftime('%Y-%m-%d')
                up_count = sum(1 for s in _realtime_cache if s['code'] in codes and s['changePct'] > 0)
                total = len(codes)
                ratio = (up_count * 100.0 / total) if total > 0 else 0
                return jsonify({
                    'keyword': keyword,
                    'concept': concept,
                    'matched_concepts': matching[:10],
                    'points': [{'date': today_str, 'count': up_count, 'ratio': ratio, 'close': 0}],
                    'note': 'Only latest day available (EM history unavailable)'
                })
        except:
            pass

        return jsonify({'keyword': keyword, 'concept': concept, 'points': []})


@app.route('/api/quant/health')
def quant_health():
    data = {
        'status': 'ok' if QUANT_AVAILABLE else 'unavailable',
        'quant_core': QUANT_AVAILABLE,
        'model': 'baseline-v0',
        'model_type': 'transparent-rule-score',
        'trained': False,
        'feature_schema': 'event+name+concept+market-v1',
        'pipeline': 'market->feature->dataset->alpha->backtest',
    }
    if QUANT_AVAILABLE:
        data['qlib'] = qlib_status()
    else:
        data['error'] = _QUANT_IMPORT_ERROR
    return jsonify(data)


@app.route('/api/quant/features')
def quant_features():
    if not QUANT_AVAILABLE:
        return jsonify({'status': 'unavailable', 'error': _QUANT_IMPORT_ERROR}), 503
    code = request.args.get('code', '').strip()
    if not code:
        return jsonify({'status': 'error', 'error': 'code is required'}), 400
    stock = next((x for x in _realtime_cache if x.get('code') == code), None)
    if stock is None:
        return jsonify({'status': 'empty', 'code': code, 'source': _realtime_source}), 404
    features = build_feature_vector(
        stock,
        event_strength=float(request.args.get('event_strength', 0)),
        event_confidence=float(request.args.get('event_confidence', 0)),
        event_direction=float(request.args.get('event_direction', 0)),
        name_resonance=float(request.args.get('name_resonance', 0)),
        concept_heat=float(request.args.get('concept_heat', 0)),
    )
    features['alpha_score'] = baseline_alpha(features)
    features['as_of'] = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    features['market_source'] = _realtime_source
    return jsonify({'status': 'ok', 'features': features})


@app.route('/api/quant/history')
def quant_history():
    """Fetch real historical daily OHLCV for one A-share stock."""
    if not AKSHARE_AVAILABLE:
        return jsonify({'status': 'unavailable', 'error': 'AKShare is unavailable'}), 503
    code = normalize_code(request.args.get('code', '').strip())
    if not code or not is_target_stock(code):
        return jsonify({'status': 'error', 'error': 'target A-share code is required'}), 400
    end_date = request.args.get('end_date', datetime.now().strftime('%Y%m%d'))
    start_date = request.args.get('start_date', (datetime.now() - timedelta(days=365 * 3)).strftime('%Y%m%d'))
    adjust = request.args.get('adjust', 'qfq')
    try:
        df = ak.stock_zh_a_hist(symbol=code, period='daily', start_date=start_date,
                                end_date=end_date, adjust=adjust)
        rows = []
        for _, row in df.iterrows():
            d = row.get('日期', '')
            d = d.strftime('%Y-%m-%d') if hasattr(d, 'strftime') else str(d)[:10]
            close = safe_float(row.get('收盘'))
            open_ = safe_float(row.get('开盘'))
            high = safe_float(row.get('最高'))
            low = safe_float(row.get('最低'))
            volume = safe_float(row.get('成交量'))
            prev_close = safe_float(row.get('昨收'))
            change = safe_float(row.get('涨跌幅'))
            rows.append({
                'date': d, 'code': code, 'open': open_, 'high': high, 'low': low,
                'close': close, 'volume': volume, 'amount': safe_float(row.get('成交额')),
                'change_pct': change, 'prev_close': prev_close,
                'intraday_range': ((high - low) / close) if close > 0 else 0.0,
                'open_gap': ((open_ - prev_close) / prev_close) if prev_close > 0 else 0.0,
            })
        return jsonify({'status': 'ok', 'source': 'AKShare/EastMoney', 'code': code,
                        'start_date': start_date, 'end_date': end_date, 'adjust': adjust,
                        'rows': rows})
    except Exception as exc:
        return jsonify({'status': 'network_error', 'code': code, 'error': str(exc)}), 502


@app.route('/api/quant/dataset', methods=['POST'])
def quant_dataset():
    if not QUANT_AVAILABLE:
        return jsonify({'status': 'unavailable', 'error': _QUANT_IMPORT_ERROR}), 503
    payload = request.get_json(silent=True) or {}
    rows = payload.get('rows')
    if not isinstance(rows, list):
        return jsonify({'status': 'error', 'error': 'rows must be a list of point-in-time feature rows'}), 400
    try:
        dataset = build_supervised_dataset(rows, horizon=int(payload.get('horizon', 5)))
        return jsonify({'status': 'ok', 'feature_columns': [
            'event_strength', 'event_confidence', 'event_direction', 'name_resonance',
            'concept_heat', 'change_pct', 'volume_ratio', 'intraday_range', 'open_gap'
        ], 'rows': dataset})
    except Exception as exc:
        return jsonify({'status': 'error', 'error': str(exc)}), 400


@app.route('/api/quant/train', methods=['POST'])
def quant_train():
    if not QUANT_AVAILABLE:
        return jsonify({'status': 'unavailable', 'error': _QUANT_IMPORT_ERROR}), 503
    payload = request.get_json(silent=True) or {}
    rows = payload.get('rows')
    if not isinstance(rows, list):
        return jsonify({'status': 'error', 'error': 'rows must be a list of supervised samples'}), 400
    result = train_lightgbm(rows)
    return jsonify(result)


@app.route('/api/quant/backtest', methods=['POST'])
def quant_backtest():
    if not QUANT_AVAILABLE:
        return jsonify({'status': 'unavailable', 'error': _QUANT_IMPORT_ERROR}), 503
    payload = request.get_json(silent=True) or {}
    rows = payload.get('rows')
    if not isinstance(rows, list):
        return jsonify({'status': 'error', 'error': 'rows must be a list of point-in-time bars with signal'}), 400
    result = backtest_long_only(
        rows,
        initial_cash=float(payload.get('initial_cash', 100000)),
        score_threshold=float(payload.get('score_threshold', 0)),
        fee_rate=float(payload.get('fee_rate', 0.0003)),
        slippage=float(payload.get('slippage', 0.0005)),
    )
    return jsonify(result)



def parse_hist_points(df):
    points = []
    for _, row in df.iterrows():
        d = row.get('日期', '')
        if hasattr(d, 'strftime'):
            d = d.strftime('%Y-%m-%d')
        else:
            d = str(d)[:10]
        points.append({
            'date': d,
            'count': safe_int(row.get('成交量')),
            'ratio': safe_float(row.get('涨跌幅')),
            'close': safe_float(row.get('收盘'))
        })
    return points


def init():
    if not FLASK_AVAILABLE:
        logger.error("Flask 不可用")
        return
    if not AKSHARE_AVAILABLE:
        logger.warning("AKShare 不可用")
    else:
        # 后台只加载实时行情和已有概念缓存。
        # 不在启动时自动执行 375 个概念的全量成分查询，避免启动阶段长时间占用网络，
        # 也避免用户首次查询具体概念时被全量构建“卡住”。具体概念由 /concept/{name}/stocks 按需真实拉取，
        # 用户需要全量数据时再调用 /concepts/refresh。
        def background_init():
            refresh_realtime()
            load_concept_cache()
        threading.Thread(target=background_init, daemon=True, name='bridge-init').start()


if __name__ == '__main__':
    init()
    if FLASK_AVAILABLE:
        port = int(os.environ.get('AKSHARE_PORT', '5555'))
        app.run(host='127.0.0.1', port=port, debug=False, threaded=True)
    else:
        logger.error("无法启动: Flask 未安装")
        sys.exit(1)
