package de.fraunhofer.iosb.ilt.dbtest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class RunTests {

	public static final String TAG_THREADS = "threads";
	public static final int DEF_THREADS = 1;

	public static final String TAG_LOOPS = "loops";
	public static final int DEF_LOOPS = 100;

	public static final String TAG_DB_DRIVER = "db_driver";
	public static final String DEF_DB_DRIVER = "org.postgresql.Driver";

	public static final String TAG_DB_URL = "db_url";
	public static final String DEF_DB_URL = "jdbc:postgresql://localhost:5432/sensorthingsTest";

	public static final String TAG_DB_USERNAME = "db_username";
	public static final String DEF_DB_USERNAME = "sensorthings";

	public static final String TAG_DB_PASSWORD = "db_password";
	public static final String DEF_DB_PASSWORD = "ChangeMe";

	public static final String TAG_DATASTREAM_START = "dsStart";
	public static final int DEF_DATASTREAM_START = 1;

	public static final String TAG_DATASTREAM_END = "dsEnd";
	public static final int DEF_DATASTREAM_END = 10;

	public static final String TAG_FEATURE_START = "featureStart";
	public static final int DEF_FEATURE_START = 1;

	public static final String TAG_FEATURE_END = "featureEnd";
	public static final int DEF_FEATURE_END = 10;

	public static final String TAG_FEATURE = "feature";
	public static final int DEF_FEATURE = 1;

	public static final String TAG_STATEMENT = "insert into \"OBSERVATIONS\" (\"PHENOMENON_TIME_START\",\"PHENOMENON_TIME_END\", \"RESULT_NUMBER\",\"RESULT_STRING\", \"DATASTREAM_ID\",\"FEATURE_ID\",\"RESULT_TYPE\") values (now(),now(),1,'1', ?,?,0);";

	/**
	 * The logger for this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(RunTests.class);
	private static final String poolName = "db";
	static Map<String, ConnectionSource> existingPools = new HashMap<>();

	private static class Writer extends Thread {

		private int nr;

		public Writer(int nr) {
			this.nr = nr;
		}

		public int getNr() {
			return nr;
		}

		@Override
		public void run() {
			try {
				connectAndWrite();
			} catch (SQLException | ClassNotFoundException ex) {
				LOGGER.error("Excpetion while running", ex);
			}
		}

		private int getIntInRange(int start, int end, int nr) {
			int delta = end - start;
			int i = start + nr;
			while (i > end) {
				i -= delta;
			}
			return i;
		}

		private int getDatastreamId() {
			int dsStart = getEnv(TAG_DATASTREAM_START, DEF_DATASTREAM_START);
			int dsEnd = getEnv(TAG_DATASTREAM_END, DEF_DATASTREAM_END);
			return getIntInRange(dsStart, dsEnd, nr);
		}

		private int getFeatureId() {
			int fStart = getEnv(TAG_FEATURE_START, DEF_FEATURE_START);
			int fEnd = getEnv(TAG_FEATURE_END, DEF_FEATURE_END);
			return getIntInRange(fStart, fEnd, nr);
		}

		public void connectAndWrite() throws SQLException, ClassNotFoundException {
			int datastream = getDatastreamId();
			int feature = getFeatureId();
			int loops = getEnv(TAG_LOOPS, 100);
			LOGGER.debug("{} Looping {}", nr, loops);
			Connection conn = getPoolingConnection(poolName);
			PreparedStatement statement = conn.prepareStatement(TAG_STATEMENT);
			for (int i = 0; i < loops; i++) {
				statement.setInt(1, datastream);
				statement.setInt(2, feature);
				statement.executeUpdate();
			}
			statement.close();
			conn.close();
		}
	}

	public void runThreads() throws InterruptedException, SQLException, ClassNotFoundException {
		getPoolingConnection(poolName);
		int threadCount = getEnv(TAG_THREADS, DEF_THREADS);
		int loops = getEnv(TAG_LOOPS, DEF_LOOPS);
		List<Writer> threads = new ArrayList<>();
		LOGGER.debug("Creating {} threads.", threadCount);
		for (int i = 0; i < threadCount; i++) {
			final int nr = i;
			threads.add(new Writer(nr));
		}
		long start = System.currentTimeMillis();
		for (Writer thread : threads) {
			thread.start();
		}
		for (Writer thread : threads) {
			thread.join();
			LOGGER.debug("Thread {} done.", thread.getNr());
		}
		long end = System.currentTimeMillis();
		int insertCount = threadCount * loops;
		long duration = end - start;
		double perSec = insertCount * 1000 / duration;
		LOGGER.info("Inserted {} in {}ms: {}/s", insertCount, duration, perSec);
	}

	/**
	 * Creates a connection, setting up a new pool if needed.
	 *
	 * @param name The name to use for the source
	 * @return A pooled database connection.
	 * @throws SQLException
	 */
	public static Connection getPoolingConnection(String name) throws SQLException, ClassNotFoundException {
		ConnectionSource source = existingPools.get(name);
		if (source == null) {
			source = createPoolingConnection(name);
		}
		return source.getConnection();
	}

	static ConnectionSource createPoolingConnection(String name) throws SQLException, ClassNotFoundException {
		synchronized (existingPools) {
			ConnectionSource source = existingPools.get(name);
			if (source == null) {
				source = setupBasicDataSource();
				existingPools.put(name, source);
			}
			return source;
		}
	}

	static ConnectionSource setupBasicDataSource() throws ClassNotFoundException {
		LOGGER.info("Setting up BasicDataSource.");
		Class.forName(getEnv(TAG_DB_DRIVER, DEF_DB_DRIVER));
		return new ConnectionSourceBasicDataSource(
				getEnv(TAG_DB_URL, DEF_DB_URL),
				getEnv(TAG_DB_USERNAME, "sensorthings"),
				getEnv(TAG_DB_PASSWORD, "ChangeMe"));
	}

	static ConnectionSource setupApachePoolSource() throws ClassNotFoundException {
		LOGGER.info("Setting up BasicDataSource.");
		Class.forName(getEnv(TAG_DB_DRIVER, DEF_DB_DRIVER));
		return new ConnectionSourceApachePool(
				getEnv(TAG_DB_URL, "jdbc:postgresql://localhost:5432/sensorthingsTest"),
				getEnv(TAG_DB_USERNAME, "sensorthings"),
				getEnv(TAG_DB_PASSWORD, "ChangeMe"));
	}

	static interface ConnectionSource {

		public Connection getConnection() throws SQLException;
	}

	static class ConnectionSourceBasicDataSource implements ConnectionSource {

		private BasicDataSource dataSource;
		private String url;
		private String username;
		private String password;

		public ConnectionSourceBasicDataSource(String url, String username, String password) {
			this.url = url;
			this.username = username;
			this.password = password;
		}

		@Override
		public Connection getConnection() throws SQLException {
			return getDataSource().getConnection();
		}

		private BasicDataSource getDataSource() {
			if (dataSource == null) {
				BasicDataSource ds = new BasicDataSource();
				ds.setUrl(url);
				ds.setUsername(username);
				ds.setPassword(password);
				ds.setMinIdle(getEnv("minIdle", 8));
				ds.setMaxIdle(getEnv("maxIdle", 10));
				ds.setMaxTotal(getEnv("maxTotal", 20));
				dataSource = ds;
			}
			return dataSource;
		}
	}

	static class ConnectionSourceApachePool implements ConnectionSource {

		private DataSource dataSource;
		private String url;
		private String username;
		private String password;

		public ConnectionSourceApachePool(String url, String username, String password) {
			this.url = url;
			this.username = username;
			this.password = password;
		}

		@Override
		public Connection getConnection() throws SQLException {
			return getDataSource().getConnection();
		}

		private DataSource getDataSource() {
			if (dataSource == null) {
				PoolProperties p = new PoolProperties();
				p.setDriverClassName(getEnv(TAG_DB_DRIVER, DEF_DB_DRIVER));
				p.setUrl(url);
				p.setUsername(username);
				p.setPassword(password);
				p.setMaxActive(20);
				p.setMinIdle(10);
				p.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
				DataSource ds = new DataSource(p);
				dataSource = ds;
			}
			return dataSource;
		}
	}

	public static String getEnv(String name, String deflt) {
		String value = System.getenv(name);
		if (value == null) {
			LOGGER.warn("No value found for {}, using default {}", name, deflt);
			return deflt;
		}
		return value;
	}

	public static int getEnv(String name, int deflt) {
		String value = System.getenv(name);
		if (value == null) {
			LOGGER.warn("No value found for {}, using default {}", name, deflt);
			return deflt;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			LOGGER.trace("Failed to parse parameter to int.", ex);
			LOGGER.info("Value for {} ({}) was not an Integer, using default {}", name, value, deflt);
			return deflt;
		}
	}

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws InterruptedException, SQLException, ClassNotFoundException {
		int repeats = getEnv("repeats", 1);
		int threadCount = getEnv(TAG_THREADS, DEF_THREADS);
		int loops = getEnv(TAG_LOOPS, DEF_LOOPS);
		int count = repeats * threadCount * loops;

		LOGGER.info("Running {} times, Using {} threads, {} Obs per thread = {} Observations.", repeats, threadCount, loops, count);
		long start = System.currentTimeMillis();
		for (int i = 1; i <= repeats; i++) {
			LOGGER.debug("Repeat {}", i);
			new RunTests().runThreads();
		}
		long end = System.currentTimeMillis();
		long duration = end - start;
		long perSec = count * 1000 / duration;
		LOGGER.info("Created {} Observations in {} ms. {}/s.", count, duration, perSec);
	}

}
