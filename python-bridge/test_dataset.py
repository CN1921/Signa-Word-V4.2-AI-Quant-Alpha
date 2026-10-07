import unittest
from signa_core.dataset import build_supervised_dataset, split_time_series, FEATURE_COLUMNS


class DatasetTests(unittest.TestCase):
    def rows(self):
        return [
            {'date': f'2026-01-{i:02d}', 'code': '600000', 'close': float(i),
             'event_strength': .5, 'event_confidence': .8, 'event_direction': 1,
             'name_resonance': .2, 'concept_heat': .3, 'change_pct': .1,
             'volume_ratio': 1.2, 'intraday_range': .03, 'open_gap': .01}
            for i in range(1, 8)
        ]

    def test_point_in_time_label(self):
        ds = build_supervised_dataset(self.rows(), horizon=2)
        self.assertEqual(len(ds), 5)
        self.assertAlmostEqual(ds[0]['forward_return'], 3.0 / 1.0 - 1.0)
        self.assertEqual(set(FEATURE_COLUMNS).issubset(ds[0]), True)

    def test_time_split_no_shuffle(self):
        ds = build_supervised_dataset(self.rows(), horizon=1)
        train, test = split_time_series(ds, .6)
        self.assertLess(train[-1]['date'], test[0]['date'])


if __name__ == '__main__':
    unittest.main()
