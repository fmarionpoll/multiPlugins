package plugins.fmp.multitools.experiment.spot.measurement;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/** Versioned, lossless primary-data sidecar. No derived caches or legacy reconstructions are saved. */
public final class SpotMeasurementXml {
    public static final String FILENAME = "SpotsPrimaryMeasurements.xml";
    private SpotMeasurementXml() { }

    public static Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> read(Path path) throws IOException {
        Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> results = new LinkedHashMap<>();
        if (!Files.exists(path)) { return results; }
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        try (InputStream input = Files.newInputStream(path)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input, "UTF-8");
            try {
                reader.nextTag();
                require(reader, "spot-measurements");
                if (!"1".equals(attribute(reader, "version"))) { throw new IOException("Unsupported primary measurement version"); }
                while (reader.nextTag() == XMLStreamConstants.START_ELEMENT) {
                    require(reader, "spot");
                    String name = attribute(reader, "name");
                    Map<SpotMeasurementMethod, SpotMethodResult> methods = new EnumMap<>(SpotMeasurementMethod.class);
                    while (reader.nextTag() == XMLStreamConstants.START_ELEMENT) {
                        SpotMethodResult result = readMethod(reader);
                        if (methods.put(result.getMethod(), result) != null) { throw new IOException("Duplicate method for " + name); }
                    }
                    if (results.put(name, methods) != null) { throw new IOException("Duplicate spot: " + name); }
                }
                while (reader.hasNext()) { reader.next(); }
            } finally { reader.close(); }
        } catch (XMLStreamException | IllegalArgumentException e) {
            throw new IOException("Invalid primary measurement file: " + path, e);
        }
        return results;
    }

    private static SpotMethodResult readMethod(XMLStreamReader reader) throws XMLStreamException, IOException {
        require(reader, "method");
        SpotMeasurementMethod method = SpotMeasurementMethod.valueOf(attribute(reader, "id"));
        String flyUnit = attribute(reader, "fly-unit");
        double[] times = null, fly = null;
        Map<String, String> parameters = new LinkedHashMap<>();
        List<SpotMeasuredQuantity> quantities = new ArrayList<>();
        while (reader.nextTag() == XMLStreamConstants.START_ELEMENT) {
            String tag = reader.getLocalName();
            switch (tag) {
            case "times":
                if (times != null) { throw new IOException("Duplicate times"); }
                times = doubles(reader.getElementText()); break;
            case "fly":
                if (fly != null) { throw new IOException("Duplicate fly evidence"); }
                fly = doubles(reader.getElementText()); break;
            case "parameter":
                String name = attribute(reader, "name"), value = attribute(reader, "value");
                if (parameters.put(name, value) != null) { throw new IOException("Duplicate parameter: " + name); }
                if (!reader.getElementText().trim().isEmpty()) { throw new IOException("Unexpected parameter content"); }
                break;
            case "quantity": quantities.add(readQuantity(reader)); break;
            default: throw new IOException("Unknown method element: " + tag);
            }
        }
        if (times == null || fly == null) { throw new IOException("Missing method timeline or fly evidence"); }
        return new SpotMethodResult(method, times, fly, flyUnit, parameters, quantities);
    }

    private static SpotMeasuredQuantity readQuantity(XMLStreamReader reader) throws XMLStreamException, IOException {
        String id = attribute(reader, "id"), unit = attribute(reader, "unit");
        double[] raw = null;
        int[] flags = null;
        boolean[] manual = null;
        while (reader.nextTag() == XMLStreamConstants.START_ELEMENT) {
            switch (reader.getLocalName()) {
            case "raw":
                if (raw != null) { throw new IOException("Duplicate raw values"); }
                raw = doubles(reader.getElementText()); break;
            case "flags":
                if (flags != null) { throw new IOException("Duplicate flags"); }
                flags = integers(reader.getElementText()); break;
            case "manual":
                if (manual != null) { throw new IOException("Duplicate manual decisions"); }
                int[] decisions = integers(reader.getElementText());
                manual = new boolean[decisions.length];
                for (int i = 0; i < decisions.length; i++) {
                    if (decisions[i] != 0 && decisions[i] != 1) { throw new IOException("Invalid manual decision"); }
                    manual[i] = decisions[i] == 1;
                }
                break;
            default: throw new IOException("Unknown quantity element: " + reader.getLocalName());
            }
        }
        if (raw == null || flags == null || manual == null) { throw new IOException("Incomplete measured quantity: " + id); }
        return new SpotMeasuredQuantity(id, unit, raw, flags, manual);
    }

    public static void write(Path path, Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> results)
            throws IOException {
        Path target = path.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".spot-primary-", ".xml.tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output, "UTF-8");
                try {
                    writer.writeStartDocument("UTF-8", "1.0");
                    writer.writeStartElement("spot-measurements");
                    writer.writeAttribute("version", "1");
                    for (Map.Entry<String, Map<SpotMeasurementMethod, SpotMethodResult>> spot : results.entrySet()) {
                        writer.writeStartElement("spot");
                        writer.writeAttribute("name", spot.getKey());
                        for (SpotMethodResult result : spot.getValue().values()) { writeMethod(writer, result); }
                        writer.writeEndElement();
                    }
                    writer.writeEndElement();
                    writer.writeEndDocument();
                    writer.flush();
                } finally { writer.close(); }
            } catch (XMLStreamException e) { throw new IOException("Cannot write primary measurements", e); }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void writeMethod(XMLStreamWriter writer, SpotMethodResult result) throws XMLStreamException {
        writer.writeStartElement("method");
        writer.writeAttribute("id", result.getMethod().name());
        writer.writeAttribute("fly-unit", result.getFlyEvidenceUnit());
        element(writer, "times", join(result.getTimesSeconds()));
        element(writer, "fly", join(result.getFlyEvidence()));
        for (Map.Entry<String, String> p : result.getParameters().entrySet()) {
            writer.writeEmptyElement("parameter");
            writer.writeAttribute("name", p.getKey());
            writer.writeAttribute("value", p.getValue());
        }
        for (SpotMeasuredQuantity q : result.getQuantities().values()) {
            writer.writeStartElement("quantity");
            writer.writeAttribute("id", q.getId());
            writer.writeAttribute("unit", q.getUnit());
            element(writer, "raw", join(q.getRawValues()));
            element(writer, "flags", join(q.getAcquisitionFlags()));
            boolean[] manual = q.getManualRejections();
            int[] decisions = new int[manual.length];
            for (int i = 0; i < manual.length; i++) { decisions[i] = manual[i] ? 1 : 0; }
            element(writer, "manual", join(decisions));
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    private static String attribute(XMLStreamReader r, String name) throws IOException {
        String value = r.getAttributeValue(null, name);
        if (value == null) { throw new IOException("Missing attribute: " + name); }
        return value;
    }
    private static void require(XMLStreamReader r, String name) throws IOException {
        if (!name.equals(r.getLocalName())) { throw new IOException("Expected " + name); }
    }
    private static String[] tokens(String value) {
        return value.trim().isEmpty() ? new String[0] : value.trim().split("\\s+");
    }
    private static double[] doubles(String text) {
        String[] tokens = tokens(text);
        double[] values = new double[tokens.length];
        for (int i = 0; i < values.length; i++) { values[i] = Double.parseDouble(tokens[i]); }
        return values;
    }
    private static int[] integers(String text) {
        String[] tokens = tokens(text);
        int[] values = new int[tokens.length];
        for (int i = 0; i < values.length; i++) { values[i] = Integer.parseInt(tokens[i]); }
        return values;
    }
    private static String join(double[] values) {
        StringBuilder text = new StringBuilder();
        for (double value : values) { if (text.length() > 0) { text.append(' '); } text.append(Double.toString(value)); }
        return text.toString();
    }
    private static String join(int[] values) {
        StringBuilder text = new StringBuilder();
        for (int value : values) { if (text.length() > 0) { text.append(' '); } text.append(value); }
        return text.toString();
    }
    private static void element(XMLStreamWriter writer, String name, String text) throws XMLStreamException {
        writer.writeStartElement(name); writer.writeCharacters(text); writer.writeEndElement();
    }
}
