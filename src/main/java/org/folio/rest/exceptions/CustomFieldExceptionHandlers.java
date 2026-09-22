package org.folio.rest.exceptions;

import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;

import static org.folio.common.pf.PartialFunctions.pf;
import static org.folio.rest.exc.ExceptionPredicates.instanceOf;

import java.util.List;

import javax.ws.rs.core.Response;


import io.vertx.core.json.Json;

import org.folio.common.pf.PartialFunction;
import org.folio.rest.ResponseHelper;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.service.SectionInUseException;
import org.folio.service.exc.InvalidFieldValueException;
import org.folio.validate.ValidationUtil;

public class CustomFieldExceptionHandlers {

  private CustomFieldExceptionHandlers() {
  }

  public static PartialFunction<Throwable, Response> invalidOrderHandler() {
    return pf(instanceOf(IllegalArgumentException.class)
        .and(throwable -> {
          IllegalArgumentException exc = (IllegalArgumentException) throwable;
          return "Order number should be unique.".equals(exc.getMessage());
        })
      , CustomFieldExceptionHandlers::invalidOrderToUnprocessable);
  }

  public static PartialFunction<Throwable, Response> invalidValueHandler() {
    return pf(instanceOf(InvalidFieldValueException.class), CustomFieldExceptionHandlers::invalidValueToUnprocessable);
  }

  public static PartialFunction<Throwable, Response> sectionInUseHandler() {
    return pf(instanceOf(SectionInUseException.class), CustomFieldExceptionHandlers::sectionInUseToUnprocessable);
  }

  private static Response invalidOrderToUnprocessable(Throwable t) {
    IllegalArgumentException exc = (IllegalArgumentException) t;
    final Error errorMessage = ValidationUtil.createError(
      null, null, exc.getMessage());
    return ResponseHelper.statusWithJson(SC_UNPROCESSABLE_ENTITY, errorMessage);
  }

  private static Response sectionInUseToUnprocessable(Throwable t) {
    SectionInUseException exc = (SectionInUseException) t;

    Error error = new Error()
      .withMessage(exc.getMessage())
      .withCode(SectionInUseException.CODE)
      .withType("1")
      .withParameters(exc.getAssignedFields().stream()
        .map(field -> new Parameter().withKey(field.getId()).withValue(field.getName()))
        .toList());

    return ResponseHelper.statusWithJson(SC_UNPROCESSABLE_ENTITY, new Errors().withErrors(List.of(error)));
  }

  private static Response invalidValueToUnprocessable(Throwable t) {
    InvalidFieldValueException exc = (InvalidFieldValueException) t;

    Error error = ValidationUtil.createError(Json.encode(exc.getValue()), exc.getField(), exc.getMessage());

    return ResponseHelper.statusWithJson(SC_UNPROCESSABLE_ENTITY, error);
  }
}
