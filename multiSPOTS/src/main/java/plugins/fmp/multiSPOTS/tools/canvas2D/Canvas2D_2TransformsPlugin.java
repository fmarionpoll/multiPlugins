package plugins.fmp.multiSPOTS.tools.canvas2D;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.plugin.abstract_.Plugin;
import icy.plugin.interface_.PluginCanvas;

public class Canvas2D_2TransformsPlugin extends Plugin implements PluginCanvas {
	@Override
	public String getCanvasClassName() {
		return Canvas2D_2TransformsPlugin.class.getName();
	}

	@Override
	public IcyCanvas createCanvas(Viewer viewer) {
		return new Canvas2D_2Transforms(viewer);
	}

}
