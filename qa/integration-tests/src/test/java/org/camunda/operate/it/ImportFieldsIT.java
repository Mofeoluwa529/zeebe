/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Map;

import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.PayloadUtil;
import org.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ImportFieldsIT extends OperateZeebeIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(ImportFieldsIT.class);

  @Autowired
  private PayloadUtil payloadUtil;

  @Autowired
  private UpdateVariableHandler updateVariableHandler;

  @Before
  public void before() {
    super.before();
    try {
      FieldSetter.setField(updateVariableHandler, UpdateVariableHandler.class.getDeclaredField("zeebeClient"), zeebeClient);
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }
  }

  @Test // OPE-818
  public void testErrorMessageSizeCanBeHigherThan32KB() {
    // having
    String errorMessageMoreThan32KB = buildStringWithLengthOf(32 * 1024 + 42);

    // when
    tester
        .deployWorkflow("demoProcess_v_1.bpmn")
        .and()
        .startWorkflowInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil().workflowInstanceIsStarted()
        .and()
        .failTask("taskA", errorMessageMoreThan32KB)
        .waitUntil().incidentIsActive();

    // then
    assertThat(tester.hasIncidentWithErrorMessage(errorMessageMoreThan32KB)).isTrue();
  }
  
  @Test 
  // OPE-900
  // See also: https://discuss.elastic.co/t/error-document-contains-at-least-one-immense-term-in-field/66486
  public void testVariableValueSizeCanBeHigherThan32KB() throws Exception {
    // having
    //  big json string
    String bigJSONVariablePayload = payloadUtil.readJSONStringFromClasspath("/large-payload.json");
    //  and object with two vars
    Map<String, Object> variables = payloadUtil.parsePayload(bigJSONVariablePayload);

    // when
    tester
      .deployWorkflow("single-task.bpmn")
      .and()
      .startWorkflowInstance("process", bigJSONVariablePayload)
      .waitUntil().workflowInstanceIsStarted()
      .and()
      .waitUntil().variableExists("small")
      .and().variableExists("large");

    // then
    assertThat(tester.hasVariable("small","\""+variables.get("small").toString()+"\"")).isTrue();
    assertThat(tester.hasVariable("large","\""+variables.get("large").toString()+"\"")).isTrue();
  }

  @Test
  // OPE-900
  // See also: https://discuss.elastic.co/t/error-document-contains-at-least-one-immense-term-in-field/66486
  public void testUpdateVariableValueSizeCanBeHigherThan32KB() throws Exception {
    // having
    //  big json string
    String bigJSONVariablePayload = "\"" + buildStringWithLengthOf(32 * 1024 + 42) + "\"";
    String varName = "name";

    // when
    tester
      .deployWorkflow("single-task.bpmn")
      .and()
      .startWorkflowInstance("process", "{\"" + varName + "\": \"smallValue\"}")
      .waitUntil().workflowInstanceIsStarted()
      .and()
      .waitUntil().variableExists(varName)
      .updateVariableOperation(varName, bigJSONVariablePayload)
      .waitUntil().operationIsCompleted();

    // then
    assertThat(tester.hasVariable(varName, bigJSONVariablePayload)).isTrue();
  }

  protected String buildStringWithLengthOf(int length) {
    StringBuilder result = new StringBuilder();
    String fillChar = "a";
    for (int i = 0; i < length; i++) {
      result.append(fillChar);
    }
    return result.toString();
  }

}
