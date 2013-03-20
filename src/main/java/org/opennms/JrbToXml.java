package org.opennms;

import org.jrobin.core.RrdDb;
import org.jrobin.core.RrdDbPool;
import org.jrobin.core.RrdException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


public class JrbToXml extends Thread {
    private static boolean rrdDbPoolUsed = false;
    private ConvertJrb m_convertJrb;
    private Queue<String> m_queue = new ConcurrentLinkedQueue<String>();
    private boolean queueClosed = false;
    private String m_rrdTool;

    public JrbToXml(ConvertJrb convertJrb) {
        m_convertJrb = convertJrb;
        m_rrdTool = convertJrb.getRrrTool();
    }

    public void close() {
        queueClosed = true;
    }

    public void add(String path) {
        m_queue.add(path);
    }

    private RrdDb getRrdDbReference(String path) throws IOException, RrdException {
        if (rrdDbPoolUsed) {
            return RrdDbPool.getInstance().requestRrdDb(path);
        } else {
            return new RrdDb(path);
        }
    }

    private RrdDb getRrdDbReference(String path, String xmlPath) throws IOException, RrdException, RrdException {
        if (rrdDbPoolUsed) {
            return RrdDbPool.getInstance().requestRrdDb(path, xmlPath);
        } else {
            return new RrdDb(path, xmlPath);
        }
    }

    public int size() {
        return m_queue.size();
    }

    public void convertToRrd(String path) throws IOException, RrdException {
        String xmlPath = path + ".xml";
        String rrdPath = path + ".rrd";

        Process p = Runtime.getRuntime().exec(m_rrdTool + " restore " + xmlPath + " " + rrdPath);
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void convertToXml(String path) throws RrdException, IOException {
        RrdDb rrdDb = getRrdDbReference(path + ".jrb");

        try {
            byte[] buf = rrdDb.getXml().getBytes();
            FileChannel writeChannel = new RandomAccessFile(path + ".xml", "rw").getChannel();
            ByteBuffer wrBuf = writeChannel.map(FileChannel.MapMode.READ_WRITE, 0, buf.length);
            wrBuf.put(buf);
            writeChannel.close();
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
        while (!queueClosed || !m_queue.isEmpty()) {
            while (!m_queue.isEmpty()) {
                try {
                    String path = m_queue.poll();

                    convertToXml(path);
                    convertToRrd(path);

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