package com.rieno.gadgetsandgizmos.lib.plot;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlotPointExportTest {
    @Test
    void jsonAndCsvPreserveEverySampleAndEscapeNames() throws Exception {
        List<PlotPointTimeline.Sample> samples = List.of(
                new PlotPointTimeline.Sample("Speed, \"port\"\nA", 1.25, -4.5, 0xFFFF00AA),
                new PlotPointTimeline.Sample("Speed", 2.5, 3.75, 0xFF00CC11));

        StringWriter json = new StringWriter();
        PlotPointExport.write(samples, PlotPointExport.Format.JSON, json);
        var points = JsonParser.parseString(json.toString()).getAsJsonArray();
        assertEquals(2, points.size());
        assertEquals(samples.getFirst().name(), points.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(1.25, points.get(0).getAsJsonObject().get("x").getAsDouble());
        assertEquals(-4.5, points.get(0).getAsJsonObject().get("y").getAsDouble());
        assertEquals("#FF00AA", points.get(0).getAsJsonObject().get("color").getAsString());
        assertEquals(2.5, points.get(1).getAsJsonObject().get("x").getAsDouble());

        StringWriter csv = new StringWriter();
        PlotPointExport.write(samples, PlotPointExport.Format.CSV, csv);
        assertEquals("name,x,y,color\r\n"
                        + "\"Speed, \"\"port\"\"\nA\",1.25,-4.5,\"#FF00AA\"\r\n"
                        + "\"Speed\",2.5,3.75,\"#00CC11\"\r\n",
                csv.toString());
    }
}
