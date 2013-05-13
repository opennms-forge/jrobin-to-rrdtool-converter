package org.opennms;

import org.apache.commons.cli.*;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ConvertJrb {
    public static final int DEFAULT_THREADS = 4;
    public static final String DEFAULT_RRDTOOL = "/usr/local/bin/rrdtool";

    private static final int m_statusThreadSleep = 1000;
    private int m_threadCount = 8;

    private int m_fileCount = 0;
    private int m_filesConverted = 0;
    private int m_skippedFiles = 0;
    private int m_oldFilesConverted = 0;

    private String m_rrdTool;

    private boolean m_searchDone = false;

    private JrbToXml[] m_threads;

    private String m_path;

    public ConvertJrb(String path, String rrdTool, int threadCount) {
        m_path = path;
        m_threadCount = threadCount;
        m_rrdTool = rrdTool;
    }

    public synchronized void increaseConvertedFiles() {
        m_filesConverted++;
    }

    private void search(String path) {
        File directory = new File(path);
        Set<File> fileSet = new TreeSet<File>();
        Collections.addAll(fileSet, directory.listFiles());

        for (File file : fileSet) {
            if (file.isDirectory()) {
                search(file.getAbsolutePath());
            } else {
                if (file.getName().endsWith(".jrb")) {
                    String filename = file.getAbsolutePath().substring(0, file.getAbsolutePath().length() - 4);
                    if (fileSet.contains(new File(filename + ".xml")) && fileSet.contains(new File(filename + ".rrd"))) {
                        m_skippedFiles++;
                    } else {
                        m_threads[m_fileCount % m_threadCount].add(filename);
                        m_fileCount++;
                    }
                } else {
                    // ignore
                }
            }
        }
    }

    public boolean searchDone() {
        return m_searchDone;
    }

    public String getRrrTool() {
        return m_rrdTool;
    }

    private void runConversion() {
        System.out.println("Using rrdtool '" + m_rrdTool + "'...");

        System.out.print("Setting up " + m_threadCount + " converter thread(s)...");

        m_threads = new JrbToXml[m_threadCount];

        for (int i = 0; i < m_threadCount; i++) {
            m_threads[i] = new JrbToXml(this);
            m_threads[i].start();
        }

        System.out.print(" done!\nSetting up status thread running every " + m_statusThreadSleep + " ms...");

        Thread t = new Thread(new Runnable() {
            public void run() {
                while (!searchDone() || m_fileCount > m_filesConverted) {
                    //System.out.println("m_fileCount="+m_fileCount);
                    //System.out.println("m_filesConverted="+m_filesConverted);
                    //System.out.println("searchDone()="+searchDone());
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    int convertedPerSecond = (m_filesConverted - m_oldFilesConverted);

                    m_oldFilesConverted = m_filesConverted;

                    System.out.print("search=" + (m_searchDone ? "finished" : "running") + ", found=" + m_fileCount + ", skipped=" + m_skippedFiles + ", queued=" + (m_fileCount - m_oldFilesConverted) + ", converted=" + m_oldFilesConverted);

                    for (int i = 0; i < m_threadCount; i++) {
                        System.out.print(", #" + i + "=" + m_threads[i].size());
                    }

                    System.out.println(", delta=" + convertedPerSecond);
                }
                System.out.println("Finished!\n");
            }
        });

        t.start();

        System.out.println(" done!\nSearching path is '" + m_path + "'");

        search(m_path);

        m_searchDone = true;

        for (int i = 0; i < m_threadCount; i++) {
            m_threads[i].close();
        }
    }

    private static void usage(final Options options, final CommandLine cmd, final String error, final Exception e) {
        final HelpFormatter formatter = new HelpFormatter();
        final PrintWriter pw = new PrintWriter(System.out);
        if (error != null) {
            pw.println("An error occurred: " + error + "\n");
        }

        formatter.printHelp("Usage: ConvertJrb <path>", options);

        if (e != null) {
            pw.println(e.getMessage());
            e.printStackTrace(pw);
        }

        pw.close();
    }

    private static void usage(final Options options, final CommandLine cmd) {
        usage(options, cmd, null, null);
    }

    public static void main(String args[]) throws ParseException {

        String rrdTool = DEFAULT_RRDTOOL;
        int threadCount = DEFAULT_THREADS;
        String path = "./";

        final Options options = new Options();

        options.addOption("rrdtool", true, "set rrdtool to use for converting Xml to Rrd, default: '" + DEFAULT_RRDTOOL + "'");
        options.addOption("threads", true, "set number of threads to use, default: " + DEFAULT_THREADS);

        final CommandLineParser parser = new PosixParser();
        final CommandLine cmd = parser.parse(options, args);

        @SuppressWarnings("unchecked")
        List<String> arguments = (List<String>) cmd.getArgList();

        if (arguments.size() < 1) {
            usage(options, cmd);
            System.exit(1);
        }

        if (cmd.hasOption("rrdtool")) {
            rrdTool = cmd.getOptionValue("rrdtool");
        }

        if (cmd.hasOption("threads")) {
            try {
                threadCount = Integer.valueOf(cmd.getOptionValue("threads"));
            } catch (NumberFormatException numberFormatException) {
                usage(options, cmd);
                System.exit(1);
            }
        }

        path = arguments.remove(0);

        ConvertJrb convertJrb = new ConvertJrb(path, rrdTool, threadCount);
        convertJrb.runConversion();
    }
}
