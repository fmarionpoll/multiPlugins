import unittest
import numpy as np
from intake_local_position_trial import localize


def image(track,height=110):
    yy=np.arange(height)[:,None]
    ridge=np.exp(-.5*((yy-track[None,:])/.8)**2)
    plane=150-15/(1+np.exp(np.clip(-(yy-track[None,:]),-60,60)))-35*ridge
    return np.repeat(plane[:,:,None],3,axis=2)


class LocalPositionTest(unittest.TestCase):
    def test_moving_ridge_recovered_from_offset_seed(self):
        target=25+.3*np.arange(80)
        r=localize(image(target),target+3)
        self.assertGreater(r['accepted'].mean(),.8)
        self.assertLess(np.median(np.abs(r['proposed']-target)),.3)

    def test_abrupt_drop_keeps_its_time(self):
        target=25+.15*np.arange(80)+6*(np.arange(80)>=40)
        r=localize(image(target),target+3)
        self.assertEqual(np.argmax(np.diff(r['proposed'])),39)
        self.assertAlmostEqual(np.diff(r['proposed'])[39],6.15,delta=.3)

    def test_unsupported_flat_image_keeps_seed(self):
        seed=np.full(40,35.)
        r=localize(np.full((80,40,3),150.),seed)
        self.assertFalse(r['accepted'].any())
        np.testing.assert_equal(r['safe'],seed)

    def test_stationary_feature_is_flagged_not_forced(self):
        target=np.full(50,35.)
        r=localize(image(target),target+3)
        self.assertFalse(r['accepted'].any())
        self.assertTrue(all('persistent_horizontal_feature_nearby' in reasons for reasons in r['reasons']))
        np.testing.assert_equal(r['safe'],target+3)

    def test_missing_seed_is_not_filled(self):
        target=25+.3*np.arange(40);seed=target+3;seed[20]=np.nan
        r=localize(image(target),seed)
        self.assertFalse(r['accepted'][20])
        self.assertTrue(np.isnan(r['safe'][20]))

    def test_constant_translation_preserves_amplitudes(self):
        target=25+.2*np.arange(80)+5*(np.arange(80)>=35)
        r=localize(image(target),target+3)
        translated=target+3+np.median(r['proposed']-(target+3))
        np.testing.assert_allclose(np.diff(translated),np.diff(target),atol=1e-12)


if __name__=='__main__':unittest.main()
