package utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;

public class WebRequest
{
	private WebRequest(){}
	
	static{
		final CookieManager cook = new CookieManager();
		cook.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL);
		CookieHandler.setDefault(cook);
	}
	private static String[][] headers = new String[0][0];
	public static synchronized String GET(String url) throws MalformedURLException, IOException
	{
		return GET(new URL(url));
	}
	public static synchronized String GET(URL url) throws IOException
	{
		HttpURLConnection connection = request(url);
		connection.setRequestMethod("GET");
		return read(connection);
	}
	public static synchronized String POST(String url, String data) throws MalformedURLException, IOException
	{
		return POST(new URL(url), data);
	}
	public static synchronized String POST(URL url, String data) throws IOException
	{
		HttpURLConnection connection = request(url);
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		send(connection, data);
		try{
			return read(connection);
		}catch(NullPointerException npe){
			npe.printStackTrace();
			return null;
		}
	}
	public static void setDefaultHeaders(String[][] headers)
	{
		WebRequest.headers = headers;
	}
	public static HttpURLConnection request(URL url)
	{
		try
		{
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			for(String[] headermap : headers){
				if(headermap.length!=2)
					throw new IllegalArgumentException("Invalid header "+java.util.Arrays.deepToString(headermap));
				connection.setRequestProperty(headermap[0], headermap[1]);
			}
			return connection;
		}
		catch(IOException e)
		{
			throw new InternalError(e);
		}
	}
	private static synchronized String read(HttpURLConnection connection) throws IOException
	{
		connection.connect();
		String encoding = (connection.getContentEncoding()!=null) ? connection.getContentEncoding().toLowerCase() : "";
		InputStream is = connection.getInputStream();
		switch(encoding){
			case "gzip":
				is=new GZIPInputStream(is);
				break;
			case "deflate":
				is=new DeflaterInputStream(is);
				break;
			default:
				break;
		}
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		String response_text="", line;
		while((line=reader.readLine()) != null)
			response_text+=line+System.lineSeparator();
		reader.close();
		return response_text;
	}
	private static synchronized void send(HttpURLConnection connection, String data) throws IOException
	{
		try{
			connection.connect();
			OutputStream os = connection.getOutputStream();
			os.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}catch(java.net.SocketTimeoutException ste){
			try{
				send(connection, data);
			}
			catch(StackOverflowError soe){
				System.err.println("Connection timed out to "+connection.getURL());
			}
		}
	}
}
