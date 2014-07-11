package at.itec.mf;

/**
 * 
 * Interface containing synchronization constants and methods
 * 
 * @author stefan petscharnig
 *
 */
public interface SyncI {

	/** Tags for android log */
	public static final String TAG_CS = "coarse sync mf";
	public static final String TAG_FS = "fine sync mf";

	/**
	 * amount of time we wait in the coarse sync after sending a request to all
	 * known peers
	 */
	public static final int WAIT_TIME_CS_MS = 1000;
	/** fine sync period length */
	public static final int PERIOD_FS_MS = 250;

	/*
	 * TODO: unfortunately the delimiter is part of the reg expression language
	 * ... maybe we will change that sometime..
	 */
	public static final String DELIM = "|";

	/** bloom filter length: n_exp_elem * bits_per_elem */
	public static final int N_EXP_ELEM = 256;
	public static final int BITS_PER_ELEM = 2;
	/** number of used hash functions in the bloom filter **/
	public static final int N_HASHES = 4;

	/** difference in ms when we stop (fine) sync */
	public static final long EPSILON = 125;

	/** constants for message types */
	public static final int TYPE_COARSE_REQ = 1;
	public static final int TYPE_COARSE_RESP = 2;
	public static final int TYPE_FINE = 3;

	/**
	 * here, the first steps of the sync are done
	 */
	public abstract void startSync();

	/**
	 * process an incoming request
	 * 
	 * @param request
	 */
	public abstract void processRequest(String request);

}
