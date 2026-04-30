package plugins.fmp.multitools.tools.csv;

/**
 * Locale-safe parsing for numeric cells in semicolon-separated CSV files.
 */
public final class CsvNumberParsing {

	private CsvNumberParsing() {
	}

	public static double parseDouble(String raw) throws NumberFormatException {
		if (raw == null) {
			throw new NumberFormatException("null");
		}
		String s = raw.trim();
		if (s.isEmpty()) {
			throw new NumberFormatException("empty");
		}
		int comma = s.lastIndexOf(',');
		int dot = s.lastIndexOf('.');
		if (comma >= 0 && dot >= 0) {
			if (comma > dot) {
				s = s.replace(".", "").replace(',', '.');
			} else {
				s = s.replace(",", "");
			}
		} else if (comma >= 0) {
			s = s.replace(',', '.');
		}
		return Double.parseDouble(s);
	}
}
