import unittest
from signa_core.quant import board_type, limit_pct, build_feature_vector, baseline_alpha, backtest_long_only

class QuantTests(unittest.TestCase):
    def test_board_rules(self):
        self.assertEqual(board_type('300750'), 'CHINEXT')
        self.assertEqual(limit_pct('688001'), 0.20)
        self.assertEqual(limit_pct('600000', 'ST测试'), 0.10)

    def test_feature_and_alpha(self):
        f = build_feature_vector({'code':'300750','name':'测试','open':10,'close':11,'high':11.2,'low':9.9,'volume':100,'amount':1000}, event_strength=.8, event_confidence=.9, event_direction=1, name_resonance=.7, concept_heat=.6)
        self.assertEqual(f['board'], 'CHINEXT')
        self.assertGreater(baseline_alpha(f), 0)

    def test_no_future_price_execution(self):
        rows = [
            {'date':'2026-01-01','open':10,'close':10,'signal':1},
            {'date':'2026-01-02','open':11,'close':12,'signal':0},
            {'date':'2026-01-03','open':9,'close':10,'signal':0},
        ]
        r = backtest_long_only(rows)
        self.assertEqual(r['execution'], 'next_open')
        self.assertEqual(r['trades'], 2)

if __name__ == '__main__':
    unittest.main()
