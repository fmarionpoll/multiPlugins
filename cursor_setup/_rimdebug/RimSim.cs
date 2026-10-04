using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Linq;

public static class RimSim2 {
    const int W = 1920;
    const string Spot = "spot_022_001_005";

    public static void Run(string descPath, string measPath, string grabDir) {
        double[] xs, ys;
        List<double[]> ellipses;
        LoadOutline(descPath, out xs, out ys, out ellipses);
        int[] sigX, sigY, flX, flY;
        BuildPixels(xs, ys, 3, 5, ellipses, out sigX, out sigY, out flX, out flY);
        Console.WriteLine("signal " + sigX.Length + " floor " + flX.Length);

        string[] files = Directory.GetFiles(grabDir, "Frame_*.jpg").OrderBy(f => f, StringComparer.OrdinalIgnoreCase).ToArray();
        int n = files.Length;
        int ns = sigX.Length;
        double[] frac = new double[n];
        double[] p75 = new double[n];
        double[] floorMed = new double[n];
        double[] excess = new double[n * ns];
        var openingFloors = new List<double>();
        int window = 5;
        for (int t = 0; t < n; t++) {
            byte[] pix = Load(files[t]);
            double[] fd = Deficits(pix, flX, flY);
            double fm = Median(fd);
            floorMed[t] = fm;
            if (t < window) openingFloors.AddRange(fd);
            for (int i = 0; i < ns; i++) excess[t * ns + i] = DefAt(pix, sigX[i], sigY[i]) - fm;
            var row = new double[ns];
            Array.Copy(excess, t * ns, row, 0, ns);
            p75[t] = Quantile(row, 0.75);
        }
        double dye = Median(p75.Take(window).ToArray());
        double noise = 5.0 * Mad(openingFloors.ToArray());
        double cut = Math.Max(noise, 0.5 * dye);
        Console.WriteLine(string.Format("dyeLevel {0:R} noise {1:R} cut {2:R}", dye, noise, cut));
        Console.WriteLine("opening p75 " + string.Join(" ", p75.Take(window).Select(v => v.ToString("F2"))));
        Console.WriteLine("opening floor " + string.Join(" ", floorMed.Take(window).Select(v => v.ToString("F2"))));

        for (int t = 0; t < n; t++) {
            int above = 0;
            for (int i = 0; i < ns; i++) if (excess[t * ns + i] > cut) above++;
            frac[t] = ns == 0 ? double.NaN : above / (double)ns;
        }
        double[] csv = LoadCsv(measPath);
        Console.WriteLine("csv n " + csv.Length + " first " + (csv.Length > 0 ? csv[0].ToString("R") : "none"));
        Report("smooth9 bins5", frac, 9, 5, csv);
        int simMaxAt = 0;
        double simMax = double.NegativeInfinity;
        double[] sm = MovingMedian(frac, 9);
        double i0 = Median(sm.Take(5).ToArray());
        for (int t = 0; t < sm.Length; t++) {
            double r = i0 > 0 ? sm[t] / i0 : double.NaN;
            if (r > simMax) { simMax = r; simMaxAt = t; }
        }
        Console.WriteLine(string.Format("sim max {0:F3} at bin {1} min {2:F1}", simMax, simMaxAt, simMaxAt * 21.0 / 60.0));
        for (int t = 180; t <= 210 && t < sm.Length; t++) {
            double r = i0 > 0 ? sm[t] / i0 : double.NaN;
            double c = t < csv.Length ? csv[t] : double.NaN;
            if (t % 2 == 0) Console.WriteLine(string.Format("bin {0} raw {1:F3} ratio {2:F3} csv {3:F3}", t, frac[t], r, c));
        }
        // locate the stored spike and print the raw fraction there
        int spike = 0;
        for (int i = 1; i < csv.Length; i++) if (csv[i] > csv[spike]) spike = i;
        Console.WriteLine(string.Format("csv max {0:R} at bin {1} minute {2:F1} rawFrac {3:F3} floor {4:F2} p75 {5:F2}",
            csv[spike], spike, spike * 21.0 / 60.0, frac[Math.Min(spike, frac.Length - 1)],
            floorMed[Math.Min(spike, floorMed.Length - 1)], p75[Math.Min(spike, p75.Length - 1)]));
        int bin64 = Math.Min(64, frac.Length - 1);
        Console.WriteLine(string.Format("frame64 raw {0:F3} csv {1:R} floor {2:F2}", frac[bin64], csv.Length > 64 ? csv[64] : double.NaN, floorMed[bin64]));
    }

