package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.reverseOrder;

import static org.folio.service.CqlSortUtils.withSortByOrder;
import static org.folio.service.CustomFieldUtils.extractDefaultOptionIds;
import static org.folio.service.CustomFieldUtils.extractOptionIds;
import static org.folio.service.CustomFieldUtils.isSelectableCustomFieldType;
import static org.folio.service.CustomFieldUtils.isTextBoxCustomFieldType;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.common.OkapiParams;
import org.folio.model.RecordUpdate;
import org.folio.repository.CustomFieldSectionsRepository;
import org.folio.repository.CustomFieldsRepository;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldCollection;
import org.folio.rest.jaxrs.model.CustomFieldOptionStatistic;
import org.folio.rest.jaxrs.model.CustomFieldStatistic;
import org.folio.rest.jaxrs.model.SelectFieldOption;
import org.folio.rest.jaxrs.model.SelectFieldOptions;
import org.folio.rest.jaxrs.model.TextField;
import org.folio.rest.validate.Validation;
import org.folio.service.exc.InvalidFieldValueException;
import org.folio.service.exc.ServiceExceptions;

@Log4j2
@Component
public class CustomFieldsServiceImpl implements CustomFieldsService {

  private static final String FORMAT_ATTRIBUTE = "fieldFormat";
  private static final String TYPE_ATTRIBUTE = "type";

  private static final String TYPE_CHANGING_MESSAGE =
    "The type of the custom field can not be changed: newType = %s, oldType = %s";
  private static final String NOT_SELECTABLE_TYPE_MESSAGE = "The type of the custom field must be one of:"
    + "SINGLE_SELECT_DROPDOWN, MULTI_SELECT_DROPDOWN, RADIO_BUTTON";
  private static final String MISSED_OPTION_MESSAGE = "Option with id '%s' not found in custom field '%s'";
  private static final String FORMAT_CHANGING_MESSAGE =
    "The format of the custom field can not be changed: newFormat = %s, oldFormat = %s";
  private static final String SECTION_ID_ATTRIBUTE = "sectionId";
  private static final String SECTION_NOT_FOUND_MESSAGE = "Custom field section with id '%s' not found";
  private static final String SECTION_ENTITY_TYPE_MESSAGE =
    "Custom field section '%s' belongs to entityType '%s', but the custom field has entityType '%s'";

  @Autowired
  private CustomFieldsRepository repository;
  @Autowired
  private CustomFieldSectionsRepository sectionsRepository;
  @Autowired
  private UserService userService;
  @Autowired
  private RecordService recordService;
  @Autowired
  private Vertx vertx;

  @Override
  public Future<CustomField> save(CustomField customField, OkapiParams params) {
    log.debug("Attempts to save customField by [tenant: {}, customField: {}]",
      params.getTenant(), customField);

    return repository.maxOrder(params.getTenant())
      .compose(maxOrder -> {
        customField.setOrder(maxOrder + 1);
        return pgClient(params.getTenant()).withTrans(conn -> validateSection(customField, params.getTenant(), conn)
          .compose(v -> save(customField, params,
                (unAccentName, tenantId) -> repository.maxRefId(unAccentName, params.getTenant(), conn),
                (customField1, tenantId) -> repository.save(customField1, conn))));
      });
  }

  @Override
  public Future<Void> update(String id, CustomField customField, OkapiParams params) {
    log.debug("Attempts to update customField by [id: {}, tenant: {}, customField: {}]",
      id, params.getTenant(), customField);

    return findById(id, params.getTenant())
      .compose(oldCustomField -> {
        customField.setId(oldCustomField.getId());
        customField.setOrder(oldCustomField.getOrder());
        return pgClient(params.getTenant()).withTrans(conn -> validateSection(customField, params.getTenant(), conn)
          .compose(v -> update(customField, oldCustomField, params,
                (customFieldEntity, tenantId) -> repository.update(customFieldEntity, conn))));
      });
  }

  @Override
  public Future<CustomField> findById(String id, String tenantId) {
    log.debug("findById:: Attempts to get customField by [id: {}, tenantId: {}]", id, tenantId);

    return repository.findById(id, tenantId)
      .map(customField -> customField
        .orElseThrow(() -> {
          log.warn("Failed on finding customField by [id: {}, tenantId: {}]", id, tenantId);
          return ServiceExceptions.notFound(CustomField.class, id);
        }));
  }

