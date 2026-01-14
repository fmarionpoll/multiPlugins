package plugins.fmp.multitools.fmp_experiment.sequence;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileNameTimePattern {

	public DateFormat dateFormat = null;
	public String patternString = null;
	public Pattern patternCompiled = null;

	long timeFirstImageInMs = 0;

	FileNameTimePattern(String dateFormatString, String patternString) {

		this.dateFormat = new SimpleDateFormat(dateFormatString);
		this.patternString = patternString;
		patternCompiled = Pattern.compile(patternString);
	}

	public FileNameTimePattern() {

	}

	long getTimeFromString(String fileName, int t) {

		if (dateFormat == null)
			return getDummyTime(t);

		long timeInMs = 0;
		Matcher m = patternCompiled.matcher(fileName);
		if (m.find()) {
			try {
				Date date = dateFormat.parse(m.group(0)); // DateFormat
				timeInMs = date.getTime();
			} catch (ParseException e) {
				e.printStackTrace();
				System.out.println("FileNamePatterns:getTimeFromString() Error parsing filename: " + fileName);
				timeInMs = getDummyTime(t);
			}

		} else {
			System.out.println("FileNamePatterns:getTimeFromString() Error finding time in filename: " + fileName);
			timeInMs = getDummyTime(t);
		}
		return timeInMs;
	}

	public boolean findMatch(String fileName) {
		Matcher m = patternCompiled.matcher(fileName);
		return m.find();
	}

	public long getDummyTime(int t) {

		if (timeFirstImageInMs == 0)
			timeFirstImageInMs = System.currentTimeMillis();

		return timeFirstImageInMs + t * 60 * 1000;
	}

}
