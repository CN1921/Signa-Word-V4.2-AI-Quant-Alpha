# Signa-Word V3.1 — A股 AI Quant

Signa-Word 已从早期的“股票名称/关键词统计”与“事件驱动分析”演进为 **A股 AI 量化研究与选股系统**。

项目的核心目标不是单独判断新闻情绪，而是把：

**市场数据 + 新闻/NLP + 事件抽取 + 股票/概念映射 + 名称语义共振**

统一转换为可回测、可训练、可解释的 **Alpha / Feature / Signal**，最终服务于 AI 量化选股、组合和交易研究。

> V3.1 的 AI 量化模型目前仍处于基础设施阶段。项目不会伪造训练结果，也不会用未来数据生成当前信号。

---

## 1. 总体路线

```text
                 Signa-Word AI Quant
                         │
        ┌────────────────┼────────────────┐
        │                │                │
   Market Engine     News Engine     Stock Universe
        │                │                │
        └────────────────┼────────────────┘
                         ↓
                    NLP Engine
                         ↓
                  Event Extraction
                         ↓
                Financial Event Model
                         ↓
             ┌───────────┴───────────┐
             ↓                       ↓
       Stock / Concept Map      Name Resonance
             │                       │
             └───────────┬───────────┘
                         ↓
                  Feature Engine
                         ↓
                   Alpha Engine
                         ↓
              ML / Qlib / Future RL
                         ↓
                 Signal / Portfolio
                         ↓
                    Backtest
                         ↓
             Walk-Forward / OOS Test
                         ↓
                 AI Stock Selection
```

### 核心原则

1. **量化从 V3.1 开始就是一级模块**，不是后置功能。
2. NLP 和事件抽取是 Alpha 特征来源，而不是系统最终目的。
3. 股票名称、人物、数字、品牌、热点词等“名称共振”必须转化为可量化 Feature。
4. 所有 Feature 和 Signal 遵守 **Point-in-Time** 原则。
5. 回测必须考虑 A 股实际交易规则、成交时点、手续费、滑点和涨跌停限制。
6. 没有历史训练数据时，使用透明基线，不伪造 AI 模型结果。
7. Qlib / LightGBM 是量化能力供应层，Signa-Word 自己保留事件、名称、股票映射和业务逻辑。

---

## 2. 当前 V3.1 已实现

### 2.1 A 股市场层