  @Override
  public Future<CustomFieldCollection> findByQuery(String query, int offset, int limit, String lang, String tenantId) {
    return repository.findByQuery(withSortByOrder(query), offset, limit, tenantId);
  }

  @Override
  public Future<Void> delete(String id, String tenantId) {
    Future<CustomField> cf = findById(id, tenantId);
    log.debug("delete:: Attempts to delete customField by [id: {}, tenantId: {}]", id, tenantId);

    return cf
      .compose(field -> recordService.deleteAllValues(field, tenantId))
      .compose(v -> repository.delete(id, tenantId))
      .compose(deleted -> failIfNotFound(deleted, id))
      .compose(v -> updateCustomFieldsOrder(tenantId));
  }

  @Override
  public Future<List<CustomField>> replaceAll(
      List<CustomField> customFields, String entityType, OkapiParams params) {
    log.info("putCustomFields:: replaceAll method called....");
    log.debug("replaceAll:: Attempt to replace all customFields by [tenantId: {}]", params.getTenant());

    String queryStr = String.format("query=(entityType==%s)", entityType);
    return repository.findByQuery(queryStr, 0, Integer.MAX_VALUE, params.getTenant())
      .compose(existingFields -> {
        setOrder(customFields);
        setIdIfEmpty(customFields);
        Map<String, CustomField> newFieldsMap = createMapById(customFields);
        Map<String, CustomField> existingFieldsMap = createMapById(existingFields.getCustomFields());

        Set<String> fieldsToRemove = Sets.difference(existingFieldsMap.keySet(), newFieldsMap.keySet());
        Set<String> fieldsToUpdate = Sets.intersection(existingFieldsMap.keySet(), newFieldsMap.keySet());
        Set<String> fieldsToInsert = Sets.difference(newFieldsMap.keySet(), existingFieldsMap.keySet());
        PostgresClient postgresClient = PostgresClient.getInstance(vertx, params.getTenant());

        return postgresClient.withTrans(connection -> validateSections(customFields, params.getTenant(), connection)
                .compose(x -> removeFields(connection, fieldsToRemove))
                .compose(x -> updateFields(params, connection, fieldsToUpdate, newFieldsMap, existingFieldsMap))
                .compose(x -> insertFields(params, connection, fieldsToInsert, newFieldsMap))
                .compose(unused -> removeValues(params, connection, fieldsToRemove, existingFieldsMap)))
                .map(customFields);
      });
  }

  /**
   * Validates the sectionId of each distinct section referenced by the given fields.
   */
  private Future<Void> validateSections(List<CustomField> customFields, String tenantId, Conn connection) {
    Collection<CustomField> oneFieldPerSection = customFields.stream()
      .filter(field -> field.getSectionId() != null)
      .collect(Collectors.toMap(CustomField::getSectionId, Function.identity(), (first, second) -> first))
      .values();
    return executeForEach(oneFieldPerSection, field -> validateSection(field, tenantId, connection));
  }

  /**
   * Validates that the section the field is assigned to exists and has the field's entityType.
   * The section row is locked with FOR SHARE until the transaction ends, so a concurrent section delete
   * (FOR UPDATE) waits and then sees this field as assigned. No field can end up in a deleted section.
   */
  private Future<Void> validateSection(CustomField customField, String tenantId, Conn connection) {
    String sectionId = customField.getSectionId();
    if (sectionId == null) {
      return succeededFuture();
    }
    return sectionsRepository.findByIdForShare(sectionId, tenantId, connection)
      .compose(section -> {
        if (section.isEmpty()) {
          return failedFuture(new InvalidFieldValueException(SECTION_ID_ATTRIBUTE, sectionId,
            format(SECTION_NOT_FOUND_MESSAGE, sectionId)));
        }
        String sectionEntityType = section.get().getEntityType();
        if (!Objects.equals(sectionEntityType, customField.getEntityType())) {
          return failedFuture(new InvalidFieldValueException(SECTION_ID_ATTRIBUTE, sectionId,
            format(SECTION_ENTITY_TYPE_MESSAGE, section.get().getName(), sectionEntityType,
              customField.getEntityType())));
        }
        return succeededFuture();
      });
  }

