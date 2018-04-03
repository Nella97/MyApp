package net.finarx.twc.buskiosk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public final class StreamUtils {

	public static final int BUFFER_SIZE = 4096;

	public static long copy(InputStream in,
			OutputStream out) throws IOException {
		if (null == in || null == out) {
			return 0L;
		}

		byte[] buffer = new byte[BUFFER_SIZE];
		long totalLen = 0;
		int len = -1;
		while (-1 != (len = in.read(buffer))) {
			out.write(buffer, 0, len);
			totalLen += len;
		}

		return totalLen;
	}

	public static byte[] readByteArray(InputStream in) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(in, baos);
        return baos.toByteArray();
	}

    public static String readString(InputStream in, String charsetName) throws IOException {
        byte[] bytes = readByteArray(in);
        return new String(bytes, charsetName);
    }

    public static String readString(InputStream in) throws IOException{
       return readString(in, Charset.defaultCharset().name());
    }

    public static long writeByteArray(byte[] bytes, OutputStream out) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        return copy(bais, out);
    }

    public static long writeString(String value, String charsetName, OutputStream out) throws IOException{
        return writeByteArray(value.getBytes(charsetName), out);
    }

    public static long writeString(String value, OutputStream out) throws IOException{
        return writeString(value, Charset.defaultCharset().name(), out);
    }
}
