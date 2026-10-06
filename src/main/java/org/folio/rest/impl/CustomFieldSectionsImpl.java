package org.folio.rest.impl;

import static org.folio.rest.ResponseHelper.respond;
import static org.folio.rest.jaxrs.resource.CustomFieldSections.PostCustomFieldSectionsResponse.headersFor201;
import static org.folio.rest.jaxrs.resource.CustomFieldSections.PostCustomFieldSectionsResponse.respond201WithApplicationJson;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.folio.common.pf.PartialFunction;
import org.folio.rest.annotations.Validate;
import org.folio.rest.aspect.HandleValidationErrors;
import org.folio.rest.jaxrs.model.CustomFieldSection;
import org.folio.rest.jaxrs.model.CustomFieldSectionCollection;
import org.folio.rest.jaxrs.model.PutCustomFieldSectionCollection;
import org.folio.rest.jaxrs.resource.CustomFieldSections;
import org.folio.service.CustomFieldSectionsService;
import org.folio.spring.SpringContextUtil;
import org.folio.validate.section.CustomFieldSectionValidator;

public class CustomFieldSectionsImpl implements CustomFieldSections {

  @Autowired
  private CustomFieldSectionsService sectionsService;

  @Autowired @Qualifier("customFieldsExcHandler")
  private PartialFunction<Throwable, Response> excHandler;

  @Autowired
  private CustomFieldSectionValidator sectionValidator;

  public CustomFieldSectionsImpl() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  @HandleValidationErrors
  public void postCustomFieldSections(String lang, String xOkapiModuleId, CustomFieldSection entity,
                                      Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    sectionValidator.validate(entity);
    Future<CustomFieldSection> saved = sectionsService.save(entity, tenantId(okapiHeaders));
    respond(saved, section -> respond201WithApplicationJson(section, headersFor201()), asyncResultHandler, excHandler);
  }

  @Override
  @Validate
  @HandleValidationErrors
  public void putCustomFieldSections(String xOkapiModuleId, PutCustomFieldSectionCollection entity,
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    List<CustomFieldSection> sections = entity.getCustomFieldSections();
    sections.forEach(sectionValidator::validate);
    validatePutCustomFieldSectionCollection(entity);
    Future<Void> replaced = sectionsService.replaceAll(sections, entity.getEntityType(), tenantId(okapiHeaders));
    respond(replaced, v -> PutCustomFieldSectionsResponse.respond204(), asyncResultHandler, excHandler);
  }

  @Override
  @Validate
  @HandleValidationErrors
  public void getCustomFieldSections(String query, int offset, int limit, String lang, String xOkapiModuleId,
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<CustomFieldSectionCollection> found =
      sectionsService.findByQuery(query, offset, limit, tenantId(okapiHeaders));
    respond(found, GetCustomFieldSectionsResponse::respond200WithApplicationJson, asyncResultHandler, excHandler);
  }

  @Override
  @Validate
  @HandleValidationErrors
  public void getCustomFieldSectionsById(String id, String lang, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<CustomFieldSection> section = sectionsService.findById(id, tenantId(okapiHeaders));
    respond(section, GetCustomFieldSectionsByIdResponse::respond200WithApplicationJson, asyncResultHandler,
      excHandler);
  }

  @Override
  @Validate
  @HandleValidationErrors
  public void putCustomFieldSectionsById(String id, String lang, String xOkapiModuleId, CustomFieldSection entity,
                                         Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (StringUtils.isNotBlank(entity.getId()) && !entity.getId().equals(id)) {
      throw new IllegalArgumentException(
        String.format("The section id '%s' in the body does not match the id '%s' in the path", entity.getId(), id));
    }
    sectionValidator.validate(entity);
    Future<Void> updated = sectionsService.update(id, entity, tenantId(okapiHeaders));
    respond(updated, v -> PutCustomFieldSectionsByIdResponse.respond204(), asyncResultHandler, excHandler);
  }

  @Override
  @Validate
  @HandleValidationErrors
  public void deleteCustomFieldSectionsById(String id, String lang, String xOkapiModuleId,
                                            Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<Void> deleted = sectionsService.delete(id, tenantId(okapiHeaders));
    respond(deleted, v -> DeleteCustomFieldSectionsByIdResponse.respond204(), asyncResultHandler, excHandler);
  }

  private void validatePutCustomFieldSectionCollection(PutCustomFieldSectionCollection collection) {
    List<CustomFieldSection> sections = collection.getCustomFieldSections();
    sections.stream()
      .filter(section -> !section.getEntityType().equals(collection.getEntityType()))
      .findFirst()
      .ifPresent(section -> {
        throw new IllegalArgumentException(String.format(
          "Collection entityType '%s' does not match entityType '%s' of section '%s'",
          collection.getEntityType(), section.getEntityType(), section.getName()));
      });
    failOnDuplicate(sections, CustomFieldSection::getId, "id");
    failOnDuplicate(sections, section -> section.getName().toLowerCase(Locale.ROOT), "name");
  }

  private void failOnDuplicate(List<CustomFieldSection> sections, Function<CustomFieldSection, String> attribute,
                               String attributeName) {
    List<String> values = sections.stream().map(attribute).filter(Objects::nonNull).toList();
    values.stream()
      .filter(value -> values.indexOf(value) != values.lastIndexOf(value))
      .findFirst()
      .ifPresent(value -> {
        throw new IllegalArgumentException(
          String.format("Duplicate section %s '%s' in the collection", attributeName, value));
      });
  }
}
