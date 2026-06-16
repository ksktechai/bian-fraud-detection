package nz.co.ksktech.fraud.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives {@link DataLoader#load(Path, long)} against the Dev Services Postgres with crafted CSVs,
 * covering parsing (incl. blank lines, empty fields, short rows), the max-rows cap across files, the
 * skip-when-populated guard, and the not-a-directory guard.
 */
@QuarkusTest
class DataLoaderTest {

  private static final String HEADER =
      "step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,"
          + "oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud";

  @Inject DataLoader loader;
  @Inject DataSource dataSource;

  @TempDir Path dir;

  @BeforeEach
  void clearStaging() {
    exec("DELETE FROM fraud_txn_sample");
  }

  @Test
  void loadsRowsSkipsBlankLinesAndParsesNullFields() throws Exception {
    Files.writeString(
        dir.resolve("paysim.csv"),
        HEADER
            + "\n1,PAYMENT,9839.64,C1231006815,170136.0,160296.36,M1979787155,0.0,0.0,0,0"
            + "\n2,TRANSFER,181.0,C1305486145,181.0,0.0,C553264065,0.0,0.0,1,0"
            + "\n" // blank line — must be skipped
            + "\n3,CASH_OUT,,C840083671,,0.0,C38997010,21182.0,0.0,1,0" // empty amount + oldbalance
            + "\n4,DEBIT,5337.77,C712410124,41720.0\n"); // short row — trailing columns null

    loader.load(dir, 100);

    assertThat(count()).isEqualTo(4L);
    // empty numeric fields parsed as NULL
    assertThat(scalar("SELECT amount FROM fraud_txn_sample WHERE step = 3")).isNull();
    assertThat(scalar("SELECT oldbalance_org FROM fraud_txn_sample WHERE step = 3")).isNull();
    // short row: missing trailing columns are NULL, present ones parsed
    assertThat(scalar("SELECT name_dest FROM fraud_txn_sample WHERE step = 4")).isNull();
    assertThat(scalar("SELECT is_flagged_fraud FROM fraud_txn_sample WHERE step = 4")).isNull();
    assertThat(scalar("SELECT type FROM fraud_txn_sample WHERE step = 4")).isEqualTo("DEBIT");
  }

  @Test
  void respectsMaxRowsAcrossFiles() throws Exception {
    Files.writeString(dir.resolve("a.csv"), HEADER + row(1) + row(2) + row(3));
    Files.writeString(dir.resolve("b.csv"), HEADER + row(4) + row(5) + row(6));

    loader.load(dir, 2);

    assertThat(count()).isEqualTo(2L);
  }

  @Test
  void skipsLoadWhenStagingTableAlreadyPopulated() throws Exception {
    Files.writeString(dir.resolve("first.csv"), HEADER + row(1) + row(2));
    loader.load(dir, 100);
    assertThat(count()).isEqualTo(2L);

    // a second run is a no-op because the table already has rows
    Files.writeString(dir.resolve("second.csv"), HEADER + row(3) + row(4) + row(5));
    loader.load(dir, 100);
    assertThat(count()).isEqualTo(2L);
  }

  @Test
  void ignoresPathThatIsNotADirectory() throws Exception {
    loader.load(dir.resolve("no-such-dir"), 100);
    assertThat(count()).isZero();
  }

  @Test
  void rollsBackAndLoadsNothingOnMalformedRow() throws Exception {
    Files.writeString(
        dir.resolve("bad.csv"),
        HEADER + "\nNOT_A_NUMBER,PAYMENT,10.0,C1,10.0,0.0,M1,0.0,0.0,0,0");

    assertThatThrownBy(() -> loader.load(dir, 100)).isInstanceOf(NumberFormatException.class);
    assertThat(count()).isZero(); // nothing partially committed
  }

  // ---- helpers --------------------------------------------------------------

  private static String row(int step) {
    return "\n" + step + ",PAYMENT,10.00,C" + step + ",10.0,0.0,M" + step + ",0.0,0.0,0,0";
  }

  private long count() {
    return ((Number) scalar("SELECT count(*) FROM fraud_txn_sample")).longValue();
  }

  private Object scalar(String sql) {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      return rs.next() ? rs.getObject(1) : null;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void exec(String sql) {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.execute(sql);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
