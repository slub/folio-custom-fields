package org.folio.repository;

import static org.folio.repository.CustomFieldsConstants.CUSTOM_FIELDS_TABLE;
import static org.folio.repository.CustomFieldsConstants.JSONB_COLUMN;
import static org.folio.repository.CustomFieldsConstants.MAX_ORDER_COLUMN;
import static org.folio.repository.CustomFieldsConstants.REF_ID_REGEX;
import static org.folio.repository.CustomFieldsConstants.SELECT_BY_SECTION_ID;
import static org.folio.repository.CustomFieldsConstants.SELECT_MAX_ORDER;
import static org.folio.repository.CustomFieldsConstants.SELECT_REF_IDS;
import static org.folio.repository.CustomFieldsConstants.VALUES_COLUMN;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.persist.Conn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.db.CqlQuery;
import org.folio.db.RowSetUtils;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.db.exc.translation.DBExceptionTranslator;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldCollection;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.interfaces.Results;

@Log4j2
@Component
public class CustomFieldsRepositoryImpl implements CustomFieldsRepository {

  @Autowired
  private Vertx vertx;
  @Autowired
  private DBExceptionTranslator excTranslator;

  @Override
  public Future<CustomField> save(CustomField entity, String tenantId) {
    return pgClient(tenantId).withConn(conn -> save(entity, conn));
  }

  @Override
  public Future<CustomField> save(CustomField entity, @Nonnull Conn connection) {
    log.debug("Saving a custom field with id: {}.", entity.getId());

    setIdIfMissing(entity);
    return connection.save(CUSTOM_FIELDS_TABLE, entity.getId(), entity)
        .map(id -> {
          entity.setId(id);
          return entity;
        }).recover(excTranslator.translateOrPassBy());
  }

  @Override
  public Future<Optional<CustomField>> findById(String id, String tenantId) {
    log.debug("Getting a custom field with id: {}.", id);
    return pgClient(tenantId).getById(CUSTOM_FIELDS_TABLE, id, CustomField.class)
        .map(Optional::ofNullable)
        .recover(excTranslator.translateOrPassBy());
  }


  @Override
  public Future<Integer> maxRefId(String customFieldName, String tenantId) {
    return pgClient(tenantId).withConn(conn -> maxRefId(customFieldName, tenantId, conn));
  }

  @Override
  public Future<Integer> maxRefId(String customFieldName, String tenantId, @Nonnull Conn connection) {
    log.debug("Getting custom field ref ids by given name: {}.", customFieldName);
    String query = String.format(SELECT_REF_IDS, getCFTableName(tenantId));
    String refIdRegex = String.format(REF_ID_REGEX, customFieldName);
    Tuple parameters = Tuple.of(refIdRegex);

    return connection.execute(query, parameters)
        .map(this::mapMaxRefId)
        .recover(excTranslator.translateOrPassBy());
  }

  @Override
  public Future<Integer> maxOrder(String tenantId) {
    final String query = String.format(SELECT_MAX_ORDER, getCFTableName(tenantId));
    log.debug("Getting maximum order of custom fields.");
    return pgClient(tenantId).selectSingle(query)
        .map(this::mapMaxOrder)
        .recover(excTranslator.translateOrPassBy());
  }

  @Override
  public Future<CustomFieldCollection> findByQuery(String query, int offset, int limit, String tenantId) {
    CqlQuery<CustomField> q = new CqlQuery<>(pgClient(tenantId), CUSTOM_FIELDS_TABLE, CustomField.class);
    log.debug("Getting custom fields by query.");
    return q.get(query, offset, limit).map(this::toCustomFieldCollection)
      .recover(excTranslator.translateOrPassBy());
  }

  @Override
  public Future<List<CustomField>> findBySectionId(String sectionId, String tenantId, @Nonnull Conn connection) {
    log.debug("Getting custom fields by section id: {}.", sectionId);
    String query = String.format(SELECT_BY_SECTION_ID, getCFTableName(tenantId));
    return connection.execute(query, Tuple.of(sectionId))
      .map(this::mapCustomFields)
      .recover(excTranslator.translateOrPassBy());
  }

  @Override
  public Future<Boolean> update(CustomField entity, String tenantId) {
    return pgClient(tenantId).withConn(conn -> update(entity, conn));
  }

  @Override
  public Future<Boolean> update(CustomField entity, @Nonnull Conn connection) {
    log.debug("Updating a custom field with id: {}.", entity.getId());

    return connection.update(CUSTOM_FIELDS_TABLE, entity, entity.getId())
        .map(rowSet -> rowSet.rowCount() == 1)
        .recover(excTranslator.translateOrPassBy());
  }

  @Override
  public Future<Boolean> delete(String id, String tenantId) {
    return pgClient(tenantId).withConn(conn -> delete(id, conn));
  }

  @Override
  public Future<Boolean> delete(String id, @Nonnull Conn connection) {
    log.debug("Deleting custom field by given id: {}.", id);
    return connection.delete(CUSTOM_FIELDS_TABLE, id)
        .map(rowSet -> rowSet.rowCount() == 1)
        .recover(excTranslator.translateOrPassBy());
  }

  private Integer mapMaxRefId(RowSet<Row> rowSet) {
    if (rowSet.rowCount() == 1 &&
      !RowSetUtils.mapFirstItem(rowSet, row -> row.getString(VALUES_COLUMN)).contains("_")) {
      return 1;
    }
    return RowSetUtils.streamOf(rowSet)
      .map(row -> row.getString(VALUES_COLUMN))
      .map(s -> StringUtils.substringAfter(s, "_"))
      .filter(StringUtils::isNotBlank)
      .mapToInt(Integer::parseInt)
      .max().orElse(0);
  }

  private List<CustomField> mapCustomFields(RowSet<Row> rowSet) {
    return RowSetUtils.mapItems(rowSet,
      row -> ObjectMapperTool.readValue(row.getJsonObject(JSONB_COLUMN).encode(), CustomField.class));
  }

  private Integer mapMaxOrder(Row result) {
    Integer maxOrder = result.getInteger(MAX_ORDER_COLUMN);
    return maxOrder != null ? maxOrder : 0;
  }

  private void setIdIfMissing(CustomField customField) {
    if (StringUtils.isBlank(customField.getId())) {
      customField.setId(UUID.randomUUID().toString());
    }
  }

  private CustomFieldCollection toCustomFieldCollection(Results<CustomField> results) {
    return new CustomFieldCollection()
      .withCustomFields(results.getResults())
      .withTotalRecords(results.getResultInfo().getTotalRecords());
  }

  private String getCFTableName(String tenantId) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + CUSTOM_FIELDS_TABLE;
  }

  private PostgresClient pgClient(String tenantId) {
    return PostgresClient.getInstance(vertx, tenantId);
  }
}
