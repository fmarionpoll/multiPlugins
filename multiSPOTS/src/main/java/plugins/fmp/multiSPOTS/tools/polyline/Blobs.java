package plugins.fmp.multiSPOTS.tools.polyline;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import icy.image.IcyBufferedImage;
import icy.roi.BooleanMask2D;
import icy.type.geom.Polygon2D;

public class Blobs {

	int[] binaryData = null;
	int sizeX = 0;
	int sizeY = 0;

	public Blobs(IcyBufferedImage img) {
		binaryData = img.getDataXYAsInt(0);
		sizeX = img.getSizeX();
		sizeY = img.getSizeY();
	}

	public int getPixelsConnected() {
		int blobnumber = 1;
		for (int iy = 0; iy < sizeY; iy++) {
			for (int ix = 0; ix < sizeX; ix++) {
				if (binaryData[ix + sizeX * iy] < 1)
					continue;
				int ioffset = ix + sizeX * iy;
				int ioffsetpreviousrow = ix + sizeX * (iy - 1);
				if ((iy > 0) && (ix > 0) && (binaryData[ioffsetpreviousrow - 1] > 0)) // ix-1, iy-1
					binaryData[ioffset] = binaryData[ioffsetpreviousrow - 1];
				else if ((iy > 0) && (binaryData[ioffsetpreviousrow] > 0)) // ix, iy-1
					binaryData[ioffset] = binaryData[ioffsetpreviousrow];
				else if ((iy > 0) && ((ix + 1) < sizeX) && (binaryData[ioffsetpreviousrow + 1] > 0)) // ix+1, iy-1
					binaryData[ioffset] = binaryData[ioffsetpreviousrow + 1];
				else if ((ix > 0) && (binaryData[ioffset - 1] > 0)) // ix-1, iy
					binaryData[ioffset] = binaryData[ioffset - 1];
				else { // new blob number
					binaryData[ioffset] = blobnumber;
					blobnumber++;
				}
			}
		}
		return (int) blobnumber - 1;
	}

	public void getBlobsConnected() {
		for (int iy = 0; iy < sizeY; iy++) {
			for (int ix = 0; ix < sizeX; ix++) {
				if (binaryData[ix + sizeX * iy] < 1)
					continue;
				int ioffset = ix + sizeX * iy;
				int ioffsetpreviousrow = ix + sizeX * (iy - 1);
				int val = binaryData[ioffset];

				if ((iy > 0) && (ix > 0) && (binaryData[ioffsetpreviousrow - 1] > 0)) // ix-1, iy-1
					if (binaryData[ioffsetpreviousrow - 1] != val)
						changeAllBlobNumber1Into2(binaryData[ioffsetpreviousrow - 1], val, binaryData);

					else if ((iy > 0) && (binaryData[ioffsetpreviousrow] > 0)) // ix, iy-1
						if (binaryData[ioffsetpreviousrow] != val)
							changeAllBlobNumber1Into2(binaryData[ioffsetpreviousrow], val, binaryData);

						else if ((iy > 0) && ((ix + 1) < sizeX) && (binaryData[ioffsetpreviousrow + 1] > 0)) // ix+1,
																												// iy-1
							if (binaryData[ioffsetpreviousrow + 1] != val)
								changeAllBlobNumber1Into2(binaryData[ioffsetpreviousrow + 1], val, binaryData);

							else if ((ix > 0) && (binaryData[ioffset - 1] > 0)) // ix-1, iy
								if (binaryData[ioffset - 1] != val)
									changeAllBlobNumber1Into2(binaryData[ioffset - 1], val, binaryData);
			}
		}
	}

	private void changeAllBlobNumber1Into2(int oldvalue, int newvalue, int[] binaryData) {
		for (int i = 0; i < binaryData.length; i++) {
			if (binaryData[i] == oldvalue)
				binaryData[i] = newvalue;
		}
	}

