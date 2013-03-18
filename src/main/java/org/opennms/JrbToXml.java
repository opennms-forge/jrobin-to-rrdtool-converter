package org.opennms;

import org.jrobin.core.RrdDb;
import org.jrobin.core.RrdDbPool;
import org.jrobin.core.RrdException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class JrbToXml implements Runnable {
    private static boolean rrdDbPoolUsed = true;
    private ConvertJrb m_convertJrb;

    public JrbToXml(ConvertJrb convertJrb) {
        m_convertJrb = convertJrb;
    }

    static RrdDb getRrdDbReference(String path) throws IOException, RrdException {
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

    static void releaseRrdDbReference(RrdDb rrdDb) throws IOException, RrdException {
        if (rrdDbPoolUsed) {
            RrdDbPool.getInstance().release(rrdDb);
        } else {
            rrdDb.close();
        }
    }

    @Override
    public void run() {
        int size = m_convertJrb.getStack().size();

        while (size > 0 || !m_convertJrb.searchDone()) {

            String path = null;

            synchronized (m_convertJrb) {
                size = m_convertJrb.getStack().size();

                if (size > 0) {
                    path = m_convertJrb.getStack().pop();
                }
            }

            if (path != null) {
                try {
                    convert(path);
                } catch (RrdException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                m_convertJrb.increaseConvertedFiles();
            }
        }
    }
}
