package plugins.fmp.multitools.tools.JComponents;

/**
 * Column order for {@link CapillaryTableModel}. Use {@link #fromIndex(int)} instead of raw indices.
 */
public enum CapillaryTableColumn {
	NAME("Name", false),
	CAGE_ID("Cage", true),
	POSITION("Side", false),
	N_FLIES("N flies", true),
	VOLUME("Volume", true),
	STIMULUS("Stimulus", true),
	CONCENTRATION("Concentration", true);

	private final String header;
	private final boolean editable;

	CapillaryTableColumn(String header, boolean editable) {
		this.header = header;
		this.editable = editable;
	}

	public String getHeader() {
		return header;
	}

	public boolean isEditable() {
		return editable;
	}

	public static CapillaryTableColumn fromIndex(int columnIndex) {
		CapillaryTableColumn[] v = values();
		if (columnIndex < 0 || columnIndex >= v.length)
			throw new IllegalArgumentException("columnIndex: " + columnIndex);
		return v[columnIndex];
	}

	public static int countColumns() {
		return values().length;
	}
}
