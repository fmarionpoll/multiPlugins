package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class Deriche extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	double alpha = 0;
	boolean transformToGrey = false;

	public Deriche(double alpha, boolean transformToGrey) {
		this.alpha = alpha;
		this.transformToGrey = transformToGrey;
	}

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		IcyBufferedImage img2 = doDeriche(sourceImage, this.alpha);
		if (transformToGrey)
			img2 = transformToGrey(img2, options.copyResultsToThe3planes);
		return img2;
	}

	private IcyBufferedImage doDeriche(IcyBufferedImage img, double alpha) {
		IcyBufferedImage img2 = new IcyBufferedImage(img.getWidth(), img.getHeight(), 3, img.getDataType_());
		final int lignes = img.getHeight();
		final int colonnes = img.getWidth();

		/* alloc temporary buffers */
		final int nmem = lignes * colonnes;
		float[] nf_grx = new float[nmem];
		float[] nf_gry = new float[nmem];

		short[] a1 = new short[nmem];
		float[] a2 = new float[nmem];
		float[] a3 = new float[nmem];
		float[] a4 = new float[nmem];

		final float ad1 = (float) -Math.exp(-alpha);
		final float ad2 = 0;
		final float an1 = 1;
		final float an2 = 0;
		final float an3 = (float) Math.exp(-alpha);
		final float an4 = 0;
		final float an11 = 1;

		for (int ch = 0; ch < img.getSizeC(); ch++) {
			double[] tabInDouble = Array1DUtil.arrayToDoubleArray(img.getDataXY(ch), img.isSignedDataType());
			doDeriche_step0(lignes, colonnes, ad1, ad2, an1, an2, an3, an4, an11, a1, a2, a3, tabInDouble);

			/* FIRST STEP Y-GRADIENT : y-derivative */
			doDeriche_step1(lignes, colonnes, ad1, ad2, an11, a2, a3, a4, nf_gry);

			/* SECOND STEP X-GRADIENT */
			doDeriche_step2(lignes, colonnes, ad1, ad2, an1, an2, an3, an4, an11, a1, a2, a3, a4, nf_grx);

			/* THIRD STEP : NORM */
			doDeriche_step3(lignes, colonnes, a2, a3, nf_gry);

			/* FOURTH STEP : NON MAXIMA SUPPRESSION */
			for (int i = 0; i < lignes; i++)
				for (int j = 0; j < colonnes; j++)
					a4[i * colonnes + j] = nf_grx[i * colonnes + j];

			for (int i = 0; i < lignes; i++)
				for (int j = 0; j < colonnes; j++)
					a3[i * colonnes + j] = nf_gry[i * colonnes + j];

			/* Nom maxima suppression with linear interpolation */
			int lig_2 = lignes - 2;
			for (int i = 1; i <= lig_2; ++i) {
				int icoll = i * colonnes;
				int col_2 = colonnes - 2;
				for (int j = 1; j <= col_2; ++j) {
					int jp1 = j + 1;
					int jm1 = j - 1;
					int ip1 = i + 1;
					int im1 = i - 1;
					if (a3[icoll + j] > 0.) {
						float wd = a4[icoll + j] / a3[icoll + j];
						a3[icoll + j] = 0;
						if (wd >= 1) {
							float gun = a2[icoll + jp1] + (a2[ip1 * colonnes + jp1] - a2[icoll + jp1]) / wd;
							if (a2[icoll + j] <= gun)
								continue;
							float gzr = a2[icoll + jm1] + (a2[im1 * colonnes + jm1] - a2[icoll + jm1]) / wd;
							if (a2[icoll + j] < gzr)
								continue;
							a3[icoll + j] = a2[icoll + j];
							continue;
						}
						if (wd >= 0) {
							float gun = a2[ip1 * colonnes + j]
									+ (a2[ip1 * colonnes + jp1] - a2[ip1 * colonnes + j]) * wd;
							if (a2[icoll + j] <= gun)
								continue;
							float gzr = a2[im1 * colonnes + j]
									+ (a2[im1 * colonnes + jm1] - a2[im1 * colonnes + j]) * wd;
							if (a2[icoll + j] < gzr)
								continue;
							a3[icoll + j] = a2[icoll + j];
							continue;
						}
						if (wd >= -1) {
							int icolonnes = ip1 * colonnes;
							float gun = a2[icolonnes + j] - (a2[icolonnes + jm1] - a2[icolonnes + j]) * wd;
							if (a2[icoll + j] <= gun)
								continue;
							icolonnes = im1 * colonnes;
							float gzr = a2[icolonnes + j] - (a2[icolonnes + jp1] - a2[icolonnes + j]) * wd;
							if (a2[icoll + j] < gzr)
								continue;
							a3[icoll + j] = a2[icoll + j];
							continue;
						}

						float gun = a2[icoll + jm1] - (a2[ip1 * colonnes + jm1] - a2[icoll + jm1]) / wd;
						if (a2[icoll + j] <= gun)
							continue;
						float gzr = a2[icoll + jp1] - (a2[im1 * colonnes + jp1] - a2[icoll + jp1]) / wd;
						if (a2[icoll + j] < gzr)
							continue;
						a3[icoll + j] = a2[icoll + j];
						continue;
					}
					if ((a3[icoll + j]) == 0.) {
						if (a4[icoll + j] == 0)
							continue;
						if (a4[icoll + j] < 0) {
							float gzr = a2[icoll + jp1];
							if (a2[icoll + j] < gzr)
								continue;
							float gun = a2[icoll + jm1];
							if (a2[icoll + j] <= gun)
								continue;
							a3[icoll + j] = a2[icoll + j];
							continue;
						}
						float gzr = a2[icoll + jm1];
						if (a2[icoll + j] < gzr)
							continue;
						float gun = a2[icoll + jp1];
						if (a2[icoll + j] <= gun)
							continue;
						a3[icoll + j] = a2[icoll + j];
						continue;
					}

					float wd = a4[icoll + j] / a3[icoll + j];
					a3[icoll + j] = 0;
					if (wd >= 1) {
						float gzr = a2[icoll + jp1] + (a2[ip1 * colonnes + jp1] - a2[icoll + jp1]) / wd;
						if (a2[icoll + j] < gzr)
							continue;
						float gun = a2[icoll + jm1] + (a2[im1 * colonnes + jm1] - a2[icoll + jm1]) / wd;
						if (a2[icoll + j] <= gun)
							continue;
						a3[icoll + j] = a2[icoll + j];
						continue;
					}
					if (wd >= 0) {
						float gzr = a2[ip1 * colonnes + j] + (a2[ip1 * colonnes + jp1] - a2[ip1 * colonnes + j]) * wd;
						if (a2[icoll + j] < gzr)
							continue;
						float gun = a2[im1 * colonnes + j] + (a2[im1 * colonnes + jm1] - a2[im1 * colonnes + j]) * wd;
						if (a2[icoll + j] <= gun)
							continue;
						a3[icoll + j] = a2[icoll + j];
						continue;
					}
					if (wd >= -1) {
						int icolonnes = ip1 * colonnes;
						float gzr = a2[icolonnes + j] - (a2[icolonnes + jm1] - a2[icolonnes + j]) * wd;
						if (a2[icoll + j] < gzr)
							continue;
						icolonnes = im1 * colonnes;
						float gun = a2[icolonnes + j] - (a2[icolonnes + jp1] - a2[icolonnes + j]) * wd;
						if (a2[icoll + j] <= gun)
							continue;
						a3[icoll + j] = a2[icoll + j];
						continue;
					}
					float gzr = a2[icoll + jm1] - (a2[ip1 * colonnes + jm1] - a2[icoll + jm1]) / wd;
					if (a2[icoll + j] < gzr)
						continue;
					float gun = a2[icoll + jp1] - (a2[im1 * colonnes + jp1] - a2[icoll + jp1]) / wd;
					if (a2[icoll + j] <= gun)
						continue;
					a3[icoll + j] = a2[icoll + j];
				}
			}

			for (int i = 0; i < lignes; ++i)
				a3[i * colonnes] = 0;
			for (int i = 0; i < lignes; ++i)
				a3[i * colonnes + colonnes - 1] = 0;
			for (int i = 0; i < colonnes; ++i)
				a3[i] = 0;
			int lig_1 = lignes - 1;
			for (int i = 0; i < colonnes; ++i)
				a3[colonnes * lig_1 + i] = 0;

			/* TODO ? transfert au format int */

			img2.setDataXY(ch, Array1DUtil.floatArrayToArray(a3, img2.getDataXY(ch)));
		}
		return img2;
	}

	private float Modul(float a, float b) {
		return ((float) Math.sqrt(Math.pow(a, 2) + Math.pow(b, 2)));
	}

	private void doDeriche_step0(int lignes, int colonnes, float ad1, float ad2, float an1, float an2, float an3,
			float an4, float an11, short[] a1, float[] a2, float[] a3, double[] tabInDouble) {
		// Copy mina1 to a1
		for (int y = 0; y < lignes; ++y)
			for (int x = 0; x < colonnes; ++x)
				a1[y * colonnes + x] = (short) tabInDouble[y * colonnes + x];

		for (int i = 0; i < lignes; ++i) {
			int icolonnes = i * colonnes;
			int icol_1 = icolonnes - 1;
			int icol_2 = icolonnes - 2;
			a2[icolonnes] = an1 * a1[icolonnes];
			a2[icolonnes + 1] = an1 * a1[icolonnes + 1] + an2 * a1[icolonnes] - ad1 * a2[icolonnes];
			for (int j = 2; j < colonnes; ++j)
				a2[icolonnes + j] = an1 * a1[icolonnes + j] + an2 * a1[icol_1 + j] - ad1 * a2[icol_1 + j]
						- ad2 * a2[icol_2 + j];
		}

		int col_1 = colonnes - 1;
		int col_2 = colonnes - 2;
		int col_3 = colonnes - 3;
		for (int i = 0; i < lignes; ++i) {
			int icolonnes = i * colonnes;
			int icol_1 = icolonnes + 1;
			int icol_2 = icolonnes + 2;
			a3[icolonnes + col_1] = 0;
			a3[icolonnes + col_2] = an3 * a1[icolonnes + col_1];
			for (int j = col_3; j >= 0; --j)
				a3[icolonnes + j] = an3 * a1[icol_1 + j] + an4 * a1[icol_2 + j] - ad1 * a3[icol_1 + j]
						- ad2 * a3[icol_2 + j];
		}

		int icol_1 = lignes * colonnes;
		for (int i = 0; i < icol_1; ++i)
			a2[i] += a3[i];
	}

	private void doDeriche_step1(int lignes, int colonnes, float ad1, float ad2, float an11, float[] a2, float[] a3,
			float[] a4, float[] nf_gry) {
		/* columns top - down */
		for (int j = 0; j < colonnes; ++j) {
			a3[j] = 0;
			a3[colonnes + j] = an11 * a2[j] - ad1 * a3[j];
			for (int i = 2; i < lignes; ++i)
				a3[i * colonnes + j] = an11 * a2[(i - 1) * colonnes + j] - ad1 * a3[(i - 1) * colonnes + j]
						- ad2 * a3[(i - 2) * colonnes + j];
		}

		/* columns down top */
		int lig_1 = lignes - 1;
		int lig_2 = lignes - 2;
		int lig_3 = lignes - 3;
		for (int j = 0; j < colonnes; ++j) {
			a4[lig_1 * colonnes + j] = 0;
			a4[(lig_2 * colonnes) + j] = -an11 * a2[lig_1 * colonnes + j] - ad1 * a4[lig_1 * colonnes + j];
			for (int i = lig_3; i >= 0; --i)
				a4[i * colonnes + j] = -an11 * a2[(i + 1) * colonnes + j] - ad1 * a4[(i + 1) * colonnes + j]
						- ad2 * a4[(i + 2) * colonnes + j];
		}

		int icol_1 = colonnes * lignes;
		for (int i = 0; i < icol_1; ++i)
			a3[i] += a4[i]; /**** a2 ??? *****/

		for (int i = 0; i < lignes; i++)
			for (int j = 0; j < colonnes; j++)
				nf_gry[i * colonnes + j] = a3[i * colonnes + j];
	}

	private void doDeriche_step2(int lignes, int colonnes, float ad1, float ad2, float an1, float an2, float an3,
			float an4, float an11, short[] a1, float[] a2, float[] a3, float[] a4, float[] nf_grx) {
		for (int i = 0; i < lignes; ++i) {
			int icolonnes = i * colonnes;
			int icol_1 = icolonnes - 1;
			int icol_2 = icolonnes - 2;
			a2[icolonnes] = 0;
			a2[icolonnes + 1] = an11 * a1[icolonnes];
			for (int j = 2; j < colonnes; ++j)
				a2[icolonnes + j] = an11 * a1[icol_1 + j] - ad1 * a2[icol_1 + j] - ad2 * a2[icol_2 + j];
		}

		int col_1 = colonnes - 1;
		int col_2 = colonnes - 2;
		int col_3 = colonnes - 3;
		for (int i = 0; i < lignes; ++i) {
			int icolonnes = i * colonnes;
			int icol_1 = icolonnes + 1;
			int icol_2 = icolonnes + 2;
			a3[icolonnes + col_1] = 0;
			a3[icolonnes + col_2] = -an11 * a1[icolonnes + col_1];
			for (int j = col_3; j >= 0; --j)
				a3[icolonnes + j] = -an11 * a1[icol_1 + j] - ad1 * a3[icol_1 + j] - ad2 * a3[icol_2 + j];
		}
		int icol_1 = lignes * colonnes;
		for (int i = 0; i < icol_1; ++i)
			a2[i] += a3[i];

		/* on the columns */
		/* columns top down */
		for (int j = 0; j < colonnes; ++j) {
			a3[j] = an1 * a2[j];
			a3[colonnes + j] = an1 * a2[colonnes + j] + an2 * a2[j] - ad1 * a3[j];
			for (int i = 2; i < lignes; ++i)
				a3[i * colonnes + j] = an1 * a2[i * colonnes + j] + an2 * a2[(i - 1) * colonnes + j]
						- ad1 * a3[(i - 1) * colonnes + j] - ad2 * a3[(i - 2) * colonnes + j];
		}

		/* columns down top */
		int lig_1 = lignes - 1;
		int lig_2 = lignes - 2;
		int lig_3 = lignes - 3;

		for (int j = 0; j < colonnes; ++j) {
			a4[lig_1 * colonnes + j] = 0;
			a4[lig_2 * colonnes + j] = an3 * a2[lig_1 * colonnes + j] - ad1 * a4[lig_1 * colonnes + j];
			for (int i = lig_3; i >= 0; --i)
				a4[i * colonnes + j] = an3 * a2[(i + 1) * colonnes + j] + an4 * a2[(i + 2) * colonnes + j]
						- ad1 * a4[(i + 1) * colonnes + j] - ad2 * a4[(i + 2) * colonnes + j];
		}

		icol_1 = colonnes * lignes;
		for (int i = 0; i < icol_1; ++i)
			a3[i] += a4[i];

		for (int i = 0; i < lignes; i++)
			for (int j = 0; j < colonnes; j++)
				nf_grx[i * colonnes + j] = a3[i * colonnes + j];
	}

	private void doDeriche_step3(int lignes, int colonnes, float[] a2, float[] a3, float[] nf_gry) {
		/* the magnitude computation */
		for (int i = 0; i < lignes; i++)
			for (int j = 0; j < colonnes; j++)
				a2[i * colonnes + j] = nf_gry[i * colonnes + j];
		int icol_1 = colonnes * lignes;
		for (int i = 0; i < icol_1; ++i)
			a2[i] = Modul(a2[i], a3[i]);
	}

}
