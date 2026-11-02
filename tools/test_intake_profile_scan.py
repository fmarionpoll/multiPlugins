import unittest
import numpy as np
from intake_profile_registration import register,position_scatter
from intake_143_step_study import register as reference
from intake_profile_scan import image_gate,select,evaluate
from test_intake_143_step_study import movie


class FullScanTest(unittest.TestCase):
    def test_vectorized_registration_matches_reference(self):
        t=np.arange(85,dtype=float);y=35+.04*t+4.2*(t>=40);rgb=movie(y,10)
        for first in [30,39,40,41]:
            a=register(rgb,y+2,t,first,.3)
            b=reference(rgb,y+2,t,first,position_noise=.3)
            self.assertAlmostEqual(a['step'],b['stepAfterLocalDrift'],places=10)
            self.assertAlmostEqual(a['score'],b['score'],places=10)

    def test_only_actual_boundary_has_concentrated_step(self):
        t=np.arange(85,dtype=float);y=35+.03*t+4*(t>=40);rgb=movie(y)
        a=register(rgb,y,t,40,.3);b=register(rgb,y,t,39,.3)
        self.assertNotIn('movement_not_concentrated_at_boundary',image_gate(a,np.array([])))
        self.assertIn('movement_not_concentrated_at_boundary',image_gate(b,np.array([])))

    def test_scatter_estimator_uses_native_times_and_noise(self):
        rng=np.random.default_rng(301);t=np.cumsum(rng.uniform(.8,1.2,10000));y=25+.1*t+rng.normal(0,.3,len(t))
        self.assertAlmostEqual(position_scatter(y,t),.3,delta=.025)
        self.assertAlmostEqual(position_scatter(25+.1*t,t),.08)

    def test_baseline_and_neighbour_protection(self):
        baseline=np.zeros(30,bool);baseline[10]=True
        cs=[dict(first=i,normalisedStep=s,gates=[]) for i,s in [(10,50),(11,40),(18,20),(19,10)]]
        chosen,_=select(cs,baseline,5)
        self.assertEqual([c['first'] for c in chosen],[18])
        self.assertTrue(baseline[10])

    def test_nearby_recovery_is_one_to_one(self):
        rows=[dict(edit_action='unchanged',manual_override='false',edited_net_intake_px='0') for _ in range(30)]
        for i in [18,20]:rows[i]=dict(edit_action='added',manual_override='true',edited_net_intake_px='1')
        metrics=evaluate(rows,np.zeros(30,bool),[dict(first=19)])
        self.assertEqual(metrics['recoveredWithinOne'],1)
        self.assertEqual(metrics['recoveredExact'],0)

    def test_persistent_edge_is_deferred(self):
        t=np.arange(85,dtype=float);y=35+.03*t+4*(t>=40)
        a=register(movie(y),y,t,40,.3)
        self.assertIn('persistent_horizontal_edge_near_feature',image_gate(a,np.array([a['featureBefore']])))


if __name__=='__main__':unittest.main()
