package plugins.fmp.multiSPOTS96.tools.JComponents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.swing.JComboBox;

import icy.gui.frame.progress.ProgressFrame;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.tools.Comparators;
import plugins.fmp.multiSPOTS96.tools.LazyExperiment;
import plugins.fmp.multiSPOTS96.tools.LazyExperiment.ExperimentMetadata;
import plugins.fmp.multiSPOTS96.tools.toExcel.EnumXLSColumnHeader;
import plugins.fmp.multiSPOTS96.tools.toExcel.XLSExportOptions;

/**
 * Memory-optimized version of JComboBoxExperiment that uses LazyExperiment
 * objects for reduced memory footprint when handling large numbers of
 * experiments.
 * 
 * <p>
 * Key features:
 * <ul>
 * <li><strong>Lazy Loading</strong>: Only loads full experiment data when
 * needed</li>
 * <li><strong>Metadata Storage</strong>: Stores lightweight metadata for all
 * experiments</li>
 * <li><strong>Memory Efficient</strong>: Dramatically reduces memory usage for
 * large datasets</li>
 * <li><strong>Backward Compatible</strong>: Maintains same interface as
 * JComboBoxExperiment</li>
 * </ul>
 * </p>
 * 
 * @author MultiSPOTS96
 * @version 2.0.0
 */
public class JComboBoxExperimentLazy extends JComboBox<Experiment> {

	private static final long serialVersionUID = 2L;
	private static final Logger LOGGER = Logger.getLogger(JComboBoxExperimentLazy.class.getName());

	public int index0 = 0;
	public int index1 = 0;
	public int maxSizeOfSpotsArrays = 0;
	public String stringExpBinSubDirectory = null;

	// Metadata storage for lazy loading
	private List<ExperimentMetadata> experimentMetadataList = new ArrayList<>();

	public JComboBoxExperimentLazy() {
	}

	@Override
	public void removeAllItems() {
		super.removeAllItems();
		stringExpBinSubDirectory = null;
		experimentMetadataList.clear();
	}

	public int addExperiment(Experiment exp) {
		LazyExperiment lazyExp = convertToLazyExperiment(exp);
		return addLazyExperiment(lazyExp);
	}

	public int addLazyExperiment(LazyExperiment lazyExp) {
		int index = isFoundAt(lazyExp);
		if (index < 0) {
			experimentMetadataList.add(lazyExp.getMetadata());
			addItem(lazyExp);
			return getItemCount() - 1;
		}
		return index;
	}

	public void addLazyExperimentsBulk(List<LazyExperiment> lazyExperiments) {
		for (LazyExperiment lazyExp : lazyExperiments) {
			if (isUnique(lazyExp)) {
				experimentMetadataList.add(lazyExp.getMetadata());
				addItem(lazyExp);
			}
		}
	}

	private int isFoundAt(LazyExperiment newLazyExperiment) {
		int newCode = newLazyExperiment.getMetadata().hashCode();
		int index = -1;
		for (ExperimentMetadata expData : experimentMetadataList) {
			int codeFromList = expData.hashCode();
			if (codeFromList == newCode) {
				return index;
			}
			index++;
		}
		return -1;
	}

	private boolean isUnique(LazyExperiment newLazyExperiment) {
		int newCode = newLazyExperiment.getMetadata().hashCode();
		for (ExperimentMetadata expData : experimentMetadataList) {
			int codeFromList = expData.hashCode();
			if (codeFromList == newCode) {
				return false;
			}
		}
		return true;
	}

	private LazyExperiment convertToLazyExperiment(Experiment exp) {
		if (exp instanceof LazyExperiment) {
			return (LazyExperiment) exp;
		}

		// Create metadata from the experiment
		ExperimentMetadata metadata = new ExperimentMetadata(
				exp.seqCamData != null ? exp.seqCamData.getImagesDirectory() : exp.toString(),
				exp.getResultsDirectory(), exp.getBinDirectory());
		return new LazyExperiment(metadata);
	}

	@Override
	public Experiment getItemAt(int index) {
		Object item = super.getItemAt(index);
		if (item instanceof Experiment) {
			Experiment exp = (Experiment) item;
			if (exp instanceof LazyExperiment) {
				((LazyExperiment) exp).loadIfNeeded();
			}
			return exp;
		}
		return null;
	}