  private Future<Void> removeFields(Conn connection, Set<String> fieldsToRemove) {
    return executeForEach(fieldsToRemove, id -> repository.delete(id, connection));
  }

  private Future<Void> updateFields(OkapiParams params, Conn connection, Set<String> fieldsToUpdate, Map<String, CustomField> newFieldsMap, Map<String, CustomField> existingFieldsMap) {
    return executeForEach(fieldsToUpdate,
            id -> update(newFieldsMap.get(id), existingFieldsMap.get(id), params,
                    (customFieldEntity, tenantId) -> repository.update(customFieldEntity, connection)));
  }

  private Future<Void> insertFields(OkapiParams params, Conn connection, Set<String> fieldsToInsert, Map<String, CustomField> newFieldsMap) {
    return executeForEach(fieldsToInsert, id -> save(newFieldsMap.get(id), params,
            (unAccentName, tenantId) -> repository.maxRefId(unAccentName, params.getTenant(), connection),
            (customField, tenantId) -> repository.save(customField, connection)));
  }

  private Future<Void> removeValues(
      OkapiParams params, Conn conn, Set<String> fieldsToRemove, Map<String, CustomField> existingFieldsMap) {

    List<CustomField> deletedFields = fieldsToRemove.stream()
            .map(existingFieldsMap::get)
            .collect(Collectors.toList());
    return executeForEach(deletedFields, field -> recordService.deleteAllValues(conn, field, params.getTenant()));
  }

  @Override
  public Future<CustomFieldStatistic> retrieveStatistic(String id, String tenantId) {
    return findById(id, tenantId)
      .compose(field -> recordService.retrieveStatistic(field, tenantId));
  }

  @Override
  public Future<CustomFieldOptionStatistic> retrieveOptionStatistic(String id, String optId, String tenantId) {
    return findById(id, tenantId)
      .compose(field -> {
          Validation validation = Validation.instance()
            .addTest(field, isSelectable())
            .addTest(field, hasOptionWithId(optId));

          return validation.validate()
            .compose(aVoid -> recordService.retrieveOptionStatistic(field, optId, tenantId));
        }
      );
  }

  private void setIdIfEmpty(List<CustomField> customFields) {
    customFields.stream()
      .filter(field -> StringUtils.isBlank(field.getId()))
      .forEach(field -> field.setId(UUID.randomUUID().toString()));
  }

  private List<String> getOptionsIdsToDelete(CustomField newCustomField, CustomField oldCustomField) {
    if (isSelectableCustomFieldType(newCustomField)) {
      List<String> newCFOptionIds = extractOptionIds(newCustomField);
      List<String> oldCFOptionIds = extractOptionIds(oldCustomField);
      return new ArrayList<>(CollectionUtils.subtract(oldCFOptionIds, newCFOptionIds));
    }
    return Collections.emptyList();
  }

  private void sortOptions(CustomField customField) {
    final SelectFieldOptions cfOptions = customField.getSelectField().getOptions();
    final Optional<SelectFieldOptions.SortingOrder> sortingOrder = Optional.ofNullable(cfOptions.getSortingOrder());
    if (sortingOrder.isPresent()) {
      switch (sortingOrder.get()) {
        case ASC:
          log.info("sortOptions:: Attempts to sort by naturalOrder");
          cfOptions.getValues().sort(comparing(SelectFieldOption::getValue, naturalOrder()));
          break;
        case DESC:
          log.info("sortOptions:: Attempts to sort by reversedOrder");
          cfOptions.getValues().sort(comparing(SelectFieldOption::getValue, reverseOrder()));
          break;
        case CUSTOM:
        default:
          break;
      }
    }
  }

