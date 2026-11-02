"""Check the uncertainty estimator against signals whose noise is known."""
import unittest
import numpy as np
from estimate_intake_measurement_noise import estimate, eligible_points, illumination, rank_correlation


class MeasurementNoiseTest(unittest.TestCase):
    def test_independent_noise_on_drift(self):
        rng=np.random.default_rng(720)
        t=np.arange(10000,dtype=float)
        y=40+.03*t+rng.normal(0,.2,len(t))
        r,_=estimate(y,t,np.ones(len(t),bool))
        self.assertAlmostEqual(r['positionSigma'],.2,delta=.025)
        self.assertAlmostEqual(r['differenceSigma'],.2,delta=.025)

    def test_constant_drift_and_offset_do_not_become_noise(self):
        t=np.arange(150,dtype=float)
        r,_=estimate(100+.17*t,t,np.ones(len(t),bool))
        self.assertLess(r['positionSigma'],1e-10)
        self.assertLess(r['differenceSigma'],1e-10)

    def test_feeding_mask_preserves_controls_and_excludes_both_sides(self):
        y=np.full(40,30.);e=np.zeros(40,bool);e[20]=True
        fly=eligible_points(y,100,e,False,2)
        control=eligible_points(y,100,e,True,2)
        self.assertFalse(fly[17:23].any())
        self.assertTrue(fly[16] and fly[23])
        self.assertTrue(control.all())

    def test_missing_points_are_not_bridged(self):
        t=np.arange(40,dtype=float);y=30+.1*t;mask=np.ones(40,bool);mask[18:25]=False
        r,s=estimate(y,t,mask)
        self.assertFalse(s[18:25].any())
        self.assertTrue(all(b['last']<18 or b['first']>=25 for b in r['blocks']))

    def test_correlated_errors_make_difference_estimate_too_small(self):
        rng=np.random.default_rng(34);e=rng.normal(size=15000)
        for i in range(1,len(e)):e[i]=.8*e[i-1]+.6*e[i]
        t=np.arange(len(e),dtype=float)
        r,_=estimate(30+.01*t+e,t,np.ones(len(t),bool))
        self.assertGreater(r['residualLag1'],.3)
        self.assertLess(r['differenceSigma'],r['positionSigma']*.8)

    def test_digital_brightness_and_contrast_are_separate(self):
        rgb=np.full((60,30,3),100.);rgb[30:,:,:]=140
        r=illumination(rgb,np.full(30,30.),np.ones(30,bool))
        self.assertAlmostEqual(r['contrast']['median'],40.)
        self.assertGreater(r['brightness']['median'],100.)
        self.assertLess(r['brightness']['median'],140.)

    def test_ranks_handle_ties(self):
        self.assertAlmostEqual(rank_correlation([1,1,2,3],[4,4,5,6]),1.)


if __name__=='__main__':unittest.main()