	/**
	 * Return item without triggering lazy loading. Use this for UI operations that
	 * only need lightweight metadata or descriptor properties.
	 */
	public Experiment getItemAtNoLoad(int index) {
		Object item = super.getItemAt(index);
		if (item instanceof Experiment) {
			return (Experiment) item;
		}
		return null;
	}

	@Override
	public Experiment getSelectedItem() {
		Object selected = super.getSelectedItem();
		if (selected instanceof Experiment) {
			Experiment exp = (Experiment) selected;
			if (exp instanceof LazyExperiment) {
				((LazyExperiment) exp).loadIfNeeded();
			}
			return exp;
		}
		return null;
	}

	public Experiment get_MsTime_of_StartAndEnd_AllExperiments(XLSExportOptions options) {
		Experiment expAll = new Experiment();
		Experiment exp0 = getItemAt(0);
		if (options.fixedIntervals) {
			expAll.seqCamData.getTimeManager().setFirstImageMs(options.startAll_Ms);
			expAll.seqCamData.setLastImageMs(options.endAll_Ms);
		} else {
			if (options.absoluteTime) {
				Experiment expFirst = exp0.getFirstChainedExperiment(options.collateSeries);
				expAll.setFileTimeImageFirst(expFirst.firstImage_FileTime);
				Experiment expLast = exp0.getLastChainedExperiment(options.collateSeries);
				expAll.setFileTimeImageLast(expLast.lastImage_FileTime);
				for (int i = 0; i < getItemCount(); i++) {
					Experiment exp = getItemAt(i);
					expFirst = exp.getFirstChainedExperiment(options.collateSeries);
					if (expAll.firstImage_FileTime.compareTo(expFirst.firstImage_FileTime) > 0)
						expAll.setFileTimeImageFirst(expFirst.firstImage_FileTime);
					expLast = exp.getLastChainedExperiment(options.collateSeries);
					if (expAll.lastImage_FileTime.compareTo(expLast.lastImage_FileTime) < 0)
						expAll.setFileTimeImageLast(expLast.lastImage_FileTime);
				}
				expAll.seqCamData.setFirstImageMs(expAll.firstImage_FileTime.toMillis());
				expAll.seqCamData.setLastImageMs(expAll.lastImage_FileTime.toMillis());
			} else {
				expAll.seqCamData.setFirstImageMs(0);
				expAll.seqCamData.setLastImageMs(exp0.seqCamData.getTimeManager().getBinLast_ms()
						- exp0.seqCamData.getTimeManager().getBinFirst_ms());
				long firstOffset_Ms = 0;
				long lastOffset_Ms = 0;

				for (int i = 0; i < getItemCount(); i++) {
					Experiment exp = getItemAt(i);
					Experiment expFirst = exp.getFirstChainedExperiment(options.collateSeries);
					firstOffset_Ms = expFirst.seqCamData.getTimeManager().getBinFirst_ms()
							+ expFirst.seqCamData.getFirstImageMs();
					exp.chainImageFirst_ms = expFirst.seqCamData.getFirstImageMs()
							+ expFirst.seqCamData.getTimeManager().getBinFirst_ms();

					Experiment expLast = exp.getLastChainedExperiment(options.collateSeries);
					if (expLast.seqCamData.getTimeManager().getBinLast_ms() <= 0) {
						expLast.seqCamData.getTimeManager().setBinLast_ms(
								expLast.seqCamData.getLastImageMs() - expLast.seqCamData.getFirstImageMs());
					}
					lastOffset_Ms = expLast.seqCamData.getTimeManager().getBinLast_ms()
							+ expLast.seqCamData.getFirstImageMs();

					long diff = lastOffset_Ms - firstOffset_Ms;
					if (diff < 1) {
						System.out.println("ExperimentCombo:get_MsTime_of_StartAndEnd_AllExperiments() Expt # " + i
								+ ": FileTime difference between last and first image < 1; set dt between images = 1 ms");
						diff = exp.seqCamData.getSequence().getSizeT();
					}
					if (expAll.seqCamData.getLastImageMs() < diff)
						expAll.seqCamData.setLastImageMs(diff);
				}
			}
		}
		return expAll;
	}