- A 股目标股票池：主板 + 创业板 + 科创板
- 新浪实时行情
- 当前真实实时股票数量约 4750（随市场数据变化）
- 主板普通股 ±10%
- ST/*ST ±10%
- 创业板 ±20%
- 科创板 ±20%
- 基础交易执行模型
- Point-in-Time 时间约束

### 2.2 真实概念数据

使用 AKShare / 东方财富概念接口：

- `stock_board_concept_name_em`
- `stock_board_concept_cons_em`

概念采用**按需真实拉取 + 本地缓存**，不在启动时强制抓取全部概念。

概念接口失败时：

```text
network_error ≠ empty
```

网络失败不会被伪装成“没有成分股”。Java 层也不再生成模拟概念关系。

### 2.3 Quant Core

`python-bridge/signa_core/quant.py`

已经包含：

- A 股板块规则
- Feature Vector
- 事件特征接口
- 名称共振特征接口
- 概念热度特征接口
- 市场行情特征
- Alpha V0 透明基线
- next-open 执行
- 手续费
- 滑点
- 收益率
- 最大回撤
- 交易次数

### 2.4 Historical Market Data

V3.1 新增真实历史行情入口：

```text
GET /api/quant/history
```

例如：

```text
/api/quant/history?code=600519&start_date=20240101&end_date=20261006&adjust=qfq
```

数据来自 AKShare/EastMoney，支持日线 OHLCV、成交额、涨跌幅等字段。

### 2.5 Feature / Alpha Dataset

`python-bridge/signa_core/dataset.py`

当前 Feature Schema：

```text
Event
├── event_strength
├── event_confidence
└── event_direction

Name
└── name_resonance

Concept
└── concept_heat

Market
├── change_pct
├── volume_ratio
├── intraday_range
└── open_gap
```

后续还会继续加入：

```text
Momentum
Volatility
Liquidity
Fund Flow
Industry
Market Regime
Technical Factors
Event Decay
Event Frequency
```

### 2.6 Point-in-Time Dataset

接口：

```text
POST /api/quant/dataset
```

输入当前时点 Feature，自动生成未来收益 Label，例如：

```text
T
 ↓
Feature(T)
 ↓
Future Close(T+5)
 ↓
Forward Return(T+5)
```

Feature 不读取未来价格，Label 才允许使用未来价格。

默认 Label：

```text
forward_return = Close(T+5) / Close(T) - 1
```

### 2.7 LightGBM

V3.1 已提供可选 LightGBM 训练入口：

```text
POST /api/quant/train
```

当前采用时间顺序切分：

```text
历史数据
   ↓
Train
   ↓
Test
```

**不随机打乱金融时间序列。**

LightGBM 没有安装时，系统明确返回 `unavailable`，不会伪造模型结果。

### 2.8 Qlib Adapter

```text
python-bridge/signa_core/qlib_adapter.py
```

Qlib 是可选依赖。

Qlib 在整体架构中的角色：

```text
Signa-Word Feature
        ↓
Qlib Dataset / Alpha
        ↓
Forecast Model
        ↓
Portfolio Strategy
        ↓
Backtest
        ↓
Risk / Report
```

Qlib 官方定位就是 AI-oriented quantitative investment platform，并覆盖 Data、Forecast Model、Portfolio/Backtest、RL、Workflow 等组件。其模块是松耦合设计，因此 Signa-Word 采用 Adapter，而不是把整个项目改造成 Qlib 项目。

---

## 3. API

### 市场

```text
GET /api/stocks/realtime
GET /api/status
```

### 概念

```text
GET /api/concepts
GET /api/concept/{name}/stocks
GET /api/concepts/refresh
```

### AI Quant

```text
GET  /api/quant/health
GET  /api/quant/features?code=600519
GET  /api/quant/history?code=600519&start_date=20240101&end_date=20261006&adjust=qfq
POST /api/quant/dataset
POST /api/quant/train
POST /api/quant/backtest
```

### Quant Health

```text
GET /api/quant/health
```

返回的信息包括：

- Quant Core 是否可用
- 当前模型状态
- Feature Schema
- Qlib 是否安装
- Qlib 版本

V3.1 默认模型：

```text
baseline-v0
```

它是透明规则基线，不是训练完成的 AI 模型。

---

## 4. 为什么 NLP / 事件抽取仍然是核心

虽然项目定位已经升级为 AI Quant，但 NLP 和事件抽取没有被删除，反而成为独特 Alpha 来源。

例如：

```text
新闻
 ↓
NER
 ↓
事件抽取
 ↓
事件类型
事件主体
事件客体
事件触发词
事件方向
事件强度
 ↓
Stock Mapping
 ↓
Name Resonance
 ↓
Feature Vector
 ↓
AI Alpha
```

例如：

```text
“某公司拟收购 XX 公司 100% 股权”
```

应该最终形成类似：

```json
{
  "event_type": "并购重组",
  "trigger": "收购",
  "acquirer": "某公司",
  "target": "XX公司",
  "event_direction": 1,
  "event_strength": 0.86
}
```

这些不是最终答案，而是进入 AI Quant 的特征。

---

## 5. 名称共振为什么也是 Quant Factor

Signa-Word 与普通量化系统最大的区别之一，就是研究：

```text
新闻事件
   ↓
关键词 / 人物 / 地名 / 数字 / 品牌 / 热词
   ↓
股票名称
   ↓
语义 / 谐音 / 数字 / 字符 / 热点关联
   ↓
Name Resonance Score
```

例如：

```text
事件：某公众人物热点
        ↓
关键词：XXX
        ↓
股票名称：XXX科技
        ↓
Name Resonance = 0.91
```

随后 AI 模型自己学习：

> 这种共振到底有没有未来收益预测能力。

因此名称共振不是一个 UI 小功能，而是一个潜在 Alpha Factor。

---

## 6. AI Quant 发展路线

### V3.1 — Quant Foundation

当前阶段：

```text
真实行情
 ↓
交易规则
 ↓
Point-in-Time Feature
 ↓
Alpha V0
 ↓
Historical Dataset
 ↓
LightGBM / Qlib Adapter
 ↓
基础 Backtest
```

### V3.2 — NLP + Event Intelligence

```text
News
 ↓
NLP
 ↓
NER
 ↓
Event Extraction
 ↓
Financial Event Schema
```

### V3.3 — Alpha Feature Engine

```text
Event Factor
+
Name Resonance Factor
+
Concept Factor
+
Market Factor
+
Technical Factor
+
Liquidity Factor
        ↓
Feature Vector
```

### V3.4 — AI Alpha + Walk Forward

```text
Feature Vector
 ↓
LightGBM / Qlib Model
 ↓
Alpha Score
 ↓
Walk Forward
 ↓
Out-of-Sample
 ↓
Backtest
```

### V4 — Portfolio / Risk / AI Selection

```text
Alpha
 ↓
Risk Model
 ↓
Portfolio Optimization
 ↓
Position Sizing
 ↓
Execution
 ↓
AI Stock Selection
```

### 后续 — RL / Agent

```text
Market State
 ↓
AI Agent
 ↓
Action
 ↓
Execution
 ↓
Reward
 ↓
Policy Update
```

强化学习不是 V3.1 的重点，必须在监督学习 Alpha、组合和回测体系稳定以后再进入。

---

## 7. 回测原则

回测必须遵守：

### 时间

```text
新闻发布时间
事件识别时间
信号生成时间
成交时间
```

必须能够区分。

### 成交

基础策略默认：

```text
T 日产生 Signal
        ↓
T+1 Open 执行
```

避免使用信号产生以后才能知道的价格。

### A 股规则

```text
主板       ±10%
ST/*ST     ±10%
创业板     ±20%
科创板     ±20%
```

并逐步加入：

```text
T+1
涨跌停无法成交
停牌
复牌
手续费
印花税
滑点
最小交易单位
流动性限制
```

### 模型验证

禁止只看一次回测。

最终采用：

```text
Train
 ↓
Validation
 ↓
Walk Forward
 ↓
Out-of-Sample
 ↓
最终评估
```

核心指标包括：

```text
Return
Excess Return
IC
Rank IC
ICIR
Sharpe
Information Ratio
Max Drawdown
Win Rate
Turnover
Cost
```

Qlib 官方 benchmark 同样采用 Alpha 与未来收益相关性、组合收益以及 IC、ICIR、年化收益、Information Ratio、最大回撤等指标评估模型。citeturn0search0turn0search5

---

## 8. 安装

### 基础运行

```powershell
cd python-bridge
pip install -r requirements.txt
python akshare_server.py
```

### AI Quant 可选依赖

```powershell
cd python-bridge
pip install -r requirements-quant.txt
```

包括：

```text
LightGBM
Qlib
```

不安装也不影响基础行情和 Quant Foundation 运行。

---

## 9. Windows 启动

推荐：

```powershell
.\start.bat
```

或：

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean package
```

注意 PowerShell 中 Maven Wrapper 使用：

```powershell
.\mvnw.cmd
```

而不是：

```powershell
mvnw.cmd
```

---

## 10. 当前测试

V3.1 Python Quant / Dataset 测试覆盖：

- A 股板块涨跌停规则
- Feature Vector
- Alpha V0
- next-open 无未来价格执行
- Point-in-Time Label
- 时间序列切分

运行：

```powershell
python -m unittest discover -s python-bridge -p "test_*.py" -v
```

---

## 11. 技术栈

### Java

- Java
- Spring Boot
- REST API
- JPA
- Redis（可选）
- MySQL（可选）

### Python

- Python
- AKShare
- Flask
- Pandas
- Quant Core
- LightGBM（可选）
- Qlib（可选）

### AI / Quant

```text
NLP
Event Extraction
Feature Engineering
Alpha Research
Machine Learning
Backtest
Portfolio
Risk
Reinforcement Learning（后续）
```

---

## 12. 设计边界

Signa-Word 不会把所有能力都自己重新实现。

```text
AKShare       → 市场数据
NLP项目       → NLP能力
事件抽取项目   → Event Extraction能力
LightGBM      → 第一阶段 ML Alpha
Qlib          → AI Quant Workflow
Signa-Word    → 事件、名称、股票、Feature、Signal与业务逻辑
```

外部项目采用 Adapter / Capability Provider 接入，避免复制整个第三方工程进入核心代码。

---

## 13. 当前项目最重要的研究问题

最终不是：

> “某个新闻是不是利好？”

而是：

> **“事件、名称共振、概念、市场状态等信息，能不能形成稳定、可重复、Out-of-Sample 的 Alpha？”**

如果不能，就降低权重或删除因子。

如果能够持续产生超额收益，再进入组合和实盘研究。

这就是 Signa-Word 从事件分析项目升级为 AI Quant 项目的核心。

---

## 14. 免责声明

本项目仅用于软件开发、量化研究和技术学习，不构成任何投资建议。历史回测结果不代表未来收益。