  private Future<CustomField> save(CustomField customField, OkapiParams params,
                                    BiFunction<String, String, Future<Integer>> maxRefIdFunction,
                                    BiFunction<CustomField, String, Future<CustomField>> dbSaveOperation) {
    final String unAccentName = unAccentName(customField.getName());
    setDefaultFormat(customField);
    if (isSelectableCustomFieldType(customField)) {
      sortOptions(customField);
      generateOptionIds(customField);
    }
    return populateCreator(customField, params)
            .compose(o -> maxRefIdFunction.apply(unAccentName, params.getTenant()))
            .compose(maxRefId -> {
              customField.setRefId(getCustomFieldRefId(unAccentName, maxRefId));
              return dbSaveOperation.apply(customField, params.getTenant());
            });
  }

  private Future<Void> update(CustomField customField, CustomField oldCustomField, OkapiParams params,
                               BiFunction<CustomField, String, Future<Boolean>> dbUpdateOperation) {
    customField.setRefId(oldCustomField.getRefId());
    setDefaultFormat(customField);

    RecordUpdate recordUpdate = createRecordUpdate(customField, oldCustomField);

    Future<Void> validated = Validation.instance()
            .addTest(customField.getType(), typeNotChanged(oldCustomField.getType()))
            .addTest(customField, formatNotChanged(oldCustomField))
            .validate();

    return validated
            .compose(o -> populateUpdater(customField, params))
            .compose(o -> dbUpdateOperation.apply(customField, params.getTenant()))
            .compose(found -> failIfNotFound(found, customField.getId()))
            .compose(aVoid -> {
              if (isRequiredRecordUpdate(recordUpdate)) {
                return recordService.deleteMissedOptionValues(recordUpdate, params.getTenant());
              } else {
                return Future.succeededFuture(aVoid);
              }
            });
  }

  private void setDefaultFormat(CustomField customField) {
    if (isTextBoxCustomFieldType(customField) && customField.getTextField() == null) {
      customField.setTextField(new TextField().withFieldFormat(TextField.FieldFormat.TEXT));
    }
  }

  private boolean isRequiredRecordUpdate(RecordUpdate recordUpdate) {
    return recordUpdate != null && !CollectionUtils.isEmpty(recordUpdate.getOptionIdsToDelete());
  }

  private RecordUpdate createRecordUpdate(CustomField customField, CustomField oldCustomField) {
    RecordUpdate recordUpdate = null;
    if (isSelectableCustomFieldType(customField)) {
      sortOptions(customField);
      generateOptionIds(customField);
      List<String> optionIdsToDelete = getOptionsIdsToDelete(customField, oldCustomField);
      List<String> defaultIds = extractDefaultOptionIds(customField);
      recordUpdate = new RecordUpdate(customField, optionIdsToDelete, defaultIds);
    }
    return recordUpdate;
  }

  private Consumer<CustomField.Type> typeNotChanged(CustomField.Type oldType) {
    return type -> validateValueNotChanged(TYPE_ATTRIBUTE, type, oldType, TYPE_CHANGING_MESSAGE, type, oldType);
  }

  private Consumer<CustomField> formatNotChanged(CustomField oldTextField) {
    return field -> {
      if (isTextBoxCustomFieldType(field)) {
        TextField.FieldFormat oldFieldFormat = oldTextField.getTextField().getFieldFormat();
        TextField.FieldFormat newFieldFormat = field.getTextField().getFieldFormat();
        validateValueNotChanged(FORMAT_ATTRIBUTE, newFieldFormat, oldFieldFormat,
          FORMAT_CHANGING_MESSAGE, newFieldFormat, oldFieldFormat);
      }
    };
  }

  private Consumer<CustomField> isSelectable() {
    return field -> {
      if (!isSelectableCustomFieldType(field)) {
        throw new InvalidFieldValueException(TYPE_ATTRIBUTE, field.getType(), NOT_SELECTABLE_TYPE_MESSAGE);
      }
    };
  }

  private Consumer<CustomField> hasOptionWithId(String optId) {
    return field -> {
      if (!extractOptionIds(field).contains(optId)) {
        throw new InvalidFieldValueException("optId", field.getSelectField().getOptions(),
          String.format(MISSED_OPTION_MESSAGE, optId, field.getId())
        );
      }
    };
  }

  private <T> void validateValueNotChanged(String field, T newValue, T oldValue, String message, Object... values) {
    if (!Objects.equals(newValue, oldValue)) {
      throw new InvalidFieldValueException(field, newValue, format(message, values));
    }
  }

