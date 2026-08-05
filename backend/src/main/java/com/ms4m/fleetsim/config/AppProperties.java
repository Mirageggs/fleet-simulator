package com.ms4m.fleetsim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuración de la aplicación (prefijo "app" en application.yml).
 * Todos los valores tienen defaults y pueden sobreescribirse con variables de
 * entorno (ver application.yml y README).
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String dataFile = "classpath:data/data.json";
    private String coordinateOrder = "AUTO"; // AUTO | LAT_LNG | LNG_LAT
    private String corsOrigins = "*";
    private Sim sim = new Sim();
    private Report report = new Report();

    public String getDataFile() { return dataFile; }
    public void setDataFile(String dataFile) { this.dataFile = dataFile; }
    public String getCoordinateOrder() { return coordinateOrder; }
    public void setCoordinateOrder(String coordinateOrder) { this.coordinateOrder = coordinateOrder; }
    public String getCorsOrigins() { return corsOrigins; }
    public void setCorsOrigins(String corsOrigins) { this.corsOrigins = corsOrigins; }
    public Sim getSim() { return sim; }
    public void setSim(Sim sim) { this.sim = sim; }
    public Report getReport() { return report; }
    public void setReport(Report report) { this.report = report; }

    public static class Sim {
        private int trucks = 5;
        private double tickSeconds = 1;
        private double timeScale = 5;
        private double speedMinKmh = 15;
        private double speedMaxKmh = 45;
        private int loadingSeconds = 4;
        private int unloadingSeconds = 4;
        private double snapMaxMeters = 200;

        public int getTrucks() { return trucks; }
        public void setTrucks(int trucks) { this.trucks = trucks; }
        public double getTickSeconds() { return tickSeconds; }
        public void setTickSeconds(double tickSeconds) { this.tickSeconds = tickSeconds; }
        public double getTimeScale() { return timeScale; }
        public void setTimeScale(double timeScale) { this.timeScale = timeScale; }
        public double getSpeedMinKmh() { return speedMinKmh; }
        public void setSpeedMinKmh(double speedMinKmh) { this.speedMinKmh = speedMinKmh; }
        public double getSpeedMaxKmh() { return speedMaxKmh; }
        public void setSpeedMaxKmh(double speedMaxKmh) { this.speedMaxKmh = speedMaxKmh; }
        public int getLoadingSeconds() { return loadingSeconds; }
        public void setLoadingSeconds(int loadingSeconds) { this.loadingSeconds = loadingSeconds; }
        public int getUnloadingSeconds() { return unloadingSeconds; }
        public void setUnloadingSeconds(int unloadingSeconds) { this.unloadingSeconds = unloadingSeconds; }
        public double getSnapMaxMeters() { return snapMaxMeters; }
        public void setSnapMaxMeters(double snapMaxMeters) { this.snapMaxMeters = snapMaxMeters; }
    }

    public static class Report {
        private double fastThresholdPct = 10;
        private double slowThresholdPct = 10;
        private int minSamples = 10;
        private Llm llm = new Llm();

        public double getFastThresholdPct() { return fastThresholdPct; }
        public void setFastThresholdPct(double fastThresholdPct) { this.fastThresholdPct = fastThresholdPct; }
        public double getSlowThresholdPct() { return slowThresholdPct; }
        public void setSlowThresholdPct(double slowThresholdPct) { this.slowThresholdPct = slowThresholdPct; }
        public int getMinSamples() { return minSamples; }
        public void setMinSamples(int minSamples) { this.minSamples = minSamples; }
        public Llm getLlm() { return llm; }
        public void setLlm(Llm llm) { this.llm = llm; }
    }

    public static class Llm {
        private boolean enabled = false;
        private String model = "claude-haiku-4-5";
        private String apiKey = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
}
