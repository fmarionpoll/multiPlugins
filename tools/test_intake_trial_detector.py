import unittest
import numpy as np
from intake_trial_detector import fit_change, image_support, stationary_dark_rows, choose, allocate, evaluate


def image_for(levels):
    rows = np.arange(120)[:, None]
    edge = 1 / (1 + np.exp(np.clip(-(rows - levels[None, :]), -50, 50)))
    a, b = np.array([225, 235, 225]), np.array([125, 190, 175])
    return (a + edge[:, :, None] * (b - a)).astype(np.uint8)


class TrialDetectorTest(unittest.TestCase):
    def setUp(self):
        self.time = np.arange(100, dtype=float)
        self.background = 20 + .04 * self.time + .0008 * self.time ** 2
        self.valid = np.ones(100, dtype=bool)

    def test_persistent_step_on_smooth_drift(self):
        raw = self.background + 2 * (self.time >= 50)
        candidate = fit_change(raw, self.time, self.valid, 50, 50, .15)
        self.assertAlmostEqual(candidate.amount, 2, places=8)
        self.assertGreater(candidate.score, 5)
        rgb = image_for(raw)
        correlation, contrast, border = image_support(rgb, raw, self.valid, 50, 50, stationary_dark_rows(rgb))
        self.assertGreater(correlation, .8)
        self.assertGreater(contrast, 10)
        self.assertFalse(border)

    def test_eight_interval_burst_and_conserved_allocation(self):
        raw = self.background + 4 * np.clip((self.time - 44) / 8, 0, 1)
        candidate = fit_change(raw, self.time, self.valid, 45, 52, .15)
        self.assertAlmostEqual(candidate.amount, 4, places=8)
        allocation = allocate(candidate, {"levels": raw, "times": self.time})
        self.assertEqual(len(allocation), 8)
        self.assertAlmostEqual(float(allocation.sum()), 4, places=8)
        self.assertTrue(np.all(allocation >= 0))

    def test_smooth_drift_and_one_frame_excursion_are_not_persistent_steps(self):
        quiet = fit_change(self.background, self.time, self.valid, 50, 50, .15)
        self.assertLess(abs(quiet.amount), 1e-8)
        pulse = self.background.copy(); pulse[50] += 2
        transient = fit_change(pulse, self.time, self.valid, 50, 50, .15)
        self.assertLess(transient.score, 5)

    def test_uncertain_boundary_can_use_reliable_flanks(self):
        raw = self.background + 2 * (self.time >= 50)
        self.valid[50] = False
        candidate = fit_change(raw, self.time, self.valid, 50, 50, .15)
        self.assertAlmostEqual(candidate.amount, 2, places=8)
        raw[50] = np.nan
        self.assertIsNone(fit_change(raw, self.time, self.valid, 50, 50, .15))

    def test_stationary_dark_band_is_flagged_and_blocked(self):
        raw = self.background.copy(); raw[50:] = 70
        rgb = image_for(raw); rgb[68:73, :] = 15
        dark = stationary_dark_rows(rgb)
        self.assertTrue(dark[70])
        correlation, contrast, border = image_support(rgb, raw, self.valid, 50, 50, dark)
        self.assertTrue(border)
        candidate = fit_change(raw, self.time, self.valid, 50, 50, .15)
        candidate.image_correlation = 1.; candidate.image_contrast = 50.; candidate.border_confounded = True
        accepted, _ = choose([candidate], {"1": 5.}, np.zeros(100))
        self.assertFalse(accepted)
        self.assertEqual(candidate.reason, "stationary_dark_band_near_track")

    def test_existing_automatic_events_are_preserved(self):
        raw = self.background + 2 * (self.time >= 50)
        candidate = fit_change(raw, self.time, self.valid, 50, 50, .15)
        candidate.image_correlation = 1.; candidate.image_contrast = 50.
        baseline = np.zeros(100); baseline[20] = 1.5
        accepted, _ = choose([candidate], {"1": 5.}, baseline)
        rows = [{"edit_action": "added" if i == 50 else "unchanged", "manual_override": "true" if i == 50 else "false", "edited_net_intake_px": "2" if i == 50 else "0"} for i in range(100)]
        result = evaluate({"experiment": "006", "cap": "1L", "control": False},
                          {"baseline": baseline, "rows": rows, "levels": raw, "times": self.time}, accepted)
        self.assertEqual(result["trial"][20], 1.5)
        self.assertAlmostEqual(result["trial"][50], 2, places=8)
        self.assertEqual(result["baseline_bins_lost"], 0)

    def test_distant_step_cannot_supply_an_incorrect_boundary(self):
        raw = self.background + 2 * (self.time >= 50)
        candidate = fit_change(raw, self.time, self.valid, 47, 47, .15)
        candidate.image_correlation = 1.; candidate.image_contrast = 50.
        accepted, _ = choose([candidate], {"1": 5.}, np.zeros(100))
        self.assertFalse(accepted)

    def test_abrupt_step_is_not_reported_as_a_long_episode(self):
        raw = self.background + 4 * (self.time >= 50)
        candidate = fit_change(raw, self.time, self.valid, 46, 53, .15)
        candidate.image_correlation = 1.; candidate.image_contrast = 50.
        accepted, _ = choose([candidate], {"8": 5.}, np.zeros(100))
        self.assertFalse(accepted)


if __name__ == "__main__":
    unittest.main()
