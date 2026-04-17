package plugins.fmp.multiSPOTS96.canvas2D;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import icy.canvas.IcyCanvas;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;

/**
 * Compatibility layer to interact with Canvas2D_3Transforms implementations
 * coming from different plugins (e.g. multiCAFE vs multiSPOTS96).
 *
 * The ICY plugin loader can provide a canvas instance whose concrete class is
 * not the one compiled against by this module. Avoid direct casts and invoke
 * the required API by reflection.
 */
public final class Canvas2D3TransformsCompat {
	private Canvas2D3TransformsCompat() {
	}

	public static boolean is3TransformsCanvas(IcyCanvas canvas) {
		return canvas != null && canvas.getClass().getSimpleName().equals("Canvas2D_3Transforms");
	}

	public static int getTransformStep1ItemCount(IcyCanvas canvas) {
		Object result = invoke(canvas, "getTransformStep1ItemCount", new Class<?>[] {}, new Object[] {});
		return (result instanceof Integer) ? ((Integer) result).intValue() : 0;
	}

	public static void updateTransformsStep1(IcyCanvas canvas, ImageTransformEnums[] transforms) {
		invoke(canvas, "updateTransformsStep1", new Class<?>[] { ImageTransformEnums[].class }, new Object[] { transforms });
	}

	public static void setTransformStep1Index(IcyCanvas canvas, int index) {
		invoke(canvas, "setTransformStep1Index", new Class<?>[] { int.class }, new Object[] { index });
	}

	public static void setTransformStep1(IcyCanvas canvas, int index, CanvasImageTransformOptions options) {
		invoke(canvas, "setTransformStep1", new Class<?>[] { int.class, CanvasImageTransformOptions.class },
				new Object[] { index, options });
	}

	public static void setTransformStep1(IcyCanvas canvas, ImageTransformEnums transform, CanvasImageTransformOptions options) {
		invoke(canvas, "setTransformStep1", new Class<?>[] { ImageTransformEnums.class, CanvasImageTransformOptions.class },
				new Object[] { transform, options });
	}

	public static void setReferenceImage(IcyCanvas canvas, Object icyBufferedImage) {
		// Parameter type is IcyBufferedImage but we avoid linking directly here.
		invoke(canvas, "setReferenceImage", new Class<?>[] { icyBufferedImage != null ? icyBufferedImage.getClass() : Object.class },
				new Object[] { icyBufferedImage });
	}

	private static Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object[] args) {
		if (target == null)
			return null;
		try {
			Method m = findMethod(target.getClass(), methodName, paramTypes);
			if (m == null)
				return null;
			m.setAccessible(true);
			return m.invoke(target, args);
		} catch (IllegalAccessException | InvocationTargetException e) {
			return null;
		}
	}

	private static Method findMethod(Class<?> clazz, String name, Class<?>[] paramTypes) {
		try {
			return clazz.getMethod(name, paramTypes);
		} catch (NoSuchMethodException e) {
			// Try to find any public method with same name & arity (handles differing option class loaders).
			for (Method m : clazz.getMethods()) {
				if (!m.getName().equals(name))
					continue;
				if (m.getParameterCount() != paramTypes.length)
					continue;
				return m;
			}
			return null;
		}
	}
}

