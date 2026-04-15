package plugins.fmp.multitools.tools.toExcel.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum EnumXLSQueryColumnHeader {
	QDATE("Date", 0, EnumColumnType.DESCRIPTOR_STR), //
	QEXP_ID("Exp_ID", 1, EnumColumnType.DESCRIPTOR_STR), //
	QCAGEID("Cage_ID", 2, EnumColumnType.DESCRIPTOR_STR), //
	QEXP_EXPT("Expmt", 3, EnumColumnType.DESCRIPTOR_STR), //
	QEXP_STRAIN("Strain", 4, EnumColumnType.DESCRIPTOR_STR), //
	QEXP_SEX("Sex", 5, EnumColumnType.DESCRIPTOR_STR), //
	QEXP_STIM1("Stim1", 6, EnumColumnType.DESCRIPTOR_STR), //
	QEXP_CONC1("Conc1", 7, EnumColumnType.DESCRIPTOR_STR), //
	QEXP_STIM2("Stim2", 8, EnumColumnType.DESCRIPTOR_STR), //
	QEXP_CONC2("Conc2", 9, EnumColumnType.DESCRIPTOR_STR), //
	QCAGE_POS("Position", 10, EnumColumnType.DESCRIPTOR_INT), //
	QCAGE_NFLIES("NFlies", 11, EnumColumnType.DESCRIPTOR_INT), //
	QCAGE_STRAIN("Cage_strain", 12, EnumColumnType.DESCRIPTOR_STR), //
	QCAGE_SEX("Cage_sex", 13, EnumColumnType.DESCRIPTOR_STR), //
	QCAGE_AGE("Cage_age", 14, EnumColumnType.DESCRIPTOR_INT), //
	QCAGE_COMMENT("Cage_comment", 15, EnumColumnType.DESCRIPTOR_STR), //
	QDUM4("Dum4", 16, EnumColumnType.DESCRIPTOR_STR), //

	QVAL_TIME("time", 17, EnumColumnType.MEASURE), //
	QVAL_STIM1("value1", 18, EnumColumnType.MEASURE), //
	QN_STIM1("n_spots_value1", 19, EnumColumnType.DESCRIPTOR_INT), //
	QVAL_STIM2("value2", 20, EnumColumnType.MEASURE), //
	QN_STIM2("n_spots_value2", 21, EnumColumnType.DESCRIPTOR_INT), //
	QVAL_SUM("sum", 22, EnumColumnType.MEASURE), //
	QVAL_PI("PI", 23, EnumColumnType.MEASURE);

	private final String name;
	private int value;
	private final EnumColumnType type;

	EnumXLSQueryColumnHeader(String label, int value, EnumColumnType type) {
		this.name = label;
		this.value = value;
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int val) {
		this.value = val;
	}

	static final Map<String, EnumXLSQueryColumnHeader> names = Arrays.stream(EnumXLSQueryColumnHeader.values())
			.collect(Collectors.toMap(EnumXLSQueryColumnHeader::getName, Function.identity()));

	static final Map<Integer, EnumXLSQueryColumnHeader> values = Arrays.stream(EnumXLSQueryColumnHeader.values())
			.collect(Collectors.toMap(EnumXLSQueryColumnHeader::getValue, Function.identity()));

	public static EnumXLSQueryColumnHeader fromName(final String name) {
		return names.get(name);
	}

	public static EnumXLSQueryColumnHeader fromValue(final int value) {
		return values.get(value);
	}

	public String toString() {
		return name;
	}

	public EnumColumnType toType() {
		return type;
	}

	public static EnumXLSQueryColumnHeader findByText(String abbr) {
		for (EnumXLSQueryColumnHeader v : values()) {
			if (v.toString().equals(abbr))
				return v;
		}
		return null;
	}
}