	public boolean loadListOfMeasuresFromAllExperiments(boolean loadSpots, boolean loadDrosoTrack) {
		ProgressFrame progress = new ProgressFrame("Load experiment(s) parameters");
		int nexpts = getItemCount();

		maxSizeOfSpotsArrays = 0;
		progress.setLength(nexpts);
		boolean flag = true;

		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("loadAllExperiments");
		processor.setPriority(Processor.NORM_PRIORITY);
		ArrayList<Future<?>> futuresArray = new ArrayList<Future<?>>(nexpts);
		futuresArray.clear();

		for (int i = 0; i < nexpts; i++) {
			final int it = i;
			final Experiment exp = getItemAt(it);

			futuresArray.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					progress.setMessage("Load experiment " + it + " of " + nexpts);

					// Ensure the experiment is loaded if it's a LazyExperiment
					if (exp instanceof LazyExperiment) {
						((LazyExperiment) exp).loadIfNeeded();
					}

					exp.load_MS96_experiment();
					exp.load_MS96_cages();
					if (loadSpots)
						exp.load_MS96_spotsMeasures();

					if (loadDrosoTrack)
						exp.zopenPositionsMeasures();

					int nCages = exp.cagesArray.cagesList.size();
					int nSpotsPerCage = exp.cagesArray.nColumnsPerCage * exp.cagesArray.nRowsPerCage;
					int nMaxSpots = nCages * nSpotsPerCage;
					if (maxSizeOfSpotsArrays < nMaxSpots) {
						maxSizeOfSpotsArrays = nMaxSpots;
						if (maxSizeOfSpotsArrays % 2 != 0)
							maxSizeOfSpotsArrays += 1;
					}
					progress.incPosition();
				}
			}));
		}
		waitFuturesCompletion(processor, futuresArray, progress);

		progress.close();
		return flag;
	}

	protected void waitFuturesCompletion(Processor processor, ArrayList<Future<?>> futuresArray,
			ProgressFrame progressBar) {
		int frame = 1;
		int nframes = futuresArray.size();
		while (!futuresArray.isEmpty()) {
			final Future<?> f = futuresArray.get(futuresArray.size() - 1);
			if (progressBar != null)
				progressBar.setMessage("Analyze experiment: " + (frame) + "//" + nframes);
			try {
				f.get();
			} catch (ExecutionException e) {
				System.out.println("ExperimentCombo:waitFuturesCompletion() - Warning: " + e);
			} catch (InterruptedException e) {
				// ignore
			}
			futuresArray.remove(f);
			frame++;
		}
	}

	public void setFirstImageForAllExperiments(boolean collate) {
		for (int i = 0; i < getItemCount(); i++) {
			Experiment expi = getItemAt(i);
			Experiment expFirst = expi.getFirstChainedExperiment(collate);
			expi.chainImageFirst_ms = expFirst.seqCamData.getFirstImageMs()
					+ expFirst.seqCamData.getTimeManager().getBinFirst_ms();
		}
	}

	private void resetChaining(Experiment expi) {
		expi.chainToPreviousExperiment = null;
		expi.chainToNextExperiment = null;
	}

	public void chainExperimentsUsingKymoIndexes(boolean collate) {
		for (int i = 0; i < getItemCount(); i++) {
			Experiment expi = getItemAt(i);
			if (!collate) {
				resetChaining(expi);
				continue;
			}
			if (expi.chainToNextExperiment != null || expi.chainToPreviousExperiment != null)
				continue;

			List<Experiment> list = new ArrayList<Experiment>();
			list.add(expi);

			for (int j = 0; j < getItemCount(); j++) {
				if (i == j)
					continue;
				Experiment expj = getItemAt(j);
				if (!expi.getProperties().isSameProperties(expj.getProperties()))
					continue;
				if (expj.chainToNextExperiment != null || expj.chainToPreviousExperiment != null)
					continue;
				list.add(expj);
			}

			if (list.size() < 2)
				continue;

			Collections.sort(list, new Comparators.Experiment_Start());
			for (int k = 0; k < list.size(); k++) {
				Experiment expk = list.get(k);
				if (k > 0)
					expk.chainToPreviousExperiment = list.get(k - 1);
				if (k < (list.size() - 1))
					expk.chainToNextExperiment = list.get(k + 1);
			}
		}
	}

	public int getExperimentIndexFromExptName(String filename) {
		int position = -1;
		if (filename != null) {
			for (int i = 0; i < getItemCount(); i++) {
				Object item = super.getItemAt(i);
				if (item instanceof Experiment) {
					Experiment exp = (Experiment) item;
					if (filename.equals(exp.toString())) {
						position = i;
						break;
					}
				}
			}
		}
		return position;
	}

	public Experiment getExperimentFromExptName(String filename) {
		Experiment exp = null;
		for (int i = 0; i < getItemCount(); i++) {
			Object item = super.getItemAt(i);
			if (item instanceof Experiment) {
				Experiment experiment = (Experiment) item;
				String expString = experiment.toString();
				if (filename.equals(expString)) {
					exp = experiment;
					break;
				}
			}
		}
		return exp;
	}

	public List<String> getFieldValuesFromAllExperimentsLightweight(EnumXLSColumnHeader field) {
		List<String> textList = new ArrayList<>();
		for (int i = 0; i < getItemCount(); i++) {
			Object item = super.getItemAt(i);
			if (item instanceof LazyExperiment) {
				LazyExperiment lazyExp = (LazyExperiment) item;
				String fieldValue = lazyExp.getFieldValue(field);
				if (fieldValue != null && !fieldValue.isEmpty()) {
					addIfUniqueString(textList, fieldValue);
				}
			} else if (item instanceof Experiment) {
				Experiment exp = (Experiment) item;
				if (exp instanceof LazyExperiment) {
					((LazyExperiment) exp).loadIfNeeded();
				}
				exp.load_MS96_experiment();
				addIfUniqueStrings(textList, exp.getFieldValues(field));
			}
		}
		return textList;
	}

	public void addIfUniqueString(List<String> uniqueList, String newString) {
		for (String value : uniqueList) {
			if (newString.equals(value))
				return;
		}
		uniqueList.add(newString);
	}

	public void addIfUniqueStrings(List<String> uniqueList, List<String> newList) {
		for (String newString : newList)
			addIfUniqueString(uniqueList, newString);
	}

	public void getFieldValuesToComboLightweight(JComboBox<String> combo, EnumXLSColumnHeader header) {
		combo.removeAllItems();
		List<String> textList = getFieldValuesFromAllExperimentsLightweight(header);
		java.util.Collections.sort(textList);
		for (String text : textList)
			combo.addItem(text);
	}

	public List<Experiment> getExperimentsAsList() {
		int nitems = getItemCount();
		List<Experiment> expList = new ArrayList<Experiment>(nitems);
		for (int i = 0; i < nitems; i++)
			expList.add(getItemAt(i));
		return expList;
	}

	/**
	 * Returns experiments list without triggering lazy loading.
	 */
	public List<Experiment> getExperimentsAsListNoLoad() {
		int nitems = getItemCount();
		List<Experiment> expList = new ArrayList<Experiment>(nitems);
		for (int i = 0; i < nitems; i++) {
			Experiment exp = getItemAtNoLoad(i);
			if (exp != null)
				expList.add(exp);
		}
		return expList;
	}

	public void setExperimentsFromList(List<Experiment> listExp) {
		removeAllItems();
		for (Experiment exp : listExp) {
			Experiment lazyExp = convertToLazyExperiment(exp);
			addItem(lazyExp);
		}
	}

	public String getMemoryUsageInfo() {
		Runtime runtime = Runtime.getRuntime();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long usedMemory = totalMemory - freeMemory;

		return String.format("Memory: %dMB used, %dMB total, %d experiments loaded", usedMemory / 1024 / 1024,
				totalMemory / 1024 / 1024, experimentMetadataList.size());
	}

	public int getLoadedExperimentCount() {
		int count = 0;
		for (int i = 0; i < getItemCount(); i++) {
			Object item = super.getItemAt(i);
			if (item instanceof Experiment) {
				Experiment exp = (Experiment) item;
				if (exp instanceof LazyExperiment) {
					if (((LazyExperiment) exp).isLoaded()) {
						count++;
					}
				} else {
					count++; // Regular experiments are considered loaded
				}
			}
		}
		return count;
	}
}