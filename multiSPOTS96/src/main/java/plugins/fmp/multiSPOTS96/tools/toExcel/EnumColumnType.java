package plugins.fmp.multiSPOTS96.tools.toExcel;

public enum EnumColumnType {
	SPOT("spot"), MOVE("move"), COMMON("common"), DESCRIPTOR_STR("descriptor_str"), DESCRIPTOR_INT("descriptor_int"), MEASURE("measure");

	private String label;

	EnumColumnType(String label) {
		this.label = label;
	}

	public String toString() {
		return label;
	}
}
