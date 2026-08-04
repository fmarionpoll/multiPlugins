package plugins.fmp.multitools.tools.toExcel.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum EnumXLSColumnHeader {
	PATH("Path", 0, EnumColumnType.COMMON), // 0
	DATE("Date", 1, EnumColumnType.COMMON), // 1
	EXP_ID("Exp_ID", 2, EnumColumnType.COMMON), // 2
	CAM("Cam", 3, EnumColumnType.COMMON), // 3
	CAM_SAMPLE_S("Cam_sample_s", 4, EnumColumnType.COMMON), // 4
	ANALYSIS_BIN_S("Analysis_bin_s", 5, EnumColumnType.COMMON), // 5
	//
	EXP_EXPT("Expmt", 6, EnumColumnType.COMMON), // 6
	EXP_STIM1("Stim1", 7, EnumColumnType.COMMON), // 7
	EXP_CONC1("Conc1", 8, EnumColumnType.COMMON), // 8
	EXP_STIM2("Stim2", 9, EnumColumnType.COMMON), // 9
	EXP_CONC2("Conc2", 10, EnumColumnType.COMMON), // 10
	EXP_STRAIN("Strain", 11, EnumColumnType.COMMON), // 11
	EXP_SEX("Sex", 12, EnumColumnType.COMMON), // 12

	//
	CAGEID("Cage_ID", 13, EnumColumnType.COMMON), // 13
	CAGEPOS("Cage_position", 14, EnumColumnType.COMMON), // 14
	CAGE_NFLIES("Cage_nflies", 15, EnumColumnType.COMMON), // 15
	CAGE_STRAIN("Cage_strain", 16, EnumColumnType.COMMON), // 16
	CAGE_SEX("Cage_sex", 17, EnumColumnType.COMMON), // 17
	CAGE_AGE("Cage_age", 18, EnumColumnType.COMMON), // 18
	CAGE_COMMENT("Cage_comment", 19, EnumColumnType.COMMON), // 19
	CAGE_FOOD_SIDE("Cage_foodSide", 20, EnumColumnType.COMMON), // 20

	//
	SPOT_INDEX("spot_index", 21, EnumColumnType.SPOT), // 21
	SPOT_CAGEROW("spot_cageRow", 22, EnumColumnType.SPOT), // 22
	SPOT_CAGECOL("spot_cageCol", 23, EnumColumnType.SPOT), // 23
	SPOT_VOLUME("Spot_ul", 24, EnumColumnType.SPOT), // 24
	SPOT_PIXELS("Spot_npixels", 25, EnumColumnType.SPOT), // 25
	SPOT_STIM("Spot_stimulus", 26, EnumColumnType.SPOT), // 26
	SPOT_CONC("Spot_concentration", 27, EnumColumnType.SPOT), // 27
	SPOT_NFLIES("Spot_nflies", 28, EnumColumnType.SPOT), // 28
	SPOT_COLOR("Spot_color", 29, EnumColumnType.SPOT), // 29
	DUM5("Spot_measure", 30, EnumColumnType.SPOT),
	//
	CAP("Cap", 21, EnumColumnType.CAP), //
	CAP_INDEX("Cap_ID", 22, EnumColumnType.CAP), //
	CAP_VOLUME("Cap_ul", 23, EnumColumnType.CAP), //
	CAP_PIXELS("Cap_npixels", 24, EnumColumnType.CAP), //
	CAP_STIM("Cap_stimulus", 25, EnumColumnType.CAP), //
	CAP_CONC("Cap_concentration", 26, EnumColumnType.CAP), //
	CAP_NFLIES("Cap_nflies", 27, EnumColumnType.CAP), //
	CAP_COMMENT("Cap_comment", 28, EnumColumnType.CAP), //
	DUM4("Cap_measure", 29, EnumColumnType.CAP);

	private final String name;
	private int value;
	private final EnumColumnType type;

	EnumXLSColumnHeader(String label, int value, EnumColumnType type) {
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

	public void setValue(int newValue) {
		this.value = newValue;
	}

	static final Map<String, EnumXLSColumnHeader> names = Arrays.stream(EnumXLSColumnHeader.values())
			.collect(Collectors.toMap(EnumXLSColumnHeader::getName, Function.identity()));

	public static EnumXLSColumnHeader fromName(final String name) {
		return names.get(name);
	}

	public String toString() {
		return name;
	}

	public EnumColumnType toType() {
		return type;
	}

	public static EnumXLSColumnHeader findByText(String abbr) {
		for (EnumXLSColumnHeader v : values()) {
			if (v.toString().equals(abbr))
				return v;
		}
		return null;
	}
}
