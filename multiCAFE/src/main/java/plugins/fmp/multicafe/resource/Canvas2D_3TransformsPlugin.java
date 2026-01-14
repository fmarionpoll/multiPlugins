package plugins.fmp.multicafe.resource;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.plugin.abstract_.Plugin;
import icy.plugin.interface_.PluginCanvas;

public class Canvas2D_3TransformsPlugin extends Plugin implements PluginCanvas {
	@Override
	public String getCanvasClassName() {
		return Canvas2D_3Transforms.class.getName();
	}

	@Override
	public IcyCanvas createCanvas(Viewer viewer) {
		return new Canvas2D_3Transforms(viewer);
	}

}
