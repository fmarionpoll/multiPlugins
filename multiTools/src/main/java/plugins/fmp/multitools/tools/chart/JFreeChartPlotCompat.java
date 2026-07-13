package plugins.fmp.multitools.tools.chart;

import java.awt.geom.Rectangle2D;
import java.lang.reflect.Method;

import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.Plot;

/**
 * JFreeChart helpers that work across ICY runtime versions (older 1.0.x and
 * newer 1.5.x package layouts). Avoids NoSuchMethodError when compiled
 * against one JFreeChart build and run with another.
 */
public final class JFreeChartPlotCompat {

	private JFreeChartPlotCompat() {
	}

	public static double domainJava2DToValue(ValueAxis domainAxis, double java2DValue, Rectangle2D dataArea,
			Plot plot) {
		return axisJava2DToValue(domainAxis, java2DValue, dataArea, getDomainAxisEdge(plot));
	}

	public static double rangeJava2DToValue(ValueAxis rangeAxis, double java2DValue, Rectangle2D dataArea, Plot plot) {
		return axisJava2DToValue(rangeAxis, java2DValue, dataArea, getRangeAxisEdge(plot));
	}

	public static Object getDomainAxisEdge(Plot plot) {
		Object edge = invokePlotEdgeGetter(plot, "getDomainAxisEdge");
		return edge != null ? edge : rectangleEdge("BOTTOM");
	}

	public static Object getRangeAxisEdge(Plot plot) {
		Object edge = invokePlotEdgeGetter(plot, "getRangeAxisEdge");
		return edge != null ? edge : rectangleEdge("LEFT");
	}

	private static Object invokePlotEdgeGetter(Plot plot, String methodName) {
		if (plot == null) {
			return null;
		}
		try {
			Method m = plot.getClass().getMethod(methodName);
			return m.invoke(plot);
		} catch (Exception e) {
			return null;
		}
	}

	private static double axisJava2DToValue(ValueAxis axis, double java2DValue, Rectangle2D dataArea,
			Object rectangleEdge) {
		if (axis == null || dataArea == null || rectangleEdge == null) {
			return Double.NaN;
		}
		for (Method m : axis.getClass().getMethods()) {
			if (!"java2DToValue".equals(m.getName()) || m.getParameterCount() != 3) {
				continue;
			}
			Class<?>[] params = m.getParameterTypes();
			if (params[0] != double.class) {
				continue;
			}
			if (!Rectangle2D.class.isAssignableFrom(params[1])) {
				continue;
			}
			Object edge = resolveEdgeForType(params[2], rectangleEdge);
			if (edge == null) {
				continue;
			}
			try {
				return ((Number) m.invoke(axis, java2DValue, dataArea, edge)).doubleValue();
			} catch (Exception ignored) {
			}
		}
		return Double.NaN;
	}

	private static Object resolveEdgeForType(Class<?> edgeParamType, Object preferredEdge) {
		if (edgeParamType.isInstance(preferredEdge)) {
			return preferredEdge;
		}
		if (preferredEdge instanceof Enum) {
			String name = ((Enum<?>) preferredEdge).name();
			try {
				return edgeParamType.getField(name).get(null);
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}

	private static Object rectangleEdge(String constantName) {
		Object edge = rectangleEdge(constantName, "org.jfree.chart.ui.RectangleEdge");
		if (edge != null) {
			return edge;
		}
		edge = rectangleEdge(constantName, "org.jfree.ui.RectangleEdge");
		if (edge != null) {
			return edge;
		}
		throw new IllegalStateException("RectangleEdge not available on classpath");
	}

	private static Object rectangleEdge(String constantName, String className) {
		try {
			Class<?> edgeClass = Class.forName(className);
			return edgeClass.getField(constantName).get(null);
		} catch (Exception e) {
			return null;
		}
	}
}