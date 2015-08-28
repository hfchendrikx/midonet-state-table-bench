package org.midonet.benchmarks.latencyNodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by huub on 25-8-15.
 */
public class Bookkeeper {

    private static final Logger log =
            LoggerFactory.getLogger(Bookkeeper.class);

    protected String basePath;
    protected String tag;
    protected String hostname;


    public Bookkeeper(String cBasePath, String cHostname, String cTag) {
        this.basePath = cBasePath;
        this.hostname = cHostname;
        this.tag = cTag;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    protected String getPathToExperiment() {
        return basePath + "/" + tag;
    }

    public String getPathToLog(String name) {
         return this.getPathToExperiment() + "/" + hostname + "/" + name;
    }

    public PrintStream getFileWriter(String name) {
        PrintStream stream = this.getHeaderLessFileWriter(name);
        stream.println("HOST: " + hostname + " DATE: " + (new Date()).toString());
        return stream;
    }

    public PrintStream getHeaderLessFileWriter(String name) {
        String pathToFile = this.getPathToLog(name);
        File file = new File(pathToFile);
        file.getParentFile().mkdirs();
        try {
            PrintStream stream = new PrintStream(pathToFile);
            return stream;
        } catch (FileNotFoundException e) {
            log.error("Could not open file " + pathToFile + " for experiment log", e);
            return null;
        }
    }

    public List<String> getPathsToAllLogs(String name) {
        File folder = new File(this.getPathToExperiment());
        LinkedList<String> files = new LinkedList<>();

        for (File entry : folder.listFiles()) {
            if (entry.isDirectory()) {
                File logFile = new File(entry.getAbsolutePath() + "/" + name);
                if (logFile.exists()) {
                    files.add(logFile.getAbsolutePath());
                }
            }
        }

        return files;

    }
}
