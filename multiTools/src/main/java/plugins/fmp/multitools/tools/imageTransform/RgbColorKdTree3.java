package plugins.fmp.multitools.tools.imageTransform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * KD-tree over RGB reference points for squared Euclidean nearest-neighbor distance.
 */
public final class RgbColorKdTree3 {

	private final double[] xs;
	private final double[] ys;
	private final double[] zs;
	private final Node root;

	private RgbColorKdTree3(double[] xs, double[] ys, double[] zs, Node root) {
		this.xs = xs;
		this.ys = ys;
		this.zs = zs;
		this.root = root;
	}

	public static RgbColorKdTree3 build(int[] rr, int[] gg, int[] bb) {
		int n = rr.length;
		if (n == 0) {
			return null;
		}
		double[] xs = new double[n];
		double[] ys = new double[n];
		double[] zs = new double[n];
		for (int i = 0; i < n; i++) {
			xs[i] = rr[i];
			ys[i] = gg[i];
			zs[i] = bb[i];
		}
		List<Integer> ind = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			ind.add(i);
		}
		Node root = build(ind, 0, xs, ys, zs);
		return new RgbColorKdTree3(xs, ys, zs, root);
	}

	private static Node build(List<Integer> pts, int depth, double[] xs, double[] ys, double[] zs) {
		int n = pts.size();
		if (n == 0) {
			return null;
		}
		if (n == 1) {
			return new Leaf(pts.get(0));
		}
		if (n <= 8) {
			int[] bucket = new int[n];
			for (int i = 0; i < n; i++) {
				bucket[i] = pts.get(i);
			}
			return new LeafBucket(bucket);
		}
		final int axis = depth % 3;
		Comparator<Integer> cmp = (a, b) -> {
			double va = axis == 0 ? xs[a] : (axis == 1 ? ys[a] : zs[a]);
			double vb = axis == 0 ? xs[b] : (axis == 1 ? ys[b] : zs[b]);
			return Double.compare(va, vb);
		};
		Collections.sort(pts, cmp);
		int mid = n / 2;
		int pivot = pts.get(mid);
		List<Integer> left = new ArrayList<>(pts.subList(0, mid));
		List<Integer> right = new ArrayList<>(pts.subList(mid + 1, n));
		double split = axis == 0 ? xs[pivot] : (axis == 1 ? ys[pivot] : zs[pivot]);
		Node L = build(left, depth + 1, xs, ys, zs);
		Node R = build(right, depth + 1, xs, ys, zs);
		return new Internal(axis, split, pivot, L, R);
	}

	public double nearestDistanceSquared(double px, double py, double pz) {
		if (root == null) {
			return Double.POSITIVE_INFINITY;
		}
		return root.nearest(px, py, pz, Double.POSITIVE_INFINITY, xs, ys, zs);
	}

	private abstract static class Node {
		abstract double nearest(double px, double py, double pz, double bestSq, double[] xs, double[] ys, double[] zs);
	}

	private static final class Leaf extends Node {
		final int i;

		Leaf(int i) {
			this.i = i;
		}

		@Override
		double nearest(double px, double py, double pz, double bestSq, double[] xs, double[] ys, double[] zs) {
			double dx = px - xs[i];
			double dy = py - ys[i];
			double dz = pz - zs[i];
			double d = dx * dx + dy * dy + dz * dz;
			return Math.min(bestSq, d);
		}
	}

	private static final class LeafBucket extends Node {
		final int[] is;

		LeafBucket(int[] is) {
			this.is = is;
		}

		@Override
		double nearest(double px, double py, double pz, double bestSq, double[] xs, double[] ys, double[] zs) {
			for (int i : is) {
				double dx = px - xs[i];
				double dy = py - ys[i];
				double dz = pz - zs[i];
				double d = dx * dx + dy * dy + dz * dz;
				if (d < bestSq) {
					bestSq = d;
				}
			}
			return bestSq;
		}
	}

	private static final class Internal extends Node {
		final int axis;
		final double split;
		final int pivot;
		final Node left;
		final Node right;

		Internal(int axis, double split, int pivot, Node left, Node right) {
			this.axis = axis;
			this.split = split;
			this.pivot = pivot;
			this.left = left;
			this.right = right;
		}

		@Override
		double nearest(double px, double py, double pz, double bestSq, double[] xs, double[] ys, double[] zs) {
			double dx = px - xs[pivot];
			double dy = py - ys[pivot];
			double dz = pz - zs[pivot];
			double d0 = dx * dx + dy * dy + dz * dz;
			if (d0 < bestSq) {
				bestSq = d0;
			}
			double v = axis == 0 ? px : (axis == 1 ? py : pz);
			double diff = v - split;
			Node first = diff < 0 ? left : right;
			Node second = diff < 0 ? right : left;
			if (first != null) {
				bestSq = first.nearest(px, py, pz, bestSq, xs, ys, zs);
			}
			double planeDist = diff * diff;
			if (planeDist < bestSq && second != null) {
				bestSq = second.nearest(px, py, pz, bestSq, xs, ys, zs);
			}
			return bestSq;
		}
	}
}
