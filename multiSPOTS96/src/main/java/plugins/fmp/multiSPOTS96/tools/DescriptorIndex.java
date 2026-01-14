package plugins.fmp.multiSPOTS96.tools;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.experiment.ExperimentProperties;
import plugins.fmp.multiSPOTS96.experiment.cages.Cage;
import plugins.fmp.multiSPOTS96.experiment.spots.Spot;
import plugins.fmp.multiSPOTS96.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multiSPOTS96.tools.toExcel.EnumXLSColumnHeader;

public class DescriptorIndex {

	private volatile boolean ready = false;

	private Map<String, ExperimentProperties> propertiesByResultsDir = new HashMap<String, ExperimentProperties>();
	private EnumMap<EnumXLSColumnHeader, TreeSet<String>> distinctByField = new EnumMap<EnumXLSColumnHeader, TreeSet<String>>(
			EnumXLSColumnHeader.class);

	public DescriptorIndex() {
		initializeDistinctMaps();
	}

	private void initializeDistinctMaps() {
		distinctByField.put(EnumXLSColumnHeader.EXP_EXPT, new TreeSet<String>());
		distinctByField.put(EnumXLSColumnHeader.EXP_BOXID, new TreeSet<String>());
		distinctByField.put(EnumXLSColumnHeader.EXP_STIM1, new TreeSet<String>());
		distinctByField.put(EnumXLSColumnHeader.EXP_CONC1, new TreeSet<String>());
		distinctByField.put(EnumXLSColumnHeader.EXP_STRAIN, new TreeSet<String>());
		distinctByField.put(EnumXLSColumnHeader.EXP_SEX, new TreeSet<String>());
		distinctByField.put(EnumXLSColumnHeader.EXP_STIM2, new TreeSet<String>());
		distinctByField.put(EnumXLSColumnHeader.EXP_CONC2, new TreeSet<String>());
		// Also cache cage and spot descriptors
		distinctByField.put(EnumXLSColumnHeader.CAGE_SEX, new TreeSet<String>());
		distinctByField.put(EnumXLSColumnHeader.CAGE_STRAIN, new TreeSet<String>());
		distinctByField.put(EnumXLSColumnHeader.CAGE_AGE, new TreeSet<String>());
		distinctByField.put(EnumXLSColumnHeader.SPOT_STIM, new TreeSet<String>());
		distinctByField.put(EnumXLSColumnHeader.SPOT_CONC, new TreeSet<String>());
		distinctByField.put(EnumXLSColumnHeader.SPOT_VOLUME, new TreeSet<String>());
	}

	public void clear() {
		ready = false;
		propertiesByResultsDir.clear();
		for (TreeSet<String> set : distinctByField.values())
			set.clear();
	}

	public boolean isReady() {
		return ready;
	}

