package plugins.fmp.multiSPOTS96.tools.toExcel.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum EnumXLS_QueryColumnHeader {
	DATE("Date", 0, EnumColumnType.DESCRIPTOR_STR), //
	EXP_BOXID("Box_ID", 1, EnumColumnType.DESCRIPTOR_STR), //
	CAGEID("Cage_ID", 2, EnumColumnType.DESCRIPTOR_STR), //
	EXP_EXPT("Expmt", 3, EnumColumnType.DESCRIPTOR_STR), //
	EXP_STRAIN("Strain", 4, EnumColumnType.DESCRIPTOR_STR), //
	EXP_SEX("Sex", 5, EnumColumnType.DESCRIPTOR_STR), //
	EXP_STIM1("Stim1", 6, EnumColumnType.DESCRIPTOR_STR), //
	EXP_CONC1("Conc1", 7, EnumColumnType.DESCRIPTOR_STR), //
	EXP_STIM2("Stim2", 8, EnumColumnType.DESCRIPTOR_STR), //
	EXP_CONC2("Conc2", 9, EnumColumnType.DESCRIPTOR_STR), //
	CAGE_POS("Position", 10, EnumColumnType.DESCRIPTOR_INT), //
	CAGE_NFLIES("NFlies", 11, EnumColumnType.DESCRIPTOR_INT), //
	CAGE_STRAIN("Cage_strain", 12, EnumColumnType.DESCRIPTOR_STR), //
	CAGE_SEX("Cage_sex", 13, EnumColumnType.DESCRIPTOR_STR), //
	CAGE_AGE("Cage_age", 14, EnumColumnType.DESCRIPTOR_INT), //
	CAGE_COMMENT("Cage_comment", 15, EnumColumnType.DESCRIPTOR_STR), //
	DUM4("Dum4", 16, EnumColumnType.DESCRIPTOR_STR), //
	
	VAL_TIME("time", 17, EnumColumnType.MEASURE), //
	VAL_STIM1("value1", 18, EnumColumnType.MEASURE), //
	N_STIM1("n_spots_value1", 19, EnumColumnType.DESCRIPTOR_INT), // 
	VAL_STIM2("value2", 20, EnumColumnType.MEASURE), //
	N_STIM2("n_spots_value2", 21, EnumColumnType.DESCRIPTOR_INT), //
	VAL_SUM("sum", 22, EnumColumnType.MEASURE), //
	VAL_PI("PI", 23, EnumColumnType.MEASURE);

	private final String name;
	private int value;
	private final EnumColumnType type;

	EnumXLS_QueryColumnHeader(String label, int value, EnumColumnType type) {
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

	static final Map<String, EnumXLS_QueryColumnHeader> names = Arrays.stream(EnumXLS_QueryColumnHeader.values())
			.collect(Collectors.toMap(EnumXLS_QueryColumnHeader::getName, Function.identity()));

	static final Map<Integer, EnumXLS_QueryColumnHeader> values = Arrays.stream(EnumXLS_QueryColumnHeader.values())
			.collect(Collectors.toMap(EnumXLS_QueryColumnHeader::getValue, Function.identity()));

	public static EnumXLS_QueryColumnHeader fromName(final String name) {
		return names.get(name);
	}

	public static EnumXLS_QueryColumnHeader fromValue(final int value) {
		return values.get(value);
	}

	public String toString() {
		return name;
	}

	public EnumColumnType toType() {
		return type;
	}

	public static EnumXLS_QueryColumnHeader findByText(String abbr) {
		for (EnumXLS_QueryColumnHeader v : values()) {
			if (v.toString().equals(abbr))
				return v;
		}
		return null;
	}
}