	public void fillBlanksPixelsWithinBlobs() {
		for (int irow = 0; irow < sizeY; irow++) {
			for (int icolumn = 0; icolumn < sizeX; icolumn++) {
				int iblob = binaryData[icolumn + sizeX * irow];
				if (iblob < 1)
					continue;
				int icol_first = icolumn;
				int icol_last = icolumn;
				// get last col of iblob
				for (int icolumn1 = icol_first; icolumn1 < sizeX; icolumn1++) {
					if (binaryData[icolumn1 + sizeX * irow] == iblob)
						icol_last = icolumn1;
				}
				// make sure that all pixels between icol_first and icol_last are set at iblob
				for (int icolumn1 = icol_first; icolumn1 <= icol_last; icolumn1++)
					binaryData[icolumn1 + sizeX * irow] = iblob;
				icolumn = icol_last;
			}
		}
	}

	public int getBlobAt(int ix, int iy) {
		return binaryData[ix + sizeX * iy];
	}

	public List<Integer> getListOfBlobs(int[] binaryData) {
		List<Integer> list = new ArrayList<Integer>(10);
		for (int i = 0; i < binaryData.length; i++) {
			int val = binaryData[i];
			boolean found = false;
			for (int ref : list) {
				if (val == ref) {
					found = true;
					break;
				}
			}
			if (!found)
				list.add(val);
		}
		return list;
	}

	public Polygon2D getBlobPolygon2D(int blobNumber) {
		List<Point> list_right = new ArrayList<Point>();
		List<Point> list_left = new ArrayList<Point>();
		for (int irow = 0; irow < sizeY; irow++) {
			for (int icolumn = 0; icolumn < sizeX; icolumn++) {
				if (binaryData[icolumn + sizeX * irow] != blobNumber)
					continue;
				int icolumn_left = icolumn;
				int icolumn_right = icolumn;
				for (int icolumn1 = icolumn; icolumn1 < sizeX; icolumn1++) {
					if (binaryData[icolumn1 + sizeX * irow] != blobNumber) {
						icolumn_right = icolumn1;
						break;
					}
				}
				list_left.add(new Point(icolumn_left, irow));
				if (icolumn_right != icolumn_left)
					list_right.add(new Point(icolumn_right, irow));
				break;
			}
		}

		List<Point> allpoints = new ArrayList<Point>();
		allpoints.addAll(list_left);
		Collections.reverse(list_right);
		allpoints.addAll(list_right);

		final List<Point2D> points2D = new ArrayList<Point2D>(allpoints.size());
		for (Point pt : allpoints)
			points2D.add(new Point2D.Double(pt.x + 0.5d, pt.y + 0.5d));
		double dev = 1.;
		return Polygon2D.getPolygon2D(points2D, dev);
	}

	public BooleanMask2D getBlobBooleanMask2D(int blobNumber) {
		List<Point> ptList = new ArrayList<Point>();
		for (int irow = 0; irow < sizeY; irow++) {
			for (int icolumn = 0; icolumn < sizeX; icolumn++) {
				if (binaryData[icolumn + sizeX * irow] != blobNumber)
					continue;
				for (int icolumn1 = icolumn; icolumn1 < sizeX; icolumn1++) {
					if (binaryData[icolumn1 + sizeX * irow] != blobNumber)
						break;
					ptList.add(new Point(icolumn1, irow));
				}
				break;
			}
		}
		Point[] ptArray = new Point[ptList.size()];
		ptList.toArray(ptArray);
		BooleanMask2D mask = new BooleanMask2D(ptArray);
		return mask;
	}

	public Rectangle getBlobRectangle(int blobNumber) {
		Rectangle rect = new Rectangle(0, 0, 0, 0);
		int[] arrayX = new int[sizeX];
		int[] arrayY = new int[sizeY];
		for (int iy = 0; iy < sizeY; iy++) {
			for (int ix = 0; ix < sizeX; ix++) {
				if (binaryData[ix + sizeX * iy] != blobNumber)
					continue;
				arrayX[ix]++;
				arrayY[iy]++;
			}
		}
		for (int i = 0; i < sizeX; i++)
			if (arrayX[i] > 0) {
				rect.x = i;
				break;
			}
		for (int i = sizeX - 1; i >= 0; i--)
			if (arrayX[i] > 0) {
				rect.width = i - rect.x + 1;
				break;
			}

		for (int i = 0; i < sizeY; i++)
			if (arrayY[i] > 0) {
				rect.y = i;
				break;
			}
		for (int i = sizeY - 1; i >= 0; i--)
			if (arrayY[i] > 0) {
				rect.height = i - rect.y + 1;
				break;
			}
		return rect;
	}

}