    static void Report(string label, double[] frac, int smooth, int initial, double[] csv) {
        double[] sm = MovingMedian(frac, smooth);
        int window = Math.Min(initial, sm.Length);
        double i0 = Median(sm.Take(window).ToArray());
        Console.WriteLine("--- " + label + " i0 " + i0.ToString("R"));
        int n = Math.Min(8, sm.Length);
        var parts = new List<string>();
        for (int t = 0; t < n; t++) {
            double r = i0 > 0 ? sm[t] / i0 : double.NaN;
            double c = t < csv.Length ? csv[t] : double.NaN;
            parts.Add(string.Format("t{0} raw {1:F3} sm {2:F3} ratio {3:F4} csv {4:F4}", t, frac[t], sm[t], r, c));
        }
        foreach (string p in parts) Console.WriteLine(p);
        double maxAbs = 0;
        int compared = Math.Min(sm.Length, csv.Length);
        int worst = 0;
        for (int t = 0; t < compared; t++) {
            double r = i0 > 0 ? sm[t] / i0 : double.NaN;
            double d = Math.Abs(r - csv[t]);
            if (d > maxAbs) { maxAbs = d; worst = t; }
        }
        Console.WriteLine(string.Format("maxAbsDiff {0:F4} at bin {1}", maxAbs, worst));
    }

    static double[] LoadCsv(string path) {
        string section = "";
        foreach (string line in File.ReadLines(path)) {
            if (line.StartsWith("#;")) {
                string[] h = line.Split(';');
                section = h.Length > 1 ? h[1] : "";
                continue;
            }
            if (section != "KYMO_RIM_RATIO" || !line.StartsWith(Spot + ";")) continue;
            string[] c = line.Split(';');
            var v = new List<double>();
            for (int i = 3; i < c.Length; i++) {
                double d;
                if (double.TryParse(c[i], System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out d))
                    v.Add(d);
            }
            return v.ToArray();
        }
        return new double[0];
    }

    static void LoadOutline(string path, out double[] xs, out double[] ys, out List<double[]> ellipses) {
        xs = null; ys = null; ellipses = new List<double[]>();
        foreach (string line in File.ReadLines(path)) {
            if (line.StartsWith("#") || line.StartsWith("n spots") || line.StartsWith("name;")) continue;
            string[] c = line.Split(';');
            if (c.Length < 25 || c[16] != "ellipse") continue;
            double cx = D(c[17]), cy = D(c[18]), rx = D(c[19]), ry = D(c[20]);
            ellipses.Add(new double[] { cx, cy, Math.Max(1, rx), Math.Max(1, ry) });
            if (c[0] != Spot) continue;
            int n = int.Parse(c[24], System.Globalization.CultureInfo.InvariantCulture);
            xs = new double[n]; ys = new double[n];
            for (int i = 0; i < n; i++) {
                xs[i] = D(c[25 + 2 * i]);
                ys[i] = D(c[26 + 2 * i]);
            }
        }
        // drop self ellipse: last added when we saw the spot is not tracked; remove the matching center
        ellipses.RemoveAll(e => Math.Abs(e[0] - 1183.0) < 0.1 && Math.Abs(e[1] - 738.5) < 0.1);
    }

    static double D(string s) {
        return double.Parse(s, System.Globalization.CultureInfo.InvariantCulture);
    }

