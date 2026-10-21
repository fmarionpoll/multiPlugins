import unittest

import numpy as np

from gulp_correlation import normalized_correlation, anchored_refinement, register_image


class CorrelationTest(unittest.TestCase):
    @staticmethod
    def profile(shift=0.):
        rows = np.arange(90)
        return 10 + 40 / (1 + np.exp(-(rows - 40 - shift) / 1.4))

    def test_positive_and_negative_fractional_motion(self):
        for shift in (-2.3, -.7, 0., .6, 1.4, 4.2):
            with self.subTest(shift=shift):
                r = normalized_correlation(self.profile(), self.profile(shift), 40.)
                self.assertAlmostEqual(r['shift'], shift, delta=.15)
                self.assertGreater(r['correlation'], .99)

    def test_brightness_and_contrast_changes(self):
        r = normalized_correlation(self.profile(), 2 * self.profile(1.4) + 30, 40.)
        self.assertAlmostEqual(r['shift'], 1.4, delta=.15)

    def test_border_and_flat_profiles_are_unscorable(self):
        self.assertIsNone(normalized_correlation(self.profile(), self.profile(), 8.))
        self.assertIsNone(normalized_correlation(np.ones(90), np.ones(90), 40.))

    def test_search_limit_is_flagged(self):
        r = normalized_correlation(self.profile(), self.profile(8.), 40.)
        self.assertTrue(r['at_limit'])

    def test_sharp_step_preserved_with_noisy_anchors(self):
        truth = np.zeros(80)
        truth[40:] = 4.
        noisy = truth + .4 * np.sin(np.arange(80) * 2)
        records = [dict(frame=t, accepted=True, shift=truth[t] - truth[t-1]) for t in range(1, 80)]
        fitted = anchored_refinement(noisy, records)
        self.assertAlmostEqual(fitted[40] - fitted[39], 4., delta=.05)
        self.assertLess(np.sqrt(np.mean((fitted - truth) ** 2)), .1)

    def test_no_motion_evidence_retains_raw(self):
        raw = np.arange(20, dtype=float)
        np.testing.assert_allclose(anchored_refinement(raw, []), raw, atol=1e-10)

    def test_known_color_movie_and_anchored_reconstruction(self):
        centers = 40 + np.arange(25) * .15
        centers[12:] += 3.
        rows = np.arange(90)[:, None]
        f = 1 / (1 + np.exp(-(rows - centers) / 1.4))
        rgb = np.array([150., 155., 145.]) + f[:, :, None] * np.array([-65., -5., -5.])
        raw = np.round(centers)
        records = register_image(rgb, raw)
        self.assertTrue(all(r['accepted'] for r in records))
        refined = anchored_refinement(raw, records)
        self.assertLess(np.sqrt(np.mean((refined - centers) ** 2)), .15)
        self.assertAlmostEqual(refined[12] - refined[11], 3.15, delta=.2)


if __name__ == '__main__':
    unittest.main()
