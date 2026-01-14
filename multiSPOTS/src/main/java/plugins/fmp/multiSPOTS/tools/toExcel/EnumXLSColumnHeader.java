package plugins.fmp.multiSPOTS.tools.toExcel;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum EnumXLSColumnHeader {
	PATH("Path", 0), DATE("Date", 1), EXP_BOXID("Box_ID", 2), CAM("Cam", 3), EXP_EXPT("Expmt", 4), CAGEID("Cage_ID", 5),
	EXP_STIM("Stim", 6), EXP_CONC("Conc", 7), EXP_STRAIN("Strain", 8), EXP_SEX("Sex", 9), EXP_COND1("Cond1", 10),
	EXP_COND2("Cond2", 11), CAGEPOS("Cap", 12), CAP_VOLUME("Cap_ul", 13), CAP_PIXELS("Cap_npixels", 14),
	CHOICE_NOCHOICE("Choice", 15), CAP_STIM("Cap_stimulus", 16), CAP_CONC("Cap_concentration", 17),
	SPOT_NFLIES("Nflies", 18), SPOT_CAGEID("Cage", 19), DUM4("Dum4", 20), CAGE_STRAIN("Cage_strain", 21),
	CAGE_SEX("Cage_sex", 22), CAGE_AGE("Cage_age", 23), CAGE_COMMENT("Cage_comment", 24);

	private final String name;
	private final int value;

	EnumXLSColumnHeader(String label, int value) {
		this.name = label;
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public int getValue() {
		return value;
	}

	static final Map<String, EnumXLSColumnHeader> names = Arrays.stream(EnumXLSColumnHeader.values())
			.collect(Collectors.toMap(EnumXLSColumnHeader::getName, Function.identity()));

	static final Map<Integer, EnumXLSColumnHeader> values = Arrays.stream(EnumXLSColumnHeader.values())
			.collect(Collectors.toMap(EnumXLSColumnHeader::getValue, Function.identity()));

	public static EnumXLSColumnHeader fromName(final String name) {
		return names.get(name);
	}

	public static EnumXLSColumnHeader fromValue(final int value) {
		return values.get(value);
	}

	public String toString() {
		return name;
	}

	public static EnumXLSColumnHeader findByText(String abbr) {
		for (EnumXLSColumnHeader v : values()) {
			if (v.toString().equals(abbr))
				return v;
		}
		return null;
	}
}