	public void preloadFromCombo(final JComboBoxExperimentLazy combo, final Runnable onDone) {
		clear();
		final int nitems = combo.getItemCount();
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				// Build local structures to avoid partial reads
				Map<String, ExperimentProperties> propsLocal = new HashMap<String, ExperimentProperties>(nitems);
				EnumMap<EnumXLSColumnHeader, TreeSet<String>> distinctLocal = new EnumMap<EnumXLSColumnHeader, TreeSet<String>>(
						EnumXLSColumnHeader.class);
				for (Map.Entry<EnumXLSColumnHeader, TreeSet<String>> e : distinctByField.entrySet())
					distinctLocal.put(e.getKey(), new TreeSet<String>());

				for (int i = 0; i < nitems; i++) {
					Experiment exp = combo.getItemAtNoLoad(i);
					if (exp == null)
						continue;
					String resDir = exp.getResultsDirectory();
					// Prefer new descriptors file if available
					Map<EnumXLSColumnHeader, List<String>> preDicts = DescriptorsIO.readDescriptors(resDir);
					if (preDicts != null && !preDicts.isEmpty()) {
						for (Map.Entry<EnumXLSColumnHeader, List<String>> e : preDicts.entrySet()) {
							TreeSet<String> set = distinctLocal.get(e.getKey());
							if (set != null)
								set.addAll(e.getValue());
						}
						continue;
					}
					ExperimentProperties props = null;
					if (exp instanceof LazyExperiment) {
						LazyExperiment lexp = (LazyExperiment) exp;
						lexp.loadPropertiesIfNeeded();
						props = lexp.getCachedProperties();
					} else {
						exp.load_MS96_experiment();
						props = exp.getProperties();
					}
					if (props == null || resDir == null)
						continue;
					propsLocal.put(resDir, props);
					updateDistinctLocal(distinctLocal, props);

					// Load cage and spot descriptors (no image I/O) and aggregate distincts
					try {
						exp.load_MS96_cages();
						if (exp.cagesArray != null && exp.cagesArray.cagesList != null) {
							for (Cage cage : exp.cagesArray.cagesList) {
								addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.CAGE_SEX),
										cage.getField(EnumXLSColumnHeader.CAGE_SEX));
								addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.CAGE_STRAIN),
										cage.getField(EnumXLSColumnHeader.CAGE_STRAIN));
								addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.CAGE_AGE),
										cage.getField(EnumXLSColumnHeader.CAGE_AGE));
								if (cage.spotsArray != null && cage.spotsArray.getSpotsList() != null) {
									for (Spot spot : cage.spotsArray.getSpotsList()) {
										addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.SPOT_STIM),
												spot.getField(EnumXLSColumnHeader.SPOT_STIM));
										addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.SPOT_CONC),
												spot.getField(EnumXLSColumnHeader.SPOT_CONC));
										addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.SPOT_VOLUME),
												spot.getField(EnumXLSColumnHeader.SPOT_VOLUME));
									}
								}
							}
						}
					} catch (Exception ex) {
						// Ignore malformed per-experiment cage files
					}
				}

				// Publish
				propertiesByResultsDir.clear();
				propertiesByResultsDir.putAll(propsLocal);
				for (EnumXLSColumnHeader field : distinctByField.keySet()) {
					TreeSet<String> set = distinctByField.get(field);
					set.clear();
					set.addAll(distinctLocal.get(field));
				}
				ready = true;
				return null;
			}

			@Override
			protected void done() {
				if (onDone != null) {
					SwingUtilities.invokeLater(onDone);
				}
			}
		}.execute();
	}

	private void updateDistinctLocal(EnumMap<EnumXLSColumnHeader, TreeSet<String>> distinctLocal,
			ExperimentProperties props) {
		addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.EXP_EXPT),
				props.getExperimentField(EnumXLSColumnHeader.EXP_EXPT));
		addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.EXP_BOXID),
				props.getExperimentField(EnumXLSColumnHeader.EXP_BOXID));
		addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.EXP_STIM1),
				props.getExperimentField(EnumXLSColumnHeader.EXP_STIM1));
		addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.EXP_CONC1),
				props.getExperimentField(EnumXLSColumnHeader.EXP_CONC1));
		addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.EXP_STRAIN),
				props.getExperimentField(EnumXLSColumnHeader.EXP_STRAIN));
		addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.EXP_SEX),
				props.getExperimentField(EnumXLSColumnHeader.EXP_SEX));
		addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.EXP_STIM2),
				props.getExperimentField(EnumXLSColumnHeader.EXP_STIM2));
		addIfNotEmpty(distinctLocal.get(EnumXLSColumnHeader.EXP_CONC2),
				props.getExperimentField(EnumXLSColumnHeader.EXP_CONC2));
	}

	private void addIfNotEmpty(Set<String> set, String value) {
		if (value != null && !value.isEmpty())
			set.add(value);
	}

	public List<String> getDistinctValues(EnumXLSColumnHeader field) {
		TreeSet<String> set = distinctByField.get(field);
		if (set == null)
			return new ArrayList<String>();
		return new ArrayList<String>(set);
	}

	// Incremental in-memory updates for the current subset
	public synchronized void addValue(EnumXLSColumnHeader field, String value) {
		if (value == null)
			return;
		String v = value.trim();
		if (v.isEmpty())
			return;
		TreeSet<String> set = distinctByField.get(field);
		if (set != null)
			set.add(v);
	}

	public synchronized void removeValue(EnumXLSColumnHeader field, String value) {
		if (value == null)
			return;
		String v = value.trim();
		if (v.isEmpty())
			return;
		TreeSet<String> set = distinctByField.get(field);
		if (set != null)
			set.remove(v);
	}

	public List<String> getDistinctValuesForExperiments(List<Experiment> experiments, EnumXLSColumnHeader field) {
		TreeSet<String> set = new TreeSet<String>();
		for (Experiment exp : experiments) {
			if (exp == null)
				continue;
			ExperimentProperties props = propertiesByResultsDir.get(exp.getResultsDirectory());
			if (props == null) {
				if (exp instanceof LazyExperiment) {
					LazyExperiment lexp = (LazyExperiment) exp;
					lexp.loadPropertiesIfNeeded();
					props = lexp.getCachedProperties();
				} else {
					exp.load_MS96_experiment();
					props = exp.getProperties();
				}
			}
			if (props != null) {
				String value = props.getExperimentField(field);
				if (value != null && !value.isEmpty())
					set.add(value);
			}
		}
		return new ArrayList<String>(set);
	}

	public ExperimentProperties getCachedProperties(Experiment exp) {
		if (exp == null)
			return null;
		return propertiesByResultsDir.get(exp.getResultsDirectory());
	}

}
