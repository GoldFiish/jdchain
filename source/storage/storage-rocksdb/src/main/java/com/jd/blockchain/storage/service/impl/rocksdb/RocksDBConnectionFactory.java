package com.jd.blockchain.storage.service.impl.rocksdb;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.annotation.PreDestroy;

import org.rocksdb.*;

import com.jd.blockchain.storage.service.DbConnection;
import com.jd.blockchain.storage.service.DbConnectionFactory;
import org.rocksdb.util.SizeUnit;

public class RocksDBConnectionFactory implements DbConnectionFactory {

	private static final String DB_CONFIG_ARG = "-rb";

	private static Properties dbConfigProperties = null;

	static {
		RocksDB.loadLibrary();
		init();
	}

	public static final String URI_SCHEME = "rocksdb";

	public static final Pattern URI_PATTER = Pattern
			.compile("^\\w+\\://(/)?\\w+(\\:)?([/\\\\].*)*$");

	private Map<String, RocksDBConnection> connections = new ConcurrentHashMap<>();

	@Override
	public DbConnection connect(String dbUri) {
		return connect(dbUri, null);
	}

	@Override
	public synchronized DbConnection connect(String dbConnectionString, String password) {
		if (!URI_PATTER.matcher(dbConnectionString).matches()) {
			throw new IllegalArgumentException("Illegal format of rocksdb connection string!");
		}
		URI dbUri = URI.create(dbConnectionString.replace("\\", "/"));
		if (!support(dbUri.getScheme())) {
			throw new IllegalArgumentException(
					String.format("Not supported db connection string with scheme \"%s\"!", dbUri.getScheme()));
		}

		String uriHead = dbPrefix();
		int beginIndex = dbConnectionString.indexOf(uriHead);
		String dbPath = dbConnectionString.substring(beginIndex + uriHead.length());

		RocksDBConnection conn = connections.get(dbPath);
		if (conn != null) {
			return conn;
		}

		Options options = initOptions();

		conn = new RocksDBConnection(dbPath, options);
		connections.put(dbPath, conn);

		return conn;
	}


	@Override
	public String dbPrefix() {
		return URI_SCHEME + "://";
	}

	@Override
	public boolean support(String scheme) {
		return URI_SCHEME.equalsIgnoreCase(scheme);
	}

	@PreDestroy
	@Override
	public void close() {
		RocksDBConnection[] conns = connections.values().toArray(new RocksDBConnection[connections.size()]);
		connections.clear();
		for (RocksDBConnection conn : conns) {
			conn.dbClose();
		}
	}

	private Options initOptions() {
		return initOptionsByProperties(dbConfigProperties);
	}

	private Options initOptionsByProperties(Properties dbProperties) {
		long cacheCapacity = getLong(dbProperties, "cache.capacity", 512 * SizeUnit.MB);
		int cacheNumShardBits = getInt(dbProperties, "cache.numShardBits", 64);

		long tableBlockSize = getLong(dbProperties, "table.blockSize", 4 * SizeUnit.KB);
		long tableMetadataBlockSize = getLong(dbProperties, "table.metadata.blockSize", 8 * SizeUnit.KB);
		int tableBloomBitsPerKey = getInt(dbProperties, "table.bloom.bitsPerKey", 16);

		long optionWriteBufferSize = getLong(dbProperties, "option.writeBufferSize", 256 * SizeUnit.MB);
		int optionMaxWriteBufferNumber = getInt(dbProperties, "option.maxWriteBufferNumber", 7);
		int optionMinWriteBufferNumberToMerge = getInt(dbProperties, "option.minWriteBufferNumberToMerge", 2);
		int optionMaxOpenFiles = getInt(dbProperties, "option.maxOpenFiles", -1);
		int optionMaxBackgroundCompactions = getInt(dbProperties, "option.maxBackgroundCompactions", 5);
		int optionMaxBackgroundFlushes = getInt(dbProperties, "option.maxBackgroundFlushes", 4);

		Cache cache = new LRUCache(cacheCapacity, cacheNumShardBits, false);
		final BlockBasedTableConfig tableOptions = new BlockBasedTableConfig()
				.setBlockCache(cache)
				.setBlockSize(tableBlockSize)
				.setMetadataBlockSize(tableMetadataBlockSize)
				.setCacheIndexAndFilterBlocks(true) // 设置索引和布隆过滤器使用Block Cache内存
				.setCacheIndexAndFilterBlocksWithHighPriority(true)
				.setIndexType(IndexType.kTwoLevelIndexSearch) // 设置两级索引，控制索引占用内存
				.setPinL0FilterAndIndexBlocksInCache(true) // 设置两级索引
				.setFilterPolicy(new BloomFilter(tableBloomBitsPerKey, false)) // 设置布隆过滤器
				;
		Options options = new Options()
				// 最多占用256 * 7 + 512 = 2G+内存
				.setWriteBufferSize(optionWriteBufferSize)
				.setMaxWriteBufferNumber(optionMaxWriteBufferNumber)
				.setMinWriteBufferNumberToMerge(optionMinWriteBufferNumberToMerge)
				.setMaxOpenFiles(optionMaxOpenFiles) // 控制最大打开文件数量，防止内存持续增加
				.setAllowConcurrentMemtableWrite(true) //允许并行Memtable写入
				.setCreateIfMissing(true)
				.setTableFormatConfig(tableOptions)
				.setMaxBackgroundCompactions(optionMaxBackgroundCompactions)
				.setMaxBackgroundFlushes(optionMaxBackgroundFlushes)
				;
		return options;
	}

	/**
	 * 初始化参数配置
	 *
	 */
	private static void init() {
		String dbConfigPath = System.getProperty(DB_CONFIG_ARG);
		if (dbConfigPath != null && dbConfigPath.length() > 0) {
			File dbConfigFile = new File(dbConfigPath);
			try {
				dbConfigProperties = new Properties();
				dbConfigProperties.load(new FileInputStream(dbConfigFile));
			} catch (Exception e) {
				throw new IllegalStateException(String.format("Load rocksdb.config %s error !!!", dbConfigPath), e);
			}
		}
	}

	private long getLong(Properties properties, String key, long defaultVal) {
		if (properties == null || properties.isEmpty()) {
			return defaultVal;
		}
		String prop = properties.getProperty(key);
		if (prop == null || prop.length() == 0) {
			return defaultVal;
		} else {
			return Long.parseLong(prop);
		}
	}

	private int getInt(Properties properties, String key, int defaultVal) {
		if (properties == null || properties.isEmpty()) {
			return defaultVal;
		}
		String prop = properties.getProperty(key);
		if (prop == null || prop.length() == 0) {
			return defaultVal;
		} else {
			return Integer.parseInt(prop);
		}
	}
}