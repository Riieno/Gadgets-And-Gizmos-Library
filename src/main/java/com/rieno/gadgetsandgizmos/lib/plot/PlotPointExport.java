package com.rieno.gadgetsandgizmos.lib.plot;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Writes full resolution plot samples in formats usable outside the game. */
public final class PlotPointExport {
    public enum Format {
        JSON("json"), CSV("csv");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }

    private PlotPointExport() {
    }

    public static void write(List<PlotPointTimeline.Sample> samples, Format format, Writer output)
            throws IOException {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(output, "output");
        if (format == Format.JSON) writeJson(samples, output);
        else writeCsv(samples, output);
    }

    private static void writeJson(List<PlotPointTimeline.Sample> samples, Writer output) throws IOException {
        JsonWriter json = new JsonWriter(output);
        json.setIndent("  ");
        json.beginArray();
        for (PlotPointTimeline.Sample sample : samples) {
            json.beginObject();
            json.name("name").value(sample.name());
            json.name("x").value(sample.x());
            json.name("y").value(sample.y());
            json.name("color").value(color(sample.color()));
            json.endObject();
        }
        json.endArray();
        json.flush();
    }

    private static void writeCsv(List<PlotPointTimeline.Sample> samples, Writer output) throws IOException {
        output.write("name,x,y,color\r\n");
        for (PlotPointTimeline.Sample sample : samples) {
            output.write('"');
            output.write(sample.name().replace("\"", "\"\""));
            output.write("\",");
            output.write(Double.toString(sample.x()));
            output.write(',');
            output.write(Double.toString(sample.y()));
            output.write(",\"");
            output.write(color(sample.color()));
            output.write("\"\r\n");
        }
    }

    private static String color(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }
}
