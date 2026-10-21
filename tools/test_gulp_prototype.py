"""Numerical checks for the exploratory detector; no experiment data needed."""
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from gulp_prototype import events, fit_evaporation, measures, refine_from_image, step_scores


class GulpPrototypeTest(unittest.TestCase):
    def test_evaporation_recovers_known_curve(self):
        t = np.arange(141)
        expected = 20 * (1 - np.exp(-t / 75))
        model, fit = fit_evaporation(expected)
        np.testing.assert_allclose(model, expected, atol=.01)
        self.assertAlmostEqual(fit['tau_samples'], 75, delta=.2)

    def test_evaporation_only_is_silent(self):
        t = np.arange(100)
        ref = 15 * (1 - np.exp(-t / 60))
        scores, _, _ = step_scores(30 + ref, ref)
        self.assertEqual(events(scores, .01), [])

    def test_known_step_timing_and_amplitude(self):
        t = np.arange(100)
        ref = 15 * (1 - np.exp(-t / 60))
        y = 30 + ref + (t >= 40) * 2.5
        scores, amplitude, _ = step_scores(y, ref)
        self.assertEqual(events(scores, 1), [40])
        self.assertAlmostEqual(amplitude[40], 2.5, places=8)

    def test_isolated_spike_is_rejected(self):
        y = np.zeros(100)
        y[40] = 10
        scores, _, _ = step_scores(y, np.zeros(100))
        self.assertEqual(events(scores, .1), [])

    def test_gap_is_not_bridged(self):
        y = np.zeros(100)
        y[40:] = 5
        y[39:42] = np.nan
        scores, _, widths = step_scores(y, np.zeros(100))
        self.assertEqual(events(scores, .1), [])
        self.assertTrue(np.all(widths[39:43] == 0))

    def test_image_fit_recovers_fractional_boundary(self):
        rows = np.arange(60)[:, None]
        centers = np.array([20.2, 20.7, 21.3, 22.8])
        fraction = 1 / (1 + np.exp(-(rows - centers) / 1.))
        rgb = np.array([150., 155., 145.]) + fraction[:, :, None] * np.array([-65., -5., -5.])
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parent) as tmp:
            path = Path(tmp) / 'synthetic.tiff'
            Image.fromarray(np.round(rgb).astype('uint8')).save(path)
            fitted, quality = refine_from_image(path, np.round(centers))
        np.testing.assert_allclose(fitted, centers, atol=.15)
        self.assertFalse(any(quality['constrained']))

    def test_parser_checks_point_count(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parent) as tmp:
            path = Path(tmp) / 'measures.csv'
            path.write_text('#;TOPRAW;columns=;name;index;npts;yi\n1L;2;3;4;5;\n')
            with self.assertRaises(ValueError):
                measures(path)


if __name__ == '__main__':
    unittest.main()
