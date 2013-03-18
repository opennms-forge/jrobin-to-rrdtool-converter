package org.opennms;

import org.jrobin.core.RrdDb;
import org.jrobin.core.RrdDbPool;
import org.jrobin.core.RrdException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Stack;


public class JrbToXml extends Thread {
    private static boolean rrdDbPoolUsed = true;
    private ConvertJrb m_convertJrb;
    private Stack<String> m_stack = new Stack<String>();
    private boolean stackClosed = false;

    public JrbToXml(ConvertJrb convertJrb) {
        m_convertJrb = convertJrb;
    }

    public void close() {
        stackClosed = true;
    }

    public void add(String path) {
        m_stack.add(path);
    }

    private RrdDb getRrdDbReference(String path) throws IOException, RrdException {
        if (rrdDbPoolUsed) {
            return RrdDbPool.getInstance().requestRrdDb(path);
        } else {
            return new RrdDb(path);
        }
    }

    public void convert(String path) throws RrdException, IOException {
        RrdDb rrdDb = getRrdDbReference(path + ".jrb");

        try {
            String xml = rrdDb.getXml();
            BufferedWriter out = new BufferedWriter(new FileWriter(path + ".xml"), xml.length());
            out.write(xml);
            out.close();
        } finally {
            releaseRrdDbReference(rrdDb);
        }
    }

    private void releaseRrdDbReference(RrdDb rrdDb) throws IOException, RrdException {
        if (rrdDbPoolUsed) {
            RrdDbPool.getInstance().release(rrdDb);
        } else {
            rrdDb.close();
        }
    }

    public void run() {
        while (!stackClosed) {
            while (!m_stack.empty()) {
                try {
                    convert(m_stack.pop());
                    m_convertJrb.increaseConvertedFiles();
                } catch (RrdException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}