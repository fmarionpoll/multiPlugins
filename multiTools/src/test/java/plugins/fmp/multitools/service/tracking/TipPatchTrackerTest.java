package plugins.fmp.multitools.service.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.geom.Point2D;

import org.junit.Test;

/* CODEX */
public class TipPatchTrackerTest {
 @Test public void rejectedTipsHoldIndependentlyAndCanRecover() {
  TipPatchTracker.AcceptedPositions state=new TipPatchTracker.AcceptedPositions(new java.awt.geom.Line2D.Double(20,30,20,90));
  TipPatchTracker.Match bad=new TipPatchTracker.Match(new Point2D.Double(20,30),false,.2,3);
  assertEquals(30.,state.update(bad,bad).getY1(),0.);
  TipPatchTracker.Match top=new TipPatchTracker.Match(new Point2D.Double(21,34),true,.99,0);
  TipPatchTracker.Match bottom=new TipPatchTracker.Match(new Point2D.Double(21,92),true,.99,0);
  state.update(top,bottom);
  for(int i=0;i<5;i++) {
   java.awt.geom.Line2D held=state.update(bad,bad);
   assertEquals(34.,held.getY1(),0.);assertEquals(92.,held.getY2(),0.);
  }
  TipPatchTracker.Match recovered=new TipPatchTracker.Match(new Point2D.Double(21,35),true,.99,0);
  java.awt.geom.Line2D line=state.update(recovered,bad);
  assertEquals(35.,line.getY1(),0.);assertEquals(92.,line.getY2(),0.);
  TipPatchTracker.AcceptedPositions newRun=new TipPatchTracker.AcceptedPositions(new java.awt.geom.Line2D.Double(10,40,10,100));
  assertEquals(40.,newRun.update(bad,bad).getY1(),0.);
 }
	@Test
	public void followsTextureWithBrightnessChange() {
		int w = 100;
		double[] a = new double[w * w], b = new double[w * w];
		java.util.Random random = new java.util.Random(7);
		for (int i = 0; i < a.length; i++)
			a[i] = random.nextDouble() * 100;
		for (int y = 0; y < 100; y++)
			for (int x = 0; x < 100; x++)
				if (x >= 3 && y >= 2)
					b[x + y * w] = a[x - 3 + (y - 2) * w] * .8 + 20;
		TipPatchTracker.Match m = new TipPatchTracker().track(new Point2D.Double(50, 50), a, b, w, w);
		assertTrue(m.reliable);
		assertEquals(53, m.position.getX(), .1);
		assertEquals(52, m.position.getY(), .1);
	}

	@Test
	public void rejectsBlankAndOccludedPatches() {
		double[] a = new double[10000], b = new double[10000];
		TipPatchTracker tracker = new TipPatchTracker();
		Point2D p = new Point2D.Double(50, 50);
		assertFalse(tracker.track(p, a, b, 100, 100).reliable);
		java.util.Random random = new java.util.Random(8);
		for (int i = 0; i < a.length; i++)
			a[i] = random.nextDouble() * 100;
		TipPatchTracker.Match m = tracker.track(p, a, b, 100, 100);
		assertFalse(m.reliable);
		assertEquals(0, p.distance(m.position), 0);
	}
}
