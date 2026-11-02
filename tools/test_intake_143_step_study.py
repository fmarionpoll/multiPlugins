import unittest
import numpy as np
from intake_143_step_study import register,parse_measures,lower_summary


def movie(track,brightness=0):
    yy=np.arange(130)[:,None]
    edge=1/(1+np.exp(np.clip(-(yy-track[None,:])/.7,-60,60)))
    ridge=np.exp(-.5*((yy-track[None,:])/.8)**2)
    channels=[175-45*edge-12*ridge,170-5*edge-15*ridge,155-5*edge-12*ridge]
    rgb=np.stack(channels,axis=2)
    rgb[:,40:,:]+=brightness
    return rgb


class StepRegistrationTest(unittest.TestCase):
    def test_step_on_drift(self):
        t=np.arange(85,dtype=float);y=35+.04*t+4.2*(t>=40)
        r=register(movie(y),y+3,t,40)
        self.assertGreater(r['score'],.95)
        self.assertAlmostEqual(r['stepAfterLocalDrift'],4.2,delta=.12)

    def test_constant_seed_offset_does_not_change_step(self):
        t=np.arange(85,dtype=float);y=35+.03*t+4*(t>=40);rgb=movie(y)
        a=register(rgb,y,t,40);b=register(rgb,y+3,t,40)
        self.assertAlmostEqual(a['stepAfterLocalDrift'],b['stepAfterLocalDrift'],delta=.1)

    def test_constant_brightness_offset_is_tolerated(self):
        t=np.arange(85,dtype=float);y=35+.02*t+3.5*(t>=40)
        r=register(movie(y,20),y,t,40)
        self.assertGreater(r['score'],.95)
        self.assertAlmostEqual(r['stepAfterLocalDrift'],3.5,delta=.15)

    def test_no_step_on_constant_drift(self):
        t=np.arange(85,dtype=float);y=35+.04*t
        r=register(movie(y),y+2,t,40)
        self.assertAlmostEqual(r['stepAfterLocalDrift'],0,delta=.12)

    def test_flat_profile_is_not_high_quality_match(self):
        t=np.arange(85,dtype=float);y=np.full(85,40.)
        r=register(np.full((130,85,3),150.),y,t,40)
        self.assertIn('weak_profile_match',r['reasons'])

    def test_profile_outside_image_is_unavailable(self):
        t=np.arange(85,dtype=float);y=np.full(85,2.)
        self.assertIsNone(register(np.full((130,85,3),150.),y,t,40))

    def test_native_time_intervals_are_used(self):
        t=np.arange(85,dtype=float)*1.7;y=35+.02*t+4*(np.arange(85)>=40)
        r=register(movie(y),y+2,t,40)
        self.assertAlmostEqual(r['stepAfterLocalDrift'],4,delta=.15)

    def test_change_of_drift_is_flagged(self):
        t=np.arange(85,dtype=float);y=35+.4*np.minimum(t,39)
        r=register(movie(y),y,t,40,position_noise=.05)
        self.assertIn('flank_drifts_disagree',r['reasons'])

    def test_saved_sections_and_border_diagnostics(self):
        blob=b'#;TOPRAW;columns=;name;index;npts;yi\n6L;12;3;1;2;3\n#;BOTTOMLEVEL;columns=;name;index;npts;yi\n6L;12;3;8;8;8\n'
        r=parse_measures(blob)
        np.testing.assert_equal(r['6L']['BOTTOMLEVEL'],[8,8,8])
        summary=lower_summary(np.full(100,9.),10)
        self.assertTrue(summary['constant'])
        self.assertEqual(summary['nearImageBorderFraction'],1.)


if __name__=='__main__':unittest.main()
