package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import java.awt.Dimension;

import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.type.collection.array.Array1DUtil;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarSequence;

public class ChessboardImage extends EzPlug implements Block {

	private EzVarSequence seq1;
	private EzVarSequence seq2;
	private EzVarInteger squareSize;

	private EzVarSequence seqResult;

	@Override
	protected void initialize() {
		seq1 = new EzVarSequence("Sequence 1");
		seq2 = new EzVarSequence("Sequence 2");
		squareSize = new EzVarInteger("Size of partition square (pixels)");

		addEzComponent(seq1);
		addEzComponent(seq2);
		addEzComponent(squareSize);
	}

	@Override
	public void declareInput(VarList inputMap) {
		seq1 = new EzVarSequence("Sequence 1");
		seq2 = new EzVarSequence("Sequence 2");
		squareSize = new EzVarInteger("Size of partition square (pixels)");

		inputMap.add(seq1.name, seq1.getVariable());
		inputMap.add(seq2.name, seq2.getVariable());
		inputMap.add(squareSize.name, squareSize.getVariable());
	}

	@Override
	public void declareOutput(VarList outputMap) {
		seqResult = new EzVarSequence("Result image");
		outputMap.add(seqResult.name, seqResult.getVariable());
	}

	@Override
	protected void execute() {
		Sequence s1 = seq1.getValue(true);
		Sequence s2 = seq2.getValue(true);
		int sSize = squareSize.getValue();

		Sequence result = createChessboardImage(s1, s2, sSize);

		if (isHeadLess()) {
			seqResult.setValue(result);
		} else {
			addSequence(result);
		}
	}

	@Override
	public void clean() {
	}

	public static Sequence createChessboardImage(Sequence s1, Sequence s2, int squareSize) {

		IcyBufferedImage s1Image = s1.getFirstImage();
		IcyBufferedImage s2Image = s2.getFirstImage();

		Dimension resultDim = new Dimension(Math.max(s1Image.getWidth(), s2Image.getWidth()),
				Math.max(s1Image.getHeight(), s2Image.getHeight()));
		IcyBufferedImage resultImage = new IcyBufferedImage(resultDim.width, resultDim.height, s1Image.getSizeC(),
				s1Image.getDataType_());
		resultImage.beginUpdate();

		for (int ch = 0; ch < resultImage.getSizeC(); ch++) {
			double[] resultData = Array1DUtil.arrayToDoubleArray(resultImage.getDataXY(ch),
					resultImage.isSignedDataType());
			double[] s1Data = Array1DUtil.arrayToDoubleArray(s1Image.getDataXY(ch), resultImage.isSignedDataType());
			double[] s2Data = Array1DUtil.arrayToDoubleArray(s2Image.getDataXY(ch), resultImage.isSignedDataType());

			for (int j = 0; j < resultImage.getHeight(); j++) {

				int jResultOffset = j * resultImage.getWidth();
				int js1Offset = j * s1Image.getWidth();
				int js2Offset = j * s2Image.getWidth();

				for (int i = 0; i < resultImage.getWidth(); i++) {
					boolean showS1 = (((i / squareSize) % 2) + ((j / squareSize) % 2)) % 2 == 0;
					if (showS1) {
						if (i < s1Image.getWidth() && j < s1Image.getHeight()) {
							resultData[jResultOffset + i] = s1Data[js1Offset + i];
						} else {
							resultData[jResultOffset + i] = 0;
						}
					} else {
						if (i < s2Image.getWidth() && j < s2Image.getHeight()) {
							resultData[jResultOffset + i] = s2Data[js2Offset + i];
						} else {
							resultData[jResultOffset + i] = 0;
						}
					}
				}
			}

			Array1DUtil.doubleArrayToArray(resultData, resultImage.getDataXY(ch));
		}

		resultImage.endUpdate();
		Sequence result = new Sequence("Chessboard(" + s1.getName() + s2.getName() + ")", resultImage);
		return result;
	}
}
