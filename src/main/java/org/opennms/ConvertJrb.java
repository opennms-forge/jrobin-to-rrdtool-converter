package org.opennms;

import java.io.File;

public class ConvertJrb {
    private static final int m_statusThreadSleep = 1000;
    private static final int m_threadCount = 4;

    private int m_fileCount = 0;
    private int m_filesConverted = 0;

    // private Stack<String> m_stack = new Stack<String>();
    private boolean m_searchDone = false;

    private JrbToXml[] m_threads = new JrbToXml[m_threadCount];

    private String m_path;

    public ConvertJrb(String path) {
        m_path = path;
    }

    public void increaseConvertedFiles() {
        m_filesConverted++;
    }

    private void search(String path) {
        File directory = new File(path);
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                search(file.getAbsolutePath());
            } else {
                if (file.getName().endsWith(".jrb")) {
                    m_threads[m_fileCount % m_threadCount].add(file.getAbsolutePath().substring(0, file.getAbsolutePath().length() - 4));
                    m_fileCount++;
                } else {
                    // ignore
                }
            }
        }
    }

    public boolean searchDone() {
        return m_searchDone;
    }

    private void runConversion() {
        System.out.print("Setting up " + m_threadCount + " converter thread(s)...");


        for (int i = 0; i < m_threadCount; i++) {
            m_threads[i] = new JrbToXml(this);
            m_threads[i].start();
        }

        System.out.print(" done!\nSetting up status thread running every " + m_statusThreadSleep + " ms...");

        Thread t = new Thread(new Runnable() {
            public void run() {
                while (!searchDone() || m_fileCount > m_filesConverted) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    System.out.println("search=" + (m_searchDone ? "finished" : "running") + ", found=" + m_fileCount + ", queued=" + (m_fileCount - m_filesConverted) + ", converted=" + m_filesConverted);
                    System.out.flush();
                }
                System.out.println("search=" + (m_searchDone ? "finished" : "running") + ", found=" + m_fileCount + ", queued=" + (m_fileCount - m_filesConverted) + ", converted=" + m_filesConverted);
                System.out.flush();
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

    public static void main(String args[]) {

        if (args.length != 1) {
            System.out.println("Usage: ConvertJrb <search-path>");
        } else {
            ConvertJrb convertJrb = new ConvertJrb(args[0]);
            convertJrb.runConversion();
        }
    }
}