  private void setOrder(List<CustomField> customFields) {
    for (int i = 0; i < customFields.size(); i++) {
      customFields.get(i).setOrder(i + 1);
    }
  }

  private Map<String, CustomField> createMapById(List<CustomField> customFields) {
    return customFields.stream()
      .collect(Collectors.toMap(CustomField::getId, Function.identity()));
  }

  private <T> Future<Void> executeForEach(Collection<T> collection, Function<T, Future<?>> action) {
    Future<?> resultFuture = Future.succeededFuture();
    for (T item : collection) {
      resultFuture = resultFuture.compose(o -> action.apply(item));
    }
    return resultFuture.map(o -> null);
  }

  private Future<Void> updateCustomFieldsOrder(String tenantId) {
    final Future<Void> result = succeededFuture();
    return result
      .compose(v -> repository.findByQuery(null, 0, Integer.MAX_VALUE, tenantId)
        .map(this::updateCustomFieldsOrder)
        .compose(customFields -> updateCustomFields(customFields, tenantId)));
  }

  private Future<Void> updateCustomFields(List<CustomField> customFields, String tenantId) {
    return Future.all(customFields
      .stream()
      .map(customField -> repository.update(customField, tenantId))
      .collect(Collectors.toList())
    ).map(f -> null);
  }

  private List<CustomField> updateCustomFieldsOrder(CustomFieldCollection customFieldsCollection) {
    final List<CustomField> customFields = sortByOrder(customFieldsCollection).getCustomFields();
    for (int i = 0; i < customFields.size(); i++) {
      customFields.get(i).setOrder(i + 1);
    }
    return customFields;
  }

  private Future<Void> failIfNotFound(boolean found, String entityId) {
    if (found) {
      return succeededFuture();
    }
    log.warn("Failed on finding customField by [id: {}]", entityId);
    return failedFuture(ServiceExceptions.notFound(CustomField.class, entityId));
  }

  private Future<Void> populateCreator(CustomField entity, OkapiParams params) {
    return userService.getUserInfo(params.getHeaders()).map(user -> {
      if (entity.getMetadata() != null) {
        entity.getMetadata().setCreatedByUsername(user.getUsername());
      }
      return null;
    });
  }

  private Future<Void> populateUpdater(CustomField entity, OkapiParams params) {
    return userService.getUserInfo(params.getHeaders()).map(user -> {
      if (entity.getMetadata() != null) {
        entity.getMetadata().setUpdatedByUsername(user.getUsername());
      }
      return null;
    });
  }

  private PostgresClient pgClient(String tenantId) {
    return PostgresClient.getInstance(vertx, tenantId);
  }

  private String unAccentName(String customFieldName) {
    final String capitalizedString = WordUtils.capitalizeFully(customFieldName);
    final String splitString = Normalizer.normalize(capitalizedString, Normalizer.Form.NFD)
      .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
      .replaceAll("[^a-zA-Z\\s]", "")
      .replaceAll("\\s+", "");

    final String result = StringUtils.uncapitalize(splitString);
    return StringUtils.isBlank(result)
      ? Base64.getEncoder().withoutPadding().encodeToString(customFieldName.getBytes(StandardCharsets.UTF_8))
      : result;
  }

  private String getCustomFieldRefId(String id, Integer maxRefId) {
    return id + (maxRefId < 1 ? "" : "_" + (maxRefId + 1));
  }

  private CustomFieldCollection sortByOrder(CustomFieldCollection collection) {
    collection.getCustomFields().sort(Comparator.comparingInt(CustomField::getOrder));
    return collection;
  }

  private void generateOptionIds(CustomField field) {
    List<SelectFieldOption> values = field.getSelectField().getOptions().getValues();
    int maxOptionIdIndex = extractOptionIds(field).stream()
      .filter(Objects::nonNull)
      .map(s -> s.substring(s.indexOf('_') + 1))
      .mapToInt(Integer::parseInt)
      .max()
      .orElse(0);
    for (SelectFieldOption value : values) {
      if (StringUtils.isBlank(value.getId())) {
        value.setId("opt_" + ++maxOptionIdIndex);
      }
    }
  }
}
