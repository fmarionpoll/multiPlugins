package plugins.fmp.multitools.tools.toExcel.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum EnumXLSColumnHeader {
	PATH("Path", 0, EnumColumnType.COMMON), // 0
	DATE("Date", 1, EnumColumnType.COMMON), // 1
	EXP_BOXID("Box_ID", 2, EnumColumnType.COMMON), // 2
	CAM("Cam", 3, EnumColumnType.COMMON), // 3
	EXP_EXPT("Expmt", 4, EnumColumnType.COMMON), // 4
	EXP_STIM1("Stim1", 5, EnumColumnType.COMMON), // 5
	EXP_CONC1("Conc1", 6, EnumColumnType.COMMON), // 6
	EXP_STIM2("Stim2", 7, EnumColumnType.COMMON), // 7
	EXP_CONC2("Conc2", 8, EnumColumnType.COMMON), // 8
	EXP_STRAIN("Strain", 9, EnumColumnType.COMMON), // 9
	EXP_SEX("Sex", 10, EnumColumnType.COMMON), // 10

	//
	CAGEID("Cage_ID", 11, EnumColumnType.COMMON), // 12
	CAGEPOS("Cage_position", 12, EnumColumnType.COMMON), // 13
	CAGE_NFLIES("Cage_nflies", 13, EnumColumnType.COMMON), // 11
	CAGE_STRAIN("Cage_strain", 14, EnumColumnType.COMMON), // 14
	CAGE_SEX("Cage_sex", 15, EnumColumnType.COMMON), // 15
	CAGE_AGE("Cage_age", 16, EnumColumnType.COMMON), // 16
	CAGE_COMMENT("Cage_comment", 17, EnumColumnType.COMMON), // 17
	//
	SPOT_INDEX("spot_index", 18, EnumColumnType.SPOT), // 18
	SPOT_CAGEROW("spot_cageRow", 19, EnumColumnType.SPOT), // 19
	SPOT_CAGECOL("spot_cageCol", 20, EnumColumnType.SPOT), // 20
	SPOT_VOLUME("Spot_ul", 21, EnumColumnType.SPOT), // 21
	SPOT_PIXELS("Spot_npixels", 22, EnumColumnType.SPOT), // 22
	SPOT_STIM("Spot_stimulus", 23, EnumColumnType.SPOT), // 23
	SPOT_CONC("Spot_concentration", 24, EnumColumnType.SPOT), // 24
	SPOT_NFLIES("Spot_nflies", 25, EnumColumnType.SPOT), // 25
	//
	CAP("Cap", 18, EnumColumnType.CAP), //
	CAP_INDEX("Cap_ID", 19, EnumColumnType.CAP), //
	CAP_VOLUME("Cap_ul", 20, EnumColumnType.CAP), //
	CAP_PIXELS("Cap_npixels", 21, EnumColumnType.CAP), //
	CAP_STIM("Cap_stimulus", 22, EnumColumnType.CAP), //
	CAP_CONC("Cap_concentration", 23, EnumColumnType.CAP), //
	CAP_NFLIES("Cap_nflies", 24, EnumColumnType.CAP), //
	CAP_COMMENT("Cap_comment", 25, EnumColumnType.CAP), //
	//
	DUM4("Dum4", 26, EnumColumnType.COMMON);

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
