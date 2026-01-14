package plugins.fmp.multiSPOTS.tools.overlay;

import java.awt.Color;

import icy.image.colormap.IcyColorMap;
import icy.util.ColorUtil;

public class OverlayColorMap extends IcyColorMap {
	/*
	 * Creates a simple color map using a linear gradient from 'colorFrom' to
	 * 'colorTo'.
	 */
	public OverlayColorMap(String mapName, Color colorMask) {
		this(mapName, colorMask, IcyColorMapType.RGB);
	}

	public OverlayColorMap(String mapName, Color colorMask, IcyColorMapType type) {
		super(mapName, type);
		beginUpdate();
		try {
			int maskposition = MAX_INDEX;
			int flatstart = 0;
			int flatend = MAX_INDEX - 1;
			int OFF = 0xFF;
			int ON = 0x00;
			Color colorBackground = new Color(0x00000000, true);

			red.setControlPoint(flatstart, colorBackground.getRed());
			green.setControlPoint(flatstart, colorBackground.getGreen());
			blue.setControlPoint(flatstart, colorBackground.getBlue());
			gray.setControlPoint(flatstart, ColorUtil.getGrayMix(colorBackground));
			alpha.setControlPoint(flatstart, ON);

			red.setControlPoint(flatend, colorBackground.getRed());
			green.setControlPoint(flatend, colorBackground.getGreen());
			blue.setControlPoint(flatend, colorBackground.getBlue());
			gray.setControlPoint(flatend, ColorUtil.getGrayMix(colorBackground));
			alpha.setControlPoint(flatend, ON);

			red.setValue(maskposition, colorMask.getRed());
			green.setValue(maskposition, colorMask.getGreen());
			blue.setValue(maskposition, colorMask.getBlue());
			gray.setValue(maskposition, ColorUtil.getGrayMix(colorBackground));
			alpha.setValue(maskposition, OFF);
		} finally {
			endUpdate();
		}
	}
}
