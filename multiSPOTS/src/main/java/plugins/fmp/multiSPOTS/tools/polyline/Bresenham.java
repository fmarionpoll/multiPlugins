package plugins.fmp.multiSPOTS.tools.polyline;

import java.awt.geom.Point2D;
import java.util.ArrayList;

/*
 Bresenham's algorithm
 algorithm to get pixel coordinates between 2 points connected by a line
 as described by:
 Michael ABRASH (1992) The Good, the Bad and the Run-sliced
 Dr Dobb's Journal 194: 171-191
 see also: https://www.javatpoint.com/computer-graphics-bresenhams-line-algorithm
 implementation adapted from https://gist.github.com/0x414c/3bbd1122a50e4be229ce
 from Alexey Gorishny
 and
 drawFatLine (in C) from 
 https://github.com/ArminJo/STMF3-Discovery-Demos/blob/master/lib/graphics/src/thickLine.cpp
 ---
 nice description of Bresenham's algorithm also here:
 http://members.chello.at/~easyfilter/bresenham.html
*/

public class Bresenham {
	public static ArrayList<int[]> getPixelsBetween2Points(int x1, int y1, int x2, int y2) {
		ArrayList<int[]> line = new ArrayList<int[]>();
		int x, y;
		int dx, dy;
		int incx, incy;
		int balance;

		if (x2 >= x1) {
			dx = x2 - x1;
			incx = 1;
		} else {
			dx = x1 - x2;
			incx = -1;
		}

		if (y2 >= y1) {
			dy = y2 - y1;
			incy = 1;
		} else {
			dy = y1 - y2;
			incy = -1;
		}

		x = x1;
		y = y1;

		if (dx >= dy) {
			dy <<= 1;
			balance = dy - dx;
			dx <<= 1;
			while (x != x2) {
				line.add(new int[] { x, y });
				if (balance >= 0) {
					y += incy;
					balance -= dx;
				}
				balance += dy;
				x += incx;
			}
			line.add(new int[] { x, y });
		} else {
			dx <<= 1;
			balance = dx - dy;
			dy <<= 1;

			while (y != y2) {
				line.add(new int[] { x, y });
				if (balance >= 0) {
					x += incx;
					balance -= dy;
				}
				balance += dx;
				y += incy;
			}
			line.add(new int[] { x, y });
		}
		return line;
	}

	public static ArrayList<int[]> getPixelsAlongLineFromROI2D(ArrayList<Point2D> pointsList) {
		ArrayList<int[]> line = new ArrayList<int[]>();
		for (int i = 1; i < pointsList.size(); i++) {
			ArrayList<int[]> linei = getPixelsBetween2Points((int) pointsList.get(i - 1).getX(),
					(int) pointsList.get(i - 1).getY(), (int) pointsList.get(i).getX(), (int) pointsList.get(i).getY());
			line.addAll(linei);
		}
		return line;
	}

}
