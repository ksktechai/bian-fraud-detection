package nz.co.ksktech.fraud.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Staging table for raw PaySim transactions loaded from CSV. Mirrors the PaySim column layout
 * (step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,oldbalanceDest,newbalanceDest,
 * isFraud,isFlaggedFraud) so analysts can replay rows into the evaluation flow.
 */
@Entity
@Table(name = "fraud_txn_sample")
public class FraudTxnSample extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Integer step;

  public String type;

  public BigDecimal amount;

  @Column(name = "name_orig")
  public String nameOrig;

  @Column(name = "oldbalance_org")
  public BigDecimal oldbalanceOrg;

  @Column(name = "newbalance_orig")
  public BigDecimal newbalanceOrig;

  @Column(name = "name_dest")
  public String nameDest;

  @Column(name = "oldbalance_dest")
  public BigDecimal oldbalanceDest;

  @Column(name = "newbalance_dest")
  public BigDecimal newbalanceDest;

  @Column(name = "is_fraud")
  public Integer isFraud;

  @Column(name = "is_flagged_fraud")
  public Integer isFlaggedFraud;
}
