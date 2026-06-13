package nz.co.ksktech.fraud.data;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Loads PaySim CSV files from {@code app.dataset.path} into the {@code fraud_txn_sample} staging
 * table at startup, capped at {@code app.dataset.max-rows} (first N rows across files). Loading is
 * skipped entirely when the staging table is already populated, and can be disabled with {@code
 * app.dataset.enabled=false} (the test profile does this so the suite never touches the big CSV).
 *
 * <p>Expected PaySim header:
 * {@code step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,oldbalanceDest,
 * newbalanceDest,isFraud,isFlaggedFraud}.
 */
@ApplicationScoped
public class DataLoader {

  private static final String INSERT =
      "INSERT INTO fraud_txn_sample (step, type, amount, name_orig, oldbalance_org,"
          + " newbalance_orig, name_dest, oldbalance_dest, newbalance_dest, is_fraud,"
          + " is_flagged_fraud) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private static final int BATCH_SIZE = 1000;

  private final DataSource dataSource;

  @ConfigProperty(name = "app.dataset.enabled", defaultValue = "true")
  boolean enabled;

  @ConfigProperty(name = "app.dataset.path", defaultValue = "./data")
  String datasetPath;

  @ConfigProperty(name = "app.dataset.max-rows", defaultValue = "100000")
  long maxRows;

  /**
   * Constructor.
   *
   * @param dataSource the application datasource
   */
  public DataLoader(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * Triggers the load on application startup.
   *
   * @param event the Quarkus startup event
   */
  void onStart(@Observes StartupEvent event) {
    if (!enabled) {
      Log.info("Dataset loading disabled (app.dataset.enabled=false); skipping.");
      return;
    }
    try {
      load();
    } catch (Exception e) {
      // A bad/missing dataset must not stop the service from booting.
      Log.errorf(e, "Dataset load failed: %s", e.getMessage());
    }
  }

  /**
   * Loads CSV rows into the staging table unless it is already populated.
   *
   * @throws IOException if a CSV file cannot be read
   * @throws SQLException if a database error occurs
   */
  private void load() throws IOException, SQLException {
    if (isPopulated()) {
      Log.info("Staging table fraud_txn_sample already populated; skipping dataset load.");
      return;
    }
    Path dir = Path.of(datasetPath);
    if (!Files.isDirectory(dir)) {
      Log.warnf("Dataset path %s is not a directory; nothing to load.", dir.toAbsolutePath());
      return;
    }

    long loaded = 0;
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try (DirectoryStream<Path> csvFiles = Files.newDirectoryStream(dir, "*.csv");
          PreparedStatement ps = conn.prepareStatement(INSERT)) {
        for (Path csv : csvFiles) {
          if (loaded >= maxRows) {
            break;
          }
          loaded = loadFile(csv, ps, loaded);
        }
        ps.executeBatch();
        conn.commit();
      } catch (RuntimeException | SQLException | IOException e) {
        conn.rollback();
        throw e;
      }
    }
    Log.infof("Loaded %d PaySim rows into fraud_txn_sample from %s.", loaded, datasetPath);
  }

  /**
   * Loads a single CSV file, respecting the global {@code maxRows} cap.
   *
   * @param csv the CSV file
   * @param ps the prepared insert statement
   * @param loadedSoFar rows already loaded from earlier files
   * @return the cumulative loaded-row count
   * @throws IOException if the file cannot be read
   * @throws SQLException if a batch insert fails
   */
  private long loadFile(Path csv, PreparedStatement ps, long loadedSoFar)
      throws IOException, SQLException {
    long loaded = loadedSoFar;
    int sinceFlush = 0;
    Log.infof("Reading PaySim file %s ...", csv.getFileName());
    try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
      String line = reader.readLine(); // header
      while ((line = reader.readLine()) != null && loaded < maxRows) {
        if (line.isBlank()) {
          continue;
        }
        bindRow(ps, line.split(","));
        ps.addBatch();
        loaded++;
        if (++sinceFlush >= BATCH_SIZE) {
          ps.executeBatch();
          sinceFlush = 0;
        }
      }
      ps.executeBatch();
    }
    return loaded;
  }

  /**
   * Binds one PaySim CSV row to the insert statement.
   *
   * @param ps the prepared statement
   * @param c the split CSV columns
   * @throws SQLException if binding fails
   */
  private static void bindRow(PreparedStatement ps, String[] c) throws SQLException {
    ps.setObject(1, intOrNull(c, 0));
    ps.setString(2, value(c, 1));
    ps.setBigDecimal(3, decimalOrNull(c, 2));
    ps.setString(4, value(c, 3));
    ps.setBigDecimal(5, decimalOrNull(c, 4));
    ps.setBigDecimal(6, decimalOrNull(c, 5));
    ps.setString(7, value(c, 6));
    ps.setBigDecimal(8, decimalOrNull(c, 7));
    ps.setBigDecimal(9, decimalOrNull(c, 8));
    ps.setObject(10, intOrNull(c, 9));
    ps.setObject(11, intOrNull(c, 10));
  }

  /**
   * Checks whether the staging table already holds rows.
   *
   * @return true if at least one row exists
   * @throws SQLException if the query fails
   */
  private boolean isPopulated() throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT 1 FROM fraud_txn_sample LIMIT 1")) {
      return rs.next();
    }
  }

  private static String value(String[] c, int i) {
    return i < c.length ? c[i].trim() : null;
  }

  private static Integer intOrNull(String[] c, int i) {
    String v = value(c, i);
    return (v == null || v.isEmpty()) ? null : Integer.valueOf(v);
  }

  private static BigDecimal decimalOrNull(String[] c, int i) {
    String v = value(c, i);
    return (v == null || v.isEmpty()) ? null : new BigDecimal(v);
  }
}