    static void BuildPixels(double[] xs, double[] ys, int rimW, int outer, List<double[]> blockers,
        out int[] sigX, out int[] sigY, out int[] flX, out int[] flY) {
        var sx = new List<int>(); var sy = new List<int>();
        double minX = xs.Min(), maxX = xs.Max(), minY = ys.Min(), maxY = ys.Max();
        int x0 = Math.Max(0, (int)Math.Floor(minX) - 1);
        int y0 = Math.Max(0, (int)Math.Floor(minY) - 1);
        int x1 = Math.Min(W - 1, (int)Math.Ceiling(maxX) + 1);
        int y1 = Math.Min(1079, (int)Math.Ceiling(maxY) + 1);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double d = DistClosed(x, y, xs, ys);
                if (d <= rimW && (d <= 0.6 || Inside(x, y, xs, ys))) {
                    sx.Add(x); sy.Add(y);
                }
            }
        }
        double[] ox, oy;
        Offset(xs, ys, outer, out ox, out oy);
        var seen = new HashSet<long>();
        var fx = new List<int>(); var fy = new List<int>();
        double radius = Math.Max(0.5, rimW / 2.0);
        int pad = (int)Math.Ceiling(radius);
        int n = ox.Length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double dx = ox[j] - ox[i], dy = oy[j] - oy[i];
            double len = Math.Sqrt(dx * dx + dy * dy);
            int steps = Math.Max(1, (int)Math.Ceiling(len * 2.0));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double)steps;
                double px = ox[i] + t * dx, py = oy[i] + t * dy;
                int ix = (int)Math.Round(px), iy = (int)Math.Round(py);
                for (int yy = iy - pad; yy <= iy + pad; yy++) {
                    if (yy < 0 || yy >= 1080) continue;
                    for (int xx = ix - pad; xx <= ix + pad; xx++) {
                        if (xx < 0 || xx >= W) continue;
                        double ddx = xx - px, ddy = yy - py;
                        if (ddx * ddx + ddy * ddy > radius * radius) continue;
                        long key = ((long)yy << 16) | (uint)xx;
                        if (!seen.Add(key)) continue;
                        if (InsideAny(blockers, xx, yy)) continue;
                        fx.Add(xx); fy.Add(yy);
                    }
                }
            }
        }
        sigX = sx.ToArray(); sigY = sy.ToArray(); flX = fx.ToArray(); flY = fy.ToArray();
    }

    static bool InsideAny(List<double[]> e, int x, int y) {
        for (int i = 0; i < e.Count; i++) {
            double dx = (x - e[i][0]) / e[i][2];
            double dy = (y - e[i][1]) / e[i][3];
            if (dx * dx + dy * dy <= 1.0) return true;
        }
        return false;
    }

    static void Offset(double[] xs, double[] ys, double distance, out double[] ox, out double[] oy) {
        int n = xs.Length;
        ox = new double[n]; oy = new double[n];
        double cx = 0, cy = 0;
        for (int i = 0; i < n; i++) { cx += xs[i]; cy += ys[i]; }
        cx /= n; cy /= n;
        for (int i = 0; i < n; i++) {
            int prev = (i + n - 1) % n, next = (i + 1) % n;
            double tx = xs[next] - xs[prev], ty = ys[next] - ys[prev];
            double len = Math.Sqrt(tx * tx + ty * ty);
            if (len < 1e-6 || distance == 0) { ox[i] = xs[i]; oy[i] = ys[i]; continue; }
            double nx = -ty / len, ny = tx / len;
            if (nx * (xs[i] - cx) + ny * (ys[i] - cy) < 0) { nx = -nx; ny = -ny; }
            ox[i] = xs[i] + nx * distance;
            oy[i] = ys[i] + ny * distance;
        }
    }

    static double DistClosed(double px, double py, double[] xs, double[] ys) {
        int n = xs.Length;
        double best = double.PositiveInfinity;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            best = Math.Min(best, DistSeg(px, py, xs[i], ys[i], xs[j], ys[j]));
        }
        return best;
    }

    static double DistSeg(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy, t = 0;
        if (len2 > 1e-12) {
            t = ((px - ax) * dx + (py - ay) * dy) / len2;
            if (t < 0) t = 0; else if (t > 1) t = 1;
        }
        double qx = ax + t * dx, qy = ay + t * dy;
        return Math.Sqrt((px - qx) * (px - qx) + (py - qy) * (py - qy));
    }

    static bool Inside(double px, double py, double[] xs, double[] ys) {
        bool inn = false;
        int n = xs.Length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double yi = ys[i], yj = ys[j];
            if ((yi > py) != (yj > py)) {
                double x = xs[j] + (xs[i] - xs[j]) * (py - yj) / (yi - yj);
                if (px < x) inn = !inn;
            }
        }
        return inn;
    }

    static byte[] Load(string path) {
        using (var bmp = new Bitmap(path)) {
            var rect = new Rectangle(0, 0, bmp.Width, bmp.Height);
            var data = bmp.LockBits(rect, ImageLockMode.ReadOnly, PixelFormat.Format24bppRgb);
            int stride = data.Stride;
            byte[] buf = new byte[Math.Abs(stride) * bmp.Height];
            System.Runtime.InteropServices.Marshal.Copy(data.Scan0, buf, 0, buf.Length);
            bmp.UnlockBits(data);
            // pack to W*H*3 RGB, top-down
            byte[] rgb = new byte[bmp.Width * bmp.Height * 3];
            for (int y = 0; y < bmp.Height; y++) {
                int src = y * stride;
                int dst = y * bmp.Width * 3;
                for (int x = 0; x < bmp.Width; x++) {
                    rgb[dst + x * 3] = buf[src + x * 3 + 2];
                    rgb[dst + x * 3 + 1] = buf[src + x * 3 + 1];
                    rgb[dst + x * 3 + 2] = buf[src + x * 3];
                }
            }
            return rgb;
        }
    }

    static double DefAt(byte[] rgb, int x, int y) {
        int i = (y * W + x) * 3;
        return ((rgb[i + 1] + rgb[i + 2]) * 0.5) - rgb[i];
    }

    static double[] Deficits(byte[] rgb, int[] xs, int[] ys) {
        var v = new double[xs.Length];
        for (int i = 0; i < xs.Length; i++) v[i] = DefAt(rgb, xs[i], ys[i]);
        return v;
    }

    static double[] Excess(byte[] rgb, int[] xs, int[] ys, double floor) {
        var v = new double[xs.Length];
        for (int i = 0; i < xs.Length; i++) v[i] = DefAt(rgb, xs[i], ys[i]) - floor;
        return v;
    }

    static bool Finite(double v) { return !double.IsNaN(v) && !double.IsInfinity(v); }

    static double Quantile(double[] values, double q) {
        var copy = values.Where(Finite).OrderBy(v => v).ToArray();
        if (copy.Length == 0) return 0;
        double pos = Math.Min(1.0, Math.Max(0.0, q)) * (copy.Length - 1);
        int lo = (int)pos;
        int hi = Math.Min(copy.Length - 1, lo + 1);
        double w = pos - lo;
        return copy[lo] * (1.0 - w) + copy[hi] * w;
    }

    static double Median(double[] values) {
        var copy = values.Where(Finite).OrderBy(v => v).ToArray();
        if (copy.Length == 0) return 0;
        int mid = copy.Length / 2;
        if ((copy.Length & 1) == 1) return copy[mid];
        return 0.5 * (copy[mid - 1] + copy[mid]);
    }

    static double Mad(double[] values) {
        double med = Median(values);
        var dev = values.Select(v => Math.Abs(v - med)).ToArray();
        return Median(dev);
    }

    static double[] MovingMedian(double[] values, int window) {
        int n = values.Length;
        var output = new double[n];
        int w = Math.Max(1, window);
        if ((w & 1) == 0) w++;
        int half = w / 2;
        for (int i = 0; i < n; i++) {
            int from = Math.Max(0, i - half);
            int to = Math.Min(n - 1, i + half);
            var buf = new List<double>();
            for (int j = from; j <= to; j++) if (Finite(values[j])) buf.Add(values[j]);
            output[i] = buf.Count == 0 ? double.NaN : Median(buf.ToArray());
        }
        return output;
    }
}
